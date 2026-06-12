import os
import sys
import json
from pathlib import Path

# Ensure workspace root is in path
script_dir = os.path.dirname(os.path.abspath(__file__))
workspace_dir = os.path.dirname(os.path.dirname(script_dir))
if workspace_dir not in sys.path:
    sys.path.append(workspace_dir)

from ai.src.processors.document import RAGDocumentProcessor

if __name__ == "__main__":
    from ai.src.config import HANDBOOK_DIR, QA_DIR, CHUNKS_PATH
    
    processor = RAGDocumentProcessor()
    
    # Find all supported documents in the handbook directory, including admin uploads.
    supported_extensions = {".md", ".txt", ".pdf", ".docx"}
    handbook_files = [
        path
        for path in Path(HANDBOOK_DIR).rglob("*")
        if path.is_file() and path.suffix.lower() in supported_extensions
    ]
    
    # Include the QA dataset
    input_files = handbook_files + [Path(QA_DIR) / "qa_dataset.md"]
    
    all_chunks = []
    
    print(f"Found {len(input_files)} files to process...")
    
    for file_path in input_files:
        if not file_path.exists():
            print(f"Warning: File '{file_path}' does not exist. Skipping.")
            continue
            
        content = processor.load_file(str(file_path))
        if not content.strip():
            print(f"Warning: File '{file_path}' is empty or could not be read. Skipping.")
            continue
            
        # Determine relative path from data directory for cleaner metadata
        try:
            rel_path = str(file_path.relative_to(Path(HANDBOOK_DIR).parent))
        except ValueError:
            rel_path = str(file_path.name)
            
        doc = {
            "source": file_path.name,
            "path": rel_path,
            "content": content,
        }
        
        chunks = processor.process_document(doc)
        if chunks:
            all_chunks.extend(chunks)

    if all_chunks:
        Path(CHUNKS_PATH).parent.mkdir(parents=True, exist_ok=True)
        with open(CHUNKS_PATH, "w", encoding="utf-8") as f:
            for item in all_chunks:
                f.write(json.dumps(item, ensure_ascii=False) + "\n")

        tokens = [d["tokens"] for d in all_chunks]
        print("\nStats")
        print("Total files processed:", len(input_files))
        print("Total chunks generated:", len(all_chunks))
        print("Min tokens:", min(tokens))
        print("Max tokens:", max(tokens))
        print(f"Avg tokens: {sum(tokens) / len(tokens):.1f}")
        print(f"Saved {len(all_chunks)} chunks to {CHUNKS_PATH}")
    else:
        print("No chunks were generated from the dataset.")
