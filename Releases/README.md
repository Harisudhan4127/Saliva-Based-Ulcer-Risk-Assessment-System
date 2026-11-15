# SURAS Releases

Platform-organized release artifacts for SURAS.

## Structure

- `android/` — Android application package(s) and checksums
  - `suras-2.1.0-android-release-unsigned.apk` (unsigned local release build)
  - `SHA256SUMS`
- `linux/` — Linux desktop application artifact and checksums
  - `suras-2.1.0-linux-x86_64.tar.gz` (PyInstaller bundle archive)
  - `SHA256SUMS`
- `windows/` — Windows desktop application artifacts
  - _Pending:_ no Windows binary currently checked in (see `windows/README.md`)

## Version & tags

- Versions defined in `shared/version.json` (current: **2.1.0**).
- Canonical version tags (local): `v2.0.0`, `v2.1.0`.

## Notes

- **Research prototype** for decision support only — not a diagnosis. See repository `README.md`, `PRIVACY.md`, and in-app policy/disclaimers.
- Android CI release builds (`.github/workflows/release.yml`) are manual/draft and require signing secrets. The stored APK is an unsigned local build unless otherwise noted.
- Desktop builds come from `apps/desktop/` (PyInstaller `UlcerRiskAI.spec`; Inno Setup scripts for Windows).
- Artifact/runtime verification on target devices/OSes may be **UNVERIFIED** unless explicitly stated.
