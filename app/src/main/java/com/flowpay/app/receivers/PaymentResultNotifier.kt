package com.flowpay.app.receivers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.flowpay.app.R
import com.flowpay.app.data.TransactionStatus

/**
 * Posts a payment outcome as a high-priority notification.
 *
 * The direct [com.flowpay.app.ui.activities.PaymentResultActivity] launch
 * from a background receiver/service relies on the background-activity-launch
 * exemption granted by the overlay permission; with that permission denied or
 * revoked, the launch is silently dropped and the user would never see their
 * outcome. The notification is posted ALONGSIDE the launch: tapping it opens
 * the same result screen, and when the direct launch did work it simply sits
 * in the shade as a receipt.
 *
 * Best-effort by design: on Android 13+ notifications additionally need the
 * POST_NOTIFICATIONS runtime grant; posting without it is a silent no-op,
 * never a crash.
 */
object PaymentResultNotifier {

    private const val TAG = "PaymentResultNotifier"
    private const val CHANNEL_ID = "payment_results"

    fun notifyResult(context: Context, resultIntent: Intent) {
        try {
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_channel_payment_results),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notif_channel_payment_results_desc)
                }
            )

            val status = resultIntent.getStringExtra("status") ?: ""
            val amount = resultIntent.getStringExtra("amount") ?: ""
            val bank = resultIntent.getStringExtra("bank_name") ?: ""
            val title = context.getString(statusTitleRes(status))
            val text = listOf("₹$amount", bank).filter { it.length > 1 }.joinToString(" — ")

            val contentIntent = PendingIntent.getActivity(
                context,
                // Distinct request code per payment so receipts don't overwrite
                // each other's intents.
                (resultIntent.getStringExtra("transaction_id") ?: "").hashCode(),
                resultIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = android.app.Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_check_circle)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .build()

            manager.notify((resultIntent.getStringExtra("transaction_id") ?: "").hashCode(), notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not post payment result notification: ${e.message}")
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Could not post payment result notification: ${e.message}")
        }
    }

    private fun statusTitleRes(status: String): Int = when (status.uppercase()) {
        TransactionStatus.FAILED -> R.string.payment_status_failed
        TransactionStatus.NEEDS_REVIEW -> R.string.payment_status_needs_review
        TransactionStatus.UNVERIFIED -> R.string.payment_status_unverified
        else -> R.string.payment_status_success
    }
}
