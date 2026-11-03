from __future__ import annotations

from datetime import datetime, timezone
from pathlib import Path

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.config import settings
from app.core.library import scan_library
from app.core.platform_playlists import PLATFORM_PLAYLISTS
from app.core.sources import sources_by_relative, sync_sources_from_tracks
from app.models import Playlist

# Версия формата, который читает мобильное приложение (см. SyncPayload.kt).
SYNC_VERSION = 1


async def build_export(session: AsyncSession) -> dict:
    """Снимок библиотеки для телефона: ссылки, названия и плейлисты.

    Источник истины — файлы на диске, как и в самой библиотеке: трек, чья задача
    удалена из «Загрузок», всё равно должен уехать на телефон. Ссылки берутся
    из `library_sources`, которая переживает задачи; если её ещё нет (старая
    база), сначала досыпаем её из живых задач.

    Экспорт односторонний и «только добавление»: ни id, ни времени изменения,
    ни надгробий — мобильное приложение само решает, чего у него ещё нет.
    """
    await sync_sources_from_tracks(session)

    sources = await sources_by_relative(session)
    url_by_relative: dict[str, str] = {}
    exported_tracks: list[dict] = []
    seen_urls: set[str] = set()
    without_source = 0

    for item in scan_library():
        source = sources.get(item.relative_path)
        if source is None or not source.source_url:
            without_source += 1
            continue
        if source.source_url in seen_urls:
            continue

        seen_urls.add(source.source_url)
        url_by_relative[item.relative_path] = source.source_url
        exported_tracks.append(
            {
                "url": source.source_url,
                "title": item.title or source.title,
                "artist": item.artist or source.artist,
                "platform": source.platform,
            }
        )

    return {
        "version": SYNC_VERSION,
        "exportedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "tracks": exported_tracks,
        "playlists": await _export_playlists(session, url_by_relative),
        # Файлы, у которых не осталось ссылки: скачать их на телефоне неоткуда.
        "withoutSource": without_source,
    }


def export_target() -> Path:
    """Куда класть файл при экспорте «в один клик» из десктопного приложения.

    Вебвью Tauri не всегда умеет браузерные загрузки, поэтому сервер пишет файл
    сам — в «Загрузки», а если её нет, в папку данных.
    """
    downloads = Path.home() / "Downloads"
    base = downloads if downloads.is_dir() else settings.data_dir
    base.mkdir(parents=True, exist_ok=True)
    return base / "soundtube-library.json"


async def _export_playlists(
    session: AsyncSession, url_by_relative: dict[str, str]
) -> list[dict]:
    result = await session.execute(
        select(Playlist).options(selectinload(Playlist.items)).order_by(Playlist.created_at)
    )

    # Плейлисты площадок на телефоне наполняются сами при скачивании.
    system_names = set(PLATFORM_PLAYLISTS.values())
    exported: list[dict] = []

    for playlist in result.scalars().unique().all():
        if playlist.name in system_names:
            continue

        urls: list[str] = []
        seen: set[str] = set()
        for item in sorted(playlist.items, key=lambda entry: entry.position):
            url = url_by_relative.get(item.relative_path)
            if url is None or url in seen:
                continue
            seen.add(url)
            urls.append(url)

        if urls:
            exported.append({"name": playlist.name, "tracks": urls})

    return exported
