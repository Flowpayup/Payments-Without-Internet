package com.flowpay.app.services

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.flowpay.app.helpers.TransactionDetector
import com.flowpay.app.receivers.SmsIngestionPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FlowpayNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "FlowpayNotifListener"

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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        val body = extras.getString(Notification.EXTRA_TEXT) ?: return

        Log.d(TAG, "SMS notification received")

        val detector = TransactionDetector.getInstance(applicationContext)

        if (!detector.shouldProcessSMS()) {
            Log.d(TAG, "No active payment operation, ignoring notification")
            return
        }

        // The broadcast receiver is the primary pipeline; skip anything it
        // already claimed so the same SMS is never processed twice.
        if (!detector.tryClaimSms(body)) {
            Log.d(TAG, "SMS already claimed by another pipeline")
            return
        }

        // Same shared pipeline as the broadcast receiver — parsing, session
        // confirmation / orphaned-row reattach, persistence, broadcasts, and
        // the result-screen launch. This listener used to reimplement all of
        // that (with drift: it passed the bank ref instead of the session
        // txnId to the result screen); now the two pipelines cannot diverge.
        serviceScope.launch {
            try {
                SmsIngestionPipeline.ingest(applicationContext, detector, sender, body)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process SMS notification", e)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // No-op
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
