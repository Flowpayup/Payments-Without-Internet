package com.flowpay.app.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SharedPreferencesHelper(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "FlowPayPrefs"
        private const val KEY_IS_FIRST_LAUNCH = "is_first_launch"
        private const val KEY_OVERLAY_PERMISSION = "overlay_permission"
    }


    fun isFirstLaunch(): Boolean {
        return prefs.getBoolean(KEY_IS_FIRST_LAUNCH, true)
    }

    fun setFirstLaunchCompleted() {
        prefs.edit().putBoolean(KEY_IS_FIRST_LAUNCH, false).apply()
    }


    fun hasOverlayPermission(): Boolean {
        return prefs.getBoolean(KEY_OVERLAY_PERMISSION, false)
    }

    fun setOverlayPermission(granted: Boolean) {
        prefs.edit().putBoolean(KEY_OVERLAY_PERMISSION, granted).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
