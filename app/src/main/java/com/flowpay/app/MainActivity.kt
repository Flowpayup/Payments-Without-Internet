// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay
//
// Home screen: the entry point to both payment rails (Scan QR and Pay
// Contact), recent payments, and settings. UI only — the transfer
// orchestration and permission gating live in MainActivityHelper.

package com.flowpay.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PermContactCalendar
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flowpay.app.constants.AppConstants
import com.flowpay.app.constants.PermissionConstants
import com.flowpay.app.data.PaymentDetails
import com.flowpay.app.data.PaymentStatus
import com.flowpay.app.data.TestResultsManager
import com.flowpay.app.helpers.MainActivityHelper
import com.flowpay.app.managers.PermissionManager
import com.flowpay.app.payment.Upi123CallStringBuilder
import com.flowpay.app.payment.messageFor
import com.flowpay.app.ui.activities.SettingsActivity
import com.flowpay.app.ui.activities.TransactionHistoryActivity
import com.flowpay.app.ui.components.TransactionDetailDialog
import com.flowpay.app.ui.dialogs.ContactPickerDialog
import com.flowpay.app.ui.theme.FlowpayDarkGray
import com.flowpay.app.ui.theme.FlowpayStatusError
import com.flowpay.app.ui.theme.FlowpayTextGray
import com.flowpay.app.ui.theme.FlowpayTextLightGray
import com.flowpay.app.ui.theme.FlowpayTextPale
import com.flowpay.app.ui.theme.FlowpayTheme
import com.flowpay.app.utils.CurrencyFormat
import com.flowpay.app.utils.findComponentActivity
import com.flowpay.app.viewmodel.MainUiEvent
import com.flowpay.app.viewmodel.MainViewModel
import com.flowpay.app.viewmodel.TransactionViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "Flowpay"
    }

    // Helper for all business logic
    private lateinit var helper: MainActivityHelper

    // Shared with MainScreen (same instance via Compose viewModel()); carries
    // one-shot Activity -> Compose events, replacing the old static callbacks.
    private val mainViewModel: MainViewModel by viewModels()

    // Launches QRScannerActivity and, on return, un-sticks the QR button's
    // "Opening..." state via a QrScannerClosed event.
    private val qrScannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        mainViewModel.onQrScannerClosed()
    }

    // Launches the system "draw over other apps" settings screen; on return,
    // re-checks the permission and reports the outcome via toast (there is
    // no reliable resultCode for this settings screen, so re-checking is
    // the only correct way to know what happened).
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val granted = PermissionManager.canDrawOverlays(this)
        val message = if (granted) {
            "Overlay permission granted. You can now proceed with the transfer."
        } else {
            "Overlay permission is required for payment protection. Please enable it in Settings."
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // Phone-call permission group, requested before dialing or QR scanning.
    // There is no auto-retry: the user re-taps the action once granted.
    private val phonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        if (!granted) {
            Toast.makeText(this, R.string.error_permissions_denied, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(R.style.Theme_Flowpay)

        // Draw edge-to-edge so Compose's statusBarsPadding()/navigationBarsPadding()
        // are the single source of inset padding. The theme previously also set
        // android:fitsSystemWindows=true, which made the decor pad the content as
        // well — a double inset that, depending on inset-dispatch timing, showed
        // intermittent black bars at the top and bottom.
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        // Black system bars from the first frame
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        window.setBackgroundDrawableResource(android.R.color.black)
        window.decorView.setBackgroundColor(android.graphics.Color.BLACK)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
            android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        window.decorView.post { enforceBlackStatusBar() }

        // Initialize helper with UI callbacks (matches the 6-method UICallback)
        helper = MainActivityHelper(
            this,
            object : MainActivityHelper.UICallback {
                override fun showToast(message: String) {
                    runOnUiThread { Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show() }
                }

                override fun updatePaymentState(paymentState: com.flowpay.app.states.PaymentState) {
                    Log.d(TAG, "Payment state updated: ${paymentState::class.simpleName}")
                }

                override fun navigateToSetup() {
                    startActivity(Intent(this@MainActivity, SetupActivity::class.java))
                    finish()
                }

                override fun navigateToTestConfiguration() {
                    startActivity(Intent(this@MainActivity, TestConfigurationActivity::class.java))
                    finish()
                }

                override fun finishActivity() {
                    finish()
                }

                override fun showOverlayPermissionExplanation() {
                    mainViewModel.onOverlayPermissionNeeded()
                }

                override fun launchQRScanner(intent: Intent) {
                    qrScannerLauncher.launch(intent)
                }

                override fun requestPhonePermissions() {
                    phonePermissionLauncher.launch(PermissionConstants.PHONE_PERMISSIONS)
                }
            }
        )

        helper.initialize()

        // The launch gate must know which screen to show before rendering, so
        // these two SharedPreferences flags are read synchronously. Reading
        // them faults the prefs file in once (a small, one-time disk read);
        // annotate it as permitted so the debug StrictMode tripwire stays
        // sharp for genuinely unexpected main-thread disk I/O instead of
        // crying wolf on this known-safe read every launch.
        val setupCompleted: Boolean
        val testCompleted: Boolean
        val oldPolicy = android.os.StrictMode.allowThreadDiskReads()
        try {
            setupCompleted = helper.isSetupCompleted()
            testCompleted = helper.isTestCompleted()
        } finally {
            android.os.StrictMode.setThreadPolicy(oldPolicy)
        }

        if (!setupCompleted) {
            helper.navigateToSetup()
            return
        }
        if (!testCompleted) {
            helper.navigateToTestConfiguration()
            return
        }

        setContent {
            FlowpayTheme {
                MainScreen(
                    onInitiateTransfer = { phoneNumber, amount ->
                        helper.initiateTransfer(phoneNumber, amount)
                    },
                    onQRScanClick = {
                        helper.startQRScanning()
                    },
                    onRequestOverlayPermission = {
                        PermissionManager(this).overlayPermissionSettingsIntent()?.let {
                            overlayPermissionLauncher.launch(it)
                        }
                    }
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        helper.onPause()
    }

    override fun onResume() {
        super.onResume()
        helper.onResume()
        enforceBlackStatusBar()
    }

    private fun enforceBlackStatusBar() {
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        // Dark bars => light (white) icons. WindowInsetsControllerCompat
        // routes through WindowInsetsController on API 30+ and the legacy
        // systemUiVisibility flags on API 29, replacing the deprecated
        // direct flag manipulation.
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = false
    }

    override fun onStop() {
        super.onStop()
        helper.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        helper.onDestroy()
    }
}

// Payment Action Buttons - QR scan + Pay Contact.
//
// Each rail is locked until its own connectivity test has passed: Scan QR
// dials *99#, Pay Contact dials the UPI 123 IVR. One composable per button,
// because the locked/unlocked branches on both pushed the combined function
// past detekt's complexity and parameter limits.
@Composable
fun PaymentActionButtons(
    onQRScanClick: () -> Unit,
    onPayContactClick: () -> Unit,
    isUpi123Ready: Boolean,
    isUssdReady: Boolean,
    isScanning: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Tap is only gated by isScanning — same as the original button:
        // tapping while the *99# test hasn't passed still fires onClick,
        // which routes to setup rather than opening the scanner.
        com.flowpay.app.ui.components.FormTabButton(
            icon = when {
                !isUssdReady -> Icons.Default.Lock
                isScanning -> Icons.Default.QrCode
                else -> Icons.Default.QrCodeScanner
            },
            label = when {
                !isUssdReady -> stringResource(R.string.home_setup_ussd)
                isScanning -> stringResource(R.string.home_scan_opening)
                else -> stringResource(R.string.home_scan_qr)
            },
            sublabel = "*99# USSD",
            enabled = !isScanning,
            isPrimary = isUssdReady,
            onClick = onQRScanClick,
            modifier = Modifier.weight(1f)
        )
        com.flowpay.app.ui.components.FormTabButton(
            icon = if (isUpi123Ready) Icons.Default.Person else Icons.Default.Lock,
            label = if (isUpi123Ready) {
                stringResource(R.string.home_pay_contact)
            } else {
                stringResource(R.string.home_setup_upi123)
            },
            sublabel = "UPI 123 IVR",
            enabled = true,
            isPrimary = false,
            onClick = onPayContactClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onInitiateTransfer: (String, String) -> Unit,
    onQRScanClick: () -> Unit,
    onRequestOverlayPermission: () -> Unit
) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    var savedBank by remember {
        mutableStateOf(sharedPreferences.getString(AppConstants.KEY_SELECTED_BANK, "hdfc") ?: "hdfc")
    }

    // Pay Contact dials the UPI 123 IVR, so it stays inactive until the
    // UPI 123 configuration test has passed (re-checked on every resume so
    // completing the test activates it immediately).
    val testResultsManager = remember { TestResultsManager(context) }
    var isUpi123Ready by remember {
        mutableStateOf(testResultsManager.getTestResults()?.upi123Enabled == true)
    }

    // Scan QR dials *99#, so it stays locked until the *99# test has passed —
    // the same gate Pay Contact has always had for the UPI 123 IVR. Without
    // it the scanner opened, read a QR, and only then dialled a rail this SIM
    // may not support.
    var isUssdReady by remember {
        mutableStateOf(testResultsManager.getTestResults()?.ussdEnabled == true)
    }

    LaunchedEffect(lifecycle) {
        snapshotFlow { lifecycle.currentState }.collect { state ->
            if (state == Lifecycle.State.RESUMED) {
                savedBank = sharedPreferences.getString(AppConstants.KEY_SELECTED_BANK, "hdfc") ?: "hdfc"
                isUpi123Ready = testResultsManager.getTestResults()?.upi123Enabled == true
                isUssdReady = testResultsManager.getTestResults()?.ussdEnabled == true
            }
        }
    }

    val transactionViewModel: TransactionViewModel = viewModel()
    val recentPayments by transactionViewModel.recentTransactions.collectAsState()
    val selectedTransaction by transactionViewModel.selectedTransaction.collectAsState()
    val isLoading by transactionViewModel.isLoading.collectAsState()
    val error by transactionViewModel.error.collectAsState()

    var showPayContact by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }

    var showSmsPermissionDialog by remember { mutableStateOf(false) }
    var pendingSmsAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showOverlayPermissionDialog by remember { mutableStateOf(false) }
    val hostActivity = remember(context) { context.findComponentActivity() }
    val permissionManager = remember(hostActivity) {
        hostActivity?.let { PermissionManager(it) }
    }

    // Runs the queued action (start scan / open pay dialog / initiate transfer)
    // once RECEIVE_SMS is granted; the launcher stays Compose-scoped so no
    // Activity-level callback bridge is needed.
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingSmsAction?.invoke()
        } else {
            Toast.makeText(
                context,
                R.string.error_sms_permission_required,
                Toast.LENGTH_LONG
            ).show()
        }
        pendingSmsAction = null
    }

    // POST_NOTIFICATIONS backs the payment-outcome notification — the
    // fallback the result screen relies on when its direct background launch
    // is blocked (see PaymentResultNotifier). It is best-effort, not a
    // prerequisite, so we ask ONCE at the first payment and never block on the
    // result: the payment proceeds whether granted or not. Settings offers a
    // later toggle for anyone who declines.
    val postNotificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* best-effort: outcome recorded by the OS; nothing to do here */ }
    val maybeAskNotifications = remember(context) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val alreadyAsked = sharedPreferences.getBoolean(AppConstants.KEY_NOTIFICATIONS_ASKED, false)
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!alreadyAsked && !granted) {
                    sharedPreferences.edit()
                        .putBoolean(AppConstants.KEY_NOTIFICATIONS_ASKED, true)
                        .apply()
                    postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    // One-shot events from MainActivity (launchers + business-logic helper),
    // replacing the former static @Volatile callbacks on its companion.
    val mainViewModel: MainViewModel = viewModel()
    LaunchedEffect(Unit) {
        mainViewModel.events.collect { event ->
            when (event) {
                MainUiEvent.QrScannerClosed -> isScanning = false
                MainUiEvent.OverlayPermissionNeeded -> showOverlayPermissionDialog = true
            }
        }
    }

    // Reset scanning + refresh list whenever the app resumes
    LaunchedEffect(lifecycle) {
        snapshotFlow { lifecycle.currentState }.collect { state ->
            if (state == Lifecycle.State.RESUMED) {
                isScanning = false
                transactionViewModel.refresh()
            }
        }
    }

    // Safety: never leave the QR button stuck in "Opening..."
    LaunchedEffect(isScanning) {
        if (isScanning) {
            kotlinx.coroutines.delay(AppConstants.USSD_SESSION_TIMEOUT)
            if (isScanning) isScanning = false
        }
    }

    val selectedBankName = when (savedBank) {
        "sbi" -> "State Bank of India"
        "hdfc" -> "HDFC Bank"
        "icici" -> "ICICI Bank"
        "axis" -> "Axis Bank"
        "kotak" -> "Kotak Mahindra Bank"
        "pnb" -> "Punjab National Bank"
        "bob" -> "Bank of Baroda"
        "yes" -> "Yes Bank"
        "idbi" -> "IDBI Bank"
        "canara" -> "Canara Bank"
        else -> "HDFC Bank"
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 420.dp)
                    .align(Alignment.Center)
                    .background(Color.Black)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // Header — flat record, not a floating gradient card. A single
                // bottom hairline rule closes it off instead of elevation/shadow.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .padding(bottom = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.home_wordmark),
                                    style = com.flowpay.app.ui.theme.FlowpayDisplayStyle,
                                    fontSize = 26.sp,
                                    color = com.flowpay.app.ui.theme.FlowpayInkWarm
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.home_tagline),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    letterSpacing = 1.sp,
                                    color = FlowpayTextLightGray
                                )
                            }

                            // Settings Button — squared, bordered, not a
                            // translucent-white circle.
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(1.dp, com.flowpay.app.ui.theme.FlowpayLedgerRule, RoundedCornerShape(6.dp))
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) {
                                        context.startActivity(Intent(context, SettingsActivity::class.java))
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    contentDescription = "Settings",
                                    tint = com.flowpay.app.ui.theme.FlowpayInkWarm,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Verified seal + connected-bank reference, in the
                        // register of a stamped bank record rather than a
                        // gradient info pill.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            com.flowpay.app.ui.components.SealBadge(text = "VERIFIED")
                            Text(
                                text = selectedBankName,
                                style = com.flowpay.app.ui.theme.FlowpayMonoStyle.copy(
                                    fontSize = 12.sp,
                                    color = FlowpayTextLightGray
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = com.flowpay.app.ui.theme.FlowpayLedgerRule.copy(alpha = 0.4f)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                PaymentActionButtons(
                    onQRScanClick = {
                        maybeAskNotifications()
                        val hasSms = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECEIVE_SMS
                        ) == PackageManager.PERMISSION_GRANTED
                        when {
                            // *99# not verified yet — the button is in its
                            // "Set up *99#" state; take the user to the test
                            // screen rather than opening a scanner whose
                            // payment rail has not been shown to work.
                            !isUssdReady -> {
                                context.startActivity(
                                    Intent(context, TestConfigurationActivity::class.java)
                                )
                            }
                            !hasSms -> {
                                pendingSmsAction = {
                                    isScanning = true
                                    onQRScanClick()
                                }
                                showSmsPermissionDialog = true
                            }
                            else -> {
                                isScanning = true
                                onQRScanClick()
                            }
                        }
                    },
                    onPayContactClick = {
                        maybeAskNotifications()
                        val hasSms = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECEIVE_SMS
                        ) == PackageManager.PERMISSION_GRANTED
                        when {
                            // UPI 123 IVR not verified yet — the button is in its
                            // "Set up UPI 123 IVR" state; take the user to the
                            // *99# / UPI 123 test screen instead of the pay dialog.
                            !isUpi123Ready -> {
                                context.startActivity(
                                    Intent(context, TestConfigurationActivity::class.java)
                                )
                            }
                            // Overlay permission is required before the payment
                            // call can show its UI, so ask now — not after the
                            // user has filled in the transfer details.
                            !PermissionManager.canDrawOverlays(context) -> {
                                showOverlayPermissionDialog = true
                            }
                            !hasSms -> {
                                pendingSmsAction = { showPayContact = true }
                                showSmsPermissionDialog = true
                            }
                            else -> showPayContact = true
                        }
                    },
                    isUpi123Ready = isUpi123Ready,
                    isUssdReady = isUssdReady,
                    isScanning = isScanning
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Recent Payments — a ledger, not a floating rounded card.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.home_recent_payments_title),
                                style = com.flowpay.app.ui.theme.FlowpayDisplayStyle,
                                fontSize = 16.sp,
                                color = com.flowpay.app.ui.theme.FlowpayInkWarm
                            )
                            Text(
                                text = stringResource(R.string.home_recent_payments_subtitle),
                                fontSize = 12.sp,
                                color = FlowpayTextLightGray
                            )
                        }

                        TextButton(
                            onClick = {
                                context.startActivity(Intent(context, TransactionHistoryActivity::class.java))
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.home_view_all),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = com.flowpay.app.ui.theme.FlowpayInkWarm,
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                            )
                        }
                    }

                    HorizontalDivider(
                        thickness = 1.dp,
                        color = com.flowpay.app.ui.theme.FlowpayLedgerRule.copy(alpha = 0.4f),
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )

                    when {
                        isLoading -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    color = com.flowpay.app.ui.theme.FlowpayLedgerRule,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    text = stringResource(R.string.home_loading_transactions),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = FlowpayTextLightGray
                                )
                            }
                        }

                        error != null -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.home_failed_to_load),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = com.flowpay.app.ui.theme.FlowpayInkWarm
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = error ?: stringResource(R.string.home_unknown_error),
                                    fontSize = 13.sp,
                                    color = FlowpayTextLightGray,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                TextButton(onClick = { transactionViewModel.refresh() }) {
                                    Text(
                                        text = stringResource(R.string.action_retry),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = com.flowpay.app.ui.theme.FlowpayInkWarm
                                    )
                                }
                            }
                        }

                        recentPayments.isEmpty() -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "No transactions",
                                    modifier = Modifier.size(32.dp),
                                    tint = FlowpayTextLightGray
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.home_no_transactions_yet),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = com.flowpay.app.ui.theme.FlowpayInkWarm
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.home_payment_history_placeholder),
                                    fontSize = 13.sp,
                                    color = FlowpayTextLightGray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        else -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                recentPayments.forEachIndexed { index, payment ->
                                    TransactionItem(
                                        payment = payment,
                                        onClick = {
                                            transactionViewModel.selectTransaction(payment.id)
                                        },
                                        showDivider = index < recentPayments.lastIndex
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Detail dialog for a tapped Recent Payments row — the same
            // surface the history screen shows.
            selectedTransaction?.let { transaction ->
                TransactionDetailDialog(
                    transaction = transaction,
                    onDismiss = { transactionViewModel.clearSelectedTransaction() },
                    onDelete = {
                        transactionViewModel.deleteTransaction(transaction)
                        transactionViewModel.clearSelectedTransaction()
                    }
                )
            }

            // Dialogs
            if (showPayContact) {
                PayContactDialog(
                    onDismiss = { showPayContact = false },
                    onConfirm = { phone, amt ->
                        if (permissionManager?.checkSMSPermissions() != true) {
                            pendingSmsAction = {
                                showPayContact = false
                                onInitiateTransfer(phone, amt)
                            }
                            showSmsPermissionDialog = true
                        } else {
                            showPayContact = false
                            onInitiateTransfer(phone, amt)
                        }
                    }
                )
            }

            if (showOverlayPermissionDialog) {
                PermissionExplanationDialog(
                    title = "Overlay Permission",
                    message = "Flowpay needs overlay permission to show a payment UI anchor during the call. This keeps your transaction details visible while the call is in progress.",
                    confirmButtonText = "Grant",
                    onConfirm = {
                        showOverlayPermissionDialog = false
                        onRequestOverlayPermission()
                    },
                    onDismiss = { showOverlayPermissionDialog = false }
                )
            }

            if (showSmsPermissionDialog) {
                PermissionExplanationDialog(
                    title = "SMS Permission",
                    message = "Flowpay reads incoming bank SMS only while a payment is in progress, to detect the confirmation. It never reads your inbox and nothing leaves the device.",
                    confirmButtonText = "Grant",
                    onConfirm = {
                        showSmsPermissionDialog = false
                        // Only RECEIVE_SMS is declared in the manifest and
                        // needed (the app never reads the inbox). The launcher's
                        // callback runs pendingSmsAction once granted.
                        smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                    },
                    onDismiss = {
                        showSmsPermissionDialog = false
                        pendingSmsAction = null
                    }
                )
            }
        }
    }
}

@Composable
fun TransactionItem(payment: PaymentDetails, onClick: () -> Unit, showDivider: Boolean = true) {
    // FAILED and CANCELLED read as "this payment did not go through" — a
    // cross, not the same outgoing arrow a successful payment gets. Every
    // other outcome (COMPLETED, PENDING, NEEDS_REVIEW, UNVERIFIED) keeps the
    // arrow: this row has no separate status chip (unlike transaction
    // history), so the icon is the only signal here.
    val failed = payment.status == PaymentStatus.FAILED || payment.status == PaymentStatus.CANCELLED
    val tint = if (failed) FlowpayStatusError else com.flowpay.app.ui.theme.FlowpayInkWarm

    com.flowpay.app.ui.components.LedgerRow(
        title = payment.recipientName ?: payment.phoneNumber,
        subtitle = formatDate(payment.timestamp),
        trailingText = stringResource(R.string.amount_rupees, CurrencyFormat.inr(payment.amount)),
        trailingColor = tint,
        trailingIcon = if (failed) Icons.Default.Close else Icons.Default.ArrowOutward,
        trailingIconDescription = stringResource(
            if (failed) R.string.cd_payment_failed else R.string.cd_payment_outgoing
        ),
        onClick = onClick,
        showDivider = showDivider
    )
}

@OptIn(ExperimentalMaterial3Api::class)
// a self-contained bottom-sheet form with its own validation, contact-picker, and permission-dialog branches
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun PayContactDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    val context = LocalContext.current
    val hostActivity = remember(context) { context.findComponentActivity() }
    var phoneNumber by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    // UPI 123Pay's IVR will not accept 5000 or more (AppConstants
    // .UPI123PAY_MAX_AMOUNT = 4999), so the cap is enforced before dialling.
    val isOverCap = (amount.toLongOrNull() ?: 0L) > AppConstants.UPI123PAY_MAX_AMOUNT.toLong()
    var selectedContactName by remember { mutableStateOf<String?>(null) }
    var showContactPicker by remember { mutableStateOf(false) }
    var showContactPermissionDialog by remember { mutableStateOf(false) }
    val permissionManager = remember(hostActivity) {
        hostActivity?.let { PermissionManager(it) }
    }
    val contactPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showContactPicker = true
    }
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ledgerRule = com.flowpay.app.ui.theme.FlowpayLedgerRule
    val ink = com.flowpay.app.ui.theme.FlowpayInkWarm

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = FlowpayDarkGray,
        dragHandle = { com.flowpay.app.ui.components.LedgerDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = stringResource(R.string.home_pay_contact),
                style = com.flowpay.app.ui.theme.FlowpayDisplayStyle,
                fontSize = 20.sp,
                color = ink
            )
            Spacer(modifier = Modifier.height(18.dp))

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                selectedContactName?.let { name ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = ledgerRule,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.home_sending_to, name),
                            color = ink,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = {
                            if (it.all { char -> char.isDigit() } && it.length <= 10) {
                                phoneNumber = it
                                selectedContactName = null
                            }
                        },
                        label = {
                            Text(stringResource(R.string.home_field_mobile_label), color = FlowpayTextLightGray)
                        },
                        placeholder = {
                            Text(stringResource(R.string.home_field_mobile_hint), color = FlowpayTextGray)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = com.flowpay.app.ui.theme.FlowpayRecordShape,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = ink,
                            unfocusedTextColor = ink,
                            focusedBorderColor = ink,
                            unfocusedBorderColor = ledgerRule,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )

                    IconButton(
                        onClick = {
                            val pm = permissionManager
                            if (pm == null) {
                                Toast.makeText(
                                    context.applicationContext,
                                    R.string.error_contacts_unavailable,
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@IconButton
                            }
                            if (pm.hasContactPermission()) {
                                showContactPicker = true
                            } else {
                                showContactPermissionDialog = true
                            }
                        },
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(48.dp)
                            .border(1.5.dp, ledgerRule, com.flowpay.app.ui.theme.FlowpayRecordShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PermContactCalendar,
                            contentDescription = "Select Contact",
                            tint = ink
                        )
                    }
                }

                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        if (it.all { char -> char.isDigit() } && it.length <= 6) {
                            amount = it
                        }
                    },
                    label = {
                        Text(stringResource(R.string.home_field_amount_label), color = FlowpayTextLightGray)
                    },
                    placeholder = {
                        Text(stringResource(R.string.home_field_amount_hint), color = FlowpayTextGray)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = isOverCap,
                    shape = com.flowpay.app.ui.theme.FlowpayRecordShape,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ink,
                        unfocusedTextColor = ink,
                        focusedBorderColor = if (isOverCap) FlowpayStatusError else ink,
                        unfocusedBorderColor = if (isOverCap) FlowpayStatusError else ledgerRule,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )

                // Told here, in the dialog, while the number can still be
                // corrected. The IVR itself only rejects an over-cap amount
                // mid-call, after the user has already dialled.
                if (isOverCap) {
                    Text(
                        text = Upi123CallStringBuilder.Reason.AMOUNT_ABOVE_CAP.messageFor(context),
                        color = FlowpayStatusError,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            val canTransfer = phoneNumber.length == 10 && amount.isNotEmpty() &&
                amount != "0" && !isOverCap
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                androidx.compose.material3.OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = com.flowpay.app.ui.theme.FlowpayRecordShapeLarge,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, ledgerRule)
                ) {
                    Text(stringResource(R.string.action_cancel), color = FlowpayTextLightGray)
                }
                androidx.compose.material3.Button(
                    onClick = { onConfirm(phoneNumber, amount) },
                    enabled = canTransfer,
                    modifier = Modifier.weight(1f),
                    shape = com.flowpay.app.ui.theme.FlowpayRecordShapeLarge,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = ink,
                        contentColor = com.flowpay.app.ui.theme.FlowpaySurfaceDim,
                        disabledContainerColor = ledgerRule.copy(alpha = 0.3f),
                        disabledContentColor = FlowpayTextGray
                    )
                ) {
                    Text(stringResource(R.string.action_transfer), fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showContactPicker) {
        ContactPickerDialog(
            onDismiss = { showContactPicker = false },
            onContactSelected = { contact ->
                phoneNumber = contact.phoneNumber
                selectedContactName = contact.name
                showContactPicker = false
            }
        )
    }

    if (showContactPermissionDialog) {
        PermissionExplanationDialog(
            title = "Contacts Permission",
            message = "Flowpay needs access to your contacts so you can pick a recipient by name instead of typing their number.",
            confirmButtonText = "Grant",
            onConfirm = {
                showContactPermissionDialog = false
                contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            },
            onDismiss = { showContactPermissionDialog = false }
        )
    }
}

@Composable
fun PermissionExplanationDialog(
    title: String,
    message: String,
    confirmButtonText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FlowpayDarkGray,
        title = {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        },
        text = {
            Text(
                text = message,
                fontSize = 14.sp,
                color = FlowpayTextPale,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = com.flowpay.app.ui.theme.FlowpayInkWarm
                )
            ) {
                Text(confirmButtonText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_not_now), color = FlowpayTextLightGray)
            }
        }
    )
}

// Utility functions
fun formatDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("dd MMM, HH:mm", Locale("en", "IN"))
    return formatter.format(Date(timestamp))
}
