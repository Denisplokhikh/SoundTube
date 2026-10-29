from __future__ import annotations

import uuid
from datetime import datetime, timezone

from sqlalchemy import DateTime, Float, ForeignKey, Integer, String, Text, UniqueConstraint
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


class Base(DeclarativeBase):
    pass


class Job(Base):
    __tablename__ = "jobs"

    id: Mapped[str] = mapped_column(
        String(32), primary_key=True, default=lambda: uuid.uuid4().hex
    )
    url: Mapped[str] = mapped_column(Text, nullable=False)
    platform: Mapped[str] = mapped_column(String(32), nullable=False)
    kind: Mapped[str] = mapped_column(String(32), nullable=False, default="playlist")
    title: Mapped[str] = mapped_column(Text, nullable=False, default="")
    thumbnail: Mapped[str | None] = mapped_column(Text)
    audio_format: Mapped[str] = mapped_column(String(16), default="original")

    status: Mapped[str] = mapped_column(String(16), default="pending", index=True)
    total: Mapped[int] = mapped_column(Integer, default=0)
    completed: Mapped[int] = mapped_column(Integer, default=0)
    failed: Mapped[int] = mapped_column(Integer, default=0)
    error: Mapped[str | None] = mapped_column(Text)

    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=utcnow, onupdate=utcnow
    )

    tracks: Mapped[list["Track"]] = relationship(
        back_populates="job",
        cascade="all, delete-orphan",
        order_by="Track.position",
        lazy="selectin",
    )


class Track(Base):
    __tablename__ = "tracks"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    job_id: Mapped[str] = mapped_column(
        ForeignKey("jobs.id", ondelete="CASCADE"), index=True, nullable=False
    )
    position: Mapped[int] = mapped_column(Integer, default=0)

    title: Mapped[str] = mapped_column(Text, nullable=False)
    artist: Mapped[str | None] = mapped_column(Text)
    album: Mapped[str | None] = mapped_column(Text)
    duration: Mapped[float | None] = mapped_column(Float)
    source_url: Mapped[str] = mapped_column(Text, nullable=False)

    status: Mapped[str] = mapped_column(String(16), default="pending", index=True)
    file_path: Mapped[str | None] = mapped_column(Text)
    error: Mapped[str | None] = mapped_column(Text)

    job: Mapped[Job] = relationship(back_populates="tracks")


class Playlist(Base):
    """Пользовательский плейлист. Треки ссылаются на файлы библиотеки."""

    __tablename__ = "playlists"

    id: Mapped[str] = mapped_column(
        String(32), primary_key=True, default=lambda: uuid.uuid4().hex
    )
    name: Mapped[str] = mapped_column(Text, nullable=False)

    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=utcnow, onupdate=utcnow
    )

    items: Mapped[list["PlaylistTrack"]] = relationship(
        back_populates="playlist",
        cascade="all, delete-orphan",
        order_by="PlaylistTrack.position",
        lazy="selectin",
    )


class PlaylistTrack(Base):
    __tablename__ = "playlist_tracks"
    __table_args__ = (
        UniqueConstraint("playlist_id", "relative_path", name="uq_playlist_track"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    playlist_id: Mapped[str] = mapped_column(
        ForeignKey("playlists.id", ondelete="CASCADE"), index=True, nullable=False
    )
    # путь относительно папки музыки: файлы — источник истины, id библиотеки нестабилен
    relative_path: Mapped[str] = mapped_column(Text, nullable=False)
    position: Mapped[int] = mapped_column(Integer, default=0)
    added_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)

    playlist: Mapped[Playlist] = relationship(back_populates="items")
