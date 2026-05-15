import csv
import re
import os
import glob

log_dir = "/home/phuongnha/SourcesCode/TLTN/tltn-internal-chat/AI Module/ai/logs"
list_of_files = glob.glob(os.path.join(log_dir, 'eval_results_*.csv'))
latest_csv = max(list_of_files, key=os.path.getctime)
print(f"Using CSV: {latest_csv}")

md_path = "/home/phuongnha/SourcesCode/TLTN/tltn-internal-chat/AI Module/ai/data/qa/qa_dataset.md"

with open(latest_csv, "r", encoding="utf-8-sig") as f:
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
                if ans_idx < len(results):
                    new_answer = " " + results[ans_idx] + "\n"
                else:
                    new_answer = parts[i][:chunk_idx]
                parts[i] = new_answer + parts[i][chunk_idx:]
            ans_idx += 1
        new_parts.append(parts[i])

new_content = "".join(new_parts)
with open(md_path, "w", encoding="utf-8") as f:
    f.write(new_content)

print(f"Updated {min(ans_idx, len(results))} answers in {md_path}")
