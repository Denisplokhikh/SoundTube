from __future__ import annotations

import shutil
from pathlib import Path

import pytest

from scripts.version import (
    ANDROID_GRADLE,
    APP_INIT,
    CARGO_LOCK,
    CARGO_TOML,
    PACKAGE_JSON,
    PACKAGE_LOCK,
    PYPROJECT,
    ROOT,
    TAURI_CONF,
    UV_LOCK,
    VersionError,
    _validate,
    apply_version,
    current_version,
    read_all,
)

FILES = [
    APP_INIT,
    PYPROJECT,
    UV_LOCK,
    PACKAGE_JSON,
    PACKAGE_LOCK,
    CARGO_TOML,
    CARGO_LOCK,
    TAURI_CONF,
    ANDROID_GRADLE,
]


@pytest.fixture()
def repo(tmp_path: Path) -> Path:
    """Копия файлов с версиями во временной папке, чтобы не трогать рабочее дерево."""

    for relative in FILES:
        source = ROOT / relative
        if not source.is_file():
            pytest.skip(f"в репозитории нет {relative}")
        target = tmp_path / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
    return tmp_path


def test_versions_are_consistent() -> None:
    canonical = current_version()
    assert {entry.version for entry in read_all()} == {canonical}


def test_bump_updates_every_file(repo: Path) -> None:
    apply_version("1.2.3", repo)

    versions = {entry.version for entry in read_all(repo)}
    assert versions == {"1.2.3"}
    assert current_version(repo) == "1.2.3"


def test_bump_is_idempotent(repo: Path) -> None:
    version = current_version(repo)
    before = {relative: (repo / relative).read_bytes() for relative in FILES}

    assert apply_version(version, repo) == []

    after = {relative: (repo / relative).read_bytes() for relative in FILES}
    assert before == after


def test_android_version_code_follows_version(repo: Path) -> None:
    apply_version("2.5.1", repo)

    gradle = (repo / ANDROID_GRADLE).read_text(encoding="utf-8")
    assert 'versionName = "2.5.1"' in gradle
    assert "versionCode = 20501" in gradle


def test_lockfile_package_entries_updated(repo: Path) -> None:
    apply_version("0.9.9", repo)

    for relative in (UV_LOCK, CARGO_LOCK):
        text = (repo / relative).read_text(encoding="utf-8")
        assert 'name = "melodown"\nversion = "0.9.9"' in text.replace("\r\n", "\n")


@pytest.mark.parametrize("value", ["1.2", "abc", "1.2.3.4.5", ""])
def test_rejects_bad_version(value: str) -> None:
    with pytest.raises(VersionError):
        _validate(value)


def test_strips_leading_v() -> None:
    assert _validate("v1.2.3") == "1.2.3"
