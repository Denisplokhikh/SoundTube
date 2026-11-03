from __future__ import annotations

from pathlib import Path

import pytest_asyncio
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

from app.config import settings
from app.core.library import invalidate_cache
from app.core.sources import remember_source
from app.core.sync_export import build_export, export_target
from app.models import Base, Job, Playlist, PlaylistTrack, Track


@pytest_asyncio.fixture
async def session(tmp_path, monkeypatch):
    monkeypatch.setattr(settings, "music_dir", tmp_path)
    invalidate_cache()
    engine = create_async_engine(f"sqlite+aiosqlite:///{(tmp_path / 'test.db').as_posix()}")
    async with engine.begin() as connection:
        await connection.run_sync(Base.metadata.create_all)
    factory = async_sessionmaker(engine, expire_on_commit=False, class_=AsyncSession)
    async with factory() as db:
        yield db
    await engine.dispose()
    invalidate_cache()


def _file(root, relative: str) -> str:
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"fake")
    return str(path)


async def test_export_skips_system_playlists_and_unknown_files(session, tmp_path):
    one = _file(tmp_path, "Artist/Album/Artist - One.mp3")
    two = _file(tmp_path, "Artist/Album/Artist - Two.m4a")

    job = Job(url="https://example.com", platform="youtube", kind="playlist", status="completed")
    job.tracks.extend(
        [
            Track(
                title="One",
                artist="Artist",
                source_url="https://youtu.be/one",
                status="completed",
                file_path=one,
            ),
            Track(
                title="Two",
                artist="Artist",
                source_url="https://soundcloud.com/artist/two",
                status="completed",
                file_path=two,
            ),
        ]
    )

    liked = Playlist(
        name="Любимое",
        items=[
            PlaylistTrack(relative_path="Artist/Album/Artist - Two.m4a", position=1),
            PlaylistTrack(relative_path="Artist/Album/Artist - One.mp3", position=0),
            PlaylistTrack(relative_path="Artist/Album/Deleted.mp3", position=2),
        ],
    )
    platform = Playlist(
        name="YouTube",
        items=[PlaylistTrack(relative_path="Artist/Album/Artist - One.mp3", position=0)],
    )

    session.add_all([job, liked, platform])
    await session.commit()

    payload = await build_export(session)

    assert payload["version"] == 1
    assert [track["url"] for track in payload["tracks"]] == [
        "https://youtu.be/one",
        "https://soundcloud.com/artist/two",
    ]
    assert payload["tracks"][0]["platform"] == "youtube"
    assert payload["playlists"] == [
        {
            "name": "Любимое",
            "tracks": ["https://youtu.be/one", "https://soundcloud.com/artist/two"],
        }
    ]


def test_export_target_prefers_downloads(tmp_path, monkeypatch):
    monkeypatch.setattr(Path, "home", classmethod(lambda cls: tmp_path))
    monkeypatch.setattr(settings, "data_dir", tmp_path / "data")
    (tmp_path / "Downloads").mkdir()

    assert export_target() == tmp_path / "Downloads" / "soundtube-library.json"


def test_export_target_falls_back_to_data_dir(tmp_path, monkeypatch):
    monkeypatch.setattr(Path, "home", classmethod(lambda cls: tmp_path))
    monkeypatch.setattr(settings, "data_dir", tmp_path / "data")

    assert export_target() == tmp_path / "data" / "soundtube-library.json"


async def test_export_ignores_vanished_files_and_duplicate_urls(session, tmp_path):
    existing = _file(tmp_path, "Artist/Album/Artist - Song.mp3")

    job = Job(url="https://example.com", platform="soundcloud", kind="track", status="completed")
    job.tracks.extend(
        [
            Track(
                title="Song",
                artist="Artist",
                source_url="https://soundcloud.com/artist/song",
                status="completed",
                file_path=existing,
            ),
            Track(
                title="Song copy",
                artist="Artist",
                source_url="https://soundcloud.com/artist/song",
                status="completed",
                file_path=existing,
            ),
            Track(
                title="Gone",
                artist="Artist",
                source_url="https://soundcloud.com/artist/gone",
                status="completed",
                file_path=str(tmp_path / "Artist/Album/gone.mp3"),
            ),
        ]
    )
    session.add(job)
    await session.commit()

    payload = await build_export(session)

    assert [track["url"] for track in payload["tracks"]] == [
        "https://soundcloud.com/artist/song"
    ]
    assert payload["playlists"] == []


async def test_export_survives_deleting_download_job(session, tmp_path):
    """Удаление задачи в «Загрузках» не должно уносить ссылку из экспорта."""
    path = _file(tmp_path, "Artist/Album/Artist - Song.mp3")

    job = Job(url="https://example.com", platform="youtube", kind="track", status="completed")
    job.tracks.append(
        Track(
            title="Song",
            artist="Artist",
            source_url="https://youtu.be/song",
            status="done",
            file_path=path,
        )
    )
    session.add(job)
    await session.commit()

    await remember_source(
        session,
        file_path=path,
        url="https://youtu.be/song",
        platform="youtube",
        title="Song",
        artist="Artist",
    )
    await session.commit()

    await session.delete(job)
    await session.commit()

    payload = await build_export(session)

    assert [track["url"] for track in payload["tracks"]] == ["https://youtu.be/song"]
    assert payload["tracks"][0]["platform"] == "youtube"
    assert payload["withoutSource"] == 0


async def test_export_counts_files_without_known_source(session, tmp_path):
    _file(tmp_path, "Artist/Album/Artist - Unknown.mp3")

    payload = await build_export(session)

    assert payload["tracks"] == []
    assert payload["withoutSource"] == 1
