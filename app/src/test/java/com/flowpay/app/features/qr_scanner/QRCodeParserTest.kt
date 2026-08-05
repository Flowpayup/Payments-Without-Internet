// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.features.qr_scanner

import com.flowpay.app.features.qr_scanner.domain.QRCodeParser
import com.flowpay.app.features.qr_scanner.domain.QRCodeParser.ParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The strict QR contract: only valid `upi://` URIs or bare VPAs are
 * accepted. The old parser regex-fished `pa=` out of arbitrary text and
 * treated any string containing "@" as a payee — every rejection case here
 * is a payment the app would previously have attempted against garbage.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QRCodeParserTest {

    private fun valid(qr: String): com.flowpay.app.data.UPIData {
        val result = QRCodeParser.parse(qr)
        assertTrue("expected Valid for: $qr but was $result", result is ParseResult.Valid)
        return (result as ParseResult.Valid).data
    }

    private fun invalid(qr: String) {
        val result = QRCodeParser.parse(qr)
        assertTrue("expected Invalid for: $qr but was $result", result is ParseResult.Invalid)
    }

    @Test
    fun `full npci qr parses all fields`() {
        val data = valid("upi://pay?pa=merchant@okhdfcbank&pn=Test%20Store&am=150.00&tn=Bill%20payment")
        assertEquals("merchant@okhdfcbank", data.vpa)
        assertEquals("Test Store", data.payeeName)
        assertEquals("150.00", data.amount)
        assertEquals("Bill payment", data.transactionNote)
    }

    @Test
    fun `qr without amount is valid`() {
        val data = valid("upi://pay?pa=a.b@ybl&pn=Someone")
        assertEquals("a.b@ybl", data.vpa)
        assertEquals("", data.amount)
    }

    @Test
    fun `bare vpa qr is valid`() {
        val data = valid("someone@paytm")
        assertEquals("someone@paytm", data.vpa)
    }

    @Test
    fun `scheme is case insensitive`() {
        val data = valid("UPI://PAY?pa=ok@axl")
        assertEquals("ok@axl", data.vpa)
    }

    @Test
    fun `arbitrary text is rejected`() {
        invalid("https://example.com/checkout?id=12345")
        invalid("hello world")
        invalid("")
    }

    @Test
    fun `pa parameter outside a upi uri is rejected`() {
        // The old parser would regex-extract this and dial a payment.
        invalid("randomtext pa=attacker@upi moretext")
    }

    @Test
    fun `upi uri without payee is rejected`() {
        invalid("upi://pay?am=100")
        invalid("upi://pay?pa=")
    }

    @Test
    fun `structurally invalid vpas are rejected`() {
        invalid("upi://pay?pa=x@") // no handle
        invalid("upi://pay?pa=@bank") // no local part
        invalid("upi://pay?pa=a@b@c") // double @
        invalid("upi://pay?pa=has space@ybl")
        invalid("upi://pay?pa=a@1bank") // handle must start with a letter
    }

    @Test
    fun `invalid amounts are rejected`() {
        invalid("upi://pay?pa=ok@ybl&am=abc")
        invalid("upi://pay?pa=ok@ybl&am=0")
        invalid("upi://pay?pa=ok@ybl&am=-5")
        invalid("upi://pay?pa=ok@ybl&am=100001") // above ceiling
        invalid("upi://pay?pa=ok@ybl&am=10.555") // three decimal places
    }

    @Test
    fun `legacy adapter returns empty vpa on invalid input`() {
        assertEquals("", QRCodeParser.parseUPIQRCode("not a qr").vpa)
        assertEquals("ok@ybl", QRCodeParser.parseUPIQRCode("upi://pay?pa=ok@ybl").vpa)
    }

    @Test
    fun `isValidUPIQRCode matches parse verdict`() {
        assertTrue(QRCodeParser.isValidUPIQRCode("upi://pay?pa=ok@ybl"))
        assertTrue(!QRCodeParser.isValidUPIQRCode("random@text with spaces"))
    }
}
