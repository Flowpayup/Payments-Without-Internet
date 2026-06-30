package com.flowpay.app.constants

/**
 * Application-wide constants to avoid magic numbers and improve maintainability
 */
object AppConstants {
    
    // Timeout values (in milliseconds)
    const val OVERLAY_SHOW_DELAY = 500L
    const val OVERLAY_BACKUP_DELAY = 1000L
    const val OVERLAY_FORCE_DELAY = 2000L
    const val PIN_ENTRY_ALERT_DELAY = 2000L
    const val USSD_DIAL_DELAY = 500L
    const val UPI_CALL_TIMEOUT = 60000L
    const val USSD_SESSION_TIMEOUT = 30000L
    const val USSD_STEP_DELAY = 5000L
    const val USSD_TOTAL_TIMEOUT = 50000L
    const val USSD_AUTO_DISMISS_DELAY = 5000L
    
    // Animation durations
    const val PROGRESS_ANIMATION_DURATION = 300L
    const val VIBRATION_DURATION = 200L
    
    // UI dimensions and limits
    const val MAX_PHONE_NUMBER_LENGTH = 10
    const val MAX_AMOUNT_LENGTH = 6
    const val MIN_AMOUNT_VALUE = 1.0
    const val MAX_AMOUNT_VALUE = 100000.0
    const val MAX_SENDER_LENGTH = 20
    
    // Progress steps
    const val USSD_PROGRESS_STEPS = 5
    const val USSD_FINAL_STEP = 5
    
    // Default values
    const val DEFAULT_UPI_SERVICE_NUMBER = "08045163666"
    const val DEFAULT_USSD_CODE = "*99*1*3#"

    // NPCI publishes more than one IVR endpoint for UPI 123Pay. Ordered
    // fallback list: if a payment fails against one number the user can be
    // offered the next. A user-configured override (KEY_UPI_SERVICE_NUMBER)
    // always takes precedence.
    val UPI_SERVICE_NUMBERS = listOf(
        DEFAULT_UPI_SERVICE_NUMBER,
        "08045163581"
    )

    // Per-transaction cap for the UPI 123Pay rail (RBI raised it from
    // Rs 5,000 to Rs 10,000 effective January 2025). Distinct from
    // MAX_AMOUNT_VALUE, which is the generic input ceiling.
    const val UPI123PAY_MAX_AMOUNT = 10000.0
    
    // SharedPreferences keys
    const val PREFS_NAME = "FlowPayPrefs"
    const val KEY_UPI_SERVICE_NUMBER = "upi_service_number"
    const val KEY_PENDING_OVERLAY = "pending_overlay"
    const val KEY_PENDING_PHONE = "pending_phone"
    const val KEY_PENDING_AMOUNT = "pending_amount"
    const val KEY_SETUP_COMPLETED = "setup_completed"
    const val KEY_TEST_COMPLETED = "test_configuration_completed"
    const val KEY_SELECTED_BANK = "selected_bank"

    // Broadcast actions
    const val ACTION_RESTORE_AUDIO = "com.flowpay.app.RESTORE_AUDIO"
    const val ACTION_CALL_TERMINATED = "com.flowpay.app.CALL_TERMINATED"
    const val ACTION_STOP_OVERLAY = "STOP_OVERLAY"
    
    // SMS patterns
    const val SMS_DEBIT_KEYWORDS = "debited|sent|transferred"
    const val SMS_SUCCESS_KEYWORDS = "success"
    
    // Bank keywords for SMS detection
    val BANK_KEYWORDS = listOf(
        "HDFC", "ICICI", "SBI", "AXIS", "KOTAK", "PNB", 
        "BOB", "CANARA", "UNION", "IDBI", "YES", "INDUS",
        "PAYTM", "PHONEPE", "GPAY"
    )
    
    // Regex patterns
    const val PHONE_NUMBER_PATTERN = "^[1-9][0-9]{9}$"
    const val AMOUNT_PATTERN = "^[0-9]+(\\.[0-9]{1,2})?$"
    const val SENDER_FORMAT_PATTERN = "^[A-Za-z0-9\\s\\-_]+$"
    const val UPI_ID_PATTERN = "UPI[0-9]{9,12}"
    const val VPA_PATTERN = "[a-zA-Z0-9._-]+@[a-zA-Z0-9]+"
    
    // Amount extraction patterns
    val AMOUNT_EXTRACTION_PATTERNS = listOf(
        "(?:rs\\.?|inr|₹)\\s*([0-9,]+(?:\\.[0-9]+)?)",
        "([0-9,]+(?:\\.[0-9]+)?)\\s*(?:rs\\.?|inr|₹)"
    )
    
    // Balance extraction patterns
    val BALANCE_EXTRACTION_PATTERNS = listOf(
        "(?:bal|balance)[:.]?\\s*(?:rs\\.?|inr|₹)?\\s*([0-9,]+(?:\\.[0-9]+)?)",
        "(?:available bal)[:.]?\\s*(?:rs\\.?|inr|₹)?\\s*([0-9,]+(?:\\.[0-9]+)?)"
    )
}
