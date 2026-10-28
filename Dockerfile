FROM python:3.13-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /opt/melodown

COPY pyproject.toml README.md LICENSE ./
COPY app ./app
COPY web ./web

RUN pip install .

ENV MELODOWN_MUSIC_DIR=/music \
    MELODOWN_DATA_DIR=/data \
    MELODOWN_HOST=0.0.0.0 \
    MELODOWN_PORT=8765

VOLUME ["/music", "/data"]
EXPOSE 8765

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8765"]
