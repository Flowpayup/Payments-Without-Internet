package com.flowpay.app.states

import java.util.UUID

/**
 * Enum for different payment types
 */
enum class PaymentType {
    MANUAL_TRANSFER,  // UPI call flow
    QR_SCANNING       // USSD flow
}

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
     * State when payment is being retried after a failure
     * @param retryCount Current retry attempt number
     * @param maxRetries Maximum number of retries allowed
     * @param phoneNumber The recipient's phone number
     * @param amount The payment amount
     * @param transactionId Unique transaction identifier
     */
    data class Retrying(
        val retryCount: Int,
        val maxRetries: Int,
        val phoneNumber: String,
        val amount: String,
        val transactionId: String
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
    
    // QR Payment States
    
    /**
     * State when QR payment is being initiated
     * @param vpa The recipient's VPA
     * @param amount The payment amount
     * @param transactionId Unique transaction identifier
     */
    data class QRPaymentInitiating(
        val vpa: String,
        val amount: String,
        val transactionId: String = UUID.randomUUID().toString()
    ) : PaymentState()
    
    /**
     * State when QR payment is in progress
     * @param vpa The recipient's VPA
     * @param amount The payment amount
     * @param step Current step in the payment process
     * @param progress Progress percentage (0.0 to 1.0)
     * @param transactionId Unique transaction identifier
     */
    data class QRPaymentInProgress(
        val vpa: String,
        val amount: String,
        val step: String,
        val progress: Float,
        val transactionId: String
    ) : PaymentState()
    
    /**
     * State when QR payment is waiting for verification
     * @param vpa The recipient's VPA
     * @param amount The payment amount
     * @param timeout Timeout in milliseconds
     * @param transactionId Unique transaction identifier
     */
    data class QRPaymentWaitingForVerification(
        val vpa: String,
        val amount: String,
        val timeout: Long,
        val transactionId: String
    ) : PaymentState()
    
    /**
     * State when QR payment is successfully completed
     * @param vpa The recipient's VPA
     * @param amount The payment amount
     * @param transactionId Unique transaction identifier
     */
    data class QRPaymentSuccess(
        val vpa: String,
        val amount: String,
        val transactionId: String
    ) : PaymentState()
    
    /**
     * State when QR payment fails
     * @param vpa The recipient's VPA
     * @param amount The payment amount
     * @param error Error message describing the failure
     * @param transactionId Unique transaction identifier
     */
    data class QRPaymentFailed(
        val vpa: String,
        val amount: String,
        val error: String,
        val transactionId: String
    ) : PaymentState()
    
    /**
     * Checks if the payment is in a terminal state (Success, Failed, Cancelled)
     */
    fun isTerminal(): Boolean = this is Success || this is Failed || this is Cancelled || 
                                this is QRPaymentSuccess || this is QRPaymentFailed
    
    /**
     * Checks if the payment is in progress
     */
    fun isInProgress(): Boolean = this is Initiating || this is InProgress || this is WaitingForVerification || this is Retrying ||
                                 this is QRPaymentInitiating || this is QRPaymentInProgress || this is QRPaymentWaitingForVerification
    
    /**
     * Checks if the payment can be retried
     */
    fun canRetry(): Boolean = when (this) {
        is Failed -> canRetry
        is Timeout -> true
        is QRPaymentFailed -> true
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
        is Cancelled -> this.transactionId
        is Retrying -> this.transactionId
        is Timeout -> this.transactionId
        is QRPaymentInitiating -> this.transactionId
        is QRPaymentInProgress -> this.transactionId
        is QRPaymentWaitingForVerification -> this.transactionId
        is QRPaymentSuccess -> this.transactionId
        is QRPaymentFailed -> this.transactionId
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
        is Cancelled -> this.phoneNumber
        is Retrying -> this.phoneNumber
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
        is Cancelled -> this.amount
        is Retrying -> this.amount
        is Timeout -> this.amount
        is QRPaymentInitiating -> this.amount
        is QRPaymentInProgress -> this.amount
        is QRPaymentWaitingForVerification -> this.amount
        is QRPaymentSuccess -> this.amount
        is QRPaymentFailed -> this.amount
        else -> null
    }
    
    /**
     * Gets the VPA if available (for QR payments)
     */
    fun getVpaValue(): String? = when (this) {
        is QRPaymentInitiating -> this.vpa
        is QRPaymentInProgress -> this.vpa
        is QRPaymentWaitingForVerification -> this.vpa
        is QRPaymentSuccess -> this.vpa
        is QRPaymentFailed -> this.vpa
        else -> null
    }
    
    /**
     * Gets the payment type
     */
    fun getPaymentType(): PaymentType? = when (this) {
        is Initiating, is InProgress, is WaitingForVerification, is Success, is Failed, is Cancelled, is Retrying, is Timeout -> PaymentType.MANUAL_TRANSFER
        is QRPaymentInitiating, is QRPaymentInProgress, is QRPaymentWaitingForVerification, is QRPaymentSuccess, is QRPaymentFailed -> PaymentType.QR_SCANNING
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
    is PaymentState.Cancelled -> "Payment cancelled: $reason"
    is PaymentState.Retrying -> "Retrying payment (${retryCount}/${maxRetries})..."
    is PaymentState.Timeout -> "Payment timeout: ${timeoutType.name}"
    is PaymentState.QRPaymentInitiating -> "Initiating QR payment..."
    is PaymentState.QRPaymentInProgress -> "Processing QR payment: $step"
    is PaymentState.QRPaymentWaitingForVerification -> "Waiting for QR payment verification..."
    is PaymentState.QRPaymentSuccess -> "QR payment successful!"
    is PaymentState.QRPaymentFailed -> "QR payment failed: $error"
}

fun PaymentState.getProgressPercentage(): Float = when (this) {
    is PaymentState.Idle -> 0f
    is PaymentState.Initiating -> 0.1f
    is PaymentState.InProgress -> progress
    is PaymentState.WaitingForVerification -> 0.7f
    is PaymentState.Success -> 1f
    is PaymentState.Failed -> 0f
    is PaymentState.Cancelled -> 0f
    is PaymentState.Retrying -> 0.1f
    is PaymentState.Timeout -> 0f
    is PaymentState.QRPaymentInitiating -> 0.1f
    is PaymentState.QRPaymentInProgress -> progress
    is PaymentState.QRPaymentWaitingForVerification -> 0.7f
    is PaymentState.QRPaymentSuccess -> 1f
    is PaymentState.QRPaymentFailed -> 0f
}
