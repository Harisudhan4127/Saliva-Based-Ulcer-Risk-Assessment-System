package com.harisudhan.suras.ui.screens.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harisudhan.suras.R
import com.harisudhan.suras.ui.components.DisclaimerCard
import com.harisudhan.suras.ui.components.SurasTopBar
import kotlinx.coroutines.delay

@Composable
fun ExportScreen(
    viewModel: ExportViewModel = hiltViewModel(),
) {
    val count by viewModel.count.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.status) {
        if (state.status == ExportUiStatus.Shared || state.status == ExportUiStatus.Failed) {
            delay(3_000)
            viewModel.consumeStatus()
        }
    }

    Scaffold(topBar = {
        SurasTopBar(title = stringResource(R.string.export_title))
    }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.export_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Text(
                    text = pluralStringResource(R.plurals.history_count, count, count),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (count == 0) {
                item {
                    Text(
                        text = stringResource(R.string.export_no_records),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                ExportCard(
                    title = stringResource(R.string.export_csv_title),
                    description = stringResource(R.string.export_csv_desc),
                    icon = {
                        Icon(Icons.Filled.Description, contentDescription = null)
                    },
                    actionLabel = stringResource(R.string.action_export_csv),
                    enabled = count > 0 && !state.busy,
                    onExport = viewModel::exportCsv,
                )
            }
            item {
                ExportCard(
                    title = stringResource(R.string.export_pdf_title),
                    description = stringResource(R.string.export_pdf_desc),
                    icon = {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                    },
                    actionLabel = stringResource(R.string.action_export_pdf),
                    enabled = count > 0 && !state.busy,
                    onExport = viewModel::exportPdf,
                )
            }

            if (state.busy) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .width(20.dp)
                                .height(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.loading),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            when (state.status) {
                ExportUiStatus.NoRecords -> item {
                    Text(
                        text = stringResource(R.string.export_no_records),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                ExportUiStatus.Shared -> item {
                    Text(
                        text = stringResource(R.string.export_success),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                ExportUiStatus.Failed -> item {
                    Text(
                        text = stringResource(
                            R.string.export_failed,
                            stringResource(R.string.generic_error),
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                else -> Unit
            }

            item { DisclaimerCard() }
        }
    }
}

@Composable
private fun ExportCard(
    title: String,
    description: String,
    icon: @Composable () -> Unit,
    actionLabel: String,
    enabled: Boolean,
    onExport: () -> Unit,
) {
    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon()
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onExport, enabled = enabled) {
                Text(actionLabel)
            }
        }
    }
}
