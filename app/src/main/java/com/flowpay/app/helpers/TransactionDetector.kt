package com.flowpay.app.helpers

import android.content.Context
import android.content.SharedPreferences
import android.os.Parcelable
import android.util.Log
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
 * What sits AROUND the matching is newer plumbing that does not change
 * which messages match:
 *  - an explicit operation window (startOperation / stopOperation)
 *  - @Synchronized processSMS + tryClaimSms, so the broadcast receiver and
 *    the notification listener can never double-process one SMS
 *  - the stored excerpt is built constructively from extracted fields
 *    (raw SMS bodies are not persisted)
 */
class TransactionDetector private constructor(context: Context) {

    companion object {
        private const val TAG = "TransactionDetector"
        private const val PREF_NAME = "payment_operation"
        private const val KEY_ACTIVE = "is_active"
        private const val KEY_START_TIME = "start_time"
        private const val KEY_OPERATION_TYPE = "operation_type"
        private const val KEY_EXPECTED_AMOUNT = "expected_amount"
        private const val KEY_PHONE_NUMBER = "phone_number"
        private const val TIMEOUT_MILLIS = 5 * 60 * 1000L // 5 minutes
        private const val SMS_CLAIM_WINDOW_MILLIS = 60 * 1000L // dedupe window across pipelines

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

        @Volatile
        private var instance: TransactionDetector? = null

        fun getInstance(context: Context): TransactionDetector {
            return instance ?: synchronized(this) {
                instance ?: TransactionDetector(context.applicationContext).also {
                    instance = it
                }
            }
        }

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

        /**
         * True when the SMS body reports a failed/declined transaction.
         * Kept in the companion (pure, Context-free) so JVM tests exercise
         * the exact production logic.
         */
        internal fun detectsFailure(body: String): Boolean {
            val bodyLower = body.lowercase(Locale.getDefault())
            return FAILURE_INDICATORS.any { bodyLower.contains(it) }
        }

        /** ±1.0 tolerance absorbs decimal-formatting differences ("100" vs "100.00"). */
        internal fun isAmountMatching(extracted: String, expected: String): Boolean {
            val extractedNum = extracted.replace(",", "").toDoubleOrNull() ?: return false
            val expectedNum = expected.replace(",", "").toDoubleOrNull() ?: return false
            return kotlin.math.abs(extractedNum - expectedNum) < 1.0
        }

        internal fun detectBank(sender: String, body: String): String? {
            val senderUpper = sender.uppercase(Locale.getDefault())
            val bodyUpper = body.uppercase(Locale.getDefault())

            // Check sender first
            for ((keyword, bankName) in BANK_KEYWORDS) {
                if (senderUpper.contains(keyword.uppercase())) {
                    return bankName
                }
            }

            // Check body for bank names
            for ((keyword, bankName) in BANK_KEYWORDS) {
                if (bodyUpper.contains(keyword.uppercase())) {
                    return bankName
                }
            }

            // Generic DLT-shaped sender fallback. Deliberately layered rather
            // than strict: an all-caps 6-letter promo sender ("AMAZON") can
            // still slip through here, but downstream defenses keep that
            // non-catastrophic — a failure keyword records FAILED and an
            // amount mismatch records NEEDS_REVIEW, never a silent SUCCESS.
            // The old blanket `sender.length == 6` check was removed: it also
            // admitted mixed/lowercase senders ("Amazon", "MyShop"), which
            // are never DLT bank headers.
            if (sender.matches(Regex("^[A-Z]{2}-[A-Z0-9]{6}(-[A-Z])?$")) ||
                sender.matches(Regex("^[A-Z]{6}$")) ||
                sender.matches(Regex("^[0-9]{6}$"))) {

                // It's likely a bank shortcode, but we don't know which one
                return "Bank"
            }

            return null
        }

        /**
         * Cross-pipeline dedupe key. The notification listener sees a
         * truncated EXTRA_TEXT while the broadcast receiver sees the full
         * PDU body, so hashing the full body would give the two pipelines
         * different keys for the same SMS. Normalising whitespace and
         * capping at 120 chars (below any plausible notification truncation
         * point) makes both keys converge.
         */
        internal fun claimKey(sender: String, body: String): String {
            val normalisedBody = body.trim().replace(Regex("\\s+"), " ").take(120)
            return "${sender.trim().uppercase(Locale.ROOT)}|$normalisedBody"
        }

        // Amount patterns - multiple formats
        private val AMOUNT_PATTERNS = listOf(
            "(?:Rs\\.?|INR|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",
            "amount\\s*(?:of)?\\s*(?:Rs\\.?|INR|₹)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",
            "([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:Rs\\.?|INR|₹)"
        )
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // Recently claimed SMS fingerprints (sender|body hash -> claim time).
    // Both ingestion pipelines (broadcast receiver + notification listener)
    // call tryClaimSms() before processing, so the same message is parsed
    // and persisted exactly once even when both fire near-simultaneously.
    private val recentSmsClaims = object : LinkedHashMap<String, Long>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > 64
    }

    @Synchronized
    fun tryClaimSms(sender: String, body: String): Boolean {
        val key = claimKey(sender, body).hashCode().toString()
        val now = System.currentTimeMillis()
        val lastClaim = recentSmsClaims[key]
        if (lastClaim != null && now - lastClaim < SMS_CLAIM_WINDOW_MILLIS) {
            return false
        }
        recentSmsClaims[key] = now
        return true
    }

    // Start monitoring for a payment operation
    fun startOperation(operationType: String, expectedAmount: String? = null, phoneNumber: String? = null) {
        Log.d(TAG, "Starting operation: $operationType")

        prefs.edit().apply {
            putBoolean(KEY_ACTIVE, true)
            putLong(KEY_START_TIME, System.currentTimeMillis())
            putString(KEY_OPERATION_TYPE, operationType)
            expectedAmount?.let { putString(KEY_EXPECTED_AMOUNT, it) }
            phoneNumber?.let { putString(KEY_PHONE_NUMBER, it) }
            apply()
        }
    }

    // Stop monitoring
    fun stopOperation() {
        Log.d(TAG, "Stopping operation")

        prefs.edit().apply {
            clear()
            apply()
        }
    }

    // Check if we should process SMS
    fun shouldProcessSMS(): Boolean {
        val isActive = prefs.getBoolean(KEY_ACTIVE, false)
        if (!isActive) {
            return false
        }

        // Check timeout
        val startTime = prefs.getLong(KEY_START_TIME, 0)
        val elapsed = System.currentTimeMillis() - startTime

        if (elapsed > TIMEOUT_MILLIS) {
            Log.d(TAG, "Operation timed out after ${elapsed / 1000} seconds")
            stopOperation()
            return false
        }

        // Additional check: Ensure we have an operation type
        val operationType = prefs.getString(KEY_OPERATION_TYPE, null)
        if (operationType.isNullOrEmpty()) {
            stopOperation()
            return false
        }

        return true
    }

    fun getOperationType(): String? = prefs.getString(KEY_OPERATION_TYPE, null)
    fun getPhoneNumber(): String? = prefs.getString(KEY_PHONE_NUMBER, null)

    // Main processing function — the original detection pipeline.
    // Synchronized so that when both ingestion pipelines race past
    // shouldProcessSMS(), only the first one inside consumes the active
    // operation — the second sees it stopped and bails.
    @Synchronized
    fun processSMS(sender: String, body: String): SimpleTransaction? {
        Log.d(TAG, "Processing SMS")

        // Re-check under lock: the other pipeline may have just consumed
        // the active operation.
        if (!shouldProcessSMS()) {
            Log.d(TAG, "Operation no longer active, skipping")
            return null
        }

        // Step 1: Check if it's from a bank
        val bankName = detectBank(sender, body)
        if (bankName == null) {
            Log.d(TAG, "Not a bank SMS")
            return null
        }
        Log.d(TAG, "Detected bank: $bankName")

        // Step 2: Check if it's a transaction message
        if (!isTransactionMessage(body)) {
            Log.d(TAG, "Not a transaction message")
            return null
        }
        Log.d(TAG, "Transaction message confirmed")

        // Step 3: Extract amount
        val amount = extractAmount(body)
        if (amount == null) {
            Log.d(TAG, "Could not extract amount")
            return null
        }

        // Step 4: Compare against the expected amount (if any). The message
        // is still accepted either way — permissive matching preserved — but
        // a mismatch is no longer silently auto-confirmed: it is recorded as
        // NEEDS_REVIEW so an unrelated debit SMS in the operation window
        // (e.g. an auto-debit) can never masquerade as this payment.
        val expectedAmount = prefs.getString(KEY_EXPECTED_AMOUNT, null)
        val amountMatches =
            expectedAmount.isNullOrEmpty() || isAmountMatching(amount, expectedAmount)
        if (!amountMatches) {
            Log.d(TAG, "Amount mismatch with expected value - marking NEEDS_REVIEW")
        }

        // Step 5: Generate transaction ID
        val transactionId = extractTransactionId(body) ?: generateTransactionId()
        val upiId = extractUPIId(body)
        val transactionType = detectTransactionType(body)

        // Extract recipient/sender name and phone number
        val (recipientName, phoneNumber) = extractRecipientInfo(body, transactionType)

        // Derive the outcome from the SMS itself. A failure keyword wins
        // over everything: banks send "Payment of Rs 500 failed" messages
        // that would otherwise qualify via "payment of" + an amount.
        val status = when {
            detectsFailure(body) -> TransactionStatus.FAILED
            !amountMatches -> TransactionStatus.NEEDS_REVIEW
            else -> TransactionStatus.SUCCESS
        }

        // Step 6: Mark operation complete — unless this was an incoming
        // CREDIT. A stray credit must not consume the operation window the
        // real outgoing-debit confirmation may still need.
        if (transactionType != "CREDIT") {
            stopOperation()
        }

        return SimpleTransaction(
            transactionId = transactionId,
            amount = amount,
            status = status,
            bankName = bankName,
            smsExcerpt = buildExcerpt(amount, transactionType, bankName, status),
            upiId = upiId,
            transactionType = transactionType,
            recipientName = recipientName,
            phoneNumber = phoneNumber
        )
    }

    /**
     * Privacy-safe stored summary, built only from extracted fields so the
     * raw SMS body (account fragments, balances) never reaches the database.
     */
    private fun buildExcerpt(
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


    private fun isTransactionMessage(body: String): Boolean {
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

    private fun extractAmount(body: String): String? {
        for (pattern in AMOUNT_PATTERNS) {
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            val match = regex.find(body)

            if (match != null && match.groups.size > 1) {
                val amount = match.groups[1]?.value?.replace(",", "")
                if (!amount.isNullOrEmpty()) {
                    return amount
                }
            }
        }
        return null
    }


    private fun extractTransactionId(body: String): String? {
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
                    // Add timestamp to make it unique even if ref number is same
                    return "${baseId}_${System.currentTimeMillis()}"
                }
            }
        }

        return null
    }

    private fun extractUPIId(body: String): String? {
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

    private fun detectTransactionType(body: String): String {
        val bodyLower = body.lowercase(Locale.getDefault())
        return when {
            bodyLower.contains("credited") ||
            bodyLower.contains("received") ||
            bodyLower.contains("added") -> "CREDIT"
            else -> "DEBIT"
        }
    }

    private fun generateTransactionId(): String {
        // Generate unique ID using timestamp + random component
        val timestamp = System.currentTimeMillis()
        val random = (1000..9999).random()
        return "TXN${timestamp}${random}"
    }

    // Extract recipient information
    private fun extractRecipientInfo(body: String, transactionType: String): Pair<String?, String?> {
        var recipientName: String? = null
        var phoneNumber: String? = null

        val patterns = if (transactionType == "CREDIT") {
            SENDER_PATTERNS
        } else {
            RECIPIENT_PATTERNS
        }

        for ((index, pattern) in patterns.withIndex()) {
            try {
                val regex = Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                val match = regex.find(body)

                if (match != null && match.groups.size > 1) {
                    val extracted = match.groups[1]?.value?.trim()

                    if (!extracted.isNullOrEmpty()) {
                        // Check if it's a phone number
                        if (extracted.matches(Regex("\\d{10}"))) {
                            phoneNumber = extracted
                        } else {
                            // Clean up the name
                            val cleanedName = cleanupName(extracted)
                            if (!cleanedName.isNullOrEmpty()) {
                                recipientName = cleanedName
                                break // Stop if we found a good name
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error with recipient pattern $index", e)
            }
        }

        // If no name found but we have UPI ID, extract name from it
        if (recipientName.isNullOrEmpty()) {
            val upiName = extractNameFromUPI(body)
            if (!upiName.isNullOrEmpty()) {
                recipientName = upiName
            }
        }

        return Pair(recipientName, phoneNumber)
    }

    // Clean up extracted name
    private fun cleanupName(name: String): String? {
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
        cleaned = cleaned.split(" ").joinToString(" ") { word ->
            word.lowercase().replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }
        }

        return cleaned
    }

    // Extract name from UPI ID
    private fun extractNameFromUPI(body: String): String? {
        val upiPattern = Regex("([a-zA-Z][a-zA-Z0-9._-]+)@[a-zA-Z0-9]+", RegexOption.IGNORE_CASE)
        val match = upiPattern.find(body)

        if (match != null && match.groups.size > 1) {
            val upiPrefix = match.groups[1]?.value

            if (!upiPrefix.isNullOrEmpty()) {
                // Convert UPI prefix to readable name
                // e.g., "johnsmith" -> "John Smith", "john.smith" -> "John Smith"
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
        }

        return null
    }
}
