# Improved RAG Implementation

## Changes Made:
- **Higher Retrieval Thresholds:**
  - Updated QA threshold to **0.65**
  - Updated document threshold to **0.72**
- **Better Embedding Model:** Incorporated a new embedding model for enhanced performance.
- **Enhanced Prompt Engineering:** Revised the prompts to improve response quality.
- **Increased Top_k:** Set `top_k` parameter to **7** for wider result exploration.
- **Answer Validation:** Added a validation step to ensure answers meet specified criteria.
- **Configurable Parameters:** Parameters such as retrieval thresholds and top_k are now configurable.

# Example Usage
```python
rag_pipeline = RAGPipeline(
    qa_threshold=0.65,
    doc_threshold=0.72,
    embedding_model='new_model',
    top_k=7,
)
```

# Note:
Ensure to adjust configurations based on the specific dataset and use case. 
Refer to the documentation for further details on each parameter.