package com.qatledger.developer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PackagePriceDao {
    @Query("SELECT * FROM dev_package_prices ORDER BY packageCode ASC")
    fun observeAll(): Flow<List<PackagePriceEntity>>

    @Query("SELECT * FROM dev_package_prices")
    suspend fun getAllDirect(): List<PackagePriceEntity>

    @Query("SELECT * FROM dev_package_prices WHERE packageCode = :code LIMIT 1")
    suspend fun getByCode(code: String): PackagePriceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PackagePriceEntity)
}

@Dao
interface DeveloperCustomerDao {
    @Query("SELECT * FROM dev_customers ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DeveloperCustomerEntity>>

    @Query("SELECT * FROM dev_customers ORDER BY updatedAt DESC")
    suspend fun getAllDirect(): List<DeveloperCustomerEntity>

    @Query("SELECT * FROM dev_customers WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DeveloperCustomerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DeveloperCustomerEntity): Long

    @Update
    suspend fun update(entity: DeveloperCustomerEntity)
}

@Dao
interface IssuedLicenseDao {
    @Query("SELECT * FROM dev_issued_licenses ORDER BY issuedAtEpochMs DESC")
    fun observeAll(): Flow<List<IssuedLicenseEntity>>

    @Query("SELECT * FROM dev_issued_licenses ORDER BY issuedAtEpochMs DESC")
    suspend fun getAllDirect(): List<IssuedLicenseEntity>

    @Query("SELECT * FROM dev_issued_licenses WHERE licenseId = :licenseId LIMIT 1")
    suspend fun getById(licenseId: String): IssuedLicenseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: IssuedLicenseEntity)
}

@Dao
interface LicenseAuditDao {
    @Query("SELECT * FROM dev_license_audit ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<LicenseAuditEntity>>

    @Query("SELECT * FROM dev_license_audit ORDER BY timestamp DESC")
    suspend fun getAllDirect(): List<LicenseAuditEntity>

    @Query("SELECT * FROM dev_license_audit WHERE licenseId = :licenseId ORDER BY timestamp DESC")
    fun observeForLicense(licenseId: String): Flow<List<LicenseAuditEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LicenseAuditEntity): Long
}
