from __future__ import annotations

import os
import sys
from functools import lru_cache
from pathlib import Path

from pydantic import Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

FROZEN = bool(getattr(sys, "frozen", False))
PROJECT_ROOT = (
    Path(sys._MEIPASS).resolve() if FROZEN else Path(__file__).resolve().parent.parent
)

AUDIO_FORMATS = ("original", "mp3", "m4a", "opus", "flac")


def _default_data_dir() -> Path:
    """Keep runtime data out of the temporary PyInstaller bundle."""
    if not FROZEN:
        return PROJECT_ROOT / "data"

    base = os.environ.get("LOCALAPPDATA") or os.environ.get("XDG_DATA_HOME")
    if base:
        return Path(base) / "melodown" / "data"
    return Path.home() / ".melodown" / "data"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="MELODOWN_",
        env_file=PROJECT_ROOT / ".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    host: str = "127.0.0.1"
    port: int = 8765

    music_dir: Path = Path.home() / "Music" / "Melodown"
    data_dir: Path = Field(default_factory=_default_data_dir)

    audio_format: str = "original"
    audio_quality: str = "0"

    max_concurrent_jobs: int = 1
    max_concurrent_tracks: int = 1
    retries: int = 3
    limit_tracks: int = 500

    youtube_cookies: Path | None = None
    soundcloud_cookies: Path | None = None
    proxy: str | None = None

    @field_validator("audio_format")
    @classmethod
    def _validate_format(cls, value: str) -> str:
        value = value.lower().strip()
        if value not in AUDIO_FORMATS:
            raise ValueError(f"audio_format must be one of {AUDIO_FORMATS}")
        return value

    def cookies_for(self, platform: str) -> Path | None:
        candidate = {
            "youtube": self.youtube_cookies,
            "soundcloud": self.soundcloud_cookies,
        }.get(platform)
        if candidate and candidate.exists():
            return candidate
        return None


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
