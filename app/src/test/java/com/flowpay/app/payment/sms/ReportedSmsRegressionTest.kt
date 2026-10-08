// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

@file:Suppress("MagicNumber") // Explicit fixture values and timings make regressions readable.

package com.flowpay.app.payment.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReportedSmsRegressionTest {
    @Test
    fun `words account masks and helplines are not references`() {
        listOf(
            "Rs.500.00 debited successfully from your A/c XX1234 to shop@okaxis",
            "Rs.500.00 debited from HDFC Bank A/c XX1234 to VPA shop@okaxis. Not you? Call 18002586161",
            "Your txn of Rs.500 to SHOP via UPI is successful. A/c XXXXXX1234",
            "Rs.500 debited from A/c XXXXXX1234 on 01-08-26"
        ).forEach { body -> assertNull(SmsTransactionParser.extractTransactionId(body) { 123L }) }
    }

    @Test
    fun `SBI debited by template includes amount reference and merchant`() {
        val result = SmsTransactionParser.parse(
            "VM-SBIUPI",
            "Dear UPI user A/C X1234 debited by 150.0 on date 05Mar24 trf to SWIGGY " +
                "Refno 406512345678. If not u? call 1800111109. -SBI",
            "150",
            clock = { 123L }
        )
        assertNotNull(result)
        assertEquals("150.0", result!!.amount)
        assertEquals("DEBIT", result.transactionType)
        assertEquals("Swiggy", result.recipientName)
        assertEquals("406512345678_123", result.transactionId)
    }
}
