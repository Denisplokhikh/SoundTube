"""Поиск, парсинг и локальное хранение текстов песен (LRCLIB + описания + .lrc + теги)."""

from __future__ import annotations

import asyncio
import logging
import re
import time
from dataclasses import dataclass, field
from pathlib import Path
from urllib.parse import quote

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
from app.core.ytdlp import probe as ytdlp_probe
from app.platforms import detect_platform

logger = logging.getLogger(__name__)

LRCLIB_GET_URL = "https://lrclib.net/api/get"
LRCLIB_SEARCH_URL = "https://lrclib.net/api/search"
LYRICS_OVH_URL = "https://api.lyrics.ovh/v1"
REQUEST_TIMEOUT = 8.0
NEGATIVE_CACHE_TTL = 10 * 60.0
DESCRIPTION_TIMEOUT = 25.0

_TIME_TAG_RE = re.compile(r"\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]")
_OFFSET_TAG_RE = re.compile(r"^\[offset:\s*([+-]?\d+)\s*\]$", re.IGNORECASE)
_META_TAG_RE = re.compile(
    r"^\[(?:ar|ti|al|au|by|length|re|ve|id|tool|offset):[^\]]*\]$",
    re.IGNORECASE,
)
# Слова-мусор из названий на YouTube/SoundCloud: «(Official Music Video)»,
# «[4K Remaster]», «[HD]» и т.п. Целиком скобку убираем только если она
# состоит из таких слов — «(Live)», «(Remix)», «(Acoustic)» остаются.
_NOISE_TOKEN = (
    r"(?:official|music|video|audio|lyrics?|lyric|visuali[sz]er|hd|hq|uhd|"
    r"4k|8k|remaster(?:ed)?|explicit|clean|clip\s*officiel|m/?v|mv|\d{4})"
)
_NOISE_RE = re.compile(
    rf"\s*[\(\[]\s*{_NOISE_TOKEN}(?:[\s\-–—/,&+]*{_NOISE_TOKEN})*\s*[\)\]]",
    re.IGNORECASE,
)
_TRAILING_NOISE_RE = re.compile(
    r"(?:\s*[-–—|/]\s*|\s+)(?:official\s*(?:music\s*)?(?:video|audio)|"
    r"lyrics?\s*(?:video)?|lyrics?|hd|hq|uhd|4k|8k|"
    r"remaster(?:ed)?(?:\s*\d{4})?|visuali[sz]er)\s*$",
    re.IGNORECASE,
)
_ANY_BRACKET_RE = re.compile(r"\s*[\(\[]\s*[^\)\]]*[\)\]]")
_TRAILING_PROD_RE = re.compile(
    r"\s*(?:[\(\[]\s*)?\bprod\.?(?:\s+by)?\s+[^\)\]]*[\)\]]?\s*$",
    re.IGNORECASE,
)
_TRAILING_PLUS_RE = re.compile(r"\s*\+\s*[^+]+$")
_SEPARATOR_RE = re.compile(r"\s+[-–—:|]\s+")
_FEAT_RE = re.compile(
    r"\s*(?:[\(\[]\s*(?:feat|ft)\b\.?\s+[^\)\]]+[\)\]]|\b(?:feat|ft)\b\.?\s+.+$)",
    re.IGNORECASE,
)
_JUNK_ARTISTS = {"unknown artist", "неизвестный исполнитель", "unknown", "va", "various artists"}
# Строки описания трека, которые точно не являются текстом песни:
# ссылки, кредиты, теги и призывы подписаться.
_PROMO_LINE_RE = re.compile(
    r"^(?:https?://|www\.|t\.me/|tgk?\b|telegram\b|vk\.com/|instagram\b|@[a-z0-9_.]{2,}|#\w|"
    r"prod\.?\s|prod\b\s*by|mixed\b|master(?:ing)?\b|mix\b|artwork\b|design\b|"
    r"follow\b|subscribe\b|listen\b|download\b|stream\b|spotify\b|apple music\b|"
    r"soundcloud\.com/|youtube\.com/|youtu\.be/)",
    re.IGNORECASE,
)
_WORD_RE = re.compile(r"[0-9a-zа-яё]+", re.IGNORECASE)

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


def _strip_noise(value: str) -> str:
    """Убирает из строки скобочный и хвостовой мусор площадок."""
    text = _NOISE_RE.sub("", value or "")
    for _ in range(3):
        cleaned = _TRAILING_NOISE_RE.sub("", text).strip()
        if cleaned == text:
            break
        text = cleaned
    return text.strip(" -–—:|")


def _split_artist_title(title: str) -> tuple[str, str] | None:
    """Делит «Artist - Title» (в т.ч. через –, —, :, |) на две части."""
    match = _SEPARATOR_RE.search(title)
    if not match:
        return None
    left = title[: match.start()].strip(" -–—:|")
    right = title[match.end() :].strip(" -–—:|")
    if 1 < len(left) <= 70 and len(right) > 1:
        return left, right
    return None


def _normalize(value: str) -> str:
    return re.sub(r"[^0-9a-zа-яё]+", "", (value or "").lower())


def build_query_variants(title: str, artist: str | None) -> list[tuple[str, str | None]]:
    """Собирает варианты запроса к LRCLIB по порядку приоритета.

    Метаданные из YouTube Music и SoundCloud часто содержат в теге артиста
    канал загрузчика («HollywoodRecordsVEVO», «Andrew Cant»), а в названии —
    «Artist - Song». Поэтому первым идёт вариант, разобранный из названия.
    """
    clean_title = _strip_noise(_FEAT_RE.sub("", _strip_noise(title)) or title)
    clean_artist = _strip_noise(_FEAT_RE.sub("", _strip_noise(artist or "")) or (artist or ""))
    if clean_artist.lower() in _JUNK_ARTISTS:
        clean_artist = ""

    variants: list[tuple[str, str | None]] = []

    def push(candidate_title: str, candidate_artist: str | None) -> None:
        value = (candidate_title or "").strip(" -–—:|")
        who = (candidate_artist or "").strip(" -–—:|") or None
        if len(value) < 2:
            return
        pair = (value, who)
        if pair not in variants:
            variants.append(pair)

    split = _split_artist_title(clean_title)
    if split:
        left, right = split
        push(right, left)
        # «Song +FEAT» -> «Song»
        core_right = _TRAILING_PLUS_RE.sub("", right).strip(" -–—:|")
        if core_right and core_right != right:
            push(core_right, left)
        push(right, clean_artist)

    push(clean_title, clean_artist)

    # Вариант без содержимого скобок и хвостов «prod. by ...»:
    # «LAXVE (PROD. NOIRNOVEL)» -> «LAXVE», «10percOFF prod. by s7eepy» -> «10percOFF».
    core = _TRAILING_PROD_RE.sub("", _ANY_BRACKET_RE.sub("", clean_title)).strip(" -–—:|")
    if core and core != clean_title:
        core_split = _split_artist_title(core)
        if core_split:
            push(core_split[1], core_split[0])
        push(core, clean_artist)

    # «SMARTCHAIN +ARLEKIN 40 000» -> «SMARTCHAIN»
    base = core or clean_title
    trimmed = _TRAILING_PLUS_RE.sub("", base).strip(" -–—:|")
    if trimmed and trimmed != base:
        push(trimmed, clean_artist)

    return variants[:5]


def clean_query_metadata(title: str, artist: str | None) -> tuple[str, str]:
    """Возвращает самый вероятный вариант «название + артист» для запроса."""
    variants = build_query_variants(title, artist)
    if not variants:
        return (title or "").strip(), (artist or "").strip()
    best_title, best_artist = variants[0]
    return best_title, best_artist or ""


def _artist_score(candidate_artist: str | None, target_artist: str | None) -> int:
    if not target_artist:
        return 0
    left = _normalize(candidate_artist or "")
    right = _normalize(target_artist)
    if not left or not right:
        return 0
    if left == right:
        return 2
    if left in right or right in left:
        return 1
    return 0


def _is_plausible(
    item: dict,
    *,
    target_title: str | None,
    target_artist: str | None,
    target_duration: float | None,
) -> bool:
    """Отсекает чужие песни с тем же названием, которые отдаёт нечёткий поиск.

    Результат принимается, если совпал артист либо в точности совпало название
    и длина отличается не больше чем на 3 секунды.
    """
    if not target_artist:
        return True
    if _artist_score(item.get("artistName"), target_artist) >= 1:
        return True
    duration = item.get("duration")
    if not target_duration or not isinstance(duration, (int, float)):
        return False
    return (
        abs(float(duration) - target_duration) <= 3.0
        and _normalize(item.get("trackName")) == _normalize(target_title or "")
    )


def _pick_best_candidate(
    candidates: list[dict],
    target_duration: float | None,
    target_artist: str | None = None,
    target_title: str | None = None,
    tolerance: float | None = 15.0,
) -> dict | None:
    if not candidates:
        return None

    pool = [
        item
        for item in candidates
        if _is_plausible(
            item,
            target_title=target_title,
            target_artist=target_artist,
            target_duration=target_duration,
        )
    ]
    if not pool:
        return None

    if tolerance is not None and target_duration:
        close = [
            item
            for item in pool
            if not isinstance(item.get("duration"), (int, float))
            or abs(float(item["duration"]) - target_duration) <= tolerance
        ]
        if close:
            pool = close

    def score(item: dict) -> tuple[int, int, int, float]:
        has_synced = 1 if (item.get("syncedLyrics") or "").strip() else 0
        has_plain = 1 if (item.get("plainLyrics") or "").strip() else 0
        is_inst = 1 if item.get("instrumental") else 0
        dur = item.get("duration")
        if target_duration and isinstance(dur, (int, float)):
            diff = abs(float(dur) - target_duration)
            within_tolerance = 1 if diff <= 15.0 else 0
            neg_diff = -diff
        else:
            within_tolerance = 1
            neg_diff = 0.0
        return (
            _artist_score(item.get("artistName"), target_artist),
            within_tolerance,
            has_synced * 3 + has_plain * 2 + is_inst,
            neg_diff,
        )

    best = max(pool, key=score)
    if (
        (best.get("syncedLyrics") or "").strip()
        or (best.get("plainLyrics") or "").strip()
        or best.get("instrumental")
    ):
        return best
    return None


def _has_lyrics(payload: dict | None) -> bool:
    if not payload:
        return False
    return bool(
        (payload.get("syncedLyrics") or "").strip()
        or (payload.get("plainLyrics") or "").strip()
        or payload.get("instrumental")
    )


async def _lookup_variant(
    client: httpx.AsyncClient,
    track_title: str,
    track_artist: str | None,
    *,
    album: str | None,
    duration: float | None,
) -> dict | None:
    """Пробует точный `/api/get` и затем `/api/search` для одного варианта."""
    if track_artist:
        params: dict[str, str | int] = {
            "track_name": track_title,
            "artist_name": track_artist,
        }
        if album:
            params["album_name"] = album
        if duration and duration > 0:
            params["duration"] = round(duration)

        try:
            response = await client.get(LRCLIB_GET_URL, params=params)
            if response.status_code == 200:
                data = response.json()
                if isinstance(data, dict) and _has_lyrics(data):
                    return data
        except (httpx.HTTPError, ValueError):
            logger.debug("Ошибка запроса к LRCLIB /api/get", exc_info=True)
            return None

    search_params: dict[str, str] = {"track_name": track_title}
    if track_artist:
        search_params["artist_name"] = track_artist
    else:
        search_params["q"] = track_title

    try:
        response = await client.get(LRCLIB_SEARCH_URL, params=search_params)
        if response.status_code != 200:
            return None
        items = response.json()
    except (httpx.HTTPError, ValueError):
        logger.debug("Ошибка запроса к LRCLIB /api/search", exc_info=True)
        return None

    if not isinstance(items, list):
        return None
    return _pick_best_candidate(
        items,
        duration,
        target_artist=track_artist,
        target_title=track_title,
    )


async def _free_search(
    client: httpx.AsyncClient,
    query: str,
    *,
    duration: float | None,
    artist: str | None,
    title: str | None,
) -> dict | None:
    """Свободный поиск по `q` — принимает только близкие по длительности версии."""
    try:
        response = await client.get(LRCLIB_SEARCH_URL, params={"q": query})
        if response.status_code != 200:
            return None
        items = response.json()
    except (httpx.HTTPError, ValueError):
        logger.debug("Ошибка свободного поиска LRCLIB", exc_info=True)
        return None

    if not isinstance(items, list):
        return None
    return _pick_best_candidate(
        items,
        duration,
        target_artist=artist,
        target_title=title,
        tolerance=10.0,
    )


async def fetch_lrclib(
    *,
    title: str,
    artist: str | None,
    album: str | None = None,
    duration: float | None = None,
) -> dict | None:
    """Ищет текст песни в LRCLIB: перебирает варианты запроса, затем свободный поиск."""
    variants = build_query_variants(title, artist)
    if not variants:
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
        for track_title, track_artist in variants:
            payload = await _lookup_variant(
                client,
                track_title,
                track_artist,
                album=album,
                duration=duration,
            )
            if _has_lyrics(payload):
                return payload

        attempts: list[tuple[str, str, str | None]] = []
        for track_title, track_artist in variants[:2]:
            query = " ".join(part for part in (track_title, track_artist) if part).strip()
            if query and all(query != item[0] for item in attempts):
                attempts.append((query, track_title, track_artist))

        first_title, first_artist = variants[0]
        if all(first_title != item[0] for item in attempts):
            attempts.append((first_title, first_title, first_artist))

        for query, target_title, target_artist in attempts[:3]:
            payload = await _free_search(
                client,
                query,
                duration=duration,
                artist=target_artist,
                title=target_title,
            )
            if _has_lyrics(payload):
                return payload

    return None


def description_to_lyrics(description: str | None) -> str | None:
    """Достаёт текст песни из описания загрузки SoundCloud/YouTube.

    В андерграунде авторы часто вставляют текст прямо в описание вместе со
    ссылками и кредитами. Возвращает очищенный текст или None, если на текст
    это не похоже.
    """
    raw = (description or "").replace("\r\n", "\n").replace("\r", "\n").strip()
    if not raw:
        return None

    kept: list[str] = []
    for line in raw.split("\n"):
        text = line.strip()
        if not text:
            if kept and kept[-1] != "":
                kept.append("")
            continue
        if _PROMO_LINE_RE.match(text):
            continue
        if len(text) <= 60 and re.search(r"(?:https?://|t\.me/|@[a-z0-9_]{3,})", text, re.IGNORECASE):
            continue
        kept.append(text)

    while kept and kept[0] == "":
        kept.pop(0)
    while kept and kept[-1] == "":
        kept.pop()

    text = "\n".join(kept).strip()
    if not text:
        return None

    lines = [line for line in kept if line]
    words = _WORD_RE.findall(text)
    song_lines = [line for line in lines if len(_WORD_RE.findall(line)) >= 3]
    if len(words) < 20 or len(lines) < 6 or len(song_lines) < max(4, len(lines) // 2):
        return None

    return text


def _description_from_url(url: str) -> str | None:
    try:
        platform = detect_platform(url) or ""
    except Exception:
        platform = ""

    try:
        info = ytdlp_probe(url, platform)
    except Exception:
        logger.debug("Не удалось получить описание трека %s", url, exc_info=True)
        return None

    if not isinstance(info, dict):
        return None
    return description_to_lyrics(info.get("description"))


async def fetch_description_lyrics(source_url: str | None) -> str | None:
    """Текст из описания загрузки — для треков, которых нет в базах текстов."""
    url = (source_url or "").strip()
    if not url.startswith("http"):
        return None

    try:
        return await asyncio.wait_for(
            asyncio.to_thread(_description_from_url, url),
            timeout=DESCRIPTION_TIMEOUT,
        )
    except (TimeoutError, asyncio.TimeoutError):
        logger.debug("Таймаут получения описания %s", url)
        return None


async def fetch_lyrics_ovh(title: str, artist: str | None) -> str | None:
    """Запасной источник простого (несинхронного) текста — api.lyrics.ovh."""
    clean_title, clean_artist = clean_query_metadata(title, artist)
    if not clean_title or not clean_artist:
        return None

    url = f"{LYRICS_OVH_URL}/{quote(clean_artist)}/{quote(clean_title)}"
    headers = {
        "Accept": "application/json",
        "User-Agent": f"SoundTube/{__version__}",
    }

    try:
        async with httpx.AsyncClient(
            timeout=REQUEST_TIMEOUT,
            headers=headers,
            proxy=settings.proxy,
        ) as client:
            response = await client.get(url)
        if response.status_code != 200:
            return None
        data = response.json()
    except (httpx.HTTPError, ValueError):
        logger.debug("Ошибка запроса к lyrics.ovh", exc_info=True)
        return None

    if not isinstance(data, dict):
        return None

    text = (data.get("lyrics") or "").replace("\r\n", "\n").strip()
    if not text:
        return None
    # Иногда вместо текста приходит заглушка «Paroles de la chanson ...».
    if text.lower().startswith("paroles de la chanson") and "\n" not in text:
        return None
    return re.sub(r"\n{3,}", "\n\n", text)


async def get_track_lyrics(
    audio_path: Path,
    relative_path: str,
    *,
    refresh: bool = False,
    source_url: str | None = None,
) -> LyricsResult:
    """Возвращает текст для трека: с диска, затем LRCLIB, описание загрузки, lyrics.ovh."""
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

    raw_lyrics = ""
    source = "lrclib"
    instrumental = False

    if payload is not None:
        if payload.get("instrumental") and not (
            payload.get("syncedLyrics") or payload.get("plainLyrics")
        ):
            instrumental = True
        else:
            raw_lyrics = (payload.get("syncedLyrics") or payload.get("plainLyrics") or "").strip()

    if not raw_lyrics and not instrumental:
        described = await fetch_description_lyrics(source_url)
        if described:
            raw_lyrics, source = described, "description"

    if not raw_lyrics and not instrumental:
        fallback = await fetch_lyrics_ovh(title, artist)
        if fallback:
            raw_lyrics, source = fallback, "lyrics.ovh"

    if instrumental:
        _miss_cache[relative_path] = (time.monotonic(), True)
        return LyricsResult(
            path=relative_path,
            found=False,
            instrumental=True,
            source="lrclib",
        )

    if not raw_lyrics:
        _miss_cache[relative_path] = (time.monotonic(), False)
        if not refresh:
            return LyricsResult(path=relative_path, found=False)
        local = read_local_lyrics(audio_path, relative_path)
        return local or LyricsResult(path=relative_path, found=False)

    _miss_cache.pop(relative_path, None)
    save_lrc_file(audio_path, raw_lyrics)
    return _build_result(relative_path, raw_lyrics, source=source)


def clear_lyrics_cache() -> None:
    _miss_cache.clear()
