// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.helpers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.flowpay.app.FlowpayApplication
import com.flowpay.app.R
import com.flowpay.app.SetupActivity
import com.flowpay.app.TestConfigurationActivity
import com.flowpay.app.constants.AppConstants
import com.flowpay.app.managers.CallManager
import com.flowpay.app.managers.PermissionManager
import com.flowpay.app.payment.PaymentInputValidator
import com.flowpay.app.payment.Upi123CallStringBuilder
import com.flowpay.app.payment.messageFor
import com.flowpay.app.services.CallOverlayService
import com.flowpay.app.states.PaymentState

/**
 * Helper class containing all business logic for MainActivity
 * This separates the UI concerns from the business logic
 *
 * Call-state tracking lives in CallStateCoordinator and payment lifecycle
 * in PaymentSessionManager (both app-scoped) — this class no longer keeps
 * its own PhoneStateListener or duration timers.
 */
class MainActivityHelper(
    private val context: Context,
    private val uiCallback: UICallback
) {
    companion object {
        private const val TAG = "MainActivityHelper"
    }

    // Managers
    private var callManager: CallManager? = null
    private var permissionManager: PermissionManager? = null

    /**
     * Interface for UI callbacks
     */
    interface UICallback {
        fun showToast(message: String)
        fun updatePaymentState(paymentState: PaymentState)
        fun navigateToSetup()
        fun navigateToTestConfiguration()
        fun finishActivity()
        fun showOverlayPermissionExplanation()
        fun launchQRScanner(intent: Intent)

        /** Launch the phone-permission request (Activity owns the launcher). */
        fun requestPhonePermissions()
    }

    /**
     * Open QR scanner
     */
    private fun openQRScanner() {
        val intent = Intent(context, com.flowpay.app.features.qr_scanner.presentation.QRScannerActivity::class.java)
        uiCallback.launchQRScanner(intent)
    }

    /**
     * Start QR scanning - public method for UI access
     */
    fun startQRScanning() {
        Log.d(TAG, "=== STARTING QR SCANNING ===")

        SetupHelper.getScanToPayBlockedMessage(context)?.let { message ->
            Log.d(TAG, "Scan to pay blocked: $message")
            uiCallback.showToast(message)
            return
        }

        // Check phone permissions only (camera is handled by QRScannerActivity)
        if (permissionManager?.hasPhonePermissions() != true) {
            Log.d(TAG, "Phone permissions not granted, requesting...")
            uiCallback.showToast(context.getString(R.string.status_requesting_permissions))
            uiCallback.requestPhonePermissions()
            return
        }

        Log.d(TAG, "Phone permissions granted, opening QR scanner")
        uiCallback.showToast(context.getString(R.string.status_opening_scanner))
        openQRScanner()
    }

    /**
     * Hide overlay
     */
    fun hideOverlay() {
        try {
            CallOverlayService.hideOverlay(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding overlay: ${e.message}")
        }
    }

    /**
     * Initialize all managers and services
     */
    fun initialize() {
        try {
            callManager = CallManager(context)
            permissionManager = PermissionManager(context as Activity)
        } catch (e: Exception) {
            Log.e(TAG, "Error during initialization: ${e.message}")
        }
    }

    /**
     * Check if setup is completed
     */
    fun isSetupCompleted(): Boolean {
        val sharedPreferences = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean(AppConstants.KEY_SETUP_COMPLETED, false)
    }

    /**
     * Check if test configuration is completed
     */
    fun isTestCompleted(): Boolean {
        val sharedPreferences = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean(AppConstants.KEY_TEST_COMPLETED, false)
    }

    /**
     * Initiate transfer with validation and business logic.
     *
     * Records a PENDING transaction via PaymentSessionManager BEFORE dialing,
     * so the database knows about the attempt even if the process dies
     * mid-call. The session reaches SUCCESS only via a confirming bank SMS.
     */
    /**
     * Validates what the user typed, reporting the first problem found.
     * Returns false when the transfer must not proceed.
     *
     * The amount ceiling is the 123Pay IVR's, not a generic input limit: the
     * IVR rejects anything from ₹5,000 up, and it does so *mid-call*, after
     * the user has already dialled. Catching it here turns a confusing failed
     * call into a clear message before anything is placed.
     */
    @Suppress("ReturnCount") // one guard clause per rule reads clearer than one accumulated condition
    private fun isTransferInputValid(phoneNumber: String, amount: String): Boolean {
        if (phoneNumber.isBlank() || amount.isBlank()) {
            uiCallback.showToast(context.getString(R.string.error_enter_phone_and_amount))
            return false
        }
        if (!PaymentInputValidator.isValidPhoneNumber(phoneNumber)) {
            uiCallback.showToast(context.getString(R.string.error_invalid_phone_number))
            return false
        }
        val amountValue = amount.toDoubleOrNull()
        if (amountValue == null || amountValue < AppConstants.MIN_AMOUNT_VALUE) {
            uiCallback.showToast(context.getString(R.string.error_invalid_amount))
            return false
        }
        if (amountValue > AppConstants.UPI123PAY_MAX_AMOUNT) {
            // Same reason, same wording as the one Upi123CallStringBuilder
            // raises further down the path — see Reason.AMOUNT_ABOVE_CAP.
            uiCallback.showToast(
                Upi123CallStringBuilder.Reason.AMOUNT_ABOVE_CAP.messageFor(context)
            )
            return false
        }
        return true
    }

    fun initiateTransfer(phoneNumber: String, amount: String) {
        Log.d(TAG, "Initiating transfer")

        if (!isTransferInputValid(phoneNumber, amount)) return

        // Check phone permissions only (camera/contacts handled separately)
        if (permissionManager?.hasPhonePermissions() != true) {
            uiCallback.showToast(context.getString(R.string.error_phone_permission_required))
            uiCallback.requestPhonePermissions()
            return
        }

        if (permissionManager?.checkOverlayPermission() != true || !Settings.canDrawOverlays(context)) {
            Log.d(TAG, "Overlay permission not granted, showing explanation dialog")
            uiCallback.showOverlayPermissionExplanation()
            return
        }

        warnIfVoiceSimMismatch()

        val sessionManager = FlowpayApplication.from(context)?.paymentSessionManager
        if (sessionManager == null) {
            Log.e(TAG, "PaymentSessionManager unavailable")
            uiCallback.showToast(context.getString(R.string.error_payment_not_started))
            return
        }

        // PENDING row is written before anything is dialled. begin() always
        // succeeds — a new payment supersedes any session still in flight
        // rather than being refused.
        val transactionId = sessionManager.begin(phoneNumber, amount)

        // Gate SMS detection to this operation window. The session txnId is
        // persisted so a confirmation arriving after a process death can be
        // reattached to this payment's row instead of inserting a duplicate.
        TransactionDetector.getInstance(context).startOperation(
            operationType = "UPI_123",
            expectedAmount = amount,
            phoneNumber = phoneNumber,
            sessionTxnId = transactionId
        )

        val success = callManager?.initiateUPI123Call(phoneNumber, amount) ?: false
        if (success) {
            // Overlay service shows once the coordinator reports the call OFFHOOK
            CallOverlayService.showOverlay(context, phoneNumber, amount)
        } else {
            Log.e(TAG, "Failed to initiate UPI123 call")
            sessionManager.onDialFailed("Could not start the payment call")
            TransactionDetector.getInstance(context).stopOperation()
        }
    }

    /**
     * Dual-SIM pitfall: the IVR call silently goes out on the device's
     * DEFAULT VOICE SIM, which may not be the UPI-registered SIM the user
     * selected in setup — payments then fail mysteriously. Detect the
     * mismatch and warn before dialling. Never blocks the payment.
     */
    private fun warnIfVoiceSimMismatch() {
        try {
            val selectedCarrier = context
                .getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
                .getString("selected_primary_sim", "") ?: ""
            if (selectedCarrier.isEmpty()) return

            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                as? android.telephony.SubscriptionManager ?: return
            val voiceSubId = android.telephony.SubscriptionManager.getDefaultVoiceSubscriptionId()
            if (voiceSubId == android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID) return

            // Single-SIM devices can't mismatch
            if ((subscriptionManager.activeSubscriptionInfoCount) < 2) return

            val voiceCarrier = subscriptionManager.getActiveSubscriptionInfo(voiceSubId)
                ?.carrierName?.toString()?.lowercase() ?: return

            val matches = when (selectedCarrier) {
                "vodafone" -> voiceCarrier.contains("vodafone") || voiceCarrier.contains("vi")
                else -> voiceCarrier.contains(selectedCarrier)
            }
            if (!matches) {
                uiCallback.showToast(
                    context.getString(
                        R.string.warn_voice_sim_mismatch,
                        voiceCarrier,
                        selectedCarrier.replaceFirstChar { it.uppercase() }
                    )
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot check SIM mismatch: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "SIM mismatch check failed: ${e.message}")
        }
    }

    /**
     * Handle app lifecycle events. Call-state listening is owned by the
     * app-scoped CallStateCoordinator, so nothing telephony-related needs
     * registering or unregistering here any more.
     */
    fun onPause() = Unit

    fun onStop() = Unit

    fun onResume() = Unit

    fun onDestroy() {
        try {
            try {
                callManager?.cleanup()
            } catch (e: Exception) {
                Log.w(TAG, "Error cleaning up CallManager: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup: ${e.message}")
        }
    }

    /**
     * Navigate to setup screen
     */
    fun navigateToSetup() {
        val intent = Intent(context, SetupActivity::class.java)
        context.startActivity(intent)
    }

    /**
     * Navigate to test configuration screen
     */
    fun navigateToTestConfiguration() {
        val intent = Intent(context, TestConfigurationActivity::class.java)
        context.startActivity(intent)
    }

    /**
     * Cleanup resources and stop monitoring
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up resources")
    }
}
