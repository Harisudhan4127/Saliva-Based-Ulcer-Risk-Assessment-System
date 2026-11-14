package com.harisudhan.suras.domain.model

/** Mirrors the strict thresholds in apps/desktop/main.py:886. */
enum class RiskCategory {
    HIGH,
    MEDIUM,
    LOW;

    companion object {
        fun fromCore(value: String?): RiskCategory? = when (value) {
            "HIGH" -> HIGH
            "MEDIUM" -> MEDIUM
            "LOW" -> LOW
            else -> null
        }
    }
}
