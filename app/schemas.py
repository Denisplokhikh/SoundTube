from __future__ import annotations

from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field, field_validator

from app.config import AUDIO_FORMATS


class ImportRequest(BaseModel):
    url: str = Field(min_length=4, max_length=2048)
    audio_format: str | None = None

    @field_validator("url")
    @classmethod
    def _validate_url(cls, value: str) -> str:
        value = value.strip()
        if not value.startswith(("http://", "https://")):
            value = f"https://{value}"
        return value

    @field_validator("audio_format")
    @classmethod
    def _validate_format(cls, value: str | None) -> str | None:
        if value is None:
            return None
        value = value.lower().strip()
        if value not in AUDIO_FORMATS:
            raise ValueError(f"audio_format must be one of {AUDIO_FORMATS}")
        return value


class TrackOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    position: int
    title: str
    artist: str | None = None
    album: str | None = None
    duration: float | None = None
    status: str
    error: str | None = None
    file_path: str | None = None
    source_url: str


class JobSummary(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    url: str
    platform: str
    kind: str
    title: str
    thumbnail: str | None = None
    audio_format: str
    status: str
    total: int
    completed: int
    failed: int
    error: str | None = None
    created_at: datetime
    updated_at: datetime


class JobOut(JobSummary):
    tracks: list[TrackOut] = Field(default_factory=list)


class LibraryEntry(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    relative_path: str
    title: str
    artist: str
    album: str | None = None
    duration: float | None = None
    size: int
    modified: datetime
    extension: str


class LibraryResponse(BaseModel):
    root: str
    total: int
    total_size: int
    entries: list[LibraryEntry]


class PlaylistSummary(BaseModel):
    id: str
    name: str
    track_count: int
    cover_path: str | None = None


class PlaylistDetail(PlaylistSummary):
    entries: list[LibraryEntry] = Field(default_factory=list)
    missing: int = 0


class PlaylistCreate(BaseModel):
    name: str = Field(min_length=1, max_length=80)

    @field_validator("name")
    @classmethod
    def _clean_name(cls, value: str) -> str:
        value = " ".join(value.split())
        if not value:
            raise ValueError("Название не может быть пустым")
        return value


class PlaylistRename(PlaylistCreate):
    pass


class PlaylistAddTracks(BaseModel):
    paths: list[str] = Field(min_length=1, max_length=2000)
