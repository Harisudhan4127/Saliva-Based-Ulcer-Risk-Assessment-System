"""Tests for updater decision logic. No network access is performed here."""

from __future__ import annotations

import pytest

import update_logic


def _asset(url="https://github.com/o/r/releases/download/v9.9.9/Setup.exe", digest="sha256:" + "a" * 64):
    return {
        "tag_name": "v9.9.9",
        "assets": [
            {
                "name": "Setup.exe",
                "browser_download_url": url,
                "digest": digest,
            }
        ],
    }


@pytest.mark.parametrize(
    "value,expected",
    [
        ("2.1.0", (2, 1, 0)),
        ("v2.1.0", (2, 1, 0)),
        ("v2.1.0-beta.1", (2, 1, 0)),
        ("", None),
        ("latest", None),
    ],
)
def test_parse_version(value, expected):
    assert update_logic.parse_version(value) == expected


@pytest.mark.parametrize(
    "candidate,current,newer",
    [
        ("v2.2.0", "2.1.0", True),
        ("v2.1.1", "2.1.0", True),
        ("v3.0.0", "2.1.0", True),
        ("v2.1.0", "2.1.0", False),
        ("v2.0.9", "2.1.0", False),
        ("not-a-version", "2.1.0", False),
        ("v9.0.0", "not-a-version", False),
    ],
)
def test_is_newer(candidate, current, newer):
    assert update_logic.is_newer(candidate, current) is newer


def test_app_version_comes_from_shared_spec():
    import json

    shared = update_logic.shared_dir()
    expected = json.loads((shared / "version.json").read_text())["version"]
    assert update_logic.app_version() == expected


def test_release_asset_requires_https():
    payload = _asset(url="http://github.com/o/r/releases/download/v9/Setup.exe")
    with pytest.raises(ValueError, match="HTTPS"):
        update_logic.parse_release_asset(payload)


def test_release_asset_rejects_untrusted_host():
    payload = _asset(url="https://evil.example.com/Setup.exe")
    with pytest.raises(ValueError, match="not trusted"):
        update_logic.parse_release_asset(payload)


def test_release_asset_requires_digest():
    payload = _asset(digest="")
    with pytest.raises(ValueError, match="no published SHA-256 digest"):
        update_logic.parse_release_asset(payload)


def test_release_asset_rejects_non_sha256_digest():
    payload = _asset(digest="md5:" + "a" * 32)
    with pytest.raises(ValueError, match="no published SHA-256 digest"):
        update_logic.parse_release_asset(payload)


def test_release_asset_rejects_malformed_digest():
    payload = _asset(digest="sha256:xyz")
    with pytest.raises(ValueError, match="malformed"):
        update_logic.parse_release_asset(payload)


def test_release_without_executable_is_rejected():
    payload = {"tag_name": "v9.9.9", "assets": [{"name": "notes.txt"}]}
    with pytest.raises(ValueError, match="No Windows installer"):
        update_logic.parse_release_asset(payload)


def test_valid_release_asset_is_accepted():
    tag, url, digest = update_logic.parse_release_asset(_asset())
    assert tag == "v9.9.9"
    assert url.endswith("Setup.exe")
    assert digest == "a" * 64
