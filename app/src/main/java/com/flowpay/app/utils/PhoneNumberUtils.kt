// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.utils

import android.util.Log

/**
 * Utility class for phone number operations and validation
 * Handles normalization and comparison of phone numbers with different formats
 */
object PhoneNumberUtils {

    private const val TAG = "PhoneNumberUtils"

    /**
     * Normalizes a phone number by removing all non-digit characters
     * @param phoneNumber The phone number to normalize
     * @return Normalized phone number or null if input is invalid
     */
    fun normalizePhoneNumber(phoneNumber: String?): String? {
        if (phoneNumber.isNullOrBlank()) {
            Log.d(TAG, "Phone number is null or blank")
            return null
        }

        val normalized = phoneNumber.replace(Regex("[^0-9]"), "")
        return normalized
    }

    /**
     * Extracts the last N digits from a phone number for display purposes
     * @param phoneNumber The phone number to format
     * @param lastDigits Number of last digits to show (default 4)
     * @return Formatted phone number with dots for hidden digits
     */
    fun formatPhoneForDisplay(phoneNumber: String?, lastDigits: Int = 4): String {
        if (phoneNumber.isNullOrBlank()) {
            return "••••••0000"
        }

        val normalized = normalizePhoneNumber(phoneNumber) ?: return "••••••0000"

        return if (normalized.length >= lastDigits) {
            val dots = "•".repeat(normalized.length - lastDigits)
            "$dots${normalized.takeLast(lastDigits)}"
        } else {
            phoneNumber
        }
    }
}
