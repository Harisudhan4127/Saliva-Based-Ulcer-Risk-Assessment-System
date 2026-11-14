package com.harisudhan.suras.data.update

import com.harisudhan.suras.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Reads the latest public GitHub release.
 *
 * Only `https://api.github.com` is contacted. The repository slug comes from a
 * build property, is validated against a strict character set and is never
 * interpolated into anything other than a path segment.
 */
@Singleton
class GitHubReleaseClient @Inject constructor() {

    suspend fun latest(repo: String = BuildConfig.UPDATE_REPO): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            if (!isConfigured(repo)) return@withContext UpdateCheckResult.NotConfigured
            try {
                val json = getJson("https://api.github.com/repos/$repo/releases/latest")
                val parsed = parseRelease(json)
                if (SemVer.isNewer(parsed.version, INSTALLED_VERSION)) {
                    UpdateCheckResult.Available(parsed)
                } else {
                    UpdateCheckResult.UpToDate
                }
            } catch (error: UpdateException) {
                UpdateCheckResult.Failed(error.message ?: "update check failed")
            } catch (error: IOException) {
                UpdateCheckResult.Failed("network error")
            } catch (error: Throwable) {
                UpdateCheckResult.Failed("update check failed")
            }
        }

    fun isConfigured(repo: String): Boolean =
        repo != TODO_REPO && REPO_PATTERN.matches(repo)

    private fun getJson(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "SURAS-Android/${BuildConfig.VERSION_NAME}")
        }
        try {
            if (connection.url.protocol != "https") {
                throw UpdateException("release API must use HTTPS")
            }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw UpdateException("release API returned HTTP $code")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            if (body.length > MAX_RESPONSE_CHARS) {
                throw UpdateException("release API response is too large")
            }
            return body
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseRelease(body: String): ReleaseInfo {
        val json = JSONObject(body)
        val tag = json.optString("tag_name").trim()
        if (tag.isEmpty()) throw UpdateException("release has no tag")
        val version = tag.removePrefix("v").removePrefix("V")
        if (!VERSION_PATTERN.matches(version)) {
            throw UpdateException("release tag is not a SemVer version")
        }

        val assets = json.optJSONArray("assets")
        var best: ReleaseAsset? = null
        if (assets != null) {
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                val name = asset.optString("name")
                val url = asset.optString("browser_download_url")
                if (!name.endsWith(".apk", ignoreCase = true)) continue
                if (!url.startsWith("https://")) continue
                val digest = asset.optString("digest").trim()
                val sha = if (digest.startsWith("sha256:")) {
                    digest.removePrefix("sha256:").lowercase().takeIf { SHA256_PATTERN.matches(it) }
                } else {
                    null
                }
                val candidate = ReleaseAsset(name = name, downloadUrl = url, sha256 = sha)
                // Prefer the universal release APK over anything else.
                if (best == null || name.contains("universal", ignoreCase = true)) {
                    best = candidate
                }
            }
        }

        return ReleaseInfo(
            version = version,
            tagName = tag,
            notes = json.optString("body").take(MAX_NOTES_CHARS),
            htmlUrl = json.optString("html_url").takeIf { it.startsWith("https://") } ?: "",
            apk = best,
        )
    }

    private companion object {
        const val TODO_REPO = "TODO_FILL"
        const val TIMEOUT_MS = 15_000
        const val MAX_RESPONSE_CHARS = 1_000_000
        const val MAX_NOTES_CHARS = 4_000
        val REPO_PATTERN = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
        val VERSION_PATTERN = Regex("^[0-9]+\\.[0-9]+\\.[0-9]+([-+].*)?$")
        val SHA256_PATTERN = Regex("^[a-f0-9]{64}$")
        val INSTALLED_VERSION: String = BuildConfig.VERSION_NAME.substringBefore('-')
    }
}
