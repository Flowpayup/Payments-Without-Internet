package com.flowpay.app.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Hand-written migrations. The database previously used
 * fallbackToDestructiveMigration(), which would have silently wiped every
 * user's payment history on the first schema change — these migrations
 * exist so that never happens.
 */
object Migrations {

    /**
     * v1 -> v2: transaction lifecycle fields.
     *  - bankRef:    the bank's own reference number from the confirming SMS
     *                (the primary key is now a client-generated UUID)
     *  - deadlineAt: when a PENDING row should be considered UNVERIFIED
     *  - source:     SMS / NOTIFICATION / MANUAL
     *  - verifiedAt: when the confirming SMS arrived
     * plus indexes on the two columns every history query filters/sorts by.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN bankRef TEXT")
            db.execSQL("ALTER TABLE transactions ADD COLUMN deadlineAt INTEGER")
            db.execSQL("ALTER TABLE transactions ADD COLUMN source TEXT")
            db.execSQL("ALTER TABLE transactions ADD COLUMN verifiedAt INTEGER")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_timestamp ON transactions(timestamp)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_status ON transactions(status)")
        }
    }

    /**
     * v2 -> v3: privacy hardening — drop the raw SMS body column.
     *
     * The table is rebuilt without `rawMessage`; a privacy-safe
     * `smsExcerpt` is synthesised from the already-parsed columns
     * (amount/status/bank/ref), so no message prose survives into the new
     * schema. The verbatim SMS remains in the user's SMS inbox app.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE transactions_new (
                    transactionId TEXT NOT NULL PRIMARY KEY,
                    amount TEXT NOT NULL,
                    status TEXT NOT NULL,
                    bankName TEXT NOT NULL,
                    smsExcerpt TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    upiId TEXT,
                    transactionType TEXT NOT NULL,
                    recipientName TEXT,
                    phoneNumber TEXT,
                    bankRef TEXT,
                    deadlineAt INTEGER,
                    source TEXT,
                    verifiedAt INTEGER
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO transactions_new
                SELECT transactionId, amount, status, bankName,
                       ('₹' || amount ||
                        CASE WHEN upper(status) = 'FAILED' THEN ' failed'
                             WHEN upper(transactionType) = 'CREDIT' THEN ' credited'
                             ELSE ' debited' END ||
                        CASE WHEN bankName != '' THEN ' — ' || bankName ELSE '' END ||
                        CASE WHEN bankRef IS NOT NULL THEN ' · Ref ' || bankRef ELSE '' END),
                       timestamp, upiId, transactionType, recipientName, phoneNumber,
                       bankRef, deadlineAt, source, verifiedAt
                FROM transactions
                """.trimIndent()
            )
            db.execSQL("DROP TABLE transactions")
            db.execSQL("ALTER TABLE transactions_new RENAME TO transactions")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_timestamp ON transactions(timestamp)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_status ON transactions(status)")
        }
    }

    val ALL = arrayOf<Migration>(MIGRATION_1_2, MIGRATION_2_3)
}
