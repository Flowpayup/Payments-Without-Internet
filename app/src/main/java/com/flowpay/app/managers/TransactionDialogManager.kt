package com.flowpay.app.managers

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.flowpay.app.R

/**
 * Manages transaction-related dialogs based on payment session outcomes.
 *
 * When constructed with a Service context the dialog window type is set to
 * TYPE_APPLICATION_OVERLAY so the dialog can actually display over the
 * dialer (a plain AlertDialog from a Service throws BadTokenException —
 * previously these silently degraded to toasts).
 *
 * [onAnyDismiss] fires whenever the user dismisses a result dialog, letting
 * the caller acknowledge the payment session so the result isn't re-shown.
 */
class TransactionDialogManager(
    private val context: Context,
    private val onAnyDismiss: (() -> Unit)? = null
) {

    companion object {
        private const val TAG = "TransactionDialogManager"
    }

    private fun AlertDialog.prepareForOverlayDisplay(): AlertDialog {
        if (context !is Activity) {
            window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        }
        return this
    }

    private fun dismissed(which: String) {
        Log.d(TAG, "$which dialog dismissed")
        onAnyDismiss?.invoke()
    }

    /**
     * Show dialog when user cancels the transaction by ending the call early
     */
    fun showTransactionCancelled() {
        try {
            AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.dialog_cancelled_title))
                .setMessage(context.getString(R.string.dialog_cancelled_message))
                .setPositiveButton(context.getString(R.string.action_ok)) { dialog, _ ->
                    dialog.dismiss()
                    dismissed("Transaction cancelled")
                }
                .setCancelable(false)
                .create()
                .prepareForOverlayDisplay()
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show transaction cancelled dialog: ${e.message}")
            Toast.makeText(context, "Transaction cancelled", Toast.LENGTH_LONG).show()
            onAnyDismiss?.invoke()
        }
    }

    /**
     * Show dialog when user cancels the transaction using the terminate button
     */
    fun showTransactionCancelledByUser() {
        try {
            AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.dialog_cancelled_title))
                .setMessage(context.getString(R.string.dialog_cancelled_by_user_message))
                .setPositiveButton(context.getString(R.string.action_ok)) { dialog, _ ->
                    dialog.dismiss()
                    dismissed("Transaction cancelled by user")
                }
                .setCancelable(false)
                .create()
                .prepareForOverlayDisplay()
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show transaction cancelled by user dialog: ${e.message}")
            Toast.makeText(context, "The transaction was cancelled", Toast.LENGTH_LONG).show()
            onAnyDismiss?.invoke()
        }
    }

    /**
     * Show dialog when the bank SMS confirms the transaction
     */
    fun showTransactionCompleted() {
        try {
            // Create custom dialog with Flowpay design
            val dialogBuilder = AlertDialog.Builder(context)
            val inflater = LayoutInflater.from(context)
            val dialogView = inflater.inflate(R.layout.dialog_transaction_success, null)

            val dialog = dialogBuilder
                .setView(dialogView)
                .setCancelable(false)
                .create()
                .prepareForOverlayDisplay()

            // Make dialog background transparent and rounded
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            val successIcon = dialogView.findViewById<ImageView>(R.id.iv_success_icon)
            successIcon?.setImageResource(R.drawable.ic_success_check)
            successIcon?.setColorFilter(context.getColor(R.color.white))

            val titleText = dialogView.findViewById<TextView>(R.id.tv_success_title)
            titleText?.text = context.getString(R.string.dialog_success_title)

            val messageText = dialogView.findViewById<TextView>(R.id.tv_success_message)
            messageText?.text = context.getString(R.string.dialog_success_message)

            val doneButton = dialogView.findViewById<Button>(R.id.btn_done)
            doneButton?.setOnClickListener {
                dialog.dismiss()
                dismissed("Transaction completed")
            }

            dialog.show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show transaction completed dialog: ${e.message}")
            try {
                AlertDialog.Builder(context)
                    .setTitle(context.getString(R.string.dialog_success_title))
                    .setMessage(context.getString(R.string.dialog_success_message))
                    .setPositiveButton(context.getString(R.string.action_great)) { dialog, _ ->
                        dialog.dismiss()
                        dismissed("Transaction completed (fallback)")
                    }
                    .setCancelable(false)
                    .create()
                    .prepareForOverlayDisplay()
                    .show()
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to show fallback dialog: ${e2.message}")
                Toast.makeText(context, "Transaction completed successfully!", Toast.LENGTH_LONG).show()
                onAnyDismiss?.invoke()
            }
        }
    }

    /**
     * Show dialog when the bank SMS reports the transaction failed
     */
    fun showTransactionFailed() {
        try {
            AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.dialog_failed_title))
                .setMessage(context.getString(R.string.dialog_failed_message))
                .setPositiveButton(context.getString(R.string.action_ok)) { dialog, _ ->
                    dialog.dismiss()
                    dismissed("Transaction failed")
                }
                .setCancelable(false)
                .create()
                .prepareForOverlayDisplay()
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show transaction failed dialog: ${e.message}")
            Toast.makeText(context, "Transaction failed. Please try again.", Toast.LENGTH_LONG).show()
            onAnyDismiss?.invoke()
        }
    }

    /**
     * Call finished normally; the request was sent and the bank's
     * confirmation (callback call + SMS) is still on its way.
     */
    fun showAwaitingConfirmation() {
        try {
            AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.dialog_awaiting_title))
                .setMessage(context.getString(R.string.dialog_awaiting_message))
                .setPositiveButton(context.getString(R.string.action_got_it)) { dialog, _ ->
                    dialog.dismiss()
                    Log.d(TAG, "Awaiting confirmation dialog dismissed")
                    // Deliberately NOT acknowledging: the session is still waiting for the SMS.
                }
                .setCancelable(false)
                .create()
                .prepareForOverlayDisplay()
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show awaiting confirmation dialog: ${e.message}")
            Toast.makeText(context, "Request sent — waiting for bank confirmation", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * No confirmation SMS arrived before the deadline. Honest outcome:
     * the payment may or may not have gone through.
     */
    fun showUnverified() {
        try {
            AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.dialog_unverified_title))
                .setMessage(context.getString(R.string.dialog_unverified_message))
                .setPositiveButton(context.getString(R.string.action_ok)) { dialog, _ ->
                    dialog.dismiss()
                    dismissed("Unverified")
                }
                .setCancelable(false)
                .create()
                .prepareForOverlayDisplay()
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show unverified dialog: ${e.message}")
            Toast.makeText(context, "No bank confirmation received — check your bank", Toast.LENGTH_LONG).show()
            onAnyDismiss?.invoke()
        }
    }

    /**
     * Show a custom dialog with specific title and message
     */
    fun showCustomDialog(title: String, message: String, positiveButton: String = "OK") {
        try {
            AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(positiveButton) { dialog, _ ->
                    dialog.dismiss()
                    Log.d(TAG, "Custom dialog dismissed: $title")
                }
                .setCancelable(false)
                .create()
                .prepareForOverlayDisplay()
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show custom dialog: ${e.message}")
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}
