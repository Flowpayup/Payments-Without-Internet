package com.flowpay.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class TestResults(
    val ussdEnabled: Boolean = false,
    val upi123Enabled: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val testVersion: String = "1.0"
)

class TestResultsManager(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "flowpay_test_results"
        private const val KEY_TEST_RESULTS = "test_results"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    fun saveTestResults(results: TestResults) {
        val json = gson.toJson(results)
        prefs.edit().putString(KEY_TEST_RESULTS, json).apply()
    }
    
    fun getTestResults(): TestResults? {
        val json = prefs.getString(KEY_TEST_RESULTS, null) ?: return null
        return try {
            gson.fromJson(json, TestResults::class.java)
        } catch (e: Exception) {
            null
        }
    }
    
    fun clearTestResults() {
        prefs.edit().remove(KEY_TEST_RESULTS).apply()
    }
    
    fun hasCompletedTests(): Boolean {
        val results = getTestResults()
        return results?.ussdEnabled == true || results?.upi123Enabled == true
    }
    
    fun areAllTestsCompleted(): Boolean {
        val results = getTestResults()
        return results?.ussdEnabled == true && results?.upi123Enabled == true
    }
}
