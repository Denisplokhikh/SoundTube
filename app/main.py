from __future__ import annotations

import logging
import os
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
    BUNDLED_WEB_DIR = Path(sys._MEIPASS).resolve() / "web"
else:
    BUNDLED_WEB_DIR = Path(__file__).resolve().parent.parent / "web"


def resolve_web_dir() -> Path:
    """Внешняя папка web/ перекрывает встроенную.

    Позволяет править фронтенд без пересборки exe: достаточно положить
    index.html/app.js/styles.css рядом с сервером или в %LOCALAPPDATA%\\melodown\\web
    и перезапустить приложение. Путь можно задать и через MELODOWN_WEB_DIR.
    """
    override = os.environ.get("MELODOWN_WEB_DIR")
    if override:
        candidate = Path(override).expanduser()
        if (candidate / "index.html").is_file():
            return candidate.resolve()

    if getattr(sys, "frozen", False):
        base = os.environ.get("LOCALAPPDATA") or os.environ.get("XDG_DATA_HOME")
        candidates = [
            Path(sys.executable).resolve().parent / "web",
            Path(base) / "melodown" / "web" if base else None,
        ]
        for candidate in candidates:
            if candidate and (candidate / "index.html").is_file():
                return candidate.resolve()

    return BUNDLED_WEB_DIR


WEB_DIR = resolve_web_dir()


@asynccontextmanager
async def lifespan(_app: FastAPI):
    await init_db()
    await runner.start()
    await recover_stale_jobs()
    yield
    await runner.stop()
    await engine.dispose()


app = FastAPI(
    title="SoundTube",
    version=__version__,
    description="Self-hosted archive for YouTube Music and SoundCloud playlists",
    lifespan=lifespan,
)

app.include_router(api_router)

if WEB_DIR.exists():
    logging.getLogger(__name__).info("frontend: %s", WEB_DIR)
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
