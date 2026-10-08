// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flowpay.app.FlowpayApplication
import com.flowpay.app.helpers.TransactionDetector
import com.flowpay.app.receivers.SmsIngestionPipeline
import com.flowpay.app.repository.TransactionRepository
import com.flowpay.app.states.PaymentState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentHashMap

@RunWith(AndroidJUnit4::class)
class SmsReceiptPipelineTest {
    @Test
    fun closedQrFlowAcceptsDelayedReceiptOnItsExistingRowExactlyOnce() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = FlowpayApplication.from(context)!!.paymentSessionManager
        val detector = TransactionDetector.getInstance(context)
        val repository = TransactionRepository.getInstance(context)
        val id = manager.begin("", "500", "fixture@okaxis", TransactionSource.QR)
        val broadcasts = LocalBroadcastManager.getInstance(context)
        val eventOwners = ConcurrentHashMap<String, String>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                val owner = intent.getStringExtra("transaction_id") ?: return
                eventOwners[action] = owner
            }
        }
        val filter = IntentFilter("DISMISS_OVERLAY").apply { addAction("com.flowpay.app.SMS_RECEIVED") }
        broadcasts.registerReceiver(receiver, filter)
        try {
            assertTrue(manager.awaitPending(id))
            detector.startOperation("QR_SCAN", "500", sessionTxnId = id, vpa = "fixture@okaxis")
            assertTrue(manager.markHandoff(id))
            manager.onUserCancelled(id)
            val body = "Rs 700 sent to fixture@okaxis via UPI ref 001233440091 -HDFC Bank"
            assertTrue(detector.tryClaimSms(body))
            SmsIngestionPipeline.ingest(context, detector, "VM-HDFCBK", body)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(id, eventOwners["DISMISS_OVERLAY"])
            assertEquals(id, eventOwners["com.flowpay.app.SMS_RECEIVED"])
            val row = repository.getTransactionById(id)!!
            assertEquals(TransactionStatus.SUCCESS, row.status)
            assertEquals("700", row.amount)
            assertEquals("001233440091", row.bankRef)
            assertEquals("fixture@okaxis", row.upiId)
            assertTrue(manager.paymentState.value is PaymentState.Success)
            assertFalse(detector.shouldProcessSMS())
            assertFalse(detector.tryClaimSms(body))
        } finally {
            broadcasts.unregisterReceiver(receiver)
            detector.completeOperation(id)
            manager.acknowledgeResult()
            repository.deleteTransactionById(id)
        }
    }
}
