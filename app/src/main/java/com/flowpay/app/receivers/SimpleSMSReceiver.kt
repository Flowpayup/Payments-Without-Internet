// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.flowpay.app.helpers.TransactionDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
        if (!detector.tryClaimSms(body)) {
            Log.d(TAG, "SMS already claimed by another pipeline")
            return
        }

        // Keep the process alive until parsing + persistence complete —
        // a fire-and-forget coroutine from onReceive() can be killed
        // mid-write once onReceive() returns.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // goAsync() grants ~10s before the system finishes the
                // receiver anyway; bound our own work below that so
                // pendingResult.finish() always runs on our terms.
                val completed = withTimeoutOrNull(8_000) {
                    SmsIngestionPipeline.ingest(context, detector, sender, body)
                }
                if (completed == null) {
                    Log.w(TAG, "SMS processing timed out before completion")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
