// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────────────
// "Bank-Stamped" shape tokens.
//
// The rest of the app (and most AI-generated fintech UI) rounds every
// surface the same 12-20dp regardless of what it is. Here, roundness is
// reserved for one meaning — a status/seal badge — so a record (a ledger
// row, a form field, a card) reads as rectangular and a badge reads as
// a pill; the shape itself now carries information instead of decoration.
// ─────────────────────────────────────────────────────────────────────────

/** Records: transaction rows, form fields, receipt/detail cards. */
val FlowpayRecordShape = RoundedCornerShape(6.dp)

/** Slightly larger record shape for full-width containers (bottom sheets'
 *  inner cards, setup section cards). */
val FlowpayRecordShapeLarge = RoundedCornerShape(8.dp)

/** Status pills, seal badges, chips — the only rounded-pill surfaces. */
val FlowpaySealPillShape = RoundedCornerShape(percent = 50)

/** Bottom sheet top corners. */
val FlowpaySheetShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
