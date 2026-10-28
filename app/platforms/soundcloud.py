from __future__ import annotations

from urllib.parse import urlparse

from app.core import ytdlp
from app.platforms.base import Collection, ResolveError, TrackSpec, clean_text

NAME = "soundcloud"

HOSTS = ("soundcloud.com", "m.soundcloud.com", "on.soundcloud.com")


def matches(url: str) -> bool:
    host = (urlparse(url).hostname or "").lower()
    return any(host == candidate or host.endswith(f".{candidate}") for candidate in HOSTS)


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


def resolve(url: str) -> Collection:
    try:
        info = ytdlp.extract_flat(url, NAME)
    except Exception as exc:
        raise ResolveError(f"SoundCloud: {exc}") from exc

    if not info:
        raise ResolveError("SoundCloud вернул пустой ответ")

    entries = [entry for entry in (info.get("entries") or []) if entry]
    if entries:
        tracks = [track for entry in entries if (track := _track_from_entry(entry))]
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
