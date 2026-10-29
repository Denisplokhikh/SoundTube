from app.core import ytdlp
from app.platforms import detect_platform
from app.platforms.soundcloud import _collect_tracks
from app.platforms.soundcloud import _track_from_entry as soundcloud_track
from app.platforms.soundcloud import _track_from_meta as soundcloud_meta
from app.platforms.youtube import (
    _track_from_entry as youtube_track,
    is_radio_url,
    single_video_url,
)


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


STATION_ENTRY = {
    "id": "1957261763",
    "_type": "url_transparent",
    "url": "https://api-v2.soundcloud.com/tracks/1957261763",
}


def test_soundcloud_station_entry_becomes_track_from_api():
    track = soundcloud_meta(
        STATION_ENTRY,
        {
            "id": 1957261763,
            "title": "Young White",
            "user": {"username": "FallenCastle"},
            "duration": 576714,
            "permalink_url": "https://soundcloud.com/deadcastlefl/whitedistrictflow",
        },
    )
    assert track is not None
    assert track.title == "Young White"
    assert track.artist == "FallenCastle"
    assert track.duration == 576.714
    assert track.url == "https://soundcloud.com/deadcastlefl/whitedistrictflow"
    assert track.source_id == "1957261763"


def test_soundcloud_station_entries_are_filled_in_order(monkeypatch):
    entries = [
        STATION_ENTRY,
        {"id": 2, "title": "Заголовок из сета", "uploader": "B", "url": "https://soundcloud.com/a/b"},
        {"id": "3", "url": "https://api-v2.soundcloud.com/tracks/3"},
    ]
    monkeypatch.setattr(
        ytdlp,
        "soundcloud_track_meta",
        lambda ids: {
            "1957261763": {
                "id": 1957261763,
                "title": "Первый",
                "user": {"username": "A"},
                "duration": 1000,
                "permalink_url": "https://soundcloud.com/a/1",
            },
            "3": {
                "id": 3,
                "title": "Третий",
                "user": {"username": "C"},
                "duration": 2000,
                "permalink_url": "https://soundcloud.com/c/3",
            },
        },
    )

    tracks = _collect_tracks(entries)

    assert [track.title for track in tracks] == ["Первый", "Заголовок из сета", "Третий"]
    assert [track.artist for track in tracks] == ["A", "B", "C"]


def test_radio_mix_link_is_detected():
    assert is_radio_url(
        "https://www.youtube.com/watch?v=UTNP93A_FJI&list=RDUTNP93A_FJI&start_radio=1"
    )


def test_regular_playlist_is_not_radio():
    assert not is_radio_url("https://www.youtube.com/playlist?list=PL123")


def test_radio_link_collapses_to_single_video():
    assert (
        single_video_url(
            "https://www.youtube.com/watch?v=UTNP93A_FJI&list=RDUTNP93A_FJI&start_radio=1"
        )
        == "https://www.youtube.com/watch?v=UTNP93A_FJI"
    )


def test_short_link_collapses_to_single_video():
    assert (
        single_video_url("https://youtu.be/abc123?list=RDabc123")
        == "https://www.youtube.com/watch?v=abc123"
    )
