from __future__ import annotations

from pathlib import Path

import pytest_asyncio
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

from app.config import settings
from app.core import runner as runner_module
from app.core.downloader import DownloadResult
from app.core.library import invalidate_cache
from app.models import Base, Job, Track


@pytest_asyncio.fixture
async def db(tmp_path, monkeypatch):
    """Изолированная база и папка музыки для проверки воркера."""
    monkeypatch.setattr(settings, "music_dir", tmp_path / "music")
    monkeypatch.setattr(settings, "data_dir", tmp_path / "data")
    settings.music_dir.mkdir(parents=True)
    invalidate_cache()

    engine = create_async_engine(f"sqlite+aiosqlite:///{(tmp_path / 'test.db').as_posix()}")
    async with engine.begin() as connection:
        await connection.run_sync(Base.metadata.create_all)
    factory = async_sessionmaker(engine, expire_on_commit=False, class_=AsyncSession)
    monkeypatch.setattr(runner_module, "SessionLocal", factory)
    try:
        yield factory
    finally:
        await engine.dispose()
        invalidate_cache()


def _fake_downloader(monkeypatch, calls: list[tuple[str, str]]):
    async def download(spec, *, platform: str, audio_format: str = "original"):
        calls.append((spec.url, platform))
        target = settings.music_dir / "Artist/Album/Artist - Song.m4a"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(b"audio")
        return DownloadResult(
            path=target,
            title="Song",
            artist="Artist",
            album=None,
            duration=180.0,
        )

    monkeypatch.setattr(runner_module, "download_track", download)


async def test_downloads_again_when_duplicate_file_disappeared(db, monkeypatch):
    """Старая done-запись без файла не считается дубликатом.

    Иначе восстановление библиотеки из JSON навсегда застревало бы в
    «уже есть в библиотеке» без самого файла.
    """
    async with db() as session:
        old = Job(
            url="https://soundcloud.com/old",
            platform="soundcloud",
            kind="set",
            title="Старая задача",
            total=1,
            completed=1,
            status="done",
        )
        old.tracks.append(
            Track(
                position=0,
                title="Song",
                source_url="https://soundcloud.com/a/song",
                status="done",
                file_path=str(settings.music_dir / "Old/Album/Old - Song.m4a"),
            )
        )
        job = Job(
            url="soundtube://library-import",
            platform="soundcloud",
            kind="sync",
            title="Импорт библиотеки",
            total=1,
            status="running",
        )
        job.tracks.append(
            Track(
                position=0,
                title="Song",
                source_url="https://soundcloud.com/a/song",
                status="pending",
            )
        )
        session.add_all([old, job])
        await session.commit()
        track_id = job.tracks[0].id
        job_id = job.id

    calls: list[tuple[str, str]] = []
    _fake_downloader(monkeypatch, calls)

    await runner_module.runner._download_one(job_id, track_id, "soundcloud", "original")

    assert calls == [("https://soundcloud.com/a/song", "soundcloud")]
    async with db() as session:
        track = await session.get(Track, track_id)
        assert track is not None
        assert track.status == "done"
        assert track.file_path is not None
        assert Path(track.file_path).is_file()


async def test_skips_real_duplicate_with_file(db, monkeypatch):
    """Если дубликат действительно лежит на диске — трек не качаем."""
    existing = settings.music_dir / "Artist/Album/Artist - Song.m4a"
    existing.parent.mkdir(parents=True)
    existing.write_bytes(b"audio")

    async with db() as session:
        old = Job(
            url="https://soundcloud.com/old",
            platform="soundcloud",
            kind="set",
            title="Старая задача",
            total=1,
            completed=1,
            status="done",
        )
        old.tracks.append(
            Track(
                position=0,
                title="Song",
                source_url="https://soundcloud.com/a/song",
                status="done",
                file_path=str(existing),
            )
        )
        job = Job(
            url="soundtube://library-import",
            platform="soundcloud",
            kind="sync",
            title="Импорт библиотеки",
            total=1,
            status="running",
        )
        job.tracks.append(
            Track(
                position=0,
                title="Song",
                source_url="https://soundcloud.com/a/song",
                status="pending",
            )
        )
        session.add_all([old, job])
        await session.commit()
        track_id = job.tracks[0].id
        job_id = job.id

    calls: list[tuple[str, str]] = []
    _fake_downloader(monkeypatch, calls)

    await runner_module.runner._download_one(job_id, track_id, "soundcloud", "original")

    assert calls == []
    async with db() as session:
        track = await session.get(Track, track_id)
        assert track is not None
        assert track.status == "skipped"
        assert track.file_path == str(existing)


async def test_detects_platform_from_track_url(db, monkeypatch):
    """В смешанной задаче «Импорт библиотеки» площадку берём из ссылки трека."""
    async with db() as session:
        job = Job(
            url="soundtube://library-import",
            platform="mixed",
            kind="sync",
            title="Импорт библиотеки",
            total=1,
            status="running",
        )
        job.tracks.append(
            Track(
                position=0,
                title="Song",
                source_url="https://www.youtube.com/watch?v=abc",
                status="pending",
            )
        )
        session.add(job)
        await session.commit()
        track_id = job.tracks[0].id
        job_id = job.id

    calls: list[tuple[str, str]] = []
    _fake_downloader(monkeypatch, calls)

    await runner_module.runner._download_one(job_id, track_id, "mixed", "original")

    assert calls == [("https://www.youtube.com/watch?v=abc", "youtube")]


async def test_removes_file_when_job_deleted_during_download(db, monkeypatch):
    """Удалённая задача не оставляет скачанный файл-сироту."""

    async def download(spec, *, platform: str, audio_format: str = "original"):
        target = settings.music_dir / "Artist/Album/Artist - Song.m4a"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(b"audio")
        async with db() as session:
            track = await session.get(Track, track_id)
            await session.delete(track)
            await session.commit()
        return DownloadResult(
            path=target, title="Song", artist="Artist", album=None, duration=180.0
        )

    async with db() as session:
        job = Job(
            url="soundtube://library-import",
            platform="soundcloud",
            kind="sync",
            title="Импорт библиотеки",
            total=1,
            status="running",
        )
        job.tracks.append(
            Track(
                position=0,
                title="Song",
                source_url="https://soundcloud.com/a/song",
                status="pending",
            )
        )
        session.add(job)
        await session.commit()
        track_id = job.tracks[0].id
        job_id = job.id

    monkeypatch.setattr(runner_module, "download_track", download)

    await runner_module.runner._download_one(job_id, track_id, "soundcloud", "original")

    assert not (settings.music_dir / "Artist/Album/Artist - Song.m4a").exists()
