package com.flowpay.app.di

import android.content.Context
import com.flowpay.app.data.SettingsRepository
import com.flowpay.app.payment.PaymentSessionManager
import com.flowpay.app.repository.TransactionRepository
import com.flowpay.app.telephony.CallStateCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The app's single manual composition root. Constructs and owns the
 * process-scoped object graph — the payment lifecycle manager, the one
 * telephony listener, the settings repository, and the scope they share.
 *
 * Deliberately hand-wired rather than using a DI framework: the graph is
 * small, and for a payments app, construction that a reader can follow by
 * eye (no annotation-generated indirection) is a feature. Held by
 * [com.flowpay.app.FlowpayApplication], which exposes these members and is
 * reachable from receivers/services via `FlowpayApplication.from(context)`.
 *
 * The remaining `getInstance()` singletons (TransactionRepository,
 * AppDatabase, TransactionDetector) are thread-safe, application-context
 * keyed, and constructed lazily where first needed; the container references
 * them rather than duplicating their lifecycle.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** Process-wide scope for work that must outlive any single screen. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }

    /** Single telephony listener for the whole app. */
    val callStateCoordinator: CallStateCoordinator by lazy { CallStateCoordinator(appContext) }

    /** The only writer of payment lifecycle state. */
    val paymentSessionManager: PaymentSessionManager by lazy {
        PaymentSessionManager(
            store = TransactionRepository.getInstance(appContext),
            coordinator = callStateCoordinator,
            scope = appScope
        )
    }
}
