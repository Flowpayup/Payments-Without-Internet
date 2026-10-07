// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

@file:Suppress("MagicNumber") // Explicit fixture values and timings make regressions readable.

package com.flowpay.app.payment

import com.flowpay.app.data.Transaction
import com.flowpay.app.data.TransactionSource
import com.flowpay.app.data.TransactionStatus
import com.flowpay.app.payment.sms.SimpleTransaction
import com.flowpay.app.states.PaymentState
import com.flowpay.app.telephony.CallSessionEvent
import com.flowpay.app.telephony.CallStateSource
import com.flowpay.app.telephony.DeviceCallState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentHandoffRegressionTest {
    @Test
    fun `ending UI after handoff retains pending verification and accepts delayed receipt`() = runTest {
        val (manager, store, source) = newManager()
        val id = manager.begin("9876543210", "100")
        runCurrent()
        manager.markHandoff(id)
        manager.onUserCancelled(id)
        source.callStarted(0)
        source.callEnded(100)
        runCurrent()
        assertTrue(manager.paymentState.value is PaymentState.WaitingForVerification)
        assertEquals(TransactionStatus.PENDING, store.rows[id]!!.status)
        assertEquals(id, manager.onSmsConfirmed(bankSms(), id))
        assertEquals(TransactionStatus.SUCCESS, store.rows[id]!!.status)
        assertEquals("HDFC123456", (manager.paymentState.value as PaymentState.Success).bankReference)
    }

    @Test
    fun `short call after handoff is not proof of cancellation`() = runTest {
        val (manager, store, source) = newManager()
        val id = manager.begin("9876543210", "100")
        runCurrent()
        manager.markHandoff(id)
        source.callStarted(0)
        runCurrent()
        source.callEnded(100)
        runCurrent()
        assertTrue(manager.paymentState.value is PaymentState.WaitingForVerification)
        assertEquals(TransactionStatus.PENDING, store.rows[id]!!.status)
    }

    @Test
    fun `old screen and receipt cannot change a newer session`() = runTest {
        val (manager, store) = newManager()
        val old = manager.begin("9876543210", "100")
        runCurrent()
        val current = manager.begin("9123456780", "200")
        runCurrent()
        manager.onUserCancelled(old)
        assertNull(manager.onSmsConfirmed(bankSms(), old))
        assertEquals(current, manager.paymentState.value.getTransactionIdValue())
        assertEquals(TransactionStatus.PENDING, store.rows[current]!!.status)
    }

    @Test
    fun `failed receipt write cannot report success and can be retried`() = runTest {
        val (manager, store) = newManager()
        val id = manager.begin("9876543210", "100")
        runCurrent()
        store.failConfirmation = true
        try {
            manager.onSmsConfirmed(bankSms(), id)
            error("Expected storage failure")
        } catch (expected: IllegalStateException) {
            assertEquals("storage unavailable", expected.message)
        }
        assertTrue(manager.paymentState.value.isInProgress())
        assertEquals(TransactionStatus.PENDING, store.rows[id]!!.status)
        store.failConfirmation = false
        assertEquals(id, manager.onSmsConfirmed(bankSms(), id))
    }

    @Test
    fun `QR bank amount replaces suggested amount in receipt and history`() = runTest {
        val (manager, store) = newManager()
        val id = manager.begin("", "500", upiId = "shop@okaxis", source = TransactionSource.QR)
        runCurrent()
        manager.onSmsConfirmed(bankSms(amount = "700"), id)
        assertEquals("700", store.rows[id]!!.amount)
        assertEquals("700", (manager.paymentState.value as PaymentState.Success).amount)
    }

    @Test
    fun `failed confirmation after deadline still expires instead of waiting forever`() = runTest {
        val (manager, store) = newManager()
        val id = manager.begin("9876543210", "100")
        runCurrent()
        store.failConfirmation = true
        val gate = CompletableDeferred<Unit>()
        store.confirmationGate = gate
        var failed = false
        val receipt = launch {
            try {
                manager.onSmsConfirmed(bankSms(), id)
            } catch (expected: IllegalStateException) {
                failed = true
            }
        }
        runCurrent()
        advanceTimeBy(PaymentSessionManager.DEFAULT_VERIFICATION_DEADLINE_MS)
        runCurrent()
        assertTrue(manager.paymentState.value.isInProgress())
        gate.complete(Unit)
        receipt.join()
        runCurrent()
        assertTrue(failed)
        assertTrue(manager.paymentState.value is PaymentState.Timeout)
        assertNull(store.rows[id])
    }

    @Test
    fun `old dial failure and never-started watchdog cannot cancel the next session`() = runTest {
        val (manager, store) = newManager()
        val old = manager.begin("9876543210", "100")
        runCurrent()
        val current = manager.begin("9123456780", "200")
        runCurrent()
        manager.onDialFailed("stale failure", old)
        manager.onCallNeverStarted(old)
        assertTrue(!manager.markHandoff(old))
        assertEquals(current, manager.paymentState.value.getTransactionIdValue())
        assertEquals(TransactionStatus.PENDING, store.rows[current]!!.status)
    }

    @Test
    fun `pending readiness waits for the insert and propagates storage failure`() = runTest {
        val (manager, store) = newManager()
        val gate = CompletableDeferred<Unit>()
        store.insertGate = gate
        val id = manager.begin("9876543210", "100")
        var ready = false
        val readiness = launch { ready = manager.awaitPending(id) }
        runCurrent()
        assertTrue(!ready)
        assertNull(store.rows[id])
        gate.complete(Unit)
        readiness.join()
        assertTrue(ready)
        assertEquals(TransactionStatus.PENDING, store.rows[id]!!.status)
        manager.onUserCancelled(id)
        runCurrent()
        store.insertGate = null
        store.failInsert = true
        val failedId = manager.begin("9876543210", "100")
        try {
            manager.awaitPending(failedId)
            error("Expected insert failure")
        } catch (expected: IllegalStateException) {
            assertEquals("storage unavailable", expected.message)
        }
        assertNull(store.rows[failedId])
    }

    private class FakeCallStateSource : CallStateSource {
        override val callState = MutableStateFlow<DeviceCallState>(DeviceCallState.Idle)
        override val sessionEvents = MutableSharedFlow<CallSessionEvent>(extraBufferCapacity = 16)
        var acquireCount = 0
        var releaseCount = 0
        override fun acquire(tag: String) { acquireCount++ }
        override fun release(tag: String) { releaseCount++ }

        fun callStarted(at: Long) {
            callState.value = DeviceCallState.OffHook
            sessionEvents.tryEmit(CallSessionEvent.Started(at))
        }

        fun callEnded(durationMs: Long) {
            callState.value = DeviceCallState.Idle
            sessionEvents.tryEmit(CallSessionEvent.Ended(durationMs))
        }
    }

    private class FakeStore : PaymentTransactionStore {
        var failInsert = false
        var insertGate: CompletableDeferred<Unit>? = null
        var failConfirmation = false
        var confirmationGate: CompletableDeferred<Unit>? = null

        // synchronizedMap: the concurrency test below drives this store from
        // real JVM threads on a real dispatcher (not the single-threaded
        // TestDispatcher every other test uses), so it needs actual
        // thread-safety, not just single-threaded test-scheduler ordering.
        val rows = java.util.Collections.synchronizedMap(LinkedHashMap<String, Transaction>())

        // Counts every write ATTEMPT, including ones the status guard rejects.
        // The race regression tests assert that the caller which loses the
        // claim issues no attempt at all, rather than relying on the guard to
        // absorb a write that should never have been queued.
        val transitionAttempts = java.util.concurrent.atomic.AtomicInteger(0)
        val deletePendingAttempts = java.util.concurrent.atomic.AtomicInteger(0)

        override suspend fun insertPending(transaction: Transaction) {
            insertGate?.await()
            check(!failInsert) { "storage unavailable" }
            rows[transaction.transactionId] = transaction
        }

        override suspend fun transitionStatus(
            transactionId: String,
            expectedStatus: String,
            newStatus: String
        ): Int {
            transitionAttempts.incrementAndGet()
            val row = rows[transactionId]?.takeIf { it.status == expectedStatus } ?: return 0
            rows[transactionId] = row.copy(status = newStatus)
            return 1
        }

        override suspend fun confirmTransaction(
            transactionId: String,
            status: String,
            parsed: SimpleTransaction,
            verifiedAt: Long
        ): Int {
            // Mirrors the DAO's status guard: a confirmation only lands on a
            // row still awaiting one, never on a cancelled or settled row.
            confirmationGate?.await()
            check(!failConfirmation) { "storage unavailable" }
            val row = rows[transactionId]?.takeIf { it.status == TransactionStatus.PENDING }
                ?: return 0
            rows[transactionId] = row.copy(
                status = status,
                bankRef = parsed.bankReference,
                bankName = parsed.bankName,
                smsExcerpt = parsed.smsExcerpt,
                // Mirrors the DAO: sparser SMS data never erases known values;
                // amount fills only when the row started without one (QR flow).
                upiId = parsed.upiId ?: row.upiId,
                recipientName = parsed.recipientName ?: row.recipientName,
                amount = if (row.source == TransactionSource.QR) {
                    parsed.amount
                } else {
                    row.amount.ifEmpty { parsed.amount }
                },
                verifiedAt = verifiedAt
            )
            return 1
        }

        override suspend fun deleteStalePending(now: Long): Int {
            val stale = rows.filterValues { row ->
                row.status == TransactionStatus.PENDING &&
                    row.deadlineAt != null && row.deadlineAt < now
            }.keys
            stale.forEach { rows.remove(it) }
            return stale.size
        }

        override suspend fun deletePending(transactionId: String): Int {
            deletePendingAttempts.incrementAndGet()
            val pending = rows[transactionId]?.status == TransactionStatus.PENDING
            if (pending) rows.remove(transactionId)
            return if (pending) 1 else 0
        }
    }

    private fun TestScope.newManager(
        store: FakeStore = FakeStore(),
        source: FakeCallStateSource = FakeCallStateSource()
    ): Triple<PaymentSessionManager, FakeStore, FakeCallStateSource> {
        val manager = PaymentSessionManager(
            store = store,
            coordinator = source,
            scope = CoroutineScope(
                backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext[Job])
            ),
            clock = { testScheduler.currentTime }
        )
        return Triple(manager, store, source)
    }

    private fun bankSms(
        status: String = "SUCCESS",
        amount: String = "100",
        transactionType: String = "DEBIT"
    ) = SimpleTransaction(
        transactionId = "HDFC123456",
        amount = amount,
        status = status,
        bankName = "HDFC Bank",
        smsExcerpt = "₹$amount debited — HDFC Bank · Ref HDFC123456",
        timestamp = 0L,
        transactionType = transactionType,
        bankReference = "HDFC123456"
    )
}
