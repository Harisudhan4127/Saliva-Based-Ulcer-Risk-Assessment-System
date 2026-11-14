package com.harisudhan.suras.data.settings

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromStored(value: String?): ThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

data class AppSettings(
    val consentAccepted: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val autoUpdateCheck: Boolean = true,
    val lastUpdateCheckMillis: Long = 0L,
)
