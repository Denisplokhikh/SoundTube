#!/usr/bin/env python3
"""Одна команда, чтобы обновить версию SoundTube во всех файлах.

Версию читают пять разных тулчейнов, и у каждого свой файл:

    app/__init__.py        — что показывает приложение и /api/update
    pyproject.toml         — пакет Python (hatchling)
    uv.lock                — зафиксированная версия пакета для uv
    package.json           — npm / Tauri CLI
    package-lock.json      — npm
    src-tauri/Cargo.toml   — Rust-крейт
    src-tauri/Cargo.lock   — зафиксированная версия крейта
    src-tauri/tauri.conf.json — версия установщика
    android/app/build.gradle.kts — versionName/versionCode Android-приложения

Править их руками не нужно:

    python scripts/version.py            # показать текущую версию и проверить, что везде одно и то же
    python scripts/version.py 1.2.0      # выставить 1.2.0 везде
    python scripts/version.py --check    # код возврата 1, если файлы разъехались (для CI)

При релизе версию всё равно подставит CI из тега (см. .github/workflows/release.yml),
так что забытая правка не превратится в неправильный номер в установщике.
"""

from __future__ import annotations

import re
import sys
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

APP_INIT = "app/__init__.py"
PYPROJECT = "pyproject.toml"
UV_LOCK = "uv.lock"
PACKAGE_JSON = "package.json"
PACKAGE_LOCK = "package-lock.json"
CARGO_TOML = "src-tauri/Cargo.toml"
CARGO_LOCK = "src-tauri/Cargo.lock"
TAURI_CONF = "src-tauri/tauri.conf.json"
ANDROID_GRADLE = "android/app/build.gradle.kts"

# Версия в lock-файлах лежит в блоке пакета с этим именем.
PACKAGE_NAME = "melodown"

SEMVER_RE = re.compile(
    r"^(?P<major>\d+)\.(?P<minor>\d+)\.(?P<patch>\d+)" r"(?P<suffix>[-+][0-9A-Za-z.\-]+)?$"
)


class VersionError(Exception):
    """Файл не найден или в нём неожиданный формат."""


@dataclass
class FileVersion:
    path: Path
    version: str


def _read(path: Path) -> str:
    # newline="" — чтобы не менять CRLF на LF и обратно.
    with path.open("r", encoding="utf-8", newline="") as handle:
        return handle.read()


def _write(path: Path, text: str) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        handle.write(text)


def _sub_once(path: Path, text: str, pattern: str, version: str) -> str:
    """Заменяет ровно одно вхождение (group 1 + version + group 2)."""

    new_text, count = re.subn(
        pattern,
        lambda match: match.group(1) + version + match.group(2),
        text,
        count=1,
    )
    if count != 1:
        raise VersionError(f"{path}: не нашёл версию по шаблону {pattern!r}")
    return new_text


def _sub_package_block(path: Path, text: str, name: str, version: str) -> str:
    """Правит `version = "..."`, стоящий сразу после `name = "<name>"`."""

    pattern = rf'(?m)(name = "{re.escape(name)}"\s*\nversion = ")[^"]+(")'
    return _sub_once(path, text, pattern, version)


def _validate(value: str) -> str:
    version = value.strip()
    if version.startswith(("v", "V")):
        version = version[1:]
    if not SEMVER_RE.match(version):
        raise VersionError(
            f"«{value}» не похоже на версию. Нужен формат MAJOR.MINOR.PATCH, например 1.2.0"
        )
    return version


def current_version(root: Path = ROOT) -> str:
    text = _read(root / APP_INIT)
    match = re.search(r'^__version__\s*=\s*"([^"]+)"', text, flags=re.MULTILINE)
    if not match:
        raise VersionError(f"{APP_INIT}: нет строки __version__ = \"...\"")
    return match.group(1)


def read_all(root: Path = ROOT) -> list[FileVersion]:
    """Собирает версии из всех файлов, где они продублированы."""

    result: list[FileVersion] = []

    def add(relative: str, pattern: str) -> None:
        path = root / relative
        text = _read(path)
        match = re.search(pattern, text, flags=re.MULTILINE)
        if not match:
            raise VersionError(f"{relative}: версия не найдена")
        result.append(FileVersion(path, match.group(1)))

    add(APP_INIT, r'^__version__\s*=\s*"([^"]+)"')
    add(PYPROJECT, r'^version\s*=\s*"([^"]+)"')
    add(PACKAGE_JSON, r'^\s*"version":\s*"([^"]+)"')
    add(TAURI_CONF, r'^\s*"version":\s*"([^"]+)"')
    add(CARGO_TOML, r'^version\s*=\s*"([^"]+)"')

    for relative in (UV_LOCK, CARGO_LOCK):
        add(
            relative,
            rf'name = "{re.escape(PACKAGE_NAME)}"\s*\nversion = "([^"]+)"',
        )

    package_lock = root / PACKAGE_LOCK
    if package_lock.is_file():
        # В файле версия лежит дважды (корень и packages[""]), но она одна и та же.
        result.append(FileVersion(package_lock, _package_lock_versions(package_lock)[0]))

    gradle = root / ANDROID_GRADLE
    if gradle.is_file():
        text = _read(gradle)
        match = re.search(r'versionName\s*=\s*"([^"]+)"', text)
        if match:
            result.append(FileVersion(gradle, match.group(1)))

    return result


def _package_lock_versions(path: Path) -> tuple[str, str]:
    """Версия из корня package-lock.json и из блока packages[""]."""

    text = _read(path)
    root_match = re.search(r'"version":\s*"([^"]+)"', text)
    if not root_match:
        raise VersionError(f"{path.name}: нет версии в корне")

    packages_at = text.find('"packages"')
    entry_at = text.find('"": {', packages_at) if packages_at >= 0 else -1
    entry_match = re.search(r'"version":\s*"([^"]+)"', text[entry_at:]) if entry_at >= 0 else None
    if not entry_match:
        raise VersionError(f'{path.name}: нет версии в packages[""]')

    root_version, entry_version = root_match.group(1), entry_match.group(1)
    if root_version != entry_version:
        raise VersionError(
            f'{path.name}: версия в корне ({root_version}) не совпадает с packages[""] '
            f'({entry_version}). Проще починить «python scripts/version.py {root_version}»'
        )

    return root_version, entry_version


def apply_version(new_version: str, root: Path = ROOT) -> list[Path]:
    """Проставляет версию во все файлы. Возвращает список изменённых файлов."""

    version = _validate(new_version)
    changed: list[Path] = []

    def update(relative: str, transform) -> None:
        path = root / relative
        if not path.is_file():
            return
        text = _read(path)
        new_text = transform(path, text)
        if new_text != text:
            _write(path, new_text)
            changed.append(path)

    update(
        APP_INIT,
        lambda path, text: _sub_once(path, text, r'(?m)(^__version__\s*=\s*")[^"]+(")', version),
    )
    update(
        PYPROJECT,
        lambda path, text: _sub_once(path, text, r'(?m)(^version\s*=\s*")[^"]+(")', version),
    )
    update(
        PACKAGE_JSON,
        lambda path, text: _sub_once(path, text, r'(?m)(^\s*"version":\s*")[^"]+(")', version),
    )
    update(
        TAURI_CONF,
        lambda path, text: _sub_once(path, text, r'(?m)(^\s*"version":\s*")[^"]+(")', version),
    )
    update(
        CARGO_TOML,
        lambda path, text: _sub_once(path, text, r'(?m)(^version\s*=\s*")[^"]+(")', version),
    )
    update(UV_LOCK, lambda path, text: _sub_package_block(path, text, PACKAGE_NAME, version))
    update(CARGO_LOCK, lambda path, text: _sub_package_block(path, text, PACKAGE_NAME, version))
    update(PACKAGE_LOCK, lambda path, text: _apply_package_lock(path, text, version))
    update(ANDROID_GRADLE, lambda path, text: _apply_android(path, text, version))

    return changed


def _apply_package_lock(path: Path, text: str, version: str) -> str:
    text = _sub_once(path, text, r'("version":\s*")[^"]+(")', version)

    packages_at = text.find('"packages"')
    entry_at = text.find('"": {', packages_at) if packages_at >= 0 else -1
    if entry_at < 0:
        raise VersionError(f'{path.name}: нет блока packages[""]')

    new_text, count = re.subn(
        r'("(?:version)":\s*")[^"]+(")',
        lambda match: match.group(1) + version + match.group(2),
        text[entry_at:],
        count=1,
    )
    if count != 1:
        raise VersionError(f"{path.name}: нет версии в packages[\"\"]")
    return text[:entry_at] + new_text


def _apply_android(path: Path, text: str, version: str) -> str:
    match = SEMVER_RE.match(version)
    if not match:
        raise VersionError(f"{version} не подходит для Android versionName")

    code = (
        int(match.group("major")) * 10_000
        + int(match.group("minor")) * 100
        + int(match.group("patch"))
    )

    new_text = _sub_once(path, text, r'(versionName\s*=\s*")[^"]+(")', version)
    new_text, count = re.subn(r"(versionCode\s*=\s*)\d+", rf"\g<1>{code}", new_text, count=1)
    if count != 1:
        raise VersionError(f"{path}: не нашёл versionCode")
    return new_text


def _print_report(root: Path = ROOT) -> bool:
    canonical = current_version(root)
    entries = read_all(root)
    width = max(len(str(entry.path.relative_to(root))) for entry in entries)
    ok = True

    for entry in entries:
        relative = str(entry.path.relative_to(root)).replace("\\", "/")
        mark = "ok" if entry.version == canonical else "!!"
        if entry.version != canonical:
            ok = False
        print(f"  {mark}  {relative:<{width}}  {entry.version}")

    print()
    if ok:
        print(f"Версия везде одинаковая: {canonical}")
    else:
        print(f"Версия разъехалась, эталон (app/__init__.py): {canonical}")
        print(f"Починить: python scripts/version.py {canonical}")
    return ok


def main(argv: list[str]) -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")

    check_only = any(arg in ("--check", "-c") for arg in argv[1:])
    args = [arg for arg in argv[1:] if arg not in ("--check", "-c")]

    if not args:
        ok = _print_report()
        return 0 if ok else 1
    if check_only:
        print("Ошибка: --check работает без аргументов", file=sys.stderr)
        return 2

    try:
        version = _validate(args[0])
    except VersionError as error:
        print(f"Ошибка: {error}", file=sys.stderr)
        return 2

    try:
        changed = apply_version(version)
    except VersionError as error:
        print(f"Ошибка: {error}", file=sys.stderr)
        return 2

    if not changed:
        print(f"Версия уже {version}, менять нечего.")
    else:
        for path in changed:
            print(f"обновлён {path.relative_to(ROOT)}")
        print(f"\nВерсия: {version}")
        print("Дальше:")
        print(f'  git add -A && git commit -m "chore: release {version}"')
        print(f"  git tag v{version}")
        print("  git push && git push --tags")

    return 0 if _print_report() else 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
