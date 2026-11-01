from app.core.updates import is_newer, parse_version


def test_parse_version_strips_prefix_and_suffix():
    assert parse_version("v1.2.3") == (1, 2, 3)
    assert parse_version("1.10.0-beta.1") == (1, 10, 0)
    assert parse_version("nightly") == ()


def test_is_newer_compares_numerically():
    assert is_newer("v1.10.0", "1.9.0")
    assert not is_newer("v1.9.0", "1.10.0")


def test_is_newer_ignores_equal_and_shorter_versions():
    assert not is_newer("v1.1.0", "1.1.0")
    assert not is_newer("v1.1", "1.1.0")
    assert is_newer("v1.1.1", "1.1")


def test_is_newer_handles_garbage():
    assert not is_newer("", "1.1.0")
    assert not is_newer("v1.2.0", "")
