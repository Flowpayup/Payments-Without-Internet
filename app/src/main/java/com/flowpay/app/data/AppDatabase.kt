package com.flowpay.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.flowpay.app.data.migrations.Migrations
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Room database for Flowpay app.
 *
 * Schema changes require a hand-written migration in [Migrations] —
 * destructive fallback is deliberately NOT configured, so a missing
 * migration fails fast in development instead of silently erasing the
 * user's payment history in production.
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
                        .addMigrations(*Migrations.ALL)
                        .build()
                    INSTANCE = instance
                    instance
                }
            }
        }
    }
}
