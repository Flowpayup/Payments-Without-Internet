// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Room database for Flowpay app.
 *
 * Version 3 is the only schema this codebase has ever produced — the first
 * commit already declared it — so there are no migrations to carry. The next
 * schema change must add all four things together: a hand-written `Migration`,
 * the exported schema JSON the compiler emits for the new version, a
 * `MigrationTest` against it, and the emulator workflow that runs that test.
 * Destructive fallback is deliberately NOT configured, so a missing migration
 * fails fast in development instead of silently erasing payment history in
 * production.
 *
 * The database is encrypted at rest with SQLCipher. The passphrase is
 * Keystore-wrapped ([DatabaseKeyManager]); existing plaintext installs are
 * converted once by [DatabaseEncryptionMigrator] before Room opens.
 */
@Database(
    entities = [Transaction::class],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

    companion object {
        private const val DB_NAME = "flowpay_database"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val appContext = context.applicationContext
                    System.loadLibrary("sqlcipher")
                    val passphrase = DatabaseKeyManager.getOrCreatePassphrase(appContext)
                    DatabaseEncryptionMigrator.ensureEncrypted(appContext, DB_NAME, passphrase)
                    val instance = Room.databaseBuilder(
                        appContext,
                        AppDatabase::class.java,
                        DB_NAME
                    )
                        .openHelperFactory(
                            SupportOpenHelperFactory(passphrase.toByteArray(Charsets.UTF_8))
                        )
                        .build()
                    INSTANCE = instance
                    instance
                }
            }
        }
    }
}
