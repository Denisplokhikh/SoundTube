from app.platforms import detect_platform
from app.platforms.soundcloud import _track_from_entry as soundcloud_track
from app.platforms.youtube import _track_from_entry as youtube_track


def test_detects_youtube_music_playlist():
    assert detect_platform("https://music.youtube.com/playlist?list=PL123") == "youtube"


def test_detects_short_youtube_link():
    assert detect_platform("https://youtu.be/dQw4w9WgXcQ") == "youtube"


def test_detects_soundcloud_set():
    assert detect_platform("https://soundcloud.com/artist/sets/name") == "soundcloud"


def test_ignores_unknown_hosts():
    assert detect_platform("https://example.com/playlist") is None


def test_youtube_entry_becomes_track_spec():
    track = youtube_track(
        {
            "id": "abc123",
            "title": "Song",
            "uploader": "Artist",
            "duration": 210,
        }
    )
    assert track is not None
    assert track.url == "https://www.youtube.com/watch?v=abc123"
    assert track.artist == "Artist"
    assert track.duration == 210.0


def test_youtube_entry_without_title_is_skipped():
    assert youtube_track({"id": "abc123"}) is None


def test_soundcloud_entry_keeps_url():
    track = soundcloud_track(
        {
            "id": 42,
            "url": "https://soundcloud.com/artist/song",
            "title": "Song",
            "uploader": "Artist",
        }
    )
    assert track is not None
    assert track.url == "https://soundcloud.com/artist/song"
    assert track.source_id == "42"
