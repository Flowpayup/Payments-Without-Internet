// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

@file:Suppress("MagicNumber") // Explicit fixture values and timings make regressions readable.

package com.flowpay.app.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class DatabaseEncryptionMigratorTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "migration-fixture"
    private val file get() = context.getDatabasePath(name)
    private val key = "migration-fixture-key"
    private val payload = "redacted receipt fixture ".repeat(8_000)

    @Before
    fun prepare() {
        System.loadLibrary("sqlcipher")
        cleanup()
        file.parentFile!!.mkdirs()
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            database.enableWriteAheadLogging()
            database.execSQL(
                "CREATE TABLE transactions (id TEXT PRIMARY KEY, amount TEXT, status TEXT, bankRef TEXT, detail TEXT)"
            )
            database.execSQL(
                "INSERT INTO transactions VALUES (?, ?, ?, ?, ?)",
                arrayOf("original-id", "150.0", "SUCCESS", "001233440091", payload)
            )
            database.version = 3
        }
    }

    @After
    fun cleanup() {
        listOf("", ".encrypting", ".migration-backup", ".unreadable").forEach {
            android.database.sqlite.SQLiteDatabase.deleteDatabase(File(file.parentFile, name + it))
        }
    }

    private fun assertContents(passphrase: String, databaseFile: File = file, expectedPayload: String = payload) {
        SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            passphrase,
            null,
            SQLiteDatabase.OPEN_READONLY,
            null,
            null
        )
            .use { database ->
                assertEquals(3, database.version)
                database.rawQuery("SELECT * FROM transactions", emptyArray()).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("original-id", cursor.getString(0))
                    assertEquals("150.0", cursor.getString(1))
                    assertEquals("SUCCESS", cursor.getString(2))
                    assertEquals("001233440091", cursor.getString(3))
                    assertEquals(expectedPayload, cursor.getString(4))
                    assertFalse(cursor.moveToNext())
                }
            }
    }

    @Test
    fun exportPreservesAllFieldsAndCanRunAgain() {
        DatabaseEncryptionMigrator.ensureEncrypted(context, name, key)
        assertContents(key)
        DatabaseEncryptionMigrator.ensureEncrypted(context, name, key)
        assertContents(key)
        assertFalse(File(file.parentFile, name + ".migration-backup").exists())
    }

    @Test
    fun failedReplacementRestoresOriginalAndRetries() {
        var moves = 0
        expectRetry {
            DatabaseEncryptionMigrator.ensureEncrypted(context, name, key, move = { from, to ->
                moves++
                if (moves == 2) false else from.renameTo(to)
            })
        }
        assertContents("")
        DatabaseEncryptionMigrator.ensureEncrypted(context, name, key)
        assertContents(key)
    }

    @Test
    fun interruptedSwapRecoversRetainedOriginalOnNextLaunch() {
        expectRetry {
            DatabaseEncryptionMigrator.ensureEncrypted(context, name, key, move = { from, to ->
                check(from.renameTo(to))
                throw IOException("simulated interruption after preserving original")
            })
        }
        assertTrue(File(file.parentFile, name + ".migration-backup").exists())
        DatabaseEncryptionMigrator.ensureEncrypted(context, name, key)
        assertContents(key)
    }

    @Test
    fun fullEncryptedDestinationPreservesPlaintextAndRetries() {
        expectRetry {
            DatabaseEncryptionMigrator.ensureEncrypted(context, name, key, export = { database ->
                database.rawQuery(
                    "PRAGMA encrypted.max_page_count=1",
                    emptyArray()
                ).use { assertTrue(it.moveToFirst()) }
                database.rawQuery(
                    "SELECT sqlcipher_export('encrypted')",
                    emptyArray()
                ).use { assertTrue(it.moveToFirst()) }
            })
        }
        assertContents("")
        assertFalse(
            context.getSharedPreferences(DatabaseKeyManager.PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean("db_encrypted", true)
        )
        DatabaseEncryptionMigrator.ensureEncrypted(context, name, key)
        assertContents(key)
    }

    @Test
    fun invalidKeyRetainsEncryptedHistoryForRecovery() {
        DatabaseEncryptionMigrator.ensureEncrypted(context, name, key)
        DatabaseEncryptionMigrator.ensureEncrypted(context, name, "different-key")
        assertContents(key, File(file.parentFile, name + ".unreadable"))
        assertFalse(file.exists())
    }

    @Test
    fun openWalWriterBlocksSwapWithoutLosingItsCommittedReceipt() {
        val committedPayload = "$payload committed in WAL"
        SQLiteDatabase.openDatabase(
            file.absolutePath,
            "",
            null,
            SQLiteDatabase.OPEN_READWRITE,
            null,
            null
        ).use { writer ->
            assertTrue(writer.enableWriteAheadLogging())
            writer.execSQL("UPDATE transactions SET detail = ?", arrayOf(committedPayload))
            writer.beginTransaction()
            try {
                expectRetry { DatabaseEncryptionMigrator.ensureEncrypted(context, name, key) }
                writer.rawQuery("SELECT detail FROM transactions", null).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(committedPayload, cursor.getString(0))
                }
            } finally {
                writer.endTransaction()
            }
        }
        assertTrue(file.exists())
        DatabaseEncryptionMigrator.ensureEncrypted(context, name, key)
        assertContents(key, expectedPayload = committedPayload)
    }

    private fun expectRetry(action: () -> Unit) {
        var failed = false
        try {
            action()
        } catch (expected: DatabaseMigrationException) {
            failed = true
        }
        assertTrue("failure must be retryable rather than resetting history", failed)
    }
}
