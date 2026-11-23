import 'dart:convert';
import 'dart:math';

import 'package:cryptography/cryptography.dart';
import 'package:sqflite/sqflite.dart';

import '../licensing/license_canonical.dart';
import '../licensing/license_models.dart';
import '../licensing/trusted_key_registry.dart';
import 'developer_database.dart';
import 'developer_key_manager.dart';

class SignedLicenseArtifact {
  const SignedLicenseArtifact({
    required this.payload,
    required this.keyId,
    required this.compactToken,
    required this.qlicJson,
  });

  final LicensePayload payload;
  final String keyId;
  final String compactToken;
  final String qlicJson;
}

class SignedRevocationArtifact {
  const SignedRevocationArtifact({
    required this.payload,
    required this.keyId,
    required this.revocationToken,
  });

  final RevocationPayload payload;
  final String keyId;
  final String revocationToken;
}

class DeveloperLicenseSigner {
  DeveloperLicenseSigner({
    required this.keyManager,
    required this.devDb,
  });

  static const String backupFormat = 'QAT_LEDGER_DEV_REGISTRY_V1';

  final DeveloperKeyManager keyManager;
  final DeveloperDatabaseService devDb;
  final Ed25519 _ed25519 = Ed25519();

  /// Backward-compatible v2 API kept intact for existing tests, while also supporting
  /// official Kotlin `licensing-core` payloads (`issueOfficialLicense`).
  Future<SignedLicenseArtifact> issueLicense({
    required String customerName,
    required String businessName,
    required String planCode,
    required String installationId,
    required int durationDays,
    int maxDevices = 1,
    List<String> features = const ['OFFLINE_PRO', 'BACKUP', 'REPORTS'],
    String notes = '',
  }) async {
    final kp = await keyManager.getOrGenerateSigningKeyPair();
    final pub = await kp.extractPublicKey();
    final keyId = TrustedPublicKeyRegistry.computeKeyIdFromRaw(pub.bytes);

    final nowMs = DateTime.now().millisecondsSinceEpoch;
    final expiresMs = durationDays <= 0
        ? 0
        : nowMs + Duration(days: durationDays).inMilliseconds;

    final rndHex = List<int>.generate(4, (_) => Random.secure().nextInt(256))
        .map((b) => b.toRadixString(16).padLeft(2, '0'))
        .join()
        .toUpperCase();
    final licenseId = 'QAT-${DateTime.now().year}-$rndHex';

    final payload = LicensePayload(
      format: LicenseCanonical.canonicalPayloadFormat,
      licenseId: licenseId,
      product: 'QAT_LEDGER',
      edition: 'CUSTOMER',
      customerName: customerName.trim(),
      businessName: businessName.trim(),
      planCode: planCode.trim(),
      issuedAtEpochMs: nowMs,
      expiresAtEpochMs: expiresMs,
      installationId: installationId.trim().isEmpty ? '*' : installationId.trim().toUpperCase(),
      maxDevices: maxDevices.clamp(1, 999).toInt(),
      features: LicenseCanonical.normalizeFeatures(features),
      canonicalFlavor: LicenseCanonicalFlavor.flutterJsonV1,
    );

    final canonical = LicenseCanonical.canonicalBytes(payload);
    final signature = await _ed25519.sign(canonical, keyPair: kp);
    final compact = LicenseCanonical.encodeCompactToken(
      payload: payload,
      signatureBytes: signature.bytes,
    );
    final qlic = LicenseCanonical.encodeQlicJson(
      keyId: keyId,
      payload: payload,
      signatureBytes: signature.bytes,
    );

    final db = await devDb.database;
    await db.transaction<void>((txn) async {
      await txn.insert('issued_licenses', {
        'license_id': licenseId,
        'plan_code': payload.planCode,
        'package_code': PackageTier.fromRaw(planCode).code,
        'license_type': durationDays <= 0 ? 'PERPETUAL' : 'SUBSCRIPTION',
        'duration_days': durationDays <= 0 ? null : durationDays,
        'customer_id': null,
        'installation_id': payload.installationId,
        'licensee_name': payload.customerName,
        'business_name': payload.businessName,
        'phone_snapshot': '',
        'issued_at_ms': nowMs,
        'expires_at_ms': expiresMs,
        'max_devices': payload.maxDevices,
        'features_csv': payload.features.join(','),
        'token_compact': compact,
        'qlic_json': qlic,
        'key_id': keyId,
        'status': 'ACTIVE',
        'notes': notes.trim(),
        'created_at_ms': nowMs,
        'updated_at_ms': nowMs,
      });
      await txn.insert('developer_audit_logs', {
        'action': 'ISSUE_LICENSE',
        'actor': 'developer',
        'target_id': licenseId,
        'details': 'plan=$planCode install=${payload.installationId} key=$keyId',
        'created_at_ms': nowMs,
      });
    });

    return SignedLicenseArtifact(
      payload: payload,
      keyId: keyId,
      compactToken: compact,
      qlicJson: qlic,
    );
  }

  /// Secret developer package pricing (never pre-populated / never leaked to customer builds).
  Future<void> saveSecretPackagePricing({
    required PackageTier packageTier,
    required String currency,
    double? perpetualPrice,
    double? monthlyPrice,
    double? quarterlyPrice,
    double? yearlyPrice,
  }) async {
    final db = await devDb.database;
    final now = DateTime.now().millisecondsSinceEpoch;
    final cleanCur = currency.trim().isEmpty ? 'ريال يمني' : currency.trim();
    await db.insert(
      'dev_package_prices',
      {
        'package_code': packageTier.code,
        'currency': cleanCur,
        'perpetual_price': perpetualPrice,
        'monthly_price': monthlyPrice,
        'quarterly_price': quarterlyPrice,
        'yearly_price': yearlyPrice,
        'updated_at_ms': now,
      },
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
    await db.insert('developer_audit_logs', {
      'action': 'PRICE_UPDATED',
      'actor': 'developer',
      'target_id': 'PRICING_${packageTier.code}',
      'details': 'تحديث تسعير ${packageTier.arabicName} ($cleanCur)',
      'created_at_ms': now,
    });
  }

  Future<int> upsertDevCustomer({
    int? existingId,
    required String agencyName,
    required String ownerName,
    String phone = '',
    String city = '',
    String notes = '',
  }) async {
    final db = await devDb.database;
    final now = DateTime.now().millisecondsSinceEpoch;
    final data = {
      'full_name': ownerName.trim(),
      'business_name': agencyName.trim(),
      'phone': phone.trim(),
      'city': city.trim(),
      'notes': notes.trim(),
      'updated_at_ms': now,
    };
    if (existingId != null && existingId > 0) {
      await db.update('dev_customers', data, where: 'id = ?', whereArgs: [existingId]);
      return existingId;
    }
    return db.insert('dev_customers', {
      ...data,
      'created_at_ms': now,
    });
  }

  /// Issues a Kotlin `licensing-core` compatible Ed25519 signed license (`QL1.` + `.qlic`).
  Future<SignedLicenseArtifact> issueOfficialLicense({
    int? customerId,
    required String agencyName,
    required String ownerName,
    String phone = '',
    required PackageTier packageTier,
    required LicenseType licenseType,
    int? durationDays,
    String? installationId,
    double? priceCharged,
    String priceCurrency = 'ريال يمني',
    double? paidAmount,
    required bool requestedPaidConfirmed,
    String notes = '',
    int? nowEpochMs,
    String? forcedLicenseId,
  }) async {
    final kp = await keyManager.getOrGenerateSigningKeyPair();
    final pub = await kp.extractPublicKey();
    final kotlinKeyId = TrustedPublicKeyRegistry.computeKotlinKeyIdFromRaw(pub.bytes);

    final now = nowEpochMs ?? DateTime.now().millisecondsSinceEpoch;
    final cleanInstall = (installationId ?? '').trim().toUpperCase();
    final effectiveInstall = cleanInstall.isEmpty ? '*' : cleanInstall;

    final int? validDays = licenseType == LicenseType.subscription
        ? (durationDays ?? 30).clamp(1, 36500).toInt()
        : null;
    final expiresMs =
        validDays == null ? 0 : now + Duration(days: validDays).inMilliseconds;

    final features = LicenseFeature.defaultFeaturesFor(packageTier)
        .map((f) => f.code)
        .toList();

    final licenseId = forcedLicenseId ?? _generateLicenseId(packageTier);
    final payload = LicensePayload(
      format: LicenseCanonical.canonicalPayloadFormat,
      licenseId: licenseId,
      product: 'QAT_LEDGER',
      edition: 'CUSTOMER',
      customerName: ownerName.trim(),
      businessName: agencyName.trim(),
      planCode: '${packageTier.code}_${licenseType.code}',
      issuedAtEpochMs: now,
      expiresAtEpochMs: expiresMs,
      installationId: effectiveInstall,
      maxDevices: 1,
      features: LicenseCanonical.normalizeFeatures(features),
      canonicalFlavor: LicenseCanonicalFlavor.kotlinLineV1,
      version: 1,
      packageTier: packageTier,
      licenseType: licenseType,
      phone: phone.trim(),
      keyIdField: kotlinKeyId,
      maxClockSkewSeconds: 300,
      notes: notes.trim(),
    );

    final canonical = LicenseCanonical.canonicalBytes(payload);
    final signature = await _ed25519.sign(canonical, keyPair: kp);
    final compact = LicenseCanonical.encodeCompactToken(
      payload: payload,
      signatureBytes: signature.bytes,
    );
    final qlic = LicenseCanonical.encodeQlicJson(
      keyId: kotlinKeyId,
      payload: payload,
      signatureBytes: signature.bytes,
    );

    final paymentStatus = _computePaymentStatus(
      priceCharged: priceCharged,
      paidAmount: paidAmount,
      explicitlyConfirmedPaid: requestedPaidConfirmed,
    );

    final db = await devDb.database;
    await db.transaction<void>((txn) async {
      await txn.insert(
        'issued_licenses',
        {
          'license_id': licenseId,
          'plan_code': payload.planCode,
          'package_code': packageTier.code,
          'license_type': licenseType.code,
          'duration_days': validDays,
          'customer_id': customerId,
          'installation_id': effectiveInstall,
          'licensee_name': ownerName.trim(),
          'business_name': agencyName.trim(),
          'phone_snapshot': phone.trim(),
          'issued_at_ms': now,
          'expires_at_ms': expiresMs,
          'max_devices': 1,
          'features_csv': payload.features.join(','),
          'token_compact': compact,
          'qlic_json': qlic,
          'key_id': kotlinKeyId,
          'status': 'ACTIVE',
          'price_charged': priceCharged,
          'price_currency': priceCurrency.trim().isEmpty ? 'ريال يمني' : priceCurrency.trim(),
          'paid_amount': paidAmount,
          'payment_status': paymentStatus,
          'payment_confirmed_at_ms': paymentStatus == 'PAID' ? now : null,
          'notes': notes.trim(),
          'created_at_ms': now,
          'updated_at_ms': now,
        },
        conflictAlgorithm: ConflictAlgorithm.replace,
      );
      await txn.insert('developer_audit_logs', {
        'action': 'ISSUED',
        'actor': 'developer',
        'target_id': licenseId,
        'details':
            'إصدار ${packageTier.arabicName} (${licenseType.arabicName}) للوكالة: ${agencyName.trim()} — حالة السداد: $paymentStatus',
        'new_installation_id': effectiveInstall == '*' ? null : effectiveInstall,
        'reason': notes.trim().isEmpty ? null : notes.trim(),
        'created_at_ms': now,
      });
    });

    return SignedLicenseArtifact(
      payload: payload,
      keyId: kotlinKeyId,
      compactToken: compact,
      qlicJson: qlic,
    );
  }

  Future<void> confirmOrUpdatePayment({
    required String licenseId,
    double? priceCharged,
    double? paidAmount,
    required bool explicitlyConfirmedPaid,
  }) async {
    final db = await devDb.database;
    final rows = await db.query(
      'issued_licenses',
      where: 'license_id = ?',
      whereArgs: [licenseId],
      limit: 1,
    );
    if (rows.isEmpty) return;
    final now = DateTime.now().millisecondsSinceEpoch;
    final nextStatus = _computePaymentStatus(
      priceCharged: priceCharged,
      paidAmount: paidAmount,
      explicitlyConfirmedPaid: explicitlyConfirmedPaid,
    );
    await db.update(
      'issued_licenses',
      {
        'price_charged': priceCharged,
        'paid_amount': paidAmount,
        'payment_status': nextStatus,
        if (nextStatus == 'PAID') 'payment_confirmed_at_ms': now,
        'updated_at_ms': now,
      },
      where: 'license_id = ?',
      whereArgs: [licenseId],
    );
    await db.insert('developer_audit_logs', {
      'action': 'PAYMENT_UPDATED',
      'actor': 'developer',
      'target_id': licenseId,
      'details': 'تحديث الدفعة: المدفوع=${paidAmount ?? 0} / السعر=${priceCharged ?? 0} — الحالة: $nextStatus',
      'created_at_ms': now,
    });
  }

  Future<SignedLicenseArtifact?> renewSubscription({
    required String previousLicenseId,
    required int additionalDays,
  }) async {
    final db = await devDb.database;
    final rows = await db.query(
      'issued_licenses',
      where: 'license_id = ?',
      whereArgs: [previousLicenseId],
      limit: 1,
    );
    if (rows.isEmpty) return null;
    final prev = rows.first;
    final now = DateTime.now().millisecondsSinceEpoch;
    final prevExp = (prev['expires_at_ms'] as num).toInt();
    final baseTime = prevExp > now ? prevExp : now;
    final newExpires =
        baseTime + Duration(days: additionalDays.clamp(1, 36500).toInt()).inMilliseconds;
    final effectiveDays =
        ((newExpires - now) / Duration.millisecondsPerDay).ceil().clamp(1, 36500).toInt();

    await db.update(
      'issued_licenses',
      {'status': 'SUPERSEDED', 'updated_at_ms': now},
      where: 'license_id = ?',
      whereArgs: [previousLicenseId],
    );

    final next = await issueOfficialLicense(
      customerId: (prev['customer_id'] as num?)?.toInt(),
      agencyName: (prev['business_name'] ?? '').toString(),
      ownerName: (prev['licensee_name'] ?? '').toString(),
      phone: (prev['phone_snapshot'] ?? '').toString(),
      packageTier: PackageTier.fromRaw(prev['package_code']?.toString()),
      licenseType: LicenseType.subscription,
      durationDays: effectiveDays,
      installationId: prev['installation_id']?.toString(),
      priceCharged: (prev['price_charged'] as num?)?.toDouble(),
      priceCurrency: (prev['price_currency'] ?? 'ريال يمني').toString(),
      paidAmount: (prev['paid_amount'] as num?)?.toDouble(),
      requestedPaidConfirmed: prev['payment_status'] == 'PAID',
      notes: 'تجديد للترخيص $previousLicenseId',
      nowEpochMs: now,
    );
    await db.insert('developer_audit_logs', {
      'action': 'RENEWED',
      'actor': 'developer',
      'target_id': previousLicenseId,
      'details': 'تم تجديد الاشتراك بإصدار الترخيص الجديد ${next.payload.licenseId} لمدة $effectiveDays يوم',
      'created_at_ms': now,
    });
    return next;
  }

  Future<SignedLicenseArtifact?> upgradeToPerpetual({
    required String previousLicenseId,
    PackageTier targetPackage = PackageTier.pro,
  }) async {
    final db = await devDb.database;
    final rows = await db.query(
      'issued_licenses',
      where: 'license_id = ?',
      whereArgs: [previousLicenseId],
      limit: 1,
    );
    if (rows.isEmpty) return null;
    final prev = rows.first;
    final now = DateTime.now().millisecondsSinceEpoch;
    await db.update(
      'issued_licenses',
      {'status': 'SUPERSEDED', 'updated_at_ms': now},
      where: 'license_id = ?',
      whereArgs: [previousLicenseId],
    );
    final next = await issueOfficialLicense(
      customerId: (prev['customer_id'] as num?)?.toInt(),
      agencyName: (prev['business_name'] ?? '').toString(),
      ownerName: (prev['licensee_name'] ?? '').toString(),
      phone: (prev['phone_snapshot'] ?? '').toString(),
      packageTier: targetPackage,
      licenseType: LicenseType.perpetual,
      durationDays: null,
      installationId: prev['installation_id']?.toString(),
      priceCharged: (prev['price_charged'] as num?)?.toDouble(),
      priceCurrency: (prev['price_currency'] ?? 'ريال يمني').toString(),
      paidAmount: (prev['paid_amount'] as num?)?.toDouble(),
      requestedPaidConfirmed: prev['payment_status'] == 'PAID',
      notes: 'ترقية من $previousLicenseId إلى ${targetPackage.arabicName} دائم',
      nowEpochMs: now,
    );
    await db.insert('developer_audit_logs', {
      'action': 'UPGRADED',
      'actor': 'developer',
      'target_id': previousLicenseId,
      'details': 'ترقية إلى ${next.payload.licenseId} (${targetPackage.arabicName} — دائم)',
      'created_at_ms': now,
    });
    return next;
  }

  Future<SignedLicenseArtifact?> changeInstallationId({
    required String previousLicenseId,
    required String? newInstallationId,
    required String reason,
  }) async {
    if (reason.trim().isEmpty) {
      throw ArgumentError('يجب توثيق سبب تغيير معرّف التثبيت في سجل المراجعة');
    }
    final db = await devDb.database;
    final rows = await db.query(
      'issued_licenses',
      where: 'license_id = ?',
      whereArgs: [previousLicenseId],
      limit: 1,
    );
    if (rows.isEmpty) return null;
    final prev = rows.first;
    final now = DateTime.now().millisecondsSinceEpoch;
    final lType = LicenseType.fromRaw(prev['license_type']?.toString());
    final exp = (prev['expires_at_ms'] as num).toInt();
    final remDays = lType == LicenseType.subscription && exp > 0
        ? ((exp - now) / Duration.millisecondsPerDay).ceil().clamp(1, 36500).toInt()
        : null;

    await db.update(
      'issued_licenses',
      {'status': 'SUPERSEDED', 'updated_at_ms': now},
      where: 'license_id = ?',
      whereArgs: [previousLicenseId],
    );
    final reissued = await issueOfficialLicense(
      customerId: (prev['customer_id'] as num?)?.toInt(),
      agencyName: (prev['business_name'] ?? '').toString(),
      ownerName: (prev['licensee_name'] ?? '').toString(),
      phone: (prev['phone_snapshot'] ?? '').toString(),
      packageTier: PackageTier.fromRaw(prev['package_code']?.toString()),
      licenseType: lType,
      durationDays: remDays,
      installationId: newInstallationId,
      priceCharged: (prev['price_charged'] as num?)?.toDouble(),
      priceCurrency: (prev['price_currency'] ?? 'ريال يمني').toString(),
      paidAmount: (prev['paid_amount'] as num?)?.toDouble(),
      requestedPaidConfirmed: prev['payment_status'] == 'PAID',
      notes: 'نقل معرّف التثبيت — ${reason.trim()}',
      nowEpochMs: now,
    );
    await db.insert('developer_audit_logs', {
      'action': 'INSTALLATION_CHANGED',
      'actor': 'developer',
      'target_id': reissued.payload.licenseId,
      'details': 'تغيير معرّف التثبيت للترخيص السابق $previousLicenseId',
      'prev_installation_id': prev['installation_id']?.toString(),
      'new_installation_id': newInstallationId?.trim().toUpperCase(),
      'reason': reason.trim(),
      'created_at_ms': now,
    });
    return reissued;
  }

  Future<SignedRevocationArtifact?> revokeLicense({
    required String licenseId,
    required String reason,
  }) async {
    if (reason.trim().isEmpty) {
      throw ArgumentError('يرجى كتابة سبب الإلغاء');
    }
    final db = await devDb.database;
    final now = DateTime.now().millisecondsSinceEpoch;
    final rows = await db.query(
      'issued_licenses',
      where: 'license_id = ?',
      whereArgs: [licenseId],
      limit: 1,
    );
    await db.update(
      'issued_licenses',
      {'status': 'REVOKED', 'updated_at_ms': now},
      where: 'license_id = ?',
      whereArgs: [licenseId],
    );
    await db.insert('developer_audit_logs', {
      'action': 'REVOKED',
      'actor': 'developer',
      'target_id': licenseId,
      'details': 'إلغاء الترخيص في سجل المطور المحلي.',
      'reason': reason.trim(),
      'created_at_ms': now,
    });

    final kp = await keyManager.loadSigningKeyPair();
    if (kp == null) return null;
    final pub = await kp.extractPublicKey();
    final keyId = TrustedPublicKeyRegistry.computeKotlinKeyIdFromRaw(pub.bytes);
    final installId = rows.isNotEmpty
        ? (rows.first['installation_id'] ?? '*').toString()
        : '*';
    final revPayload = RevocationPayload(
      licenseId: licenseId.trim().toUpperCase(),
      installationId: installId.trim().isEmpty ? '*' : installId.trim().toUpperCase(),
      keyId: keyId,
      revokedAtEpochMs: now,
      reason: reason.trim(),
    );
    final sig = await _ed25519.sign(
      LicenseCanonical.canonicalRevocationBytes(revPayload),
      keyPair: kp,
    );
    final token = LicenseCanonical.encodeRevocationToken(
      payload: revPayload,
      signatureBytes: sig.bytes,
    );
    return SignedRevocationArtifact(
      payload: revPayload,
      keyId: keyId,
      revocationToken: token,
    );
  }

  Future<SignedLicenseArtifact?> reissueLicense(String licenseId) async {
    final db = await devDb.database;
    final rows = await db.query(
      'issued_licenses',
      where: 'license_id = ?',
      whereArgs: [licenseId],
      limit: 1,
    );
    if (rows.isEmpty) return null;
    final cur = rows.first;
    final lType = LicenseType.fromRaw(cur['license_type']?.toString());
    final now = DateTime.now().millisecondsSinceEpoch;
    final exp = (cur['expires_at_ms'] as num).toInt();
    final remDays = lType == LicenseType.subscription && exp > 0
        ? ((exp - now) / Duration.millisecondsPerDay).ceil().clamp(1, 36500).toInt()
        : null;
    final reissued = await issueOfficialLicense(
      customerId: (cur['customer_id'] as num?)?.toInt(),
      agencyName: (cur['business_name'] ?? '').toString(),
      ownerName: (cur['licensee_name'] ?? '').toString(),
      phone: (cur['phone_snapshot'] ?? '').toString(),
      packageTier: PackageTier.fromRaw(cur['package_code']?.toString()),
      licenseType: lType,
      durationDays: remDays,
      installationId: cur['installation_id']?.toString(),
      priceCharged: (cur['price_charged'] as num?)?.toDouble(),
      priceCurrency: (cur['price_currency'] ?? 'ريال يمني').toString(),
      paidAmount: (cur['paid_amount'] as num?)?.toDouble(),
      requestedPaidConfirmed: cur['payment_status'] == 'PAID',
      notes: (cur['notes'] ?? '').toString(),
      nowEpochMs: (cur['issued_at_ms'] as num).toInt(),
      forcedLicenseId: licenseId,
    );
    await db.insert('developer_audit_logs', {
      'action': 'REISSUED',
      'actor': 'developer',
      'target_id': licenseId,
      'details': 'إعادة توقيع وإصدار نسخة الترخيص بالمفتاح ${reissued.keyId}',
      'created_at_ms': DateTime.now().millisecondsSinceEpoch,
    });
    return reissued;
  }

  /// Offline JSON Backup compatible with Kotlin `DeveloperLicenseRepository.exportRegistryBackupJson` (no private keys).
  Future<String> exportRegistryBackupJson() async {
    final db = await devDb.database;
    final prices = await db.query('dev_package_prices');
    final customers = await db.query('dev_customers');
    final licenses = await db.query('issued_licenses');
    final audits = await db.query('developer_audit_logs');

    return const JsonEncoder.withIndent('  ').convert({
      'format': backupFormat,
      'exportedAt': DateTime.now().millisecondsSinceEpoch,
      'prices': prices
          .map((p) => {
                'packageCode': p['package_code'],
                'currency': p['currency'],
                'perpetualPrice': p['perpetual_price'],
                'monthlyPrice': p['monthly_price'],
                'quarterlyPrice': p['quarterly_price'],
                'yearlyPrice': p['yearly_price'],
                'updatedAt': p['updated_at_ms'],
              })
          .toList(),
      'customers': customers
          .map((c) => {
                'id': c['id'],
                'agencyName': c['business_name'],
                'ownerName': c['full_name'],
                'phone': c['phone'],
                'city': c['city'],
                'notes': c['notes'],
                'createdAt': c['created_at_ms'],
                'updatedAt': c['updated_at_ms'],
              })
          .toList(),
      'licenses': licenses
          .map((l) => {
                'licenseId': l['license_id'],
                'customerId': l['customer_id'],
                'packageCode': l['package_code'],
                'licenseType': l['license_type'],
                'durationDays': l['duration_days'],
                'featuresCsv': l['features_csv'],
                'agencyNameSnapshot': l['business_name'],
                'ownerNameSnapshot': l['licensee_name'],
                'phoneSnapshot': l['phone_snapshot'],
                'installationId': l['installation_id'] == '*' ? null : l['installation_id'],
                'issuedAtEpochMs': l['issued_at_ms'],
                'expiresAtEpochMs': (l['expires_at_ms'] as num).toInt() <= 0 ? null : l['expires_at_ms'],
                'keyId': l['key_id'],
                'status': l['status'],
                'priceCharged': l['price_charged'],
                'priceCurrency': l['price_currency'],
                'paidAmount': l['paid_amount'],
                'paymentStatus': l['payment_status'],
                'paymentConfirmedAt': l['payment_confirmed_at_ms'],
                'notes': l['notes'],
                'compactToken': l['token_compact'],
                'qlicJson': l['qlic_json'],
                'createdAt': l['created_at_ms'],
                'updatedAt': l['updated_at_ms'],
              })
          .toList(),
      'audits': audits
          .map((a) => {
                'id': a['id'],
                'licenseId': a['target_id'],
                'actionType': a['action'],
                'detailsArabic': a['details'],
                'previousInstallationId': a['prev_installation_id'],
                'newInstallationId': a['new_installation_id'],
                'reason': a['reason'],
                'timestamp': a['created_at_ms'],
              })
          .toList(),
    });
  }

  Future<bool> importRegistryBackupJson(String jsonText) async {
    try {
      final Object? rawRoot = jsonDecode(jsonText);
      if (rawRoot is! Map<Object?, Object?>) return false;
      final root = rawRoot.map<String, Object?>(
        (k, v) => MapEntry<String, Object?>(k.toString(), v),
      );
      if (root['format'] != backupFormat) return false;
      final db = await devDb.database;

      await db.transaction<void>((txn) async {
        final rawPrices = root['prices'];
        if (rawPrices is List<Object?>) {
          for (final p in rawPrices) {
            if (p is! Map<Object?, Object?>) continue;
            final m = p.map<String, Object?>(
              (k, v) => MapEntry<String, Object?>(k.toString(), v),
            );
            await txn.insert(
              'dev_package_prices',
              {
                'package_code': m['packageCode'],
                'currency': m['currency'] ?? 'ريال يمني',
                'perpetual_price': m['perpetualPrice'],
                'monthly_price': m['monthlyPrice'],
                'quarterly_price': m['quarterlyPrice'],
                'yearly_price': m['yearlyPrice'],
                'updated_at_ms':
                    m['updatedAt'] ?? DateTime.now().millisecondsSinceEpoch,
              },
              conflictAlgorithm: ConflictAlgorithm.replace,
            );
          }
        }
        final rawCustomers = root['customers'];
        if (rawCustomers is List<Object?>) {
          for (final c in rawCustomers) {
            if (c is! Map<Object?, Object?>) continue;
            final m = c.map<String, Object?>(
              (k, v) => MapEntry<String, Object?>(k.toString(), v),
            );
            await txn.insert(
              'dev_customers',
              {
                'id': m['id'],
                'full_name': m['ownerName'] ?? '',
                'business_name': m['agencyName'] ?? '',
                'phone': m['phone'] ?? '',
                'city': m['city'] ?? '',
                'notes': m['notes'] ?? '',
                'created_at_ms':
                    m['createdAt'] ?? DateTime.now().millisecondsSinceEpoch,
                'updated_at_ms':
                    m['updatedAt'] ?? DateTime.now().millisecondsSinceEpoch,
              },
              conflictAlgorithm: ConflictAlgorithm.replace,
            );
          }
        }
        final rawLicenses = root['licenses'];
        if (rawLicenses is List<Object?>) {
          for (final l in rawLicenses) {
            if (l is! Map<Object?, Object?>) continue;
            final m = l.map<String, Object?>(
              (k, v) => MapEntry<String, Object?>(k.toString(), v),
            );
            final exp = m['expiresAtEpochMs'];
            await txn.insert(
              'issued_licenses',
              {
                'license_id': m['licenseId'],
                'plan_code': '${m['packageCode']}_${m['licenseType']}',
                'package_code': m['packageCode'] ?? 'PRO',
                'license_type': m['licenseType'] ?? 'PERPETUAL',
                'duration_days': m['durationDays'],
                'customer_id': m['customerId'],
                'installation_id': m['installationId'] ?? '*',
                'licensee_name': m['ownerNameSnapshot'] ?? '',
                'business_name': m['agencyNameSnapshot'] ?? '',
                'phone_snapshot': m['phoneSnapshot'] ?? '',
                'issued_at_ms': m['issuedAtEpochMs'],
                'expires_at_ms': exp ?? 0,
                'max_devices': 1,
                'features_csv': m['featuresCsv'] ?? '',
                'token_compact': m['compactToken'] ?? '',
                'qlic_json': m['qlicJson'] ?? '',
                'key_id': m['keyId'] ?? '',
                'status': m['status'] ?? 'ACTIVE',
                'price_charged': m['priceCharged'],
                'price_currency': m['priceCurrency'] ?? 'ريال يمني',
                'paid_amount': m['paidAmount'],
                'payment_status': m['paymentStatus'] ?? 'UNPAID',
                'payment_confirmed_at_ms': m['paymentConfirmedAt'],
                'notes': m['notes'] ?? '',
                'created_at_ms':
                    m['createdAt'] ?? DateTime.now().millisecondsSinceEpoch,
                'updated_at_ms':
                    m['updatedAt'] ?? DateTime.now().millisecondsSinceEpoch,
              },
              conflictAlgorithm: ConflictAlgorithm.replace,
            );
          }
        }
        final rawAudits = root['audits'];
        if (rawAudits is List<Object?>) {
          for (final a in rawAudits) {
            if (a is! Map<Object?, Object?>) continue;
            final m = a.map<String, Object?>(
              (k, v) => MapEntry<String, Object?>(k.toString(), v),
            );
            await txn.insert('developer_audit_logs', {
              'action': m['actionType'] ?? 'AUDIT',
              'actor': 'developer',
              'target_id': m['licenseId'] ?? '',
              'details': m['detailsArabic'] ?? '',
              'prev_installation_id': m['previousInstallationId'],
              'new_installation_id': m['newInstallationId'],
              'reason': m['reason'],
              'created_at_ms':
                  m['timestamp'] ?? DateTime.now().millisecondsSinceEpoch,
            });
          }
        }
        await txn.insert('developer_audit_logs', {
          'action': 'BACKUP_RESTORED',
          'actor': 'developer',
          'target_id': 'SYSTEM_BACKUP',
          'details': 'استعادة سجل تراخيص المطور الاحتياطي دون المساس بسجلات الوكالة',
          'created_at_ms': DateTime.now().millisecondsSinceEpoch,
        });
      });
      return true;
    } catch (_) {
      return false;
    }
  }

  String _computePaymentStatus({
    double? priceCharged,
    double? paidAmount,
    required bool explicitlyConfirmedPaid,
  }) {
    final paid = paidAmount ?? 0.0;
    final price = priceCharged ?? 0.0;
    if (explicitlyConfirmedPaid) {
      if (price > 0.0 && paid > 0.0 && paid < price) return 'PARTIAL';
      return 'PAID';
    }
    return paid > 0.0 ? 'PARTIAL' : 'UNPAID';
  }

  String _generateLicenseId(PackageTier tier) {
    const chars = '23456789ABCDEFGHJKLMNPQRSTUVWXYZ';
    final rng = Random.secure();
    final suffix = List<String>.generate(
      6,
      (_) => chars[rng.nextInt(chars.length)],
    ).join();
    final prefix = tier == PackageTier.pro ? 'QLP' : 'QLB';
    return '$prefix-$suffix';
  }
}
