// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowpay.app.R
import com.flowpay.app.data.Transaction
import com.flowpay.app.ui.theme.FlowpayDarkGray
import com.flowpay.app.ui.theme.FlowpayInkWarm
import com.flowpay.app.ui.theme.FlowpayLedgerRule
import com.flowpay.app.ui.theme.FlowpayMonoStyle
import com.flowpay.app.ui.theme.FlowpaySeal
import com.flowpay.app.ui.theme.FlowpayStatusError
import com.flowpay.app.ui.theme.FlowpayTextLightGray
import com.flowpay.app.ui.theme.FlowpayTextPale
import com.flowpay.app.ui.theme.statusColor
import com.flowpay.app.utils.CurrencyFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Transaction detail — a bottom sheet styled as an official transaction
 * register: the reference number and timestamp render in monospace like a
 * printed receipt, and every row is a ledger line rather than a rounded
 * "detail card". Same data and same delete-confirmation flow as before.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod") // one self-contained bottom-sheet composable, same as other large screen-level composables
@Composable
fun TransactionDetailDialog(
    transaction: Transaction,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val clipboardManager = LocalClipboardManager.current
    val statusColor = statusColor(transaction.status)
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = FlowpayDarkGray,
        dragHandle = { LedgerDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.detail_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = FlowpayTextLightGray,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Large amount, in the serif display style used for every
            // money figure across the ledger system.
            Text(
                text = stringResource(R.string.amount_rupees, CurrencyFormat.inr(transaction.amount)),
                style = com.flowpay.app.ui.theme.FlowpayDisplayStyle,
                fontSize = 32.sp,
                color = FlowpayInkWarm,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Status word + seal-style verified badge, not an alpha-tinted pill.
            if (transaction.status.uppercase() in setOf("SUCCESS", "SUCCESSFUL", "COMPLETED")) {
                SealBadge(text = transaction.status.uppercase())
            } else {
                Text(
                    text = transaction.status.lowercase().replaceFirstChar { it.uppercase() },
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

            // Official transaction register — a ledger card, hairline rows
            // instead of a rounded detail card.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        FlowpayLedgerRule.copy(alpha = 0.5f),
                        com.flowpay.app.ui.theme.FlowpayRecordShapeLarge
                    )
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.detail_register_label).uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    color = FlowpayTextLightGray
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Bank reference — the number from the bank's own SMS, the
                // one a user would actually quote back to their bank in a
                // dispute. Shown first, in monospace, and only when the bank
                // supplied one (a PENDING row has none yet). displayBankRef()
                // strips the `_<timestamp>` row-key suffix rows on disk may
                // still carry — the bank never sees that suffix.
                val bankRef = transaction.displayBankRef()
                if (bankRef != null) {
                    DetailRow(
                        label = stringResource(R.string.label_bank_reference),
                        value = bankRef,
                        mono = true,
                        valueColor = FlowpaySeal,
                        onCopy = { clipboardManager.setText(AnnotatedString(bankRef)) }
                    )
                    DetailDivider()
                }

                DetailRow(
                    label = stringResource(R.string.label_bank),
                    value = transaction.bankName,
                    onCopy = { clipboardManager.setText(AnnotatedString(transaction.bankName)) }
                )

                if (!transaction.recipientName.isNullOrEmpty()) {
                    DetailDivider()
                    DetailRow(
                        label = stringResource(R.string.detail_label_recipient),
                        value = transaction.recipientName,
                        onCopy = { clipboardManager.setText(AnnotatedString(transaction.recipientName)) }
                    )
                }

                if (!transaction.phoneNumber.isNullOrEmpty()) {
                    DetailDivider()
                    DetailRow(
                        label = stringResource(R.string.detail_label_phone_number),
                        value = transaction.phoneNumber,
                        mono = true,
                        onCopy = { clipboardManager.setText(AnnotatedString(transaction.phoneNumber)) }
                    )
                }

                if (!transaction.upiId.isNullOrEmpty()) {
                    DetailDivider()
                    DetailRow(
                        label = stringResource(R.string.label_upi_id),
                        value = transaction.upiId,
                        mono = true,
                        onCopy = { clipboardManager.setText(AnnotatedString(transaction.upiId)) }
                    )
                }

                DetailDivider()

                DetailRow(
                    label = stringResource(R.string.label_date_time),
                    value = formatFullDate(transaction.timestamp),
                    mono = true,
                    onCopy = {
                        clipboardManager.setText(
                            AnnotatedString(formatFullDate(transaction.timestamp))
                        )
                    }
                )
            }

            // Privacy-safe bank summary (raw SMS bodies are not stored)
            if (transaction.smsExcerpt.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, FlowpayLedgerRule.copy(alpha = 0.3f), com.flowpay.app.ui.theme.FlowpayRecordShape)
                        .padding(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.detail_bank_confirmation),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = FlowpayTextLightGray
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = transaction.smsExcerpt,
                        style = FlowpayMonoStyle.copy(
                            fontSize = 12.sp,
                            color = FlowpayTextLightGray,
                            lineHeight = 16.sp
                        )
                    )
                }
            }

            // Delete button
            if (onDelete != null) {
                Spacer(modifier = Modifier.height(20.dp))
                TextButton(onClick = { showDeleteConfirm = true }) {
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

    // Deletion is permanent, so confirm before removing the record.
    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = FlowpayDarkGray,
            titleContentColor = FlowpayInkWarm,
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
                    stringResource(R.string.detail_delete_confirm_body),
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
    onCopy: () -> Unit,
    mono: Boolean = false,
    valueColor: androidx.compose.ui.graphics.Color = FlowpayInkWarm
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
            if (mono) {
                Text(
                    text = value,
                    style = FlowpayMonoStyle.copy(fontSize = 14.sp, color = valueColor),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = value,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = valueColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = "Copy",
            modifier = Modifier
                .size(28.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onCopy() }
                .padding(6.dp),
            tint = FlowpayTextLightGray
        )
    }
}

@Composable
private fun DetailDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 2.dp),
        thickness = 0.5.dp,
        color = FlowpayLedgerRule.copy(alpha = 0.4f)
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

private fun formatFullDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("dd MMMM yyyy, hh:mm a", Locale("en", "IN"))
    return formatter.format(Date(timestamp))
}
