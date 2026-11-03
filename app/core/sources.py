from __future__ import annotations

from pathlib import Path

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.core.platform_playlists import relative_to_music_dir
from app.models import LibrarySource, Track


async def remember_source(
    session: AsyncSession,
    *,
    file_path: str | Path | None,
    url: str | None,
    platform: str = "",
    title: str = "",
    artist: str = "",
) -> None:
    """Запоминает ссылку, с которой скачан файл.

    Вызывается после скачивания, но не коммитит — это делает вызывающая сторона.
    Если файл перекачали по другой ссылке, побеждает последняя.
    """
    relative = relative_to_music_dir(file_path)
    url = (url or "").strip()
    if relative is None or not url:
        return

    entry = await session.get(LibrarySource, relative)
    if entry is None:
        session.add(
            LibrarySource(
                relative_path=relative,
                source_url=url,
                platform=platform or "",
                title=title or "",
                artist=artist or "",
            )
        )
        return

    entry.source_url = url
    if platform:
        entry.platform = platform
    if title:
        entry.title = title
    if artist:
        entry.artist = artist


async def sync_sources_from_tracks(session: AsyncSession) -> int:
    """Досыпает ссылки из ещё живых задач — для баз, созданных до этой таблицы.

    Удалённые задачи сюда уже не попадут, поэтому функция идемпотентна и дешёвая:
    просто заполняет пробелы, ничего не перезаписывая.
    """
    rows = await session.execute(
        select(Track)
        .where(Track.file_path.is_not(None))
        .options(selectinload(Track.job))
    )

    known = set((await session.scalars(select(LibrarySource.relative_path))).all())
    added = 0

    for track in rows.scalars().unique():
        relative = relative_to_music_dir(track.file_path)
        if relative is None or relative in known:
            continue

        session.add(
            LibrarySource(
                relative_path=relative,
                source_url=track.source_url,
                platform=track.job.platform if track.job else "",
                title=track.title or "",
                artist=track.artist or "",
            )
        )
        known.add(relative)
        added += 1

    if added:
        await session.commit()
    return added


async def sources_by_relative(session: AsyncSession) -> dict[str, LibrarySource]:
    result = await session.execute(select(LibrarySource))
    return {item.relative_path: item for item in result.scalars().all()}
