package com.harisudhan.suras.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.harisudhan.suras.R
import com.harisudhan.suras.domain.model.CoreIssue

@Composable
fun fieldLabel(field: String?): String = when (field) {
    "Age" -> stringResource(R.string.field_age)
    "Gender" -> stringResource(R.string.field_gender)
    "Spicy_Food" -> stringResource(R.string.field_spicy_food)
    "Tobacco" -> stringResource(R.string.field_tobacco)
    "Alcohol" -> stringResource(R.string.field_alcohol)
    "Sleep_Hours" -> stringResource(R.string.field_sleep_hours)
    "Stress" -> stringResource(R.string.field_stress)
    "Skip_Meals" -> stringResource(R.string.field_skip_meals)
    "Soft_Drinks" -> stringResource(R.string.field_soft_drinks)
    "Empty_Stomach_Pain" -> stringResource(R.string.field_empty_stomach_pain)
    "Day1_pH_Before" -> stringResource(R.string.field_day1_ph_before)
    "Day1_pH_After" -> stringResource(R.string.field_day1_ph_after)
    "Day2_pH_Before" -> stringResource(R.string.field_day2_ph_before)
    "Day2_pH_After" -> stringResource(R.string.field_day2_ph_after)
    "model_probability" -> stringResource(R.string.result_ml_output)
    else -> field ?: stringResource(R.string.validation_error_internal)
}

/**
 * Maps a machine code from the shared core to a localized message. The core's
 * English message is only a last-resort fallback, never the primary UI copy.
 */
@Composable
fun issueMessage(issue: CoreIssue): String {
    val label = fieldLabel(issue.field)
    return when (issue.code) {
        "MISSING_FIELD" -> stringResource(R.string.validation_error_missing, label)
        "INVALID_TYPE", "NON_FINITE" -> stringResource(R.string.validation_error_number, label)
        "OUT_OF_RANGE" -> stringResource(R.string.validation_error_range, label)
        "INVALID_VALUE" -> stringResource(R.string.validation_error_value, label)
        "OUTSIDE_DATASET_ENVELOPE" ->
            stringResource(R.string.warning_outside_envelope, label)
        "INVALID_JSON", "SPEC_ERROR", "INTERNAL_ERROR" ->
            stringResource(R.string.validation_error_internal)
        else -> issue.message
    }
}
