# -*- mode: python ; coding: utf-8 -*-
"""PyInstaller spec for the melodown server sidecar used by Tauri."""

import os
from pathlib import Path

from PyInstaller.utils.hooks import collect_all, collect_submodules, copy_metadata

datas = [("web", "web")]
binaries = []
hiddenimports = [
    "aiosqlite",
    "sqlalchemy.dialects.sqlite.aiosqlite",
]

for package in ("yt_dlp", "yt_dlp_ejs", "uvicorn"):
    package_datas, package_binaries, package_hiddenimports = collect_all(package)
    datas += package_datas
    binaries += package_binaries
    hiddenimports += package_hiddenimports

datas += copy_metadata("yt-dlp-ejs")

tools_dir = Path(os.environ["MELODOWN_BUILD_MEDIA_TOOLS"])
suffix = ".exe" if os.name == "nt" else ""
for name in ("deno", "ffmpeg", "ffprobe"):
    executable = tools_dir / f"{name}{suffix}"
    if not executable.is_file():
        raise FileNotFoundError(f"Не найден встроенный инструмент: {executable}")
    binaries.append((str(executable), "media-tools"))

hiddenimports += collect_submodules("app")

a = Analysis(
    ["scripts/server_entry.py"],
    pathex=["."],
    binaries=binaries,
    datas=datas,
    hiddenimports=hiddenimports,
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=["tkinter", "pytest"],
    noarchive=False,
)
pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    [],
    name="melodown-server",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=False,
    upx_exclude=[],
    runtime_tmpdir=None,
    console=False,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)
