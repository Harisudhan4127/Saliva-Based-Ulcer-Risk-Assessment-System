"""Tests for checksum-verified ONNX loading and inference parity.

Skipped automatically when numpy/onnxruntime are not installed (the desktop
runtime requirements install both).
"""

from __future__ import annotations

import importlib.util
import json
import shutil
from pathlib import Path

import pytest

import inference

pytestmark = pytest.mark.skipif(
    importlib.util.find_spec("onnxruntime") is None or importlib.util.find_spec("numpy") is None,
    reason="onnxruntime and numpy are required for inference tests",
)

ROOT = Path(__file__).resolve().parents[3]
PARITY = json.loads((ROOT / "shared" / "model" / "parity_vectors.json").read_text())
MODEL_PATH, SUMS_PATH = inference.model_paths()


def test_model_loads_and_verifies_checksum():
    model = inference.load_model()
    assert model.sha256 == inference.expected_digest(SUMS_PATH)
    assert model.sha256 == inference.sha256_of(MODEL_PATH)


def test_inference_matches_exported_parity_vectors():
    model = inference.load_model()
    tolerance = PARITY["tolerance"]
    for vector in PARITY["vectors"][:50]:
        probability = model.predict_proba(vector["features"])[0][1]
        assert abs(probability - vector["onnxProbability"]) < tolerance


def test_dataframe_input_matches_sequence_input():
    import numpy as np
    import pandas as pd

    model = inference.load_model()
    features = PARITY["vectors"][0]["features"]
    from_sequence = model.predict_proba(features)[0][1]
    from_frame = model.predict_proba(
        pd.DataFrame([features], columns=PARITY["featureOrder"])
    )[0][1]
    assert from_sequence == pytest.approx(from_frame)
    assert np.isfinite(from_sequence)


def test_tampered_model_is_rejected(tmp_path):
    model_copy = tmp_path / "model.onnx"
    shutil.copyfile(MODEL_PATH, model_copy)
    original = bytearray(model_copy.read_bytes())
    original[len(original) // 2] ^= 0xFF
    model_copy.write_bytes(bytes(original))

    with pytest.raises(inference.ModelIntegrityError):
        inference.OnnxRiskModel(model_copy, SUMS_PATH)


def test_missing_checksum_file_is_rejected(tmp_path):
    with pytest.raises(inference.ModelIntegrityError):
        inference.expected_digest(tmp_path / "SHA256SUMS")


def test_wrong_feature_count_is_rejected():
    model = inference.load_model()
    with pytest.raises(inference.ModelUnavailableError):
        model.predict_proba([1.0, 2.0, 3.0])
