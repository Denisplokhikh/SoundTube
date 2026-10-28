from __future__ import annotations

import pytest
from fastapi import HTTPException

from app.api.routes import delete_track
from app.config import settings


def _make_library(root, relative: str, payload: bytes = b"fake") -> None:
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(payload)


async def test_delete_track_removes_file(tmp_path, monkeypatch):
    monkeypatch.setattr(settings, "music_dir", tmp_path)
    _make_library(tmp_path, "Artist/Album/Artist - Song.mp3")

    response = await delete_track("Artist/Album/Artist - Song.mp3")

    assert response.status_code == 204
    assert not (tmp_path / "Artist/Album/Artist - Song.mp3").exists()


async def test_delete_track_keeps_other_files(tmp_path, monkeypatch):
    monkeypatch.setattr(settings, "music_dir", tmp_path)
    _make_library(tmp_path, "Artist/Album/Artist - Song.mp3")
    _make_library(tmp_path, "Artist/Album/Artist - Keep.mp3")

    await delete_track("Artist/Album/Artist - Song.mp3")

    assert (tmp_path / "Artist/Album/Artist - Keep.mp3").exists()


@pytest.mark.parametrize(
    "relative",
    ["../outside.mp3", "Artist/../../outside.mp3", "Artist/Album/notes.txt"],
)
async def test_delete_track_rejects_unsafe_paths(tmp_path, monkeypatch, relative):
    monkeypatch.setattr(settings, "music_dir", tmp_path)
    _make_library(tmp_path, "Artist/Album/Artist - Song.mp3")

    with pytest.raises(HTTPException) as excinfo:
        await delete_track(relative)

    assert excinfo.value.status_code == 404
    assert (tmp_path / "Artist/Album/Artist - Song.mp3").exists()
