from __future__ import annotations

import logging
import sys
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

if getattr(sys, "frozen", False):
    WEB_DIR = Path(sys._MEIPASS).resolve() / "web"
else:
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

    # Pass the app object instead of an import string so the same entry point
    # works inside a PyInstaller bundle.
    uvicorn.run(
        app,
        host=settings.host,
        port=settings.port,
    )


if __name__ == "__main__":
    main()
