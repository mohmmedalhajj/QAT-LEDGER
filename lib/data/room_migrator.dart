import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:path/path.dart' as p;
import 'package:sqflite/sqflite.dart';

import '../core/date_utils.dart';
import '../core/money.dart';
import '../core/platform_bridge.dart';
import 'database_service.dart';
import 'models.dart';

/// Deterministic, idempotent migrator from legacy Android Room v1..v18 (`tofan_al_aqsa_qat_db`)
/// into normalized Flutter SQLite v2 (`qat_ledger_v2.db`).
class RoomMigrator {
  RoomMigrator({
    required this.dbService,
    DatabaseFactory? sourceFactory,
  }) : _sourceFactory = sourceFactory ?? dbService.effectiveFactory;

  final DatabaseService dbService;
  final DatabaseFactory _sourceFactory;

  Future<MigrationSummary> migrateIfNeeded({
    LegacyMigrationSources? legacySources,
    String? explicitSourceDbPath,
    String? snapshotDirectory,
  }) async {
    final sources = legacySources ?? await PlatformBridge.instance.getLegacyMigrationSources();
    final targetDb = await dbService.database;

    // Migrate legacy SharedPreferences values idempotently into app_settings if absent
    await _migrateLegacyPrefsIfAbsent(targetDb, sources.legacyPrefs);

    final candidatePath = explicitSourceDbPath ??
        (sources.legacyRoomDbExists ? sources.legacyRoomDbPath : '');
    if (candidatePath.isEmpty || !File(candidatePath).existsSync()) {
      return const MigrationSummary(
        performed: false,
        sourceVersion: 0,
        rowCounts: {},
        auditMessages: [],
        sourceSha256: '',
        backupSnapshotPath: '',
      );
    }

    final sourceFile = File(candidatePath);
    final sourceBytes = await sourceFile.readAsBytes();
    final sourceSha256 = sha256.convert(sourceBytes).toString();

    // Idempotency guard: skip if exact source checksum already migrated cleanly
    final already = await targetDb.query(
      '_migration_history',
      where: 'checksum_sha256 = ? AND status = ?',
      whereArgs: [sourceSha256, 'COMPLETED'],
      limit: 1,
    );
    if (already.isNotEmpty) {
      return MigrationSummary(
        performed: false,
        sourceVersion: (already.first['source_version'] as int?) ?? 18,
        rowCounts: const {},
        auditMessages: const [],
        sourceSha256: sourceSha256,
        backupSnapshotPath: '',
      );
    }

    // Create pre-migration snapshot copy (including WAL/SHM if present) before opening source
    final snapDirPath = snapshotDirectory ??
        p.join(p.dirname(await dbService.resolveDatabasePath()), 'pre_migration_snapshots');
    await Directory(snapDirPath).create(recursive: true);
    final ts = DateTime.now().millisecondsSinceEpoch;
    final snapPath = p.join(snapDirPath, 'room_backup_$ts.db');
    await sourceFile.copy(snapPath);
    for (final ext in ['-wal', '-shm']) {
      final side = File('$candidatePath$ext');
      if (side.existsSync()) {
        await side.copy('$snapPath$ext');
      }
    }

    final sourceDb = await _sourceFactory.openDatabase(
      snapPath,
      options: OpenDatabaseOptions(readOnly: false),
    );

    try {
      // Checkpoint WAL & verify integrity before importing
      try {
        await sourceDb.rawQuery('PRAGMA wal_checkpoint(TRUNCATE)');
      } catch (_) {
        // Legacy Room source may be opened without active WAL; continue to integrity check.
      }
      final integrityRows = await sourceDb.rawQuery('PRAGMA integrity_check');
      final integrityOk = integrityRows.isNotEmpty &&
          integrityRows.first.values.first?.toString().toLowerCase() == 'ok';
      if (!integrityOk) {
        throw StateError('قاعدة البيانات القديمة تالفة وفشل فحص السلامة (PRAGMA integrity_check).');
      }

      final versionRows = await sourceDb.rawQuery('PRAGMA user_version');
      final sourceVersion =
          (versionRows.first.values.first as int?) ?? 18;

      final migrated = await _executeMigrationTransaction(
        sourceDb: sourceDb,
        targetDb: targetDb,
        sourcePath: candidatePath,
        sourceVersion: sourceVersion,
        sourceSha256: sourceSha256,
        snapshotPath: snapPath,
      );
      return migrated;
    } finally {
      await sourceDb.close();
    }
  }

  Future<MigrationSummary> _executeMigrationTransaction({
    required Database sourceDb,
    required Database targetDb,
    required String sourcePath,
    required int sourceVersion,
    required String sourceSha256,
    required String snapshotPath,
  }) async {
    final nowMs = DateTime.now().millisecondsSinceEpoch;
    final counts = <String, int>{};
    final audits = <String>[];

    Future<Set<String>> colsOf(String table) async {
      final info = await sourceDb.rawQuery('PRAGMA table_info($table)');
      return info.map((r) => (r['name'] ?? '').toString()).toSet();
    }

    Future<bool> tableExists(String table) async {
      final res = await sourceDb.rawQuery(
        "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
        [table],
      );
      return res.isNotEmpty;
    }

    await targetDb.transaction<void>((txn) async {
      // Clear target tables in safe dependency order only if importing a fresh migration
      const clearOrder = [
        'money_transfers',
        'transfer_recipients',
        'qat_tax_entries',
        'expenses',
        'supplier_payments',
        'supplier_purchase_items',
        'supplier_purchases',
        'supplier_balances',
        'suppliers',
        'customer_payments',
        'sales_invoice_items',
        'sales',
        'customer_balances',
        'customers',
        'inventory_batches',
        'qat_types',
      ];
      for (final tbl in clearOrder) {
        await txn.delete(tbl);
      }

      Future<void> logAudit(String table, int? legacyId, String severity, String code, String msg) async {
        audits.add('[$severity] $table#${legacyId ?? '-'}: $msg');
        await txn.insert('_migration_audit', {
          'table_name': table,
          'legacy_id': legacyId,
          'severity': severity,
          'code': code,
          'message': msg,
          'created_at_ms': nowMs,
        });
      }

      // 1. qat_types
      final qatTypeIdByName = <String, int>{};
      if (await tableExists('qat_types')) {
        final cols = await colsOf('qat_types');
        final rows = await sourceDb.query('qat_types');
        for (final r in rows) {
          final id = (r['id'] as num).toInt();
          final name = (r['name'] ?? '').toString().trim();
          if (name.isEmpty) continue;
          final cleanName = qatTypeIdByName.containsKey(name) ? '$name #$id' : name;
          await txn.insert('qat_types', {
            'id': id,
            'name': cleanName,
            'default_buy_price_minor': Money.fromLegacyDouble(r['defaultBuyPrice'] as num?),
            'default_sell_price_minor': Money.fromLegacyDouble(r['defaultSellPrice'] as num?),
            'default_currency': 'YER',
            'notes': (r['notes'] ?? '').toString(),
            'is_hidden': cols.contains('isHidden') ? ((r['isHidden'] as num?)?.toInt() ?? 0) : 0,
            'created_at_ms': nowMs,
          });
          qatTypeIdByName[cleanName] = id;
        }
        counts['qat_types'] = rows.length;
      }

      // 2. customers
      final validCustomerIds = <int>{};
      final customerIdByName = <String, int>{};
      final legacyCustomerDebtMinor = <int, int>{};
      if (await tableExists('customers')) {
        final rows = await sourceDb.query('customers');
        for (final r in rows) {
          final id = (r['id'] as num).toInt();
          final name = (r['name'] ?? '').toString().trim();
          await txn.insert('customers', {
            'id': id,
            'name': name.isEmpty ? 'عميل #$id' : name,
            'phone': (r['phone'] ?? '').toString(),
            'address': (r['address'] ?? '').toString(),
            'notes': (r['notes'] ?? '').toString(),
            'created_at_ms': nowMs,
            'is_archived': 0,
          });
          validCustomerIds.add(id);
          if (name.isNotEmpty) {
            customerIdByName.putIfAbsent(name.toLowerCase(), () => id);
          }
          legacyCustomerDebtMinor[id] = Money.fromLegacyDouble(r['totalDebt'] as num?);
        }
        counts['customers'] = rows.length;
      }

      // 3. suppliers
      final validSupplierIds = <int>{};
      final supplierIdByName = <String, int>{};
      final legacySupplierBalMinor = <int, int>{};
      if (await tableExists('suppliers')) {
        final rows = await sourceDb.query('suppliers');
        for (final r in rows) {
          final id = (r['id'] as num).toInt();
          final name = (r['name'] ?? '').toString().trim();
          await txn.insert('suppliers', {
            'id': id,
            'name': name.isEmpty ? 'مورد #$id' : name,
            'phone': (r['phone'] ?? '').toString(),
            'address': (r['address'] ?? '').toString(),
            'notes': (r['notes'] ?? '').toString(),
            'created_at_ms': nowMs,
            'is_archived': 0,
          });
          validSupplierIds.add(id);
          if (name.isNotEmpty) {
            supplierIdByName.putIfAbsent(name.toLowerCase(), () => id);
          }
          legacySupplierBalMinor[id] = Money.fromLegacyDouble(r['totalBalance'] as num?);
        }
        counts['suppliers'] = rows.length;
      }

      // 4. inventory -> inventory_batches
      if (await tableExists('inventory')) {
        final cols = await colsOf('inventory');
        final rows = await sourceDb.query('inventory');
        for (final r in rows) {
          final id = (r['id'] as num).toInt();
          final qatName = (r['qatType'] ?? '').toString().trim();
          final supName = (r['supplierName'] ?? '').toString().trim();
          final qtyMilli = QuantityMath.fromLegacyDouble(r['remainingQty'] as num?).clamp(0, 1 << 60);
          final pd = QatDateUtils.parseLegacyDate(r['purchaseDate']?.toString(), fallbackMs: nowMs);
          final currency = cols.contains('currency')
              ? CurrencyCode.fromRaw(r['currency']?.toString())
              : CurrencyCode.yer;
          await txn.insert('inventory_batches', {
            'id': id,
            'qat_type_id': qatTypeIdByName[qatName],
            'qat_type_name': qatName.isEmpty ? 'صنف غير مسمى' : qatName,
            'supplier_id': supplierIdByName[supName.toLowerCase()],
            'supplier_name': supName,
            'remaining_qty_milli': qtyMilli,
            'initial_qty_milli': qtyMilli,
            'unit': (r['unit'] ?? 'ربطة').toString(),
            'buy_price_minor': Money.fromLegacyDouble(r['buyPrice'] as num?).clamp(0, 1 << 60),
            'sell_price_minor': Money.fromLegacyDouble(r['sellPrice'] as num?).clamp(0, 1 << 60),
            'currency_code': currency.code,
            'purchase_date_ms': pd.epochMs,
            'purchase_date_iso': pd.isoDate,
            'is_archived': 0,
          });
        }
        counts['inventory'] = rows.length;
      }

      // 5. sales & sales_invoice_items
      final validSaleIds = <int>{};
      final custLedgerDelta = <(int, String), int>{};
      if (await tableExists('sales')) {
        final cols = await colsOf('sales');
        final rows = await sourceDb.query('sales');
        for (final r in rows) {
          final id = (r['id'] as num).toInt();
          final rawCustId = (r['customerId'] as num?)?.toInt();
          final custName = (r['customerName'] ?? '').toString().trim();
          int? resolvedCustId =
              (rawCustId != null && validCustomerIds.contains(rawCustId)) ? rawCustId : null;
          if (resolvedCustId == null && custName.isNotEmpty) {
            resolvedCustId = customerIdByName[custName.toLowerCase()];
          }

          final currency = cols.contains('currency')
              ? CurrencyCode.fromRaw(r['currency']?.toString())
              : CurrencyCode.yer;
          final pd = QatDateUtils.parseLegacyDate(r['date']?.toString(), fallbackMs: nowMs);
          final totalMinor = Money.fromLegacyDouble(r['totalAmount'] as num?);
          final discountMinor = cols.contains('discount')
              ? Money.fromLegacyDouble(r['discount'] as num?).clamp(0, 1 << 60)
              : 0;
          final taxBps = cols.contains('taxPercent')
              ? Money.fromLegacyDouble(r['taxPercent'] as num?).clamp(0, 10000)
              : 0;
          final paidMinor = Money.fromLegacyDouble(r['paidAmount'] as num?).clamp(0, 1 << 60);
          final remainingMinor = Money.fromLegacyDouble(r['remainingAmount'] as num?);
          final pType = PaymentType.fromRaw(r['paymentType']?.toString());
          final isArchived = cols.contains('isArchived')
              ? ((r['isArchived'] as num?)?.toInt() ?? 0)
              : 0;

          await txn.insert('sales', {
            'id': id,
            'invoice_number': 'INV-$id',
            'customer_id': resolvedCustId,
            'customer_name': custName.isEmpty ? 'عميل نقدي' : custName,
            'date_ms': pd.epochMs,
            'date_iso': pd.isoDate,
            'currency_code': currency.code,
            'subtotal_minor': totalMinor + discountMinor,
            'discount_minor': discountMinor,
            'tax_bps': taxBps,
            'tax_amount_minor': 0,
            'total_amount_minor': totalMinor,
            'paid_amount_minor': paidMinor,
            'remaining_amount_minor': remainingMinor,
            'payment_type': pType.code,
            'status': (r['status'] ?? pType.arabicLabel).toString(),
            'notes': (r['notes'] ?? '').toString(),
            'is_archived': isArchived,
          });
          validSaleIds.add(id);

          if (resolvedCustId != null && isArchived == 0 && remainingMinor != 0) {
            final key = (resolvedCustId, currency.code);
            custLedgerDelta[key] = (custLedgerDelta[key] ?? 0) + remainingMinor;
          }
        }
        counts['sales'] = rows.length;
      }

      if (await tableExists('sales_invoice_items')) {
        final cols = await colsOf('sales_invoice_items');
        final rows = await sourceDb.query('sales_invoice_items');
        int inserted = 0;
        for (final r in rows) {
          final id = (r['id'] as num).toInt();
          final saleId = (r['saleId'] as num?)?.toInt() ?? -1;
          if (!validSaleIds.contains(saleId)) {
            await logAudit('sales_invoice_items', id, 'WARNING', 'ORPHAN_SKIPPED',
                'تم تجاهل صنف فاتورة يتيم لعدم وجود الفاتورة رقم $saleId.');
            continue;
          }
          final currency = cols.contains('currency')
              ? CurrencyCode.fromRaw(r['currency']?.toString())
              : CurrencyCode.yer;
          final qtyMilli = QuantityMath.fromLegacyDouble(r['quantity'] as num?).clamp(1, 1 << 60);
          await txn.insert('sales_invoice_items', {
            'id': id,
            'sale_id': saleId,
            'inventory_batch_id': null,
            'qat_type_name': (r['qatType'] ?? '').toString(),
            'qty_milli': qtyMilli,
            'unit': (r['unit'] ?? 'ربطة').toString(),
            'unit_price_minor': Money.fromLegacyDouble(r['unitPrice'] as num?).clamp(0, 1 << 60),
            'buy_price_minor': Money.fromLegacyDouble(r['buyPrice'] as num?).clamp(0, 1 << 60),
            'line_total_minor': Money.fromLegacyDouble(r['total'] as num?),
            'currency_code': currency.code,
          });
          inserted++;
        }
        counts['sales_invoice_items'] = inserted;
      }

      // 6. customer_payments
      if (await tableExists('customer_payments')) {
        final cols = await colsOf('customer_payments');
        final rows = await sourceDb.query('customer_payments');
        int inserted = 0;
        for (final r in rows) {
          final id = (r['id'] as num).toInt();
          final rawCustId = (r['customerId'] as num?)?.toInt();
          final custName = (r['customerName'] ?? '').toString().trim();
          int? resolvedCustId =
              (rawCustId != null && validCustomerIds.contains(rawCustId)) ? rawCustId : null;
          resolvedCustId ??= customerIdByName[custName.toLowerCase()];
          if (resolvedCustId == null) {
            await logAudit('customer_payments', id, 'WARNING', 'ORPHAN_SKIPPED',
                'تم تجاهل سند قبض عميل بدون عميل مرتبط ($custName).');
            continue;
          }
          final currency = cols.contains('currency')
              ? CurrencyCode.fromRaw(r['currency']?.toString())
              : CurrencyCode.yer;
          final amountMinor = Money.fromLegacyDouble(r['amount'] as num?).clamp(1, 1 << 60);
          final pd = QatDateUtils.parseLegacyDate(r['date']?.toString(), fallbackMs: nowMs);
          final isArchived = cols.contains('isArchived')
              ? ((r['isArchived'] as num?)?.toInt() ?? 0)
              : 0;
          await txn.insert('customer_payments', {
            'id': id,
            'customer_id': resolvedCustId,
            'customer_name': custName.isEmpty ? 'عميل #$resolvedCustId' : custName,
            'amount_minor': amountMinor,
            'currency_code': currency.code,
            'date_ms': pd.epochMs,
            'date_iso': pd.isoDate,
            'payment_method': cols.contains('paymentMethod')
                ? (r['paymentMethod'] ?? 'نقدي').toString()
                : 'نقدي',
            'notes': (r['notes'] ?? '').toString(),
            'is_archived': isArchived,
          });
          if (isArchived == 0) {
            final key = (resolvedCustId, currency.code);
            custLedgerDelta[key] = (custLedgerDelta[key] ?? 0) - amountMinor;
          }
          inserted++;
        }
        counts['customer_payments'] = inserted;
      }

      // Reconcile per-currency customer_balances without blending YER & SAR
      for (final cid in validCustomerIds) {
        final yerBal = custLedgerDelta[(cid, 'YER')] ?? 0;
        final sarBal = custLedgerDelta[(cid, 'SAR')] ?? 0;
        final legacyTotal = legacyCustomerDebtMinor[cid] ?? 0;
        final hasLedgerActivity = custLedgerDelta.containsKey((cid, 'YER')) ||
            custLedgerDelta.containsKey((cid, 'SAR'));

        if (!hasLedgerActivity && legacyTotal != 0) {
          // Preserve opening debt explicitly in YER and log audit note
          await txn.insert('customer_balances', {
            'customer_id': cid,
            'currency_code': 'YER',
            'debt_minor': legacyTotal,
            'updated_at_ms': nowMs,
          });
          await logAudit('customers', cid, 'INFO', 'OPENING_DEBT_YER',
              'تم حفظ الرصيد الافتتاحي القديم (${Money.formatMinor(legacyTotal)} ر.ي) دون فواتير مبيعات سابقة.');
        } else {
          if (yerBal != 0) {
            await txn.insert('customer_balances', {
              'customer_id': cid,
              'currency_code': 'YER',
              'debt_minor': yerBal,
              'updated_at_ms': nowMs,
            });
          }
          if (sarBal != 0) {
            await txn.insert('customer_balances', {
              'customer_id': cid,
              'currency_code': 'SAR',
              'debt_minor': sarBal,
              'updated_at_ms': nowMs,
            });
          }
          if (legacyTotal != (yerBal + sarBal)) {
            await logAudit('customers', cid, 'INFO', 'BALANCE_RECONCILED_PER_CURRENCY',
                'تمت إعادة تسوية رصيد العميل من الفواتير والسندات بشكل منفصل لكل عملة (YER=${Money.formatMinor(yerBal)}, SAR=${Money.formatMinor(sarBal)}) بدلاً من الرصيد المختلط القديم (${Money.formatMinor(legacyTotal)}).');
          }
        }
      }

      // 7. supplier_purchases & supplier_purchase_items
      final validPurchaseIds = <int>{};
      final supLedgerDelta = <(int, String), int>{};
      if (await tableExists('supplier_purchases')) {
        final cols = await colsOf('supplier_purchases');
        final rows = await sourceDb.query('supplier_purchases');
        int inserted = 0;
        for (final r in rows) {
          final id = (r['id'] as num).toInt();
          final rawSupId = (r['supplierId'] as num?)?.toInt();
          final supName = (r['supplierName'] ?? '').toString().trim();
          int? resolvedSupId =
              (rawSupId != null && validSupplierIds.contains(rawSupId)) ? rawSupId : null;
          resolvedSupId ??= supplierIdByName[supName.toLowerCase()];
          if (resolvedSupId == null) {
            await logAudit('supplier_purchases', id, 'WARNING', 'ORPHAN_SKIPPED',
                'تم تجاهل فاتورة شراء غير مرتبطة بمورد ($supName).');
            continue;
          }
          final currency = cols.contains('currency')
              ? CurrencyCode.fromRaw(r['currency']?.toString())
              : CurrencyCode.yer;
          final pd = QatDateUtils.parseLegacyDate(r['date']?.toString(), fallbackMs: nowMs);
          final totalMinor = Money.fromLegacyDouble(r['totalAmount'] as num?);
          final discountMinor = cols.contains('discount')
              ? Money.fromLegacyDouble(r['discount'] as num?).clamp(0, 1 << 60)
              : 0;
          final taxBps = cols.contains('taxPercent')
              ? Money.fromLegacyDouble(r['taxPercent'] as num?).clamp(0, 10000)
              : 0;
          final paidMinor = Money.fromLegacyDouble(r['paidAmount'] as num?).clamp(0, 1 << 60);
          final remainingMinor = Money.fromLegacyDouble(r['remainingAmount'] as num?);
          final pType = PaymentType.fromRaw(r['paymentType']?.toString());
          final isArchived = cols.contains('isArchived')
              ? ((r['isArchived'] as num?)?.toInt() ?? 0)
              : 0;

          await txn.insert('supplier_purchases', {
            'id': id,
            'purchase_number': 'PUR-$id',
            'supplier_id': resolvedSupId,
            'supplier_name': supName.isEmpty ? 'مورد #$resolvedSupId' : supName,
            'date_ms': pd.epochMs,
            'date_iso': pd.isoDate,
            'currency_code': currency.code,
            'subtotal_minor': totalMinor + discountMinor,
            'discount_minor': discountMinor,
            'tax_bps': taxBps,
            'tax_amount_minor': 0,
            'total_amount_minor': totalMinor,
            'paid_amount_minor': paidMinor,
            'remaining_amount_minor': remainingMinor,
            'payment_type': pType.code,
            'notes': (r['notes'] ?? '').toString(),
            'is_archived': isArchived,
          });
          validPurchaseIds.add(id);
          if (isArchived == 0 && remainingMinor != 0) {
            final key = (resolvedSupId, currency.code);
            supLedgerDelta[key] = (supLedgerDelta[key] ?? 0) + remainingMinor;
          }
          inserted++;
        }
        counts['supplier_purchases'] = inserted;
      }

      if (await tableExists('supplier_purchase_items')) {
        final cols = await colsOf('supplier_purchase_items');
        final rows = await sourceDb.query('supplier_purchase_items');
        int inserted = 0;
        for (final r in rows) {
          final id = (r['id'] as num).toInt();
          final purchaseId = (r['purchaseId'] as num?)?.toInt() ?? -1;
          if (!validPurchaseIds.contains(purchaseId)) {
            await logAudit('supplier_purchase_items', id, 'WARNING', 'ORPHAN_SKIPPED',
                'تم تجاهل صنف مشتريات يتيم للفاتورة رقم $purchaseId.');
            continue;
          }
          final currency = cols.contains('currency')
              ? CurrencyCode.fromRaw(r['currency']?.toString())
              : CurrencyCode.yer;
          final qtyMilli = QuantityMath.fromLegacyDouble(r['quantity'] as num?).clamp(1, 1 << 60);
          await txn.insert('supplier_purchase_items', {
            'id': id,
            'purchase_id': purchaseId,
            'qat_type_name': (r['qatType'] ?? '').toString(),
            'qty_milli': qtyMilli,
            'unit': (r['unit'] ?? 'ربطة').toString(),
            'buy_price_minor': Money.fromLegacyDouble(r['buyPrice'] as num?).clamp(0, 1 << 60),
            'line_total_minor': Money.fromLegacyDouble(r['total'] as num?),
            'currency_code': currency.code,
          });
          inserted++;
        }
        counts['supplier_purchase_items'] = inserted;
      }

      // 8. supplier_payments
      if (await tableExists('supplier_payments')) {
        final cols = await colsOf('supplier_payments');
        final rows = await sourceDb.query('supplier_payments');
        int inserted = 0;
        for (final r in rows) {
          final id = (r['id'] as num).toInt();
          final rawSupId = (r['supplierId'] as num?)?.toInt();
          final supName = (r['supplierName'] ?? '').toString().trim();
          int? resolvedSupId =
              (rawSupId != null && validSupplierIds.contains(rawSupId)) ? rawSupId : null;
          resolvedSupId ??= supplierIdByName[supName.toLowerCase()];
          if (resolvedSupId == null) {
            await logAudit('supplier_payments', id, 'WARNING', 'ORPHAN_SKIPPED',
                'تم تجاهل سند صرف مورد غير مرتبط ($supName).');
            continue;
          }
          final currency = cols.contains('currency')
              ? CurrencyCode.fromRaw(r['currency']?.toString())
              : CurrencyCode.yer;
          final amountMinor = Money.fromLegacyDouble(r['amount'] as num?).clamp(1, 1 << 60);
          final pd = QatDateUtils.parseLegacyDate(r['date']?.toString(), fallbackMs: nowMs);
          final isArchived = cols.contains('isArchived')
              ? ((r['isArchived'] as num?)?.toInt() ?? 0)
              : 0;
          await txn.insert('supplier_payments', {
            'id': id,
            'supplier_id': resolvedSupId,
            'supplier_name': supName.isEmpty ? 'مورد #$resolvedSupId' : supName,
            'amount_minor': amountMinor,
            'currency_code': currency.code,
            'date_ms': pd.epochMs,
            'date_iso': pd.isoDate,
            'payment_method': cols.contains('paymentMethod')
                ? (r['paymentMethod'] ?? 'نقدي').toString()
                : 'نقدي',
            'notes': (r['notes'] ?? '').toString(),
            'is_archived': isArchived,
          });
          if (isArchived == 0) {
            final key = (resolvedSupId, currency.code);
            supLedgerDelta[key] = (supLedgerDelta[key] ?? 0) - amountMinor;
          }
          inserted++;
        }
        counts['supplier_payments'] = inserted;
      }

      // Reconcile supplier_balances
      for (final sid in validSupplierIds) {
        final yerBal = supLedgerDelta[(sid, 'YER')] ?? 0;
        final sarBal = supLedgerDelta[(sid, 'SAR')] ?? 0;
        final legacyTotal = legacySupplierBalMinor[sid] ?? 0;
        final hasLedgerActivity = supLedgerDelta.containsKey((sid, 'YER')) ||
            supLedgerDelta.containsKey((sid, 'SAR'));

        if (!hasLedgerActivity && legacyTotal != 0) {
          await txn.insert('supplier_balances', {
            'supplier_id': sid,
            'currency_code': 'YER',
            'balance_minor': legacyTotal,
            'updated_at_ms': nowMs,
          });
          await logAudit('suppliers', sid, 'INFO', 'OPENING_PAYABLE_YER',
              'تم حفظ الرصيد الافتتاحي للمورد (${Money.formatMinor(legacyTotal)} ر.ي).');
        } else {
          if (yerBal != 0) {
            await txn.insert('supplier_balances', {
              'supplier_id': sid,
              'currency_code': 'YER',
              'balance_minor': yerBal,
              'updated_at_ms': nowMs,
            });
          }
          if (sarBal != 0) {
            await txn.insert('supplier_balances', {
              'supplier_id': sid,
              'currency_code': 'SAR',
              'balance_minor': sarBal,
              'updated_at_ms': nowMs,
            });
          }
          if (legacyTotal != (yerBal + sarBal)) {
            await logAudit('suppliers', sid, 'INFO', 'SUPPLIER_RECONCILED_PER_CURRENCY',
                'تمت تسوية رصيد المورد لكل عملة على حدة (YER=${Money.formatMinor(yerBal)}, SAR=${Money.formatMinor(sarBal)}).');
          }
        }
      }

      // 9. expenses
      if (await tableExists('expenses')) {
        final cols = await colsOf('expenses');
        final rows = await sourceDb.query('expenses');
        for (final r in rows) {
          final id = (r['id'] as num).toInt();
          final currency = cols.contains('currency')
              ? CurrencyCode.fromRaw(r['currency']?.toString())
              : CurrencyCode.yer;
          final pd = QatDateUtils.parseLegacyDate(r['date']?.toString(), fallbackMs: nowMs);
          final isArchived = cols.contains('isArchived')
              ? ((r['isArchived'] as num?)?.toInt() ?? 0)
              : 0;
          await txn.insert('expenses', {
            'id': id,
            'description': (r['description'] ?? '').toString(),
            'category': (r['category'] ?? 'عام').toString(),
            'account_id': null,
            'amount_minor': Money.fromLegacyDouble(r['amount'] as num?).clamp(0, 1 << 60),
            'currency_code': currency.code,
            'date_ms': pd.epochMs,
            'date_iso': pd.isoDate,
            'notes': (r['notes'] ?? '').toString(),
            'is_archived': isArchived,
          });
        }
        counts['expenses'] = rows.length;
      }

      // 10. qat_tax_entries
      if (await tableExists('qat_tax_entries')) {
        final cols = await colsOf('qat_tax_entries');
        final rows = await sourceDb.query('qat_tax_entries');
        for (final r in rows) {
          final id = (r['id'] as num).toInt();
          final pd = QatDateUtils.parseLegacyDate(r['date']?.toString(), fallbackMs: nowMs);
          final isArchived = cols.contains('isArchived')
              ? ((r['isArchived'] as num?)?.toInt() ?? 0)
              : 0;
          final currency = cols.contains('currency')
              ? CurrencyCode.fromRaw(r['currency']?.toString())
              : CurrencyCode.yer;
          await txn.insert('qat_tax_entries', {
            'id': id,
            'date_ms': pd.epochMs,
            'date_iso': pd.isoDate,
            'total_qty_milli': QuantityMath.fromLegacyDouble(r['totalQty'] as num?),
            'tax_rate_minor': Money.fromLegacyDouble(r['taxRate'] as num?),
            'tax_amount_minor': Money.fromLegacyDouble(r['totalTaxAmount'] as num?),
            'currency_code': currency.code,
            'source': cols.contains('source') ? (r['source'] ?? 'يدوي').toString() : 'يدوي',
            'notes': (r['notes'] ?? '').toString(),
            'is_archived': isArchived,
          });
        }
        counts['qat_tax_entries'] = rows.length;
      }

      // 11. transfer_recipients
      final validRecipientIds = <int>{};
      if (await tableExists('transfer_recipients')) {
        final rows = await sourceDb.query('transfer_recipients');
        for (final r in rows) {
          final id = (r['id'] as num).toInt();
          await txn.insert('transfer_recipients', {
            'id': id,
            'name': (r['name'] ?? '').toString(),
            'phone': (r['phone'] ?? '').toString(),
            'company': (r['company'] ?? '').toString(),
            'notes': (r['notes'] ?? '').toString(),
            'created_at_ms': (r['createdAt'] as num?)?.toInt() ?? nowMs,
          });
          validRecipientIds.add(id);
        }
        counts['transfer_recipients'] = rows.length;
      }

      // 12. money_transfers (never invent fake recipients when recipientId <= 0!)
      if (await tableExists('money_transfers')) {
        final cols = await colsOf('money_transfers');
        final rows = await sourceDb.query('money_transfers');
        int unlinkedCount = 0;
        for (final r in rows) {
          final id = (r['id'] as num).toInt();
          final rawRecId = cols.contains('recipientId') ? (r['recipientId'] as num?)?.toInt() : null;
          final int? resolvedRecId =
              (rawRecId != null && rawRecId > 0 && validRecipientIds.contains(rawRecId))
                  ? rawRecId
                  : null;
          if (resolvedRecId == null) {
            unlinkedCount++;
          }
          final currency = cols.contains('currency')
              ? CurrencyCode.fromRaw(r['currency']?.toString())
              : CurrencyCode.yer;
          final pd = QatDateUtils.parseLegacyDate(r['date']?.toString(), fallbackMs: nowMs);
          final status = TransferStatus.fromRaw(r['status']?.toString());
          final isArchived = cols.contains('isArchived')
              ? ((r['isArchived'] as num?)?.toInt() ?? 0)
              : 0;
          await txn.insert('money_transfers', {
            'id': id,
            'sender_name': (r['senderName'] ?? '').toString(),
            'receiver_name': (r['receiverName'] ?? '').toString(),
            'recipient_id': resolvedRecId,
            'amount_minor': Money.fromLegacyDouble(r['amount'] as num?).clamp(0, 1 << 60),
            'fee_minor': cols.contains('fee')
                ? Money.fromLegacyDouble(r['fee'] as num?).clamp(0, 1 << 60)
                : 0,
            'currency_code': currency.code,
            'transfer_company': (r['transferCompany'] ?? '').toString(),
            'transfer_number': (r['transferNumber'] ?? '').toString(),
            'date_ms': pd.epochMs,
            'date_iso': pd.isoDate,
            'status_code': status.code,
            'status_label': status.arabicLabel,
            'notes': (r['notes'] ?? '').toString(),
            'is_archived': isArchived,
          });
        }
        if (unlinkedCount > 0) {
          await logAudit('money_transfers', null, 'INFO', 'UNLINKED_TRANSFERS_PRESERVED',
              'يوجد $unlinkedCount حوالة قديمة غير مرتبطة ببطاقة مستلم (تم حفظها كحوالات غير مرتبطة دون اختلاق أسماء).');
        }
        counts['money_transfers'] = rows.length;
      }

      // Verify zero foreign-key violations inside the transaction
      final fkViolations = await txn.rawQuery('PRAGMA foreign_key_check');
      if (fkViolations.isNotEmpty) {
        throw StateError('فشل التحقق من القيود المرجعية بعد الترحيل: ${fkViolations.length} مخالفة.');
      }

      await txn.insert('_migration_history', {
        'source_path': sourcePath,
        'source_version': sourceVersion,
        'migrated_at_ms': nowMs,
        'status': 'COMPLETED',
        'rows_migrated_json': jsonEncode(counts),
        'checksum_sha256': sourceSha256,
      });
    });

    return MigrationSummary(
      performed: true,
      sourceVersion: sourceVersion,
      rowCounts: counts,
      auditMessages: audits,
      sourceSha256: sourceSha256,
      backupSnapshotPath: snapshotPath,
    );
  }

  Future<void> _migrateLegacyPrefsIfAbsent(
    Database targetDb,
    Map<String, Map<String, Object?>> legacyPrefs,
  ) async {
    if (legacyPrefs.isEmpty) return;
    final agency = legacyPrefs['qat_agency_prefs'] ?? {};
    final theme = legacyPrefs['app_theme_prefs'] ?? {};
    final nowMs = DateTime.now().millisecondsSinceEpoch;

    Future<void> putIfAbsent(String key, String value) async {
      if (value.isEmpty) return;
      await targetDb.insert(
        'app_settings',
        {'key': key, 'value': value, 'updated_at_ms': nowMs},
        conflictAlgorithm: ConflictAlgorithm.ignore,
      );
    }

    await putIfAbsent('agency_name', (agency['agency_name'] ?? '').toString());
    await putIfAbsent('agency_phone', (agency['agency_phone'] ?? '').toString());
    await putIfAbsent('agency_address', (agency['agency_address'] ?? '').toString());
    await putIfAbsent('footer_note', (agency['footer_note'] ?? '').toString());

    final rawCurrencyMode = (agency['currency_mode'] ?? '').toString();
    if (rawCurrencyMode.isNotEmpty) {
      await putIfAbsent('currency_mode', CurrencyMode.fromKey(rawCurrencyMode).storageKey);
    }
    final isDark = theme['is_dark_mode'] == true;
    if (theme.containsKey('is_dark_mode')) {
      await putIfAbsent('theme_choice', isDark ? 'dark_black' : 'light_enterprise');
    }
    final onboardingDone = agency['onboarding_done'] == true || agency['is_setup_done'] == true;
    if (onboardingDone) {
      await putIfAbsent('onboarding_complete', '1');
    }
  }
}
