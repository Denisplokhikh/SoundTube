"""PyInstaller entry point for the bundled melodown server."""

from __future__ import annotations

import os
import sys
from pathlib import Path


def _ensure_streams() -> None:
    """Windowed PyInstaller builds start with sys.stdout/sys.stderr set to None.

    Uvicorn and logging expect real streams, so route them to a log file
    provided by the Tauri shell (or to devnull when it is not set).
    """
    if sys.stdout is not None and sys.stderr is not None:
        return

    log_file = os.environ.get("MELODOWN_LOG_FILE")
    if log_file:
        path = Path(log_file)
        path.parent.mkdir(parents=True, exist_ok=True)
        stream = open(path, "a", encoding="utf-8", buffering=1)
    else:
        stream = open(os.devnull, "w", encoding="utf-8")

    if sys.stdout is None:
        sys.stdout = stream
    if sys.stderr is None:
        sys.stderr = stream


_ensure_streams()

from app.main import main  # noqa: E402

if __name__ == "__main__":
    main()
