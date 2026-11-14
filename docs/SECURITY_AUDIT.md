# Security audit

Scope: the SURAS repository after the Android/core change set. This is a
best-effort engineering review, **not** a penetration test, and it makes no
compliance claim. SURAS is a research prototype.

> Regulatory note: a software tool that estimates health risk may fall under
> medical-device / Software-as-a-Medical-Device (SaMD) rules, and handling
> patient data may fall under data-protection law (e.g. GDPR / HIPAA / India's
> DPDP Act). **No compliance has been assessed or claimed.** See
> `OPEN_QUESTIONS.md`.

## Assets

| Asset | Where | Sensitivity |
|-------|-------|-------------|
| Patient records (name, age, biomarkers, risk result) | Desktop SQLite; Android SQLCipher DB | High (personal/health data) |
| Local access password hash | `<user data>/access.json` (PBKDF2, salted) | Medium |
| Model + contracts | `shared/` | Public |
| Signing keystore | CI secret only | Critical |
| Update channel | GitHub Releases | High (code execution path) |

## Threat model

| Threat | Vector | Mitigation | Status |
|--------|--------|------------|--------|
| Credential guessing | Hardcoded default password | Removed; first-run local password, PBKDF2-HMAC-SHA256, 390k iterations, 16-byte salt, min length 8 | Fixed |
| Malicious model file | `pickle`/`joblib.load` executes code | ONNX only, SHA-256 verified against `shared/model/SHA256SUMS`; mismatch → hard failure | Fixed |
| Malicious update | Download + execute unverified binary | HTTPS + GitHub-host allowlist + mandatory `sha256:` digest + streaming hash + direct `Popen` (no shell); Windows-only auto-install | Fixed |
| Model substitution on Android | Tampered packaged model | Model copied from `shared/` at build time and verified against the checksum before use | Fixed |
| Data at rest (Android) | Rooted device / backup extraction | SQLCipher encryption, key in Android Keystore, `allowBackup=false`, `dataExtractionRules` exclude all domains | Fixed |
| Cleartext network | MITM | `usesCleartextTraffic=false`, network security config forbids cleartext, trust only system anchors | Fixed |
| Over-broad permissions | Manifest | Only `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `REQUEST_INSTALL_PACKAGES` | Reviewed |
| Exported components | IPC surface | Only `MainActivity` (launcher) is exported; FileProvider is `exported=false` with `grantUriPermissions`; startup provider merged with `exported=false` | Reviewed |
| PII in logs | Debug logging | No patient payloads are logged; core errors name fields, not values | Reviewed |
| Screen capture / recents leak | OS screenshot | `FLAG_SECURE` set on the activity | Reviewed |
| Secrets in repo | Committed keys | `.gitignore` covers `.env`, keystores, `secret_key.py`; `.env.example` documents variables; gitleaks in CI | Fixed going forward |
| Supply chain | Vulnerable dependencies | `cargo audit`, `pip-audit`, OSV scan, Dependabot, CodeQL | Added |
| Unsigned releases | Tampered APK/AAB | Release workflow signs from CI secrets, publishes SHA-256 checksums, creates a draft release | Added |
| Decompilation / tampering | APK reverse engineering | R8 minify + shrink on release; note this is obfuscation, not protection | Added |

## What was verified by reading code

- Desktop: no hardcoded credentials; no `pickle`/`joblib` import in the runtime
  path; updater requires a digest; DB path is per-user. (VERIFIED.)
- Android manifest: `allowBackup=false`, cleartext disabled, minimal permissions,
  exported components limited as above. (VERIFIED.)
- Rust core: JSON API is panic-safe at the boundary and returns typed errors.
  (VERIFIED by tests.)

## What is NOT verified

- The Android native core (`libsuras_core.so`) was **not** built on the author's
  machine (no NDK cross-targets). The runtime is UNVERIFIED; CI builds it. Exact
  command: `scripts/build_android_core.sh`.
- No device or emulator was available, so runtime behaviour (Keystore access,
  SQLCipher open, ONNX Runtime load, update install) is **UNVERIFIED**. Exact
  command: `./gradlew :app:installDebug` on a connected device, then exercise the
  screens.
- No dynamic analysis / fuzzing of the desktop app was performed.
- R8 rules have not been validated against a release runtime on a device.

## Residual risks

1. **PyQt5 is GPL v3.** Distributing the desktop binary may conflict with the
   project's academic license. Legal review required.
2. **Desktop auto-update still executes a downloaded binary** (after digest
   verification). This is an inherent trust decision; it is Windows-only and
   user-triggered.
3. **Git history still contains a patient DB, a pickle and a `secret_key.py`
   reference.** Treat anything that was ever committed as exposed; rotate keys.
   See `HISTORY_CLEANUP.md`.
4. **Local password is a convenience lock, not a security boundary** against an
   attacker with filesystem access to the user data directory.
5. **Health data is sensitive**; the Android app has no authentication gate, so
   anyone with the unlocked device can read history.

## Recommendations (not implemented)

- Add an app-level lock (biometric / PIN) for the Android history.
- Move the desktop auto-update to an OS package manager or a signed installer
  verified with a code-signing certificate (not just a digest).
- Purge the git history and rotate every credential that ever appeared in it.
- Add instrumented (androidTest) security tests once a device/emulator exists.
- Complete a DPIA / data-protection review before any real patient data is used.
