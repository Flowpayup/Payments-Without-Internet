package com.flowpay.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for storing transaction data.
 *
 * The primary key is a client-generated UUID created when the payment is
 * initiated; the bank's own reference number from the confirming SMS is
 * stored separately in [bankRef]. Lifecycle values for [status] are the
 * constants in [TransactionStatus].
 */
@Entity(
    tableName = "transactions",
    indices = [Index("timestamp"), Index("status")]
)
data class Transaction(
    @PrimaryKey
    val transactionId: String,
    val amount: String,
    val status: String,
    val bankName: String,
    /**
     * Privacy-safe summary ("₹500 debited — HDFC Bank · Ref …") built from
     * parsed fields. Raw SMS bodies are deliberately NOT stored (v3
     * migration removed them); the verbatim message stays in the user's
     * SMS inbox.
     */
    val smsExcerpt: String,
    val timestamp: Long = System.currentTimeMillis(),
    val upiId: String? = null,
    val transactionType: String = "DEBIT",
    val recipientName: String? = null,
    val phoneNumber: String? = null,
    val bankRef: String? = null,
    val deadlineAt: Long? = null,
    val source: String? = null,
    val verifiedAt: Long? = null
) {
    /**
     * Convert to PaymentDetails for UI compatibility
     */
    fun toPaymentDetails(): PaymentDetails {
        return PaymentDetails(
            id = transactionId,
            recipientName = recipientName,
            phoneNumber = phoneNumber ?: "",
            amount = amount.toDoubleOrNull() ?: 0.0,
            timestamp = timestamp,
            status = when (status.uppercase()) {
                "SUCCESS", "SUCCESSFUL", "COMPLETED" -> PaymentStatus.COMPLETED
                "PENDING", "UNVERIFIED" -> PaymentStatus.PENDING
                "CANCELLED" -> PaymentStatus.CANCELLED
                "FAILED", "DECLINED" -> PaymentStatus.FAILED
                else -> PaymentStatus.PENDING
            }
        )
    }

    /**
     * Convert from SimpleTransaction
     */
    companion object {
        fun fromSimpleTransaction(simpleTransaction: com.flowpay.app.helpers.SimpleTransaction): Transaction {
            return Transaction(
                transactionId = simpleTransaction.transactionId,
                amount = simpleTransaction.amount,
                status = simpleTransaction.status,
                bankName = simpleTransaction.bankName,
                smsExcerpt = simpleTransaction.smsExcerpt,
                timestamp = simpleTransaction.timestamp,
                upiId = simpleTransaction.upiId,
                transactionType = simpleTransaction.transactionType,
                recipientName = simpleTransaction.recipientName,
                phoneNumber = simpleTransaction.phoneNumber,
                bankRef = simpleTransaction.transactionId,
                source = TransactionSource.SMS
            )
        }
    }
}
