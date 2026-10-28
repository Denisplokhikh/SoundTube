from __future__ import annotations

from urllib.parse import parse_qs, urlparse

from app.core import ytdlp
from app.platforms.base import Collection, ResolveError, TrackSpec, clean_text

NAME = "youtube"

HOSTS = (
    "youtube.com",
    "music.youtube.com",
    "m.youtube.com",
    "youtube-nocookie.com",
    "youtu.be",
)


def matches(url: str) -> bool:
    host = (urlparse(url).hostname or "").lower()
    return any(host == candidate or host.endswith(f".{candidate}") for candidate in HOSTS)


def is_radio_url(url: str) -> bool:
    """YouTube radio/mix links (list=RD...) are endless auto-generated playlists."""
    query = parse_qs(urlparse(url).query)
    return any(value.startswith("RD") for value in query.get("list", []))


def single_video_url(url: str) -> str | None:
    parsed = urlparse(url)
    query = parse_qs(parsed.query)

    video_id = (query.get("v") or [None])[0]
    if video_id:
        return f"https://www.youtube.com/watch?v={video_id}"

    if parsed.hostname and parsed.hostname.endswith("youtu.be"):
        candidate = parsed.path.strip("/").split("/")[0]
        if candidate:
            return f"https://www.youtube.com/watch?v={candidate}"
    return None


def _track_from_entry(entry: dict) -> TrackSpec | None:
    if not entry:
        return None
    video_id = entry.get("id")
    url = entry.get("url") or entry.get("webpage_url")
    if not url and video_id:
        url = f"https://www.youtube.com/watch?v={video_id}"
    if not url:
        return None

    title = clean_text(entry.get("track")) or clean_text(entry.get("title"))
    if not title:
        return None

    artist = (
        clean_text(entry.get("artist"))
        or clean_text(entry.get("creator"))
        or clean_text(entry.get("uploader"))
        or clean_text(entry.get("channel"))
    )
    duration = entry.get("duration")
    return TrackSpec(
        title=title,
        url=str(url),
        artist=artist,
        album=clean_text(entry.get("album")),
        duration=float(duration) if isinstance(duration, (int, float)) else None,
        source_id=str(video_id) if video_id else None,
    )


def resolve(url: str) -> Collection:
    if is_radio_url(url):
        video_url = single_video_url(url)
        if video_url:
            url = video_url

    try:
        info = ytdlp.extract_flat(url, NAME)
    except Exception as exc:  # yt-dlp raises a wide range of errors
        raise ResolveError(f"YouTube: {exc}") from exc

    if not info:
        raise ResolveError("YouTube вернул пустой ответ")

    entries = [entry for entry in (info.get("entries") or []) if entry]
    if entries:
        tracks = [track for entry in entries if (track := _track_from_entry(entry))]
        if not tracks:
            raise ResolveError("В плейлисте не нашлось доступных треков")
        return Collection(
            platform=NAME,
            kind="playlist",
            title=clean_text(info.get("title")) or "YouTube playlist",
            thumbnail=_thumbnail(info),
            tracks=tracks,
        )

    track = _track_from_entry(info)
    if not track:
        raise ResolveError("Не удалось разобрать трек YouTube")
    return Collection(
        platform=NAME,
        kind="track",
        title=track.title,
        thumbnail=_thumbnail(info),
        tracks=[track],
    )


def _thumbnail(info: dict) -> str | None:
    thumbnails = info.get("thumbnails") or []
    if thumbnails:
        best = max(thumbnails, key=lambda item: item.get("width") or 0)
        return best.get("url")
    return info.get("thumbnail")
