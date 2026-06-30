package com.flowpay.app.payment

import com.flowpay.app.data.Transaction
import com.flowpay.app.data.TransactionStatus
import com.flowpay.app.helpers.SimpleTransaction
import com.flowpay.app.states.PaymentState
import com.flowpay.app.telephony.CallSessionEvent
import com.flowpay.app.telephony.CallStateSource
import com.flowpay.app.telephony.DeviceCallState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentSessionManagerTest {

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
        val rows = LinkedHashMap<String, Transaction>()

        override suspend fun insertPending(transaction: Transaction) {
            rows[transaction.transactionId] = transaction
        }

        override suspend fun transitionStatus(
            transactionId: String,
            expectedStatus: String,
            newStatus: String
        ): Int {
            val row = rows[transactionId] ?: return 0
            if (row.status != expectedStatus) return 0
            rows[transactionId] = row.copy(status = newStatus)
            return 1
        }

        override suspend fun confirmTransaction(
            transactionId: String,
            status: String,
            bankRef: String?,
            bankName: String,
            smsExcerpt: String,
            upiId: String?,
            recipientName: String?,
            verifiedAt: Long
        ): Int {
            val row = rows[transactionId] ?: return 0
            rows[transactionId] = row.copy(
                status = status,
                bankRef = bankRef,
                bankName = bankName,
                smsExcerpt = smsExcerpt,
                upiId = upiId,
                recipientName = recipientName ?: row.recipientName,
                verifiedAt = verifiedAt
            )
            return 1
        }

        override suspend fun expireStalePending(now: Long): Int {
            var changed = 0
            rows.replaceAll { _, row ->
                if (row.status == TransactionStatus.PENDING && row.deadlineAt != null && row.deadlineAt < now) {
                    changed++
                    row.copy(status = TransactionStatus.UNVERIFIED)
                } else row
            }
            return changed
        }
    }

    private fun TestScope.newManager(
        store: FakeStore = FakeStore(),
        source: FakeCallStateSource = FakeCallStateSource()
    ): Triple<PaymentSessionManager, FakeStore, FakeCallStateSource> {
        val manager = PaymentSessionManager(
            store = store,
            coordinator = source,
            scope = backgroundScope,
            clock = { testScheduler.currentTime }
        )
        return Triple(manager, store, source)
    }

    private fun bankSms(status: String = "SUCCESS", amount: String = "100") = SimpleTransaction(
        transactionId = "HDFC123456",
        amount = amount,
        status = status,
        bankName = "HDFC Bank",
        smsExcerpt = "₹$amount debited — HDFC Bank · Ref HDFC123456",
        timestamp = 0L
    )

    @Test
    fun `begin records a PENDING row before any call activity`() = runTest {
        val (manager, store, source) = newManager()

        val txnId = manager.begin("9876543210", "100")
        runCurrent()

        assertNotNull(txnId)
        assertTrue(manager.paymentState.value is PaymentState.Initiating)
        val row = store.rows[txnId]
        assertNotNull("PENDING row must exist before dialing", row)
        assertEquals(TransactionStatus.PENDING, row!!.status)
        assertEquals("9876543210", row.phoneNumber)
        assertNotNull("deadline must be persisted", row.deadlineAt)
        assertEquals(1, source.acquireCount)
    }

    @Test
    fun `second begin while active is rejected`() = runTest {
        val (manager, _, _) = newManager()

        val first = manager.begin("9876543210", "100")
        runCurrent()
        val second = manager.begin("9123456780", "200")

        assertNotNull(first)
        assertNull("concurrent session must be rejected", second)
    }

    @Test
    fun `call start moves session to InProgress`() = runTest {
        val (manager, _, source) = newManager()
        manager.begin("9876543210", "100")
        runCurrent()

        source.callStarted(at = testScheduler.currentTime)
        runCurrent()

        assertTrue(manager.paymentState.value is PaymentState.InProgress)
    }

    @Test
    fun `confirming SMS during call yields SUCCESS row with bank reference`() = runTest {
        val (manager, store, source) = newManager()
        val txnId = manager.begin("9876543210", "100")!!
        runCurrent()
        source.callStarted(at = testScheduler.currentTime)
        runCurrent()

        val claimed = manager.onSmsConfirmed(bankSms())
        runCurrent()

        assertEquals(txnId, claimed)
        assertTrue(manager.paymentState.value is PaymentState.Success)
        val row = store.rows[txnId]!!
        assertEquals(TransactionStatus.SUCCESS, row.status)
        assertEquals("HDFC123456", row.bankRef)
        assertEquals("HDFC Bank", row.bankName)
        assertNotNull(row.verifiedAt)
    }

    @Test
    fun `short call without SMS is CANCELLED - never SUCCESS`() = runTest {
        val (manager, store, source) = newManager()
        val txnId = manager.begin("9876543210", "100")!!
        runCurrent()
        source.callStarted(at = testScheduler.currentTime)
        runCurrent()

        source.callEnded(durationMs = 2_000)
        runCurrent()

        assertTrue(manager.paymentState.value is PaymentState.Cancelled)
        assertEquals(TransactionStatus.CANCELLED, store.rows[txnId]!!.status)
    }

    @Test
    fun `normal call end without SMS waits for verification then SMS confirms`() = runTest {
        val (manager, store, source) = newManager()
        val txnId = manager.begin("9876543210", "100")!!
        runCurrent()
        source.callStarted(at = testScheduler.currentTime)
        runCurrent()
        source.callEnded(durationMs = 40_000)
        runCurrent()

        assertTrue(
            "call duration must never be treated as success",
            manager.paymentState.value is PaymentState.WaitingForVerification
        )
        assertEquals(TransactionStatus.PENDING, store.rows[txnId]!!.status)

        val claimed = manager.onSmsConfirmed(bankSms())
        runCurrent()

        assertEquals(txnId, claimed)
        assertTrue(manager.paymentState.value is PaymentState.Success)
        assertEquals(TransactionStatus.SUCCESS, store.rows[txnId]!!.status)
    }

    @Test
    fun `no SMS before deadline yields UNVERIFIED row and Timeout state`() = runTest {
        val (manager, store, source) = newManager()
        val txnId = manager.begin("9876543210", "100")!!
        runCurrent()
        source.callStarted(at = testScheduler.currentTime)
        runCurrent()
        source.callEnded(durationMs = 40_000)
        runCurrent()

        advanceTimeBy(PaymentSessionManager.DEFAULT_VERIFICATION_DEADLINE_MS + 1_000)
        runCurrent()

        assertTrue(manager.paymentState.value is PaymentState.Timeout)
        assertEquals(TransactionStatus.UNVERIFIED, store.rows[txnId]!!.status)
    }

    @Test
    fun `failed bank SMS yields FAILED row and Failed state`() = runTest {
        val (manager, store, source) = newManager()
        val txnId = manager.begin("9876543210", "100")!!
        runCurrent()
        source.callStarted(at = testScheduler.currentTime)
        runCurrent()

        manager.onSmsConfirmed(bankSms(status = TransactionStatus.FAILED))
        runCurrent()

        assertTrue(manager.paymentState.value is PaymentState.Failed)
        assertEquals(TransactionStatus.FAILED, store.rows[txnId]!!.status)
    }

    @Test
    fun `sms with no active session is not claimed`() = runTest {
        val (manager, _, _) = newManager()

        assertNull(manager.onSmsConfirmed(bankSms()))
    }

    @Test
    fun `dial failure cancels the session`() = runTest {
        val (manager, store, source) = newManager()
        val txnId = manager.begin("9876543210", "100")!!
        runCurrent()

        manager.onDialFailed("Could not start the payment call")
        runCurrent()

        assertTrue(manager.paymentState.value is PaymentState.Cancelled)
        assertEquals(TransactionStatus.CANCELLED, store.rows[txnId]!!.status)
        assertEquals("coordinator must be released on terminal state", 1, source.releaseCount)
    }

    @Test
    fun `acknowledge returns terminal session to Idle and allows a new begin`() = runTest {
        val (manager, _, source) = newManager()
        manager.begin("9876543210", "100")
        runCurrent()
        manager.onDialFailed("failed")
        runCurrent()

        manager.acknowledgeResult()
        assertEquals(PaymentState.Idle, manager.paymentState.value)

        val next = manager.begin("9123456780", "50")
        runCurrent()
        assertNotNull("new session must be possible after acknowledge", next)
        assertEquals(2, source.acquireCount)
    }

    @Test
    fun `stale PENDING rows are expired on next begin`() = runTest {
        val store = FakeStore()
        // A leftover PENDING row from a killed process, deadline long past.
        store.rows["stale"] = Transaction(
            transactionId = "stale",
            amount = "75",
            status = TransactionStatus.PENDING,
            bankName = "",
            smsExcerpt = "",
            timestamp = 0L,
            deadlineAt = 1L
        )
        val (manager, _, _) = newManager(store = store)

        advanceTimeBy(10_000)
        manager.begin("9876543210", "100")
        runCurrent()

        assertEquals(TransactionStatus.UNVERIFIED, store.rows["stale"]!!.status)
    }
}
