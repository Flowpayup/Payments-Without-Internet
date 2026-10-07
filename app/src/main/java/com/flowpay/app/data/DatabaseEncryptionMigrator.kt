// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.data

import android.content.Context
import android.util.Log
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import java.io.IOException

/** A retryable storage failure. Never replace preserved history with an empty database. */
internal class DatabaseMigrationException(cause: Exception) : IOException("Database encryption must be retried", cause)

/** Export first, retain the original during replacement, and verify before deleting it. */
@Suppress("TooGenericExceptionCaught") // File swaps must preserve the original on any failed export/install.
internal object DatabaseEncryptionMigrator {
    private const val TAG = "DbEncryptionMigrator"
    private const val PREF_MIGRATED = "db_encrypted"
    private const val BACKUP_SUFFIX = ".migration-backup"

    @Synchronized
    fun ensureEncrypted(
        context: Context,
        dbName: String,
        passphrase: String,
        move: (File, File) -> Boolean = { from, to -> from.renameTo(to) },
        export: (SQLiteDatabase) -> Unit = { database ->
            database.rawQuery("SELECT sqlcipher_export('encrypted')", emptyArray()).use { check(it.moveToFirst()) }
        }
    ) {
        val prefs = context.getSharedPreferences(DatabaseKeyManager.PREFS_NAME, Context.MODE_PRIVATE)
        val dbFile = context.getDatabasePath(dbName)
        val backup = File(dbFile.parentFile, dbFile.name + BACKUP_SUFFIX)
        try {
            restoreInterruptedSwap(dbFile, backup, passphrase, move)
            if (dbFile.exists() && isPlaintextSqlite(dbFile)) {
                prefs.edit().putBoolean(PREF_MIGRATED, false).commit()
                migratePlaintext(dbFile, backup, passphrase, move, export)
            } else if (dbFile.exists()) {
                recoverEncryptedIfUnreadable(dbFile, passphrase)
            }
            prefs.edit().putBoolean(PREF_MIGRATED, true).commit()
        } catch (failure: Exception) {
            prefs.edit().putBoolean(PREF_MIGRATED, false).commit()
            throw DatabaseMigrationException(failure)
        }
    }

    private fun restoreInterruptedSwap(
        dbFile: File,
        backup: File,
        key: String,
        move: (File, File) -> Boolean
    ) {
        if (!backup.exists()) return
        if (dbFile.exists() && !isPlaintextSqlite(dbFile) && runCatching { verify(dbFile, key) }.isSuccess) {
            backup.delete() // An unsuccessful cleanup can be retried next launch.
            return
        }
        check(isPlaintextSqlite(backup)) { "Migration backup cannot be classified safely" }
        // The original is checkpointed before it is moved; prove it is readable before restoring.
        verify(backup, "")
        if (dbFile.exists()) {
            check(!isPlaintextSqlite(dbFile)) { "Two plaintext originals require recovery" }
            check(
                android.database.sqlite.SQLiteDatabase.deleteDatabase(dbFile)
            ) { "Cannot remove incomplete replacement" }
        }
        check(move(backup, dbFile)) { "Cannot restore migration backup" }
    }

    private fun migratePlaintext(
        dbFile: File,
        backup: File,
        key: String,
        move: (File, File) -> Boolean,
        export: (SQLiteDatabase) -> Unit
    ) {
        val temp = File(dbFile.parentFile, "${dbFile.name}.encrypting")
        if (temp.exists()) check(android.database.sqlite.SQLiteDatabase.deleteDatabase(temp))
        val plain = open(dbFile, "", SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY)
        val version: Int
        val counts: Map<String, Long>
        try {
            version = plain.version
            counts = tableCounts(plain)
            plain.execSQL("ATTACH DATABASE ? AS encrypted KEY ?", arrayOf(temp.absolutePath, key))
            export(plain)
            plain.execSQL("DETACH DATABASE encrypted")
            // Flush the source's committed WAL before moving its main file.
            plain.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", emptyArray()).use { cursor ->
                if (cursor.moveToFirst()) check(cursor.getInt(0) == 0) { "Source database is busy" }
            }
            plain.rawQuery("PRAGMA journal_mode=DELETE", emptyArray()).use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0).equals("delete", true)) { "Source journal is busy" }
            }
        } finally {
            plain.close()
        }
        open(temp, key, SQLiteDatabase.OPEN_READWRITE).use { it.version = version }
        check(verify(temp, key) == counts) { "Encrypted export did not preserve all tables" }
        check(!backup.exists()) { "Previous migration backup still exists" }
        check(move(dbFile, backup)) { "Cannot preserve original database" }
        try {
            check(move(temp, dbFile)) { "Cannot install encrypted export" }
            check(verify(dbFile, key) == counts) { "Installed export did not preserve all tables" }
        } catch (failure: Exception) {
            // Retain the backup if restore itself fails; startup retries the same recovery.
            if (dbFile.exists()) android.database.sqlite.SQLiteDatabase.deleteDatabase(dbFile)
            if (!dbFile.exists()) move(backup, dbFile)
            throw failure
        }
        backup.delete()
    }

    private fun verify(file: File, key: String): Map<String, Long> =
        open(file, key, SQLiteDatabase.OPEN_READONLY).use { database ->
            database.rawQuery("PRAGMA integrity_check", emptyArray()).use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0) == "ok") { "Database integrity check failed" }
            }
            tableCounts(database)
        }

    private fun tableCounts(database: SQLiteDatabase): Map<String, Long> {
        val tables = mutableListOf<String>()
        database.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'",
            emptyArray()
        )
            .use { cursor -> while (cursor.moveToNext()) tables.add(cursor.getString(0)) }
        return tables.associateWith { name ->
            val quoted = name.replace("\"", "\"\"")
            database.rawQuery("SELECT count(*) FROM \"$quoted\"", emptyArray()).use { cursor ->
                check(cursor.moveToFirst())
                cursor.getLong(0)
            }
        }
    }

    private fun open(file: File, key: String, flags: Int): SQLiteDatabase =
        SQLiteDatabase.openDatabase(
            file.absolutePath,
            key,
            null,
            flags,
            net.zetetic.database.DatabaseErrorHandler {
                Log.e(TAG, "Database integrity failure; retaining the file for recovery")
            },
            null
        )

    private fun recoverEncryptedIfUnreadable(file: File, key: String) {
        try {
            verify(file, key)
        } catch (failure: android.database.sqlite.SQLiteDatabaseCorruptException) {
            preserveUnreadable(file, failure)
        } catch (failure: android.database.sqlite.SQLiteException) {
            // Busy, full, or inaccessible storage is retryable; it is not evidence of key loss.
            val invalidKey = failure.message?.contains("file is not a database", ignoreCase = true) == true ||
                failure.message?.contains("file is encrypted", ignoreCase = true) == true
            if (!invalidKey) throw failure
            preserveUnreadable(file, failure)
        }
    }

    private fun preserveUnreadable(file: File, failure: Exception) {
        // Preserve existing key-loss recovery, but never discard a plaintext migration original.
        check(!isPlaintextSqlite(file)) { "Plaintext migration requires retry" }
        Log.w(TAG, "Encrypted history unreadable; preserving it for recovery", failure)
        val setAside = File(file.parentFile, "${file.name}.unreadable")
        check(!setAside.exists()) { "Previous unreadable history must be preserved" }
        check(file.renameTo(setAside)) { "Cannot preserve unreadable history" }
        listOf("-wal", "-shm", "-journal").forEach { suffix ->
            val companion = File(file.parentFile, file.name + suffix)
            if (companion.exists()) check(companion.renameTo(File(file.parentFile, setAside.name + suffix)))
        }
    }

    private fun isPlaintextSqlite(file: File): Boolean {
        val magic = "SQLite format 3".toByteArray(Charsets.US_ASCII)
        return file.inputStream().use { stream ->
            val header = ByteArray(magic.size)
            stream.read(header) == magic.size && header.contentEquals(magic)
        }
    }
}
