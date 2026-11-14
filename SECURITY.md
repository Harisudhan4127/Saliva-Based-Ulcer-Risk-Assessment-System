# Security policy

SURAS is a research prototype. Security reports are welcome and taken seriously.

## Reporting a vulnerability

Please **do not** open a public issue for a security problem. Instead use
GitHub's private vulnerability reporting:

- https://github.com/Harisudhan4127/Saliva-Based-Ulcer-Risk-Assessment-System/security/advisories/new

Include:

- a description and the impact,
- steps to reproduce (a minimal proof of concept if possible),
- affected component (desktop, Android, Rust core, CI),
- the version from `shared/version.json` or the app's About screen.

We will acknowledge as soon as possible. There is no bug-bounty program.

## Scope

In scope: the desktop app, the Android app, the shared Rust core, the ONNX model
pipeline, the update mechanisms, and CI/release configuration.

Out of scope: the accuracy or clinical validity of the model (this is a research
prototype — see `docs/OPEN_QUESTIONS.md`), and issues that require a
compromised device or physical access.

## What we already do

- No secrets in the repository; `.env` and keystores are git-ignored; gitleaks
  runs in CI.
- The ONNX model is verified against a published SHA-256 before use.
- Updates require HTTPS on GitHub hosts and a published `sha256:` digest before
  anything is executed; installs are never silent.
- Android: `allowBackup=false`, cleartext disabled, minimal permissions,
  encrypted local storage, screen-capture flag, R8 on release.
- Dependency scanning (`cargo audit`, `pip-audit`, OSV, Dependabot, CodeQL).

See `docs/SECURITY_AUDIT.md` for the full threat model and residual risks.

## Known residual risks

- The git history still contains a patient database, a pickle and a
  `secret_key.py` reference. Treat anything that was ever committed as exposed
  and rotate it. See `docs/HISTORY_CLEANUP.md`.
- The desktop app depends on PyQt5 (GPL v3); licensing needs legal review.
- The desktop local password is a convenience lock, not a boundary against an
  attacker with filesystem access.
