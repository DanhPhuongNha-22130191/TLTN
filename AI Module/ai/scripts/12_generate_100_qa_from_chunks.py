import os
import sys
import json
import random
import asyncio
from pathlib import Path

# Đảm bảo import được module từ thư mục root
script_dir = os.path.dirname(os.path.abspath(__file__))
workspace_dir = os.path.dirname(os.path.dirname(script_dir))
if workspace_dir not in sys.path:
    sys.path.append(workspace_dir)

from ai.src.models.generator import QwenGenerator
from ai.src.config import CHUNKS_PATH, QA_DIR, GENERATOR_MODEL_NAME

async def main():
    print(f"Đang đọc dữ liệu ngữ cảnh từ chunks.jsonl...")
    chunks = []
    
    if not os.path.exists(CHUNKS_PATH):
        print(f"Không tìm thấy file {CHUNKS_PATH}!")
        return
        
    with open(CHUNKS_PATH, "r", encoding="utf-8") as f:
        for line in f:
            if line.strip():
                chunks.append(json.loads(line))
                
    if not chunks:
        print("File chunks rỗng!")
        return
        
    print(f"Tổng số chunks đã nạp: {len(chunks)}")
    print(f"Đang dùng Qwen local qua Ollama: {GENERATOR_MODEL_NAME}\n")
    
    # LỌC CHUNKS CHẤT LƯỢNG: Chỉ lấy các chunk có chứa keyword về nhân sự, lương, nghỉ phép, quy định
    keywords = [
        "lương", "thưởng", "nghỉ", "phép", "ốm", "bệnh", "thai sản", "chính sách", 
        "quy định", "phụ cấp", "trợ cấp", "bảo hiểm", "hợp đồng", "tuyển dụng", 
        "nhân sự", "đãi ngộ", "lợi ích", "bồi thường"
    ]
    
    valid_chunks = []
    for c in chunks:
        text = c.get("text", c.get("content", "")).lower()
        if len(text) > 50 and any(kw in text for kw in keywords):
            valid_chunks.append(c)
            
    print(f"Số lượng chunks liên quan đến Nhân sự/Chính sách: {len(valid_chunks)}")
    if not valid_chunks:
        valid_chunks = chunks # Fallback
        
    # Chọn ngẫu nhiên chunks
    random.seed(123)
    if len(valid_chunks) >= 150:
        selected_chunks = random.sample(valid_chunks, 150) # Lấy dư ra để phòng hờ bị SKIP
    else:
        selected_chunks = random.choices(valid_chunks, k=150)
        
    generator = QwenGenerator()
    
    print("BẮT ĐẦU SINH CÂU HỎI BẰNG OLLAMA (Qwen Local)...")
    out_path = os.path.join(QA_DIR, "qa_dataset_100_generated.md")
    
    with open(out_path, "w", encoding="utf-8") as f:
        pass # Reset file
        
    success_count = 0
    
    for i, chunk in enumerate(selected_chunks, 1):
        if success_count >= 100:
            break
            
        text = chunk.get("text", chunk.get("content", ""))
        
        prompt = f"""Đọc kỹ tài liệu nội bộ sau đây và đóng vai một nhân viên mới đang hỏi phòng nhân sự.
Yêu cầu BẮT BUỘC:
1. CÂU HỎI PHẢI ĐƯỢC RÚT RA TỪ TÀI LIỆU NÀY VÀ ĐÁP ÁN PHẢI NẰM HOÀN TOÀN TRONG TÀI LIỆU. Không tự bịa thông tin.
2. Tập trung vào: Lương, Thưởng, Nghỉ phép, Quy định công ty, hoặc Nghiệp vụ.
3. TUYỆT ĐỐI KHÔNG SỬ DỤNG TIẾNG TRUNG QUỐC (CHINESE) HAY TIẾNG ANH. Viết bằng 100% Tiếng Việt chuẩn.
4. TUYỆT ĐỐI KHÔNG dùng bất kỳ ký tự markdown nào như in đậm (**), in nghiêng (*), hay thẻ HTML.
5. Nếu tài liệu này chỉ là rác, danh bạ, tên người ("Chưa có bí danh..."), hoặc KHÔNG THỂ trích xuất được 1 cặp QA có ý nghĩa, hãy trả về DUY NHẤT chữ: SKIP
6. CHỈ TRẢ VỀ ĐÚNG định dạng sau (không giải thích thêm):
Câu hỏi: [Nhập câu hỏi thực tế]
Đáp án: [Nhập đáp án chi tiết lấy từ tài liệu]

Tài liệu:
{text}
"""
        try:
            print(f"[{success_count+1}/100] Đang xử lý chunk {i}...")
            response = await generator.generate(prompt)
            
            # Nếu AI đánh giá chunk là rác
            if "SKIP" in response[:20].upper():
                print("  -> Bỏ qua (Tài liệu không phù hợp)")
                continue
                
            # Xoá các ký hiệu in đậm (**) và các ký tự markdown thừa
            response = response.replace("**", "").replace("*", "").replace("`", "").replace("#", "")
            response = response.strip()
            
            import re
            q_match = re.search(r"(?:Câu\s*hỏi|Câu\s*\d+|Q|Question)\s*:\s*(.*)", response, re.IGNORECASE)
            a_match = re.search(r"(?:Đáp\s*án|Trả\s*lời|A|Answer)\s*:\s*(.*)", response, re.IGNORECASE | re.DOTALL)
            
            if q_match and a_match:
                question = q_match.group(1).strip()
                answer = a_match.group(1).strip()
                
                # Check thêm bộ lọc chống tiếng Trung (kiểm tra ký tự non-ASCII lạ)
                if any('\u4e00' <= char <= '\u9fff' for char in response):
                    print("  -> Phát hiện Tiếng Trung, bỏ qua.")
                    continue
                
                success_count += 1
                final_text = f"Câu {success_count}: {question}\nĐáp án: {answer}\n\n"
                
                with open(out_path, "a", encoding="utf-8") as f:
                    f.write(final_text)
                print(f"  -> Thành công!")
            else:
                print(f"  -> Sai định dạng, bỏ qua.")
                
        except Exception as e:
            print(f"  -> Lỗi: {e}")
            
    print(f"\nHOÀN THÀNH! Đã lưu {success_count} câu hỏi vào: {out_path}")

if __name__ == "__main__":
    asyncio.run(main())
