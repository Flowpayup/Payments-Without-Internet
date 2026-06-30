package com.flowpay.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.flowpay.app.data.migrations.Migrations

/**
 * Room database for FlowPay app.
 *
 * Schema changes require a hand-written migration in [Migrations] —
 * destructive fallback is deliberately NOT configured, so a missing
 * migration fails fast in development instead of silently erasing the
 * user's payment history in production.
 */
@Database(
    entities = [Transaction::class],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "flowpay_database"
                )
                .addMigrations(*Migrations.ALL)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
