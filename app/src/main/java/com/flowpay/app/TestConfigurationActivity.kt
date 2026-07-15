package com.flowpay.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.flowpay.app.constants.PermissionConstants
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowpay.app.ui.theme.FlowpayTheme
import com.flowpay.app.ui.theme.BlueAccentTheme
import com.flowpay.app.ui.theme.FlowpayAccentGreen
import com.flowpay.app.ui.theme.FlowpayDarkGray
import com.flowpay.app.ui.theme.FlowpayLightGray
import com.flowpay.app.ui.theme.FlowpayMediumGray
import com.flowpay.app.ui.theme.FlowpayStatusWarning
import com.flowpay.app.ui.theme.FlowpaySurfaceDim
import com.flowpay.app.ui.theme.FlowpayTextGray
import com.flowpay.app.ui.theme.FlowpayTextLightGray
import com.flowpay.app.ui.theme.FlowpayTextPale
import com.flowpay.app.ui.theme.LocalFlowpayAccentTheme
import com.flowpay.app.FlowpayApplication
import com.flowpay.app.data.SettingsRepository
import androidx.compose.runtime.CompositionLocalProvider
import com.flowpay.app.R
import com.flowpay.app.helpers.TestConfigurationHelper
import com.flowpay.app.managers.CallType
import com.flowpay.app.ui.dialogs.UssdProgressDialog
import com.flowpay.app.ui.dialogs.Upi123ProgressDialog
import android.widget.Toast
import com.flowpay.app.helpers.SetupHelper
import kotlinx.coroutines.delay

class TestConfigurationActivity : ComponentActivity() {
    private lateinit var testHelper: TestConfigurationHelper

    // Phone-call permission group, requested before a test dial. No auto-retry:
    // the user re-taps the test action once granted.
    private val phonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        testHelper.onPhonePermissionsResult(results.values.all { it })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize test helper
        testHelper = TestConfigurationHelper(this, object : TestConfigurationHelper.UICallback {
            override fun showToast(message: String) {
                runOnUiThread { android.widget.Toast.makeText(this@TestConfigurationActivity, message, android.widget.Toast.LENGTH_LONG).show() }
            }

            override fun updateUssdTesting(isTesting: Boolean) {
                // State will be managed by the composable
            }

            override fun updateUssdDialog(show: Boolean) {
                // State will be managed by the composable
            }

            override fun updateUssdTestCompleted(completed: Boolean) {
                // State will be managed by the composable
            }

            override fun updateUpi123Testing(isTesting: Boolean) {
                // State will be managed by the composable
            }

            override fun updateUpi123TestCompleted(completed: Boolean) {
                // State will be managed by the composable
            }

            override fun updateUpi123Dialog(show: Boolean) {
                // State will be managed by the composable
            }

            override fun updateUpi123ConfigurationOptions(show: Boolean) {
                // State will be managed by the composable
            }

            override fun updateVoiceTesting(isTesting: Boolean) {
                // State will be managed by the composable
            }

            override fun updateVoiceDialog(show: Boolean) {
                // State will be managed by the composable
            }

            override fun updateVoiceTestCompleted(completed: Boolean) {
                // State will be managed by the composable
            }

            override fun updateCallCompleteButton(show: Boolean) {
                // State will be managed by the composable
            }

            override fun updateUssdProgressMessage(message: String) {
                // State will be managed by the composable
            }

            override fun updateUssdConfigurationOptions(show: Boolean) {
                // State will be managed by the composable
            }

            override fun navigateToMain() {
                val intent = Intent(this@TestConfigurationActivity, MainActivity::class.java)
                startActivity(intent)
                finish()
            }

            override fun requestPhonePermissions() {
                phonePermissionLauncher.launch(PermissionConstants.PHONE_PERMISSIONS)
            }
        })

        // Initialize the helper
        testHelper.initialize()

        setTheme(R.style.Theme_Flowpay)
        // Edge-to-edge: Compose insets are the single source of padding (see MainActivity).
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            CompositionLocalProvider(LocalFlowpayAccentTheme provides BlueAccentTheme) {
                FlowpayTheme {
                    TestConfigurationScreen(testHelper = testHelper)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregisters the CallManager's PhoneStateListener and cancels
        // pending timeout runnables — without this, a listener registered
        // for an in-flight test call leaks past the screen.
        if (::testHelper.isInitialized) {
            testHelper.cleanup()
        }
    }
}

@Composable
fun TestConfigurationScreen(testHelper: TestConfigurationHelper) {
    val context = LocalContext.current
    val accent = LocalFlowpayAccentTheme.current

    // Get test states from helper
    val testStates = testHelper.getTestStates()
    var ussdTestCompleted by remember { mutableStateOf(testStates.ussdTestCompleted) }
    var upi123TestCompleted by remember { mutableStateOf(testStates.upi123TestCompleted) }
    var ussdTesting by remember { mutableStateOf(testStates.ussdTesting) }
    var upi123Testing by remember { mutableStateOf(testStates.upi123Testing) }
    // Holds the pending real-call dial action until the user consents (a test
    // dial places a real *99#/UPI 123 call that may incur carrier charges).
    var pendingDial by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showUssdDialog by remember { mutableStateOf(testStates.showUssdDialog) }
    var showUpi123Dialog by remember { mutableStateOf(testStates.showUpi123Dialog) }
    var showUssdConfigurationOptions by remember { mutableStateOf(testStates.showUssdConfigurationOptions) }
    var showUpi123ConfigurationOptions by remember { mutableStateOf(testStates.showUpi123ConfigurationOptions) }
    var ussdProgressMessage by remember { mutableStateOf(testStates.ussdProgressMessage) }
    var showCallCompleteButton by remember { mutableStateOf(testStates.showCallCompleteButton) }

    // Load existing test results
    LaunchedEffect(Unit) {
        val existingResults = testHelper.getTestResults()
        if (existingResults != null) {
            ussdTestCompleted = existingResults.ussdEnabled
            upi123TestCompleted = existingResults.upi123Enabled
        }
    }

    // Update states when helper states change
    LaunchedEffect(testStates) {
        ussdTestCompleted = testStates.ussdTestCompleted
        upi123TestCompleted = testStates.upi123TestCompleted
        ussdTesting = testStates.ussdTesting
        upi123Testing = testStates.upi123Testing
        showUssdDialog = testStates.showUssdDialog
        showUpi123Dialog = testStates.showUpi123Dialog
        showUssdConfigurationOptions = testStates.showUssdConfigurationOptions
        showUpi123ConfigurationOptions = testStates.showUpi123ConfigurationOptions
        ussdProgressMessage = testStates.ussdProgressMessage
        showCallCompleteButton = testStates.showCallCompleteButton
    }

    // Add a periodic state check to ensure UI updates
    LaunchedEffect(Unit) {
        while (true) {
            delay(100) // Check every 100ms
            val currentStates = testHelper.getTestStates()
            ussdTestCompleted = currentStates.ussdTestCompleted
            upi123TestCompleted = currentStates.upi123TestCompleted
            ussdTesting = currentStates.ussdTesting
            upi123Testing = currentStates.upi123Testing
            showUssdDialog = currentStates.showUssdDialog
            showUpi123Dialog = currentStates.showUpi123Dialog
            showUssdConfigurationOptions = currentStates.showUssdConfigurationOptions
            showUpi123ConfigurationOptions = currentStates.showUpi123ConfigurationOptions
            ussdProgressMessage = currentStates.ussdProgressMessage
            showCallCompleteButton = currentStates.showCallCompleteButton
        }
    }

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

            // Back to Setup
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clickable {
                        context.startActivity(Intent(context, SetupActivity::class.java))
                        (context as? android.app.Activity)?.finish()
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.testcfg_back),
                    tint = FlowpayTextLightGray,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.testcfg_back_to_setup),
                    fontSize = 14.sp,
                    color = FlowpayTextLightGray,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Gradient Header Card
            TestHeaderCard()

            Spacer(modifier = Modifier.height(20.dp))

            // Test Instructions
            TestInstructions()

            Spacer(modifier = Modifier.height(20.dp))

            // Test Buttons Container
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // USSD Test Button
                val isJioSim = !SetupHelper.isPrimarySimUssdCapable(context)
                val userReportedUssdIssue = SetupHelper.hasUserReportedUssdNotWorking(context)
                TestButton(
                    title = stringResource(R.string.testcfg_set_up),
                    code = "*99#",
                    description = when {
                        isJioSim -> stringResource(R.string.testcfg_jio_no_ussd)
                        userReportedUssdIssue -> stringResource(R.string.testcfg_ussd_reported_issue)
                        else -> stringResource(R.string.testcfg_enable_scan_payments)
                    },
                    isCompleted = ussdTestCompleted || isJioSim || userReportedUssdIssue,
                    isTesting = ussdTesting,
                    isUnsupported = isJioSim,
                    onClick = {
                        if (isJioSim) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.testcfg_jio_no_ussd),
                                Toast.LENGTH_LONG
                            ).show()
                        } else if (!ussdTestCompleted && !ussdTesting) {
                            // Ask before placing a real *99# call. The consent
                            // dialog runs this action on confirm; ussdTesting is
                            // flipped there to guard against a double-dial.
                            pendingDial = {
                                ussdTesting = true
                                testHelper.initiateCall(CallType.USSD)
                            }
                        }
                    }
                )

                // UPI123 Test Button
                TestButton(
                    title = stringResource(R.string.testcfg_set_up),
                    code = "UPI123",
                    description = stringResource(R.string.testcfg_enable_manual_payments),
                    isCompleted = upi123TestCompleted,
                    isTesting = upi123Testing,
                    onClick = {
                        if (!upi123TestCompleted && !upi123Testing) {
                            pendingDial = {
                                upi123Testing = true
                                testHelper.initiateUpi123Test()
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Continue Button — Gradient
            val canContinue = testHelper.canContinue()
            val allTestsCompleted = testHelper.allTestsCompleted()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(58.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        brush = if (canContinue) {
                            Brush.horizontalGradient(
                                colors = listOf(accent.headerGradientStart, accent.headerGradientEnd)
                            )
                        } else {
                            Brush.horizontalGradient(
                                colors = listOf(FlowpayLightGray, FlowpayMediumGray)
                            )
                        }
                    )
                    .then(
                        if (canContinue) Modifier.clickable { testHelper.continueToMain() }
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        allTestsCompleted -> "All Tests Passed! Continue"
                        canContinue -> "Continue with partial setup"
                        else -> "Complete tests to continue"
                    },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (canContinue) Color.White else FlowpayTextGray
                )
            }

            // Skip path: a failed/hanging *99# test must never trap the
            // user on this screen. Skipping persists completion and the
            // tests can be re-run later from Settings > Reconfigure.
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Skip for now — payments may not work until tests pass",
                fontSize = 13.sp,
                color = FlowpayTextLightGray,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable { testHelper.skipTests() }
                    .padding(8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        // USSD Progress Dialog
        UssdProgressDialog(
            isVisible = showUssdDialog || showUssdConfigurationOptions,
            progressMessage = ussdProgressMessage,
            showConfigurationOptions = showUssdConfigurationOptions,
            onConfigured = { testHelper.handleUssdConfigurationConfirmation(true) },
            onNotConfigured = { testHelper.handleUssdConfigurationConfirmation(false) },
            onDismiss = { testHelper.dismissUssdDialog(fromDoesNotWork = false) },
            onDoesNotWork = { testHelper.dismissUssdDialog(fromDoesNotWork = true) }
        )

        // UPI123 Progress Dialog
        Upi123ProgressDialog(
            isVisible = showUpi123Dialog || showUpi123ConfigurationOptions,
            showConfigurationOptions = showUpi123ConfigurationOptions,
            onConfigured = { testHelper.handleUpi123ConfigurationConfirmation(true) },
            onNotConfigured = { testHelper.handleUpi123ConfigurationConfirmation(false) },
            onDismiss = { testHelper.dismissUpi123Dialog() }
        )

        // Consent before placing a real test call to the carrier.
        pendingDial?.let { dial ->
            AlertDialog(
                onDismissRequest = { pendingDial = null },
                containerColor = FlowpayDarkGray,
                titleContentColor = Color.White,
                textContentColor = FlowpayTextPale,
                title = {
                    Text(
                        stringResource(R.string.testcfg_consent_title),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Text(
                        "This places a real call to your carrier's *99# / UPI 123 " +
                            "service to check setup on your SIM. Standard call or USSD " +
                            "charges from your operator may apply.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        pendingDial = null
                        dial()
                    }) {
                        Text(stringResource(R.string.action_continue), fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDial = null }) {
                        Text(stringResource(R.string.action_cancel), color = FlowpayTextLightGray)
                    }
                }
            )
        }
    }
}

@Composable
fun TestHeaderCard() {
    val accent = LocalFlowpayAccentTheme.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        accent.headerGradientStart,
                        accent.headerGradientEnd
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon in frosted circle
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            color = Color.White.copy(alpha = 0.22f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CheckCircleIcon,
                        contentDescription = "Test",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = "Test Configuration",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 0.3.sp
                    )
                    Text(
                        text = "Step 2 of 2",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Complete these tests to verify your payment methods work correctly",
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.85f),
                fontWeight = FontWeight.Normal,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProgressDot(isActive = false)
                ProgressDot(isActive = true)
            }
        }
    }
}

@Composable
fun ProgressDot(isActive: Boolean) {
    Box(
        modifier = Modifier
            .width(if (isActive) 24.dp else 8.dp)
            .height(8.dp)
            .background(
                color = if (isActive) Color.White else Color.White.copy(alpha = 0.4f),
                shape = if (isActive) RoundedCornerShape(4.dp) else CircleShape
            )
    )
}

@Composable
fun TestInstructions() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(FlowpaySurfaceDim, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Configure Payment Methods",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 0.3.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "We'll test both scanning and manual payment methods to ensure everything works smoothly",
                fontSize = 13.sp,
                color = FlowpayTextLightGray,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun TestButton(
    title: String,
    code: String,
    description: String,
    isCompleted: Boolean,
    isTesting: Boolean,
    isUnsupported: Boolean = false,
    onClick: () -> Unit
) {
    val accent = LocalFlowpayAccentTheme.current

    val iconBgColor = when {
        isUnsupported -> FlowpayStatusWarning.copy(alpha = 0.15f)
        isCompleted -> FlowpayAccentGreen.copy(alpha = 0.15f)
        else -> accent.primary.copy(alpha = 0.15f)
    }
    val iconTint = when {
        isUnsupported -> FlowpayStatusWarning
        isCompleted -> FlowpayAccentGreen
        else -> accent.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = FlowpaySurfaceDim),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Icon circle
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = iconBgColor,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (code == "*99#") UssdIcon else UpiIcon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Text content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isUnsupported) FlowpayStatusWarning else Color.White
                    )
                    Text(
                        text = code,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isUnsupported) FlowpayStatusWarning else accent.accent,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = if (isUnsupported) FlowpayStatusWarning.copy(alpha = 0.8f) else FlowpayTextLightGray,
                    lineHeight = 18.sp
                )
            }

            // Status indicator
            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isTesting -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = accent.primary,
                            strokeWidth = 2.5.dp,
                            trackColor = FlowpayLightGray
                        )
                    }
                    isUnsupported -> {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(FlowpayStatusWarning, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "!",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                    isCompleted -> {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(FlowpayAccentGreen, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = CheckIcon,
                                contentDescription = "Completed",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .border(1.5.dp, FlowpayLightGray, CircleShape)
                        )
                    }
                }
            }
        }
    }
}

// Configuration Dialogs - Now integrated into progress dialogs

// Custom Icons
val CheckCircleIcon: ImageVector
    get() {
        return ImageVector.Builder(
            name = "check_circle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Full circle centered at (12,12)
            path(
                fill = null,
                stroke = androidx.compose.ui.graphics.SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
                strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
            ) {
                moveTo(21f, 12f)
                arcTo(9f, 9f, 0f, false, true, 3f, 12f)
                arcTo(9f, 9f, 0f, false, true, 21f, 12f)
                close()
            }
            // Centered checkmark
            path(
                fill = null,
                stroke = androidx.compose.ui.graphics.SolidColor(Color.White),
                strokeLineWidth = 2.5f,
                strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
                strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
            ) {
                moveTo(8f, 12f)
                lineTo(11f, 15f)
                lineTo(16f, 9f)
            }
        }.build()
    }

val UssdIcon: ImageVector
    get() {
        return ImageVector.Builder(
            name = "ussd_phone",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Material Design phone icon (filled)
            path(
                fill = androidx.compose.ui.graphics.SolidColor(Color.White)
            ) {
                moveTo(6.62f, 10.79f)
                curveTo(8.06f, 13.62f, 10.38f, 15.93f, 13.21f, 17.38f)
                lineTo(15.41f, 15.18f)
                curveTo(15.68f, 14.91f, 16.08f, 14.82f, 16.43f, 14.94f)
                curveTo(17.55f, 15.31f, 18.76f, 15.51f, 20f, 15.51f)
                curveTo(20.55f, 15.51f, 21f, 15.96f, 21f, 16.51f)
                lineTo(21f, 20f)
                curveTo(21f, 20.55f, 20.55f, 21f, 20f, 21f)
                curveTo(10.61f, 21f, 3f, 13.39f, 3f, 4f)
                curveTo(3f, 3.45f, 3.45f, 3f, 4f, 3f)
                lineTo(7.5f, 3f)
                curveTo(8.05f, 3f, 8.5f, 3.45f, 8.5f, 4f)
                curveTo(8.5f, 5.25f, 8.7f, 6.45f, 9.07f, 7.57f)
                curveTo(9.18f, 7.92f, 9.1f, 8.31f, 8.82f, 8.59f)
                lineTo(6.62f, 10.79f)
                close()
            }
        }.build()
    }

val UpiIcon: ImageVector
    get() {
        return ImageVector.Builder(
            name = "upi_payment",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Material Design credit card / payment icon (filled)
            path(
                fill = androidx.compose.ui.graphics.SolidColor(Color.White)
            ) {
                moveTo(20f, 4f)
                lineTo(4f, 4f)
                curveTo(2.89f, 4f, 2.01f, 4.89f, 2.01f, 6f)
                lineTo(2f, 18f)
                curveTo(2f, 19.11f, 2.89f, 20f, 4f, 20f)
                lineTo(20f, 20f)
                curveTo(21.11f, 20f, 22f, 19.11f, 22f, 18f)
                lineTo(22f, 6f)
                curveTo(22f, 4.89f, 21.11f, 4f, 20f, 4f)
                close()
                moveTo(20f, 18f)
                lineTo(4f, 18f)
                lineTo(4f, 12f)
                lineTo(20f, 12f)
                lineTo(20f, 18f)
                close()
                moveTo(20f, 8f)
                lineTo(4f, 8f)
                lineTo(4f, 6f)
                lineTo(20f, 6f)
                lineTo(20f, 8f)
                close()
            }
        }.build()
    }

val CheckIcon: ImageVector
    get() {
        return ImageVector.Builder(
            name = "check",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = androidx.compose.ui.graphics.SolidColor(Color.White),
                strokeLineWidth = 3f,
                strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
                strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
            ) {
                moveTo(6f, 12f)
                lineTo(10f, 16f)
                lineTo(18f, 8f)
            }
        }.build()
    }
