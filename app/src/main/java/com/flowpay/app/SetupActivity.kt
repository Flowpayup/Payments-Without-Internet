// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowpay.app.helpers.SetupHelper
import com.flowpay.app.ui.theme.FlowpayDarkGray
import com.flowpay.app.ui.theme.FlowpayDisabledGray
import com.flowpay.app.ui.theme.FlowpaySurfaceDim
import com.flowpay.app.ui.theme.FlowpayTextGray
import com.flowpay.app.ui.theme.FlowpayTextLightGray
import com.flowpay.app.ui.theme.FlowpayTheme

class SetupActivity : ComponentActivity() {
    private lateinit var setupHelper: SetupHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize setup helper
        setupHelper = SetupHelper(
            this,
            object : SetupHelper.UICallback {
                override fun showToast(message: String) {
                    runOnUiThread { Toast.makeText(this@SetupActivity, message, Toast.LENGTH_LONG).show() }
                }

                override fun navigateToTestConfiguration() {
                    val intent = Intent(this@SetupActivity, TestConfigurationActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }
        )

        setTheme(R.style.Theme_Flowpay)
        // Edge-to-edge: Compose insets are the single source of padding (see MainActivity).
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            FlowpayTheme {
                SetupScreen(setupHelper = setupHelper)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(setupHelper: SetupHelper) {
    var selectedBank by remember { mutableStateOf("") }
    var selectedPrimarySim by remember { mutableStateOf("") }
    var selectedSecondarySim by remember { mutableStateOf("") }
    var isDualSimEnabled by remember { mutableStateOf(false) }
    var disclaimerAccepted by remember { mutableStateOf(false) }

    // Use helper methods for data
    val banks = setupHelper.getBanks()
    val simCarriers = setupHelper.getSimCarriers()
    val secondarySimOptions = setupHelper.getSecondarySimOptions(selectedPrimarySim)

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
                // Insets must sit outside verticalScroll so they pad the
                // viewport, not the scrolling content — otherwise the nav bar
                // overlays the content at every offset but the very bottom.
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Header Card
            HeaderCard()

            Spacer(modifier = Modifier.height(20.dp))

            // Bank Selection Section
            BankSelectionSection(
                banks = banks,
                selectedBank = selectedBank,
                onBankSelected = { selectedBank = it }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // SIM Card Selection Section
            SimCardSelectionSection(
                simCarriers = simCarriers,
                selectedPrimarySim = selectedPrimarySim,
                selectedSecondarySim = selectedSecondarySim,
                isDualSimEnabled = isDualSimEnabled,
                onPrimarySimSelected = { selectedPrimarySim = it },
                onSecondarySimSelected = { selectedSecondarySim = it },
                onDualSimToggled = { isDualSimEnabled = it },
                secondarySimOptions = secondarySimOptions
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Disclaimer Section
            DisclaimerSection(
                isAccepted = disclaimerAccepted,
                onAcceptedChange = { disclaimerAccepted = it }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // All form fields must be answered before the user can continue
            val isFormComplete = selectedBank.isNotBlank() &&
                selectedPrimarySim.isNotBlank() &&
                (!isDualSimEnabled || selectedSecondarySim.isNotBlank()) &&
                disclaimerAccepted

            // Complete Setup Button
            CompleteSetupButton(
                enabled = isFormComplete,
                onCompleteSetup = {
                    val setupData = SetupHelper.SetupData(
                        selectedBank = selectedBank,
                        selectedPrimarySim = selectedPrimarySim,
                        isDualSimEnabled = isDualSimEnabled,
                        selectedSecondarySim = selectedSecondarySim,
                        disclaimerAccepted = disclaimerAccepted
                    )
                    setupHelper.completeSetup(setupData)
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun HeaderCard() {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = com.flowpay.app.ui.theme.FlowpayInkWarm,
                    modifier = Modifier.size(22.dp)
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = stringResource(R.string.setup_flowpay),
                        style = com.flowpay.app.ui.theme.FlowpayDisplayStyle,
                        fontSize = 24.sp,
                        color = com.flowpay.app.ui.theme.FlowpayInkWarm
                    )
                    Text(
                        text = stringResource(R.string.setup_step_1_of_2),
                        style = com.flowpay.app.ui.theme.FlowpayMonoStyle.copy(
                            fontSize = 12.sp,
                            color = FlowpayTextLightGray
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.configure_upi_payments),
                fontSize = 15.sp,
                color = FlowpayTextLightGray,
                fontWeight = FontWeight.Normal,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProgressDot(isActive = true)
                ProgressDot(isActive = false)
            }
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = com.flowpay.app.ui.theme.FlowpayLedgerRule.copy(alpha = 0.4f)
        )
    }
}

/**
 * Shared section-header pattern: plain icon (no colored badge circle) plus
 * a 16sp title and 12sp gray subtitle — matches the ledger system's rule
 * that roundness/color-badges are reserved for status/seal indicators, not
 * decoration.
 */
@Composable
private fun SetupSectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = FlowpayTextLightGray,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = com.flowpay.app.ui.theme.FlowpayInkWarm
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = FlowpayTextLightGray
            )
        }
    }
}

@Composable
private fun SetupFieldLabel(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = FlowpayTextLightGray,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankSelectionSection(
    banks: List<Pair<String, String>>,
    selectedBank: String,
    onBankSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                com.flowpay.app.ui.theme.FlowpayLedgerRule.copy(alpha = 0.4f),
                com.flowpay.app.ui.theme.FlowpayRecordShapeLarge
            )
            .padding(18.dp)
    ) {
        SetupSectionHeader(
            icon = Icons.Default.AccountBalance,
            title = stringResource(R.string.bank_selection),
            subtitle = stringResource(R.string.choose_primary_bank)
        )

        Spacer(modifier = Modifier.height(16.dp))

        SetupFieldLabel(stringResource(R.string.setup_select_bank))

        var expanded by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = banks.find { it.first == selectedBank }?.second
                    ?: stringResource(R.string.setup_choose_your_bank),
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = com.flowpay.app.ui.theme.FlowpayInkWarm,
                    unfocusedBorderColor = com.flowpay.app.ui.theme.FlowpayLedgerRule,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = com.flowpay.app.ui.theme.FlowpayInkWarm,
                    unfocusedTextColor = com.flowpay.app.ui.theme.FlowpayInkWarm,
                    focusedTrailingIconColor = com.flowpay.app.ui.theme.FlowpayInkWarm,
                    unfocusedTrailingIconColor = com.flowpay.app.ui.theme.FlowpayInkWarm
                ),
                shape = com.flowpay.app.ui.theme.FlowpayRecordShape,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp),
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(FlowpayDarkGray)
            ) {
                banks.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                label,
                                color = com.flowpay.app.ui.theme.FlowpayInkWarm,
                                fontSize = 15.sp
                            )
                        },
                        onClick = {
                            onBankSelected(value)
                            expanded = false
                        },
                        modifier = Modifier.background(FlowpayDarkGray)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimCardSelectionSection(
    simCarriers: List<Pair<String, String>>,
    selectedPrimarySim: String,
    selectedSecondarySim: String,
    isDualSimEnabled: Boolean,
    onPrimarySimSelected: (String) -> Unit,
    onSecondarySimSelected: (String) -> Unit,
    onDualSimToggled: (Boolean) -> Unit,
    secondarySimOptions: List<Pair<String, String>>
) {
    val ink = com.flowpay.app.ui.theme.FlowpayInkWarm
    val ledgerRule = com.flowpay.app.ui.theme.FlowpayLedgerRule

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ledgerRule.copy(alpha = 0.4f), com.flowpay.app.ui.theme.FlowpayRecordShapeLarge)
            .padding(18.dp)
    ) {
        SetupSectionHeader(
            icon = Icons.Default.SimCard,
            title = stringResource(R.string.sim_card_selection),
            subtitle = stringResource(R.string.configure_sim_cards)
        )

        Spacer(modifier = Modifier.height(16.dp))

        SetupFieldLabel(stringResource(R.string.setup_primary_sim))

        // Primary SIM Selection
        var primaryExpanded by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(
            expanded = primaryExpanded,
            onExpandedChange = { primaryExpanded = !primaryExpanded }
        ) {
            OutlinedTextField(
                value = simCarriers.find { it.first == selectedPrimarySim }?.second
                    ?: stringResource(R.string.setup_select_primary_sim),
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = primaryExpanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ink,
                    unfocusedBorderColor = ledgerRule,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = ink,
                    unfocusedTextColor = ink,
                    focusedTrailingIconColor = ink,
                    unfocusedTrailingIconColor = ink
                ),
                shape = com.flowpay.app.ui.theme.FlowpayRecordShape,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp),
            )

            ExposedDropdownMenu(
                expanded = primaryExpanded,
                onDismissRequest = { primaryExpanded = false },
                modifier = Modifier.background(FlowpayDarkGray)
            ) {
                simCarriers.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                label,
                                color = ink,
                                fontSize = 15.sp
                            )
                        },
                        onClick = {
                            onPrimarySimSelected(value)
                            primaryExpanded = false
                        },
                        modifier = Modifier.background(FlowpayDarkGray)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Dual SIM Checkbox
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDualSimToggled(!isDualSimEnabled) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .border(
                        width = 2.dp,
                        color = if (isDualSimEnabled) ink else FlowpayDisabledGray,
                        shape = CircleShape
                    )
                    .background(
                        color = if (isDualSimEnabled) ink else Color.Transparent,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isDualSimEnabled) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(FlowpaySurfaceDim, CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = stringResource(R.string.enable_dual_sim),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = ink
            )
        }

        // Secondary SIM Section
        if (isDualSimEnabled) {
            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(
                color = ledgerRule.copy(alpha = 0.4f),
                thickness = 1.dp
            )

            Spacer(modifier = Modifier.height(16.dp))

            SetupFieldLabel(stringResource(R.string.secondary_sim))

            // Secondary SIM Selection
            var secondaryExpanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = secondaryExpanded,
                onExpandedChange = { secondaryExpanded = !secondaryExpanded }
            ) {
                OutlinedTextField(
                    value = secondarySimOptions.find { it.first == selectedSecondarySim }?.second
                        ?: stringResource(R.string.setup_select_secondary_sim),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = secondaryExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ink,
                        unfocusedBorderColor = ledgerRule,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = ink,
                        unfocusedTextColor = ink,
                        focusedTrailingIconColor = ink,
                        unfocusedTrailingIconColor = ink
                    ),
                    shape = com.flowpay.app.ui.theme.FlowpayRecordShape,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp),
                )

                ExposedDropdownMenu(
                    expanded = secondaryExpanded,
                    onDismissRequest = { secondaryExpanded = false },
                    modifier = Modifier.background(FlowpayDarkGray)
                ) {
                    secondarySimOptions.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    label,
                                    color = ink,
                                    fontSize = 15.sp
                                )
                            },
                            onClick = {
                                onSecondarySimSelected(value)
                                secondaryExpanded = false
                            },
                            modifier = Modifier.background(FlowpayDarkGray)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DisclaimerSection(
    isAccepted: Boolean,
    onAcceptedChange: (Boolean) -> Unit
) {
    val ink = com.flowpay.app.ui.theme.FlowpayInkWarm
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                com.flowpay.app.ui.theme.FlowpayLedgerRule.copy(alpha = 0.4f),
                com.flowpay.app.ui.theme.FlowpayRecordShapeLarge
            )
            .padding(18.dp)
    ) {
        SetupSectionHeader(
            icon = Icons.Outlined.Info,
            title = stringResource(R.string.disclaimer),
            subtitle = stringResource(R.string.setup_please_read_before_continuing)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Disclaimer body
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.Top
            ) {
                // Circular Checkbox — independent tap zone for accepting
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(22.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onAcceptedChange(!isAccepted) }
                        .border(
                            width = 2.dp,
                            color = if (isAccepted) ink else FlowpayDisabledGray,
                            shape = CircleShape
                        )
                        .background(
                            color = if (isAccepted) ink else Color.Transparent,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isAccepted) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(FlowpaySurfaceDim, CircleShape)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Text area — independent tap zone for expanding/collapsing
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { isExpanded = !isExpanded }
                ) {
                    Text(
                        text = stringResource(
                            if (isExpanded) {
                                R.string.disclaimer_text
                            } else {
                                R.string.disclaimer_summary
                            }
                        ),
                        fontSize = 14.sp,
                        color = FlowpayTextLightGray,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(
                            if (isExpanded) {
                                R.string.setup_show_less
                            } else {
                                R.string.setup_show_more
                            }
                        ),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = ink,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                    )
                }
            }
        }
    }
}

@Composable
fun CompleteSetupButton(
    enabled: Boolean,
    onCompleteSetup: () -> Unit
) {
    val ink = com.flowpay.app.ui.theme.FlowpayInkWarm
    val ledgerRule = com.flowpay.app.ui.theme.FlowpayLedgerRule
    val buttonShape = com.flowpay.app.ui.theme.FlowpayRecordShapeLarge

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(
                color = if (enabled) ink else ledgerRule.copy(alpha = 0.3f),
                shape = buttonShape
            )
            .clip(buttonShape)
            .clickable(enabled = enabled) { onCompleteSetup() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.complete_setup),
                color = if (enabled) FlowpaySurfaceDim else FlowpayTextGray,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = if (enabled) FlowpaySurfaceDim else FlowpayTextGray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
