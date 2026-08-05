// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.features.qr_scanner.domain

import android.content.Context
import com.flowpay.app.R

/**
 * The UI edge where a [QRCodeParser.Reason] becomes the sentence shown in the
 * scanner's inline banner ("Not a valid UPI payment QR — %1$s. Try another
 * code.").
 *
 * Lives beside the parser rather than inside it so [QRCodeParser] keeps no
 * Context and stays testable on a plain JVM.
 */
fun QRCodeParser.Reason.messageFor(context: Context): String = when (this) {
    QRCodeParser.Reason.EMPTY ->
        context.getString(R.string.qr_reason_empty)

    QRCodeParser.Reason.NOT_A_UPI_QR ->
        context.getString(R.string.qr_reason_not_upi)

    QRCodeParser.Reason.MALFORMED ->
        context.getString(R.string.qr_reason_malformed)

    QRCodeParser.Reason.NO_PAYEE_ADDRESS ->
        context.getString(R.string.qr_reason_no_payee)

    QRCodeParser.Reason.INVALID_PAYEE_ADDRESS ->
        context.getString(R.string.qr_reason_invalid_payee)

    QRCodeParser.Reason.INVALID_AMOUNT ->
        context.getString(R.string.qr_reason_invalid_amount)
}
