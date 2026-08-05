// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.receivers

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.flowpay.app.FlowpayApplication
import com.flowpay.app.helpers.TransactionDetector
import com.flowpay.app.payment.sms.SimpleTransaction
import com.flowpay.app.repository.TransactionRepository
import com.flowpay.app.ui.activities.PaymentResultActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The live SMS -> transaction pipeline, shared by every ingestion entry
 * point: [SimpleSMSReceiver] (the real broadcast receiver), the
 * notification-listener fallback, and, in debug builds only,
 * `DebugSmsInjectionReceiver`. Keeping this in one place means the debug
 * injection tool exercises exactly the same code a real bank SMS would go
 * through — parsing, session confirmation, persistence, and the
 * result-screen launch — not a parallel reimplementation of it.
 */
object SmsIngestionPipeline {

    private const val TAG = "SmsIngestionPipeline"

    suspend fun ingest(context: Context, detector: TransactionDetector, sender: String, body: String) {
        // Captured BEFORE processSMS: a consuming match clears the operation
        // window (including this id).
        val windowTxnId = detector.getSessionTxnId()

        val transaction = detector.processSMS(sender, body)
        if (transaction == null) {
            Log.d(TAG, "Not a transaction SMS")
            return
        }

        // An active payment session owns its row — update it in place. With
        // no live session (QR flow, or the process died mid-payment), try to
        // reattach the confirmation to the still-PENDING row recorded at
        // begin(); only insert a fresh row when neither applies. Without the
        // reattach, a payment whose process died would end up as TWO rows:
        // a fresh SUCCESS plus the original row. A confirmation arriving
        // after the deadline discarded that row finds nothing to adopt and
        // is saved standalone — the payment still surfaces.
        val sessionManager = FlowpayApplication.from(context)?.paymentSessionManager
        val sessionTxnId = sessionManager?.onSmsConfirmed(transaction)
        val recordTxnId = resolveOwnerTxnId(
            sessionTxnId = sessionTxnId,
            windowTxnId = windowTxnId,
            parsed = transaction
        ) { orphanId -> adoptOrphanedRow(context, orphanId, transaction) }

        // A stray incoming CREDIT that no payment claimed is an unrelated bank
        // alert (salary, refund, someone paying you) that merely arrived inside
        // the operation window — it is never a confirmation of our outgoing
        // DEBIT. Surfacing it would flash a false "Payment successful" result
        // screen for the credit's amount, post a success notification, and save
        // a phantom row, all while the real payment is still pending. Ignore it;
        // processSMS deliberately left the window open for the genuine debit.
        if (isUnrelatedIncomingCredit(recordTxnId, transaction)) {
            Log.d(TAG, "Unrelated incoming credit during payment window - ignored")
            return
        }

        if (recordTxnId == null) {
            TransactionRepository.getInstance(context).saveTransaction(transaction)
            Log.d(TAG, "Transaction saved (no session, no adoptable row)")
        }

        withContext(Dispatchers.Main) {
            // Dismiss the USSD overlay (QR flow) and notify listening screens.
            LocalBroadcastManager.getInstance(context)
                .sendBroadcast(Intent("DISMISS_OVERLAY"))
            LocalBroadcastManager.getInstance(context)
                .sendBroadcast(Intent("com.flowpay.app.SMS_RECEIVED"))

            val successIntent = Intent(context, PaymentResultActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("transaction_id", recordTxnId ?: transaction.transactionId)
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
            // Notification first (also serves as a receipt), then the direct
            // launch — which can be silently blocked without the overlay
            // permission; the notification is the guaranteed-reachable path.
            PaymentResultNotifier.notifyResult(context, successIntent)
            context.startActivity(successIntent)
        }
    }

    /**
     * Decides which existing row (if any) owns this confirmation:
     * - a live session claimed it -> that session's row;
     * - no live session but the operation window recorded a session txnId and
     *   this is an outgoing-debit confirmation -> adopt that row (returns the
     *   id when [adopt] actually updated a row);
     * - otherwise null -> caller inserts a standalone record.
     *
     * A CREDIT can never adopt: the recorded row is always an outgoing DEBIT.
     * Pure decision logic — unit-tested with a fake [adopt].
     */
    internal suspend fun resolveOwnerTxnId(
        sessionTxnId: String?,
        windowTxnId: String?,
        parsed: SimpleTransaction,
        adopt: suspend (String) -> Int
    ): String? = when {
        sessionTxnId != null -> sessionTxnId
        windowTxnId == null || parsed.transactionType == "CREDIT" -> null
        else -> {
            val updated = runCatching { adopt(windowTxnId) }
                .onFailure { Log.e(TAG, "Adopting orphaned row failed", it) }
                .getOrDefault(0)
            if (updated > 0) {
                Log.d(TAG, "Confirmation reattached to orphaned session row")
                windowTxnId
            } else {
                null
            }
        }
    }

    /**
     * True when this ingestion is an incoming CREDIT that no payment claimed —
     * an unrelated bank alert that only coincided with the operation window and
     * must not surface a result screen or be persisted as a payment.
     *
     * A CREDIT can never legitimately own a row here: [PaymentSessionManager]'s
     * onSmsConfirmed rejects a CREDIT (a session is always an outgoing DEBIT)
     * and [resolveOwnerTxnId] refuses to adopt one, so [recordTxnId] is always
     * null for a CREDIT. The null check is kept explicit so that if a future
     * owning path ever does claim one, it is surfaced rather than silently
     * dropped. Pure — unit-tested.
     */
    internal fun isUnrelatedIncomingCredit(recordTxnId: String?, parsed: SimpleTransaction): Boolean =
        recordTxnId == null && parsed.transactionType == "CREDIT"

    /** Fills bank-confirmed details into the orphaned session row. */
    private suspend fun adoptOrphanedRow(
        context: Context,
        txnId: String,
        parsed: SimpleTransaction
    ): Int {
        return TransactionRepository.getInstance(context).confirmTransaction(
            transactionId = txnId,
            status = parsed.status,
            parsed = parsed,
            verifiedAt = System.currentTimeMillis()
        )
    }
}
