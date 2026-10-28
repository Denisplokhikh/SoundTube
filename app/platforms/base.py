from __future__ import annotations

from dataclasses import dataclass, field


class ResolveError(RuntimeError):
    """Raised when a URL cannot be turned into a track list."""


class UnsupportedURL(ResolveError):
    """Raised when no platform knows how to handle the URL."""


@dataclass(slots=True)
class TrackSpec:
    title: str
    url: str
    artist: str | None = None
    album: str | None = None
    duration: float | None = None
    source_id: str | None = None


@dataclass(slots=True)
class Collection:
    platform: str
    kind: str
    title: str
    tracks: list[TrackSpec] = field(default_factory=list)
    thumbnail: str | None = None


def clean_text(value: object) -> str | None:
    if not isinstance(value, str):
        return None
    value = " ".join(value.split()).strip()
    return value or None
