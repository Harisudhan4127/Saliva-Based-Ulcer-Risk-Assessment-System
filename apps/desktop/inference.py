"""Checksum-verified ONNX inference for the desktop app.

The previous build unpickled ``src/data.pkl`` at startup. Unpickling executes
arbitrary code from the file, and the pickle was produced with a different
scikit-learn version than the one currently installed. The desktop app now runs
the same ONNX artifact as Android, after verifying its SHA-256 digest from
``shared/model/SHA256SUMS``.

Loading happens in a background ``QThread`` so the window can paint immediately;
inference itself is a few milliseconds and stays on the calling thread.
"""

from __future__ import annotations

import hashlib
import hmac
from pathlib import Path
from typing import Any, Iterable, List, Sequence

import numpy as np

from paths import shared_dir

MODEL_FILE_NAME = "model.onnx"
SHA256SUMS_NAME = "SHA256SUMS"
INPUT_NAME = "input"
OUTPUT_NAME = "probabilities"
POSITIVE_CLASS_INDEX = 1
FEATURE_COUNT = 14


class ModelIntegrityError(RuntimeError):
    """The model file is missing or does not match the published checksum."""


class ModelUnavailableError(RuntimeError):
    """The model could not be loaded or returned an unusable probability."""


def model_paths() -> tuple[Path, Path]:
    model_dir = shared_dir() / "model"
    return model_dir / MODEL_FILE_NAME, model_dir / SHA256SUMS_NAME


def expected_digest(sums_path: Path) -> str:
    if not sums_path.is_file():
        raise ModelIntegrityError(f"Checksum file not found: {sums_path}")
    for line in sums_path.read_text(encoding="utf-8").splitlines():
        parts = line.split()
        if len(parts) >= 2 and parts[-1].lstrip("*") == MODEL_FILE_NAME:
            digest = parts[0].strip().lower()
            if len(digest) != 64 or any(c not in "0123456789abcdef" for c in digest):
                raise ModelIntegrityError("Malformed SHA-256 digest in checksum file")
            return digest
    raise ModelIntegrityError(f"No checksum entry for {MODEL_FILE_NAME}")


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


class OnnxRiskModel:
    """Thin wrapper exposing the ``predict_proba`` shape used by the UI."""

    def __init__(self, model_path: Path, sums_path: Path) -> None:
        if not model_path.is_file():
            raise ModelIntegrityError(f"Model file not found: {model_path}")
        expected = expected_digest(sums_path)
        actual = sha256_of(model_path)
        if not hmac.compare_digest(actual, expected):
            raise ModelIntegrityError(
                "Model checksum mismatch: refusing to load the model"
            )

        import onnxruntime as ort

        options = ort.SessionOptions()
        options.intra_op_num_threads = 1
        options.inter_op_num_threads = 1
        options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
        self._session = ort.InferenceSession(
            str(model_path),
            sess_options=options,
            providers=["CPUExecutionProvider"],
        )
        self._input_name = self._session.get_inputs()[0].name or INPUT_NAME
        output_names = [output.name for output in self._session.get_outputs()]
        if OUTPUT_NAME in output_names:
            self._output_name = OUTPUT_NAME
        elif output_names:
            self._output_name = output_names[-1]
        else:  # pragma: no cover - defensive
            raise ModelUnavailableError("Model has no outputs")
        self.sha256 = actual

    def predict_proba(self, features: Any) -> List[List[float]]:
        """Return ``[[1 - p, p]]`` for one patient.

        Accepts a pandas DataFrame (as used by the form), a sequence, or a numpy
        array. Exactly 14 finite floats are required.
        """
        vector = _to_vector(features)
        array = np.asarray(vector, dtype=np.float32).reshape(1, FEATURE_COUNT)
        try:
            outputs = self._session.run([self._output_name], {self._input_name: array})
        except Exception as exc:  # pragma: no cover - depends on native runtime
            raise ModelUnavailableError(f"Inference failed: {exc}") from exc
        if not outputs:
            raise ModelUnavailableError("Model returned no output")
        probabilities = np.asarray(outputs[0])
        if probabilities.ndim != 2 or probabilities.shape[1] <= POSITIVE_CLASS_INDEX:
            raise ModelUnavailableError("Unexpected model output shape")
        probability = float(probabilities[0][POSITIVE_CLASS_INDEX])
        if not np.isfinite(probability) or not 0.0 <= probability <= 1.0:
            raise ModelUnavailableError("Model returned an invalid probability")
        return [[1.0 - probability, probability]]


def _to_vector(features: Any) -> Sequence[float]:
    if hasattr(features, "to_numpy"):
        features = features.to_numpy()
    if isinstance(features, np.ndarray):
        flat: Iterable[Any] = features.reshape(-1).tolist()
    else:
        flat = features
    values = [float(value) for value in flat]
    if len(values) != FEATURE_COUNT:
        raise ModelUnavailableError(
            f"Expected {FEATURE_COUNT} features, received {len(values)}"
        )
    if not all(np.isfinite(value) for value in values):
        raise ModelUnavailableError("Feature vector contains a non-finite value")
    return values


def load_model() -> OnnxRiskModel:
    model_path, sums_path = model_paths()
    return OnnxRiskModel(model_path, sums_path)


def make_loader():
    """Return a ``QThread`` subclass; imported lazily so tests need no Qt."""
    from PyQt5.QtCore import QThread, pyqtSignal

    class ModelLoadWorker(QThread):
        loaded = pyqtSignal(object)
        failed = pyqtSignal(str)

        def run(self) -> None:  # pragma: no cover - exercised on device
            try:
                self.loaded.emit(load_model())
            except Exception as exc:
                self.failed.emit(str(exc))

    return ModelLoadWorker
