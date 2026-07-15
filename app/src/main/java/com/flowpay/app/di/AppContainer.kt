package com.flowpay.app.di

import android.content.Context
import com.flowpay.app.data.SettingsRepository
import com.flowpay.app.data.Transaction
import com.flowpay.app.payment.PaymentSessionManager
import com.flowpay.app.payment.PaymentTransactionStore
import com.flowpay.app.payment.UnverifiedOutcomeObserver
import com.flowpay.app.payment.sms.SimpleTransaction
import com.flowpay.app.repository.TransactionRepository
import com.flowpay.app.telephony.CallStateCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

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
            store = LazyTransactionStore(appContext),
            coordinator = callStateCoordinator,
            scope = appScope
        )
    }

    /**
     * Surfaces the UNVERIFIED outcome to the user when a session times out
     * without a confirming SMS. Process-scoped so it outlives the overlay
     * service that showed the earlier "Request Sent" dialog.
     */
    val unverifiedOutcomeObserver: UnverifiedOutcomeObserver by lazy {
        UnverifiedOutcomeObserver(
            appContext = appContext,
            paymentState = paymentSessionManager.paymentState,
            scope = appScope
        )
    }
}

/**
 * Defers the first (expensive) database materialisation to the first store
 * CALL, on Dispatchers.IO — never at construction time. Without this,
 * touching `paymentSessionManager` in Application.onCreate opened SQLCipher,
 * unwrapped the Keystore passphrase, and (on upgrade) ran the full
 * plaintext->encrypted export ON THE MAIN THREAD during cold start.
 * All [PaymentTransactionStore] methods are suspend, so the hop is free.
 */
private class LazyTransactionStore(private val appContext: Context) : PaymentTransactionStore {

    private suspend fun repo(): TransactionRepository =
        withContext(Dispatchers.IO) { TransactionRepository.getInstance(appContext) }

    override suspend fun insertPending(transaction: Transaction) =
        repo().insertPending(transaction)

    override suspend fun transitionStatus(
        transactionId: String,
        expectedStatus: String,
        newStatus: String
    ): Int = repo().transitionStatus(transactionId, expectedStatus, newStatus)

    override suspend fun confirmTransaction(
        transactionId: String,
        status: String,
        parsed: SimpleTransaction,
        verifiedAt: Long
    ): Int = repo().confirmTransaction(
        transactionId = transactionId,
        status = status,
        parsed = parsed,
        verifiedAt = verifiedAt
    )

    override suspend fun expireStalePending(now: Long): Int = repo().expireStalePending(now)
}
