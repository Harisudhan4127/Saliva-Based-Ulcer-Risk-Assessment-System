package com.harisudhan.suras.data.update

/** A GitHub release parsed from the public Releases API. */
data class ReleaseInfo(
    val version: String,
    val tagName: String,
    val notes: String,
    val htmlUrl: String,
    val apk: ReleaseAsset?,
)

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    /** Lower-case hex SHA-256 from the release asset digest, or null. */
    val sha256: String?,
)

sealed interface UpdateCheckResult {
    data object NotConfigured : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class Available(val release: ReleaseInfo) : UpdateCheckResult
    data class Failed(val message: String) : UpdateCheckResult
}

sealed interface UpdateDownloadResult {
    data class Verified(val file: java.io.File) : UpdateDownloadResult
    data class Failed(val reason: Reason) : UpdateDownloadResult

    enum class Reason {
        NO_DIGEST,
        CHECKSUM_MISMATCH,
        DOWNLOAD_FAILED,
        TOO_LARGE,
    }
}

class UpdateException(message: String, cause: Throwable? = null) : Exception(message, cause)
