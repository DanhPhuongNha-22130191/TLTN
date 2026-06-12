from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import uvicorn
import time
import sys
import os
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
    # BƯỚC 7: FASTAPI NHẬN YÊU CẦU POST /chat VÀ BẮT ĐẦU ĐO RUNTIME
    try:
        start_time = time.time()
        
        # BƯỚC 8: GỌI HÀM pipeline.run(question) ĐỂ KHỞI CHẠY ĐƯỜNG ỐNG RAG
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
        # BƯỚC 23: TỰ ĐỘNG CHÈN THÊM DISCLAIMER NẾU ĐÁP ÁN KHÔNG PHẢI EXACT MATCH/XÃ GIAO/TỪ CHỐI
        if not is_greeting and not is_exact_qa and not is_no_info:
            disclaimer = "\n\nLưu ý: Câu trả lời này chỉ mang tính chất tham khảo. Vui lòng liên hệ bộ phận nhân sự hoặc quản lý để xác nhận thông tin chính xác."
            answer = answer.strip() + disclaimer
            
        # BƯỚC 22: TÍNH TOÁN runtime_ms CỦA TRUY VẤN
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
