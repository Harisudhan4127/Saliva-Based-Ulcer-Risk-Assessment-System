//! Golden-vector conformance tests.
//!
//! `shared/golden_vectors.json` is generated from `ai/reference.py`, which is
//! the executable definition of the shipped desktop behaviour. Every platform
//! binding must reproduce these vectors; this test is the contract.

use serde_json::Value;
use suras_core::{
    classify_json, evaluate_json, prepare_json, spec_json, swbra_category, swbra_json,
    versions_json,
};

const GOLDEN: &str = include_str!("../../../shared/golden_vectors.json");

fn golden() -> Value {
    serde_json::from_str(GOLDEN).expect("shared/golden_vectors.json must be valid JSON")
}

fn parse(output: &str) -> Value {
    serde_json::from_str(output).expect("core output must be valid JSON")
}

fn assert_close(actual: &[Value], expected: &[Value]) {
    assert_eq!(actual.len(), expected.len(), "feature vector length");
    for (index, (actual, expected)) in actual.iter().zip(expected).enumerate() {
        let actual = actual.as_f64().expect("actual feature is numeric");
        let expected = expected.as_f64().expect("expected feature is numeric");
        assert!(
            (actual - expected).abs() <= 1e-12,
            "feature {index}: {actual} != {expected}"
        );
    }
}

fn strings(value: &Value) -> Vec<String> {
    value
        .as_array()
        .expect("array")
        .iter()
        .map(|item| item.as_str().expect("string").to_string())
        .collect()
}

#[test]
fn ml_threshold_vectors_match() {
    for vector in golden()["mlThresholdVectors"].as_array().unwrap() {
        let probability = vector["probability"].as_f64().unwrap();
        let expected = vector["expectedCategory"].as_str().unwrap();
        let result = parse(&classify_json(probability));
        assert_eq!(result["ok"], Value::Bool(true), "probability {probability}");
        assert_eq!(result["category"], expected, "probability {probability}");
        assert_eq!(result["probability"].as_f64().unwrap(), probability);
    }
}

#[test]
fn evaluation_vectors_match() {
    let expected_versions = &golden()["versions"];
    for vector in golden()["evaluationVectors"].as_array().unwrap() {
        let payload = vector["payload"].to_string();
        let probability = vector["probability"].as_f64().unwrap();
        let result = parse(&evaluate_json(payload, Some(probability)));
        let expected = &vector["expected"];
        let name = vector["name"].as_str().unwrap();

        assert_eq!(result["ok"], Value::Bool(true), "{name}: {result}");
        assert_close(
            result["features"].as_array().unwrap(),
            expected["features"].as_array().unwrap(),
        );

        let warning_codes: Vec<String> = result["warnings"]
            .as_array()
            .unwrap()
            .iter()
            .map(|warning| warning["code"].as_str().unwrap().to_string())
            .collect();
        assert_eq!(warning_codes, strings(&expected["warningCodes"]), "{name}");

        assert_eq!(result["mlCategory"], expected["mlCategory"], "{name}");
        assert_eq!(result["swbra"]["status"], expected["swbraStatus"], "{name}");
        assert_eq!(result["swbra"]["score"], expected["swbraScore"], "{name}");
        assert_eq!(
            result["swbra"]["category"], expected["swbraCategory"],
            "{name}"
        );

        for key in [
            "appVersion",
            "modelVersion",
            "swbraSpecVersion",
            "preprocessingVersion",
        ] {
            assert_eq!(
                result["versions"][key], expected["versions"][key],
                "{name}: {key}"
            );
            assert_eq!(
                expected_versions[key], expected["versions"][key],
                "{name}: golden version drift for {key}"
            );
        }
    }
}

#[test]
fn validation_vectors_match() {
    for vector in golden()["validationVectors"].as_array().unwrap() {
        let name = vector["name"].as_str().unwrap();
        let result = parse(&prepare_json(vector["payload"].to_string()));
        let expected = &vector["expected"];

        assert_eq!(result["ok"], expected["ok"], "{name}: {result}");

        let error_codes: Vec<String> = result
            .get("errors")
            .and_then(Value::as_array)
            .map(|errors| {
                errors
                    .iter()
                    .map(|error| error["code"].as_str().unwrap().to_string())
                    .collect()
            })
            .unwrap_or_default();
        assert_eq!(error_codes, strings(&expected["errorCodes"]), "{name}");

        let error_fields: Vec<String> = result
            .get("errors")
            .and_then(Value::as_array)
            .map(|errors| {
                errors
                    .iter()
                    .map(|error| error["field"].as_str().unwrap().to_string())
                    .collect()
            })
            .unwrap_or_default();
        assert_eq!(error_fields, strings(&expected["errorFields"]), "{name}");

        let warning_codes: Vec<String> = result
            .get("warnings")
            .and_then(Value::as_array)
            .map(|warnings| {
                warnings
                    .iter()
                    .map(|warning| warning["code"].as_str().unwrap().to_string())
                    .collect()
            })
            .unwrap_or_default();
        assert_eq!(warning_codes, strings(&expected["warningCodes"]), "{name}");

        if expected["ok"].as_bool() == Some(true) {
            assert_close(
                result["features"].as_array().unwrap(),
                expected["features"].as_array().unwrap(),
            );
        }
    }
}

#[test]
fn swbra_flag_vectors_match() {
    for vector in golden()["swbraFlagVectors"].as_array().unwrap() {
        let result = parse(&swbra_json(vector["flags"].to_string()));
        assert_eq!(
            result["status"],
            Value::String("DOCUMENTED_ARITHMETIC_ONLY".to_string())
        );
        assert_eq!(result["score"], vector["expectedScore"]);
        assert_eq!(result["category"], vector["expectedCategory"]);
        assert_eq!(result["maxScore"], Value::from(90));
    }
}

#[test]
fn swbra_band_boundaries_match() {
    for vector in golden()["swbraBandBoundaryVectors"].as_array().unwrap() {
        let score = vector["score"].as_i64().unwrap();
        let expected = vector["expectedCategory"].as_str().unwrap();
        assert_eq!(swbra_category(score), Some(expected), "score {score}");
    }
}

#[test]
fn swbra_is_unavailable_without_flags() {
    for input in ["", "null", "   "] {
        let result = parse(&swbra_json(input.to_string()));
        assert_eq!(result["status"], Value::String("UNAVAILABLE".to_string()));
        assert_eq!(result["score"], Value::Null);
        assert_eq!(result["category"], Value::Null);
    }
    let result = parse(&evaluate_json(
        golden()["evaluationVectors"][0]["payload"].to_string(),
        Some(0.5),
    ));
    assert_eq!(
        result["swbra"]["status"],
        Value::String("UNAVAILABLE".to_string())
    );
}

#[test]
fn evaluate_requires_probability() {
    let payload = golden()["evaluationVectors"][0]["payload"].to_string();
    let result = parse(&evaluate_json(payload, None));
    assert_eq!(result["ok"], Value::Bool(false));
    assert_eq!(result["errors"][0]["code"], "MISSING_FIELD");
    assert_eq!(result["errors"][0]["field"], "model_probability");
}

#[test]
fn evaluate_rejects_out_of_range_probability() {
    let payload = golden()["evaluationVectors"][0]["payload"].to_string();
    for probability in [-0.01, 1.01, f64::NAN] {
        let result = parse(&evaluate_json(payload.clone(), Some(probability)));
        assert_eq!(
            result["ok"],
            Value::Bool(false),
            "probability {probability}"
        );
        assert_eq!(result["errors"][0]["code"], "OUT_OF_RANGE");
    }
}

#[test]
fn invalid_json_is_reported_without_echo() {
    for output in [
        prepare_json("{\"Age\":".to_string()),
        evaluate_json("not json".to_string(), Some(0.5)),
        swbra_json("{} trailing".to_string()),
    ] {
        let result = parse(&output);
        assert_eq!(result["ok"], Value::Bool(false));
        assert_eq!(result["errors"][0]["code"], "INVALID_JSON");
        assert!(!output.contains("trailing"));
    }
}

#[test]
fn versions_and_spec_are_consistent() {
    let versions = parse(&versions_json());
    assert_eq!(versions["appVersion"], "2.1.0");
    assert_eq!(versions["modelVersion"], "1.0.0");

    let spec = parse(&spec_json());
    assert_eq!(
        spec["preprocessing"]["featureOrder"]
            .as_array()
            .unwrap()
            .len(),
        14
    );
    assert_eq!(
        spec["swbra"]["status"],
        "DOCUMENTED_ONLY_NOT_IMPLEMENTED_IN_SOURCE"
    );
    assert_eq!(
        spec["version"]["version"], versions["appVersion"],
        "version.json is the single source of truth"
    );
}
