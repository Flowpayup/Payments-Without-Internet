package com.flowpay.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flowpay.app.data.migrations.Migrations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves that a populated v1 database survives the v1 -> v2 migration with
 * every row intact. This is the regression net for the decision to remove
 * fallbackToDestructiveMigration() — if a migration is ever wrong, this
 * fails instead of users losing their payment history.
 *
 * Runs on a device/emulator: ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate2To3_dropsRawBodyAndSynthesisesExcerpt() {
        helper.createDatabase(testDbName, 1).apply {
            execSQL(
                """
                INSERT INTO transactions
                    (transactionId, amount, status, bankName, rawMessage, timestamp, upiId, transactionType, recipientName, phoneNumber)
                VALUES
                    ('legacy-2', '500', 'SUCCESS', 'HDFC Bank', 'Rs.500 sent. A/c **9876. Avl bal Rs.42,310.55', 1700000000000, NULL, 'DEBIT', NULL, NULL)
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            testDbName, 3, true, Migrations.MIGRATION_1_2, Migrations.MIGRATION_2_3
        )

        db.query("SELECT smsExcerpt FROM transactions WHERE transactionId = 'legacy-2'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            val excerpt = cursor.getString(0)
            assertEquals("₹500 debited — HDFC Bank", excerpt)
            assertTrue("balance prose must not survive migration", !excerpt.contains("42,310"))
        }
        // rawMessage column must be gone
        db.query("SELECT * FROM transactions LIMIT 1").use { cursor ->
            assertTrue(cursor.columnNames.none { it == "rawMessage" })
            assertTrue(cursor.columnNames.any { it == "smsExcerpt" })
        }
    }

    @Test
    fun migrate1To2_preservesExistingRows() {
        helper.createDatabase(testDbName, 1).apply {
            execSQL(
                """
                INSERT INTO transactions
                    (transactionId, amount, status, bankName, rawMessage, timestamp, upiId, transactionType, recipientName, phoneNumber)
                VALUES
                    ('legacy-1', '250', 'SUCCESS', 'HDFC Bank', 'Rs.250 debited Ref 111', 1700000000000, NULL, 'DEBIT', 'Ravi', '9876543210')
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDbName, 2, true, Migrations.MIGRATION_1_2)

        db.query("SELECT transactionId, amount, status, bankRef, deadlineAt FROM transactions").use { cursor ->
            assertEquals(1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("legacy-1", cursor.getString(0))
            assertEquals("250", cursor.getString(1))
            assertEquals("SUCCESS", cursor.getString(2))
            assertTrue("new bankRef column defaults to null", cursor.isNull(3))
            assertTrue("new deadlineAt column defaults to null", cursor.isNull(4))
        }
    }
}
