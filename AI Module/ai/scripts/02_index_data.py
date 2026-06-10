import os
import sys

# Ensure workspace root is in path
script_dir = os.path.dirname(os.path.abspath(__file__))
workspace_dir = os.path.dirname(os.path.dirname(script_dir))
if workspace_dir not in sys.path:
    sys.path.append(workspace_dir)

from ai.src.database.qdrant import QdrantIndexer

def main():
    indexer = QdrantIndexer()
    indexer.index_chunks()

if __name__ == "__main__":
    main()