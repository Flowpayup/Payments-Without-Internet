// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.ui.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flowpay.app.R
import com.flowpay.app.data.Transaction
import com.flowpay.app.ui.components.TransactionDetailDialog
import com.flowpay.app.ui.theme.FlowpayStatusError
import com.flowpay.app.ui.theme.FlowpayTextLightGray
import com.flowpay.app.ui.theme.FlowpayTheme
import com.flowpay.app.ui.theme.statusColor
import com.flowpay.app.utils.CurrencyFormat
import com.flowpay.app.viewmodel.TransactionViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Utility functions
fun formatDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("dd MMM, HH:mm", Locale("en", "IN"))
    return formatter.format(Date(timestamp))
}

fun formatTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("hh:mm a", Locale("en", "IN"))
    return formatter.format(Date(timestamp))
}

@androidx.annotation.StringRes
fun statusLabelRes(status: String): Int = when (status.uppercase()) {
    "SUCCESS", "SUCCESSFUL", "COMPLETED" -> R.string.status_label_success
    "UNVERIFIED" -> R.string.status_label_unverified
    "NEEDS_REVIEW" -> R.string.status_label_needs_review
    "CANCELLED" -> R.string.status_label_cancelled
    "FAILED", "DECLINED" -> R.string.status_label_failed
    else -> R.string.status_label_pending
}

private fun isSameDay(c1: Calendar, c2: Calendar): Boolean =
    c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
        c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)

class TransactionHistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_Flowpay)
        setContent {
            FlowpayTheme {
                TransactionHistoryScreen(
                    onBackClick = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(
    onBackClick: () -> Unit
) {
    val transactionViewModel: TransactionViewModel = viewModel()

    // State
    var searchQuery by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }

    // Collect data
    val allTransactions by transactionViewModel.loadAllTransactions().collectAsState(initial = emptyList())
    val isLoading by transactionViewModel.isLoading.collectAsState()
    val error by transactionViewModel.error.collectAsState()

    // Filter by search only
    val filteredTransactions = remember(allTransactions, searchQuery) {
        if (searchQuery.isEmpty()) {
            allTransactions
        } else {
            allTransactions.filter { transaction ->
                transaction.recipientName?.contains(searchQuery, ignoreCase = true) == true ||
                    transaction.phoneNumber?.contains(searchQuery, ignoreCase = true) == true ||
                    transaction.bankName.contains(searchQuery, ignoreCase = true) ||
                    transaction.amount.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // Group by date
    val groupToday = stringResource(R.string.history_group_today)
    val groupYesterday = stringResource(R.string.history_group_yesterday)
    val groupThisWeek = stringResource(R.string.history_group_this_week)
    val groupedTransactions = remember(filteredTransactions) {
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val weekAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }
        val dateFormat = SimpleDateFormat("dd MMM", Locale("en", "IN"))

        val grouped = linkedMapOf<String, List<Transaction>>()
        filteredTransactions
            .sortedByDescending { it.timestamp }
            .groupBy { transaction ->
                val txCal = Calendar.getInstance().apply { timeInMillis = transaction.timestamp }
                when {
                    isSameDay(txCal, today) -> groupToday
                    isSameDay(txCal, yesterday) -> groupYesterday
                    txCal.after(weekAgo) -> groupThisWeek
                    else -> dateFormat.format(Date(transaction.timestamp))
                }
            }
            .forEach { (key, value) -> grouped[key] = value }
        grouped
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
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // ═══ HEADER — flat, hairline-ruled, not a gradient card ═══
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Back button — squared, bordered
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, com.flowpay.app.ui.theme.FlowpayLedgerRule, RoundedCornerShape(6.dp))
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { onBackClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = stringResource(R.string.history_back),
                                tint = com.flowpay.app.ui.theme.FlowpayInkWarm,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = stringResource(R.string.history_title),
                            style = com.flowpay.app.ui.theme.FlowpayDisplayStyle,
                            fontSize = 19.sp,
                            color = com.flowpay.app.ui.theme.FlowpayInkWarm,
                            modifier = Modifier.weight(1f)
                        )

                        // Search button — squared, bordered
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, com.flowpay.app.ui.theme.FlowpayLedgerRule, RoundedCornerShape(6.dp))
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { showSearchBar = !showSearchBar },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = stringResource(R.string.history_search),
                                tint = com.flowpay.app.ui.theme.FlowpayInkWarm,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = com.flowpay.app.ui.theme.FlowpayLedgerRule.copy(alpha = 0.4f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ═══ SEARCH BAR ═══
                AnimatedVisibility(
                    visible = showSearchBar,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                stringResource(R.string.history_search_placeholder),
                                color = FlowpayTextLightGray,
                                fontSize = 14.sp
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = FlowpayTextLightGray,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = FlowpayTextLightGray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = com.flowpay.app.ui.theme.FlowpayInkWarm,
                            unfocusedTextColor = com.flowpay.app.ui.theme.FlowpayInkWarm,
                            focusedBorderColor = com.flowpay.app.ui.theme.FlowpayInkWarm,
                            unfocusedBorderColor = com.flowpay.app.ui.theme.FlowpayLedgerRule,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        shape = com.flowpay.app.ui.theme.FlowpayRecordShape,
                        singleLine = true
                    )
                }

                // ═══ TRANSACTION LIST ═══
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = com.flowpay.app.ui.theme.FlowpayLedgerRule,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                    error != null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = FlowpayStatusError,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.history_error_title),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = com.flowpay.app.ui.theme.FlowpayInkWarm
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                TextButton(onClick = { transactionViewModel.refresh() }) {
                                    Text(
                                        stringResource(R.string.action_retry),
                                        color = com.flowpay.app.ui.theme.FlowpayInkWarm,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                    filteredTransactions.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = FlowpayTextLightGray,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = if (searchQuery.isNotEmpty()) {
                                        stringResource(R.string.history_no_matching)
                                    } else {
                                        stringResource(R.string.history_no_transactions)
                                    },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = com.flowpay.app.ui.theme.FlowpayInkWarm
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (searchQuery.isNotEmpty()) {
                                        stringResource(R.string.history_try_different_search)
                                    } else {
                                        stringResource(R.string.history_transactions_placeholder)
                                    },
                                    fontSize = 13.sp,
                                    color = FlowpayTextLightGray
                                )
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)
                        ) {
                            groupedTransactions.forEach { (dateLabel, transactions) ->
                                // Date section header
                                item(key = "header_$dateLabel") {
                                    Text(
                                        text = dateLabel,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = FlowpayTextLightGray,
                                        letterSpacing = 0.5.sp,
                                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                                    )
                                }

                                // Transaction items with dividers
                                itemsIndexed(
                                    items = transactions,
                                    key = { _, tx -> tx.transactionId }
                                ) { index, transaction ->
                                    TransactionHistoryItem(
                                        transaction = transaction,
                                        onClick = { selectedTransaction = transaction }
                                    )
                                    if (index < transactions.lastIndex) {
                                        HorizontalDivider(
                                            thickness = 1.dp,
                                            color = com.flowpay.app.ui.theme.FlowpayLedgerRule.copy(alpha = 0.4f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Detail dialog
    selectedTransaction?.let { transaction ->
        TransactionDetailDialog(
            transaction = transaction,
            onDismiss = { selectedTransaction = null },
            onDelete = {
                transactionViewModel.deleteTransaction(transaction)
                selectedTransaction = null
            }
        )
    }
}

// ═══ TRANSACTION ROW — flat, minimal, GPay-style ═══

@Composable
private fun TransactionHistoryItem(
    transaction: Transaction,
    onClick: () -> Unit
) {
    val displayName = transaction.recipientName?.takeIf { it.isNotEmpty() }
        ?: transaction.phoneNumber?.takeIf { it.isNotEmpty() }
        ?: "Unknown"
    val initial = displayName.first().uppercaseChar()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Plain ink initial — no colored circular avatar.
        Text(
            text = initial.toString(),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = com.flowpay.app.ui.theme.FlowpayLedgerRule,
            modifier = Modifier.width(28.dp)
        )

        // Name + time
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = com.flowpay.app.ui.theme.FlowpayInkWarm,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = formatTime(transaction.timestamp),
                style = com.flowpay.app.ui.theme.FlowpayMonoStyle.copy(
                    fontSize = 12.sp,
                    color = FlowpayTextLightGray
                ),
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Amount + status word: the outcome must be readable at a glance, so
        // SUCCESS and UNVERIFIED never look identical in the list.
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = stringResource(R.string.amount_rupees, CurrencyFormat.inr(transaction.amount)),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = com.flowpay.app.ui.theme.FlowpayInkWarm,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            StatusPill(transaction.status)
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val statusColor = statusColor(status)
    Text(
        text = stringResource(statusLabelRes(status)),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = statusColor,
        maxLines = 1
    )
}
