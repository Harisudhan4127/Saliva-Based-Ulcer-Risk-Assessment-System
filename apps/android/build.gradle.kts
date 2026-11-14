plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 compiles Kotlin itself; the kotlin-android plugin must not be applied.
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
}
