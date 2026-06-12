import os
import sys
import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from pydantic import BaseModel

# Add workspace and ai paths to sys.path
api_dir = os.path.dirname(os.path.abspath(__file__))
ai_dir = os.path.dirname(api_dir)
sys.path.append(ai_dir)

from ai.src.pipeline.rag_pipeline import AsyncQueryPipeline

app = FastAPI(title="GitLab Internal AI Chat API")

# Setup CORS to allow cross-origin requests from the browser
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

class QueryRequest(BaseModel):
    query: str

class QueryResponse(BaseModel):
    answer: str
    sources: list

pipeline = None

def init_pipeline():
    global pipeline
    if pipeline is None:
        pipeline = AsyncQueryPipeline()
    return pipeline

@app.on_event("startup")
async def startup_event():
    print("Khởi động API Server và nạp mô hình AI...")
    init_pipeline()

@app.get("/")
async def serve_ui():
    html_path = os.path.join(api_dir, "templates", "chat_ui.html")
    if os.path.exists(html_path):
        return FileResponse(html_path)
    return {"message": "Không tìm thấy file templates/chat_ui.html. Hãy đảm bảo nó nằm đúng thư mục."}

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

@app.post("/chat", response_model=QueryResponse)
async def chat_endpoint(request: QueryRequest):
    print(f"\n[API] Nhận câu hỏi: {request.query}")
    p = init_pipeline()
    result = await p.run(request.query)
    
    answer = result["answer"]
    is_exact_qa = result.get("is_exact_qa", False)
    is_greeting = is_greeting_query(request.query)
    
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
    
    # Không thêm disclaimer nếu:
    # 1. Là câu chào xã giao
    # 2. Câu hỏi trùng khớp chính xác trong bộ dữ liệu (Exact QA)
    # 3. Câu trả lời báo không có thông tin hoặc từ chối trả lời
    if not is_greeting and not is_exact_qa and not is_no_info:
        disclaimer = "\n\nLưu ý: Câu trả lời này chỉ mang tính chất tham khảo. Vui lòng liên hệ bộ phận nhân sự hoặc quản lý để xác nhận thông tin chính xác."
        answer = answer.strip() + disclaimer
    
    return QueryResponse(answer=answer.strip(), sources=result["sources"])

if __name__ == "__main__":
    print("\n" + "="*60)
    print("ĐANG CHẠY MÁY CHỦ API & GIAO DIỆN CHAT AI ")
    print("Truy cập giao diện tại: http://localhost:8000")
    print("="*60 + "\n")
    uvicorn.run(app, host="0.0.0.0", port=8000)
