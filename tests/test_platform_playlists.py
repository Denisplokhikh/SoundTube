from __future__ import annotations

import pytest
import pytest_asyncio
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

from app.config import settings
from app.core.platform_playlists import add_to_platform_playlist
from app.models import Base, Playlist


@pytest_asyncio.fixture
async def session(tmp_path, monkeypatch):
    monkeypatch.setattr(settings, "music_dir", tmp_path)
    engine = create_async_engine(f"sqlite+aiosqlite:///{(tmp_path / 'test.db').as_posix()}")
    async with engine.begin() as connection:
        await connection.run_sync(Base.metadata.create_all)
    factory = async_sessionmaker(engine, expire_on_commit=False, class_=AsyncSession)
    async with factory() as db:
        yield db
    await engine.dispose()


async def _playlists(session: AsyncSession) -> list[Playlist]:
    result = await session.execute(select(Playlist))
    return list(result.scalars().unique().all())


@pytest.mark.parametrize(
    ("platform", "name"),
    [("youtube", "YouTube"), ("soundcloud", "SoundCloud")],
)
async def test_creates_platform_playlist(session, tmp_path, platform, name):
    file = tmp_path / "Artist/Album/Artist - Song.mp3"
    file.parent.mkdir(parents=True)
    file.write_bytes(b"fake")

    added = await add_to_platform_playlist(session, platform, file)
    await session.commit()

    assert added is True
    playlists = await _playlists(session)
    assert [playlist.name for playlist in playlists] == [name]
    assert [item.relative_path for item in playlists[0].items] == ["Artist/Album/Artist - Song.mp3"]


async def test_reuses_existing_playlist_and_skips_duplicates(session, tmp_path):
    first = tmp_path / "Artist/Album/Artist - One.mp3"
    second = tmp_path / "Artist/Album/Artist - Two.mp3"
    first.parent.mkdir(parents=True)
    first.write_bytes(b"fake")
    second.write_bytes(b"fake")

    assert await add_to_platform_playlist(session, "youtube", first)
    assert await add_to_platform_playlist(session, "youtube", second)
    assert await add_to_platform_playlist(session, "youtube", first) is False
    await session.commit()

    playlists = await _playlists(session)
    assert len(playlists) == 1
    assert [item.relative_path for item in playlists[0].items] == [
        "Artist/Album/Artist - One.mp3",
        "Artist/Album/Artist - Two.mp3",
    ]


async def test_ignores_unknown_platform_and_outside_paths(session, tmp_path):
    file = tmp_path / "Artist/Album/Artist - Song.mp3"
    file.parent.mkdir(parents=True)
    file.write_bytes(b"fake")

    assert await add_to_platform_playlist(session, "bandcamp", file) is False
    assert await add_to_platform_playlist(session, "youtube", None) is False
    assert await add_to_platform_playlist(session, "youtube", tmp_path.parent / "outside.mp3") is False
    await session.commit()

    assert await _playlists(session) == []
