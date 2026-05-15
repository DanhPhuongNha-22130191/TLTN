import asyncio
from typing import Dict, Any
import sys
import os
import importlib

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
query_pipeline = importlib.import_module("08_query_pipeline")
AsyncQueryPipeline = query_pipeline.AsyncQueryPipeline

async def main():
    pipeline = AsyncQueryPipeline()
    query = "Tại sao thời gian nghỉ của tính năng Deel không đồng bộ hóa với GitLab.com?"
    result = await pipeline.run(query)
    print("--------------------------------------------------")
    print(f"Câu hỏi: {query}")
    print(f"Trả lời: {result['answer']}")
    print("--------------------------------------------------")

if __name__ == "__main__":
    asyncio.run(main())
