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
import kotlinx.coroutines.Job
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

    /** PENDING rows past their deadline become UNVERIFIED. Returns rows changed. */
    suspend fun expireStalePending(now: Long): Int
}

/**
 * The only writer of payment lifecycle state.
 *
 * A manual UPI 123Pay transfer flows through here:
 *  1. [begin] inserts a PENDING row *before* anything is dialled, so the
 *     database always knows a payment was attempted — even if the process
 *     dies mid-call.
 *  2. Call activity (from [CallStateCoordinator]) moves the in-memory
 *     [PaymentState]: OFFHOOK -> InProgress, short hang-up -> Cancelled,
 *     normal call end -> WaitingForVerification.
 *  3. A confirming bank SMS ([onSmsConfirmed]) is the only path to SUCCESS.
 *     Call duration is never treated as proof of payment.
 *  4. If no SMS arrives before the deadline the row becomes UNVERIFIED —
 *     visibly distinct from both success and failure.
 *
 * Stale PENDING rows from a killed process are finalised lazily via
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
    private var collectJob: Job? = null
    private var watchdogJob: Job? = null
    private var pendingInsertJob: Job? = null
    private var coordinatorAcquired = false

    /**
     * Starts a new payment session and records a PENDING row.
     * Returns the client transaction id, or null if a session is already active.
     *
     * [upiId] and [source] let the QR flow record what it actually knows (a
     * payee VPA, provenance QR) — the QR flow used to bypass the session
     * entirely, so an unconfirmed QR payment left no trace at all.
     */
    fun begin(
        phoneNumber: String,
        amount: String,
        upiId: String? = null,
        source: String = TransactionSource.MANUAL
    ): String? {
        val initiating: PaymentState.Initiating
        synchronized(sessionLock) {
            if (_paymentState.value.isInProgress()) {
                Log.w(TAG, "begin() rejected - a payment session is already active")
                return null
            }
            initiating = PaymentState.Initiating(phoneNumber, amount)
            _paymentState.value = initiating
            if (!coordinatorAcquired) {
                coordinator.acquire(COORDINATOR_TAG)
                coordinatorAcquired = true
            }
        }

        val txnId = initiating.transactionId
        val now = clock()
        val deadlineAt = now + verificationDeadlineMs

        pendingInsertJob = scope.launch {
            // Older stale sessions are finalised before a new one starts.
            runCatching { store.expireStalePending(now) }
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
                    deadlineAt = deadlineAt,
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
            onVerificationDeadline()
        }

        Log.d(TAG, "Payment session started")
        return txnId
    }

    /** The dial intent could not be launched; the session never really started. */
    fun onDialFailed(reason: String) {
        finishSession(TransactionStatus.CANCELLED) { phone, amount, txnId ->
            PaymentState.Cancelled(phone, amount, txnId, reason)
        }
    }

    /** The user aborted from the in-call overlay. */
    fun onUserCancelled() {
        finishSession(TransactionStatus.CANCELLED) { phone, amount, txnId ->
            PaymentState.Cancelled(phone, amount, txnId, "Cancelled by user")
        }
    }

    /**
     * The dial intent launched but no call ever went OFFHOOK (watchdog from
     * the overlay service). Only acts while the session is still Initiating.
     */
    fun onCallNeverStarted() {
        synchronized(sessionLock) {
            if (_paymentState.value !is PaymentState.Initiating) return
        }
        finishSession(TransactionStatus.CANCELLED) { phone, amount, txnId ->
            PaymentState.Cancelled(phone, amount, txnId, "The payment call was never connected")
        }
    }

    /**
     * A parsed bank SMS arrived. If a session is active, its row is updated
     * in place and the session transaction id is returned; callers must NOT
     * insert a separate row in that case. Returns null when no session was
     * active (e.g. the QR flow), letting callers fall back to their own
     * persistence.
     */
    fun onSmsConfirmed(parsed: SimpleTransaction): String? {
        // Direction check: a manual session is always an outgoing DEBIT
        // (begin() inserts transactionType = "DEBIT"), so an incoming CREDIT
        // SMS can never confirm it. Returning null lets the caller fall back
        // to standalone persistence, exactly like the QR/no-session flow.
        if (parsed.transactionType == "CREDIT") {
            Log.d(TAG, "CREDIT SMS during DEBIT session - not a confirmation")
            return null
        }

        val newStatus = when {
            parsed.status.equals(TransactionStatus.FAILED, ignoreCase = true) ->
                TransactionStatus.FAILED
            parsed.status.equals(TransactionStatus.NEEDS_REVIEW, ignoreCase = true) ->
                TransactionStatus.NEEDS_REVIEW
            else -> TransactionStatus.SUCCESS
        }
        val verifiedAt = clock()

        // Check-and-claim must be one atomic step: with the check in one
        // lock and the terminal transition in a second, two near-simultaneous
        // confirming SMS (both pipelines, or a debug injection racing a real
        // message) could BOTH observe InProgress and both write a terminal
        // outcome, last-writer-wins. Claiming inside a single lock guarantees
        // exactly one caller ever owns the confirmation.
        val txnId: String
        synchronized(sessionLock) {
            val current = _paymentState.value
            if (!current.isInProgress()) return null
            txnId = current.getTransactionIdValue() ?: return null
            val phone = current.getPhoneNumberValue() ?: parsed.phoneNumber ?: ""
            val amount = current.getAmountValue() ?: parsed.amount

            _paymentState.value = when (newStatus) {
                TransactionStatus.FAILED -> PaymentState.Failed(
                    error = "Bank reported the payment as failed",
                    phoneNumber = phone,
                    amount = amount,
                    transactionId = txnId,
                    canRetry = true
                )
                TransactionStatus.NEEDS_REVIEW -> PaymentState.NeedsReview(
                    transactionId = txnId,
                    phoneNumber = phone,
                    amount = amount
                )
                else -> PaymentState.Success(
                    transactionId = txnId,
                    phoneNumber = phone,
                    amount = amount,
                    bankReference = parsed.transactionId,
                    timestamp = verifiedAt
                )
            }
            cleanupLocked()
        }

        scope.launch {
            pendingInsertJob?.join()
            val updated = store.confirmTransaction(
                transactionId = txnId,
                status = newStatus,
                parsed = parsed,
                verifiedAt = verifiedAt
            )
            Log.d(TAG, "Session row confirmed as $newStatus (rows=$updated)")
        }
        return txnId
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

    /** Finalises PENDING rows whose deadline passed while we weren't running. */
    fun reconcileStalePending() {
        scope.launch {
            val expired = runCatching { store.expireStalePending(clock()) }.getOrDefault(0)
            if (expired > 0) {
                Log.d(TAG, "Marked $expired stale PENDING transaction(s) as UNVERIFIED")
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
                    state is PaymentState.InProgress && event.durationMs < minRealCallDurationMs -> {
                        finishSession(TransactionStatus.CANCELLED) { phone, amount, txnId ->
                            PaymentState.Cancelled(
                                phone, amount, txnId,
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

    private fun onVerificationDeadline() {
        val state = synchronized(sessionLock) { _paymentState.value }
        if (!state.isInProgress()) return
        val txnId = state.getTransactionIdValue() ?: return

        scope.launch {
            pendingInsertJob?.join()
            store.transitionStatus(txnId, TransactionStatus.PENDING, TransactionStatus.UNVERIFIED)
        }
        synchronized(sessionLock) {
            val s = _paymentState.value
            if (s.isInProgress()) {
                _paymentState.value = PaymentState.Timeout(
                    timeoutType = TimeoutType.BANK_VERIFICATION,
                    phoneNumber = s.getPhoneNumberValue() ?: "",
                    amount = s.getAmountValue() ?: "",
                    transactionId = txnId
                )
                cleanupLocked()
            }
        }
        Log.w(TAG, "No bank SMS before deadline - session marked UNVERIFIED")
    }

    private fun finishSession(
        rowStatus: String,
        terminalState: (phone: String, amount: String, txnId: String) -> PaymentState
    ) {
        val state = synchronized(sessionLock) { _paymentState.value }
        if (!state.isInProgress()) return
        val txnId = state.getTransactionIdValue() ?: return

        scope.launch {
            pendingInsertJob?.join()
            store.transitionStatus(txnId, TransactionStatus.PENDING, rowStatus)
        }
        synchronized(sessionLock) {
            val s = _paymentState.value
            if (s.isInProgress()) {
                _paymentState.value = terminalState(
                    s.getPhoneNumberValue() ?: "",
                    s.getAmountValue() ?: "",
                    txnId
                )
                cleanupLocked()
            }
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
