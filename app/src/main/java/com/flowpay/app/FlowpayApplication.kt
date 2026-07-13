package com.flowpay.app

import android.app.Application
import android.content.Context
import android.util.Log
import com.flowpay.app.data.SettingsRepository
import com.flowpay.app.di.AppContainer
import com.flowpay.app.payment.PaymentSessionManager
import com.flowpay.app.telephony.CallStateCoordinator
import kotlinx.coroutines.CoroutineScope

class FlowpayApplication : Application() {

    /** The manual composition root; see [AppContainer]. */
    val container: AppContainer by lazy { AppContainer(applicationContext) }

    // Delegating accessors so existing call sites
    // (FlowpayApplication.from(context)?.paymentSessionManager, etc.) keep
    // working while construction lives in the container.
    val appScope: CoroutineScope get() = container.appScope
    val settingsRepository: SettingsRepository get() = container.settingsRepository
    val callStateCoordinator: CallStateCoordinator get() = container.callStateCoordinator
    val paymentSessionManager: PaymentSessionManager get() = container.paymentSessionManager

    companion object {
        private const val TAG = "FlowpayApplication"

        /** Convenience accessor for receivers/services that only hold a Context. */
        fun from(context: Context): FlowpayApplication? =
            context.applicationContext as? FlowpayApplication
    }

    override fun onCreate() {
        super.onCreate()
        // Finalise any PENDING rows whose deadline passed while the app was dead.
        paymentSessionManager.reconcileStalePending()
        Log.d(TAG, "FlowpayApplication initialized")
    }
}
