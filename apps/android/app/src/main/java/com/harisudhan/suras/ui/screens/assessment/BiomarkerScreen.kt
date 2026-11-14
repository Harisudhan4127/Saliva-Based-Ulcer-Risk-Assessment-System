package com.harisudhan.suras.ui.screens.assessment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harisudhan.suras.R
import com.harisudhan.suras.domain.model.AnalysisFailure
import com.harisudhan.suras.domain.model.PatientInput
import com.harisudhan.suras.ui.components.SectionCard
import com.harisudhan.suras.ui.components.SurasTopBar
import com.harisudhan.suras.ui.components.SwitchRow
import com.harisudhan.suras.ui.components.issueMessage

@Composable
fun BiomarkerScreen(
    onBack: () -> Unit,
    onResult: (String) -> Unit,
    viewModel: AssessmentViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.savedPatientId) {
        val saved = state.savedPatientId
        if (saved != null) {
            viewModel.consumeSaved()
            onResult(saved)
        }
    }

    if (state.failure != null) {
        AlertDialog(
            onDismissRequest = viewModel::consumeFailure,
            confirmButton = {
                TextButton(onClick = viewModel::consumeFailure) {
                    Text(stringResource(R.string.action_close))
                }
            },
            title = { Text(stringResource(R.string.analysis_failed)) },
            text = {
                Text(
                    stringResource(
                        if (state.failure == AnalysisFailure.MODEL_UNAVAILABLE) {
                            R.string.model_unavailable
                        } else {
                            R.string.analysis_failed
                        },
                    ),
                )
            },
        )
    }

    Scaffold(topBar = {
        SurasTopBar(title = stringResource(R.string.biomarker_title), onBack = onBack)
    }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 8.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column {
                    Text(
                        text = stringResource(R.string.biomarker_step),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${state.draft.patientId} · ${state.draft.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.biomarker_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.errors.isNotEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            state.errors.forEach { issue ->
                                Text(
                                    text = "• ${issueMessage(issue)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.section_demographics)) {
                    NumericField(
                        label = stringResource(R.string.field_age),
                        value = state.draft.age,
                        onValueChange = viewModel::updateAge,
                        keyboardType = KeyboardType.Number,
                    )
                    Text(
                        text = stringResource(R.string.field_gender),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = state.draft.gender == PatientInput.MALE,
                            onClick = { viewModel.updateGender(PatientInput.MALE) },
                            label = { Text(stringResource(R.string.gender_male)) },
                        )
                        FilterChip(
                            selected = state.draft.gender == PatientInput.FEMALE,
                            onClick = { viewModel.updateGender(PatientInput.FEMALE) },
                            label = { Text(stringResource(R.string.gender_female)) },
                        )
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.section_lifestyle)) {
                    SwitchRow(
                        label = stringResource(R.string.field_spicy_food),
                        checked = state.draft.spicyFood,
                        onCheckedChange = viewModel::updateSpicyFood,
                    )
                    SwitchRow(
                        label = stringResource(R.string.field_tobacco),
                        checked = state.draft.tobacco,
                        onCheckedChange = viewModel::updateTobacco,
                    )
                    SwitchRow(
                        label = stringResource(R.string.field_alcohol),
                        checked = state.draft.alcohol,
                        onCheckedChange = viewModel::updateAlcohol,
                    )
                    SwitchRow(
                        label = stringResource(R.string.field_skip_meals),
                        checked = state.draft.skipMeals,
                        onCheckedChange = viewModel::updateSkipMeals,
                    )
                    SwitchRow(
                        label = stringResource(R.string.field_soft_drinks),
                        checked = state.draft.softDrinks,
                        onCheckedChange = viewModel::updateSoftDrinks,
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.section_sleep_stress)) {
                    NumericField(
                        label = stringResource(R.string.field_sleep_hours),
                        value = state.draft.sleepHours,
                        onValueChange = viewModel::updateSleepHours,
                    )
                    NumericField(
                        label = stringResource(R.string.field_stress),
                        value = state.draft.stress,
                        onValueChange = viewModel::updateStress,
                        keyboardType = KeyboardType.Number,
                    )
                    SwitchRow(
                        label = stringResource(R.string.field_empty_stomach_pain),
                        checked = state.draft.emptyStomachPain,
                        onCheckedChange = viewModel::updateEmptyStomachPain,
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.section_ph_day1)) {
                    NumericField(
                        label = stringResource(R.string.field_day1_ph_before),
                        value = state.draft.day1PhBefore,
                        onValueChange = viewModel::updateDay1PhBefore,
                    )
                    NumericField(
                        label = stringResource(R.string.field_day1_ph_after),
                        value = state.draft.day1PhAfter,
                        onValueChange = viewModel::updateDay1PhAfter,
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.section_ph_day2)) {
                    NumericField(
                        label = stringResource(R.string.field_day2_ph_before),
                        value = state.draft.day2PhBefore,
                        onValueChange = viewModel::updateDay2PhBefore,
                    )
                    NumericField(
                        label = stringResource(R.string.field_day2_ph_after),
                        value = state.draft.day2PhAfter,
                        onValueChange = viewModel::updateDay2PhAfter,
                    )
                }
            }

            item {
                Button(
                    onClick = viewModel::analyze,
                    enabled = !state.running,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    if (state.running) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .width(20.dp)
                                .height(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.biomarker_running))
                    } else {
                        Text(stringResource(R.string.action_run_analysis))
                    }
                }
            }
        }
    }
}

@Composable
private fun NumericField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Decimal,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            // Accept a comma decimal separator from local keyboards; the core
            // only accepts a JSON number with a dot.
            onValueChange(raw.replace(',', '.').filter { it.isDigit() || it == '.' || it == '-' })
        },
        label = { Text(label) },
        placeholder = { Text(stringResource(R.string.input_hint_numeric)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = ImeAction.Next,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
