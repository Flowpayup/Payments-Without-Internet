// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Transaction operations
 */
@Dao
interface TransactionDao {

    /**
     * Get all transactions ordered by timestamp (newest first)
     */
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    /**
     * Get recent transactions (last 10)
     */
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentTransactions(limit: Int = 10): Flow<List<Transaction>>

    /**
     * Recent transactions the bank has already spoken about. PENDING rows are
     * excluded: a payment in flight has no outcome yet, and showing it beside
     * settled ones on the home screen reads as though it completed.
     */
    @Query("SELECT * FROM transactions WHERE status != 'PENDING' ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentConfirmedTransactions(limit: Int = 10): Flow<List<Transaction>>

    /**
     * Get transactions by status
     */
    @Query("SELECT * FROM transactions WHERE status = :status ORDER BY timestamp DESC")
    fun getTransactionsByStatus(status: String): Flow<List<Transaction>>

    /**
     * Get transactions by bank name
     */
    @Query("SELECT * FROM transactions WHERE bankName = :bankName ORDER BY timestamp DESC")
    fun getTransactionsByBank(bankName: String): Flow<List<Transaction>>

    /**
     * Search transactions by recipient name or phone number
     */
    @Query(
        "SELECT * FROM transactions WHERE recipientName LIKE :query OR phoneNumber LIKE :query ORDER BY timestamp DESC"
    )
    fun searchTransactions(query: String): Flow<List<Transaction>>

    /**
     * Get transaction by ID
     */
    @Query("SELECT * FROM transactions WHERE transactionId = :transactionId")
    suspend fun getTransactionById(transactionId: String): Transaction?

    /**
     * Insert a new transaction
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction)

    /**
     * Insert multiple transactions
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<Transaction>)

    /**
     * Update an existing transaction
     */
    @Update
    suspend fun updateTransaction(transaction: Transaction)

    /**
     * Delete a transaction
     */
    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    /**
     * Delete transaction by ID
     */
    @Query("DELETE FROM transactions WHERE transactionId = :transactionId")
    suspend fun deleteTransactionById(transactionId: String)

    /**
     * Delete all transactions
     */
    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()

    /**
     * Get transaction count
     */
    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun getTransactionCount(): Int

    /**
     * Get total amount of completed transactions
     */
    @Query("SELECT SUM(CAST(amount AS REAL)) FROM transactions WHERE status IN ('SUCCESS', 'SUCCESSFUL', 'COMPLETED')")
    suspend fun getTotalAmount(): Double?

    /**
     * Get transactions within date range
     */
    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getTransactionsByDateRange(startTime: Long, endTime: Long): Flow<List<Transaction>>

    /**
     * Atomically move a row from one lifecycle status to another.
     * Returns the number of rows changed (0 if the row was not in the expected status).
     */
    @Query(
        "UPDATE transactions SET status = :newStatus WHERE transactionId = :transactionId AND status = :expectedStatus"
    )
    suspend fun transitionStatus(transactionId: String, expectedStatus: String, newStatus: String): Int

    /**
     * Fill in bank-confirmed details on a session row once the confirming SMS
     * arrives. Values the session already knows are never erased by a sparser
     * SMS: upiId/recipientName keep their existing value when the parse found
     * none (COALESCE), and amount is filled only when the row started without
     * one (the QR flow can begin before the user has entered an amount).
     *
     * [expectedStatus] guards which rows a confirmation may land on — callers
     * pass PENDING, so an SMS arriving after the payment was cancelled can
     * never rewrite that cancelled row into a success.
     */
    @Query(
        "UPDATE transactions SET status = :status, bankRef = :bankRef, bankName = :bankName, " +
            "smsExcerpt = :smsExcerpt, upiId = COALESCE(:upiId, upiId), " +
            "recipientName = COALESCE(:recipientName, recipientName), " +
            "amount = CASE WHEN amount = '' THEN :amount ELSE amount END, " +
            "verifiedAt = :verifiedAt " +
            "WHERE transactionId = :transactionId AND status = :expectedStatus"
    )
    @Suppress("LongParameterList") // Room @Query binds flat parameters; the
    // domain seam (PaymentTransactionStore) takes a SimpleTransaction instead.
    suspend fun confirmTransaction(
        transactionId: String,
        status: String,
        bankRef: String?,
        bankName: String,
        smsExcerpt: String,
        upiId: String?,
        recipientName: String?,
        amount: String,
        verifiedAt: Long,
        expectedStatus: String
    ): Int

    /**
     * PENDING rows past their verification deadline are discarded: a payment
     * with no bank confirmation is not a payment we can report on, so it
     * leaves no trace rather than lingering as an outcome the user can't act
     * on. Returns the number of rows removed.
     */
    @Query("DELETE FROM transactions WHERE status = 'PENDING' AND deadlineAt IS NOT NULL AND deadlineAt < :now")
    suspend fun deleteStalePending(now: Long): Int

    /**
     * Discards one row only while it is still awaiting confirmation — the
     * status guard keeps a deadline that fires just as the SMS lands from
     * deleting the row that SMS confirmed.
     */
    @Query("DELETE FROM transactions WHERE transactionId = :transactionId AND status = 'PENDING'")
    suspend fun deletePending(transactionId: String): Int
}
