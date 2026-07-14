package com.flowpay.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [Transaction.toPaymentDetails] must preserve every lifecycle status. It used
 * to collapse UNVERIFIED and NEEDS_REVIEW into PENDING, hiding the two outcomes
 * a user most needs to see.
 */
class TransactionMappingTest {

    private fun row(status: String) = Transaction(
        transactionId = "t1",
        amount = "100",
        status = status,
        bankName = "HDFC Bank",
        smsExcerpt = "",
        timestamp = 0L,
        transactionType = "DEBIT",
        phoneNumber = "9876543210"
    )

    @Test
    fun `each canonical status maps to its own PaymentStatus`() {
        assertEquals(PaymentStatus.COMPLETED, row(TransactionStatus.SUCCESS).toPaymentDetails().status)
        assertEquals(PaymentStatus.FAILED, row(TransactionStatus.FAILED).toPaymentDetails().status)
        assertEquals(PaymentStatus.CANCELLED, row(TransactionStatus.CANCELLED).toPaymentDetails().status)
        assertEquals(PaymentStatus.PENDING, row(TransactionStatus.PENDING).toPaymentDetails().status)
        assertEquals(PaymentStatus.UNVERIFIED, row(TransactionStatus.UNVERIFIED).toPaymentDetails().status)
        assertEquals(PaymentStatus.NEEDS_REVIEW, row(TransactionStatus.NEEDS_REVIEW).toPaymentDetails().status)
    }

    @Test
    fun `unverified and needs-review are not collapsed into pending`() {
        val unverified = row(TransactionStatus.UNVERIFIED).toPaymentDetails().status
        val needsReview = row(TransactionStatus.NEEDS_REVIEW).toPaymentDetails().status
        assertEquals(PaymentStatus.UNVERIFIED, unverified)
        assertEquals(PaymentStatus.NEEDS_REVIEW, needsReview)
    }

    @Test
    fun `unknown status falls back to pending`() {
        assertEquals(PaymentStatus.PENDING, row("WHATEVER").toPaymentDetails().status)
    }
}
