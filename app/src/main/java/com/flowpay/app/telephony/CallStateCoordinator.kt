package com.flowpay.app.telephony

import android.content.Context
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface DeviceCallState {
    data object Idle : DeviceCallState
    data object Ringing : DeviceCallState
    data object OffHook : DeviceCallState
}

sealed interface CallSessionEvent {
    /** A call became active (OFFHOOK). */
    data class Started(val timestampMs: Long) : CallSessionEvent

    /** The active call ended (OFFHOOK -> IDLE). */
    data class Ended(val durationMs: Long) : CallSessionEvent
}

/**
 * What consumers (PaymentSessionManager, overlay) actually depend on —
 * lets tests substitute a fake without a real TelephonyManager.
 */
interface CallStateSource {
    val callState: StateFlow<DeviceCallState>
    val sessionEvents: SharedFlow<CallSessionEvent>
    fun acquire(tag: String)
    fun release(tag: String)
}

/**
 * The single authority for device call state.
 *
 * Exactly one telephony listener is registered for the whole app
 * (TelephonyCallback on API 31+, PhoneStateListener on 29-30) and every
 * consumer observes [callState] / [sessionEvents]. This replaces the five
 * independent PhoneStateListeners that previously each kept their own
 * divergent idea of whether a call was active.
 *
 * Registration is reference-counted: consumers call [acquire] while they
 * need call-state updates and [release] when done; the underlying listener
 * is registered only while at least one consumer holds a reference.
 */
class CallStateCoordinator(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis
) : CallStateSource {

    companion object {
        private const val TAG = "CallStateCoordinator"
    }

    private val appContext = context.applicationContext
    private val telephonyManager =
        appContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private val _callState = MutableStateFlow<DeviceCallState>(DeviceCallState.Idle)
    override val callState: StateFlow<DeviceCallState> = _callState.asStateFlow()

    private val _sessionEvents = MutableSharedFlow<CallSessionEvent>(extraBufferCapacity = 16)
    override val sessionEvents: SharedFlow<CallSessionEvent> = _sessionEvents.asSharedFlow()

    private val refCount = AtomicInteger(0)
    private val registrationLock = Any()

    @Volatile
    private var offHookSinceMs = 0L

    private var telephonyCallback: TelephonyCallback? = null

    @Suppress("DEPRECATION")
    private var phoneStateListener: PhoneStateListener? = null

    override fun acquire(tag: String) {
        if (refCount.incrementAndGet() == 1) {
            register(tag)
        }
        Log.d(TAG, "acquire($tag) refCount=${refCount.get()}")
    }

    override fun release(tag: String) {
        val remaining = refCount.decrementAndGet()
        if (remaining <= 0) {
            refCount.set(0)
            unregister(tag)
        }
        Log.d(TAG, "release($tag) refCount=${refCount.get()}")
    }

    private fun register(tag: String) {
        synchronized(registrationLock) {
            if (telephonyCallback != null || phoneStateListener != null) return
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                        override fun onCallStateChanged(state: Int) {
                            handleStateChange(state)
                        }
                    }
                    telephonyManager.registerTelephonyCallback(
                        ContextCompat.getMainExecutor(appContext),
                        callback
                    )
                    telephonyCallback = callback
                } else {
                    @Suppress("DEPRECATION")
                    val listener = object : PhoneStateListener() {
                        @Deprecated("Deprecated in Java")
                        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                            handleStateChange(state)
                        }
                    }
                    @Suppress("DEPRECATION")
                    telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
                    phoneStateListener = listener
                }
                // Seed current state so consumers that acquire mid-call see OFFHOOK.
                handleStateChange(currentCallStateCompat())
                Log.d(TAG, "Telephony listener registered ($tag)")
            } catch (e: SecurityException) {
                // READ_PHONE_STATE not granted; consumers observe a permanently Idle flow.
                Log.e(TAG, "Cannot register telephony listener: ${e.message}")
                telephonyCallback = null
                phoneStateListener = null
            }
        }
    }

    private fun unregister(tag: String) {
        synchronized(registrationLock) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    telephonyCallback?.let { telephonyManager.unregisterTelephonyCallback(it) }
                } else {
                    @Suppress("DEPRECATION")
                    phoneStateListener?.let {
                        telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
                    }
                }
                Log.d(TAG, "Telephony listener unregistered ($tag)")
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering telephony listener: ${e.message}")
            } finally {
                telephonyCallback = null
                phoneStateListener = null
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun currentCallStateCompat(): Int = try {
        telephonyManager.callState
    } catch (e: SecurityException) {
        TelephonyManager.CALL_STATE_IDLE
    }

    internal fun handleStateChange(state: Int) {
        val newState = when (state) {
            TelephonyManager.CALL_STATE_RINGING -> DeviceCallState.Ringing
            TelephonyManager.CALL_STATE_OFFHOOK -> DeviceCallState.OffHook
            else -> DeviceCallState.Idle
        }
        val previous = _callState.value
        if (previous == newState) return
        _callState.value = newState
        Log.d(TAG, "Call state: $previous -> $newState")

        when {
            newState is DeviceCallState.OffHook && previous !is DeviceCallState.OffHook -> {
                offHookSinceMs = clock()
                _sessionEvents.tryEmit(CallSessionEvent.Started(offHookSinceMs))
            }
            newState is DeviceCallState.Idle && previous is DeviceCallState.OffHook -> {
                val duration = (clock() - offHookSinceMs).coerceAtLeast(0L)
                _sessionEvents.tryEmit(CallSessionEvent.Ended(duration))
            }
        }
    }
}
