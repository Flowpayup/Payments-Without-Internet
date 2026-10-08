// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.payment

import android.content.ClipData
import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VpaClipboardTest {
    @Test
    fun `owned VPA is cleared while newer text scanner copy and non-text clips survive`() {
        val vpa = "shop@okaxis"
        assertTrue(ownsVpaClip(ClipData.newPlainText("Flowpay VPA first", vpa), vpa, "first"))
        assertFalse(ownsVpaClip(ClipData.newPlainText("user copy", "new text"), vpa, "first"))
        assertFalse(ownsVpaClip(ClipData.newPlainText("Flowpay VPA second", vpa), vpa, "first"))
        assertFalse(ownsVpaClip(ClipData.newIntent("Flowpay VPA first", Intent()), vpa, "first"))
        assertFalse(ownsVpaClip(ClipData.newPlainText("Flowpay VPA first", vpa), null, null))
    }
}
