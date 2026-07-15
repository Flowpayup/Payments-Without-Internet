package com.flowpay.app.receivers

import com.flowpay.app.data.TransactionStatus
import com.flowpay.app.payment.sms.SimpleTransaction
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The ownership decision for an incoming confirmation: live session first,
 * then reattach-to-orphaned-row (process died mid-payment), else standalone
 * insert. Without the reattach a killed process turned one payment into two
 * rows — a fresh SUCCESS plus the original expiring to UNVERIFIED.
 */
class SmsIngestionPipelineTest {

    private fun parsed(type: String = "DEBIT") = SimpleTransaction(
        transactionId = "BANKREF123",
        amount = "500",
        status = TransactionStatus.SUCCESS,
        bankName = "HDFC Bank",
        smsExcerpt = "₹500 debited — HDFC Bank",
        timestamp = 0L,
        transactionType = type
    )

    @Test
    fun `live session always owns the confirmation`() = runTest {
        var adopted: String? = null
        val owner = SmsIngestionPipeline.resolveOwnerTxnId(
            sessionTxnId = "session-1",
            windowTxnId = "window-1",
            parsed = parsed()
        ) {
            adopted = it
            1
        }

        assertEquals("session-1", owner)
        assertNull("no adoption when a live session claimed it", adopted)
    }

    @Test
    fun `orphaned row is adopted after process death`() = runTest {
        var adopted: String? = null
        val owner = SmsIngestionPipeline.resolveOwnerTxnId(
            sessionTxnId = null,
            windowTxnId = "window-1",
            parsed = parsed()
        ) {
            adopted = it
            1
        }

        assertEquals("window-1", owner)
        assertEquals("window-1", adopted)
    }

    @Test
    fun `missing row falls back to standalone insert`() = runTest {
        val owner = SmsIngestionPipeline.resolveOwnerTxnId(
            sessionTxnId = null,
            windowTxnId = "window-1",
            parsed = parsed()
        ) { 0 } // row no longer exists (e.g. history cleared)

        assertNull(owner)
    }

    @Test
    fun `a CREDIT never adopts the outgoing-debit row`() = runTest {
        var adoptCalled = false
        val owner = SmsIngestionPipeline.resolveOwnerTxnId(
            sessionTxnId = null,
            windowTxnId = "window-1",
            parsed = parsed(type = "CREDIT")
        ) {
            adoptCalled = true
            1
        }

        assertNull(owner)
        assertEquals(false, adoptCalled)
    }

    @Test
    fun `no window txnId means standalone insert`() = runTest {
        val owner = SmsIngestionPipeline.resolveOwnerTxnId(
            sessionTxnId = null,
            windowTxnId = null,
            parsed = parsed()
        ) { 1 }

        assertNull(owner)
    }

    @Test
    fun `adoption failure is contained and falls back to standalone insert`() = runTest {
        val owner = SmsIngestionPipeline.resolveOwnerTxnId(
            sessionTxnId = null,
            windowTxnId = "window-1",
            parsed = parsed()
        ) { error("db unavailable") }

        assertNull(owner)
    }
}
