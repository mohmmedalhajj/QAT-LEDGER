package com.qatledger.developer.data

import android.content.Context
import com.qatledger.developer.crypto.DeveloperKeyManager
import com.qatledger.developer.crypto.LicenseSigner
import com.qatledger.licensing.LicenseFeature
import com.qatledger.licensing.LicensePayload
import com.qatledger.licensing.LicenseType
import com.qatledger.licensing.PackageId
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

class DeveloperLicenseRepository(context: Context) {

    private val db = DeveloperLicenseDatabase.getInstance(context)
    private val priceDao = db.packagePriceDao()
    private val customerDao = db.customerDao()
    private val licenseDao = db.issuedLicenseDao()
    private val auditDao = db.auditDao()

    val keyManager = DeveloperKeyManager(context.applicationContext)
    val signer = LicenseSigner(keyManager)

    init {
        keyManager.registerAllKnownPublicKeysInSession()
    }

    fun observePrices(): Flow<List<PackagePriceEntity>> = priceDao.observeAll()
    fun observeCustomers(): Flow<List<DeveloperCustomerEntity>> = customerDao.observeAll()
    fun observeIssuedLicenses(): Flow<List<IssuedLicenseEntity>> = licenseDao.observeAll()
    fun observeAllAudits(): Flow<List<LicenseAuditEntity>> = auditDao.observeAll()
    fun observeAuditsForLicense(licenseId: String): Flow<List<LicenseAuditEntity>> = auditDao.observeForLicense(licenseId)

    /**
     * Secret pricing management — never pre-populates or invents default prices.
     */
    suspend fun saveSecretPackagePricing(
        packageId: PackageId,
        currency: String,
        perpetualPrice: Double?,
        monthlyPrice: Double?,
        quarterlyPrice: Double?,
        yearlyPrice: Double?
    ) {
        val now = System.currentTimeMillis()
        priceDao.upsert(
            PackagePriceEntity(
                packageCode = packageId.code,
                currency = currency.ifBlank { "ريال يمني" },
                perpetualPrice = perpetualPrice,
                monthlyPrice = monthlyPrice,
                quarterlyPrice = quarterlyPrice,
                yearlyPrice = yearlyPrice,
                updatedAt = now
            )
        )
        auditDao.insert(
            LicenseAuditEntity(
                licenseId = "PRICING_${packageId.code}",
                actionType = "PRICE_UPDATED",
                detailsArabic = "تحديث تسعير ${packageId.arabicName} (${currency.ifBlank { "ريال يمني" }})",
                timestamp = now
            )
        )
    }

    suspend fun upsertCustomer(
        existingId: Long = 0L,
        agencyName: String,
        ownerName: String,
        phone: String,
        city: String,
        notes: String
    ): Long {
        val now = System.currentTimeMillis()
        return if (existingId > 0L) {
            val prev = customerDao.getById(existingId)
            val updated = (prev ?: DeveloperCustomerEntity(id = existingId, agencyName = agencyName, ownerName = ownerName))
                .copy(
                    agencyName = agencyName.trim(),
                    ownerName = ownerName.trim(),
                    phone = phone.trim(),
                    city = city.trim(),
                    notes = notes.trim(),
                    updatedAt = now
                )
            customerDao.update(updated)
            existingId
        } else {
            customerDao.insert(
                DeveloperCustomerEntity(
                    agencyName = agencyName.trim(),
                    ownerName = ownerName.trim(),
                    phone = phone.trim(),
                    city = city.trim(),
                    notes = notes.trim(),
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    /**
     * Issues a new signed offline license.
     * Enforces mandatory explicit confirmation before recording status as `PAID`.
     */
    suspend fun issueNewLicense(
        customerId: Long,
        agencyName: String,
        ownerName: String,
        phone: String,
        packageId: PackageId,
        licenseType: LicenseType,
        durationDays: Int?,
        customFeatures: Set<LicenseFeature>? = null,
        installationId: String?,
        priceCharged: Double?,
        priceCurrency: String,
        paidAmount: Double?,
        requestedPaidConfirmed: Boolean,
        notes: String,
        nowEpochMs: Long = System.currentTimeMillis()
    ): IssuedLicenseEntity {
        val activeKey = keyManager.getActiveKey()
        val licenseId = generateLicenseId(packageId)
        val normalizedInstallId = installationId?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }

        val validDays = if (licenseType == LicenseType.SUBSCRIPTION) {
            (durationDays ?: 30).coerceAtLeast(1)
        } else {
            null
        }
        val expiresAt = validDays?.let { nowEpochMs + it.toLong() * 86_400_000L }
        val resolvedFeatures = (customFeatures ?: LicenseFeature.defaultFeaturesFor(packageId)).let {
            if (packageId == PackageId.BASIC) it.filter { f -> !f.isProOnly }.toSet() else it
        }

        val payload = LicensePayload(
            licenseId = licenseId,
            version = LicensePayload.CURRENT_VERSION,
            packageId = packageId,
            licenseType = licenseType,
            features = resolvedFeatures,
            agencyName = agencyName.trim(),
            ownerName = ownerName.trim(),
            phone = phone.trim(),
            installationId = normalizedInstallId,
            issuedAtEpochMs = nowEpochMs,
            expiresAtEpochMs = expiresAt,
            keyId = activeKey.keyId,
            notes = notes.trim()
        )
        val artifacts = signer.signPayload(payload, generateQr = false)

        val resolvedPaymentStatus = computePaymentStatus(
            priceCharged = priceCharged,
            paidAmount = paidAmount,
            explicitlyConfirmedPaid = requestedPaidConfirmed
        )

        val entity = IssuedLicenseEntity(
            licenseId = licenseId,
            customerId = customerId,
            packageCode = packageId.code,
            licenseType = licenseType.code,
            durationDays = validDays,
            featuresCsv = resolvedFeatures.map { it.code }.sorted().joinToString(","),
            agencyNameSnapshot = agencyName.trim(),
            ownerNameSnapshot = ownerName.trim(),
            phoneSnapshot = phone.trim(),
            installationId = normalizedInstallId,
            issuedAtEpochMs = nowEpochMs,
            expiresAtEpochMs = expiresAt,
            keyId = activeKey.keyId,
            status = "ACTIVE",
            priceCharged = priceCharged,
            priceCurrency = priceCurrency.ifBlank { "ريال يمني" },
            paidAmount = paidAmount,
            paymentStatus = resolvedPaymentStatus,
            paymentConfirmedAt = if (resolvedPaymentStatus == "PAID") nowEpochMs else null,
            notes = notes.trim(),
            compactToken = artifacts.compactToken,
            qlicJson = artifacts.qlicJson,
            createdAt = nowEpochMs,
            updatedAt = nowEpochMs
        )
        licenseDao.upsert(entity)
        auditDao.insert(
            LicenseAuditEntity(
                licenseId = licenseId,
                actionType = "ISSUED",
                detailsArabic = "إصدار ${packageId.arabicName} (${licenseType.arabicName}) للوكالة: ${agencyName.trim()} — حالة السداد: $resolvedPaymentStatus",
                newInstallationId = normalizedInstallId,
                reason = notes.trim().takeIf { it.isNotEmpty() },
                timestamp = nowEpochMs
            )
        )
        return entity
    }

    suspend fun confirmOrUpdatePayment(
        licenseId: String,
        priceCharged: Double?,
        paidAmount: Double?,
        explicitlyConfirmedPaid: Boolean
    ): IssuedLicenseEntity? {
        val current = licenseDao.getById(licenseId) ?: return null
        val now = System.currentTimeMillis()
        val nextStatus = computePaymentStatus(priceCharged, paidAmount, explicitlyConfirmedPaid)
        val updated = current.copy(
            priceCharged = priceCharged,
            paidAmount = paidAmount,
            paymentStatus = nextStatus,
            paymentConfirmedAt = if (nextStatus == "PAID") now else current.paymentConfirmedAt,
            updatedAt = now
        )
        licenseDao.upsert(updated)
        auditDao.insert(
            LicenseAuditEntity(
                licenseId = licenseId,
                actionType = "PAYMENT_UPDATED",
                detailsArabic = "تحديث الدفعة: المدفوع=${paidAmount ?: 0.0} / السعر=${priceCharged ?: 0.0} — الحالة: $nextStatus",
                timestamp = now
            )
        )
        return updated
    }

    suspend fun renewSubscription(
        previousLicenseId: String,
        additionalDays: Int,
        priceCharged: Double? = null,
        paidAmount: Double? = null,
        explicitlyConfirmedPaid: Boolean = false,
        notes: String = ""
    ): IssuedLicenseEntity? {
        val prev = licenseDao.getById(previousLicenseId) ?: return null
        val now = System.currentTimeMillis()
        val pkg = PackageId.fromCode(prev.packageCode) ?: PackageId.BASIC
        val baseTime = maxOf(now, prev.expiresAtEpochMs ?: now)
        val newExpires = baseTime + additionalDays.coerceAtLeast(1).toLong() * 86_400_000L
        val effectiveDays = ((newExpires - now) / 86_400_000L).toInt().coerceAtLeast(1)

        licenseDao.upsert(prev.copy(status = "SUPERSEDED", updatedAt = now))
        val newLicense = issueNewLicense(
            customerId = prev.customerId,
            agencyName = prev.agencyNameSnapshot,
            ownerName = prev.ownerNameSnapshot,
            phone = prev.phoneSnapshot,
            packageId = pkg,
            licenseType = LicenseType.SUBSCRIPTION,
            durationDays = effectiveDays,
            customFeatures = LicenseFeature.fromCodes(prev.featuresCsv.split(",")),
            installationId = prev.installationId,
            priceCharged = priceCharged,
            priceCurrency = prev.priceCurrency,
            paidAmount = paidAmount,
            requestedPaidConfirmed = explicitlyConfirmedPaid,
            notes = notes.ifBlank { "تجديد للترخيص $previousLicenseId" },
            nowEpochMs = now
        )
        auditDao.insert(
            LicenseAuditEntity(
                licenseId = previousLicenseId,
                actionType = "RENEWED",
                detailsArabic = "تم تجديد الاشتراك بإصدار الترخيص الجديد ${newLicense.licenseId} لمدة $effectiveDays يوم",
                reason = notes.takeIf { it.isNotBlank() },
                timestamp = now
            )
        )
        return newLicense
    }

    suspend fun upgradeToPerpetual(
        previousLicenseId: String,
        targetPackage: PackageId = PackageId.PRO,
        priceCharged: Double? = null,
        paidAmount: Double? = null,
        explicitlyConfirmedPaid: Boolean = false,
        notes: String = ""
    ): IssuedLicenseEntity? {
        val prev = licenseDao.getById(previousLicenseId) ?: return null
        val now = System.currentTimeMillis()
        licenseDao.upsert(prev.copy(status = "SUPERSEDED", updatedAt = now))
        val upgraded = issueNewLicense(
            customerId = prev.customerId,
            agencyName = prev.agencyNameSnapshot,
            ownerName = prev.ownerNameSnapshot,
            phone = prev.phoneSnapshot,
            packageId = targetPackage,
            licenseType = LicenseType.PERPETUAL,
            durationDays = null,
            customFeatures = LicenseFeature.defaultFeaturesFor(targetPackage),
            installationId = prev.installationId,
            priceCharged = priceCharged,
            priceCurrency = prev.priceCurrency,
            paidAmount = paidAmount,
            requestedPaidConfirmed = explicitlyConfirmedPaid,
            notes = notes.ifBlank { "ترقية من $previousLicenseId إلى ${targetPackage.arabicName} دائم" },
            nowEpochMs = now
        )
        auditDao.insert(
            LicenseAuditEntity(
                licenseId = previousLicenseId,
                actionType = "UPGRADED",
                detailsArabic = "ترقية إلى ${upgraded.licenseId} (${targetPackage.arabicName} — دائم)",
                timestamp = now
            )
        )
        return upgraded
    }

    suspend fun changeInstallationId(
        previousLicenseId: String,
        newInstallationId: String?,
        reason: String
    ): IssuedLicenseEntity? {
        require(reason.isNotBlank()) { "يجب توثيق سبب تغيير معرّف التثبيت في سجل المراجعة" }
        val prev = licenseDao.getById(previousLicenseId) ?: return null
        val now = System.currentTimeMillis()
        val pkg = PackageId.fromCode(prev.packageCode) ?: PackageId.BASIC
        val type = LicenseType.fromCode(prev.licenseType) ?: LicenseType.PERPETUAL
        val remainingDays = if (type == LicenseType.SUBSCRIPTION && prev.expiresAtEpochMs != null) {
            ((prev.expiresAtEpochMs - now) / 86_400_000L).toInt().coerceAtLeast(1)
        } else null

        licenseDao.upsert(prev.copy(status = "SUPERSEDED", updatedAt = now))
        val reissued = issueNewLicense(
            customerId = prev.customerId,
            agencyName = prev.agencyNameSnapshot,
            ownerName = prev.ownerNameSnapshot,
            phone = prev.phoneSnapshot,
            packageId = pkg,
            licenseType = type,
            durationDays = remainingDays,
            customFeatures = LicenseFeature.fromCodes(prev.featuresCsv.split(",")),
            installationId = newInstallationId,
            priceCharged = prev.priceCharged,
            priceCurrency = prev.priceCurrency,
            paidAmount = prev.paidAmount,
            requestedPaidConfirmed = prev.paymentStatus == "PAID",
            notes = "نقل معرّف التثبيت — ${reason.trim()}",
            nowEpochMs = now
        )
        auditDao.insert(
            LicenseAuditEntity(
                licenseId = reissued.licenseId,
                actionType = "INSTALLATION_CHANGED",
                detailsArabic = "تغيير معرّف التثبيت للترخيص السابق $previousLicenseId",
                previousInstallationId = prev.installationId,
                newInstallationId = newInstallationId?.trim()?.uppercase()?.takeIf { it.isNotEmpty() },
                reason = reason.trim(),
                timestamp = now
            )
        )
        return reissued
    }

    suspend fun revokeLicense(licenseId: String, reason: String): IssuedLicenseEntity? {
        require(reason.isNotBlank()) { "يرجى كتابة سبب الإلغاء" }
        val current = licenseDao.getById(licenseId) ?: return null
        val now = System.currentTimeMillis()
        val updated = current.copy(status = "REVOKED", updatedAt = now)
        licenseDao.upsert(updated)
        auditDao.insert(
            LicenseAuditEntity(
                licenseId = licenseId,
                actionType = "REVOKED",
                detailsArabic = "إلغاء الترخيص في سجل المطور المحلي (يمنع تجديده أو إعادة إصداره مستقبلاً).",
                reason = reason.trim(),
                timestamp = now
            )
        )
        return updated
    }

    suspend fun reissueLicense(licenseId: String): IssuedLicenseEntity? {
        val current = licenseDao.getById(licenseId) ?: return null
        val activeKey = keyManager.getActiveKey()
        val now = System.currentTimeMillis()
        val pkg = PackageId.fromCode(current.packageCode) ?: PackageId.BASIC
        val type = LicenseType.fromCode(current.licenseType) ?: LicenseType.PERPETUAL
        val payload = LicensePayload(
            licenseId = current.licenseId,
            packageId = pkg,
            licenseType = type,
            features = LicenseFeature.fromCodes(current.featuresCsv.split(",")),
            agencyName = current.agencyNameSnapshot,
            ownerName = current.ownerNameSnapshot,
            phone = current.phoneSnapshot,
            installationId = current.installationId,
            issuedAtEpochMs = current.issuedAtEpochMs,
            expiresAtEpochMs = current.expiresAtEpochMs,
            keyId = activeKey.keyId,
            notes = current.notes
        )
        val artifacts = signer.signPayload(payload, generateQr = false)
        val updated = current.copy(
            keyId = activeKey.keyId,
            compactToken = artifacts.compactToken,
            qlicJson = artifacts.qlicJson,
            updatedAt = now
        )
        licenseDao.upsert(updated)
        auditDao.insert(
            LicenseAuditEntity(
                licenseId = licenseId,
                actionType = "REISSUED",
                detailsArabic = "إعادة توقيع وإصدار نسخة الترخيص بالمفتاح ${activeKey.keyId}",
                timestamp = now
            )
        )
        return updated
    }

    /**
     * Offline Backup Export for Developer Registry (prices, customers, issued licenses, audit log).
     * Contains ZERO private signing keys.
     */
    suspend fun exportRegistryBackupJson(): String {
        val prices = priceDao.getAllDirect()
        val customers = customerDao.getAllDirect()
        val licenses = licenseDao.getAllDirect()
        val audits = auditDao.getAllDirect()

        return JSONObject().apply {
            put("format", BACKUP_FORMAT)
            put("exportedAt", System.currentTimeMillis())
            put("prices", JSONArray().apply {
                prices.forEach { p ->
                    put(JSONObject().apply {
                        put("packageCode", p.packageCode)
                        put("currency", p.currency)
                        put("perpetualPrice", p.perpetualPrice ?: JSONObject.NULL)
                        put("monthlyPrice", p.monthlyPrice ?: JSONObject.NULL)
                        put("quarterlyPrice", p.quarterlyPrice ?: JSONObject.NULL)
                        put("yearlyPrice", p.yearlyPrice ?: JSONObject.NULL)
                        put("updatedAt", p.updatedAt)
                    })
                }
            })
            put("customers", JSONArray().apply {
                customers.forEach { c ->
                    put(JSONObject().apply {
                        put("id", c.id)
                        put("agencyName", c.agencyName)
                        put("ownerName", c.ownerName)
                        put("phone", c.phone)
                        put("city", c.city)
                        put("notes", c.notes)
                        put("createdAt", c.createdAt)
                        put("updatedAt", c.updatedAt)
                    })
                }
            })
            put("licenses", JSONArray().apply {
                licenses.forEach { l ->
                    put(JSONObject().apply {
                        put("licenseId", l.licenseId)
                        put("customerId", l.customerId)
                        put("packageCode", l.packageCode)
                        put("licenseType", l.licenseType)
                        put("durationDays", l.durationDays ?: JSONObject.NULL)
                        put("featuresCsv", l.featuresCsv)
                        put("agencyNameSnapshot", l.agencyNameSnapshot)
                        put("ownerNameSnapshot", l.ownerNameSnapshot)
                        put("phoneSnapshot", l.phoneSnapshot)
                        put("installationId", l.installationId ?: JSONObject.NULL)
                        put("issuedAtEpochMs", l.issuedAtEpochMs)
                        put("expiresAtEpochMs", l.expiresAtEpochMs ?: JSONObject.NULL)
                        put("keyId", l.keyId)
                        put("status", l.status)
                        put("priceCharged", l.priceCharged ?: JSONObject.NULL)
                        put("priceCurrency", l.priceCurrency)
                        put("paidAmount", l.paidAmount ?: JSONObject.NULL)
                        put("paymentStatus", l.paymentStatus)
                        put("paymentConfirmedAt", l.paymentConfirmedAt ?: JSONObject.NULL)
                        put("notes", l.notes)
                        put("compactToken", l.compactToken)
                        put("qlicJson", l.qlicJson)
                        put("createdAt", l.createdAt)
                        put("updatedAt", l.updatedAt)
                    })
                }
            })
            put("audits", JSONArray().apply {
                audits.forEach { a ->
                    put(JSONObject().apply {
                        put("id", a.id)
                        put("licenseId", a.licenseId)
                        put("actionType", a.actionType)
                        put("detailsArabic", a.detailsArabic)
                        put("previousInstallationId", a.previousInstallationId ?: JSONObject.NULL)
                        put("newInstallationId", a.newInstallationId ?: JSONObject.NULL)
                        put("reason", a.reason ?: JSONObject.NULL)
                        put("timestamp", a.timestamp)
                    })
                }
            })
        }.toString(2)
    }

    suspend fun importRegistryBackupJson(jsonText: String): Boolean {
        return try {
            val root = JSONObject(jsonText)
            if (root.optString("format") != BACKUP_FORMAT) return false

            val pricesArr = root.optJSONArray("prices") ?: JSONArray()
            for (i in 0 until pricesArr.length()) {
                val p = pricesArr.getJSONObject(i)
                priceDao.upsert(
                    PackagePriceEntity(
                        packageCode = p.getString("packageCode"),
                        currency = p.optString("currency", "ريال يمني"),
                        perpetualPrice = if (p.isNull("perpetualPrice")) null else p.getDouble("perpetualPrice"),
                        monthlyPrice = if (p.isNull("monthlyPrice")) null else p.getDouble("monthlyPrice"),
                        quarterlyPrice = if (p.isNull("quarterlyPrice")) null else p.getDouble("quarterlyPrice"),
                        yearlyPrice = if (p.isNull("yearlyPrice")) null else p.getDouble("yearlyPrice"),
                        updatedAt = p.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }

            val custArr = root.optJSONArray("customers") ?: JSONArray()
            for (i in 0 until custArr.length()) {
                val c = custArr.getJSONObject(i)
                customerDao.insert(
                    DeveloperCustomerEntity(
                        id = c.getLong("id"),
                        agencyName = c.getString("agencyName"),
                        ownerName = c.getString("ownerName"),
                        phone = c.optString("phone", ""),
                        city = c.optString("city", ""),
                        notes = c.optString("notes", ""),
                        createdAt = c.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = c.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }

            val licArr = root.optJSONArray("licenses") ?: JSONArray()
            for (i in 0 until licArr.length()) {
                val l = licArr.getJSONObject(i)
                licenseDao.upsert(
                    IssuedLicenseEntity(
                        licenseId = l.getString("licenseId"),
                        customerId = l.getLong("customerId"),
                        packageCode = l.getString("packageCode"),
                        licenseType = l.getString("licenseType"),
                        durationDays = if (l.isNull("durationDays")) null else l.getInt("durationDays"),
                        featuresCsv = l.getString("featuresCsv"),
                        agencyNameSnapshot = l.getString("agencyNameSnapshot"),
                        ownerNameSnapshot = l.getString("ownerNameSnapshot"),
                        phoneSnapshot = l.optString("phoneSnapshot", ""),
                        installationId = if (l.isNull("installationId")) null else l.getString("installationId"),
                        issuedAtEpochMs = l.getLong("issuedAtEpochMs"),
                        expiresAtEpochMs = if (l.isNull("expiresAtEpochMs")) null else l.getLong("expiresAtEpochMs"),
                        keyId = l.getString("keyId"),
                        status = l.optString("status", "ACTIVE"),
                        priceCharged = if (l.isNull("priceCharged")) null else l.getDouble("priceCharged"),
                        priceCurrency = l.optString("priceCurrency", "ريال يمني"),
                        paidAmount = if (l.isNull("paidAmount")) null else l.getDouble("paidAmount"),
                        paymentStatus = l.optString("paymentStatus", "UNPAID"),
                        paymentConfirmedAt = if (l.isNull("paymentConfirmedAt")) null else l.getLong("paymentConfirmedAt"),
                        notes = l.optString("notes", ""),
                        compactToken = l.getString("compactToken"),
                        qlicJson = l.getString("qlicJson"),
                        createdAt = l.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = l.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }

            val auditsArr = root.optJSONArray("audits") ?: JSONArray()
            for (i in 0 until auditsArr.length()) {
                val a = auditsArr.getJSONObject(i)
                auditDao.insert(
                    LicenseAuditEntity(
                        id = a.optLong("id", 0L),
                        licenseId = a.getString("licenseId"),
                        actionType = a.getString("actionType"),
                        detailsArabic = a.getString("detailsArabic"),
                        previousInstallationId = if (a.isNull("previousInstallationId")) null else a.getString("previousInstallationId"),
                        newInstallationId = if (a.isNull("newInstallationId")) null else a.getString("newInstallationId"),
                        reason = if (a.isNull("reason")) null else a.getString("reason"),
                        timestamp = a.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }

            auditDao.insert(
                LicenseAuditEntity(
                    licenseId = "SYSTEM_BACKUP",
                    actionType = "BACKUP_RESTORED",
                    detailsArabic = "استعادة سجل تراخيص المطور الاحتياطي دون المساس بسجلات الوكالة",
                    timestamp = System.currentTimeMillis()
                )
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun computePaymentStatus(
        priceCharged: Double?,
        paidAmount: Double?,
        explicitlyConfirmedPaid: Boolean
    ): String {
        val paid = paidAmount ?: 0.0
        val price = priceCharged ?: 0.0
        if (explicitlyConfirmedPaid) {
            return when {
                price > 0.0 && paid > 0.0 && paid < price -> "PARTIAL"
                else -> "PAID"
            }
        }
        return if (paid > 0.0) "PARTIAL" else "UNPAID"
    }

    private fun generateLicenseId(packageId: PackageId): String {
        val rng = SecureRandom()
        val suffix = buildString(6) {
            val chars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
            repeat(6) { append(chars[rng.nextInt(chars.length)]) }
        }
        val prefix = if (packageId == PackageId.PRO) "QLP" else "QLB"
        return "$prefix-$suffix"
    }

    companion object {
        private const val BACKUP_FORMAT = "QAT_LEDGER_DEV_REGISTRY_V1"
    }
}
