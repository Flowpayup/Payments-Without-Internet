package com.flowpay.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.flowpay.app.FlowPayApplication
import com.flowpay.app.helpers.TransactionDetector
import com.flowpay.app.repository.TransactionRepository
import com.flowpay.app.ui.activities.PaymentSuccessActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SimpleSMSReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SimpleSMSReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        // Framework-provided PDU parsing (handles format + multipart correctly)
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val sender = messages[0]?.originatingAddress ?: return
        val body = messages.filterNotNull().joinToString("") { it.messageBody ?: "" }
        if (body.isBlank()) return

        Log.d(TAG, "SMS received")

        val detector = TransactionDetector.getInstance(context)
        if (!detector.shouldProcessSMS()) {
            Log.d(TAG, "No active payment operation, ignoring SMS")
            return
        }

        // Claim this exact message so the notification-listener pipeline
        // can't process the same SMS a second time.
        if (!detector.tryClaimSms(sender, body)) {
            Log.d(TAG, "SMS already claimed by another pipeline")
            return
        }

        // Keep the process alive until parsing + persistence complete —
        // a fire-and-forget coroutine from onReceive() can be killed
        // mid-write once onReceive() returns.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val transaction = detector.processSMS(sender, body)
                if (transaction == null) {
                    Log.d(TAG, "Not a transaction SMS")
                    return@launch
                }

                // An active payment session owns its row — update it in place.
                // Only fall back to inserting a fresh row (QR/legacy flow)
                // when no session claimed the confirmation.
                val sessionManager = FlowPayApplication.from(context)?.paymentSessionManager
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

                    val successIntent = Intent(context, PaymentSuccessActivity::class.java).apply {
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
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
