package com.harisudhan.suras.domain

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
import java.nio.FloatBuffer
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Runs the shared ONNX model entirely on-device.
 *
 * The model asset is verified against `model/SHA256SUMS` before the session is
 * created. Verification is a tamper check, not a signature: the checksum ships
 * inside the signed APK, so an attacker who can rewrite the APK can rewrite the
 * checksum too. The real trust anchor for distribution is APK signing.
 */
@Singleton
class RiskEngine @Inject constructor(@ApplicationContext context: Context) {

    private val appContext = context.applicationContext
    private val initMutex = Mutex()

    @Volatile
    private var session: OrtSession? = null

    @Volatile
    private var environment: OrtEnvironment? = null

    /**
     * Returns the positive-class probability for [features], or `null` when the
     * model is missing, corrupt or fails to run. Never throws into the UI.
     */
    suspend fun predict(features: List<Double>): Double? = withContext(Dispatchers.Default) {
        if (features.size != FEATURE_COUNT) return@withContext null
        val active = ensureSession() ?: return@withContext null
        val env = environment ?: return@withContext null
        val input = FloatArray(FEATURE_COUNT) { index -> features[index].toFloat() }
        try {
            OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(input),
                longArrayOf(1, FEATURE_COUNT.toLong()),
            ).use { tensor ->
                active.run(mapOf(INPUT_NAME to tensor)).use { result ->
                    val output = result.get(OUTPUT_NAME).orElse(null) as? OnnxTensor
                        ?: return@withContext null
                    val buffer = output.floatBuffer
                    if (buffer.capacity() < 2) return@withContext null
                    val probability = buffer.get(POSITIVE_CLASS_INDEX).toDouble()
                    if (probability.isFinite() && probability in 0.0..1.0) probability else null
                }
            }
        } catch (error: Throwable) {
            // Never log feature values or payloads.
            Log.e(TAG, "ONNX inference failed (${error.javaClass.simpleName})")
            null
        }
    }

    private suspend fun ensureSession(): OrtSession? = initMutex.withLock {
        session?.let { return@withLock it }
        try {
            val modelBytes = readVerifiedModel()
            val env = OrtEnvironment.getEnvironment()
            val options = OrtSession.SessionOptions().apply {
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                setIntraOpNumThreads(1)
            }
            val created = env.createSession(modelBytes, options)
            environment = env
            session = created
            created
        } catch (error: Throwable) {
            Log.e(TAG, "Model load failed (${error.javaClass.simpleName})")
            null
        }
    }

    private fun readVerifiedModel(): ByteArray {
        val model = appContext.assets.open(MODEL_ASSET).use { it.readBytes() }
        val expected = expectedDigest()
        val actual = MessageDigest.getInstance("SHA-256").digest(model).toHex()
        if (!actual.equals(expected, ignoreCase = true)) {
            throw SecurityException("Model checksum mismatch")
        }
        return model
    }

    private fun expectedDigest(): String {
        val sums = try {
            appContext.assets.open(SUMS_ASSET).bufferedReader().use { it.readText() }
        } catch (error: FileNotFoundException) {
            throw SecurityException("Model checksum file is missing", error)
        }
        val line = sums.lineSequence()
            .map(String::trim)
            .firstOrNull { it.endsWith(MODEL_FILE_NAME) }
            ?: throw SecurityException("No checksum entry for $MODEL_FILE_NAME")
        return line.substringBefore(' ').trim()
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val TAG = "RiskEngine"
        const val MODEL_ASSET = "model/model.onnx"
        const val SUMS_ASSET = "model/SHA256SUMS"
        const val MODEL_FILE_NAME = "model.onnx"
        const val INPUT_NAME = "input"
        const val OUTPUT_NAME = "probabilities"
        const val FEATURE_COUNT = 14
        const val POSITIVE_CLASS_INDEX = 1
    }
}
