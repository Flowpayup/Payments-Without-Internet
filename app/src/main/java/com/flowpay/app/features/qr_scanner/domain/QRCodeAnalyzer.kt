// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.features.qr_scanner.domain

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer

/**
 * ZXing-backed QR analyzer — the only barcode-decoding dependency in the app.
 * ZXing's QR detector locates the three finder patterns via a homography
 * search rather than a raster scan, so it decodes correctly regardless of
 * camera-sensor rotation; the Y-plane luminance data is used as-is.
 */
class QRCodeAnalyzer(
    private val onQRCodeDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader()
    private val hints: Map<DecodeHintType, Any> = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true
    )

    private var lastDetectedCode: String? = null
    private var lastDetectionTime = 0L
    private val duplicateDetectionThreshold = 2000L // 2 seconds

    @Volatile
    private var isProcessing = false

    // Guard clauses read clearer here than one accumulated boolean condition.
    @Suppress("ReturnCount")
    override fun analyze(imageProxy: ImageProxy) {
        try {
            if (isProcessing) {
                Log.d(TAG, "Already processing QR code, ignoring new detection")
                return
            }

            val qrCode = decode(imageProxy) ?: return
            val currentTime = System.currentTimeMillis()
            val isDuplicate = lastDetectedCode == qrCode &&
                (currentTime - lastDetectionTime) < duplicateDetectionThreshold
            if (isDuplicate) {
                Log.d(TAG, "Duplicate QR code detected, ignoring")
                return
            }

            Log.d(TAG, "QR Code detected")
            isProcessing = true
            lastDetectedCode = qrCode
            lastDetectionTime = currentTime
            onQRCodeDetected(qrCode)
        } finally {
            imageProxy.close()
        }
    }

    private fun decode(imageProxy: ImageProxy): String? {
        val source = imageProxy.toLuminanceSource() ?: return null
        // Try the frame as captured, then an inverted copy — handles
        // dark-QR-on-light-background and the light-on-dark inverse.
        return decodeSource(source) ?: decodeSource(source.invert())
    }

    // A camera frame is untrusted input arriving continuously; this screen
    // must never crash on a malformed one, so the fallback catch is broad.
    @Suppress("TooGenericExceptionCaught")
    private fun decodeSource(source: LuminanceSource): String? {
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        return try {
            reader.decode(bitmap, hints).text
        } catch (expected: ReaderException) {
            // No valid QR in this frame — the overwhelmingly common outcome
            // while the camera is idle, not an error.
            null
        } catch (e: Exception) {
            Log.w(TAG, "QR decode failed unexpectedly: ${e.message}")
            null
        }
    }

    // Guard clauses read clearer here than one accumulated boolean condition.
    @Suppress("ReturnCount")
    private fun ImageProxy.toLuminanceSource(): PlanarYUVLuminanceSource? {
        if (format != ImageFormat.YUV_420_888) return null
        val yPlane = planes.getOrNull(0) ?: return null
        // Camera2's Y plane is byte-per-pixel (pixelStride 1) on every
        // real device; bail rather than mis-decode on an exotic sensor.
        if (yPlane.pixelStride != 1) return null

        val buffer = yPlane.buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)

        return PlanarYUVLuminanceSource(
            data,
            yPlane.rowStride,
            height,
            0,
            0,
            width,
            height,
            false
        )
    }

    /**
     * Reset the processing flag to allow new QR code detection
     */
    fun resetProcessing() {
        isProcessing = false
        Log.d(TAG, "Processing flag reset - ready for new QR detection")
    }

    companion object {
        private const val TAG = "QRCodeAnalyzer"

        private val staticImageHints: Map<DecodeHintType, Any> = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true
        )

        /**
         * One-shot decode of a static image (e.g. a QR code picked from the
         * gallery) — same ZXing pipeline as the live camera analyzer, just
         * fed an RGB bitmap instead of a YUV camera frame.
         */
        @Suppress("TooGenericExceptionCaught") // gallery images are untrusted input; must never crash the picker flow
        fun decodeBitmap(bitmap: Bitmap): String? {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            return try {
                MultiFormatReader().decode(binaryBitmap, staticImageHints).text
            } catch (expected: ReaderException) {
                null
            } catch (e: Exception) {
                Log.w(TAG, "Gallery QR decode failed unexpectedly: ${e.message}")
                null
            }
        }
    }
}
