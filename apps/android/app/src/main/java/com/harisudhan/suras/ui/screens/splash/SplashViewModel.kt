package com.harisudhan.suras.ui.screens.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harisudhan.suras.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface StartDestination {
    data object Consent : StartDestination
    data object Home : StartDestination
    data object Settings : StartDestination
}

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _destination = MutableStateFlow<StartDestination?>(null)
    val destination: StateFlow<StartDestination?> = _destination.asStateFlow()

    private var resolved = false

    fun resolve(startAtSettings: Boolean) {
        if (resolved) return
        resolved = true
        viewModelScope.launch {
            // Keep the branded splash visible long enough to avoid a flash,
            // then decide from persisted consent only.
            delay(550)
            val current = settings.settings.first()
            _destination.value = when {
                !current.consentAccepted -> StartDestination.Consent
                startAtSettings -> StartDestination.Settings
                else -> StartDestination.Home
            }
        }
    }
}
