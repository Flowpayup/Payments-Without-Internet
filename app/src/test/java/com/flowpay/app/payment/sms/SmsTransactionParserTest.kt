package com.flowpay.app.payment.sms

import com.flowpay.app.data.TransactionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end corpus test for [SmsTransactionParser.parse] — the pure
 * bank-SMS matching pipeline extracted from [com.flowpay.app.helpers.TransactionDetector].
 * Every sample below is redacted-but-real-shaped (account digits masked,
 * reference numbers randomized) rather than synthetic, so this corpus is
 * also the reference for what a new bank's SMS template should look like.
 * See [com.flowpay.app.helpers.SmsParsingRegexTest] for focused tests of
 * the individual regex-backed building blocks.
 */
class SmsTransactionParserTest {

    private data class BankCase(
        val bank: String,
        val sender: String,
        val body: String,
        val amount: String
    )

    // One representative success SMS per supported bank, deliberately
    // varying the amount format (Rs./INR/₹, comma grouping, decimals) and
    // the wording (debited/sent to/paid to/transferred) across entries so
    // the corpus exercises the full pattern set, not just one shape.
    private val bankCorpus = listOf(
        BankCase(
            "HDFC Bank",
            "VK-HDFCBK",
            "Rs.500.00 sent to KIRANA STORE from HDFC Bank A/c **1234 via UPI ref 512233440091 on 20-MAY-26",
            "500.00"
        ),
        BankCase(
            "ICICI Bank",
            "AD-ICICIB",
            "INR 1,250.50 sent to Rahul Sharma from your ICICI A/c **5566 on 20MAY26 via UPI Ref 778899001122",
            "1250.50"
        ),
        BankCase(
            "State Bank of India",
            "BP-SBIINB",
            "Rs 12,000 debited from your SBI A/c **7788 via UPI to 9876543210 on 20/05/26. Ref: 334455667788",
            "12000"
        ),
        BankCase(
            "Axis Bank",
            "AX-AXISBK",
            "₹250.00 paid to merchant from Axis Bank A/c **9012. UPI Ref: 998877665544",
            "250.00"
        ),
        BankCase(
            "Kotak Bank",
            "KT-KOTAKB",
            "100 Rs debited from Kotak A/c **3456. UPI Ref 112233445566",
            "100"
        ),
        BankCase(
            "Punjab National Bank",
            "PN-PNBSMS",
            "Rs 3,499.00 transferred from PNB A/c **6789 to VPA merchant@okaxis. Ref 223344556677",
            "3499.00"
        ),
        BankCase(
            "Bank of Baroda",
            "BB-BOBIBK",
            "INR 7500.25 sent to Priya Nair from Bank of Baroda A/c **2233 via UPI. Ref 445566778899",
            "7500.25"
        ),
        BankCase(
            "IDFC First Bank",
            "ID-IDFCFB",
            "Rs 999 debited from IDFC A/c **4455 via UPI. Payment of Rs 999 to merchant@ybl. Ref 556677889900",
            "999"
        ),
        BankCase(
            "Yes Bank",
            "YB-YESBNK",
            "Rs 42 transferred from Yes Bank A/c **6677 via UPI Ref 667788990011. Txn successful.",
            "42"
        ),
        BankCase(
            "Paytm Payments Bank",
            "PT-PAYTMB",
            "Rs.300.00 paid to Rohit Kumar from Paytm Payments Bank A/c via UPI. Ref 778899001122",
            "300.00"
        ),
        BankCase(
            "Union Bank",
            "UB-UNIONB",
            "Rs 15,000 debited from Union Bank A/c **8899 via UPI to 9988776655. Ref 889900112233",
            "15000"
        ),
        BankCase(
            "Canara Bank",
            "CN-CANBNK",
            "INR 620.75 sent to Anita Desai from Canara Bank A/c **1122 via UPI. Ref 990011223344",
            "620.75"
        ),
        BankCase(
            "IndusInd Bank",
            "IB-INDUSB",
            "Rs 88 debited from IndusInd Bank A/c **3344 via UPI. Payment of Rs 88 for Coffee. Ref 001122334455",
            "88"
        ),
        BankCase(
            "Federal Bank",
            "FB-FEDBNK",
            "Rs 5,000.00 transferred from Federal Bank A/c **5566 to merchant@fbl via UPI. Ref 112233445566",
            "5000.00"
        )
    )

    @Test
    fun `parses a success SMS for every supported bank`() {
        for (case in bankCorpus) {
            val result = SmsTransactionParser.parse(case.sender, case.body, expectedAmount = null)

            assertNotNull("expected a match for ${case.bank}: ${case.body}", result)
            assertEquals(case.bank, result!!.bankName)
            assertEquals(case.amount, result.amount)
            assertEquals(TransactionStatus.SUCCESS, result.status)
            assertEquals("DEBIT", result.transactionType)
        }
    }

    @Test
    fun `parses a failure SMS across several banks`() {
        val cases = listOf(
            BankCase(
                "HDFC Bank",
                "VK-HDFCBK",
                "Payment of Rs 500 failed. Ref 123456789012. -HDFC Bank",
                "500"
            ),
            BankCase(
                "ICICI Bank",
                "AD-ICICIB",
                "Your UPI payment of Rs.75 could not be processed. Please retry. -ICICI Bank",
                "75"
            ),
            BankCase(
                "State Bank of India",
                "BP-SBIINB",
                "Rs 120 reversed to your SBI account **5678. Ref 998877",
                "120"
            )
        )
        for (case in cases) {
            val result = SmsTransactionParser.parse(case.sender, case.body, expectedAmount = null)

            assertNotNull("expected a match for ${case.bank}: ${case.body}", result)
            assertEquals(TransactionStatus.FAILED, result!!.status)
        }
    }

    @Test
    fun `incoming CREDIT sms is parsed with sender info, not recipient info`() {
        val result = SmsTransactionParser.parse(
            sender = "AD-ICICIB",
            body = "INR 1,500.00 credited to ICICI A/c **5566 from Sanjay Mehta via UPI. Ref 665544332211",
            expectedAmount = null
        )

        assertNotNull(result)
        assertEquals("CREDIT", result!!.transactionType)
        assertEquals(TransactionStatus.SUCCESS, result.status)
        assertEquals("1500.00", result.amount)
    }

    @Test
    fun `non-UPI debit alert still qualifies via the generic amount fallback`() {
        // No UPI wording and none of the SUCCESS_INDICATORS verbs — this is
        // deliberately permissive (documented in SmsTransactionParser): any
        // bank SMS carrying a Rs/INR amount is treated as a candidate
        // transaction, since banks phrase ATM/card alerts inconsistently.
        val result = SmsTransactionParser.parse(
            sender = "SB-SBIINB",
            body = "Rs 2000 withdrawn from SBI ATM Card **9021 on 20-MAY-26. Avl Bal Rs 34,210.00.",
            expectedAmount = null
        )

        assertNotNull(result)
        assertEquals("State Bank of India", result!!.bankName)
        assertEquals("2000", result.amount)
    }

    @Test
    fun `amount formats — Rs, INR, rupee symbol, and Indian lakh grouping`() {
        assertEquals("500.00", SmsTransactionParser.extractAmount("Rs.500.00 sent to KIRANA STORE"))
        assertEquals("1250.50", SmsTransactionParser.extractAmount("INR 1,250.50 credited"))
        assertEquals("250.00", SmsTransactionParser.extractAmount("₹250.00 paid to merchant"))
        // Indian numbering groups by lakh/crore after the first three digits.
        assertEquals("100000.00", SmsTransactionParser.extractAmount("Rs 1,00,000.00 transferred to merchant"))
    }

    @Test
    fun `mismatched-amount debit is not this payment's confirmation and is dropped`() {
        // The window is waiting for a Rs.500 confirmation; a Rs.100 debit is a
        // different transaction. Returning null leaves the window open for the
        // real confirmation instead of consuming it or mis-recording this one.
        val result = SmsTransactionParser.parse(
            sender = "VK-HDFCBK",
            body = "Rs.100.00 sent to KIRANA STORE from HDFC Bank A/c **1234 via UPI ref 512233440091",
            expectedAmount = "500"
        )

        assertNull("a debit for a different amount is not our confirmation", result)
    }

    @Test
    fun `unrelated failure alert with a mismatched amount never confirms this payment`() {
        // The money-loss blocker: while a Rs.500 payment is pending, an
        // unrelated Rs.200 decline must NOT be recorded as this payment's
        // FAILED (which would offer a retry of money that already moved).
        val result = SmsTransactionParser.parse(
            sender = "VK-HDFCBK",
            body = "HDFC Bank card txn of Rs.200.00 at AMAZON declined: insufficient balance. Ref 700000000001",
            expectedAmount = "500"
        )

        assertNull("a mismatched-amount failure alert is not this payment's failure", result)
    }

    @Test
    fun `failure alert with a matching amount is recorded as this payment's FAILED`() {
        // A genuine failure of THIS payment (amount matches) must still surface
        // as FAILED — the failure keyword wins over the success wording.
        val result = SmsTransactionParser.parse(
            sender = "VK-HDFCBK",
            body = "Payment of Rs.500.00 to KIRANA STORE failed. UPI ref 512233440091 -HDFC Bank",
            expectedAmount = "500"
        )

        assertNotNull(result)
        assertEquals(TransactionStatus.FAILED, result!!.status)
    }

    @Test
    fun `expected-amount match is SUCCESS`() {
        val result = SmsTransactionParser.parse(
            sender = "VK-HDFCBK",
            body = "Rs.500.00 sent to KIRANA STORE from HDFC Bank A/c **1234 via UPI ref 512233440091",
            expectedAmount = "500"
        )

        assertNotNull(result)
        assertEquals(TransactionStatus.SUCCESS, result!!.status)
    }

    @Test
    fun `sms with no bank affiliation and no amount does not match`() {
        val result = SmsTransactionParser.parse(
            sender = "JD-BIGSALE",
            body = "Flash sale starts now! Check the app for today's deals.",
            expectedAmount = null
        )

        assertNull(result)
    }

    @Test
    fun `claim key converges for a full PDU body and its notification-truncated form`() {
        val fullBody = bankCorpus.first().body +
            ". Avl bal Rs 12,345.67. Call 18002586161 to report fraud. Never share your UPI PIN with anyone."
        val truncated = fullBody.take(140) // simulates a notification EXTRA_TEXT cutoff

        assertEquals(
            SmsTransactionParser.claimKey(fullBody),
            SmsTransactionParser.claimKey(truncated)
        )
    }

    @Test
    fun `promo SMS containing the word YES never enters the pipeline as Yes Bank`() {
        // "YES" is an everyday word; only the full bank phrase (or a matching
        // sender header) may attribute a body to Yes Bank. Before this rule a
        // promo with an amount could enter the payment pipeline during an
        // operation window — and with a matching amount, auto-confirm it.
        val result = SmsTransactionParser.parse(
            sender = "JD-PROMO4U",
            body = "Say YES to win Rs 5000! Reply YES to enter the lucky draw today.",
            expectedAmount = "5000"
        )
        assertNull(result)

        // The real thing still matches — by sender header...
        assertEquals("Yes Bank", SmsTransactionParser.detectBank("YB-YESBNK", "Rs 100 debited"))
        // ...and by full phrase in the body.
        assertEquals(
            "Yes Bank",
            SmsTransactionParser.detectBank("XX-UNKNOWN", "Rs 100 debited from your Yes Bank A/c")
        )
    }

    @Test
    fun `balance-first template extracts the transaction amount, not the balance`() {
        // Some banks lead with the balance. With no expected amount (QR flow)
        // a first-match extraction would store the balance as the payment.
        val result = SmsTransactionParser.parse(
            sender = "VK-SBIUPI",
            body = "Avl Bal Rs 34,210.00 in A/c X1234. Rs 2000 debited for UPI txn 512233440091 -SBI",
            expectedAmount = null
        )

        assertNotNull(result)
        assertEquals("2000", result!!.amount)
    }

    @Test
    fun `balance-only body still extracts something rather than nothing`() {
        // If every amount in the body is a balance figure, fall back to it —
        // the NEEDS_REVIEW mismatch tier is the safety net in the manual flow.
        assertEquals(
            "12345.67",
            SmsTransactionParser.extractAmount("Avl bal Rs 12,345.67 in your account")
        )
    }

    @Test
    fun `transaction id is deterministic under an injected clock and random suffix`() {
        // No "ref"/"txn"/"id" wording and no run of 10+ alphanumeric chars,
        // so extractTransactionId finds nothing and parse() falls through
        // to generateTransactionId — the case this test targets.
        val body = "Flash cashback of Rs 200 credited to your wallet."
        val fixedClock = { 1_700_000_000_000L }
        val fixedSuffix = { 4321 }

        val first = SmsTransactionParser.parse("YB-YESBNK", body, null, fixedClock, fixedSuffix)
        val second = SmsTransactionParser.parse("YB-YESBNK", body, null, fixedClock, fixedSuffix)

        assertNotNull(first)
        assertEquals("TXN17000000000004321", first!!.transactionId)
        assertEquals("deterministic inputs must produce the same id", first.transactionId, second!!.transactionId)
    }

    @Test
    fun `transaction id derived from a reference number carries the injected clock, not wall time`() {
        val result = SmsTransactionParser.parse(
            sender = "VK-HDFCBK",
            body = "Rs.500.00 sent to KIRANA STORE from HDFC Bank A/c **1234 via UPI ref 512233440091",
            expectedAmount = null,
            clock = { 42L }
        )

        assertNotNull(result)
        assertTrue(result!!.transactionId.endsWith("_42"))
    }
}
