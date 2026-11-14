package com.harisudhan.suras.ui.screens.result

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harisudhan.suras.data.export.ReportExporter
import com.harisudhan.suras.data.local.PatientRecord
import com.harisudhan.suras.data.repository.PatientRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ExportStatus {
    data object Idle : ExportStatus
    data object Busy : ExportStatus
    data object Shared : ExportStatus
    data object Failed : ExportStatus
}

@HiltViewModel
class ResultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: PatientRepository,
    private val exporter: ReportExporter,
) : ViewModel() {

    val patientId: String = checkNotNull(savedStateHandle[ARG_PATIENT_ID])

    val record: StateFlow<PatientRecord?> = repository.observeById(patientId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    private val _exportStatus = MutableStateFlow<ExportStatus>(ExportStatus.Idle)
    val exportStatus: StateFlow<ExportStatus> = _exportStatus.asStateFlow()

    fun exportPdf() {
        val current = record.value ?: return
        if (_exportStatus.value == ExportStatus.Busy) return
        _exportStatus.value = ExportStatus.Busy
        viewModelScope.launch {
            try {
                val file = exporter.exportRecordPdf(current)
                exporter.share(file, "application/pdf")
                _exportStatus.value = ExportStatus.Shared
            } catch (_: Throwable) {
                _exportStatus.value = ExportStatus.Failed
            }
        }
    }

    fun consumeExportStatus() {
        _exportStatus.value = ExportStatus.Idle
    }

    companion object {
        const val ARG_PATIENT_ID = "patientId"
        const val ROUTE = "result/{$ARG_PATIENT_ID}"
        fun route(patientId: String): String = "result/$patientId"
    }
}
