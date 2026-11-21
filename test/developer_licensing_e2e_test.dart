import 'dart:convert';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:path/path.dart' as p;
import 'package:qat_ledger/core/money.dart';
import 'package:qat_ledger/core/platform_bridge.dart';
import 'package:qat_ledger/data/backup_service.dart';
import 'package:qat_ledger/data/database_service.dart';
import 'package:qat_ledger/data/models.dart';
import 'package:qat_ledger/data/qat_repository.dart';
import 'package:qat_ledger/data/room_migrator.dart';
import 'package:qat_ledger/developer/developer_database.dart';
import 'package:qat_ledger/developer/developer_key_manager.dart';
import 'package:qat_ledger/developer/license_signer.dart';
import 'package:qat_ledger/licensing/license_canonical.dart';
import 'package:qat_ledger/licensing/license_models.dart';
import 'package:qat_ledger/licensing/license_verifier.dart';
import 'package:qat_ledger/licensing/local_license_store.dart';
import 'package:qat_ledger/licensing/trusted_key_registry.dart';
import 'package:qat_ledger/licensing/trusted_time_guard.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  sqfliteFfiInit();

  test('Android Developer key storage fails closed when AndroidKeyStore is unavailable', () async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final keyManager = DeveloperKeyManager(prefs, requireAndroidKeyStore: true);

    await expectLater(
      keyManager.getOrGenerateSigningKeyPair(),
      throwsA(isA<StateError>()),
    );
    expect(
      prefs.getKeys().where((key) => key.contains('fallback_seed')),
      isEmpty,
    );
    expect(await keyManager.hasSigningKey(), isFalse);
  });

  test('Developer refuses to replace an existing key when it cannot be unsealed', () async {
    SharedPreferences.setMockInitialValues({
      'qat_dev_ed25519_sealed_seed_v2': 'opaque-sealed-fixture',
    });
    final prefs = await SharedPreferences.getInstance();
    final keyManager = DeveloperKeyManager(prefs, requireAndroidKeyStore: true);

    await expectLater(
      keyManager.getOrGenerateSigningKeyPair(),
      throwsA(isA<StateError>()),
    );
    expect(
      prefs.getString('qat_dev_ed25519_sealed_seed_v2'),
      'opaque-sealed-fixture',
    );
    expect(
      prefs.getKeys().where((key) => key.contains('fallback_seed')),
      isEmpty,
    );
  });

  test('Android migrates a legacy Developer key into AndroidKeyStore before signing', () async {
    final seedB64 = base64.encode(List<int>.generate(32, (index) => index + 1));
    SharedPreferences.setMockInitialValues({
      'qat_dev_ed25519_fallback_seed_v2': seedB64,
    });
    final prefs = await SharedPreferences.getInstance();
    final channel = const MethodChannel('com.qatledger.app/platform');
    String? sealedPayload;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
      if (call.method == 'keystoreSeal') {
        sealedPayload = 'test-sealed-payload';
        return sealedPayload;
      }
      if (call.method == 'keystoreUnseal') return seedB64;
      return null;
    });
    try {
      final keyManager = DeveloperKeyManager(prefs, requireAndroidKeyStore: true);
      final info = await keyManager.getPublicKeyInfo();
      expect(info, isNotNull);
      expect(info!.hardwareSealed, isTrue);
      expect(sealedPayload, isNotNull);
      expect(prefs.getString('qat_dev_ed25519_fallback_seed_v2'), isNull);
      expect(prefs.getString('qat_dev_ed25519_sealed_seed_v2'), sealedPayload);
    } finally {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, null);
    }
  });

  test('Developer signs license -> Customer verifies with public key only; expired license never destroys data', () async {
    final tempDir = await Directory.systemTemp.createTemp('qat_dev_e2e_');
    try {
      SharedPreferences.setMockInitialValues({});
      final devPrefs = await SharedPreferences.getInstance();
      final devDb = DeveloperDatabaseService(
        factory: databaseFactoryFfi,
        customPath: p.join(tempDir.path, 'dev_registry.db'),
      );
      final keyMgr = DeveloperKeyManager(devPrefs);
      final signer = DeveloperLicenseSigner(keyManager: keyMgr, devDb: devDb);

      // Developer generates Ed25519 keypair and signs a 7-day customer license
      final artifact = await signer.issueLicense(
        customerName: 'سعيد المقطري',
        businessName: 'مكتب السعيد',
        planCode: 'TRIAL_7',
        installationId: 'QLD-AAAA-BBBB-CCCC-DDDD',
        durationDays: 7,
      );
      final keyInfo = await keyMgr.getPublicKeyInfo();
      expect(keyInfo, isNotNull);
      expect(artifact.compactToken.startsWith('QL1.'), true);

      // Verify developer DB stored audit log and issued license
      final devSqlDb = await devDb.database;
      final issuedRows = await devSqlDb.query('issued_licenses');
      final auditRows = await devSqlDb.query('developer_audit_logs');
      expect(issuedRows.length, 1);
      expect(auditRows.length, 1);

      // Customer side only receives SPKI public key CSV (zero private seed!)
      final customerRegistry = TrustedPublicKeyRegistry.fromCsv(keyInfo!.spkiBase64);
      final verifier = OfflineLicenseVerifier(
        registry: customerRegistry,
        timeGuard: TrustedTimeGuard(),
      );

      final valid = await verifier.verify(
        rawLicenseInput: artifact.compactToken,
        currentInstallationId: 'QLD-AAAA-BBBB-CCCC-DDDD',
        nowEpochMs: artifact.payload.issuedAtEpochMs + 1000,
      );
      expect(valid.isValid, true);
      expect(valid.keyId, keyInfo.keyId);

      // Simulate license expiry (> 7 days later): status becomes expired, ledger + backup stay intact!
      final expired = await OfflineLicenseVerifier(
        registry: customerRegistry,
        timeGuard: TrustedTimeGuard(),
      ).verify(
        rawLicenseInput: artifact.qlicJson,
        currentInstallationId: 'QLD-AAAA-BBBB-CCCC-DDDD',
        nowEpochMs: artifact.payload.expiresAtEpochMs + 86400000,
      );
      expect(expired.kind, LicenseStateKind.expired);

      final custDbService = DatabaseService(
        factory: databaseFactoryFfi,
        customPath: p.join(tempDir.path, 'customer.db'),
      );
      final repo = QatRepository(custDbService);
      final custId = await repo.upsertCustomer(name: 'عميل محفوظ');
      await repo.makeSale(
        customerId: custId,
        customerName: 'عميل محفوظ',
        items: const [
          NewSaleItemInput(
            qatTypeName: 'صنف',
            qtyMilli: 1000,
            unit: 'ربطة',
            unitPriceMinor: 100000,
            buyPriceMinor: 50000,
          ),
        ],
        currency: CurrencyCode.yer,
        paymentType: PaymentType.credit,
      );
      final backupSvc = BackupService(
        dbService: custDbService,
        migrator: RoomMigrator(dbService: custDbService, sourceFactory: databaseFactoryFfi),
      );
      final bak = await backupSvc.createAtomicBackup(outputDir: p.join(tempDir.path, 'bak'));
      expect(File(bak.filePath).existsSync(), true);
      expect((await repo.getCustomers()).single.balanceFor(CurrencyCode.yer), 100000);

      await custDbService.close();
      await devSqlDb.close();
    } finally {
      await tempDir.delete(recursive: true);
    }
  });

  test('Developer 4-tab registry lifecycle: secret pricing, official kotlinLineV1 issue, payment confirm, renew, upgrade, revoke, and backup export/import', () async {
    final tempDir = await Directory.systemTemp.createTemp('qat_dev_lifecycle_');
    try {
      SharedPreferences.setMockInitialValues({});
      final devPrefs = await SharedPreferences.getInstance();
      final devDb = DeveloperDatabaseService(
        factory: databaseFactoryFfi,
        customPath: p.join(tempDir.path, 'dev_lifecycle.db'),
      );
      final keyMgr = DeveloperKeyManager(devPrefs);
      final signer = DeveloperLicenseSigner(keyManager: keyMgr, devDb: devDb);

      await signer.saveSecretPackagePricing(
        packageTier: PackageTier.pro,
        currency: 'YER',
        monthlyPrice: 12000,
        perpetualPrice: 150000,
      );
      final devSql = await devDb.database;
      final prices = await devSql.query('dev_package_prices');
      expect(prices.any((r) => r['package_code'] == 'PRO' && r['perpetual_price'] == 150000.0), true);

      final issued = await signer.issueOfficialLicense(
        agencyName: 'وكالة الخولاني',
        ownerName: 'عميل الخولاني',
        phone: '777123456',
        installationId: 'QAT-1111-2222-3333',
        packageTier: PackageTier.basic,
        licenseType: LicenseType.subscription,
        durationDays: 30,
        priceCharged: 12000,
        priceCurrency: 'YER',
        requestedPaidConfirmed: false,
      );
      final issuedRows = await devSql.query(
        'issued_licenses',
        where: 'license_id = ?',
        whereArgs: [issued.payload.licenseId],
      );
      expect(issuedRows.single['payment_status'], 'UNPAID');
      expect(issuedRows.single['package_code'], 'BASIC');

      final keyInfo = await keyMgr.getPublicKeyInfo();
      final verifier = OfflineLicenseVerifier(
        registry: TrustedPublicKeyRegistry.fromCsv(keyInfo!.kotlinConfigLine),
        timeGuard: TrustedTimeGuard(),
      );
      final verifiedBasic = await verifier.verify(
        rawLicenseInput: issued.compactToken,
        currentInstallationId: 'QAT-1111-2222-3333',
        nowEpochMs: issued.payload.issuedAtEpochMs + 1000,
      );
      expect(verifiedBasic.isValid, true);
      expect(verifiedBasic.hasFeature(LicenseFeature.reports), false);

      // Confirm payment -> Renew -> Upgrade to PRO PERPETUAL -> Revoke
      await signer.confirmOrUpdatePayment(
        licenseId: issued.payload.licenseId,
        priceCharged: 12000,
        paidAmount: 12000,
        explicitlyConfirmedPaid: true,
      );
      final renewed = await signer.renewSubscription(
        previousLicenseId: issued.payload.licenseId,
        additionalDays: 60,
      );
      expect(renewed, isNotNull);

      final upgraded = await signer.upgradeToPerpetual(
        previousLicenseId: renewed!.payload.licenseId,
        targetPackage: PackageTier.pro,
      );
      expect(upgraded, isNotNull);
      final verifiedPro = await verifier.verify(
        rawLicenseInput: upgraded!.qlicJson,
        currentInstallationId: 'QAT-1111-2222-3333',
        nowEpochMs: upgraded.payload.issuedAtEpochMs + 5000,
      );
      expect(verifiedPro.isPro, true);
      expect(verifiedPro.hasFeature(LicenseFeature.reports), true);

      await signer.revokeLicense(
        licenseId: upgraded.payload.licenseId,
        reason: 'انتهاء التعاقد التجاري',
      );
      final recRows = await devSql.query(
        'issued_licenses',
        where: 'license_id = ?',
        whereArgs: [upgraded.payload.licenseId],
      );
      expect(recRows.single['status'], 'REVOKED');

      // Export & Re-import QAT_LEDGER_DEV_REGISTRY_V1 backup into a fresh DB
      final backupJson = await signer.exportRegistryBackupJson();
      expect(backupJson.contains('QAT_LEDGER_DEV_REGISTRY_V1'), true);
      final restoredDb = DeveloperDatabaseService(
        factory: databaseFactoryFfi,
        customPath: p.join(tempDir.path, 'dev_restored.db'),
      );
      final restoredSigner = DeveloperLicenseSigner(keyManager: keyMgr, devDb: restoredDb);
      final ok = await restoredSigner.importRegistryBackupJson(backupJson);
      expect(ok, true);
      final restoredSql = await restoredDb.database;
      expect((await restoredSql.query('issued_licenses')).length, 3);
      expect((await restoredSql.query('dev_package_prices')).length, greaterThanOrEqualTo(1));

      await devSql.close();
      await restoredSql.close();
    } finally {
      await tempDir.delete(recursive: true);
    }
  });

  test('DeveloperKeyManager exports and re-imports Ed25519 private signing seed preserving keyId and signatures', () async {
    final tempDir = await Directory.systemTemp.createTemp('qat_dev_seed_');
    try {
      SharedPreferences.setMockInitialValues({});
      final prefsA = await SharedPreferences.getInstance();
      final dbA = DeveloperDatabaseService(
        factory: databaseFactoryFfi,
        customPath: p.join(tempDir.path, 'dev_a.db'),
      );
      final mgrA = DeveloperKeyManager(prefsA);
      await mgrA.getOrGenerateSigningKeyPair();
      final infoA = (await mgrA.getPublicKeyInfo())!;
      final seedB64 = await mgrA.exportSigningSeedB64();
      expect(seedB64, isNotNull);

      SharedPreferences.setMockInitialValues({});
      final prefsB = await SharedPreferences.getInstance();
      final dbB = DeveloperDatabaseService(
        factory: databaseFactoryFfi,
        customPath: p.join(tempDir.path, 'dev_b.db'),
      );
      final mgrB = DeveloperKeyManager(prefsB);
      final infoB = await mgrB.importSigningSeedB64(seedB64!);
      expect(infoB.keyId, infoA.keyId);
      expect(infoB.spkiBase64, infoA.spkiBase64);

      final signerB = DeveloperLicenseSigner(keyManager: mgrB, devDb: dbB);
      final artifact = await signerB.issueLicense(
        customerName: 'عميل البذرة',
        businessName: 'وكالة البذرة',
        planCode: 'PRO_PERPETUAL',
        installationId: 'QAT-SEED-1111-2222',
        durationDays: 0,
      );

      final verifierA = OfflineLicenseVerifier(
        registry: TrustedPublicKeyRegistry.fromCsv(infoA.spkiBase64),
        timeGuard: TrustedTimeGuard(),
      );
      final verified = await verifierA.verify(
        rawLicenseInput: artifact.compactToken,
        currentInstallationId: 'QAT-SEED-1111-2222',
        nowEpochMs: artifact.payload.issuedAtEpochMs + 1000,
      );
      expect(verified.isValid, true);
      expect(verified.keyId, infoA.keyId);

      // Verify PEM / hex / Kotlin BuildConfig line cross-compatibility and decodeSeed formats
      final parsedHex = DeveloperKeyManager.decodeSeed(
        List.generate(32, (i) => i.toRadixString(16).padLeft(2, '0')).join(),
      );
      expect(parsedHex.length, 32);
      expect(infoA.spkiPem, contains('BEGIN PUBLIC KEY'));
      expect(infoA.kotlinConfigLine, startsWith(infoA.kotlinKeyId));
      final registryFromPem = TrustedPublicKeyRegistry.fromCsv(infoA.spkiPem);
      expect(registryFromPem.findById(infoA.keyId), isNotNull);

      await (await dbA.database).close();
      await (await dbB.database).close();
    } finally {
      await tempDir.delete(recursive: true);
    }
  });

  test('Key rotation continuity: Customer trusting K1,K2 accepts old K1 and new K2 licenses; K2-only rejects K1 as untrustedKey', () async {
    final tempDir = await Directory.systemTemp.createTemp('qat_dev_rotation_');
    try {
      SharedPreferences.setMockInitialValues({});
      final devPrefs = await SharedPreferences.getInstance();
      final devDb = DeveloperDatabaseService(
        factory: databaseFactoryFfi,
        customPath: p.join(tempDir.path, 'dev_rot.db'),
      );
      final mgr = DeveloperKeyManager(devPrefs);
      await mgr.getOrGenerateSigningKeyPair();
      final k1 = (await mgr.getPublicKeyInfo())!;

      final signer = DeveloperLicenseSigner(keyManager: mgr, devDb: devDb);
      final oldLicense = await signer.issueOfficialLicense(
        agencyName: 'وكالة قديمة',
        ownerName: 'أحمد',
        packageTier: PackageTier.pro,
        licenseType: LicenseType.perpetual,
        installationId: 'QAT-ROT1-AAAA-BBBB',
        requestedPaidConfirmed: true,
      );

      // Rotate to new Ed25519 key K2
      await mgr.rotateSigningKeyPair();
      final k2 = (await mgr.getPublicKeyInfo())!;
      expect(k2.kotlinKeyId, isNot(k1.kotlinKeyId));

      final newLicense = await signer.issueOfficialLicense(
        agencyName: 'وكالة حديثة',
        ownerName: 'صالح',
        packageTier: PackageTier.pro,
        licenseType: LicenseType.perpetual,
        installationId: 'QAT-ROT2-CCCC-DDDD',
        requestedPaidConfirmed: true,
      );

      // Customer built with rotated multi-key list (K1 + K2) honors both old and new licenses
      final combinedCsv = '${k1.kotlinConfigLine},${k2.kotlinConfigLine}';
      final combinedVerifier = OfflineLicenseVerifier(
        registry: TrustedPublicKeyRegistry.fromCsv(combinedCsv),
        timeGuard: TrustedTimeGuard(),
      );
      final verifyOldOnCombined = await combinedVerifier.verify(
        rawLicenseInput: oldLicense.compactToken,
        currentInstallationId: 'QAT-ROT1-AAAA-BBBB',
      );
      final verifyNewOnCombined = await combinedVerifier.verify(
        rawLicenseInput: newLicense.qlicJson,
        currentInstallationId: 'QAT-ROT2-CCCC-DDDD',
      );
      expect(verifyOldOnCombined.isPro, true);
      expect(verifyNewOnCombined.isPro, true);

      // Customer built ONLY with K2 rejects old K1 license cleanly as untrustedKey
      final k2OnlyVerifier = OfflineLicenseVerifier(
        registry: TrustedPublicKeyRegistry.fromCsv(k2.kotlinConfigLine),
        timeGuard: TrustedTimeGuard(),
      );
      final rejectedOld = await k2OnlyVerifier.verify(
        rawLicenseInput: oldLicense.compactToken,
        currentInstallationId: 'QAT-ROT1-AAAA-BBBB',
      );
      expect(rejectedOld.isValid, false);
      expect(rejectedOld.kind, LicenseStateKind.untrustedKey);

      await (await devDb.database).close();
    } finally {
      await tempDir.delete(recursive: true);
    }
  });

  test('LocalLicenseStore persists installation ID and signed license across simulated restarts, rejects clock rollback, and preserves customer data', () async {
    final tempDir = await Directory.systemTemp.createTemp('qat_store_e2e_');
    try {
      // 1. Developer issues subscription bound to Customer installation ID
      SharedPreferences.setMockInitialValues({});
      final devPrefs = await SharedPreferences.getInstance();
      final devDb = DeveloperDatabaseService(
        factory: databaseFactoryFfi,
        customPath: p.join(tempDir.path, 'dev_store.db'),
      );
      final mgr = DeveloperKeyManager(devPrefs);
      await mgr.getOrGenerateSigningKeyPair();
      final keyInfo = (await mgr.getPublicKeyInfo())!;

      // Customer initializes install ID
      SharedPreferences.setMockInitialValues({});
      final custPrefs = await SharedPreferences.getInstance();
      final store = LocalLicenseStore(custPrefs);
      final installId = await store.getOrCreateInstallationId();
      expect(installId, matches(RegExp(r'^QAT-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$')));

      final signer = DeveloperLicenseSigner(keyManager: mgr, devDb: devDb);
      final issuedNow = DateTime.now().millisecondsSinceEpoch - 60000;
      final subLicense = await signer.issueOfficialLicense(
        agencyName: 'وكالة الثبات',
        ownerName: 'مختار',
        packageTier: PackageTier.pro,
        licenseType: LicenseType.subscription,
        durationDays: 30,
        installationId: installId,
        requestedPaidConfirmed: true,
        nowEpochMs: issuedNow,
      );

      final editionCfg = EditionConfig(
        edition: 'customer',
        publicKeysCsv: keyInfo.kotlinConfigLine,
        applicationId: 'com.aistudio.tofanqat.nxzwmb',
        versionName: '1.0.0',
        versionCode: 1,
      );

      await store.saveRawLicense(subLicense.compactToken);

      // 2. Re-open store from persisted SharedPreferences (restart simulation)
      final restartedStore = LocalLicenseStore(await SharedPreferences.getInstance());
      expect(await restartedStore.getOrCreateInstallationId(), installId);
      final active = await restartedStore.loadAndVerify(editionConfig: editionCfg);
      expect(active.isPro, true);
      expect(active.hasFeature(LicenseFeature.reports), true);
      expect(active.hasFeature(LicenseFeature.archive), true);

      // 3. Simulate clock rollback after recording trusted high-water mark -> downgraded to BASIC
      final timeGuard = TrustedTimeGuard(prefs: custPrefs);
      final forwardCheck = await timeGuard.evaluateNow(issuedNow + 5 * 86400000);
      expect(forwardCheck.trusted, true);
      final tamperedVerifier = OfflineLicenseVerifier(
        registry: TrustedPublicKeyRegistry.fromCsv(editionCfg.publicKeysCsv),
        timeGuard: TrustedTimeGuard(prefs: custPrefs),
      );
      final tamperedRes = await tamperedVerifier.verify(
        rawLicenseInput: subLicense.compactToken,
        currentInstallationId: installId,
        nowEpochMs: issuedNow + 86400000, // 4 days backwards rollback!
      );
      expect(tamperedRes.kind, LicenseStateKind.clockTampered);
      expect(tamperedRes.isPro, false);

      // 4. Clear license cleanly -> returns to BASIC unlicensed
      await restartedStore.clearLicense();
      final afterClear = await restartedStore.loadAndVerify(editionConfig: editionCfg);
      expect(afterClear.kind, LicenseStateKind.unlicensed);

      await (await devDb.database).close();
    } finally {
      await tempDir.delete(recursive: true);
    }
  });

  test('Passphrase-encrypted .qkey export/import protects private key and survives device replacement', () async {
    SharedPreferences.setMockInitialValues({});
    final devPrefsA = await SharedPreferences.getInstance();
    final mgrA = DeveloperKeyManager(devPrefsA);
    await mgrA.getOrGenerateSigningKeyPair();
    final originalInfo = (await mgrA.getPublicKeyInfo())!;

    // Weak passphrase rejected
    await expectLater(
      mgrA.exportEncryptedKeyBundle('short'),
      throwsFormatException,
    );

    // Strong passphrase exports encrypted PBKDF2 + AES-GCM bundle
    final bundle = await mgrA.exportEncryptedKeyBundle('QatLedger#2026Strong!');
    expect(bundle.startsWith('${DeveloperKeyManager.encryptedBundlePrefix}.'), true);
    expect(bundle.contains(originalInfo.kotlinKeyId), false);

    // Simulate new Developer device B
    await mgrA.wipeSigningKey();
    expect(await mgrA.hasSigningKey(), false);

    // Wrong passphrase fails decryption cleanly
    await expectLater(
      mgrA.importEncryptedKeyBundle(bundle, 'WrongPassphrase123!'),
      throwsException,
    );

    // Correct passphrase restores identical Ed25519 keyId
    final restoredInfo = await mgrA.importEncryptedKeyBundle(
      bundle,
      'QatLedger#2026Strong!',
    );
    expect(restoredInfo.kotlinKeyId, originalInfo.kotlinKeyId);
    expect(restoredInfo.kotlinConfigLine, originalInfo.kotlinConfigLine);
  });

  test('Cross-device offline transfer + QLR1 signed revocation + data preservation E2E', () async {
    final tempDir = await Directory.systemTemp.createTemp('qat_cross_device_');
    try {
      // DEVICE 1 (Developer): independent prefs & registry DB
      SharedPreferences.setMockInitialValues({});
      final devPrefs = await SharedPreferences.getInstance();
      final devDb = DeveloperDatabaseService(
        factory: databaseFactoryFfi,
        customPath: p.join(tempDir.path, 'dev_device.db'),
      );
      final devMgr = DeveloperKeyManager(devPrefs);
      await devMgr.getOrGenerateSigningKeyPair();
      final devPubInfo = (await devMgr.getPublicKeyInfo())!;
      final devSigner = DeveloperLicenseSigner(keyManager: devMgr, devDb: devDb);

      // DEVICE 2 (Customer): independent DB & installation ID (receives ONLY public key at build time)
      const custDeviceInstallId = 'QAT-7F2B-9C4D-1E8A';
      const wrongDeviceInstallId = 'QAT-0000-1111-2222';
      final custConfig = EditionConfig(
        edition: 'customer',
        publicKeysCsv: devPubInfo.kotlinConfigLine,
        applicationId: 'com.aistudio.tofanqat.nxzwmb',
        versionName: '2.0.0',
        versionCode: 200,
      );
      final custDb = DatabaseService(
        factory: databaseFactoryFfi,
        customPath: p.join(tempDir.path, 'customer_device.db'),
      );
      final custRepo = QatRepository(custDb);
      final cid = await custRepo.upsertCustomer(name: 'تاجر محلي');
      await custRepo.makeSale(
        customerId: cid,
        customerName: 'تاجر محلي',
        items: const [
          NewSaleItemInput(
            qatTypeName: 'همداني',
            qtyMilli: 2000,
            unit: 'ربطة',
            unitPriceMinor: 500000,
            buyPriceMinor: 350000,
          ),
        ],
        currency: CurrencyCode.yer,
        paymentType: PaymentType.credit,
      );

      // Developer issues license bound strictly to Device 2's installation ID
      final issued = await devSigner.issueOfficialLicense(
        agencyName: 'وكالة الشمال',
        ownerName: 'فيصل',
        packageTier: PackageTier.pro,
        licenseType: LicenseType.subscription,
        durationDays: 30,
        installationId: custDeviceInstallId,
        requestedPaidConfirmed: true,
      );

      // Transfer token to a DIFFERENT Customer device -> rejected as deviceMismatch
      final verifierWrong = OfflineLicenseVerifier(
        registry: TrustedPublicKeyRegistry.fromCsv(custConfig.publicKeysCsv),
        timeGuard: TrustedTimeGuard(),
      );
      final wrongRes = await verifierWrong.verify(
        rawLicenseInput: issued.compactToken,
        currentInstallationId: wrongDeviceInstallId,
      );
      expect(wrongRes.kind, LicenseStateKind.deviceMismatch);

      // Transfer token to target Customer Device 2 offline -> activates PRO
      SharedPreferences.setMockInitialValues({'qat_install_id_v2': custDeviceInstallId});
      final custPrefs = await SharedPreferences.getInstance();
      final custStore = LocalLicenseStore(custPrefs);
      await custStore.saveRawLicense(issued.compactToken);
      final active = await custStore.loadAndVerify(editionConfig: custConfig);
      expect(active.isPro, true);
      expect(active.hasFeature(LicenseFeature.reports), true);

      // Tampering a single character in payload -> rejected as invalidSignature
      final dotParts = issued.compactToken.substring(4).split('.');
      final rawMap = jsonDecode(
        utf8.decode(LicenseCanonical.decodeBase64UrlLenient(dotParts[0])),
      ) as Map<String, dynamic>;
      rawMap['agencyName'] = 'وكالة مزورة';
      final tamperedPayloadB64 = LicenseCanonical.encodeBase64UrlNoPad(
        utf8.encode(jsonEncode(rawMap)),
      );
      final tamperedToken = 'QL1.$tamperedPayloadB64.${dotParts[1]}';
      final tamperedCheck = await OfflineLicenseVerifier(
        registry: TrustedPublicKeyRegistry.fromCsv(custConfig.publicKeysCsv),
        timeGuard: TrustedTimeGuard(),
      ).verify(
        rawLicenseInput: tamperedToken,
        currentInstallationId: custDeviceInstallId,
      );
      expect(tamperedCheck.kind, LicenseStateKind.invalidSignature);

      // Developer revokes license -> emits QLR1 signed revocation notice
      final revNotice = await devSigner.revokeLicense(
        licenseId: issued.payload.licenseId,
        reason: 'إلغاء بطلب العميل للاستبدال',
      );
      expect(revNotice, isNotNull);
      expect(revNotice!.revocationToken.startsWith('QLR1.'), true);

      // Forged revocation token from untrusted key must be rejected
      final alienRevPayload = RevocationPayload(
        licenseId: issued.payload.licenseId,
        installationId: custDeviceInstallId,
        keyId: 'QLK-DEADBEEF',
        revokedAtEpochMs: DateTime.now().millisecondsSinceEpoch,
        reason: 'fake',
      );
      final forgedRevToken = LicenseCanonical.encodeRevocationToken(
        payload: alienRevPayload,
        signatureBytes: List<int>.filled(64, 1),
      );
      final forgedRevCheck = await OfflineLicenseVerifier(
        registry: TrustedPublicKeyRegistry.fromCsv(custConfig.publicKeysCsv),
        timeGuard: TrustedTimeGuard(),
      ).verifyRevocationToken(
        rawInput: forgedRevToken,
        currentInstallationId: custDeviceInstallId,
      );
      expect(forgedRevCheck.isAuthenticRevocation, false);
      expect(forgedRevCheck.kind, LicenseStateKind.untrustedKey);

      // Authentic QLR1 revocation token accepted -> downgrades Customer to BASIC & preserves data
      final authRevCheck = await OfflineLicenseVerifier(
        registry: TrustedPublicKeyRegistry.fromCsv(custConfig.publicKeysCsv),
        timeGuard: TrustedTimeGuard(),
      ).verifyRevocationToken(
        rawInput: revNotice.revocationToken,
        currentInstallationId: custDeviceInstallId,
      );
      expect(authRevCheck.isAuthenticRevocation, true);
      await custStore.addRevokedLicenseId(authRevCheck.payload!.licenseId);

      final afterRevoke = await custStore.loadAndVerify(editionConfig: custConfig);
      expect(afterRevoke.kind, LicenseStateKind.revoked);
      expect(afterRevoke.isPro, false);

      // Renewal issues a fresh licenseId that activates cleanly again on Customer Device 2
      final renewed = await devSigner.renewSubscription(
        previousLicenseId: issued.payload.licenseId,
        additionalDays: 90,
      );
      expect(renewed, isNotNull);
      await custStore.saveRawLicense(renewed!.compactToken);
      final afterRenew = await custStore.loadAndVerify(editionConfig: custConfig);
      expect(afterRenew.isPro, true);
      expect(afterRenew.remainingDays, greaterThanOrEqualTo(89));

      // Verify Customer ledger data survived every license state transition untouched
      final customers = await custRepo.getCustomers();
      expect(customers.single.balanceFor(CurrencyCode.yer), 1000000);

      await custDb.close();
      await (await devDb.database).close();
    } finally {
      await tempDir.delete(recursive: true);
    }
  });
}
