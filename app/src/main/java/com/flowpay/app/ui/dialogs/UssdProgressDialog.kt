package com.flowpay.app.ui.dialogs

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.flowpay.app.ui.theme.LocalFlowPayAccentTheme

@Composable
fun UssdProgressDialog(
    isVisible: Boolean,
    progressMessage: String = "Initializing...",
    showConfigurationOptions: Boolean = false,
    onConfigured: () -> Unit = {},
    onNotConfigured: () -> Unit = {},
    onDismiss: () -> Unit = {},
    onDoesNotWork: () -> Unit = {}
) {
    if (isVisible) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            UssdProgressDialogContent(
                progressMessage = progressMessage,
                showConfigurationOptions = showConfigurationOptions,
                onConfigured = onConfigured,
                onNotConfigured = onNotConfigured,
                onDismiss = onDismiss,
                onDoesNotWork = onDoesNotWork
            )
        }
    }
}

@Composable
private fun UssdProgressDialogContent(
    progressMessage: String,
    showConfigurationOptions: Boolean = false,
    onConfigured: () -> Unit = {},
    onNotConfigured: () -> Unit = {},
    onDismiss: () -> Unit = {},
    onDoesNotWork: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        border = BorderStroke(1.dp, Color(0xFF333333))
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp)
                    .padding(top = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            color = LocalFlowPayAccentTheme.current.accent.copy(alpha = alpha * 0.2f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = PhoneIcon,
                        contentDescription = "USSD Setup",
                        tint = LocalFlowPayAccentTheme.current.accent,
                        modifier = Modifier.size(48.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = if (showConfigurationOptions) "Setup Complete?" else "Setting Up Your Payment System",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = if (showConfigurationOptions) 
                        "Did you complete the *99# setup?\n\nThis enables seamless payment scanning."
                    else
                        "We have triggered USSD. It usually takes a few seconds for the data sessions to appear—be patient and don't close the app.",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF888888),
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                if (showConfigurationOptions) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = onNotConfigured,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(15.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF333333)
                            )
                        ) {
                            Text(
                                text = "Not Yet",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        }
                        
                        Button(
                            onClick = onConfigured,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(15.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LocalFlowPayAccentTheme.current.accent
                            )
                        ) {
                            Text(
                                text = "Yes",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = LocalFlowPayAccentTheme.current.accent,
                        trackColor = Color(0xFF333333)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = progressMessage,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Light,
                        color = Color(0xFF666666).copy(alpha = alpha),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedButton(
                        onClick = onDoesNotWork,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(15.dp),
                        border = BorderStroke(1.dp, Color(0xFF444444)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFBBBBBB)
                        )
                    ) {
                        Text(
                            text = "It doesn't work for me",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = Color(0xFF888888),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

// Custom Phone Icon
val PhoneIcon: ImageVector
    get() {
        return ImageVector.Builder(
            name = "phone",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = androidx.compose.ui.graphics.SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
                strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
            ) {
                moveTo(22f, 16.92f)
                verticalLineTo(19.92f)
                curveTo(22.0011f, 20.1985f, 21.9441f, 20.4742f, 21.8325f, 20.7293f)
                curveTo(21.7209f, 20.9844f, 21.5573f, 21.2136f, 21.3521f, 21.4019f)
                curveTo(21.1468f, 21.5901f, 20.9046f, 21.7335f, 20.6407f, 21.8227f)
                curveTo(20.3769f, 21.9119f, 20.0974f, 21.9451f, 19.82f, 21.92f)
                curveTo(16.7428f, 21.5856f, 13.787f, 20.5341f, 11.19f, 18.85f)
                curveTo(8.77382f, 17.3147f, 6.72533f, 15.2662f, 5.19f, 12.85f)
                curveTo(3.49996f, 10.2412f, 2.44824f, 7.271f, 2.12f, 4.18f)
                curveTo(2.095f, 3.90347f, 2.12787f, 3.62476f, 2.21649f, 3.36162f)
                curveTo(2.30512f, 3.09849f, 2.44766f, 2.85669f, 2.63476f, 2.65162f)
                curveTo(2.82185f, 2.44655f, 3.04924f, 2.28271f, 3.302f, 2.17f)
                curveTo(3.55476f, 2.05729f, 3.82777f, 2.00026f, 4.1f, 2f)
                horizontalLineTo(7.1f)
                curveTo(7.59565f, 1.99522f, 8.06622f, 2.16708f, 8.43306f, 2.48353f)
                curveTo(8.7999f, 2.79999f, 9.04224f, 3.23945f, 9.12f, 3.72f)
                curveTo(9.28573f, 4.68007f, 9.51567f, 5.62273f, 9.81f, 6.55f)
                curveTo(9.94454f, 6.97953f, 9.97366f, 7.43697f, 9.89482f, 7.88189f)
                curveTo(9.81598f, 8.32681f, 9.63151f, 8.74707f, 9.36f, 9.11f)
                lineTo(8.09f, 10.81f)
                curveTo(9.51315f, 13.2072f, 11.3868f, 15.2809f, 13.62f, 16.91f)
                lineTo(15.32f, 15.64f)
                curveTo(15.6829f, 15.3685f, 16.1032f, 15.184f, 16.5481f, 15.1052f)
                curveTo(16.993f, 15.0264f, 17.4504f, 15.0555f, 17.88f, 15.19f)
                curveTo(18.8073f, 15.4843f, 19.7499f, 15.7143f, 20.71f, 15.88f)
                curveTo(21.1906f, 15.9578f, 21.6301f, 16.2001f, 21.9465f, 16.5669f)
                curveTo(22.263f, 16.9338f, 22.4348f, 17.4044f, 22.43f, 17.9f)
                lineTo(22.43f, 17.9f)
                close()
            }
        }.build()
    }
