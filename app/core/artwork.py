from __future__ import annotations

import base64
from pathlib import Path

import mutagen
from mutagen.flac import FLAC, Picture
from mutagen.mp3 import MP3
from mutagen.mp4 import MP4
from mutagen.oggopus import OggOpus
from mutagen.oggvorbis import OggVorbis

MAX_COVER_BYTES = 10 * 1024 * 1024
MAX_CACHE_ENTRIES = 400

_cache: dict[str, tuple[float, bytes | None]] = {}


def extract_cover(path: Path) -> bytes | None:
    try:
        mtime = path.stat().st_mtime
    except OSError:
        return None

    key = str(path)
    cached = _cache.get(key)
    if cached is not None and cached[0] == mtime:
        return cached[1]

    cover = _extract(path)

    if len(_cache) >= MAX_CACHE_ENTRIES:
        _cache.clear()
    _cache[key] = (mtime, cover)
    return cover


def _extract(path: Path) -> bytes | None:
    try:
        audio = mutagen.File(path)
    except Exception:
        return None

    if audio is None:
        return None

    if isinstance(audio, MP4):
        covers = (audio.tags or {}).get("covr")
        if covers:
            return _limit(bytes(covers[0]))
        return None

    if isinstance(audio, FLAC):
        if audio.pictures:
            return _limit(audio.pictures[0].data)
        return None

    if isinstance(audio, MP3):
        if audio.tags:
            for frame in audio.tags.getall("APIC"):
                return _limit(frame.data)
        return None

    if isinstance(audio, (OggOpus, OggVorbis)):
        pictures = audio.get("metadata_block_picture")
        if pictures:
            try:
                picture = Picture(base64.b64decode(pictures[0]))
                return _limit(picture.data)
            except Exception:
                return None

    return None


def _limit(data: bytes) -> bytes | None:
    return data if 0 < len(data) <= MAX_COVER_BYTES else None
