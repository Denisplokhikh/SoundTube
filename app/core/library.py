from __future__ import annotations

import hashlib
import threading
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

import mutagen

from app.config import settings

AUDIO_SUFFIXES = {".mp3", ".m4a", ".flac", ".opus", ".ogg", ".aac", ".wav", ".webm"}

CACHE_TTL_SECONDS = 15.0

_lock = threading.Lock()
_cache: tuple[float, list["LibraryTrack"]] | None = None


@dataclass(slots=True)
class LibraryTrack:
    id: str
    relative_path: str
    title: str
    artist: str
    album: str | None
    duration: float | None
    size: int
    modified: datetime
    extension: str


def invalidate_cache() -> None:
    global _cache
    with _lock:
        _cache = None


def scan_library(force: bool = False) -> list[LibraryTrack]:
    global _cache

    now = time.monotonic()
    with _lock:
        if not force and _cache is not None and now - _cache[0] < CACHE_TTL_SECONDS:
            return _cache[1]

    tracks = _scan()

    with _lock:
        _cache = (time.monotonic(), tracks)
    return tracks


def _scan() -> list[LibraryTrack]:
    root = settings.music_dir
    if not root.exists():
        return []

    tracks: list[LibraryTrack] = []
    for path in root.rglob("*"):
        if not path.is_file() or path.suffix.lower() not in AUDIO_SUFFIXES:
            continue
        try:
            stat = path.stat()
            relative = path.relative_to(root)
        except OSError:
            continue

        artist_fallback, album_fallback = _folders(relative)
        title, artist, album, duration = _read_tags(path)

        tracks.append(
            LibraryTrack(
                id=hashlib.sha1(relative.as_posix().encode("utf-8")).hexdigest()[:16],
                relative_path=relative.as_posix(),
                title=title or _title_from_filename(path, artist_fallback),
                artist=artist or artist_fallback,
                album=album or album_fallback,
                duration=duration,
                size=stat.st_size,
                modified=datetime.fromtimestamp(stat.st_mtime, tz=timezone.utc),
                extension=path.suffix.lower().lstrip("."),
            )
        )

    tracks.sort(key=lambda item: (item.artist.lower(), (item.album or "").lower(), item.title.lower()))
    return tracks


def _folders(relative: Path) -> tuple[str, str | None]:
    parts = relative.parts
    if len(parts) >= 3:
        return parts[0] or "Unknown artist", parts[1] or None
    if len(parts) == 2:
        return parts[0] or "Unknown artist", None
    return "Unknown artist", None


def _title_from_filename(path: Path, artist: str) -> str:
    stem = path.stem.strip()
    prefix = f"{artist} - "
    if artist and stem.startswith(prefix):
        return stem[len(prefix) :].strip() or stem
    if " - " in stem:
        return stem.split(" - ", 1)[1].strip() or stem
    return stem


def _read_tags(path: Path) -> tuple[str | None, str | None, str | None, float | None]:
    try:
        audio = mutagen.File(path, easy=True)
    except Exception:
        return None, None, None, None

    if audio is None:
        return None, None, None, None

    duration = getattr(getattr(audio, "info", None), "length", None)
    tags = audio.tags or {}

    def first(key: str) -> str | None:
        value = tags.get(key) if hasattr(tags, "get") else None
        if isinstance(value, (list, tuple)):
            value = value[0] if value else None
        if value is None:
            return None
        text = str(value).strip()
        return text or None

    return first("title"), first("artist"), first("album"), float(duration) if duration else None


def resolve_track_path(relative_path: str) -> Path | None:
    """Resolve a library-relative path and make sure it cannot escape the music dir."""
    root = settings.music_dir.resolve()
    try:
        candidate = (root / relative_path).resolve()
    except (OSError, ValueError):
        return None

    if not candidate.is_relative_to(root) or not candidate.is_file():
        return None
    if candidate.suffix.lower() not in AUDIO_SUFFIXES:
        return None
    return candidate
