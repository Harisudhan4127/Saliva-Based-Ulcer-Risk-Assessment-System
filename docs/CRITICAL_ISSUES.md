# Critical issues

Findings from the pre-Android audit of the SURAS repository, what was done about
them, and what remains. Tags:

- **VERIFIED** — confirmed by reading the code or running a command; evidence is
  quoted.
- **ASSUMPTION** — believed true but not directly proven here.
- **RECOMMENDATION** — a suggested change that was not implemented.
- **UNKNOWN** — not determinable from the repository.

This project is a research prototype. Nothing in this document is a claim of
clinical validity or regulatory compliance.

## Fixed

| # | Issue | Evidence (before) | Fix | Status |
|---|-------|-------------------|-----|--------|
| 1 | **Hardcoded desktop credentials** | `main.py` compared the typed user/password against literal `admin` / `admin@123`. (VERIFIED by reading the file at the pre-restructure tag.) | Removed. First run sets a local access password; it is stored as a PBKDF2-HMAC-SHA256 hash (390 000 iterations, 16-byte random salt) in `<user data>/access.json`. Minimum length 8, surrounding whitespace rejected. Recovery = delete `access.json`. See `apps/desktop/auth.py`. | Fixed |
| 2 | **Updater executed unverified downloads** | The old `updater.py` downloaded a release asset and launched it without verifying a checksum; a compromised release or MITM could run arbitrary code. (VERIFIED by reading the file.) | `apps/desktop/update_logic.py` + `updater.py` now require HTTPS on a GitHub-owned host, require a `sha256:` asset digest, cap the download at 512 MB, hash while streaming, and launch the installer directly (no shell). Windows-only auto-install. | Fixed |
| 3 | **Pickle model loading + random fallback** | `joblib.load("src/data.pkl")` unpickles attacker-controllable code; on failure a `_MockModel` returned random probabilities. (VERIFIED by reading the file.) | Replaced with a checksum-verified ONNX model (`shared/model/model.onnx` vs `shared/model/SHA256SUMS`) loaded in a background `QThread`. Analysis is disabled until the model is ready; there is **no** random fallback. See `apps/desktop/inference.py`. | Fixed |
| 4 | **Writable database inside the install/extraction directory** | The desktop DB path resolved to `_MEIPASS/User_data/patients.db`, i.e. inside the PyInstaller extraction dir, which is deleted on exit and is typically not user-writable. (VERIFIED.) | Patient DB moved to a per-user data directory (`paths.py`): Windows `%APPDATA%/UlcerRiskAI`, macOS `~/Library/Application Support/UlcerRiskAI`, Linux `$XDG_DATA_HOME/ulcer-risk-ai`. A legacy DB is copied once (never moved). | Fixed |
| 5 | **Patient database tracked in git** | `User_data/patients.db` was committed (VERIFIED: `git ls-tree -r v2.0.0-desktop`). | Untracked and git-ignored. It still exists in **history**; see `HISTORY_CLEANUP.md`. | Fixed going forward |
| 6 | **Pickle model + caches tracked** | `src/data.pkl` and `__pycache__/*.pyc` were committed (VERIFIED). | Untracked and git-ignored; pickle artifacts are build output now. Still present in history. | Fixed going forward |
| 7 | **Secret handling** | `secret_key.py` existed at HEAD as a 0-byte file; commit `545f43f` referenced an env `SECRET_KEY`. (VERIFIED.) | `secret_key.py` untracked and git-ignored; `.env.example` documents the variables. A key-rotation note is in `HISTORY_CLEANUP.md` because any real key that was ever committed must be considered compromised. | Fixed (rotation is manual) |
| 8 | **SWBRA documented but not implemented** | `README.md` describes a weighted biomarker algorithm (IL-6, CRP, IgA, pH, protein); no such function, weight, or input field exists in any tracked Python file. (VERIFIED.) | Resolved with the author: the app mirrors the **real** 14-field flow and model. `shared/swbra_spec.json` records the README weights/bands and marks thresholds/units/missing-value handling as UNKNOWN. The result screen shows SWBRA as unavailable rather than inventing a score. | Fixed (by scope decision) |
| 9 | **No license** | No `LICENSE` file existed (VERIFIED). | Added an academic/research-only license. It is **not** OSI open source and needs legal review. | Fixed (needs review) |
| 10 | **Version drift / duplicated logic** | Desktop and Android would each hardcode the version, feature order and thresholds. | `shared/version.json` feeds Gradle, `version_info.txt`, and the About screen; `shared/preprocessing.json` and `shared/swbra_spec.json` are the single contracts. | Fixed |
| 11 | **Placeholder repository URL** | The README clone URL pointed at a placeholder. (VERIFIED.) | README updated; `TODO_FILL` is used where an author-supplied value is genuinely missing. | Fixed |

## Remaining (tracked)

| # | Issue | Severity | Detail | Where |
|---|-------|----------|--------|-------|
| A | Patient data still present in git history | High | `User_data/patients.db`, `src/data.pkl` and `__pycache__` remain reachable in history. Rewriting history is a destructive operation that was **not** performed (it is out of the authorized scope). | `docs/HISTORY_CLEANUP.md` |
| B | `secret_key.py` / `SECRET_KEY` in history | High if a real key was ever committed | Treat any value that appeared in a commit as compromised and rotate it. | `docs/HISTORY_CLEANUP.md` |
| C | PyQt5 is GPL v3 | Medium | Distributing the desktop binary under the academic license may conflict with PyQt5's GPL. Not legally reviewed. | `docs/OPEN_QUESTIONS.md`, `LICENSE` |
| D | Desktop auto-update execution path | Medium | Even with digest verification, auto-installing a downloaded binary is a trust decision. Windows-only, user-triggered, digest required. | `docs/SECURITY_AUDIT.md` |
| E | Android native core runtime | Medium | The UniFFI `.so` is cross-compiled per ABI. Local cross-compile is now **VERIFIED** (`scripts/build_android_core.sh` produced `arm64-v8a` + `x86_64` `libsuras_core.so`, ELF for Android 21, NDK r27). Loading it at runtime on a device/emulator is still UNVERIFIED (no device available). CI performs the same build. | `docs/BUILD_GUIDE.md`, final report |
| F | Device/emulator behaviour | Medium | No device or emulator was available. Every runtime-only item is marked UNVERIFIED with its exact command. | final report |
| G | Model is trained on a small internal dataset | High | 300 rows, provenance UNKNOWN, likely synthetic/teaching data. Metrics are internal cross-validation only. | `docs/AI_MODEL_DOCUMENTATION.md` |

## Not issues (clarifications)

- The project is **PyQt5**, not Tkinter. (VERIFIED.)
- There are **no** IL-6/CRP/IgA/protein input fields in the app; the 14 real
  features are lifestyle + gastric-pH readings. (VERIFIED.)
- The repository does **not** claim clinical validation anywhere after this
  change set; where the README previously implied it, the wording is now
  "risk estimate for research/decision support".
