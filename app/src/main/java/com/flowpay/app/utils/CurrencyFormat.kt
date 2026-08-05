// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.utils

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * One place that decides how a rupee amount is grouped.
 *
 * The same ₹1,00,000 used to render three different ways depending on which
 * surface showed it (observed on a real device, 2026-08-01):
 *  - history/home/detail  `₹1,00,000.00`  — correct
 *  - payment result screen `₹100,000.00`  — `String.format("%,.2f")`
 *  - result notification   `₹100000.00`   — raw string interpolation
 *
 * `String.format("%,…")` is the trap: its `,` flag takes only the *separator*
 * from the locale and always groups in threes, so it cannot produce the
 * Indian 3-2-2 pattern no matter which Locale is passed.
 *
 * The grouping is implemented here rather than delegated to
 * `NumberFormat.getInstance(en-IN)`, which looks like the obvious answer and
 * is not one. On Android that call is backed by ICU and does produce
 * `1,00,000.00`; on a plain JVM — which is where the unit tests run — the
 * JDK's own locale data groups `en-IN` in threes and returns `100,000.00`.
 * Verified on JDK 17: `getInstance`, `getCurrencyInstance`, and even `hi-IN`
 * all group in threes. Leaving the rule to platform locale data therefore
 * meant the one thing worth pinning could not be tested at all, and could
 * shift with an OEM's ICU build. `java.text.DecimalFormat` is no escape
 * either — it supports a single grouping size, so the pattern `#,##,##0.00`
 * silently degrades to `1,234,567.00`.
 *
 * Twelve lines of explicit grouping are cheaper than that, and they behave
 * identically on every device and in CI. India-only is a deliberate product
 * constraint here, not an oversight: see the README.
 *
 * Returns digits only — callers supply the ₹ through
 * `R.string.amount_rupees`, so the symbol has one source too.
 */
object CurrencyFormat {

    /** Digits in the rightmost group: ₹1,00,**000**. */
    private const val LAST_GROUP = 3

    /** Digits in every group above it: ₹**12**,**34**,567. */
    private const val HIGHER_GROUPS = 2

    private const val FRACTION_DIGITS = 2

    /**
     * Groups [amount] Indian-style. Returns it unchanged when it isn't a
     * number — a bank template this parser didn't fully understand must still
     * show the user whatever it did capture, never an error, a blank, or a
     * fabricated `0.00`.
     */
    fun inr(amount: String): String {
        val value = amount.toBigDecimalOrNull() ?: return amount
        return format(value)
    }

    /** Same grouping for callers that already hold a number. */
    fun inr(amount: Double): String = format(BigDecimal.valueOf(amount))

    private fun format(value: BigDecimal): String {
        // HALF_EVEN matches what NumberFormat did before, so no displayed
        // amount changes at the rounding boundary.
        val rounded = value.setScale(FRACTION_DIGITS, RoundingMode.HALF_EVEN)
        val sign = if (rounded.signum() < 0) "-" else ""
        val (whole, fraction) = rounded.abs().toPlainString().split(".")
        return "$sign${groupIndian(whole)}.$fraction"
    }

    /** `1234567` -> `12,34,567`. Three at the end, then twos. */
    private fun groupIndian(digits: String): String {
        if (digits.length <= LAST_GROUP) return digits

        val groups = ArrayDeque<String>()
        groups.addFirst(digits.takeLast(LAST_GROUP))

        var rest = digits.dropLast(LAST_GROUP)
        while (rest.length > HIGHER_GROUPS) {
            groups.addFirst(rest.takeLast(HIGHER_GROUPS))
            rest = rest.dropLast(HIGHER_GROUPS)
        }
        if (rest.isNotEmpty()) groups.addFirst(rest)

        return groups.joinToString(",")
    }
}
