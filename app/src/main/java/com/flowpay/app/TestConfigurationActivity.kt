package com.flowpay.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowpay.app.ui.theme.FlowPayTheme
import com.flowpay.app.ui.theme.BlueAccentTheme
import com.flowpay.app.ui.theme.LocalFlowPayAccentTheme
import com.flowpay.app.FlowPayApplication
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
        })

        // Initialize the helper
        testHelper.initialize()

        setTheme(R.style.Theme_FlowPay)
        setContent {
            CompositionLocalProvider(LocalFlowPayAccentTheme provides BlueAccentTheme) {
                FlowPayTheme {
                    TestConfigurationScreen(testHelper = testHelper)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        testHelper.handlePermissionResult(requestCode, permissions, grantResults)
    }
}

@Composable
fun TestConfigurationScreen(testHelper: TestConfigurationHelper) {
    val context = LocalContext.current
    val accent = LocalFlowPayAccentTheme.current

    // Get test states from helper
    val testStates = testHelper.getTestStates()
    var ussdTestCompleted by remember { mutableStateOf(testStates.ussdTestCompleted) }
    var upi123TestCompleted by remember { mutableStateOf(testStates.upi123TestCompleted) }
    var ussdTesting by remember { mutableStateOf(testStates.ussdTesting) }
    var upi123Testing by remember { mutableStateOf(testStates.upi123Testing) }
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
                    contentDescription = "Back",
                    tint = Color(0xFF888888),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Back to Setup",
                    fontSize = 14.sp,
                    color = Color(0xFF888888),
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Gradient Header Card
            TestHeaderCard()

            Spacer(modifier = Modifier.height(28.dp))

            // Test Instructions
            TestInstructions()

            Spacer(modifier = Modifier.height(28.dp))

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
                    title = "Set up",
                    code = "*99#",
                    description = when {
                        isJioSim -> "Jio does not support *99# USSD payments"
                        userReportedUssdIssue ->
                            "You reported USSD didn't work — scan to pay is off. Tap to try setup again."
                        else -> "Enable pay via scanning payments"
                    },
                    isCompleted = ussdTestCompleted || isJioSim || userReportedUssdIssue,
                    isTesting = ussdTesting,
                    isUnsupported = isJioSim,
                    onClick = {
                        if (isJioSim) {
                            Toast.makeText(context, "Jio does not support *99# USSD payments", Toast.LENGTH_LONG).show()
                        } else if (!ussdTestCompleted && !ussdTesting) {
                            // Flip the local Compose state immediately so a second tap
                            // (within the 100ms polling window) cannot pass this guard.
                            ussdTesting = true
                            testHelper.initiateCall(CallType.USSD)
                        }
                    }
                )

                // UPI123 Test Button
                TestButton(
                    title = "Set up",
                    code = "UPI123",
                    description = "Enable manual payment entries",
                    isCompleted = upi123TestCompleted,
                    isTesting = upi123Testing,
                    onClick = {
                        if (!upi123TestCompleted && !upi123Testing) {
                            upi123Testing = true
                            testHelper.initiateUpi123Test()
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
                        brush = if (canContinue)
                            Brush.horizontalGradient(
                                colors = listOf(accent.headerGradientStart, accent.headerGradientEnd)
                            )
                        else
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFF333333), Color(0xFF2A2A2A))
                            )
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
                    color = if (canContinue) Color.White else Color(0xFF666666)
                )
            }

            // Skip path: a failed/hanging *99# test must never trap the
            // user on this screen. Skipping persists completion and the
            // tests can be re-run later from Settings > Reconfigure.
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Skip for now — payments may not work until tests pass",
                fontSize = 13.sp,
                color = Color(0xFF8A8A8A),
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
    }
}

@Composable
fun TestHeaderCard() {
    val accent = LocalFlowPayAccentTheme.current

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
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 0.3.sp
                    )
                    Text(
                        text = "Step 2 of 3",
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
                ProgressDot(isActive = false)
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
            .background(Color(0xFF1A1A1A), RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Configure Payment Methods",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                letterSpacing = 0.3.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "We'll test both scanning and manual payment methods to ensure everything works smoothly",
                fontSize = 15.sp,
                color = Color(0xFF888888),
                lineHeight = 22.sp,
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
    val accent = LocalFlowPayAccentTheme.current

    val iconBgColor = when {
        isUnsupported -> Color(0xFFFF9800).copy(alpha = 0.15f)
        isCompleted -> Color(0xFF4CAF50).copy(alpha = 0.15f)
        else -> accent.primary.copy(alpha = 0.15f)
    }
    val iconTint = when {
        isUnsupported -> Color(0xFFFF9800)
        isCompleted -> Color(0xFF4CAF50)
        else -> accent.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon circle — bigger
            Box(
                modifier = Modifier
                    .size(52.dp)
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
                    modifier = Modifier.size(26.dp)
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
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isUnsupported) Color(0xFFFF9800) else Color.White
                    )
                    Text(
                        text = code,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isUnsupported) Color(0xFFFF9800) else accent.accent,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = if (isUnsupported) Color(0xFFFF9800).copy(alpha = 0.8f) else Color(0xFF888888),
                    lineHeight = 20.sp
                )
            }

            // Status indicator — bigger
            Box(
                modifier = Modifier.size(32.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isTesting -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = accent.primary,
                            strokeWidth = 2.5.dp,
                            trackColor = Color(0xFF333333)
                        )
                    }
                    isUnsupported -> {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color(0xFFFF9800), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "!",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                    isCompleted -> {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color(0xFF4CAF50), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = CheckIcon,
                                contentDescription = "Completed",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .border(1.5.dp, Color(0xFF333333), CircleShape)
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
