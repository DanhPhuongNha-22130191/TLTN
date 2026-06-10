import os
import sys
import asyncio
import uvicorn
from fastapi import FastAPI
from pydantic import BaseModel
from typing import Dict, Any

# Ensure workspace root is in path
script_dir = os.path.dirname(os.path.abspath(__file__))
workspace_dir = os.path.dirname(os.path.dirname(script_dir))
if workspace_dir not in sys.path:
    sys.path.append(workspace_dir)

from ai.src.pipeline.rag_pipeline import AsyncQueryPipeline

app = FastAPI(title="Company Handbook Advanced Async RAG V2 API")
pipeline = None

class QueryRequest(BaseModel):
    query: str

class QueryResponse(BaseModel):
    answer: str
    sources: list

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
        print("Running API at http://localhost:8000")
        uvicorn.run(app, host="0.0.0.0", port=8000)
    else:
        asyncio.run(run_cli())