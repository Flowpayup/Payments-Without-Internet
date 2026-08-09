// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.payment.sms

import com.flowpay.app.data.TransactionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        ),
        // Dual-verb templates: the bank narrates both sides of one payment,
        // so the body carries "debited" AND "credited". These are ordinary
        // outgoing confirmations and must parse as DEBIT — read as CREDIT
        // they look like somebody else's incoming money and get dropped.
        BankCase(
            "ICICI Bank",
            "AD-ICICIB",
            "ICICI Bank Acct XX556 debited for Rs 500.00 on 29-Jul-26; KIRANA STORE credited. UPI:512233440091",
            "500.00"
        ),
        BankCase(
            "Union Bank",
            "VM-UNIONB",
            "Rs.2,340.00 debited from Union Bank A/c **8899 and credited to merchant@ubi. UPI Ref 445566778899",
            "2340.00"
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

    // A dual-verb body describes our own outgoing payment from both sides.
    // Classifying it CREDIT would make the ingestion pipeline treat a genuine
    // confirmation as an unrelated incoming credit and drop it, so the
    // payment would never appear even though the SMS arrived.
    @Test
    fun `dual-verb debit template confirms the payment it belongs to`() {
        val result = SmsTransactionParser.parse(
            sender = "AD-ICICIB",
            body = "ICICI Bank Acct XX556 debited for Rs 500.00 on 29-Jul-26; KIRANA STORE credited. UPI:512233440091",
            expectedAmount = "500"
        )

        assertNotNull("a dual-verb debit is our confirmation", result)
        assertEquals("DEBIT", result!!.transactionType)
        assertEquals(TransactionStatus.SUCCESS, result.status)
        assertEquals("500.00", result.amount)
    }

    @Test
    fun `dual-verb debit for a different amount is still dropped`() {
        // Debit precedence must not weaken the amount gate: this is somebody
        // else's payment that merely landed inside our window.
        val result = SmsTransactionParser.parse(
            sender = "AD-ICICIB",
            body = "ICICI Bank Acct XX556 debited for Rs 900.00 on 29-Jul-26; OTHER SHOP credited. UPI:512233440099",
            expectedAmount = "500"
        )

        assertNull("a dual-verb debit for another amount is not our confirmation", result)
    }

    @Test
    fun `debit verbs win over credit words when a body carries both`() {
        assertEquals(
            "DEBIT",
            SmsTransactionParser.detectTransactionType("A/c XX1 debited Rs 10; payee credited")
        )
        // A pure incoming alert has no debit verb and stays CREDIT.
        assertEquals(
            "CREDIT",
            SmsTransactionParser.detectTransactionType("Rs 10 credited to A/c XX1 by UPI")
        )
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
        // Assert the reference itself, not just the clock suffix: an id that
        // ends in "_42" can still carry entirely the wrong prefix, which is
        // exactly how the defect below survived this file.
        assertEquals("512233440091_42", result!!.transactionId)
    }

    // "paid to SHARMA STORE" hides an "id" inside "pa|id|", and the reference
    // pattern had no word boundaries — so it matched there, took the following
    // word, and stamped the receipt for the single most common Indian debit
    // template with "to_<timestamp>". Seen on a real device on 2026-08-05.
    // The reference is the one field a user can carry to their bank statement,
    // so this asserts the digits rather than merely that parsing succeeded.
    @Test
    fun `a reference is read from the bank's ref number, not from inside the word paid`() {
        val result = SmsTransactionParser.parse(
            sender = "VK-HDFCBK",
            body = "Rs.1250.00 paid to SHARMA STORE from HDFC Bank A/c **1234 failed. " +
                "UPI Ref No 512233440091. Not you? Call 18002586161",
            expectedAmount = null,
            clock = { 42L }
        )

        assertNotNull(result)
        assertEquals("512233440091_42", result!!.transactionId)
        assertEquals(TransactionStatus.FAILED, result.status)
    }

    // A failure template used to leave the payee as "Kirana Store Has Failed"
    // — the lazy name group had nothing to stop it before end-of-string, so
    // the status words were captured and title-cased into the name. Seen on a
    // real device on 2026-08-01, on a row the user could open and read.
    @Test
    fun `failure templates do not absorb status words into the payee name`() {
        val bodies = listOf(
            "Rs.200.00 paid to Kirana Store has failed. HDFC Bank",
            "Rs.200 paid to Kirana Store has failed",
            "Payment of Rs 200 sent to Kirana Store was declined"
        )

        for (body in bodies) {
            val result = SmsTransactionParser.parse("VK-HDFCBK", body, null)
            assertNotNull("expected a match for: $body", result)
            assertEquals("payee must stop before the status words: $body", "Kirana Store", result!!.recipientName)
        }
    }

    // The three bodies above all say "paid to" / "sent to", so they are caught
    // by the FIRST recipient pattern — the one that already shared
    // NAME_TERMINATOR. That made the stop-word guard look complete when it was
    // not: a template with no verb before the payee falls through to the bare
    // "to NAME" fallback, which carried its own terminator list with no stop
    // words and a greedy quantifier. On a real device (2026-08-09) that
    // rendered "Big Merchant Has Failed" on the result screen and wrote it to
    // history; the longer "was declined by your bank" variant overran the
    // 30-character ceiling and produced no payee at all.
    @Test
    fun `failure templates with no verb before the payee still stop at the status words`() {
        val cases = mapOf(
            "Your payment of Rs.850.00 to BIG MERCHANT has failed. Ref 850999888777"
                to "Big Merchant",
            "Payment of Rs.300.00 to SHARMA GENERAL STORE was declined by your bank. Ref 300111222"
                to "Sharma General Store",
            "Rs.450.00 to CORNER TEA STALL could not be processed. Ref 450111"
                to "Corner Tea Stall"
        )

        for ((body, expected) in cases) {
            val result = SmsTransactionParser.parse("VK-HDFCBK", body, null)
            assertNotNull("expected a match for: $body", result)
            assertEquals("payee must stop before the status words: $body", expected, result!!.recipientName)
        }
    }

    // The same fallback must keep resolving the templates it always handled —
    // a date or a ref keyword after the payee, with no status words in sight.
    @Test
    fun `bare to-NAME fallback still resolves date and ref terminated payees`() {
        val cases = mapOf(
            "Rs.250.00 debited from A/c XX9999 to CAFE COFFEE DAY on 09-Aug-26. Ref 250333444"
                to "Cafe Coffee Day",
            "Rs.75.00 debited from A/c XX9999 to METRO STORE dated 09-Aug-26"
                to "Metro Store"
        )

        for ((body, expected) in cases) {
            val result = SmsTransactionParser.parse("VK-HDFCBK", body, null)
            assertNotNull("expected a match for: $body", result)
            assertEquals("payee must survive intact: $body", expected, result!!.recipientName)
        }
    }

    // The bare `\s` terminator used to stop the payee at the FIRST space, so
    // every multi-word merchant was truncated to one word on the most common
    // Indian debit template ("sent to NAME from <bank> A/c ..."). Stored rows
    // on a real device read "Kirana", "Tea", "Book" — all truncations of
    // "KIRANA STORE", "TEA STALL", "BOOK STORE". Found on device 2026-08-01.
    @Test
    fun `multi-word payee names are not truncated at the first space`() {
        // cleanupName deliberately title-cases for display; what matters here
        // is that BOTH words survive.
        val cases = mapOf(
            "Rs.500.00 sent to KIRANA STORE from HDFC Bank A/c **1234 via UPI ref 512233440091"
                to "Kirana Store",
            "Rs.100000.00 sent to BIG MERCHANT from HDFC Bank A/c **1234 via UPI ref 512233441000"
                to "Big Merchant",
            "Rs.300 sent to TEA STALL from HDFC Bank A/c **1234 via UPI ref 99"
                to "Tea Stall"
        )

        for ((body, expected) in cases) {
            val result = SmsTransactionParser.parse("VK-HDFCBK", body, null)
            assertNotNull("expected a match for: $body", result)
            assertEquals("payee must not stop at the first space: $body", expected, result!!.recipientName)
        }
    }

    // The stop-word list must not truncate real payees that merely BEGIN with
    // one of those words. These are the names that would break a naive fix.
    @Test
    fun `payee names beginning with a status word survive intact`() {
        val cases = mapOf(
            "Rs.100 paid to Hasty Traders via UPI" to "Hasty Traders",
            "Rs.100 paid to Hasmukh Patel via UPI" to "Hasmukh Patel",
            "Rs.100 paid to Ismail Khan via UPI" to "Ismail Khan",
            "Rs.100 paid to Notandas Stores via UPI" to "Notandas Stores",
            "Rs.100 sent to Arewa Foods via UPI" to "Arewa Foods",
            "Rs.100 sent to Isabella Fernandes via UPI" to "Isabella Fernandes"
        )

        for ((body, expected) in cases) {
            val result = SmsTransactionParser.parse("VK-HDFCBK", body, null)
            assertNotNull("expected a match for: $body", result)
            assertEquals(expected, result!!.recipientName)
        }
    }

    // -----------------------------------------------------------------
    // An amount alone is not a payment (found on a real device, 2026-08-05)
    // -----------------------------------------------------------------

    /**
     * Each of these carries the exact expected amount and arrives from a
     * plausible sender inside an open payment window. Before the
     * describesTransaction gate every one produced a green "Payment
     * Successful" screen AND consumed the window, so the bank's genuine
     * confirmation seconds later was discarded as having no active payment.
     */
    @Test
    fun `a message carrying an amount but no transaction verb never confirms`() {
        val notPayments = listOf(
            "AD-BIGBAZ" to "Mega sale! Get products worth Rs.300 free. Shop now at bigbazaar.example",
            "VM-HDFCBK" to "456 is your OTP for HDFC Bank. Rs.456 txn. Do not share with anyone.",
            "VK-HDFCBK" to "Your HDFC Bank A/c **1234 Avl Bal is Rs.600.00 as on 05-Aug-26."
        )
        notPayments.forEach { (sender, body) ->
            val amount = Regex("Rs\\.?([0-9]+)").find(body)!!.groupValues[1]
            assertNull(
                "must not confirm a payment: $body",
                SmsTransactionParser.parse(sender, body, amount)
            )
        }
    }

    /** The balance phrasing that slipped past the balance-avoidance regex. */
    @Test
    fun `Avl Bal is Rs X is not a payment even at the exact expected amount`() {
        assertNull(
            SmsTransactionParser.parse(
                "VK-HDFCBK",
                "Your HDFC Bank A/c **1234 Avl Bal is Rs.600.00 as on 05-Aug-26.",
                "600"
            )
        )
    }

    /** The gate must not cost us real debits phrased "transferred FROM". */
    @Test
    fun `a genuine debit still confirms through the gate`() {
        val parsed = SmsTransactionParser.parse(
            "PN-PNBSMS",
            "Rs 3,499.00 transferred from PNB A/c **6789 to VPA merchant@okaxis. Ref 223344556677",
            "3499"
        )
        assertNotNull(parsed)
        assertEquals(TransactionStatus.SUCCESS, parsed!!.status)
    }

    /**
     * The dual-verb template names the payee on the credited side. Direction
     * detection already read these as debits; nothing could reach the payee,
     * so the row recorded "Unknown" on a real device.
     */
    @Test
    fun `dual-verb template resolves the payee, not Unknown`() {
        val parsed = SmsTransactionParser.parse(
            "AD-ICICIB",
            "ICICI Bank Acct XX556 debited for Rs 500.00 on 05-Aug-26; KIRANA STORE credited. UPI:512233440091",
            "500"
        )
        assertNotNull(parsed)
        assertEquals("DEBIT", parsed!!.transactionType)
        assertEquals("Kirana Store", parsed.recipientName)
    }
}
