package com.flowpay.app.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

data class FlowPayAccentTheme(
    val primary: Color,
    val primaryDark: Color,
    val headerGradientStart: Color,
    val headerGradientEnd: Color,
    val accent: Color,
    val accentLight: Color
)

val BlueAccentTheme = FlowPayAccentTheme(
    primary = Color(0xFF5B8DEF),
    primaryDark = Color(0xFF1976D2),
    headerGradientStart = Color(0xFF7BA8F5),
    headerGradientEnd = Color(0xFF6A96EE),
    accent = Color(0xFF4A90E2),
    accentLight = Color(0xFF4A9EFF)
)

val LocalFlowPayAccentTheme = compositionLocalOf { BlueAccentTheme }
