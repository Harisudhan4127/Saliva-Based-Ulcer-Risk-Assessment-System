package com.harisudhan.suras.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harisudhan.suras.BuildConfig
import com.harisudhan.suras.data.local.PatientRecord
import com.harisudhan.suras.data.repository.PatientRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val recent: List<PatientRecord> = emptyList(),
    val total: Int = 0,
    val appVersion: String = BuildConfig.VERSION_NAME,
    val modelVersion: String = BuildConfig.MODEL_VERSION,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    repository: PatientRepository,
) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        repository.observeRecent(3),
        repository.observeCount(),
    ) { recent, total ->
        HomeUiState(recent = recent, total = total)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )
}
