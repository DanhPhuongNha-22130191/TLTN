"""
Advanced RAG Document Processor (Markdown-Aware & Deterministic)
"""

import os
import re
import json
import hashlib
from pathlib import Path
from typing import List, Dict
from dataclasses import dataclass

from underthesea import sent_tokenize
from transformers import AutoTokenizer

# =========================
# CONFIG
# =========================
@dataclass
class ChunkingConfig:
    target_tokens: int = 220
    max_tokens: int = 350
    min_tokens: int = 30
    overlap_sentences: int = 1
    preserve_headers: bool = True

# =========================
# SENTENCE SEGMENTER
# =========================
class SentenceSegmenter:
    def split(self, text: str) -> List[str]:
        return [s.strip() for s in sent_tokenize(text) if s.strip()]

# =========================
# MARKDOWN PARSER
# =========================
class MarkdownParser:
    HEADER_PATTERN = re.compile(r'^(#{1,6})\s+(.*)', re.MULTILINE)

    def extract_sections(self, text: str) -> List[Dict]:
        sections = []
        matches = list(self.HEADER_PATTERN.finditer(text))

        if not matches:
            return [{"header": "", "content": text}]

        for i, match in enumerate(matches):
            level = len(match.group(1))
            title = match.group(2).strip()

            start = match.end()
            end = matches[i + 1].start() if i + 1 < len(matches) else len(text)

            content = text[start:end].strip()
            sections.append({
                "level": level,
                "header": title,
                "content": content
            })

        return sections

# =========================
# DOCUMENT PROCESSOR
# =========================
class RAGDocumentProcessor:
    def __init__(self, config: ChunkingConfig = None):
        self.config = config or ChunkingConfig()
        self.segmenter = SentenceSegmenter()
        self.parser = MarkdownParser()
        self.tokenizer = AutoTokenizer.from_pretrained("keepitreal/vietnamese-sbert")

    def count_tokens(self, text: str) -> int:
        return len(self.tokenizer.encode(text, add_special_tokens=False))

    def load_file(self, path: str) -> str:
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
            print(f"❌ Error loading {path}: {e}")
        return ""

    def clean_markdown(self, text: str) -> str:
        text = re.sub(r'```.*?```', ' ', text, flags=re.DOTALL)
        text = re.sub(r'!\[.*?\]\(.*?\)', ' ', text)
        text = re.sub(r'\[([^\]]+)\]\([^\)]+\)', r'\1', text)
        text = re.sub(r'<[^>]+>', ' ', text)
        text = re.sub(r'\n{2,}', '\n', text)
        text = re.sub(r'[ \t]+', ' ', text)
        return text.strip()

    def split_long_sentence(self, sentence: str) -> List[str]:
        words = sentence.split()
        chunks = []
        for i in range(0, len(words), 80):
            chunks.append(" ".join(words[i:i + 80]))
        return chunks

    def chunk_section(self, header: str, content: str) -> List[str]:
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
        text = self.clean_markdown(doc["content"])
        sections = self.parser.extract_sections(text)
        results = []

        for sec_id, sec in enumerate(sections):
            header = sec["header"]
            content = sec["content"]
            chunks = self.chunk_section(header, content)

            for i, chunk in enumerate(chunks):
                # Deterministic UUID generation
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
        docs = []
        if not os.path.exists(base_path):
            print(f"⚠️ Warning: Dataset path '{base_path}' not found.")
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

        print(f"📚 Loaded: {len(docs)} docs")
        return docs

    def run(self, input_dir: str, output_path: str):
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

        print(f"\n✅ Total chunks saved: {len(all_chunks)}")
        return all_chunks

if __name__ == "__main__":
    base_dir = os.path.dirname(os.path.abspath(__file__))
    input_dir = os.path.join(base_dir, "..", "data", "handbook")
    output_path = os.path.join(base_dir, "..", "data", "processed", "chunks.jsonl")

    processor = RAGDocumentProcessor()
    data = processor.run(input_dir=input_dir, output_path=output_path)

    if data:
        tokens = [d["tokens"] for d in data]
        print("\n📊 Stats")
        print("Min tokens:", min(tokens))
        print("Max tokens:", max(tokens))
        print(f"Avg tokens: {sum(tokens) / len(tokens):.1f}")