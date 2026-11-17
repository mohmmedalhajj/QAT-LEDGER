import 'dart:convert';
import 'dart:io';

import 'package:archive/archive.dart';
import 'package:crypto/crypto.dart';
import 'package:flutter/foundation.dart';
import 'package:path/path.dart' as p;
import 'package:sqflite/sqflite.dart';

import 'database_service.dart';
import 'room_migrator.dart';
import 'schema.dart';

@immutable
class BackupResult {
  const BackupResult({
    required this.filePath,
    required this.dbSha256,
    required this.sizeBytes,
    required this.rowCounts,
  });

  final String filePath;
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
  });

  final bool wasLegacyRoomDb;
  final String rollbackBackupPath;
  final String verifiedSha256;
  final Map<String, int> rowCounts;
}

class BackupService {
  BackupService({
    required this.dbService,
    required this.migrator,
  });

  final DatabaseService dbService;
  final RoomMigrator migrator;

  Future<BackupResult> createAtomicBackup({
    required String outputDir,
    String appEdition = 'customer',
    String? agencyLogoPath,
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
        'format_version': 3,
        'app': 'QAT_LEDGER',
        'app_edition': appEdition,
        'db_version': QatLedgerSchema.currentVersion,
        'created_at_ms': ts.millisecondsSinceEpoch,
        'db_filename': QatLedgerSchema.databaseFileName,
        'db_sha256': dbHash,
        'row_counts': rowCounts,
      };
      final manifestBytes = utf8.encode(const JsonEncoder.withIndent('  ').convert(manifest));

      final archive = Archive()
        ..addFile(ArchiveFile('manifest.json', manifestBytes.length, manifestBytes))
        ..addFile(ArchiveFile(QatLedgerSchema.databaseFileName, dbBytes.length, dbBytes));

      if (agencyLogoPath != null &&
          agencyLogoPath.isNotEmpty &&
          File(agencyLogoPath).existsSync()) {
        final logoBytes = await File(agencyLogoPath).readAsBytes();
        final ext = p.extension(agencyLogoPath);
        archive.addFile(ArchiveFile('agency_assets/logo$ext', logoBytes.length, logoBytes));
      }

      final zipBytes = ZipEncoder().encode(archive)!;
      final finalPath = p.join(outputDir, 'QatLedger_Backup_$stamp.qbak');
      final tmpZipPath = '$finalPath.tmp';
      await File(tmpZipPath).writeAsBytes(zipBytes, flush: true);
      await File(tmpZipPath).rename(finalPath);

      return BackupResult(
        filePath: finalPath,
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

  Future<RestoreResult> restoreFromFile(String sourceFilePath) async {
    final file = File(sourceFilePath);
    if (!file.existsSync()) {
      throw StateError('ملف النسخة الاحتياطية غير موجود.');
    }

    final dbPath = await dbService.resolveDatabasePath();
    final dbDir = p.dirname(dbPath);
    final rollbackDir = Directory(p.join(dbDir, 'restore_rollbacks'));
    await rollbackDir.create(recursive: true);

    // Capture automatic rollback snapshot of live database before touching anything
    final rollbackRes = await createAtomicBackup(outputDir: rollbackDir.path);

    final bytes = await file.readAsBytes();
    final isZip = bytes.length >= 4 &&
        bytes[0] == 0x50 &&
        bytes[1] == 0x4B;

    if (!isZip) {
      // Raw SQLite file: verify SQLite header and inspect schema (v2 vs legacy Room <= 18)
      final header = bytes.length >= 16 ? utf8.decode(bytes.sublist(0, 15), allowMalformed: true) : '';
      if (!header.startsWith('SQLite format 3')) {
        throw StateError('صيغة الملف غير مدعومة (يجب أن يكون .qbak أو قاعدة بيانات SQLite صالحة).');
      }
      return _restoreRawSqliteFile(
        sourceSqlitePath: sourceFilePath,
        rollbackBackupPath: rollbackRes.filePath,
      );
    }

    // Parse .qbak ZIP archive & verify SHA-256 manifest
    final archive = ZipDecoder().decodeBytes(bytes, verify: true);
    final manifestEntry = archive.findFile('manifest.json');
    final dbEntry = archive.findFile(QatLedgerSchema.databaseFileName);
    if (manifestEntry == null || dbEntry == null) {
      throw StateError('ملف .qbak غير مكتمل: ينقصه manifest.json أو ملف قاعدة البيانات.');
    }

    final manifestMap = jsonDecode(utf8.decode(manifestEntry.content as List<int>))
        as Map<String, dynamic>;
    final expectedSha = (manifestMap['db_sha256'] ?? '').toString();
    final dbBytes = dbEntry.content as List<int>;
    final actualSha = sha256.convert(dbBytes).toString();
    if (expectedSha.isEmpty || expectedSha != actualSha) {
      throw StateError('فشل التحقق من البصمة الرقمية (SHA-256) للنسخة الاحتياطية؛ قد يكون الملف تالفاً.');
    }

    final stagePath = p.join(dbDir, '.restore_stage_${DateTime.now().millisecondsSinceEpoch}.db');
    await File(stagePath).writeAsBytes(dbBytes, flush: true);

    try {
      // Verify integrity & foreign keys on staged database before swapping
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
      // Ensure any v1/v2 backup upgrades idempotently to v3 schema columns/tables.
      await dbService.database;

      // Restore bundled agency logo if present in .qbak
      for (final entry in archive.files) {
        if (entry.isFile && entry.name.startsWith('agency_assets/logo')) {
          final ext = p.extension(entry.name);
          final restoredLogo = p.join(dbDir, 'agency_logo_restored$ext');
          await File(restoredLogo).writeAsBytes(entry.content as List<int>, flush: true);
          final db = await dbService.database;
          await db.insert(
            'app_settings',
            {
              'key': 'agency_logo_path',
              'value': restoredLogo,
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
      );
    } catch (e) {
      if (File(stagePath).existsSync()) await File(stagePath).delete();
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
      await dbService.database;
      return RestoreResult(
        wasLegacyRoomDb: false,
        rollbackBackupPath: rollbackBackupPath,
        verifiedSha256: sha,
        rowCounts: counts,
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
}
