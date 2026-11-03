#!/usr/bin/env python3
"""Версии SoundTube: у десктопа и Android они независимы.

Десктоп (Tauri + Python-сервер) и Android-приложение выпускаются разными
тегами и нумеруются отдельно, поэтому версия живёт в двух группах:

    desktop — app/__init__.py, pyproject.toml, uv.lock, package.json,
              package-lock.json, src-tauri/Cargo.toml, src-tauri/Cargo.lock,
              src-tauri/tauri.conf.json
    android — android/app/build.gradle.kts (versionName, а versionCode
              считается из него: major*10000 + minor*100 + patch)

Внутри группы версия обязана совпадать во всех файлах — это проверяет CI.
Совпадать между группами она не обязана: десктоп может быть 1.2.4, а
Android — 1.2.3.

    python scripts/version.py                     # показать обе версии
    python scripts/version.py --check             # проверить обе группы (CI)
    python scripts/version.py --check desktop     # только десктоп
    python scripts/version.py --check android     # только Android
    python scripts/version.py --desktop 1.2.4     # выставить десктоп
    python scripts/version.py --android 1.2.3     # выставить Android
    python scripts/version.py 1.2.4               # выставить обе сразу

Теги: `vX.Y.Z` — релиз десктопа, `android-vX.Y.Z` — релиз APK; CI подставляет
номер из тега, поэтому забытая правка не попадёт в артефакт.
"""

from __future__ import annotations

import argparse
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

# Файлы десктопа: путь и шаблон, где в группе 1 стоит версия.
DESKTOP_FILES: tuple[tuple[str, str], ...] = (
    (APP_INIT, r'^__version__\s*=\s*"([^"]+)"'),
    (PYPROJECT, r'^version\s*=\s*"([^"]+)"'),
    (PACKAGE_JSON, r'^\s*"version":\s*"([^"]+)"'),
    (TAURI_CONF, r'^\s*"version":\s*"([^"]+)"'),
    (CARGO_TOML, r'^version\s*=\s*"([^"]+)"'),
)

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


def version_code_for(version: str) -> int:
    """versionCode Android из versionName — та же схема, что была до разделения."""

    match = SEMVER_RE.match(version)
    if not match:
        raise VersionError(f"{version} не подходит для Android versionName")

    minor = int(match.group("minor"))
    patch = int(match.group("patch"))
    # major*10000 + minor*100 + patch: при minor/patch >= 100 соседние версии
    # получают одинаковый код, и обновление перестаёт устанавливаться.
    if minor > 99 or patch > 99:
        raise VersionError(
            f"{version}: для Android minor и patch должны быть меньше 100"
        )

    return (
        int(match.group("major")) * 10_000
        + minor * 100
        + patch
    )


def current_version(root: Path = ROOT) -> str:
    """Версия десктопа — она же эталон для своей группы."""

    text = _read(root / APP_INIT)
    match = re.search(r'^__version__\s*=\s*"([^"]+)"', text, flags=re.MULTILINE)
    if not match:
        raise VersionError(f"{APP_INIT}: нет строки __version__ = \"...\"")
    return match.group(1)


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


def read_desktop(root: Path = ROOT) -> list[FileVersion]:
    """Все файлы, где продублирована версия десктопа."""

    result: list[FileVersion] = []

    for relative, pattern in DESKTOP_FILES:
        path = root / relative
        text = _read(path)
        match = re.search(pattern, text, flags=re.MULTILINE)
        if not match:
            raise VersionError(f"{relative}: версия не найдена")
        result.append(FileVersion(path, match.group(1)))

    for relative in (UV_LOCK, CARGO_LOCK):
        path = root / relative
        text = _read(path)
        match = re.search(
            rf'name = "{re.escape(PACKAGE_NAME)}"\s*\nversion = "([^"]+)"',
            text,
        )
        if not match:
            raise VersionError(f"{relative}: версия не найдена")
        result.append(FileVersion(path, match.group(1)))

    package_lock = root / PACKAGE_LOCK
    if package_lock.is_file():
        # В файле версия лежит дважды (корень и packages[""]), но она одна и та же.
        result.append(FileVersion(package_lock, _package_lock_versions(package_lock)[0]))

    return result


def android_version(root: Path = ROOT) -> str:
    text = _read(root / ANDROID_GRADLE)
    match = re.search(r'versionName\s*=\s*"([^"]+)"', text)
    if not match:
        raise VersionError(f"{ANDROID_GRADLE}: нет versionName")
    return match.group(1)


def android_code(root: Path = ROOT) -> int:
    text = _read(root / ANDROID_GRADLE)
    match = re.search(r"versionCode\s*=\s*(\d+)", text)
    if not match:
        raise VersionError(f"{ANDROID_GRADLE}: нет versionCode")
    return int(match.group(1))


def read_all(root: Path = ROOT) -> list[FileVersion]:
    """Версии из всех файлов (обе группы) — для отчёта и тестов."""

    result = read_desktop(root)
    result.append(FileVersion(root / ANDROID_GRADLE, android_version(root)))
    return result


def _update(relative: str, transform, root: Path, changed: list[Path]) -> None:
    path = root / relative
    if not path.is_file():
        return
    text = _read(path)
    new_text = transform(path, text)
    if new_text != text:
        _write(path, new_text)
        changed.append(path)


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
    code = version_code_for(version)
    new_text = _sub_once(path, text, r'(versionName\s*=\s*")[^"]+(")', version)
    new_text, count = re.subn(r"(versionCode\s*=\s*)\d+", rf"\g<1>{code}", new_text, count=1)
    if count != 1:
        raise VersionError(f"{path}: не нашёл versionCode")
    return new_text


def apply_desktop(new_version: str, root: Path = ROOT) -> list[Path]:
    """Проставляет версию десктопа. Возвращает список изменённых файлов."""

    version = _validate(new_version)
    changed: list[Path] = []

    _update(
        APP_INIT,
        lambda path, text: _sub_once(path, text, r'(?m)(^__version__\s*=\s*")[^"]+(")', version),
        root,
        changed,
    )
    _update(
        PYPROJECT,
        lambda path, text: _sub_once(path, text, r'(?m)(^version\s*=\s*")[^"]+(")', version),
        root,
        changed,
    )
    _update(
        PACKAGE_JSON,
        lambda path, text: _sub_once(path, text, r'(?m)(^\s*"version":\s*")[^"]+(")', version),
        root,
        changed,
    )
    _update(
        TAURI_CONF,
        lambda path, text: _sub_once(path, text, r'(?m)(^\s*"version":\s*")[^"]+(")', version),
        root,
        changed,
    )
    _update(
        CARGO_TOML,
        lambda path, text: _sub_once(path, text, r'(?m)(^version\s*=\s*")[^"]+(")', version),
        root,
        changed,
    )
    _update(
        UV_LOCK,
        lambda path, text: _sub_package_block(path, text, PACKAGE_NAME, version),
        root,
        changed,
    )
    _update(
        CARGO_LOCK,
        lambda path, text: _sub_package_block(path, text, PACKAGE_NAME, version),
        root,
        changed,
    )
    _update(
        PACKAGE_LOCK,
        lambda path, text: _apply_package_lock(path, text, version),
        root,
        changed,
    )

    return changed


def apply_android(new_version: str, root: Path = ROOT) -> list[Path]:
    """Проставляет версию Android (versionName + versionCode)."""

    version = _validate(new_version)
    changed: list[Path] = []
    _update(
        ANDROID_GRADLE,
        lambda path, text: _apply_android(path, text, version),
        root,
        changed,
    )
    return changed


def apply_version(new_version: str, root: Path = ROOT) -> list[Path]:
    """Проставляет одну версию в обе группы — как до разделения."""

    return apply_desktop(new_version, root) + apply_android(new_version, root)


def check(component: str = "all", root: Path = ROOT) -> bool:
    """Проверяет, что внутри группы версии не разъехались."""

    ok = True

    if component in ("all", "desktop"):
        entries = read_desktop(root)
        canonical = entries[0].version
        if any(entry.version != canonical for entry in entries):
            ok = False

    if component in ("all", "android"):
        version = android_version(root)
        try:
            expected = version_code_for(version)
        except VersionError:
            expected = -1
        if android_code(root) != expected:
            ok = False

    return ok


def _print_report(root: Path = ROOT) -> bool:
    entries = read_desktop(root)
    canonical = entries[0].version
    width = max(len(str(entry.path.relative_to(root))) for entry in entries)
    ok = True

    print("desktop")
    for entry in entries:
        relative = str(entry.path.relative_to(root)).replace("\\", "/")
        mark = "ok" if entry.version == canonical else "!!"
        if entry.version != canonical:
            ok = False
        print(f"  {mark}  {relative:<{width}}  {entry.version}")

    version = android_version(root)
    code = android_code(root)
    try:
        expected = version_code_for(version)
    except VersionError as error:
        expected = -1
        print(f"  !!  {ANDROID_GRADLE}  {error}")

    android_ok = code == expected
    ok = ok and android_ok
    print("android")
    print(
        f"  {'ok' if android_ok else '!!'}  {ANDROID_GRADLE}  "
        f"versionName={version} versionCode={code}"
        + ("" if android_ok else f" (ожидался {expected})")
    )

    print()
    if ok:
        print(f"Десктоп: {canonical}, Android: {version} — внутри групп всё сходится.")
    else:
        print("Версии внутри группы разъехались.")
        print(f"Починить десктоп: python scripts/version.py --desktop {canonical}")
        print(f"Починить Android: python scripts/version.py --android {version}")
    return ok


def main(argv: list[str]) -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")

    parser = argparse.ArgumentParser(
        prog="version.py",
        description="Версии SoundTube: десктоп и Android нумеруются независимо.",
    )
    parser.add_argument("version", nargs="?", help="выставить обе группы сразу")
    parser.add_argument("--desktop", metavar="X.Y.Z", help="версия десктопа")
    parser.add_argument("--android", metavar="X.Y.Z", help="версия Android")
    parser.add_argument(
        "--check",
        nargs="?",
        const="all",
        choices=("all", "desktop", "android"),
        metavar="ГРУППА",
        help="проверить согласованность: all (по умолчанию), desktop или android",
    )
    args = parser.parse_args(argv[1:])

    if args.check:
        if args.version or args.desktop or args.android:
            parser.error("--check не сочетается с установкой версии")
        return 0 if check(args.check) else 1

    try:
        desktop = _validate(args.desktop) if args.desktop else None
        android = _validate(args.android) if args.android else None
        if args.version:
            both = _validate(args.version)
            desktop = desktop or both
            android = android or both
    except VersionError as error:
        print(f"Ошибка: {error}", file=sys.stderr)
        return 2

    if desktop is None and android is None:
        return 0 if _print_report() else 1

    changed: list[Path] = []
    try:
        if desktop:
            changed += apply_desktop(desktop)
        if android:
            changed += apply_android(android)
    except VersionError as error:
        print(f"Ошибка: {error}", file=sys.stderr)
        return 2

    if not changed:
        print("Версия уже такая, менять нечего.")
    else:
        for path in changed:
            print(f"обновлён {path.relative_to(ROOT)}")

    _print_report()
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
