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


SOUNDCLOUD_API_TRACKS = "https://api-v2.soundcloud.com/tracks"
SOUNDCLOUD_IDS_PER_REQUEST = 50


def soundcloud_track_meta(ids: list[str], platform: str = "soundcloud") -> dict[str, dict[str, Any]]:
    """Метаданные треков SoundCloud пачками, одним запросом на 50 штук.

    Станции и подборки «discover» yt-dlp отдаёт как url_transparent: в записи
    есть только id, без названия и исполнителя. Дозапрашиваем их у API.
    """
    unique = [str(item) for item in dict.fromkeys(ids) if item]
    if not unique:
        return {}

    result: dict[str, dict[str, Any]] = {}
    with yt_dlp.YoutubeDL(base_options(platform)) as ydl:
        extractor = ydl.get_info_extractor("Soundcloud")
        extractor.initialize()
        for start in range(0, len(unique), SOUNDCLOUD_IDS_PER_REQUEST):
            chunk = unique[start : start + SOUNDCLOUD_IDS_PER_REQUEST]
            # Публичного API у yt-dlp нет: client_id и запросы живут в экстракторе.
            data = extractor._call_api(  # noqa: SLF001
                SOUNDCLOUD_API_TRACKS,
                None,
                query={"ids": ",".join(chunk)},
            )
            for item in data or []:
                if isinstance(item, dict) and item.get("id") is not None:
                    result[str(item["id"])] = item
    return result
