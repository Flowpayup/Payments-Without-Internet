package com.flowpay.app.helpers

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

object AudioStateManager {
    private const val TAG = "AudioStateManager"
    private var originalCallVolume: Int = -1
    private var originalMicMute: Boolean = false
    private var isAudioMuted: Boolean = false
    
    fun muteCallAudio(context: Context): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            
            // Check if we're in a call
            if (audioManager.mode != AudioManager.MODE_IN_CALL) {
                // Force call mode if not set
                audioManager.mode = AudioManager.MODE_IN_CALL
                Log.d(TAG, "Set audio mode to IN_CALL")
            }
            
            // Save original states we actually change.
            originalCallVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
            originalMicMute = audioManager.isMicrophoneMute

            Log.d(TAG, "Original call volume: $originalCallVolume")

            // Silence ONLY the voice-call stream. Ring/notification/system
            // streams are deliberately left untouched — zeroing a user's ringer
            // during a payment (and never restoring it) is real-world harm.
            audioManager.setStreamVolume(
                AudioManager.STREAM_VOICE_CALL,
                0,  // Mute the IVR voice line
                0   // No UI flags
            )
            Log.d(TAG, "Set STREAM_VOICE_CALL volume to 0")

            // Mute microphone
            audioManager.isMicrophoneMute = true
            Log.d(TAG, "Microphone muted")
            
            // Request audio focus so our app controls the call audio
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .build()
                audioManager.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }
            
            // Belt-and-suspenders: force the voice-call stream down on OEMs
            // where a single setStreamVolume(0) doesn't fully take effect.
            for (i in 0..10) {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.ADJUST_LOWER,
                    0
                )
            }
            
            isAudioMuted = true
            
            // Verify muting worked
            val newVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
            Log.d(TAG, "Call audio muted successfully. New volume: $newVolume")
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mute call audio", e)
            false
        }
    }
    
    fun restoreCallAudio(context: Context): Boolean {
        return try {
            if (!isAudioMuted || originalCallVolume == -1) {
                Log.d(TAG, "Audio was not muted or no saved state")
                return false
            }
            
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            
            // Restore original volume
            audioManager.setStreamVolume(
                AudioManager.STREAM_VOICE_CALL, 
                originalCallVolume, 
                0  // No flags
            )
            
            // Restore microphone state
            audioManager.isMicrophoneMute = originalMicMute
            
            Log.d(TAG, "Call audio restored to volume: $originalCallVolume")
            
            // Reset saved states
            originalCallVolume = -1
            originalMicMute = false
            isAudioMuted = false
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore call audio", e)
            false
        }
    }
    
    fun isCallAudioMuted(): Boolean = isAudioMuted
    
    fun resetState() {
        originalCallVolume = -1
        originalMicMute = false
        isAudioMuted = false
    }
}


