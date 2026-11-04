"""Поиск, парсинг и локальное хранение текстов песен (LRCLIB + .lrc + теги)."""

from __future__ import annotations

import logging
import re
import time
from dataclasses import dataclass, field
from pathlib import Path

import httpx
import mutagen
from mutagen.flac import FLAC
from mutagen.mp3 import MP3
from mutagen.mp4 import MP4
from mutagen.oggopus import OggOpus
from mutagen.oggvorbis import OggVorbis

from app import __version__
from app.config import settings
from app.core.library import read_track_meta

logger = logging.getLogger(__name__)

LRCLIB_GET_URL = "https://lrclib.net/api/get"
LRCLIB_SEARCH_URL = "https://lrclib.net/api/search"
REQUEST_TIMEOUT = 8.0
NEGATIVE_CACHE_TTL = 10 * 60.0

_TIME_TAG_RE = re.compile(r"\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]")
_OFFSET_TAG_RE = re.compile(r"^\[offset:\s*([+-]?\d+)\s*\]$", re.IGNORECASE)
_META_TAG_RE = re.compile(
    r"^\[(?:ar|ti|al|au|by|length|re|ve|id|tool|offset):[^\]]*\]$",
    re.IGNORECASE,
)
_NOISE_RE = re.compile(
    r"\s*[\(\[]"
    r"(?:official\s*(?:music\s*)?(?:video|audio|lyric\s*video|visualizer)|"
    r"lyrics?\s*(?:video)?|audio|hd|hq|4k|remastered(?:\s*\d{4})?|"
    r"explicit|clean|visualizer|clip\s*officiel)"
    r"[\)\]]",
    re.IGNORECASE,
)
_FEAT_RE = re.compile(
    r"\s*(?:[\(\[]\s*(?:feat|ft)\b\.?\s+[^\)\]]+[\)\]]|\b(?:feat|ft)\b\.?\s+.+$)",
    re.IGNORECASE,
)

# Кэш неудачных сетевых запросов по пути файла, чтобы не дёргать API повторно.
_miss_cache: dict[str, tuple[float, bool]] = {}


@dataclass(slots=True)
class LyricLine:
    time: float
    text: str


@dataclass(slots=True)
class LyricsResult:
    path: str
    found: bool
    synced: bool = False
    instrumental: bool = False
    source: str | None = None
    plain: str | None = None
    lrc: str | None = None
    lines: list[LyricLine] = field(default_factory=list)


def lrc_path_for(audio_path: Path) -> Path:
    return audio_path.with_suffix(".lrc")


def parse_lrc(raw: str) -> tuple[list[LyricLine], str]:
    """Разбирает `.lrc`-текст в отсортированный список строк с таймкодами и чистый текст."""
    offset_sec = 0.0
    lines: list[LyricLine] = []
    plain_lines: list[str] = []

    for raw_line in raw.splitlines():
        stripped = raw_line.strip()
        if not stripped:
            continue

        offset_match = _OFFSET_TAG_RE.match(stripped)
        if offset_match:
            try:
                offset_sec = int(offset_match.group(1)) / 1000.0
            except ValueError:
                pass
            continue

        matches = list(_TIME_TAG_RE.finditer(stripped))
        if not matches:
            if _META_TAG_RE.match(stripped):
                continue
            plain_lines.append(stripped)
            continue

        text = _TIME_TAG_RE.sub("", stripped).strip()
        if text:
            plain_lines.append(text)

        for match in matches:
            minutes = int(match.group(1))
            seconds = int(match.group(2))
            fraction_raw = match.group(3) or "0"
            fraction = int(fraction_raw) / (10 ** len(fraction_raw))
            timestamp = max(0.0, round(minutes * 60 + seconds + fraction - offset_sec, 3))
            lines.append(LyricLine(time=timestamp, text=text))

    lines.sort(key=lambda item: item.time)
    if lines and not plain_lines:
        plain_lines = [item.text for item in lines if item.text]

    return lines, "\n".join(plain_lines).strip()


def _build_result(relative_path: str, raw: str, source: str) -> LyricsResult:
    text = raw.strip()
    if not text:
        return LyricsResult(path=relative_path, found=False)

    lines, plain = parse_lrc(text)
    if lines:
        return LyricsResult(
            path=relative_path,
            found=True,
            synced=True,
            source=source,
            plain=plain or None,
            lrc=text,
            lines=lines,
        )

    return LyricsResult(
        path=relative_path,
        found=True,
        synced=False,
        source=source,
        plain=text,
        lrc=None,
        lines=[],
    )


def read_local_lyrics(audio_path: Path, relative_path: str) -> LyricsResult | None:
    """Читает текст из соседнего `.lrc` файла или из встроенных тегов аудиофайла."""
    sidecar = lrc_path_for(audio_path)
    if sidecar.is_file():
        try:
            content = sidecar.read_text(encoding="utf-8", errors="replace").strip()
            if content:
                return _build_result(relative_path, content, source="lrc")
        except OSError:
            logger.debug("Не удалось прочитать %s", sidecar, exc_info=True)

    embedded = extract_embedded_lyrics(audio_path)
    if embedded:
        return _build_result(relative_path, embedded, source="embedded")

    return None


def extract_embedded_lyrics(path: Path) -> str | None:
    """Извлекает текст песни из тегов аудиофайла, если он там есть."""
    try:
        audio = mutagen.File(path)
    except Exception:
        return None

    if audio is None or not getattr(audio, "tags", None):
        return None

    if isinstance(audio, MP3):
        for frame in audio.tags.getall("USLT"):
            if getattr(frame, "text", None) and str(frame.text).strip():
                return str(frame.text).strip()
        return None

    if isinstance(audio, MP4):
        values = audio.tags.get("\xa9lyr")
        if values and str(values[0]).strip():
            return str(values[0]).strip()
        return None

    if isinstance(audio, (FLAC, OggOpus, OggVorbis)):
        for key in ("lyrics", "unsyncedlyrics", "LYRICS", "UNSYNCEDLYRICS"):
            values = audio.tags.get(key)
            if values and str(values[0]).strip():
                return str(values[0]).strip()
        return None

    return None


def save_lrc_file(audio_path: Path, content: str) -> bool:
    """Сохраняет `.lrc` файл рядом с аудиофайлом."""
    text = content.strip()
    if not text:
        return False
    sidecar = lrc_path_for(audio_path)
    try:
        sidecar.write_text(text + "\n", encoding="utf-8")
        return True
    except OSError:
        logger.warning("Не удалось сохранить текст в %s", sidecar, exc_info=True)
        return False


def clean_query_metadata(title: str, artist: str | None) -> tuple[str, str]:
    """Очищает название и артиста от мусора площадок (Official Video, feat. и дублей)."""
    clean_title = _NOISE_RE.sub("", title or "").strip()
    clean_artist = (artist or "").strip()

    if clean_artist.lower() in {"unknown artist", "неизвестный исполнитель"}:
        clean_artist = ""

    if " - " in clean_title and (
        not clean_artist or clean_title.lower().startswith(f"{clean_artist.lower()} - ")
    ):
        left, right = clean_title.split(" - ", 1)
        if not clean_artist:
            clean_artist = left.strip()
        clean_title = right.strip()

    clean_title_no_feat = _FEAT_RE.sub("", clean_title).strip()
    if clean_title_no_feat:
        clean_title = clean_title_no_feat

    clean_artist_no_feat = _FEAT_RE.sub("", clean_artist).strip()
    if clean_artist_no_feat:
        clean_artist = clean_artist_no_feat

    return clean_title or title.strip(), clean_artist


def _pick_best_candidate(
    candidates: list[dict],
    target_duration: float | None,
) -> dict | None:
    if not candidates:
        return None

    def score(item: dict) -> tuple[int, int, float]:
        has_synced = 1 if (item.get("syncedLyrics") or "").strip() else 0
        has_plain = 1 if (item.get("plainLyrics") or "").strip() else 0
        is_inst = 1 if item.get("instrumental") else 0
        dur = item.get("duration")
        if target_duration and isinstance(dur, (int, float)):
            diff = abs(float(dur) - target_duration)
            # Отсекаем совсем далёкие по длительности версии (> 15 сек разницы),
            # если только нет лучшего совпадения.
            within_tolerance = 1 if diff <= 15.0 else 0
            neg_diff = -diff
        else:
            within_tolerance = 1
            neg_diff = 0.0
        return (within_tolerance, has_synced * 3 + has_plain * 2 + is_inst, neg_diff)

    best = max(candidates, key=score)
    if (
        (best.get("syncedLyrics") or "").strip()
        or (best.get("plainLyrics") or "").strip()
        or best.get("instrumental")
    ):
        return best
    return None


async def fetch_lrclib(
    *,
    title: str,
    artist: str | None,
    album: str | None = None,
    duration: float | None = None,
) -> dict | None:
    """Ищет текст песни в LRCLIB (сначала точный `/api/get`, затем `/api/search`)."""
    clean_title, clean_artist = clean_query_metadata(title, artist)
    if not clean_title:
        return None

    headers = {
        "Accept": "application/json",
        "User-Agent": f"SoundTube/{__version__} (https://github.com/Denisplokhikh/SoundTube)",
    }

    async with httpx.AsyncClient(
        timeout=REQUEST_TIMEOUT,
        follow_redirects=True,
        headers=headers,
        proxy=settings.proxy,
    ) as client:
        # 1. Точный запрос /api/get (требует и track_name, и artist_name)
        if clean_artist:
            params: dict[str, str | int] = {
                "track_name": clean_title,
                "artist_name": clean_artist,
            }
            if album:
                params["album_name"] = album
            if duration and duration > 0:
                params["duration"] = round(duration)

            try:
                response = await client.get(LRCLIB_GET_URL, params=params)
                if response.status_code == 200:
                    data = response.json()
                    if isinstance(data, dict) and (
                        data.get("syncedLyrics")
                        or data.get("plainLyrics")
                        or data.get("instrumental")
                    ):
                        return data
            except (httpx.HTTPError, ValueError):
                logger.debug("Ошибка запроса к LRCLIB /api/get", exc_info=True)
                return None

        # 2. Фоллбэк на /api/search (прощает расхождение длительности, альбома и артиста)
        search_params: dict[str, str] = {"track_name": clean_title}
        if clean_artist:
            search_params["artist_name"] = clean_artist
        else:
            search_params["q"] = clean_title

        try:
            response = await client.get(LRCLIB_SEARCH_URL, params=search_params)
            if response.status_code != 200:
                return None
            items = response.json()
            if not isinstance(items, list):
                return None
            return _pick_best_candidate(items, duration)
        except (httpx.HTTPError, ValueError):
            logger.debug("Ошибка запроса к LRCLIB /api/search", exc_info=True)
            return None


async def get_track_lyrics(
    audio_path: Path,
    relative_path: str,
    *,
    refresh: bool = False,
) -> LyricsResult:
    """Возвращает текст для трека: сперва с диска, при отсутствии — из LRCLIB с сохранением `.lrc`."""
    if not refresh:
        local = read_local_lyrics(audio_path, relative_path)
        if local is not None:
            return local

        cached_miss = _miss_cache.get(relative_path)
        if cached_miss is not None and time.monotonic() - cached_miss[0] < NEGATIVE_CACHE_TTL:
            return LyricsResult(
                path=relative_path,
                found=False,
                instrumental=cached_miss[1],
            )

    title, artist, album, duration = read_track_meta(audio_path)
    payload = await fetch_lrclib(
        title=title,
        artist=artist,
        album=album,
        duration=duration,
    )

    if payload is None:
        _miss_cache[relative_path] = (time.monotonic(), False)
        if not refresh:
            return LyricsResult(path=relative_path, found=False)
        local = read_local_lyrics(audio_path, relative_path)
        return local or LyricsResult(path=relative_path, found=False)

    if payload.get("instrumental") and not (
        payload.get("syncedLyrics") or payload.get("plainLyrics")
    ):
        _miss_cache[relative_path] = (time.monotonic(), True)
        return LyricsResult(
            path=relative_path,
            found=False,
            instrumental=True,
            source="lrclib",
        )

    raw_lyrics = (payload.get("syncedLyrics") or payload.get("plainLyrics") or "").strip()
    if not raw_lyrics:
        _miss_cache[relative_path] = (time.monotonic(), False)
        return LyricsResult(path=relative_path, found=False)

    _miss_cache.pop(relative_path, None)
    save_lrc_file(audio_path, raw_lyrics)
    return _build_result(relative_path, raw_lyrics, source="lrclib")


def clear_lyrics_cache() -> None:
    _miss_cache.clear()
