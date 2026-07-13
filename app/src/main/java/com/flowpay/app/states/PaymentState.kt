package com.flowpay.app.states

import java.util.UUID

/**
 * Sealed class hierarchy for payment states providing type-safe state management
 * and clear state transitions for the UPI123 payment system
 */
sealed class PaymentState {
    
    /**
     * Initial state when no payment is in progress
     */
    object Idle : PaymentState()
    
    /**
     * State when payment is being initiated
     * @param phoneNumber The recipient's phone number
     * @param amount The payment amount
     * @param transactionId Unique transaction identifier
     */
    data class Initiating(
        val phoneNumber: String,
        val amount: String,
        val transactionId: String = UUID.randomUUID().toString()
    ) : PaymentState()
    
    /**
     * State when UPI123 call is in progress
     * @param step Current step in the call process
     * @param progress Progress percentage (0.0 to 1.0)
     * @param phoneNumber The recipient's phone number
     * @param amount The payment amount
     * @param transactionId Unique transaction identifier
     */
    data class InProgress(
        val step: String,
        val progress: Float,
        val phoneNumber: String,
        val amount: String,
        val transactionId: String
    ) : PaymentState()
    
    /**
     * State when waiting for bank verification call
     * @param timeout Timeout in milliseconds
     * @param phoneNumber The recipient's phone number
     * @param amount The payment amount
     * @param transactionId Unique transaction identifier
     */
    data class WaitingForVerification(
        val timeout: Long,
        val phoneNumber: String,
        val amount: String,
        val transactionId: String
    ) : PaymentState()
    
    /**
     * State when payment is successfully completed
     * @param transactionId Unique transaction identifier
     * @param phoneNumber The recipient's phone number
     * @param amount The payment amount
     * @param bankReference Bank's reference number
     * @param timestamp Completion timestamp
     */
    data class Success(
        val transactionId: String,
        val phoneNumber: String,
        val amount: String,
        val bankReference: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    ) : PaymentState()
    
    /**
     * State when payment fails
     * @param error Error message describing the failure
     * @param errorCode Specific error code if available
     * @param phoneNumber The recipient's phone number
     * @param amount The payment amount
     * @param transactionId Unique transaction identifier
     * @param canRetry Whether the payment can be retried
     */
    data class Failed(
        val error: String,
        val errorCode: String? = null,
        val phoneNumber: String,
        val amount: String,
        val transactionId: String,
        val canRetry: Boolean = true
    ) : PaymentState()
    
    /**
     * Terminal state when a bank SMS arrived during the payment but did not
     * match what was sent (e.g. a different amount). The outcome is recorded
     * but must be reviewed by the user against their bank statement.
     * @param transactionId Unique transaction identifier
     * @param phoneNumber The recipient's phone number
     * @param amount The payment amount that was requested
     */
    data class NeedsReview(
        val transactionId: String,
        val phoneNumber: String,
        val amount: String
    ) : PaymentState()

    /**
     * State when payment is cancelled by user
     * @param phoneNumber The recipient's phone number
     * @param amount The payment amount
     * @param transactionId Unique transaction identifier
     * @param reason Reason for cancellation
     */
    data class Cancelled(
        val phoneNumber: String,
        val amount: String,
        val transactionId: String,
        val reason: String = "User cancelled"
    ) : PaymentState()
    
    /**
     * State when payment is in a timeout scenario
     * @param timeoutType Type of timeout (call, verification, etc.)
     * @param phoneNumber The recipient's phone number
     * @param amount The payment amount
     * @param transactionId Unique transaction identifier
     */
    data class Timeout(
        val timeoutType: TimeoutType,
        val phoneNumber: String,
        val amount: String,
        val transactionId: String
    ) : PaymentState()

    /**
     * Checks if the payment is in a terminal state (Success, Failed, Cancelled)
     */
    fun isTerminal(): Boolean = this is Success || this is Failed || this is Cancelled || this is NeedsReview

    /**
     * Checks if the payment is in progress
     */
    fun isInProgress(): Boolean = this is Initiating || this is InProgress || this is WaitingForVerification

    /**
     * Checks if the payment can be retried
     */
    fun canRetry(): Boolean = when (this) {
        is Failed -> canRetry
        is Timeout -> true
        else -> false
    }

    /**
     * Gets the transaction ID if available
     */
    fun getTransactionIdValue(): String? = when (this) {
        is Initiating -> this.transactionId
        is InProgress -> this.transactionId
        is WaitingForVerification -> this.transactionId
        is Success -> this.transactionId
        is Failed -> this.transactionId
        is NeedsReview -> this.transactionId
        is Cancelled -> this.transactionId
        is Timeout -> this.transactionId
        else -> null
    }

    /**
     * Gets the phone number if available
     */
    fun getPhoneNumberValue(): String? = when (this) {
        is Initiating -> this.phoneNumber
        is InProgress -> this.phoneNumber
        is WaitingForVerification -> this.phoneNumber
        is Success -> this.phoneNumber
        is Failed -> this.phoneNumber
        is NeedsReview -> this.phoneNumber
        is Cancelled -> this.phoneNumber
        is Timeout -> this.phoneNumber
        else -> null
    }

    /**
     * Gets the amount if available
     */
    fun getAmountValue(): String? = when (this) {
        is Initiating -> this.amount
        is InProgress -> this.amount
        is WaitingForVerification -> this.amount
        is Success -> this.amount
        is Failed -> this.amount
        is NeedsReview -> this.amount
        is Cancelled -> this.amount
        is Timeout -> this.amount
        else -> null
    }
}

/**
 * Enum for different types of timeouts
 */
enum class TimeoutType {
    CALL_INITIATION,
    BANK_VERIFICATION,
    USER_RESPONSE,
    NETWORK_CONNECTION
}

/**
 * Extension functions for PaymentState
 */
fun PaymentState.getDisplayMessage(): String = when (this) {
    is PaymentState.Idle -> "Ready to make payment"
    is PaymentState.Initiating -> "Initiating payment..."
    is PaymentState.InProgress -> "Processing: $step"
    is PaymentState.WaitingForVerification -> "Waiting for bank verification..."
    is PaymentState.Success -> "Payment successful!"
    is PaymentState.Failed -> "Payment failed: $error"
    is PaymentState.NeedsReview -> "Payment needs review — check your bank statement"
    is PaymentState.Cancelled -> "Payment cancelled: $reason"
    is PaymentState.Timeout -> "Payment timeout: ${timeoutType.name}"
}

fun PaymentState.getProgressPercentage(): Float = when (this) {
    is PaymentState.Idle -> 0f
    is PaymentState.Initiating -> 0.1f
    is PaymentState.InProgress -> progress
    is PaymentState.WaitingForVerification -> 0.7f
    is PaymentState.Success -> 1f
    is PaymentState.Failed -> 0f
    is PaymentState.NeedsReview -> 1f
    is PaymentState.Cancelled -> 0f
    is PaymentState.Timeout -> 0f
}
