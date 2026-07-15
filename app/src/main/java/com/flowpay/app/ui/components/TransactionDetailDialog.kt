package com.flowpay.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.flowpay.app.R
import com.flowpay.app.data.Transaction
import com.flowpay.app.ui.theme.FlowpayDarkGray
import com.flowpay.app.ui.theme.FlowpayLightGray
import com.flowpay.app.ui.theme.FlowpayMediumGray
import com.flowpay.app.ui.theme.FlowpayStatusError
import com.flowpay.app.ui.theme.FlowpaySurfaceDim
import com.flowpay.app.ui.theme.FlowpayTextLightGray
import com.flowpay.app.ui.theme.FlowpayTextPale
import com.flowpay.app.ui.theme.LocalFlowpayAccentTheme
import com.flowpay.app.ui.theme.statusColor
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TransactionDetailDialog(
    transaction: Transaction,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val clipboardManager = LocalClipboardManager.current
    val accent = LocalFlowpayAccentTheme.current
    val statusColor = statusColor(transaction.status)
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = FlowpaySurfaceDim,
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(FlowpayLightGray)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.detail_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(FlowpayDarkGray)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.detail_close),
                            tint = FlowpayTextLightGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Large amount
                Text(
                    text = formatAmount(transaction.amount.toDoubleOrNull() ?: 0.0),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent.headerGradientStart,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Status pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = transaction.status.lowercase()
                            .replaceFirstChar { it.uppercase() },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor
                    )
                }

                // Non-success outcomes get a plain-language explanation
                statusExplainerText(transaction.status)?.let { explainer ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = explainer,
                        fontSize = 12.sp,
                        color = FlowpayTextPale,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Detail card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = FlowpayDarkGray,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Transaction ID
                        DetailRow(
                            label = stringResource(R.string.label_transaction_id),
                            value = transaction.transactionId,
                            onCopy = { clipboardManager.setText(AnnotatedString(transaction.transactionId)) }
                        )

                        DetailDivider()

                        // Bank
                        DetailRow(
                            label = stringResource(R.string.label_bank),
                            value = transaction.bankName,
                            onCopy = { clipboardManager.setText(AnnotatedString(transaction.bankName)) }
                        )

                        // Recipient
                        if (!transaction.recipientName.isNullOrEmpty()) {
                            DetailDivider()
                            DetailRow(
                                label = stringResource(R.string.detail_label_recipient),
                                value = transaction.recipientName,
                                onCopy = { clipboardManager.setText(AnnotatedString(transaction.recipientName)) }
                            )
                        }

                        // Phone
                        if (!transaction.phoneNumber.isNullOrEmpty()) {
                            DetailDivider()
                            DetailRow(
                                label = stringResource(R.string.detail_label_phone_number),
                                value = transaction.phoneNumber,
                                onCopy = { clipboardManager.setText(AnnotatedString(transaction.phoneNumber)) }
                            )
                        }

                        // UPI ID
                        if (!transaction.upiId.isNullOrEmpty()) {
                            DetailDivider()
                            DetailRow(
                                label = stringResource(R.string.label_upi_id),
                                value = transaction.upiId,
                                onCopy = { clipboardManager.setText(AnnotatedString(transaction.upiId)) }
                            )
                        }

                        DetailDivider()

                        // Date & Time
                        DetailRow(
                            label = stringResource(R.string.label_date_time),
                            value = formatFullDate(transaction.timestamp),
                            onCopy = { clipboardManager.setText(AnnotatedString(formatFullDate(transaction.timestamp))) }
                        )
                    }
                }

                // Privacy-safe bank summary (raw SMS bodies are not stored)
                if (transaction.smsExcerpt.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = FlowpayDarkGray,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(R.string.detail_bank_confirmation),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = FlowpayTextLightGray
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = transaction.smsExcerpt,
                                fontSize = 12.sp,
                                color = FlowpayTextLightGray,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Delete button
                if (onDelete != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(FlowpayStatusError.copy(alpha = 0.1f))
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { showDeleteConfirm = true }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.detail_delete_transaction),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = FlowpayStatusError
                        )
                    }
                }
            }
        }
    }

    // Deletion is permanent, so confirm before removing the record.
    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = FlowpayDarkGray,
            titleContentColor = Color.White,
            textContentColor = FlowpayTextPale,
            title = {
                Text(
                    stringResource(R.string.detail_delete_confirm_title),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    "This removes the record from your history on this device. " +
                        "It cannot be undone and does not affect the actual payment.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) {
                    Text(
                        stringResource(R.string.action_delete),
                        color = FlowpayStatusError,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel), color = FlowpayTextLightGray)
                }
            }
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    onCopy: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = FlowpayTextLightGray
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(FlowpayMediumGray)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onCopy() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy",
                modifier = Modifier.size(14.dp),
                tint = FlowpayTextLightGray
            )
        }
    }
}

@Composable
private fun DetailDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 2.dp),
        thickness = 0.5.dp,
        color = FlowpayMediumGray
    )
}

/** Localised plain-language meaning of a non-success lifecycle status. */
@androidx.compose.runtime.Composable
private fun statusExplainerText(status: String): String? = when (status.uppercase()) {
    "PENDING" -> androidx.compose.ui.res.stringResource(com.flowpay.app.R.string.status_explainer_pending)
    "UNVERIFIED" -> androidx.compose.ui.res.stringResource(com.flowpay.app.R.string.status_explainer_unverified)
    "NEEDS_REVIEW" -> androidx.compose.ui.res.stringResource(com.flowpay.app.R.string.status_explainer_needs_review)
    "CANCELLED" -> androidx.compose.ui.res.stringResource(com.flowpay.app.R.string.status_explainer_cancelled)
    "FAILED" -> androidx.compose.ui.res.stringResource(com.flowpay.app.R.string.status_explainer_failed)
    else -> null
}

private fun formatAmount(amount: Double): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    return formatter.format(amount)
}

private fun formatFullDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("dd MMMM yyyy, hh:mm a", Locale("en", "IN"))
    return formatter.format(Date(timestamp))
}
