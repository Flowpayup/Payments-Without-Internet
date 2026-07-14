package com.flowpay.app.payment

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import com.flowpay.app.data.TransactionStatus
import com.flowpay.app.states.PaymentState
import com.flowpay.app.states.TimeoutType
import com.flowpay.app.ui.activities.PaymentResultActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Surfaces the UNVERIFIED outcome to the user.
 *
 * When a payment call completes but no confirming bank SMS arrives before the
 * deadline, [PaymentSessionManager] emits [PaymentState.Timeout]. That happens
 * ~10 minutes after dialing — long after [com.flowpay.app.services.CallOverlayService]
 * has shown its "Request Sent" dialog and stopped itself, so the service's own
 * state collector is already gone. Without this observer the row is written
 * UNVERIFIED but the user is never told.
 *
 * This observer lives on the process-scoped [AppContainer.appScope], so it
 * outlives the overlay service and reliably launches [PaymentResultActivity]
 * with the UNVERIFIED status — the same result surface the SMS pipeline uses
 * for confirmed outcomes. Every other terminal state already has a surface
 * (SMS pipeline for Success/NeedsReview/Failed, overlay dialog for Cancelled),
 * so this deliberately handles only [PaymentState.Timeout].
 *
 * The state→intent decision is the pure, unit-tested [toUnverifiedSurface];
 * only the Intent launch itself touches the Android framework.
 */
class UnverifiedOutcomeObserver(
    private val appContext: Context,
    private val paymentState: StateFlow<PaymentState>,
    private val scope: CoroutineScope,
    private val onUnverified: (UnverifiedSurface) -> Unit = { defaultLaunch(appContext, it) }
) {
    fun start() {
        scope.launch {
            paymentState.collect { state ->
                state.toUnverifiedSurface()?.let(onUnverified)
            }
        }
    }

    companion object {
        private const val TAG = "UnverifiedOutcome"

        private fun defaultLaunch(context: Context, surface: UnverifiedSurface) {
            val intent = Intent(context, PaymentResultActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("transaction_id", surface.transactionId)
                putExtra("amount", surface.amount)
                putExtra("status", TransactionStatus.UNVERIFIED)
                putExtra("phone_number", surface.phoneNumber)
                putExtra("transaction_type", "DEBIT")
                putExtra("timestamp", System.currentTimeMillis())
            }
            // Background-activity-launch can be blocked without overlay
            // permission; Phase 2.8 adds a notification fallback. Log so the
            // row-vs-UI mismatch is at least diagnosable meanwhile.
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "Could not launch unverified result screen: ${e.message}")
            } catch (e: SecurityException) {
                Log.w(TAG, "Blocked launching unverified result screen: ${e.message}")
            }
        }
    }
}

/** Immutable description of the unverified-result surface to show. */
data class UnverifiedSurface(
    val transactionId: String,
    val amount: String,
    val phoneNumber: String
)

/**
 * Pure mapping: a bank-verification timeout is the only state that needs the
 * unverified surface. Everything else returns null. Unit-tested without any
 * Android dependency.
 */
fun PaymentState.toUnverifiedSurface(): UnverifiedSurface? =
    if (this is PaymentState.Timeout && timeoutType == TimeoutType.BANK_VERIFICATION) {
        UnverifiedSurface(
            transactionId = transactionId,
            amount = amount,
            phoneNumber = phoneNumber
        )
    } else {
        null
    }
