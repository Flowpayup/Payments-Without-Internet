package com.flowpay.app.payment

import com.flowpay.app.data.Transaction
import com.flowpay.app.data.TransactionSource
import com.flowpay.app.data.TransactionStatus
import com.flowpay.app.payment.sms.SimpleTransaction
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
        // synchronizedMap: the concurrency test below drives this store from
        // real JVM threads on a real dispatcher (not the single-threaded
        // TestDispatcher every other test uses), so it needs actual
        // thread-safety, not just single-threaded test-scheduler ordering.
        val rows = java.util.Collections.synchronizedMap(LinkedHashMap<String, Transaction>())

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
            parsed: SimpleTransaction,
            verifiedAt: Long
        ): Int {
            // Mirrors the DAO's status guard: a confirmation only lands on a
            // row still awaiting one, never on a cancelled or settled row.
            val row = rows[transactionId]?.takeIf { it.status == TransactionStatus.PENDING }
                ?: return 0
            rows[transactionId] = row.copy(
                status = status,
                bankRef = parsed.transactionId,
                bankName = parsed.bankName,
                smsExcerpt = parsed.smsExcerpt,
                // Mirrors the DAO: sparser SMS data never erases known values;
                // amount fills only when the row started without one (QR flow).
                upiId = parsed.upiId ?: row.upiId,
                recipientName = parsed.recipientName ?: row.recipientName,
                amount = row.amount.ifEmpty { parsed.amount },
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
            scope = backgroundScope,
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
        transactionType = transactionType
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

    // No confirming SMS means there is no payment to report: the row is
    // discarded rather than left behind as an outcome the user can't act on.
    // The Timeout state is still emitted so collectors wind themselves down.
    @Test
    fun `no SMS before deadline discards the row and emits Timeout`() = runTest {
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
        assertNull("An unconfirmed payment must leave no row behind", store.rows[txnId])
    }

    @Test
    fun `QR session records VPA and source, and the confirming SMS fills the amount`() = runTest {
        // The QR flow starts before the user has typed an amount into the
        // USSD menu, and pays a VPA rather than a phone number. The PENDING
        // row must still capture what IS known, and the bank's confirmation
        // must fill the missing amount — QR payments used to bypass the
        // session entirely and leave no trace when no SMS arrived.
        val (manager, store, source) = newManager()

        val txnId = manager.begin(
            phoneNumber = "",
            amount = "",
            upiId = "kirana@okhdfcbank",
            source = TransactionSource.QR
        )!!
        runCurrent()

        val pending = store.rows[txnId]!!
        assertEquals(TransactionStatus.PENDING, pending.status)
        assertEquals("kirana@okhdfcbank", pending.upiId)
        assertEquals(TransactionSource.QR, pending.source)

        source.callStarted(at = testScheduler.currentTime)
        runCurrent()
        manager.onSmsConfirmed(bankSms(amount = "750"))
        runCurrent()

        val confirmed = store.rows[txnId]!!
        assertEquals(TransactionStatus.SUCCESS, confirmed.status)
        assertEquals("SMS amount must fill the empty QR amount", "750", confirmed.amount)
        assertEquals("VPA must survive a sparser SMS", "kirana@okhdfcbank", confirmed.upiId)
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
    fun `truly concurrent confirmations - exactly one claims the session`() {
        // The check-and-claim inside onSmsConfirmed is a single atomic step.
        // Before, the check and the terminal transition sat in two separate
        // locks, so two near-simultaneous SMS (both pipelines, or a debug
        // injection racing a real message) could both observe InProgress and
        // both write a terminal outcome, last-writer-wins.
        //
        // Deliberately NOT runTest/backgroundScope: this races real JVM
        // threads against the manager, and kotlinx-coroutines-test's virtual
        // TestDispatcher is single-threaded and driven only by the test's
        // own coroutine (runCurrent()) — calling into it concurrently from
        // unmanaged threads while the test body blocks on a raw
        // CountDownLatch previously deadlocked the whole test run. A real
        // dispatcher exercises the actual production concurrency instead of
        // fighting the test scheduler, and every wait below is
        // timeout-bounded so a genuine regression fails this test in seconds
        // instead of hanging the build again.
        val store = FakeStore()
        val source = FakeCallStateSource()
        val manager = PaymentSessionManager(
            store = store,
            coordinator = source,
            scope = kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default
            ),
            clock = System::currentTimeMillis
        )

        val txnId = manager.begin("9876543210", "100")!!
        // begin() subscribes to sessionEvents via scope.launch on the real
        // dispatcher — that attach is asynchronous. sessionEvents has no
        // replay buffer, so emitting before the collector subscribes would
        // silently lose the event. Wait for the subscription before emitting.
        awaitTrue("session must subscribe to call events") {
            source.sessionEvents.subscriptionCount.value >= 1
        }
        source.callStarted(at = System.currentTimeMillis())
        awaitTrue("session must reach InProgress") { manager.paymentState.value is PaymentState.InProgress }

        val claimed = raceTwoConfirmations(
            manager,
            bankSms(status = TransactionStatus.SUCCESS),
            bankSms(status = TransactionStatus.FAILED)
        )
        assertEquals("exactly one confirmation may claim the session", 1, claimed.size)
        assertEquals(txnId, claimed.single())

        // The winner's persistence write is fire-and-forget on the manager's
        // scope; wait for it to land before asserting the stored outcome.
        awaitTrue("winning confirmation must be persisted") {
            store.rows[txnId]?.status in setOf(TransactionStatus.SUCCESS, TransactionStatus.FAILED)
        }
        val terminal = manager.paymentState.value
        val expectedRowStatus = if (terminal is PaymentState.Failed) {
            TransactionStatus.FAILED
        } else {
            TransactionStatus.SUCCESS
        }
        assertEquals(expectedRowStatus, store.rows[txnId]!!.status)
    }

    /**
     * Calls [manager].onSmsConfirmed with [smsA] and [smsB] from two real
     * threads released simultaneously, and returns the non-null results
     * (i.e. the winner(s)). Any exception inside a racing thread is
     * re-thrown here rather than silently swallowed by the thread's default
     * handler, and every wait is timeout-bounded so a genuine regression
     * fails this test in seconds instead of hanging the build.
     */
    private fun raceTwoConfirmations(
        manager: PaymentSessionManager,
        smsA: SimpleTransaction,
        smsB: SimpleTransaction
    ): List<String> {
        // ConcurrentLinkedQueue rejects null elements (throws NPE on
        // add(null)), and a losing onSmsConfirmed() call correctly returns
        // null — CopyOnWriteArrayList allows it.
        val results = java.util.concurrent.CopyOnWriteArrayList<String?>()
        val failures = java.util.concurrent.CopyOnWriteArrayList<Throwable>()
        val startGate = java.util.concurrent.CountDownLatch(1)
        val done = java.util.concurrent.CountDownLatch(2)
        fun race(sms: SimpleTransaction) = Thread {
            try {
                startGate.await()
                results.add(manager.onSmsConfirmed(sms))
            } catch (t: Throwable) {
                failures.add(t)
            } finally {
                done.countDown()
            }
        }
        race(smsA).start()
        race(smsB).start()
        startGate.countDown()
        val finished = done.await(5, java.util.concurrent.TimeUnit.SECONDS)
        assertTrue(
            "both racing threads must finish within the timeout" +
                (failures.firstOrNull()?.let { " (threw: $it)" } ?: ""),
            finished
        )
        assertTrue("no exception during the race: ${failures.firstOrNull()}", failures.isEmpty())
        return results.filterNotNull()
    }

    /** Polls [condition] until true, failing the test after 5s rather than hanging. */
    private fun awaitTrue(message: String, condition: () -> Boolean) {
        val deadlineMs = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadlineMs) {
            if (condition()) return
            Thread.sleep(10)
        }
        org.junit.Assert.fail("Timed out waiting for: $message")
    }

    @Test
    fun `incoming CREDIT SMS never confirms an outgoing DEBIT session`() = runTest {
        val (manager, store, source) = newManager()
        val txnId = manager.begin("9876543210", "100")!!
        runCurrent()
        source.callStarted(at = testScheduler.currentTime)
        runCurrent()

        val claimed = manager.onSmsConfirmed(bankSms(transactionType = "CREDIT"))
        runCurrent()

        assertNull("credit SMS must not be claimed by a debit session", claimed)
        assertTrue(
            "session must stay live for the real confirmation",
            manager.paymentState.value is PaymentState.InProgress
        )
        assertEquals(TransactionStatus.PENDING, store.rows[txnId]!!.status)
    }

    @Test
    fun `NEEDS_REVIEW confirmation yields NEEDS_REVIEW row and NeedsReview state`() = runTest {
        val (manager, store, source) = newManager()
        val txnId = manager.begin("9876543210", "100")!!
        runCurrent()
        source.callStarted(at = testScheduler.currentTime)
        runCurrent()

        val claimed = manager.onSmsConfirmed(
            bankSms(status = TransactionStatus.NEEDS_REVIEW, amount = "499")
        )
        runCurrent()

        assertEquals(txnId, claimed)
        assertTrue(manager.paymentState.value is PaymentState.NeedsReview)
        assertEquals(TransactionStatus.NEEDS_REVIEW, store.rows[txnId]!!.status)
    }

    @Test
    fun `second onSmsConfirmed after terminal state is not claimed`() = runTest {
        val (manager, store, source) = newManager()
        val txnId = manager.begin("9876543210", "100")!!
        runCurrent()
        source.callStarted(at = testScheduler.currentTime)
        runCurrent()

        val first = manager.onSmsConfirmed(bankSms())
        runCurrent()
        val second = manager.onSmsConfirmed(bankSms())
        runCurrent()

        assertEquals(txnId, first)
        assertNull("terminal session must not claim a second SMS", second)
        assertEquals(TransactionStatus.SUCCESS, store.rows[txnId]!!.status)
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

    // -------------------------------------------------------------------
    // onUserCancelled / onCallNeverStarted — CallOverlayService's overlay
    // handlers call these unconditionally, before any UI/telecom cleanup
    // that could throw (see CallOverlayService.handleTerminateCall and
    // .startTimeoutTimer). These tests prove the transition itself is
    // correct and safe to invoke more than once, which is what makes that
    // call-before-cleanup ordering — and a defensive retry from a catch
    // block — safe rather than merely convenient.
    // -------------------------------------------------------------------

    @Test
    fun `user cancellation from the overlay ends the session as CANCELLED`() = runTest {
        val (manager, store, source) = newManager()
        val txnId = manager.begin("9876543210", "100")!!
        runCurrent()
        source.callStarted(at = testScheduler.currentTime)
        runCurrent()

        manager.onUserCancelled()
        runCurrent()

        assertTrue(manager.paymentState.value is PaymentState.Cancelled)
        assertEquals(TransactionStatus.CANCELLED, store.rows[txnId]!!.status)
        assertEquals("coordinator must be released on terminal state", 1, source.releaseCount)
    }

    @Test
    fun `onUserCancelled is a safe no-op with no active session`() = runTest {
        val (manager, _, _) = newManager()

        // Simulates a stray overlay callback (or a defensive retry after a
        // caught exception) with nothing in flight — must not throw.
        manager.onUserCancelled()
        runCurrent()

        assertEquals(PaymentState.Idle, manager.paymentState.value)
    }

    @Test
    fun `onUserCancelled called twice does not double-transition or crash`() = runTest {
        val (manager, store, source) = newManager()
        val txnId = manager.begin("9876543210", "100")!!
        runCurrent()
        source.callStarted(at = testScheduler.currentTime)
        runCurrent()

        // Mirrors CallOverlayService.handleTerminateCall calling this again
        // from its catch block after cleanup below it throws.
        manager.onUserCancelled()
        runCurrent()
        manager.onUserCancelled()
        runCurrent()

        assertTrue(manager.paymentState.value is PaymentState.Cancelled)
        assertEquals(TransactionStatus.CANCELLED, store.rows[txnId]!!.status)
        assertEquals("second call must not release the coordinator again", 1, source.releaseCount)
    }

    @Test
    fun `call never starting during Initiating ends the session as CANCELLED`() = runTest {
        val (manager, store, source) = newManager()
        val txnId = manager.begin("9876543210", "100")!!
        runCurrent()

        assertTrue(
            "precondition: session must still be Initiating (no OFFHOOK yet)",
            manager.paymentState.value is PaymentState.Initiating
        )

        manager.onCallNeverStarted()
        runCurrent()

        assertTrue(manager.paymentState.value is PaymentState.Cancelled)
        assertEquals(TransactionStatus.CANCELLED, store.rows[txnId]!!.status)
        assertEquals("coordinator must be released on terminal state", 1, source.releaseCount)
    }

    @Test
    fun `onCallNeverStarted is a no-op once the call has actually started`() = runTest {
        val (manager, store, source) = newManager()
        val txnId = manager.begin("9876543210", "100")!!
        runCurrent()
        source.callStarted(at = testScheduler.currentTime)
        runCurrent()
        assertTrue(manager.paymentState.value is PaymentState.InProgress)

        // The overlay's 40s watchdog firing late, after OFFHOOK already
        // arrived, must not cancel a call that is genuinely in progress.
        manager.onCallNeverStarted()
        runCurrent()

        assertTrue(manager.paymentState.value is PaymentState.InProgress)
        assertEquals(TransactionStatus.PENDING, store.rows[txnId]!!.status)
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
    fun `stale PENDING rows are discarded on next begin`() = runTest {
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

        assertNull("Stale unconfirmed rows must be removed", store.rows["stale"])
    }

    // A cancelled payment must stay cancelled. The user may well re-pay the
    // same amount through another app minutes later; that bank SMS must not
    // be able to rewrite this row into a success.
    @Test
    fun `a confirming SMS cannot resurrect a cancelled row`() = runTest {
        val (manager, store, source) = newManager()
        val txnId = manager.begin("9876543210", "500")!!
        runCurrent()
        source.callStarted(at = testScheduler.currentTime)
        runCurrent()
        manager.onUserCancelled()
        runCurrent()
        assertEquals(TransactionStatus.CANCELLED, store.rows[txnId]!!.status)

        val claimed = manager.onSmsConfirmed(
            SimpleTransaction(
                transactionId = "BANKREF999",
                amount = "500",
                status = TransactionStatus.SUCCESS,
                bankName = "HDFC Bank",
                smsExcerpt = "₹500 debited — HDFC Bank",
                timestamp = 0L,
                transactionType = "DEBIT"
            )
        )
        runCurrent()

        assertNull("A cancelled session must not claim a later SMS", claimed)
        assertEquals(TransactionStatus.CANCELLED, store.rows[txnId]!!.status)
    }
}
