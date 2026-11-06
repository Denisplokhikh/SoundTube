from __future__ import annotations

import io
import os

from PIL import Image

from app.core import artwork
from app.core.artwork import _CoverCache, _prepare, extract_cover
from app.core.tagging import guess_image_mime


def _image_bytes(size: tuple[int, int], fmt: str = "PNG") -> bytes:
    image = Image.new("RGB", size, (20, 40, 60))
    buffer = io.BytesIO()
    image.save(buffer, format=fmt)
    return buffer.getvalue()


def test_prepare_resizes_and_converts_to_webp():
    prepared = _prepare(_image_bytes((1600, 900)))
    assert prepared is not None
    assert guess_image_mime(prepared) == "image/webp"

    with Image.open(io.BytesIO(prepared)) as image:
        assert image.size == (512, 288)


def test_prepare_rejects_oversized_source(monkeypatch):
    monkeypatch.setattr(artwork, "MAX_COVER_BYTES", 32)
    assert _prepare(b"x" * 33) is None


def test_prepare_returns_none_for_broken_data():
    assert _prepare(b"not an image") is None


def test_cache_evicts_least_recently_used():
    cache = _CoverCache(max_bytes=12, max_entries=10)
    cache.put("a", 1.0, b"12345")
    cache.put("b", 2.0, b"abcdef")

    assert cache.get("a", 1.0) == (True, b"12345")

    cache.put("c", 3.0, b"1234")

    assert cache.get("b", 2.0) == (False, None)
    assert cache.get("a", 1.0) == (True, b"12345")
    assert cache.get("c", 3.0) == (True, b"1234")


def test_cache_invalidates_changed_mtime():
    cache = _CoverCache(max_bytes=100, max_entries=10)
    cache.put("track", 1.0, b"cover")

    assert cache.get("track", 2.0) == (False, None)
    assert cache.get("track", 1.0) == (False, None)


def test_cache_does_not_store_item_larger_than_budget():
    cache = _CoverCache(max_bytes=4, max_entries=10)
    cache.put("a", 1.0, b"12345")

    assert cache.get("a", 1.0) == (False, None)


def test_cache_limits_empty_entries_by_count():
    cache = _CoverCache(max_bytes=100, max_entries=2)
    cache.put("a", 1.0, None)
    cache.put("b", 1.0, None)
    cache.put("c", 1.0, None)

    assert cache.get("a", 1.0) == (False, None)
    assert cache.get("b", 1.0) == (True, None)
    assert cache.get("c", 1.0) == (True, None)


def test_extract_cover_uses_cache_and_invalidates_on_mtime(tmp_path, monkeypatch):
    track = tmp_path / "track.mp3"
    track.write_bytes(b"audio")
    calls = []

    def fake_extract(path):
        calls.append(path)
        return b"cover"

    monkeypatch.setattr(artwork, "_extract", fake_extract)
    monkeypatch.setattr(artwork, "_cache", _CoverCache(max_bytes=100, max_entries=10))

    assert extract_cover(track) == b"cover"
    assert extract_cover(track) == b"cover"
    assert len(calls) == 1

    stat = track.stat()
    os.utime(track, (stat.st_atime, stat.st_mtime + 10))
    assert extract_cover(track) == b"cover"
    assert len(calls) == 2
