# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
The version number is defined once in `shared/version.json` and consumed by the
desktop app, the Android app, and CI.

## [Unreleased]

## [2.1.0] - 2026-11-14

### Added

- **Android application** (`apps/android/`): Kotlin + Jetpack Compose, Material 3,
  single-activity MVVM. Screens: splash, first-run consent, home, new patient,
  biomarker input, result, history + search, export, settings, about.
- **Shared Rust core** (`core/rust/`): validation, preprocessing, strict risk
  classification and the documented SWBRA flag arithmetic, exposed through
  UniFFI (Android) and PyO3 (Python). JSON-in / JSON-out.
- **Shared single source of truth** (`shared/`): `version.json`, `author.json`,
  `preprocessing.json`, `swbra_spec.json`, `golden_vectors.json`, and the ONNX
  model with `SHA256SUMS`, `metadata.json` and `parity_vectors.json`.
- **Reproducible training + ONNX export** (`ai/`): deterministic training,
  nested cross-validation metrics (`docs/ai_metrics.json`) and 300-row ONNX
  parity vectors.
- **Checksum-verified model runtime** for the desktop app (`onnxruntime`).
- **Local access password** for the desktop app (PBKDF2-HMAC-SHA256, stored per
  user; first run sets it).
- **CI** (`ci.yml`): Rust fmt/clippy/test/audit, Python tests + golden vectors +
  model checksum, Android lint/unit tests/assembleDebug, secret and dependency
  scanning.
- **Manual release workflow** (`release.yml`): signed APK/AAB from secrets,
  SHA-256 checksums, draft GitHub Release.
- Documentation: `docs/CRITICAL_ISSUES.md`, `DECISIONS.md`, `ARCHITECTURE.md`,
  `AI_MODEL_DOCUMENTATION.md`, `SECURITY_AUDIT.md`, `HISTORY_CLEANUP.md`,
  `OPEN_QUESTIONS.md`, `ROADMAP.md`, and an academic research `LICENSE`.

### Changed

- Repository restructured into `apps/desktop/`, `apps/android/`, `core/rust/`,
  `shared/`, `ai/`, `docs/`, `scripts/`.
- Desktop patient database moved from inside the application bundle to a
  per-user data directory; an existing legacy database is migrated once.
- Desktop auto-updater now requires HTTPS on GitHub hosts and a published
  SHA-256 digest before anything is executed.
- Android update check is user-triggerable and can be disabled; installs go
  through the system package installer and are never silent.

### Removed

- Hardcoded desktop credentials (`admin` / `admin@123`).
- Unverified executable download/launch path in the desktop updater.
- `joblib`/pickle model loading on the desktop app (arbitrary-code-execution
  risk). Pickle artifacts are no longer tracked.
- Tracked patient database, pickle model, caches and build output.

### Security

- `secret_key.py` untracked; `.env.example` added; secrets, keystores, patient
  data and datasets are git-ignored.
- Android: `allowBackup=false`, network security config, minimal permissions,
  R8, no PII logging, encrypted local storage (SQLCipher, key in Android
  Keystore), screen-capture flag.
- See `docs/SECURITY_AUDIT.md` for the threat model and remaining risks.

### Notes

- SURAS is a **research prototype and is not clinically validated**. The SWBRA
  algorithm described in the README is documented only; it is not implemented in
  the source and is shown as unavailable in the app. See `docs/OPEN_QUESTIONS.md`.

## [2.0.0] - 2025

- Initial public desktop release (PyQt5): patient intake, 14-feature risk model,
  history, PDF/CSV export, auto-update.
- Tagged `v2.0.0` and preserved as `v2.0.0-desktop` before the Android/core
  restructure.

[Unreleased]: https://github.com/Harisudhan4127/Saliva-Based-Ulcer-Risk-Assessment-System/compare/v2.1.0...HEAD
[2.1.0]: https://github.com/Harisudhan4127/Saliva-Based-Ulcer-Risk-Assessment-System/compare/v2.0.0-desktop...v2.1.0
