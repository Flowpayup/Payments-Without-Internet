package com.flowpay.app

import android.app.Application
import android.content.Context
import android.util.Log
import com.flowpay.app.data.SettingsRepository
import com.flowpay.app.payment.PaymentSessionManager
import com.flowpay.app.repository.TransactionRepository
import com.flowpay.app.telephony.CallStateCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class FlowpayApplication : Application() {

    /** Process-wide scope for work that must outlive any single screen. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(applicationContext) }

    /** Single telephony listener for the whole app. */
    val callStateCoordinator: CallStateCoordinator by lazy {
        CallStateCoordinator(applicationContext)
    }

    /** The only writer of payment lifecycle state. */
    val paymentSessionManager: PaymentSessionManager by lazy {
        PaymentSessionManager(
            store = TransactionRepository.getInstance(applicationContext),
            coordinator = callStateCoordinator,
            scope = appScope
        )
    }

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
