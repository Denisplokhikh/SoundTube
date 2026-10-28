import pytest
from pydantic import ValidationError

from app.schemas import ImportRequest


def test_import_request_adds_scheme():
    request = ImportRequest(url="music.youtube.com/playlist?list=PL123")
    assert request.url == "https://music.youtube.com/playlist?list=PL123"


def test_import_request_strips_whitespace():
    request = ImportRequest(url="  https://youtu.be/abc  ")
    assert request.url == "https://youtu.be/abc"


def test_import_request_rejects_unknown_format():
    with pytest.raises(ValidationError):
        ImportRequest(url="https://youtu.be/abc", audio_format="wav")


def test_import_request_accepts_known_format():
    request = ImportRequest(url="https://youtu.be/abc", audio_format="MP3")
    assert request.audio_format == "mp3"
