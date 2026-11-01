"""Проверка новых релизов SoundTube на GitHub."""

from __future__ import annotations

import re
import time
from typing import Any

import httpx

from app import __version__
from app.config import settings

GITHUB_REPO = "Denisplokhikh/SoundTube"
RELEASES_URL = f"https://github.com/{GITHUB_REPO}/releases"
LATEST_RELEASE_API = f"https://api.github.com/repos/{GITHUB_REPO}/releases/latest"

# Кэш, чтобы окно и поллинг не дёргали GitHub API на каждый запрос.
CACHE_TTL = 30 * 60.0
FAILURE_TTL = 5 * 60.0
REQUEST_TIMEOUT = 6.0

_VERSION_RE = re.compile(r"v?(\d+(?:\.\d+)*)")

_cache: tuple[float, dict[str, Any], float] | None = None


def parse_version(value: str) -> tuple[int, ...]:
    """`v1.2.3-beta` -> `(1, 2, 3)`; пустой кортеж, если версии нет."""
    match = _VERSION_RE.match(value.strip())
    if not match:
        return ()
    return tuple(int(part) for part in match.group(1).split("."))


def is_newer(latest: str, current: str) -> bool:
    left = parse_version(latest)
    right = parse_version(current)
    if not left or not right:
        return False

    size = max(len(left), len(right))
    return left + (0,) * (size - len(left)) > right + (0,) * (size - len(right))


def _payload(current: str) -> dict[str, Any]:
    return {
        "current": current,
        "latest": None,
        "update_available": False,
        "url": RELEASES_URL,
        "published_at": None,
    }


async def check_for_update(force: bool = False) -> dict[str, Any]:
    """Возвращает статус обновления. Сетевые ошибки не считаются ошибкой."""
    global _cache

    now = time.monotonic()
    if not force and _cache and now - _cache[0] < _cache[2]:
        return _cache[1]

    result = _payload(__version__)
    ttl = FAILURE_TTL

    if settings.update_check:
        try:
            async with httpx.AsyncClient(
                timeout=REQUEST_TIMEOUT, follow_redirects=True
            ) as client:
                response = await client.get(
                    LATEST_RELEASE_API,
                    headers={
                        "Accept": "application/vnd.github+json",
                        "User-Agent": f"SoundTube/{__version__}",
                    },
                )
            response.raise_for_status()
            release = response.json()

            tag = str(release.get("tag_name") or "")
            if tag:
                result.update(
                    latest=tag,
                    update_available=is_newer(tag, __version__),
                    url=str(release.get("html_url") or RELEASES_URL),
                    published_at=release.get("published_at"),
                )
                ttl = CACHE_TTL
        except (httpx.HTTPError, ValueError):
            # Офлайн, rate limit или сменившийся формат ответа — молча живём дальше.
            pass

    _cache = (now, result, ttl)
    return result
