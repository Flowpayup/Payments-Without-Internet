// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

// =====================================
// 1. SettingsRepository.kt
// =====================================
package com.flowpay.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(private val context: Context) {
    private companion object {
        val SUPPORTED_BANK_IDS = setOf("sbi", "hdfc", "icici", "axis", "kotak", "pnb", "bob", "yes", "idbi", "canara")
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "flowpay_settings",
        Context.MODE_PRIVATE
    )

    private val bankPrefs = context.getSharedPreferences("FlowpayPrefs", Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<SavedSettings> = _settingsFlow.asStateFlow()

    data class SavedSettings(
        val bankId: String = "",
        val ussdTimeout: Int = 30,
        val smsDetectionEnabled: Boolean = true,
        val overlayEnabled: Boolean = true,
        val debugMode: Boolean = false,
        val setupCompleted: Boolean = false,
        val testConfigCompleted: Boolean = false
    )

    fun saveSettings(settings: SavedSettings) {
        prefs.edit {
            remove("bank_id")
            putInt("ussd_timeout", settings.ussdTimeout)
            putBoolean("sms_detection_enabled", settings.smsDetectionEnabled)
            putBoolean("overlay_enabled", settings.overlayEnabled)
            putBoolean("debug_mode", settings.debugMode)
            putBoolean("setup_completed", settings.setupCompleted)
            putBoolean("test_config_completed", settings.testConfigCompleted)
        }
        bankPrefs.edit { putString("selected_bank", settings.bankId) }
        _settingsFlow.value = settings
    }

    fun refresh() {
        _settingsFlow.value = loadSettings()
    }

    private fun loadSettings(): SavedSettings {
        val bankId = bankPrefs.getString("selected_bank", null)
            ?: prefs.getString("bank_id", null).orEmpty().takeIf { it in SUPPORTED_BANK_IDS }.orEmpty().also { legacy ->
                if (legacy in SUPPORTED_BANK_IDS) bankPrefs.edit { putString("selected_bank", legacy) }
            }
        return SavedSettings(
            bankId = bankId,
            ussdTimeout = prefs.getInt("ussd_timeout", 30),
            smsDetectionEnabled = prefs.getBoolean("sms_detection_enabled", true),
            overlayEnabled = prefs.getBoolean("overlay_enabled", true),
            debugMode = prefs.getBoolean("debug_mode", false),
            setupCompleted = prefs.getBoolean("setup_completed", false),
            testConfigCompleted = prefs.getBoolean("test_config_completed", false)
        )
    }

    fun clearAllData() {
        prefs.edit { clear() }
        bankPrefs.edit { remove("selected_bank") }
        _settingsFlow.value = SavedSettings()
    }

    fun resetToDefaults() {
        saveSettings(SavedSettings())
    }
}
