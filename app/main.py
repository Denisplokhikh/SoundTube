from __future__ import annotations

import logging
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles

from app import __version__
from app.api.routes import router as api_router
from app.core.runner import recover_stale_jobs, runner
from app.db import engine, init_db

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)-8s %(name)s: %(message)s",
)

WEB_DIR = Path(__file__).resolve().parent.parent / "web"


@asynccontextmanager
async def lifespan(_app: FastAPI):
    await init_db()
    await runner.start()
    await recover_stale_jobs()
    yield
    await runner.stop()
    await engine.dispose()


app = FastAPI(
    title="melodown",
    version=__version__,
    description="Self-hosted archive for YouTube Music and SoundCloud playlists",
    lifespan=lifespan,
)

app.include_router(api_router)

if WEB_DIR.exists():
    app.mount("/", StaticFiles(directory=WEB_DIR, html=True), name="web")


def main() -> None:
    import uvicorn

    from app.config import settings

    uvicorn.run(
        "app.main:app",
        host=settings.host,
        port=settings.port,
        reload=False,
    )


if __name__ == "__main__":
    main()
