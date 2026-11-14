# Contributing

Thanks for your interest in SURAS. This is a final-year academic project and a
research prototype, so please read the constraints before contributing.

## Ground rules

- **Do not add clinical, accuracy, or compliance claims** that are not backed by
  evidence in the repository. Where a value is unknown, write it down in
  `docs/OPEN_QUESTIONS.md` instead of guessing.
- **Never commit** secrets, keystores, patient data, datasets, or build output.
  They are git-ignored; keep it that way.
- **Keep the shared contracts authoritative.** `shared/` is the single source of
  truth for versions, preprocessing, the SWBRA spec and the model. If you change
  behaviour, change it in the Rust core (`core/rust`) so both apps stay in sync.
- **UI text lives in `strings.xml`** on Android. Keep it accessible (content
  descriptions, font scaling, contrast).

## Workflow

1. Branch from `main` (for example `feature/short-name`).
2. Make small, focused commits using [Conventional Commits](https://www.conventionalcommits.org/):
   `feat(android): ...`, `fix(core): ...`, `docs: ...`, `chore: ...`.
3. Run the relevant checks (below).
4. Open a pull request and fill in the template.

Do not force-push shared branches, rewrite history, or push to `main` directly.

## Checks to run

```bash
# Rust core
cd core/rust
cargo fmt --all -- --check
cargo clippy --all-targets --features python -- -D warnings
cargo test --all-targets --features python

# Python (core bindings + AI + desktop logic)
python -m venv .venv && . .venv/bin/activate
pip install maturin pytest numpy pandas scikit-learn onnx onnxruntime
cd core/rust && maturin develop --features python && cd ../..
pytest -q core/python/tests ai/tests apps/desktop/tests
(cd shared/model && sha256sum -c SHA256SUMS)

# Android
cd apps/android
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

If you change the model, regenerate `shared/model/*` and `docs/ai_metrics.json`
and re-run the parity tests. If you change `core/rust/src/lib.rs`, regenerate the
UniFFI Kotlin bindings (see `docs/BUILD_GUIDE.md`).

## What a good PR looks like

- A clear description of the problem and the fix.
- Evidence that the tests pass.
- Any device/emulator-only behaviour marked **UNVERIFIED** with the exact command
  to verify it.
- No unrelated reformatting.

## Reporting bugs and security issues

- Bugs: open an issue (see the template).
- Security: report privately — see `SECURITY.md`. Do not open a public issue.
