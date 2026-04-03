"""
Script chia dataset thanh cac tap train/val/test
"""

import json
import random
from pathlib import Path

def split_dataset(input_file: str, output_dir: str, train_ratio: float = 0.8, val_ratio: float = 0.1):
    # Chia tap du lieu goc thanh cac phan huan luyen, kiem thu va danh gia
    
    # Doc cac cap QA
    qa_pairs = []
    with open(input_file, 'r', encoding='utf-8') as f:
        for line in f:
            qa_pairs.append(json.loads(line))
    
    print(f"Tong cong: {len(qa_pairs)} QA pairs")
    
    # Tron ngau nhien du lieu
    random.shuffle(qa_pairs)
    
    # Tinh toan so luong cho tung tap
    total = len(qa_pairs)
    train_count = int(total * train_ratio)
    val_count = int(total * val_ratio)
    test_count = total - train_count - val_count
    
    # Thuc hien chia cat du lieu
    train_set = qa_pairs[:train_count]
    val_set = qa_pairs[train_count:train_count + val_count]
    test_set = qa_pairs[train_count + val_count:]
    
    print(f"Tap huan luyen (Train): {len(train_set)} ({train_ratio*100:.0f}%)")
    print(f"Tap kiem thu (Val): {len(val_set)} ({val_ratio*100:.0f}%)")
    print(f"Tap danh gia (Test): {len(test_set)} ({(1-train_ratio-val_ratio)*100:.0f}%)")
    
    # Luu ket qua ra cac file tuong ung
    Path(output_dir).mkdir(parents=True, exist_ok=True)
    
    for split_name, split_data in [
        ('train', train_set),
        ('val', val_set),
        ('test', test_set)
    ]:
        output_file = f"{output_dir}/{split_name}.jsonl"
        with open(output_file, 'w', encoding='utf-8') as f:
            for item in split_data:
                f.write(json.dumps(item, ensure_ascii=False) + '\n')
        print(f"Da luu: {output_file}")

if __name__ == '__main__':
    # Khoi chay quy trinh chia tap du lieu
    split_dataset(
        input_file='./company-ai-chatbot/data/processed/qa_pairs.jsonl',
        output_dir='./company-ai-chatbot/data/',
        train_ratio=0.8,
        val_ratio=0.1
    )