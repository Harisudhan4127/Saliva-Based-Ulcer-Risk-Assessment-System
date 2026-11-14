package com.harisudhan.suras.domain

import com.harisudhan.suras.BuildConfig
import com.harisudhan.suras.core.classifyJson
import com.harisudhan.suras.core.evaluateJson
import com.harisudhan.suras.core.prepareJson
import com.harisudhan.suras.core.swbraJson
import com.harisudhan.suras.core.versionsJson
import com.harisudhan.suras.domain.model.CoreIssue
import com.harisudhan.suras.domain.model.EvaluationOutcome
import com.harisudhan.suras.domain.model.PrepareOutcome
import com.harisudhan.suras.domain.model.RiskCategory
import com.harisudhan.suras.domain.model.VersionInfo
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

/**
 * Thin typed facade over the UniFFI-generated JSON API.
 *
 * Every call is wrapped so a missing native library or an unexpected panic
 * becomes a typed error instead of an app crash. Payloads are never logged.
 */
@Singleton
class SurasCoreService @Inject constructor() {

    fun prepare(payloadJson: String): PrepareOutcome = try {
        parsePrepare(JSONObject(prepareJson(payloadJson)))
    } catch (_: Throwable) {
        PrepareOutcome(
            ok = false,
            features = emptyList(),
            warnings = emptyList(),
            errors = listOf(internalError()),
            versions = null,
        )
    }

    fun evaluate(payloadJson: String, probability: Double): EvaluationOutcome = try {
        parseEvaluation(JSONObject(evaluateJson(payloadJson, probability)))
    } catch (_: Throwable) {
        EvaluationOutcome(
            ok = false,
            probability = null,
            category = null,
            swbraStatus = null,
            swbraScore = null,
            swbraCategory = null,
            versions = null,
            errors = listOf(internalError()),
        )
    }

    fun classify(probability: Double): RiskCategory? = try {
        val result = JSONObject(classifyJson(probability))
        if (result.optBoolean("ok", false)) {
            RiskCategory.fromCore(result.optString("category").ifBlank { null })
        } else {
            null
        }
    } catch (_: Throwable) {
        null
    }

    fun swbra(flagsJson: String = "null"): SwbraOutcome = try {
        val result = JSONObject(swbraJson(flagsJson))
        SwbraOutcome(
            status = result.optString("status").ifBlank { null },
            score = if (result.isNull("score")) null else result.optInt("score"),
            category = result.optString("category").ifBlank { null },
        )
    } catch (_: Throwable) {
        SwbraOutcome(status = "UNAVAILABLE", score = null, category = null)
    }

    fun versions(): VersionInfo = try {
        parseVersions(JSONObject(versionsJson()))
    } catch (_: Throwable) {
        VersionInfo(
            appVersion = BuildConfig.VERSION_NAME,
            modelVersion = BuildConfig.MODEL_VERSION,
            swbraSpecVersion = BuildConfig.SWBRA_SPEC_VERSION,
            preprocessingVersion = BuildConfig.PREPROCESSING_VERSION,
        )
    }

    private fun parsePrepare(json: JSONObject): PrepareOutcome = PrepareOutcome(
        ok = json.optBoolean("ok", false),
        features = json.optJSONArray("features").toDoubleList(),
        warnings = json.optJSONArray("warnings").toIssues(),
        errors = json.optJSONArray("errors").toIssues(),
        versions = json.optJSONObject("versions")?.let(::parseVersions),
    )

    private fun parseEvaluation(json: JSONObject): EvaluationOutcome {
        val swbra = json.optJSONObject("swbra")
        return EvaluationOutcome(
            ok = json.optBoolean("ok", false),
            probability = if (json.has("probability") && !json.isNull("probability")) {
                json.optDouble("probability")
            } else {
                null
            },
            category = RiskCategory.fromCore(json.optString("mlCategory").ifBlank { null }),
            swbraStatus = swbra?.optString("status")?.ifBlank { null },
            swbraScore = swbra?.let { if (it.isNull("score")) null else it.optInt("score") },
            swbraCategory = swbra?.optString("category")?.ifBlank { null },
            versions = json.optJSONObject("versions")?.let(::parseVersions),
            errors = json.optJSONArray("errors").toIssues(),
        )
    }

    private fun parseVersions(json: JSONObject) = VersionInfo(
        appVersion = json.optString("appVersion", BuildConfig.VERSION_NAME),
        modelVersion = json.optString("modelVersion", BuildConfig.MODEL_VERSION),
        swbraSpecVersion = json.optString("swbraSpecVersion", BuildConfig.SWBRA_SPEC_VERSION),
        preprocessingVersion = json.optString(
            "preprocessingVersion",
            BuildConfig.PREPROCESSING_VERSION,
        ),
    )

    private fun internalError() = CoreIssue(
        code = "INTERNAL_ERROR",
        field = "core",
        message = "internal error in the shared core",
    )

    data class SwbraOutcome(
        val status: String?,
        val score: Int?,
        val category: String?,
    )
}

internal fun JSONArray?.toDoubleList(): List<Double> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        if (isNull(index)) null else optDouble(index)
    }
}

internal fun JSONArray?.toIssues(): List<CoreIssue> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        val item = optJSONObject(index) ?: return@mapNotNull null
        CoreIssue(
            code = item.optString("code"),
            field = item.optString("field").ifBlank { null },
            message = item.optString("message"),
        )
    }
}
