// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.constants

/**
 * Application-wide constants to avoid magic numbers and improve maintainability
 */
object AppConstants {

    // Timeout values (in milliseconds)
    const val USSD_SESSION_TIMEOUT = 30000L

    // UI dimensions and limits
    const val MIN_AMOUNT_VALUE = 1.0
    const val MAX_AMOUNT_VALUE = 100000.0

    // Default values
    const val DEFAULT_UPI_SERVICE_NUMBER = "08045163666"

    // Per-transaction cap for the UPI 123Pay IVR rail: the flow accepts
    // amounts strictly below Rs 5,000, so Rs 4,999 is the highest that goes
    // through. NPCI's published ceiling for 123Pay is higher, but what matters
    // here is what the IVR actually accepts — a payment above this is rejected
    // mid-call, after the user has already dialled. Distinct from
    // MAX_AMOUNT_VALUE, which is the generic input ceiling.
    const val UPI123PAY_MAX_AMOUNT = 4999.0

    // SharedPreferences keys
    const val PREFS_NAME = "FlowpayPrefs"
    const val KEY_SETUP_COMPLETED = "setup_completed"
    const val KEY_TEST_COMPLETED = "test_configuration_completed"
    const val KEY_SELECTED_BANK = "selected_bank"

    // Set once we've asked for POST_NOTIFICATIONS at a payment, so the
    // contextual one-shot request never re-prompts on later payments.
    const val KEY_NOTIFICATIONS_ASKED = "notifications_permission_asked"

    // Regex patterns
    const val PHONE_NUMBER_PATTERN = "^[1-9][0-9]{9}$"
}
