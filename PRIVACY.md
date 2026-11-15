# Privacy

SURAS is a research prototype for estimating oral-ulcer risk. This document
describes what data the software handles. It is **not** a legal privacy policy
and no regulatory compliance is claimed. See `docs/OPEN_QUESTIONS.md`.

## Summary

- SURAS does **not** send patient data anywhere. There is no server, no account,
  no analytics, and no telemetry.
- Patient records are stored only on the device, encrypted.
- The only network request is an optional, user-controlled update check to the
  GitHub Releases API.

## What is stored, and where

| Data | Stored where | Encryption |
|------|--------------|------------|
| Patient name, age, gender, lifestyle answers, pH readings, risk result, timestamp | Desktop: SQLite DB in the per-user data directory. Android: Room DB. | Desktop: filesystem only. Android: SQLCipher, key in the Android Keystore. |
| Local access password (desktop only) | `<user data>/access.json` | PBKDF2-HMAC-SHA256 hash with a random salt (never the password itself). |
| Settings (consent, update preference, theme) | Android DataStore / desktop config | Filesystem. |

Desktop data directory:

| OS | Path |
|----|------|
| Windows | `%APPDATA%\UlcerRiskAI` |
| macOS | `~/Library/Application Support/UlcerRiskAI` |
| Linux | `$XDG_DATA_HOME/ulcer-risk-ai` or `~/.local/share/ulcer-risk-ai` |

Android backups are disabled (`allowBackup=false`); cloud backup and
device-to-device transfer are excluded for all data domains.

## Network

The Android app can optionally check GitHub for a newer release. This request
goes to `api.github.com` over HTTPS and sends only a standard HTTP user agent —
no patient data, no identifiers. The check can be disabled in Settings, and it
does not run at all unless a release repository is configured (`suras.updateRepo`;
the default `TODO_FILL` disables it). The desktop updater behaves the same way.

No analytics, crash reporting, advertising, or third-party SDKs are included.

## Exports

CSV and PDF exports are created on-device and shared through the system share
sheet. Once shared, the data leaves the app and its handling is up to the
receiving application and the user.

## Your control

- History entries can be deleted individually; Settings can clear all history.
- Deleting the app's data directory (desktop) or clearing app storage (Android)
  removes everything.
- Desktop password recovery is "delete `access.json`".

## Children, and sensitive data

SURAS is not intended for children. It processes health-related data; anyone
deploying it with real patient data must satisfy the data-protection law that
applies to them (for example GDPR, HIPAA, or India's DPDP Act). No such
assessment has been performed here.

## Policy

Not clinically validated. SURAS is a research prototype for decision support
only and is not a diagnosis. No regulatory or compliance assessment has been
performed.
