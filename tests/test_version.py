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
    android_code,
    android_version,
    apply_android,
    apply_desktop,
    apply_version,
    check,
    current_version,
    read_all,
    read_desktop,
    version_code_for,
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
    # Десктоп и Android нумеруются независимо: сверяем только внутри групп.
    desktop = {entry.version for entry in read_desktop()}
    assert len(desktop) == 1, desktop

    version = android_version()
    assert android_code() == version_code_for(version)


def test_bump_updates_every_file(repo: Path) -> None:
    apply_version("1.2.3", repo)

    versions = {entry.version for entry in read_all(repo)}
    assert versions == {"1.2.3"}
    assert current_version(repo) == "1.2.3"


def test_bump_is_idempotent(repo: Path) -> None:
    before = {relative: (repo / relative).read_bytes() for relative in FILES}

    assert apply_desktop(current_version(repo), repo) == []
    assert apply_android(android_version(repo), repo) == []

    after = {relative: (repo / relative).read_bytes() for relative in FILES}
    assert before == after


def test_android_version_code_follows_version(repo: Path) -> None:
    apply_android("2.5.1", repo)

    gradle = (repo / ANDROID_GRADLE).read_text(encoding="utf-8")
    assert 'versionName = "2.5.1"' in gradle
    assert "versionCode = 20501" in gradle


def test_desktop_and_android_bump_independently(repo: Path) -> None:
    apply_desktop("1.3.0", repo)
    apply_android("1.2.3", repo)

    assert current_version(repo) == "1.3.0"
    assert {entry.version for entry in read_desktop(repo)} == {"1.3.0"}
    assert android_version(repo) == "1.2.3"
    assert android_code(repo) == 10203
    assert check("all", repo)


def test_check_catches_desktop_drift(repo: Path) -> None:
    apply_desktop("1.2.4", repo)
    init = repo / APP_INIT
    init.write_text(
        init.read_text(encoding="utf-8").replace('__version__ = "1.2.4"', '__version__ = "1.2.5"'),
        encoding="utf-8",
    )

    assert not check("desktop", repo)
    assert check("android", repo)


def test_check_catches_android_code_drift(repo: Path) -> None:
    apply_android("1.2.3", repo)
    gradle = repo / ANDROID_GRADLE
    gradle.write_text(
        gradle.read_text(encoding="utf-8").replace("versionCode = 10203", "versionCode = 999"),
        encoding="utf-8",
    )

    assert not check("android", repo)
    assert check("desktop", repo)


def test_lockfile_package_entries_updated(repo: Path) -> None:
    apply_desktop("0.9.9", repo)

    for relative in (UV_LOCK, CARGO_LOCK):
        text = (repo / relative).read_text(encoding="utf-8")
        assert 'name = "melodown"\nversion = "0.9.9"' in text.replace("\r\n", "\n")


@pytest.mark.parametrize("value", ["1.2", "abc", "1.2.3.4.5", ""])
def test_rejects_bad_version(value: str) -> None:
    with pytest.raises(VersionError):
        _validate(value)


def test_strips_leading_v() -> None:
    assert _validate("v1.2.3") == "1.2.3"
