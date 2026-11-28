from __future__ import annotations

import os
import sys

import pytest

from app.core.ytdlp import base_options


@pytest.mark.parametrize("platform", ["youtube", "soundcloud"])
def test_frozen_options_resolve_bundled_media_tools(monkeypatch, tmp_path, platform):
    bundle = tmp_path / "Сборка SoundTube"
    tools = bundle / "media-tools"
    tools.mkdir(parents=True)
    suffix = ".exe" if os.name == "nt" else ""
    for name in ("deno", "ffmpeg", "ffprobe"):
        (tools / f"{name}{suffix}").touch()
    monkeypatch.setattr(sys, "_MEIPASS", str(bundle), raising=False)

    options = base_options(platform)

    assert options["ffmpeg_location"] == str(tools)
    if platform == "youtube":
        assert options["js_runtimes"] == {"deno": {"path": str(tools / f"deno{suffix}")}}
    else:
        assert "js_runtimes" not in options


def test_source_options_leave_runtime_discovery_to_ytdlp(monkeypatch):
    monkeypatch.delattr(sys, "_MEIPASS", raising=False)

    options = base_options("youtube")

    assert "ffmpeg_location" not in options
    assert "js_runtimes" not in options
