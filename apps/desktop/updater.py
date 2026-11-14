"""updater.py  —  Auto-updater for Ulcer Risk AI

Runs in a QThread so the UI stays responsive. Pure parsing/version logic lives
in ``update_logic.py``; this module only adds the Qt worker and process launch.

Security properties (see docs/SECURITY_AUDIT.md):
* Release metadata is fetched over HTTPS from api.github.com only.
* The download URL must be HTTPS on a GitHub-owned host.
* The release asset must publish a ``sha256:`` digest. The file is hashed while
  it is downloaded and the download is rejected on any mismatch. An updater that
  runs an unverified executable is a remote-code-execution path, so a missing
  digest is a hard failure, not a warning.
* The installer is launched directly, never through a shell.
* Automatic install is Windows-only because the release artifact is a Windows
  installer; other platforms are told to download manually instead of being
  handed a file that cannot run.
"""

from __future__ import annotations

import hashlib
import json
import os
import subprocess
import sys
import tempfile
import urllib.request

from PyQt5.QtCore import QThread, pyqtSignal

from update_logic import (
    MAX_DOWNLOAD_BYTES,
    api_url,
    app_version,
    is_newer,
    parse_release_asset,
    require_https,
    ALLOWED_API_HOSTS,
)

CURRENT_VERSION = app_version()
USER_AGENT = "UlcerRiskAI-Updater/2"


class UpdateWorker(QThread):
    """Background thread: checks GitHub → downloads → verifies → offers install."""

    status_changed = pyqtSignal(str)   # short status text
    progress_changed = pyqtSignal(int)  # 0-100
    update_available = pyqtSignal(str)  # latest version string
    up_to_date = pyqtSignal()
    error_occurred = pyqtSignal(str)
    ready_to_install = pyqtSignal(str)  # path to downloaded, verified installer

    def run(self) -> None:
        try:
            self.status_changed.emit("Checking for updates…")
            request = urllib.request.Request(
                require_https(api_url(), ALLOWED_API_HOSTS),
                headers={"User-Agent": USER_AGENT, "Accept": "application/vnd.github+json"},
            )
            with urllib.request.urlopen(request, timeout=15) as response:
                if response.status != 200:
                    raise ValueError(f"GitHub returned HTTP {response.status}")
                data = json.loads(response.read(8 * 1024 * 1024).decode("utf-8"))

            latest = str(data.get("tag_name", "")).strip()
            if not latest:
                raise ValueError("Release metadata has no tag_name")
            if not is_newer(latest, CURRENT_VERSION):
                self.status_changed.emit("You are on the latest version.")
                self.up_to_date.emit()
                return

            self.update_available.emit(latest)
            tag, url, expected_digest = parse_release_asset(data)
            if not sys.platform.startswith("win"):
                self.error_occurred.emit(
                    f"Version {tag} is available. Automatic installation is only "
                    "supported on Windows; download it manually from the release page."
                )
                return

            self.status_changed.emit(f"Update {tag} found. Downloading…")
            target = os.path.join(tempfile.gettempdir(), f"UlcerRiskAI_Update_{tag}.exe")
            actual_digest = self._download(url, target)
            if actual_digest != expected_digest:
                try:
                    os.remove(target)
                except OSError:
                    pass
                raise ValueError(
                    "Downloaded installer failed SHA-256 verification; refusing to run it"
                )

            self.progress_changed.emit(100)
            self.status_changed.emit("Download verified. Launching installer…")
            self.ready_to_install.emit(target)
        except Exception as exc:  # noqa: BLE001 - surfaced verbatim to the UI
            self.error_occurred.emit(str(exc))
            self.status_changed.emit(f"Update failed: {exc}")

    def _download(self, url: str, target: str) -> str:
        digest = hashlib.sha256()
        received = 0
        request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(request, timeout=30) as response, open(target, "wb") as out:
            total = int(response.headers.get("Content-Length") or 0)
            if total > MAX_DOWNLOAD_BYTES:
                raise ValueError("Update is larger than the allowed size")
            while True:
                chunk = response.read(256 * 1024)
                if not chunk:
                    break
                received += len(chunk)
                if received > MAX_DOWNLOAD_BYTES:
                    raise ValueError("Update is larger than the allowed size")
                digest.update(chunk)
                out.write(chunk)
                if total:
                    percent = min(int(received * 100 / total), 99)
                    self.progress_changed.emit(percent)
                    self.status_changed.emit(
                        f"Downloading… {received / 1_048_576:.1f} MB / {total / 1_048_576:.1f} MB"
                    )
        if received == 0:
            raise ValueError("Downloaded installer is empty")
        return digest.hexdigest()


def launch_installer(path: str) -> None:
    """Start the verified installer directly (no shell) and exit."""
    if not os.path.isfile(path):
        raise FileNotFoundError(path)
    if not path.lower().endswith(".exe"):
        raise ValueError("Refusing to execute a file that is not the .exe installer")
    subprocess.Popen([path], close_fds=True)
    sys.exit(0)
