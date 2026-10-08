// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.payment

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Intent
import android.view.View
import android.widget.TextView
import com.flowpay.app.features.qr_scanner.presentation.QRScannerActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 35], application = Application::class)
class QRReceiptOwnershipTest {
    @Test
    fun `old missing and unowned events cannot close a newer active scanner`() {
        val activity = activeScanner("new-payment")
        val status = field(activity, "tvStatus") as TextView
        status.text = "Waiting for new payment"
        val sms = field(activity, "smsReceiver") as BroadcastReceiver
        val overlay = field(activity, "overlayDismissReceiver") as BroadcastReceiver

        listOf(null, "old-payment").forEach { owner ->
            sms.onReceive(activity, event("com.flowpay.app.SMS_RECEIVED", owner))
            overlay.onReceive(activity, event("DISMISS_OVERLAY", owner))
            assertFalse(activity.isFinishing)
            assertEquals("Waiting for new payment", status.text.toString())
        }
        setField(activity, "ownedTxnId", null)
        sms.onReceive(activity, event("com.flowpay.app.SMS_RECEIVED", "new-payment"))
        overlay.onReceive(activity, event("DISMISS_OVERLAY", "new-payment"))
        assertFalse(activity.isFinishing)
        assertEquals("Waiting for new payment", status.text.toString())
    }

    @Test
    fun `matching receipt closes its active scanner with the existing result`() {
        val activity = activeScanner("new-payment")
        val sms = field(activity, "smsReceiver") as BroadcastReceiver

        sms.onReceive(activity, event("com.flowpay.app.SMS_RECEIVED", "new-payment"))

        assertTrue(activity.isFinishing)
        assertEquals(QRScannerActivity.RESULT_SUCCESS, shadowOf(activity).resultCode)
    }

    /** Attach the real activity without starting its camera or permission flow. */
    private fun activeScanner(owner: String): QRScannerActivity {
        val activity = Robolectric.buildActivity(QRScannerActivity::class.java).get()
        setField(activity, "ownedTxnId", owner)
        setField(activity, "isUSSDProcessActive", true)
        setField(activity, "tvStatus", TextView(activity))
        listOf("instructionsBox", "btnTerminate", "topBar", "instructionsHeaderInitial", "instructionsExpanded")
            .forEach { setField(activity, it, View(activity)) }
        return activity
    }

    private fun event(action: String, owner: String?): Intent =
        Intent(action).apply { owner?.let { putExtra("transaction_id", it) } }

    private fun field(activity: QRScannerActivity, name: String): Any? =
        QRScannerActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(activity)

    private fun setField(activity: QRScannerActivity, name: String, value: Any?) {
        QRScannerActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.set(activity, value)
    }
}
