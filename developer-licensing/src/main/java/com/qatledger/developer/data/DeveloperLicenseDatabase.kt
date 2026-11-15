package com.qatledger.developer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Separate Room database reserved strictly for Developer Build.
 * Completely independent from customer accounting database `tofan_al_aqsa_qat_db`.
 */
@Database(
    entities = [
        PackagePriceEntity::class,
        DeveloperCustomerEntity::class,
        IssuedLicenseEntity::class,
        LicenseAuditEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class DeveloperLicenseDatabase : RoomDatabase() {
    abstract fun packagePriceDao(): PackagePriceDao
    abstract fun customerDao(): DeveloperCustomerDao
    abstract fun issuedLicenseDao(): IssuedLicenseDao
    abstract fun auditDao(): LicenseAuditDao

    companion object {
        const val DB_NAME = "qat_ledger_developer_registry.db"

        @Volatile
        private var INSTANCE: DeveloperLicenseDatabase? = null

        fun getInstance(context: Context): DeveloperLicenseDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DeveloperLicenseDatabase::class.java,
                    DB_NAME
                ).build().also { INSTANCE = it }
            }
        }

        fun closeForTests() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}
