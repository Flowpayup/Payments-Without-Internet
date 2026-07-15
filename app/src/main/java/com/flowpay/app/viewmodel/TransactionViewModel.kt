package com.flowpay.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flowpay.app.data.PaymentDetails
import com.flowpay.app.data.Transaction
import com.flowpay.app.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for managing transaction data and UI state
 */
class TransactionViewModel(application: Application) : AndroidViewModel(application) {

    /**
     * First repository access materialises the encrypted database (SQLCipher
     * open, Keystore unwrap, possible migration) — expensive disk work that
     * must never run on the main thread. This ViewModel can be the process's
     * first DB toucher (home screen), so resolution is deferred to IO here
     * instead of an eager field initialiser.
     */
    private suspend fun repository(): TransactionRepository =
        withContext(Dispatchers.IO) { TransactionRepository.getInstance(getApplication()) }

    /** Flow variant of [repository]: resolves on IO at collection time. */
    private fun <T> repositoryFlow(block: (TransactionRepository) -> Flow<T>): Flow<T> =
        flow { emitAll(block(TransactionRepository.getInstance(getApplication()))) }
            .flowOn(Dispatchers.IO)

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
                repository().getRecentPaymentDetails(10).collect { transactions ->
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
        return repositoryFlow { it.getAllTransactions() }
    }
    
    /**
     * Search transactions
     */
    fun searchTransactions(query: String): Flow<List<Transaction>> {
        return repositoryFlow { it.searchTransactions(query) }
    }
    
    /**
     * Get transactions by status
     */
    fun getTransactionsByStatus(status: String): Flow<List<Transaction>> {
        return repositoryFlow { it.getTransactionsByStatus(status) }
    }
    
    /**
     * Get transactions by bank
     */
    fun getTransactionsByBank(bankName: String): Flow<List<Transaction>> {
        return repositoryFlow { it.getTransactionsByBank(bankName) }
    }
    
    /**
     * Delete a transaction
     */
    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            try {
                repository().deleteTransaction(transaction)
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
                repository().deleteTransactionById(transactionId)
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
                repository().deleteAllTransactions()
                _recentTransactions.value = emptyList()
            } catch (e: Exception) {
                _error.value = "Failed to clear transactions: ${e.message}"
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

