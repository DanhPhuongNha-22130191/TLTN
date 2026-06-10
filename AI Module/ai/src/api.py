from fastapi import FastAPI, HTTPException, BackgroundTasks, UploadFile, File
from pydantic import BaseModel
import uvicorn
import time
import sys
import os
import subprocess
from pathlib import Path

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

def run_indexing_scripts():
    print("\n[Background] Bắt đầu quá trình cắt chunk và lưu DB...")
    try:
        # Đường dẫn tới thư mục ai/scripts
        scripts_dir = os.path.join(workspace_dir, "ai", "scripts")
        script_01 = os.path.join(scripts_dir, "01_prepare_data.py")
        script_02 = os.path.join(scripts_dir, "02_index_data.py")
        
        print("[Background] Chạy 01_prepare_data.py...")
        subprocess.run([sys.executable, script_01], check=True)
        
        print("[Background] Chạy 02_index_data.py...")
        subprocess.run([sys.executable, script_02], check=True)
        
        print("[Background] Hoàn thành cắt chunk và lưu DB thành công!")
        
        # Reset pipeline để cập nhật dữ liệu mới từ Qdrant
        global pipeline
        pipeline = AsyncQueryPipeline()
    except subprocess.CalledProcessError as e:
        print(f"[Background] Lỗi khi chạy quá trình Indexing: {e}")

@app.post("/upload")
async def upload_document(background_tasks: BackgroundTasks, file: UploadFile = File(...)):
    try:
        # Lưu file vào thư mục upload bên trong handbook
        uploads_dir = os.path.join(workspace_dir, "ai", "data", "handbook", "uploads")
        os.makedirs(uploads_dir, exist_ok=True)
        
        file_path = os.path.join(uploads_dir, file.filename)
        
        with open(file_path, "wb") as buffer:
            content = await file.read()
            buffer.write(content)
            
        print(f"\n[API] Đã lưu file mới: {file_path}")
        
        # Kích hoạt quá trình cắt chunk và lưu DB chạy ngầm
        background_tasks.add_task(run_indexing_scripts)
        
        return {"message": f"Đã nhận file '{file.filename}'. Hệ thống đang tự động cắt chunk và lưu vào cơ sở dữ liệu nền."}
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
