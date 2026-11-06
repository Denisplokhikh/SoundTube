from __future__ import annotations

import hashlib
from pathlib import Path

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.api import routes
from app.core import artwork
from app.core.artwork import _CoverCache


@pytest.fixture()
def client(monkeypatch):
    monkeypatch.setattr(
        artwork, "_cache", _CoverCache(max_bytes=10_000_000, max_entries=100)
    )
    app = FastAPI()
    app.include_router(routes.router)
    return TestClient(app)


def test_artwork_returns_etag_and_304(client, tmp_path, monkeypatch):
    track = tmp_path / "track.mp3"
    track.write_bytes(b"audio")
    cover = b"fake cover bytes"

    monkeypatch.setattr(routes, "extract_cover", lambda _file: cover)
    monkeypatch.setattr(routes, "resolve_track_path", lambda _path: track)

    response = client.get(f"/api/artwork?path={track}")
    assert response.status_code == 200, response.text
    etag = f'"{hashlib.md5(cover).hexdigest()}"'
    assert response.headers["ETag"] == etag
    assert response.headers["Cache-Control"] == "public, max-age=604800"
    assert response.content == cover

    cached = client.get(f"/api/artwork?path={track}", headers={"If-None-Match": etag})
    assert cached.status_code == 304
    assert cached.content == b""
