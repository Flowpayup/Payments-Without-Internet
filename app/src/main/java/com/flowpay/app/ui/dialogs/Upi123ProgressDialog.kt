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
 * UPI 123Pay setup/test progress — a bottom sheet matching the rest of the
 * app's modal pattern, instead of a centered dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Upi123ProgressDialog(
    isVisible: Boolean,
    showConfigurationOptions: Boolean = false,
    onConfigured: () -> Unit = {},
    onNotConfigured: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    if (isVisible) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = FlowpayDarkGray,
            dragHandle = { LedgerDragHandle() }
        ) {
            Upi123ProgressDialogContent(
                showConfigurationOptions = showConfigurationOptions,
                onConfigured = onConfigured,
                onNotConfigured = onNotConfigured
            )
        }
    }
}

// one self-contained bottom-sheet body covering both the in-progress and confirmation states
@Suppress("LongMethod")
@Composable
private fun Upi123ProgressDialogContent(
    showConfigurationOptions: Boolean = false,
    onConfigured: () -> Unit = {},
    onNotConfigured: () -> Unit = {}
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
            imageVector = UpiIcon,
            contentDescription = stringResource(R.string.upi123_dlg_setup_icon_desc),
            tint = FlowpayInkWarm,
            modifier = Modifier.size(40.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = if (showConfigurationOptions) {
                stringResource(R.string.upi123_dlg_setup_complete_title)
            } else {
                stringResource(R.string.upi123_dlg_setting_up_title)
            },
            style = com.flowpay.app.ui.theme.FlowpayDisplayStyle,
            fontSize = 21.sp,
            color = FlowpayInkWarm,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (showConfigurationOptions) {
                stringResource(R.string.upi123_dlg_setup_complete_message)
            } else {
                stringResource(R.string.upi123_dlg_ivr_triggered_message)
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
                        text = stringResource(R.string.upi123_dlg_not_yet),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = FlowpayTextLightGray,
                        textAlign = TextAlign.Center
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
                        text = stringResource(R.string.upi123_dlg_yes),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
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
                text = stringResource(R.string.upi123_dlg_configuring),
                fontSize = 14.sp,
                fontWeight = FontWeight.Light,
                color = FlowpayTextGray.copy(alpha = alpha),
                textAlign = TextAlign.Center
            )

            // Shortcut for users who already have UPI 123 set up: after a
            // short delay, offer a way to confirm without waiting for the
            // whole call flow. This only records the test result — it never
            // touches the ongoing IVR call.
            var showAlreadySetUp by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(3000)
                showAlreadySetUp = true
            }
            if (showAlreadySetUp) {
                Spacer(modifier = Modifier.height(20.dp))
                OutlinedButton(
                    onClick = onConfigured,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = com.flowpay.app.ui.theme.FlowpayRecordShapeLarge,
                    border = BorderStroke(1.5.dp, FlowpayInkWarm),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = FlowpayInkWarm
                    )
                ) {
                    Text(
                        text = stringResource(R.string.upi123_dlg_already_set_up),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// Custom UPI Icon
val UpiIcon: ImageVector
    get() {
        return ImageVector.Builder(
            name = "upi",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = androidx.compose.ui.graphics.SolidColor(FlowpayInkWarm),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
                strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
                strokeLineMiter = 1f,
                pathFillType = androidx.compose.ui.graphics.PathFillType.NonZero
            ) {
                moveTo(6f, 8f)
                verticalLineTo(16f)
                curveTo(6f, 18.2f, 7.8f, 20f, 10f, 20f)
                horizontalLineTo(14f)
                curveTo(16.2f, 20f, 18f, 18.2f, 18f, 16f)
                verticalLineTo(8f)
                moveTo(8f, 8f)
                verticalLineTo(16f)
                curveTo(8f, 17.1f, 8.9f, 18f, 10f, 18f)
                horizontalLineTo(14f)
                curveTo(15.1f, 18f, 16f, 17.1f, 16f, 16f)
                verticalLineTo(8f)
                moveTo(10f, 12f)
                horizontalLineTo(14f)
                moveTo(10f, 14f)
                horizontalLineTo(14f)
            }
        }.build()
    }
