// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.payment.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BankReferenceTest {
    @Test
    fun `legacy epoch suffix is omitted from displayed and copied reference`() {
        assertEquals("001233440091", BankReference.display("001233440091_1785952502285"))
        assertEquals("HDFC123456", BankReference.display("HDFC123456"))
        assertEquals("XABC00123", BankReference.display("XABC00123"))
    }

    @Test
    fun `missing generated masked and word references are unavailable`() {
        listOf(null, "", "successfully", "XXXXXX1234", "TXN17859525022851234", "3f2b8c1e-abcd-1234")
            .forEach { assertNull(BankReference.display(it)) }
    }

    @Test
    fun `explicit references retain leading zeroes and do not match ordinary txn prose`() {
        assertEquals("001233440091", SmsTransactionParser.extractBankReference("UPI Refno 001233440091."))
        assertEquals("HDFC123456", SmsTransactionParser.extractBankReference("Txn ID: HDFC123456"))
        assertNull(SmsTransactionParser.extractBankReference("Your txn of Rs 500. Call 18002586161"))
        assertNull(SmsTransactionParser.extractBankReference("Merchant ID 123456, mobile 9876543210"))
    }
}
