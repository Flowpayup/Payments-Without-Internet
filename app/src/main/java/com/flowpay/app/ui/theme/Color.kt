// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.ui.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────
// Flowpay design tokens — dark theme.
//
// Every Compose screen draws from these; inline Color(0x…) literals outside
// this package are a CI failure (see the palette-gate step in build.yml).
// The ramp consolidates the near-duplicate greys that had accreted across
// screens (0x1E1E1E vs 0x1A1A1A, 0x8A8A8A vs 0x888888, …) into one value
// per visual role, so "card grey" or "secondary text" can be changed in
// exactly one place.
// ─────────────────────────────────────────────────────────────────────────

// Surfaces (darkest → lightest)
val FlowpayBlack = Color(0xFF000000)

/** Screen background behind cards/lists. */
val FlowpaySurfaceDim = Color(0xFF0A0A0A)

/** Card / dialog surface. */
val FlowpayDarkGray = Color(0xFF1A1A1A)

/** Elevated surface: input fields, chips, avatars. */
val FlowpayMediumGray = Color(0xFF2A2A2A)

/** Borders, dividers, inactive track. */
val FlowpayLightGray = Color(0xFF333333)

/** Stronger outline / disabled container. */
val FlowpayOutlineGray = Color(0xFF4A4A4A)

/** Disabled content / faint hint. */
val FlowpayDisabledGray = Color(0xFF555555)

// Text (dimmest → brightest)
/** Placeholder / hint text. */
val FlowpayTextGray = Color(0xFF666666)

/** Secondary text: captions, labels, timestamps. */
val FlowpayTextLightGray = Color(0xFF888888)

/** Long-form body text on dark dialogs. */
val FlowpayTextPale = Color(0xFFCCCCCC)

val FlowpayTextWhite = Color(0xFFFFFFFF)

// Card Colors (light card variant)
val FlowpayCardBackground = Color(0xFFE8E8E8)
val FlowpayCardText = Color(0xFF000000)
val FlowpayCardSubtext = Color(0xFF4A4A4A)

// Accents
val FlowpayAccentBlue = Color(0xFF4A90E2)
val FlowpayAccentGreen = Color(0xFF4CAF50)

/** Bright green used as the light end of success gradients. */
val FlowpayAccentGreenBright = Color(0xFF43E97B)

// ─────────────────────────────────────────────────────────────────────────
// Transaction status palette. One color per outcome, used identically in
// the history list, detail dialog and result screen so a status never
// changes meaning between screens.
// ─────────────────────────────────────────────────────────────────────────

/** SUCCESS — bank confirmed. */
val FlowpayStatusSuccess = FlowpayAccentGreen

/** FAILED / declined, and destructive actions (delete, clear). */
val FlowpayStatusError = Color(0xFFF44336)

/** NEEDS_REVIEW / PENDING — user attention required. */
val FlowpayStatusWarning = Color(0xFFFF9800)

/** UNVERIFIED / CANCELLED — outcome unknown or nothing happened. Neutral:
 *  deliberately neither success-green nor failure-red. */
val FlowpayStatusNeutral = Color(0xFF9E9E9E)

/**
 * The single mapping from a [com.flowpay.app.data.TransactionStatus] string
 * to its display color. Replaces the byte-identical getStatusColor()
 * functions that had been copy-pasted into multiple screens.
 */
fun statusColor(status: String): Color = when (status.uppercase()) {
    "SUCCESS", "SUCCESSFUL", "COMPLETED" -> FlowpayStatusSuccess
    "FAILED", "DECLINED" -> FlowpayStatusError
    "PENDING", "NEEDS_REVIEW" -> FlowpayStatusWarning
    else -> FlowpayStatusNeutral // UNVERIFIED, CANCELLED, unknown
}
