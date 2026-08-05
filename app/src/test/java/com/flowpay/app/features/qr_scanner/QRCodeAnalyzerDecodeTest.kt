// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.features.qr_scanner

import android.graphics.Bitmap
import com.flowpay.app.features.qr_scanner.domain.QRCodeAnalyzer
import com.flowpay.app.features.qr_scanner.domain.QRCodeParser
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Closes the "parsing tested, decoding never" gap left by the ML Kit -> ZXing
 * swap: encodes the same UPI URIs [QRCodeParserTest] already covers into real
 * QR bitmaps, decodes them through [QRCodeAnalyzer.decodeBitmap] — the same
 * pipeline the gallery-scan path uses — and confirms the full
 * encode -> decode -> parse round trip.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QRCodeAnalyzerDecodeTest {

    private fun encodeToBitmap(text: String, size: Int = 300): Bitmap {
        val matrix: BitMatrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix[x, y]) BLACK else WHITE)
            }
        }
        return bitmap
    }

    @Test
    fun `decodes an encoded full npci uri`() {
        val original = "upi://pay?pa=merchant@okhdfcbank&pn=Test%20Store&am=150.00&tn=Bill%20payment"

        val decoded = QRCodeAnalyzer.decodeBitmap(encodeToBitmap(original))

        assertEquals(original, decoded)
    }

    @Test
    fun `decodes a uri with no amount`() {
        val original = "upi://pay?pa=a.b@ybl&pn=Someone"

        val decoded = QRCodeAnalyzer.decodeBitmap(encodeToBitmap(original))

        assertEquals(original, decoded)
    }

    @Test
    fun `decodes a bare vpa qr`() {
        val original = "someone@paytm"

        val decoded = QRCodeAnalyzer.decodeBitmap(encodeToBitmap(original))

        assertEquals(original, decoded)
    }

    @Test
    fun `decoded text round-trips through QRCodeParser as valid`() {
        val original = "upi://pay?pa=merchant@okhdfcbank&pn=Test%20Store&am=150.00"
        val decoded = QRCodeAnalyzer.decodeBitmap(encodeToBitmap(original))
        requireNotNull(decoded)

        val result = QRCodeParser.parse(decoded)

        assertTrue("expected Valid for decoded text: $decoded", result is QRCodeParser.ParseResult.Valid)
    }

    @Test
    fun `blank image decodes to null rather than throwing`() {
        val blank = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        blank.eraseColor(WHITE)

        assertNull(QRCodeAnalyzer.decodeBitmap(blank))
    }

    private companion object {
        const val BLACK = 0xFF000000.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
    }
}
