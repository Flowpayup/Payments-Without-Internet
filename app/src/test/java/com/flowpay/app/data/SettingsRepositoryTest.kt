// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

@file:Suppress("MagicNumber") // Explicit fixture values and timings make regressions readable.

package com.flowpay.app.data

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val canonical = context.getSharedPreferences("FlowpayPrefs", Context.MODE_PRIVATE)
    private val legacy = context.getSharedPreferences("flowpay_settings", Context.MODE_PRIVATE)

    @Test
    fun `unset bank never invents HDFC`() {
        assertEquals("", SettingsRepository(context).settingsFlow.value.bankId)
        assertFalse(canonical.contains("selected_bank"))
    }

    @Test
    fun `setup bank wins legacy conflict and refresh sees changes`() {
        canonical.edit().putString("selected_bank", "sbi").commit()
        legacy.edit().putString("bank_id", "hdfc").commit()
        val repository = SettingsRepository(context)
        assertEquals("sbi", repository.settingsFlow.value.bankId)
        canonical.edit().putString("selected_bank", "axis").commit()
        repository.refresh()
        assertEquals("axis", repository.settingsFlow.value.bankId)
    }

    @Test
    fun `explicit valid legacy bank is recovered but invalid legacy is ignored`() {
        legacy.edit().putString("bank_id", "icici").commit()
        assertEquals("icici", SettingsRepository(context).settingsFlow.value.bankId)
        assertEquals("icici", canonical.getString("selected_bank", null))
        canonical.edit().clear().commit()
        legacy.edit().putString("bank_id", "unknown").commit()
        assertEquals("", SettingsRepository(context).settingsFlow.value.bankId)
        assertFalse(canonical.contains("selected_bank"))
    }

    @Test
    fun `settings bank is saved where home reads it without losing other preferences`() {
        canonical.edit().putString("selected_primary_sim", "airtel").commit()
        val repository = SettingsRepository(context)
        repository.saveSettings(SettingsRepository.SavedSettings(bankId = "canara", ussdTimeout = 45))
        assertEquals("canara", canonical.getString("selected_bank", null))
        assertEquals("airtel", canonical.getString("selected_primary_sim", null))
        assertEquals(45, SettingsRepository(context).settingsFlow.value.ussdTimeout)
        assertFalse(legacy.contains("bank_id"))
    }
}
