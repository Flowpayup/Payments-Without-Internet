package com.flowpay.app.data

/**
 * Canonical lifecycle states for a transaction row.
 *
 * PENDING      — payment initiated, dial in progress or awaiting bank SMS
 * SUCCESS      — bank SMS confirmed the debit
 * FAILED       — bank SMS reported a failure/decline
 * CANCELLED    — call ended before the IVR flow could complete, or dial failed
 * UNVERIFIED   — call completed but no bank SMS arrived within the deadline;
 *                the money may or may not have moved — the user must check
 *                with their bank
 * NEEDS_REVIEW — a bank SMS confirmed *something* during this payment, but
 *                its amount didn't match what was sent — the user must verify
 *                against their bank statement before trusting the outcome
 */
object TransactionStatus {
    const val PENDING = "PENDING"
    const val SUCCESS = "SUCCESS"
    const val FAILED = "FAILED"
    const val CANCELLED = "CANCELLED"
    const val UNVERIFIED = "UNVERIFIED"
    const val NEEDS_REVIEW = "NEEDS_REVIEW"
}

/** Where a transaction record originated. */
object TransactionSource {
    const val SMS = "SMS"
    const val NOTIFICATION = "NOTIFICATION"
    const val MANUAL = "MANUAL"
    const val QR = "QR"
}
