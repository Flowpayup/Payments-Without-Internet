package com.flowpay.app.managers

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.compose.runtime.mutableStateOf
import com.flowpay.app.constants.PermissionConstants
import com.flowpay.app.constants.AppConstants
import com.flowpay.app.payment.Upi123CallStringBuilder
import java.util.concurrent.atomic.AtomicBoolean

enum class CallType {
    USSD, VOICE, UPI123, MANUAL_TRANSFER
}

class CallManager(private val context: Context) {
    
    companion object {
        private const val TAG = "CallManager"
    }
    
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    
    private var originalCallVolume: Int = 0
    private var previousAudioMode: Int = AudioManager.MODE_NORMAL
    private var isAudioMuted = false
    
    // Call state tracking - thread-safe with proper synchronization
    private var _isCallInProgress = mutableStateOf(false)
    @Volatile
    private var currentCallType: CallType? = null
    private var onCallEndedCallback: ((CallType) -> Unit)? = null
    private var onUssdSessionComplete: (() -> Unit)? = null
    private var phoneStateListener: PhoneStateListener? = null
    
    // Synchronization objects for thread safety
    private val audioLock = Any()
    private val callStateLock = Any()
    private val callbackLock = Any()
    private val listenerLock = Any()

    private val isUSSDDialPending = AtomicBoolean(false)
    
    private val prefs = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Gets the configured UPI service number or default
     */
    private fun getUpiServiceNumber(): String {
        return prefs.getString(AppConstants.KEY_UPI_SERVICE_NUMBER, AppConstants.DEFAULT_UPI_SERVICE_NUMBER) ?: AppConstants.DEFAULT_UPI_SERVICE_NUMBER
    }
    
    /**
     * Sets the UPI service number with validation
     */
    fun setUpiServiceNumber(serviceNumber: String) {
        val sanitizedNumber = sanitizePhoneNumber(serviceNumber)
        if (isValidPhoneNumber(sanitizedNumber)) {
            prefs.edit().putString(AppConstants.KEY_UPI_SERVICE_NUMBER, sanitizedNumber).apply()
            Log.d(TAG, "UPI service number updated to: $sanitizedNumber")
        } else {
            Log.e(TAG, "Invalid UPI service number: $serviceNumber")
            throw IllegalArgumentException("Invalid UPI service number format")
        }
    }
    
    /**
     * Gets the current UPI service number
     */
    fun getCurrentUpiServiceNumber(): String = getUpiServiceNumber()
    
    /**
     * Thread-safe callback management
     */
    private fun setCallEndedCallback(callback: ((CallType) -> Unit)?) {
        synchronized(callbackLock) {
            onCallEndedCallback = callback
        }
    }
    
    private fun setUssdSessionCompleteCallback(callback: (() -> Unit)?) {
        synchronized(callbackLock) {
            onUssdSessionComplete = callback
        }
    }
    
    private fun getCallEndedCallback(): ((CallType) -> Unit)? {
        synchronized(callbackLock) {
            return onCallEndedCallback
        }
    }
    
    private fun getUssdSessionCompleteCallback(): (() -> Unit)? {
        synchronized(callbackLock) {
            return onUssdSessionComplete
        }
    }
    
    private fun clearCallbacks() {
        synchronized(callbackLock) {
            onCallEndedCallback = null
            onUssdSessionComplete = null
        }
    }
    
    /**
     * Initiates a call - simplified without cycle tracking
     */
    fun initiateCall(
        context: Context,
        phoneNumber: String,
        callType: CallType,
        onCallEnded: (CallType) -> Unit,
        onUssdComplete: (() -> Unit)? = null
    ) {
        // Check permissions using centralized constants
        if (ContextCompat.checkSelfPermission(context, PermissionConstants.CRITICAL_PERMISSIONS[0]) != 
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "CALL_PHONE permission not granted")
            return
        }
        
        // Handle USSD calls using ACTION_CALL (simplified)
        if (callType == CallType.USSD) {
            handleUSSDCall(phoneNumber, onUssdComplete)
            return
        }
        
        // Set up call tracking with thread safety
        synchronized(callStateLock) {
            currentCallType = callType
            setCallEndedCallback(onCallEnded)
            _isCallInProgress.value = true
        }
        
        // Create and register phone state listener for non-USSD calls
        synchronized(listenerLock) {
            phoneStateListener = createPhoneStateListener(onCallEnded)
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
        }
        
        // Initiate the call
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$phoneNumber")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        
        try {
            context.startActivity(intent)
            Log.d(TAG, "Call initiated: $callType")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initiate call: ${e.message}")
            synchronized(callStateLock) {
                _isCallInProgress.value = false
                currentCallType = null
                clearCallbacks()
            }
            unregisterPhoneStateListener()
        }
    }

    private fun unregisterPhoneStateListener() {
        synchronized(listenerLock) {
            try {
                phoneStateListener?.let { telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE) }
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering phone state listener: ${e.message}")
            } finally {
                phoneStateListener = null
            }
        }
    }
    
    /**
     * Handle USSD calls using ACTION_CALL (simplified)
     */
    private fun handleUSSDCall(ussdCode: String, onUssdComplete: (() -> Unit)?) {
        // Prevent double-dial at the call-manager level (atomic check-and-set)
        if (!isUSSDDialPending.compareAndSet(false, true)) {
            Log.w(TAG, "USSD dial already pending, ignoring duplicate")
            return
        }

        // Set up USSD callback
        setUssdSessionCompleteCallback(onUssdComplete)
        _isCallInProgress.value = true
        currentCallType = CallType.USSD

        Log.d(TAG, "Starting USSD call: $ussdCode")

        // Create and register phone state listener for USSD calls
        synchronized(listenerLock) {
            phoneStateListener = createPhoneStateListener { callType ->
                if (callType == CallType.USSD) {
                    isUSSDDialPending.set(false)
                    synchronized(callStateLock) {
                        _isCallInProgress.value = false
                        currentCallType = null
                        getUssdSessionCompleteCallback()?.invoke()
                        clearCallbacks()
                    }
                }
            }
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
        }
        
        // Initiate the USSD call using ACTION_CALL
        // URL encode the USSD code to preserve special characters like #
        val encodedUssdCode = Uri.encode(ussdCode)
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$encodedUssdCode")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        
        try {
            context.startActivity(intent)
            Log.d(TAG, "USSD call initiated: $ussdCode")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initiate USSD call: ${e.message}")
            isUSSDDialPending.set(false)
            synchronized(callStateLock) {
                _isCallInProgress.value = false
                currentCallType = null
                getUssdSessionCompleteCallback()?.invoke()
                clearCallbacks()
            }
            unregisterPhoneStateListener()
        }
    }
    
    /**
     * Creates phone state listener for non-USSD calls
     */
    private fun createPhoneStateListener(onCallEnded: (CallType) -> Unit): PhoneStateListener {
        return object : PhoneStateListener() {
            @Deprecated("Deprecated in Java")
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                val stateName = when (state) {
                    TelephonyManager.CALL_STATE_IDLE -> "IDLE"
                    TelephonyManager.CALL_STATE_RINGING -> "RINGING"
                    TelephonyManager.CALL_STATE_OFFHOOK -> "OFFHOOK"
                    else -> "UNKNOWN($state)"
                }
                Log.d(TAG, "Call State: $stateName")
                
                when (state) {
                    TelephonyManager.CALL_STATE_IDLE -> {
                        if (_isCallInProgress.value) {
                            // Call ended
                            synchronized(callStateLock) {
                                _isCallInProgress.value = false
                                currentCallType?.let { callType ->
                                    getCallEndedCallback()?.invoke(callType)
                                }
                                currentCallType = null
                                clearCallbacks()
                            }
                        }
                    }
                    TelephonyManager.CALL_STATE_OFFHOOK -> {
                        if (!_isCallInProgress.value) {
                            _isCallInProgress.value = true
                        }
                    }
                    TelephonyManager.CALL_STATE_RINGING -> {
                        // Call is ringing
                    }
                }
            }
        }
    }
    
    /**
     * Ends current call
     */
    fun endCall() {
        synchronized(callStateLock) {
            _isCallInProgress.value = false
            currentCallType = null
            clearCallbacks()
        }
        
        // Unregister phone state listener
        synchronized(listenerLock) {
            phoneStateListener?.let { listener ->
                telephonyManager.listen(listener, PhoneStateListener.LISTEN_NONE)
            }
            phoneStateListener = null
        }
    }
    
    /**
     * Checks if call is in progress
     */
    fun isCallInProgress(): Boolean = _isCallInProgress.value
    
    /**
     * Constructs the UPI123 call string in the format: tel:<serviceNumber>,,1,<phoneNumber>,,<amount>,,1
     * Validation and construction live in the pure, unit-tested Upi123CallStringBuilder.
     */
    fun constructUPI123CallString(phoneNumber: String, amount: String): String {
        return when (val result = Upi123CallStringBuilder.build(getUpiServiceNumber(), phoneNumber, amount)) {
            is Upi123CallStringBuilder.Result.Valid -> result.callString
            is Upi123CallStringBuilder.Result.Invalid -> throw IllegalArgumentException(result.reason)
        }
    }
    
    
    /**
     * Sanitizes phone number input (digits only)
     */
    private fun sanitizePhoneNumber(input: String): String {
        return input.replace(Regex("[^0-9]"), "")
    }
    
    /**
     * Sanitizes amount input (digits and single decimal point)
     */
    private fun sanitizeAmount(input: String): String {
        val digitsOnly = input.replace(Regex("[^0-9.]"), "")
        // Ensure only one decimal point
        val parts = digitsOnly.split(".")
        return if (parts.size > 2) {
            parts[0] + "." + parts.drop(1).joinToString("")
        } else {
            digitsOnly
        }
    }
    
    /**
     * Initiates a UPI123 call with the given phone number and amount
     */
    fun initiateUPI123Call(phoneNumber: String, amount: String): Boolean {
        return try {
            val result = Upi123CallStringBuilder.build(
                getUpiServiceNumber(),
                phoneNumber.orEmpty(),
                amount.orEmpty()
            )
            val callString = when (result) {
                is Upi123CallStringBuilder.Result.Invalid -> {
                    Log.e(TAG, "UPI123 call rejected: ${result.reason}")
                    Toast.makeText(context, result.reason, Toast.LENGTH_SHORT).show()
                    return false
                }
                is Upi123CallStringBuilder.Result.Valid -> result.callString
            }

            val hasCallPermission = ContextCompat.checkSelfPermission(
                context, PermissionConstants.CRITICAL_PERMISSIONS[0]
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasCallPermission) {
                Log.e(TAG, "CALL_PHONE permission not granted")
                Toast.makeText(
                    context,
                    "Phone call permission is required for manual payments. Please grant permission in Settings.",
                    Toast.LENGTH_LONG
                ).show()
                return false
            }

            // Call-state tracking and outcome interpretation are owned by
            // CallStateCoordinator / PaymentSessionManager — no per-call
            // PhoneStateListener is registered here.
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse(callString)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.d(TAG, "UPI123 call started")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception while initiating call: ${e.message}")
            Toast.makeText(context, "Permission denied: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error while initiating UPI123 call: ${e.message}")
            Toast.makeText(context, "Failed to initiate call. Please try again.", Toast.LENGTH_SHORT).show()
            false
        }
    }
    
    /**
     * Mutes all audio streams for seamless call experience
     * @return true if successful, false otherwise
     */
    fun muteCallAudio(): Boolean {
        synchronized(audioLock) {
            return try {
            Log.d(TAG, "Attempting to mute call audio")
            
            // Store original audio settings with error checking
            originalCallVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
            previousAudioMode = audioManager.mode
            
            // Set audio mode for call with validation
            audioManager.mode = AudioManager.MODE_IN_CALL
            if (audioManager.mode != AudioManager.MODE_IN_CALL) {
                Log.w(TAG, "Failed to set audio mode to MODE_IN_CALL")
                return false
            }
            
            // Mute microphone and speaker
            audioManager.isMicrophoneMute = true
            audioManager.isSpeakerphoneOn = false

            // Mute ONLY the voice-call stream. Ring/alarm/notification streams
            // must never be touched — silencing a user's alarm clock during a
            // 35-second payment call is real-world harm.
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, 0, 0)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mute voice-call stream: ${e.message}")
            }

            isAudioMuted = true
            Log.d(TAG, "Call audio muted")
            true
            
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception while muting audio: ${e.message}")
                Toast.makeText(context, "Permission required to control audio", Toast.LENGTH_SHORT).show()
                false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mute call audio: ${e.message}")
                Toast.makeText(context, "Failed to mute audio: ${e.message}", Toast.LENGTH_SHORT).show()
                false
            }
        }
    }
    
    /**
     * Restores all audio streams to original settings
     * @return true if successful, false otherwise
     */
    fun unmuteCallAudio(): Boolean {
        synchronized(audioLock) {
            return try {
            Log.d(TAG, "Restoring call audio")
            
            // Restore audio mode with validation
            audioManager.mode = previousAudioMode
            if (audioManager.mode != previousAudioMode) {
                Log.w(TAG, "Failed to restore audio mode to $previousAudioMode")
            }
            
            // Unmute microphone
            audioManager.isMicrophoneMute = false

            // Restore the voice-call volume (the only stream mute touches)
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, originalCallVolume, 0)
                val currentCallVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
                if (currentCallVolume != originalCallVolume) {
                    Log.w(TAG, "Failed to restore call volume, expected: $originalCallVolume, actual: $currentCallVolume")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore call volume: ${e.message}")
            }

            isAudioMuted = false
            Log.d(TAG, "Call audio restored successfully")
            true
            
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception while restoring audio: ${e.message}")
                Toast.makeText(context, "Permission required to control audio", Toast.LENGTH_SHORT).show()
                false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore call audio: ${e.message}")
                Toast.makeText(context, "Failed to restore audio: ${e.message}", Toast.LENGTH_SHORT).show()
                false
            }
        }
    }
    
    /**
     * Sets call volume to minimum (1) for IVR calls
     * @return true if successful, false otherwise
     */
    fun setCallVolumeToMinimum(): Boolean {
        synchronized(audioLock) {
            return try {
                Log.d(TAG, "Setting call volume to minimum (1)")
                
                // Store original call volume if not already stored
                if (originalCallVolume == 0) {
                    originalCallVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
                    Log.d(TAG, "Stored original call volume: $originalCallVolume")
                }
                
                // Set audio mode for call
                audioManager.mode = AudioManager.MODE_IN_CALL
                
                // Set call volume to 1 (minimum audible level)
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, 1, AudioManager.FLAG_SHOW_UI)
                val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
                
                Log.d(TAG, "Call volume set to: $currentVolume (target was 1)")
                
                if (currentVolume <= 1) {
                    Log.d(TAG, "Call volume set to minimum successfully")
                    true
                } else {
                    Log.w(TAG, "Failed to set call volume to 1, current volume: $currentVolume")
                    false
                }
                
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception while setting call volume: ${e.message}")
                false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set call volume to minimum: ${e.message}")
                false
            }
        }
    }
    
    /**
     * Restores call volume to original level
     * @return true if successful, false otherwise
     */
    fun restoreCallVolume(): Boolean {
        synchronized(audioLock) {
            return try {
                Log.d(TAG, "Restoring call volume to original level: $originalCallVolume")
                
                if (originalCallVolume > 0) {
                    // Restore audio mode
                    audioManager.mode = previousAudioMode
                    
                    // Restore call volume
                    audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, originalCallVolume, AudioManager.FLAG_SHOW_UI)
                    val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
                    
                    Log.d(TAG, "Call volume restored to: $currentVolume (expected: $originalCallVolume)")
                    
                    if (currentVolume == originalCallVolume) {
                        Log.d(TAG, "Call volume restored successfully")
                        true
                    } else {
                        Log.w(TAG, "Failed to restore call volume, expected: $originalCallVolume, actual: $currentVolume")
                        false
                    }
                } else {
                    Log.w(TAG, "No original call volume stored, cannot restore")
                    false
                }
                
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception while restoring call volume: ${e.message}")
                false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore call volume: ${e.message}")
                false
            }
        }
    }
    
    /**
     * Shows the call overlay for UPI123 protection using the new service
     */
    fun showCallOverlay(phoneNumber: String, amount: String) {
        try {
            // Use the new CallOverlayService instead of activity
            com.flowpay.app.services.CallOverlayService.showOverlay(context, phoneNumber, amount)
            Log.d(TAG, "Call overlay shown via service")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show call overlay: ${e.message}")
        }
    }
    
    /**
     * Hides the call overlay using the new service
     */
    fun hideCallOverlay() {
        try {
            // Use the new CallOverlayService instead of activity
            com.flowpay.app.services.CallOverlayService.hideOverlay(context)
            Log.d(TAG, "Call overlay hidden via service")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hide call overlay: ${e.message}")
        }
    }
    
    /**
     * Terminates the active call via TelecomManager.endCall().
     *
     * Requires the ANSWER_PHONE_CALLS runtime permission; without it this
     * honestly returns false so the caller can tell the user to hang up
     * manually. The previous KEYCODE_ENDCALL / ITelephony-reflection
     * fallbacks never worked on Android 10+ and were removed.
     */
    fun terminateCall(): Boolean {
        return try {
            if (getCallState() == TelephonyManager.CALL_STATE_IDLE) {
                Log.w(TAG, "No call in progress, cannot terminate")
                return false
            }
            val hasPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ANSWER_PHONE_CALLS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                Log.w(TAG, "ANSWER_PHONE_CALLS not granted - cannot end call programmatically")
                return false
            }
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            @Suppress("DEPRECATION")
            val terminated = telecomManager.endCall()
            Log.d(TAG, "TelecomManager.endCall() -> $terminated")
            if (terminated) {
                synchronized(callStateLock) {
                    _isCallInProgress.value = false
                    currentCallType = null
                }
            }
            terminated
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception while terminating call: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to terminate call: ${e.message}")
            false
        }
    }
    
    
    /**
     * Cleanup method to release all resources and prevent memory leaks
     */
    fun cleanup() {
        synchronized(audioLock) {
            try {
                // Restore audio if it was muted
                if (isAudioMuted) {
                    unmuteCallAudio()
                } else {
                    Log.d(TAG, "Audio was not muted, no restoration needed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during audio cleanup: ${e.message}")
            }
        }
        
        synchronized(callStateLock) {
            try {
                // Clear all callbacks and state
                clearCallbacks()
                currentCallType = null
                _isCallInProgress.value = false
            } catch (e: Exception) {
                Log.e(TAG, "Error during state cleanup: ${e.message}")
            }
        }
        
        // Unregister phone state listener
        synchronized(listenerLock) {
            try {
                phoneStateListener?.let { listener ->
                    telephonyManager.listen(listener, PhoneStateListener.LISTEN_NONE)
                }
                phoneStateListener = null
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering phone state listener: ${e.message}")
            }
        }
        
        Log.d(TAG, "CallManager cleanup completed")
    }
    
    /**
     * Checks if audio is currently muted
     */
    fun isAudioMuted(): Boolean = isAudioMuted
    
    /**
     * Gets the current call state
     */
    fun getCallState(): Int = telephonyManager.callState
    
    /**
     * Validates phone number format (10 digits)
     */
    fun isValidPhoneNumber(phoneNumber: String?): Boolean {
        if (phoneNumber.isNullOrBlank()) return false
        // More strict validation - exactly 10 digits, no leading zeros for first digit
        return phoneNumber.matches(Regex(AppConstants.PHONE_NUMBER_PATTERN))
    }
    
    /**
     * Validates amount format (positive number with reasonable limits)
     */
    fun isValidAmount(amount: String?): Boolean {
        if (amount.isNullOrBlank()) return false
        return try {
            val amountValue = amount.toDouble()
            // More reasonable limits: minimum 1 rupee, maximum 1 lakh
            amountValue >= AppConstants.MIN_AMOUNT_VALUE && amountValue <= AppConstants.MAX_AMOUNT_VALUE
        } catch (e: NumberFormatException) {
            false
        }
    }
    
    /**
     * Validates UPI service number format
     */
    fun isValidUpiServiceNumber(serviceNumber: String): Boolean {
        if (serviceNumber.isNullOrBlank()) return false
        return serviceNumber.matches(Regex("^[0-9]{10,12}$"))
    }
    
    /**
     * Sanitizes and validates phone number input
     */
    fun sanitizeAndValidatePhoneNumber(phoneNumber: String): String? {
        if (phoneNumber.isNullOrBlank()) return null
        val sanitized = phoneNumber.replace(Regex("[^0-9]"), "")
        return if (sanitized.length == 10) sanitized else null
    }
    
    /**
     * Sanitizes and validates amount input
     */
    fun sanitizeAndValidateAmount(amount: String): String? {
        if (amount.isNullOrBlank()) return null
        val sanitized = amount.replace(Regex("[^0-9.]"), "")
        return if (isValidAmount(sanitized)) sanitized else null
    }
}