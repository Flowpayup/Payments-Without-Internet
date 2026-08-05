// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.flowpay.app.helpers.TransactionDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug-only tool: replays a bank SMS through the exact same live pipeline
 * a real confirmation would go through ([SmsIngestionPipeline], shared with
 * [SimpleSMSReceiver]), without needing an Indian SIM, a real bank, or a
 * real *99#/123Pay call. This is how a payment confirmation can be
 * reproduced on any emulator. See docs/TESTING.md for the full recipe.
 *
 * Compiled only into debug builds — this whole source set (and its
 * manifest declaration in src/debug/AndroidManifest.xml) is absent from
 * release APKs, not merely gated by a runtime BuildConfig.DEBUG check.
 *
 * Two actions, mirroring the two things a real payment flow does before an
 * SMS can be claimed. The broadcasts MUST target this component with -n:
 * Android silently drops implicit broadcasts to manifest-declared
 * receivers. Quote the whole am command so the device shell doesn't split
 * the SMS body on spaces, and launch the app first — a force-stopped app
 * receives no broadcasts.
 *
 *  adb shell "am broadcast -n com.flowpay.app/.receivers.DebugSmsInjectionReceiver \
 *    -a com.flowpay.app.debug.START_OPERATION \
 *    --es operation_type UPI_123 --es expected_amount 500 --es phone_number 9876543210"
 *
 * `--es session_txn_id <id>` is optional; see [startOperation] for when it
 * matters.
 *
 *  adb shell "am broadcast -n com.flowpay.app/.receivers.DebugSmsInjectionReceiver \
 *    -a com.flowpay.app.debug.INJECT_SMS \
 *    --es sender VK-HDFCBK \
 *    --es body 'Rs.500.00 sent to KIRANA STORE from HDFC Bank A/c **1234 via UPI ref 512233440091'"
 */
class DebugSmsInjectionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DebugSmsInjection"
        const val ACTION_START_OPERATION = "com.flowpay.app.debug.START_OPERATION"
        const val ACTION_INJECT_SMS = "com.flowpay.app.debug.INJECT_SMS"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        when (intent.action) {
            ACTION_START_OPERATION -> startOperation(context, intent)
            ACTION_INJECT_SMS -> injectSms(context, intent)
            else -> Log.w(TAG, "Unknown debug action: ${intent.action}")
        }
    }

    private fun startOperation(context: Context, intent: Intent) {
        val operationType = intent.getStringExtra("operation_type") ?: "UPI_123"
        val expectedAmount = intent.getStringExtra("expected_amount")
        val phoneNumber = intent.getStringExtra("phone_number")
        // Optional, and the only way to reach the orphan-adoption path from
        // here: with no session_txn_id the window carries no row to adopt, so
        // an injected SMS always saves standalone and the process-death
        // reattach in SmsIngestionPipeline.resolveOwnerTxnId is never
        // exercised. Pass the txnId of a real PENDING row to test it.
        val sessionTxnId = intent.getStringExtra("session_txn_id")

        TransactionDetector.getInstance(context)
            .startOperation(operationType, expectedAmount, phoneNumber, sessionTxnId)
        Log.i(
            TAG,
            "Debug operation window started: type=$operationType amount=$expectedAmount " +
                "sessionTxnId=${sessionTxnId ?: "none"}"
        )
    }

    private fun injectSms(context: Context, intent: Intent) {
        val sender = intent.getStringExtra("sender")
        val body = intent.getStringExtra("body")
        if (sender.isNullOrBlank() || body.isNullOrBlank()) {
            Log.e(TAG, "INJECT_SMS requires non-empty 'sender' and 'body' extras")
            return
        }

        val detector = TransactionDetector.getInstance(context)
        if (!detector.shouldProcessSMS()) {
            Log.w(TAG, "No active payment operation — send START_OPERATION first")
            return
        }
        if (!detector.tryClaimSms(body)) {
            Log.w(TAG, "SMS already claimed (duplicate injection?)")
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i(TAG, "Injecting debug SMS from $sender")
                SmsIngestionPipeline.ingest(context, detector, sender, body)
            } catch (e: Exception) {
                Log.e(TAG, "Debug SMS injection failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
