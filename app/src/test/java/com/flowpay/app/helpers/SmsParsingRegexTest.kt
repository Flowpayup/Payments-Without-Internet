package com.flowpay.app.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // ---------------------------------------------------------------------
    // Failure detection — these call the REAL production logic
    // (TransactionDetector.detectsFailure is internal + Context-free).
    // ---------------------------------------------------------------------

    @Test
    fun `Failure SMS corpus is detected as failed`() {
        val failures = listOf(
            "Payment of Rs 500 failed. Ref 123456789012. -HDFC Bank",
            "Txn of INR 250.00 declined due to insufficient balance in A/c **1234",
            "Rs 120 reversed to your account **5678. Ref 998877 -SBI",
            "Your UPI payment of Rs.75 could not be processed. Please retry. -ICICI Bank",
            "UPI txn of Rs 300 was unsuccessful. A/c not debited. -AXIS",
            "Payment request of Rs 1,000 rejected by beneficiary bank. -KOTAK",
            "Your txn expired. Rs 50 payment was not completed. -PNB"
        )
        for (body in failures) {
            assertTrue("should detect failure: $body", TransactionDetector.detectsFailure(body))
        }
    }

    // ---------------------------------------------------------------------
    // Sender fallback — real production logic (TransactionDetector.detectBank
    // is internal). Locks in the DLT-shaped fallback contract after dropping
    // the blanket `sender.length == 6` check.
    // ---------------------------------------------------------------------

    @Test
    fun `DLT-shaped senders match the generic bank fallback`() {
        // Headers shaped like real DLT bank senders, for banks NOT in the
        // keyword map (a keyword match would win before the fallback).
        val body = "Rs 100 debited via UPI. Ref 123456789012"
        assertEquals("Bank", TransactionDetector.detectBank("VK-DBSBNK", body))   // alphanumeric DLT header
        assertEquals("Bank", TransactionDetector.detectBank("AD-BOIUPI-S", body)) // suffixed DLT header
        assertEquals("Bank", TransactionDetector.detectBank("561616", body))      // numeric shortcode
        assertEquals("Bank", TransactionDetector.detectBank("CTBBNK", body))      // bare 6-letter header
    }

    @Test
    fun `Mixed-case 6-char senders no longer match the fallback`() {
        val promoBody = "Big sale! Get Rs 500 off today only."
        assertNull(TransactionDetector.detectBank("Amazon", promoBody))
        assertNull(TransactionDetector.detectBank("MyShop", promoBody))
        assertNull(TransactionDetector.detectBank("Swiggy", promoBody))
    }

    @Test
    fun `All-caps 6-letter promo sender still matches - documented residual`() {
        // Deliberately kept: real bare DLT headers share this shape (HDFCBK,
        // SBIUPI). Downstream NEEDS_REVIEW/FAILED tiers are the safety net.
        assertEquals("Bank", TransactionDetector.detectBank("AMAZON", "Rs 500 off your next order"))
    }

    @Test
    fun `Bank keyword in sender or body still wins over the fallback`() {
        assertEquals("HDFC Bank", TransactionDetector.detectBank("VM-HDFCBK", "Rs 100 debited"))
        assertEquals("State Bank of India", TransactionDetector.detectBank("XY-123456", "Rs 100 debited from your SBI A/c"))
    }

    // ---------------------------------------------------------------------
    // Cross-pipeline dedupe key
    // ---------------------------------------------------------------------

    @Test
    fun `Claim key converges for full and notification-truncated bodies`() {
        val fullBody = "Rs.500.00 sent to KIRANA STORE from HDFC Bank A/c **1234 via UPI ref 123456789012 " +
            "on 20-MAY-26. Avl bal Rs 12,345.67. Call 18002586161 to report fraud. Never share your UPI PIN with anyone."
        val truncated = fullBody.take(150) // notification EXTRA_TEXT truncation
        assertEquals(
            TransactionDetector.claimKey("VM-HDFCBK", fullBody),
            TransactionDetector.claimKey("VM-HDFCBK", truncated)
        )
        // Different SMS still produce different keys
        assertFalse(
            TransactionDetector.claimKey("VM-HDFCBK", fullBody) ==
                TransactionDetector.claimKey("VM-HDFCBK", "Rs.200.00 sent to SOMEONE ELSE via UPI")
        )
    }

    @Test
    fun `Amount matching tolerates decimal formatting but rejects real mismatches`() {
        // Real production logic (TransactionDetector.isAmountMatching is internal).
        assertTrue(TransactionDetector.isAmountMatching("100", "100.00"))
        assertTrue(TransactionDetector.isAmountMatching("1,250.50", "1250.50"))
        assertFalse("unrelated debit must not match", TransactionDetector.isAmountMatching("499", "100"))
        assertFalse("non-numeric never matches", TransactionDetector.isAmountMatching("abc", "100"))
    }

    @Test
    fun `Success SMS corpus is NOT detected as failed`() {
        // The same real bank samples used above — locks in that adding
        // failure detection never flips a genuine success confirmation.
        val successes = listOf(
            "Rs.500.00 sent to KIRANA STORE from HDFC Bank A/c **1234 via UPI ref 123456789012 on 20-MAY-26",
            "INR 1,250.50 credited to your ICICI A/c on 20MAY26 from Rahul Sharma via UPI",
            "Rs 12,000 debited from your SBI A/c via UPI 9876543210 on 20/05/26. Ref: 123456789",
            "₹ 250.00 paid to merchant from Axis Bank A/c. UPI Ref: 99887766",
            "100 Rs debited from Kotak A/c **5678. UPI Ref 11223344",
            "Txn successful. Rs 42 transferred. Ref 55667788 -YES Bank"
        )
        for (body in successes) {
            assertFalse("should NOT detect failure: $body", TransactionDetector.detectsFailure(body))
        }
    }
}
