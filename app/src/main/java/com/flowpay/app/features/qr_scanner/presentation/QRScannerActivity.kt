package com.flowpay.app.features.qr_scanner.presentation

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.flowpay.app.R
import com.flowpay.app.data.UPIData
import com.flowpay.app.features.qr_scanner.domain.QRCodeAnalyzer
import com.flowpay.app.helpers.SetupHelper
import com.flowpay.app.helpers.TransactionDetector
import com.flowpay.app.managers.PermissionManager
import android.view.WindowManager
import android.widget.ImageButton
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.camera.core.Camera
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class QRScannerActivity : ComponentActivity() {

    companion object {
        const val RESULT_CANCELLED = 0
        const val RESULT_SUCCESS = 1
        const val RESULT_ERROR = 2
    }

    private lateinit var viewFinder: PreviewView
    private lateinit var scannerOverlay: View
    private lateinit var progressBar: View
    private lateinit var tvStatus: TextView
    private lateinit var btnBack: View
    private lateinit var btnTerminate: View
    private lateinit var topBar: View
    private lateinit var instructionsBox: View
    private lateinit var instructionsHeaderInitial: View
    private lateinit var instructionsExpanded: View
    private lateinit var btnGallery: ImageButton
    private lateinit var btnFlash: ImageButton
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

    // FIX: Add flag to prevent multiple QR code processing
    @Volatile
    private var isProcessingQRCode = false

    // FIX: Ensure USSD is only dialed once per payment flow (prevents loop when returning from dialer)
    @Volatile
    private var hasDialedUSSD = false

    // Gallery image picker launcher
    private val galleryLauncher = registerForActivityResult(GetContent()) { uri ->
        if (uri != null) scanImageFromGallery(uri)
    }

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
            btnBack = findViewById(R.id.btnBack)
            btnTerminate = findViewById(R.id.btnTerminate)
            topBar = findViewById(R.id.topBar)
            instructionsBox = findViewById(R.id.instructionsBox)
            instructionsHeaderInitial = findViewById(R.id.instructionsHeaderInitial)
            instructionsExpanded = findViewById(R.id.instructionsExpanded)
            btnGallery = findViewById(R.id.btnGallery)
            btnFlash = findViewById(R.id.btnFlash)
            bottomActionBar = findViewById(R.id.bottomActionBar)
            scanLine = findViewById(R.id.scanLine)

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
                    if (isFlashOn) R.drawable.bottom_action_button_active_bg
                    else R.drawable.bottom_action_button_bg
                )
            }

            btnGallery.setOnClickListener {
                galleryLauncher.launch("image/*")
            }

            // Register broadcast receivers
            val overlayFilter = IntentFilter("DISMISS_OVERLAY")
            LocalBroadcastManager.getInstance(this).registerReceiver(overlayDismissReceiver, overlayFilter)

            val smsFilter = IntentFilter("com.flowpay.app.SMS_RECEIVED")
            LocalBroadcastManager.getInstance(this).registerReceiver(smsReceiver, smsFilter)

            Log.d("QRScanner", "Starting permission check...")
            checkPermissionsAndStartCamera()

        } catch (e: Exception) {
            Log.e("QRScanner", "Error in onCreate: ${e.message}", e)
            Toast.makeText(this, "Failed to initialize QR scanner: ${e.message}", Toast.LENGTH_LONG).show()
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
                            it.setAnalyzer(cameraExecutor, QRCodeAnalyzer { qrCode ->
                                processQRCode(qrCode)
                            })
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
                            Toast.makeText(this, "Camera initialization failed: ${exc.message}", Toast.LENGTH_LONG).show()
                        }
                    }

                } catch (e: Exception) {
                    Log.e("QRScanner", "Error in camera setup: ${e.message}", e)
                    runOnUiThread {
                        if (!isActivityAlive()) return@runOnUiThread
                        Toast.makeText(this, "Camera setup failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }

            }, ContextCompat.getMainExecutor(this))
        } catch (e: Exception) {
            Log.e("QRScanner", "Failed to start camera: ${e.message}", e)
            Toast.makeText(this, "Failed to start camera: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun processQRCode(qrCode: String) {
        try {
            // FIX: Check if already processing a QR code to prevent multiple dials
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
                tvStatus.text = "Processing QR code..."
                tvStatus.visibility = View.VISIBLE
            }

            // Parse UPI data
            val upiData = parseUPIData(qrCode)
            if (upiData != null && upiData.vpa.isNotEmpty()) {
                Log.d("QRScanner", "Successfully parsed UPI data")
                // Process payment
                initiateUSSDPayment(upiData)
            } else {
                Log.w("QRScanner", "Failed to parse UPI data or empty VPA")
                showError("Invalid UPI QR code. Please scan a valid UPI payment QR code.")
                // Reset processing flag on error
                isProcessingQRCode = false
            }
        } catch (e: Exception) {
            Log.e("QRScanner", "Error processing QR code", e)
            showError("Error processing QR code. Please try again.")
            // Reset processing flag on error
            isProcessingQRCode = false
        }
    }

    private fun parseUPIData(upiString: String): UPIData? {
        return when (val result = com.flowpay.app.features.qr_scanner.domain.QRCodeParser.parse(upiString)) {
            is com.flowpay.app.features.qr_scanner.domain.QRCodeParser.ParseResult.Valid -> result.data
            is com.flowpay.app.features.qr_scanner.domain.QRCodeParser.ParseResult.Invalid -> {
                Log.w("QRScanner", "QR rejected: ${result.reason}")
                null
            }
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

            // Start SMS monitoring for QR payment
            try {
                TransactionDetector.getInstance(this).startOperation(
                    operationType = "QR_SCAN",
                    expectedAmount = upiData.amount
                )
                Log.d("QRScanner", "SMS monitoring started for QR payment")
            } catch (e: Exception) {
                Log.e("QRScanner", "Failed to start SMS monitoring: ${e.message}")
                showError("Failed to initialize payment system. Please try again.")
                return
            }

            // Show black screen with status
            showBlackScreenWithStatus("Processing payment...")

            // 1. Copy VPA to clipboard
            try {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("VPA", upiData.vpa)
                clipboard.setPrimaryClip(clip)
                Log.d("QRScanner", "VPA copied to clipboard successfully")

                // Update status
                updateBlackScreenStatus("VPA copied to clipboard")
            } catch (e: Exception) {
                Log.e("QRScanner", "Failed to copy VPA to clipboard: ${e.message}")
                // Continue anyway, this is not critical
            }

            // 2. Show USSD overlay - DISABLED
            // DISABLED: USSDOverlay functionality temporarily disabled
            // try {
            //     Log.d("QRScanner", "Starting USSD overlay service...")
            //     updateBlackScreenStatus("Starting payment guidance...")
            //     Log.d("QRScanner", "USSD overlay service started successfully")
            //     updateBlackScreenStatus("Payment guidance ready")
            // } catch (e: Exception) {
            //     Log.e("QRScanner", "Failed to start USSD overlay: ${e.message}", e)
            //     showError("Failed to start payment guidance: ${e.message}")
            //     return
            // }

            // 3. Dial USSD code after a short delay to ensure overlay is ready (only once per flow)
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
            showError("Failed to initiate USSD call: ${e.message}")
        }
    }

    /**
     * Start extended message sequence during USSD process
     */
    private fun startUSSDProcessMessages() {
        Log.d("QRScanner", "Starting USSD process message sequence")
        isUSSDProcessActive = true

        val ussdMessages = listOf(
            "Connecting to USSD service...",
            "Almost there! Setting up payment...",
            "Great job! Processing transaction...",
            "Sending transaction details...",
            "Please wait while we process your payment...",
            "Transaction in progress...",
            "Almost done! Finalizing payment...",
            "Great work! Completing transaction...",
            "Payment processing... Please hold on...",
            "Final steps... Almost there!",
            "Transaction being processed...",
            "Great job! Payment almost complete...",
            "Finalizing your payment...",
            "USSD completed! Waiting for transaction confirmation...",
            "Please wait for SMS confirmation..."
        )

        val smsWaitingMessages = listOf(
            "Waiting for transaction confirmation...",
            "Please wait for SMS from your bank...",
            "Transaction being processed by bank...",
            "Almost there! Checking for confirmation...",
            "Great job! Payment is being verified...",
            "Please be patient, confirmation coming soon...",
            "Transaction in final stages...",
            "Bank is processing your payment...",
            "Confirmation SMS on the way...",
            "Almost done! Just a moment more...",
            "Great work! Payment being finalized...",
            "Bank is sending confirmation...",
            "Transaction almost complete...",
            "Please wait for SMS notification...",
            "Final verification in progress..."
        )

        var messageIndex = 0
        var isWaitingForSMS = false

        // Show messages every 2 seconds
        messageHandler = object : android.os.Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: android.os.Message) {
                if (!isActivityAlive()) return
                if (isUSSDProcessActive) {
                    if (!isWaitingForSMS && messageIndex < ussdMessages.size) {
                        // Show USSD messages first
                        updateBlackScreenStatus(ussdMessages[messageIndex])
                        messageIndex++

                        if (messageIndex >= ussdMessages.size) {
                            // Switch to SMS waiting messages
                            isWaitingForSMS = true
                            messageIndex = 0
                        }
                        sendEmptyMessageDelayed(0, 2000)
                    } else if (isWaitingForSMS) {
                        // Show SMS waiting messages
                        updateBlackScreenStatus(smsWaitingMessages[messageIndex % smsWaitingMessages.size])
                        messageIndex++
                        sendEmptyMessageDelayed(0, 3000) // Slower for SMS waiting
                    }
                }
            }
        }

        // Start the message sequence
        messageHandler?.sendEmptyMessageDelayed(0, 2000)

        // Start SMS timeout (150 seconds)
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
                topBar.visibility = View.VISIBLE  // Show top bar again

                // Reset instruction states for next time
                instructionsHeaderInitial.visibility = View.VISIBLE
                instructionsHeaderInitial.alpha = 1f
                instructionsExpanded.visibility = View.GONE
                instructionsExpanded.alpha = 1f

                // Reset background to normal
                findViewById<View>(android.R.id.content).setBackgroundColor(ContextCompat.getColor(this, R.color.background_secondary))
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
     * Navigate to success screen
     */
    private fun navigateToSuccessScreen(transactionData: com.flowpay.app.models.TransactionData?) {
        try {
            Log.d("QRScanner", "Navigating to success screen")
            // Payment success handled via callback instead of separate activity
            Log.d("QRScanner", "Payment success - showing toast instead of separate activity")
            android.widget.Toast.makeText(this, "Payment successful!", android.widget.Toast.LENGTH_LONG).show()
            setResult(RESULT_SUCCESS)
            finish()
        } catch (e: Exception) {
            Log.e("QRScanner", "Failed to navigate to success screen: ${e.message}", e)
            // Fallback: just close the activity
            setResult(RESULT_ERROR)
            finish()
        }
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
            topBar.visibility = View.GONE  // Hide top bar to prevent overlap

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
            findViewById<View>(android.R.id.content).setBackgroundColor(ContextCompat.getColor(this, android.R.color.black))
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
                    topBar.visibility = View.VISIBLE  // Show top bar again
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

                    findViewById<View>(android.R.id.content).setBackgroundColor(ContextCompat.getColor(this, android.R.color.black))

            // FIX: Reset processing flag and "dialed" flag when resuming scanning after error
                    isProcessingQRCode = false
                    hasDialedUSSD = false
                    if (::cameraExecutor.isInitialized && !cameraExecutor.isShutdown) {
                        imageAnalyzer?.clearAnalyzer()
                        imageAnalyzer?.setAnalyzer(cameraExecutor, QRCodeAnalyzer { qrCode ->
                            processQRCode(qrCode)
                        })
                    }
                } catch (e: Exception) {
                    Log.e("QRScanner", "Failed to resume scanning: ${e.message}", e)
                    Toast.makeText(this, "Failed to resume scanning. Please restart the app.", Toast.LENGTH_LONG).show()
                }
            }, 3000)
        }
    }

    private fun scanImageFromGallery(uri: Uri) {
        try {
            val image = InputImage.fromFilePath(this, uri)
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
            BarcodeScanning.getClient(options).process(image)
                .addOnSuccessListener { barcodes ->
                    val qr = barcodes.firstOrNull()?.rawValue
                    if (qr != null) {
                        processQRCode(qr)
                    } else {
                        Toast.makeText(this, "No QR code found in image", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to scan image", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Toast.makeText(this, "Could not load image", Toast.LENGTH_SHORT).show()
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
                // FIX: Clear analyzer before setting up new one to prevent multiple analyzers
                imageAnalyzer?.clearAnalyzer()
                imageAnalyzer?.setAnalyzer(cameraExecutor, QRCodeAnalyzer { qrCode ->
                    processQRCode(qrCode)
                })
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

            // FIX: Reset processing flags
            isProcessingQRCode = false
            hasDialedUSSD = false

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
