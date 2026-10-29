from __future__ import annotations

from pathlib import Path

from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.config import settings
from app.models import Playlist, PlaylistTrack

# Плейлисты, которые пополняются сами при скачивании с площадки.
PLATFORM_PLAYLISTS = {
    "youtube": "YouTube",
    "soundcloud": "SoundCloud",
}


def playlist_name(platform: str) -> str | None:
    return PLATFORM_PLAYLISTS.get(platform)


def relative_to_music_dir(file_path: str | Path | None) -> str | None:
    """Путь внутри папки музыки — в том виде, в каком его хранит плейлист."""
    if not file_path:
        return None
    root = settings.music_dir.resolve()
    try:
        candidate = Path(file_path).resolve()
    except (OSError, ValueError):
        return None
    if not candidate.is_relative_to(root):
        return None
    return candidate.relative_to(root).as_posix()


async def _get_or_create(session: AsyncSession, name: str) -> Playlist:
    playlist = await session.scalar(
        select(Playlist).where(Playlist.name == name).options(selectinload(Playlist.items))
    )
    if playlist is not None:
        return playlist

    playlist = Playlist(name=name, items=[])
    session.add(playlist)
    await session.flush()
    return playlist


async def add_to_platform_playlist(
    session: AsyncSession,
    platform: str,
    file_path: str | Path | None,
) -> bool:
    """Добавляет файл в плейлист площадки, создавая его при необходимости.

    Изменения не коммитятся — это делает вызывающая сторона.
    Возвращает True, если элемент действительно добавлен.
    """
    name = playlist_name(platform)
    relative = relative_to_music_dir(file_path)
    if name is None or relative is None:
        return False

    playlist = await _get_or_create(session, name)
    if any(item.relative_path == relative for item in playlist.items):
        return False

    position = max((item.position for item in playlist.items), default=-1) + 1
    item = PlaylistTrack(relative_path=relative, position=position)
    playlist.items.append(item)
    try:
        # savepoint: гонка двух воркеров на одном файле не должна ронять коммит трека
        async with session.begin_nested():
            await session.flush()
    except IntegrityError:
        playlist.items.remove(item)
        return False
    return True
