// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.payment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IndianMobileNumberTest {
    @Test
    fun `supported formats preserve the mobile destination`() {
        listOf("9876543210", "+91 (98765) 43210", "0091-9876543210", "919876543210")
            .forEach { assertEquals("9876543210", IndianMobileNumber.normalize(it)) }
    }

    @Test
    fun `ambiguous and foreign numbers cannot be turned into payees`() {
        listOf(
            "080 2345 6789", "044-2811-2345", "+1 415 555 0123", "09876543210",
            "1234567890", "9876543210 ext 1", "98765#43210", "++919876543210", "", "98765432101"
        ).forEach { assertNull(IndianMobileNumber.normalize(it)) }
    }
}
