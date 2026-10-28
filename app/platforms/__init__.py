from __future__ import annotations

from app.platforms import soundcloud, youtube
from app.platforms.base import Collection, ResolveError, TrackSpec, UnsupportedURL

PLATFORMS = (youtube, soundcloud)


def detect_platform(url: str) -> str | None:
    for platform in PLATFORMS:
        if platform.matches(url):
            return platform.NAME
    return None


def resolve(url: str) -> Collection:
    for platform in PLATFORMS:
        if platform.matches(url):
            try:
                return platform.resolve(url)
            except ResolveError:
                raise
    raise UnsupportedURL(
        "Поддерживаются YouTube Music (youtube.com, music.youtube.com) и SoundCloud"
    )


__all__ = [
    "Collection",
    "ResolveError",
    "TrackSpec",
    "UnsupportedURL",
    "detect_platform",
    "resolve",
]
