package com.qatledger.developer.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Secret developer-only pricing table. Prices are nullable (NEVER defaulted/invented).
 */
@Entity(tableName = "dev_package_prices")
data class PackagePriceEntity(
    @PrimaryKey
    val packageCode: String, // "BASIC" or "PRO"
    val currency: String = "ريال يمني",
    val perpetualPrice: Double? = null,
    val monthlyPrice: Double? = null,
    val quarterlyPrice: Double? = null,
    val yearlyPrice: Double? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "dev_customers",
    indices = [Index("agencyName"), Index("phone")]
)
data class DeveloperCustomerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val agencyName: String,
    val ownerName: String,
    val phone: String = "",
    val city: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "dev_issued_licenses",
    indices = [Index("customerId"), Index("status"), Index("packageCode"), Index("paymentStatus")]
)
data class IssuedLicenseEntity(
    @PrimaryKey
    val licenseId: String,
    val customerId: Long,
    val packageCode: String,
    val licenseType: String,
    val durationDays: Int? = null,
    val featuresCsv: String,
    val agencyNameSnapshot: String,
    val ownerNameSnapshot: String,
    val phoneSnapshot: String = "",
    val installationId: String? = null,
    val issuedAtEpochMs: Long,
    val expiresAtEpochMs: Long? = null,
    val keyId: String,
    val status: String = "ACTIVE", // ACTIVE, REVOKED, SUPERSEDED, EXPIRED
    val priceCharged: Double? = null,
    val priceCurrency: String = "ريال يمني",
    val paidAmount: Double? = null,
    val paymentStatus: String = "UNPAID", // UNPAID, PARTIAL, PAID
    val paymentConfirmedAt: Long? = null,
    val notes: String = "",
    val compactToken: String,
    val qlicJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "dev_license_audit",
    indices = [Index("licenseId"), Index("timestamp")]
)
data class LicenseAuditEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val licenseId: String,
    val actionType: String,
    val detailsArabic: String,
    val previousInstallationId: String? = null,
    val newInstallationId: String? = null,
    val reason: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
