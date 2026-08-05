// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.payment

import com.flowpay.app.constants.AppConstants

/**
 * Builds the DTMF-encoded dial string for the UPI 123Pay IVR flow.
 *
 * Format: tel:<serviceNumber>,,1,<phoneNumber>,,<amount>,,1
 * where "," is a 2-second dialer pause and the digits between pauses are
 * DTMF tones consumed by the IVR menu (1 = "send money", then recipient,
 * then amount, then 1 = confirm).
 *
 * This object is intentionally pure (no Context, no Android dependencies)
 * so the exact dial string is locked by unit tests — a malformed string
 * here dials a wrong DTMF sequence against a live payment IVR.
 */
object Upi123CallStringBuilder {

    private val SERVICE_NUMBER_REGEX = Regex("^0?[1-9][0-9]{9,11}$")
    private val PHONE_REGEX = Regex(AppConstants.PHONE_NUMBER_PATTERN)
    private val WHOLE_RUPEES_REGEX = Regex("^[0-9]{1,6}$")

    sealed class Result {
        data class Valid(val callString: String) : Result()
        data class Invalid(val reason: String) : Result()
    }

    fun build(serviceNumber: String, phoneNumber: String, amount: String): Result {
        val service = serviceNumber.filter(Char::isDigit)
        val phone = phoneNumber.filter(Char::isDigit)
        val rupees = amount.trim()

        if (!SERVICE_NUMBER_REGEX.matches(service)) {
            return Result.Invalid("Invalid UPI service number")
        }
        if (!PHONE_REGEX.matches(phone)) {
            return Result.Invalid("Recipient must be a 10-digit mobile number")
        }
        // The IVR consumes whole-rupee DTMF digits; decimals cannot be dialled.
        if (!WHOLE_RUPEES_REGEX.matches(rupees)) {
            return Result.Invalid("Amount must be whole rupees (digits only)")
        }
        val value = rupees.toLongOrNull()
            ?: return Result.Invalid("Amount must be a number")
        if (value < AppConstants.MIN_AMOUNT_VALUE.toLong()) {
            return Result.Invalid("Minimum amount is ₹${AppConstants.MIN_AMOUNT_VALUE.toLong()}")
        }
        if (value > AppConstants.UPI123PAY_MAX_AMOUNT.toLong()) {
            return Result.Invalid(
                "Maximum ₹${AppConstants.UPI123PAY_MAX_AMOUNT.toLong()} per payment — " +
                    "the UPI 123Pay IVR does not accept ₹5,000 or more"
            )
        }

        return Result.Valid("tel:$service,,1,$phone,,$value,,1")
    }
}
