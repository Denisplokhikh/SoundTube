from pathlib import Path

from app.core.naming import build_relative_path, sanitize, unique_path


def test_sanitize_replaces_windows_forbidden_characters():
    assert sanitize('AC/DC: "Live"') == "AC_DC_ _Live_"


def test_sanitize_strips_trailing_dots_and_spaces():
    assert sanitize("Album... ") == "Album"


def test_sanitize_escapes_reserved_names():
    assert sanitize("CON") == "_CON"
    assert sanitize("lpt1") == "_lpt1"


def test_sanitize_uses_fallback_for_blank_values():
    assert sanitize("   ") == "Unknown"
    assert sanitize(None) == "Unknown"


def test_sanitize_limits_length():
    assert len(sanitize("a" * 300)) == 120


def test_build_relative_path_with_album():
    path = build_relative_path(
        title="Hells Bells",
        artist="AC/DC",
        album="Back in Black",
        extension=".mp3",
    )
    assert path == Path("AC_DC") / "Back in Black" / "AC_DC - Hells Bells.mp3"


def test_build_relative_path_without_album():
    path = build_relative_path(
        title="Song",
        artist=None,
        album=None,
        extension=".m4a",
    )
    assert path == Path("Unknown artist") / "Unknown artist - Song.m4a"


def test_unique_path_adds_counter(tmp_path):
    original = tmp_path / "track.mp3"
    original.write_bytes(b"x")

    assert unique_path(original) == tmp_path / "track (2).mp3"

    (tmp_path / "track (2).mp3").write_bytes(b"x")
    assert unique_path(original) == tmp_path / "track (3).mp3"
