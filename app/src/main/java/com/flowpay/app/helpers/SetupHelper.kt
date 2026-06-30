package com.flowpay.app.helpers

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.flowpay.app.TestConfigurationActivity

/**
 * Helper class containing all business logic for SetupActivity
 * This separates the UI concerns from the business logic
 */
class SetupHelper(
    private val context: Context,
    private val uiCallback: UICallback
) {
    companion object {
        private const val TAG = "SetupHelper"
        private const val PREFS = "FlowPayPrefs"
        private const val KEY_USER_REPORTED_USSD_NOT_WORKING = "user_reported_ussd_not_working"

        /** Jio (and similar) — primary SIM carrier does not support *99# for this flow. */
        fun isPrimarySimUssdCapable(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val primarySim = prefs.getString("selected_primary_sim", "") ?: ""
            return primarySim != "jio"
        }

        /** User chose “It doesn't work for me” during *99# setup test. */
        fun hasUserReportedUssdNotWorking(context: Context): Boolean {
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_USER_REPORTED_USSD_NOT_WORKING, false)
        }

        fun setUserReportedUssdNotWorking(context: Context, reported: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_USER_REPORTED_USSD_NOT_WORKING, reported)
                .apply()
        }

        internal fun clearUserReportedUssdNotWorking(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(KEY_USER_REPORTED_USSD_NOT_WORKING)
                .apply()
        }

        /** Scan-to-pay (USSD) is allowed: carrier supports *99# and user did not opt out in setup. */
        fun isScanToPayUssdAvailable(context: Context): Boolean {
            return isPrimarySimUssdCapable(context) && !hasUserReportedUssdNotWorking(context)
        }

        /**
         * If scan-to-pay should be blocked, returns a user-facing reason; otherwise null.
         * Jio is checked before the user-reported flag so the correct message is shown.
         */
        fun getScanToPayBlockedMessage(context: Context): String? {
            if (isScanToPayUssdAvailable(context)) return null
            if (!isPrimarySimUssdCapable(context)) {
                return "Scan to pay is not available — Jio does not support *99# USSD payments"
            }
            return "Scan to pay is not available — USSD does not work for you on this device, so this feature can't be used."
        }

        @Deprecated(
            message = "Use isPrimarySimUssdCapable for carrier-only checks, or isScanToPayUssdAvailable for scan-to-pay.",
            replaceWith = ReplaceWith("isPrimarySimUssdCapable(context)")
        )
        fun isUssdSupported(context: Context): Boolean = isPrimarySimUssdCapable(context)
    }

    /**
     * Interface for UI callbacks
     */
    interface UICallback {
        fun showToast(message: String)
        fun navigateToTestConfiguration()
    }

    /**
     * Data class for validation results
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String
    )

    /**
     * Data class for setup data
     */
    data class SetupData(
        val selectedBank: String,
        val selectedPrimarySim: String,
        val isDualSimEnabled: Boolean,
        val selectedSecondarySim: String,
        val disclaimerAccepted: Boolean
    )

    /**
     * Get available banks
     */
    fun getBanks(): List<Pair<String, String>> {
        return listOf(
            "sbi" to "State Bank of India",
            "hdfc" to "HDFC Bank",
            "icici" to "ICICI Bank",
            "axis" to "Axis Bank",
            "kotak" to "Kotak Mahindra Bank",
            "pnb" to "Punjab National Bank",
            "bob" to "Bank of Baroda",
            "yes" to "Yes Bank",
            "idbi" to "IDBI Bank",
            "canara" to "Canara Bank"
        )
    }

    /**
     * Get available SIM carriers
     */
    fun getSimCarriers(): List<Pair<String, String>> {
        return listOf(
            "jio" to "Jio",
            "airtel" to "Airtel",
            "vodafone" to "Vodafone",
            "bsnl" to "BSNL"
        )
    }

    /**
     * Get secondary SIM options (excluding primary SIM)
     */
    fun getSecondarySimOptions(primarySim: String): List<Pair<String, String>> {
        val allCarriers = getSimCarriers()
        return if (primarySim.isNotEmpty()) {
            allCarriers.filter { it.first != primarySim }
        } else {
            allCarriers
        }
    }

    /**
     * Validate disclaimer acceptance
     */
    fun validateDisclaimer(disclaimerAccepted: Boolean): ValidationResult {
        if (!disclaimerAccepted) {
            return ValidationResult(false, "Please accept the disclaimer to proceed")
        }
        return ValidationResult(true, "")
    }

    /**
     * Validate bank selection
     */
    fun validateBankSelection(selectedBank: String): ValidationResult {
        if (selectedBank.isEmpty()) {
            return ValidationResult(false, "Please select your bank")
        }
        return ValidationResult(true, "")
    }

    /**
     * Validate primary SIM selection
     */
    fun validatePrimarySimSelection(selectedPrimarySim: String): ValidationResult {
        if (selectedPrimarySim.isEmpty()) {
            return ValidationResult(false, "Please select your primary SIM")
        }
        return ValidationResult(true, "")
    }

    /**
     * Validate secondary SIM selection (only if dual SIM is enabled)
     */
    fun validateSecondarySimSelection(
        isDualSimEnabled: Boolean,
        selectedSecondarySim: String
    ): ValidationResult {
        if (isDualSimEnabled && selectedSecondarySim.isEmpty()) {
            return ValidationResult(false, "Please select your secondary SIM")
        }
        return ValidationResult(true, "")
    }

    /**
     * Validate complete form
     */
    fun validateForm(setupData: SetupData): ValidationResult {
        // Validate disclaimer
        val disclaimerValidation = validateDisclaimer(setupData.disclaimerAccepted)
        if (!disclaimerValidation.isValid) {
            return disclaimerValidation
        }

        // Validate bank selection
        val bankValidation = validateBankSelection(setupData.selectedBank)
        if (!bankValidation.isValid) {
            return bankValidation
        }

        // Validate primary SIM selection
        val primarySimValidation = validatePrimarySimSelection(setupData.selectedPrimarySim)
        if (!primarySimValidation.isValid) {
            return primarySimValidation
        }

        // Validate secondary SIM selection
        val secondarySimValidation = validateSecondarySimSelection(
            setupData.isDualSimEnabled,
            setupData.selectedSecondarySim
        )
        if (!secondarySimValidation.isValid) {
            return secondarySimValidation
        }

        return ValidationResult(true, "")
    }

    /**
     * Save setup data to SharedPreferences
     */
    fun saveSetupData(setupData: SetupData) {
        val sharedPreferences = context.getSharedPreferences("FlowPayPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .putBoolean("setup_completed", true)
            .putString("selected_bank", setupData.selectedBank)
            .putString("selected_primary_sim", setupData.selectedPrimarySim)
            .putBoolean("is_dual_sim_enabled", setupData.isDualSimEnabled)
            .putString("selected_secondary_sim", setupData.selectedSecondarySim)
            .putBoolean("disclaimer_accepted", setupData.disclaimerAccepted)
            .apply()
    }

    /**
     * Complete setup process
     */
    fun completeSetup(setupData: SetupData) {
        val validationResult = validateForm(setupData)
        
        if (validationResult.isValid) {
            // Save setup data
            saveSetupData(setupData)
            
            // Show success message
            uiCallback.showToast("Setup completed successfully!")
            
            // Navigate to test configuration
            uiCallback.navigateToTestConfiguration()
        } else {
            // Show validation error
            uiCallback.showToast(validationResult.errorMessage)
        }
    }

    /**
     * Load existing setup data
     */
    fun loadSetupData(): SetupData? {
        val sharedPreferences = context.getSharedPreferences("FlowPayPrefs", Context.MODE_PRIVATE)
        
        val selectedBank = sharedPreferences.getString("selected_bank", "")
        val selectedPrimarySim = sharedPreferences.getString("selected_primary_sim", "")
        val isDualSimEnabled = sharedPreferences.getBoolean("is_dual_sim_enabled", false)
        val selectedSecondarySim = sharedPreferences.getString("selected_secondary_sim", "")
        val disclaimerAccepted = sharedPreferences.getBoolean("disclaimer_accepted", false)

        // Only return data if setup was completed
        val isSetupCompleted = sharedPreferences.getBoolean("setup_completed", false)
        if (!isSetupCompleted) {
            return null
        }

        return SetupData(
            selectedBank = selectedBank ?: "",
            selectedPrimarySim = selectedPrimarySim ?: "",
            isDualSimEnabled = isDualSimEnabled,
            selectedSecondarySim = selectedSecondarySim ?: "",
            disclaimerAccepted = disclaimerAccepted
        )
    }

    /**
     * Check if setup is already completed
     */
    fun isSetupCompleted(): Boolean {
        val sharedPreferences = context.getSharedPreferences("FlowPayPrefs", Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean("setup_completed", false)
    }

    /**
     * Reset setup data
     */
    fun resetSetupData() {
        val sharedPreferences = context.getSharedPreferences("FlowPayPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .putBoolean("setup_completed", false)
            .remove("selected_bank")
            .remove("selected_primary_sim")
            .remove("is_dual_sim_enabled")
            .remove("selected_secondary_sim")
            .remove("disclaimer_accepted")
            .apply()
        clearUserReportedUssdNotWorking(context)
    }

    /**
     * Check if dual SIM is valid (primary and secondary are different)
     */
    fun isDualSimValid(primarySim: String, secondarySim: String): Boolean {
        return primarySim.isNotEmpty() && secondarySim.isNotEmpty() && primarySim != secondarySim
    }

    /**
     * Get setup completion message
     */
    fun getSetupCompletionMessage(): String {
        return "Setup completed successfully!"
    }
}
