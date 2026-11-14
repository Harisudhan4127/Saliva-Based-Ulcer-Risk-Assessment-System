package com.harisudhan.suras.data.repository

import androidx.annotation.WorkerThread
import com.harisudhan.suras.data.local.PatientDao
import com.harisudhan.suras.data.local.PatientRecord
import com.harisudhan.suras.domain.RiskEngine
import com.harisudhan.suras.domain.SurasCoreService
import com.harisudhan.suras.domain.model.AnalysisFailure
import com.harisudhan.suras.domain.model.AnalysisResult
import com.harisudhan.suras.domain.model.CoreIssue
import com.harisudhan.suras.domain.model.PatientInput
import com.harisudhan.suras.domain.model.RiskCategory
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
     * Validate the payload with the core, run ONNX inference, re-validate the
     * probability with the core and persist the result. No step silently
     * substitutes a default.
     */
    suspend fun analyzeAndSave(input: PatientInput): AnalysisResult = withContext(Dispatchers.Default) {
        val payloadJson = input.toPayloadJson()
        val prepared = core.prepare(payloadJson)
        if (!prepared.ok) {
            return@withContext AnalysisResult.Invalid(prepared.errors)
        }
        if (prepared.features.size != FEATURE_COUNT) {
            return@withContext AnalysisResult.Failed(AnalysisFailure.CORE_ERROR)
        }

        val probability = riskEngine.predict(prepared.features)
            ?: return@withContext AnalysisResult.Failed(AnalysisFailure.MODEL_UNAVAILABLE)

        val evaluation = core.evaluate(payloadJson, probability)
        if (!evaluation.ok) {
            return@withContext if (evaluation.errors.isNotEmpty()) {
                AnalysisResult.Invalid(evaluation.errors)
            } else {
                AnalysisResult.Failed(AnalysisFailure.CORE_ERROR)
            }
        }
        val category = evaluation.category
            ?: return@withContext AnalysisResult.Failed(AnalysisFailure.CORE_ERROR)
        val versions = evaluation.versions
            ?: return@withContext AnalysisResult.Failed(AnalysisFailure.CORE_ERROR)
        val validatedProbability = evaluation.probability
            ?: return@withContext AnalysisResult.Failed(AnalysisFailure.CORE_ERROR)

        val record = try {
            buildRecord(
                input = input,
                probability = validatedProbability,
                category = category,
                swbraStatus = evaluation.swbraStatus,
                versions = versions,
                warnings = prepared.warnings,
            )
        } catch (error: IllegalArgumentException) {
            return@withContext AnalysisResult.Failed(AnalysisFailure.CORE_ERROR)
        }

        try {
            dao.insert(record)
        } catch (error: Throwable) {
            return@withContext AnalysisResult.Failed(AnalysisFailure.CORE_ERROR)
        }
        AnalysisResult.Success(record, prepared.warnings)
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
