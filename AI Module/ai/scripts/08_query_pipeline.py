import os
import sys
import json
import datetime
import asyncio
import aiofiles
import uvicorn
from fastapi import FastAPI
from typing import List, Dict, Any
from pydantic import BaseModel

base_dir = os.path.dirname(os.path.abspath(__file__))
sys.path.append(base_dir)

from importlib import import_module
QdrantRetriever = import_module("05_retriever").QdrantRetriever
Reranker = import_module("06_reranker").Reranker
QwenGenerator = import_module("07_qwen_generator").QwenGenerator

app = FastAPI(title="Company Handbook Advanced Async RAG V2 API")

pipeline = None

class QueryRequest(BaseModel):
    query: str

class QueryResponse(BaseModel):
    answer: str
    sources: list

class AsyncQueryPipeline:
    def __init__(self):
        print("🚀 Initializing Advanced Async RAG Pipeline...")
        self.retriever = QdrantRetriever()
        self.reranker = Reranker()
        self.generator = QwenGenerator("Qwen/Qwen2.5-1.5B-Instruct")
        self.logs_dir = os.path.join(base_dir, "..", "logs")
        os.makedirs(self.logs_dir, exist_ok=True)

    def build_prompt(self, query: str, chunks: list) -> str:
        context_blocks = []
        for c in chunks:
            cid = c.get("chunk_id", "doc_x")
            content = c.get("content", "").strip()
            context_blocks.append(f"[{cid}]\n{content}")
            
        context_text = "\n\n".join(context_blocks)
        
        return f"""
QUY TẮC HIỆU LỆNH:
1. Bạn là Trợ lý AI của công ty. CHỈ sử dụng tài liệu CONTEXT được cung cấp dưới đây để trả lời.
2. HOÀN TOÀN TRẢ LỜI BẰNG TIẾNG VIỆT 100%. Tuyệt đối không dùng tiếng Anh trừ khi là thuật ngữ kỹ thuật.
3. Nếu CONTEXT không có dữ liệu để giải quyết câu hỏi, lập tức trả lời: "Tôi không có thông tin này trong hệ thống".
4. Đi thẳng vào trọng tâm câu trả lời, cực kỳ ngắn gọn và súc tích.

CONTEXT:
{context_text}

QUESTION:
{query}

ANSWER (chỉ bằng Tiếng Việt):
""".strip()

    async def run(self, query: str) -> Dict[str, Any]:
        print(f"      [Pipeline] Phân tích query: {query}", flush=True)
        # 1. Async Retrieve (Dense + Sparse Hybrid)
        print("      [Pipeline] Đang truy xuất dữ liệu từ Qdrant...", flush=True)
        # Bằng sức mạnh GPU, mở rộng không gian tìm kiếm
        candidates = await self.retriever.search(query, top_k=20)

        import torch
        torch.cuda.empty_cache()
        # 2. Async Rerank (CrossEncoder on thread pool)
        print(f"      [Pipeline] Đang Rerank {len(candidates)} đoạn dữ liệu bằng GPU...", flush=True)
        top_chunks = await self.reranker.rerank(query, candidates, top_k=5)

        torch.cuda.empty_cache()
        # 3. Async Generate
        if not top_chunks:
            answer = "Tôi không rõ thông tin này trong hệ thống."
        else:
            print("      [Pipeline] Đang đợi Qwen LLM sinh câu trả lời...", flush=True)
            prompt = self.build_prompt(query, top_chunks)
            answer = await self.generator.generate(prompt)
            print("      [Pipeline] Hoàn thành sinh câu trả lời!", flush=True)
        
        torch.cuda.empty_cache()

        # 4. Normalize Details
        sources = []
        for c in top_chunks:
            sources.append({
                "chunk_id": c.get("chunk_id"),
                "score": c.get("rerank_score", c.get("score")),
                "content_preview": c.get("content", "")[:200]
            })

        # 5. Async Log with aiofiles
        log_path = os.path.join(self.logs_dir, "v2_advanced_query_log.jsonl")
        entry = {
            "timestamp": datetime.datetime.now().isoformat(),
            "query": query,
            "answer": answer,
            "sources": sources
        }
        
        try:
            async with aiofiles.open(log_path, "a", encoding="utf-8") as f:
                await f.write(json.dumps(entry, ensure_ascii=False) + "\n")
        except ImportError:
            # Fallback if aiofiles missing
            with open(log_path, "a", encoding="utf-8") as f:
                f.write(json.dumps(entry, ensure_ascii=False) + "\n")

        return {
            "answer": answer,
            "sources": sources,
            "used_chunks": top_chunks
        }


def init_pipeline():
    global pipeline
    if pipeline is None:
        pipeline = AsyncQueryPipeline()
    return pipeline


@app.on_event("startup")
async def startup_event():
    init_pipeline()


@app.post("/chat", response_model=QueryResponse)
async def chat_endpoint(request: QueryRequest):
    p = init_pipeline()
    result = await p.run(request.query)
    return QueryResponse(answer=result["answer"], sources=result["sources"])


async def run_cli():
    print("\n=== ADVANCED ASYNC RAG CLI MODE ===")
    print("Nhập câu hỏi (Enter để thoát)\n")

    p = init_pipeline()

    while True:
        try:
            # Input is sync, but it's ok for CLI wrapper
            q = input(">> Question: ").strip()
            if not q:
                break
                
            res = await p.run(q)
            
            print("\n=== ANSWER ===")
            print(res["answer"])
            
            print("\n=== SOURCES ===")
            for s in res["sources"]:
                print(f"- {s['chunk_id']} | score: {round(s['score'], 5)}")
                
            print("\n====================\n")
            
        except KeyboardInterrupt:
            print("\nExit.")
            break
        except Exception as e:
            print(f"\nError: {e}")
            break


if __name__ == "__main__":
    if "--api" in sys.argv:
        print("🚀 Running API at http://localhost:8000")
        uvicorn.run(app, host="0.0.0.0", port=8000)
    else:
        asyncio.run(run_cli())