package com.flowpay.app.payment

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure validation extracted from CallManager. These guard the two user
 * inputs — recipient number and amount — before any dial is attempted.
 */
class PaymentInputValidatorTest {

    @Test
    fun `valid 10-digit numbers are accepted`() {
        assertTrue(PaymentInputValidator.isValidPhoneNumber("9876543210"))
        assertTrue(PaymentInputValidator.isValidPhoneNumber("1234567890"))
    }

    @Test
    fun `phone numbers with a leading zero are rejected`() {
        assertFalse(PaymentInputValidator.isValidPhoneNumber("0123456789"))
    }

    @Test
    fun `phone numbers of the wrong length are rejected`() {
        assertFalse(PaymentInputValidator.isValidPhoneNumber("98765"))
        assertFalse(PaymentInputValidator.isValidPhoneNumber("98765432109"))
    }

    @Test
    fun `non-numeric, blank, and null phone numbers are rejected`() {
        assertFalse(PaymentInputValidator.isValidPhoneNumber("98765abcde"))
        assertFalse(PaymentInputValidator.isValidPhoneNumber("98765 4321"))
        assertFalse(PaymentInputValidator.isValidPhoneNumber(""))
        assertFalse(PaymentInputValidator.isValidPhoneNumber(null))
    }

    @Test
    fun `amounts within the range are accepted`() {
        assertTrue(PaymentInputValidator.isValidAmount("1"))
        assertTrue(PaymentInputValidator.isValidAmount("500"))
        assertTrue(PaymentInputValidator.isValidAmount("100000"))
        assertTrue(PaymentInputValidator.isValidAmount("99.99"))
    }

    @Test
    fun `amounts below the minimum or above the ceiling are rejected`() {
        assertFalse(PaymentInputValidator.isValidAmount("0"))
        assertFalse(PaymentInputValidator.isValidAmount("0.5"))
        assertFalse(PaymentInputValidator.isValidAmount("100001"))
    }

    @Test
    fun `non-numeric, blank, and null amounts are rejected`() {
        assertFalse(PaymentInputValidator.isValidAmount("abc"))
        assertFalse(PaymentInputValidator.isValidAmount(""))
        assertFalse(PaymentInputValidator.isValidAmount(null))
    }
}
