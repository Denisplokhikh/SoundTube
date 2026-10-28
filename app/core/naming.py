from __future__ import annotations

import re
import unicodedata
from pathlib import Path

INVALID_CHARS = set('<>:"/\\|?*')
RESERVED_NAMES = {
    "CON",
    "PRN",
    "AUX",
    "NUL",
    *(f"COM{index}" for index in range(1, 10)),
    *(f"LPT{index}" for index in range(1, 10)),
}


def sanitize(value: str | None, fallback: str = "Unknown", max_length: int = 120) -> str:
    """Make a string safe as a single file or folder name on Windows and POSIX."""
    if not value:
        return fallback

    value = unicodedata.normalize("NFKC", value)
    cleaned = "".join("_" if char in INVALID_CHARS or ord(char) < 32 else char for char in value)
    cleaned = re.sub(r"\s+", " ", cleaned).strip(" .")

    if len(cleaned) > max_length:
        cleaned = cleaned[:max_length].rstrip(" .")
    if not cleaned:
        return fallback
    if cleaned.upper() in RESERVED_NAMES:
        cleaned = f"_{cleaned}"
    return cleaned


def build_relative_path(
    *,
    title: str,
    artist: str | None,
    album: str | None,
    extension: str,
) -> Path:
    artist_part = sanitize(artist, "Unknown artist", 80)
    title_part = sanitize(title, "Untitled", 120)
    filename = sanitize(f"{artist_part} - {title_part}", title_part, 150)

    album_part = sanitize(album, "", 80) if album else ""
    name = f"{filename}{extension}"
    if album_part:
        return Path(artist_part) / album_part / name
    return Path(artist_part) / name


def unique_path(path: Path) -> Path:
    if not path.exists():
        return path
    for index in range(2, 1000):
        candidate = path.with_name(f"{path.stem} ({index}){path.suffix}")
        if not candidate.exists():
            return candidate
    raise FileExistsError(f"Не удалось подобрать свободное имя для {path}")
