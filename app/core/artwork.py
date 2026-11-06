from __future__ import annotations

import base64
import io
import threading
from collections import OrderedDict
from pathlib import Path

import mutagen
from mutagen.flac import FLAC, Picture
from mutagen.mp3 import MP3
from mutagen.mp4 import MP4
from mutagen.oggopus import OggOpus
from mutagen.oggvorbis import OggVorbis
from PIL import Image, ImageOps

# Обложка в кэше уже пережата до 512 px, поэтому 3 МБ с запасом хватает
# на исходник любого формата. Отдельный потолок нужен, чтобы не тянуть
# в память многомегабайтные картинки.
MAX_COVER_BYTES = 3 * 1024 * 1024
MAX_CACHE_BYTES = 64 * 1024 * 1024
# Страховка для файлов без обложки: они занимают 0 байт, но остаются записями.
MAX_CACHE_ENTRIES = 1000
COVER_MAX_SIDE = 512
COVER_QUALITY = 82
MAX_COVER_PIXELS = 32_000_000


class _CoverCache:
    """LRU-кэш обложек, ограниченный суммарным размером в байтах."""

    def __init__(
        self,
        max_bytes: int = MAX_CACHE_BYTES,
        max_entries: int = MAX_CACHE_ENTRIES,
    ) -> None:
        self._max_bytes = max_bytes
        self._max_entries = max_entries
        self._items: OrderedDict[str, tuple[float, bytes | None]] = OrderedDict()
        self._size = 0
        self._lock = threading.Lock()

    def get(self, key: str, mtime: float) -> tuple[bool, bytes | None]:
        with self._lock:
            item = self._items.get(key)
            if item is None:
                return False, None
            if item[0] != mtime:
                self._drop(key)
                return False, None
            self._items.move_to_end(key)
            return True, item[1]

    def put(self, key: str, mtime: float, cover: bytes | None) -> None:
        size = len(cover) if cover else 0
        if size > self._max_bytes:
            return

        with self._lock:
            old = self._items.pop(key, None)
            if old is not None:
                self._size -= len(old[1]) if old[1] else 0

            self._items[key] = (mtime, cover)
            self._size += size

            while self._items and (
                self._size > self._max_bytes or len(self._items) > self._max_entries
            ):
                _, evicted = self._items.popitem(last=False)
                self._size -= len(evicted[1]) if evicted[1] else 0

    def _drop(self, key: str) -> None:
        item = self._items.pop(key)
        self._size -= len(item[1]) if item[1] else 0


_cache = _CoverCache()


def extract_cover(path: Path) -> bytes | None:
    try:
        mtime = path.stat().st_mtime
    except OSError:
        return None

    key = str(path)
    found, cover = _cache.get(key, mtime)
    if found:
        return cover

    cover = _extract(path)
    _cache.put(key, mtime, cover)
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
            return _prepare(bytes(covers[0]))
        return None

    if isinstance(audio, FLAC):
        if audio.pictures:
            return _prepare(audio.pictures[0].data)
        return None

    if isinstance(audio, MP3):
        if audio.tags:
            for frame in audio.tags.getall("APIC"):
                return _prepare(frame.data)
        return None

    if isinstance(audio, (OggOpus, OggVorbis)):
        pictures = audio.get("metadata_block_picture")
        if pictures:
            try:
                picture = Picture(base64.b64decode(pictures[0]))
                return _prepare(picture.data)
            except Exception:
                return None

    return None


def _prepare(data: bytes) -> bytes | None:
    """Уменьшает обложку до 512 px и пережимает в WebP (или JPEG)."""
    if not data or len(data) > MAX_COVER_BYTES:
        return None

    try:
        with Image.open(io.BytesIO(data)) as image:
            if image.width * image.height > MAX_COVER_PIXELS:
                return None
            image = ImageOps.exif_transpose(image)
            if image.mode in {"LA", "PA"} or "transparency" in image.info:
                image = image.convert("RGBA")
            elif image.mode != "RGB":
                image = image.convert("RGB")

            image.thumbnail(
                (COVER_MAX_SIDE, COVER_MAX_SIDE),
                Image.Resampling.LANCZOS,
            )
            return _encode(image)
    except Exception:
        return None


def _encode(image: Image.Image) -> bytes:
    buffer = io.BytesIO()
    try:
        image.save(buffer, format="WEBP", quality=COVER_QUALITY, method=4)
        return buffer.getvalue()
    except Exception:
        # На случай сборки Pillow без поддержки WebP.
        buffer = io.BytesIO()
        image.convert("RGB").save(buffer, format="JPEG", quality=85, optimize=True)
        return buffer.getvalue()
