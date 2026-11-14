# Build & distribution guide

How to build and test every part of SURAS. SURAS is a research prototype and is
**not clinically validated**.

The repository layout is:

```
apps/desktop/   PyQt5 desktop application
apps/android/   Kotlin + Jetpack Compose application
core/rust/      Shared Rust core (UniFFI + PyO3)
shared/         Single source of truth (contracts + ONNX model)
ai/             Training + ONNX export
scripts/        Build helpers
```

## 0. Prerequisites

| Tool | Version | Used for |
|------|---------|----------|
| Python | 3.10+ (3.12 tested) | desktop, AI, core bindings |
| Rust (rustup) | 1.75+ (1.99 tested) | shared core |
| JDK | 21 | Android |
| Android SDK | platform 37, build-tools 36 | Android |
| Android NDK | 27+ | Android native core |
| Gradle | via wrapper (9.9.1) | Android |
| cargo-ndk | latest | Android native core |

---

## 1. Shared Rust core

```bash
cd core/rust
cargo fmt --all -- --check
cargo clippy --all-targets --features python -- -D warnings
cargo test --all-targets --features python
cargo audit          # needs: cargo install cargo-audit
```

Benchmarks (optional):

```bash
cargo bench
```

### Python bindings (PyO3)

```bash
python -m venv .venv && . .venv/bin/activate
pip install maturin pytest numpy pandas scikit-learn onnx onnxruntime
cd core/rust && maturin develop --features python
cd ../.. && pytest -q core/python/tests ai/tests apps/desktop/tests
```

### Kotlin bindings (UniFFI)

The generated Kotlin file is committed at
`apps/android/app/src/main/java/com/harisudhan/suras/core/suras_core.kt`. To
regenerate it after changing `core/rust/src/lib.rs`:

```bash
cd core/rust
cargo run --bin uniffi-bindgen generate \
  --library ../../core/rust/target/debug/libsuras_core.so \
  --language kotlin \
  --out-dir ../../apps/android/app/src/main/java
```

---

## 2. Android application

### 2a. Cross-compile the shared native core

```bash
rustup target add aarch64-linux-android x86_64-linux-android
cargo install cargo-ndk
export ANDROID_NDK_HOME="$HOME/Android/Sdk/ndk/<version>"
scripts/build_android_core.sh            # or: ABIS="arm64-v8a" scripts/build_android_core.sh
```

This writes `libsuras_core.so` into
`apps/android/app/src/main/jniLibs/<abi>/`. Those files are git-ignored build
output; CI regenerates them.

> **VERIFIED locally:** running the command above on the author's machine
> produced `arm64-v8a/libsuras_core.so` and `x86_64/libsuras_core.so` (ELF for
> Android 21, NDK r27). Loading the library at runtime still needs a device and
> remains **UNVERIFIED** — see below.

### 2b. Build and test

```bash
cd apps/android
./gradlew :app:testDebugUnitTest     # JVM unit tests
./gradlew :app:lintDebug             # Android lint (abortOnError = true)
./gradlew :app:assembleDebug         # debug APK
```

Install on a connected device/emulator:

```bash
./gradlew :app:installDebug
adb shell am start -n com.harisudhan.suras.debug/com.harisudhan.suras.MainActivity
```

> Runtime behaviour (Keystore, SQLCipher, ONNX Runtime, update install) is
> **UNVERIFIED** without a device.

### 2c. Release signing

Create `apps/android/keystore.properties` (git-ignored):

```properties
storeFile=/absolute/path/release.keystore
storePassword=...
keyAlias=...
keyPassword=...
```

```bash
./gradlew :app:assembleRelease :app:bundleRelease
```

Outputs land in `apps/android/app/build/outputs/`. R8 minify + resource shrinking
are enabled for release.

### 2d. Gradle notes

- AGP 9.5.1 compiles Kotlin itself; the `org.jetbrains.kotlin.android` plugin
  must **not** be applied.
- `compileSdk = 37` (androidx 1.12.x requires it); `targetSdk = 36`; `minSdk = 26`.
- The shared assets are copied into the APK by the `syncSharedAssets` task,
  registered through the AGP 9 variant Sources API.
- If `services.gradle.org` is slow, the wrapper can be pointed at a mirror or a
  local Gradle 9.9.1 install can be used directly:
  `/path/to/gradle-9.9.1/bin/gradle :app:testDebugUnitTest`.

---

## 3. Desktop application

```bash
cd apps/desktop
python -m pip install -r requirements.txt
python main.py
```

Run tests (no PyQt5 needed for the logic tests):

```bash
cd ../..    # repo root
pytest -q apps/desktop/tests
```

### 3a. Build the EXE (PyInstaller)

```bash
cd apps/desktop
pyinstaller UlcerRiskAI.spec
```

Output: `apps/desktop/dist/UlcerRiskAI/`. The spec bundles `shared/` as
`_MEIPASS/shared` and `assets/`.

Optional size trims (safe):

```bat
cd dist\UlcerRiskAI
rmdir /s /q PyQt5\Qt5\translations
del PyQt5\Qt5\plugins\imageformats\qgif.dll
del PyQt5\Qt5\plugins\imageformats\qjpeg.dll
```

### 3b. Installer (Inno Setup 6)

```bat
"C:\Program Files (x86)\Inno Setup 6\ISCC.exe" UlcerRiskAI_Setup.iss
```

Output: `apps/desktop/Output/UlcerRiskAI_Setup_v2.1.0.exe`.

### 3c. Data locations

The patient database and the local access password live in a per-user directory,
never inside the install folder:

| OS | Path |
|----|------|
| Windows | `%APPDATA%\UlcerRiskAI` |
| macOS | `~/Library/Application Support/UlcerRiskAI` |
| Linux | `$XDG_DATA_HOME/ulcer-risk-ai` or `~/.local/share/ulcer-risk-ai` |

Override with `SURAS_DATA_DIR` / `SURAS_SHARED_DIR`.

---

## 4. AI model

```bash
python -m venv .venv && . .venv/bin/activate
pip install numpy pandas scikit-learn onnx onnxruntime skl2onnx
python ai/train.py                  # retrain + export ONNX + rewrite metadata/parity
python ai/generate_golden_vectors.py
```

Verify the shipped model checksum:

```bash
cd shared/model && sha256sum -c SHA256SUMS
```

See `AI_MODEL_DOCUMENTATION.md` for metrics and limitations.

---

## 5. Versioning

`shared/version.json` is the single source of truth:

```json
{ "version": "2.1.0", "versionCode": 20100, "modelVersion": "1.0.0", ... }
```

- Gradle reads it for `versionName` / `versionCode`.
- The desktop reads it for the About screen and updater.
- Tags are `vMAJOR.MINOR.PATCH`.
- CI injects `buildDate` and `gitCommit`.

Update the version in one place and it propagates everywhere. Also update
`CHANGELOG.md`.

---

## 6. CI and releases

- `.github/workflows/ci.yml` — runs on push/PR: Rust (fmt, clippy, test, audit),
  Python (core + AI + desktop tests, golden vectors, model checksum), Android
  (lint, unit tests, assembleDebug), and secret/dependency scanning.
- `.github/workflows/release.yml` — **manual only** (`workflow_dispatch`).
  Builds a signed APK/AAB from repository secrets, writes SHA-256 checksums, and
  creates a **draft** GitHub Release. Nothing is auto-published.

Required release secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.

---

## 7. Troubleshooting

| Symptom | Cause / fix |
|---------|-------------|
| `Unresolved reference 'KeyboardOptions'` | Import from `androidx.compose.foundation.text.KeyboardOptions`. |
| `AAR metadata ... compile against version 37` | Keep `compileSdk = 37`. |
| `kotlin.android plugin is no longer required` | Remove the `org.jetbrains.kotlin.android` plugin (AGP 9). |
| `Context cannot be provided without @Provides` | Add `@ApplicationContext` to the injected constructor parameter. |
| Gradle wrapper download is very slow | Use a local Gradle 9.9.1 or a distribution mirror. |
| Desktop analysis is disabled | The ONNX model has not finished loading, or the checksum failed. Check the status banner. |
