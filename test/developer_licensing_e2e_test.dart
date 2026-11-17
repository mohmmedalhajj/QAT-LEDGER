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

import '_ci_diag_helper.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  sqfliteFfiInit();

  test('Developer signs license -> Customer verifies with public key only; expired license never destroys data', () async {
    await runWithCiDiag('developer_licensing_e2e_test', () async {
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
  });
}
