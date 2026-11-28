"""Готовит проверенные исполняемые файлы для desktop-сборки и Docker."""

from __future__ import annotations

import argparse
import hashlib
import os
import platform
import shutil
import sys
import tarfile
import tempfile
import urllib.request
import zipfile
from pathlib import Path

DENO_RELEASE = "https://github.com/denoland/deno/releases/download/v2.9.8"
FFMPEG_RELEASE = "https://github.com/BtbN/FFmpeg-Builds/releases/download/autobuild-2026-10-31-12-59"
FFMPEG_BUILD = "ffmpeg-n8.1.3-27-g8dca1586f4"

DENO_HASHES = {
    "x86_64-pc-windows-msvc": "cba84b26e0df5ae7f74fc66b86a95b866872a86292ebd84fa1e959927edd93bf",
    "aarch64-pc-windows-msvc": "b413b92b735b50c198e8fe3b85af1aa50e29d5f72e229aab701d132286e14cef",
    "x86_64-unknown-linux-gnu": "e890975bc2549807bbcc0b584ffafda72cc7c93349dd99c80483dd784f6c4609",
    "aarch64-unknown-linux-gnu": "d49596c20b9788cf8f5d8ddf644103d4bde56ed239753f0bdb92ba9361d51905",
}
FFMPEG_ARCHIVES = {
    "x86_64-pc-windows-msvc": (
        "win64-lgpl-8.1.zip",
        "d4c2a42673917b4a97104a3e9f082729a9730796b98664e37c0b7eba6c6c613d",
    ),
    "aarch64-pc-windows-msvc": (
        "winarm64-lgpl-8.1.zip",
        "c918142d3920f6b15282739dd523885be0586cbd8684f400acea9d4a8c97002f",
    ),
    "x86_64-unknown-linux-gnu": (
        "linux64-lgpl-8.1.tar.xz",
        "a64f3171e6ad1f7d358d28890a907ee478c331d1177ec696a93418f79ac3f1f3",
    ),
    "aarch64-unknown-linux-gnu": (
        "linuxarm64-lgpl-8.1.tar.xz",
        "2ea30086a637e3f0ad0f49a1b38db34121422b85e93cc82df2e01d9ee5200809",
    ),
}


def _sha256(path: Path) -> str:
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def _download(url: str, expected_hash: str, cache: Path) -> Path:
    destination = cache / url.split("/")[-1]
    if destination.is_file() and _sha256(destination) == expected_hash:
        return destination

    print(f"Скачиваю {url}", flush=True)
    with tempfile.TemporaryDirectory(dir=cache) as temporary:
        archive = Path(temporary) / destination.name
        with urllib.request.urlopen(url, timeout=120) as response, archive.open("wb") as target:
            shutil.copyfileobj(response, target)
        if _sha256(archive) != expected_hash:
            raise ValueError(f"Контрольная сумма не совпала: {destination.name}")
        archive.replace(destination)
    return destination


def _stage_deno(archive: Path, stage: Path, executable: str) -> None:
    with zipfile.ZipFile(archive) as package:
        with package.open(executable) as source, (stage / executable).open("wb") as destination:
            shutil.copyfileobj(source, destination)


def _stage_ffmpeg(archive: Path, stage: Path, members: dict[str, str]) -> None:
    if archive.suffix == ".zip":
        with zipfile.ZipFile(archive) as package:
            for name, member in members.items():
                with package.open(member) as source, (stage / name).open("wb") as destination:
                    shutil.copyfileobj(source, destination)
        return

    with tarfile.open(archive, "r|xz") as package:
        outputs = {member: name for name, member in members.items()}
        for entry in package:
            if entry.name in outputs and entry.isfile():
                with package.extractfile(entry) as source, (stage / outputs.pop(entry.name)).open("wb") as destination:
                    shutil.copyfileobj(source, destination)
            if not outputs:
                break
        if outputs:
            raise ValueError(f"В архиве {archive.name} отсутствуют файлы: {', '.join(outputs)}")


def prepare(target: str, output: Path, cache: Path, *, deno_only: bool) -> None:
    cache.mkdir(parents=True, exist_ok=True)
    output.mkdir(parents=True, exist_ok=True)
    suffix = ".exe" if "-windows-" in target else ""
    deno = f"deno{suffix}"
    deno_archive = _download(f"{DENO_RELEASE}/deno-{target}.zip", DENO_HASHES[target], cache)

    with tempfile.TemporaryDirectory(dir=output) as temporary:
        stage = Path(temporary)
        _stage_deno(deno_archive, stage, deno)
        if not deno_only:
            archive_suffix, checksum = FFMPEG_ARCHIVES[target]
            ffmpeg_archive = _download(f"{FFMPEG_RELEASE}/{FFMPEG_BUILD}-{archive_suffix}", checksum, cache)
            directory = ffmpeg_archive.name.removesuffix(".zip").removesuffix(".tar.xz")
            members = {f"{name}{suffix}": f"{directory}/bin/{name}{suffix}" for name in ("ffmpeg", "ffprobe")}
            _stage_ffmpeg(ffmpeg_archive, stage, members)
        for executable in stage.iterdir():
            if os.name != "nt":
                executable.chmod(0o755)
            executable.replace(output / executable.name)


def main() -> None:
    # При перенаправлении вывода Windows может выбрать кодировку без кириллицы.
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target", choices=DENO_HASHES)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--cache", type=Path, default=Path("build/media-archives"))
    parser.add_argument("--deno-only", action="store_true")
    args = parser.parse_args()
    target = args.target
    if target is None:
        # Сборка Docker выполняется на целевой архитектуре.
        arch = {"x86_64": "x86_64", "aarch64": "aarch64"}.get(platform.machine())
        if not sys.platform.startswith("linux") or arch is None:
            parser.error("Укажите поддерживаемую платформу через --target")
        target = f"{arch}-unknown-linux-gnu"
    try:
        prepare(target, args.output, args.cache, deno_only=args.deno_only)
    except (OSError, KeyError, ValueError, tarfile.TarError, zipfile.BadZipFile) as error:
        parser.exit(1, f"Не удалось подготовить инструменты для {target}: {error}\n")
    print(f"Инструменты готовы: {args.output}")


if __name__ == "__main__":
    main()
