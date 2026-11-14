"""Reference implementation of SURAS validation, preprocessing and classification.

This module is the executable definition used to generate shared/golden_vectors.json.
It intentionally mirrors shared/preprocessing.json and shared/swbra_spec.json.
The Rust core and Android app must produce identical results for the same vectors.

SWBRA raw-value thresholds are UNKNOWN in the source, so this module never derives
SWBRA flags from raw biomarkers. Only the documented boolean-flag arithmetic is
implemented, for tests and future work.
"""
from __future__ import annotations

import json
import math
import os
import sys
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple


def _resolve_shared_dir() -> Path:
    """Locate shared/ in source checkouts and in frozen PyInstaller builds."""
    override = os.environ.get("SURAS_SHARED_DIR")
    if override:
        return Path(override).expanduser().resolve()
    if getattr(sys, "frozen", False):
        return Path(getattr(sys, "_MEIPASS", Path(sys.executable).parent)) / "shared"
    return Path(__file__).resolve().parents[1] / "shared"


SHARED = _resolve_shared_dir()
ROOT = SHARED.parent

BOOLEAN_FIELDS = (
    "Spicy_Food",
    "Tobacco",
    "Alcohol",
    "Skip_Meals",
    "Soft_Drinks",
    "Empty_Stomach_Pain",
)
NUMERIC_FIELDS = (
    "Age",
    "Sleep_Hours",
    "Stress",
    "Day1_pH_Before",
    "Day1_pH_After",
    "Day2_pH_Before",
    "Day2_pH_After",
)


def load_specs() -> Tuple[Dict[str, Any], Dict[str, Any], Dict[str, Any]]:
    preprocessing = json.loads((SHARED / "preprocessing.json").read_text())
    swbra = json.loads((SHARED / "swbra_spec.json").read_text())
    version = json.loads((SHARED / "version.json").read_text())
    return preprocessing, swbra, version


def feature_order() -> List[str]:
    preprocessing, _, _ = load_specs()
    return [f["name"] for f in preprocessing["featureOrder"]]


def _is_finite_number(value: Any) -> bool:
    return isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(float(value))


def validate_and_encode(payload: Dict[str, Any]) -> Dict[str, Any]:
    """Validate one patient payload and return the 14-float feature vector in model order.

    Returns {"ok": True, "features": [...], "warnings": [...]} on success or
    {"ok": False, "errors": [{"code","field","message"}]} on failure.
    """
    preprocessing, _, version = load_specs()
    errors: List[Dict[str, str]] = []
    warnings: List[Dict[str, str]] = []
    features: List[float] = []

    expected = {f["name"]: f for f in preprocessing["featureOrder"]}
    for name in expected:
        if name not in payload:
            errors.append({"code": "MISSING_FIELD", "field": name, "message": f"{name} is required"})
    if errors:
        return {"ok": False, "errors": errors}

    for field in preprocessing["featureOrder"]:
        name = field["name"]
        value = payload[name]
        if field["type"] == "number":
            if not _is_finite_number(value):
                errors.append(
                    {"code": "NON_FINITE" if isinstance(value, (int, float)) and not isinstance(value, bool) else "INVALID_TYPE",
                     "field": name, "message": f"{name} must be a finite number"}
                )
                continue
            numeric = float(value)
            bound = field.get("sanityBound")
            if bound and not (bound["min"] <= numeric <= bound["max"]):
                errors.append(
                    {"code": "OUT_OF_RANGE", "field": name,
                     "message": f"{name} must be between {bound['min']} and {bound['max']}"}
                )
                continue
            envelope = preprocessing["datasetEnvelope"]["bounds"].get(name)
            if envelope and not (envelope["min"] <= numeric <= envelope["max"]):
                warnings.append(
                    {"code": "OUTSIDE_DATASET_ENVELOPE", "field": name,
                     "message": f"{name}={numeric} is outside the training data range "
                                f"[{envelope['min']}, {envelope['max']}]; prediction may be less reliable"}
                )
            features.append(numeric)
        elif field["type"] == "categorical":
            options = field["encoding"]
            if value not in options:
                errors.append(
                    {"code": "INVALID_VALUE", "field": name,
                     "message": f"{name} must be one of {list(options)}"}
                )
                continue
            features.append(float(options[value]))
        elif field["type"] == "boolean":
            options = field["encoding"]
            if value not in options:
                errors.append(
                    {"code": "INVALID_VALUE", "field": name,
                     "message": f"{name} must be Yes or No"}
                )
                continue
            features.append(float(options[value]))
        else:  # pragma: no cover - guarded by the spec
            errors.append({"code": "SPEC_ERROR", "field": name, "message": "unknown field type"})

    if errors:
        return {"ok": False, "errors": errors, "warnings": warnings}

    return {
        "ok": True,
        "features": features,
        "warnings": warnings,
        "versions": _versions(version),
    }


def _versions(version: Dict[str, Any]) -> Dict[str, str]:
    return {
        "appVersion": version["version"],
        "modelVersion": version["modelVersion"],
        "swbraSpecVersion": version["swbraSpecVersion"],
        "preprocessingVersion": version["preprocessingVersion"],
    }


def classify_probability(probability: float) -> str:
    """Apply the exact strict operators from apps/desktop/main.py:886."""
    if not _is_finite_number(probability) or not (0.0 <= float(probability) <= 1.0):
        raise ValueError("probability must be finite and in [0, 1]")
    p = float(probability)
    if p > 0.7:
        return "HIGH"
    if p > 0.3:
        return "MEDIUM"
    return "LOW"


def swbra_score(flags: Dict[str, bool]) -> int:
    _, swbra, _ = load_specs()
    points = {name: spec["points"] for name, spec in swbra["inputs"].items()}
    return int(
        points["IL6"] * bool(flags.get("il6_high"))
        + points["CRP"] * bool(flags.get("crp_high"))
        + points["IgA"] * bool(flags.get("iga_low"))
        + points["pH"] * bool(flags.get("ph_abnormal"))
    )


def swbra_category(score: int) -> str:
    _, swbra, _ = load_specs()
    for band in swbra["bands"]:
        if band["min"] <= score <= band["max"]:
            return band["category"]
    raise ValueError(f"score {score} outside documented 0..90 range")


def swbra_result(flags: Optional[Dict[str, bool]] = None) -> Dict[str, Any]:
    _, swbra, version = load_specs()
    if flags is None:
        return {
            "status": "UNAVAILABLE",
            "score": None,
            "category": None,
            "reason": "Raw biomarker thresholds, operators and units are UNKNOWN in the source implementation; the app does not collect IL-6, CRP, IgA or protein.",
            "specVersion": version["swbraSpecVersion"],
        }
    score = swbra_score(flags)
    return {
        "status": "DOCUMENTED_ARITHMETIC_ONLY",
        "score": score,
        "maxScore": swbra["maxScore"],
        "category": swbra_category(score),
        "reason": "Computed from explicit boolean flags using README-documented weights; raw-value thresholds remain UNKNOWN and clinically unvalidated.",
        "specVersion": version["swbraSpecVersion"],
    }


def evaluate(payload: Dict[str, Any], probability: Optional[float]) -> Dict[str, Any]:
    prepared = validate_and_encode(payload)
    if not prepared["ok"]:
        return prepared
    if probability is None:
        return {
            "ok": False,
            "errors": [{"code": "MISSING_FIELD", "field": "model_probability",
                        "message": "model_probability is required for evaluation"}],
        }
    try:
        category = classify_probability(probability)
    except ValueError as exc:
        return {"ok": False, "errors": [{"code": "OUT_OF_RANGE", "field": "model_probability",
                                         "message": str(exc)}]}
    _, _, version = load_specs()
    return {
        "ok": True,
        "features": prepared["features"],
        "warnings": prepared["warnings"],
        "probability": float(probability),
        "mlCategory": category,
        "swbra": swbra_result(payload.get("swbraFlags")),
        "versions": _versions(version),
    }
