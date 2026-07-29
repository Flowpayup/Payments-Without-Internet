package com.flowpay.app.repository

import android.content.Context
import com.flowpay.app.data.AppDatabase
import com.flowpay.app.data.Transaction
import com.flowpay.app.data.TransactionDao
import com.flowpay.app.data.TransactionStatus
import com.flowpay.app.payment.sms.SimpleTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository for managing transaction data
 * Provides a clean interface between UI and data layer
 */
class TransactionRepository private constructor(context: Context) :
    com.flowpay.app.payment.PaymentTransactionStore {

    private val transactionDao: TransactionDao = AppDatabase.getDatabase(context).transactionDao()
    
    companion object {
        @Volatile
        private var INSTANCE: TransactionRepository? = null
        
        fun getInstance(context: Context): TransactionRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = TransactionRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
    
    /**
     * Get all transactions as Flow
     */
    fun getAllTransactions(): Flow<List<Transaction>> {
        return transactionDao.getAllTransactions()
    }
    
    /**
     * Get recent transactions (last 10 by default)
     */
    fun getRecentTransactions(limit: Int = 10): Flow<List<Transaction>> {
        return transactionDao.getRecentTransactions(limit)
    }
    
    /**
     * Get recent transactions as PaymentDetails for UI compatibility
     */
    fun getRecentPaymentDetails(limit: Int = 10): Flow<List<com.flowpay.app.data.PaymentDetails>> {
        return transactionDao.getRecentConfirmedTransactions(limit)
            .map { transactions ->
                transactions.map { it.toPaymentDetails() }
            }
    }
    
    /**
     * Get transactions by status
     */
    fun getTransactionsByStatus(status: String): Flow<List<Transaction>> {
        return transactionDao.getTransactionsByStatus(status)
    }
    
    /**
     * Get transactions by bank
     */
    fun getTransactionsByBank(bankName: String): Flow<List<Transaction>> {
        return transactionDao.getTransactionsByBank(bankName)
    }
    
    /**
     * Search transactions
     */
    fun searchTransactions(query: String): Flow<List<Transaction>> {
        return transactionDao.searchTransactions("%$query%")
    }
    
    /**
     * Get transaction by ID
     */
    suspend fun getTransactionById(transactionId: String): Transaction? {
        return transactionDao.getTransactionById(transactionId)
    }
    
    /**
     * Save a transaction from SimpleTransaction
     */
    suspend fun saveTransaction(simpleTransaction: SimpleTransaction) {
        val transaction = Transaction.fromSimpleTransaction(simpleTransaction)
        transactionDao.insertTransaction(transaction)
    }
    
    /**
     * Save multiple transactions
     */
    suspend fun saveTransactions(transactions: List<Transaction>) {
        transactionDao.insertTransactions(transactions)
    }
    
    /**
     * Update a transaction
     */
    suspend fun updateTransaction(transaction: Transaction) {
        transactionDao.updateTransaction(transaction)
    }
    
    /**
     * Delete a transaction
     */
    suspend fun deleteTransaction(transaction: Transaction) {
        transactionDao.deleteTransaction(transaction)
    }
    
    /**
     * Delete transaction by ID
     */
    suspend fun deleteTransactionById(transactionId: String) {
        transactionDao.deleteTransactionById(transactionId)
    }
    
    /**
     * Delete all transactions
     */
    suspend fun deleteAllTransactions() {
        transactionDao.deleteAllTransactions()
    }
    
    /**
     * Get transaction count
     */
    suspend fun getTransactionCount(): Int {
        return transactionDao.getTransactionCount()
    }
    
    /**
     * Get total amount of completed transactions
     */
    suspend fun getTotalAmount(): Double? {
        return transactionDao.getTotalAmount()
    }
    
    /**
     * Get transactions within date range
     */
    fun getTransactionsByDateRange(startTime: Long, endTime: Long): Flow<List<Transaction>> {
        return transactionDao.getTransactionsByDateRange(startTime, endTime)
    }

    // PaymentTransactionStore — used by PaymentSessionManager

    override suspend fun insertPending(transaction: Transaction) {
        transactionDao.insertTransaction(transaction)
    }

    override suspend fun transitionStatus(
        transactionId: String,
        expectedStatus: String,
        newStatus: String
    ): Int {
        return transactionDao.transitionStatus(transactionId, expectedStatus, newStatus)
    }

    override suspend fun confirmTransaction(
        transactionId: String,
        status: String,
        parsed: SimpleTransaction,
        verifiedAt: Long
    ): Int {
        return transactionDao.confirmTransaction(
            transactionId = transactionId,
            status = status,
            bankRef = parsed.transactionId,
            bankName = parsed.bankName,
            smsExcerpt = parsed.smsExcerpt,
            upiId = parsed.upiId,
            recipientName = parsed.recipientName,
            amount = parsed.amount,
            verifiedAt = verifiedAt,
            expectedStatus = TransactionStatus.PENDING
        )
    }

    override suspend fun deleteStalePending(now: Long): Int {
        return transactionDao.deleteStalePending(now)
    }

    override suspend fun deletePending(transactionId: String): Int {
        return transactionDao.deletePending(transactionId)
    }
}

