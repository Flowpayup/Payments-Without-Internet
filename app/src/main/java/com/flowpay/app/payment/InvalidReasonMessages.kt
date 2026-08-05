// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.payment

import android.content.Context
import com.flowpay.app.R
import com.flowpay.app.constants.AppConstants
import com.flowpay.app.utils.CurrencyFormat

/**
 * The UI edge where a validation [Upi123CallStringBuilder.Reason] becomes a
 * sentence a user reads.
 *
 * Keeping this out of [Upi123CallStringBuilder] is the point: that object is
 * pure and unit-tested precisely because it decides a live payment's DTMF
 * string, and giving it a Context to build English would have both broken
 * that and parked user-facing copy where no copy gate looks.
 *
 * The two amount messages take their numbers from [AppConstants] and render
 * them through [CurrencyFormat], so the ceiling quoted to the user cannot
 * drift from the ceiling actually enforced — the previous copy hardcoded
 * "does not accept ₹5,000 or more" next to an interpolated cap, two
 * statements of one number that were free to disagree.
 */
fun Upi123CallStringBuilder.Reason.messageFor(context: Context): String = when (this) {
    Upi123CallStringBuilder.Reason.SERVICE_NUMBER ->
        context.getString(R.string.error_invalid_service_number)

    Upi123CallStringBuilder.Reason.RECIPIENT_NUMBER ->
        context.getString(R.string.error_invalid_phone_number)

    Upi123CallStringBuilder.Reason.AMOUNT_NOT_WHOLE_RUPEES ->
        context.getString(R.string.error_amount_whole_rupees_only)

    Upi123CallStringBuilder.Reason.AMOUNT_NOT_A_NUMBER ->
        context.getString(R.string.error_invalid_amount)

    Upi123CallStringBuilder.Reason.AMOUNT_BELOW_MINIMUM ->
        context.getString(
            R.string.error_amount_below_minimum,
            CurrencyFormat.inr(AppConstants.MIN_AMOUNT_VALUE)
        )

    Upi123CallStringBuilder.Reason.AMOUNT_ABOVE_CAP ->
        context.getString(
            R.string.error_amount_above_123pay_cap,
            CurrencyFormat.inr(AppConstants.UPI123PAY_MAX_AMOUNT)
        )
}
