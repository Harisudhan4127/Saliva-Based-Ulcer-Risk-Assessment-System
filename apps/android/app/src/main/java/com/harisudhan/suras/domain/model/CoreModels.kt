package com.harisudhan.suras.domain.model

/** A typed validation issue from the shared Rust core. */
data class CoreIssue(
    val code: String,
    val field: String?,
    val message: String,
)

data class VersionInfo(
    val appVersion: String,
    val modelVersion: String,
    val swbraSpecVersion: String,
    val preprocessingVersion: String,
)

data class PrepareOutcome(
    val ok: Boolean,
    val features: List<Double>,
    val warnings: List<CoreIssue>,
    val errors: List<CoreIssue>,
    val versions: VersionInfo?,
)

data class EvaluationOutcome(
    val ok: Boolean,
    val probability: Double?,
    val category: RiskCategory?,
    val swbraStatus: String?,
    val swbraScore: Int?,
    val swbraCategory: String?,
    val versions: VersionInfo?,
    val errors: List<CoreIssue>,
)

enum class AnalysisFailure {
    MODEL_UNAVAILABLE,
    CORE_ERROR,
}

sealed interface AnalysisResult {
    data class Success(
        val record: com.harisudhan.suras.data.local.PatientRecord,
        val warnings: List<CoreIssue>,
    ) : AnalysisResult

    data class Invalid(val errors: List<CoreIssue>) : AnalysisResult

    data class Failed(val reason: AnalysisFailure) : AnalysisResult
}
