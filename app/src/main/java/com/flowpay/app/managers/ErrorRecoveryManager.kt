package com.flowpay.app.managers

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.flowpay.app.R

/**
 * Centralized error recovery manager for the USSD Overlay system
 * Provides user-friendly error messages and recovery suggestions
 */
class ErrorRecoveryManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ErrorRecoveryManager"
        
        // Error types
        const val ERROR_OVERLAY_PERMISSION = "overlay_permission"
        const val ERROR_CALL_PERMISSION = "call_permission"
        const val ERROR_SMS_PERMISSION = "sms_permission"
        const val ERROR_USSD_DIAL_FAILED = "ussd_dial_failed"
        const val ERROR_OVERLAY_SERVICE_FAILED = "overlay_service_failed"
        const val ERROR_SMS_DETECTION_FAILED = "sms_detection_failed"
        const val ERROR_PAYMENT_STATE_INVALID = "payment_state_invalid"
        const val ERROR_NETWORK_UNAVAILABLE = "network_unavailable"
        const val ERROR_UNKNOWN = "unknown"
    }
    
    /**
     * Handle error with appropriate recovery action
     */
    fun handleError(errorType: String, errorMessage: String, onRetry: (() -> Unit)? = null) {
        Log.e(TAG, "Handling error: $errorType - $errorMessage")
        
        when (errorType) {
            ERROR_OVERLAY_PERMISSION -> handleOverlayPermissionError(onRetry)
            ERROR_CALL_PERMISSION -> handleCallPermissionError(onRetry)
            ERROR_SMS_PERMISSION -> handleSMSPermissionError(onRetry)
            ERROR_USSD_DIAL_FAILED -> handleUSSDDialError(errorMessage, onRetry)
            ERROR_OVERLAY_SERVICE_FAILED -> handleOverlayServiceError(errorMessage, onRetry)
            ERROR_SMS_DETECTION_FAILED -> handleSMSDetectionError(errorMessage, onRetry)
            ERROR_PAYMENT_STATE_INVALID -> handlePaymentStateError(errorMessage, onRetry)
            ERROR_NETWORK_UNAVAILABLE -> handleNetworkError(errorMessage, onRetry)
            else -> handleUnknownError(errorMessage, onRetry)
        }
    }
    
    /**
     * Handle overlay permission error
     */
    private fun handleOverlayPermissionError(onRetry: (() -> Unit)?) {
        val dialog = AlertDialog.Builder(context)
            .setTitle("Overlay Permission Required")
            .setMessage("Flowpay needs overlay permission to show payment guidance during USSD calls.\n\n" +
                    "This helps protect you from fraud by showing secure payment instructions.\n\n" +
                    "Please grant overlay permission in Settings to continue.")
            .setPositiveButton("Open Settings") { _, _ ->
                openOverlayPermissionSettings()
            }
            .setNegativeButton("Cancel") { _, _ ->
                // Do nothing, user cancelled
            }
        
        if (onRetry != null) {
            dialog.setNeutralButton("Retry") { _, _ ->
                onRetry()
            }
        }
        
        dialog.show()
    }
    
    /**
     * Handle call permission error
     */
    private fun handleCallPermissionError(onRetry: (() -> Unit)?) {
        val dialog = AlertDialog.Builder(context)
            .setTitle("Call Permission Required")
            .setMessage("Flowpay needs call permission to initiate USSD calls for payments.\n\n" +
                    "This is essential for the payment process to work.\n\n" +
                    "Please grant call permission in Settings to continue.")
            .setPositiveButton("Open Settings") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Cancel") { _, _ ->
                // Do nothing, user cancelled
            }
        
        if (onRetry != null) {
            dialog.setNeutralButton("Retry") { _, _ ->
                onRetry()
            }
        }
        
        dialog.show()
    }
    
    /**
     * Handle SMS permission error
     */
    private fun handleSMSPermissionError(onRetry: (() -> Unit)?) {
        val dialog = AlertDialog.Builder(context)
            .setTitle("SMS Permission Required")
            .setMessage("Flowpay needs SMS permission to detect payment confirmations.\n\n" +
                    "This allows automatic completion of payments when you receive SMS from your bank.\n\n" +
                    "Please grant SMS permission in Settings to continue.")
            .setPositiveButton("Open Settings") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Cancel") { _, _ ->
                // Do nothing, user cancelled
            }
        
        if (onRetry != null) {
            dialog.setNeutralButton("Retry") { _, _ ->
                onRetry()
            }
        }
        
        dialog.show()
    }
    
    /**
     * Handle USSD dial error
     */
    private fun handleUSSDDialError(errorMessage: String, onRetry: (() -> Unit)?) {
        val dialog = AlertDialog.Builder(context)
            .setTitle("USSD Call Failed")
            .setMessage("Failed to initiate USSD call: $errorMessage\n\n" +
                    "This might be due to:\n" +
                    "• Network issues\n" +
                    "• Invalid USSD code for your carrier\n" +
                    "• Call permission not granted\n\n" +
                    "Please check your network connection and try again.")
            .setPositiveButton("OK") { _, _ ->
                // Do nothing
            }
        
        if (onRetry != null) {
            dialog.setNegativeButton("Retry") { _, _ ->
                onRetry()
            }
        }
        
        dialog.show()
    }
    
    /**
     * Handle overlay service error
     */
    private fun handleOverlayServiceError(errorMessage: String, onRetry: (() -> Unit)?) {
        val dialog = AlertDialog.Builder(context)
            .setTitle("Payment Guidance Failed")
            .setMessage("Failed to show payment guidance: $errorMessage\n\n" +
                    "This might be due to:\n" +
                    "• Overlay permission not granted\n" +
                    "• System resource limitations\n" +
                    "• App configuration issues\n\n" +
                    "Please check permissions and try again.")
            .setPositiveButton("OK") { _, _ ->
                // Do nothing
            }
        
        if (onRetry != null) {
            dialog.setNegativeButton("Retry") { _, _ ->
                onRetry()
            }
        }
        
        dialog.show()
    }
    
    /**
     * Handle SMS detection error
     */
    private fun handleSMSDetectionError(errorMessage: String, onRetry: (() -> Unit)?) {
        val dialog = AlertDialog.Builder(context)
            .setTitle("SMS Detection Failed")
            .setMessage("Failed to detect payment SMS: $errorMessage\n\n" +
                    "This might be due to:\n" +
                    "• SMS permission not granted\n" +
                    "• Bank SMS format not recognized\n" +
                    "• Network issues\n\n" +
                    "You can still complete the payment manually.")
            .setPositiveButton("OK") { _, _ ->
                // Do nothing
            }
        
        if (onRetry != null) {
            dialog.setNegativeButton("Retry") { _, _ ->
                onRetry()
            }
        }
        
        dialog.show()
    }
    
    /**
     * Handle payment state error
     */
    private fun handlePaymentStateError(errorMessage: String, onRetry: (() -> Unit)?) {
        val dialog = AlertDialog.Builder(context)
            .setTitle("Payment State Error")
            .setMessage("Payment state is invalid: $errorMessage\n\n" +
                    "This might be due to:\n" +
                    "• App was closed during payment\n" +
                    "• System memory issues\n" +
                    "• Data corruption\n\n" +
                    "Please restart the payment process.")
            .setPositiveButton("OK") { _, _ ->
                // Do nothing
            }
        
        if (onRetry != null) {
            dialog.setNegativeButton("Retry") { _, _ ->
                onRetry()
            }
        }
        
        dialog.show()
    }
    
    /**
     * Handle network error
     */
    private fun handleNetworkError(errorMessage: String, onRetry: (() -> Unit)?) {
        val dialog = AlertDialog.Builder(context)
            .setTitle("Network Error")
            .setMessage("Network connection issue: $errorMessage\n\n" +
                    "Please check your internet connection and try again.")
            .setPositiveButton("OK") { _, _ ->
                // Do nothing
            }
        
        if (onRetry != null) {
            dialog.setNegativeButton("Retry") { _, _ ->
                onRetry()
            }
        }
        
        dialog.show()
    }
    
    /**
     * Handle unknown error
     */
    private fun handleUnknownError(errorMessage: String, onRetry: (() -> Unit)?) {
        val dialog = AlertDialog.Builder(context)
            .setTitle("Unexpected Error")
            .setMessage("An unexpected error occurred: $errorMessage\n\n" +
                    "Please try again or contact support if the problem persists.")
            .setPositiveButton("OK") { _, _ ->
                // Do nothing
            }
        
        if (onRetry != null) {
            dialog.setNegativeButton("Retry") { _, _ ->
                onRetry()
            }
        }
        
        dialog.show()
    }
    
    /**
     * Show simple error toast
     */
    fun showErrorToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
    
    /**
     * Show success toast
     */
    fun showSuccessToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Open overlay permission settings
     */
    private fun openOverlayPermissionSettings() {
        try {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:${context.packageName}")
            )
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open overlay permission settings", e)
            openAppSettings()
        }
    }
    
    /**
     * Open app settings
     */
    private fun openAppSettings() {
        try {
            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = android.net.Uri.parse("package:${context.packageName}")
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app settings", e)
        }
    }
    
    /**
     * Check if error is recoverable
     */
    fun isRecoverableError(errorType: String): Boolean {
        return when (errorType) {
            ERROR_OVERLAY_PERMISSION, ERROR_CALL_PERMISSION, ERROR_SMS_PERMISSION,
            ERROR_USSD_DIAL_FAILED, ERROR_OVERLAY_SERVICE_FAILED, ERROR_SMS_DETECTION_FAILED,
            ERROR_NETWORK_UNAVAILABLE -> true
            else -> false
        }
    }
    
    /**
     * Get error recovery suggestion
     */
    fun getRecoverySuggestion(errorType: String): String {
        return when (errorType) {
            ERROR_OVERLAY_PERMISSION -> "Grant overlay permission in Settings > Apps > FlowPay > Permissions"
            ERROR_CALL_PERMISSION -> "Grant call permission in Settings > Apps > FlowPay > Permissions"
            ERROR_SMS_PERMISSION -> "Grant SMS permission in Settings > Apps > FlowPay > Permissions"
            ERROR_USSD_DIAL_FAILED -> "Check network connection and try again"
            ERROR_OVERLAY_SERVICE_FAILED -> "Restart the app and try again"
            ERROR_SMS_DETECTION_FAILED -> "Check SMS permissions and try again"
            ERROR_PAYMENT_STATE_INVALID -> "Restart the payment process"
            ERROR_NETWORK_UNAVAILABLE -> "Check your internet connection"
            else -> "Try again or contact support"
        }
    }
}

