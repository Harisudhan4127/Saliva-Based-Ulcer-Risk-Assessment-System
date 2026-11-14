# Decisions

Architecture and product decisions made while adding the Android app and the
shared core, with the reasoning and the alternatives that were rejected. Each
decision is marked VERIFIED (it is visible in the code) or RECOMMENDATION
(suggested, not yet implemented).

## D1. Mirror the real application; treat SWBRA as documentation only

**Context.** The README describes a "SWBRA" weighted algorithm using IL-6, CRP,
IgA, pH and protein. The source implements none of it: the app collects 14
lifestyle/gastric-pH features and feeds them to a scikit-learn model.

**Decision.** The Android app reproduces the **actual** 14-field desktop flow
and the same ML model. The README's SWBRA weights and bands are recorded in
`shared/swbra_spec.json` as `DOCUMENTED_ONLY_NOT_IMPLEMENTED_IN_SOURCE`, with
thresholds, units and missing-value handling marked UNKNOWN. The result screen
shows "SWBRA: unavailable" and explains why. No thresholds or scores were
invented. (VERIFIED.)

**Rejected.** Implementing SWBRA from the README would have required inventing
clinical cut-offs and units — not acceptable for a medical-adjacent tool.

## D2. Shared contracts in `shared/`

A single source of truth (`version.json`, `preprocessing.json`,
`swbra_spec.json`, `golden_vectors.json`, `model/`) is consumed by the desktop
app, the Android app, the Rust core and CI, so the apps cannot drift apart.
(VERIFIED.)

## D3. Rust core with UniFFI + PyO3, JSON-in/JSON-out

Validation, preprocessing, classification and SWBRA flag arithmetic live once in
`core/rust`. Android calls it through UniFFI; Python calls it through PyO3. The
public API is JSON-in / JSON-out (`prepare_json`, `classify_json`,
`evaluate_json`, `swbra_json`, `versions_json`, `spec_json`), which keeps the FFI
boundary small and language-neutral and makes golden-vector testing trivial.
(VERIFIED.)

**Rejected.** Reimplementing the logic in Kotlin and Python would triple the
places a threshold can silently diverge.

## D4. ONNX Runtime, not pickle

The model ships as ONNX and is loaded after SHA-256 verification, on Android
(ONNX Runtime Mobile) and desktop (`onnxruntime`). Pickle/joblib is removed
because unpickling executes arbitrary code. (VERIFIED.)

**Rejected.** Quantising the model: at 51.8 KiB there is nothing meaningful to
gain, and quantisation would change the numerics for no benefit.

## D5. Model choice

`GradientBoostingClassifier(n_estimators=200, max_depth=2, learning_rate=0.1,
random_state=42)` — the best model that ONNX could export. HistGradientBoosting
scored marginally better in nested CV but skl2onnx could not export it. (VERIFIED
in `docs/ai_metrics.json`.)

## D6. SQLCipher for Android local storage

Patient records are stored in an encrypted Room database using
`net.zetetic:sqlcipher-android`; the passphrase is generated once and kept in the
Android Keystore. `allowBackup=false`. (VERIFIED.)

## D7. Update channels

- **Android:** user-triggerable check, optionally disabled; HTTPS GitHub Releases
  API; the release asset must publish a `sha256:` digest; install goes through
  the system package installer. No silent installs, no cleartext, no unverified
  execution. If `suras.updateRepo` is unset (default `TODO_FILL`) the updater is
  disabled. For Play distribution, in-app updates should be used instead.
  (VERIFIED.)
- **Desktop:** HTTPS + SHA-256 verification before execution; Windows-only
  auto-install; other platforms are told to download manually. (VERIFIED.)

## D8. Android palette mirrors the desktop *content*, not its chrome

The desktop uses a light slate/teal content palette (`#0f766e`, `#f8fafc`,
`#1e293b`; risk colours `#dc2626` / `#d97706` / `#16a34a`) with a **dark navy
sidebar** (`#0f172a`). The Android app reproduces the content palette in a light
and dark theme. The dark navy sidebar is **intentionally not reproduced** as a
background because the brief forbids dark navy/dark-blue backgrounds; the sidebar
is a desktop navigation chrome element, not app content. This is the one
deliberate colour conflict and it is recorded here as required. (VERIFIED.)

## D9. Android defaults differ from the desktop in two places

- Boolean questions (spicy food, tobacco, alcohol, skip meals, soft drinks, empty
  stomach pain) default to **No** on Android, whereas the desktop pre-selects
  **Yes**.
- Gender defaults to **Male** on Android.

Both are low-risk UI defaults, but they are a behavioural difference and are
called out rather than silently matched. (VERIFIED.)

## D10. Local access password instead of accounts

The desktop had a single hardcoded login. Rather than remove the gate entirely
(patient data is sensitive) the app sets a local access password on first run and
stores only a PBKDF2 hash. There is no server, no account recovery, and no
password reset — deleting `access.json` clears it. This is a convenience lock,
not a security boundary against someone with filesystem access. (VERIFIED.)

## D11. Versioning

SemVer in `shared/version.json` (`version`, `versionCode`, plus model/spec/
preprocessing versions). Tags are `vMAJOR.MINOR.PATCH`. CI injects `buildDate`
and `gitCommit` at build time; `null` means "not built by CI". (VERIFIED.)

## D12. App metadata is not invented

`shared/author.json` contains only facts stated in the README (name, degree,
project). Email, GitHub and LinkedIn are empty strings with a `TODO_FILL`
marker because they were never provided. They were **not** guessed. (VERIFIED.)

## D13. Release process is manual

`release.yml` is `workflow_dispatch` only, produces a **draft** GitHub Release,
and requires signing secrets. Nothing is auto-published and nothing is pushed to
a store. (VERIFIED.)

## D14. Rejected / not done

- **History rewrite** to purge the patient DB and pickle from git — destructive,
  out of the authorized scope; documented in `HISTORY_CLEANUP.md` instead.
- **Force-push / push to `main`** — not authorized.
- **Play Store upload** — needs an account and a privacy/data-safety review;
  out of scope. In-app update is documented instead.
- **Clinical thresholds / accuracy claims** — no source exists; left UNKNOWN.
