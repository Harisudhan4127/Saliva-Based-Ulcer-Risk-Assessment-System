"""
updater.py  —  Auto-updater for Ulcer Risk AI
Runs in a QThread so the UI stays responsive.
"""
import urllib.request
import json
import os
import sys
import subprocess

from PyQt5.QtCore import QThread, pyqtSignal

CURRENT_VERSION = "v2.0.0"
API_URL = (
    "https://api.github.com/repos/"
    "Harisudhan4127/Saliva-Based-Ulcer-Risk-Assessment-System/releases/latest"
)


class UpdateWorker(QThread):
    """Background thread: checks GitHub → downloads → launches installer."""

    # ── signals ──────────────────────────────────────────────────────────────
    status_changed  = pyqtSignal(str)   # short status text
    progress_changed = pyqtSignal(int)  # 0-100
    update_available = pyqtSignal(str)  # latest version string
    up_to_date      = pyqtSignal()
    error_occurred  = pyqtSignal(str)
    ready_to_install = pyqtSignal(str)  # path to downloaded exe

    def run(self):
        try:
            # ── 1. fetch latest release info ──────────────────────────────
            self.status_changed.emit("Checking for updates…")
            req = urllib.request.Request(
                API_URL,
                headers={"User-Agent": "UlcerRiskAI-Updater"}
            )
            with urllib.request.urlopen(req, timeout=10) as res:
                data = json.loads(res.read().decode())

            latest = data.get("tag_name", "").strip()

            if not latest or latest == CURRENT_VERSION:
                self.status_changed.emit("You are on the latest version.")
                self.up_to_date.emit()
                return

            # ── 2. find .exe asset ────────────────────────────────────────
            self.update_available.emit(latest)
            self.status_changed.emit(f"Update {latest} found. Downloading…")

            exe_url = None
            for asset in data.get("assets", []):
                if asset["name"].lower().endswith(".exe"):
                    exe_url = asset["browser_download_url"]
                    break

            if not exe_url:
                self.error_occurred.emit("No installer found in the release assets.")
                return

            # ── 3. download with progress ──────────────────────────────────
            save_path = os.path.join(
                os.environ.get("TEMP", os.path.expanduser("~")),
                f"UlcerRiskAI_Update_{latest}.exe"
            )

            def _reporthook(block_num, block_size, total_size):
                if total_size > 0:
                    pct = min(int(block_num * block_size * 100 / total_size), 100)
                    self.progress_changed.emit(pct)
                    mb_done = block_num * block_size / 1_048_576
                    mb_total = total_size / 1_048_576
                    self.status_changed.emit(
                        f"Downloading… {mb_done:.1f} MB / {mb_total:.1f} MB"
                    )

            urllib.request.urlretrieve(exe_url, save_path, reporthook=_reporthook)

            self.progress_changed.emit(100)
            self.status_changed.emit("Download complete. Launching installer…")
            self.ready_to_install.emit(save_path)

        except Exception as exc:
            self.error_occurred.emit(str(exc))
            self.status_changed.emit(f"Update failed: {exc}")


def launch_installer(path: str):
    """Start the installer and exit the current app."""
    subprocess.Popen([path], shell=True)
    sys.exit(0)