package com.flowpay.app.managers

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.OvershootInterpolator
import android.view.animation.ScaleAnimation
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.flowpay.app.R

/**
 * Centralized feedback manager for enhanced user experience
 * Provides visual, haptic, and audio feedback throughout the app
 */
class FeedbackManager(private val context: Context) {
    
    companion object {
        private const val TAG = "FeedbackManager"
        
        // Feedback types
        const val FEEDBACK_SUCCESS = "success"
        const val FEEDBACK_ERROR = "error"
        const val FEEDBACK_WARNING = "warning"
        const val FEEDBACK_INFO = "info"
        const val FEEDBACK_PROGRESS = "progress"
        
        // Animation durations
        private const val ANIMATION_DURATION_SHORT = 200L
        private const val ANIMATION_DURATION_MEDIUM = 400L
        private const val ANIMATION_DURATION_LONG = 600L
    }
    
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private val handler = Handler(Looper.getMainLooper())
    
    /**
     * Show visual feedback with animation
     */
    fun showVisualFeedback(view: View, message: String, type: String = FEEDBACK_INFO) {
        Log.d(TAG, "Showing visual feedback: $message (type: $type)")
        
        when (type) {
            FEEDBACK_SUCCESS -> showSuccessFeedback(view, message)
            FEEDBACK_ERROR -> showErrorFeedback(view, message)
            FEEDBACK_WARNING -> showWarningFeedback(view, message)
            FEEDBACK_INFO -> showInfoFeedback(view, message)
            FEEDBACK_PROGRESS -> showProgressFeedback(view, message)
        }
    }
    
    /**
     * Show success feedback with green color and check animation
     */
    private fun showSuccessFeedback(view: View, message: String) {
        if (view is TextView) {
            view.setTextColor(ContextCompat.getColor(context, R.color.success_green))
            view.text = "✓ $message"
        }
        
        // Animate with bounce effect
        animateView(view, R.color.success_green, true)
        
        // Show haptic feedback
        vibrate(VibrationEffect.EFFECT_TICK)
        
        // Show toast
        showToast(message, Toast.LENGTH_SHORT)
    }
    
    /**
     * Show error feedback with red color and shake animation
     */
    private fun showErrorFeedback(view: View, message: String) {
        if (view is TextView) {
            view.setTextColor(ContextCompat.getColor(context, R.color.error_red))
            view.text = "✗ $message"
        }
        
        // Animate with shake effect
        animateView(view, R.color.error_red, false)
        
        // Show haptic feedback
        vibrate(VibrationEffect.EFFECT_DOUBLE_CLICK)
        
        // Show toast
        showToast(message, Toast.LENGTH_LONG)
    }
    
    /**
     * Show warning feedback with orange color and pulse animation
     */
    private fun showWarningFeedback(view: View, message: String) {
        if (view is TextView) {
            view.setTextColor(ContextCompat.getColor(context, R.color.warning_orange))
            view.text = "⚠ $message"
        }
        
        // Animate with pulse effect
        animateView(view, R.color.warning_orange, false)
        
        // Show haptic feedback
        vibrate(VibrationEffect.EFFECT_HEAVY_CLICK)
        
        // Show toast
        showToast(message, Toast.LENGTH_SHORT)
    }
    
    /**
     * Show info feedback with blue color and fade animation
     */
    private fun showInfoFeedback(view: View, message: String) {
        val infoColor = getThemeAccentColor()
        if (view is TextView) {
            view.setTextColor(infoColor)
            view.text = "ℹ $message"
        }
        
        // Animate with fade effect
        animateView(view, R.color.info_blue, false)
        
        // Show toast
        showToast(message, Toast.LENGTH_SHORT)
    }
    
    private fun getThemeAccentColor(): Int {
        val typedValue = TypedValue()
        return if (context.theme.resolveAttribute(R.attr.flowpayColorAccent, typedValue, true) && typedValue.resourceId != 0) {
            ContextCompat.getColor(context, typedValue.resourceId)
        } else {
            ContextCompat.getColor(context, R.color.info_blue)
        }
    }
    
    /**
     * Show progress feedback with loading animation
     */
    private fun showProgressFeedback(view: View, message: String) {
        if (view is TextView) {
            view.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            view.text = "⟳ $message"
        }
        
        // Animate with rotation effect
        animateProgressView(view)
        
        // Show toast
        showToast(message, Toast.LENGTH_SHORT)
    }
    
    /**
     * Animate view with color and bounce effect
     */
    private fun animateView(view: View, colorRes: Int, isSuccess: Boolean) {
        val originalColor = if (view is TextView) view.currentTextColor else 0
        
        // Scale animation
        val scaleAnimation = ScaleAnimation(
            1.0f, if (isSuccess) 1.1f else 0.95f,
            1.0f, if (isSuccess) 1.1f else 0.95f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = ANIMATION_DURATION_MEDIUM
            interpolator = if (isSuccess) OvershootInterpolator(1.2f) else null
        }
        
        // Alpha animation
        val alphaAnimation = AlphaAnimation(1.0f, 0.7f).apply {
            duration = ANIMATION_DURATION_SHORT
            repeatCount = 1
            repeatMode = AlphaAnimation.REVERSE
        }
        
        // Animation set
        val animationSet = AnimationSet(true).apply {
            addAnimation(scaleAnimation)
            if (!isSuccess) addAnimation(alphaAnimation)
        }
        
        view.startAnimation(animationSet)
        
        // Reset color after animation
        handler.postDelayed({
            if (view is TextView) {
                view.setTextColor(originalColor)
            }
        }, ANIMATION_DURATION_MEDIUM)
    }
    
    /**
     * Animate progress view with rotation
     */
    private fun animateProgressView(view: View) {
        val rotationAnimation = ScaleAnimation(
            1.0f, 1.0f, 1.0f, 1.0f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 1000
            repeatCount = -1 // Infinite
        }
        
        view.startAnimation(rotationAnimation)
    }
    
    /**
     * Show haptic feedback
     */
    fun vibrate(effect: Int) {
        if (vibrator?.hasVibrator() == true) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createPredefined(effect))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(100)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to vibrate", e)
            }
        }
    }
    
    /**
     * Show custom haptic pattern
     */
    fun vibratePattern(pattern: LongArray) {
        if (vibrator?.hasVibrator() == true) {
            try {
                vibrator.vibrate(pattern, -1)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to vibrate pattern", e)
            }
        }
    }
    
    /**
     * Show toast message
     */
    fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        handler.post {
            Toast.makeText(context, message, duration).show()
        }
    }
    
    /**
     * Show success toast with haptic feedback
     */
    fun showSuccessToast(message: String) {
        showToast("✓ $message", Toast.LENGTH_SHORT)
        vibrate(VibrationEffect.EFFECT_TICK)
    }
    
    /**
     * Show error toast with haptic feedback
     */
    fun showErrorToast(message: String) {
        showToast("✗ $message", Toast.LENGTH_LONG)
        vibrate(VibrationEffect.EFFECT_DOUBLE_CLICK)
    }
    
    /**
     * Show warning toast with haptic feedback
     */
    fun showWarningToast(message: String) {
        showToast("⚠ $message", Toast.LENGTH_SHORT)
        vibrate(VibrationEffect.EFFECT_HEAVY_CLICK)
    }
    
    /**
     * Show info toast
     */
    fun showInfoToast(message: String) {
        showToast("ℹ $message", Toast.LENGTH_SHORT)
    }
    
    /**
     * Show progress toast
     */
    fun showProgressToast(message: String) {
        showToast("⟳ $message", Toast.LENGTH_SHORT)
    }
    
    /**
     * Clear all animations from view
     */
    fun clearAnimations(view: View) {
        view.clearAnimation()
    }
    
    /**
     * Show loading state with progress indicator
     */
    fun showLoadingState(view: View, message: String = "Loading...") {
        if (view is TextView) {
            view.text = "⟳ $message"
            view.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        }
        
        animateProgressView(view)
    }
    
    /**
     * Hide loading state
     */
    fun hideLoadingState(view: View) {
        clearAnimations(view)
        
        if (view is TextView) {
            view.text = ""
        }
    }
    
    /**
     * Show countdown feedback
     */
    fun showCountdown(view: View, seconds: Int, onComplete: () -> Unit) {
        if (view is TextView) {
            var remaining = seconds
            
            val countdownRunnable = object : Runnable {
                override fun run() {
                    if (remaining > 0) {
                        view.text = "Starting in $remaining..."
                        view.setTextColor(ContextCompat.getColor(context, R.color.warning_orange))
                        remaining--
                        handler.postDelayed(this, 1000)
                    } else {
                        view.text = "Starting now!"
                        view.setTextColor(ContextCompat.getColor(context, R.color.success_green))
                        vibrate(VibrationEffect.EFFECT_TICK)
                        onComplete()
                    }
                }
            }
            
            handler.post(countdownRunnable)
        }
    }
    
    /**
     * Show step progress feedback
     */
    fun showStepProgress(currentStep: Int, totalSteps: Int, stepName: String) {
        val message = "Step $currentStep of $totalSteps: $stepName"
        showProgressToast(message)
        
        // Haptic feedback for step completion
        if (currentStep == totalSteps) {
            vibrate(VibrationEffect.EFFECT_TICK)
        } else {
            vibrate(VibrationEffect.EFFECT_CLICK)
        }
    }
}

