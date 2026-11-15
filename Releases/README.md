# SURAS Releases

This directory stores release artifacts organized by platform.

## Structure

- `android/` — Android application package(s) and checksums
  - `suras-2.1.0-android-release-unsigned.apk` (unsigned; for local/testing or signing outside CI)
  - `SHA256SUMS`
- `windows/` — Windows desktop application artifacts (installer/executable bundle)
  - _Pending: no Windows build artifact is currently checked in._
  - Expected (when available): signed/packaged installer or bundle + `SHA256SUMS`
- `linux/` — Linux desktop application artifacts
  - _Linux bundle build may be added here; see checksums when present._
  - Expected: packaged desktop bundle/archive + `SHA256SUMS`

## Notes

- **Research prototype** for decision support only — not a diagnosis. See repository `README.md`, `PRIVACY.md`, and in-app policy/disclaimers.
- Android release builds produced by CI require signing secrets (`release.yml`, manual/draft). The APK stored here is the local unsigned release build unless otherwise noted.
- Desktop builds are produced from `apps/desktop/` (PyInstaller spec `UlcerRiskAI.spec` / Inno Setup scripts) on the target platform.

## Version

- App/model/spec versions: `shared/version.json` (current: 2.1.0).
