# Windows release artifacts

No Windows desktop binary is currently included in this directory.

**To produce (on Windows):**
1. Build the shared core/desktop environment as documented in `docs/BUILD_GUIDE.md`.
2. Package with PyInstaller using `apps/desktop/UlcerRiskAI.spec`.
3. (Optional) Build installer with `apps/desktop/UlcerRiskAI_Setup.iss` / `apps/desktop/installer/setup.iss`.
4. Copy the final `.exe`/installer here and add a `SHA256SUMS` file.

Artifacts placed here must be verified and checksummed before distribution.
