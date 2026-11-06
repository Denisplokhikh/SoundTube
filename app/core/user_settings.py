from __future__ import annotations

import json
from pathlib import Path

from app.config import settings
from app.core.library import invalidate_cache

FILE_NAME = "user_settings.json"

# Значение из окружения/умолчания на момент импорта — до того, как lifespan
# применит сохранённый выбор пользователя. Нужно для кнопки «Сбросить».
_INITIAL_MUSIC_DIR = settings.music_dir


def settings_file() -> Path:
    return settings.data_dir / FILE_NAME


def initial_music_dir() -> Path:
    return _INITIAL_MUSIC_DIR


def is_default_music_dir() -> bool:
    try:
        return settings.music_dir.resolve() == _INITIAL_MUSIC_DIR.resolve()
    except OSError:
        return settings.music_dir == _INITIAL_MUSIC_DIR


def _read_saved() -> dict:
    try:
        return json.loads(settings_file().read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return {}


def _write_saved(payload: dict) -> None:
    settings.data_dir.mkdir(parents=True, exist_ok=True)
    target = settings_file()
    tmp = target.with_suffix(".tmp")
    tmp.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    tmp.replace(target)


def apply_saved_settings() -> None:
    """Применяет сохранённый выбор пользователя поверх значений из окружения.

    Вызывается один раз при старте сервера (см. lifespan в app.main).
    """
    saved = _read_saved()
    raw = saved.get("music_dir")
    if not raw:
        return
    try:
        candidate = Path(str(raw).strip()).expanduser()
        candidate.mkdir(parents=True, exist_ok=True)
    except (OSError, ValueError):
        return
    settings.music_dir = candidate
    invalidate_cache()


def set_music_dir(raw: str) -> Path:
    """Меняет папку музыки, создаёт её и запоминает выбор между перезапусками."""
    candidate = Path((raw or "").strip()).expanduser()
    if not str(candidate).strip():
        raise ValueError("Укажите путь к папке")
    if not candidate.is_absolute():
        raise ValueError("Путь должен быть абсолютным")
    try:
        candidate.mkdir(parents=True, exist_ok=True)
    except OSError as exc:
        raise ValueError(f"Не удалось создать папку: {exc}") from exc

    settings.music_dir = candidate
    invalidate_cache()

    saved = _read_saved()
    saved["music_dir"] = str(candidate)
    try:
        _write_saved(saved)
    except OSError as exc:
        raise ValueError(f"Не удалось сохранить настройку: {exc}") from exc
    return candidate


def reset_music_dir() -> Path:
    """Возвращает папку из окружения/умолчания и забывает сохранённый выбор."""
    settings.music_dir = _INITIAL_MUSIC_DIR
    invalidate_cache()

    saved = _read_saved()
    saved.pop("music_dir", None)
    try:
        if saved:
            _write_saved(saved)
        else:
            settings_file().unlink(missing_ok=True)
    except OSError:
        pass
    return settings.music_dir
