package com.flowpay.app.services

import android.app.Notification
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.flowpay.app.helpers.AudioStateManager
import com.flowpay.app.helpers.TransactionDetector
import com.flowpay.app.repository.TransactionRepository
import com.flowpay.app.ui.activities.PaymentSuccessActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FlowPayNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "FlowPayNotifListener"

        private val SMS_APP_PACKAGES = setOf(
            "com.android.mms",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.oneplus.mms",
            "com.miui.sms",
            "com.bbm",
            "com.hihonor.messaging"
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in SMS_APP_PACKAGES) return

        // Fallback-only ingestion: when RECEIVE_SMS is granted the broadcast
        // receiver is the (faster, more reliable) primary pipeline, so this
        // listener stays inert unless the user explicitly enables it in
        // settings. Reading SMS via two always-on paths doubled both the
        // race surface and the privacy surface.
        val receiveSmsGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            applicationContext, android.Manifest.permission.RECEIVE_SMS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val fallbackEnabled = applicationContext
            .getSharedPreferences("flowpay_settings", MODE_PRIVATE)
            .getBoolean("notification_fallback_enabled", false)
        if (receiveSmsGranted && !fallbackEnabled) return

        val extras = sbn.notification.extras
        val sender = extras.getString(Notification.EXTRA_TITLE) ?: return
        val body   = extras.getString(Notification.EXTRA_TEXT)  ?: return

        Log.d(TAG, "SMS notification received")

        val detector = TransactionDetector.getInstance(applicationContext)

        if (!detector.shouldProcessSMS()) {
            Log.d(TAG, "No active payment operation, ignoring notification")
            return
        }

        // The broadcast receiver is the primary pipeline; skip anything it
        // already claimed so the same SMS is never processed twice.
        if (!detector.tryClaimSms(sender, body)) {
            Log.d(TAG, "SMS already claimed by another pipeline")
            return
        }

        val transaction = detector.processSMS(sender, body) ?: run {
            Log.d(TAG, "Not a transaction notification")
            return
        }

        Log.d(TAG, "Transaction detected (status=${transaction.status})")

        // For UPI 123 — mute call audio immediately (same as SimpleSMSReceiver)
        val operationType = detector.getOperationType()
        if (operationType == "UPI_123") {
            Log.d(TAG, "UPI 123 transaction — muting call immediately")
            Handler(Looper.getMainLooper()).post {
                val muted = AudioStateManager.muteCallAudio(applicationContext)
                if (muted) {
                    Log.d(TAG, "Call audio muted successfully")
                    Toast.makeText(applicationContext, "Call muted - Payment successful", Toast.LENGTH_SHORT).show()
                } else {
                    Log.e(TAG, "Failed to mute call audio")
                }
            }
        }

        // An active payment session owns its row — update it in place;
        // otherwise fall back to inserting a fresh row (QR/legacy flow).
        val sessionTxnId = com.flowpay.app.FlowPayApplication.from(applicationContext)
            ?.paymentSessionManager?.onSmsConfirmed(transaction)
        if (sessionTxnId == null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    TransactionRepository.getInstance(applicationContext).saveTransaction(transaction)
                    Log.d(TAG, "Transaction saved (no active session)")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save transaction", e)
                }
            }
        }

        // Broadcast to dismiss USSD overlay
        LocalBroadcastManager.getInstance(applicationContext)
            .sendBroadcast(Intent("DISMISS_OVERLAY"))

        // Broadcast for QRScannerActivity
        LocalBroadcastManager.getInstance(applicationContext)
            .sendBroadcast(Intent("com.flowpay.app.SMS_RECEIVED"))

        // Launch PaymentSuccessActivity
        val intent = Intent(applicationContext, PaymentSuccessActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("transaction_id",   transaction.transactionId)
            putExtra("amount",           transaction.amount)
            putExtra("status",           transaction.status)
            putExtra("bank_name",        transaction.bankName)
            putExtra("message",          transaction.smsExcerpt)
            putExtra("timestamp",        transaction.timestamp)
            putExtra("upi_id",           transaction.upiId)
            putExtra("transaction_type", transaction.transactionType)
            putExtra("recipient_name",   transaction.recipientName)
            putExtra("phone_number",     transaction.phoneNumber)
        }
        applicationContext.startActivity(intent)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // No-op
    }
}
