package com.flowpay.app.payment

import com.flowpay.app.states.PaymentState
import com.flowpay.app.states.TimeoutType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure-logic tests for the state -> unverified-surface mapping that
 * [UnverifiedOutcomeObserver] uses. Only a bank-verification timeout should
 * ever trigger the UNVERIFIED result screen; every other state must not.
 */
class UnverifiedOutcomeObserverTest {

    @Test
    fun `bank-verification timeout maps to a surface carrying its fields`() {
        val surface = PaymentState.Timeout(
            timeoutType = TimeoutType.BANK_VERIFICATION,
            phoneNumber = "9876543210",
            amount = "250",
            transactionId = "txn-1"
        ).toUnverifiedSurface()

        assertEquals("txn-1", surface?.transactionId)
        assertEquals("250", surface?.amount)
        assertEquals("9876543210", surface?.phoneNumber)
    }

    @Test
    fun `non-verification timeouts do not surface as unverified`() {
        val others = listOf(
            TimeoutType.CALL_INITIATION,
            TimeoutType.USER_RESPONSE,
            TimeoutType.NETWORK_CONNECTION
        )
        others.forEach { type ->
            val surface = PaymentState.Timeout(
                timeoutType = type,
                phoneNumber = "9876543210",
                amount = "250",
                transactionId = "txn-1"
            ).toUnverifiedSurface()
            assertNull("timeoutType $type must not map to unverified", surface)
        }
    }

    @Test
    fun `terminal and in-progress states never surface as unverified`() {
        val states = listOf(
            PaymentState.Idle,
            PaymentState.Initiating("9876543210", "250"),
            PaymentState.InProgress("step", 0.4f, "9876543210", "250", "txn-1"),
            PaymentState.WaitingForVerification(600_000, "9876543210", "250", "txn-1"),
            PaymentState.Success("txn-1", "9876543210", "250"),
            PaymentState.Failed("err", null, "9876543210", "250", "txn-1"),
            PaymentState.NeedsReview("txn-1", "9876543210", "250"),
            PaymentState.Cancelled("9876543210", "250", "txn-1")
        )
        states.forEach { state ->
            assertNull(
                "${state::class.simpleName} must not map to unverified",
                state.toUnverifiedSurface()
            )
        }
    }
}
