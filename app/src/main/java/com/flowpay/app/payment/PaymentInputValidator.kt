package com.flowpay.app.payment

import com.flowpay.app.constants.AppConstants

/**
 * Pure validation of user-entered payment inputs — no Android dependencies,
 * so it is directly unit-testable. Extracted from CallManager, which needed
 * an Activity/Service Context and thus couldn't be exercised in a JVM test.
 * The dial-string construction that consumes these values is validated
 * separately in [Upi123CallStringBuilder].
 */
object PaymentInputValidator {

    /** Exactly 10 digits, no leading zero (per [AppConstants.PHONE_NUMBER_PATTERN]). */
    fun isValidPhoneNumber(phoneNumber: String?): Boolean {
        if (phoneNumber.isNullOrBlank()) return false
        return phoneNumber.matches(Regex(AppConstants.PHONE_NUMBER_PATTERN))
    }

    /** A positive amount within the generic input range (₹1 … ₹1,00,000). */
    fun isValidAmount(amount: String?): Boolean {
        val amountValue = amount?.toDoubleOrNull() ?: return false
        return amountValue >= AppConstants.MIN_AMOUNT_VALUE && amountValue <= AppConstants.MAX_AMOUNT_VALUE
    }
}
