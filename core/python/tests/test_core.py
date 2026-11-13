"""Tests for the PyO3 binding of the shared Rust core.

Run after `maturin develop --features python` from `core/rust`:

    pytest core/python/tests
"""
from __future__ import annotations

import json
from pathlib import Path

import suras_core

ROOT = Path(__file__).resolve().parents[3]
GOLDEN = json.loads((ROOT / "shared" / "golden_vectors.json").read_text())


def load(output: str) -> dict:
    return json.loads(output)


def test_ml_threshold_vectors() -> None:
    for vector in GOLDEN["mlThresholdVectors"]:
        result = load(suras_core.classify_json(vector["probability"]))
        assert result["ok"] is True
        assert result["category"] == vector["expectedCategory"]


def test_validation_vectors() -> None:
    for vector in GOLDEN["validationVectors"]:
        result = load(suras_core.prepare_json(json.dumps(vector["payload"])))
        expected = vector["expected"]
        assert result["ok"] is expected["ok"], vector["name"]
        assert [e["code"] for e in result.get("errors", [])] == expected["errorCodes"]
        assert [e["field"] for e in result.get("errors", [])] == expected["errorFields"]
        assert [w["code"] for w in result.get("warnings", [])] == expected["warningCodes"]
        if expected["ok"]:
            assert result["features"] == expected["features"]


def test_evaluation_vectors() -> None:
    for vector in GOLDEN["evaluationVectors"]:
        result = load(
            suras_core.evaluate_json(json.dumps(vector["payload"]), vector["probability"])
        )
        expected = vector["expected"]
        assert result["ok"] is True, vector["name"]
        assert result["features"] == expected["features"]
        assert result["mlCategory"] == expected["mlCategory"]
        assert result["swbra"]["status"] == expected["swbraStatus"]
        assert result["swbra"]["score"] == expected["swbraScore"]
        assert result["swbra"]["category"] == expected["swbraCategory"]
        assert result["versions"] == expected["versions"]


def test_swbra_vectors() -> None:
    for vector in GOLDEN["swbraFlagVectors"]:
        result = load(suras_core.swbra_json(json.dumps(vector["flags"])))
        assert result["status"] == "DOCUMENTED_ARITHMETIC_ONLY"
        assert result["score"] == vector["expectedScore"]
        assert result["category"] == vector["expectedCategory"]

    unavailable = load(suras_core.swbra_json(""))
    assert unavailable["status"] == "UNAVAILABLE"
    assert unavailable["score"] is None


def test_versions_and_spec() -> None:
    versions = load(suras_core.versions_json())
    assert versions == GOLDEN["versions"]

    spec = load(suras_core.spec_json())
    assert [f["name"] for f in spec["preprocessing"]["featureOrder"]] == GOLDEN["featureOrder"]
    assert spec["version"]["version"] == versions["appVersion"]


def test_invalid_json_does_not_echo_input() -> None:
    marker = "patient-name-marker"
    result = load(suras_core.prepare_json('{"Age": ' + marker))
    assert result["ok"] is False
    assert result["errors"][0]["code"] == "INVALID_JSON"
    assert marker not in json.dumps(result)


def test_probability_validation() -> None:
    assert load(suras_core.classify_json(-0.01))["ok"] is False
    assert load(suras_core.classify_json(1.01))["ok"] is False
    payload = json.dumps(GOLDEN["evaluationVectors"][0]["payload"])
    assert load(suras_core.evaluate_json(payload, None))["errors"][0]["code"] == "MISSING_FIELD"
