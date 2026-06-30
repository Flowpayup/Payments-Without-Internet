package com.flowpay.app.helpers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.flowpay.app.FlowPayApplication
import com.flowpay.app.constants.PermissionConstants
import com.flowpay.app.managers.CallManager
import com.flowpay.app.managers.PermissionManager
import com.flowpay.app.services.CallOverlayService
import com.flowpay.app.states.PaymentState
import com.flowpay.app.SetupActivity
import com.flowpay.app.TestConfigurationActivity
import com.flowpay.app.constants.AppConstants

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

    // Permission request tracking
    private var isRequestingPermissions = false

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
    }

    /**
     * Check SMS permissions specifically
     */
    fun checkSMSPermissions(): Boolean {
        return permissionManager?.checkSMSPermissions() ?: false
    }

    /**
     * Request SMS permissions specifically
     */
    fun requestSMSPermissions() {
        permissionManager?.requestSMSPermissions()
    }

    /**
     * Open QR scanner
     */
    private fun openQRScanner() {
        val intent = Intent(context, com.flowpay.app.features.qr_scanner.presentation.QRScannerActivity::class.java)
        // Remove FLAG_ACTIVITY_NEW_TASK to allow result handling
        if (context is Activity) {
            context.startActivityForResult(intent, com.flowpay.app.MainActivity.QR_SCAN_REQUEST_CODE)
        } else {
            // Fallback for non-Activity context
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
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
            uiCallback.showToast("Requesting required permissions...")
            isRequestingPermissions = true
            permissionManager?.requestPhonePermissions()
            return
        }

        Log.d(TAG, "Phone permissions granted, opening QR scanner")
        uiCallback.showToast("Opening QR scanner...")
        openQRScanner()
    }

    /**
     * Handle activity result
     */
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "handleActivityResult: requestCode=$requestCode, resultCode=$resultCode")

        when (requestCode) {
            PermissionConstants.OVERLAY_PERMISSION_REQ_CODE -> {
                Log.d(TAG, "Overlay permission result received")
                isRequestingPermissions = false
                if (Settings.canDrawOverlays(context)) {
                    Log.d(TAG, "Overlay permission granted")
                    uiCallback.showToast("Overlay permission granted. You can now proceed with the transfer.")
                } else {
                    Log.w(TAG, "Overlay permission not granted")
                    uiCallback.showToast("Overlay permission is required for payment protection. Please enable it in Settings.")
                }
            }
            PermissionConstants.PERMISSIONS_REQUEST_CODE -> {
                Log.d(TAG, "Basic permissions result received")
                isRequestingPermissions = false
                // Handle basic permission results if needed
                if (permissionManager?.checkAllPermissions() == true) {
                    Log.d(TAG, "Basic permissions granted, checking overlay permission")
                    startQRScanning()
                } else {
                    Log.w(TAG, "Basic permissions not granted")
                    uiCallback.showToast("Required permissions not granted. Please try again.")
                }
            }
        }
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
    fun initiateTransfer(phoneNumber: String, amount: String) {
        Log.d(TAG, "Initiating transfer")

        // Validate input
        if (phoneNumber.isBlank() || amount.isBlank()) {
            uiCallback.showToast("Please enter both phone number and amount")
            return
        }

        // Validate phone number
        if (callManager?.isValidPhoneNumber(phoneNumber) != true) {
            uiCallback.showToast("Please enter valid 10-digit number")
            return
        }

        // Validate amount against the 123Pay per-transaction cap
        val amountValue = amount.toDoubleOrNull()
        if (amountValue == null || amountValue < AppConstants.MIN_AMOUNT_VALUE) {
            uiCallback.showToast("Please enter valid amount")
            return
        }
        if (amountValue > AppConstants.UPI123PAY_MAX_AMOUNT) {
            uiCallback.showToast("UPI 123Pay allows up to ₹${AppConstants.UPI123PAY_MAX_AMOUNT.toLong()} per transaction")
            return
        }

        // Check phone permissions only (camera/contacts handled separately)
        if (permissionManager?.hasPhonePermissions() != true) {
            uiCallback.showToast("Phone permissions required")
            permissionManager?.requestPhonePermissions()
            return
        }

        if (permissionManager?.checkOverlayPermission() != true || !Settings.canDrawOverlays(context)) {
            Log.d(TAG, "Overlay permission not granted, showing explanation dialog")
            uiCallback.showOverlayPermissionExplanation()
            return
        }

        warnIfVoiceSimMismatch()

        val sessionManager = FlowPayApplication.from(context)?.paymentSessionManager
        if (sessionManager == null) {
            Log.e(TAG, "PaymentSessionManager unavailable")
            uiCallback.showToast("Payment could not be started. Please try again.")
            return
        }

        // PENDING row is written before anything is dialled
        val transactionId = sessionManager.begin(phoneNumber, amount)
        if (transactionId == null) {
            uiCallback.showToast("A payment is already in progress")
            return
        }

        // Gate SMS detection to this operation window
        TransactionDetector.getInstance(context).startOperation(
            operationType = "UPI_123",
            expectedAmount = amount,
            phoneNumber = phoneNumber
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
                    "Note: this call will use your default calling SIM ($voiceCarrier), " +
                        "which doesn't look like the ${selectedCarrier.replaceFirstChar { it.uppercase() }} SIM from setup. " +
                        "If the payment fails, switch your default calling SIM."
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot check SIM mismatch: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "SIM mismatch check failed: ${e.message}")
        }
    }

    /**
     * Handle QR permission results
     */
    fun handleQRPermissionResult(permissions: Map<String, Boolean>) {
        Log.d(TAG, "QR permission results received: $permissions")

        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Log.d(TAG, "All QR permissions granted, starting QR scanning")
            startQRScanning()
        } else {
            Log.w(TAG, "Some QR permissions were denied")
            uiCallback.showToast("Camera permission is required for QR scanning")
        }
    }

    /**
     * Handle permission results
     */
    fun handlePermissionResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray): Boolean {
        val success = permissionManager?.handlePermissionResult(requestCode, permissions, grantResults) ?: false

        if (!success && requestCode == PermissionConstants.PERMISSIONS_REQUEST_CODE) {
            // Only show the generic toast for the batch request. Specific request codes
            // (SMS, camera, contacts) have their own activity-level feedback.
            uiCallback.showToast("Some permissions were denied. App may not work properly.")
        }

        return success
    }

    /**
     * Handle overlay permission result
     */
    fun handleOverlayPermissionResult() {
        if (permissionManager?.canDrawOverlays() == true) {
            Log.d(TAG, "Overlay permission granted")

            // Check if there's a pending overlay request
            val sharedPrefs = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
            val hasPendingOverlay = sharedPrefs.getBoolean(AppConstants.KEY_PENDING_OVERLAY, false)

            if (hasPendingOverlay) {
                val phoneNumber = sharedPrefs.getString(AppConstants.KEY_PENDING_PHONE, "") ?: ""
                val amount = sharedPrefs.getString(AppConstants.KEY_PENDING_AMOUNT, "") ?: ""

                Log.d(TAG, "Processing pending overlay request")

                // Clear pending request
                sharedPrefs.edit()
                    .remove(AppConstants.KEY_PENDING_OVERLAY)
                    .remove(AppConstants.KEY_PENDING_PHONE)
                    .remove(AppConstants.KEY_PENDING_AMOUNT)
                    .apply()

                // Show overlay immediately
                CallOverlayService.showOverlay(context, phoneNumber, amount)
            }
        } else {
            Log.w(TAG, "Overlay permission denied")
            uiCallback.showToast("Overlay permission is required for call protection")

            // Check for special device permissions (Xiaomi/MIUI)
            checkSpecialPermissions()
        }
    }

    /**
     * Check for special permissions required by custom ROMs like MIUI
     */
    private fun checkSpecialPermissions() {
        try {
            // For Xiaomi/MIUI devices
            val intent = Intent("miui.intent.action.APP_PERM_EDITOR")
            intent.setClassName("com.miui.securitycenter",
                "com.miui.permcenter.permissions.PermissionsEditorActivity")
            intent.putExtra("extra_pkgname", context.packageName)
            context.startActivity(intent)
            Log.d(TAG, "Opened MIUI permission settings")
        } catch (e: Exception) {
            // Try alternative MIUI permission path
            try {
                val intent = Intent("miui.intent.action.APP_PERM_EDITOR")
                intent.setClassName("com.miui.securitycenter",
                    "com.miui.permcenter.permissions.AppPermissionsEditorActivity")
                intent.putExtra("extra_pkgname", context.packageName)
                context.startActivity(intent)
                Log.d(TAG, "Opened MIUI permission settings (alternative)")
            } catch (e2: Exception) {
                // Try Huawei EMUI
                try {
                    val intent = Intent("com.huawei.systemmanager.startupmamager.StartupManagerActivity")
                    intent.putExtra("packageName", context.packageName)
                    context.startActivity(intent)
                    Log.d(TAG, "Opened EMUI permission settings")
                } catch (e3: Exception) {
                    // Try Oppo ColorOS
                    try {
                        val intent = Intent("com.coloros.safecenter.permission.PermissionManagerActivity")
                        intent.putExtra("packageName", context.packageName)
                        context.startActivity(intent)
                        Log.d(TAG, "Opened ColorOS permission settings")
                    } catch (e4: Exception) {
                        // Try Vivo FuntouchOS
                        try {
                            val intent = Intent("com.vivo.permissionmanager.activity.BaikeActivity")
                            intent.putExtra("packageName", context.packageName)
                            context.startActivity(intent)
                            Log.d(TAG, "Opened FuntouchOS permission settings")
                        } catch (e5: Exception) {
                            Log.d(TAG, "Not a custom ROM device or permission settings not available")
                        }
                    }
                }
            }
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
