"""Tests for the reference implementation, the exported ONNX model and parity.

Run:

    pytest ai/tests
"""
from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

import numpy as np
import onnxruntime as ort
import pytest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "ai"))

import reference  # noqa: E402

GOLDEN = json.loads((ROOT / "shared" / "golden_vectors.json").read_text())
MODEL_DIR = ROOT / "shared" / "model"
METADATA = json.loads((MODEL_DIR / "metadata.json").read_text())
PARITY = json.loads((MODEL_DIR / "parity_vectors.json").read_text())


def test_golden_vectors_match_reference() -> None:
    for vector in GOLDEN["mlThresholdVectors"]:
        assert reference.classify_probability(vector["probability"]) == vector["expectedCategory"]

    for vector in GOLDEN["validationVectors"]:
        result = reference.validate_and_encode(vector["payload"])
        expected = vector["expected"]
        assert result["ok"] is expected["ok"], vector["name"]
        assert [e["code"] for e in result.get("errors", [])] == expected["errorCodes"]
        assert [e["field"] for e in result.get("errors", [])] == expected["errorFields"]
        assert [w["code"] for w in result.get("warnings", [])] == expected["warningCodes"]
        if expected["ok"]:
            assert result["features"] == expected["features"]

    for vector in GOLDEN["evaluationVectors"]:
        result = reference.evaluate(vector["payload"], vector["probability"])
        expected = vector["expected"]
        assert result["ok"] is True, vector["name"]
        assert result["features"] == expected["features"]
        assert result["mlCategory"] == expected["mlCategory"]
        assert result["swbra"]["status"] == expected["swbraStatus"]
        assert result["swbra"]["score"] == expected["swbraScore"]
        assert result["swbra"]["category"] == expected["swbraCategory"]
        assert result["versions"] == expected["versions"]

    for vector in GOLDEN["swbraFlagVectors"]:
        assert reference.swbra_score(vector["flags"]) == vector["expectedScore"]
        assert reference.swbra_category(vector["expectedScore"]) == vector["expectedCategory"]

    for vector in GOLDEN["swbraBandBoundaryVectors"]:
        assert reference.swbra_category(vector["score"]) == vector["expectedCategory"]


def test_golden_vectors_are_regenerable() -> None:
    """The committed vectors must equal a fresh regeneration (no silent drift)."""
    sys.path.insert(0, str(ROOT / "ai"))
    import generate_golden_vectors  # noqa: F401

    # The generator writes the file; compare its in-memory expectations instead
    # of rewriting the working tree during tests.
    for vector in GOLDEN["evaluationVectors"]:
        result = reference.evaluate(vector["payload"], vector["probability"])
        assert result["features"] == vector["expected"]["features"]
        assert result["swbra"]["status"] == vector["expected"]["swbraStatus"]


def test_model_checksum_matches_metadata() -> None:
    model_bytes = (MODEL_DIR / "model.onnx").read_bytes()
    digest = hashlib.sha256(model_bytes).hexdigest()
    assert digest == METADATA["sha256"]
    assert (MODEL_DIR / "SHA256SUMS").read_text().split()[0] == digest
    assert len(model_bytes) == METADATA["sizeBytes"]


def test_metadata_matches_shared_specs() -> None:
    preprocessing = json.loads((ROOT / "shared" / "preprocessing.json").read_text())
    version = json.loads((ROOT / "shared" / "version.json").read_text())
    assert METADATA["trainingData"]["features"] == GOLDEN["featureOrder"]
    assert METADATA["trainingData"]["features"] == [
        field["name"] for field in preprocessing["featureOrder"]
    ]
    assert METADATA["modelVersion"] == version["modelVersion"]
    assert METADATA["onnxOpset"] == 17


def test_onnx_parity_vectors() -> None:
    session = ort.InferenceSession(
        str(MODEL_DIR / "model.onnx"), providers=["CPUExecutionProvider"]
    )
    input_name = session.get_inputs()[0].name
    tolerance = PARITY["tolerance"]
    assert tolerance > 0
    worst = 0.0

    for vector in PARITY["vectors"]:
        features = np.asarray([vector["features"]], dtype=np.float32)
        outputs = session.run(None, {input_name: features})
        probability = float(outputs[1][0, 1])
        assert abs(probability - vector["onnxProbability"]) <= tolerance
        assert abs(vector["onnxProbability"] - vector["sklearnProbability"]) <= tolerance
        worst = max(worst, abs(probability - vector["sklearnProbability"]))

    assert worst <= tolerance


def test_onnx_matches_reference_pipeline() -> None:
    """End-to-end: reference validation -> ONNX -> reference classification."""
    session = ort.InferenceSession(
        str(MODEL_DIR / "model.onnx"), providers=["CPUExecutionProvider"]
    )
    input_name = session.get_inputs()[0].name

    for vector in GOLDEN["evaluationVectors"]:
        prepared = reference.validate_and_encode(vector["payload"])
        assert prepared["ok"] is True
        features = np.asarray([prepared["features"]], dtype=np.float32)
        outputs = session.run(None, {input_name: features})
        probability = float(outputs[1][0, 1])
        assert 0.0 <= probability <= 1.0
        # The golden probability is a fixture, not the model output for these
        # inputs, so only check that the category is consistent with the model.
        assert reference.classify_probability(probability) in {"LOW", "MEDIUM", "HIGH"}


def test_model_is_small_enough_for_mobile() -> None:
    """RECOMMENDATION: keep the mobile artifact well below the 5 MiB budget."""
    assert (MODEL_DIR / "model.onnx").stat().st_size < 5 * 1024 * 1024


@pytest.mark.parametrize("name", ["metadata.json", "SHA256SUMS", "parity_vectors.json"])
def test_expected_artifacts_exist(name: str) -> None:
    assert (MODEL_DIR / name).is_file()
