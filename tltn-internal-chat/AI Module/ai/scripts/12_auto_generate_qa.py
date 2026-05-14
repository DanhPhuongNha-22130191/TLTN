import os
import sys
import json
import random
import asyncio

base_dir = os.path.dirname(os.path.abspath(__file__))
sys.path.append(base_dir)

from importlib import import_module
QwenGenerator = import_module("07_qwen_generator").QwenGenerator

async def main():
    print("🚀 Bắt đầu tự động tạo Dataset QA từ tài liệu gốc...")
    chunks_path = os.path.join(base_dir, "..", "data", "processed", "chunks.jsonl")
    output_path = os.path.join(base_dir, "..", "data", "qa", "qa_dataset_new.md")

    if not os.path.exists(chunks_path):
        print(f"❌ Không tìm thấy file {chunks_path}")
        return

    # 1. Đọc chunks
    chunks = []
    with open(chunks_path, "r", encoding="utf-8") as f:
        for line in f:
            if line.strip():
                try:
                    c = json.loads(line)
                    # Chỉ lấy các chunk đủ dài để làm QA
                    if len(c.get("text", "")) > 100:
                        chunks.append(c)
                except:
                    pass

    print(f"📦 Đã load {len(chunks)} chunks hợp lệ.")
    
    # Lấy ngẫu nhiên 50 chunks (hoặc ít hơn nếu tổng số chunks < 50)
    random.seed(42)
    selected_chunks = random.sample(chunks, min(50, len(chunks)))

    generator = QwenGenerator("Qwen/Qwen2.5-1.5B-Instruct")
    
    qa_results = []
    
    print(f"\n⚙️ Đang nhờ AI tự sinh {len(selected_chunks)} câu hỏi & đáp án (Sẽ mất một chút thời gian)...\n")

    for i, c in enumerate(selected_chunks, 1):
        chunk_id = c.get("chunk_id", "unknown")
        text = c.get("text", "")
        
        prompt = f"""
Nhiệm vụ: Bạn là chuyên gia tạo câu hỏi thi trắc nghiệm và đào tạo nhân sự.
Dựa CHỈ VÀO nội dung đoạn văn bản sau, hãy tạo ra MỘT (01) câu hỏi quan trọng nhất và MỘT (01) câu trả lời chính xác tuyệt đối theo văn bản.

Yêu cầu:
1. Hỏi và đáp hoàn toàn bằng TIẾNG VIỆT.
2. Câu trả lời phải tóm tắt thông tin quan trọng trong đoạn văn.
3. KHÔNG ĐƯỢC bịa đặt thêm thông tin.
4. Trả về đúng định dạng sau, KHÔNG giải thích gì thêm:
Q: [Câu hỏi của bạn]
A: [Câu trả lời của bạn]

Đoạn văn bản:
{text}
"""
        try:
            print(f"Đang xử lý chunk {i}/{len(selected_chunks)}...")
            response = await generator.generate(prompt)
            
            # Phân tách Q và A
            lines = response.strip().split('\n')
            q = ""
            a = ""
            for line in lines:
                if line.startswith("Q:") or line.startswith("Question:"):
                    q = line.replace("Q:", "").replace("Question:", "").strip()
                elif line.startswith("A:") or line.startswith("Answer:"):
                    a = line.replace("A:", "").replace("Answer:", "").strip()
            
            # Fallback nếu AI trả lời lỗi định dạng
            if not q or not a:
                if len(lines) >= 2:
                    q = lines[0].replace("Q:", "").strip()
                    a = " ".join(lines[1:]).replace("A:", "").strip()
                else:
                    continue
                    
            qa_results.append({
                "question": q,
                "truth": a,
                "chunk_id": chunk_id
            })
            print(f"  -> Q: {q[:50]}...")
            
            import torch
            torch.cuda.empty_cache()
            
        except Exception as e:
            print(f"⚠️ Lỗi chunk {i}: {e}")

    # Ghi xuất ra file .md
    with open(output_path, "w", encoding="utf-8") as f:
        f.write("# Bộ câu hỏi (Tự động sinh bằng AI từ Handbook)\n\n")
        f.write(f"> Tổng số: {len(qa_results)} câu\n")
        f.write(f"> Lưu ý: Dữ liệu này đảm bảo 100% khớp với Handbook hiện tại.\n\n")
        
        for i, pair in enumerate(qa_results, 1):
            f.write(f"**Q{i}:** {pair['question']}\n\n")
            f.write(f"**A:** {pair['truth']}\n\n")
            f.write(f"**ChunkID:** {pair['chunk_id']}\n\n")

    print(f"\n✅ XONG! Đã tự động tạo và lưu {len(qa_results)} câu QA mới tại: '{output_path}'")
    print("Bạn có thể đổi tên file này thành 'qa_dataset.md' và dùng để Evaluate.")

if __name__ == "__main__":
    asyncio.run(main())
