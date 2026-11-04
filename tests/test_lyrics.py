from __future__ import annotations

import httpx
import pytest
from fastapi import HTTPException

from app.api.routes import API_REVISION, delete_track, health, lyrics
from app.config import settings
from app.core.lyrics import (
    LRCLIB_GET_URL,
    LRCLIB_SEARCH_URL,
    _pick_best_candidate,
    build_query_variants,
    clean_query_metadata,
    clear_lyrics_cache,
    description_to_lyrics,
    get_track_lyrics,
    parse_lrc,
)


def _make_track(root, relative: str, payload: bytes = b"fake-audio") -> None:
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(payload)


class _NoSources:
    """Сессия-заглушка: у трека нет записи library_sources."""

    async def get(self, model, key):
        return None


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


def test_clean_query_metadata_handles_dash_variants_and_remaster_tags():
    title, artist = clean_query_metadata(
        "Goo Goo Dolls – Iris [Official Music Video] [4K Remaster]",
        "Goo Goo Dolls",
    )
    assert (title, artist) == ("Iris", "Goo Goo Dolls")


def test_build_query_variants_prefers_artist_from_title_over_channel_name():
    variants = build_query_variants(
        "Miley Cyrus - Party In The U.S.A. (Official Video)",
        "HollywoodRecordsVEVO",
    )
    assert variants[0] == ("Party In The U.S.A.", "Miley Cyrus")


def test_build_query_variants_strips_prod_and_feature_tails():
    variants = build_query_variants(
        "MORE MORE #2010 +ARLEKIN 40 000 [prod. bliss]",
        "7wizard， ARLEKIN 40 000",
    )
    titles = [title for title, _ in variants]
    assert "MORE MORE #2010 +ARLEKIN 40 000" in titles
    assert "MORE MORE #2010" in titles


def test_pick_best_candidate_rejects_same_title_by_another_artist():
    items = [
        {
            "trackName": "MDMA",
            "artistName": "Lil Eli",
            "duration": 166.0,
            "syncedLyrics": "[00:01.00]чужой текст",
        }
    ]

    assert (
        _pick_best_candidate(
            items,
            180.0,
            target_artist="ARLEKIN 40 000",
            target_title="MDMA",
        )
        is None
    )


def test_pick_best_candidate_accepts_exact_title_and_duration_despite_junk_artist():
    items = [
        {
            "trackName": "Iris",
            "artistName": "Goo Goo Dolls",
            "duration": 215.6,
            "syncedLyrics": "[00:01.00]And I'd give up forever",
        }
    ]

    best = _pick_best_candidate(
        items,
        215.5,
        target_artist="matvey",
        target_title="Iris",
    )

    assert best is not None
    assert best["artistName"] == "Goo Goo Dolls"


async def test_health_exposes_api_revision():
    data = await health()
    assert data["api"] == API_REVISION


async def test_lyrics_reads_local_lrc_without_network(tmp_path, monkeypatch):
    clear_lyrics_cache()
    monkeypatch.setattr(settings, "music_dir", tmp_path)
    rel = "Artist/Album/Artist - Song.mp3"
    _make_track(tmp_path, rel)
    (tmp_path / "Artist/Album/Artist - Song.lrc").write_text(
        "[00:01.20]Первая строка\n[00:04.00]Вторая строка\n",
        encoding="utf-8",
    )

    response = await lyrics(rel, session=_NoSources())

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

    response = await lyrics(rel, session=_NoSources())

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
    second = await lyrics(rel, session=_NoSources())
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

    response = await lyrics(rel, session=_NoSources())

    assert response.found is True
    assert response.synced is False
    assert response.source == "lrclib"
    assert response.plain == "Просто текст без таймкодов"
    assert response.lines == []


async def test_lyrics_rejects_missing_or_unsafe_paths(tmp_path, monkeypatch):
    monkeypatch.setattr(settings, "music_dir", tmp_path)
    with pytest.raises(HTTPException) as excinfo:
        await lyrics("../outside.mp3", session=_NoSources())
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


def test_description_to_lyrics_extracts_text_and_drops_promo_lines():
    description = """t.me/sevenwizard

Я меняю планы
Я меняю planes
Лечу на багамы
Убиваю стресс
Голубая water белый коко в номер
Я меняю лайны звонит админ hotel

prod. bliss
mastering mix uplayd
https://soundcloud.com/7wizard"""

    text = description_to_lyrics(description)

    assert text is not None
    assert text.startswith("Я меняю планы")
    assert "t.me" not in text
    assert "prod. bliss" not in text
    assert "https://" not in text


def test_description_to_lyrics_rejects_link_only_descriptions():
    assert description_to_lyrics("t.me/ceoketamine") is None
    assert description_to_lyrics("") is None
    assert description_to_lyrics(None) is None
    assert description_to_lyrics("@xanzlighter\n@blisterpacked\nfollow me") is None


async def test_lyrics_falls_back_to_description_when_lrclib_misses(tmp_path, monkeypatch):
    clear_lyrics_cache()
    monkeypatch.setattr(settings, "music_dir", tmp_path)
    rel = "Artist/Artist - Underground.mp3"
    _make_track(tmp_path, rel)

    async def no_lrclib(**kwargs):
        return None

    async def fake_description(source_url):
        assert source_url == "https://soundcloud.com/artist/underground"
        return "Я меняю планы\nЯ меняю planes\nЛечу на багамы"

    monkeypatch.setattr("app.core.lyrics.fetch_lrclib", no_lrclib)
    monkeypatch.setattr("app.core.lyrics.fetch_description_lyrics", fake_description)

    result = await get_track_lyrics(
        tmp_path / rel,
        rel,
        source_url="https://soundcloud.com/artist/underground",
    )

    assert result.found is True
    assert result.synced is False
    assert result.source == "description"
    assert "Я меняю планы" in (result.plain or "")

    lrc_file = tmp_path / "Artist/Artist - Underground.lrc"
    assert lrc_file.is_file()


async def test_lyrics_falls_back_to_lyrics_ovh(tmp_path, monkeypatch):
    clear_lyrics_cache()
    monkeypatch.setattr(settings, "music_dir", tmp_path)
    rel = "Artist/Artist - Old Song.mp3"
    _make_track(tmp_path, rel)

    async def no_lrclib(**kwargs):
        return None

    async def no_description(source_url):
        return None

    async def fake_ovh(title, artist):
        return "Простой текст\nВторая строка"

    monkeypatch.setattr("app.core.lyrics.fetch_lrclib", no_lrclib)
    monkeypatch.setattr("app.core.lyrics.fetch_description_lyrics", no_description)
    monkeypatch.setattr("app.core.lyrics.fetch_lyrics_ovh", fake_ovh)

    result = await get_track_lyrics(tmp_path / rel, rel)

    assert result.found is True
    assert result.source == "lyrics.ovh"
    assert result.plain == "Простой текст\nВторая строка"

