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
            
            // Save ALL original states
            originalCallVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
            originalMicMute = audioManager.isMicrophoneMute
            val originalRingerMode = audioManager.ringerMode
            
            Log.d(TAG, "Original call volume: $originalCallVolume")
            Log.d(TAG, "Max call volume: ${audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)}")
            
            // Method 1: Set call volume to 0
            audioManager.setStreamVolume(
                AudioManager.STREAM_VOICE_CALL, 
                0,  // Mute
                0   // No UI flags
            )
            Log.d(TAG, "Set STREAM_VOICE_CALL volume to 0")
            
            // Method 2: Also mute other relevant streams
            audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, 0)
            audioManager.setStreamVolume(AudioManager.STREAM_RING, 0, 0)
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0)
            
            // Method 3: Mute microphone
            audioManager.isMicrophoneMute = true
            Log.d(TAG, "Microphone muted")
            
            // Method 4: Request audio focus to ensure our app controls audio
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
            
            // Method 5: Adjust call volume using adjustStreamVolume (alternative approach)
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


