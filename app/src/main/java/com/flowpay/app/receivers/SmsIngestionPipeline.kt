package com.flowpay.app.receivers

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.flowpay.app.FlowpayApplication
import com.flowpay.app.helpers.TransactionDetector
import com.flowpay.app.repository.TransactionRepository
import com.flowpay.app.ui.activities.PaymentResultActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The live SMS -> transaction pipeline, shared by every ingestion entry
 * point: [SimpleSMSReceiver] (the real broadcast receiver) and, in debug
 * builds only, `DebugSmsInjectionReceiver`. Keeping this in one place means
 * the debug injection tool exercises exactly the same code a real bank SMS
 * would go through — parsing, session confirmation, persistence, and the
 * result-screen launch — not a parallel reimplementation of it.
 */
object SmsIngestionPipeline {

    private const val TAG = "SmsIngestionPipeline"

    suspend fun ingest(context: Context, detector: TransactionDetector, sender: String, body: String) {
        val transaction = detector.processSMS(sender, body)
        if (transaction == null) {
            Log.d(TAG, "Not a transaction SMS")
            return
        }

        // An active payment session owns its row — update it in place.
        // Only fall back to inserting a fresh row (QR/legacy flow)
        // when no session claimed the confirmation.
        val sessionManager = FlowpayApplication.from(context)?.paymentSessionManager
        val sessionTxnId = sessionManager?.onSmsConfirmed(transaction)
        if (sessionTxnId == null) {
            TransactionRepository.getInstance(context).saveTransaction(transaction)
            Log.d(TAG, "Transaction saved (no active session)")
        } else {
            Log.d(TAG, "Transaction confirmed into active session")
        }

        withContext(Dispatchers.Main) {
            // Broadcast for QRScannerActivity / UPI123 flow
            LocalBroadcastManager.getInstance(context)
                .sendBroadcast(Intent("com.flowpay.app.SMS_RECEIVED"))

            val successIntent = Intent(context, PaymentResultActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("transaction_id", sessionTxnId ?: transaction.transactionId)
                putExtra("amount", transaction.amount)
                putExtra("status", transaction.status)
                putExtra("bank_name", transaction.bankName)
                putExtra("message", transaction.smsExcerpt)
                putExtra("timestamp", transaction.timestamp)
                putExtra("upi_id", transaction.upiId)
                putExtra("transaction_type", transaction.transactionType)
                putExtra("recipient_name", transaction.recipientName)
                putExtra("phone_number", transaction.phoneNumber)
            }
            context.startActivity(successIntent)
        }
    }
}
