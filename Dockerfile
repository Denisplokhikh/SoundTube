FROM python:3.13-slim AS deno

COPY scripts/prepare_media_tools.py /tmp/prepare_media_tools.py
RUN python /tmp/prepare_media_tools.py --deno-only --output /usr/local/bin --cache /tmp/media-archives

FROM python:3.13-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*

COPY --from=deno /usr/local/bin/deno /usr/local/bin/deno
COPY licenses/Deno-LICENSE.md /usr/local/share/licenses/deno/LICENSE.md

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
