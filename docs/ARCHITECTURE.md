# Architecture

SURAS is a research prototype for estimating oral-ulcer risk from salivary /
lifestyle data. The same logic and the same model serve a PyQt5 desktop app and
an Android app, so they cannot diverge.

> SURAS produces a **risk estimate for research and decision support**. It is not
> a diagnostic device; it is for research and decision support.

## Repository layout

```
apps/
  desktop/        PyQt5 desktop application (unchanged UX, hardened)
  android/        Kotlin + Jetpack Compose application
core/
  rust/           Shared Rust core (validation, preprocessing, classification, SWBRA)
  python/tests/   Tests for the PyO3 bindings
ai/               Reproducible training, ONNX export, golden-vector generation
shared/           Single source of truth (contracts + model) used by every target
docs/             This documentation set
scripts/          Build helpers (e.g. Android native-core cross-compile)
.github/          CI, release workflow, Dependabot, templates
```

## Shared contracts (`shared/`)

| File | Purpose |
|------|---------|
| `version.json` | App version + `versionCode`, and model/spec/preprocessing versions. Feeds Gradle, `version_info.txt`, the About screens. |
| `author.json` | Author metadata. Email/GitHub/LinkedIn are empty `TODO_FILL` — not invented. |
| `preprocessing.json` | Feature order, encodings, sanity bounds, classification thresholds, ONNX I/O description. |
| `swbra_spec.json` | The README's SWBRA weights/bands, marked documented-only, with thresholds/units UNKNOWN. |
| `golden_vectors.json` | Expected outputs for validation, classification, SWBRA and bands. |
| `model/model.onnx` | The exported ONNX classifier. |
| `model/SHA256SUMS` | The expected SHA-256 of `model.onnx`. |
| `model/metadata.json` | Model provenance, hyperparameters, metrics, checksum. |
| `model/parity_vectors.json` | 300 rows of scikit-learn vs ONNX probabilities for parity testing. |

Android copies these into the APK as generated assets at build time
(`SyncSharedAssetsTask`), so the packaged model is always the committed one.

## Shared Rust core (`core/rust`)

One implementation of the logic, two bindings:

- **UniFFI** → Kotlin (`com.harisudhan.suras.core`, cdylib `suras_core`).
- **PyO3** → Python module `suras_core` (built with `maturin develop --features python`).

Public API is JSON-in / JSON-out, which keeps the FFI surface tiny and
language-neutral and makes golden-vector testing straightforward:

| Function | Responsibility |
|----------|----------------|
| `prepare_json(payload)` | Validate + encode the 14 features; returns features, warnings, typed errors. |
| `classify_json(probability)` | Map a probability to LOW / MEDIUM / HIGH using `preprocessing.json`. |
| `evaluate_json(payload, probability?)` | Prepare + classify + SWBRA status in one call. |
| `swbra_json(flags)` | SWBRA flag arithmetic and band lookup (documented weights only). |
| `versions_json()` | Embedded version metadata. |
| `spec_json()` | Embedded spec metadata. |

The core is panic-safe at the boundary: an invalid payload or a panic becomes a
typed error object, never a crash. Errors are typed (`MISSING`, `INVALID_TYPE`,
`NON_FINITE`, `OUT_OF_RANGE`, …) and name the offending field.

## Data flow — Android

```
Compose screen (ViewModel)
   │  user input
   ▼
SurasCoreService  ──(JSON)──►  Rust core (UniFFI)  ──►  validated features
   │                                                       │
   │                                                       ▼
   │                                            ONNX Runtime Mobile
   │                                            (checksum-verified model)
   │                                                       │
   ▼                                                       ▼
PatientRepository (Room + SQLCipher)  ◄──  ResultViewModel (probability,
   │                                        category, SWBRA unavailable,
   ▼                                        versions, timestamp, disclaimer)
Encrypted local history
```

All database and model work runs off the main thread (Coroutines / `StateFlow`).
Auto-save happens after a successful analysis; the user can delete an entry or
clear all history.

## Data flow — desktop

```
MainWindow (PyQt5)
   │  user input
   ▼
Rust core via Python (`suras_core`)  ──►  validated features
   │                                          │
   ▼                                          ▼
sqlite3 (per-user data dir)          ONNX Runtime (checksum-verified)
   ▲                                          │
   └──────────── result panel ◄───────────────┘
```

The model is loaded once in a background `QThread` at startup. Until it is ready,
analysis is disabled. There is no random fallback.

## Model

A `GradientBoostingClassifier` (200 estimators, depth 2, lr 0.1, seed 42),
exported to ONNX opset 17 (53 077 bytes, SHA-256
`884da6e01e442119a969094746fa2bbf484a393fe511685c91e3617acc65f78a`). Input is
`float[?,14]`; outputs are `label` and `probabilities[?,2]`; the positive class is
index 1. ONNX vs scikit-learn parity: max abs diff `8.07e-08`. See
`AI_MODEL_DOCUMENTATION.md`.

## Android update flow

```
Settings (user-triggered) or optional WorkManager check (disable-able)
   ▼
GitHub Releases API over HTTPS
   ▼  newer SemVer?
Release asset must publish sha256: digest
   ▼
Download → verify SHA-256 → system package installer (user confirms)
```

No silent installs, no cleartext, no execution of unverified files. If
`suras.updateRepo` is unset the updater is disabled.

## Desktop update flow

```
QThread: check → download (HTTPS, GitHub host) → verify sha256: digest
   ▼
User confirms → launch installer directly (Windows only)
```

## Security posture

See `SECURITY_AUDIT.md` for the threat model. Highlights: no secrets in the
repo, encrypted local patient data, `allowBackup=false`, network security config,
minimal permissions, R8, no PII logging, screen-capture flag, checksum-verified
model and updates, signed releases from CI secrets.

## Technology stack

- **Desktop:** Python 3.10+, PyQt5, pandas, scikit-learn, onnxruntime, reportlab.
- **Android:** Kotlin, Jetpack Compose (Material 3), Navigation Compose,
  Room + SQLCipher, Hilt, DataStore, WorkManager, ONNX Runtime Mobile, JNA.
- **Core:** Rust (serde, thiserror, uniffi, pyo3).
- **AI:** scikit-learn, skl2onnx, onnx, onnxruntime.
