from __future__ import annotations

from pathlib import Path

import httpx
import pytest
import pytest_asyncio
from fastapi import FastAPI
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

from app.api import routes
from app.config import settings
from app.core.library import invalidate_cache
from app.core.runner import runner
from app.db import get_session
from app.models import Base, Job, LibrarySource, Playlist, Track


@pytest.fixture()
def isolated_dirs(tmp_path, monkeypatch):
    monkeypatch.setattr(settings, "data_dir", tmp_path / "data")
    monkeypatch.setattr(settings, "music_dir", tmp_path / "music")
    invalidate_cache()
    yield tmp_path
    invalidate_cache()


@pytest_asyncio.fixture
async def session(isolated_dirs):
    engine = create_async_engine(f"sqlite+aiosqlite:///{(isolated_dirs / 'test.db').as_posix()}")
    async with engine.begin() as connection:
        await connection.run_sync(Base.metadata.create_all)
    factory = async_sessionmaker(engine, expire_on_commit=False, class_=AsyncSession)
    async with factory() as db:
        yield db
    await engine.dispose()


def _track_file(root: Path, relative: str) -> None:
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"fake")


def _client(session: AsyncSession, monkeypatch):
    app = FastAPI()
    app.include_router(routes.router)

    async def override_session():
        yield session

    app.dependency_overrides[get_session] = override_session

    # Не запускаем реальный воркер, только фиксируем вызовы enqueue.
    enqueued: list[str] = []

    async def fake_enqueue(job_id: str) -> None:
        enqueued.append(job_id)

    monkeypatch.setattr(runner, "enqueue", fake_enqueue)

    transport = httpx.ASGITransport(app=app)
    return httpx.AsyncClient(transport=transport, base_url="http://test"), enqueued


async def test_import_updates_sources_from_relative_paths(session, isolated_dirs, monkeypatch):
    music = isolated_dirs / "music"
    _track_file(music, "Artist/Album/Artist - One.mp3")

    client, enqueued = _client(session, monkeypatch)
    try:
        response = await client.post(
            "/api/sync/import",
            json={
                "version": 1,
                "tracks": [
                    {
                        "url": "https://youtu.be/one",
                        "title": "One",
                        "artist": "Artist",
                        "platform": "youtube",
                        "relative_path": "Artist/Album/Artist - One.mp3",
                    }
                ],
            },
        )
    finally:
        await client.aclose()

    assert response.status_code == 200, response.text
    body = response.json()
    assert body["sources_updated"] == 1
    assert body["download_jobs_created"] == 0
    assert enqueued == []

    source = await session.get(LibrarySource, "Artist/Album/Artist - One.mp3")
    assert source is not None
    assert source.source_url == "https://youtu.be/one"
    assert source.platform == "youtube"


async def test_import_creates_download_job_for_missing_track(session, isolated_dirs, monkeypatch):
    client, enqueued = _client(session, monkeypatch)
    try:
        response = await client.post(
            "/api/sync/import",
            json={
                "version": 1,
                "tracks": [
                    {
                        "url": "https://youtu.be/missing",
                        "title": "Missing",
                        "artist": "Artist",
                        "platform": "youtube",
                        "relative_path": "Artist/Album/Artist - Missing.mp3",
                    }
                ],
            },
        )
    finally:
        await client.aclose()

    assert response.status_code == 200, response.text
    body = response.json()
    assert body["sources_updated"] == 0
    assert body["download_jobs_created"] == 1
    assert len(enqueued) == 1

    job = await session.get(Job, enqueued[0])
    assert job is not None
    assert job.platform == "youtube"
    assert job.kind == "track"
    assert len(job.tracks) == 1
    assert job.tracks[0].source_url == "https://youtu.be/missing"


async def test_import_restores_playlists_using_relative_path(session, isolated_dirs, monkeypatch):
    music = isolated_dirs / "music"
    _track_file(music, "Artist/Album/Artist - One.mp3")

    client, enqueued = _client(session, monkeypatch)
    try:
        response = await client.post(
            "/api/sync/import",
            json={
                "version": 1,
                "tracks": [
                    {
                        "url": "https://youtu.be/one",
                        "title": "One",
                        "artist": "Artist",
                        "platform": "youtube",
                        "relative_path": "Artist/Album/Artist - One.mp3",
                    }
                ],
                "playlists": [
                    {
                        "name": "Дорога",
                        "tracks": ["https://youtu.be/one"],
                    }
                ],
            },
        )
    finally:
        await client.aclose()

    assert response.status_code == 200, response.text
    body = response.json()
    assert body["playlists_created"] == 1
    assert body["tracks_added"] == 1
    assert body["sources_updated"] == 1

    playlist = await session.get(Playlist, body.get("playlist_id") or "")
    # Получаем плейлист по имени, так как id не возвращается.
    from sqlalchemy import select

    result = await session.execute(select(Playlist).where(Playlist.name == "Дорога"))
    playlist = result.scalar_one()
    assert [item.relative_path for item in playlist.items] == ["Artist/Album/Artist - One.mp3"]


async def test_import_skips_duplicate_active_download(session, isolated_dirs, monkeypatch):
    existing_job = Job(
        url="https://youtu.be/active",
        platform="youtube",
        kind="track",
        title="Active",
        total=1,
        status="pending",
    )
    existing_job.tracks.append(
        Track(
            position=0,
            title="Active",
            source_url="https://youtu.be/active",
            status="pending",
        )
    )
    session.add(existing_job)
    await session.commit()

    client, enqueued = _client(session, monkeypatch)
    try:
        response = await client.post(
            "/api/sync/import",
            json={
                "tracks": [
                    {
                        "url": "https://youtu.be/active",
                        "title": "Active",
                        "artist": "Artist",
                        "platform": "youtube",
                    }
                ]
            },
        )
    finally:
        await client.aclose()

    assert response.status_code == 200, response.text
    assert response.json()["download_jobs_created"] == 0
    assert enqueued == []


async def test_import_is_backward_compatible_with_playlists_only(session, isolated_dirs, monkeypatch):
    music = isolated_dirs / "music"
    _track_file(music, "Artist/Album/Artist - One.mp3")

    session.add(
        LibrarySource(
            relative_path="Artist/Album/Artist - One.mp3",
            source_url="https://youtu.be/one",
            platform="youtube",
        )
    )
    await session.commit()

    client, enqueued = _client(session, monkeypatch)
    try:
        response = await client.post(
            "/api/sync/import",
            json={"playlists": [{"name": "Старый формат", "tracks": ["https://youtu.be/one"]}]},
        )
    finally:
        await client.aclose()

    assert response.status_code == 200, response.text
    body = response.json()
    assert body["playlists_created"] == 1
    assert body["tracks_added"] == 1
    assert body["download_jobs_created"] == 0
