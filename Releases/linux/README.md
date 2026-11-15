# Linux release artifacts

Place the final Linux desktop application bundle/archive here with `SHA256SUMS`.

**To produce:**
1. Install desktop dependencies (`apps/desktop/requirements.txt`, including PyInstaller).
2. From `apps/desktop/`: `pyinstaller UlcerRiskAI.spec`
3. Package `dist/UlcerRiskAI/` (e.g. tarball) and record checksums.

If a verified Linux build is generated, add it to this folder and update this README/checksums.
