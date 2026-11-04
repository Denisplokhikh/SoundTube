from __future__ import annotations

import httpx
import pytest
from fastapi import HTTPException

from app.api.routes import delete_track, lyrics
from app.config import settings
from app.core.lyrics import (
    LRCLIB_GET_URL,
    LRCLIB_SEARCH_URL,
    clean_query_metadata,
    clear_lyrics_cache,
    parse_lrc,
)


def _make_track(root, relative: str, payload: bytes = b"fake-audio") -> None:
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(payload)


def test_parse_lrc_handles_timestamps_multi_tags_and_offset():
    raw = """
    [ar:Kino]
    [ti:Gruppa krovi]
    [offset:500]
    [00:10.00]Тёплыйк место на улице
    [00:15.250][00:30.50]Припев повторяется
    """
    lines, plain = parse_lrc(raw)

    assert len(lines) == 3
    assert lines[0].time == pytest.approx(9.5)
    assert lines[0].text == "Тёплыйк место на улице"
    assert lines[1].time == pytest.approx(14.75)
    assert lines[1].text == "Припев повторяется"
    assert lines[2].time == pytest.approx(30.0)
    assert lines[2].text == "Припев повторяется"
    assert "Тёплыйк место на улице\nПрипев повторяется" == plain


def test_clean_query_metadata_strips_youtube_noise_and_splits_artist():
    title, artist = clean_query_metadata(
        "Daft Punk - Harder Better Faster Stronger (Official Video) [HD] (feat. Someone)",
        "Unknown artist",
    )
    assert artist == "Daft Punk"
    assert title == "Harder Better Faster Stronger"


async def test_lyrics_reads_local_lrc_without_network(tmp_path, monkeypatch):
    clear_lyrics_cache()
    monkeypatch.setattr(settings, "music_dir", tmp_path)
    rel = "Artist/Album/Artist - Song.mp3"
    _make_track(tmp_path, rel)
    (tmp_path / "Artist/Album/Artist - Song.lrc").write_text(
        "[00:01.20]Первая строка\n[00:04.00]Вторая строка\n",
        encoding="utf-8",
    )

    response = await lyrics(rel)

    assert response.found is True
    assert response.synced is True
    assert response.source == "lrc"
    assert len(response.lines) == 2
    assert response.lines[0].time == pytest.approx(1.2)
    assert response.lines[0].text == "Первая строка"
    assert response.plain == "Первая строка\nВторая строка"


async def test_lyrics_fetches_from_lrclib_and_caches_lrc_file(tmp_path, monkeypatch):
    clear_lyrics_cache()
    monkeypatch.setattr(settings, "music_dir", tmp_path)
    rel = "Molchat Doma/Etazhi/Molchat Doma - Sudno.mp3"
    _make_track(tmp_path, rel)

    calls: list[str] = []

    async def fake_get(self, url, params=None, **kwargs):
        calls.append(url)
        return httpx.Response(
            200,
            json={
                "id": 1,
                "trackName": "Sudno",
                "artistName": "Molchat Doma",
                "syncedLyrics": "[00:12.50]Эмалированное судно\n[00:16.00]Окошко, тумбочка, кровать",
                "plainLyrics": "Эмалированное судно\nОкошко, тумбочка, кровать",
                "instrumental": False,
            },
        )

    monkeypatch.setattr(httpx.AsyncClient, "get", fake_get)

    response = await lyrics(rel)

    assert response.found is True
    assert response.synced is True
    assert response.source == "lrclib"
    assert len(response.lines) == 2
    assert len(calls) == 1
    assert calls[0] == LRCLIB_GET_URL

    # Файл .lrc должен сохраниться рядом с аудиофайлом
    lrc_file = tmp_path / "Molchat Doma/Etazhi/Molchat Doma - Sudno.lrc"
    assert lrc_file.is_file()

    # Повторный запрос должен читаться с диска без похода в сеть
    second = await lyrics(rel)
    assert second.source == "lrc"
    assert len(calls) == 1


async def test_lyrics_falls_back_to_search_on_404(tmp_path, monkeypatch):
    clear_lyrics_cache()
    monkeypatch.setattr(settings, "music_dir", tmp_path)
    rel = "Artist/Album/Artist - Rare Edit.mp3"
    _make_track(tmp_path, rel)

    async def fake_get(self, url, params=None, **kwargs):
        if url == LRCLIB_GET_URL:
            return httpx.Response(404, json={"statusCode": 404})
        if url == LRCLIB_SEARCH_URL:
            return httpx.Response(
                200,
                json=[
                    {
                        "id": 10,
                        "trackName": "Rare Edit",
                        "artistName": "Artist",
                        "syncedLyrics": None,
                        "plainLyrics": "Просто текст без таймкодов",
                        "instrumental": False,
                    }
                ],
            )
        raise AssertionError(f"Unexpected URL {url}")

    monkeypatch.setattr(httpx.AsyncClient, "get", fake_get)

    response = await lyrics(rel)

    assert response.found is True
    assert response.synced is False
    assert response.source == "lrclib"
    assert response.plain == "Просто текст без таймкодов"
    assert response.lines == []


async def test_lyrics_rejects_missing_or_unsafe_paths(tmp_path, monkeypatch):
    monkeypatch.setattr(settings, "music_dir", tmp_path)
    with pytest.raises(HTTPException) as excinfo:
        await lyrics("../outside.mp3")
    assert excinfo.value.status_code == 404


async def test_delete_track_also_removes_sidecar_lrc(tmp_path, monkeypatch):
    monkeypatch.setattr(settings, "music_dir", tmp_path)
    rel = "Artist/Album/Artist - Song.mp3"
    _make_track(tmp_path, rel)
    lrc_file = tmp_path / "Artist/Album/Artist - Song.lrc"
    lrc_file.write_text("[00:01.00]Текст", encoding="utf-8")

    await delete_track(rel)

    assert not (tmp_path / rel).exists()
    assert not lrc_file.exists()
