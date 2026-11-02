from __future__ import annotations

from datetime import datetime, timezone
from pathlib import Path

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.config import settings
from app.core.platform_playlists import PLATFORM_PLAYLISTS, relative_to_music_dir
from app.models import Playlist, Track

# Версия формата, который читает мобильное приложение (см. SyncPayload.kt).
SYNC_VERSION = 1


async def build_export(session: AsyncSession) -> dict:
    """Снимок библиотеки для телефона: ссылки, названия и плейлисты.

    Экспорт намеренно односторонний и «только добавление»: мобильное приложение
    само решает, чего у него ещё нет. Поэтому здесь нет ни id, ни времени
    изменения, ни надгробий — только источник скачивания.

    Ссылки берутся из таблицы tracks, которую заполняет импорт. Файл, скачанный
    иначе, в экспорт не попадёт: перекачать его на телефоне всё равно неоткуда.
    """
    result = await session.execute(
        select(Track).where(Track.file_path.is_not(None)).options(selectinload(Track.job))
    )
    tracks = list(result.scalars().unique().all())

    url_by_relative: dict[str, str] = {}
    exported_tracks: list[dict] = []
    seen_urls: set[str] = set()

    for track in tracks:
        if not track.file_path:
            continue

        path = Path(track.file_path)
        relative = relative_to_music_dir(path)
        if relative is None or not path.is_file():
            continue

        url = (track.source_url or "").strip()
        if not url or url in seen_urls:
            continue

        seen_urls.add(url)
        url_by_relative[relative] = url
        exported_tracks.append(
            {
                "url": url,
                "title": track.title or "",
                "artist": track.artist or "",
                "platform": track.job.platform if track.job else "",
            }
        )

    exported_tracks.sort(key=lambda item: (item["artist"].lower(), item["title"].lower()))

    return {
        "version": SYNC_VERSION,
        "exportedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "tracks": exported_tracks,
        "playlists": await _export_playlists(session, url_by_relative),
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
