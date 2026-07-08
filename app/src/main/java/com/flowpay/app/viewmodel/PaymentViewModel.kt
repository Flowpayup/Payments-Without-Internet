package com.flowpay.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.flowpay.app.FlowpayApplication
import com.flowpay.app.states.PaymentState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Exposes the active payment session's [PaymentState] to Compose.
 * The state itself is owned by the app-scoped PaymentSessionManager, so it
 * survives configuration changes and is shared with the in-call overlay.
 */
class PaymentViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionManager = (application as? FlowpayApplication)?.paymentSessionManager

    val paymentState: StateFlow<PaymentState> =
        sessionManager?.paymentState ?: MutableStateFlow(PaymentState.Idle)

    /** Dismiss a terminal result and return the session to Idle. */
    fun acknowledgeResult() {
        sessionManager?.acknowledgeResult()
    }
}
