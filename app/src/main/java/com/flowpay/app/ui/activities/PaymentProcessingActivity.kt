package com.flowpay.app.ui.activities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.flowpay.app.R
import com.flowpay.app.models.TransactionData

class PaymentProcessingActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "PaymentProcessingActivity"
        private const val PROCESSING_TIMEOUT = 30000L // 30 seconds timeout
    }
    
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private var timeoutHandler: Handler? = null
    private var timeoutRunnable: Runnable? = null
    
    private val smsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
                Log.d(TAG, "SMS received during processing")
                handleSmsReceived(intent!!)
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment_processing)
        
        Log.d(TAG, "PaymentProcessingActivity created")
        
        // Make fullscreen with black background
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        
        initializeViews()
        startProcessing()
        registerSmsReceiver()
        startTimeout()
    }
    
    private fun initializeViews() {
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
        
        Log.d(TAG, "Views initialized")
    }
    
    private fun startProcessing() {
        Log.d(TAG, "Starting payment processing")
        
        // Start circular progress animation
        startProgressAnimation()
        
        // Update status text
        statusText.text = "Processing Payment..."
    }
    
    private fun startProgressAnimation() {
        // Create a simple rotation animation for the progress bar
        progressBar.animate()
            .rotation(360f)
            .setDuration(1000)
            .withEndAction {
                if (!isFinishing) {
                    startProgressAnimation() // Loop the animation
                }
            }
            .start()
    }
    
    private fun startTimeout() {
        Log.d(TAG, "Starting processing timeout")
        
        timeoutHandler = Handler(Looper.getMainLooper())
        timeoutRunnable = Runnable {
            Log.d(TAG, "Processing timeout reached")
            handleTimeout()
        }
        
        timeoutHandler?.postDelayed(timeoutRunnable!!, PROCESSING_TIMEOUT)
    }
    
    private fun handleSmsReceived(intent: Intent) {
        Log.d(TAG, "Handling SMS received - transaction completed")
        
        // Cancel timeout
        timeoutRunnable?.let {
            timeoutHandler?.removeCallbacks(it)
        }
        
        // Get transaction data from intent extras
        val transactionData = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("transaction_data", TransactionData::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<TransactionData>("transaction_data")
        }
        
        if (transactionData != null) {
            navigateToSuccess(transactionData)
        } else {
            Log.w(TAG, "No transaction data in SMS")
            navigateToSuccess(null)
        }
    }
    
    private fun handleTimeout() {
        Log.d(TAG, "Processing timeout - showing error")
        
        // Show timeout message
        statusText.text = "Payment processing timed out"
        
        // Navigate back after delay
        Handler(Looper.getMainLooper()).postDelayed({
            finish()
        }, 2000)
    }
    
    private fun navigateToSuccess(transactionData: TransactionData?) {
        Log.d(TAG, "Showing success dialog")
        
        try {
            // Use TransactionDialogManager to show success dialog
            val dialogManager = com.flowpay.app.managers.TransactionDialogManager(this)
            dialogManager.showTransactionCompleted()
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing success dialog", e)
            finish()
        }
    }
    
    private fun registerSmsReceiver() {
        Log.d(TAG, "Registering SMS receiver")
        val filter = IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
        LocalBroadcastManager.getInstance(this).registerReceiver(smsReceiver, filter)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "PaymentProcessingActivity destroyed")
        
        // Cancel timeout
        timeoutRunnable?.let {
            timeoutHandler?.removeCallbacks(it)
        }
        
        // Unregister receiver
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(smsReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering SMS receiver", e)
        }
    }
}