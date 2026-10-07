// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.payment

import android.util.Log
import com.flowpay.app.data.Transaction
import com.flowpay.app.data.TransactionSource
import com.flowpay.app.data.TransactionStatus
import com.flowpay.app.payment.sms.SimpleTransaction
import com.flowpay.app.states.PaymentState
import com.flowpay.app.states.TimeoutType
import com.flowpay.app.telephony.CallSessionEvent
import com.flowpay.app.telephony.CallStateSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Minimal persistence surface the session manager needs. Implemented by
 * TransactionRepository; faked in unit tests.
 */
interface PaymentTransactionStore {
    suspend fun insertPending(transaction: Transaction)

    /** Atomically move a row from [expectedStatus] to [newStatus]. Returns rows changed. */
    suspend fun transitionStatus(transactionId: String, expectedStatus: String, newStatus: String): Int

    /**
     * Fill in bank-confirmed details from [parsed] on the session row,
     * recording it as [status] at [verifiedAt]. Returns rows changed.
     */
    suspend fun confirmTransaction(
        transactionId: String,
        status: String,
        parsed: SimpleTransaction,
        verifiedAt: Long
    ): Int

    /** Discards PENDING rows past their deadline. Returns rows removed. */
    suspend fun deleteStalePending(now: Long): Int

    /** Discards one row, only while it is still PENDING. Returns rows removed. */
    suspend fun deletePending(transactionId: String): Int
}

/**
 * The only writer of payment lifecycle state.
 *
 * A manual UPI 123Pay transfer flows through here:
 *  1. [begin] inserts a PENDING row *before* anything is dialled, so the
 *     database always knows a payment was attempted — even if the process
 *     dies mid-call.
 *  2. Call activity (from [CallStateCoordinator]) moves the in-memory
 *     [PaymentState]: OFFHOOK -> InProgress, then WaitingForVerification after handoff.
 *     Ending the call cannot revoke a bank request.
 *  3. A confirming bank SMS ([onSmsConfirmed]) is the only path to SUCCESS.
 *     Call duration is never treated as proof of payment.
 *  4. If no SMS arrives before the deadline the row is discarded. A payment
 *     the bank never confirmed is one this app cannot report on, so it
 *     leaves no record rather than an outcome the user can't act on.
 *
 * Stale PENDING rows from a killed process are discarded lazily via
 * [reconcileStalePending] (app start, history open, next begin()).
 */
class PaymentSessionManager(
    private val store: PaymentTransactionStore,
    private val coordinator: CallStateSource,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val verificationDeadlineMs: Long = DEFAULT_VERIFICATION_DEADLINE_MS,
    private val minRealCallDurationMs: Long = DEFAULT_MIN_REAL_CALL_DURATION_MS
) {

    companion object {
        private const val TAG = "PaymentSessionManager"
        const val DEFAULT_VERIFICATION_DEADLINE_MS = 10 * 60 * 1000L // 10 minutes
        const val DEFAULT_MIN_REAL_CALL_DURATION_MS = 5_000L
        private const val COORDINATOR_TAG = "payment-session"
    }

    private val _paymentState = MutableStateFlow<PaymentState>(PaymentState.Idle)
    val paymentState: StateFlow<PaymentState> = _paymentState.asStateFlow()

    private val sessionLock = Any()

    // begin() publishes state and jobs under sessionLock. Volatile visibility
    // also protects coroutine callbacks that read the jobs outside that lock.
    @Volatile private var collectJob: Job? = null

    @Volatile private var watchdogJob: Job? = null

    @Volatile private var pendingInsertJob: Deferred<Unit>? = null

    @Volatile private var handedOff = false
    private var confirmingTxnId: String? = null
    private var deadlineAt = 0L
    private var source: String = TransactionSource.MANUAL
    private var coordinatorAcquired = false

    /**
     * Starts a new payment session; callers awaitPending before dialling.
     *
     * A new payment SUPERSEDES any session still in flight. This used to be
     * refused with "a payment is already in progress", which stranded the user:
     * a session the bank never confirmed stayed in progress for its full
     * verification deadline, and every attempt to pay in the meantime was
     * blocked — including a retry of the very payment that had failed.
     *
     * Superseding is safe because a session is not what decides an outcome —
     * the bank's SMS is. The superseded PENDING row is discarded (nothing ever
     * confirmed it, so under the "no confirmation, no record" rule it must
     * leave no trace), and the caller re-arms the SMS window for the new
     * amount, so a confirmation matching what the user just entered still
     * lands on the new session exactly as before.
     *
     * [upiId] and [source] let the QR flow record what it actually knows (a
     * payee VPA, provenance QR) — the QR flow used to bypass the session
     * entirely, so an unconfirmed QR payment left no trace at all.
     */
    @Suppress("LongMethod") // Publish the state and its jobs together under one lock.
    fun begin(
        phoneNumber: String,
        amount: String,
        upiId: String? = null,
        source: String = TransactionSource.MANUAL
    ): String {
        val initiating: PaymentState.Initiating
        val supersededTxnId: String?
        synchronized(sessionLock) {
            check(confirmingTxnId == null) { "Receipt persistence in progress" }
            handedOff = false
            this.source = source
            val previous = _paymentState.value
            supersededTxnId = if (previous.isInProgress()) {
                Log.w(TAG, "begin() supersedes the session still in flight")
                cleanupLocked()
                previous.getTransactionIdValue()
            } else {
                null
            }
            initiating = PaymentState.Initiating(phoneNumber, amount)
            _paymentState.value = initiating
            if (!coordinatorAcquired) {
                coordinator.acquire(COORDINATOR_TAG)
                coordinatorAcquired = true
            }

            val txnId = initiating.transactionId
            val now = clock()
            val pendingDeadline = now + verificationDeadlineMs
            deadlineAt = pendingDeadline

            // Drop the superseded row. deletePending is guarded to PENDING, so a
            // confirmation that landed on it a moment earlier is never erased.
            if (supersededTxnId != null) {
                val previousInsert = pendingInsertJob
                scope.launch {
                    previousInsert?.join()
                    store.deletePending(supersededTxnId)
                }
            }

            pendingInsertJob = scope.async {
                // Older stale sessions are discarded before a new one starts.
                runCatching { store.deleteStalePending(now) }
                store.insertPending(
                    Transaction(
                        transactionId = txnId,
                        amount = amount,
                        status = TransactionStatus.PENDING,
                        bankName = "",
                        smsExcerpt = "",
                        timestamp = now,
                        transactionType = "DEBIT",
                        phoneNumber = phoneNumber,
                        upiId = upiId,
                        deadlineAt = pendingDeadline,
                        source = source
                    )
                )
                Log.d(TAG, "PENDING row recorded for session")
            }

            collectJob = scope.launch {
                coordinator.sessionEvents.collect { event -> onCallSessionEvent(event) }
            }

            watchdogJob = scope.launch {
                delay(verificationDeadlineMs)
                onVerificationDeadline(txnId)
            }

            Log.d(TAG, "Payment session started")
            return txnId
        }
    }

    suspend fun awaitPending(transactionId: String): Boolean {
        val insert = synchronized(sessionLock) {
            if (_paymentState.value.getTransactionIdValue() != transactionId) return false
            pendingInsertJob
        }
        insert?.await()
        return synchronized(sessionLock) {
            _paymentState.value.isInProgress() && _paymentState.value.getTransactionIdValue() == transactionId
        }
    }

    fun markHandoff(transactionId: String): Boolean = synchronized(sessionLock) {
        val state = _paymentState.value
        if (state.isInProgress() && state.getTransactionIdValue() == transactionId) {
            handedOff = true
            true
        } else {
            false
        }
    }

    /** The dial intent could not be launched; the session never really started. */
    fun onDialFailed(reason: String, expectedTxnId: String? = null) {
        finishSession(TransactionStatus.CANCELLED, expectedTxnId) { phone, amount, txnId ->
            PaymentState.Cancelled(phone, amount, txnId, reason)
        }
    }

    /** Ending a call/screen after handoff cannot revoke a bank request. */
    @Suppress("ReturnCount") // Reject stale owners and losing lifecycle races before any write.
    fun onUserCancelled(expectedTxnId: String? = null) {
        synchronized(sessionLock) {
            val state = _paymentState.value
            if (expectedTxnId != null && state.getTransactionIdValue() != expectedTxnId) return
            if (!state.isInProgress() || confirmingTxnId != null) return
            if (handedOff) {
                _paymentState.value = PaymentState.WaitingForVerification(
                    verificationDeadlineMs,
                    state.getPhoneNumberValue().orEmpty(),
                    state.getAmountValue().orEmpty(),
                    state.getTransactionIdValue() ?: return
                )
                return
            }
            finishSession(TransactionStatus.CANCELLED) { phone, amount, txnId ->
                PaymentState.Cancelled(phone, amount, txnId, "Cancelled before dialling")
            }
        }
    }

    /**
     * The dial intent launched but no call ever went OFFHOOK (watchdog from
     * the overlay service). Only acts while the session is still Initiating.
     */
    fun onCallNeverStarted(expectedTxnId: String? = null) {
        synchronized(sessionLock) {
            val state = _paymentState.value
            if (state !is PaymentState.Initiating || handedOff) return
            finishSession(TransactionStatus.CANCELLED, expectedTxnId ?: state.transactionId) { phone, amount, txnId ->
                PaymentState.Cancelled(phone, amount, txnId, "The payment call was never connected")
            }
        }
    }

    /**
     * A parsed bank SMS arrived. If a session is active, its row is updated
     * in place and the session transaction id is returned; callers must NOT
     * insert a separate row in that case. Returns null when no session was
     * active (e.g. after process death), letting callers fall back to their own
     * persistence.
     */
    @Suppress("ReturnCount") // Reject stale owners and losing lifecycle races before any write.
    suspend fun onSmsConfirmed(parsed: SimpleTransaction, expectedTxnId: String? = null): String? {
        if (parsed.transactionType == "CREDIT") return null
        val current: PaymentState
        val txnId: String
        val amount: String
        synchronized(sessionLock) {
            current = _paymentState.value
            if (!current.isInProgress() || confirmingTxnId != null) return null
            txnId = current.getTransactionIdValue() ?: return null
            if (expectedTxnId != null && txnId != expectedTxnId) return null
            confirmingTxnId = txnId
            amount = if (source == TransactionSource.QR) parsed.amount else current.getAmountValue() ?: parsed.amount
        }
        try {
            pendingInsertJob?.await()
            val updated = store.confirmTransaction(txnId, parsed.status, parsed, clock())
            check(updated == 1) { "Receipt could not update its pending row" }
            synchronized(sessionLock) {
                val phone = current.getPhoneNumberValue() ?: parsed.phoneNumber.orEmpty()
                _paymentState.value = when (parsed.status) {
                    TransactionStatus.FAILED -> PaymentState.Failed(
                        error = "Bank reported the payment as failed",
                        phoneNumber = phone,
                        amount = amount,
                        transactionId = txnId,
                        canRetry = true
                    )
                    TransactionStatus.NEEDS_REVIEW -> PaymentState.NeedsReview(txnId, phone, amount)
                    else -> PaymentState.Success(txnId, phone, amount, parsed.bankReference, clock())
                }
                cleanupLocked()
            }
            return txnId
        } finally {
            val expired = synchronized(sessionLock) {
                confirmingTxnId = null
                clock() >= deadlineAt
            }
            if (expired) onVerificationDeadline(txnId)
        }
    }

    /** UI acknowledges a terminal result and returns the session to Idle. */
    fun acknowledgeResult() {
        synchronized(sessionLock) {
            val state = _paymentState.value
            if (state.isTerminal() || state is PaymentState.Timeout) {
                _paymentState.value = PaymentState.Idle
            }
        }
    }

    /** Discards PENDING rows whose deadline passed while we weren't running. */
    fun reconcileStalePending() {
        scope.launch {
            val discarded = runCatching { store.deleteStalePending(clock()) }.getOrDefault(0)
            if (discarded > 0) {
                Log.d(TAG, "Discarded $discarded unconfirmed stale PENDING transaction(s)")
            }
        }
    }

    private fun onCallSessionEvent(event: CallSessionEvent) {
        when (event) {
            is CallSessionEvent.Started -> {
                synchronized(sessionLock) {
                    val state = _paymentState.value
                    if (state is PaymentState.Initiating) {
                        _paymentState.value = PaymentState.InProgress(
                            step = "On the line with the UPI service",
                            progress = 0.4f,
                            phoneNumber = state.phoneNumber,
                            amount = state.amount,
                            transactionId = state.transactionId
                        )
                    }
                }
            }
            is CallSessionEvent.Ended -> {
                val state = synchronized(sessionLock) { _paymentState.value }
                when {
                    state is PaymentState.InProgress && !handedOff && event.durationMs < minRealCallDurationMs -> {
                        finishSession(TransactionStatus.CANCELLED) { phone, amount, txnId ->
                            PaymentState.Cancelled(
                                phone,
                                amount,
                                txnId,
                                "Call ended before the payment flow could complete"
                            )
                        }
                    }
                    state is PaymentState.InProgress -> {
                        synchronized(sessionLock) {
                            // Re-check: an SMS may have confirmed while we were deciding.
                            val s = _paymentState.value
                            if (s is PaymentState.InProgress) {
                                _paymentState.value = PaymentState.WaitingForVerification(
                                    timeout = verificationDeadlineMs,
                                    phoneNumber = s.phoneNumber,
                                    amount = s.amount,
                                    transactionId = s.transactionId
                                )
                            }
                        }
                    }
                    else -> { /* Initiating without OFFHOOK, or already terminal - nothing to do */ }
                }
            }
        }
    }

    /**
     * The deadline passed with no confirming bank SMS, so the row is
     * discarded and nothing is shown: no confirmation means no payment to
     * report. A genuine confirmation arriving in the SMS window's remaining
     * grace still surfaces — with the row gone there is nothing to adopt, so
     * the ingestion pipeline records it as a standalone transaction.
     */
    @Suppress("ReturnCount") // Reject stale owners and losing lifecycle races before any write.
    private fun onVerificationDeadline(expectedTxnId: String) {
        // Claim FIRST, write second. Queueing the delete before the claim let a
        // confirming SMS win the lock (showing "Payment successful") while the
        // already-queued deletePending still ran, removing the very row that
        // confirmation had just landed on — success on screen, nothing in
        // history. Claiming inside the lock means only the winner writes.
        val txnId = synchronized(sessionLock) {
            val s = _paymentState.value
            if (!s.isInProgress() || confirmingTxnId != null) return
            val id = s.getTransactionIdValue() ?: return
            if (id != expectedTxnId) return
            _paymentState.value = PaymentState.Timeout(
                timeoutType = TimeoutType.BANK_VERIFICATION,
                phoneNumber = s.getPhoneNumberValue() ?: "",
                amount = s.getAmountValue() ?: "",
                transactionId = id
            )
            cleanupLocked()
            id to pendingInsertJob
        }

        scope.launch {
            txnId.second?.join()
            store.deletePending(txnId.first)
        }
        Log.w(TAG, "No bank SMS before deadline - unconfirmed session discarded")
    }

    @Suppress("ReturnCount") // Reject stale owners and losing lifecycle races before any write.
    private fun finishSession(
        rowStatus: String,
        expectedTxnId: String? = null,
        terminalState: (phone: String, amount: String, txnId: String) -> PaymentState
    ) {
        // Claim FIRST, write second — same invariant as onSmsConfirmed. When
        // the write was queued before the claim, a cancel landing at the same
        // instant as the bank's SMS could show "Payment successful" while the
        // already-queued PENDING->CANCELLED transition still ran, leaving
        // history contradicting the screen the user was looking at. Only the
        // caller that actually claims the terminal state may write.
        val txnId = synchronized(sessionLock) {
            val s = _paymentState.value
            if (!s.isInProgress() || confirmingTxnId != null) return
            val id = s.getTransactionIdValue() ?: return
            if (expectedTxnId != null && id != expectedTxnId) return
            _paymentState.value = terminalState(
                s.getPhoneNumberValue() ?: "",
                s.getAmountValue() ?: "",
                id
            )
            cleanupLocked()
            id to pendingInsertJob
        }

        scope.launch {
            txnId.second?.join()
            store.transitionStatus(txnId.first, TransactionStatus.PENDING, rowStatus)
        }
    }

    private fun cleanupLocked() {
        collectJob?.cancel()
        collectJob = null
        watchdogJob?.cancel()
        watchdogJob = null
        if (coordinatorAcquired) {
            coordinator.release(COORDINATOR_TAG)
            coordinatorAcquired = false
        }
    }
}
