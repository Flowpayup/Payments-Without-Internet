// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.payment

import android.Manifest
import android.app.Application
import android.content.Context
import android.net.Uri
import android.telecom.TelecomManager
import com.flowpay.app.constants.AppConstants
import com.flowpay.app.managers.CallManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowTelecomManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 35], application = Application::class)
class Upi123CallHandoffTest {
    private val application: Application = RuntimeEnvironment.getApplication()
    private val telecomManager = application.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
    private val telecom: ShadowTelecomManager = shadowOf(telecomManager)

    @Before
    fun grantCallPermission() {
        shadowOf(application).grantPermissions(Manifest.permission.CALL_PHONE)
        telecom.setCallPhonePermission(true)
    }

    @Test
    fun `system telecom receives unchanged IVR URI without choosing an outgoing account`() {
        val recipient = "+91 98765 43210"
        val amount = "1250"
        val built = Upi123CallStringBuilder.build(AppConstants.DEFAULT_UPI_SERVICE_NUMBER, recipient, amount)
        assertTrue(built is Upi123CallStringBuilder.Result.Valid)
        val expectedUri = (built as Upi123CallStringBuilder.Result.Valid).callString
        assertEquals("tel:08045163666,,1,9876543210,,1250,,1", expectedUri)

        assertTrue(CallManager(application).initiateUPI123Call(recipient, amount))

        val call = telecom.onlyOutgoingCall
        assertEquals(Uri.parse(expectedUri), call.address)
        assertTrue(call.extras.isEmpty)
        assertNull(call.phoneAccount)
        assertNull(shadowOf(application).nextStartedActivity)
    }

    @Test
    fun `denied call permission never leaves the app`() {
        shadowOf(application).denyPermissions(Manifest.permission.CALL_PHONE)
        telecom.setCallPhonePermission(false)

        assertFalse(CallManager(application).initiateUPI123Call("9876543210", "1250"))

        assertTrue(telecom.allOutgoingCalls.isEmpty())
        assertNull(shadowOf(application).nextStartedActivity)
    }
}
