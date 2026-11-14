package com.harisudhan.suras.ui.screens.consent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harisudhan.suras.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ConsentViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _accepted = MutableStateFlow(false)
    val accepted: StateFlow<Boolean> = _accepted.asStateFlow()

    fun accept() {
        viewModelScope.launch {
            settings.setConsentAccepted(true)
            _accepted.value = true
        }
    }
}
