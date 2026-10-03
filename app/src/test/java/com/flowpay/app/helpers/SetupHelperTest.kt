// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.helpers

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SetupHelperTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val prefs
        get() = context.getSharedPreferences("FlowpayPrefs", Context.MODE_PRIVATE)

    @Test
    fun `isPrimarySimUssdCapable reports false for jio and true for gsm carriers`() {
        prefs.edit().putString("selected_primary_sim", "jio").commit()
        assertFalse(SetupHelper.isPrimarySimUssdCapable(context))

        prefs.edit().putString("selected_primary_sim", "airtel").commit()
        assertTrue(SetupHelper.isPrimarySimUssdCapable(context))

        prefs.edit().putString("selected_primary_sim", "vodafone").commit()
        assertTrue(SetupHelper.isPrimarySimUssdCapable(context))
    }

    @Test
    fun `scan to pay is not blocked on jio because 123pay ivr fallback is supported`() {
        prefs.edit().putString("selected_primary_sim", "jio").commit()

        assertNull(SetupHelper.getScanToPayBlockedMessage(context))
    }

    @Test
    fun `scan to pay ussd is disabled when user reported ussd not working`() {
        prefs.edit().putString("selected_primary_sim", "airtel").commit()
        SetupHelper.setUserReportedUssdNotWorking(context, true)

        assertFalse(SetupHelper.isScanToPayUssdAvailable(context))
        org.junit.Assert.assertNotNull(SetupHelper.getScanToPayBlockedMessage(context))
    }
}
