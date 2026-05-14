import os
import sys
import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from pydantic import BaseModel

base_dir = os.path.dirname(os.path.abspath(__file__))
sys.path.append(base_dir)

# Import the existing pipeline logic
from importlib import import_module
init_pipeline = import_module("08_query_pipeline").init_pipeline

app = FastAPI(title="GitLab Internal AI Chat API")

# Setup CORS to allow the HTML file to make requests
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

@app.on_event("startup")
async def startup_event():
    print("🚀 Khởi động API Server và nạp mô hình AI...")
    init_pipeline()

@app.get("/")
async def serve_ui():
    html_path = os.path.join(base_dir, "13_chat_ui.html")
    if os.path.exists(html_path):
        return FileResponse(html_path)
    return {"message": "Không tìm thấy file 13_chat_ui.html. Hãy đảm bảo nó nằm cùng thư mục với script này."}

@app.post("/chat", response_model=QueryResponse)
async def chat_endpoint(request: QueryRequest):
    print(f"\n[API] Nhận câu hỏi: {request.query}")
    p = init_pipeline()
    result = await p.run(request.query)
    return QueryResponse(answer=result["answer"], sources=result["sources"])

if __name__ == "__main__":
    print("\n" + "="*60)
    print("🌐 ĐANG CHẠY MÁY CHỦ API & GIAO DIỆN CHAT AI 🌐")
    print("Truy cập giao diện tại: http://localhost:8000")
    print("="*60 + "\n")
    uvicorn.run(app, host="0.0.0.0", port=8000)
