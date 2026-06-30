package com.flowpay.app.ui.activities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowpay.app.services.CallOverlayService
import com.flowpay.app.ui.theme.FlowPayTheme
import com.flowpay.app.ui.theme.BlueAccentTheme
import com.flowpay.app.ui.theme.LocalFlowPayAccentTheme
import com.flowpay.app.FlowPayApplication
import com.flowpay.app.data.SettingsRepository
import androidx.compose.runtime.CompositionLocalProvider
import com.flowpay.app.R

class CallOverlayActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "CallOverlayActivity"
        private const val ACTION_STOP_OVERLAY = "STOP_OVERLAY"
    }
    
    private val stopOverlayReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_STOP_OVERLAY) {
                Log.d(TAG, "Received stop overlay broadcast")
                finish()
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Register broadcast receiver with proper flags for Android 13+
        val filter = IntentFilter(ACTION_STOP_OVERLAY)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopOverlayReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stopOverlayReceiver, filter)
        }
        
        // CRITICAL FIX: Set aggressive window flags to force overlay to top layer
        // This ensures the overlay appears over calls and other system dialogs
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN or
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN or
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
        )
        
        // Additional flags for Android 10+ compatibility
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
            )
        }
        
        // Force the activity to be on top with additional flags
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // CRITICAL: Set window type for overlay permission with higher priority
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            window.setType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        } else {
            @Suppress("DEPRECATION")
            window.setType(android.view.WindowManager.LayoutParams.TYPE_SYSTEM_ERROR) // Higher priority than SYSTEM_ALERT
        }
        
        Log.d(TAG, "Overlay window flags set for maximum visibility")
        
        // Force bring to front and make visible
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        
        // Ensure the activity is on top
        window.decorView.bringToFront()
        window.decorView.requestFocus()
        
        val phoneNumber = intent.getStringExtra("phone_number") ?: ""
        val amount = intent.getStringExtra("amount") ?: ""
        
        setTheme(R.style.Theme_FlowPay)
        setContent {
            CompositionLocalProvider(LocalFlowPayAccentTheme provides BlueAccentTheme) {
                FlowPayTheme {
                    CallOverlayScreen(
                        phoneNumber = phoneNumber,
                        amount = amount,
                        onContinue = { finish() }
                    )
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(stopOverlayReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver: ${e.message}")
        }
    }
}

@Composable
fun CallOverlayScreen(
    phoneNumber: String,
    amount: String,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    var progress by remember { mutableStateOf(0f) }
    var currentStep by remember { mutableStateOf("Initializing...") }
    
    // Animate progress
    LaunchedEffect(Unit) {
        val steps = listOf(
            "Initializing..." to 0.1f,
            "Connecting to UPI123..." to 0.3f,
            "Processing payment..." to 0.6f,
            "Waiting for verification..." to 0.8f,
            "Almost done..." to 1.0f
        )
        
        steps.forEach { (step, progressValue) ->
            currentStep = step
            progress = progressValue
            kotlinx.coroutines.delay(2000)
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .align(Alignment.Center),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Text(
                    text = "Flowpay Protection",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "UPI123 call protection is active",
                    fontSize = 14.sp,
                    color = Color(0xFF888888),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Payment Details
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "To:",
                                fontSize = 14.sp,
                                color = Color(0xFF888888)
                            )
                            Text(
                                text = phoneNumber,
                                fontSize = 16.sp,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Amount:",
                                fontSize = 14.sp,
                                color = Color(0xFF888888)
                            )
                            Text(
                                text = "₹$amount",
                                fontSize = 16.sp,
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Progress Section
                Text(
                    text = currentStep,
                    fontSize = 16.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Progress Bar
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = LocalFlowPayAccentTheme.current.accent,
                    trackColor = Color(0xFF444444)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "${(progress * 100).toInt()}%",
                    fontSize = 12.sp,
                    color = Color(0xFF888888)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Action Button
                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalFlowPayAccentTheme.current.accent
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Continue",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

