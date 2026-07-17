package com.flowpay.app.data

import android.content.Context
import android.util.Log
import java.io.File
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * One-time plaintext → SQLCipher migration, plus key-loss recovery.
 *
 * Existing installs have an unencrypted Room database on disk. On the first
 * launch after the SQLCipher upgrade this exports it into an encrypted copy
 * (sqlcipher_export), swaps the files, and records completion. If the
 * encrypted database later becomes unreadable (Keystore key invalidated —
 * e.g. some device restores), the file is set aside and the app starts with
 * an empty history rather than crash-looping: history is convenience data,
 * the bank remains the source of truth.
 */
internal object DatabaseEncryptionMigrator {

    private const val TAG = "DbEncryptionMigrator"
    private const val PREF_MIGRATED = "db_encrypted"

    /** Must run before Room opens the database. Caller loads the sqlcipher lib. */
    fun ensureEncrypted(context: Context, dbName: String, passphrase: String) {
        val prefs = context.getSharedPreferences(DatabaseKeyManager.PREFS_NAME, Context.MODE_PRIVATE)
        val dbFile = context.getDatabasePath(dbName)

        if (!dbFile.exists()) {
            // Fresh install — Room will create the encrypted DB directly.
            prefs.edit().putBoolean(PREF_MIGRATED, true).apply()
            return
        }

        if (prefs.getBoolean(PREF_MIGRATED, false)) {
            recoverIfUnreadable(dbFile, passphrase)
            return
        }

        try {
            migratePlaintext(dbFile, passphrase)
            prefs.edit().putBoolean(PREF_MIGRATED, true).apply()
            Log.i(TAG, "Transaction database encrypted in place")
        } catch (e: Exception) {
            // Most likely cause: the file is already encrypted but the
            // migrated flag was lost (cleared app data restored the DB, or a
            // partial previous run). If it opens with our key we're done;
            // otherwise set it aside and start fresh.
            Log.w(TAG, "Plaintext migration failed - checking whether DB is already encrypted", e)
            prefs.edit().putBoolean(PREF_MIGRATED, true).apply()
            recoverIfUnreadable(dbFile, passphrase)
        }
    }

    private fun migratePlaintext(dbFile: File, passphrase: String) {
        val encryptedFile = File(dbFile.parentFile, "${dbFile.name}.encrypting")
        if (encryptedFile.exists()) encryptedFile.delete()

        // Empty key = plaintext database opened through SQLCipher.
        val plain = SQLiteDatabase.openDatabase(
            dbFile.absolutePath, "", null, SQLiteDatabase.OPEN_READWRITE, null, null
        )
        val version = plain.version
        try {
            // Passphrase is Base64 (no quotes) — safe inside a '...' literal.
            plain.rawExecSQL(
                "ATTACH DATABASE '${encryptedFile.absolutePath}' AS encrypted KEY '$passphrase'"
            )
            plain.rawExecSQL("SELECT sqlcipher_export('encrypted')")
            plain.rawExecSQL("DETACH DATABASE encrypted")
        } finally {
            plain.close()
        }

        // Carry the schema version so Room doesn't re-run migrations.
        val encrypted = SQLiteDatabase.openDatabase(
            encryptedFile.absolutePath, passphrase, null, SQLiteDatabase.OPEN_READWRITE, null, null
        )
        try {
            encrypted.version = version
        } finally {
            encrypted.close()
        }

        // Remove the plaintext DB (and -shm/-wal sidecars), move encrypted in.
        android.database.sqlite.SQLiteDatabase.deleteDatabase(dbFile)
        if (!encryptedFile.renameTo(dbFile)) {
            throw IllegalStateException("Could not move encrypted database into place")
        }
    }

    private fun recoverIfUnreadable(dbFile: File, passphrase: String) {
        try {
            SQLiteDatabase.openDatabase(
                dbFile.absolutePath, passphrase, null, SQLiteDatabase.OPEN_READONLY, null, null
            ).close()
        } catch (e: Exception) {
            Log.w(TAG, "Encrypted DB unreadable with current key - starting fresh (history lost)", e)
            if (isPlaintextSqlite(dbFile)) {
                // A failed plaintext -> SQLCipher migration can leave the
                // ORIGINAL UNENCRYPTED database here. Never retain plaintext on
                // disk (it would also escape the backup exclusion by filename) —
                // delete it outright. The bank remains the source of truth.
                Log.w(TAG, "Set-aside database is plaintext - deleting rather than retaining")
                dbFile.delete()
            } else {
                // Encrypted but unreadable (Keystore key lost). Keep it set
                // aside for diagnostics; it stays out of backups by filename
                // (see backup_rules.xml / data_extraction_rules.xml).
                val setAside = File(dbFile.parentFile, "${dbFile.name}.unreadable")
                if (setAside.exists()) setAside.delete()
                if (!dbFile.renameTo(setAside)) dbFile.delete()
            }
            File(dbFile.parentFile, "${dbFile.name}-shm").delete()
            File(dbFile.parentFile, "${dbFile.name}-wal").delete()
        }
    }

    /**
     * True when the file begins with the ASCII "SQLite format 3" magic —
     * i.e. an UNENCRYPTED SQLite database. A SQLCipher-encrypted file has a
     * random-looking header and returns false, so this cleanly separates a
     * plaintext leftover (must be deleted) from an encrypted-but-unreadable one.
     */
    private fun isPlaintextSqlite(file: File): Boolean {
        return try {
            file.inputStream().use { stream ->
                val header = ByteArray(SQLITE_MAGIC_PREFIX.size)
                stream.read(header) == SQLITE_MAGIC_PREFIX.size && header.contentEquals(SQLITE_MAGIC_PREFIX)
            }
        } catch (e: Exception) {
            // Can't read the header — treat as not-plaintext so the caller
            // keeps (rather than deletes) the file; err on the safe side.
            Log.w(TAG, "Could not read DB header to classify plaintext vs encrypted", e)
            false
        }
    }

    private val SQLITE_MAGIC_PREFIX = "SQLite format 3".toByteArray(Charsets.US_ASCII)
}
