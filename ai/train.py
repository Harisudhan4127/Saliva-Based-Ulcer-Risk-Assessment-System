"""Train, audit, export and verify the SURAS ulcer-risk model.

This replaces the original `train_model.py`, which used a single 80/20 split,
one RandomForest, no calibration and no export. The dataset and feature order
are unchanged.

Usage:
    python ai/train.py                 # audit + nested CV + train + export + parity
    python ai/train.py --skip-cv       # faster: legacy audit + final model only
"""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import math
import os
import sys
import warnings
from pathlib import Path
from typing import Any, Dict, List, Tuple

import numpy as np
import pandas as pd
from sklearn.ensemble import (
    ExtraTreesClassifier,
    GradientBoostingClassifier,
    HistGradientBoostingClassifier,
    RandomForestClassifier,
)
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    accuracy_score,
    brier_score_loss,
    confusion_matrix,
    f1_score,
    log_loss,
    precision_score,
    recall_score,
    roc_auc_score,
)
from sklearn.model_selection import GridSearchCV, StratifiedKFold, train_test_split
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler
from sklearn.tree import DecisionTreeClassifier

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "apps" / "desktop" / "src" / "data.csv"
LEGACY_MODEL = ROOT / "apps" / "desktop" / "src" / "data.pkl"
MODEL_DIR = ROOT / "shared" / "model"
ONNX_PATH = MODEL_DIR / "model.onnx"
METADATA_PATH = MODEL_DIR / "metadata.json"
PARITY_PATH = MODEL_DIR / "parity_vectors.json"
SUMS_PATH = MODEL_DIR / "SHA256SUMS"
METRICS_DOC = ROOT / "docs" / "ai_metrics.json"

FEATURE_ORDER = [
    "Age", "Gender", "Spicy_Food", "Tobacco", "Alcohol", "Sleep_Hours",
    "Stress", "Skip_Meals", "Soft_Drinks", "Empty_Stomach_Pain",
    "Day1_pH_Before", "Day1_pH_After", "Day2_pH_Before", "Day2_pH_After",
]
YES_NO = ["Spicy_Food", "Tobacco", "Alcohol", "Skip_Meals", "Soft_Drinks", "Empty_Stomach_Pain"]
FINAL_PARAMS = {"learning_rate": 0.1, "max_depth": 2, "n_estimators": 200, "random_state": 42}


def load_frame() -> pd.DataFrame:
    df = pd.read_csv(DATA)
    df.columns = df.columns.str.strip()
    df["Gender"] = df["Gender"].map({"Male": 1, "Female": 0})
    for col in YES_NO:
        df[col] = df[col].map({"Yes": 1, "No": 0})
    if df[FEATURE_ORDER + ["Ulcer"]].isna().any().any():
        raise SystemExit("Dataset contains unmapped categories or missing values")
    return df


def metrics(y: np.ndarray, p: np.ndarray, threshold: float = 0.5) -> Dict[str, Any]:
    pred = (p > threshold).astype(int)
    tn, fp, fn, tp = confusion_matrix(y, pred, labels=[0, 1]).ravel()
    return {
        "n": int(len(y)),
        "positives": int(y.sum()),
        "threshold": threshold,
        "accuracy": round(float(accuracy_score(y, pred)), 4),
        "precision": round(float(precision_score(y, pred, zero_division=0)), 4),
        "recall": round(float(recall_score(y, pred, zero_division=0)), 4),
        "f1": round(float(f1_score(y, pred, zero_division=0)), 4),
        "roc_auc": round(float(roc_auc_score(y, p)), 4),
        "brier": round(float(brier_score_loss(y, p)), 4),
        "log_loss": round(float(log_loss(y, p)), 4),
        "confusion": {"tn": int(tn), "fp": int(fp), "fn": int(fn), "tp": int(tp)},
    }


def ece(y: np.ndarray, p: np.ndarray, bins: int = 10) -> Tuple[float, List[Dict[str, Any]]]:
    edges = np.linspace(0.0, 1.0, bins + 1)
    total = 0.0
    rows = []
    for i in range(bins):
        lo, hi = edges[i], edges[i + 1]
        mask = (p >= lo) & (p < hi) if i < bins - 1 else (p >= lo) & (p <= hi)
        n = int(mask.sum())
        if n == 0:
            rows.append({"bin": f"{lo:.1f}-{hi:.1f}", "n": 0, "meanPred": None, "observed": None})
            continue
        mean_pred = float(p[mask].mean())
        observed = float(y[mask].mean())
        total += (n / len(y)) * abs(mean_pred - observed)
        rows.append({
            "bin": f"{lo:.1f}-{hi:.1f}", "n": n,
            "meanPred": round(mean_pred, 4), "observed": round(observed, 4),
        })
    return round(total, 4), rows


def audit_dataset(df: pd.DataFrame) -> Dict[str, Any]:
    return {
        "path": str(DATA.relative_to(ROOT)),
        "rows": int(len(df)),
        "columns": int(df.shape[1]),
        "missingValues": int(df.isna().sum().sum()),
        "duplicateRows": int(df.duplicated().sum()),
        "classBalance": {str(k): int(v) for k, v in df["Ulcer"].value_counts().sort_index().items()},
        "positiveRate": round(float(df["Ulcer"].mean()), 4),
        "featureRanges": {
            col: {"min": float(df[col].min()), "max": float(df[col].max())}
            for col in FEATURE_ORDER
        },
        "note": "The dataset is 300 rows. It appears to be a small/possibly synthetic teaching dataset; provenance is UNKNOWN. Metrics below are internal cross-validation, not external validation.",
    }


def legacy_audit(df: pd.DataFrame) -> Dict[str, Any]:
    result: Dict[str, Any] = {
        "source": "ai/train_model.py (original)",
        "split": "single 80/20 holdout, random_state=42, no stratification",
        "algorithm": "RandomForestClassifier(n_estimators=100, random_state=42)",
        "pickleAvailable": LEGACY_MODEL.exists(),
    }
    X, y = df[FEATURE_ORDER], df["Ulcer"]
    Xtr, Xte, ytr, yte = train_test_split(X, y, test_size=0.2, random_state=42)
    rf = RandomForestClassifier(n_estimators=100, random_state=42).fit(Xtr, ytr)
    p = rf.predict_proba(Xte)[:, 1]
    result["reproducedFromCsv"] = metrics(yte.to_numpy(), p)
    result["appThresholds"] = {
        str(t): metrics(yte.to_numpy(), p, threshold=t) for t in (0.3, 0.5, 0.7)
    }
    if LEGACY_MODEL.exists():
        try:
            import joblib
            with warnings.catch_warnings(record=True) as caught:
                warnings.simplefilter("always")
                legacy = joblib.load(LEGACY_MODEL)
            lp = legacy.predict_proba(Xte)[:, 1]
            result["legacyPickle"] = {
                "loaded": True,
                "sklearnVersionWarnings": len([w for w in caught if "version" in str(w.message).lower()]),
                "metrics": metrics(yte.to_numpy(), lp),
                "maxAbsDiffVsReproduced": round(float(np.max(np.abs(lp - p))), 6),
            }
        except Exception as exc:  # pragma: no cover - depends on local untracked artifact
            result["legacyPickle"] = {"loaded": False, "error": f"{type(exc).__name__}: {exc}"}
    else:
        result["legacyPickle"] = {"loaded": False, "error": "untracked local artifact not present"}
    return result


def model_zoo() -> Dict[str, Tuple[Any, Dict[str, List[Any]]]]:
    return {
        "logreg": (
            make_pipeline(StandardScaler(), LogisticRegression(max_iter=3000, class_weight="balanced", random_state=42)),
            {"logisticregression__C": [0.01, 0.1, 1, 10]},
        ),
        "tree": (
            DecisionTreeClassifier(random_state=42),
            {"max_depth": [2, 3, 4, 5], "min_samples_leaf": [1, 5, 10]},
        ),
        "rf": (
            RandomForestClassifier(random_state=42, n_jobs=1),
            {"n_estimators": [100, 300], "max_depth": [None, 6, 10], "min_samples_leaf": [1, 3]},
        ),
        "extra": (
            ExtraTreesClassifier(random_state=42, n_jobs=1),
            {"n_estimators": [100, 300], "max_depth": [None, 6, 10], "min_samples_leaf": [1, 3]},
        ),
        "gb": (
            GradientBoostingClassifier(random_state=42),
            {"n_estimators": [50, 100, 200], "max_depth": [2, 3, 4], "learning_rate": [0.05, 0.1, 0.2]},
        ),
        "hgb_reference_only": (
            HistGradientBoostingClassifier(random_state=42),
            {"max_iter": [100, 200], "max_depth": [None, 3, 5], "learning_rate": [0.05, 0.1]},
        ),
    }


def nested_cv(df: pd.DataFrame) -> Dict[str, Any]:
    X, y = df[FEATURE_ORDER], df["Ulcer"]
    outer = StratifiedKFold(n_splits=5, shuffle=True, random_state=42)
    inner = StratifiedKFold(n_splits=3, shuffle=True, random_state=7)
    results: Dict[str, Any] = {}
    oof: Dict[str, np.ndarray] = {}
    for name, (estimator, grid) in model_zoo().items():
        search = GridSearchCV(estimator, grid, cv=inner, scoring="roc_auc", n_jobs=1, refit=True)
        fold_metrics = []
        predictions = np.zeros(len(y), dtype=float)
        best_params = []
        for train_idx, test_idx in outer.split(X, y):
            search.fit(X.iloc[train_idx], y.iloc[train_idx])
            p = search.predict_proba(X.iloc[test_idx])[:, 1]
            predictions[test_idx] = p
            fold_metrics.append(metrics(y.iloc[test_idx].to_numpy(), p))
            best_params.append(search.best_params_)
        aucs = [m["roc_auc"] for m in fold_metrics]
        briers = [m["brier"] for m in fold_metrics]
        results[name] = {
            "folds": fold_metrics,
            "mean": {
                "roc_auc": round(float(np.mean(aucs)), 4),
                "roc_auc_std": round(float(np.std(aucs)), 4),
                "brier": round(float(np.mean(briers)), 4),
                "brier_std": round(float(np.std(briers)), 4),
            },
            "bestParamsPerFold": best_params,
            "onnxExportable": name != "hgb_reference_only",
        }
        oof[name] = predictions
        print(f"  nested CV {name:22s} AUC {results[name]['mean']['roc_auc']:.4f} "
              f"+/- {results[name]['mean']['roc_auc_std']:.4f}  Brier {results[name]['mean']['brier']:.4f}")
    return {"outerFolds": 5, "innerFolds": 3, "results": results, "oof": oof}


def train_final(df: pd.DataFrame) -> Any:
    X, y = df[FEATURE_ORDER], df["Ulcer"]
    model = GradientBoostingClassifier(**FINAL_PARAMS)
    model.fit(X, y)
    return model


def export_onnx(model: Any) -> Tuple[bytes, float]:
    from skl2onnx import convert_sklearn
    from skl2onnx.common.data_types import FloatTensorType

    onx = convert_sklearn(
        model,
        initial_types=[("input", FloatTensorType([None, len(FEATURE_ORDER)]))],
        target_opset=17,
        options={id(model): {"zipmap": False}},
    )
    blob = onx.SerializeToString()
    ONNX_PATH.parent.mkdir(parents=True, exist_ok=True)
    ONNX_PATH.write_bytes(blob)
    return blob, len(blob) / 1024.0


def verify_parity(model: Any, df: pd.DataFrame, blob: bytes) -> Dict[str, Any]:
    import onnxruntime as ort

    X = df[FEATURE_ORDER].to_numpy(dtype=np.float32)
    sklearn_p = model.predict_proba(df[FEATURE_ORDER])[:, 1]
    session = ort.InferenceSession(blob, providers=["CPUExecutionProvider"])
    input_name = session.get_inputs()[0].name
    outputs = session.run(None, {input_name: X})
    names = [o.name for o in session.get_outputs()]
    prob = np.asarray(outputs[names.index("probabilities")])[:, 1]
    diff = np.abs(prob - sklearn_p)
    return {
        "rows": int(len(X)),
        "maxAbsDiff": float(diff.max()),
        "meanAbsDiff": float(diff.mean()),
        "passed": bool(diff.max() <= 1e-6),
        "sklearnProbability": sklearn_p,
        "onnxProbability": prob,
    }


def parity_vectors(df: pd.DataFrame, sklearn_p: np.ndarray, onnx_p: np.ndarray) -> Dict[str, Any]:
    vectors = []
    for i, row in df.reset_index(drop=True).iterrows():
        vectors.append({
            "source": f"dataset-row-{i}",
            "features": [float(v) for v in row[FEATURE_ORDER].tolist()],
            "sklearnProbability": float(sklearn_p[i]),
            "onnxProbability": float(onnx_p[i]),
        })
    return {
        "schemaVersion": "1.0.0",
        "modelVersion": "1.0.0",
        "featureOrder": FEATURE_ORDER,
        "tolerance": 1e-6,
        "vectors": vectors,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--skip-cv", action="store_true", help="skip nested CV (fast local runs)")
    args = parser.parse_args()

    df = load_frame()
    audit = audit_dataset(df)
    print(f"Dataset: {audit['rows']} rows, class balance {audit['classBalance']}")

    legacy = legacy_audit(df)
    print(f"Legacy holdout AUC: {legacy['reproducedFromCsv']['roc_auc']} "
          f"recall {legacy['reproducedFromCsv']['recall']}")

    report: Dict[str, Any] = {
        "generatedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
        "dataset": audit,
        "legacy": legacy,
        "python": sys.version.split()[0],
        "libraries": {},
    }
    import sklearn, pandas, numpy, onnx, onnxruntime, skl2onnx
    report["libraries"] = {
        "scikit-learn": sklearn.__version__,
        "pandas": pandas.__version__,
        "numpy": numpy.__version__,
        "onnx": onnx.__version__,
        "onnxruntime": onnxruntime.__version__,
        "skl2onnx": skl2onnx.__version__,
    }

    cv_report = None
    if not args.skip_cv:
        print("Running stratified nested CV (5 outer x 3 inner)...")
        cv_report = nested_cv(df)
        report["nestedCv"] = cv_report["results"]
        # Calibration of the exportable best model (gb) from out-of-fold predictions
        gb_oof = cv_report["oof"]["gb"]
        ece_value, reliability = ece(df["Ulcer"].to_numpy(), gb_oof)
        report["calibration_gb_oof"] = {"ece": ece_value, "bins": reliability}
        report["gb_oof_metrics"] = metrics(df["Ulcer"].to_numpy(), gb_oof)
        report["gb_thresholds_oof"] = {
            str(t): metrics(df["Ulcer"].to_numpy(), gb_oof, threshold=t) for t in (0.3, 0.5, 0.7)
        }

    model = train_final(df)
    blob, size_kb = export_onnx(model)
    parity = verify_parity(model, df, blob)
    if not parity["passed"]:
        raise SystemExit(f"ONNX parity FAILED: max abs diff {parity['maxAbsDiff']}")

    report["finalModel"] = {
        "type": "GradientBoostingClassifier",
        "reason": "Best ONNX-exportable model in nested CV; HistGradientBoosting scored slightly better but skl2onnx could not export it.",
        "hyperparameters": FINAL_PARAMS,
        "trainedOn": "all 300 rows (deployment fit); reported metrics are nested-CV metrics, not training accuracy",
        "onnxBytes": len(blob),
        "onnxKiB": round(size_kb, 2),
        "onnxOpset": 17,
        "sha256": hashlib.sha256(blob).hexdigest(),
        "parity": {k: v for k, v in parity.items() if k not in ("sklearnProbability", "onnxProbability")},
    }

    metadata = {
        "modelVersion": "1.0.0",
        "algorithm": "GradientBoostingClassifier",
        "trainingLibrary": f"scikit-learn {sklearn.__version__}",
        "onnxOpset": 17,
        "sha256": hashlib.sha256(blob).hexdigest(),
        "sizeBytes": len(blob),
        "createdAt": report["generatedAt"],
        "trainingData": {
            "path": "apps/desktop/src/data.csv",
            "rows": int(len(df)),
            "features": FEATURE_ORDER,
            "target": "Ulcer",
        },
        "hyperparameters": FINAL_PARAMS,
        "metricsSummary": {
            "legacyHoldout": legacy["reproducedFromCsv"],
            "nestedCv": {k: v["mean"] for k, v in (report.get("nestedCv") or {}).items()},
        },
        "parity": report["finalModel"]["parity"],
        "clinicalValidation": "UNKNOWN - internal dataset metrics only; no external validation performed.",
    }
    METADATA_PATH.write_text(json.dumps(metadata, indent=2, sort_keys=True) + "\n")

    vectors = parity_vectors(df, parity["sklearnProbability"], parity["onnxProbability"])
    PARITY_PATH.write_text(json.dumps(vectors, indent=2) + "\n")
    SUMS_PATH.write_text(f"{metadata['sha256']}  model.onnx\n")

    METRICS_DOC.parent.mkdir(parents=True, exist_ok=True)
    METRICS_DOC.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")

    print(f"Exported {ONNX_PATH} ({size_kb:.1f} KiB), sha256 {metadata['sha256'][:16]}...")
    print(f"ONNX parity: max abs diff {parity['maxAbsDiff']:.2e} (passed={parity['passed']})")
    print(f"Wrote {METADATA_PATH}, {PARITY_PATH}, {METRICS_DOC}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
