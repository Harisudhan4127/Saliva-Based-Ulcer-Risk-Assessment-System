package com.harisudhan.suras.data.update

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Downloads an APK to the app cache and verifies its SHA-256 before exposing it.
 *
 * The download is refused when the release carries no digest. There is no
 * fallback to "trust the TLS connection alone": a release without a digest is a
 * configuration error, not a reason to weaken verification.
 */
@Singleton
class UpdateDownloader @Inject constructor(@ApplicationContext context: Context) {

    private val appContext = context.applicationContext

    suspend fun download(
        asset: ReleaseAsset,
        onProgress: (Int) -> Unit = {},
    ): UpdateDownloadResult = withContext(Dispatchers.IO) {
        val expected = asset.sha256 ?: return@withContext UpdateDownloadResult.Failed(
            UpdateDownloadResult.Reason.NO_DIGEST,
        )
        if (!asset.downloadUrl.startsWith("https://")) {
            return@withContext UpdateDownloadResult.Failed(UpdateDownloadResult.Reason.DOWNLOAD_FAILED)
        }

        val directory = File(appContext.cacheDir, "updates").apply { mkdirs() }
        directory.listFiles()?.forEach { stale -> stale.delete() }
        val safeName = asset.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val partial = File(directory, "$safeName.part")
        val target = File(directory, safeName)

        val connection = try {
            (URL(asset.downloadUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/octet-stream")
                setRequestProperty("User-Agent", "SURAS-Android")
            }
        } catch (error: IOException) {
            return@withContext UpdateDownloadResult.Failed(
                UpdateDownloadResult.Reason.DOWNLOAD_FAILED,
            )
        }

        try {
            if (connection.url.protocol != "https") {
                return@withContext UpdateDownloadResult.Failed(
                    UpdateDownloadResult.Reason.DOWNLOAD_FAILED,
                )
            }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                return@withContext UpdateDownloadResult.Failed(
                    UpdateDownloadResult.Reason.DOWNLOAD_FAILED,
                )
            }

            val total = connection.contentLengthLong
            if (total > MAX_BYTES) {
                return@withContext UpdateDownloadResult.Failed(
                    UpdateDownloadResult.Reason.TOO_LARGE,
                )
            }

            val digest = MessageDigest.getInstance("SHA-256")
            var written = 0L
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        written += read
                        if (written > MAX_BYTES) {
                            partial.delete()
                            return@withContext UpdateDownloadResult.Failed(
                                UpdateDownloadResult.Reason.TOO_LARGE,
                            )
                        }
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        if (total > 0) {
                            onProgress(((written * 100) / total).toInt().coerceIn(0, 100))
                        }
                    }
                }
            }
            onProgress(100)

            val actual = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
            if (!actual.equals(expected, ignoreCase = true)) {
                partial.delete()
                return@withContext UpdateDownloadResult.Failed(
                    UpdateDownloadResult.Reason.CHECKSUM_MISMATCH,
                )
            }

            if (target.exists()) target.delete()
            if (!partial.renameTo(target)) {
                partial.delete()
                return@withContext UpdateDownloadResult.Failed(
                    UpdateDownloadResult.Reason.DOWNLOAD_FAILED,
                )
            }
            UpdateDownloadResult.Verified(target)
        } catch (error: IOException) {
            partial.delete()
            UpdateDownloadResult.Failed(UpdateDownloadResult.Reason.DOWNLOAD_FAILED)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 20_000
        const val READ_TIMEOUT_MS = 60_000
        const val BUFFER_BYTES = 64 * 1024
        const val MAX_BYTES = 250L * 1024 * 1024
    }
}
