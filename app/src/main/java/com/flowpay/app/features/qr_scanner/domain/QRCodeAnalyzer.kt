package com.flowpay.app.features.qr_scanner.domain

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage

class QRCodeAnalyzer(
    private val onQRCodeDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {
    
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )
    
    private var lastDetectedCode: String? = null
    private var lastDetectionTime = 0L
    private val duplicateDetectionThreshold = 2000L // 2 seconds
    @Volatile
    private var isProcessing = false
    
    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    barcodes.firstOrNull()?.rawValue?.let { qrCode ->
                        val currentTime = System.currentTimeMillis()
                        
                        // Check if already processing a QR code
                        if (isProcessing) {
                            Log.d("QRCodeAnalyzer", "Already processing QR code, ignoring new detection")
                            return@let
                        }
                        
                        // Check for duplicate detection
                        if (lastDetectedCode == qrCode && 
                            (currentTime - lastDetectionTime) < duplicateDetectionThreshold) {
                            Log.d("QRCodeAnalyzer", "Duplicate QR code detected, ignoring")
                            return@let
                        }
                        
                        Log.d("QRCodeAnalyzer", "QR Code detected: $qrCode")
                        isProcessing = true
                        lastDetectedCode = qrCode
                        lastDetectionTime = currentTime
                        onQRCodeDetected(qrCode)
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("QRCodeAnalyzer", "Barcode scanning failed", exception)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
    
    /**
     * Reset the processing flag to allow new QR code detection
     */
    fun resetProcessing() {
        isProcessing = false
        Log.d("QRCodeAnalyzer", "Processing flag reset - ready for new QR detection")
    }
}
