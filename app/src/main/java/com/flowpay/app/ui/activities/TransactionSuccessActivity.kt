package com.flowpay.app.ui.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.flowpay.app.R
import com.flowpay.app.helpers.SimpleTransaction
import java.text.SimpleDateFormat
import java.util.*

class TransactionSuccessActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "TransactionSuccessActivity"
        const val EXTRA_TRANSACTION = "transaction"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_success)
        
        Log.d(TAG, "=== TransactionSuccessActivity CREATED ===")
        Log.d(TAG, "Intent extras: ${intent.extras}")
        
        // Get transaction data from intent
        val transaction = intent.getParcelableExtra<SimpleTransaction>(EXTRA_TRANSACTION)
        
        if (transaction != null) {
            Log.d(TAG, "Transaction data received (status: ${transaction.status})")
            displayTransactionDetails(transaction)
        } else {
            Log.e(TAG, "❌ No transaction data received - finishing activity")
            finish()
            return
        }
        
        setupButtons()
        Log.d(TAG, "TransactionSuccessActivity setup completed")
    }
    
    private fun displayTransactionDetails(transaction: SimpleTransaction) {
        // Set success icon - tick in white
        val successIcon = findViewById<ImageView>(R.id.iv_success_icon)
        successIcon?.setColorFilter(ContextCompat.getColor(this, R.color.white))
        
        // Set transaction ID
        val transactionIdText = findViewById<TextView>(R.id.tv_transaction_id)
        transactionIdText?.text = "Transaction ID: ${transaction.transactionId}"
        
        // Set amount
        val amountText = findViewById<TextView>(R.id.tv_amount)
        amountText?.text = "₹${transaction.amount}"
        
        // Set bank name
        val bankText = findViewById<TextView>(R.id.tv_bank_name)
        bankText?.text = transaction.bankName
        
        // Set status
        val statusText = findViewById<TextView>(R.id.tv_status)
        statusText?.text = transaction.status
        statusText?.setTextColor(ContextCompat.getColor(this, R.color.transaction_primary))
        
        // Set timestamp
        val timestampText = findViewById<TextView>(R.id.tv_timestamp)
        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        timestampText?.text = dateFormat.format(Date(transaction.timestamp))
        
        // Privacy-safe bank summary (raw SMS bodies are not stored)
        val messageText = findViewById<TextView>(R.id.tv_sms_message)
        messageText?.text = transaction.smsExcerpt
    }
    
    private fun setupButtons() {
        // Done button - go back to main screen
        val doneButton = findViewById<Button>(R.id.btn_done)
        doneButton?.setOnClickListener {
            Log.d(TAG, "Done button clicked - returning to main screen")
            finish()
        }
        
        // View Details button - show full SMS message
        val viewDetailsButton = findViewById<Button>(R.id.btn_view_details)
        viewDetailsButton?.setOnClickListener {
            Log.d(TAG, "View details button clicked")
            showFullMessageDialog()
        }
    }
    
    private fun showFullMessageDialog() {
        val transaction = intent.getParcelableExtra<SimpleTransaction>(EXTRA_TRANSACTION)
        if (transaction != null) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Bank Confirmation")
                .setMessage(
                    "${transaction.smsExcerpt}\n\n" +
                        "The original SMS is in your messages app."
                )
                .setPositiveButton("Close") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }
    
    override fun onBackPressed() {
        Log.d(TAG, "Back button pressed - returning to main screen")
        finish()
    }
}
