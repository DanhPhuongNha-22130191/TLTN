import json
import os
import time
import re
import asyncio
from typing import List, Dict

try:
    from g4f.client import AsyncClient
    from g4f.Provider import PollinationsAI
except ImportError:
    print("Vui long cai dat: pip install -U g4f curl_cffi")
    exit(1)

# Cac ham tien ich hỗ trợ xử lý dữ liệu

def extract_json(text: str):
    # Trich xuat mang JSON tu van ban dau ra cua model
    try:
        return json.loads(text)
    except:
        pass

    match = re.search(r'\[.*?\]', text, re.DOTALL)
    if match:
        try:
            return json.loads(match.group())
        except:
            return []

    return []

# Lop chinh thuc hien viec sinh cap cau hoi-tra loi (QA)

class QAPairGenerator:
    def __init__(self, model_name: str = "openai"):
        # Khoi tao generator su dung G4F AsyncClient (khong can API Key)
        self.client = AsyncClient()
        self.model_name = model_name

    async def safe_generate(self, prompt: str, retries: int = 5):
        # Goi API voi co che thu lai (retry) de dam bao on dinh
        for i in range(retries):
            try:
                response = await self.client.chat.completions.create(
                    model=self.model_name,
                    provider=PollinationsAI,
                    messages=[{"role": "user", "content": prompt}],
                )
                return response.choices[0].message.content
            except Exception as e:
                print(f"Thu lai lan {i+1}: Loi API G4F - {e}")
                await asyncio.sleep(4)
        return ""

    async def generate_batch_qa(self, chunks: List[Dict], num_questions: int = 3) -> List[Dict]:
        # Sinh tap cau hoi va tra loi tu mot nhom cac doan van ban (chunks)
        combined_text = ""
        for i, chunk in enumerate(chunks):
            content = chunk["content"][:800]
            combined_text += f"\n--- CHUNK {i+1} ---\n{content}\n"

        prompt = f"""
Bạn là hệ thống tạo dataset QA chuyên nghiệp. Suy nghĩ tuần tự và chỉ làm theo đúng yêu cầu.

Dựa trên các đoạn văn sau, tạo câu hỏi & câu trả lời bằng tiếng Việt.

{combined_text}

YÊU CẦU:
- Mỗi chunk tạo {num_questions} câu hỏi.
- Câu hỏi rõ ràng, thực tế.
- Trả lời ngắn gọn (1-2 câu).
- KHÔNG thêm thông tin nằm ngoài văn bản.

FORMAT BẮT BUỘC (Trả về mảng JSON hợp lệ như sau):
[
  {{
    "question": "...",
    "answer": "...",
    "chunk_index": 1
  }}
]

Chỉ in ra đúng nội dung JSON (mảng bắt đầu bằng dấu ngoặc vuông '[' và kết thúc bằng ']'). Không giải thích.
"""

        result = await self.safe_generate(prompt)
        raw_pairs = extract_json(result)

        results = []

        for pair in raw_pairs:
            try:
                question = pair.get("question", "").strip()
                answer = pair.get("answer", "").strip()
                idx = pair.get("chunk_index", 1)

                if not question or not answer:
                    continue
                if not isinstance(idx, int):
                    continue

                idx -= 1
                if idx < 0 or idx >= len(chunks):
                    continue

                if len(answer) < 10 or "không có thông tin" in answer.lower():
                    continue

                chunk = chunks[idx]

                results.append({
                    "question": question,
                    "answer": answer,
                    "source": chunk["source"],
                    "chunk_id": chunk["chunk_id"]
                })
            except:
                continue

        return results

    async def _process_async(self, input_file: str, output_file: str, batch_size: int = 2, max_workers: int = 2):
        # Quy trinh xu ly file dau vao theo pipeline bat dong bo song song
        print("File dau vao:", input_file)
        print("Luu ket qua tai:", output_file)

        if not os.path.exists(input_file):
            print("Khong tim thay file dau vao!")
            return

        chunks = []
        with open(input_file, "r", encoding="utf-8") as f:
            for line in f:
                try:
                    chunks.append(json.loads(line))
                except:
                    continue

        print(f"Tong so chunks: {len(chunks)}")
        
        batches = [chunks[i:i + batch_size] for i in range(0, len(chunks), batch_size)]
        total_batches = len(batches)
        print(f"Dang xu ly {total_batches} batches song song (max_workers={max_workers})...")

        start_time = time.time()
        sem = asyncio.Semaphore(max_workers)
        all_results = []
        
        async def process_batch_with_sem(batch, batch_idx):
            async with sem:
                res = await self.generate_batch_qa(batch)
                print(f"Xong batch {batch_idx + 1}/{total_batches} (+{len(res)} pairs)")
                return res
        
        tasks = [process_batch_with_sem(b, i) for i, b in enumerate(batches)]
        results_list = await asyncio.gather(*tasks, return_exceptions=True)
        
        for res_batch in results_list:
            if isinstance(res_batch, Exception):
                print(f"Loi tai batch: {res_batch}")
            else:
                all_results.extend(res_batch)

        os.makedirs(os.path.dirname(output_file), exist_ok=True)

        with open(output_file, "w", encoding="utf-8") as f:
            for item in all_results:
                f.write(json.dumps(item, ensure_ascii=False) + "\n")

        print(f"\nHOAN THANH: Tong cong {len(all_results)} QA pairs.")
        print(f"Thoi gian thuc hien: {time.time() - start_time:.2f}s")
        
    def process(self, input_file: str, output_file: str, batch_size: int = 2, max_workers: int = 2):
        # Ham chay pipeline tu moi truong dong bo
        asyncio.run(self._process_async(input_file, output_file, batch_size, max_workers))

# Khoi chay chuong trinh chinh de sinh du lieu QA

if __name__ == "__main__":
    BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

    input_path = os.path.join(BASE_DIR, "data/processed/chunks.jsonl")
    output_path = os.path.join(BASE_DIR, "data/processed/qa_pairs.jsonl")

    generator = QAPairGenerator(model_name="openai")

    generator.process(
        input_file=input_path,
        output_file=output_path,
        batch_size=3,
        max_workers=3
    )