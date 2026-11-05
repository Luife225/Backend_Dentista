FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    YOLO_CONFIG_DIR=/tmp/Ultralytics \
    PORT=8091

WORKDIR /app

# Librerías de sistema mínimas requeridas por OpenCV / Ultralytics en contenedores slim
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgl1 \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --upgrade pip && pip install -r requirements.txt

COPY model/ ./model/
COPY detector.py app.py ./

EXPOSE 8091

CMD ["sh", "-c", "uvicorn app:app --host 0.0.0.0 --port ${PORT:-8091}"]

