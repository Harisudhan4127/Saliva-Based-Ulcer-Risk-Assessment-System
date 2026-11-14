package com.harisudhan.suras.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harisudhan.suras.BuildConfig
import com.harisudhan.suras.data.repository.PatientRepository
import com.harisudhan.suras.data.settings.AppSettings
import com.harisudhan.suras.data.settings.SettingsRepository
import com.harisudhan.suras.data.settings.ThemeMode
import com.harisudhan.suras.data.update.GitHubReleaseClient
import com.harisudhan.suras.data.update.ReleaseInfo
import com.harisudhan.suras.data.update.UpdateCheckResult
import com.harisudhan.suras.data.update.UpdateDownloadResult
import com.harisudhan.suras.data.update.UpdateDownloader
import com.harisudhan.suras.data.update.UpdateInstaller
import com.harisudhan.suras.data.update.UpdateScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object NotConfigured : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Available(val version: String) : UpdateStatus
    data object Downloading : UpdateStatus
    data object Verified : UpdateStatus
    data object PermissionRequired : UpdateStatus
    data object NoDigest : UpdateStatus
    data object ChecksumFailed : UpdateStatus
    data object DownloadFailed : UpdateStatus
    data object CheckFailed : UpdateStatus
}

data class UpdateState(
    val status: UpdateStatus = UpdateStatus.Idle,
    val release: ReleaseInfo? = null,
    val progress: Int = 0,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val repository: PatientRepository,
    private val client: GitHubReleaseClient,
    private val downloader: UpdateDownloader,
    private val installer: UpdateInstaller,
    private val scheduler: UpdateScheduler,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    private val _update = MutableStateFlow(UpdateState())
    val update: StateFlow<UpdateState> = _update.asStateFlow()

    private val _dataCleared = MutableStateFlow(false)
    val dataCleared: StateFlow<Boolean> = _dataCleared.asStateFlow()

    val appVersion: String = BuildConfig.VERSION_NAME
    val modelVersion: String = BuildConfig.MODEL_VERSION

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setAutoUpdate(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoUpdateCheck(enabled)
            scheduler.sync(enabled)
        }
    }

    fun checkForUpdates() {
        if (_update.value.status == UpdateStatus.Checking ||
            _update.value.status == UpdateStatus.Downloading
        ) {
            return
        }
        _update.value = UpdateState(status = UpdateStatus.Checking)
        viewModelScope.launch {
            when (val result = client.latest()) {
                UpdateCheckResult.NotConfigured -> _update.value =
                    UpdateState(status = UpdateStatus.NotConfigured)
                UpdateCheckResult.UpToDate -> {
                    settingsRepository.setLastUpdateCheckMillis(System.currentTimeMillis())
                    _update.value = UpdateState(status = UpdateStatus.UpToDate)
                }
                is UpdateCheckResult.Available -> _update.value = UpdateState(
                    status = UpdateStatus.Available(result.release.version),
                    release = result.release,
                )
                is UpdateCheckResult.Failed -> _update.value =
                    UpdateState(status = UpdateStatus.CheckFailed)
            }
        }
    }

    fun downloadAndInstall() {
        if (_update.value.status == UpdateStatus.Downloading) return
        val release = _update.value.release ?: return
        val asset = release.apk
        if (asset == null || asset.sha256 == null) {
            _update.value = _update.value.copy(status = UpdateStatus.NoDigest)
            return
        }
        if (!installer.canInstallPackages()) {
            _update.value = _update.value.copy(status = UpdateStatus.PermissionRequired)
            installer.openInstallPermissionSettings()
            return
        }
        _update.value = _update.value.copy(status = UpdateStatus.Downloading, progress = 0)
        viewModelScope.launch {
            val result = downloader.download(asset) { percent ->
                _update.update { it.copy(progress = percent) }
            }
            when (result) {
                is UpdateDownloadResult.Verified -> {
                    val opened = installer.openInstaller(result.file)
                    _update.value = _update.value.copy(
                        status = if (opened) UpdateStatus.Verified else UpdateStatus.DownloadFailed,
                    )
                }
                is UpdateDownloadResult.Failed -> {
                    _update.value = _update.value.copy(
                        status = when (result.reason) {
                            UpdateDownloadResult.Reason.NO_DIGEST -> UpdateStatus.NoDigest
                            UpdateDownloadResult.Reason.CHECKSUM_MISMATCH ->
                                UpdateStatus.ChecksumFailed
                            else -> UpdateStatus.DownloadFailed
                        },
                    )
                }
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAll()
            _dataCleared.value = true
        }
    }

    fun consumeDataCleared() {
        _dataCleared.value = false
    }
}
