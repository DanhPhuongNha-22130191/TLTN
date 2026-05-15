import csv
import re

csv_path = "/home/phuongnha/SourcesCode/TLTN/tltn-internal-chat/AI Module/ai/logs/eval_results_20260514_185815.csv"
md_path = "/home/phuongnha/SourcesCode/TLTN/tltn-internal-chat/AI Module/ai/data/qa/test_qa.md"

with open(csv_path, "r", encoding="utf-8-sig") as f:
    reader = csv.DictReader(f)
    results = [row['answer'].strip() for row in reader]

with open(md_path, "r", encoding="utf-8") as f:
    content = f.read()

parts = re.split(r"(\*\*A:\*\*)", content)
new_parts = []
ans_idx = 0

for i in range(len(parts)):
    if parts[i] == "**A:**":
        new_parts.append(parts[i])
    else:
        if i > 0 and parts[i-1] == "**A:**":
            chunk_idx = parts[i].find("\n**ChunkID:")
            if chunk_idx != -1:
                # Format exactly as before: space after A:, then answer, then newline before ChunkID
                new_answer = " " + results[ans_idx] + "\n"
                parts[i] = new_answer + parts[i][chunk_idx:]
            ans_idx += 1
        new_parts.append(parts[i])

new_content = "".join(new_parts)
with open(md_path, "w", encoding="utf-8") as f:
    f.write(new_content)

print(f"Updated {ans_idx} answers in {md_path}")
