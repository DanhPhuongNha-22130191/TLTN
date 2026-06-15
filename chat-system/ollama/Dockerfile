FROM ollama/ollama:0.11.4

ARG OLLAMA_MODEL=qwen2:1.5b

# Store the model in the image so starting the Compose stack never pulls it.
RUN ollama serve > /tmp/ollama.log 2>&1 & \
    server_pid=$!; \
    until ollama list > /dev/null 2>&1; do sleep 1; done; \
    ollama pull "${OLLAMA_MODEL}"; \
    kill "${server_pid}"; \
    wait "${server_pid}" || true

