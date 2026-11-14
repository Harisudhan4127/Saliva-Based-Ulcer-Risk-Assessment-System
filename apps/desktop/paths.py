"""Filesystem locations for the desktop app.

The frozen one-file build used to place the patient database inside the
PyInstaller extraction directory (``sys._MEIPASS``), which is deleted when the
process exits, so every saved record was lost on restart. Writable data now
lives in the per-user application data directory. A legacy database found next
to the executable or in the extraction directory is copied once, never moved.
"""

from __future__ import annotations

import os
import shutil
import sys
from pathlib import Path

APP_DIR_NAME = "UlcerRiskAI"
LEGACY_DIR_NAME = "User_data"
DB_FILE_NAME = "patients.db"


def project_root() -> Path:
    """Repository root in a source checkout (not meaningful when frozen)."""
    return Path(__file__).resolve().parents[2]


def shared_dir() -> Path:
    override = os.environ.get("SURAS_SHARED_DIR")
    if override:
        return Path(override).expanduser().resolve()
    if getattr(sys, "frozen", False):
        return Path(getattr(sys, "_MEIPASS", Path(sys.executable).parent)) / "shared"
    return project_root() / "shared"


def user_data_dir() -> Path:
    """Return the per-user writable directory, creating it if needed."""
    override = os.environ.get("SURAS_DATA_DIR")
    if override:
        directory = Path(override).expanduser().resolve()
    elif sys.platform.startswith("win"):
        base = os.environ.get("APPDATA") or str(Path.home() / "AppData" / "Roaming")
        directory = Path(base) / APP_DIR_NAME
    elif sys.platform == "darwin":
        directory = Path.home() / "Library" / "Application Support" / APP_DIR_NAME
    else:
        base = os.environ.get("XDG_DATA_HOME") or str(Path.home() / ".local" / "share")
        directory = Path(base) / "ulcer-risk-ai"
    directory.mkdir(parents=True, exist_ok=True)
    return directory


def legacy_data_dirs() -> list[Path]:
    """Directories older builds wrote into, most recent first."""
    candidates: list[Path] = []
    if getattr(sys, "frozen", False):
        meipass = getattr(sys, "_MEIPASS", None)
        if meipass:
            candidates.append(Path(meipass) / LEGACY_DIR_NAME)
        candidates.append(Path(sys.executable).resolve().parent / LEGACY_DIR_NAME)
    candidates.append(Path(__file__).resolve().parent / LEGACY_DIR_NAME)
    # De-duplicate while preserving order.
    seen: set[Path] = set()
    unique: list[Path] = []
    for candidate in candidates:
        if candidate not in seen:
            seen.add(candidate)
            unique.append(candidate)
    return unique


def database_path() -> Path:
    """Absolute path to the SQLite database, migrating a legacy copy once."""
    destination = user_data_dir() / DB_FILE_NAME
    if destination.exists():
        return destination
    for legacy_dir in legacy_data_dirs():
        legacy = legacy_dir / DB_FILE_NAME
        if legacy.is_file() and legacy.stat().st_size > 0:
            try:
                shutil.copy2(legacy, destination)
            except OSError:
                # Never block startup on a failed migration; the caller opens
                # the (possibly new) database in the user data directory.
                break
            break
    return destination
