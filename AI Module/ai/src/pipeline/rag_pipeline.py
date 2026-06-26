import os
import json
import datetime
import re
from typing import Dict, Any, List

try:
    import aiofiles
except ImportError:
    aiofiles = None

try:
    import torch
except ImportError:
    torch = None

from ai.src.config import LOGS_DIR, GENERATOR_MODEL_NAME, QA_DIR
from ai.src.retriever.hybrid import QdrantRetriever
from ai.src.models.generator import QwenGenerator
from ai.src.models.reranker import Reranker


class AsyncQueryPipeline:
    def __init__(self, generator_model: str = GENERATOR_MODEL_NAME):
        self.retriever = QdrantRetriever()
        self.reranker = Reranker()
        self.generator = QwenGenerator(generator_model)
        self.qa_lookup = self._load_qa_lookup()

        self.logs_dir = LOGS_DIR
        os.makedirs(self.logs_dir, exist_ok=True)

    def _normalize_question(self, value: str) -> str:
        value = (value or "").strip().lower()
        value = re.sub(r"\s+", " ", value)
        return value.rstrip("?.!。？！ ")

    def _load_qa_lookup(self) -> Dict[str, Dict[str, str]]:
        qa_path = os.path.join(QA_DIR, "qa_dataset.md")
        if not os.path.exists(qa_path):
            return {}

        with open(qa_path, "r", encoding="utf-8") as file:
            raw_text = file.read()

        pattern = re.compile(
            r"(?:Câu\s*(\d+):\s*)(.*?)\n\s*Đáp\s*án:\s*(.*?)(?=\n\s*Câu\s*\d+:|\Z)",
            re.DOTALL | re.IGNORECASE
        )
        lookup = {}
        for question_number, question, answer in pattern.findall(raw_text):
            normalized = self._normalize_question(question)
            if normalized:
                lookup[normalized] = {
                    "chunk_id": f"qa_dataset_cau_{question_number}",
                    "question": question.strip(),
                    "answer": answer.strip(),
                    "source": "qa_dataset.md"
                }
        return lookup

    def build_prompt(self, query: str, chunks: List[Dict[str, Any]]) -> str:
        context_blocks = []

        for chunk in chunks:
            chunk_id = chunk.get("chunk_id", "doc_x")
            content = chunk.get("content", chunk.get("text", "")).strip()

            if content:
                context_blocks.append(f"[{chunk_id}]\n{content}")

        context_text = "\n\n".join(context_blocks)

        return f"""
Bạn là trợ lý AI chuyên nghiệp của công ty.
Nhiệm vụ của bạn là trả lời câu hỏi dựa trên ngữ cảnh CONTEXT.

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
                async with aiofiles.open(log_path, "a", encoding="utf-8") as file:
                    await file.write(line)
                return
            except Exception:
                pass

        with open(log_path, "a", encoding="utf-8") as file:
            file.write(line)

    def _clear_cuda_cache(self) -> None:
        if torch is not None and torch.cuda.is_available():
            torch.cuda.empty_cache()

    async def run(self, query: str) -> Dict[str, Any]:
        exact_qa = self.qa_lookup.get(self._normalize_question(query))
        if exact_qa:
            return await self._return_exact_qa_result(
                query=query,
                answer=exact_qa["answer"],
                best_qa=exact_qa,
                candidates=[]
            )

        candidates = await self.retriever.search(query, top_k=20)
        self._clear_cuda_cache()

        qa_candidates = [
            candidate
            for candidate in candidates
            if candidate.get("question") and candidate.get("answer")
        ]

        if qa_candidates:
            best_qa = await self.reranker.check_qa_match(
                query,
                qa_candidates,
                threshold=0.95
            )

            self._clear_cuda_cache()

            if best_qa:
                return await self._return_exact_qa_result(
                    query=query,
                    answer=best_qa["answer"],
                    best_qa=best_qa,
                    candidates=candidates
                )

        top_chunks = await self.reranker.rerank(query, candidates, top_k=8)
        self._clear_cuda_cache()

        if not top_chunks:
            answer = "Tôi không có thông tin này trong hệ thống."
        else:
            prompt = self.build_prompt(query, top_chunks)
            answer = await self.generator.generate(prompt)

        self._clear_cuda_cache()

        sources = self._build_sources(top_chunks)
        retriever_results = self._build_retriever_results(candidates)

        entry = {
            "timestamp": datetime.datetime.now().isoformat(),
            "query": query,
            "answer": answer,
            "sources": sources,
            "retriever_results": retriever_results,
            "is_exact_qa": False
        }

        log_path = os.path.join(self.logs_dir, "local_query_log.jsonl")
        await self._write_log(log_path, entry)

        return {
            "answer": answer,
            "sources": sources,
            "used_chunks": top_chunks,
            "retriever_results": retriever_results,
            "is_exact_qa": False
        }

    async def _return_exact_qa_result(
        self,
        query: str,
        answer: str,
        best_qa: Dict[str, Any],
        candidates: List[Dict[str, Any]]
    ) -> Dict[str, Any]:

        sources = [{
            "chunk_id": best_qa.get("chunk_id"),
            "score": best_qa.get("rerank_score", best_qa.get("score")),
            "content_preview": best_qa.get("question")
        }]

        retriever_results = self._build_retriever_results(candidates)

        entry = {
            "timestamp": datetime.datetime.now().isoformat(),
            "query": query,
            "answer": answer,
            "sources": sources,
            "retriever_results": retriever_results,
            "is_exact_qa": True
        }

        log_path = os.path.join(self.logs_dir, "local_query_log.jsonl")
        await self._write_log(log_path, entry)

        return {
            "answer": answer,
            "sources": sources,
            "used_chunks": [best_qa],
            "retriever_results": candidates,
            "is_exact_qa": True
        }

    def _build_sources(self, chunks: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        return [
            {
                "chunk_id": chunk.get("chunk_id"),
                "score": chunk.get("rerank_score", chunk.get("score")),
                "content_preview": (
                    chunk.get("content") or chunk.get("text", "")
                )[:200]
            }
            for chunk in chunks
        ]

    def _build_retriever_results(
        self,
        candidates: List[Dict[str, Any]]
    ) -> List[Dict[str, Any]]:

        return [
            {
                "chunk_id": candidate.get("chunk_id"),
                "score": candidate.get("score")
            }
            for candidate in candidates
        ]
