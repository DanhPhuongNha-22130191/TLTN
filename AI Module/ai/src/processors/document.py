import os
import re
import json
import hashlib
from pathlib import Path
from typing import List, Dict, Optional
from dataclasses import dataclass

from transformers import AutoTokenizer
from ai.src.config import (
    TOKENIZER_MODEL_NAME, TARGET_TOKENS, MAX_TOKENS, MIN_TOKENS,
    OVERLAP_SENTENCES, PRESERVE_HEADERS, HANDBOOK_DIR, CHUNKS_PATH
)
from ai.src.processors.parser import SentenceSegmenter, MarkdownParser

@dataclass
class ChunkingConfig:
    target_tokens: int = TARGET_TOKENS
    max_tokens: int = MAX_TOKENS
    min_tokens: int = MIN_TOKENS
    overlap_sentences: int = OVERLAP_SENTENCES
    preserve_headers: bool = PRESERVE_HEADERS


class RAGDocumentProcessor:
    def __init__(self, config: Optional[ChunkingConfig] = None):
        self.config = config or ChunkingConfig()
        self.segmenter = SentenceSegmenter()
        self.parser = MarkdownParser()
        # Nạp bộ phân tách từ (tokenizer) để đếm số lượng token chính xác
        self.tokenizer = AutoTokenizer.from_pretrained(TOKENIZER_MODEL_NAME)

    def count_tokens(self, text: str) -> int:
        """Trả về số lượng token trong đoạn văn bản sử dụng tokenizer đã được cấu hình."""
        return len(self.tokenizer.encode(text, add_special_tokens=False))

    def load_file(self, path: str) -> str:
        """Đọc nội dung văn bản từ các định dạng file khác nhau (.md, .txt, .pdf, .docx)."""
        ext = Path(path).suffix.lower()
        try:
            if ext in [".md", ".txt"]:
                return Path(path).read_text(encoding="utf-8")
            elif ext == ".pdf":
                from pypdf import PdfReader
                reader = PdfReader(path)
                return "\n".join(p.extract_text() or "" for p in reader.pages)
            elif ext == ".docx":
                from docx import Document
                doc = Document(path)
                return "\n".join(p.text for p in doc.paragraphs)
        except Exception as e:
            print(f"Error loading {path}: {e}")
        return ""

    def clean_markdown(self, text: str) -> str:
        """Removes code blocks, HTML tags, and excessive spaces from markdown text."""
        text = re.sub(r'```.*?```', ' ', text, flags=re.DOTALL)
        text = re.sub(r'!\[.*?\]\(.*?\)', ' ', text)
        text = re.sub(r'\[([^\]]+)\]\([^\)]+\)', r'\1', text)
        text = re.sub(r'<[^>]+>', ' ', text)
        text = re.sub(r'\n{2,}', '\n', text)
        text = re.sub(r'[ \t]+', ' ', text)
        return text.strip()

    def split_long_sentence(self, sentence: str) -> List[str]:
        """Splits a single long sentence into word groups of around 80 words."""
        words = sentence.split()
        chunks = []
        for i in range(0, len(words), 80):
            chunks.append(" ".join(words[i:i + 80]))
        return chunks

    def chunk_section(self, header: str, content: str) -> List[str]:
        """Segments a section's text content into cohesive chunks based on token limits."""
        sentences = self.segmenter.split(content)
        chunks = []
        current = []
        token_count = 0

        for sentence in sentences:
            tokens = self.count_tokens(sentence)

            if tokens > self.config.target_tokens:
                sub_chunks = self.split_long_sentence(sentence)
                chunks.extend(sub_chunks)
                continue

            if token_count + tokens > self.config.target_tokens and current:
                chunk_text = " ".join(current)
                if self.config.preserve_headers and header:
                    chunk_text = f"{header}\n{chunk_text}"
                if self._valid(chunk_text):
                    chunks.append(chunk_text)

                overlap = current[-self.config.overlap_sentences:]
                current = overlap.copy()
                token_count = sum(self.count_tokens(s) for s in current)

            current.append(sentence)
            token_count += tokens

        if current:
            chunk_text = " ".join(current)
            if self.config.preserve_headers and header:
                chunk_text = f"{header}\n{chunk_text}"
            if self._valid(chunk_text):
                chunks.append(chunk_text)

        return chunks

    def _valid(self, chunk: str) -> bool:
        t = self.count_tokens(chunk)
        return self.config.min_tokens <= t <= self.config.max_tokens

    def process_document(self, doc: Dict) -> List[Dict]:
        """Processes a single raw document structure into metadata-enriched chunks."""
        raw_text = doc["content"]
        results = []

        # Tự động phát hiện xem đây có phải là bộ câu hỏi đáp án sẵn hay không
        is_qa_dataset = bool(re.search(r"\bCâu\s*\d+:\s*", raw_text, re.IGNORECASE) and 
                             re.search(r"\bĐáp\s*án:\s*", raw_text, re.IGNORECASE))

        if is_qa_dataset:
            # Tìm các khối Câu hỏi & Đáp án (giữ nguyên từng cặp QA làm 1 chunk độc lập)
            pattern = re.compile(
                r"((?:Câu\s*\d+:\s*)(.*?)\n\s*Đáp\s*án:\s*(.*?))(?=\n\s*Câu\s*\d+:|\Z)",
                re.DOTALL | re.IGNORECASE
            )
            matches = pattern.findall(raw_text)

            for full_match, question, answer in matches:
                chunk_text = full_match.strip()
                if not chunk_text:
                    continue

                # Tạo UUID định danh duy nhất cho chunk dựa trên hash của nội dung
                uuid_hash = hashlib.md5(chunk_text.encode('utf-8')).hexdigest()
                chunk_id = f"{uuid_hash[:8]}-{uuid_hash[8:12]}-{uuid_hash[12:16]}-{uuid_hash[16:20]}-{uuid_hash[20:]}"

                results.append({
                    "chunk_id": chunk_id,
                    "text": chunk_text,
                    "metadata": {
                        "source": doc["source"],
                        "path": doc["path"],
                        "section": "QA Pair"
                    },
                    "tokens": self.count_tokens(chunk_text)
                })

            if results:
                print(f"Parsed {len(results)} QA pairs from QA dataset: {doc['source']}")
                return results

        # Fallback cho tài liệu thông thường
        text = self.clean_markdown(raw_text)
        sections = self.parser.extract_sections(text)
        for sec in sections:
            header = sec["header"]
            content = sec["content"]
            chunks = self.chunk_section(header, content)

            for chunk in chunks:
                # Deterministic UUID generation based on the text hash
                uuid_hash = hashlib.md5(chunk.encode('utf-8')).hexdigest()
                chunk_id = f"{uuid_hash[:8]}-{uuid_hash[8:12]}-{uuid_hash[12:16]}-{uuid_hash[16:20]}-{uuid_hash[20:]}"

                results.append({
                    "chunk_id": chunk_id,
                    "text": chunk,
                    "metadata": {
                        "source": doc["source"],
                        "path": doc["path"],
                        "section": header
                    },
                    "tokens": self.count_tokens(chunk)
                })

        return results

    def load_documents(self, base_path: str) -> List[Dict]:
        """Loads all supported documents recursively from the given folder path."""
        docs = []
        if not os.path.exists(base_path):
            print(f"️ Warning: Dataset path '{base_path}' not found.")
            return docs

        for root, _, files in os.walk(base_path):
            for f in files:
                path = os.path.join(root, f)
                ext = Path(f).suffix.lower()

                if ext not in {".md", ".txt", ".pdf", ".docx"}:
                    continue

                content = self.load_file(path)
                if content.strip():
                    docs.append({
                        "source": f,
                        "path": os.path.relpath(path, base_path),
                        "content": content
                    })

        print(f"Loaded: {len(docs)} docs")
        return docs

    def run(self, input_dir: str = HANDBOOK_DIR, output_path: str = CHUNKS_PATH) -> List[Dict]:
        """Chạy toàn bộ luồng phân tích/cắt nhỏ văn bản (parsing/chunking) cho tất cả các file trong input_dir và lưu vào output_path."""
        docs = self.load_documents(input_dir)
        all_chunks = []
        seen = set()

        for doc in docs:
            chunks = self.process_document(doc)
            print(f"{doc['source']} → {len(chunks)} chunks")

            for c in chunks:
                if c["text"] in seen:
                    continue
                seen.add(c["text"])
                all_chunks.append(c)

        Path(output_path).parent.mkdir(parents=True, exist_ok=True)
        with open(output_path, "w", encoding="utf-8") as f:
            for item in all_chunks:
                f.write(json.dumps(item, ensure_ascii=False) + "\n")

        print(f"\nTotal chunks saved: {len(all_chunks)}")
        return all_chunks
