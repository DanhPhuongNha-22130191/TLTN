import os
import json
import datetime
from typing import Dict, Any, List

try:
    import aiofiles
except ImportError:
    aiofiles = None

try:
    import torch
except ImportError:
    torch = None

from ai.src.config import LOGS_DIR, GENERATOR_MODEL_NAME
from ai.src.retriever.hybrid import QdrantRetriever
from ai.src.models.generator import QwenGenerator
from ai.src.models.reranker import Reranker


class AsyncQueryPipeline:
    def __init__(self, generator_model: str = GENERATOR_MODEL_NAME):
        print("Đang khởi tạo luồng xử lý Async RAG local...")

        self.retriever = QdrantRetriever()

        # Sử dụng mô hình Reranker
        self.reranker = Reranker()

        self.generator = QwenGenerator(generator_model)
        self.logs_dir = LOGS_DIR

        os.makedirs(self.logs_dir, exist_ok=True)

    def build_prompt(self, query: str, chunks: List[Dict[str, Any]]) -> str:
        context_blocks = []

        for c in chunks:
            cid = c.get("chunk_id", "doc_x")
            content = c.get("content", c.get("text", "")).strip()

            if content:
                context_blocks.append(f"[{cid}]\n{content}")

        context_text = "\n\n".join(context_blocks)

        return f"""
Bạn là trợ lý AI chuyên nghiệp của công ty. Nhiệm vụ của bạn là trả lời câu hỏi dựa trên ngữ cảnh (CONTEXT). "

CONTEXT:
{context_text}

QUESTION:
{query}

ANSWER:
""".strip()

    async def _write_log(self, log_path: str, entry: Dict[str, Any]) -> None:
        line = json.dumps(entry, ensure_ascii=False) + "\n"

        if aiofiles is not None:
            try:
                async with aiofiles.open(log_path, "a", encoding="utf-8") as f:
                    await f.write(line)
                return
            except Exception:
                pass

        with open(log_path, "a", encoding="utf-8") as f:
            f.write(line)

    def _clear_cuda_cache(self) -> None:
        if torch is not None and torch.cuda.is_available():
            torch.cuda.empty_cache()

    async def run(self, query: str) -> Dict[str, Any]:
        print(f"      [RAG Local] Phân tích câu hỏi: {query}", flush=True)

        # 1. Truy xuất từ Qdrant
        print("      [RAG Local] Đang truy xuất dữ liệu từ Qdrant...", flush=True)
        candidates = await self.retriever.search(query, top_k=20)

        self._clear_cuda_cache()

        # 1.5 Kiểm tra trùng khớp câu hỏi chuẩn (QA Exact Match)
        qa_candidates = [c for c in candidates if c.get("question") and c.get("answer")]
        if qa_candidates:
            print(f"      [RAG Local] Đang kiểm tra mức độ trùng khớp với {len(qa_candidates)} QA...", flush=True)
            best_qa = await self.reranker.check_qa_match(query, qa_candidates, threshold=0.95)
            self._clear_cuda_cache()
            
            if best_qa:
                print(f"      [RAG Local] Câu hỏi trùng khớp QA! Trả về đáp án chuẩn (score: {best_qa.get('rerank_score'):.2f}).", flush=True)
                answer = best_qa["answer"]
                sources = [{
                    "chunk_id": best_qa.get("chunk_id"),
                    "score": best_qa.get("rerank_score", best_qa.get("score")),
                    "content_preview": best_qa.get("question")
                }]
                
                log_path = os.path.join(self.logs_dir, "local_query_log.jsonl")
                entry = {
                    "timestamp": datetime.datetime.now().isoformat(),
                    "query": query,
                    "answer": answer,
                    "sources": sources,
                    "retriever_results": [
                        {
                            "chunk_id": c.get("chunk_id"),
                            "score": c.get("score"),
                        }
                        for c in candidates
                    ],
                    "is_exact_qa": True
                }
                await self._write_log(log_path, entry)
                
                return {
                    "answer": answer,
                    "sources": sources,
                    "used_chunks": [best_qa],
                    "retriever_results": candidates,
                    "is_exact_qa": True
                }

        # 2. Dùng HuggingFace reranker
        print(
            f"      [RAG Local] Đang xếp hạng lại bằng reranker từ {len(candidates)} kết quả...",
            flush=True,
        )
        top_chunks = await self.reranker.rerank(query, candidates, top_k=8)

        self._clear_cuda_cache()

        # 3. Sinh câu trả lời
        if not top_chunks:
            answer = "Tôi không có thông tin này trong hệ thống."
        else:
            print("      [RAG Local] Đang sinh câu trả lời bằng Qwen local...", flush=True)
            prompt = self.build_prompt(query, top_chunks)
            answer = await self.generator.generate(prompt)
            print("      [RAG Local] Hoàn thành sinh câu trả lời!", flush=True)

        self._clear_cuda_cache()

        # 4. Sources
        sources = [
            {
                "chunk_id": c.get("chunk_id"),
                "score": c.get("rerank_score", c.get("score")),
                "content_preview": (c.get("content") or c.get("text", ""))[:200],
            }
            for c in top_chunks
        ]

        retriever_results = [
            {
                "chunk_id": c.get("chunk_id"),
                "score": c.get("score"),
            }
            for c in candidates
        ]

        # 5. Log
        log_path = os.path.join(self.logs_dir, "local_query_log.jsonl")

        entry = {
            "timestamp": datetime.datetime.now().isoformat(),
            "query": query,
            "answer": answer,
            "sources": sources,
            "retriever_results": retriever_results,
            "is_exact_qa": False
        }

        await self._write_log(log_path, entry)

        return {
            "answer": answer,
            "sources": sources,
            "used_chunks": top_chunks,
            "retriever_results": retriever_results,
            "is_exact_qa": False
        }