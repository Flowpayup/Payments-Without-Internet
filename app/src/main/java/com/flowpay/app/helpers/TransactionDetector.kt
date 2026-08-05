// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.helpers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.flowpay.app.payment.PaymentSessionManager
import com.flowpay.app.payment.sms.SimpleTransaction
import com.flowpay.app.payment.sms.SmsTransactionParser

/**
 * Stateful orchestration around [SmsTransactionParser]: the
 * SharedPreferences-backed operation window (only SMS arriving while a
 * payment is in flight are eligible) and cross-pipeline dedup so the
 * broadcast receiver and the notification listener never double-process
 * one SMS.
 *
 * All actual bank-SMS matching logic lives in [SmsTransactionParser], which
 * is pure and directly unit-testable without a Context.
 */
class TransactionDetector private constructor(context: Context) {

    companion object {
        private const val TAG = "TransactionDetector"
        private const val PREF_NAME = "payment_operation"
        private const val KEY_ACTIVE = "is_active"
        private const val KEY_START_TIME = "start_time"
        private const val KEY_OPERATION_TYPE = "operation_type"
        private const val KEY_EXPECTED_AMOUNT = "expected_amount"
        private const val KEY_PHONE_NUMBER = "phone_number"
        private const val KEY_SESSION_TXN_ID = "session_txn_id"

        /**
         * How long after a payment starts an incoming SMS is still eligible.
         * Derived from the session verification deadline (plus a grace margin
         * for a confirmation racing the deadline) — it must never be shorter,
         * or a slow-but-genuine bank SMS would be dropped here and a payment
         * that did go through would never be recorded.
         */
        internal const val OPERATION_WINDOW_MILLIS =
            PaymentSessionManager.DEFAULT_VERIFICATION_DEADLINE_MS + 30_000L

        private const val SMS_CLAIM_WINDOW_MILLIS = 60 * 1000L // dedupe window across pipelines

        @Volatile
        private var instance: TransactionDetector? = null

        fun getInstance(context: Context): TransactionDetector {
            return instance ?: synchronized(this) {
                instance ?: TransactionDetector(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // Recently claimed SMS fingerprints (sender|body hash -> claim time).
    // Both ingestion pipelines (broadcast receiver + notification listener)
    // call tryClaimSms() before processing, so the same message is parsed
    // and persisted exactly once even when both fire near-simultaneously.
    private val recentSmsClaims = object : LinkedHashMap<String, Long>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > 64
    }

    @Synchronized
    fun tryClaimSms(body: String): Boolean {
        // Key is body-only: the two pipelines see different sender strings
        // for the same SMS (DLT header vs messaging-app display name), so a
        // sender-qualified key would never dedupe across them.
        val key = SmsTransactionParser.claimKey(body).hashCode().toString()
        val now = System.currentTimeMillis()
        val lastClaim = recentSmsClaims[key]
        if (lastClaim != null && now - lastClaim < SMS_CLAIM_WINDOW_MILLIS) {
            return false
        }
        recentSmsClaims[key] = now
        return true
    }

    /**
     * Start monitoring for a payment operation. [sessionTxnId] is the
     * PaymentSessionManager transaction id (the PENDING row's key), persisted
     * here so a confirming SMS that arrives AFTER a process death — when the
     * in-memory session is gone — can still be matched back to its row
     * instead of inserting a duplicate.
     */
    fun startOperation(
        operationType: String,
        expectedAmount: String? = null,
        phoneNumber: String? = null,
        sessionTxnId: String? = null
    ) {
        Log.d(TAG, "Starting operation: $operationType")

        prefs.edit().apply {
            putBoolean(KEY_ACTIVE, true)
            putLong(KEY_START_TIME, System.currentTimeMillis())
            putString(KEY_OPERATION_TYPE, operationType)
            expectedAmount?.let { putString(KEY_EXPECTED_AMOUNT, it) }
            phoneNumber?.let { putString(KEY_PHONE_NUMBER, it) }
            sessionTxnId?.let { putString(KEY_SESSION_TXN_ID, it) }
            apply()
        }
    }

    // Stop monitoring
    fun stopOperation() {
        Log.d(TAG, "Stopping operation")

        prefs.edit().apply {
            clear()
            apply()
        }
    }

    // Check if we should process SMS
    fun shouldProcessSMS(): Boolean {
        val isActive = prefs.getBoolean(KEY_ACTIVE, false)
        if (!isActive) {
            return false
        }

        // Check timeout
        val startTime = prefs.getLong(KEY_START_TIME, 0)
        val elapsed = System.currentTimeMillis() - startTime

        if (elapsed > OPERATION_WINDOW_MILLIS) {
            Log.d(TAG, "Operation timed out after ${elapsed / 1000} seconds")
            stopOperation()
            return false
        }

        // Additional check: Ensure we have an operation type
        val operationType = prefs.getString(KEY_OPERATION_TYPE, null)
        if (operationType.isNullOrEmpty()) {
            stopOperation()
            return false
        }

        return true
    }

    fun getOperationType(): String? = prefs.getString(KEY_OPERATION_TYPE, null)
    fun getPhoneNumber(): String? = prefs.getString(KEY_PHONE_NUMBER, null)

    /**
     * The session transaction id recorded at [startOperation], or null. Read
     * BEFORE [processSMS] (which clears the window on a consuming match) by
     * callers that need to reattach an orphaned confirmation to its row.
     */
    fun getSessionTxnId(): String? = prefs.getString(KEY_SESSION_TXN_ID, null)

    // Synchronized so that when both ingestion pipelines race past
    // shouldProcessSMS(), only the first one inside consumes the active
    // operation — the second sees it stopped and bails.
    @Synchronized
    fun processSMS(sender: String, body: String): SimpleTransaction? {
        Log.d(TAG, "Processing SMS")

        // Re-check under lock: the other pipeline may have just consumed
        // the active operation.
        if (!shouldProcessSMS()) {
            Log.d(TAG, "Operation no longer active, skipping")
            return null
        }

        val expectedAmount = prefs.getString(KEY_EXPECTED_AMOUNT, null)
        val transaction = SmsTransactionParser.parse(sender, body, expectedAmount)
        if (transaction == null) {
            Log.d(TAG, "SMS did not match a bank transaction")
            return null
        }
        Log.d(TAG, "Matched ${transaction.bankName} transaction: ${transaction.status}")

        // Mark operation complete — unless this was an incoming CREDIT. A
        // stray credit must not consume the operation window the real
        // outgoing-debit confirmation may still need.
        if (transaction.transactionType != "CREDIT") {
            stopOperation()
        }

        return transaction
    }
}
