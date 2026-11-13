//! Property tests: the core must never panic, must be deterministic, and must
//! accept every payload inside the documented sanity bounds.

use proptest::prelude::*;
use serde_json::{json, Value};
use suras_core::{classify_json, evaluate_json, prepare_json, swbra_json};

fn parse(output: &str) -> Value {
    serde_json::from_str(output).expect("core output must always be valid JSON")
}

/// Build a payload from 14 raw integers, mapping each into the documented
/// sanity bounds so that generated cases exercise valid and boundary inputs.
fn payload_strategy() -> impl Strategy<Value = Value> {
    prop::collection::vec(any::<u64>(), 14).prop_map(|raw| {
        let gender = if raw[1] % 2 == 0 { "Male" } else { "Female" };
        let yes_no = |value: u64| if value % 2 == 0 { "Yes" } else { "No" };
        json!({
            "Age": (raw[0] % 120_001) as f64 / 1000.0,
            "Gender": gender,
            "Spicy_Food": yes_no(raw[2]),
            "Tobacco": yes_no(raw[3]),
            "Alcohol": yes_no(raw[4]),
            "Sleep_Hours": (raw[5] % 24_001) as f64 / 1000.0,
            "Stress": 1 + raw[6] % 10,
            "Skip_Meals": yes_no(raw[7]),
            "Soft_Drinks": yes_no(raw[8]),
            "Empty_Stomach_Pain": yes_no(raw[9]),
            "Day1_pH_Before": (raw[10] % 14_001) as f64 / 1000.0,
            "Day1_pH_After": (raw[11] % 14_001) as f64 / 1000.0,
            "Day2_pH_Before": (raw[12] % 14_001) as f64 / 1000.0,
            "Day2_pH_After": (raw[13] % 14_001) as f64 / 1000.0,
        })
    })
}

proptest! {
    #![proptest_config(ProptestConfig::with_cases(256))]

    #[test]
    fn valid_payloads_prepare_and_evaluate(
        payload in payload_strategy(),
        probability in 0.0f64..=1.0,
    ) {
        let prepared = parse(&prepare_json(payload.to_string()));
        prop_assert_eq!(prepared["ok"].as_bool(), Some(true));
        let features = prepared["features"].as_array().unwrap();
        prop_assert_eq!(features.len(), 14);
        for feature in features {
            prop_assert!(feature.as_f64().unwrap().is_finite());
        }

        let evaluated = parse(&evaluate_json(payload.to_string(), Some(probability)));
        prop_assert_eq!(evaluated["ok"].as_bool(), Some(true));
        let category = evaluated["mlCategory"].as_str().unwrap();
        prop_assert!(["LOW", "MEDIUM", "HIGH"].contains(&category));
        prop_assert_eq!(
            evaluated["swbra"]["status"].as_str(),
            Some("UNAVAILABLE"),
            "raw SWBRA is not computable from desktop inputs"
        );
    }

    #[test]
    fn classification_accepts_only_finite_unit_interval(value in any::<f64>()) {
        let result = parse(&classify_json(value));
        let in_range = value.is_finite() && (0.0..=1.0).contains(&value);
        prop_assert_eq!(result["ok"].as_bool(), Some(in_range));
        if in_range {
            let category = result["category"].as_str().unwrap();
            prop_assert!(["LOW", "MEDIUM", "HIGH"].contains(&category));
            let expected = if value > 0.7 {
                "HIGH"
            } else if value > 0.3 {
                "MEDIUM"
            } else {
                "LOW"
            };
            prop_assert_eq!(category, expected);
        } else {
            prop_assert_eq!(result["errors"][0]["code"].as_str(), Some("OUT_OF_RANGE"));
        }
    }

    #[test]
    fn arbitrary_text_never_panics(text in prop::collection::vec(any::<char>(), 0..128)) {
        let text: String = text.into_iter().collect();
        parse(&prepare_json(text.clone()));
        parse(&evaluate_json(text.clone(), Some(0.5)));
        parse(&evaluate_json(text.clone(), None));
        parse(&swbra_json(text));
    }

    #[test]
    fn output_is_deterministic(payload in payload_strategy(), probability in 0.0f64..=1.0) {
        prop_assert_eq!(
            prepare_json(payload.to_string()),
            prepare_json(payload.to_string())
        );
        prop_assert_eq!(
            evaluate_json(payload.to_string(), Some(probability)),
            evaluate_json(payload.to_string(), Some(probability))
        );
    }
}
