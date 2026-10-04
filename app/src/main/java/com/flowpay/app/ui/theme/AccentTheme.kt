// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

data class FlowpayAccentTheme(
    val primary: Color,
    val primaryDark: Color,
    val headerGradientStart: Color,
    val headerGradientEnd: Color,
    val accent: Color,
    val accentLight: Color
)

// Desaturated and narrowed vs. the original bright #7BA8F5→#6A96EE gradient:
// this is now a structural "ledger" blue (links, icons, dividers, and any
// gradient a screen still asks for reads as a near-flat, restrained tone)
// rather than the loud sky-blue gradient every generic fintech screen
// reaches for. Screens rebuilt for the ledger system use the seal-red and
// rule tokens in Color.kt directly instead of this gradient at all.
val BlueAccentTheme = FlowpayAccentTheme(
    primary = Color(0xFF6B87A6),
    primaryDark = Color(0xFF445C74),
    headerGradientStart = Color(0xFF56708A),
    headerGradientEnd = Color(0xFF4A5F76),
    accent = Color(0xFF7691AC),
    accentLight = Color(0xFF87A0B8)
)

val LocalFlowpayAccentTheme = compositionLocalOf { BlueAccentTheme }
