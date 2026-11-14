"""Pure update logic with no Qt dependency (unit-testable).

The UI thread wrapper lives in ``updater.py``.
"""

from __future__ import annotations

import json
import os
import re
import urllib.parse

from paths import shared_dir

DEFAULT_REPO = "Harisudhan4127/Saliva-Based-Ulcer-Risk-Assessment-System"
ALLOWED_API_HOSTS = ("api.github.com",)
ALLOWED_DOWNLOAD_HOSTS = (
    "github.com",
    "www.github.com",
    "objects.githubusercontent.com",
    "release-assets.githubusercontent.com",
)
MAX_DOWNLOAD_BYTES = 512 * 1024 * 1024
TAG_PATTERN = re.compile(r"^v?(\d+)\.(\d+)\.(\d+)(?:[-+].*)?$")


def app_version() -> str:
    """Version from shared/version.json (single source of truth)."""
    try:
        data = json.loads((shared_dir() / "version.json").read_text(encoding="utf-8"))
        value = str(data["version"]).strip()
    except (OSError, KeyError, TypeError, ValueError):
        return "0.0.0"
    return value.lstrip("vV")


def repository() -> str:
    return os.environ.get("SURAS_UPDATE_REPO", DEFAULT_REPO).strip() or DEFAULT_REPO


def api_url() -> str:
    return f"https://api.github.com/repos/{repository()}/releases/latest"


def parse_version(value: str) -> tuple[int, int, int] | None:
    match = TAG_PATTERN.match((value or "").strip())
    if not match:
        return None
    return tuple(int(part) for part in match.groups())  # type: ignore[return-value]


def is_newer(candidate: str, current: str) -> bool:
    parsed_candidate = parse_version(candidate)
    parsed_current = parse_version(current)
    if parsed_candidate is None or parsed_current is None:
        return False
    return parsed_candidate > parsed_current


def require_https(url: str, hosts: tuple[str, ...]) -> str:
    parsed = urllib.parse.urlparse(url)
    if parsed.scheme != "https":
        raise ValueError("Update URL is not HTTPS; refusing to download")
    host = (parsed.hostname or "").lower()
    if not any(host == allowed or host.endswith("." + allowed) for allowed in hosts):
        raise ValueError(f"Update URL host is not trusted: {host}")
    return url


def parse_release_asset(payload: dict) -> tuple[str, str, str]:
    """Return ``(tag, download_url, sha256)`` for the Windows installer.

    GitHub publishes a ``digest`` field for release assets. A release without a
    SHA-256 digest is rejected: the updater must never execute an unverified
    binary.
    """
    tag = str(payload.get("tag_name", "")).strip()
    if not tag:
        raise ValueError("Release has no tag")
    for asset in payload.get("assets") or []:
        name = str(asset.get("name", ""))
        if not name.lower().endswith(".exe"):
            continue
        url = require_https(str(asset.get("browser_download_url", "")), ALLOWED_DOWNLOAD_HOSTS)
        digest = str(asset.get("digest") or "").strip().lower()
        if not digest.startswith("sha256:"):
            raise ValueError(
                "Release installer has no published SHA-256 digest; refusing to install"
            )
        expected = digest.split(":", 1)[1]
        if len(expected) != 64 or any(c not in "0123456789abcdef" for c in expected):
            raise ValueError("Release digest is malformed")
        return tag, url, expected
    raise ValueError("No Windows installer found in the release assets")
