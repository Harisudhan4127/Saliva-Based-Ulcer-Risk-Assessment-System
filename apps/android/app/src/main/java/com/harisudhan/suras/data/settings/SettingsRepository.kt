package com.harisudhan.suras.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<AppSettings> = dataStore.data.map { preferences ->
        AppSettings(
            consentAccepted = preferences[CONSENT] ?: false,
            themeMode = ThemeMode.fromStored(preferences[THEME]),
            autoUpdateCheck = preferences[AUTO_UPDATE] ?: true,
            lastUpdateCheckMillis = preferences[LAST_UPDATE_CHECK] ?: 0L,
        )
    }

    suspend fun setConsentAccepted(accepted: Boolean) {
        dataStore.edit { it[CONSENT] = accepted }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME] = mode.name }
    }

    suspend fun setAutoUpdateCheck(enabled: Boolean) {
        dataStore.edit { it[AUTO_UPDATE] = enabled }
    }

    suspend fun setLastUpdateCheckMillis(millis: Long) {
        dataStore.edit { it[LAST_UPDATE_CHECK] = millis }
    }

    private companion object {
        val CONSENT = booleanPreferencesKey("consent_accepted")
        val THEME = stringPreferencesKey("theme_mode")
        val AUTO_UPDATE = booleanPreferencesKey("auto_update_check")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
    }
}
