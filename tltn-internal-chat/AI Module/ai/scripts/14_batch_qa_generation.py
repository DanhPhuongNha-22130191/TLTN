import os
import sys
import json
import asyncio
import re
from datetime import datetime

base_dir = os.path.dirname(os.path.abspath(__file__))
sys.path.append(base_dir)

from importlib import import_module
init_pipeline = import_module("08_query_pipeline").init_pipeline

def parse_qa_dataset(filepath: str) -> list:
    """Hàm parse bộ câu hỏi từ file .md"""
    questions = []
    with open(filepath, "r", encoding="utf-8") as f:
        content = f.read()

    # Tìm các mẫu **Q1:** hoặc Q: hoặc **Q:**
    matches = re.findall(
        r"(?:\*\*Q\d+:\*\*|\*\*Q:\*\*|Q:|Question:)\s*(.*?)(?=\n\*\*A:|\nA:|\n\*\*Q\d+:|\nQ:|\nQuestion:|\Z)",
        content,
        re.DOTALL
    )

    for q in matches:
        if q.strip():
            questions.append(q.strip())
    
    return questions

async def main():
    print("🚀 Bắt đầu quá trình Batch QA Generation bằng RAG Pipeline...")
    
    input_path = os.path.join(base_dir, "..", "data", "qa", "qa_dataset.md")
    output_path = os.path.join(base_dir, "..", "data", "qa", "rag_generated_answers.md")

    if not os.path.exists(input_path):
        print(f"❌ Không tìm thấy file nguồn: {input_path}")
        return

    # 1. Parse câu hỏi
    questions = parse_qa_dataset(input_path)
    print(f"📦 Đã tìm thấy {len(questions)} câu hỏi trong file gốc.")
    
    # 2. Khởi tạo Pipeline
    pipeline = init_pipeline()
    
    qa_results = []
    
    print(f"\n⚙️ Đang sử dụng RAG Pipeline để sinh câu trả lời cho {len(questions)} câu hỏi...\n")

    for i, q in enumerate(questions, 1):
        try:
            print(f"[{i}/{len(questions)}] Đang xử lý: {q[:50]}...")
            
            # Chạy pipeline RAG (Retrieval + Generation)
            result = await pipeline.run(q)
            answer = result.get("answer", "Tôi không có thông tin này trong hệ thống.")
            used_chunks = result.get("sources", [])
            
            qa_results.append({
                "question": q,
                "answer": answer,
                "sources": [c.get("chunk_id") for c in used_chunks]
            })
            
            # Giải phóng bộ nhớ GPU sau mỗi câu (nếu cần)
            import torch
            torch.cuda.empty_cache()
            
        except Exception as e:
            print(f"⚠️ Lỗi tại câu {i}: {e}")

    # 3. Ghi xuất ra file .md
    with open(output_path, "w", encoding="utf-8") as f:
        f.write(f"# Bộ câu hỏi và đáp án sinh bởi AI (RAG Pipeline)\n\n")
        f.write(f"> Ngày tạo: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
        f.write(f"> Nguồn câu hỏi: {os.path.basename(input_path)}\n")
        f.write(f"> Tổng số: {len(qa_results)} câu\n\n")
        f.write("---\n\n")
        
        for i, res in enumerate(qa_results, 1):
            f.write(f"**Q{i}:** {res['question']}\n\n")
            f.write(f"**A:** {res['answer']}\n\n")
            if res['sources']:
                f.write(f"*Dựa trên các ChunkID: {', '.join(filter(None, res['sources'][:3]))}*\n\n")
            f.write("---\n\n")

    print(f"\n✅ HOÀN THÀNH! Kết quả đã được lưu tại: '{output_path}'")

if __name__ == "__main__":
    asyncio.run(main())
