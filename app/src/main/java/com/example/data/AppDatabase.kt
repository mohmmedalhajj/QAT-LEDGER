package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TaxEntry::class,
        InventoryItem::class,
        SalesInvoice::class,
        SalesInvoiceItem::class,
        Customer::class,
        CustomerPayment::class,
        Supplier::class,
        SupplierPurchase::class,
        SupplierPurchaseItem::class,
        Expense::class,
        MoneyTransfer::class,
        TransferRecipientEntity::class,
        QatImportWeight::class,
        AgencyProfile::class
    ],
    version = 18,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun taxDao(): TaxDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun salesDao(): SalesDao
    abstract fun customerDao(): CustomerDao
    abstract fun supplierDao(): SupplierDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun transferDao(): TransferDao
    abstract fun transferRecipientDao(): TransferRecipientDao
    abstract fun qatImportWeightDao(): QatImportWeightDao
    abstract fun agencyProfileDao(): AgencyProfileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE supplier_purchases ADD COLUMN transferNumber TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE supplier_purchases ADD COLUMN bankName TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE supplier_purchases ADD COLUMN demarSar REAL NOT NULL DEFAULT 0.0")
                database.execSQL("ALTER TABLE supplier_purchases ADD COLUMN demarYer REAL NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE inventory_items ADD COLUMN itemNumber TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE inventory_items ADD COLUMN supplierId INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE inventory_items ADD COLUMN supplierName TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE supplier_purchases ADD COLUMN demarTypeYer TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE supplier_purchases ADD COLUMN demarNumberYer TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE supplier_purchases ADD COLUMN demarTypeSar TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE supplier_purchases ADD COLUMN demarNumberSar TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `qat_import_weights` (`date` TEXT NOT NULL, `weight` REAL NOT NULL, `notes` TEXT NOT NULL DEFAULT '', PRIMARY KEY(`date`))")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE qat_import_weights ADD COLUMN receiverName TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    database.execSQL("ALTER TABLE inventory_items ADD COLUMN receiverName TEXT NOT NULL DEFAULT ''")
                } catch (e: Exception) {}
                
                try {
                    database.execSQL("CREATE TABLE IF NOT EXISTS `qat_import_weights_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, `weight` REAL NOT NULL, `notes` TEXT NOT NULL DEFAULT '', `receiverName` TEXT NOT NULL DEFAULT '')")
                    database.execSQL("INSERT INTO qat_import_weights_new (date, weight, notes, receiverName) SELECT date, weight, notes, receiverName FROM qat_import_weights")
                    database.execSQL("DROP TABLE IF EXISTS qat_import_weights")
                    database.execSQL("ALTER TABLE qat_import_weights_new RENAME TO qat_import_weights")
                } catch (e: Exception) {}
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    database.execSQL("ALTER TABLE inventory_items ADD COLUMN receiverId INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {}
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `agency_profile` (
                        `id` INTEGER NOT NULL,
                        `agencyName` TEXT NOT NULL DEFAULT '',
                        `ownerName` TEXT NOT NULL DEFAULT '',
                        `phone` TEXT NOT NULL DEFAULT '',
                        `address` TEXT NOT NULL DEFAULT '',
                        `additionalContact` TEXT NOT NULL DEFAULT '',
                        `tagline` TEXT NOT NULL DEFAULT '',
                        `qatTypes` TEXT NOT NULL DEFAULT '',
                        `defaultCurrency` TEXT NOT NULL DEFAULT 'ريال يمني',
                        `commercialDetails` TEXT NOT NULL DEFAULT '',
                        `appDisplayName` TEXT NOT NULL DEFAULT 'ميزان القات',
                        `logoPath` TEXT NOT NULL DEFAULT '',
                        `headerEnabled` INTEGER NOT NULL DEFAULT 1,
                        `footerEnabled` INTEGER NOT NULL DEFAULT 1,
                        `headerText` TEXT NOT NULL DEFAULT '',
                        `footerText` TEXT NOT NULL DEFAULT '',
                        `isConfigured` INTEGER NOT NULL DEFAULT 0,
                        `updatedAt` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        private fun hasColumn(database: SupportSQLiteDatabase, table: String, column: String): Boolean {
            database.query("PRAGMA table_info(`$table`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIndex >= 0 && cursor.getString(nameIndex).equals(column, ignoreCase = true)) {
                        return true
                    }
                }
            }
            return false
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(database: SupportSQLiteDatabase) {
                if (!hasColumn(database, "agency_profile", "currencyMode")) {
                    database.execSQL("ALTER TABLE `agency_profile` ADD COLUMN `currencyMode` TEXT NOT NULL DEFAULT 'BOTH'")
                }
                database.execSQL(
                    """
                    UPDATE `agency_profile`
                    SET `currencyMode` = 'BOTH',
                        `defaultCurrency` = 'ريال يمني'
                    WHERE TRIM(`defaultCurrency`) = 'العملتين معاً'
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `transfer_recipients` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `phone` TEXT NOT NULL DEFAULT '',
                        `company` TEXT NOT NULL DEFAULT '',
                        `notes` TEXT NOT NULL DEFAULT '',
                        `isActive` INTEGER NOT NULL DEFAULT 1,
                        `createdAt` INTEGER NOT NULL DEFAULT 0,
                        `updatedAt` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_transfer_recipients_name` ON `transfer_recipients` (`name`)"
                )

                if (!hasColumn(database, "money_transfers", "recipientId")) {
                    database.execSQL("ALTER TABLE `money_transfers` ADD COLUMN `recipientId` INTEGER NOT NULL DEFAULT 0")
                }
                if (!hasColumn(database, "money_transfers", "status")) {
                    database.execSQL("ALTER TABLE `money_transfers` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'مكتملة'")
                }
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_money_transfers_recipientId` ON `money_transfers` (`recipientId`)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_money_transfers_date` ON `money_transfers` (`date`)"
                )

                val now = System.currentTimeMillis()
                database.execSQL(
                    """
                    INSERT INTO `transfer_recipients` (`name`, `phone`, `company`, `notes`, `isActive`, `createdAt`, `updatedAt`)
                    SELECT DISTINCT TRIM(`receiver`), '', '', '', 1, $now, $now
                    FROM `money_transfers`
                    WHERE TRIM(`receiver`) != ''
                      AND NOT EXISTS (
                          SELECT 1 FROM `transfer_recipients` tr WHERE TRIM(tr.`name`) = TRIM(`money_transfers`.`receiver`)
                      )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    UPDATE `money_transfers`
                    SET `recipientId` = COALESCE(
                        (SELECT tr.`id` FROM `transfer_recipients` tr WHERE TRIM(tr.`name`) = TRIM(`money_transfers`.`receiver`) ORDER BY tr.`id` ASC LIMIT 1),
                        0
                    )
                    WHERE `recipientId` = 0 AND TRIM(`receiver`) != ''
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tofan_al_aqsa_qat_db"
                )
                .addMigrations(
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15,
                    MIGRATION_15_16,
                    MIGRATION_16_17,
                    MIGRATION_17_18
                )
                .build()
                INSTANCE = instance
                instance
            }
        }

        fun closeDatabase() {
            INSTANCE?.close()
            INSTANCE = null
        }

        fun checkpoint() {
            try {
                INSTANCE?.openHelper?.writableDatabase?.execSQL("PRAGMA wal_checkpoint(FULL)")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
