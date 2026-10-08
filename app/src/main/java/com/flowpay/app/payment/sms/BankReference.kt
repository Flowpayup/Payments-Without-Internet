// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.payment.sms

/** Shared receipt formatting, including values stored before references had their own field. */
object BankReference {
    fun display(value: String?): String? {
        val reference = value?.replace(Regex("_[0-9]{10,13}$"), "") ?: return null
        return reference.takeIf {
            it.matches(Regex("[A-Za-z0-9]{6,35}")) && it.any(Char::isDigit) &&
                !it.matches(Regex("TXN[0-9]{10,}", RegexOption.IGNORE_CASE)) &&
                !it.matches(Regex("X{2,}[0-9]*", RegexOption.IGNORE_CASE))
        }
    }
}
