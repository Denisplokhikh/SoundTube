from __future__ import annotations

from typing import Any

import yt_dlp

from app.config import settings


def base_options(platform: str, *, flat: bool = False) -> dict[str, Any]:
    options: dict[str, Any] = {
        "quiet": True,
        "no_warnings": True,
        "noprogress": True,
        "ignoreerrors": True,
        "retries": settings.retries,
        "fragment_retries": settings.retries,
        "socket_timeout": 30,
        "extract_flat": "in_playlist" if flat else False,
        "skip_download": flat,
    }
    if flat:
        options["playlistend"] = settings.limit_tracks

    cookies = settings.cookies_for(platform)
    if cookies:
        options["cookiefile"] = str(cookies)
    if settings.proxy:
        options["proxy"] = settings.proxy
    return options


def extract_flat(url: str, platform: str) -> dict[str, Any] | None:
    with yt_dlp.YoutubeDL(base_options(platform, flat=True)) as ydl:
        return ydl.extract_info(url, download=False)


def probe(url: str, platform: str) -> dict[str, Any] | None:
    with yt_dlp.YoutubeDL(base_options(platform)) as ydl:
        return ydl.extract_info(url, download=False)
