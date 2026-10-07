// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionReceiptTest {
    private lateinit var database: AppDatabase
    private val dao get() = database.transactionDao()

    @Before
    fun prepare() {
        System.loadLibrary("sqlcipher")
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).openHelperFactory(SupportOpenHelperFactory("receipt-test-key".toByteArray())).build()
    }

    @After
    fun cleanup() = database.close()

    @Test
    fun qrReceiptUsesActualAmountAndKeepsKnownPayee() = runBlocking {
        dao.insertTransaction(pending("qr", TransactionSource.QR))
        assertEquals(1, confirm("qr"))
        val row = dao.getTransactionById("qr")!!
        assertEquals("700", row.amount)
        assertEquals("001233440091", row.bankRef)
        assertEquals("shop@okaxis", row.upiId)
        assertEquals(TransactionStatus.SUCCESS, row.status)
        assertEquals(0, confirm("qr"))
    }

    @Test
    fun manualAmountIsPreservedAndCancelledRowCannotBeRewritten() = runBlocking {
        dao.insertTransaction(pending("manual", TransactionSource.MANUAL))
        assertEquals(1, confirm("manual"))
        assertEquals("500", dao.getTransactionById("manual")!!.amount)
        dao.insertTransaction(pending("cancelled", TransactionSource.MANUAL))
        dao.transitionStatus("cancelled", TransactionStatus.PENDING, TransactionStatus.CANCELLED)
        assertEquals(0, confirm("cancelled"))
        assertEquals(TransactionStatus.CANCELLED, dao.getTransactionById("cancelled")!!.status)
    }

    private fun pending(id: String, source: String) = Transaction(
        transactionId = id,
        amount = "500",
        status = TransactionStatus.PENDING,
        bankName = "",
        smsExcerpt = "",
        timestamp = 0,
        upiId = "shop@okaxis",
        source = source
    )

    private suspend fun confirm(id: String): Int = dao.confirmTransaction(
        transactionId = id,
        status = TransactionStatus.SUCCESS,
        bankRef = "001233440091",
        bankName = "Fixture bank",
        smsExcerpt = "Redacted receipt",
        upiId = null,
        recipientName = null,
        amount = "700",
        verifiedAt = 1,
        expectedStatus = TransactionStatus.PENDING
    )
}
