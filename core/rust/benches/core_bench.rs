//! Micro-benchmarks for the shared core.
//!
//! The core is intentionally small; these numbers document the cost of the
//! JSON boundary so that Android callers can decide whether to keep sorting or
//! formatting outside the core.

use std::hint::black_box;

use criterion::{criterion_group, criterion_main, Criterion};
use serde_json::json;
use suras_core::{classify_json, evaluate_json, prepare_json, swbra_json};

fn payload() -> String {
    json!({
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
    })
    .to_string()
}

fn benches(criterion: &mut Criterion) {
    let payload = payload();

    criterion.bench_function("prepare_json", |bencher| {
        bencher.iter(|| prepare_json(black_box(payload.clone())))
    });
    criterion.bench_function("classify_json", |bencher| {
        bencher.iter(|| classify_json(black_box(0.55)))
    });
    criterion.bench_function("evaluate_json", |bencher| {
        bencher.iter(|| evaluate_json(black_box(payload.clone()), Some(black_box(0.55))))
    });
    criterion.bench_function("swbra_json", |bencher| {
        bencher.iter(|| {
            swbra_json(black_box(
                r#"{"il6_high":true,"crp_high":false,"iga_low":true,"ph_abnormal":false}"#
                    .to_string(),
            ))
        })
    });
}

criterion_group!(benches_group, benches);
criterion_main!(benches_group);
