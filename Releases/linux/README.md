# Linux release artifacts

- `suras-2.1.0-linux-x86_64.tar.gz` — PyInstaller one-folder desktop bundle (`UlcerRiskAI/`) packaged for Linux x86_64.
- `SHA256SUMS` — SHA-256 checksum for the archive.

## Build provenance (local)
- Built from `apps/desktop/UlcerRiskAI.spec` (PyInstaller 6.22.3, Python 3.14 venv on Linux).
- Runtime/functional verification on target Linux systems: **UNVERIFIED**.

## Reproduce
```bash
cd apps/desktop
python -m pip install -r requirements.txt pyinstaller
python -m PyInstaller --noconfirm --clean UlcerRiskAI.spec
tar -C dist -czf suras-2.1.0-linux-x86_64.tar.gz UlcerRiskAI
```
