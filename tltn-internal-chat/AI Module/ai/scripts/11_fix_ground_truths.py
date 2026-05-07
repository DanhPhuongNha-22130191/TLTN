import os
import sys
import re
import asyncio

base_dir = os.path.dirname(os.path.abspath(__file__))
sys.path.append(base_dir)

from importlib import import_module
init_pipeline = import_module("08_query_pipeline").init_pipeline
parse_qa_dataset = import_module("10_split_qa_dataset").parse_qa_dataset

def export_qa_md_with_chunks(pairs: list, filepath: str):
    with open(filepath, "w", encoding="utf-8") as f:
        f.write("# Bộ câu hỏi (Tự động tách)\n\n")
        f.write(f"> Tổng số: {len(pairs)} câu\n\n")
        for i, pair in enumerate(pairs, 1):
            f.write(f"**Q{i}:** {pair['question']}\n\n")
            f.write(f"**A:** {pair['truth']}\n\n")
            if "chunk_id" in pair:
                f.write(f"**ChunkID:** {pair['chunk_id']}\n\n")

async def main():
    from importlib import import_module
    QdrantRetriever = import_module("05_retriever").QdrantRetriever
    retriever = QdrantRetriever()
    print("Retriever loaded.")
    
    for filename in ["qa_dataset.md", "train_qa.md", "test_qa.md"]:
        filepath = os.path.join(base_dir, "..", "data", "qa", filename)
        if not os.path.exists(filepath):
            continue
        
        print(f"Processing {filename}...")
        pairs = parse_qa_dataset(filepath)
        for pair in pairs:
            # Query the retriever using the answer itself as the query, to find the source chunk
            results = await retriever.search(pair['truth'], top_k=1)
            if results:
                pair['chunk_id'] = results[0]['chunk_id']
                print(f"Matched Q: {pair['question'][:30]}... -> ChunkID: {pair['chunk_id']}")
            else:
                pair['chunk_id'] = "UNKNOWN"
        
        export_qa_md_with_chunks(pairs, filepath)
        print(f"✅ Updated {filename}")

if __name__ == "__main__":
    asyncio.run(main())
