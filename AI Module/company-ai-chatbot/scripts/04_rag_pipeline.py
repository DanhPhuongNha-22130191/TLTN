"""
RAG Pipeline: Uu tien QA + Lay ngu canh nghiem ngat + Chong ao giac
"""

import json
import torch
from typing import List, Dict
from transformers import AutoTokenizer, AutoModelForCausalLM
from sentence_transformers import SentenceTransformer
import chromadb
import logging
import os
import re

# Cau hinh ghi log cho he thong
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class CompanyAIChatbot:
    # Lop chinh dieu khien chatbot AI cua cong ty

    def __init__(
        self,
        model_path: str,
        embeddings_model: str = "intfloat/multilingual-e5-base",
        chromadb_path: str = "./chromadb",
        collection_name: str = "company_knowledge"
    ):
        # Khoi tao chatbot voi mo hinh LLM, embedding va co so du lieu vector
        logger.info("Khoi tao chatbot...")

        self.tokenizer = AutoTokenizer.from_pretrained(model_path)
        self.model = AutoModelForCausalLM.from_pretrained(
            model_path,
            dtype=torch.float32,
            device_map={"": "cpu"}
        )

        self.embedder = SentenceTransformer(embeddings_model)

        self.client = chromadb.PersistentClient(path=chromadb_path)
        self.collection = self.client.get_or_create_collection(
            name=collection_name,
            metadata={"hnsw:space": "cosine"}
        )

        logger.info("Ready")

    def load_qa_from_md(self, file_path: str) -> List[Dict]:
        # Tai cac cap cau hoi-tra loi tu file Markdown
        if not os.path.exists(file_path):
            return []

        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()

        qa_pairs = []
        pattern = r"\d+\.\s+\*\*(.*?)\*\*\n\s+-\s+(.*?)(?=\n\n\d+\.\s+\*\*|\Z)"
        matches = re.findall(pattern, content, re.DOTALL)

        for q, a in matches:
            qa_pairs.append({
                "question": q.strip(),
                "answer": a.strip()
            })

        logger.info(f"Da tai {len(qa_pairs)} QA pairs")
        return qa_pairs

    def build_vector_db(self, chunks_file: str, qa_file: str):
        # Xay dung co so du lieu vector tu file chunks va file QA
        logger.info("Xay dung vector DB...")

        qa_data = self.load_qa_from_md(qa_file)

        # Danh chi muc cho du lieu QA (uu tien cao)
        if qa_data:
            logger.info("Indexing QA...")

            questions = [q["question"] for q in qa_data]

            embeddings = self.embedder.encode(
                ["query: " + q for q in questions],
                normalize_embeddings=True
            ).tolist()

            self.collection.upsert(
                ids=[f"qa_{i}" for i in range(len(qa_data))],
                embeddings=embeddings,
                documents=questions,
                metadatas=[{
                    "type": "qa",
                    "answer": qa_data[i]["answer"]
                } for i in range(len(qa_data))]
            )

        # Danh chi muc cho du lieu tai lieu
        if os.path.exists(chunks_file):
            logger.info("Indexing documents...")

            chunks = []
            with open(chunks_file, 'r', encoding='utf-8') as f:
                for line in f:
                    chunks.append(json.loads(line))

            texts = [c["content"] for c in chunks]

            embeddings = self.embedder.encode(
                ["passage: " + t for t in texts],
                normalize_embeddings=True
            ).tolist()

            self.collection.upsert(
                ids=[f"doc_{c['chunk_id']}" for c in chunks],
                embeddings=embeddings,
                documents=texts,
                metadatas=[{"type": "doc"} for _ in chunks]
            )

        logger.info("Vector DB ready")

    def retrieve_context(self, query: str) -> List[Dict]:
        # Truy xuat ngu canh phu hop tu vector DB (uu tien QA truoc)
        query_emb = self.embedder.encode(
            ["query: " + query],
            normalize_embeddings=True
        )[0].tolist()

        results = self.collection.query(
            query_embeddings=[query_emb],
            n_results=8,
            include=["documents", "metadatas", "distances"]
        )

        qa_contexts = []
        doc_contexts = []

        for doc, meta, dist in zip(
            results["documents"][0],
            results["metadatas"][0],
            results["distances"][0]
        ):
            score = 1 - dist

            if meta["type"] == "qa" and score > 0.6:
                qa_contexts.append({
                    "content": f"[CÂU HỎI]: {doc}\n[CÂU TRẢ LỜI]: {meta['answer']}",
                    "score": score
                })

            elif meta["type"] == "doc" and score > 0.4:
                doc_contexts.append({
                    "content": doc,
                    "score": score
                })

        # Tra ve ca QA va Doc contexts de LLM co them thong tin
        all_contexts = sorted(qa_contexts + doc_contexts, key=lambda x: x["score"], reverse=True)
        return all_contexts[:5]

    def generate_answer(self, query: str, contexts: List[Dict]) -> str:
        # Sinh cau tra loi dua tren ngu canh da truy xuat
        if not contexts:
            return "Xin lỗi, tôi không tìm thấy thông tin trong tài liệu nội bộ."

        context = "\n".join([c["content"] for c in contexts])

        prompt = f"""
Bạn là trợ lý nội bộ thông minh và trung thực.

QUY TẮC:
1. TRẢ LỜI NGẮN GỌN, TRỰC TIẾP dựa vào THÔNG TIN cung cấp.
2. Nếu không thấy thông tin trong tài liệu, hãy đáp: "Xin lỗi, tôi không tìm thấy thông tin".
3. KHÔNG được tự ý suy diễn hoặc thêm bớt thông tin ngoài tài liệu.

THÔNG TIN:
{context}

CÂU HỎI: {query}
TRẢ LỜI:
"""

        inputs = self.tokenizer(prompt, return_tensors="pt").to(self.model.device)

        with torch.no_grad():
            outputs = self.model.generate(
                **inputs,
                max_new_tokens=80,
                do_sample=False,
                repetition_penalty=1.1,
                eos_token_id=self.tokenizer.eos_token_id
            )

        # Chi lay cac token moi duoc sinh ra (loai bo prompt)
        new_tokens = outputs[0][inputs['input_ids'].shape[-1]:]
        response = self.tokenizer.decode(new_tokens, skip_special_tokens=True).strip()

        # Hau xu ly ket qua (lay phan TRẢ LỜI neu mo hinh co sinh ra tieu de)
        answer = response
        if "TRẢ LỜI" in response.upper():
            # Tim vi tri cua chuoi "TRẢ LỜI" bat ke hoa thuong
            match = re.search(r"TRẢ LỜI", response, re.IGNORECASE)
            if match:
                answer = response[match.end():].strip(": \n")

        return answer.split("CÂU HỎI")[0].strip()

    def chat(self, query: str):
        # Ham giao tiep chinh de lay cau tra loi cho cau hoi nguoi dung
        contexts = self.retrieve_context(query)
        answer = self.generate_answer(query, contexts)

        return answer


if __name__ == "__main__":
    # Khoi chay chatbot va thuc hien truy van thu nghiem
    BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

    MODEL_PATH = os.path.join(BASE_DIR, "models", "qwen2-1.5b-lora")
    CHROMA_PATH = os.path.join(BASE_DIR, "chromadb")
    CHUNKS_FILE = os.path.join(BASE_DIR, "data", "processed", "chunks.jsonl")
    QA_FILE = os.path.join(BASE_DIR, "data", "cau_hoi.md")

    bot = CompanyAIChatbot(
        model_path=MODEL_PATH,
        chromadb_path=CHROMA_PATH
    )

    bot.build_vector_db(CHUNKS_FILE, QA_FILE)

    questions = [
        "Công ty có hỗ trợ bảo hiểm không?",
        "Ngày trả lương là khi nào?",
        "Hack Week là gì?",
        "Có hỗ trợ ăn trưa không?"
    ]

    for q in questions:
        print("\nCau hoi:", q)
        print("Tra loi:", bot.chat(q))