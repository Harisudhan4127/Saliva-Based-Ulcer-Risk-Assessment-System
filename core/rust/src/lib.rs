//! SURAS shared core.
//!
//! This crate is the single implementation of the validation, preprocessing,
//! risk classification and SWBRA-arithmetic rules that the desktop and Android
//! applications share. It is deliberately a JSON-in/JSON-out API so that the
//! UniFFI (Kotlin) and PyO3 (Python) bindings stay trivial and cannot drift.
//!
//! Provenance tags used below:
//!   VERIFIED      - copied from the shipped source code or a measured artifact.
//!   ASSUMPTION    - a defensible engineering choice, not a clinical claim.
//!   RECOMMENDATION- suggested future work.
//!
//! The embedded JSON specifications are the source of truth. Editing Rust
//! literals is never necessary: change `shared/*.json`, regenerate the golden
//! vectors, and every platform follows.
//!
//! The core never invents a clinical threshold. SWBRA raw-value scoring is
//! UNAVAILABLE because the source implementation does not define units,
//! thresholds, operators or missing-value handling. Only the documented
//! boolean-flag arithmetic is implemented, for tests and future work.

use std::panic::{catch_unwind, AssertUnwindSafe};
use std::sync::OnceLock;

use serde_json::{json, Value};

uniffi::setup_scaffolding!();

/// VERIFIED: shared/preprocessing.json is generated from apps/desktop/main.py.
const PREPROCESSING_JSON: &str = include_str!("../../../shared/preprocessing.json");
/// VERIFIED: shared/swbra_spec.json records the README-only SWBRA description.
const SWBRA_SPEC_JSON: &str = include_str!("../../../shared/swbra_spec.json");
/// VERIFIED: shared/version.json is the single version source of truth.
const VERSION_JSON: &str = include_str!("../../../shared/version.json");

/// Parsed, immutable specifications. Parsing happens once per process.
struct Specs {
    preprocessing: Value,
    swbra: Value,
    version: Value,
}

fn specs() -> &'static Specs {
    static SPECS: OnceLock<Specs> = OnceLock::new();
    SPECS.get_or_init(|| Specs {
        preprocessing: serde_json::from_str(PREPROCESSING_JSON)
            .expect("embedded shared/preprocessing.json must be valid JSON"),
        swbra: serde_json::from_str(SWBRA_SPEC_JSON)
            .expect("embedded shared/swbra_spec.json must be valid JSON"),
        version: serde_json::from_str(VERSION_JSON)
            .expect("embedded shared/version.json must be valid JSON"),
    })
}

fn feature_fields(specs: &Specs) -> &[Value] {
    specs
        .preprocessing
        .get("featureOrder")
        .and_then(Value::as_array)
        .map(Vec::as_slice)
        .expect("shared/preprocessing.json must contain featureOrder")
}

fn field_name(field: &Value) -> &str {
    field
        .get("name")
        .and_then(Value::as_str)
        .expect("featureOrder entry must have a string name")
}

fn error_item(code: &str, field: &str, message: &str) -> Value {
    json!({ "code": code, "field": field, "message": message })
}

fn warning_item(code: &str, field: &str, message: &str) -> Value {
    json!({ "code": code, "field": field, "message": message })
}

fn error_result(errors: Vec<Value>) -> Value {
    json!({ "ok": false, "errors": errors })
}

fn invalid_json(field: &str) -> Value {
    // Deliberately does not echo the input: patient payloads must never leak
    // into logs or crash reports.
    error_result(vec![error_item(
        "INVALID_JSON",
        field,
        "value must be valid JSON",
    )])
}

fn versions_value(specs: &Specs) -> Value {
    json!({
        "appVersion": specs.version.get("version").cloned().unwrap_or(Value::Null),
        "modelVersion": specs.version.get("modelVersion").cloned().unwrap_or(Value::Null),
        "swbraSpecVersion": specs.version.get("swbraSpecVersion").cloned().unwrap_or(Value::Null),
        "preprocessingVersion": specs.version.get("preprocessingVersion").cloned().unwrap_or(Value::Null),
    })
}

/// Format a number for human-readable validation messages.
fn fmt_num(value: f64) -> String {
    if value.fract() == 0.0 && value.abs() < 1e15 {
        format!("{}", value as i64)
    } else {
        format!("{value}")
    }
}

/// Validate one patient payload and encode the 14-float model vector.
///
/// Mirrors `ai/reference.py::validate_and_encode` exactly, including the
/// ordering of missing-field errors, the strict sanity bounds, and the
/// non-blocking dataset-envelope warnings.
fn prepare_value(payload: &Value) -> Value {
    let specs = specs();
    let Some(object) = payload.as_object() else {
        return error_result(vec![error_item(
            "INVALID_TYPE",
            "payload",
            "payload must be a JSON object",
        )]);
    };

    let fields = feature_fields(specs);

    // VERIFIED: main.py:686-690 requires every field; a missing field raises.
    let missing: Vec<Value> = fields
        .iter()
        .filter(|field| !object.contains_key(field_name(field)))
        .map(|field| {
            let name = field_name(field);
            error_item("MISSING_FIELD", name, &format!("{name} is required"))
        })
        .collect();
    if !missing.is_empty() {
        return error_result(missing);
    }

    let mut errors: Vec<Value> = Vec::new();
    let mut warnings: Vec<Value> = Vec::new();
    let mut features: Vec<f64> = Vec::with_capacity(fields.len());

    for field in fields {
        let name = field_name(field);
        let value = &object[name];
        let kind = field.get("type").and_then(Value::as_str).unwrap_or("");

        match kind {
            "number" => {
                let numeric = match value {
                    Value::Number(number) => match number.as_f64() {
                        Some(number) if number.is_finite() => Some(number),
                        _ => None,
                    },
                    _ => None,
                };
                let Some(numeric) = numeric else {
                    // Python distinguishes a non-finite number from a
                    // non-number. JSON cannot encode NaN/Infinity, but a
                    // binding could forward one, so keep the distinction.
                    let code = match value {
                        Value::Number(_) => "NON_FINITE",
                        _ => "INVALID_TYPE",
                    };
                    errors.push(error_item(
                        code,
                        name,
                        &format!("{name} must be a finite number"),
                    ));
                    continue;
                };

                if let Some(bound) = field.get("sanityBound").and_then(Value::as_object) {
                    let min = bound.get("min").and_then(Value::as_f64);
                    let max = bound.get("max").and_then(Value::as_f64);
                    if let (Some(min), Some(max)) = (min, max) {
                        if numeric < min || numeric > max {
                            errors.push(error_item(
                                "OUT_OF_RANGE",
                                name,
                                &format!(
                                    "{name} must be between {} and {}",
                                    fmt_num(min),
                                    fmt_num(max)
                                ),
                            ));
                            continue;
                        }
                    }
                }

                if let Some(envelope) = specs
                    .preprocessing
                    .get("datasetEnvelope")
                    .and_then(|envelope| envelope.get("bounds"))
                    .and_then(|bounds| bounds.get(name))
                    .and_then(Value::as_object)
                {
                    let min = envelope.get("min").and_then(Value::as_f64);
                    let max = envelope.get("max").and_then(Value::as_f64);
                    if let (Some(min), Some(max)) = (min, max) {
                        if numeric < min || numeric > max {
                            warnings.push(warning_item(
                                "OUTSIDE_DATASET_ENVELOPE",
                                name,
                                &format!(
                                    "{name}={} is outside the training data range [{}, {}]; prediction may be less reliable",
                                    fmt_num(numeric),
                                    fmt_num(min),
                                    fmt_num(max)
                                ),
                            ));
                        }
                    }
                }

                features.push(numeric);
            }
            "categorical" | "boolean" => {
                let encoding = field.get("encoding").and_then(Value::as_object);
                let encoded = match (value.as_str(), encoding) {
                    (Some(label), Some(encoding)) => encoding.get(label).and_then(Value::as_f64),
                    _ => None,
                };
                match encoded {
                    Some(encoded) => features.push(encoded),
                    None => {
                        let message = if kind == "boolean" {
                            format!("{name} must be Yes or No")
                        } else {
                            let labels: Vec<&str> = encoding
                                .map(|encoding| encoding.keys().map(String::as_str).collect())
                                .unwrap_or_default();
                            format!("{name} must be one of {labels:?}")
                        };
                        errors.push(error_item("INVALID_VALUE", name, &message));
                    }
                }
            }
            _ => errors.push(error_item(
                "SPEC_ERROR",
                name,
                "unknown field type in shared/preprocessing.json",
            )),
        }
    }

    if !errors.is_empty() {
        return json!({ "ok": false, "errors": errors, "warnings": warnings });
    }

    json!({
        "ok": true,
        "features": features,
        "warnings": warnings,
        "versions": versions_value(specs),
    })
}

/// VERIFIED: apps/desktop/main.py:886. Operators are strict.
fn classify_probability(probability: f64) -> Result<&'static str, &'static str> {
    if !probability.is_finite() || !(0.0..=1.0).contains(&probability) {
        return Err("probability must be finite and in [0, 1]");
    }
    if probability > 0.7 {
        Ok("HIGH")
    } else if probability > 0.3 {
        Ok("MEDIUM")
    } else {
        Ok("LOW")
    }
}

/// Test-only helper for the documented SWBRA bands. Not exported over FFI.
pub fn swbra_category(score: i64) -> Option<&'static str> {
    let specs = specs();
    let bands = specs.swbra.get("bands")?.as_array()?;
    for band in bands {
        let min = band.get("min").and_then(Value::as_i64)?;
        let max = band.get("max").and_then(Value::as_i64)?;
        if (min..=max).contains(&score) {
            return band.get("category").and_then(Value::as_str);
        }
    }
    None
}

/// Python truthiness for the SWBRA flag map. The documented API uses booleans;
/// this mirrors `bool(flags.get(...))` for callers that send other JSON values.
fn is_truthy(value: Option<&Value>) -> bool {
    match value {
        None | Some(Value::Null) => false,
        Some(Value::Bool(flag)) => *flag,
        Some(Value::Number(number)) => number.as_f64().map(|n| n != 0.0).unwrap_or(false),
        Some(Value::String(text)) => !text.is_empty(),
        Some(Value::Array(items)) => !items.is_empty(),
        Some(Value::Object(map)) => !map.is_empty(),
    }
}

fn swbra_points(specs: &Specs, name: &str) -> i64 {
    specs
        .swbra
        .get("inputs")
        .and_then(|inputs| inputs.get(name))
        .and_then(|input| input.get("points"))
        .and_then(Value::as_i64)
        .unwrap_or(0)
}

/// Mirrors `ai/reference.py::swbra_result`.
fn swbra_result(flags: Option<&Value>) -> Value {
    let specs = specs();
    let unavailable = || {
        json!({
            "status": "UNAVAILABLE",
            "score": Value::Null,
            "category": Value::Null,
            "reason": "Raw biomarker thresholds, operators and units are UNKNOWN in the source implementation; the app does not collect IL-6, CRP, IgA or protein.",
            "specVersion": specs.version.get("swbraSpecVersion").cloned().unwrap_or(Value::Null),
        })
    };

    let Some(flags) = flags.filter(|flags| !flags.is_null()) else {
        return unavailable();
    };
    let Some(flags) = flags.as_object() else {
        return json!({
            "status": "INVALID_INPUT",
            "score": Value::Null,
            "category": Value::Null,
            "reason": "swbraFlags must be a JSON object of boolean flags",
            "specVersion": specs.version.get("swbraSpecVersion").cloned().unwrap_or(Value::Null),
        });
    };

    let score = swbra_points(specs, "IL6") * i64::from(is_truthy(flags.get("il6_high")))
        + swbra_points(specs, "CRP") * i64::from(is_truthy(flags.get("crp_high")))
        + swbra_points(specs, "IgA") * i64::from(is_truthy(flags.get("iga_low")))
        + swbra_points(specs, "pH") * i64::from(is_truthy(flags.get("ph_abnormal")));

    json!({
        "status": "DOCUMENTED_ARITHMETIC_ONLY",
        "score": score,
        "maxScore": specs.swbra.get("maxScore").cloned().unwrap_or(Value::Null),
        "category": swbra_category(score),
        "reason": "Computed from explicit boolean flags using README-documented weights; raw-value thresholds remain UNKNOWN and clinically unvalidated.",
        "specVersion": specs.version.get("swbraSpecVersion").cloned().unwrap_or(Value::Null),
    })
}

/// Mirrors `ai/reference.py::evaluate`.
fn evaluate_value(payload: &Value, probability: Option<f64>) -> Value {
    let prepared = prepare_value(payload);
    if prepared.get("ok").and_then(Value::as_bool) != Some(true) {
        return prepared;
    }

    let Some(probability) = probability else {
        return error_result(vec![error_item(
            "MISSING_FIELD",
            "model_probability",
            "model_probability is required for evaluation",
        )]);
    };

    let category = match classify_probability(probability) {
        Ok(category) => category,
        Err(message) => {
            return error_result(vec![error_item(
                "OUT_OF_RANGE",
                "model_probability",
                message,
            )]);
        }
    };

    let specs = specs();
    let swbra = swbra_result(payload.get("swbraFlags"));
    let versions = versions_value(specs);
    let features = prepared
        .get("features")
        .cloned()
        .unwrap_or(Value::Array(Vec::new()));
    let warnings = prepared
        .get("warnings")
        .cloned()
        .unwrap_or(Value::Array(Vec::new()));

    json!({
        "ok": true,
        "features": features,
        "warnings": warnings,
        "probability": probability,
        "mlCategory": category,
        "swbra": swbra,
        "versions": versions,
    })
}

fn parse_json(input: &str) -> Option<Value> {
    serde_json::from_str(input).ok()
}

fn safe_json<F>(build: F) -> String
where
    F: FnOnce() -> Value,
{
    match catch_unwind(AssertUnwindSafe(build)) {
        Ok(value) => value.to_string(),
        // A panic must never cross the FFI boundary. No input is echoed.
        Err(_) => error_result(vec![error_item(
            "INTERNAL_ERROR",
            "core",
            "internal error in the SURAS core",
        )])
        .to_string(),
    }
}

// ---------------------------------------------------------------------------
// Public JSON API (UniFFI + PyO3)
// ---------------------------------------------------------------------------

/// Validate and encode a patient payload.
///
/// Input: a JSON object with the 14 documented fields.
/// Output: `{"ok":true,"features":[14 floats],"warnings":[...],"versions":{...}}`
/// or `{"ok":false,"errors":[{"code","field","message"}]}`.
#[uniffi::export]
pub fn prepare_json(payload_json: String) -> String {
    safe_json(|| match parse_json(&payload_json) {
        Some(payload) => prepare_value(&payload),
        None => invalid_json("payload"),
    })
}

/// Classify a model probability with the strict desktop thresholds.
///
/// Accepts a probability in `[0, 1]`. The result uses `LOW`/`MEDIUM`/`HIGH`.
#[uniffi::export]
pub fn classify_json(probability: f64) -> String {
    safe_json(|| match classify_probability(probability) {
        Ok(category) => json!({ "ok": true, "probability": probability, "category": category }),
        Err(message) => error_result(vec![error_item(
            "OUT_OF_RANGE",
            "model_probability",
            message,
        )]),
    })
}

/// Full evaluation: validate, attach the model probability, classify and
/// report SWBRA availability. `probability` is the ONNX positive-class value.
#[uniffi::export]
pub fn evaluate_json(payload_json: String, probability: Option<f64>) -> String {
    safe_json(|| match parse_json(&payload_json) {
        Some(payload) => evaluate_value(&payload, probability),
        None => invalid_json("payload"),
    })
}

/// SWBRA arithmetic from documented boolean flags.
///
/// Pass `null` or an empty string to obtain the `UNAVAILABLE` result that the
/// apps display. Raw biomarker values are intentionally not accepted.
#[uniffi::export]
pub fn swbra_json(flags_json: String) -> String {
    safe_json(|| {
        let trimmed = flags_json.trim();
        if trimmed.is_empty() || trimmed == "null" {
            return swbra_result(None);
        }
        match parse_json(trimmed) {
            Some(flags) => swbra_result(Some(&flags)),
            None => invalid_json("swbraFlags"),
        }
    })
}

/// Versions of the app, model, SWBRA spec and preprocessing spec.
#[uniffi::export]
pub fn versions_json() -> String {
    safe_json(|| versions_value(specs()))
}

/// The complete embedded specifications, for UI bounds and provenance.
#[uniffi::export]
pub fn spec_json() -> String {
    safe_json(|| {
        let specs = specs();
        json!({
            "preprocessing": specs.preprocessing,
            "swbra": specs.swbra,
            "version": specs.version,
        })
    })
}

// ---------------------------------------------------------------------------
// PyO3 bindings (built only with `--features python`, used by the desktop app)
// ---------------------------------------------------------------------------

#[cfg(feature = "python")]
mod python {
    use pyo3::prelude::*;

    // Thin shims with `#[pyfunction]`. They only forward to the core so that the
    // FFI surface stays one implementation; the JSON strings are the contract.
    #[pyfunction]
    #[pyo3(name = "prepare_json")]
    fn py_prepare_json(payload_json: String) -> String {
        crate::prepare_json(payload_json)
    }

    #[pyfunction]
    #[pyo3(name = "classify_json")]
    fn py_classify_json(probability: f64) -> String {
        crate::classify_json(probability)
    }

    #[pyfunction]
    #[pyo3(name = "evaluate_json")]
    fn py_evaluate_json(payload_json: String, probability: Option<f64>) -> String {
        crate::evaluate_json(payload_json, probability)
    }

    #[pyfunction]
    #[pyo3(name = "swbra_json")]
    fn py_swbra_json(flags_json: String) -> String {
        crate::swbra_json(flags_json)
    }

    #[pyfunction]
    #[pyo3(name = "versions_json")]
    fn py_versions_json() -> String {
        crate::versions_json()
    }

    #[pyfunction]
    #[pyo3(name = "spec_json")]
    fn py_spec_json() -> String {
        crate::spec_json()
    }

    /// Python module `suras_core`. Every function takes and returns JSON strings
    /// so the Python and Kotlin call sites exercise the same code path.
    #[pymodule]
    fn suras_core(module: &Bound<'_, PyModule>) -> PyResult<()> {
        module.add_function(wrap_pyfunction!(py_prepare_json, module)?)?;
        module.add_function(wrap_pyfunction!(py_classify_json, module)?)?;
        module.add_function(wrap_pyfunction!(py_evaluate_json, module)?)?;
        module.add_function(wrap_pyfunction!(py_swbra_json, module)?)?;
        module.add_function(wrap_pyfunction!(py_versions_json, module)?)?;
        module.add_function(wrap_pyfunction!(py_spec_json, module)?)?;
        module.add("__version__", env!("CARGO_PKG_VERSION"))?;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn embeds_parse() {
        let specs = specs();
        assert_eq!(feature_fields(specs).len(), 14);
    }

    #[test]
    fn threshold_boundaries_are_strict() {
        assert_eq!(classify_probability(0.3), Ok("LOW"));
        assert_eq!(classify_probability(0.300_000_000_000_000_04), Ok("MEDIUM"));
        assert_eq!(classify_probability(0.7), Ok("MEDIUM"));
        assert_eq!(classify_probability(0.700_000_000_000_000_1), Ok("HIGH"));
        assert!(classify_probability(f64::NAN).is_err());
        assert!(classify_probability(-0.1).is_err());
        assert!(classify_probability(1.1).is_err());
    }

    #[test]
    fn swbra_bands_are_inclusive() {
        assert_eq!(swbra_category(39), Some("LOW"));
        assert_eq!(swbra_category(40), Some("MODERATE"));
        assert_eq!(swbra_category(69), Some("MODERATE"));
        assert_eq!(swbra_category(70), Some("HIGH"));
        assert_eq!(swbra_category(-1), None);
        assert_eq!(swbra_category(91), None);
    }

    #[test]
    fn bad_json_is_reported_not_echoed() {
        let result = prepare_json("{not json".to_string());
        assert!(result.contains("INVALID_JSON"));
        assert!(!result.contains("not json"));
    }
}
