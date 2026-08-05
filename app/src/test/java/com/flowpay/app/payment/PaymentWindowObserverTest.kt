// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.payment

import com.flowpay.app.states.PaymentState
import com.flowpay.app.states.TimeoutType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The SMS operation window must close when a payment is cancelled — left
 * armed, it lets a later unrelated debit be adopted onto the cancelled row
 * and reported as a success.
 */
class PaymentWindowObserverTest {

    @Test
    fun `a cancelled payment closes the sms window`() {
        assertTrue(
            PaymentState.Cancelled(
                phoneNumber = "9876543210",
                amount = "500",
                transactionId = "txn-1",
                reason = "Cancelled by user"
            ).shouldDisarmSmsWindow()
        )
    }

    @Test
    fun `a timeout leaves the window open for its remaining grace`() {
        // The window deliberately outlives the verification deadline by 30s so
        // a slow-but-genuine confirmation still lands. Closing it here would
        // discard an SMS that did arrive.
        assertFalse(
            PaymentState.Timeout(
                timeoutType = TimeoutType.BANK_VERIFICATION,
                phoneNumber = "9876543210",
                amount = "500",
                transactionId = "txn-1"
            ).shouldDisarmSmsWindow()
        )
    }

    @Test
    fun `settled and in-progress states never close the window`() {
        // Success and failure consume the window in the ingestion path, and a
        // live payment obviously still needs it.
        val states = listOf(
            PaymentState.Idle,
            PaymentState.Initiating(
                phoneNumber = "9876543210",
                amount = "500",
                transactionId = "txn-1"
            ),
            PaymentState.InProgress(
                step = "On the line with the UPI service",
                progress = 0.5f,
                phoneNumber = "9876543210",
                amount = "500",
                transactionId = "txn-1"
            ),
            PaymentState.Success(
                transactionId = "txn-1",
                phoneNumber = "9876543210",
                amount = "500"
            ),
            PaymentState.Failed(
                error = "declined",
                phoneNumber = "9876543210",
                amount = "500",
                transactionId = "txn-1"
            )
        )
        for (state in states) {
            assertFalse(
                "$state must not close the SMS window",
                state.shouldDisarmSmsWindow()
            )
        }
    }
}
