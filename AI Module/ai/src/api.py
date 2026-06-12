from fastapi import FastAPI, HTTPException, BackgroundTasks, UploadFile, File
from pydantic import BaseModel
import uvicorn
import time
import sys
import os
import json
import subprocess
import threading
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

# Đảm bảo import được module
script_path = Path(__file__).resolve()
workspace_dir = script_path.parent.parent.parent
if str(workspace_dir) not in sys.path:
    sys.path.append(str(workspace_dir))

from ai.src.pipeline.rag_pipeline import AsyncQueryPipeline
from ai.src.config import GENERATOR_MODEL_NAME

app = FastAPI(
    title="Clef RAG Chatbot API",
    description=f"API giao tiếp với hệ thống RAG nội bộ bằng {GENERATOR_MODEL_NAME}",
    version="1.0.0"
)

# Khởi tạo Pipeline RAG toàn cục để tái sử dụng
pipeline = AsyncQueryPipeline()

class ChatRequest(BaseModel):
    question: str

class ChatResponse(BaseModel):
    answer: str
    runtime_ms: float

class ImportJob(BaseModel):
    id: str
    fileName: str
    fileSize: int
    savedPath: str
    status: str
    progress: int
    step: str
    message: str
    importedAt: str
    startedAt: Optional[str] = None
    completedAt: Optional[str] = None
    chunks: Optional[int] = None
    error: Optional[str] = None

class UploadResponse(BaseModel):
    message: str
    job: ImportJob

IMPORT_HISTORY_PATH = Path(workspace_dir) / "ai" / "data" / "import_jobs.json"
CHUNKS_PATH = Path(workspace_dir) / "ai" / "data" / "processed" / "chunks.jsonl"
jobs_lock = threading.Lock()

def now_iso():
    return datetime.now(timezone.utc).isoformat()

def normalize_job(job):
    normalized = {
        "id": job.get("id", str(uuid.uuid4())),
        "fileName": job.get("fileName", "unknown"),
        "fileSize": int(job.get("fileSize") or 0),
        "savedPath": job.get("savedPath", ""),
        "status": job.get("status", "completed"),
        "progress": int(job.get("progress") or 100),
        "step": job.get("step", "completed"),
        "message": job.get("message", ""),
        "importedAt": job.get("importedAt", now_iso()),
        "startedAt": job.get("startedAt"),
        "completedAt": job.get("completedAt"),
        "chunks": job.get("chunks"),
        "error": job.get("error"),
    }
    if normalized["status"] == "accepted":
        normalized["status"] = "completed"
    return normalized

def read_jobs():
    if not IMPORT_HISTORY_PATH.exists():
        return []
    try:
        jobs = json.loads(IMPORT_HISTORY_PATH.read_text(encoding="utf-8"))
        if not isinstance(jobs, list):
            return []
        return [normalize_job(job) for job in jobs if isinstance(job, dict)]
    except (json.JSONDecodeError, OSError):
        return []

def write_jobs(jobs):
    IMPORT_HISTORY_PATH.parent.mkdir(parents=True, exist_ok=True)
    IMPORT_HISTORY_PATH.write_text(json.dumps(jobs, ensure_ascii=False, indent=2), encoding="utf-8")

def save_job(job):
    with jobs_lock:
        jobs = read_jobs()
        existing_index = next((index for index, item in enumerate(jobs) if item["id"] == job["id"]), None)
        if existing_index is None:
            jobs.insert(0, job)
        else:
            jobs[existing_index] = job
        write_jobs(jobs[:100])

def update_job(job_id, **changes):
    with jobs_lock:
        jobs = read_jobs()
        for item in jobs:
            if item["id"] == job_id:
                item.update(changes)
                write_jobs(jobs[:100])
                return item
    return None

def count_chunks():
    if not CHUNKS_PATH.exists():
        return 0
    with CHUNKS_PATH.open("r", encoding="utf-8") as file:
        return sum(1 for line in file if line.strip())

def run_script(script_path):
    result = subprocess.run(
        [sys.executable, script_path],
        check=True,
        capture_output=True,
        text=True,
    )
    if result.stdout:
        print(result.stdout)
    if result.stderr:
        print(result.stderr)

def run_indexing_scripts(job_id: str):
    global pipeline
    print("\n[Background] Bắt đầu quá trình cắt chunk và lưu DB...")
    update_job(
        job_id,
        status="processing",
        progress=15,
        step="prepare",
        startedAt=now_iso(),
        message="Đang cắt tài liệu thành chunks.",
    )
    try:
        # Đường dẫn tới thư mục ai/scripts
        scripts_dir = os.path.join(workspace_dir, "ai", "scripts")
        script_01 = os.path.join(scripts_dir, "01_prepare_data.py")
        script_02 = os.path.join(scripts_dir, "02_index_data.py")
        
        print("[Background] Chạy 01_prepare_data.py...")
        run_script(script_01)
        chunk_total = count_chunks()
        update_job(
            job_id,
            progress=55,
            step="index",
            chunks=chunk_total,
            message=f"Đã tạo {chunk_total} chunks. Đang lưu vào vector DB.",
        )
        
        # Đóng Qdrant client của pipeline hiện tại để giải phóng file lock cho script indexer
        if pipeline and hasattr(pipeline, "retriever") and pipeline.retriever and hasattr(pipeline.retriever, "client") and pipeline.retriever.client:
            print("[Background] Đóng Qdrant client của pipeline để giải phóng lock...")
            try:
                import asyncio
                loop = asyncio.new_event_loop()
                loop.run_until_complete(pipeline.retriever.client.close())
                loop.close()
                print("[Background] Đã đóng Qdrant client thành công.")
            except Exception as e:
                print(f"[Background] Lỗi khi đóng Qdrant client: {e}")

        print("[Background] Chạy 02_index_data.py...")
        run_script(script_02)
        update_job(
            job_id,
            progress=85,
            step="reload",
            message="Đã index xong. Đang nạp lại pipeline RAG.",
        )
        
        print("[Background] Hoàn thành cắt chunk và lưu DB thành công!")
        
        # Reset pipeline để cập nhật dữ liệu mới từ Qdrant
        pipeline = AsyncQueryPipeline()
        update_job(
            job_id,
            status="completed",
            progress=100,
            step="completed",
            completedAt=now_iso(),
            message=f"Import hoàn tất. AI đã nạp lại dữ liệu với {count_chunks()} chunks.",
        )
    except subprocess.CalledProcessError as e:
        print(f"[Background] Lỗi khi chạy quá trình Indexing: {e}")
        error_output = e.stderr or e.stdout or str(e)
        update_job(
            job_id,
            status="failed",
            progress=100,
            step="failed",
            completedAt=now_iso(),
            error=error_output[-2000:],
            message="Import thất bại khi chạy indexing.",
        )
    except Exception as e:
        print(f"[Background] Lỗi không mong đợi khi import: {e}")
        update_job(
            job_id,
            status="failed",
            progress=100,
            step="failed",
            completedAt=now_iso(),
            error=str(e),
            message="Import thất bại.",
        )

@app.get("/uploads", response_model=list[ImportJob])
async def list_uploads():
    return read_jobs()

@app.get("/uploads/{job_id}", response_model=ImportJob)
async def get_upload(job_id: str):
    for job in read_jobs():
        if job["id"] == job_id:
            return job
    raise HTTPException(status_code=404, detail="Không tìm thấy job import.")

@app.post("/upload", response_model=UploadResponse)
async def upload_document(background_tasks: BackgroundTasks, file: UploadFile = File(...)):
    try:
        # Lưu file vào thư mục upload bên trong handbook
        uploads_dir = os.path.join(workspace_dir, "ai", "data", "handbook", "uploads")
        os.makedirs(uploads_dir, exist_ok=True)
        
        safe_filename = os.path.basename(file.filename or "document")
        file_path = os.path.join(uploads_dir, safe_filename)
        
        with open(file_path, "wb") as buffer:
            content = await file.read()
            buffer.write(content)
            
        print(f"\n[API] Đã lưu file mới: {file_path}")

        job = {
            "id": str(uuid.uuid4()),
            "fileName": safe_filename,
            "fileSize": len(content),
            "savedPath": os.path.relpath(file_path, workspace_dir),
            "status": "queued",
            "progress": 10,
            "step": "saved",
            "message": f"Đã lưu file '{safe_filename}'. Đang chờ chạy indexing.",
            "importedAt": now_iso(),
            "startedAt": None,
            "completedAt": None,
            "chunks": None,
            "error": None,
        }
        save_job(job)
        
        # Kích hoạt quá trình cắt chunk và lưu DB chạy ngầm
        background_tasks.add_task(run_indexing_scripts, job["id"])
        
        return {
            "message": f"Đã nhận file '{safe_filename}'. Có thể theo dõi quá trình import trong lịch sử.",
            "job": job,
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

def is_greeting_query(query: str) -> bool:
    query_clean = query.strip().lower().rstrip("?.! ")
    greetings = {
        "hi", "hello", "xin chào", "chào bạn", "chào", "chao", "alo", "helo", "halô", "hế lô",
        "tạm biệt", "bye", "goodbye", "tạm biệt bạn",
        "cảm ơn", "cám ơn", "thank you", "thanks", "thank",
        "bạn là ai", "bạn tên gì", "tên bạn là gì", "bot là ai",
        "khỏe không", "bạn khỏe không"
    }
    if query_clean in greetings:
        return True
    
    # Check if query is very short and contains a greeting word
    words = query_clean.split()
    if len(words) <= 3:
        if any(w in greetings for w in words):
            return True
            
    return False

@app.post("/chat", response_model=ChatResponse)
async def chat_endpoint(request: ChatRequest):
    try:
        start_time = time.time()
        
        # Đưa câu hỏi vào Pipeline RAG
        result = await pipeline.run(request.question)
        
        # Lấy câu trả lời
        is_exact_qa = False
        if isinstance(result, dict):
            answer = result.get("answer", "")
            is_exact_qa = result.get("is_exact_qa", False)
        else:
            answer = str(result)
            
        is_greeting = is_greeting_query(request.question)
        
        # Chuẩn hóa để kiểm tra các dạng câu trả lời không có thông tin/từ chối trả lời của LLM
        answer_clean = answer.strip().lower()
        refusal_keywords = [
            "không có thông tin",
            "không tìm thấy",
            "không đề cập",
            "không được đề cập",
            "xin lỗi",
            "không liên quan",
            "không cung cấp thông tin"
        ]
        is_no_info = any(kw in answer_clean for kw in refusal_keywords)
            
        # Bổ sung câu khuyến nghị miễn trừ trách nhiệm theo yêu cầu
        # Không thêm disclaimer nếu:
        # 1. Là câu chào xã giao
        # 2. Câu hỏi trùng khớp chính xác trong bộ dữ liệu (Exact QA)
        # 3. Câu trả lời báo không có thông tin hoặc từ chối trả lời
        if not is_greeting and not is_exact_qa and not is_no_info:
            disclaimer = "\n\nLưu ý: Câu trả lời này chỉ mang tính chất tham khảo. Vui lòng liên hệ bộ phận nhân sự hoặc quản lý để xác nhận thông tin chính xác."
            answer = answer.strip() + disclaimer
            
        runtime_ms = (time.time() - start_time) * 1000
        
        return ChatResponse(
            answer=answer.strip(),
            runtime_ms=round(runtime_ms, 2)
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/health")
def health_check():
    return {"status": "ok", "model": GENERATOR_MODEL_NAME}

if __name__ == "__main__":
    print("🚀 Đang khởi động API Server trên Cổng 3000...")
    uvicorn.run("api:app", host="0.0.0.0", port=3000, reload=True)
