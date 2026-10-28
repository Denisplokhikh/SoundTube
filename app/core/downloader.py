from __future__ import annotations

import asyncio
import logging
import shutil
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import httpx
import yt_dlp

from app.config import settings
from app.core.naming import build_relative_path, unique_path
from app.core.tagging import Tags, embed_tags
from app.core.ytdlp import base_options
from app.platforms.base import TrackSpec, clean_text

logger = logging.getLogger(__name__)

USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/124.0 Safari/537.36"
)


class DownloadError(RuntimeError):
    pass


@dataclass(slots=True)
class DownloadResult:
    path: Path
    title: str
    artist: str | None
    album: str | None
    duration: float | None


async def download_track(
    spec: TrackSpec,
    *,
    platform: str,
    audio_format: str = "original",
) -> DownloadResult:
    return await asyncio.to_thread(_download_sync, spec, platform, audio_format)


def _download_sync(spec: TrackSpec, platform: str, audio_format: str) -> DownloadResult:
    settings.music_dir.mkdir(parents=True, exist_ok=True)
    tmp_root = settings.data_dir / "tmp"
    tmp_root.mkdir(parents=True, exist_ok=True)
    tmp_dir = Path(tempfile.mkdtemp(prefix="track-", dir=tmp_root))

    try:
        options = base_options(platform)
        options.update(
            {
                "ignoreerrors": False,
                "format": "bestaudio[ext=m4a]/bestaudio/best",
                "outtmpl": str(tmp_dir / "%(id)s.%(ext)s"),
                "noplaylist": True,
            }
        )
        if audio_format != "original":
            postprocessor: dict[str, Any] = {
                "key": "FFmpegExtractAudio",
                "preferredcodec": audio_format,
            }
            if audio_format == "mp3":
                postprocessor["preferredquality"] = settings.audio_quality
            options["postprocessors"] = [postprocessor]

        with yt_dlp.YoutubeDL(options) as ydl:
            info = ydl.extract_info(spec.url, download=True)

        if not info:
            raise DownloadError("yt-dlp не вернул информацию о треке")

        downloaded = _pick_downloaded_file(tmp_dir)
        title, artist, album, duration = _metadata(info, spec)

        relative = build_relative_path(
            title=title,
            artist=artist,
            album=album,
            extension=downloaded.suffix.lower(),
        )
        target = unique_path(settings.music_dir / relative)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(downloaded), str(target))

        cover = _fetch_cover(info)
        embed_tags(target, Tags(title=title, artist=artist, album=album), cover)

        return DownloadResult(
            path=target,
            title=title,
            artist=artist,
            album=album,
            duration=duration,
        )
    finally:
        shutil.rmtree(tmp_dir, ignore_errors=True)


def _pick_downloaded_file(tmp_dir: Path) -> Path:
    files = [
        path
        for path in tmp_dir.iterdir()
        if path.is_file() and path.suffix.lower() not in {".part", ".ytdl", ".temp"}
    ]
    if not files:
        raise DownloadError("После скачивания не осталось аудиофайла")
    return max(files, key=lambda path: path.stat().st_size)


def _metadata(info: dict, spec: TrackSpec) -> tuple[str, str | None, str | None, float | None]:
    title = clean_text(info.get("track")) or clean_text(info.get("title")) or spec.title
    artist = (
        clean_text(info.get("artist"))
        or clean_text(info.get("creator"))
        or clean_text(info.get("uploader"))
        or spec.artist
    )
    album = clean_text(info.get("album")) or spec.album
    duration = info.get("duration")
    if not isinstance(duration, (int, float)):
        duration = spec.duration
    return title, artist, album, float(duration) if duration else None


def _cover_url(info: dict) -> str | None:
    thumbnails = info.get("thumbnails") or []
    if thumbnails:
        best = max(
            thumbnails,
            key=lambda item: (item.get("width") or 0) * (item.get("height") or 0),
        )
        if best.get("url"):
            return best["url"]
    return info.get("thumbnail")


def _fetch_cover(info: dict) -> bytes | None:
    url = _cover_url(info)
    if not url:
        return None
    try:
        with httpx.Client(
            timeout=15.0,
            follow_redirects=True,
            headers={"User-Agent": USER_AGENT},
        ) as client:
            response = client.get(url)
            response.raise_for_status()
            if len(response.content) > 8 * 1024 * 1024:
                return None
            return response.content
    except Exception:
        logger.debug("Не удалось скачать обложку %s", url, exc_info=True)
        return None
