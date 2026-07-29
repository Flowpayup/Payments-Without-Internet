package com.flowpay.app.payment.sms

import android.os.Parcelable
import com.flowpay.app.data.TransactionStatus
import kotlinx.parcelize.Parcelize
import java.util.Locale

@Parcelize
data class SimpleTransaction(
    val transactionId: String,
    val amount: String,
    val status: String,
    val bankName: String,
    /** Privacy-safe summary built from extracted fields — never the raw SMS body. */
    val smsExcerpt: String,
    val timestamp: Long = System.currentTimeMillis(),
    val upiId: String? = null,
    val transactionType: String = "DEBIT",
    val recipientName: String? = null,  // who money was sent to
    val phoneNumber: String? = null      // phone number if available
) : Parcelable

/**
 * Bank-SMS transaction detection — the original, field-proven matching
 * logic. Deliberately PERMISSIVE: substring bank keywords over sender and
 * body, a generic shortcode fallback, and "any success indicator OR an
 * amount" qualification. This matched real bank templates in the field, so
 * it is kept verbatim rather than replaced with stricter rules that risk
 * missing genuine confirmations.
 *
 * Pure and Context-free by design: no SharedPreferences, no singleton, no
 * ambient clock — [parse] takes a clock and a random-suffix provider as
 * parameters so callers (and tests) can make transaction-ID generation
 * deterministic. [TransactionDetector][com.flowpay.app.helpers.TransactionDetector]
 * is the stateful orchestrator around this: it owns the SharedPreferences-backed
 * operation window and cross-pipeline SMS dedup, and delegates all actual
 * message matching here.
 */
object SmsTransactionParser {

    // Name extraction patterns - CASE-INSENSITIVE
    private val RECIPIENT_PATTERNS = listOf(
        // Pattern for "sent to NAME" or "paid to NAME"
        "(?:sent|paid|transferred)\\s+to\\s+([a-zA-Z][a-zA-Z\\s\\.]+?)(?:\\s+(?:via|@|on|for|UPI|Ref)|\\.|,|;|$)",

        // Pattern for "to NAME via/@ UPI"
        "to\\s+([a-zA-Z][a-zA-Z\\s\\.]+?)\\s+(?:via|@)",

        // Pattern for "to merchant NAME"
        "to\\s+(?:merchant|M/s\\.?|Mr\\.?|Mrs\\.?|Ms\\.?)\\s*([a-zA-Z][a-zA-Z\\s\\.]+?)(?:\\s+(?:via|@|on|for)|\\.|,|;|$)",

        // Pattern for "Payment to NAME of Rs"
        "Payment\\s+to\\s+([a-zA-Z][a-zA-Z\\s\\.]+?)\\s+(?:of|for)\\s+(?:Rs|INR|₹)",

        // Pattern for "NAME - amount debited"
        "([a-zA-Z][a-zA-Z\\s\\.]+?)\\s*[-–]\\s*(?:Rs|INR|₹)",

        // Additional patterns for common formats
        "(?:Rs\\.?|INR|₹)\\s*[0-9,]+(?:\\.[0-9]{2})?\\s+(?:sent|paid|transferred)\\s+to\\s+([a-zA-Z][a-zA-Z\\s\\.]+?)(?:\\s|\\.|,|;|$)",

        // Pattern for simple "to NAME" without via/UPI
        "\\bto\\s+([a-zA-Z][a-zA-Z\\s\\.]{2,30})(?:\\s+(?:on|dated|ref)|\\.|,|;|$)",

        // Pattern for VPA format (name from UPI ID)
        "to\\s+([a-zA-Z][a-zA-Z0-9\\s]+?)@",

        // Pattern for phone numbers - LAST PRIORITY
        "to\\s+(\\d{10})(?:\\s|\\.|,|;|$)"
    )

    private val SENDER_PATTERNS = listOf(
        // Pattern for "received from NAME"
        "(?:received|credited)\\s+from\\s+([a-zA-Z][a-zA-Z\\s\\.]+?)(?:\\s+(?:via|@|on|for)|\\.|,|;|$)",

        // Pattern for "from NAME via/@ UPI"
        "from\\s+([a-zA-Z][a-zA-Z\\s\\.]+?)\\s+(?:via|@)",

        // Additional pattern for credit messages
        "(?:Rs\\.?|INR|₹)\\s*[0-9,]+(?:\\.[0-9]{2})?\\s+(?:received|credited)\\s+from\\s+([a-zA-Z][a-zA-Z\\s\\.]+?)(?:\\s|\\.|,|;|$)",

        // Pattern for simple "from NAME"
        "\\bfrom\\s+([a-zA-Z][a-zA-Z\\s\\.]{2,30})(?:\\s+(?:on|dated|ref)|\\.|,|;|$)",

        // Pattern for sender VPA
        "from\\s+([a-zA-Z][a-zA-Z0-9\\s]+?)@"
    )

    // Bank identifiers - comprehensive list
    private val BANK_KEYWORDS = mapOf(
        "HDFC" to "HDFC Bank",
        "ICICI" to "ICICI Bank",
        "SBI" to "State Bank of India",
        "AXIS" to "Axis Bank",
        "KOTAK" to "Kotak Bank",
        "PNB" to "Punjab National Bank",
        "BOB" to "Bank of Baroda",
        "IDFC" to "IDFC First Bank",
        "YES" to "Yes Bank",
        "PAYTM" to "Paytm Payments Bank",
        "UNION" to "Union Bank",
        "CANARA" to "Canara Bank",
        "IndusInd" to "IndusInd Bank",
        "Federal" to "Federal Bank"
    )

    // Transaction success indicators
    private val SUCCESS_INDICATORS = listOf(
        "successful",
        "successfully",
        "completed",
        "credited",
        "debited",
        "transferred",
        "sent to",
        "received from",
        "payment of",
        "paid to",
        "txn successful"
    )

    // Failure indicators. Checked with absolute precedence: a bank SMS
    // that matches the pipeline AND contains one of these is recorded as
    // FAILED, never SUCCESS. Multi-word entries are matched as substrings
    // of the lowercased body, same as SUCCESS_INDICATORS.
    private val FAILURE_INDICATORS = listOf(
        "failed",
        "failure",
        "declined",
        "rejected",
        "unsuccessful",
        "not successful",
        "could not be processed",
        "cannot be processed",
        "not processed",
        "not completed",
        "insufficient",
        "reversed",
        "not debited",
        "txn expired",
        "timed out"
    )

    // Outgoing-payment verbs, checked before the credit words in
    // [detectTransactionType]. Many banks describe one payment from both
    // sides in a single sentence ("A/c XX556 debited for Rs 500; KIRANA
    // STORE credited"), so a body carrying both verbs is ours only if the
    // debit reading wins.
    private val DEBIT_INDICATORS = listOf(
        "debited",
        "sent to",
        "paid to",
        "withdrawn",
        "spent",
        "transferred to"
    )

    // Amount patterns - multiple formats
    private val AMOUNT_PATTERNS = listOf(
        "(?:Rs\\.?|INR|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",
        "amount\\s*(?:of)?\\s*(?:Rs\\.?|INR|₹)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",
        "([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:Rs\\.?|INR|₹)"
    )

    /**
     * Parses a candidate bank SMS into a [SimpleTransaction], or `null` if
     * it isn't a bank transaction message at all — no bank detected, no
     * transaction-shaped body, or no amount found. Pure: no Context, no
     * SharedPreferences, no ambient state.
     *
     * [clock] and [randomSuffix] are injected so ID generation is
     * deterministic under test; callers in production use the defaults.
     */
    fun parse(
        sender: String,
        body: String,
        expectedAmount: String?,
        clock: () -> Long = System::currentTimeMillis,
        randomSuffix: () -> Int = { (1000..9999).random() }
    ): SimpleTransaction? {
        val bankName = detectBank(sender, body) ?: return null
        if (!isTransactionMessage(body)) return null
        val amount = extractAmount(body) ?: return null

        val transactionId = extractTransactionId(body, clock) ?: generateTransactionId(clock, randomSuffix)
        val upiId = extractUPIId(body)
        val transactionType = detectTransactionType(body)
        val (recipientName, phoneNumber) = extractRecipientInfo(body, transactionType)

        // A confirmation for our outgoing payment is a DEBIT whose amount
        // matches this payment. A mismatched debit isn't our confirmation —
        // return null so the operation window stays open for the real one,
        // instead of consuming the window or misattributing an unrelated bank
        // alert (an auto-debit, a card decline) to this payment. Credits are
        // never a debit's confirmation and are left to the downstream path.
        if (transactionType != "CREDIT" &&
            !expectedAmount.isNullOrEmpty() &&
            !isAmountMatching(amount, expectedAmount)
        ) {
            return null
        }

        // Derive the outcome from the SMS itself: a failure keyword records
        // FAILED (banks send "Payment of Rs 500 failed"), otherwise SUCCESS.
        val status = if (detectsFailure(body)) TransactionStatus.FAILED else TransactionStatus.SUCCESS

        return SimpleTransaction(
            transactionId = transactionId,
            amount = amount,
            status = status,
            bankName = bankName,
            smsExcerpt = buildExcerpt(amount, transactionType, bankName, status),
            timestamp = clock(),
            upiId = upiId,
            transactionType = transactionType,
            recipientName = recipientName,
            phoneNumber = phoneNumber
        )
    }

    /** True when the SMS body reports a failed/declined transaction. */
    internal fun detectsFailure(body: String): Boolean {
        val bodyLower = body.lowercase(Locale.getDefault())
        return FAILURE_INDICATORS.any { bodyLower.contains(it) }
    }

    private const val PAISE_PER_RUPEE = 100

    /**
     * Paise-exact comparison. "100" vs "100.00" still match (both are 10000
     * paise), but 500 vs 500.75 is a mismatch — the old ±1.0 tolerance let a
     * different transaction within ₹0.99 confirm this payment as SUCCESS.
     */
    internal fun isAmountMatching(extracted: String, expected: String): Boolean {
        val extractedNum = extracted.replace(",", "").toDoubleOrNull() ?: return false
        val expectedNum = expected.replace(",", "").toDoubleOrNull() ?: return false
        return Math.round(extractedNum * PAISE_PER_RUPEE) == Math.round(expectedNum * PAISE_PER_RUPEE)
    }

    // Keywords that are also everyday English words. In the BODY these must
    // appear as the full bank phrase — a promo like "Say YES to win Rs 5000!"
    // must never be attributed to Yes Bank and enter the payment pipeline.
    // Sender headers (VM-YESBNK, BOBTXN) still match by substring below.
    private val AMBIGUOUS_BODY_PHRASES = mapOf(
        "YES" to Regex("\\bYES\\s+BANK\\b"),
        "BOB" to Regex("\\bBANK\\s+OF\\s+BARODA\\b|\\bBOB\\s+BANK\\b")
    )

    internal fun detectBank(sender: String, body: String): String? {
        val senderUpper = sender.uppercase(Locale.getDefault())
        val bodyUpper = body.uppercase(Locale.getDefault())

        // Check sender first. DLT headers pack the bank into the code
        // (VK-HDFCBK, VM-YESBNK), so substring matching is correct here.
        for ((keyword, bankName) in BANK_KEYWORDS) {
            if (senderUpper.contains(keyword.uppercase())) {
                return bankName
            }
        }

        // Check body for bank names. Word-boundary matching, and the
        // ambiguous keywords ("YES", "BOB") additionally require the full
        // bank phrase so ordinary English can't smuggle a promo SMS in.
        for ((keyword, bankName) in BANK_KEYWORDS) {
            val ambiguous = AMBIGUOUS_BODY_PHRASES[keyword.uppercase()]
            val matched = if (ambiguous != null) {
                ambiguous.containsMatchIn(bodyUpper)
            } else {
                Regex("\\b${Regex.escape(keyword.uppercase())}\\b").containsMatchIn(bodyUpper)
            }
            if (matched) {
                return bankName
            }
        }

        // Generic DLT-shaped sender fallback. Deliberately layered rather
        // than strict: an all-caps 6-letter promo sender ("AMAZON") can
        // still slip through here, but downstream defenses keep that
        // non-catastrophic — a debit whose amount doesn't match the expected
        // amount is dropped outright (it isn't this payment's confirmation;
        // the operation window stays open), and a failure keyword records
        // FAILED — never a silent SUCCESS.
        // The old blanket `sender.length == 6` check was removed: it also
        // admitted mixed/lowercase senders ("Amazon", "MyShop"), which
        // are never DLT bank headers.
        if (sender.matches(Regex("^[A-Z]{2}-[A-Z0-9]{6}(-[A-Z])?$")) ||
            sender.matches(Regex("^[A-Z]{6}$")) ||
            sender.matches(Regex("^[0-9]{6}$"))
        ) {
            // It's likely a bank shortcode, but we don't know which one
            return "Bank"
        }

        return null
    }

    /**
     * Cross-pipeline dedupe key, built from the BODY only. The two pipelines
     * see different sender strings for the same SMS — the broadcast receiver
     * gets the DLT header ("VK-HDFCBK") while the notification listener gets
     * the messaging app's display name ("HDFC Bank") — so a sender-qualified
     * key would never collide across pipelines and the dedup would silently
     * not deduplicate. The body is also normalised (whitespace collapsed,
     * capped at 120 chars, below any plausible notification truncation point)
     * so the listener's truncated EXTRA_TEXT and the receiver's full PDU
     * converge to the same key.
     */
    internal fun claimKey(body: String): String {
        return body.trim().replace(Regex("\\s+"), " ").take(120)
    }

    internal fun isTransactionMessage(body: String): Boolean {
        val bodyLower = body.lowercase(Locale.getDefault())

        // Check for transaction success indicators
        for (indicator in SUCCESS_INDICATORS) {
            if (bodyLower.contains(indicator)) {
                return true
            }
        }

        // Failure SMS qualify too (widening only): "Payment declined" with no
        // success verb must still enter the pipeline so it can be recorded as
        // FAILED instead of being silently dropped.
        if (detectsFailure(body)) {
            return true
        }

        // Also check for amount patterns as additional validation
        for (pattern in AMOUNT_PATTERNS) {
            if (Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(body)) {
                return true
            }
        }

        return false
    }

    // Text immediately before a number that marks it as an account BALANCE,
    // not the transaction amount ("Avl Bal Rs 34,210.00", "Balance: INR ...").
    private val BALANCE_CONTEXT =
        Regex("(?:avl|available|avlbl|a/c|account)?\\s*bal(?:ance)?\\s*[:.]?\\s*$", RegexOption.IGNORE_CASE)

    // How far back to look for a balance marker before an amount match.
    private const val BALANCE_LOOKBEHIND_CHARS = 24

    internal fun extractAmount(body: String): String? {
        // Collect every amount-shaped match across all patterns, in body
        // order, then prefer the first that is NOT a balance figure. Banks
        // order these freely ("Avl Bal Rs X ... debited Rs Y" exists in the
        // wild), and with no expected amount to cross-check (the QR flow) a
        // balance picked here would be stored as the payment amount.
        val candidates = AMOUNT_PATTERNS
            .flatMap { pattern ->
                Regex(pattern, RegexOption.IGNORE_CASE).findAll(body).mapNotNull { match ->
                    val amount = match.groups[1]?.value?.replace(",", "")
                    if (amount.isNullOrEmpty()) null else match.range.first to amount
                }
            }
            .sortedBy { it.first }
        if (candidates.isEmpty()) return null

        val nonBalance = candidates.filterNot { (start, _) ->
            BALANCE_CONTEXT.containsMatchIn(body.take(start).takeLast(BALANCE_LOOKBEHIND_CHARS))
        }
        return (nonBalance.ifEmpty { candidates }).first().second
    }

    internal fun extractTransactionId(body: String, clock: () -> Long): String? {
        val patterns = listOf(
            "(?:ref|txn|transaction|id)\\s*(?:no|number|id)?\\s*:?\\s*([A-Z0-9]+)",
            "([A-Z0-9]{10,})" // Generic pattern for long alphanumeric
        )

        for (pattern in patterns) {
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            val match = regex.find(body)

            if (match != null && match.groups.size > 1) {
                val baseId = match.groups[1]?.value
                if (!baseId.isNullOrEmpty()) {
                    // Add a timestamp to make it unique even if ref number repeats
                    return "${baseId}_${clock()}"
                }
            }
        }

        return null
    }

    internal fun extractUPIId(body: String): String? {
        val patterns = listOf(
            "(?:UPI:|from|to|UPI ID:?)\\s*([a-zA-Z0-9._-]+@[a-zA-Z0-9]+)",
            "(?:VPA:?)\\s*([a-zA-Z0-9._-]+@[a-zA-Z0-9]+)"
        )

        for (pattern in patterns) {
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            val match = regex.find(body)
            if (match != null && match.groups.size > 1) {
                return match.groups[1]?.value
            }
        }
        return null
    }

    /**
     * DEBIT (money left the user's account) or CREDIT (money arrived).
     *
     * A debit verb wins over a credit word: ICICI/Union-style templates name
     * both sides of the same payment ("Acct XX556 debited for Rs 500.00 …;
     * KIRANA STORE credited"), and reading those as CREDIT would classify
     * our own outgoing confirmation as somebody else's incoming money — the
     * pipeline then drops it as unrelated and the payment never appears.
     */
    internal fun detectTransactionType(body: String): String {
        val bodyLower = body.lowercase(Locale.getDefault())
        return when {
            DEBIT_INDICATORS.any { bodyLower.contains(it) } -> "DEBIT"
            bodyLower.contains("credited") ||
                bodyLower.contains("received") ||
                bodyLower.contains("added") -> "CREDIT"
            else -> "DEBIT"
        }
    }

    internal fun generateTransactionId(clock: () -> Long, randomSuffix: () -> Int): String =
        "TXN${clock()}${randomSuffix()}"

    /** Extract recipient (DEBIT) or sender (CREDIT) name and phone number. */
    internal fun extractRecipientInfo(body: String, transactionType: String): Pair<String?, String?> {
        var recipientName: String? = null
        var phoneNumber: String? = null

        val patterns = if (transactionType == "CREDIT") SENDER_PATTERNS else RECIPIENT_PATTERNS

        for (pattern in patterns) {
            val regex = Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            val match = regex.find(body) ?: continue
            val extracted = match.groups[1]?.value?.trim()
            if (extracted.isNullOrEmpty()) continue

            if (extracted.matches(Regex("\\d{10}"))) {
                phoneNumber = extracted
                continue
            }

            val cleanedName = cleanupName(extracted)
            if (!cleanedName.isNullOrEmpty()) {
                recipientName = cleanedName
                break // Stop if we found a good name
            }
        }

        // If no name found but we have a UPI ID, extract a name from it
        if (recipientName.isNullOrEmpty()) {
            val upiName = extractNameFromUPI(body)
            if (!upiName.isNullOrEmpty()) {
                recipientName = upiName
            }
        }

        return Pair(recipientName, phoneNumber)
    }

    @Suppress("ReturnCount") // guard clauses read clearer than one accumulated condition here
    internal fun cleanupName(name: String): String? {
        var cleaned = name
            .trim()
            .replace(Regex("\\s+"), " ") // Multiple spaces to single space
            .replace(Regex("[\\-–]$"), "") // Remove trailing dashes
            .replace(Regex("\\.$"), "") // Remove trailing periods
            .trim()

        // Remove common prefixes/suffixes that aren't part of the name
        val prefixesToRemove = listOf("M/s", "Mr", "Mrs", "Ms", "Dr", "merchant", "Merchant")
        for (prefix in prefixesToRemove) {
            if (cleaned.startsWith(prefix, ignoreCase = true)) {
                cleaned = cleaned.substring(prefix.length).trim()
                if (cleaned.startsWith(".")) {
                    cleaned = cleaned.substring(1).trim()
                }
            }
        }

        // If name is too short or too long, it's probably not valid
        if (cleaned.length < 2 || cleaned.length > 50) {
            return null
        }

        // If name contains only numbers or special characters, it's not valid
        if (!cleaned.contains(Regex("[a-zA-Z]"))) {
            return null
        }

        // Convert to proper case (handle both uppercase and lowercase input)
        return cleaned.split(" ").joinToString(" ") { word ->
            word.lowercase().replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }
        }
    }

    /** e.g. "johnsmith@okhdfcbank" -> "John Smith", "john.smith@ybl" -> "John Smith" */
    internal fun extractNameFromUPI(body: String): String? {
        val upiPattern = Regex("([a-zA-Z][a-zA-Z0-9._-]+)@[a-zA-Z0-9]+", RegexOption.IGNORE_CASE)
        val upiPrefix = upiPattern.find(body)?.groups?.get(1)?.value
        if (upiPrefix.isNullOrEmpty()) return null

        return upiPrefix
            .replace(".", " ")
            .replace("_", " ")
            .replace("-", " ")
            .split(" ")
            .filter { it.isNotEmpty() }
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { it.uppercase() }
            }
    }

    /**
     * Privacy-safe stored summary, built only from extracted fields so the
     * raw SMS body (account fragments, balances) never reaches the database.
     */
    internal fun buildExcerpt(
        amount: String,
        transactionType: String,
        bankName: String,
        status: String
    ): String {
        val verb = when {
            status == TransactionStatus.FAILED -> "payment failed"
            transactionType == "CREDIT" -> "credited"
            else -> "debited"
        }
        return buildString {
            append("₹").append(amount).append(" ").append(verb)
            if (bankName.isNotBlank()) append(" — ").append(bankName)
        }
    }
}
