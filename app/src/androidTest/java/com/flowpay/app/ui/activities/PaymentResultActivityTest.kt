// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.ui.activities

import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flowpay.app.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaymentResultActivityTest {
    @Test
    fun bankReferenceAndRetainedPhoneAreRenderedRatherThanInternalUuid() {
        val intent = Intent(
            InstrumentationRegistry.getInstrumentation().targetContext,
            PaymentResultActivity::class.java
        )
            .putExtra("transaction_id", "de204bb6-45a0-4660-8d71-ffed0de10100")
            .putExtra("bank_ref", "001233440091")
            .putExtra("phone_number", "9876543210")
            .putExtra("amount", "150")
            .putExtra("status", "SUCCESS")
        ActivityScenario.launch<PaymentResultActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals("001233440091", activity.findViewById<TextView>(R.id.tv_transaction_id).text.toString())
                assertEquals("9876543210", activity.findViewById<TextView>(R.id.tv_recipient_name).text.toString())
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.layout_recipient).visibility)
            }
        }
    }

    @Test
    fun missingBankReferenceShowsUnavailable() {
        val intent = Intent(
            InstrumentationRegistry.getInstrumentation().targetContext,
            PaymentResultActivity::class.java
        )
            .putExtra("transaction_id", "de204bb6-45a0-4660-8d71-ffed0de10100")
            .putExtra("status", "SUCCESS")
        ActivityScenario.launch<PaymentResultActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(
                    activity.getString(R.string.bank_reference_unavailable),
                    activity.findViewById<TextView>(R.id.tv_transaction_id).text.toString()
                )
            }
        }
    }
}
