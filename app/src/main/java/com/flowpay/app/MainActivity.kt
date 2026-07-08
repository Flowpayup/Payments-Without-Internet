// MainActivity.kt - UI Only
// Glasses + Signal Check features removed to match this tree's slim
// MainActivityHelper (6-method UICallback, no Meta/glasses dependencies).

package com.flowpay.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PermContactCalendar
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flowpay.app.constants.AppConstants
import com.flowpay.app.constants.PermissionConstants
import com.flowpay.app.data.PaymentDetails
import com.flowpay.app.data.PaymentStatus
import com.flowpay.app.helpers.MainActivityHelper
import com.flowpay.app.managers.PermissionManager
import com.flowpay.app.ui.activities.SettingsActivity
import com.flowpay.app.ui.activities.TransactionHistoryActivity
import com.flowpay.app.ui.dialogs.ContactPickerDialog
import com.flowpay.app.ui.theme.BlueAccentTheme
import com.flowpay.app.ui.theme.FlowpayTheme
import com.flowpay.app.ui.theme.LocalFlowpayAccentTheme
import com.flowpay.app.utils.findComponentActivity
import com.flowpay.app.viewmodel.TransactionViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "Flowpay"
        const val QR_SCAN_REQUEST_CODE = 1001

        @Volatile
        var showCallDurationDialogCallback: (() -> Unit)? = null
        @Volatile
        var showCallSuccessDialogCallback: (() -> Unit)? = null
        @Volatile
        var dismissCallSuccessDialogCallback: (() -> Unit)? = null
        @Volatile
        var resetQRScanningStateCallback: (() -> Unit)? = null
        @Volatile
        var showOverlayPermissionDialogCallback: (() -> Unit)? = null
        @Volatile
        var onSmsPermissionGrantedCallback: (() -> Unit)? = null
    }

    // Helper for all business logic
    private lateinit var helper: MainActivityHelper

    // QR Scanner permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        helper.handleQRPermissionResult(permissions)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(R.style.Theme_Flowpay)

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
        helper = MainActivityHelper(this, object : MainActivityHelper.UICallback {
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
                runOnUiThread { showOverlayPermissionDialogCallback?.invoke() }
            }
        })

        helper.initialize()

        if (!helper.isSetupCompleted()) {
            helper.navigateToSetup()
            return
        }
        if (!helper.isTestCompleted()) {
            helper.navigateToTestConfiguration()
            return
        }

        setContent {
            CompositionLocalProvider(LocalFlowpayAccentTheme provides BlueAccentTheme) {
                FlowpayTheme {
                    MainScreen(
                        onInitiateTransfer = { phoneNumber, amount ->
                            helper.initiateTransfer(phoneNumber, amount)
                        },
                        onQRScanClick = {
                            helper.startQRScanning()
                        }
                    )
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == QR_SCAN_REQUEST_CODE) {
            Log.d(TAG, "QR scan finished with result code: $resultCode")
            resetQRScanningStateCallback?.invoke()
        }
        helper.handleActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        val success = helper.handlePermissionResult(requestCode, permissions, grantResults)

        when (requestCode) {
            PermissionConstants.SMS_PERMISSION_REQUEST_CODE -> {
                if (success) {
                    onSmsPermissionGrantedCallback?.invoke()
                    onSmsPermissionGrantedCallback = null
                } else {
                    Toast.makeText(
                        this,
                        "SMS permission is required to detect payment confirmations",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            PermissionConstants.PERMISSIONS_REQUEST_CODE -> {
                if (success) {
                    Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Some permissions were denied", Toast.LENGTH_SHORT).show()
                }
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

// Custom QR Frame Icon - Bracket-style scan frame
@Composable
fun QRFrameIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 4.dp.toPx()
        val cornerLength = size.width * 0.25f

        drawLine(tint, Offset(0f, cornerLength), Offset(0f, 0f), strokeWidth, cap = StrokeCap.Round)
        drawLine(tint, Offset(0f, 0f), Offset(cornerLength, 0f), strokeWidth, cap = StrokeCap.Round)
        drawLine(tint, Offset(size.width - cornerLength, 0f), Offset(size.width, 0f), strokeWidth, cap = StrokeCap.Round)
        drawLine(tint, Offset(size.width, 0f), Offset(size.width, cornerLength), strokeWidth, cap = StrokeCap.Round)
        drawLine(tint, Offset(0f, size.height - cornerLength), Offset(0f, size.height), strokeWidth, cap = StrokeCap.Round)
        drawLine(tint, Offset(0f, size.height), Offset(cornerLength, size.height), strokeWidth, cap = StrokeCap.Round)
        drawLine(tint, Offset(size.width - cornerLength, size.height), Offset(size.width, size.height), strokeWidth, cap = StrokeCap.Round)
        drawLine(tint, Offset(size.width, size.height - cornerLength), Offset(size.width, size.height), strokeWidth, cap = StrokeCap.Round)
    }
}

// Payment Action Buttons - QR scan + Pay Contact
@Composable
fun PaymentActionButtons(
    onQRScanClick: () -> Unit,
    onPayContactClick: () -> Unit,
    isScanning: Boolean,
    modifier: Modifier = Modifier
) {
    var isQRPressed by remember { mutableStateOf(false) }
    var isPayPressed by remember { mutableStateOf(false) }

    val qrButtonScale by animateFloatAsState(
        targetValue = if (isQRPressed) 0.94f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "QR Button Scale"
    )
    val payButtonScale by animateFloatAsState(
        targetValue = if (isPayPressed) 0.94f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "Pay Button Scale"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Scan QR Button
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .shadow(
                        elevation = 12.dp,
                        shape = CircleShape,
                        ambientColor = LocalFlowpayAccentTheme.current.headerGradientStart.copy(alpha = 0.3f),
                        spotColor = LocalFlowpayAccentTheme.current.headerGradientEnd.copy(alpha = 0.4f)
                    )
                    .scale(qrButtonScale)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                LocalFlowpayAccentTheme.current.headerGradientStart,
                                LocalFlowpayAccentTheme.current.headerGradientEnd
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(1f, 1f)
                        ),
                        shape = CircleShape
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                if (!isScanning) {
                                    isQRPressed = true
                                    tryAwaitRelease()
                                    isQRPressed = false
                                }
                            },
                            onTap = { if (!isScanning) onQRScanClick() }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isScanning) Icons.Default.QrCode else Icons.Default.QrCodeScanner,
                    contentDescription = "Scan QR Code",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            Text(
                text = if (isScanning) "Opening..." else "Scan QR Code",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), 3f)
                )
            )
        }

        // OR Divider
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .width(50.dp)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.3f))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.1f), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "OR",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .width(50.dp)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.3f))
            )
        }

        // Pay Contact Button
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(20.dp),
                        ambientColor = LocalFlowpayAccentTheme.current.headerGradientStart.copy(alpha = 0.3f),
                        spotColor = LocalFlowpayAccentTheme.current.headerGradientEnd.copy(alpha = 0.4f)
                    )
                    .scale(payButtonScale)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                LocalFlowpayAccentTheme.current.headerGradientStart,
                                LocalFlowpayAccentTheme.current.headerGradientEnd
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(1f, 1f)
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isPayPressed = true
                                tryAwaitRelease()
                                isPayPressed = false
                            },
                            onTap = { onPayContactClick() }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Pay Contact",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            Text(
                text = "Pay Contact",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), 3f)
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onInitiateTransfer: (String, String) -> Unit,
    onQRScanClick: () -> Unit
) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    var savedBank by remember {
        mutableStateOf(sharedPreferences.getString(AppConstants.KEY_SELECTED_BANK, "hdfc") ?: "hdfc")
    }

    LaunchedEffect(lifecycle) {
        snapshotFlow { lifecycle.currentState }.collect { state ->
            if (state == Lifecycle.State.RESUMED) {
                savedBank = sharedPreferences.getString(AppConstants.KEY_SELECTED_BANK, "hdfc") ?: "hdfc"
            }
        }
    }

    val transactionViewModel: TransactionViewModel = viewModel()
    val recentPayments by transactionViewModel.recentTransactions.collectAsState()
    val isLoading by transactionViewModel.isLoading.collectAsState()
    val error by transactionViewModel.error.collectAsState()

    var showPayContact by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }
    var showCallDurationDialog by remember { mutableStateOf(false) }
    var showCallSuccessDialog by remember { mutableStateOf(false) }

    var showSmsPermissionDialog by remember { mutableStateOf(false) }
    var pendingSmsAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showOverlayPermissionDialog by remember { mutableStateOf(false) }
    val hostActivity = remember(context) { context.findComponentActivity() }
    val permissionManager = remember(hostActivity) {
        hostActivity?.let { PermissionManager(it) }
    }

    LaunchedEffect(Unit) {
        MainActivity.showCallDurationDialogCallback = { showCallDurationDialog = true }
        MainActivity.showCallSuccessDialogCallback = { showCallSuccessDialog = true }
        MainActivity.dismissCallSuccessDialogCallback = { showCallSuccessDialog = false }
        MainActivity.resetQRScanningStateCallback = { isScanning = false }
        MainActivity.showOverlayPermissionDialogCallback = { showOverlayPermissionDialog = true }
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

                // Header Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .height(220.dp)
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(20.dp),
                            ambientColor = Color.Black.copy(alpha = 0.15f),
                            spotColor = Color.Black.copy(alpha = 0.15f)
                        ),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        LocalFlowpayAccentTheme.current.headerGradientStart,
                                        LocalFlowpayAccentTheme.current.headerGradientEnd
                                    )
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
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
                                        text = "Flowpay",
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        style = TextStyle(
                                            shadow = Shadow(Color.Black.copy(alpha = 0.15f), Offset(0f, 2f), 6f)
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Payments Without Internet",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = Color.White.copy(alpha = 0.95f)
                                    )
                                }

                                // Settings Button
                                Box(
                                    modifier = Modifier
                                        .padding(top = 8.dp, end = 8.dp)
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.22f))
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
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Bank Info Section
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(LocalFlowpayAccentTheme.current.headerGradientEnd)
                                    .padding(horizontal = 18.dp, vertical = 14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "Connected Bank",
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.85f),
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = selectedBankName,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.22f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AccountBalanceWallet,
                                            contentDescription = "Wallet",
                                            tint = Color.White,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                PaymentActionButtons(
                    onQRScanClick = {
                        val hasSms = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECEIVE_SMS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!hasSms) {
                            pendingSmsAction = {
                                isScanning = true
                                onQRScanClick()
                            }
                            showSmsPermissionDialog = true
                        } else {
                            isScanning = true
                            onQRScanClick()
                        }
                    },
                    onPayContactClick = {
                        val hasSms = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECEIVE_SMS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!hasSms) {
                            pendingSmsAction = { showPayContact = true }
                            showSmsPermissionDialog = true
                        } else {
                            showPayContact = true
                        }
                    },
                    isScanning = isScanning
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Recent Transactions
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFF0A0A0A), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = "History",
                                        modifier = Modifier.size(20.dp),
                                        tint = LocalFlowpayAccentTheme.current.headerGradientStart
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Recent Payments",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Your latest transactions",
                                        fontSize = 12.sp,
                                        color = Color(0xFF888888)
                                    )
                                }
                            }

                            TextButton(
                                onClick = {
                                    context.startActivity(Intent(context, TransactionHistoryActivity::class.java))
                                }
                            ) {
                                Text(
                                    text = "View All",
                                    fontSize = 12.sp,
                                    color = LocalFlowpayAccentTheme.current.accent
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        when {
                            isLoading -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(32.dp),
                                        color = LocalFlowpayAccentTheme.current.headerGradientStart,
                                        strokeWidth = 3.dp
                                    )
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Text(
                                        text = "Loading transactions...",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF888888)
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
                                        text = "Failed to load transactions",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = error ?: "Unknown error",
                                        fontSize = 13.sp,
                                        color = Color(0xFF888888),
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    TextButton(onClick = { transactionViewModel.refresh() }) {
                                        Text(
                                            text = "Retry",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = LocalFlowpayAccentTheme.current.headerGradientStart
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
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .background(Color(0xFF2A2A2A), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = "No transactions",
                                            modifier = Modifier.size(32.dp),
                                            tint = Color(0xFF888888)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Text(
                                        text = "No transactions yet",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Your payment history will appear here",
                                        fontSize = 13.sp,
                                        color = Color(0xFF888888),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            else -> {
                                LazyColumn(
                                    modifier = Modifier.height(280.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(recentPayments) { payment ->
                                        TransactionItem(payment = payment)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
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

            if (showCallDurationDialog) {
                CallDurationIssueDialog(onDismiss = { showCallDurationDialog = false })
            }

            if (showCallSuccessDialog) {
                CallSuccessDialog(onDismiss = { showCallSuccessDialog = false })
            }

            if (showOverlayPermissionDialog) {
                PermissionExplanationDialog(
                    title = "Overlay Permission",
                    message = "Flowpay needs overlay permission to show a payment UI anchor during the call. This keeps your transaction details visible while the call is in progress.",
                    confirmButtonText = "Grant",
                    onConfirm = {
                        showOverlayPermissionDialog = false
                        permissionManager?.requestOverlayPermission()
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
                        hostActivity?.let { activity ->
                            MainActivity.onSmsPermissionGrantedCallback = pendingSmsAction
                            pendingSmsAction = null
                            androidx.core.app.ActivityCompat.requestPermissions(
                                activity,
                                arrayOf(
                                    Manifest.permission.RECEIVE_SMS,
                                    Manifest.permission.READ_SMS
                                ),
                                PermissionConstants.SMS_PERMISSION_REQUEST_CODE
                            )
                        }
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

fun isNotificationListenerEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(
        context.contentResolver, "enabled_notification_listeners"
    )
    return flat?.contains(context.packageName) == true
}

@Composable
fun TransactionItem(payment: PaymentDetails) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = payment.recipientName ?: payment.phoneNumber,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = formatDate(payment.timestamp),
                    fontSize = 14.sp,
                    color = Color(0xFFAAAAAA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = formatAmount(payment.amount),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = LocalFlowpayAccentTheme.current.headerGradientStart,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.ArrowOutward,
                    contentDescription = "Outgoing",
                    modifier = Modifier.size(18.dp),
                    tint = LocalFlowpayAccentTheme.current.headerGradientStart
                )
            }
        }
    }
}

@Composable
fun PayContactDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    val context = LocalContext.current
    val hostActivity = remember(context) { context.findComponentActivity() }
    var phoneNumber by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var selectedContactName by remember { mutableStateOf<String?>(null) }
    var showContactPicker by remember { mutableStateOf(false) }
    var showContactPermissionDialog by remember { mutableStateOf(false) }
    val permissionManager = remember(hostActivity) {
        hostActivity?.let { PermissionManager(it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        title = {
            Text(
                text = "Pay Contact",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                selectedContactName?.let { name ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = LocalFlowpayAccentTheme.current.accent.copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = LocalFlowpayAccentTheme.current.accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sending to: $name",
                                color = LocalFlowpayAccentTheme.current.accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
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
                        label = { Text("Mobile Number", color = Color(0xFF8A8A8A)) },
                        placeholder = { Text("10 digits", color = Color(0xFF6A6A6A)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF4A4A4A),
                            unfocusedBorderColor = Color(0xFF3A3A3A),
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
                                    "Unable to open contacts from this screen.",
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
                            .padding(top = 8.dp)
                            .size(48.dp)
                            .background(
                                color = LocalFlowpayAccentTheme.current.accent.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PermContactCalendar,
                            contentDescription = "Select Contact",
                            tint = LocalFlowpayAccentTheme.current.accent
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
                    label = { Text("Amount (₹)", color = Color(0xFF8A8A8A)) },
                    placeholder = { Text("Enter amount", color = Color(0xFF6A6A6A)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF4A4A4A),
                        unfocusedBorderColor = Color(0xFF3A3A3A),
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(phoneNumber, amount) },
                enabled = phoneNumber.length == 10 && amount.isNotEmpty() && amount != "0"
            ) {
                Text(
                    "Transfer",
                    color = if (phoneNumber.length == 10 && amount.isNotEmpty())
                        LocalFlowpayAccentTheme.current.accent else Color(0xFF6A6A6A)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF8A8A8A))
            }
        }
    )

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
                permissionManager?.requestContactPermission()
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
        containerColor = Color(0xFF1A1A1A),
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
                color = Color(0xFFCCCCCC),
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = LocalFlowpayAccentTheme.current.accent
                )
            ) {
                Text(confirmButtonText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not now", color = Color(0xFF8A8A8A))
            }
        }
    )
}

@Composable
fun CallDurationIssueDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        title = {
            Text(
                text = "Payment Issue",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Transaction failed - the call ended too early.",
                    color = Color(0xFFCCCCCC),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "This usually means:",
                    color = Color(0xFFCCCCCC),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                Text(
                    text = "• Daily payment limit reached\n• UPI not configured",
                    color = Color(0xFFAAAAAA),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = LocalFlowpayAccentTheme.current.accentLight
                )
            ) {
                Text(text = "OK", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    )
}

@Composable
fun CallSuccessDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        title = {
            Text(
                text = "Great!",
                color = LocalFlowpayAccentTheme.current.accentLight,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Your request has been sent to your bank.",
                    color = Color(0xFFCCCCCC),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "When the confirmation call comes:",
                    color = Color(0xFFCCCCCC),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "1. Open your dialer\n2. Press 1 to confirm\n3. Enter your UPI PIN",
                    color = Color(0xFFAAAAAA),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = LocalFlowpayAccentTheme.current.accentLight
                )
            ) {
                Text(text = "Got it", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    )
}

// Utility functions
fun formatAmount(amount: Double): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    return formatter.format(amount)
}

fun formatDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("dd MMM, HH:mm", Locale("en", "IN"))
    return formatter.format(Date(timestamp))
}

fun getStatusColor(status: PaymentStatus): Color {
    return when (status) {
        PaymentStatus.COMPLETED -> Color(0xFF1F1F1F)
        PaymentStatus.PENDING -> Color(0xFF1F1F1F)
        PaymentStatus.FAILED -> Color(0xFF3D3D3D)
        PaymentStatus.CANCELLED -> Color(0xFF3D3D3D)
    }
}
