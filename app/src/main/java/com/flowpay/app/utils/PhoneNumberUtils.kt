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
     * Compares two phone numbers for equality after normalization
     * @param phone1 First phone number
     * @param phone2 Second phone number
     * @return true if the normalized phone numbers match, false otherwise
     */
    fun isPhoneNumberMatch(phone1: String?, phone2: String?): Boolean {
        val normalized1 = normalizePhoneNumber(phone1)
        val normalized2 = normalizePhoneNumber(phone2)
        
        val isMatch = normalized1 == normalized2 && normalized1 != null
        Log.d(TAG, "Phone number match: '$phone1' vs '$phone2' = $isMatch")
        return isMatch
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
    
    /**
     * Validates if a phone number has the expected length for Indian mobile numbers
     * @param phoneNumber The phone number to validate
     * @param expectedLength Expected length (default 10 for Indian mobile)
     * @return true if the normalized phone number has the expected length
     */
    fun isValidLength(phoneNumber: String?, expectedLength: Int = 10): Boolean {
        val normalized = normalizePhoneNumber(phoneNumber)
        val isValid = normalized?.length == expectedLength
        return isValid
    }
    
    /**
     * Checks if a phone number is a valid Indian mobile number
     * @param phoneNumber The phone number to validate
     * @return true if it's a valid 10-digit Indian mobile number
     */
    fun isValidIndianMobile(phoneNumber: String?): Boolean {
        val normalized = normalizePhoneNumber(phoneNumber)
        return if (normalized != null && normalized.length == 10) {
            // Indian mobile numbers start with 6, 7, 8, or 9
            val firstDigit = normalized.first()
            val isValid = firstDigit in "6789"
            isValid
        } else {
            Log.d(TAG, "Indian mobile validation failed: invalid length")
            false
        }
    }
}

