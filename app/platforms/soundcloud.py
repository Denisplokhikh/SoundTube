from __future__ import annotations

import logging
import re
from urllib.parse import urlparse

from app.core import ytdlp
from app.platforms.base import Collection, ResolveError, TrackSpec, clean_text

NAME = "soundcloud"

HOSTS = ("soundcloud.com", "m.soundcloud.com", "on.soundcloud.com")

logger = logging.getLogger(__name__)


def matches(url: str) -> bool:
    host = (urlparse(url).hostname or "").lower()
    return any(host == candidate or host.endswith(f".{candidate}") for candidate in HOSTS)


def _entry_id(entry: dict) -> str | None:
    identifier = entry.get("id")
    if identifier:
        return str(identifier)
    match = re.search(r"/tracks/(\d+)", str(entry.get("url") or ""))
    return match.group(1) if match else None


def _track_from_entry(entry: dict) -> TrackSpec | None:
    if not entry:
        return None
    url = entry.get("url") or entry.get("webpage_url")
    if not url:
        return None
    title = clean_text(entry.get("track")) or clean_text(entry.get("title"))
    if not title:
        return None

    artist = (
        clean_text(entry.get("artist"))
        or clean_text(entry.get("uploader"))
        or clean_text(entry.get("creator"))
    )
    duration = entry.get("duration")
    return TrackSpec(
        title=title,
        url=str(url),
        artist=artist,
        duration=float(duration) if isinstance(duration, (int, float)) else None,
        source_id=str(entry.get("id")) if entry.get("id") else None,
    )


def _track_from_meta(entry: dict, meta: dict) -> TrackSpec | None:
    """Собирает трек из ответа api-v2 (там длительность в миллисекундах)."""
    title = clean_text(meta.get("title")) or clean_text(meta.get("track"))
    if not title:
        return None

    user = meta.get("user") if isinstance(meta.get("user"), dict) else {}
    publisher = (
        meta.get("publisher_metadata") if isinstance(meta.get("publisher_metadata"), dict) else {}
    )
    artist = (
        clean_text(user.get("username"))
        or clean_text(publisher.get("artist"))
        or clean_text(meta.get("artist"))
    )

    duration = meta.get("duration")
    url = (
        clean_text(meta.get("permalink_url"))
        or clean_text(entry.get("url"))
        or clean_text(meta.get("webpage_url"))
    )
    if not url:
        return None

    return TrackSpec(
        title=title,
        url=url,
        artist=artist,
        duration=float(duration) / 1000 if isinstance(duration, (int, float)) else None,
        source_id=_entry_id(meta) or _entry_id(entry),
    )


def _collect_tracks(entries: list[dict]) -> list[TrackSpec]:
    """Разбирает сет, дозапрашивая метаданные для «прозрачных» записей."""
    specs: list[TrackSpec | None] = [_track_from_entry(entry) for entry in entries]

    pending = [index for index, spec in enumerate(specs) if spec is None]
    if pending:
        ids = [identifier for index in pending if (identifier := _entry_id(entries[index]))]
        try:
            meta = ytdlp.soundcloud_track_meta(ids) if ids else {}
        except Exception as exc:
            logger.warning("SoundCloud: не удалось дозапросить метаданные треков: %s", exc)
            meta = {}

        for index in pending:
            entry = entries[index]
            info = meta.get(_entry_id(entry) or "")
            spec = _track_from_meta(entry, info) if info else None
            if spec is not None:
                specs[index] = spec

    return [spec for spec in specs if spec is not None]


def resolve(url: str) -> Collection:
    try:
        info = ytdlp.extract_flat(url, NAME)
    except Exception as exc:
        raise ResolveError(f"SoundCloud: {exc}") from exc

    if not info:
        raise ResolveError("SoundCloud вернул пустой ответ")

    entries = [entry for entry in (info.get("entries") or []) if entry]
    if entries:
        tracks = _collect_tracks(entries)
        if not tracks:
            raise ResolveError("В сете не нашлось доступных треков")
        return Collection(
            platform=NAME,
            kind="set",
            title=clean_text(info.get("title")) or "SoundCloud set",
            thumbnail=info.get("thumbnail"),
            tracks=tracks,
        )

    track = _track_from_entry(info)
    if not track:
        raise ResolveError("Не удалось разобрать трек SoundCloud")
    return Collection(
        platform=NAME,
        kind="track",
        title=track.title,
        thumbnail=info.get("thumbnail"),
        tracks=[track],
    )
