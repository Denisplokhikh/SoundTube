from __future__ import annotations

import pytest
import pytest_asyncio
from fastapi import FastAPI
from fastapi.testclient import TestClient
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

from app.api import routes
from app.config import settings
from app.core.library import invalidate_cache
from app.core import user_settings
from app.db import get_session
from app.models import Base, LibrarySource


@pytest.fixture()
def api_client():
    app = FastAPI()
    app.include_router(routes.router)
    return TestClient(app)


@pytest.fixture()
def isolated_dirs(tmp_path, monkeypatch):
    monkeypatch.setattr(settings, "data_dir", tmp_path / "data")
    monkeypatch.setattr(settings, "music_dir", tmp_path / "music")
    invalidate_cache()
    yield tmp_path
    invalidate_cache()


def test_get_settings_reports_music_dir(api_client, isolated_dirs):
    response = api_client.get("/api/settings")
    assert response.status_code == 200, response.text
    body = response.json()
    assert body["music_dir"] == str(isolated_dirs / "music")
    assert body["is_default_music_dir"] is False


def test_patch_settings_changes_music_dir(api_client, isolated_dirs):
    target = isolated_dirs / "new-music"
    response = api_client.patch("/api/settings", json={"music_dir": str(target)})
    assert response.status_code == 200, response.text
    assert response.json()["music_dir"] == str(target)
    assert target.is_dir()
    # Выбор пережил бы рестарт: файл лежит в data_dir.
    assert user_settings.settings_file().is_file()

    settings.music_dir = isolated_dirs / "music"
    user_settings.apply_saved_settings()
    assert settings.music_dir == target


def test_patch_settings_rejects_relative_path(api_client, isolated_dirs):
    response = api_client.patch("/api/settings", json={"music_dir": "relative/path"})
    assert response.status_code == 422


def test_patch_settings_null_resets_to_default(api_client, isolated_dirs):
    target = isolated_dirs / "new-music"
    assert api_client.patch("/api/settings", json={"music_dir": str(target)}).status_code == 200

    response = api_client.patch("/api/settings", json={"music_dir": None})
    assert response.status_code == 200, response.text
    assert response.json()["music_dir"] == str(user_settings.initial_music_dir())


@pytest_asyncio.fixture
async def session(isolated_dirs):
    engine = create_async_engine(f"sqlite+aiosqlite:///{(isolated_dirs / 'test.db').as_posix()}")
    async with engine.begin() as connection:
        await connection.run_sync(Base.metadata.create_all)
    factory = async_sessionmaker(engine, expire_on_commit=False, class_=AsyncSession)
    async with factory() as db:
        yield db
    await engine.dispose()


def _track_file(root, relative: str) -> None:
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"fake")


async def test_import_restores_playlists_from_export(isolated_dirs, session):
    import httpx

    music = isolated_dirs / "music"
    _track_file(music, "Artist/Album/Artist - One.mp3")
    _track_file(music, "Artist/Album/Artist - Two.mp3")

    session.add_all(
        [
            LibrarySource(
                relative_path="Artist/Album/Artist - One.mp3",
                source_url="https://youtu.be/one",
                platform="youtube",
            ),
            LibrarySource(
                relative_path="Artist/Album/Artist - Two.mp3",
                source_url="https://youtu.be/two",
                platform="youtube",
            ),
        ]
    )
    await session.commit()

    app = FastAPI()
    app.include_router(routes.router)

    async def override_session():
        yield session

    app.dependency_overrides[get_session] = override_session
    try:
        transport = httpx.ASGITransport(app=app)
        async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
            response = await client.post(
                "/api/sync/import",
                json={
                    "playlists": [
                        {
                            "name": "Дорога",
                            "tracks": [
                                "https://youtu.be/one",
                                "https://youtu.be/two",
                                "https://youtu.be/gone",
                            ],
                        }
                    ]
                },
            )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200, response.text
    assert response.json() == {
        "playlists_created": 1,
        "playlists_updated": 0,
        "tracks_added": 2,
        "tracks_skipped": 1,
        "sources_updated": 0,
        "download_jobs_created": 0,
        "tracks_queued": 0,
        "job_id": None,
    }

    from sqlalchemy import select

    from app.models import Playlist

    result = await session.execute(select(Playlist))
    playlists = result.scalars().all()
    assert [item.name for item in playlists] == ["Дорога"]
    assert [item.relative_path for item in playlists[0].items] == [
        "Artist/Album/Artist - One.mp3",
        "Artist/Album/Artist - Two.mp3",
    ]


async def test_import_into_existing_playlist_counts_updated(isolated_dirs, session):
    import httpx

    from app.models import Playlist, PlaylistTrack

    music = isolated_dirs / "music"
    _track_file(music, "Artist/Album/Artist - One.mp3")
    _track_file(music, "Artist/Album/Artist - Two.mp3")

    session.add_all(
        [
            LibrarySource(
                relative_path="Artist/Album/Artist - One.mp3",
                source_url="https://youtu.be/one",
                platform="youtube",
            ),
            LibrarySource(
                relative_path="Artist/Album/Artist - Two.mp3",
                source_url="https://youtu.be/two",
                platform="youtube",
            ),
            Playlist(
                name="Дорога",
                items=[PlaylistTrack(relative_path="Artist/Album/Artist - One.mp3", position=0)],
            ),
        ]
    )
    await session.commit()

    app = FastAPI()
    app.include_router(routes.router)

    async def override_session():
        yield session

    app.dependency_overrides[get_session] = override_session
    try:
        transport = httpx.ASGITransport(app=app)
        async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
            response = await client.post(
                "/api/sync/import",
                json={"playlists": [{"name": "дорога", "tracks": ["https://youtu.be/two"]}]},
            )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200, response.text
    assert response.json() == {
        "playlists_created": 0,
        "playlists_updated": 1,
        "tracks_added": 1,
        "tracks_skipped": 0,
        "sources_updated": 0,
        "download_jobs_created": 0,
        "tracks_queued": 0,
        "job_id": None,
    }
