// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.ui.dialogs

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowpay.app.R
import com.flowpay.app.ui.components.LedgerDragHandle
import com.flowpay.app.ui.theme.FlowpayDarkGray
import com.flowpay.app.ui.theme.FlowpayInkWarm
import com.flowpay.app.ui.theme.FlowpayLedgerRule
import com.flowpay.app.ui.theme.FlowpaySurfaceDim
import com.flowpay.app.ui.theme.FlowpayTextGray
import com.flowpay.app.ui.theme.FlowpayTextLightGray
import kotlinx.coroutines.delay

/**
 * USSD (*99#) setup/test progress — a bottom sheet matching the rest of the
 * app's modal pattern (Pay Contact, pickers, transaction detail), instead of
 * a centered dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UssdProgressDialog(
    isVisible: Boolean,
    progressMessage: String = stringResource(R.string.ussd_dlg_initializing),
    showConfigurationOptions: Boolean = false,
    onConfigured: () -> Unit = {},
    onNotConfigured: () -> Unit = {},
    onDismiss: () -> Unit = {},
    onDoesNotWork: () -> Unit = {}
) {
    if (isVisible) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = FlowpayDarkGray,
            dragHandle = { LedgerDragHandle() }
        ) {
            UssdProgressDialogContent(
                progressMessage = progressMessage,
                showConfigurationOptions = showConfigurationOptions,
                onConfigured = onConfigured,
                onNotConfigured = onNotConfigured,
                onDoesNotWork = onDoesNotWork
            )
        }
    }
}

// one self-contained bottom-sheet body covering both the in-progress and confirmation states
@Suppress("LongMethod")
@Composable
private fun UssdProgressDialogContent(
    progressMessage: String,
    showConfigurationOptions: Boolean = false,
    onConfigured: () -> Unit = {},
    onNotConfigured: () -> Unit = {},
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = PhoneIcon,
            contentDescription = stringResource(R.string.ussd_dlg_cd_ussd_setup),
            tint = FlowpayInkWarm,
            modifier = Modifier.size(40.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = if (showConfigurationOptions) {
                stringResource(R.string.ussd_dlg_setup_complete_title)
            } else {
                stringResource(R.string.ussd_dlg_setting_up_title)
            },
            style = com.flowpay.app.ui.theme.FlowpayDisplayStyle,
            fontSize = 21.sp,
            color = FlowpayInkWarm,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (showConfigurationOptions) {
                stringResource(R.string.ussd_dlg_setup_complete_body)
            } else {
                stringResource(R.string.ussd_dlg_setting_up_body)
            },
            fontSize = 15.sp,
            color = FlowpayTextLightGray,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        if (showConfigurationOptions) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onNotConfigured,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = com.flowpay.app.ui.theme.FlowpayRecordShapeLarge,
                    border = BorderStroke(1.5.dp, FlowpayLedgerRule)
                ) {
                    Text(
                        text = stringResource(R.string.ussd_dlg_not_yet),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = FlowpayTextLightGray
                    )
                }

                Button(
                    onClick = onConfigured,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = com.flowpay.app.ui.theme.FlowpayRecordShapeLarge,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FlowpayInkWarm,
                        contentColor = FlowpaySurfaceDim
                    )
                ) {
                    Text(
                        text = stringResource(R.string.ussd_dlg_yes),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = FlowpayInkWarm,
                trackColor = FlowpayLedgerRule
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = progressMessage,
                fontSize = 14.sp,
                fontWeight = FontWeight.Light,
                color = FlowpayTextGray.copy(alpha = alpha),
                textAlign = TextAlign.Center
            )

            // After a short delay, offer two shortcuts: confirm it's
            // already set up, or report it isn't working. Delayed so
            // neither tempts an action before USSD has had time to
            // respond (mirrors the UPI 123 dialog's delayed shortcut).
            // Neither touches the ongoing call.
            var showShortcuts by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(3000)
                showShortcuts = true
            }
            if (showShortcuts) {
                Spacer(modifier = Modifier.height(20.dp))

                OutlinedButton(
                    onClick = onConfigured,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = com.flowpay.app.ui.theme.FlowpayRecordShapeLarge,
                    border = BorderStroke(1.5.dp, FlowpayInkWarm),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = FlowpayInkWarm
                    )
                ) {
                    Text(
                        text = stringResource(R.string.ussd_dlg_already_set_up),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onDoesNotWork,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = com.flowpay.app.ui.theme.FlowpayRecordShapeLarge,
                    border = BorderStroke(1.5.dp, FlowpayLedgerRule),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = FlowpayTextLightGray
                    )
                ) {
                    Text(
                        text = stringResource(R.string.ussd_dlg_does_not_work),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
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
                stroke = androidx.compose.ui.graphics.SolidColor(FlowpayInkWarm),
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
