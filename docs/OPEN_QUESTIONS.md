# Open questions

Items that could not be determined from the repository. They are listed rather
than guessed. Nothing here should be treated as resolved until the author or a
domain expert answers it.

## Data and model

1. **Dataset provenance.** Where does `apps/desktop/src/data.csv` (300 rows) come
   from? Is it real, synthetic, or a teaching set? Is it licensed for
   redistribution?
2. **Target definition.** What exactly does the `Ulcer` label mean clinically,
   and how was it measured?
3. **Clinical validation.** Has the model been validated on any external cohort?
   (Current evidence: none. Metrics are internal cross-validation only.)
4. **Decision thresholds.** The 0.7 / 0.3 cut-offs are application choices in
   `main.py`. What clinical rationale, if any, supports them?
5. **Calibration.** Should the probabilities be calibrated before display, and
   against what cohort?

## SWBRA

6. **Units and thresholds.** For IL-6, CRP, IgA and pH: what units, what
   thresholds, and which comparison operators (≥, >)? The README gives weights
   only.
7. **Protein weight.** README lists protein as an input but gives no weight.
   Should it contribute 0, or is a weight missing?
8. **Missing-value handling.** How should a missing biomarker be treated?
9. **Was SWBRA ever implemented** in a version that is not in this repository?

## Licensing and compliance

10. **PyQt5 GPL v3.** The desktop app depends on PyQt5 (GPL v3). Can the desktop
    binary be distributed under the project's academic license, or is a
    commercial PyQt license / a different toolkit required?
11. **Dataset license.** Can the training CSV be redistributed? If not, it must
    not be published and the model provenance should reflect that.
12. **Medical-device / SaMD status.** Does a research ulcer-risk estimator fall
    under medical-device regulation in the intended jurisdiction? (Not assessed.)
13. **Data protection.** What legal basis applies to processing patient data
    (GDPR / HIPAA / India DPDP Act)? Is a DPIA required? (Not assessed.)
14. **Store distribution.** Is Play Store distribution intended? If so, in-app
    updates must replace the GitHub-Releases installer, and a Data Safety form
    and privacy policy are required.

## Product

15. **Author contact details.** Email, GitHub and LinkedIn are intentionally
    empty in `shared/author.json` (`TODO_FILL`). Fill them in.
16. **Repository URL.** Confirm the canonical GitHub URL used for the updater
    (`suras.updateRepo`) and the README clone command.
17. **Single-patient vs multi-patient.** The desktop and Android apps store
    multiple patient records. Is this a single-clinician tool or a shared
    deployment? This affects authentication requirements.
18. **Retention.** How long should patient records be kept, and should the app
    auto-purge them?
19. **Localisation.** Are languages other than English required?
20. **Desktop login.** Is a single local password acceptable, or is a real
    account model required?

## Build / release

21. **Signing keys.** Who owns the Android release keystore, and where is it
    backed up? CI expects `ANDROID_KEYSTORE_BASE64` and friends.
22. **Minimum Android version.** `minSdk` is 26. Is that acceptable?
23. **Windows-only auto-install.** Should macOS/Linux get an auto-install path,
    or remain manual downloads?
