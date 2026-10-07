// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.payment

/** Never truncate a contact number into a different payment destination. */
object IndianMobileNumber {
    private const val MOBILE_LENGTH = 10

    @Suppress("ReturnCount") // Guard invalid input before choosing an explicit country prefix.
    fun normalize(value: String): String? {
        if (!value.matches(Regex("[0-9+()\\s-]+"))) return null
        val compact = value.replace(Regex("[()\\s-]"), "")
        val mobile = when {
            compact.startsWith("+91") -> compact.removePrefix("+91")
            compact.startsWith("0091") -> compact.removePrefix("0091")
            compact.startsWith("91") && compact.length == MOBILE_LENGTH + "91".length -> compact.removePrefix("91")
            compact.length == MOBILE_LENGTH -> compact
            else -> return null
        }
        return mobile.takeIf { PaymentInputValidator.isValidPhoneNumber(it) }
    }
}
