import os
import random
import re

def parse_qa_dataset(filepath: str) -> list:
    pairs = []
    with open(filepath, "r", encoding="utf-8") as f:
        content = f.read()

    matches1 = re.findall(
        r"(?:Q:|Question:)\s*(.*?)\n(?:A:|Answer:)\s*(.*?)(?=\nQ:|\nQuestion:|$)",
        content,
        re.DOTALL
    )

    matches2 = re.findall(
        r"\*\*Q\d+:\*\*\s*(.*?)\s*\*\*A:\*\*\s*(.*?)(?=\n\*\*Q\d+:|\Z)",
        content,
        re.DOTALL
    )

    matches = matches1 + matches2
    for q, a in matches:
        pairs.append({
            "question": q.strip(),
            "truth": a.strip()
        })
    return pairs

def export_qa_md(pairs: list, filepath: str):
    with open(filepath, "w", encoding="utf-8") as f:
        f.write("# Bộ câu hỏi (Tự động tách)\n\n")
        f.write(f"> Tổng số: {len(pairs)} câu\n\n")
        for i, pair in enumerate(pairs, 1):
            f.write(f"**Q{i}:** {pair['question']}\n\n")
            f.write(f"**A:** {pair['truth']}\n\n")

def main():
    base_dir = os.path.dirname(os.path.abspath(__file__))
    input_path = os.path.join(base_dir, "..", "data", "qa", "qa_dataset.md")
    train_path = os.path.join(base_dir, "..", "data", "qa", "train_qa.md")
    test_path = os.path.join(base_dir, "..", "data", "qa", "test_qa.md")

    if not os.path.exists(input_path):
        print(f"❌ Không tìm thấy {input_path}")
        return

    pairs = parse_qa_dataset(input_path)
    if not pairs:
        print("❌ Dataset rỗng!")
        return

    # Trộn ngẫu nhiên
    random.seed(42) # cố định seed để chia đều giống nhau
    random.shuffle(pairs)

    split_idx = int(len(pairs) * 0.8) # 80% train, 20% test
    train_pairs = pairs[:split_idx]
    test_pairs = pairs[split_idx:]

    print(f"📊 Đã chia Dataset ({len(pairs)} câu) thành:")
    print(f"   + Tập Train: {len(train_pairs)} câu")
    print(f"   + Tập Test:  {len(test_pairs)} câu")

    export_qa_md(train_pairs, train_path)
    export_qa_md(test_pairs, test_path)
    print(f"✅ Đã lưu tập Train tại: {train_path}")
    print(f"✅ Đã lưu tập Test tại: {test_path}")

if __name__ == "__main__":
    main()
