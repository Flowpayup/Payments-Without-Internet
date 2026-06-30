package com.flowpay.app.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Smoke tests for the SMS-parsing regex patterns used by [TransactionDetector].
 *
 * The patterns are intentionally duplicated here rather than referenced from the
 * production class — [TransactionDetector.processSMS] requires an Android [android.content.Context]
 * (SharedPreferences-backed state), which is awkward to fake in a pure-JVM test
 * without pulling in Robolectric.
 *
 * The production patterns in [TransactionDetector] are the source of truth. If
 * those patterns change, mirror the change here too. The value of this file is
 * twofold:
 *
 *   1. It proves the test infrastructure works (someone can `./gradlew test`).
 *   2. It documents the regex shape we depend on, against real bank SMS samples.
 */
class SmsParsingRegexTest {

    /** Bank detection — case-insensitive substring match against known issuer codes. */
    private val bankKeywords = mapOf(
        "HDFC" to "HDFC Bank",
        "ICICI" to "ICICI Bank",
        "SBI" to "State Bank of India",
        "AXIS" to "Axis Bank",
        "KOTAK" to "Kotak Bank"
    )

    /** Amount extraction — multiple formats handled. */
    private val amountPatterns = listOf(
        Regex("(?:Rs\\.?|INR|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", RegexOption.IGNORE_CASE),
        Regex("([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:Rs\\.?|INR|₹)", RegexOption.IGNORE_CASE)
    )

    private fun detectBank(sender: String, body: String): String? =
        bankKeywords.entries.firstOrNull { (key, _) ->
            sender.contains(key, ignoreCase = true) || body.contains(key, ignoreCase = true)
        }?.value

    private fun extractAmount(body: String): String? {
        for (pattern in amountPatterns) {
            val match = pattern.find(body)
            if (match != null) return match.groupValues[1].replace(",", "")
        }
        return null
    }

    @Test
    fun `HDFC debit SMS — bank and amount extracted`() {
        val sender = "VK-HDFCBK"
        val body = "Rs.500.00 sent to KIRANA STORE from HDFC Bank A/c **1234 via UPI ref 123456789012 on 20-MAY-26"
        assertEquals("HDFC Bank", detectBank(sender, body))
        assertEquals("500.00", extractAmount(body))
    }

    @Test
    fun `ICICI credit SMS — comma-separated amount`() {
        val sender = "AD-ICICIB"
        val body = "INR 1,250.50 credited to your ICICI A/c on 20MAY26 from Rahul Sharma via UPI"
        assertEquals("ICICI Bank", detectBank(sender, body))
        assertEquals("1250.50", extractAmount(body))
    }

    @Test
    fun `SBI USSD-style SMS — large amount with commas`() {
        val sender = "BP-SBIINB"
        val body = "Rs 12,000 debited from your SBI A/c via UPI 9876543210 on 20/05/26. Ref: 123456789"
        assertEquals("State Bank of India", detectBank(sender, body))
        assertEquals("12000", extractAmount(body))
    }

    @Test
    fun `Axis Bank with rupee symbol`() {
        val sender = "AX-AXISBK"
        val body = "₹ 250.00 paid to merchant from Axis Bank A/c. UPI Ref: 99887766"
        assertEquals("Axis Bank", detectBank(sender, body))
        assertEquals("250.00", extractAmount(body))
    }

    @Test
    fun `Kotak SMS — amount before Rs suffix`() {
        val sender = "KT-KOTAKB"
        val body = "100 Rs debited from Kotak A/c **5678. UPI Ref 11223344"
        assertEquals("Kotak Bank", detectBank(sender, body))
        assertEquals("100", extractAmount(body))
    }

    @Test
    fun `Promotional SMS — not detected as a bank transaction`() {
        val sender = "JD-OFFERS"
        val body = "Win a Rs 50000 voucher today! Reply YES to enter."
        assertNull(detectBank(sender, body))
    }

    @Test
    fun `Amount extraction handles thousand separators and decimals`() {
        assertEquals("1234567", extractAmount("Rs 1,234,567 transferred"))
        assertEquals("99.99", extractAmount("INR 99.99 paid"))
        assertEquals("0.01", extractAmount("Rs 0.01 micro-debit"))
    }

    @Test
    fun `Body without amount returns null`() {
        assertNull(extractAmount("Your OTP for HDFC is 123456. Do not share."))
    }
}
