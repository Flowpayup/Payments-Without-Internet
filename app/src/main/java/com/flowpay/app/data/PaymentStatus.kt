package com.flowpay.app.data

/**
 * Lifecycle status attached to a payment record, mirroring the canonical
 * [TransactionStatus] strings. UNVERIFIED and NEEDS_REVIEW are distinct from
 * PENDING on purpose — collapsing them would hide the two outcomes the user
 * most needs to see.
 */
enum class PaymentStatus {
    PENDING, COMPLETED, FAILED, CANCELLED, UNVERIFIED, NEEDS_REVIEW
}
