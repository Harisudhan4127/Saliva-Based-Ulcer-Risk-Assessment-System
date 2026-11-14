package com.harisudhan.suras.data.update

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.harisudhan.suras.data.settings.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Background update check. It only reads the public release API: no patient
 * data, device identifiers or assessment counts leave the device. A newer
 * release produces a notification; nothing is downloaded or installed here.
 */
@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val client: GitHubReleaseClient,
    private val settings: SettingsRepository,
    private val notifications: UpdateNotifications,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = try {
        val current = settings.settings.first()
        if (!current.autoUpdateCheck) return Result.success()

        when (val result = client.latest()) {
            is UpdateCheckResult.Available -> {
                settings.setLastUpdateCheckMillis(System.currentTimeMillis())
                notifications.notifyUpdateAvailable(result.release.version)
            }
            is UpdateCheckResult.UpToDate -> {
                settings.setLastUpdateCheckMillis(System.currentTimeMillis())
            }
            UpdateCheckResult.NotConfigured -> Unit
            is UpdateCheckResult.Failed -> Unit
        }
        Result.success()
    } catch (_: Throwable) {
        Result.success()
    }

    companion object {
        const val WORK_NAME = "suras-update-check-periodic"
    }
}
