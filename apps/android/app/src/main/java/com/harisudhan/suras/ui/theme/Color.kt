package com.harisudhan.suras.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// VERIFIED: content palette copied from apps/desktop/main.py stylesheets.
// The desktop's dark-navy sidebar (#0f172a) is intentionally not reproduced on
// Android; see docs/DECISIONS.md.
val Teal = Color(0xFF0F766E)
val TealDark = Color(0xFF0D6460)
val Slate900 = Color(0xFF0F172A)
val Slate800 = Color(0xFF1E293B)
val Slate600 = Color(0xFF475569)
val Slate500 = Color(0xFF64748B)
val Slate200 = Color(0xFFE2E8F0)
val Slate100 = Color(0xFFF1F5F9)
val Slate50 = Color(0xFFF8FAFC)
val Mint100 = Color(0xFFCCFBF1)
val Mint900 = Color(0xFF065F46)

val RiskRed = Color(0xFFDC2626)
val RiskRedContainer = Color(0xFFFEF2F2)
val RiskRedBorder = Color(0xFFFCA5A5)
val RiskAmber = Color(0xFFD97706)
val RiskAmberContainer = Color(0xFFFFFBEB)
val RiskAmberBorder = Color(0xFFFCD34D)
val RiskGreen = Color(0xFF16A34A)
val RiskGreenContainer = Color(0xFFF0FDF4)
val RiskGreenBorder = Color(0xFF86EFAC)

@Immutable
data class RiskColors(
    val highContent: Color,
    val highContainer: Color,
    val highBorder: Color,
    val mediumContent: Color,
    val mediumContainer: Color,
    val mediumBorder: Color,
    val lowContent: Color,
    val lowContainer: Color,
    val lowBorder: Color,
)

val LightRiskColors = RiskColors(
    highContent = RiskRed,
    highContainer = RiskRedContainer,
    highBorder = RiskRedBorder,
    mediumContent = RiskAmber,
    mediumContainer = RiskAmberContainer,
    mediumBorder = RiskAmberBorder,
    lowContent = RiskGreen,
    lowContainer = RiskGreenContainer,
    lowBorder = RiskGreenBorder,
)

val DarkRiskColors = RiskColors(
    highContent = Color(0xFFF87171),
    highContainer = Color(0xFF3A1414),
    highBorder = Color(0xFF7F1D1D),
    mediumContent = Color(0xFFFBBF24),
    mediumContainer = Color(0xFF3A2A08),
    mediumBorder = Color(0xFF92400E),
    lowContent = Color(0xFF4ADE80),
    lowContainer = Color(0xFF0F2A18),
    lowBorder = Color(0xFF166534),
)

val LocalRiskColors = staticCompositionLocalOf { LightRiskColors }
