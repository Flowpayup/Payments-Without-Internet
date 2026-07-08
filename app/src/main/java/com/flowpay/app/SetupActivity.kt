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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowpay.app.ui.theme.FlowpayTheme
import com.flowpay.app.ui.theme.BlueAccentTheme
import com.flowpay.app.ui.theme.LocalFlowpayAccentTheme
import com.flowpay.app.helpers.SetupHelper
import com.flowpay.app.FlowpayApplication
import com.flowpay.app.data.SettingsRepository
import androidx.compose.runtime.CompositionLocalProvider
import com.flowpay.app.R

class SetupActivity : ComponentActivity() {
    private lateinit var setupHelper: SetupHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize setup helper
        setupHelper = SetupHelper(this, object : SetupHelper.UICallback {
            override fun showToast(message: String) {
                runOnUiThread { Toast.makeText(this@SetupActivity, message, Toast.LENGTH_LONG).show() }
            }

            override fun navigateToTestConfiguration() {
                val intent = Intent(this@SetupActivity, TestConfigurationActivity::class.java)
                startActivity(intent)
                finish()
            }
        })

        setTheme(R.style.Theme_Flowpay)
        setContent {
            CompositionLocalProvider(LocalFlowpayAccentTheme provides BlueAccentTheme) {
                FlowpayTheme {
                    SetupScreen(setupHelper = setupHelper)
                }
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

    val context = LocalContext.current

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
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Header Card
            HeaderCard()

            Spacer(modifier = Modifier.height(32.dp))

            // Bank Selection Section
            BankSelectionSection(
                banks = banks,
                selectedBank = selectedBank,
                onBankSelected = { selectedBank = it }
            )

            Spacer(modifier = Modifier.height(32.dp))

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

            Spacer(modifier = Modifier.height(32.dp))

            // Disclaimer Section
            DisclaimerSection(
                isAccepted = disclaimerAccepted,
                onAcceptedChange = { disclaimerAccepted = it }
            )

            Spacer(modifier = Modifier.height(32.dp))

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
    val accent = LocalFlowpayAccentTheme.current
    val headerShape = RoundedCornerShape(20.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = headerShape,
                ambientColor = Color.Black.copy(alpha = 0.15f),
                spotColor = Color.Black.copy(alpha = 0.15f)
            ),
        shape = headerShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        listOf(accent.headerGradientStart, accent.headerGradientEnd)
                    ),
                    shape = headerShape
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon
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
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = stringResource(R.string.setup_flowpay),
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.15f),
                                offset = Offset(0f, 2f),
                                blurRadius = 6f
                            )
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = stringResource(R.string.configure_upi_payments),
                        style = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color.White.copy(alpha = 0.95f),
                            letterSpacing = 0.2.sp,
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.1f),
                                offset = Offset(0f, 1f),
                                blurRadius = 3f
                            )
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankSelectionSection(
    banks: List<Pair<String, String>>,
    selectedBank: String,
    onBankSelected: (String) -> Unit
) {
    Column {
        // Section Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.AccountBalance,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(20.dp))

            Text(
                text = stringResource(R.string.bank_selection),
                style = TextStyle(
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    letterSpacing = (-0.5).sp,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.6f),
                        offset = Offset(0f, 1f),
                        blurRadius = 3f
                    )
                )
            )
        }

        Text(
            text = stringResource(R.string.choose_primary_bank),
            fontSize = 18.sp,
            color = Color(0xFF888888),
            modifier = Modifier.padding(start = 44.dp, top = 10.dp, bottom = 16.dp)
        )

        Text(
            text = "Select Bank",
            fontSize = 18.sp,
            color = Color.White,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(start = 44.dp, bottom = 8.dp)
        )

        var expanded by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = banks.find { it.first == selectedBank }?.second ?: "Choose your bank",
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF555555),
                    unfocusedBorderColor = Color(0xFF333333),
                    focusedContainerColor = Color(0xFF222222),
                    unfocusedContainerColor = Color(0xFF1A1A1A),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedTrailingIconColor = Color.White,
                    unfocusedTrailingIconColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                textStyle = TextStyle(fontSize = 17.sp),
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color(0xFF2A2A2A))
            ) {
                banks.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                label,
                                color = Color.White,
                                fontSize = 17.sp
                            )
                        },
                        onClick = {
                            onBankSelected(value)
                            expanded = false
                        },
                        modifier = Modifier.background(Color(0xFF2A2A2A))
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
    val accent = LocalFlowpayAccentTheme.current

    Column {
        // Section Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.SimCard,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(20.dp))

            Text(
                text = stringResource(R.string.sim_card_selection),
                style = TextStyle(
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    letterSpacing = (-0.5).sp,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.6f),
                        offset = Offset(0f, 1f),
                        blurRadius = 3f
                    )
                )
            )
        }

        Text(
            text = stringResource(R.string.configure_sim_cards),
            fontSize = 18.sp,
            color = Color(0xFF888888),
            modifier = Modifier.padding(start = 44.dp, top = 10.dp, bottom = 16.dp)
        )

        Text(
            text = "Primary SIM",
            fontSize = 18.sp,
            color = Color.White,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(start = 44.dp, bottom = 8.dp)
        )

        // Primary SIM Selection
        var primaryExpanded by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(
            expanded = primaryExpanded,
            onExpandedChange = { primaryExpanded = !primaryExpanded }
        ) {
            OutlinedTextField(
                value = simCarriers.find { it.first == selectedPrimarySim }?.second ?: "Select your primary SIM",
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = primaryExpanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF555555),
                    unfocusedBorderColor = Color(0xFF333333),
                    focusedContainerColor = Color(0xFF222222),
                    unfocusedContainerColor = Color(0xFF1A1A1A),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedTrailingIconColor = Color.White,
                    unfocusedTrailingIconColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                textStyle = TextStyle(fontSize = 17.sp),
            )

            ExposedDropdownMenu(
                expanded = primaryExpanded,
                onDismissRequest = { primaryExpanded = false },
                modifier = Modifier.background(Color(0xFF2A2A2A))
            ) {
                simCarriers.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                label,
                                color = Color.White,
                                fontSize = 17.sp
                            )
                        },
                        onClick = {
                            onPrimarySimSelected(value)
                            primaryExpanded = false
                        },
                        modifier = Modifier.background(Color(0xFF2A2A2A))
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
                        color = if (isDualSimEnabled) accent.accent else Color(0xFF555555),
                        shape = CircleShape
                    )
                    .background(
                        color = if (isDualSimEnabled) accent.accent else Color.Transparent,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isDualSimEnabled) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color.White, CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.width(15.dp))

            Text(
                text = stringResource(R.string.enable_dual_sim),
                fontSize = 18.sp,
                color = Color.White
            )
        }

        // Secondary SIM Section
        if (isDualSimEnabled) {
            Spacer(modifier = Modifier.height(30.dp))

            HorizontalDivider(
                color = Color(0xFF333333),
                thickness = 1.dp
            )

            Spacer(modifier = Modifier.height(25.dp))

            // Secondary SIM Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.SimCard,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(20.dp))

                Text(
                    text = stringResource(R.string.secondary_sim),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Light,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(25.dp))

            Text(
                text = "Secondary SIM",
                fontSize = 18.sp,
                color = Color.White,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(start = 44.dp, bottom = 12.dp)
            )

            // Secondary SIM Selection
            var secondaryExpanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = secondaryExpanded,
                onExpandedChange = { secondaryExpanded = !secondaryExpanded }
            ) {
                OutlinedTextField(
                    value = secondarySimOptions.find { it.first == selectedSecondarySim }?.second ?: "Select your secondary SIM",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = secondaryExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF555555),
                        unfocusedBorderColor = Color(0xFF333333),
                        focusedContainerColor = Color(0xFF222222),
                        unfocusedContainerColor = Color(0xFF1A1A1A),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedTrailingIconColor = Color.White,
                        unfocusedTrailingIconColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = TextStyle(fontSize = 17.sp),
                )

                ExposedDropdownMenu(
                    expanded = secondaryExpanded,
                    onDismissRequest = { secondaryExpanded = false },
                    modifier = Modifier.background(Color(0xFF2A2A2A))
                ) {
                    secondarySimOptions.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    label,
                                    color = Color.White,
                                    fontSize = 17.sp
                                )
                            },
                            onClick = {
                                onSecondarySimSelected(value)
                                secondaryExpanded = false
                            },
                            modifier = Modifier.background(Color(0xFF2A2A2A))
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
    val accent = LocalFlowpayAccentTheme.current
    var isExpanded by remember { mutableStateOf(false) }

    Column {
        // Section Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = stringResource(R.string.disclaimer),
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.6f),
                        offset = Offset(0f, 1f),
                        blurRadius = 3f
                    )
                )
            )
        }

        // Disclaimer Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = Color(0xFF0A0A0A),
                    shape = RoundedCornerShape(16.dp)
                )
                .border(
                    width = 1.dp,
                    color = Color(0xFF333333),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(16.dp)
        ) {
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
                            color = if (isAccepted) accent.accent else Color(0xFF555555),
                            shape = CircleShape
                        )
                        .background(
                            color = if (isAccepted) accent.accent else Color.Transparent,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isAccepted) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Color.White, CircleShape)
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
                            if (isExpanded) R.string.disclaimer_text
                            else R.string.disclaimer_summary
                        ),
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isExpanded) "Show less ▲" else "Show more ▼",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = accent.accent
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
    val accent = LocalFlowpayAccentTheme.current
    val buttonShape = RoundedCornerShape(16.dp)

    val gradientColors = if (enabled) {
        listOf(accent.headerGradientStart, accent.headerGradientEnd)
    } else {
        listOf(Color(0xFF333333), Color(0xFF2A2A2A))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(
                brush = Brush.linearGradient(gradientColors),
                shape = buttonShape
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = if (enabled) 0.15f else 0.05f),
                shape = buttonShape
            )
            .clip(buttonShape)
            .clickable(enabled = enabled) { onCompleteSetup() }
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.complete_setup),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
