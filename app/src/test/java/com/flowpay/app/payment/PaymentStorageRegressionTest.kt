// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.payment

import com.flowpay.app.data.Transaction
import com.flowpay.app.data.TransactionStatus
import com.flowpay.app.payment.sms.SimpleTransaction
import com.flowpay.app.states.PaymentState
import com.flowpay.app.telephony.CallSessionEvent
import com.flowpay.app.telephony.CallStateSource
import com.flowpay.app.telephony.DeviceCallState
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentStorageRegressionTest {
    private class NoCallActivity : CallStateSource {
        override val callState = MutableStateFlow<DeviceCallState>(DeviceCallState.Idle)
        override val sessionEvents = MutableSharedFlow<CallSessionEvent>()
        override fun acquire(tag: String) = Unit
        override fun release(tag: String) = Unit
    }

    private class FakeStore : PaymentTransactionStore {
        val rows = mutableMapOf<String, Transaction>()
        var transitionAttempts = 0
        var deletePendingAttempts = 0
        var insertFailure: Exception? = null
        var cleanupFailure: Exception? = null
        var staleCleanupFailure: Exception? = null

        override suspend fun insertPending(transaction: Transaction) {
            insertFailure?.let { throw it }
            rows[transaction.transactionId] = transaction
        }

        override suspend fun transitionStatus(
            transactionId: String,
            expectedStatus: String,
            newStatus: String
        ): Int {
            transitionAttempts++
            cleanupFailure?.let { throw it }
            val row = rows[transactionId]?.takeIf { it.status == expectedStatus } ?: return 0
            rows[transactionId] = row.copy(status = newStatus)
            return 1
        }

        override suspend fun confirmTransaction(
            transactionId: String,
            status: String,
            parsed: SimpleTransaction,
            verifiedAt: Long
        ): Int = error("Storage recovery never confirms a receipt")

        override suspend fun deleteStalePending(now: Long): Int {
            staleCleanupFailure?.let { throw it }
            return 0
        }

        override suspend fun deletePending(transactionId: String): Int {
            deletePendingAttempts++
            cleanupFailure?.let { throw it }
            if (rows[transactionId]?.status != TransactionStatus.PENDING) return 0
            rows.remove(transactionId)
            return 1
        }
    }

    @Test
    fun `failed insertion stays handled through dial failure and next attempt works`() = runTest {
        val store = FakeStore().apply {
            insertFailure = IOException("storage unavailable")
            cleanupFailure = IOException("storage still unavailable")
            staleCleanupFailure = IOException("storage unavailable during startup maintenance")
        }
        val uncaught = mutableListOf<Throwable>()
        val appScope = CoroutineScope(
            SupervisorJob() + StandardTestDispatcher(testScheduler) +
                CoroutineExceptionHandler { _, failure -> uncaught.add(failure) }
        )
        try {
            val manager = PaymentSessionManager(store, NoCallActivity(), appScope)
            manager.reconcileStalePending()
            runCurrent()
            assertTrue("startup storage failure must be handled", uncaught.isEmpty())
            val failedId = manager.begin("9876543210", "100")
            var insertionFailure: IOException? = null
            try {
                manager.awaitPending(failedId)
            } catch (failure: IOException) {
                insertionFailure = failure
                // This is the same recovery path both payment screens use.
                manager.onDialFailed("Payment storage unavailable", failedId)
            }
            runCurrent()
            assertNotNull("await must report failed storage before dialling", insertionFailure)
            assertTrue(manager.paymentState.value is PaymentState.Cancelled)
            assertNull(store.rows[failedId])
            assertEquals("an insertion failure has no row to transition", 0, store.transitionAttempts)
            assertTrue("handled storage must not crash the application scope", uncaught.isEmpty())

            store.insertFailure = null
            store.cleanupFailure = null
            store.staleCleanupFailure = null
            val retryId = manager.begin("9876543210", "100")
            assertTrue(manager.awaitPending(retryId))
            manager.onDialFailed("Call unavailable", retryId)
            runCurrent()
            assertEquals(TransactionStatus.CANCELLED, store.rows[retryId]!!.status)
            assertEquals(1, store.transitionAttempts)
            assertTrue(uncaught.isEmpty())
        } finally {
            appScope.cancel()
        }
    }

    @Test
    fun `superseding a failed insertion never deletes a row that was not stored`() = runTest {
        val store = FakeStore().apply { insertFailure = IOException("storage unavailable") }
        val uncaught = mutableListOf<Throwable>()
        val appScope = CoroutineScope(
            SupervisorJob() + StandardTestDispatcher(testScheduler) +
                CoroutineExceptionHandler { _, failure -> uncaught.add(failure) }
        )
        try {
            val manager = PaymentSessionManager(store, NoCallActivity(), appScope)
            val failedId = manager.begin("9876543210", "100")
            runCurrent()
            store.insertFailure = null
            val nextId = manager.begin("9123456780", "200")
            assertTrue(manager.awaitPending(nextId))
            runCurrent()

            assertNull(store.rows[failedId])
            assertNotNull(store.rows[nextId])
            assertEquals(0, store.deletePendingAttempts)
            assertTrue(uncaught.isEmpty())
        } finally {
            appScope.cancel()
        }
    }

    @Test
    fun `storage failing during terminal cleanup preserves its row without an uncaught crash`() = runTest {
        val store = FakeStore()
        val uncaught = mutableListOf<Throwable>()
        val appScope = CoroutineScope(
            SupervisorJob() + StandardTestDispatcher(testScheduler) +
                CoroutineExceptionHandler { _, failure -> uncaught.add(failure) }
        )
        try {
            val manager = PaymentSessionManager(store, NoCallActivity(), appScope)
            val txnId = manager.begin("9876543210", "100")
            assertTrue(manager.awaitPending(txnId))
            store.cleanupFailure = IOException("storage became unavailable")
            manager.onDialFailed("Call unavailable", txnId)
            runCurrent()

            assertTrue(manager.paymentState.value is PaymentState.Cancelled)
            assertEquals(
                "failed cleanup must retain existing history",
                TransactionStatus.PENDING,
                store.rows[txnId]!!.status
            )
            assertEquals(1, store.transitionAttempts)
            assertTrue(uncaught.isEmpty())
        } finally {
            appScope.cancel()
        }
    }
}
