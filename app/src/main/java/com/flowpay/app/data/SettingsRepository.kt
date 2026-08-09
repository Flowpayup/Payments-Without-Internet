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
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "flowpay_settings",
        Context.MODE_PRIVATE
    )

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<SavedSettings> = _settingsFlow.asStateFlow()

    data class SavedSettings(
        val bankId: String = "hdfc",
        val ussdTimeout: Int = 30,
        val smsDetectionEnabled: Boolean = true,
        val overlayEnabled: Boolean = true,
        val debugMode: Boolean = false,
        val setupCompleted: Boolean = false,
        val testConfigCompleted: Boolean = false
    )

    fun saveSettings(settings: SavedSettings) {
        prefs.edit {
            putString("bank_id", settings.bankId)
            putInt("ussd_timeout", settings.ussdTimeout)
            putBoolean("sms_detection_enabled", settings.smsDetectionEnabled)
            putBoolean("overlay_enabled", settings.overlayEnabled)
            putBoolean("debug_mode", settings.debugMode)
            putBoolean("setup_completed", settings.setupCompleted)
            putBoolean("test_config_completed", settings.testConfigCompleted)
        }
        _settingsFlow.value = settings
    }

    private fun loadSettings(): SavedSettings {
        return SavedSettings(
            bankId = prefs.getString("bank_id", "hdfc") ?: "hdfc",
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
        _settingsFlow.value = SavedSettings()
    }

    fun resetToDefaults() {
        saveSettings(SavedSettings())
    }
}
