// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.features.qr_scanner.presentation

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.flowpay.app.FlowpayApplication
import com.flowpay.app.R
import com.flowpay.app.data.TransactionSource
import com.flowpay.app.data.UPIData
import com.flowpay.app.features.qr_scanner.domain.QRCodeAnalyzer
import com.flowpay.app.features.qr_scanner.domain.QRCodeParser
import com.flowpay.app.helpers.SetupHelper
import com.flowpay.app.helpers.TransactionDetector
import com.flowpay.app.managers.PermissionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class QRScannerActivity : ComponentActivity() {

    companion object {
        const val RESULT_CANCELLED = 0
        const val RESULT_SUCCESS = 1
        const val RESULT_ERROR = 2

        /** How long the invalid-QR banner stays up before fading out. */
        private const val SCAN_ERROR_VISIBLE_MS = 3500L

        /** Longest edge a gallery image is downsampled to before decoding. */
        private const val GALLERY_MAX_EDGE_PX = 2048
    }

    private lateinit var viewFinder: PreviewView
    private lateinit var scannerOverlay: View
    private lateinit var progressBar: View
    private lateinit var tvStatus: TextView
    private lateinit var tvScanError: TextView
    private lateinit var btnBack: View
    private lateinit var btnTerminate: View
    private lateinit var topBar: View
    private lateinit var instructionsBox: View
    private lateinit var instructionsHeaderInitial: View
    private lateinit var instructionsExpanded: View
    private lateinit var btnFlash: ImageButton
    private lateinit var btnGallery: ImageButton
    private lateinit var bottomActionBar: View
    private lateinit var scanLine: View
    private var scanLineAnimator: android.animation.ObjectAnimator? = null
    private lateinit var cameraExecutor: ExecutorService
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var isFlashOn = false
    private lateinit var permissionManager: PermissionManager
    private var isUSSDProcessActive = false
    private var messageHandler: android.os.Handler? = null
    private var smsTimeoutHandler: android.os.Handler? = null
    private var smsTimeoutRunnable: Runnable? = null

    // Centralized handler for all delayed callbacks — cleaned up in onDestroy
    private val mainHandler = Handler(Looper.getMainLooper())

    // Guards against processing the same QR more than once per flow.
    @Volatile
    private var isProcessingQRCode = false

    // Ensures USSD is dialed once per payment flow — without this, returning
    // from the dialer re-triggers the dial and loops.
    @Volatile
    private var hasDialedUSSD = false

    // Whether this flow put a payee VPA on the clipboard (wiped in onDestroy).
    private var didCopyVpa = false

    // Permission launcher for runtime permissions
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        handlePermissionResult(permissions)
    }

    // Permission launcher for CALL_PHONE permission in dialUSSD flow
    private val callPhonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            dialUSSD()
        } else {
            showError("Phone call permission is required for USSD payments")
        }
    }

    /**
     * Android's photo picker. Deliberately NOT a storage permission: the
     * picker returns a one-shot read grant for the single image the user
     * chose, so the app never gains access to the gallery as a whole. That
     * keeps the "no storage permission" claim in README/ARCHITECTURE true.
     */
    private val galleryPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) {
            Log.d("QRScanner", "Gallery pick cancelled")
        } else {
            decodeQrFromGallery(uri)
        }
    }

    /**
     * Keeps the floating chrome clear of the system bars.
     *
     * On targetSdk 35 the window is edge-to-edge, and this activity applied no
     * insets at all — so the instructions card sat at y=0, underneath the
     * status bar and the display cutout, with its first line clipped. The
     * camera preview is deliberately left full-bleed; only the overlay chrome
     * is inset, so the viewfinder still fills the screen.
     */
    private fun applyWindowInsets() {
        val root = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, windowInsets ->
            val bars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            (instructionsBox.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                it.topMargin = bars.top + resources.getDimensionPixelSize(R.dimen.qr_chrome_gap)
                instructionsBox.layoutParams = it
            }
            (topBar.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                it.topMargin = bars.top
                topBar.layoutParams = it
            }
            (bottomActionBar.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                it.bottomMargin = bars.bottom +
                    resources.getDimensionPixelSize(R.dimen.qr_bottom_bar_gap)
                bottomActionBar.layoutParams = it
            }
            windowInsets
        }
    }

    /**
     * Check if the activity is still alive and safe to access views
     */
    private fun isActivityAlive(): Boolean = !isFinishing && !isDestroyed

    // Broadcast receiver for USSD overlay dismissal
    private val overlayDismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "DISMISS_OVERLAY") {
                Log.d("QRScanner", "USSD overlay dismissed - continuing to wait for SMS")
                if (isUSSDProcessActive) {
                    // Don't close immediately, wait for SMS
                    updateBlackScreenStatus("USSD completed! Waiting for transaction confirmation...")
                }
            }
        }
    }

    // Broadcast receiver for SMS detection
    private val smsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.flowpay.app.SMS_RECEIVED") {
                Log.d("QRScanner", "SMS received - transaction successful!")
                if (isUSSDProcessActive) {
                    handleSMSReceived(intent)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            Log.d("QRScanner", "=== QR SCANNER ACTIVITY CREATED ===")
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_qr_scanner)

            SetupHelper.getScanToPayBlockedMessage(this)?.let { msg ->
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                setResult(RESULT_CANCELLED)
                finish()
                return
            }

            // Initialize views
            viewFinder = findViewById(R.id.viewFinder)
            scannerOverlay = findViewById(R.id.scannerOverlay)
            progressBar = findViewById(R.id.progressBar)
            tvStatus = findViewById(R.id.tvStatus)
            tvScanError = findViewById(R.id.tvScanError)
            btnBack = findViewById(R.id.btnBack)
            btnTerminate = findViewById(R.id.btnTerminate)
            topBar = findViewById(R.id.topBar)
            instructionsBox = findViewById(R.id.instructionsBox)
            instructionsHeaderInitial = findViewById(R.id.instructionsHeaderInitial)
            instructionsExpanded = findViewById(R.id.instructionsExpanded)
            btnFlash = findViewById(R.id.btnFlash)
            btnGallery = findViewById(R.id.btnGallery)
            bottomActionBar = findViewById(R.id.bottomActionBar)
            scanLine = findViewById(R.id.scanLine)

            applyWindowInsets()

            Log.d("QRScanner", "Views initialized successfully")

            // Initialize permission manager
            permissionManager = PermissionManager(this)
            Log.d("QRScanner", "Permission manager initialized")

            cameraExecutor = Executors.newSingleThreadExecutor()
            Log.d("QRScanner", "Camera executor created")

            btnBack.setOnClickListener {
                Log.d("QRScanner", "Back button clicked")
                setResult(RESULT_CANCELLED)
                finish()
            }

            btnTerminate.setOnClickListener {
                Log.d("QRScanner", "Terminate button clicked")
                terminateUSSDProcess()
            }

            btnFlash.setOnClickListener {
                isFlashOn = !isFlashOn
                camera?.cameraControl?.enableTorch(isFlashOn)
                btnFlash.setImageResource(
                    if (isFlashOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off
                )
                btnFlash.setBackgroundResource(
                    if (isFlashOn) {
                        R.drawable.bottom_action_button_active_bg
                    } else {
                        R.drawable.bottom_action_button_bg
                    }
                )
            }

            btnGallery.setOnClickListener {
                Log.d("QRScanner", "Gallery picker opened")
                galleryPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }

            // Register broadcast receivers
            val overlayFilter = IntentFilter("DISMISS_OVERLAY")
            LocalBroadcastManager.getInstance(this).registerReceiver(overlayDismissReceiver, overlayFilter)

            val smsFilter = IntentFilter("com.flowpay.app.SMS_RECEIVED")
            LocalBroadcastManager.getInstance(this).registerReceiver(smsReceiver, smsFilter)

            Log.d("QRScanner", "Starting permission check...")
            checkPermissionsAndStartCamera()
        } catch (e: Exception) {
            Log.e("QRScanner", "Error in onCreate", e)
            Toast.makeText(this, R.string.error_qr_scanner_init, Toast.LENGTH_LONG).show()
            setResult(RESULT_ERROR)
            finish()
        }
    }

    /**
     * Check CAMERA permission before starting camera
     */
    private fun checkPermissionsAndStartCamera() {
        Log.d("QRScanner", "Checking CAMERA permission before starting camera")

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("QRScanner", "Camera permission not granted, requesting...")
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        } else {
            Log.d("QRScanner", "Camera permission already granted, starting camera")
            startCamera()
        }
    }

    /**
     * Handle CAMERA permission request results
     */
    private fun handlePermissionResult(permissions: Map<String, Boolean>) {
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true

        if (cameraGranted) {
            Log.d("QRScanner", "Camera permission granted, starting camera")
            startCamera()
        } else {
            Log.w("QRScanner", "Camera permission denied")
            Toast.makeText(
                this,
                "Camera permission is required for QR scanning",
                Toast.LENGTH_LONG
            ).show()
            setResult(RESULT_ERROR)
            finish()
        }
    }

    private fun startCamera() {
        try {
            Log.d("QRScanner", "Starting camera initialization...")
            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

            cameraProviderFuture.addListener({
                try {
                    if (!isActivityAlive()) return@addListener

                    val cameraProvider = cameraProviderFuture.get()
                    Log.d("QRScanner", "Camera provider obtained successfully")

                    // Preview
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(viewFinder.surfaceProvider)
                    }
                    Log.d("QRScanner", "Preview configured")

                    // QR Code Analysis
                    imageAnalyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(
                                cameraExecutor,
                                QRCodeAnalyzer { qrCode ->
                                    processQRCode(qrCode)
                                }
                            )
                        }
                    Log.d("QRScanner", "Image analyzer configured")

                    // Select back camera
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        // Unbind use cases before rebinding
                        cameraProvider.unbindAll()
                        Log.d("QRScanner", "Previous use cases unbound")

                        // Bind use cases to camera — safe null check instead of !!
                        val analyzer = imageAnalyzer ?: run {
                            Log.e("QRScanner", "Image analyzer is null, cannot bind camera")
                            return@addListener
                        }
                        camera = cameraProvider.bindToLifecycle(
                            this, cameraSelector, preview, analyzer
                        )
                        Log.d("QRScanner", "Camera use cases bound successfully")
                        runOnUiThread { startScanLineAnimation() }
                    } catch (exc: Exception) {
                        Log.e("QRScanner", "Use case binding failed", exc)
                        runOnUiThread {
                            if (!isActivityAlive()) return@runOnUiThread
                            Toast.makeText(
                                this,
                                "Camera initialization failed: ${exc.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("QRScanner", "Error in camera setup", e)
                    runOnUiThread {
                        if (!isActivityAlive()) return@runOnUiThread
                        Toast.makeText(this, R.string.error_camera_setup, Toast.LENGTH_LONG).show()
                    }
                }
            }, ContextCompat.getMainExecutor(this))
        } catch (e: Exception) {
            Log.e("QRScanner", "Failed to start camera", e)
            Toast.makeText(this, R.string.error_camera_setup, Toast.LENGTH_LONG).show()
        }
    }

    private fun processQRCode(qrCode: String) {
        try {
            // Already processing a QR — a second decode must not dial again.
            if (isProcessingQRCode) {
                Log.d("QRScanner", "Already processing QR code, ignoring duplicate detection")
                return
            }

            // Set processing flag
            isProcessingQRCode = true

            // Stop scanning
            imageAnalyzer?.clearAnalyzer()

            Log.d("QRScanner", "QR Code detected: ${qrCode.take(100)}...")

            // Show loading
            runOnUiThread {
                if (!isActivityAlive()) return@runOnUiThread
                scannerOverlay.visibility = View.GONE
                bottomActionBar.visibility = View.GONE
                scanLineAnimator?.cancel()
                progressBar.visibility = View.VISIBLE
                tvStatus.text = getString(R.string.qr_processing)
                tvStatus.visibility = View.VISIBLE
            }

            // Parse UPI data. A code that isn't a usable UPI QR is a normal,
            // expected thing to point a scanner at — it must not be treated
            // like a payment failure, and the parser's specific reason is far
            // more useful to the user than a generic "invalid".
            when (val result = QRCodeParser.parse(qrCode)) {
                is QRCodeParser.ParseResult.Valid -> {
                    Log.d("QRScanner", "Successfully parsed UPI data")
                    initiateUSSDPayment(result.data)
                }
                is QRCodeParser.ParseResult.Invalid -> {
                    Log.w("QRScanner", "QR rejected: ${result.reason}")
                    showInvalidQrCode(result.reason)
                }
            }
        } catch (e: Exception) {
            Log.e("QRScanner", "Error processing QR code", e)
            showError("Error processing QR code. Please try again.")
            // Reset processing flag on error
            isProcessingQRCode = false
        }
    }

    private fun initiateUSSDPayment(upiData: UPIData) {
        // Always proceed with payment - skip amount dialog
        // VPA will be copied to clipboard and USSD will be dialed instantly
        proceedWithPayment(upiData)
    }

    private fun proceedWithPayment(upiData: UPIData) {
        try {
            Log.d("QRScanner", "=== STARTING SIMPLE QR PAYMENT PROCESS ===")

            // Validate VPA
            if (upiData.vpa.isBlank()) {
                Log.e("QRScanner", "VPA is blank, cannot proceed")
                showError("Invalid VPA. Please scan a valid UPI QR code.")
                return
            }

            if (!beginQrPaymentSession(upiData)) return

            // Show black screen with status
            showBlackScreenWithStatus("Processing payment...")

            // 1. Copy VPA to clipboard so the user can paste it into the USSD
            //    menu. It is a payee identifier (PII), so it is flagged
            //    sensitive and wiped in onDestroy when the flow ends.
            copyVpaToClipboard(upiData.vpa)

            // 2. Dial USSD code after a short delay (only once per flow)
            mainHandler.postDelayed({
                try {
                    if (!isActivityAlive()) return@postDelayed
                    if (hasDialedUSSD) {
                        Log.d("QRScanner", "USSD already dialed this flow, skipping duplicate dial")
                        return@postDelayed
                    }
                    hasDialedUSSD = true
                    Log.d("QRScanner", "Dialing USSD code: *99*1*3#")
                    updateBlackScreenStatus("Initiating USSD call...")
                    dialUSSD()
                } catch (e: Exception) {
                    Log.e("QRScanner", "Failed to dial USSD: ${e.message}", e)
                    showError("Failed to initiate USSD call: ${e.message}")
                }
            }, 1000) // 1 second delay to ensure overlay is ready
        } catch (e: Exception) {
            Log.e("QRScanner", "Unexpected error in proceedWithPayment: ${e.message}", e)
            showError("An unexpected error occurred: ${e.message}")
        }
    }

    /**
     * Starts the payment session (PENDING row + verification deadline, same
     * lifecycle as the manual flow) and opens the SMS operation window for
     * it. Returns false — after showing the user why — when the payment must
     * not proceed. The QR flow used to bypass the session manager entirely,
     * so an unconfirmed QR payment left no trace at all.
     */
    private fun beginQrPaymentSession(upiData: UPIData): Boolean {
        val sessionManager = FlowpayApplication.from(this)?.paymentSessionManager
        val sessionTxnId = sessionManager?.begin(
            phoneNumber = "",
            amount = upiData.amount ?: "",
            upiId = upiData.vpa,
            source = TransactionSource.QR
        )
        // No "already in progress" refusal: begin() supersedes any session
        // still in flight, so a scan is never blocked by a previous payment
        // the bank never confirmed.
        return try {
            TransactionDetector.getInstance(this).startOperation(
                operationType = "QR_SCAN",
                expectedAmount = upiData.amount,
                sessionTxnId = sessionTxnId
            )
            Log.d("QRScanner", "SMS monitoring started for QR payment")
            true
        } catch (e: IllegalStateException) {
            Log.e("QRScanner", "Failed to start SMS monitoring: ${e.message}")
            sessionManager?.onDialFailed("Could not start SMS monitoring")
            showError("Failed to initialize payment system. Please try again.")
            false
        }
    }

    /**
     * Copies the payee VPA to the clipboard so the user can paste it into the
     * USSD menu. Flagged sensitive on Android 13+ so it stays out of clipboard
     * previews; [clearVpaClipboard] wipes it when the flow ends.
     */
    private fun copyVpaToClipboard(vpa: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("VPA", vpa)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                clip.description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
            }
            clipboard.setPrimaryClip(clip)
            didCopyVpa = true
            Log.d("QRScanner", "VPA copied to clipboard successfully")
            // No status update here: the system already shows its own
            // "copied" confirmation on Android 13+, and step 1 of the
            // on-screen instructions already tells the user to paste it.
            // Announcing it a third time was just noise.
        } catch (e: SecurityException) {
            // Not critical — the flow can continue without the clipboard copy.
            Log.e("QRScanner", "Failed to copy VPA to clipboard: ${e.message}")
        } catch (e: IllegalStateException) {
            Log.e("QRScanner", "Failed to copy VPA to clipboard: ${e.message}")
        }
    }

    /**
     * Clears the payee VPA so it doesn't linger for other apps.
     *
     * Uses clearPrimaryClip() rather than writing an empty clip: on Android
     * 13+ EVERY setPrimaryClip() raises the system "Copied to clipboard"
     * chip, so overwriting produced a second, baffling "copied" popup at the
     * moment the session ended — after the user had already finished. Clearing
     * is not a copy, so nothing is announced, and it genuinely empties the
     * clipboard instead of parking an empty string on it.
     */
    private fun clearVpaClipboard() {
        if (!didCopyVpa) return
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.clearPrimaryClip()
            didCopyVpa = false
            Log.d("QRScanner", "VPA cleared from clipboard")
        } catch (e: SecurityException) {
            Log.e("QRScanner", "Error clearing clipboard: ${e.message}")
        } catch (e: IllegalStateException) {
            Log.e("QRScanner", "Error clearing clipboard: ${e.message}")
        }
    }

    private fun dialUSSD() {
        val ussdCode = "*99*1*3#"
        val encodedHash = Uri.encode("#")
        val ussd = ussdCode.replace("#", encodedHash)

        Log.d("QRScanner", "Preparing to dial USSD: $ussdCode")

        // Check CALL_PHONE permission before dialing — use modern launcher API
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("QRScanner", "CALL_PHONE permission not granted - requesting")
            callPhonePermissionLauncher.launch(Manifest.permission.CALL_PHONE)
            return
        }

        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$ussd")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            Log.d("QRScanner", "Starting USSD call activity")
            updateBlackScreenStatus("USSD call initiated")
            startActivity(intent)
            Log.d("QRScanner", "USSD call initiated successfully")

            // Start the extended message sequence for USSD process
            startUSSDProcessMessages()
        } catch (e: Exception) {
            Log.e("QRScanner", "Failed to dial USSD: ${e.message}", e)
            // Nothing was dialled, so end the session too — otherwise the SMS
            // window keeps listening for a payment that never happened and
            // could adopt an unrelated confirmation.
            FlowpayApplication.from(this)?.paymentSessionManager
                ?.onDialFailed("Could not start the payment call")
            showError("Failed to initiate USSD call: ${e.message}")
        }
    }

    /**
     * Start extended message sequence during USSD process
     */
    private fun startUSSDProcessMessages() {
        Log.d("QRScanner", "Entering USSD wait phase")
        isUSSDProcessActive = true

        // Honest, static status. Flowpay cannot see the USSD menu or the bank's
        // progress, so it does not fake step-by-step progress — it states what
        // the user should do and what it is genuinely waiting for.
        updateBlackScreenStatus(
            "Complete the payment in the dialer, then wait for your bank's " +
                "confirmation SMS. This can take a minute or two."
        )

        // Wait for the confirmation SMS, giving up after the timeout below.
        startSMSTimeout()
    }

    /**
     * Start SMS timeout timer
     */
    private fun startSMSTimeout() {
        Log.d("QRScanner", "Starting SMS timeout timer (150 seconds)")

        smsTimeoutHandler = Handler(Looper.getMainLooper())
        smsTimeoutRunnable = Runnable {
            if (!isActivityAlive()) return@Runnable
            Log.d("QRScanner", "SMS timeout reached - returning to main screen")
            if (isUSSDProcessActive) {
                updateBlackScreenStatus("Transaction timeout. Returning to main screen...")
                mainHandler.postDelayed({
                    if (!isActivityAlive()) return@postDelayed
                    setResult(RESULT_CANCELLED)
                    finish()
                }, 2000)
            }
        }

        smsTimeoutHandler?.postDelayed(smsTimeoutRunnable!!, 150000) // 150 seconds
    }

    /**
     * Handle SMS received
     */
    private fun handleSMSReceived(intent: Intent) {
        Log.d("QRScanner", "SMS received - finishing QRScannerActivity immediately")

        // Cancel all timeouts and handlers
        smsTimeoutRunnable?.let {
            smsTimeoutHandler?.removeCallbacks(it)
        }
        messageHandler?.removeCallbacksAndMessages(null)

        // Dismiss USSD overlay

        // Hide any black screen or status messages
        runOnUiThread {
            if (!isActivityAlive()) return@runOnUiThread
            try {
                // Hide status text, instructions box and terminate button
                tvStatus.visibility = View.GONE
                instructionsBox.visibility = View.GONE
                btnTerminate.visibility = View.GONE
                topBar.visibility = View.VISIBLE // Show top bar again

                // Reset instruction states for next time
                instructionsHeaderInitial.visibility = View.VISIBLE
                instructionsHeaderInitial.alpha = 1f
                instructionsExpanded.visibility = View.GONE
                instructionsExpanded.alpha = 1f

                // Reset background to normal
                findViewById<View>(
                    android.R.id.content
                ).setBackgroundColor(ContextCompat.getColor(this, R.color.background_secondary))
            } catch (e: Exception) {
                Log.e("QRScanner", "Error hiding black screen: ${e.message}")
            }
        }

        // Finish this activity immediately - SimpleSMSReceiver will handle showing success screen
        Log.d("QRScanner", "Finishing QRScannerActivity - SimpleSMSReceiver will show success screen")
        setResult(RESULT_SUCCESS)
        finish()
    }

    /**
     * Terminate USSD process and return to main screen
     */
    private fun terminateUSSDProcess() {
        try {
            Log.d("QRScanner", "Terminating USSD process...")

            // Stop USSD process
            isUSSDProcessActive = false

            // Cancel all timeouts and handlers
            messageHandler?.removeCallbacksAndMessages(null)
            smsTimeoutRunnable?.let {
                smsTimeoutHandler?.removeCallbacks(it)
            }

            // Hide USSD overlay

            // Stop SMS monitoring
            try {
                TransactionDetector.getInstance(this).stopOperation()
                Log.d("QRScanner", "SMS monitoring stopped")
            } catch (e: Exception) {
                Log.e("QRScanner", "Failed to stop SMS monitoring: ${e.message}")
            }

            // The user explicitly aborted: mark the session row CANCELLED so
            // it doesn't linger PENDING waiting for a payment nobody made.
            FlowpayApplication.from(this)?.paymentSessionManager?.onUserCancelled()

            // Show termination message briefly
            runOnUiThread {
                if (!isActivityAlive()) return@runOnUiThread
                updateBlackScreenStatus("Process terminated. Returning to main screen...")
                btnTerminate.visibility = View.GONE
            }

            // Return to main screen after brief delay
            mainHandler.postDelayed({
                if (!isActivityAlive()) return@postDelayed
                Log.d("QRScanner", "Returning to main screen after termination")
                setResult(RESULT_CANCELLED)
                finish()
            }, 1500)
        } catch (e: Exception) {
            Log.e("QRScanner", "Error terminating USSD process: ${e.message}", e)
            // Fallback: just close the activity
            setResult(RESULT_ERROR)
            finish()
        }
    }

    /**
     * Show black screen with status message
     */
    private fun showBlackScreenWithStatus(message: String) {
        runOnUiThread {
            if (!isActivityAlive()) return@runOnUiThread
            Log.d("QRScanner", "Showing black screen with status: $message")

            // Hide camera and scanner elements
            viewFinder.visibility = View.GONE
            scannerOverlay.visibility = View.GONE
            bottomActionBar.visibility = View.GONE
            scanLineAnimator?.cancel()
            progressBar.visibility = View.GONE
            topBar.visibility = View.GONE // Hide top bar to prevent overlap

            // Show instructions box at the top with initial state
            instructionsBox.visibility = View.VISIBLE
            instructionsHeaderInitial.visibility = View.VISIBLE
            instructionsExpanded.visibility = View.GONE

            // Animate transition to expanded state after 4 seconds
            mainHandler.postDelayed({
                if (!isActivityAlive()) return@postDelayed
                animateInstructionsTransition()
            }, 4000)

            // Show black screen with status
            tvStatus.text = message
            tvStatus.visibility = View.VISIBLE
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            tvStatus.textSize = 18f

            // Show terminate button
            btnTerminate.visibility = View.VISIBLE

            // Set black background
            findViewById<View>(
                android.R.id.content
            ).setBackgroundColor(ContextCompat.getColor(this, android.R.color.black))
        }
    }

    /**
     * Animate transition from initial header to expanded steps list
     */
    private fun animateInstructionsTransition() {
        runOnUiThread {
            if (!isActivityAlive()) return@runOnUiThread
            Log.d("QRScanner", "Animating instructions transition to expanded state")

            // Fade out initial header
            instructionsHeaderInitial.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    if (!isActivityAlive()) return@withEndAction
                    instructionsHeaderInitial.visibility = View.GONE

                    // Fade in expanded view
                    instructionsExpanded.alpha = 0f
                    instructionsExpanded.visibility = View.VISIBLE
                    instructionsExpanded.animate()
                        .alpha(1f)
                        .setDuration(400)
                        .start()
                }
                .start()
        }
    }

    /**
     * Update status message on black screen
     */
    private fun updateBlackScreenStatus(message: String) {
        runOnUiThread {
            if (!isActivityAlive()) return@runOnUiThread
            Log.d("QRScanner", "Updating black screen status: $message")
            tvStatus.text = message
        }
    }

    /**
     * Decodes a QR out of an image the user picked from their gallery, then
     * hands it to the very same [processQRCode] the live camera uses — so a
     * gallery scan and a camera scan are indistinguishable downstream.
     *
     * Useful when the payee sent a QR as a photo, or the code is on a screen
     * the camera can't focus on.
     */
    private fun decodeQrFromGallery(uri: Uri) {
        lifecycleScope.launch {
            val decoded = withContext(Dispatchers.IO) {
                runCatching {
                    val source = ImageDecoder.createSource(contentResolver, uri)
                    val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        // getPixels() throws on a HARDWARE bitmap, which is
                        // what ImageDecoder hands back by default — the QR
                        // decoder needs readable pixels.
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = false
                        // Cap the working size: gallery photos can be 100MP,
                        // and a QR stays readable far below that.
                        val longest = maxOf(info.size.width, info.size.height)
                        if (longest > GALLERY_MAX_EDGE_PX) {
                            decoder.setTargetSampleSize(longest / GALLERY_MAX_EDGE_PX + 1)
                        }
                    }
                    QRCodeAnalyzer.decodeBitmap(bitmap).also { bitmap.recycle() }
                }.onFailure { Log.w("QRScanner", "Gallery image could not be read: ${it.message}") }
            }

            if (!isActivityAlive()) return@launch
            when {
                decoded.isFailure -> showInvalidQrCode(getString(R.string.qr_gallery_unreadable))
                decoded.getOrNull() == null -> showInvalidQrCode(getString(R.string.qr_gallery_no_code))
                else -> {
                    // A gallery pick is an explicit user action: let it through
                    // even if a previous scan left the guard set.
                    isProcessingQRCode = false
                    processQRCode(decoded.getOrNull()!!)
                }
            }
        }
    }

    /**
     * A scanned code that isn't a usable UPI QR. This is NOT an error state:
     * the camera keeps running, the banner explains what was wrong, and
     * scanning re-arms so the user can just aim at another code.
     *
     * Previously this went through [showError], which blanked the preview to a
     * black screen with red "Error:" text AND fired a duplicate toast, then sat
     * dead for three seconds — which reads as the scanner having crashed.
     */
    private fun showInvalidQrCode(reason: String) {
        runOnUiThread {
            if (!isActivityAlive()) return@runOnUiThread
            tvScanError.text = getString(R.string.qr_invalid_banner, reason)
            tvScanError.visibility = View.VISIBLE

            // Re-arm immediately: the next frame holding a good QR should be
            // accepted even while the banner is still on screen.
            isProcessingQRCode = false

            mainHandler.removeCallbacks(hideScanErrorRunnable)
            mainHandler.postDelayed(hideScanErrorRunnable, SCAN_ERROR_VISIBLE_MS)
        }
    }

    private val hideScanErrorRunnable = Runnable {
        if (isActivityAlive() && ::tvScanError.isInitialized) {
            tvScanError.visibility = View.GONE
        }
    }

    private fun showError(message: String) {
        runOnUiThread {
            if (!isActivityAlive()) return@runOnUiThread
            Log.e("QRScanner", "Showing error: $message")

            // Show error on black screen
            updateBlackScreenStatus("Error: $message")
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))

            // Show toast for additional feedback
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()

            // Resume scanning after 3 seconds with proper error recovery
            mainHandler.postDelayed({
                if (!isActivityAlive()) return@postDelayed
                try {
                    Log.d("QRScanner", "Resuming QR scanning after error")
                    // Reset to camera view
                    viewFinder.visibility = View.VISIBLE
                    scannerOverlay.visibility = View.VISIBLE
                    bottomActionBar.visibility = View.VISIBLE
                    topBar.visibility = View.VISIBLE // Show top bar again
                    tvStatus.visibility = View.GONE
                    instructionsBox.visibility = View.GONE
                    btnTerminate.visibility = View.GONE
                    // Reset flash state
                    isFlashOn = false
                    camera?.cameraControl?.enableTorch(false)
                    btnFlash.setImageResource(R.drawable.ic_flash_off)
                    btnFlash.setBackgroundResource(R.drawable.bottom_action_button_bg)
                    startScanLineAnimation()

                    // Reset instruction states
                    instructionsHeaderInitial.visibility = View.VISIBLE
                    instructionsHeaderInitial.alpha = 1f
                    instructionsExpanded.visibility = View.GONE
                    instructionsExpanded.alpha = 1f

                    findViewById<View>(
                        android.R.id.content
                    ).setBackgroundColor(ContextCompat.getColor(this, android.R.color.black))

                    // Resuming after an error: clear both the processing and dialed flags.
                    isProcessingQRCode = false
                    hasDialedUSSD = false
                    if (::cameraExecutor.isInitialized && !cameraExecutor.isShutdown) {
                        imageAnalyzer?.clearAnalyzer()
                        imageAnalyzer?.setAnalyzer(
                            cameraExecutor,
                            QRCodeAnalyzer { qrCode ->
                                processQRCode(qrCode)
                            }
                        )
                    }
                } catch (e: Exception) {
                    Log.e("QRScanner", "Failed to resume scanning", e)
                    Toast.makeText(this, R.string.error_qr_resume_failed, Toast.LENGTH_LONG).show()
                }
            }, 3000)
        }
    }

    private fun startScanLineAnimation() {
        if (!::scanLine.isInitialized) return
        scanLineAnimator?.cancel()
        val density = resources.displayMetrics.density
        // Scanner frame is 260dp; margins 12dp each side; line is 2dp — travel ~234dp
        val maxTranslation = ((260 - 12 * 2 - 2) * density)
        scanLineAnimator = android.animation.ObjectAnimator.ofFloat(
            scanLine, "translationY", 0f, maxTranslation
        ).apply {
            duration = 2000
            repeatMode = android.animation.ValueAnimator.REVERSE
            repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            start()
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d("QRScanner", "Activity paused - stopping camera and terminating USSD overlay")

        // Send app paused broadcast to USSD overlay service
        try {
            val intent = Intent("APP_PAUSED")
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            Log.d("QRScanner", "App paused broadcast sent")
        } catch (e: Exception) {
            Log.e("QRScanner", "Error sending app paused broadcast: ${e.message}")
        }

        // Terminate USSD overlay when activity is paused

        try {
            imageAnalyzer?.clearAnalyzer()
            // Do NOT reset isProcessingQRCode here: when user goes to dialer we pause;
            // if we reset the flag, onResume would re-attach the analyzer and dial again in a loop.
            // Flag is reset only in onDestroy or in showError() when resuming scanning.
        } catch (e: Exception) {
            Log.e("QRScanner", "Error stopping camera on pause: ${e.message}", e)
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d("QRScanner", "Activity stopped - terminating USSD overlay")

        // Send app stopped broadcast to USSD overlay service
        try {
            val intent = Intent("APP_STOPPED")
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            Log.d("QRScanner", "App stopped broadcast sent")
        } catch (e: Exception) {
            Log.e("QRScanner", "Error sending app stopped broadcast: ${e.message}")
        }

        // Terminate USSD overlay when activity is stopped
    }

    override fun onResume() {
        super.onResume()
        Log.d("QRScanner", "Activity resumed - restarting camera")
        try {
            // Guard against shutdown executor
            if (!::cameraExecutor.isInitialized || cameraExecutor.isShutdown) {
                Log.w("QRScanner", "Camera executor not available, skipping analyzer reattach")
                return
            }
            if (imageAnalyzer != null && !isProcessingQRCode) {
                // Clear the previous analyzer first; leaving it attached stacks analyzers.
                imageAnalyzer?.clearAnalyzer()
                imageAnalyzer?.setAnalyzer(
                    cameraExecutor,
                    QRCodeAnalyzer { qrCode ->
                        processQRCode(qrCode)
                    }
                )
            }
        } catch (e: Exception) {
            Log.e("QRScanner", "Error restarting camera on resume: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        try {
            Log.d("QRScanner", "Destroying QR scanner activity...")

            // Cancel ALL pending callbacks from centralized handler
            mainHandler.removeCallbacksAndMessages(null)

            // Send app destroyed broadcast to USSD overlay service
            try {
                val intent = Intent("APP_DESTROYED")
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                Log.d("QRScanner", "App destroyed broadcast sent")
            } catch (e: Exception) {
                Log.e("QRScanner", "Error sending app destroyed broadcast: ${e.message}")
            }

            // Terminate USSD overlay when activity is destroyed

            // Stop USSD process
            isUSSDProcessActive = false
            messageHandler?.removeCallbacksAndMessages(null)
            smsTimeoutRunnable?.let {
                smsTimeoutHandler?.removeCallbacks(it)
            }

            // Reset processing flags.
            isProcessingQRCode = false
            hasDialedUSSD = false

            // Leaving this screen with a payment still in flight (system back,
            // recents swipe) abandons it — end the session so the SMS window
            // closes with it and the next scan isn't refused as "already in
            // progress". A no-op once the session reached a terminal state,
            // and `isFinishing` keeps a config change from cancelling a live
            // payment.
            if (isFinishing) {
                FlowpayApplication.from(this)?.paymentSessionManager?.onUserCancelled()
            }

            // Wipe the payee VPA from the clipboard now the flow is over.
            clearVpaClipboard()

            // Cancel scan line animation
            scanLineAnimator?.cancel()
            scanLineAnimator = null

            // Unregister broadcast receivers
            try {
                LocalBroadcastManager.getInstance(this).unregisterReceiver(overlayDismissReceiver)
                LocalBroadcastManager.getInstance(this).unregisterReceiver(smsReceiver)
            } catch (e: Exception) {
                Log.e("QRScanner", "Error unregistering receivers: ${e.message}")
            }

            // Clear image analyzer first
            imageAnalyzer?.clearAnalyzer()
            imageAnalyzer = null

            // Shutdown camera executor safely
            if (::cameraExecutor.isInitialized && !cameraExecutor.isShutdown) {
                cameraExecutor.shutdown()
                Log.d("QRScanner", "Camera executor shutdown")
            }

            Log.d("QRScanner", "QR scanner activity destroyed successfully")
        } catch (e: Exception) {
            Log.e("QRScanner", "Error during activity destruction: ${e.message}", e)
        } finally {
            super.onDestroy()
        }
    }
}
