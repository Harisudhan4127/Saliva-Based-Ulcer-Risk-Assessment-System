# -*- mode: python ; coding: utf-8 -*-
"""PyInstaller build spec for the SURAS desktop application.

Build with:
    pyinstaller UlcerRiskAI.spec

Produces a one-folder bundle in ``dist/UlcerRiskAI`` which the Inno Setup
script (``UlcerRiskAI_Setup.iss``) packages into an installer.

The ONNX risk model and every JSON contract file live in the repository-level
``shared/`` directory (the single source of truth shared with the Android app).
They are copied into the bundle as ``_MEIPASS/shared`` so ``paths.shared_dir()``
finds them at runtime.
"""

from pathlib import Path

# apps/desktop/UlcerRiskAI.spec -> repo root is two levels up.
REPO_ROOT = Path(SPECPATH).resolve().parents[2]
SHARED_DIR = REPO_ROOT / "shared"
DESKTOP_DIR = Path(SPECPATH).resolve()

datas = [
    # Shared contracts + checksum-verified ONNX model.
    (str(SHARED_DIR), "shared"),
    # Application icon / banner.
    (str(DESKTOP_DIR / "assets"), "assets"),
]

hiddenimports = [
    "onnxruntime",
    "sklearn",
    "sklearn.ensemble",
    "sklearn.tree",
    "sklearn.utils._typedefs",
    "sklearn.neighbors._partition_nodes",
    "pandas",
    "numpy",
]

excludes = [
    "PyQt5.QtWebEngineWidgets",
    "PyQt5.QtWebEngineCore",
    "PyQt5.QtWebEngine",
    "PyQt5.QtBluetooth",
    "PyQt5.QtMultimedia",
    "PyQt5.QtMultimediaWidgets",
    "PyQt5.QtQuick",
    "PyQt5.QtQml",
    "PyQt5.QtSql",
    "PyQt5.QtTest",
    "tkinter",
    "matplotlib",
    "IPython",
    "notebook",
    "pytest",
]

a = Analysis(
    [str(DESKTOP_DIR / "main.py")],
    pathex=[str(DESKTOP_DIR)],
    binaries=[],
    datas=datas,
    hiddenimports=hiddenimports,
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=excludes,
    noarchive=False,
)

pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name="UlcerRiskAI",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    console=False,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
    icon=str(DESKTOP_DIR / "assets" / "app.ico"),
    version=str(DESKTOP_DIR / "version_info.txt"),
)

coll = COLLECT(
    exe,
    a.binaries,
    a.datas,
    strip=False,
    upx=True,
    upx_exclude=[
        # UPX-compressing Qt platform plugins is a known source of startup
        # crashes; leave them alone.
        "qwindows.dll",
        "qwindowsd.dll",
        "Qt5Core.dll",
        "python3*.dll",
    ],
    name="UlcerRiskAI",
)
