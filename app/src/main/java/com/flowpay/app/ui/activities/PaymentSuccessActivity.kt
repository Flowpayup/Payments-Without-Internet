package com.flowpay.app.ui.activities

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.flowpay.app.MainActivity
import com.flowpay.app.R
import com.flowpay.app.helpers.TransactionDetector
import com.flowpay.app.helpers.AudioStateManager
import java.text.SimpleDateFormat
import java.util.*

class PaymentSuccessActivity : AppCompatActivity() {
    
    private lateinit var tickImageView: ImageView
    private lateinit var statusText: TextView
    private lateinit var amountText: TextView
    private lateinit var detailsCard: CardView
    private lateinit var bankNameText: TextView
    private lateinit var transactionIdText: TextView
    private lateinit var dateTimeText: TextView
    private lateinit var upiIdLayout: LinearLayout
    private lateinit var upiIdText: TextView
    private lateinit var recipientLayout: LinearLayout  // NEW
    private lateinit var recipientLabel: TextView      // NEW
    private lateinit var recipientText: TextView       // NEW
    private lateinit var doneButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set system UI to black theme
        setupSystemUI()
        
        setContentView(R.layout.activity_payment_success)
        
        // Ensure audio is restored when success screen appears
        restoreAudioIfNeeded()
        
        initViews()
        loadTransactionData()
        startAnimations()
    }
    
    private fun initViews() {
        tickImageView = findViewById(R.id.iv_success_tick)
        statusText = findViewById(R.id.tv_status)
        amountText = findViewById(R.id.tv_amount)
        detailsCard = findViewById(R.id.card_details)
        bankNameText = findViewById(R.id.tv_bank_name)
        transactionIdText = findViewById(R.id.tv_transaction_id)
        dateTimeText = findViewById(R.id.tv_date_time)
        upiIdLayout = findViewById(R.id.layout_upi_id)
        upiIdText = findViewById(R.id.tv_upi_id)
        recipientLayout = findViewById(R.id.layout_recipient)  // NEW
        recipientLabel = findViewById(R.id.tv_recipient_label) // NEW
        recipientText = findViewById(R.id.tv_recipient_name)   // NEW
        doneButton = findViewById(R.id.btn_done)
        
        // Initially hide views for animation
        tickImageView.alpha = 0f
        statusText.alpha = 0f
        amountText.alpha = 0f
        detailsCard.alpha = 0f
        doneButton.alpha = 0f
        
        doneButton.setOnClickListener {
            navigateToMain()
        }
    }
    
    private fun loadTransactionData() {
        val transactionId = intent.getStringExtra("transaction_id") ?: "N/A"
        val amount = intent.getStringExtra("amount") ?: "0"
        val status = intent.getStringExtra("status") ?: "UNKNOWN"
        val bankName = intent.getStringExtra("bank_name") ?: "Bank"
        val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())
        val upiId = intent.getStringExtra("upi_id")
        val transactionType = intent.getStringExtra("transaction_type") ?: "DEBIT"
        val recipientName = intent.getStringExtra("recipient_name")  // NEW
        val phoneNumber = intent.getStringExtra("phone_number")      // NEW
        
        // Get operation type from detector
        val detector = TransactionDetector.getInstance(this)
        val operationType = detector.getOperationType() ?: ""
        
        statusText.text = "Payment Successful"
        amountText.text = "₹${formatAmount(amount)}"
        
        // Handle recipient/sender display - UPDATED LOGIC
        when {
            !recipientName.isNullOrEmpty() -> {
                recipientLayout.visibility = View.VISIBLE
                recipientLabel.text = if (transactionType == "CREDIT") "Received from" else "Paid to"
                recipientText.text = recipientName
            }
            !phoneNumber.isNullOrEmpty() -> {
                recipientLayout.visibility = View.VISIBLE
                recipientLabel.text = if (transactionType == "CREDIT") "From" else "To"
                recipientText.text = phoneNumber
            }
            operationType == "UPI_123" && detector.getPhoneNumber() != null -> {
                recipientLayout.visibility = View.VISIBLE
                recipientLabel.text = "To"
                recipientText.text = detector.getPhoneNumber()
            }
            else -> {
                recipientLayout.visibility = View.GONE
            }
        }
        
        // Bank name - show if different from recipient
        bankNameText.text = bankName
        
        transactionIdText.text = transactionId
        dateTimeText.text = formatDateTime(timestamp)
        
        // Show UPI ID if available
        if (!upiId.isNullOrEmpty()) {
            upiIdLayout.visibility = View.VISIBLE
            upiIdText.text = upiId
        } else {
            upiIdLayout.visibility = View.GONE
        }
        
        // Set transaction type indicator color - payment amount always green
        amountText.setTextColor(ContextCompat.getColor(this, R.color.flowpay_green))
    }
    
    private fun formatAmount(amount: String): String {
        return try {
            val value = amount.toDouble()
            String.format(Locale.getDefault(), "%,.2f", value)
        } catch (e: Exception) {
            amount
        }
    }
    
    private fun formatDateTime(timestamp: Long): String {
        val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        return formatter.format(Date(timestamp))
    }
    
    private fun startAnimations() {
        // Animate tick with draw effect
        Handler(Looper.getMainLooper()).postDelayed({
            animateTickMark()
        }, 300)
        
        // Fade in status text
        Handler(Looper.getMainLooper()).postDelayed({
            statusText.animate()
                .alpha(1f)
                .setDuration(500)
                .start()
        }, 800)
        
        // Fade in amount
        Handler(Looper.getMainLooper()).postDelayed({
            amountText.animate()
                .alpha(1f)
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration(400)
                .withEndAction {
                    amountText.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200)
                        .start()
                }
                .start()
        }, 1200)
        
        // Slide up details card
        Handler(Looper.getMainLooper()).postDelayed({
            detailsCard.translationY = 100f
            detailsCard.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(500)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }, 1600)
        
        // Fade in done button
        Handler(Looper.getMainLooper()).postDelayed({
            doneButton.animate()
                .alpha(1f)
                .setDuration(500)
                .start()
        }, 2000)
    }
    
    private fun animateTickMark() {
        tickImageView.alpha = 1f
        
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 1000
        animator.interpolator = AccelerateDecelerateInterpolator()
        
        animator.addUpdateListener { animation ->
            val progress = animation.animatedValue as Float
            // Create custom tick drawing animation
            tickImageView.scaleX = progress
            tickImageView.scaleY = progress
            tickImageView.rotation = progress * 360f
        }
        
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                tickImageView.rotation = 0f
                // Pulse animation after tick completes
                tickImageView.animate()
                    .scaleX(1.2f)
                    .scaleY(1.2f)
                    .setDuration(200)
                    .withEndAction {
                        tickImageView.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(200)
                            .start()
                    }
                    .start()
            }
        })
        
        animator.start()
    }
    
    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
    
    private fun setupSystemUI() {
        // Make status bar and navigation bar black
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.BLACK
            window.navigationBarColor = Color.BLACK
        }
        
        // For Android 8.0 and above, ensure navigation bar buttons are visible on black background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val decorView = window.decorView
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv() and decorView.systemUiVisibility
        }
        
        // Hide status bar and navigation bar for immersive experience
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN)
        }
    }
    
    private fun restoreAudioIfNeeded() {
        // Get operation type
        val detector = TransactionDetector.getInstance(this)
        val operationType = detector.getOperationType()
        
        // If it was UPI 123 and audio is still muted, wait a bit then restore
        if (operationType == "UPI_123" && AudioStateManager.isCallAudioMuted()) {
            // Give user 3 seconds to end the call themselves
            Handler(Looper.getMainLooper()).postDelayed({
                if (AudioStateManager.isCallAudioMuted()) {
                    Log.d("PaymentSuccessActivity", "Restoring audio after delay")
                    AudioStateManager.restoreCallAudio(this)
                }
            }, 3000)
        }
    }
    
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setupSystemUI()
        }
    }
    
    override fun onBackPressed() {
        navigateToMain()
    }
}
