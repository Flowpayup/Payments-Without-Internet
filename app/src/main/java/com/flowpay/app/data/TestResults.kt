package com.flowpay.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

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

        private const val FIELD_USSD_ENABLED = "ussdEnabled"
        private const val FIELD_UPI123_ENABLED = "upi123Enabled"
        private const val FIELD_TIMESTAMP = "timestamp"
        private const val FIELD_TEST_VERSION = "testVersion"

        private fun TestResults.toJson(): String = JSONObject()
            .put(FIELD_USSD_ENABLED, ussdEnabled)
            .put(FIELD_UPI123_ENABLED, upi123Enabled)
            .put(FIELD_TIMESTAMP, timestamp)
            .put(FIELD_TEST_VERSION, testVersion)
            .toString()

        private fun parseTestResults(json: String): TestResults {
            val obj = JSONObject(json)
            return TestResults(
                ussdEnabled = obj.optBoolean(FIELD_USSD_ENABLED, false),
                upi123Enabled = obj.optBoolean(FIELD_UPI123_ENABLED, false),
                timestamp = obj.optLong(FIELD_TIMESTAMP, System.currentTimeMillis()),
                testVersion = obj.optString(FIELD_TEST_VERSION, "1.0")
            )
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveTestResults(results: TestResults) {
        prefs.edit().putString(KEY_TEST_RESULTS, results.toJson()).apply()
    }

    fun getTestResults(): TestResults? {
        val json = prefs.getString(KEY_TEST_RESULTS, null) ?: return null
        return try {
            parseTestResults(json)
        } catch (e: Exception) {
            null
        }
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
