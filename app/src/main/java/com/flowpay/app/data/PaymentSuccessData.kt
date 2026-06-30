package com.flowpay.app.data

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * Data class representing successful payment transaction details
 * Used for displaying transaction information in PaymentSuccessActivity
 */
data class PaymentSuccessData(
    val transactionId: String,
    val phoneNumber: String,
    val amount: String,
    val bankReference: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val paymentMethod: String = "UPI123",
    val recipientName: String? = null,
    val status: PaymentStatus = PaymentStatus.COMPLETED
) {
    /**
     * Get formatted amount with currency symbol
     */
    fun getFormattedAmount(): String {
        return try {
            val amountValue = amount.toDoubleOrNull() ?: 0.0
            NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(amountValue)
        } catch (e: Exception) {
            "₹$amount"
        }
    }
    
    /**
     * Get formatted timestamp
     */
    fun getFormattedTimestamp(): String {
        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }
    
    /**
     * Get masked phone number for display
     */
    fun getMaskedPhoneNumber(): String {
        return if (phoneNumber.length >= 10) {
            val start = phoneNumber.substring(0, 3)
            val end = phoneNumber.substring(phoneNumber.length - 3)
            "$start****$end"
        } else {
            phoneNumber
        }
    }
    
    /**
     * Get short transaction ID for display
     */
    fun getShortTransactionId(): String {
        return if (transactionId.length > 12) {
            "${transactionId.substring(0, 6)}...${transactionId.substring(transactionId.length - 6)}"
        } else {
            transactionId
        }
    }
}

enum class PaymentStatus {
    PENDING, COMPLETED, FAILED, CANCELLED
}

/**
 * Companion object with utility functions
 */
object PaymentSuccessDataUtils {
    /**
     * Create PaymentSuccessData from PaymentState.Success
     */
    fun fromPaymentState(successState: com.flowpay.app.states.PaymentState.Success): PaymentSuccessData {
        return PaymentSuccessData(
            transactionId = successState.transactionId,
            phoneNumber = successState.phoneNumber,
            amount = successState.amount,
            bankReference = successState.bankReference,
            timestamp = successState.timestamp
        )
    }
    
    /**
     * Create PaymentSuccessData from Intent extras
     */
    fun fromIntentExtras(intent: android.content.Intent): PaymentSuccessData? {
        return try {
            PaymentSuccessData(
                transactionId = intent.getStringExtra("transaction_id") ?: return null,
                phoneNumber = intent.getStringExtra("phone_number") ?: return null,
                amount = intent.getStringExtra("amount") ?: return null,
                bankReference = intent.getStringExtra("bank_reference"),
                timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis()),
                paymentMethod = intent.getStringExtra("payment_method") ?: "UPI123",
                recipientName = intent.getStringExtra("recipient_name")
            )
        } catch (e: Exception) {
            null
        }
    }
}
