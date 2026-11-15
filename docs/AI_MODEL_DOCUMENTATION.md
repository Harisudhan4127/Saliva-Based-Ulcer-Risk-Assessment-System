# AI model documentation

> **Research use only.** The model below is a statistical risk estimator trained
> on a small internal dataset. It has not been externally validated, has not been
> externally validated, and must not be used for diagnosis or treatment.

## What the model is

| Property | Value |
|----------|-------|
| Algorithm | `GradientBoostingClassifier` |
| Hyperparameters | `n_estimators=200`, `max_depth=2`, `learning_rate=0.1`, `random_state=42` |
| Library | scikit-learn 1.9.1 |
| Format | ONNX, opset 17 |
| Size | 53 077 bytes (51.83 KiB) |
| SHA-256 | `884da6e01e442119a969094746fa2bbf484a393fe511685c91e3617acc65f78a` |
| Input | `input: tensor(float)[?, 14]` |
| Outputs | `label: int64[?]`, `probabilities: float32[?, 2]` |
| Positive class | index 1 (`probabilities[:, 1]`), meaning "ulcer" (dataset target 1) |
| Model version | 1.0.0 |

Provenance is in `shared/model/metadata.json`; the contract is in
`shared/preprocessing.json`.

## Features (order matters)

1. `Age` (years)
2. `Gender` (Male = 1, Female = 0)
3. `Spicy_Food` (Yes = 1, No = 0)
4. `Tobacco` (Yes = 1, No = 0)
5. `Alcohol` (Yes = 1, No = 0)
6. `Sleep_Hours` (hours/day)
7. `Stress` (1–10)
8. `Skip_Meals` (Yes = 1, No = 0)
9. `Soft_Drinks` (Yes = 1, No = 0)
10. `Empty_Stomach_Pain` (Yes = 1, No = 0)
11. `Day1_pH_Before` (pH)
12. `Day1_pH_After` (pH)
13. `Day2_pH_Before` (pH)
14. `Day2_pH_After` (pH)

Note: these are the **actual** features collected by the application. The
README's IL-6/CRP/IgA/protein "SWBRA" inputs are not collected and are not used
by this model (see `swbra_spec.json`).

### Encoding and validation

- Boolean questions map Yes→1 / No→0; Gender maps Male→1 / Female→0.
- Every field is required. The core **rejects** missing/invalid input with a
  typed error naming the field; there is no imputation.
- Physical sanity bounds (not clinical ranges) are enforced: Age 0–120, Sleep
  0–24, Stress 1–10, pH 0–14. Values inside the training envelope
  (`preprocessing.json → datasetEnvelope`) are normal; values outside it are
  accepted with a warning because the model may extrapolate.

## Classification thresholds

From `apps/desktop/main.py` (the application's decision thresholds, **not**
cut-offs with no clinical validation):

| Category | Rule |
|----------|------|
| HIGH | probability > 0.7 |
| MEDIUM | probability > 0.3 |
| LOW | probability ≤ 0.3 |

Operators are strict: exactly 0.7 is MEDIUM, exactly 0.3 is LOW. The app labels
the output a "risk estimate for research/decision support".

## Training data

- `apps/desktop/src/data.csv`, 300 rows, 15 columns (14 features + target).
- Class balance: 174 negative / 126 positive (42 % positive).
- No missing values, no duplicate rows.
- Provenance is **UNKNOWN**; the file appears to be a small teaching/synthetic
  dataset. This is a major limitation.

## Evaluation (internal only)

All numbers come from `docs/ai_metrics.json`. They are internal cross-validation
results, not external validation.

### Nested cross-validation (5 folds)

| Model | ROC-AUC | Brier |
|-------|---------|-------|
| **GradientBoosting (chosen)** | **0.9920 ± 0.0059** | **0.0440 ± 0.0171** |
| HistGradientBoosting (reference only, not ONNX-exportable) | 0.9950 ± 0.0062 | 0.0278 ± 0.0192 |
| RandomForest | 0.9826 ± 0.0065 | 0.1117 ± 0.0064 |
| ExtraTrees | 0.9324 ± 0.0273 | 0.1268 ± 0.0142 |
| DecisionTree | 0.9163 ± 0.0345 | 0.1085 ± 0.0262 |
| LogisticRegression | 0.9152 ± 0.0211 | 0.1225 ± 0.0165 |

Out-of-fold GradientBoosting at threshold 0.5: accuracy 0.9567, precision 0.9669,
recall 0.9286, F1 0.9474. At the app's 0.3 threshold: recall 0.9841, precision
0.8671. Calibration: expected calibration error 0.0783.

### Legacy holdout (for continuity with the original app)

The original `train_model.py` used a single unstratified 80/20 holdout with
`random_state=42`. Reproduced metrics at threshold 0.5: accuracy 0.8667,
ROC-AUC 0.9618, F1 0.8400, Brier 0.1277 (n = 60). The old pickle was loaded
during analysis only to confirm the reproduction matched; pickle is no longer a
runtime dependency.

## ONNX parity

`shared/model/parity_vectors.json` contains 300 rows of scikit-learn probabilities
next to ONNX Runtime probabilities. Max absolute difference: **8.07e-08**, mean
**3.05e-08** — within float32 tolerance. The Python test suite re-checks a subset
of these on every CI run.

## Reproducing

```bash
python -m venv .venv && . .venv/bin/activate
pip install numpy pandas scikit-learn onnx onnxruntime skl2onnx
python ai/train.py            # retrains, re-exports ONNX, rewrites metadata + parity
python ai/generate_golden_vectors.py
```

## Limitations (read before using any number above)

1. **No external validation.** Every metric is internal to the same 300 rows.
2. **Dataset provenance UNKNOWN**, small, possibly synthetic; likely not
   representative of any real clinical population.
3. **Thresholds are application choices**, not clinical cut-offs.
4. **No calibration on an external cohort**; ECE 0.078 is internal only.
5. **Self-reported / lifestyle features** are subject to recall and entry bias.
6. **No demographic or site fairness analysis** was performed.
7. The model predicts a dataset label, not a clinically defined outcome.
