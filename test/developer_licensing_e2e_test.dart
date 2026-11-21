import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:path/path.dart' as p;
import 'package:qat_ledger/core/money.dart';
import 'package:qat_ledger/data/backup_service.dart';
import 'package:qat_ledger/data/database_service.dart';
import 'package:qat_ledger/data/models.dart';
import 'package:qat_ledger/data/qat_repository.dart';
import 'package:qat_ledger/data/room_migrator.dart';
import 'package:qat_ledger/developer/developer_database.dart';
import 'package:qat_ledger/developer/developer_key_manager.dart';
import 'package:qat_ledger/developer/license_signer.dart';
import 'package:qat_ledger/licensing/license_models.dart';
import 'package:qat_ledger/licensing/license_verifier.dart';
import 'package:qat_ledger/licensing/trusted_key_registry.dart';
import 'package:qat_ledger/licensing/trusted_time_guard.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  sqfliteFfiInit();

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
}
