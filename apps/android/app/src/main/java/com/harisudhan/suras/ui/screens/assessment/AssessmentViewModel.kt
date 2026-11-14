package com.harisudhan.suras.ui.screens.assessment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harisudhan.suras.data.repository.PatientRepository
import com.harisudhan.suras.domain.model.AnalysisFailure
import com.harisudhan.suras.domain.model.AnalysisResult
import com.harisudhan.suras.domain.model.CoreIssue
import com.harisudhan.suras.domain.model.PatientInput
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AssessmentDraft(
    val patientId: String,
    val name: String = "",
    val age: String = "",
    val gender: String = PatientInput.MALE,
    val spicyFood: Boolean = false,
    val tobacco: Boolean = false,
    val alcohol: Boolean = false,
    val sleepHours: String = "",
    val stress: String = "",
    val skipMeals: Boolean = false,
    val softDrinks: Boolean = false,
    val emptyStomachPain: Boolean = false,
    val day1PhBefore: String = "",
    val day1PhAfter: String = "",
    val day2PhBefore: String = "",
    val day2PhAfter: String = "",
) {
    fun toInput(): PatientInput = PatientInput(
        patientCode = patientId,
        name = name,
        age = age,
        gender = gender,
        spicyFood = PatientInput.yesNo(spicyFood),
        tobacco = PatientInput.yesNo(tobacco),
        alcohol = PatientInput.yesNo(alcohol),
        sleepHours = sleepHours,
        stress = stress,
        skipMeals = PatientInput.yesNo(skipMeals),
        softDrinks = PatientInput.yesNo(softDrinks),
        emptyStomachPain = PatientInput.yesNo(emptyStomachPain),
        day1PhBefore = day1PhBefore,
        day1PhAfter = day1PhAfter,
        day2PhBefore = day2PhBefore,
        day2PhAfter = day2PhAfter,
    )
}

data class AssessmentUiState(
    val draft: AssessmentDraft = newAssessmentDraft(),
    val nameError: Boolean = false,
    val running: Boolean = false,
    val errors: List<CoreIssue> = emptyList(),
    val failure: AnalysisFailure? = null,
    val savedPatientId: String? = null,
)

@HiltViewModel
class AssessmentViewModel @Inject constructor(
    private val repository: PatientRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AssessmentUiState())
    val state: StateFlow<AssessmentUiState> = _state.asStateFlow()

    fun reset() {
        _state.value = AssessmentUiState()
    }

    /** @return true when the form may advance to the biomarker step. */
    fun continueFromName(): Boolean {
        val blank = _state.value.draft.name.isBlank()
        _state.update { it.copy(nameError = blank) }
        return !blank
    }

    fun updateName(value: String) {
        edit { copy(name = value) }
        if (_state.value.nameError && value.isNotBlank()) {
            _state.update { it.copy(nameError = false) }
        }
    }

    fun updateAge(value: String) = edit { copy(age = value) }
    fun updateGender(value: String) = edit { copy(gender = value) }
    fun updateSleepHours(value: String) = edit { copy(sleepHours = value) }
    fun updateStress(value: String) = edit { copy(stress = value) }
    fun updateDay1PhBefore(value: String) = edit { copy(day1PhBefore = value) }
    fun updateDay1PhAfter(value: String) = edit { copy(day1PhAfter = value) }
    fun updateDay2PhBefore(value: String) = edit { copy(day2PhBefore = value) }
    fun updateDay2PhAfter(value: String) = edit { copy(day2PhAfter = value) }
    fun updateSpicyFood(value: Boolean) = edit { copy(spicyFood = value) }
    fun updateTobacco(value: Boolean) = edit { copy(tobacco = value) }
    fun updateAlcohol(value: Boolean) = edit { copy(alcohol = value) }
    fun updateSkipMeals(value: Boolean) = edit { copy(skipMeals = value) }
    fun updateSoftDrinks(value: Boolean) = edit { copy(softDrinks = value) }
    fun updateEmptyStomachPain(value: Boolean) = edit { copy(emptyStomachPain = value) }

    fun analyze() {
        if (_state.value.running) return
        _state.update { it.copy(running = true, errors = emptyList(), failure = null) }
        viewModelScope.launch {
            when (val result = repository.analyzeAndSave(_state.value.draft.toInput())) {
                is AnalysisResult.Success -> _state.update {
                    it.copy(running = false, savedPatientId = result.record.patientId)
                }
                is AnalysisResult.Invalid -> _state.update {
                    it.copy(running = false, errors = result.errors)
                }
                is AnalysisResult.Failed -> _state.update {
                    it.copy(running = false, failure = result.reason)
                }
            }
        }
    }

    fun consumeFailure() {
        _state.update { it.copy(failure = null) }
    }

    fun consumeSaved() {
        _state.update { it.copy(savedPatientId = null) }
    }

    private fun edit(update: AssessmentDraft.() -> AssessmentDraft) {
        _state.update {
            it.copy(
                draft = it.draft.update(),
                errors = emptyList(),
                failure = null,
            )
        }
    }
}

private fun newAssessmentDraft(): AssessmentDraft = AssessmentDraft(
    patientId = "PAT-" + UUID.randomUUID().toString()
        .replace("-", "")
        .take(8)
        .uppercase(Locale.US),
)
