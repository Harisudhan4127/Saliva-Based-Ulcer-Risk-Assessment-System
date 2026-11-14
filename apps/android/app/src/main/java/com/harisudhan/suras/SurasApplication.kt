package com.harisudhan.suras

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.harisudhan.suras.data.settings.SettingsRepository
import com.harisudhan.suras.data.update.UpdateNotifications
import com.harisudhan.suras.data.update.UpdateScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltAndroidApp
class SurasApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var scheduler: UpdateScheduler

    @Inject
    lateinit var notifications: UpdateNotifications

    @Inject
    lateinit var settings: SettingsRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        notifications.ensureChannel()
        // Re-apply the user's update preference on every cold start so the
        // periodic worker survives data migrations and app upgrades.
        appScope.launch {
            val autoUpdate = settings.settings.first().autoUpdateCheck
            scheduler.sync(autoUpdate)
        }
    }
}
