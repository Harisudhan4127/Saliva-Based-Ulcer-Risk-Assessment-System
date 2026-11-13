# Ulcer Risk AI — Build & Distribution Guide

## Prerequisites

```
pip install pyinstaller pyqt5 pandas scikit-learn joblib reportlab
```

Install **UPX** (optional, reduces EXE size ~30 %):
- Download from https://upx.github.io
- Add `upx.exe` to your system PATH

Install **Inno Setup 6**:
- Download from https://jrsoftware.org/isdl.php

---

## Step 1 — Build the EXE (PyInstaller)

```bat
pyinstaller UlcerRiskAI.spec
```

Output folder: `dist\UlcerRiskAI\`

### Size optimisation tips

| Technique | Effect |
|---|---|
| `excludes` in spec (already set) | Removes Qt WebEngine, Bluetooth, Multimedia |
| UPX compression (already enabled) | ~25-35 % smaller binaries |
| `--noupx` on `qwindows.dll` | Avoids crash from UPX on Qt plugins |
| Delete `dist\UlcerRiskAI\PyQt5\Qt5\translations\` | Saves ~15 MB if i18n not needed |
| Delete `dist\UlcerRiskAI\PyQt5\Qt5\plugins\imageformats\` except `qico.dll` | Saves ~5 MB |

**Optional manual cleanup before packaging:**
```bat
cd dist\UlcerRiskAI
rmdir /s /q PyQt5\Qt5\translations
del PyQt5\Qt5\plugins\imageformats\qgif.dll
del PyQt5\Qt5\plugins\imageformats\qjpeg.dll
del PyQt5\Qt5\plugins\imageformats\qtiff.dll
del PyQt5\Qt5\plugins\imageformats\qwebp.dll
```

---

## Step 2 — Create the Installer (Inno Setup)

```bat
"C:\Program Files (x86)\Inno Setup 6\ISCC.exe" UlcerRiskAI_Setup.iss
```

Output: `Output\UlcerRiskAI_Setup_v2.0.0.exe`

### What the installer does
- Installs to `%LocalAppData%\Programs\Ulcer Risk AI` (no UAC required)
- Creates Start Menu shortcut
- Optional Desktop shortcut
- Optional Windows startup entry
- Detects if app is already running before upgrade
- Offers to launch the app after install

---

## Step 3 — Publish a GitHub Release

1. Tag the commit: `git tag v2.0.0 && git push --tags`
2. Create a GitHub Release with tag `v2.0.0`
3. Upload `UlcerRiskAI_Setup_v2.0.0.exe` as a release asset

The auto-updater in the app will detect this release and download it automatically.

---

## Auto-update flow

```
App starts
    ↓  (2 s delay)
UpdateWorker checks GitHub API
    ↓  tag_name != CURRENT_VERSION
Download .exe asset  →  progress bar in top banner
    ↓  100 %
"Install Now" button appears
    ↓  user clicks
Subprocess launches installer → app exits
```

---

## File structure

```
project/
├── main.py                  ← Main application
├── updater.py               ← Auto-update thread
├── UlcerRiskAI.spec         ← PyInstaller build spec
├── UlcerRiskAI_Setup.iss    ← Inno Setup installer script
├── version_info.txt         ← Windows EXE metadata
├── src/
│   └── data.pkl             ← Trained ML model
└── assets/
    └── app.ico              ← Application icon
```
