package com.flowpay.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flowpay.app.data.PaymentDetails
import com.flowpay.app.data.Transaction
import com.flowpay.app.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    /** The transaction whose detail dialog is open, if any. */
    private val _selectedTransaction = MutableStateFlow<Transaction?>(null)
    val selectedTransaction: StateFlow<Transaction?> = _selectedTransaction.asStateFlow()

    /**
     * The in-flight collection of the recent-transactions Room flow. That flow
     * never completes, so each [loadRecentTransactions] must cancel the
     * previous one — [refresh] runs on every resume, and without this the
     * collectors (and their database observers) accumulate for the lifetime
     * of the ViewModel.
     */
    private var recentTransactionsJob: Job? = null

    init {
        loadRecentTransactions()
    }

    /**
     * Load recent transactions (last 10)
     */
    fun loadRecentTransactions() {
        recentTransactionsJob?.cancel()
        recentTransactionsJob = viewModelScope.launch {
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
     * Open the detail dialog for a recent-payments row. The list carries only
     * the summary [PaymentDetails], so the full row is fetched by id.
     */
    fun selectTransaction(transactionId: String) {
        viewModelScope.launch {
            try {
                _selectedTransaction.value = repository().getTransactionById(transactionId)
            } catch (e: Exception) {
                _error.value = "Failed to open transaction: ${e.message}"
            }
        }
    }

    /** Close the detail dialog. */
    fun clearSelectedTransaction() {
        _selectedTransaction.value = null
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

