"""
Script xử lý và chia nhỏ tài liệu gốc thành chunks
"""
import os
import json
from pathlib import Path
from typing import List, Dict
import re

# Cài đặt thư viện cần thiết nếu dùng PDF hoặc DOCX
# pip install pypdf2 python-docx

class DocumentProcessor:
    # Lớp xử lý tài liệu từ nhiều định dạng khác nhau
    
    def __init__(self, raw_data_dir: str, chunk_size: int = 512, overlap: int = 50):
        # Khởi tạo bộ xử lý tài liệu với cấu hình chunk
        self.raw_data_dir = raw_data_dir
        self.chunk_size = chunk_size
        self.overlap = overlap
        self.documents = []
    
    def read_markdown(self, filepath: str) -> str:
        # Đọc nội dung file Markdown
        with open(filepath, 'r', encoding='utf-8') as f:
            return f.read()
    
    def read_txt(self, filepath: str) -> str:
        # Đọc nội dung file văn bản thuần
        with open(filepath, 'r', encoding='utf-8') as f:
            return f.read()
    
    def read_pdf(self, filepath: str) -> str:
        # Đọc nội dung file PDF
        try:
            from pypdf import PdfReader
        except ImportError:
            print("Cần cài đặt: pip install pypdf")
            return ""
        
        reader = PdfReader(filepath)
        text = ""
        for page in reader.pages:
            text += page.extract_text()
        return text
    
    def read_docx(self, filepath: str) -> str:
        # Đọc nội dung file Word (.docx)
        try:
            from docx import Document
        except ImportError:
            print("Cần cài đặt: pip install python-docx")
            return ""
        
        doc = Document(filepath)
        text = "\n".join([para.text for para in doc.paragraphs])
        return text
    
    def load_all_documents(self) -> List[Dict]:
        # Tải tất cả tài liệu từ thư mục đầu vào
        documents = []
        
        for filename in os.listdir(self.raw_data_dir):
            filepath = os.path.join(self.raw_data_dir, filename)
            
            if not os.path.isfile(filepath):
                continue
            
            print(f"Dang xu ly: {filename}")
            
            # Chọn hàm xử lý dựa trên phần mở rộng file
            if filename.endswith('.md'):
                content = self.read_markdown(filepath)
            elif filename.endswith('.txt'):
                content = self.read_txt(filepath)
            elif filename.endswith('.pdf'):
                content = self.read_pdf(filepath)
            elif filename.endswith('.docx'):
                content = self.read_docx(filepath)
            else:
                print(f"Bo qua: {filename} (dinh dang khong ho tro)")
                continue
            
            if content:
                documents.append({
                    'source': filename,
                    'content': content
                })
        
        return documents
    
    def chunk_text(self, text: str, chunk_size: int = None, overlap: int = None) -> List[str]:
        # Chia nhỏ văn bản thành các đoạn (chunks)
        if chunk_size is None:
            chunk_size = self.chunk_size
        if overlap is None:
            overlap = self.overlap
        
        lines = text.split('\n')
        chunks = []
        current_chunk = ""
        
        for line in lines:
            # Kiểm tra giới hạn kích thước chunk khi thêm dòng mới
            if len(current_chunk) + len(line) + 1 > chunk_size and current_chunk:
                chunks.append(current_chunk.strip())
                # Xử lý đoạn chồng lấp (overlap)
                overlap_lines = current_chunk.split('\n')[-2:]
                current_chunk = '\n'.join(overlap_lines) + '\n' + line
            else:
                current_chunk += line + '\n'
        
        # Lưu đoạn cuối cùng
        if current_chunk.strip():
            chunks.append(current_chunk.strip())
        
        return chunks
    
    def process_and_save(self, output_file: str):
        # Chạy quy trình xử lý và lưu kết quả ra file
        print("Dang tai tai lieu...")
        documents = self.load_all_documents()
        
        print(f"Da tai {len(documents)} tai lieu")
        
        chunks_data = []
        for doc in documents:
            source = doc['source']
            content = doc['content']
            
            chunks = self.chunk_text(content)
            
            for idx, chunk in enumerate(chunks):
                chunks_data.append({
                    'source': source,
                    'chunk_id': f"{source}_{idx}",
                    'content': chunk,
                    'chunk_index': idx
                })
        
        print(f"Chia thanh {len(chunks_data)} chunks")
        
        # Tạo thư mục và lưu file kết quả
        os.makedirs(os.path.dirname(output_file), exist_ok=True)
        with open(output_file, 'w', encoding='utf-8') as f:
            for item in chunks_data:
                f.write(json.dumps(item, ensure_ascii=False) + '\n')
        
        print(f"Luu vao: {output_file}")
        return chunks_data


if __name__ == '__main__':
    # Khoi chay quy trình xu ly tai lieu
    processor = DocumentProcessor(
        raw_data_dir='./data/raw',
        chunk_size=512,
        overlap=50
    )
    
    processor.process_and_save('./company-ai-chatbot/data/processed/chunks.jsonl')