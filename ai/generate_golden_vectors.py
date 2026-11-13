"""Generate shared/golden_vectors.json from the executable Python reference.

Run: python ai/generate_golden_vectors.py
"""
from __future__ import annotations

import itertools
import json
from pathlib import Path

from reference import (
    classify_probability,
    evaluate,
    feature_order,
    load_specs,
    swbra_category,
    swbra_score,
    validate_and_encode,
)

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "shared" / "golden_vectors.json"

BASELINE = {
    "Age": 40,
    "Gender": "Male",
    "Spicy_Food": "No",
    "Tobacco": "No",
    "Alcohol": "No",
    "Sleep_Hours": 7,
    "Stress": 5,
    "Skip_Meals": "No",
    "Soft_Drinks": "No",
    "Empty_Stomach_Pain": "No",
    "Day1_pH_Before": 6.5,
    "Day1_pH_After": 6.0,
    "Day2_pH_Before": 6.6,
    "Day2_pH_After": 5.9,
}

HIGH_INPUT = {
    "Age": 64,
    "Gender": "Female",
    "Spicy_Food": "Yes",
    "Tobacco": "Yes",
    "Alcohol": "Yes",
    "Sleep_Hours": 4,
    "Stress": 10,
    "Skip_Meals": "Yes",
    "Soft_Drinks": "Yes",
    "Empty_Stomach_Pain": "Yes",
    "Day1_pH_Before": 7.5,
    "Day1_pH_After": 4.5,
    "Day2_pH_Before": 7.5,
    "Day2_pH_After": 4.5,
}

LOW_INPUT = {
    "Age": 18,
    "Gender": "Female",
    "Spicy_Food": "No",
    "Tobacco": "No",
    "Alcohol": "No",
    "Sleep_Hours": 9,
    "Stress": 1,
    "Skip_Meals": "No",
    "Soft_Drinks": "No",
    "Empty_Stomach_Pain": "No",
    "Day1_pH_Before": 5.0,
    "Day1_pH_After": 6.99,
    "Day2_pH_Before": 5.01,
    "Day2_pH_After": 7.0,
}

SANITY_EDGE_INPUT = {
    "Age": 0,
    "Gender": "Male",
    "Spicy_Food": "No",
    "Tobacco": "No",
    "Alcohol": "No",
    "Sleep_Hours": 0,
    "Stress": 1,
    "Skip_Meals": "No",
    "Soft_Drinks": "No",
    "Empty_Stomach_Pain": "No",
    "Day1_pH_Before": 0.0,
    "Day1_pH_After": 0.0,
    "Day2_pH_Before": 0.0,
    "Day2_pH_After": 0.0,
}


def validation_vector(name: str, payload: dict) -> dict:
    result = validate_and_encode(payload)
    entry = {
        "name": name,
        "payload": payload,
        "expected": {
            "ok": result["ok"],
            "errorCodes": [e["code"] for e in result.get("errors", [])],
            "errorFields": [e["field"] for e in result.get("errors", [])],
            "warningCodes": [w["code"] for w in result.get("warnings", [])],
        },
    }
    if result["ok"]:
        entry["expected"]["features"] = result["features"]
    return entry


def main() -> None:
    preprocessing, swbra, version = load_specs()
    vectors = {
        "schemaVersion": "1.0.0",
        "generatedBy": "ai/generate_golden_vectors.py",
        "generatedAt": "2026-11-13",
        "featureOrder": feature_order(),
        "versions": {
            "appVersion": version["version"],
            "modelVersion": version["modelVersion"],
            "swbraSpecVersion": version["swbraSpecVersion"],
            "preprocessingVersion": version["preprocessingVersion"],
        },
        "mlThresholdVectors": [],
        "evaluationVectors": [],
        "swbraFlagVectors": [],
        "swbraBandBoundaryVectors": [],
        "validationVectors": [],
    }

    threshold_probs = [
        0.0,
        0.1,
        0.3,
        0.30000000000000004,
        0.5,
        0.7,
        0.7000000000000001,
        0.9,
        1.0,
    ]
    for p in threshold_probs:
        vectors["mlThresholdVectors"].append(
            {"probability": p, "expectedCategory": classify_probability(p)}
        )

    for index, (payload, p) in enumerate(
        [
            (BASELINE, 0.0),
            (BASELINE, 0.3),
            (BASELINE, 0.30000000000000004),
            (BASELINE, 0.7),
            (BASELINE, 0.7000000000000001),
            (HIGH_INPUT, 0.87),
            (LOW_INPUT, 0.04),
            (SANITY_EDGE_INPUT, 0.5),
        ]
    ):
        result = evaluate(payload, p)
        assert result["ok"], result
        vectors["evaluationVectors"].append(
            {
                "name": f"eval-{index}",
                "payload": payload,
                "probability": p,
                "expected": {
                    "features": result["features"],
                    "warningCodes": [w["code"] for w in result["warnings"]],
                    "mlCategory": result["mlCategory"],
                    "swbraStatus": result["swbra"]["status"],
                    "swbraScore": result["swbra"]["score"],
                    "swbraCategory": result["swbra"]["category"],
                    "versions": result["versions"],
                },
            }
        )

    flag_names = ["il6_high", "crp_high", "iga_low", "ph_abnormal"]
    for values in itertools.product([False, True], repeat=4):
        flags = dict(zip(flag_names, values))
        score = swbra_score(flags)
        vectors["swbraFlagVectors"].append(
            {
                "flags": flags,
                "expectedScore": score,
                "expectedCategory": swbra_category(score),
            }
        )

    for score in [0, 15, 20, 25, 30, 39, 40, 55, 69, 70, 75, 90]:
        vectors["swbraBandBoundaryVectors"].append(
            {"score": score, "expectedCategory": swbra_category(score)}
        )

    invalid_cases = [
        ("missing-stress", {k: v for k, v in BASELINE.items() if k != "Stress"}),
        ("unknown-gender", {**BASELINE, "Gender": "Other"}),
        ("age-negative", {**BASELINE, "Age": -1}),
        ("age-too-high", {**BASELINE, "Age": 121}),
        ("sleep-too-high", {**BASELINE, "Sleep_Hours": 25}),
        ("sleep-negative", {**BASELINE, "Sleep_Hours": -1}),
        ("stress-zero", {**BASELINE, "Stress": 0}),
        ("stress-eleven", {**BASELINE, "Stress": 11}),
        ("ph-negative", {**BASELINE, "Day1_pH_Before": -0.1}),
        ("ph-too-high", {**BASELINE, "Day2_pH_After": 14.1}),
        ("yesno-invalid", {**BASELINE, "Tobacco": "Maybe"}),
        ("age-string", {**BASELINE, "Age": "40"}),
        ("nan-string", {**BASELINE, "Stress": "NaN"}),
        ("inf-string", {**BASELINE, "Stress": "Infinity"}),
    ]
    for name, payload in invalid_cases:
        vectors["validationVectors"].append(validation_vector(name, payload))

    vectors["validationVectors"].extend(
        [
            validation_vector("baseline", BASELINE),
            validation_vector("high-input", HIGH_INPUT),
            validation_vector("low-input", LOW_INPUT),
            validation_vector("sanity-edge", SANITY_EDGE_INPUT),
        ]
    )

    OUT.write_text(json.dumps(vectors, indent=2, sort_keys=True) + "\n")
    print(f"Wrote {OUT} ({OUT.stat().st_size} bytes)")
    print(f"  ml thresholds: {len(vectors['mlThresholdVectors'])}")
    print(f"  evaluation:    {len(vectors['evaluationVectors'])}")
    print(f"  swbra flags:   {len(vectors['swbraFlagVectors'])}")
    print(f"  swbra bands:   {len(vectors['swbraBandBoundaryVectors'])}")
    print(f"  validation:    {len(vectors['validationVectors'])}")


if __name__ == "__main__":
    main()
