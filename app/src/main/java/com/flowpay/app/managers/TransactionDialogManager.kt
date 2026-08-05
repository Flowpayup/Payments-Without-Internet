// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.managers

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.util.Log
import android.view.WindowManager
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
            Log.e(TAG, "Failed to show transaction cancelled dialog", e)
            Toast.makeText(context, R.string.toast_payment_cancelled, Toast.LENGTH_LONG).show()
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
            Log.e(TAG, "Failed to show transaction cancelled by user dialog", e)
            Toast.makeText(context, R.string.toast_payment_cancelled, Toast.LENGTH_LONG).show()
            onAnyDismiss?.invoke()
        }
    }

    // There is deliberately no success dialog here. PaymentResultActivity —
    // launched by the SMS ingestion pipeline, and backed by a notification
    // when the direct launch is blocked — is the only success surface, so a
    // second one from this service would either duplicate it or race it.

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
            Log.e(TAG, "Failed to show transaction failed dialog", e)
            Toast.makeText(context, R.string.toast_payment_failed, Toast.LENGTH_LONG).show()
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
            Log.e(TAG, "Failed to show awaiting confirmation dialog", e)
            Toast.makeText(context, R.string.toast_awaiting_confirmation, Toast.LENGTH_LONG).show()
        }
    }
}
