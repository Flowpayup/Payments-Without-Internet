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

    // Per-transaction cap for the UPI 123Pay rail (RBI raised it from
    // Rs 5,000 to Rs 10,000 effective January 2025). Distinct from
    // MAX_AMOUNT_VALUE, which is the generic input ceiling.
    const val UPI123PAY_MAX_AMOUNT = 10000.0

    // SharedPreferences keys
    const val PREFS_NAME = "FlowpayPrefs"
    const val KEY_UPI_SERVICE_NUMBER = "upi_service_number"
    const val KEY_SETUP_COMPLETED = "setup_completed"
    const val KEY_TEST_COMPLETED = "test_configuration_completed"
    const val KEY_SELECTED_BANK = "selected_bank"

    // Regex patterns
    const val PHONE_NUMBER_PATTERN = "^[1-9][0-9]{9}$"
}
