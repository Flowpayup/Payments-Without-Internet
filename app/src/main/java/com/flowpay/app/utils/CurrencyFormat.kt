// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.utils

import java.text.NumberFormat
import java.util.Locale

/**
 * One place that decides how a rupee amount is grouped.
 *
 * The same ₹1,00,000 used to render three different ways depending on which
 * surface showed it (observed on a real device, 2026-08-01):
 *  - history/home/detail  `₹1,00,000.00`  — correct, via NumberFormat(en-IN)
 *  - payment result screen `₹100,000.00`  — `String.format("%,.2f")`
 *  - result notification   `₹100000.00`   — raw string interpolation
 *
 * `String.format("%,…")` is the trap: its `,` flag takes only the *separator*
 * from the locale and always groups in threes, so it cannot produce the Indian
 * 3-2-2 pattern no matter which Locale is passed. `NumberFormat` for `en-IN`
 * carries the real grouping pattern, which is why the Compose screens were
 * already right.
 *
 * Returns digits only — callers supply the ₹ symbol, since some do it through
 * a string resource.
 */
object CurrencyFormat {

    private val INDIA = Locale("en", "IN")

    private val grouped: NumberFormat = NumberFormat.getInstance(INDIA).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    /**
     * Groups [amount] Indian-style. Returns it unchanged when it isn't a
     * number — a bank template this parser didn't fully understand must still
     * show the user whatever it did capture, never an error or a blank.
     */
    fun inr(amount: String): String {
        val value = amount.toDoubleOrNull() ?: return amount
        return synchronized(grouped) { grouped.format(value) }
    }
}
