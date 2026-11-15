package com.harisudhan.suras.data.repository

import androidx.annotation.WorkerThread
import com.harisudhan.suras.BuildConfig
import com.harisudhan.suras.data.local.PatientDao
import com.harisudhan.suras.data.local.PatientRecord
import com.harisudhan.suras.domain.RiskEngine
import com.harisudhan.suras.domain.SurasCoreService
import com.harisudhan.suras.domain.model.AnalysisFailure
import com.harisudhan.suras.domain.model.AnalysisResult
import com.harisudhan.suras.domain.model.CoreIssue
import com.harisudhan.suras.domain.model.PatientInput
import com.harisudhan.suras.domain.model.RiskCategory
import com.harisudhan.suras.domain.model.VersionInfo
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * The single write path for assessments. Validation, preprocessing and
 * classification all happen in the shared Rust core so Android and desktop
 * cannot drift. Inference runs on [Dispatchers.Default]; disk access happens on
 * Room's own executor.
 */
@Singleton
class PatientRepository @Inject constructor(
    private val dao: PatientDao,
    private val core: SurasCoreService,
    private val riskEngine: RiskEngine,
) {
    fun observeRecent(limit: Int = 3): Flow<List<PatientRecord>> = dao.observeRecent(limit)

    fun observeAll(): Flow<List<PatientRecord>> = dao.observeAll()

    fun observeById(patientId: String): Flow<PatientRecord?> = dao.observeById(patientId)

    fun observeCount(): Flow<Int> = dao.observeCount()

    fun search(query: String): Flow<List<PatientRecord>> {
        val trimmed = query.trim()
        return if (trimmed.isEmpty()) dao.observeAll() else dao.observeSearch(likePattern(trimmed))
    }

    suspend fun findById(patientId: String): PatientRecord? = dao.findById(patientId)

    suspend fun all(): List<PatientRecord> = dao.getAll()

    suspend fun delete(patientId: String) {
        dao.deleteById(patientId)
    }

    suspend fun clearAll() {
        dao.deleteAll()
    }

    /**
     * Run inference using the same encoding, feature order and thresholds as the
     * desktop app (shared/preprocessing.json): encode the 14 inputs, run the
     * on-device ONNX model, classify with strict thresholds (>0.7 HIGH, >0.3
     * MEDIUM, else LOW) and persist the assessment.
     *
     * The shared core is still consulted when available for parity/metadata, but
     * prediction and classification do not depend on it so the app remains
     * functional even if the native core binding fails on a device.
     */
    suspend fun analyzeAndSave(input: PatientInput): AnalysisResult = withContext(Dispatchers.Default) {
        val features = desktopFeatures(input)
            ?: return@withContext AnalysisResult.Invalid(
                listOf(
                    CoreIssue(
                        code = "INVALID_INPUT",
                        field = null,
                        message = "Please check that all fields contain valid values.",
                    ),
                ),
            )

        val probability = riskEngine.predict(features)
            ?: return@withContext AnalysisResult.Failed(AnalysisFailure.MODEL_UNAVAILABLE)
        if (!probability.isFinite() || probability < 0.0 || probability > 1.0) {
            return@withContext AnalysisResult.Failed(AnalysisFailure.CORE_ERROR)
        }

        val category = desktopCategory(probability)

        // Best-effort core evaluation (versions/SWBRA status). Do not block a
        // valid desktop-style prediction if the core binding is unavailable.
        val payloadJson = input.toPayloadJson()
        val evaluation = runCatching { core.evaluate(payloadJson, probability) }.getOrNull()
        val versions = evaluation?.versions ?: core.versions().let {
            VersionInfo(
                appVersion = it.appVersion.ifBlank { BuildConfig.VERSION_NAME },
                modelVersion = it.modelVersion.ifBlank { BuildConfig.MODEL_VERSION },
                swbraSpecVersion = it.swbraSpecVersion.ifBlank { BuildConfig.SWBRA_SPEC_VERSION },
                preprocessingVersion = it.preprocessingVersion.ifBlank { BuildConfig.PREPROCESSING_VERSION },
            )
        }
        val swbraStatus = evaluation?.swbraStatus ?: "UNAVAILABLE"
        val warnings = runCatching { core.prepare(payloadJson) }
            .getOrNull()?.warnings ?: emptyList()

        val record = try {
            buildRecord(
                input = input,
                probability = probability,
                category = category,
                swbraStatus = swbraStatus,
                versions = versions,
                warnings = warnings,
            )
        } catch (_: IllegalArgumentException) {
            return@withContext AnalysisResult.Failed(AnalysisFailure.CORE_ERROR)
        }

        try {
            dao.insert(record)
        } catch (_: Throwable) {
            return@withContext AnalysisResult.Failed(AnalysisFailure.CORE_ERROR)
        }
        AnalysisResult.Success(record, warnings)
    }

    /**
     * Desktop-compatible encoding (apps/desktop/main.py + shared/preprocessing.json).
     * Feature order: Age, Gender, Spicy_Food, Tobacco, Alcohol, Sleep_Hours, Stress,
     * Skip_Meals, Soft_Drinks, Empty_Stomach_Pain, Day1_pH_Before, Day1_pH_After,
     * Day2_pH_Before, Day2_pH_After.
     */
    private fun desktopFeatures(input: PatientInput): List<Double>? {
        val age = input.age.trim().toDoubleOrNull() ?: return null
        val sleep = input.sleepHours.trim().toDoubleOrNull() ?: return null
        val stress = input.stress.trim().toDoubleOrNull() ?: return null
        val ph1b = input.day1PhBefore.trim().toDoubleOrNull() ?: return null
        val ph1a = input.day1PhAfter.trim().toDoubleOrNull() ?: return null
        val ph2b = input.day2PhBefore.trim().toDoubleOrNull() ?: return null
        val ph2a = input.day2PhAfter.trim().toDoubleOrNull() ?: return null
        if (!age.isFinite() || !sleep.isFinite() || !stress.isFinite() ||
            !ph1b.isFinite() || !ph1a.isFinite() || !ph2b.isFinite() || !ph2a.isFinite()
        ) return null
        val gender = when (input.gender) {
            PatientInput.MALE -> 1.0
            PatientInput.FEMALE -> 0.0
            else -> return null
        }
        fun yn(value: String): Double? = when (value) {
            PatientInput.YES -> 1.0
            PatientInput.NO -> 0.0
            else -> null
        }
        val spicy = yn(input.spicyFood) ?: return null
        val tobacco = yn(input.tobacco) ?: return null
        val alcohol = yn(input.alcohol) ?: return null
        val skipMeals = yn(input.skipMeals) ?: return null
        val softDrinks = yn(input.softDrinks) ?: return null
        val emptyStomach = yn(input.emptyStomachPain) ?: return null
        return listOf(
            age, gender, spicy, tobacco, alcohol, sleep, stress,
            skipMeals, softDrinks, emptyStomach, ph1b, ph1a, ph2b, ph2a,
        )
    }

    /** Strict thresholds, same as apps/desktop/main.py: prob > 0.7 HIGH, > 0.3 MEDIUM, else LOW. */
    private fun desktopCategory(probability: Double): RiskCategory = when {
        probability > 0.7 -> RiskCategory.HIGH
        probability > 0.3 -> RiskCategory.MEDIUM
        else -> RiskCategory.LOW
    }

    @WorkerThread
    private fun buildRecord(
        input: PatientInput,
        probability: Double,
        category: RiskCategory,
        swbraStatus: String?,
        versions: com.harisudhan.suras.domain.model.VersionInfo,
        warnings: List<CoreIssue>,
    ): PatientRecord {
        // Values were already validated by the core; these conversions only
        // re-shape them for storage and cannot introduce a new accepted range.
        val age = input.age.trim().toDoubleOrNull()
        val sleep = input.sleepHours.trim().toDoubleOrNull()
        val stress = input.stress.trim().toDoubleOrNull()
        val ph1Before = input.day1PhBefore.trim().toDoubleOrNull()
        val ph1After = input.day1PhAfter.trim().toDoubleOrNull()
        val ph2Before = input.day2PhBefore.trim().toDoubleOrNull()
        val ph2After = input.day2PhAfter.trim().toDoubleOrNull()
        require(
            age != null && sleep != null && stress != null &&
                ph1Before != null && ph1After != null &&
                ph2Before != null && ph2After != null,
        )

        return PatientRecord(
            patientId = input.patientCode,
            name = input.name.trim(),
            age = age.toInt(),
            gender = input.gender,
            spicyFood = PatientInput.isYes(input.spicyFood),
            tobacco = PatientInput.isYes(input.tobacco),
            alcohol = PatientInput.isYes(input.alcohol),
            sleepHours = sleep,
            stress = stress.toInt(),
            skipMeals = PatientInput.isYes(input.skipMeals),
            softDrinks = PatientInput.isYes(input.softDrinks),
            emptyStomachPain = PatientInput.isYes(input.emptyStomachPain),
            day1PhBefore = ph1Before,
            day1PhAfter = ph1After,
            day2PhBefore = ph2Before,
            day2PhAfter = ph2After,
            probability = probability,
            riskLevel = category.name,
            swbraStatus = swbraStatus ?: "UNAVAILABLE",
            swbraScore = null,
            swbraCategory = null,
            appVersion = versions.appVersion,
            modelVersion = versions.modelVersion,
            preprocessingVersion = versions.preprocessingVersion,
            swbraSpecVersion = versions.swbraSpecVersion,
            warningsJson = encodeWarnings(warnings),
            createdAt = System.currentTimeMillis(),
        )
    }

    private fun likePattern(query: String): String {
        val escaped = query
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return "%$escaped%"
    }

    companion object {
        const val FEATURE_COUNT = 14

        fun encodeWarnings(warnings: List<CoreIssue>): String {
            val array = JSONArray()
            warnings.forEach { warning ->
                array.put(
                    JSONObject().apply {
                        put("code", warning.code)
                        put("field", warning.field ?: "")
                        put("message", warning.message)
                    },
                )
            }
            return array.toString()
        }

        fun decodeWarnings(json: String): List<CoreIssue> = try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                CoreIssue(
                    code = item.optString("code"),
                    field = item.optString("field").ifBlank { null },
                    message = item.optString("message"),
                )
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
