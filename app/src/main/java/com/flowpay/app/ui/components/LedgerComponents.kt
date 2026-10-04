// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowpay.app.ui.theme.FlowpayInkWarm
import com.flowpay.app.ui.theme.FlowpayLedgerRule
import com.flowpay.app.ui.theme.FlowpayMonoStyle
import com.flowpay.app.ui.theme.FlowpaySeal
import com.flowpay.app.ui.theme.FlowpayTextLightGray

// ─────────────────────────────────────────────────────────────────────────
// "Bank-Stamped" shared components.
//
// The visual counterpart to the ledger/record shape tokens: a flat,
// hairline-divided row instead of a floating rounded card with a colored
// avatar circle, a dashed "seal" badge instead of a solid gradient pill,
// and a squared form-tab action button instead of a circular icon button.
// Every component here is presentation-only — callers supply real data and
// real callbacks, never sample data.
// ─────────────────────────────────────────────────────────────────────────

/**
 * One row in a ledger-style list (recent payments, transaction history).
 * Renders as a plain two-line record separated by a hairline rule rather
 * than a rounded, elevated card — the shape difference is deliberate: a
 * record is rectangular, a status/badge is the only thing allowed to be
 * round (see [SealBadge] / status pills).
 */
// one reusable row (title/subtitle/trailing text+icon+click+divider) beats splitting into near-identical overloads
@Suppress("LongMethod", "LongParameterList")
@Composable
fun LedgerRow(
    title: String,
    subtitle: String,
    trailingText: String,
    trailingColor: Color,
    modifier: Modifier = Modifier,
    trailingIcon: ImageVector? = null,
    trailingIconDescription: String? = null,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onClick
                        )
                    } else {
                        Modifier
                    }
                )
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 12.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FlowpayInkWarm,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = FlowpayTextLightGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = FlowpayMonoStyle.copy(fontSize = 12.sp, color = FlowpayTextLightGray)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = trailingText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = trailingColor,
                    maxLines = 1
                )
                if (trailingIcon != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = trailingIcon,
                        contentDescription = trailingIconDescription,
                        modifier = Modifier.size(16.dp),
                        tint = trailingColor
                    )
                }
            }
        }
        if (showDivider) {
            HorizontalDivider(thickness = 1.dp, color = FlowpayLedgerRule.copy(alpha = 0.4f))
        }
    }
}

/**
 * A rubber-stamp-style badge: dashed seal-red border, slight counter-
 * rotation, small solid dot — used for "Verified" / bank-confirmed
 * indicators. Deliberately not a solid gradient pill.
 */
@Composable
fun SealBadge(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .rotate(-2f)
            .clip(com.flowpay.app.ui.theme.FlowpaySealPillShape)
            .border(1.5.dp, FlowpaySeal, com.flowpay.app.ui.theme.FlowpaySealPillShape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(FlowpaySeal)
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = text,
            style = FlowpayMonoStyle.copy(fontSize = 11.sp, color = FlowpaySeal, letterSpacing = 0.4.sp)
        )
    }
}

/**
 * A squared "form-tab" primary action button — the Home screen's Scan QR /
 * Pay Contact affordances. Rectangular (record-shaped), not a circular or
 * pill-shaped icon button, so its shape reads as a durable action tile
 * rather than a decorative bubble.
 */
// icon/label/sublabel/enabled/primary/click/modifier are all independent per-instance inputs
@Suppress("LongParameterList")
@Composable
fun FormTabButton(
    icon: ImageVector,
    label: String,
    sublabel: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    onClick: () -> Unit
) {
    val borderColor = when {
        !enabled -> FlowpayLedgerRule.copy(alpha = 0.35f)
        isPrimary -> FlowpayInkWarm
        else -> FlowpayLedgerRule
    }
    val contentColor = when {
        !enabled -> FlowpayTextLightGray
        isPrimary -> com.flowpay.app.ui.theme.FlowpaySurfaceDim
        else -> FlowpayInkWarm
    }
    val background = if (isPrimary && enabled) FlowpayInkWarm else Color.Transparent

    Column(
        modifier = modifier
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .clip(com.flowpay.app.ui.theme.FlowpayRecordShapeLarge)
            .background(background)
            .border(1.5.dp, borderColor, com.flowpay.app.ui.theme.FlowpayRecordShapeLarge)
            .padding(vertical = 18.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (sublabel != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = sublabel,
                style = FlowpayMonoStyle.copy(fontSize = 10.sp, color = contentColor.copy(alpha = 0.7f))
            )
        }
    }
}

/**
 * Plain-text status word (Success / Failed / …) in the status color —
 * the ledger-row alternative to a colored pill, used beneath a trailing
 * amount. Kept separate from the existing alpha-tinted [StatusPill]-style
 * chips used as true badges elsewhere.
 */
@Composable
fun StatusWord(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = modifier
    )
}

/**
 * A section header rendered as small caps with a trailing hairline rule,
 * matching a ledger's own section dividers rather than a floating label.
 */
@Composable
fun LedgerSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = FlowpayTextLightGray,
        modifier = modifier
    )
}

/**
 * Ink-stamp press animation: scales in from slightly oversized with a
 * small settle-rotation, like a rubber stamp coming down on paper, rather
 * than a checkmark fading in. Wraps arbitrary content (an icon in a ring).
 */
@Composable
fun StampPressAnimation(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var play by remember { mutableStateOf(false) }
    LaunchedEffect(visible) { if (visible) play = true }

    val scale by animateFloatAsState(
        targetValue = if (play) 1f else 1.7f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "stampScale"
    )
    val rotation by animateFloatAsState(
        targetValue = if (play) -4f else -18f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "stampRotation"
    )
    val alpha by animateFloatAsState(
        targetValue = if (play) 1f else 0f,
        animationSpec = tween(150),
        label = "stampAlpha"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = rotation
                this.alpha = alpha
            }
    ) {
        content()
    }
}

/** A bottom-sheet drag handle, matching the ledger system's restrained
 *  chrome (no shadow, single hairline-colored bar). */
@Composable
fun LedgerDragHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(FlowpayLedgerRule)
        )
    }
}
