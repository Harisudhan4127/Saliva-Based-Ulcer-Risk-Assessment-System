package com.harisudhan.suras.ui.screens.result

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harisudhan.suras.R
import com.harisudhan.suras.data.local.PatientRecord
import com.harisudhan.suras.data.repository.PatientRepository
import com.harisudhan.suras.ui.components.DisclaimerCard
import com.harisudhan.suras.ui.components.KeyValueRow
import com.harisudhan.suras.ui.components.SurasTopBar
import com.harisudhan.suras.ui.components.issueMessage
import com.harisudhan.suras.ui.theme.LocalRiskColors
import java.text.SimpleDateFormat
import java.util.Date
import kotlinx.coroutines.delay

@Composable
fun ResultScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: ResultViewModel = hiltViewModel(),
) {
    val record by viewModel.record.collectAsStateWithLifecycle()
    val exportStatus by viewModel.exportStatus.collectAsStateWithLifecycle()
    var waited by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(1_500)
        waited = true
    }

    Scaffold(topBar = {
        SurasTopBar(
            title = stringResource(R.string.result_title),
            onBack = onBack,
            actions = {
                TextButton(onClick = onDone) {
                    Text(stringResource(R.string.action_done))
                }
            },
        )
    }) { padding ->
        val current = record
        when {
            current != null -> ResultContent(
                record = current,
                exportStatus = exportStatus,
                onExport = viewModel::exportPdf,
                onDone = onDone,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            waited -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.result_not_found))
            }
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
        }
    }

    LaunchedEffect(exportStatus) {
        if (exportStatus == ExportStatus.Shared || exportStatus == ExportStatus.Failed) {
            delay(2_500)
            viewModel.consumeExportStatus()
        }
    }
}

@Composable
private fun ResultContent(
    record: PatientRecord,
    exportStatus: ExportStatus,
    onExport: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val riskColors = LocalRiskColors.current
    val (container, content, border) = when (record.riskLevel) {
        "HIGH" -> Triple(
            riskColors.highContainer,
            riskColors.highContent,
            riskColors.highBorder,
        )
        "MEDIUM" -> Triple(
            riskColors.mediumContainer,
            riskColors.mediumContent,
            riskColors.mediumBorder,
        )
        else -> Triple(
            riskColors.lowContainer,
            riskColors.lowContent,
            riskColors.lowBorder,
        )
    }
    val headline = when (record.riskLevel) {
        "HIGH" -> stringResource(R.string.result_risk_high)
        "MEDIUM" -> stringResource(R.string.result_risk_medium)
        else -> stringResource(R.string.result_risk_low)
    }
    val note = when (record.riskLevel) {
        "HIGH" -> stringResource(R.string.result_note_high)
        "MEDIUM" -> stringResource(R.string.result_note_medium)
        else -> stringResource(R.string.result_note_low)
    }
    val riskDescription = when (record.riskLevel) {
        "HIGH" -> stringResource(R.string.cd_risk_high)
        "MEDIUM" -> stringResource(R.string.cd_risk_medium)
        else -> stringResource(R.string.cd_risk_low)
    }
    val warnings = PatientRepository.decodeWarnings(record.warningsJson)
    val locale = LocalConfiguration.current.locales[0]
    val timestamp = SimpleDateFormat("dd MMM yyyy, HH:mm", locale)
        .format(Date(record.createdAt))

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = riskDescription },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = container,
                    contentColor = content,
                ),
                border = BorderStroke(2.dp, border),
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.result_probability,
                            String.format(locale, "%.1f", record.probability * 100),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(text = note, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.result_ml_output),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    KeyValueRow(
                        label = stringResource(R.string.result_title),
                        value = record.riskLevel,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = stringResource(R.string.result_swbra_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ) {
                            Text(
                                text = stringResource(R.string.result_swbra_unavailable),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = record.swbraStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = stringResource(R.string.result_swbra_reason),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (warnings.isNotEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        warnings.forEach { warning ->
                            Text(
                                text = "• ${issueMessage(warning)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.result_inputs),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    val yesNo: @Composable (Boolean) -> String = { value ->
                        stringResource(if (value) R.string.yes else R.string.no)
                    }
                    KeyValueRow(stringResource(R.string.field_age), record.age.toString())
                    KeyValueRow(stringResource(R.string.field_gender), record.gender)
                    KeyValueRow(
                        stringResource(R.string.field_spicy_food),
                        yesNo(record.spicyFood),
                    )
                    KeyValueRow(stringResource(R.string.field_tobacco), yesNo(record.tobacco))
                    KeyValueRow(stringResource(R.string.field_alcohol), yesNo(record.alcohol))
                    KeyValueRow(
                        stringResource(R.string.field_sleep_hours),
                        record.sleepHours.toString(),
                    )
                    KeyValueRow(stringResource(R.string.field_stress), record.stress.toString())
                    KeyValueRow(
                        stringResource(R.string.field_skip_meals),
                        yesNo(record.skipMeals),
                    )
                    KeyValueRow(
                        stringResource(R.string.field_soft_drinks),
                        yesNo(record.softDrinks),
                    )
                    KeyValueRow(
                        stringResource(R.string.field_empty_stomach_pain),
                        yesNo(record.emptyStomachPain),
                    )
                    KeyValueRow(
                        stringResource(R.string.field_day1_ph_before),
                        record.day1PhBefore.toString(),
                    )
                    KeyValueRow(
                        stringResource(R.string.field_day1_ph_after),
                        record.day1PhAfter.toString(),
                    )
                    KeyValueRow(
                        stringResource(R.string.field_day2_ph_before),
                        record.day2PhBefore.toString(),
                    )
                    KeyValueRow(
                        stringResource(R.string.field_day2_ph_after),
                        record.day2PhAfter.toString(),
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.result_versions),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    KeyValueRow(stringResource(R.string.result_version_app), record.appVersion)
                    KeyValueRow(stringResource(R.string.result_version_model), record.modelVersion)
                    KeyValueRow(
                        stringResource(R.string.result_version_preprocessing),
                        record.preprocessingVersion,
                    )
                    KeyValueRow(
                        stringResource(R.string.result_version_swbra),
                        record.swbraSpecVersion,
                    )
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.result_timestamp, timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item { DisclaimerCard() }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onExport,
                    enabled = exportStatus != ExportStatus.Busy,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Text(stringResource(R.string.action_export_pdf))
                }
                TextButton(
                    onClick = onDone,
                    modifier = Modifier.height(48.dp),
                ) {
                    Text(stringResource(R.string.action_done))
                }
            }
        }

        if (exportStatus == ExportStatus.Shared) {
            item {
                Text(
                    text = stringResource(R.string.export_success),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (exportStatus == ExportStatus.Failed) {
            item {
                Text(
                    text = stringResource(R.string.export_failed, stringResource(R.string.generic_error)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
