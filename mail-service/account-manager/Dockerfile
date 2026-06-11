FROM python:3.13-alpine

RUN apk add --no-cache docker-cli

WORKDIR /app
COPY server.py /app/server.py

EXPOSE 8080

CMD ["python", "/app/server.py"]
