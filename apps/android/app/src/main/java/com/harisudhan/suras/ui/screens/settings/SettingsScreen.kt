package com.harisudhan.suras.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harisudhan.suras.R
import com.harisudhan.suras.data.settings.ThemeMode
import com.harisudhan.suras.ui.components.KeyValueRow
import com.harisudhan.suras.ui.components.SectionCard
import com.harisudhan.suras.ui.components.SurasTopBar
import com.harisudhan.suras.ui.components.SwitchRow

@Composable
fun SettingsScreen(
    onAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()
    val dataCleared by viewModel.dataCleared.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val clearedMessage = stringResource(R.string.settings_cleared)
    var confirmClear by remember { mutableStateOf(false) }
    var notificationsDenied by remember { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsDenied = !granted
    }

    fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(dataCleared) {
        if (dataCleared) {
            snackbarHostState.showSnackbar(clearedMessage)
            viewModel.consumeDataCleared()
        }
    }

    Scaffold(
        topBar = { SurasTopBar(title = stringResource(R.string.settings_title)) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SectionCard(title = stringResource(R.string.settings_appearance)) {
                    Text(
                        text = stringResource(R.string.settings_theme),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    ThemeOption(
                        label = stringResource(R.string.settings_theme_system),
                        selected = settings.themeMode == ThemeMode.SYSTEM,
                        onClick = { viewModel.setTheme(ThemeMode.SYSTEM) },
                    )
                    ThemeOption(
                        label = stringResource(R.string.settings_theme_light),
                        selected = settings.themeMode == ThemeMode.LIGHT,
                        onClick = { viewModel.setTheme(ThemeMode.LIGHT) },
                    )
                    ThemeOption(
                        label = stringResource(R.string.settings_theme_dark),
                        selected = settings.themeMode == ThemeMode.DARK,
                        onClick = { viewModel.setTheme(ThemeMode.DARK) },
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.settings_updates)) {
                    SwitchRow(
                        label = stringResource(R.string.settings_auto_update),
                        checked = settings.autoUpdateCheck,
                        supporting = stringResource(R.string.settings_auto_update_desc),
                        onCheckedChange = { enabled ->
                            viewModel.setAutoUpdate(enabled)
                            if (enabled) requestNotificationsIfNeeded()
                        },
                    )
                    if (notificationsDenied) {
                        Text(
                            text = stringResource(R.string.settings_notifications_denied),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    OutlinedButton(
                        onClick = viewModel::checkForUpdates,
                        enabled = update.status != UpdateStatus.Checking,
                    ) {
                        Text(
                            stringResource(
                                if (update.status == UpdateStatus.Checking) {
                                    R.string.settings_checking
                                } else {
                                    R.string.action_check_updates
                                },
                            ),
                        )
                    }
                    UpdateStatusText(update)
                    if (update.status == UpdateStatus.Downloading) {
                        LinearProgressIndicator(
                            progress = { update.progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = stringResource(R.string.settings_update_downloading),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    val release = update.release
                    if (release != null && !release.notes.isBlank()) {
                        Text(
                            text = stringResource(R.string.settings_update_release_notes),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = release.notes.take(600),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (update.status is UpdateStatus.Available) {
                        Button(
                            onClick = viewModel::downloadAndInstall,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.settings_update_download))
                        }
                    }
                    if (update.status == UpdateStatus.PermissionRequired) {
                        OutlinedButton(
                            onClick = viewModel::downloadAndInstall,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.settings_update_install))
                        }
                    }
                    Text(
                        text = stringResource(R.string.settings_update_digest_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.settings_privacy)) {
                    Text(
                        text = stringResource(R.string.settings_encryption),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.settings_screen_capture),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.settings_network),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.settings_data)) {
                    OutlinedButton(
                        onClick = { confirmClear = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_clear_history),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.about_title)) {
                    KeyValueRow(
                        label = stringResource(R.string.result_version_app),
                        value = viewModel.appVersion,
                    )
                    KeyValueRow(
                        label = stringResource(R.string.result_version_model),
                        value = viewModel.modelVersion,
                    )
                    Text(
                        text = stringResource(
                            R.string.settings_about_app,
                            viewModel.appVersion,
                            com.harisudhan.suras.BuildConfig.VERSION_CODE,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onAbout, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.settings_about_button))
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.settings_clear_title)) },
            text = { Text(stringResource(R.string.settings_clear_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllData()
                        confirmClear = false
                    },
                ) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun ThemeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun UpdateStatusText(update: UpdateState) {
    val text = when (val status = update.status) {
        UpdateStatus.Idle -> return
        UpdateStatus.Checking -> stringResource(R.string.settings_checking)
        UpdateStatus.NotConfigured -> stringResource(R.string.settings_update_not_configured)
        UpdateStatus.UpToDate -> stringResource(R.string.settings_update_uptodate)
        is UpdateStatus.Available -> stringResource(
            R.string.settings_update_available,
            status.version,
        )
        UpdateStatus.Downloading -> stringResource(R.string.settings_update_downloading)
        UpdateStatus.Verified -> stringResource(R.string.settings_update_verified)
        UpdateStatus.PermissionRequired -> stringResource(R.string.settings_update_permission)
        UpdateStatus.NoDigest -> stringResource(R.string.settings_update_no_digest)
        UpdateStatus.ChecksumFailed -> stringResource(R.string.settings_update_checksum_failed)
        UpdateStatus.DownloadFailed, UpdateStatus.CheckFailed ->
            stringResource(R.string.settings_update_error)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (update.status == UpdateStatus.CheckFailed ||
            update.status == UpdateStatus.ChecksumFailed ||
            update.status == UpdateStatus.DownloadFailed
        ) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
    Spacer(Modifier.height(2.dp))
}
