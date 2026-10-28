from __future__ import annotations

import base64
import logging
from dataclasses import dataclass
from pathlib import Path

logger = logging.getLogger(__name__)


@dataclass(slots=True)
class Tags:
    title: str
    artist: str | None = None
    album: str | None = None


def guess_image_mime(data: bytes) -> str:
    if data.startswith(b"\x89PNG"):
        return "image/png"
    if data.startswith(b"\xff\xd8"):
        return "image/jpeg"
    if data.startswith(b"RIFF") and b"WEBP" in data[:16]:
        return "image/webp"
    return "image/jpeg"


def embed_tags(path: Path, tags: Tags, cover: bytes | None = None) -> bool:
    """Write metadata into the audio file. Returns False when the format is unsupported."""
    suffix = path.suffix.lower()
    try:
        if suffix == ".mp3":
            return _tag_mp3(path, tags, cover)
        if suffix in {".m4a", ".mp4", ".aac"}:
            return _tag_m4a(path, tags, cover)
        if suffix == ".flac":
            return _tag_flac(path, tags, cover)
        if suffix in {".ogg", ".opus"}:
            return _tag_ogg(path, tags, cover)
    except Exception:  # tagging must never break a successful download
        logger.warning("Не удалось записать теги в %s", path, exc_info=True)
        return False

    logger.debug("Формат %s не поддерживает теги, пропускаю", suffix)
    return False


def _tag_mp3(path: Path, tags: Tags, cover: bytes | None) -> bool:
    from mutagen.id3 import APIC, TALB, TIT2, TPE1
    from mutagen.mp3 import MP3

    audio = MP3(path)
    if audio.tags is None:
        audio.add_tags()

    audio.tags.delall("TIT2")
    audio.tags.delall("TPE1")
    audio.tags.delall("TALB")
    audio.tags.delall("APIC")

    audio.tags.add(TIT2(encoding=3, text=tags.title))
    if tags.artist:
        audio.tags.add(TPE1(encoding=3, text=tags.artist))
    if tags.album:
        audio.tags.add(TALB(encoding=3, text=tags.album))
    if cover:
        audio.tags.add(
            APIC(
                encoding=3,
                mime=guess_image_mime(cover),
                type=3,
                desc="Cover",
                data=cover,
            )
        )
    audio.save()
    return True


def _tag_m4a(path: Path, tags: Tags, cover: bytes | None) -> bool:
    from mutagen.mp4 import MP4, MP4Cover

    audio = MP4(path)
    audio["\xa9nam"] = [tags.title]
    if tags.artist:
        audio["\xa9ART"] = [tags.artist]
    if tags.album:
        audio["\xa9alb"] = [tags.album]
    if cover:
        image_format = (
            MP4Cover.FORMAT_PNG
            if guess_image_mime(cover) == "image/png"
            else MP4Cover.FORMAT_JPEG
        )
        audio["covr"] = [MP4Cover(cover, imageformat=image_format)]
    audio.save()
    return True


def _tag_flac(path: Path, tags: Tags, cover: bytes | None) -> bool:
    from mutagen.flac import FLAC, Picture

    audio = FLAC(path)
    audio["title"] = [tags.title]
    if tags.artist:
        audio["artist"] = [tags.artist]
    if tags.album:
        audio["album"] = [tags.album]
    if cover:
        picture = Picture()
        picture.type = 3
        picture.mime = guess_image_mime(cover)
        picture.desc = "Cover"
        picture.data = cover
        audio.add_picture(picture)
    audio.save()
    return True


def _tag_ogg(path: Path, tags: Tags, cover: bytes | None) -> bool:
    from mutagen.flac import Picture
    from mutagen.oggopus import OggOpus
    from mutagen.oggvorbis import OggVorbis

    try:
        audio = OggOpus(path)
    except Exception:
        audio = OggVorbis(path)

    audio["title"] = [tags.title]
    if tags.artist:
        audio["artist"] = [tags.artist]
    if tags.album:
        audio["album"] = [tags.album]
    if cover:
        picture = Picture()
        picture.type = 3
        picture.mime = guess_image_mime(cover)
        picture.desc = "Cover"
        picture.data = cover
        audio["metadata_block_picture"] = [
            base64.b64encode(picture.write()).decode("ascii")
        ]
    audio.save()
    return True
