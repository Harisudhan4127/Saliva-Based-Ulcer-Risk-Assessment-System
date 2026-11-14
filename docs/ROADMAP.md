# Roadmap

SURAS is a research prototype. This roadmap is ordered by what would make the
project trustworthy, not by what would make it look finished.

## Now (done in 2.1.0)

- [x] Shared Rust core used by desktop and Android.
- [x] Checksum-verified ONNX model (no pickle) on both platforms.
- [x] Shared contracts in `shared/` (version, preprocessing, SWBRA spec, model).
- [x] Desktop security fixes (no hardcoded credentials, verified updater,
      per-user data directory).
- [x] Android app: full screen set, encrypted storage, user-triggered update.
- [x] CI (Rust, Python, Android, security scans) and a manual signed-release
      workflow.
- [x] Documentation set including a threat model and open questions.

## Next (high priority)

- [ ] Resolve the open questions in `OPEN_QUESTIONS.md` — especially dataset
      provenance and the PyQt5 GPL question.
- [ ] Rewrite git history to purge the patient DB, pickle and dataset, and
      rotate any exposed key (`HISTORY_CLEANUP.md`).
- [ ] Add an app-level lock (biometric / PIN) for Android history.
- [ ] Verify the Android native core and app runtime on a real device/emulator
      (currently UNVERIFIED).
- [ ] Add Android instrumented tests (DB encryption, core binding, update flow).
- [ ] Add a golden-vector test that runs the Rust core on the packaged Android
      assets.

## Later

- [ ] External validation on a representative cohort; publish a model card.
- [ ] Calibrate the model and justify (or replace) the 0.3 / 0.7 thresholds.
- [ ] Fairness analysis across age, sex and site.
- [ ] Implement SWBRA only if a validated source for units/thresholds/missing
      handling is supplied; otherwise remove it from the README.
- [ ] Data-protection review (DPIA) and a privacy policy; decide on Play
      distribution and in-app updates.
- [ ] Code-signing certificate for the desktop installer (beyond SHA-256).
- [ ] Localisation if required.

## Explicitly out of scope

- Any clinical use, diagnosis, or treatment recommendation.
- Compliance claims (CE/FDA/SaMD) without a real regulatory process.
- Silent background installs on any platform.
