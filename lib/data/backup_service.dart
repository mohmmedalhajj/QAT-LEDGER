import 'dart:convert';
import 'dart:io';

import 'package:archive/archive.dart';
import 'package:crypto/crypto.dart';
import 'package:flutter/foundation.dart';
import 'package:path/path.dart' as p;
import 'package:sqflite/sqflite.dart';

import '../core/logo_storage.dart';
import 'database_service.dart';
import 'room_migrator.dart';
import 'schema.dart';

@immutable
class BackupResult {
  const BackupResult({
    required this.filePath,
    required this.fileName,
    required this.dbSha256,
    required this.sizeBytes,
    required this.rowCounts,
  });

  final String filePath;
  final String fileName;
  final String dbSha256;
  final int sizeBytes;
  final Map<String, int> rowCounts;
}

@immutable
class RestoreResult {
  const RestoreResult({
    required this.wasLegacyRoomDb,
    required this.rollbackBackupPath,
    required this.verifiedSha256,
    required this.rowCounts,
    this.postRestoreVerified = true,
  });

  final bool wasLegacyRoomDb;
  final String rollbackBackupPath;
  final String verifiedSha256;
  final Map<String, int> rowCounts;
  final bool postRestoreVerified;
}

@immutable
class BackupInspectionResult {
  const BackupInspectionResult({
    required this.isValid,
    required this.isLegacyRoomDb,
    required this.fileName,
    this.formatVersion = 0,
    this.dbVersion = 0,
    this.createdAtMs = 0,
    this.appEdition = '',
    this.verifiedSha256 = '',
    this.rowCounts = const {},
    this.errorMessage,
  });

  final bool isValid;
  final bool isLegacyRoomDb;
  final String fileName;
  final int formatVersion;
  final int dbVersion;
  final int createdAtMs;
  final String appEdition;
  final String verifiedSha256;
  final Map<String, int> rowCounts;
  final String? errorMessage;

  int get totalRecords => rowCounts.values.fold<int>(0, (a, b) => a + b);

  String get summaryLine {
    if (isLegacyRoomDb) {
      return 'قاعدة بيانات قديمة (${totalRecords > 0 ? '$totalRecords سجل' : 'SQLite/Room'})';
    }
    return 'إصدار قاعدة البيانات v$dbVersion • إجمالي السجلات: $totalRecords';
  }
}

class BackupService {
  BackupService({
    required this.dbService,
    required this.migrator,
  });

  final DatabaseService dbService;
  final RoomMigrator migrator;

  static const String backupExtension = '.qatbackup';
  static const List<String> supportedBackupExtensions = [
    '.qatbackup',
    '.qbak',
    '.db',
  ];

  static String formatArabicBackupFileName([DateTime? timestamp]) {
    final ts = timestamp ?? DateTime.now();
    final y = ts.year.toString().padLeft(4, '0');
    final m = ts.month.toString().padLeft(2, '0');
    final d = ts.day.toString().padLeft(2, '0');
    final hh = ts.hour.toString().padLeft(2, '0');
    final mm = ts.minute.toString().padLeft(2, '0');
    return 'نسخة_احتياطية_ميزان_القات_$y-$m-${d}_$hh-$mm$backupExtension';
  }

  Future<({Uint8List bytes, BackupResult metadata})> buildBackupPackageBytes({
    required String stagingDir,
    String appEdition = 'customer',
    String? agencyLogoPath,
    String? outputPathOverride,
  }) async {
    final res = await createAtomicBackup(
      outputDir: stagingDir,
      appEdition: appEdition,
      agencyLogoPath: agencyLogoPath,
      fileNameOverride: outputPathOverride == null ? null : p.basename(outputPathOverride),
    );
    final bytes = await File(res.filePath).readAsBytes();
    return (bytes: bytes, metadata: res);
  }

  Future<BackupResult> createAtomicBackup({
    required String outputDir,
    String appEdition = 'customer',
    String? agencyLogoPath,
    String? fileNameOverride,
  }) async {
    final db = await dbService.database;
    final dbPath = await dbService.resolveDatabasePath();
    final workDir = Directory(outputDir);
    await workDir.create(recursive: true);

    final ts = DateTime.now();
    final stamp =
        '${ts.year}${ts.month.toString().padLeft(2, '0')}${ts.day.toString().padLeft(2, '0')}_${ts.hour.toString().padLeft(2, '0')}${ts.minute.toString().padLeft(2, '0')}${ts.second.toString().padLeft(2, '0')}';

    final stagedDbPath = p.join(outputDir, '.staging_$stamp.db');
    if (File(stagedDbPath).existsSync()) {
      await File(stagedDbPath).delete();
    }

    // Use SQLite VACUUM INTO for a consistent snapshot without WAL sidecar hazards
    try {
      await db.execute("VACUUM INTO '${stagedDbPath.replaceAll("'", "''")}'");
    } catch (_) {
      try {
        await db.rawQuery('PRAGMA wal_checkpoint(TRUNCATE)');
      } catch (_) {
        // Best-effort WAL checkpoint before copying database file.
      }
      await File(dbPath).copy(stagedDbPath);
    }

    try {
      final dbBytes = await File(stagedDbPath).readAsBytes();
      final dbHash = sha256.convert(dbBytes).toString();
      final rowCounts = await _collectRowCounts(db);

      final manifest = {
        'format_version': 4,
        'app': 'QAT_LEDGER',
        'app_edition': appEdition,
        'db_version': QatLedgerSchema.currentVersion,
        'created_at_ms': ts.millisecondsSinceEpoch,
        'db_filename': QatLedgerSchema.databaseFileName,
        'db_sha256': dbHash,
        'row_counts': rowCounts,
        'device_bound_security_note':
            'Device-bound installationId, hardware keystore wraps, and offline license bindings are intentionally bound per device.',
      };
      final manifestBytes = utf8.encode(const JsonEncoder.withIndent('  ').convert(manifest));

      final archive = Archive()
        ..addFile(ArchiveFile('manifest.json', manifestBytes.length, manifestBytes))
        ..addFile(ArchiveFile(QatLedgerSchema.databaseFileName, dbBytes.length, dbBytes));

      if (agencyLogoPath != null &&
          agencyLogoPath.isNotEmpty &&
          File(agencyLogoPath).existsSync()) {
        final logoBytes = await File(agencyLogoPath).readAsBytes();
        if (logoBytes.isNotEmpty) {
          final ext = p.extension(agencyLogoPath);
          archive.addFile(ArchiveFile('agency_assets/logo$ext', logoBytes.length, logoBytes));
        }
      }

      final zipBytes = ZipEncoder().encode(archive)!;
      final outName = fileNameOverride ?? formatArabicBackupFileName(ts);
      var finalPath = p.join(outputDir, outName);
      if (File(finalPath).existsSync()) {
        final base = p.basenameWithoutExtension(outName);
        final ext = p.extension(outName);
        finalPath = p.join(outputDir, '${base}_${ts.second.toString().padLeft(2, '0')}$ext');
      }
      final tmpZipPath = '$finalPath.tmp';
      await File(tmpZipPath).writeAsBytes(zipBytes, flush: true);
      await File(tmpZipPath).rename(finalPath);

      return BackupResult(
        filePath: finalPath,
        fileName: p.basename(finalPath),
        dbSha256: dbHash,
        sizeBytes: zipBytes.length,
        rowCounts: rowCounts,
      );
    } finally {
      if (File(stagedDbPath).existsSync()) {
        await File(stagedDbPath).delete();
      }
    }
  }

  /// Validates format, manifest version, SHA-256, SQLite integrity, and FK constraints
  /// WITHOUT altering the live database.
  Future<BackupInspectionResult> inspectBackupFile(String sourceFilePath) async {
    final file = File(sourceFilePath);
    final fileName = p.basename(sourceFilePath);
    if (!file.existsSync()) {
      return BackupInspectionResult(
        isValid: false,
        isLegacyRoomDb: false,
        fileName: fileName,
        errorMessage: 'ملف النسخة الاحتياطية غير موجود.',
      );
    }

    try {
      final bytes = await file.readAsBytes();
      if (bytes.length < 16) {
        return BackupInspectionResult(
          isValid: false,
          isLegacyRoomDb: false,
          fileName: fileName,
          errorMessage: 'حجم الملف المختار صغير جداً أو غير صالح.',
        );
      }

      final isZip = bytes[0] == 0x50 && bytes[1] == 0x4B;
      if (!isZip) {
        final header = utf8.decode(bytes.sublist(0, 15), allowMalformed: true);
        if (!header.startsWith('SQLite format 3')) {
          return BackupInspectionResult(
            isValid: false,
            isLegacyRoomDb: false,
            fileName: fileName,
            errorMessage:
                'صيغة الملف غير مدعومة (يجب اختيار ملف .qatbackup أو .qbak أو قاعدة بيانات SQLite صالحة).',
          );
        }
        final tempDir = await Directory.systemTemp.createTemp('qat_inspect_');
        final tmpDb = p.join(tempDir.path, 'inspect.db');
        try {
          await File(tmpDb).writeAsBytes(bytes, flush: true);
          final db = await dbService.effectiveFactory.openDatabase(
            tmpDb,
            options: OpenDatabaseOptions(readOnly: true),
          );
          try {
            final integrity = await db.rawQuery('PRAGMA integrity_check');
            final ok = integrity.isNotEmpty &&
                integrity.first.values.first?.toString().toLowerCase() == 'ok';
            if (!ok) {
              return BackupInspectionResult(
                isValid: false,
                isLegacyRoomDb: false,
                fileName: fileName,
                errorMessage: 'فشل فحص سلامة قاعدة البيانات داخل الملف.',
              );
            }
            final hasCustBal = await db.rawQuery(
              "SELECT name FROM sqlite_master WHERE type='table' AND name='customer_balances'",
            );
            final isLegacy = hasCustBal.isEmpty;
            final counts = await _collectRowCounts(db);
            return BackupInspectionResult(
              isValid: true,
              isLegacyRoomDb: isLegacy,
              fileName: fileName,
              dbVersion: isLegacy ? 18 : QatLedgerSchema.currentVersion,
              verifiedSha256: sha256.convert(bytes).toString(),
              rowCounts: counts,
            );
          } finally {
            await db.close();
          }
        } finally {
          if (tempDir.existsSync()) tempDir.deleteSync(recursive: true);
        }
      }

      final archive = ZipDecoder().decodeBytes(bytes, verify: true);
      final manifestEntry = archive.findFile('manifest.json');
      final dbEntry = archive.findFile(QatLedgerSchema.databaseFileName);
      if (manifestEntry == null || dbEntry == null) {
        return BackupInspectionResult(
          isValid: false,
          isLegacyRoomDb: false,
          fileName: fileName,
          errorMessage:
              'ملف النسخة الاحتياطية غير مكتمل: ينقصه manifest.json أو قاعدة البيانات.',
        );
      }

      final manifestMap = jsonDecode(utf8.decode(manifestEntry.content as List<int>))
          as Map<String, dynamic>;
      final appName = (manifestMap['app'] ?? '').toString();
      if (appName.isNotEmpty && appName != 'QAT_LEDGER') {
        return BackupInspectionResult(
          isValid: false,
          isLegacyRoomDb: false,
          fileName: fileName,
          errorMessage: 'هذا الملف لا ينتمي إلى نظام ميزان القات.',
        );
      }

      final formatVersion = (manifestMap['format_version'] as num?)?.toInt() ?? 0;
      if (formatVersion < 1 || formatVersion > 10) {
        return BackupInspectionResult(
          isValid: false,
          isLegacyRoomDb: false,
          fileName: fileName,
          errorMessage: 'إصدار تنسيق النسخة الاحتياطية غير مدعوم ($formatVersion).',
        );
      }

      final dbBytes = dbEntry.content as List<int>;
      final expectedSha = (manifestMap['db_sha256'] ?? '').toString();
      final actualSha = sha256.convert(dbBytes).toString();
      if (expectedSha.isEmpty || expectedSha != actualSha) {
        return BackupInspectionResult(
          isValid: false,
          isLegacyRoomDb: false,
          fileName: fileName,
          errorMessage:
              'فشل التحقق من البصمة الرقمية (SHA-256)؛ قد يكون ملف النسخة تالفاً أو معدّلاً.',
        );
      }

      final tempDir = await Directory.systemTemp.createTemp('qat_inspect_zip_');
      final tmpDb = p.join(tempDir.path, 'inspect.db');
      try {
        await File(tmpDb).writeAsBytes(dbBytes, flush: true);
        final db = await dbService.effectiveFactory.openDatabase(
          tmpDb,
          options: OpenDatabaseOptions(readOnly: false),
        );
        try {
          final integrity = await db.rawQuery('PRAGMA integrity_check');
          final ok = integrity.isNotEmpty &&
              integrity.first.values.first?.toString().toLowerCase() == 'ok';
          if (!ok) {
            return BackupInspectionResult(
              isValid: false,
              isLegacyRoomDb: false,
              fileName: fileName,
              errorMessage: 'قاعدة البيانات داخل الأرشيف تالفة.',
            );
          }
          await db.execute('PRAGMA foreign_keys = ON');
          final fk = await db.rawQuery('PRAGMA foreign_key_check');
          if (fk.isNotEmpty) {
            return BackupInspectionResult(
              isValid: false,
              isLegacyRoomDb: false,
              fileName: fileName,
              errorMessage:
                  'توجد مخالفات قيود مرجعية داخل النسخة (${fk.length} مخالفة).',
            );
          }
          final counts = await _collectRowCounts(db);
          return BackupInspectionResult(
            isValid: true,
            isLegacyRoomDb: false,
            fileName: fileName,
            formatVersion: formatVersion,
            dbVersion: (manifestMap['db_version'] as num?)?.toInt() ?? 2,
            createdAtMs: (manifestMap['created_at_ms'] as num?)?.toInt() ?? 0,
            appEdition: (manifestMap['app_edition'] ?? '').toString(),
            verifiedSha256: actualSha,
            rowCounts: counts,
          );
        } finally {
          await db.close();
        }
      } finally {
        if (tempDir.existsSync()) tempDir.deleteSync(recursive: true);
      }
    } catch (e) {
      return BackupInspectionResult(
        isValid: false,
        isLegacyRoomDb: false,
        fileName: fileName,
        errorMessage: 'فشل فحص النسخة الاحتياطية: $e',
      );
    }
  }

  Future<RestoreResult> restoreFromFile(String sourceFilePath) async {
    final inspection = await inspectBackupFile(sourceFilePath);
    if (!inspection.isValid) {
      throw StateError(
        inspection.errorMessage ?? 'ملف النسخة الاحتياطية غير صالح للاستعادة.',
      );
    }

    final file = File(sourceFilePath);
    final dbPath = await dbService.resolveDatabasePath();
    final dbDir = p.dirname(dbPath);
    final rollbackDir = Directory(p.join(dbDir, 'restore_rollbacks'));
    await rollbackDir.create(recursive: true);

    // Capture automatic rollback snapshot of live database before touching anything
    final rollbackRes = await createAtomicBackup(outputDir: rollbackDir.path);

    final bytes = await file.readAsBytes();
    final isZip = bytes.length >= 4 && bytes[0] == 0x50 && bytes[1] == 0x4B;

    if (!isZip) {
      return _restoreRawSqliteFile(
        sourceSqlitePath: sourceFilePath,
        rollbackBackupPath: rollbackRes.filePath,
      );
    }

    // Parse archive & extract verified DB
    final archive = ZipDecoder().decodeBytes(bytes, verify: true);
    final dbEntry = archive.findFile(QatLedgerSchema.databaseFileName)!;
    final dbBytes = dbEntry.content as List<int>;
    final actualSha = sha256.convert(dbBytes).toString();

    final stagePath = p.join(dbDir, '.restore_stage_${DateTime.now().millisecondsSinceEpoch}.db');
    await File(stagePath).writeAsBytes(dbBytes, flush: true);

    try {
      final stageDb = await dbService.effectiveFactory.openDatabase(
        stagePath,
        options: OpenDatabaseOptions(readOnly: false),
      );
      final Map<String, int> counts;
      try {
        final integrity = await stageDb.rawQuery('PRAGMA integrity_check');
        final ok = integrity.isNotEmpty &&
            integrity.first.values.first?.toString().toLowerCase() == 'ok';
        if (!ok) throw StateError('فشل فحص سلامة قاعدة البيانات داخل النسخة.');
        await stageDb.execute('PRAGMA foreign_keys = ON');
        final fk = await stageDb.rawQuery('PRAGMA foreign_key_check');
        if (fk.isNotEmpty) {
          throw StateError('فشل فحص القيود المرجعية في النسخة المستعادة (${fk.length} مخالفة).');
        }
        counts = await _collectRowCounts(stageDb);
      } finally {
        await stageDb.close();
      }

      // Atomic swap with rollback protection
      await dbService.close();
      for (final ext in ['-wal', '-shm']) {
        final f = File('$dbPath$ext');
        if (f.existsSync()) await f.delete();
      }
      await File(stagePath).rename(dbPath);
      await dbService.reopenAfterRestore();
      final liveDb = await dbService.database;

      // Post-restore integrity verification
      final postCheck = await liveDb.rawQuery('PRAGMA integrity_check');
      final postOk = postCheck.isNotEmpty &&
          postCheck.first.values.first?.toString().toLowerCase() == 'ok';
      if (!postOk) {
        throw StateError('فشل فحص سلامة قاعدة البيانات بعد الاستعادة.');
      }

      // Restore bundled agency logo into durable storage if present
      for (final entry in archive.files) {
        if (entry.isFile && entry.name.startsWith('agency_assets/logo')) {
          final ext = p.extension(entry.name);
          final tmpLogo = p.join(dbDir, '.restored_logo_tmp$ext');
          await File(tmpLogo).writeAsBytes(entry.content as List<int>, flush: true);
          final durableLogo = await LogoStorage.persistLogoFile(
            tmpLogo,
            customDirectoryPath: dbDir,
          );
          if (File(tmpLogo).existsSync()) await File(tmpLogo).delete();
          await liveDb.insert(
            'app_settings',
            {
              'key': 'agency_logo_path',
              'value': durableLogo,
              'updated_at_ms': DateTime.now().millisecondsSinceEpoch,
            },
            conflictAlgorithm: ConflictAlgorithm.replace,
          );
          break;
        }
      }

      return RestoreResult(
        wasLegacyRoomDb: false,
        rollbackBackupPath: rollbackRes.filePath,
        verifiedSha256: actualSha,
        rowCounts: counts,
        postRestoreVerified: true,
      );
    } catch (e) {
      if (File(stagePath).existsSync()) await File(stagePath).delete();
      // Attempt automatic rollback to snapshot if live swap occurred
      try {
        final rbBytes = await File(rollbackRes.filePath).readAsBytes();
        final rbArchive = ZipDecoder().decodeBytes(rbBytes, verify: true);
        final rbDb = rbArchive.findFile(QatLedgerSchema.databaseFileName);
        if (rbDb != null) {
          await dbService.close();
          await File(dbPath).writeAsBytes(rbDb.content as List<int>, flush: true);
        }
      } catch (_) {}
      await dbService.reopenAfterRestore();
      rethrow;
    }
  }

  Future<RestoreResult> _restoreRawSqliteFile({
    required String sourceSqlitePath,
    required String rollbackBackupPath,
  }) async {
    final testDb = await dbService.effectiveFactory.openDatabase(
      sourceSqlitePath,
      options: OpenDatabaseOptions(readOnly: true),
    );
    bool looksLikeLegacyRoom = false;
    try {
      final hasSalesItems = await testDb.rawQuery(
        "SELECT name FROM sqlite_master WHERE type='table' AND name='customer_balances'",
      );
      looksLikeLegacyRoom = hasSalesItems.isEmpty;
    } finally {
      await testDb.close();
    }

    if (looksLikeLegacyRoom) {
      final summary = await migrator.migrateIfNeeded(explicitSourceDbPath: sourceSqlitePath);
      return RestoreResult(
        wasLegacyRoomDb: true,
        rollbackBackupPath: rollbackBackupPath,
        verifiedSha256: summary.sourceSha256,
        rowCounts: summary.rowCounts,
        postRestoreVerified: true,
      );
    }

    // Raw v2 sqlite file
    final rawBytes = await File(sourceSqlitePath).readAsBytes();
    final sha = sha256.convert(rawBytes).toString();
    final dbPath = await dbService.resolveDatabasePath();
    final stagePath = '$dbPath.stage';
    await File(sourceSqlitePath).copy(stagePath);
    try {
      final stageDb = await dbService.effectiveFactory.openDatabase(
        stagePath,
        options: OpenDatabaseOptions(readOnly: false),
      );
      final Map<String, int> counts;
      try {
        final integrity = await stageDb.rawQuery('PRAGMA integrity_check');
        final ok = integrity.isNotEmpty &&
            integrity.first.values.first?.toString().toLowerCase() == 'ok';
        if (!ok) throw StateError('قاعدة البيانات تالفة.');
        counts = await _collectRowCounts(stageDb);
      } finally {
        await stageDb.close();
      }
      await dbService.close();
      for (final ext in ['-wal', '-shm']) {
        final f = File('$dbPath$ext');
        if (f.existsSync()) await f.delete();
      }
      await File(stagePath).rename(dbPath);
      await dbService.reopenAfterRestore();
      final liveDb = await dbService.database;
      final post = await liveDb.rawQuery('PRAGMA integrity_check');
      final postOk = post.isNotEmpty &&
          post.first.values.first?.toString().toLowerCase() == 'ok';
      return RestoreResult(
        wasLegacyRoomDb: false,
        rollbackBackupPath: rollbackBackupPath,
        verifiedSha256: sha,
        rowCounts: counts,
        postRestoreVerified: postOk,
      );
    } catch (e) {
      if (File(stagePath).existsSync()) await File(stagePath).delete();
      await dbService.reopenAfterRestore();
      rethrow;
    }
  }

  Future<Map<String, int>> _collectRowCounts(Database db) async {
    const tables = [
      'qat_types',
      'inventory_batches',
      'qat_import_weights',
      'customers',
      'sales',
      'customer_payments',
      'suppliers',
      'supplier_purchases',
      'supplier_payments',
      'expenses',
      'qat_tax_entries',
      'transfer_recipients',
      'money_transfers',
    ];
    final out = <String, int>{};
    for (final t in tables) {
      try {
        final count = Sqflite.firstIntValue(await db.rawQuery('SELECT COUNT(*) FROM $t')) ?? 0;
        out[t] = count;
      } catch (_) {
        out[t] = 0;
      }
    }
    return out;
  }

  /// Creates an automatic `.qatbackup` snapshot in [rollbackDir], then clears all
  /// operational accounting ledgers atomically. Preserves agency branding/users unless
  /// [fullFactoryReset] is true; never touches device-bound license activation.
  Future<BackupResult> resetAllAccountingData({
    required String rollbackDir,
    String appEdition = 'customer',
    String? agencyLogoPath,
    bool fullFactoryReset = false,
  }) async {
    final rollback = await createAtomicBackup(
      outputDir: rollbackDir,
      appEdition: appEdition,
      agencyLogoPath: agencyLogoPath,
    );

    final db = await dbService.database;
    await db.transaction<void>((txn) async {
      const operationalTables = [
        'sale_batch_deductions',
        'sales_invoice_items',
        'sales',
        'customer_payments',
        'customer_balances',
        'customers',
        'supplier_purchase_items',
        'supplier_purchases',
        'supplier_payments',
        'supplier_balances',
        'suppliers',
        'worker_handoffs',
        'inventory_batches',
        'qat_import_weights',
        'inventory_receivers',
        'qat_types',
        'expenses',
        'qat_tax_entries',
        'money_transfers',
        'transfer_recipients',
      ];
      for (final table in operationalTables) {
        final exists = await txn.rawQuery(
          "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
          [table],
        );
        if (exists.isNotEmpty) {
          await txn.delete(table);
        }
      }

      if (fullFactoryReset) {
        await txn.delete('app_settings');
        await txn.delete('ledger_accounts');
        final batch = txn.batch();
        for (final acc in QatLedgerSchema.defaultLedgerAccounts) {
          batch.insert(
            'ledger_accounts',
            acc,
            conflictAlgorithm: ConflictAlgorithm.ignore,
          );
        }
        await batch.commit(noResult: true);
      } else {
        await txn.delete(
          'app_settings',
          where: 'key IN (?, ?)',
          whereArgs: ['daily_settlements_v1', 'inventory_reconciliation_v1'],
        );
      }
    });

    return rollback;
  }
}
