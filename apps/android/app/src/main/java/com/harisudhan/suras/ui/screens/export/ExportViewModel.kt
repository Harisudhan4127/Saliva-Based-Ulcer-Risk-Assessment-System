package com.harisudhan.suras.ui.screens.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harisudhan.suras.data.export.ReportExporter
import com.harisudhan.suras.data.repository.PatientRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ExportUiStatus {
    data object Idle : ExportUiStatus
    data object NoRecords : ExportUiStatus
    data object Shared : ExportUiStatus
    data object Failed : ExportUiStatus
}

data class ExportUiState(
    val busy: Boolean = false,
    val status: ExportUiStatus = ExportUiStatus.Idle,
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    repository: PatientRepository,
    private val exporter: ReportExporter,
) : ViewModel() {

    val count: StateFlow<Int> = repository.observeCount().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0,
    )

    private val _state = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    fun exportCsv() = export(mimeType = "text/csv") { exporter.exportAllCsv() }

    fun exportPdf() = export(mimeType = "application/pdf") { exporter.exportAllPdf() }

    fun consumeStatus() {
        _state.value = _state.value.copy(status = ExportUiStatus.Idle)
    }

    private fun export(
        mimeType: String,
        create: suspend () -> java.io.File?,
    ) {
        if (_state.value.busy) return
        _state.value = ExportUiState(busy = true)
        viewModelScope.launch {
            try {
                val file = create()
                if (file == null) {
                    _state.value = ExportUiState(status = ExportUiStatus.NoRecords)
                    return@launch
                }
                exporter.share(file, mimeType)
                _state.value = ExportUiState(status = ExportUiStatus.Shared)
            } catch (_: Throwable) {
                _state.value = ExportUiState(status = ExportUiStatus.Failed)
            }
        }
    }
}
