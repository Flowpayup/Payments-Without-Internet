// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.helpers

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.flowpay.app.R
import com.flowpay.app.constants.AppConstants
import com.flowpay.app.data.TestResults
import com.flowpay.app.data.TestResultsManager
import com.flowpay.app.managers.CallManager
import com.flowpay.app.managers.CallType
import com.flowpay.app.managers.PermissionManager

/**
 * Helper class containing all business logic for TestConfigurationActivity
 * This separates the UI concerns from the business logic
 */
class TestConfigurationHelper(
    private val context: Context,
    private val uiCallback: UICallback
) {
    companion object {
        private const val TAG = "TestConfigurationHelper"
    }

    // Managers
    private lateinit var callManager: CallManager
    private lateinit var testResultsManager: TestResultsManager
    private lateinit var permissionManager: PermissionManager

    private val mainHandler = Handler(Looper.getMainLooper())
    private var ussdTimeoutRunnable: Runnable? = null
    private var upi123ConfigDelayRunnable: Runnable? = null

    // Test state variables
    private var currentTestingType: CallType? = null
    private var ussdTesting = false
    private var showUssdDialog = false
    private var ussdTestCompleted = false
    private var showCallCompleteButton = false
    private var upi123TestCompleted = false
    private var voiceTesting = false
    private var showVoiceDialog = false
    private var voiceTestCompleted = false
    private var upi123Testing = false
    private var showUpi123Dialog = false
    private var showUpi123ConfigurationOptions = false
    private var ussdProgressMessage = "Initializing..."
    private var showUssdConfigurationOptions = false

    /**
     * Interface for UI callbacks
     */
    interface UICallback {
        fun showToast(message: String)
        fun updateUssdTesting(isTesting: Boolean)
        fun updateUssdDialog(show: Boolean)
        fun updateUssdTestCompleted(completed: Boolean)
        fun updateUpi123Testing(isTesting: Boolean)
        fun updateUpi123TestCompleted(completed: Boolean)
        fun updateUpi123Dialog(show: Boolean)
        fun updateUpi123ConfigurationOptions(show: Boolean)
        fun updateVoiceTesting(isTesting: Boolean)
        fun updateVoiceDialog(show: Boolean)
        fun updateVoiceTestCompleted(completed: Boolean)
        fun updateCallCompleteButton(show: Boolean)
        fun updateUssdProgressMessage(message: String)
        fun updateUssdConfigurationOptions(show: Boolean)
        fun navigateToMain()

        /** Launch the phone-permission request (Activity owns the launcher). */
        fun requestPhonePermissions()
    }

    /**
     * Initialize all managers
     */
    fun initialize() {
        callManager = CallManager(context)
        testResultsManager = TestResultsManager(context)
        permissionManager = PermissionManager(context as Activity)
    }

    /**
     * Releases everything with a lifecycle: pending Handler runnables and
     * the CallManager's PhoneStateListener. Must be called from the host
     * activity's onDestroy(), otherwise a listener registered for an
     * in-flight test call outlives the screen.
     */
    fun cleanup() {
        cancelUssdTimeout()
        cancelUpi123ConfigDelay()
        if (::callManager.isInitialized) {
            callManager.cleanup()
        }
    }

    /**
     * Handle the phone-permission launcher result. There is no auto-retry:
     * the user re-taps the test action once permissions are granted.
     */
    fun onPhonePermissionsResult(granted: Boolean) {
        if (granted) {
            Log.d(TAG, "All permissions granted")
            uiCallback.showToast(context.getString(R.string.status_permissions_granted_ussd))
        } else {
            Log.w(TAG, "Some permissions denied")
            uiCallback.showToast(context.getString(R.string.error_permissions_denied))
        }
    }

    /**
     * Initiate a test call
     */
    fun initiateCall(callType: CallType) {
        // Guard: prevent duplicate dials
        if (callType == CallType.USSD && (ussdTesting || ussdTestCompleted)) {
            Log.d(TAG, "USSD already in progress or completed, ignoring duplicate dial")
            return
        }

        // Check phone permissions before initiating call - request if missing
        if (!permissionManager.hasPhonePermissions()) {
            Log.e(TAG, "Phone permissions not granted")
            uiCallback.showToast(context.getString(R.string.error_call_permission_required_ussd))
            uiCallback.requestPhonePermissions()
            return
        }

        val number = when (callType) {
            CallType.USSD -> "*99#"
            CallType.VOICE -> "1234567890"
            CallType.UPI123 -> "1234567890" // This should not be used anymore
            CallType.MANUAL_TRANSFER -> "1234567890"
        }

        // Update UI state
        when (callType) {
            CallType.USSD -> {
                Log.d(TAG, "Initiating USSD setup for *99# - will show dialog after 25 seconds")
                SetupHelper.setUserReportedUssdNotWorking(context, false)
                ussdTesting = true
                showUssdDialog = true
                ussdProgressMessage = "Dialing *99#..."
                uiCallback.updateUssdTesting(true)
                uiCallback.updateUssdDialog(true)
                uiCallback.updateUssdProgressMessage(ussdProgressMessage)
                Log.d(TAG, "USSD state set - ussdTesting: $ussdTesting, showUssdDialog: $showUssdDialog")
            }
            CallType.VOICE -> {
                voiceTesting = true
                showVoiceDialog = true
                uiCallback.updateVoiceTesting(true)
                uiCallback.updateVoiceDialog(true)
            }
            CallType.MANUAL_TRANSFER -> {
                // Manual transfer testing - similar to voice call
                voiceTesting = true
                showVoiceDialog = true
                uiCallback.updateVoiceTesting(true)
                uiCallback.updateVoiceDialog(true)
            }
            CallType.UPI123 -> {
                // UPI123 should use initiateUpi123Test() method instead
                Log.w(TAG, "UPI123 should use initiateUpi123Test() method instead of initiateCall()")
                return
            }
        }

        currentTestingType = callType

        // For USSD, start the 25-second timeout
        if (callType == CallType.USSD) {
            startUssdTimeout() // Start 25-second timeout
        }

        callManager.initiateCall(
            context = context,
            phoneNumber = number,
            callType = callType,
            onCallEnded = { type ->
                when (type) {
                    CallType.VOICE -> {
                        voiceTesting = false
                        showVoiceDialog = false
                        voiceTestCompleted = true
                        showCallCompleteButton = true
                        uiCallback.updateVoiceTesting(false)
                        uiCallback.updateVoiceDialog(false)
                        uiCallback.updateVoiceTestCompleted(true)
                        uiCallback.updateCallCompleteButton(true)
                    }
                    CallType.MANUAL_TRANSFER -> {
                        voiceTesting = false
                        showVoiceDialog = false
                        voiceTestCompleted = true
                        showCallCompleteButton = true
                        uiCallback.updateVoiceTesting(false)
                        uiCallback.updateVoiceDialog(false)
                        uiCallback.updateVoiceTestCompleted(true)
                        uiCallback.updateCallCompleteButton(true)
                    }
                    CallType.UPI123 -> {
                        // UPI123 is handled by initiateUpi123Test() method
                        Log.w(TAG, "UPI123 call ended in main initiateCall - this should not happen")
                    }
                    CallType.USSD -> {
                        // USSD call ended - just update button state
                        showCallCompleteButton = true
                        uiCallback.updateCallCompleteButton(true)
                    }
                }
                currentTestingType = null
            }
        )
    }

    // Simplified dialog logic - using simple 25-second timeout for USSD and immediate dialog for UPI123

    private fun cancelUssdTimeout() {
        ussdTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        ussdTimeoutRunnable = null
    }

    private fun cancelUpi123ConfigDelay() {
        upi123ConfigDelayRunnable?.let { mainHandler.removeCallbacks(it) }
        upi123ConfigDelayRunnable = null
    }

    /**
     * Start 25-second timeout for USSD setup
     */
    private fun startUssdTimeout() {
        Log.d(TAG, "Starting 25-second USSD timeout timer")
        cancelUssdTimeout()
        ussdTimeoutRunnable = Runnable {
            ussdTimeoutRunnable = null
            Log.d(TAG, "USSD 25-second timeout reached - showing configuration dialog")
            if (ussdTesting && !ussdTestCompleted) {
                showUssdDialog = false
                showUssdConfigurationOptions = true
                uiCallback.updateUssdDialog(false)
                uiCallback.updateUssdConfigurationOptions(true)
                Log.d(TAG, "Configuration dialog shown after 25 seconds - showUssdDialog: $showUssdDialog, showUssdConfigurationOptions: $showUssdConfigurationOptions")
            } else {
                Log.d(TAG, "USSD timeout reached but conditions not met - ussdTesting: $ussdTesting, ussdTestCompleted: $ussdTestCompleted")
            }
        }
        mainHandler.postDelayed(ussdTimeoutRunnable!!, 25000)
    }

    /**
     * Close USSD dialog from the X button, or dismiss progress early ("does not work" uses [fromDoesNotWork] toast).
     * If the confirmation step is showing, behaves like "Not Yet".
     */
    fun dismissUssdDialog(fromDoesNotWork: Boolean = false) {
        if (showUssdConfigurationOptions) {
            handleUssdConfigurationConfirmation(false)
            return
        }
        cancelUssdTimeout()
        ussdTesting = false
        showUssdDialog = false
        showUssdConfigurationOptions = false
        uiCallback.updateUssdTesting(false)
        uiCallback.updateUssdDialog(false)
        uiCallback.updateUssdConfigurationOptions(false)
        if (fromDoesNotWork) {
            SetupHelper.setUserReportedUssdNotWorking(context, true)
            uiCallback.showToast(context.getString(R.string.status_scan_to_pay_disabled))
        }
    }

    /**
     * Close UPI123 dialog from the X button. If confirming setup, behaves like "Not Yet".
     */
    fun dismissUpi123Dialog() {
        if (showUpi123ConfigurationOptions) {
            handleUpi123ConfigurationConfirmation(false)
            return
        }
        cancelUpi123ConfigDelay()
        upi123Testing = false
        showUpi123Dialog = false
        showUpi123ConfigurationOptions = false
        uiCallback.updateUpi123Testing(false)
        uiCallback.updateUpi123Dialog(false)
        uiCallback.updateUpi123ConfigurationOptions(false)
    }

    /**
     * Handle USSD configuration confirmation
     */
    fun handleUssdConfigurationConfirmation(configured: Boolean) {
        Log.d(TAG, "USSD configuration confirmation: $configured")
        cancelUssdTimeout()

        showUssdConfigurationOptions = false
        showUssdDialog = false
        ussdTesting = false

        if (configured) {
            SetupHelper.setUserReportedUssdNotWorking(context, false)
            ussdTestCompleted = true
            showCallCompleteButton = true
            uiCallback.updateUssdTestCompleted(true)
            uiCallback.updateCallCompleteButton(true)
            saveTestResults(ussdTestCompleted, upi123TestCompleted)
            uiCallback.showToast(context.getString(R.string.status_ussd_setup_complete))
        } else {
            uiCallback.showToast(context.getString(R.string.status_ussd_setup_incomplete))
        }

        uiCallback.updateUssdConfigurationOptions(false)
        uiCallback.updateUssdDialog(false)
        uiCallback.updateUssdTesting(false)
    }

    /**
     * Handle UPI123 configuration confirmation
     */
    fun handleUpi123ConfigurationConfirmation(configured: Boolean) {
        Log.d(TAG, "UPI123 configuration confirmation: $configured")
        cancelUpi123ConfigDelay()

        showUpi123ConfigurationOptions = false
        showUpi123Dialog = false
        upi123Testing = false

        if (configured) {
            upi123TestCompleted = true
            showCallCompleteButton = true
            uiCallback.updateUpi123TestCompleted(true)
            uiCallback.updateCallCompleteButton(true)
            saveTestResults(ussdTestCompleted, upi123TestCompleted)
            uiCallback.showToast(context.getString(R.string.status_upi123_setup_complete))
        } else {
            uiCallback.showToast(context.getString(R.string.status_upi123_setup_incomplete))
        }

        uiCallback.updateUpi123ConfigurationOptions(false)
        uiCallback.updateUpi123Dialog(false)
        uiCallback.updateUpi123Testing(false)
    }

    /**
     * Initiate UPI123 test - simplified flow - show dialog when call ends
     */
    fun initiateUpi123Test() {
        Log.d(
            TAG,
            "initiateUpi123Test called - upi123TestCompleted: $upi123TestCompleted, upi123Testing: $upi123Testing"
        )
        if (!upi123TestCompleted && !upi123Testing) {
            Log.d(TAG, "Starting UPI123 test")
            upi123Testing = true
            showUpi123Dialog = true
            uiCallback.updateUpi123Testing(true)
            uiCallback.updateUpi123Dialog(true)

            // Initiate UPI123 call - simple flow
            callManager.initiateCall(
                context = context,
                phoneNumber = AppConstants.DEFAULT_UPI_SERVICE_NUMBER,
                callType = CallType.UPI123,
                onCallEnded = { type ->
                    if (type == CallType.UPI123 && upi123Testing) {
                        Log.d(TAG, "UPI123 call ended - showing configuration dialog after 2 second delay")

                        // Stop testing state
                        upi123Testing = false
                        uiCallback.updateUpi123Testing(false)

                        cancelUpi123ConfigDelay()
                        upi123ConfigDelayRunnable = Runnable {
                            upi123ConfigDelayRunnable = null
                            if (!upi123TestCompleted) {
                                showUpi123Dialog = false
                                showUpi123ConfigurationOptions = true
                                uiCallback.updateUpi123Dialog(false)
                                uiCallback.updateUpi123ConfigurationOptions(true)
                                Log.d(TAG, "UPI123 configuration dialog shown after 2 second delay")
                            }
                        }
                        mainHandler.postDelayed(upi123ConfigDelayRunnable!!, 2000)
                    }
                }
            )
        } else {
            Log.d(TAG, "UPI123 test not started - already completed or testing")
        }
    }

    /**
     * Save test results
     */
    private fun saveTestResults(ussdCompleted: Boolean, upi123Completed: Boolean) {
        val results = TestResults(
            ussdEnabled = ussdCompleted,
            upi123Enabled = upi123Completed
        )
        testResultsManager.saveTestResults(results)
    }

    /**
     * Get existing test results
     */
    fun getTestResults(): TestResults? {
        return testResultsManager.getTestResults()
    }

    /**
     * Skip tests and continue to main
     */
    fun skipTests() {
        // Save current test results before continuing
        saveTestResults(ussdTestCompleted, upi123TestCompleted)

        // Mark test configuration as completed even when skipped
        val sharedPreferences = context.getSharedPreferences("FlowpayPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .putBoolean("test_configuration_completed", true)
            .apply()

        uiCallback.navigateToMain()
    }

    /**
     * Continue to main after completing tests
     */
    fun continueToMain() {
        // Save test results before continuing
        saveTestResults(ussdTestCompleted, upi123TestCompleted)

        // Mark test configuration as completed
        val sharedPreferences = context.getSharedPreferences("FlowpayPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .putBoolean("test_configuration_completed", true)
            .apply()

        uiCallback.navigateToMain()
    }

    /**
     * Check if tests can continue
     */
    fun canContinue(): Boolean {
        val ussdWaived = !SetupHelper.isPrimarySimUssdCapable(context) ||
            SetupHelper.hasUserReportedUssdNotWorking(context)
        return (ussdTestCompleted || ussdWaived) || upi123TestCompleted
    }

    /**
     * Check if all tests are completed
     */
    fun allTestsCompleted(): Boolean {
        val ussdWaived = !SetupHelper.isPrimarySimUssdCapable(context) ||
            SetupHelper.hasUserReportedUssdNotWorking(context)
        return (ussdTestCompleted || ussdWaived) && upi123TestCompleted
    }

    /**
     * Get current test states
     */
    fun getTestStates(): TestStates {
        return TestStates(
            ussdTesting = ussdTesting,
            showUssdDialog = showUssdDialog,
            ussdTestCompleted = ussdTestCompleted,
            upi123Testing = upi123Testing,
            upi123TestCompleted = upi123TestCompleted,
            voiceTesting = voiceTesting,
            showVoiceDialog = showVoiceDialog,
            voiceTestCompleted = voiceTestCompleted,
            showCallCompleteButton = showCallCompleteButton,
            ussdProgressMessage = ussdProgressMessage,
            showUssdConfigurationOptions = showUssdConfigurationOptions,
            showUpi123Dialog = showUpi123Dialog,
            showUpi123ConfigurationOptions = showUpi123ConfigurationOptions
        )
    }

    /**
     * Data class for test states
     */
    data class TestStates(
        val ussdTesting: Boolean,
        val showUssdDialog: Boolean,
        val ussdTestCompleted: Boolean,
        val upi123Testing: Boolean,
        val upi123TestCompleted: Boolean,
        val voiceTesting: Boolean,
        val showVoiceDialog: Boolean,
        val voiceTestCompleted: Boolean,
        val showCallCompleteButton: Boolean,
        val ussdProgressMessage: String,
        val showUssdConfigurationOptions: Boolean,
        val showUpi123Dialog: Boolean,
        val showUpi123ConfigurationOptions: Boolean
    )
}
