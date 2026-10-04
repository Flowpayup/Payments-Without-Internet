// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
     */
)

// ─────────────────────────────────────────────────────────────────────────
// "Bank-Stamped" type roles.
//
// Two deliberate departures from the single default sans everywhere:
// a serif display face for headlines/amounts (reads like a printed
// ledger heading, not an app splash) and monospace for anything that is
// a literal record — bank reference numbers, UPI ref serials, account
// suffixes — the way a real receipt sets its transaction ID. Both use
// platform-bundled font families (Serif / Monospace): no font files to
// ship, no license to track, no download risk.
// ─────────────────────────────────────────────────────────────────────────

/** Screen titles, status headlines, the big payment amount. */
val FlowpayDisplayStyle = TextStyle(
    fontFamily = FontFamily.Serif,
    fontWeight = FontWeight.Bold
)

/** Bank references, UPI ref numbers, masked account suffixes, timestamps
 *  inside a receipt-style card. */
val FlowpayMonoStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal
)
