package com.flowpay.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flowpay.app.data.PaymentDetails
import com.flowpay.app.data.Transaction
import com.flowpay.app.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing transaction data and UI state
 */
class TransactionViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = TransactionRepository.getInstance(application)
    
    // UI State
    private val _recentTransactions = MutableStateFlow<List<PaymentDetails>>(emptyList())
    val recentTransactions: StateFlow<List<PaymentDetails>> = _recentTransactions.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    init {
        loadRecentTransactions()
    }
    
    /**
     * Load recent transactions (last 10)
     */
    fun loadRecentTransactions() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                repository.getRecentPaymentDetails(10).collect { transactions ->
                    _recentTransactions.value = transactions
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _error.value = "Failed to load transactions: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Load all transactions
     */
    fun loadAllTransactions(): Flow<List<Transaction>> {
        return repository.getAllTransactions()
    }
    
    /**
     * Search transactions
     */
    fun searchTransactions(query: String): Flow<List<Transaction>> {
        return repository.searchTransactions(query)
    }
    
    /**
     * Get transactions by status
     */
    fun getTransactionsByStatus(status: String): Flow<List<Transaction>> {
        return repository.getTransactionsByStatus(status)
    }
    
    /**
     * Get transactions by bank
     */
    fun getTransactionsByBank(bankName: String): Flow<List<Transaction>> {
        return repository.getTransactionsByBank(bankName)
    }
    
    /**
     * Get transaction by ID
     */
    fun getTransactionById(transactionId: String) {
        viewModelScope.launch {
            try {
                val transaction = repository.getTransactionById(transactionId)
                // Handle transaction details if needed
            } catch (e: Exception) {
                _error.value = "Failed to get transaction: ${e.message}"
            }
        }
    }
    
    /**
     * Delete a transaction
     */
    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            try {
                repository.deleteTransaction(transaction)
                // Reload recent transactions
                loadRecentTransactions()
            } catch (e: Exception) {
                _error.value = "Failed to delete transaction: ${e.message}"
            }
        }
    }
    
    /**
     * Delete transaction by ID
     */
    fun deleteTransactionById(transactionId: String) {
        viewModelScope.launch {
            try {
                repository.deleteTransactionById(transactionId)
                // Reload recent transactions
                loadRecentTransactions()
            } catch (e: Exception) {
                _error.value = "Failed to delete transaction: ${e.message}"
            }
        }
    }
    
    /**
     * Clear all transactions
     */
    fun clearAllTransactions() {
        viewModelScope.launch {
            try {
                repository.deleteAllTransactions()
                _recentTransactions.value = emptyList()
            } catch (e: Exception) {
                _error.value = "Failed to clear transactions: ${e.message}"
            }
        }
    }
    
    /**
     * Get transaction statistics
     */
    fun getTransactionStats() {
        viewModelScope.launch {
            try {
                val count = repository.getTransactionCount()
                val totalAmount = repository.getTotalAmount()
                // Handle stats if needed
            } catch (e: Exception) {
                _error.value = "Failed to get transaction stats: ${e.message}"
            }
        }
    }
    
    /**
     * Clear error state
     */
    fun clearError() {
        _error.value = null
    }
    
    /**
     * Refresh data
     */
    fun refresh() {
        loadRecentTransactions()
    }
}

