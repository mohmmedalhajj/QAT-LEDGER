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
/// into normalized Flutter SQLite v3 (`qat_ledger_v2.db`).
/// Supports BOTH authentic Android Room v18 entity schemas (`sales_invoices`, `inventory_items`,
/// `qat_import_weights`, `agency_profile`, `qat_tax_entries`, split YER/SAR columns)
/// AND older synthetic schemas (`sales`, `inventory`, `supplier_payments`).
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
    final sources = legacySources ??
        (explicitSourceDbPath != null
            ? LegacyMigrationSources.empty
            : await PlatformBridge.instance.getLegacyMigrationSources());
    final targetDb = await dbService.database;

    // Migrate legacy SharedPreferences values idempotently into app_settings if absent
    await _migrateLegacyPrefsIfAbsent(targetDb, sources);

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
      try {
        await sourceDb.rawQuery('PRAGMA wal_checkpoint(TRUNCATE)');
      } catch (_) {
        // Legacy Room source may not have WAL enabled.
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
      const clearOrder = [
        'qat_import_weights',
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
            'notes': cols.contains('notes') ? (r['notes'] ?? '').toString() : '',
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
      final customerNameById = <int, String>{};
      final legacyCustomerDebtMinor = <int, int>{};
      if (await tableExists('customers')) {
        final cols = await colsOf('customers');
        final rows = await sourceDb.query('customers');
        for (final r in rows) {
          final id = (r['id'] as num).toInt();
          final name = (r['name'] ?? '').toString().trim();
          final resolvedName = name.isEmpty ? 'عميل #$id' : name;
          await txn.insert('customers', {
            'id': id,
            'name': resolvedName,
            'phone': cols.contains('phone') ? (r['phone'] ?? '').toString() : '',
            'address': cols.contains('address') ? (r['address'] ?? '').toString() : '',
            'notes': cols.contains('notes') ? (r['notes'] ?? '').toString() : '',
            'created_at_ms': nowMs,
            'is_archived': 0,
          });
          validCustomerIds.add(id);
          customerNameById[id] = resolvedName;
          if (name.isNotEmpty) {
            customerIdByName.putIfAbsent(name.toLowerCase(), () => id);
          }
          legacyCustomerDebtMinor[id] =
              cols.contains('totalDebt') ? Money.fromLegacyDouble(r['totalDebt'] as num?) : 0;
        }
        counts['customers'] = rows.length;
      }

      // 3. suppliers (supports both `region` from Room v18 and `address`)
      final validSupplierIds = <int>{};
      final supplierIdByName = <String, int>{};
      final supplierNameById = <int, String>{};
      final legacySupplierBalMinor = <int, int>{};
      if (await tableExists('suppliers')) {
        final cols = await colsOf('suppliers');
        final rows = await sourceDb.query('suppliers');
        for (final r in rows) {
          final id = (r['id'] as num).toInt();
          final name = (r['name'] ?? '').toString().trim();
          final resolvedName = name.isEmpty ? 'مورد #$id' : name;
          final regionVal = cols.contains('region')
              ? (r['region'] ?? '').toString()
              : '';
          final addrVal = cols.contains('address')
              ? (r['address'] ?? '').toString()
              : '';
          await txn.insert('suppliers', {
            'id': id,
            'name': resolvedName,
            'phone': cols.contains('phone') ? (r['phone'] ?? '').toString() : '',
            'address': addrVal.isNotEmpty ? addrVal : regionVal,
            'region': regionVal,
            'notes': cols.contains('notes') ? (r['notes'] ?? '').toString() : '',
            'created_at_ms': nowMs,
            'is_archived': 0,
          });
          validSupplierIds.add(id);
          supplierNameById[id] = resolvedName;
          if (name.isNotEmpty) {
            supplierIdByName.putIfAbsent(name.toLowerCase(), () => id);
          }
          legacySupplierBalMinor[id] =
              cols.contains('totalBalance') ? Money.fromLegacyDouble(r['totalBalance'] as num?) : 0;
        }
        counts['suppliers'] = rows.length;
      }

      // 4. inventory_items (Room v18) OR inventory (synthetic) -> inventory_batches
      final validBatchIds = <int>{};
      final invTableName = (await tableExists('inventory_items'))
          ? 'inventory_items'
          : ((await tableExists('inventory')) ? 'inventory' : '');
      if (invTableName.isNotEmpty) {
        final cols = await colsOf(invTableName);
        final rows = await sourceDb.query(invTableName);
        for (final r in rows) {
          final id = (r['id'] as num).toInt();
          final rawName = cols.contains('name') ? (r['name'] ?? '').toString().trim() : '';
          final rawQatType = cols.contains('qatType') ? (r['qatType'] ?? '').toString().trim() : '';
          final qatName = rawName.isNotEmpty ? rawName : rawQatType;
          final rawSupId = cols.contains('supplierId') ? (r['supplierId'] as num?)?.toInt() : null;
          final supName = cols.contains('supplierName') ? (r['supplierName'] ?? '').toString().trim() : '';
          final int? resolvedSupId = (rawSupId != null && rawSupId > 0 && validSupplierIds.contains(rawSupId))
              ? rawSupId
              : supplierIdByName[supName.toLowerCase()];
          final rawQty = cols.contains('quantity') ? (r['quantity'] as num?) : (r['remainingQty'] as num?);
          final qtyMilli = QuantityMath.fromLegacyDouble(rawQty).clamp(0, 1 << 60).toInt();
          final dateStr = cols.contains('dateAdded')
              ? r['dateAdded']?.toString()
              : (cols.contains('purchaseDate') ? r['purchaseDate']?.toString() : null);
          final pd = QatDateUtils.parseLegacyDate(dateStr, fallbackMs: nowMs);
          final curRaw = cols.contains('buyPriceCurrency')
              ? r['buyPriceCurrency']?.toString()
              : (cols.contains('currency') ? r['currency']?.toString() : null);
          final currency = CurrencyCode.fromRaw(curRaw);
          final rawRecId = cols.contains('receiverId') ? (r['receiverId'] as num?)?.toInt() : null;
          final lowThresh = cols.contains('lowStockThreshold')
              ? QuantityMath.fromLegacyDouble(r['lowStockThreshold'] as num?).clamp(0, 1 << 60).toInt()
              : 0;
          await txn.insert('inventory_batches', {
            'id': id,
            'qat_type_id': qatTypeIdByName[qatName] ?? qatTypeIdByName[rawQatType],
            'qat_type_name': qatName.isEmpty ? 'صنف غير مسمى' : qatName,
            'supplier_id': resolvedSupId,
            'supplier_name': supName.isNotEmpty
                ? supName
                : (resolvedSupId != null ? (supplierNameById[resolvedSupId] ?? '') : ''),
            'remaining_qty_milli': qtyMilli,
            'initial_qty_milli': qtyMilli,
            'unit': cols.contains('unit') ? (r['unit'] ?? 'ربطة').toString() : 'ربطة',
            'buy_price_minor': Money.fromLegacyDouble(r['buyPrice'] as num?).clamp(0, 1 << 60).toInt(),
            'sell_price_minor': Money.fromLegacyDouble(r['sellPrice'] as num?).clamp(0, 1 << 60).toInt(),
            'currency_code': currency.code,
            'purchase_date_ms': pd.epochMs,
            'purchase_date_iso': pd.isoDate,
            'is_archived': 0,
            'item_number': cols.contains('itemNumber') ? (r['itemNumber'] ?? '').toString() : '',
            'low_stock_threshold_milli': lowThresh,
            'receiver_id': (rawRecId != null && rawRecId > 0) ? rawRecId : null,
            'receiver_name': cols.contains('receiverName') ? (r['receiverName'] ?? '').toString() : '',
            'notes': cols.contains('notes') ? (r['notes'] ?? '').toString() : '',
          });
          validBatchIds.add(id);
        }
        counts['inventory'] = rows.length;
      }

      // 5. sales_invoices (Room v18) OR sales (synthetic) -> sales + sales_invoice_items
      // Supports split YER + SAR invoices cleanly as strictly separated currency rows!
      final validSaleIds = <int>{};
      final splitSaleMap = <(int, String), int>{};
      final custLedgerDelta = <(int, String), int>{};
      final salesTableName = (await tableExists('sales_invoices'))
          ? 'sales_invoices'
          : ((await tableExists('sales')) ? 'sales' : '');
      int nextSyntheticSaleId = 100000;

      if (salesTableName.isNotEmpty) {
        final cols = await colsOf(salesTableName);
        final rows = await sourceDb.query(salesTableName);
        for (final r in rows) {
          final id = (r['id'] as num).toInt();
          if (id >= nextSyntheticSaleId) nextSyntheticSaleId = id + 1000;
        }
        int migratedSalesCount = 0;

        for (final r in rows) {
          final id = (r['id'] as num).toInt();
          final rawCustId = cols.contains('customerId') ? (r['customerId'] as num?)?.toInt() : null;
          final custName = (r['customerName'] ?? '').toString().trim();
          int? resolvedCustId =
              (rawCustId != null && validCustomerIds.contains(rawCustId)) ? rawCustId : null;
          if (resolvedCustId == null && custName.isNotEmpty) {
            resolvedCustId = customerIdByName[custName.toLowerCase()];
          }

          final pd = QatDateUtils.parseLegacyDate(r['date']?.toString(), fallbackMs: nowMs);
          final discountMinor = cols.contains('discount')
              ? Money.fromLegacyDouble(r['discount'] as num?).clamp(0, 1 << 60).toInt()
              : 0;
          final taxBps = cols.contains('taxPercent')
              ? Money.fromLegacyDouble(r['taxPercent'] as num?).clamp(0, 10000).toInt()
              : 0;
          final taxAppliedMinor = cols.contains('taxApplied')
              ? Money.fromLegacyDouble(r['taxApplied'] as num?).clamp(0, 1 << 60).toInt()
              : 0;
          final isArchived = cols.contains('isArchived')
              ? ((r['isArchived'] as num?)?.toInt() ?? 0)
              : 0;
          final notes = cols.contains('notes') ? (r['notes'] ?? '').toString() : '';

          final totalYer = cols.contains('totalAmountYer')
              ? Money.fromLegacyDouble(r['totalAmountYer'] as num?)
              : 0;
          final totalSar = cols.contains('totalAmountSar')
              ? Money.fromLegacyDouble(r['totalAmountSar'] as num?)
              : 0;

          if (totalYer > 0 && totalSar > 0) {
            // Split dual-currency Room invoice into two strict single-currency ledger entries
            final paidYer = Money.fromLegacyDouble(r['paidAmountYer'] as num?).clamp(0, 1 << 60).toInt();
            final debtYer = Money.fromLegacyDouble(r['debtAmountYer'] as num?);
            final pmYer = cols.contains('paymentMethodYer')
                ? (r['paymentMethodYer'] ?? 'نقداً').toString()
                : 'نقداً';
            final ptYer = PaymentType.fromRaw(pmYer);

            await txn.insert('sales', {
              'id': id,
              'invoice_number': 'INV-$id-YER',
              'customer_id': resolvedCustId,
              'customer_name': custName.isEmpty ? 'عميل نقدي' : custName,
              'date_ms': pd.epochMs,
              'date_iso': pd.isoDate,
              'currency_code': 'YER',
              'subtotal_minor': totalYer + discountMinor,
              'discount_minor': discountMinor,
              'tax_bps': taxBps,
              'tax_amount_minor': taxAppliedMinor,
              'total_amount_minor': totalYer,
              'paid_amount_minor': paidYer,
              'remaining_amount_minor': debtYer,
              'payment_type': ptYer.code,
              'status': pmYer.isNotEmpty ? pmYer : ptYer.arabicLabel,
              'notes': notes,
              'is_archived': isArchived,
              'payment_method': pmYer,
            });
            validSaleIds.add(id);
            splitSaleMap[(id, 'YER')] = id;
            migratedSalesCount++;
            if (resolvedCustId != null && isArchived == 0 && debtYer != 0) {
              custLedgerDelta[(resolvedCustId, 'YER')] =
                  (custLedgerDelta[(resolvedCustId, 'YER')] ?? 0) + debtYer;
            }

            final sarId = nextSyntheticSaleId++;
            final paidSar = Money.fromLegacyDouble(r['paidAmountSar'] as num?).clamp(0, 1 << 60).toInt();
            final debtSar = Money.fromLegacyDouble(r['debtAmountSar'] as num?);
            final pmSar = cols.contains('paymentMethodSar')
                ? (r['paymentMethodSar'] ?? 'نقداً').toString()
                : 'نقداً';
            final ptSar = PaymentType.fromRaw(pmSar);

            await txn.insert('sales', {
              'id': sarId,
              'invoice_number': 'INV-$id-SAR',
              'customer_id': resolvedCustId,
              'customer_name': custName.isEmpty ? 'عميل نقدي' : custName,
              'date_ms': pd.epochMs,
              'date_iso': pd.isoDate,
              'currency_code': 'SAR',
              'subtotal_minor': totalSar,
              'discount_minor': 0,
              'tax_bps': 0,
              'tax_amount_minor': 0,
              'total_amount_minor': totalSar,
              'paid_amount_minor': paidSar,
              'remaining_amount_minor': debtSar,
              'payment_type': ptSar.code,
              'status': pmSar.isNotEmpty ? pmSar : ptSar.arabicLabel,
              'notes': notes,
              'is_archived': isArchived,
              'payment_method': pmSar,
            });
            validSaleIds.add(sarId);
            splitSaleMap[(id, 'SAR')] = sarId;
            migratedSalesCount++;
            if (resolvedCustId != null && isArchived == 0 && debtSar != 0) {
              custLedgerDelta[(resolvedCustId, 'SAR')] =
                  (custLedgerDelta[(resolvedCustId, 'SAR')] ?? 0) + debtSar;
            }

            await logAudit('sales_invoices', id, 'INFO', 'SPLIT_DUAL_CURRENCY_SALE',
                'تم فصل الفاتورة المزدوجة #$id إلى سجلين مستقلين (YER=$id, SAR=$sarId) لحفظ فصل العملات.');
          } else {
            final currency = (totalSar > 0 && totalYer == 0)
                ? CurrencyCode.sar
                : (cols.contains('currency')
                    ? CurrencyCode.fromRaw(r['currency']?.toString())
                    : CurrencyCode.yer);
            final totalMinor = currency == CurrencyCode.sar && totalSar > 0
                ? totalSar
                : (totalYer > 0
                    ? totalYer
                    : Money.fromLegacyDouble(r['totalAmount'] as num?));
            final paidMinor = currency == CurrencyCode.sar && totalSar > 0 && cols.contains('paidAmountSar')
                ? Money.fromLegacyDouble(r['paidAmountSar'] as num?).clamp(0, 1 << 60).toInt()
                : (totalYer > 0 && cols.contains('paidAmountYer')
                    ? Money.fromLegacyDouble(r['paidAmountYer'] as num?).clamp(0, 1 << 60).toInt()
                    : Money.fromLegacyDouble(r['paidAmount'] as num?).clamp(0, 1 << 60).toInt());
            final remainingMinor = currency == CurrencyCode.sar && totalSar > 0 && cols.contains('debtAmountSar')
                ? Money.fromLegacyDouble(r['debtAmountSar'] as num?)
                : (totalYer > 0 && cols.contains('debtAmountYer')
                    ? Money.fromLegacyDouble(r['debtAmountYer'] as num?)
                    : (cols.contains('debtAmount')
                        ? Money.fromLegacyDouble(r['debtAmount'] as num?)
                        : Money.fromLegacyDouble(r['remainingAmount'] as num?)));
            final rawPm = currency == CurrencyCode.sar && totalSar > 0 && cols.contains('paymentMethodSar')
                ? (r['paymentMethodSar'] ?? '').toString()
                : (totalYer > 0 && cols.contains('paymentMethodYer')
                    ? (r['paymentMethodYer'] ?? '').toString()
                    : (cols.contains('paymentMethod')
                        ? (r['paymentMethod'] ?? '').toString()
                        : (cols.contains('paymentType') ? (r['paymentType'] ?? '').toString() : '')));
            final pType = PaymentType.fromRaw(rawPm);
            final statusStr = cols.contains('status')
                ? (r['status'] ?? (rawPm.isNotEmpty ? rawPm : pType.arabicLabel)).toString()
                : (rawPm.isNotEmpty ? rawPm : pType.arabicLabel);

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
              'tax_amount_minor': taxAppliedMinor,
              'total_amount_minor': totalMinor,
              'paid_amount_minor': paidMinor,
              'remaining_amount_minor': remainingMinor,
              'payment_type': pType.code,
              'status': statusStr,
              'notes': notes,
              'is_archived': isArchived,
              'payment_method': rawPm.isNotEmpty ? rawPm : pType.arabicLabel,
            });
            validSaleIds.add(id);
            migratedSalesCount++;

            if (resolvedCustId != null && isArchived == 0 && remainingMinor != 0) {
              final key = (resolvedCustId, currency.code);
              custLedgerDelta[key] = (custLedgerDelta[key] ?? 0) + remainingMinor;
            }
          }
        }
        counts['sales'] = migratedSalesCount;
      }

      if (await tableExists('sales_invoice_items')) {
        final cols = await colsOf('sales_invoice_items');
        final rows = await sourceDb.query('sales_invoice_items');
        int inserted = 0;
        for (final r in rows) {
          final id = (r['id'] as num).toInt();
          final legacySaleId = cols.contains('invoiceId')
              ? (r['invoiceId'] as num?)?.toInt() ?? -1
              : (r['saleId'] as num?)?.toInt() ?? -1;
          final curStr = cols.contains('saleCurrency')
              ? r['saleCurrency']?.toString()
              : (cols.contains('currency') ? r['currency']?.toString() : null);
          final currency = CurrencyCode.fromRaw(curStr);
          final targetSaleId = splitSaleMap[(legacySaleId, currency.code)] ?? legacySaleId;

          if (!validSaleIds.contains(targetSaleId)) {
            await logAudit('sales_invoice_items', id, 'WARNING', 'ORPHAN_SKIPPED',
                'تم تجاهل صنف فاتورة يتيم لعدم وجود الفاتورة رقم $legacySaleId.');
            continue;
          }
          final itemName = cols.contains('itemName')
              ? (r['itemName'] ?? '').toString()
              : (r['qatType'] ?? '').toString();
          final rawBatchId = cols.contains('itemId') ? (r['itemId'] as num?)?.toInt() : null;
          final resolvedBatchId =
              (rawBatchId != null && validBatchIds.contains(rawBatchId)) ? rawBatchId : null;
          final qtyMilli =
              QuantityMath.fromLegacyDouble(r['quantity'] as num?).clamp(1, 1 << 60).toInt();
          final unitPriceMinor =
              Money.fromLegacyDouble(r['unitPrice'] as num?).clamp(0, 1 << 60).toInt();
          final buyPriceMinor = cols.contains('buyPrice')
              ? Money.fromLegacyDouble(r['buyPrice'] as num?).clamp(0, 1 << 60).toInt()
              : 0;
          final lineTotal = cols.contains('total')
              ? Money.fromLegacyDouble(r['total'] as num?)
              : InvoiceMath.lineTotalMinor(qtyMilli: qtyMilli, unitPriceMinor: unitPriceMinor);

          await txn.insert('sales_invoice_items', {
            'id': id,
            'sale_id': targetSaleId,
            'inventory_batch_id': resolvedBatchId,
            'qat_type_name': itemName.isEmpty ? 'صنف' : itemName,
            'qty_milli': qtyMilli,
            'unit': cols.contains('unit') ? (r['unit'] ?? 'ربطة').toString() : 'ربطة',
            'unit_price_minor': unitPriceMinor,
            'buy_price_minor': buyPriceMinor,
            'line_total_minor': lineTotal,
            'currency_code': currency.code,
          });
          inserted++;
        }
        counts['sales_invoice_items'] = inserted;
      }

      // 6. customer_payments (Room v18 lacks `customerName` column; resolve via `customerNameById`)
      if (await tableExists('customer_payments')) {
        final cols = await colsOf('customer_payments');
        final rows = await sourceDb.query('customer_payments');
        int inserted = 0;
        for (final r in rows) {
          final id = (r['id'] as num).toInt();
          final rawCustId = (r['customerId'] as num?)?.toInt();
          final rawCustName = cols.contains('customerName')
              ? (r['customerName'] ?? '').toString().trim()
              : '';
          int? resolvedCustId =
              (rawCustId != null && validCustomerIds.contains(rawCustId)) ? rawCustId : null;
          resolvedCustId ??= customerIdByName[rawCustName.toLowerCase()];
          if (resolvedCustId == null) {
            await logAudit('customer_payments', id, 'WARNING', 'ORPHAN_SKIPPED',
                'تم تجاهل سند قبض عميل بدون عميل مرتبط ($rawCustName).');
            continue;
          }
          final custName = rawCustName.isNotEmpty
              ? rawCustName
              : (customerNameById[resolvedCustId] ?? 'عميل #$resolvedCustId');
          final currency = cols.contains('currency')
              ? CurrencyCode.fromRaw(r['currency']?.toString())
              : CurrencyCode.yer;
          final amountMinor = Money.fromLegacyDouble(r['amount'] as num?).clamp(1, 1 << 60).toInt();
          final pd = QatDateUtils.parseLegacyDate(r['date']?.toString(), fallbackMs: nowMs);
          final isArchived = cols.contains('isArchived')
              ? ((r['isArchived'] as num?)?.toInt() ?? 0)
              : 0;
          await txn.insert('customer_payments', {
            'id': id,
            'customer_id': resolvedCustId,
            'customer_name': custName,
            'amount_minor': amountMinor,
            'currency_code': currency.code,
            'date_ms': pd.epochMs,
            'date_iso': pd.isoDate,
            'payment_method': cols.contains('paymentMethod')
                ? (r['paymentMethod'] ?? 'نقدي').toString()
                : 'نقدي',
            'notes': cols.contains('notes') ? (r['notes'] ?? '').toString() : '',
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
                'تمت إعادة تسوية رصيد العميل من الفواتير والسندات بشكل منفصل لكل عملة (YER=${Money.formatMinor(yerBal)}, SAR=${Money.formatMinor(sarBal)}).');
          }
        }
      }

      // 7. supplier_purchases & supplier_purchase_items (supports Room v18 split YER/SAR + demar + bank)
      final validPurchaseIds = <int>{};
      final splitPurchaseMap = <(int, String), int>{};
      final supLedgerDelta = <(int, String), int>{};
      int nextSyntheticPurchaseId = 100000;

      if (await tableExists('supplier_purchases')) {
        final cols = await colsOf('supplier_purchases');
        final rows = await sourceDb.query('supplier_purchases');
        for (final r in rows) {
          final id = (r['id'] as num).toInt();
          if (id >= nextSyntheticPurchaseId) nextSyntheticPurchaseId = id + 1000;
        }
        int inserted = 0;

        for (final r in rows) {
          final id = (r['id'] as num).toInt();
          final rawSupId = (r['supplierId'] as num?)?.toInt();
          final rawSupName = cols.contains('supplierName')
              ? (r['supplierName'] ?? '').toString().trim()
              : '';
          int? resolvedSupId =
              (rawSupId != null && validSupplierIds.contains(rawSupId)) ? rawSupId : null;
          resolvedSupId ??= supplierIdByName[rawSupName.toLowerCase()];
          if (resolvedSupId == null) {
            await logAudit('supplier_purchases', id, 'WARNING', 'ORPHAN_SKIPPED',
                'تم تجاهل فاتورة شراء غير مرتبطة بمورد ($rawSupName).');
            continue;
          }
          final supName = rawSupName.isNotEmpty
              ? rawSupName
              : (supplierNameById[resolvedSupId] ?? 'مورد #$resolvedSupId');

          final pd = QatDateUtils.parseLegacyDate(r['date']?.toString(), fallbackMs: nowMs);
          final discountMinor = cols.contains('discount')
              ? Money.fromLegacyDouble(r['discount'] as num?).clamp(0, 1 << 60).toInt()
              : 0;
          final taxBps = cols.contains('taxPercent')
              ? Money.fromLegacyDouble(r['taxPercent'] as num?).clamp(0, 10000).toInt()
              : 0;
          final isArchived = cols.contains('isArchived')
              ? ((r['isArchived'] as num?)?.toInt() ?? 0)
              : 0;
          final notes = cols.contains('notes') ? (r['notes'] ?? '').toString() : '';
          final bankName = cols.contains('bankName') ? (r['bankName'] ?? '').toString() : '';
          final transferNo = cols.contains('transferNumber') ? (r['transferNumber'] ?? '').toString() : '';

          final totalYer = cols.contains('totalAmountYer')
              ? Money.fromLegacyDouble(r['totalAmountYer'] as num?)
              : 0;
          final totalSar = cols.contains('totalAmountSar')
              ? Money.fromLegacyDouble(r['totalAmountSar'] as num?)
              : 0;

          if (totalYer > 0 && totalSar > 0) {
            final paidYer = Money.fromLegacyDouble(r['paidAmountYer'] as num?).clamp(0, 1 << 60).toInt();
            final debtYer = Money.fromLegacyDouble(r['debtRemainingYer'] as num?);
            final pmYer = cols.contains('paymentMethodYer') ? (r['paymentMethodYer'] ?? '').toString() : '';
            final ptYer = PaymentType.fromRaw(pmYer);
            final demarYer = cols.contains('demarYer')
                ? Money.fromLegacyDouble(r['demarYer'] as num?).clamp(0, 1 << 60).toInt()
                : 0;
            final demarTypeYer = cols.contains('demarTypeYer') ? (r['demarTypeYer'] ?? '').toString() : '';
            final demarNumYer = cols.contains('demarNumberYer') ? (r['demarNumberYer'] ?? '').toString() : '';

            await txn.insert('supplier_purchases', {
              'id': id,
              'purchase_number': 'PUR-$id-YER',
              'supplier_id': resolvedSupId,
              'supplier_name': supName,
              'date_ms': pd.epochMs,
              'date_iso': pd.isoDate,
              'currency_code': 'YER',
              'subtotal_minor': totalYer + discountMinor,
              'discount_minor': discountMinor,
              'tax_bps': taxBps,
              'tax_amount_minor': 0,
              'total_amount_minor': totalYer,
              'paid_amount_minor': paidYer,
              'remaining_amount_minor': debtYer,
              'payment_type': ptYer.code,
              'notes': notes,
              'is_archived': isArchived,
              'payment_method': pmYer,
              'bank_name': bankName,
              'transfer_number': transferNo,
              'demar_minor': demarYer,
              'demar_type': demarTypeYer,
              'demar_number': demarNumYer,
            });
            validPurchaseIds.add(id);
            splitPurchaseMap[(id, 'YER')] = id;
            inserted++;
            if (isArchived == 0 && debtYer != 0) {
              supLedgerDelta[(resolvedSupId, 'YER')] =
                  (supLedgerDelta[(resolvedSupId, 'YER')] ?? 0) + debtYer;
            }

            final sarId = nextSyntheticPurchaseId++;
            final paidSar = Money.fromLegacyDouble(r['paidAmountSar'] as num?).clamp(0, 1 << 60).toInt();
            final debtSar = Money.fromLegacyDouble(r['debtRemainingSar'] as num?);
            final pmSar = cols.contains('paymentMethodSar') ? (r['paymentMethodSar'] ?? '').toString() : '';
            final ptSar = PaymentType.fromRaw(pmSar);
            final demarSar = cols.contains('demarSar')
                ? Money.fromLegacyDouble(r['demarSar'] as num?).clamp(0, 1 << 60).toInt()
                : 0;
            final demarTypeSar = cols.contains('demarTypeSar') ? (r['demarTypeSar'] ?? '').toString() : '';
            final demarNumSar = cols.contains('demarNumberSar') ? (r['demarNumberSar'] ?? '').toString() : '';

            await txn.insert('supplier_purchases', {
              'id': sarId,
              'purchase_number': 'PUR-$id-SAR',
              'supplier_id': resolvedSupId,
              'supplier_name': supName,
              'date_ms': pd.epochMs,
              'date_iso': pd.isoDate,
              'currency_code': 'SAR',
              'subtotal_minor': totalSar,
              'discount_minor': 0,
              'tax_bps': 0,
              'tax_amount_minor': 0,
              'total_amount_minor': totalSar,
              'paid_amount_minor': paidSar,
              'remaining_amount_minor': debtSar,
              'payment_type': ptSar.code,
              'notes': notes,
              'is_archived': isArchived,
              'payment_method': pmSar,
              'bank_name': bankName,
              'transfer_number': transferNo,
              'demar_minor': demarSar,
              'demar_type': demarTypeSar,
              'demar_number': demarNumSar,
            });
            validPurchaseIds.add(sarId);
            splitPurchaseMap[(id, 'SAR')] = sarId;
            inserted++;
            if (isArchived == 0 && debtSar != 0) {
              supLedgerDelta[(resolvedSupId, 'SAR')] =
                  (supLedgerDelta[(resolvedSupId, 'SAR')] ?? 0) + debtSar;
            }
          } else {
            final currency = (totalSar > 0 && totalYer == 0)
                ? CurrencyCode.sar
                : (cols.contains('currency')
                    ? CurrencyCode.fromRaw(r['currency']?.toString())
                    : CurrencyCode.yer);
            final totalMinor = currency == CurrencyCode.sar && totalSar > 0
                ? totalSar
                : (totalYer > 0
                    ? totalYer
                    : Money.fromLegacyDouble(r['totalAmount'] as num?));
            final paidMinor = currency == CurrencyCode.sar && totalSar > 0 && cols.contains('paidAmountSar')
                ? Money.fromLegacyDouble(r['paidAmountSar'] as num?).clamp(0, 1 << 60).toInt()
                : (totalYer > 0 && cols.contains('paidAmountYer')
                    ? Money.fromLegacyDouble(r['paidAmountYer'] as num?).clamp(0, 1 << 60).toInt()
                    : Money.fromLegacyDouble(r['paidAmount'] as num?).clamp(0, 1 << 60).toInt());
            final remainingMinor = currency == CurrencyCode.sar && totalSar > 0 && cols.contains('debtRemainingSar')
                ? Money.fromLegacyDouble(r['debtRemainingSar'] as num?)
                : (totalYer > 0 && cols.contains('debtRemainingYer')
                    ? Money.fromLegacyDouble(r['debtRemainingYer'] as num?)
                    : (cols.contains('debtRemaining')
                        ? Money.fromLegacyDouble(r['debtRemaining'] as num?)
                        : Money.fromLegacyDouble(r['remainingAmount'] as num?)));
            final rawPm = currency == CurrencyCode.sar && totalSar > 0 && cols.contains('paymentMethodSar')
                ? (r['paymentMethodSar'] ?? '').toString()
                : (totalYer > 0 && cols.contains('paymentMethodYer')
                    ? (r['paymentMethodYer'] ?? '').toString()
                    : (cols.contains('paymentMethod')
                        ? (r['paymentMethod'] ?? '').toString()
                        : (cols.contains('paymentType') ? (r['paymentType'] ?? '').toString() : '')));
            final pType = PaymentType.fromRaw(rawPm);
            final demarMinor = currency == CurrencyCode.sar
                ? (cols.contains('demarSar') ? Money.fromLegacyDouble(r['demarSar'] as num?).clamp(0, 1 << 60).toInt() : 0)
                : (cols.contains('demarYer') ? Money.fromLegacyDouble(r['demarYer'] as num?).clamp(0, 1 << 60).toInt() : 0);
            final demarType = currency == CurrencyCode.sar
                ? (cols.contains('demarTypeSar') ? (r['demarTypeSar'] ?? '').toString() : '')
                : (cols.contains('demarTypeYer') ? (r['demarTypeYer'] ?? '').toString() : '');
            final demarNumber = currency == CurrencyCode.sar
                ? (cols.contains('demarNumberSar') ? (r['demarNumberSar'] ?? '').toString() : '')
                : (cols.contains('demarNumberYer') ? (r['demarNumberYer'] ?? '').toString() : '');

            await txn.insert('supplier_purchases', {
              'id': id,
              'purchase_number': 'PUR-$id',
              'supplier_id': resolvedSupId,
              'supplier_name': supName,
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
              'notes': notes,
              'is_archived': isArchived,
              'payment_method': rawPm.isNotEmpty ? rawPm : pType.arabicLabel,
              'bank_name': bankName,
              'transfer_number': transferNo,
              'demar_minor': demarMinor,
              'demar_type': demarType,
              'demar_number': demarNumber,
            });
            validPurchaseIds.add(id);
            if (isArchived == 0 && remainingMinor != 0) {
              final key = (resolvedSupId, currency.code);
              supLedgerDelta[key] = (supLedgerDelta[key] ?? 0) + remainingMinor;
            }
            inserted++;
          }
        }
        counts['supplier_purchases'] = inserted;
      }

      if (await tableExists('supplier_purchase_items')) {
        final cols = await colsOf('supplier_purchase_items');
        final rows = await sourceDb.query('supplier_purchase_items');
        int inserted = 0;
        for (final r in rows) {
          final id = (r['id'] as num).toInt();
          final legacyPurchaseId = (r['purchaseId'] as num?)?.toInt() ?? -1;
          final currency = cols.contains('currency')
              ? CurrencyCode.fromRaw(r['currency']?.toString())
              : CurrencyCode.yer;
          final targetPurchaseId =
              splitPurchaseMap[(legacyPurchaseId, currency.code)] ?? legacyPurchaseId;
          if (!validPurchaseIds.contains(targetPurchaseId)) {
            await logAudit('supplier_purchase_items', id, 'WARNING', 'ORPHAN_SKIPPED',
                'تم تجاهل صنف مشتريات يتيم للفاتورة رقم $legacyPurchaseId.');
            continue;
          }
          final itemName = cols.contains('itemName')
              ? (r['itemName'] ?? '').toString()
              : (r['qatType'] ?? '').toString();
          final qtyMilli = QuantityMath.fromLegacyDouble(r['quantity'] as num?).clamp(1, 1 << 60).toInt();
          final unitPriceMinor = cols.contains('unitPrice')
              ? Money.fromLegacyDouble(r['unitPrice'] as num?).clamp(0, 1 << 60).toInt()
              : Money.fromLegacyDouble(r['buyPrice'] as num?).clamp(0, 1 << 60).toInt();
          final lineTotal = cols.contains('total')
              ? Money.fromLegacyDouble(r['total'] as num?)
              : InvoiceMath.lineTotalMinor(qtyMilli: qtyMilli, unitPriceMinor: unitPriceMinor);

          await txn.insert('supplier_purchase_items', {
            'id': id,
            'purchase_id': targetPurchaseId,
            'qat_type_name': itemName.isEmpty ? 'صنف' : itemName,
            'qty_milli': qtyMilli,
            'unit': cols.contains('unit') ? (r['unit'] ?? 'ربطة').toString() : 'ربطة',
            'buy_price_minor': unitPriceMinor,
            'line_total_minor': lineTotal,
            'currency_code': currency.code,
          });
          inserted++;
        }
        counts['supplier_purchase_items'] = inserted;
      }

      // 8. supplier_payments (if present in source)
      if (await tableExists('supplier_payments')) {
        final cols = await colsOf('supplier_payments');
        final rows = await sourceDb.query('supplier_payments');
        int inserted = 0;
        for (final r in rows) {
          final id = (r['id'] as num).toInt();
          final rawSupId = (r['supplierId'] as num?)?.toInt();
          final rawSupName = cols.contains('supplierName')
              ? (r['supplierName'] ?? '').toString().trim()
              : '';
          int? resolvedSupId =
              (rawSupId != null && validSupplierIds.contains(rawSupId)) ? rawSupId : null;
          resolvedSupId ??= supplierIdByName[rawSupName.toLowerCase()];
          if (resolvedSupId == null) {
            await logAudit('supplier_payments', id, 'WARNING', 'ORPHAN_SKIPPED',
                'تم تجاهل سند صرف مورد غير مرتبط ($rawSupName).');
            continue;
          }
          final supName = rawSupName.isNotEmpty
              ? rawSupName
              : (supplierNameById[resolvedSupId] ?? 'مورد #$resolvedSupId');
          final currency = cols.contains('currency')
              ? CurrencyCode.fromRaw(r['currency']?.toString())
              : CurrencyCode.yer;
          final amountMinor = Money.fromLegacyDouble(r['amount'] as num?).clamp(1, 1 << 60).toInt();
          final pd = QatDateUtils.parseLegacyDate(r['date']?.toString(), fallbackMs: nowMs);
          final isArchived = cols.contains('isArchived')
              ? ((r['isArchived'] as num?)?.toInt() ?? 0)
              : 0;
          await txn.insert('supplier_payments', {
            'id': id,
            'supplier_id': resolvedSupId,
            'supplier_name': supName,
            'amount_minor': amountMinor,
            'currency_code': currency.code,
            'date_ms': pd.epochMs,
            'date_iso': pd.isoDate,
            'payment_method': cols.contains('paymentMethod')
                ? (r['paymentMethod'] ?? 'نقدي').toString()
                : 'نقدي',
            'notes': cols.contains('notes') ? (r['notes'] ?? '').toString() : '',
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

      // 9. expenses (Room v18 lacks `description`; falls back to notes or category)
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
          final cat = cols.contains('category') ? (r['category'] ?? 'عام').toString() : 'عام';
          final notes = cols.contains('notes') ? (r['notes'] ?? '').toString() : '';
          final desc = cols.contains('description')
              ? (r['description'] ?? '').toString()
              : (notes.trim().isNotEmpty ? notes.trim() : cat);

          await txn.insert('expenses', {
            'id': id,
            'description': desc,
            'category': cat,
            'account_id': null,
            'amount_minor': Money.fromLegacyDouble(r['amount'] as num?).clamp(0, 1 << 60).toInt(),
            'currency_code': currency.code,
            'date_ms': pd.epochMs,
            'date_iso': pd.isoDate,
            'notes': notes,
            'is_archived': isArchived,
          });
        }
        counts['expenses'] = rows.length;
      }

      // 10. qat_tax_entries (Room v18 uses `date` PK, `taxAmount`, `stallOutflow`, `laborOutflow`, `notes`)
      final taxTableName = (await tableExists('qat_tax_entries'))
          ? 'qat_tax_entries'
          : ((await tableExists('tax_entries')) ? 'tax_entries' : '');
      if (taxTableName.isNotEmpty) {
        final cols = await colsOf(taxTableName);
        final rows = await sourceDb.query(taxTableName);
        int seqId = 1;
        for (final r in rows) {
          final id = cols.contains('id') && r['id'] != null
              ? (r['id'] as num).toInt()
              : seqId++;
          final pd = QatDateUtils.parseLegacyDate(r['date']?.toString(), fallbackMs: nowMs);
          final isArchived = cols.contains('isArchived')
              ? ((r['isArchived'] as num?)?.toInt() ?? 0)
              : 0;
          final currency = cols.contains('currency')
              ? CurrencyCode.fromRaw(r['currency']?.toString())
              : CurrencyCode.yer;
          final taxAmt = cols.contains('taxAmount')
              ? Money.fromLegacyDouble(r['taxAmount'] as num?)
              : (cols.contains('totalTaxAmount')
                  ? Money.fromLegacyDouble(r['totalTaxAmount'] as num?)
                  : 0);
          final stallAmt = cols.contains('stallOutflow')
              ? Money.fromLegacyDouble(r['stallOutflow'] as num?)
              : 0;
          final laborAmt = cols.contains('laborOutflow')
              ? Money.fromLegacyDouble(r['laborOutflow'] as num?)
              : 0;
          await txn.insert('qat_tax_entries', {
            'id': id,
            'date_ms': pd.epochMs,
            'date_iso': pd.isoDate,
            'total_qty_milli': cols.contains('totalQty')
                ? QuantityMath.fromLegacyDouble(r['totalQty'] as num?)
                : 0,
            'tax_rate_minor': cols.contains('taxRate')
                ? Money.fromLegacyDouble(r['taxRate'] as num?)
                : 0,
            'tax_amount_minor': taxAmt,
            'stall_outflow_minor': stallAmt,
            'labor_outflow_minor': laborAmt,
            'currency_code': currency.code,
            'source': cols.contains('source') ? (r['source'] ?? 'يدوي').toString() : 'يومي',
            'notes': cols.contains('notes') ? (r['notes'] ?? '').toString() : '',
            'is_archived': isArchived,
          });
        }
        counts['qat_tax_entries'] = rows.length;
      }

      // 11. qat_import_weights (Room v18 table)
      if (await tableExists('qat_import_weights')) {
        final cols = await colsOf('qat_import_weights');
        final rows = await sourceDb.query('qat_import_weights');
        for (final r in rows) {
          final id = (r['id'] as num).toInt();
          final pd = QatDateUtils.parseLegacyDate(r['date']?.toString(), fallbackMs: nowMs);
          await txn.insert('qat_import_weights', {
            'id': id,
            'date_ms': pd.epochMs,
            'date_iso': pd.isoDate,
            'weight_milli': QuantityMath.fromLegacyDouble(r['weight'] as num?).clamp(0, 1 << 60).toInt(),
            'receiver_name': cols.contains('receiverName') ? (r['receiverName'] ?? '').toString() : '',
            'notes': cols.contains('notes') ? (r['notes'] ?? '').toString() : '',
            'is_archived': 0,
          });
        }
        counts['qat_import_weights'] = rows.length;
      }

      // 12. transfer_recipients
      final validRecipientIds = <int>{};
      if (await tableExists('transfer_recipients')) {
        final cols = await colsOf('transfer_recipients');
        final rows = await sourceDb.query('transfer_recipients');
        for (final r in rows) {
          final id = (r['id'] as num).toInt();
          await txn.insert('transfer_recipients', {
            'id': id,
            'name': (r['name'] ?? '').toString(),
            'phone': cols.contains('phone') ? (r['phone'] ?? '').toString() : '',
            'company': cols.contains('company') ? (r['company'] ?? '').toString() : '',
            'notes': cols.contains('notes') ? (r['notes'] ?? '').toString() : '',
            'created_at_ms': cols.contains('createdAt')
                ? ((r['createdAt'] as num?)?.toInt() ?? nowMs)
                : nowMs,
            'is_active': cols.contains('isActive') ? ((r['isActive'] as num?)?.toInt() ?? 1) : 1,
          });
          validRecipientIds.add(id);
        }
        counts['transfer_recipients'] = rows.length;
      }

      // 13. money_transfers (handles Room v18 `sender`/`receiver`/`referenceNumber`/`statement` AND synthetic names)
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
          final sender = cols.contains('senderName')
              ? (r['senderName'] ?? '').toString()
              : (cols.contains('sender') ? (r['sender'] ?? '').toString() : '');
          final receiver = cols.contains('receiverName')
              ? (r['receiverName'] ?? '').toString()
              : (cols.contains('receiver') ? (r['receiver'] ?? '').toString() : '');
          final trNumber = cols.contains('transferNumber')
              ? (r['transferNumber'] ?? '').toString()
              : (cols.contains('referenceNumber') ? (r['referenceNumber'] ?? '').toString() : '');
          final statement = cols.contains('statement') ? (r['statement'] ?? '').toString().trim() : '';
          final notesRaw = cols.contains('notes') ? (r['notes'] ?? '').toString().trim() : '';
          final combinedNotes = [
            if (statement.isNotEmpty) statement,
            if (notesRaw.isNotEmpty && notesRaw != statement) notesRaw,
          ].join(' — ');

          await txn.insert('money_transfers', {
            'id': id,
            'sender_name': sender,
            'receiver_name': receiver,
            'recipient_id': resolvedRecId,
            'amount_minor': Money.fromLegacyDouble(r['amount'] as num?).clamp(0, 1 << 60).toInt(),
            'fee_minor': cols.contains('fee')
                ? Money.fromLegacyDouble(r['fee'] as num?).clamp(0, 1 << 60).toInt()
                : 0,
            'currency_code': currency.code,
            'transfer_company': cols.contains('transferCompany') ? (r['transferCompany'] ?? '').toString() : '',
            'transfer_number': trNumber,
            'date_ms': pd.epochMs,
            'date_iso': pd.isoDate,
            'status_code': status.code,
            'status_label': status.arabicLabel,
            'notes': combinedNotes,
            'is_archived': isArchived,
          });
        }
        if (unlinkedCount > 0) {
          await logAudit('money_transfers', null, 'INFO', 'UNLINKED_TRANSFERS_PRESERVED',
              'يوجد $unlinkedCount حوالة قديمة غير مرتبطة ببطاقة مستلم (تم حفظها كحوالات غير مرتبطة دون اختلاق أسماء).');
        }
        counts['money_transfers'] = rows.length;
      }

      // 14. agency_profile (Room v18 table)
      if (await tableExists('agency_profile')) {
        final rows = await sourceDb.query('agency_profile', limit: 1);
        if (rows.isNotEmpty) {
          final pRow = rows.first;
          Future<void> putProfile(String k, String v) async {
            if (v.trim().isEmpty) return;
            await txn.insert(
              'app_settings',
              {'key': k, 'value': v.trim(), 'updated_at_ms': nowMs},
              conflictAlgorithm: ConflictAlgorithm.ignore,
            );
          }

          await putProfile('agency_name', (pRow['agencyName'] ?? '').toString());
          await putProfile('owner_name', (pRow['ownerName'] ?? '').toString());
          await putProfile('agency_phone', (pRow['phone'] ?? '').toString());
          await putProfile('agency_address', (pRow['address'] ?? '').toString());
          await putProfile('additional_contact', (pRow['additionalContact'] ?? '').toString());
          await putProfile('tagline', (pRow['tagline'] ?? '').toString());
          await putProfile('qat_types_raw', (pRow['qatTypes'] ?? '').toString());
          await putProfile('commercial_details', (pRow['commercialDetails'] ?? '').toString());
          await putProfile('app_display_name', (pRow['appDisplayName'] ?? '').toString());
          await putProfile('agency_logo_path', (pRow['logoPath'] ?? '').toString());
          await putProfile('header_text', (pRow['headerText'] ?? '').toString());
          await putProfile('footer_note', (pRow['footerText'] ?? '').toString());

          final cm = (pRow['currencyMode'] ?? '').toString();
          if (cm.isNotEmpty) {
            await putProfile('currency_mode', CurrencyMode.fromKey(cm).storageKey);
          }
          if (pRow['headerEnabled'] != null) {
            await putProfile('header_enabled', (pRow['headerEnabled'] as num).toInt() == 1 ? '1' : '0');
          }
          if (pRow['footerEnabled'] != null) {
            await putProfile('footer_enabled', (pRow['footerEnabled'] as num).toInt() == 1 ? '1' : '0');
          }
          if ((pRow['isConfigured'] as num?)?.toInt() == 1) {
            await putProfile('onboarding_complete', '1');
          }
          counts['agency_profile'] = 1;
        }
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
    LegacyMigrationSources sources,
  ) async {
    final legacyPrefs = sources.legacyPrefs;
    if (legacyPrefs.isEmpty && sources.legacyActiveLicenseDoc.isEmpty) return;

    final appPrefs = legacyPrefs['app_settings'] ?? {};
    final branding = legacyPrefs['branding_prefs'] ?? {};
    final universalTheme = legacyPrefs['app_universal_theme_prefs'] ?? {};
    final printer = legacyPrefs['printer_prefs'] ?? {};
    final agencyLegacy = legacyPrefs['qat_agency_prefs'] ?? {};
    final themeLegacy = legacyPrefs['app_theme_prefs'] ?? {};
    final licenseStore = legacyPrefs['qat_active_license_store'] ?? {};
    final nowMs = DateTime.now().millisecondsSinceEpoch;

    Future<void> putIfAbsent(String key, String value) async {
      if (value.trim().isEmpty) return;
      await targetDb.insert(
        'app_settings',
        {'key': key, 'value': value.trim(), 'updated_at_ms': nowMs},
        conflictAlgorithm: ConflictAlgorithm.ignore,
      );
    }

    // Branding
    await putIfAbsent(
      'agency_name',
      (branding['branding_agency_name'] ?? agencyLegacy['agency_name'] ?? '').toString(),
    );
    await putIfAbsent('owner_name', (branding['branding_owner_name'] ?? '').toString());
    await putIfAbsent(
      'agency_phone',
      (branding['branding_phone'] ?? agencyLegacy['agency_phone'] ?? '').toString(),
    );
    await putIfAbsent(
      'agency_address',
      (branding['branding_address'] ?? agencyLegacy['agency_address'] ?? '').toString(),
    );
    await putIfAbsent('additional_contact', (branding['branding_additional_contact'] ?? '').toString());
    await putIfAbsent('tagline', (branding['branding_agency_tagline'] ?? '').toString());
    await putIfAbsent('qat_types_raw', (branding['branding_qat_types'] ?? '').toString());
    await putIfAbsent('commercial_details', (branding['branding_commercial_details'] ?? '').toString());
    await putIfAbsent('app_display_name', (branding['branding_app_name'] ?? '').toString());
    await putIfAbsent('agency_logo_path', (branding['branding_logo_path'] ?? '').toString());
    await putIfAbsent('header_text', (branding['branding_header_text'] ?? '').toString());
    await putIfAbsent(
      'footer_note',
      (branding['branding_footer_text'] ?? agencyLegacy['footer_note'] ?? '').toString(),
    );
    if (branding.containsKey('branding_header_enabled')) {
      await putIfAbsent('header_enabled', branding['branding_header_enabled'] == true ? '1' : '0');
    }
    if (branding.containsKey('branding_footer_enabled')) {
      await putIfAbsent('footer_enabled', branding['branding_footer_enabled'] == true ? '1' : '0');
    }

    // Currency & Exchange rate
    final rawCurrencyMode =
        (branding['branding_currency_mode'] ?? agencyLegacy['currency_mode'] ?? '').toString();
    if (rawCurrencyMode.isNotEmpty) {
      await putIfAbsent('currency_mode', CurrencyMode.fromKey(rawCurrencyMode).storageKey);
    }
    final sarRate = appPrefs['sar_to_yer_rate'];
    if (sarRate is num && sarRate > 0) {
      await putIfAbsent('display_sar_to_yer_milli', QuantityMath.fromLegacyDouble(sarRate).toString());
    }

    // Theme & Universal Palette
    final isDark = appPrefs['dark_mode'] == true || themeLegacy['is_dark_mode'] == true;
    if (appPrefs.containsKey('dark_mode') || themeLegacy.containsKey('is_dark_mode')) {
      await putIfAbsent('theme_choice', isDark ? 'dark_black' : 'light_enterprise');
    }
    final themeId = (universalTheme['theme_id'] ?? '').toString();
    if (themeId.isNotEmpty) {
      await putIfAbsent('palette_id', themeId);
    }
    await putIfAbsent('custom_primary_hex', (universalTheme['custom_primary_hex'] ?? '').toString());
    await putIfAbsent('custom_accent_hex', (universalTheme['custom_accent_hex'] ?? '').toString());

    // Printer config
    final pSize = printer['size'];
    if (pSize is num) {
      await putIfAbsent('printer_paper_mm', (pSize.toInt() >= 80 ? 80 : 58).toString());
    }
    await putIfAbsent('saved_printer_address', (printer['address'] ?? '').toString());
    await putIfAbsent('saved_printer_name', (printer['name'] ?? '').toString());

    // Auth / Lock / Onboarding / Section permissions
    if (branding['agency_profile_configured_v3'] == true ||
        agencyLegacy['onboarding_done'] == true ||
        agencyLegacy['is_setup_done'] == true) {
      await putIfAbsent('onboarding_complete', '1');
    }
    if (appPrefs.containsKey('app_locked_v2')) {
      await putIfAbsent('app_lock_enabled', appPrefs['app_locked_v2'] == true ? '1' : '0');
    }
    if (appPrefs.containsKey('remembered_user')) {
      await putIfAbsent('remember_me', (appPrefs['remembered_user'] ?? '').toString().isNotEmpty ? '1' : '0');
    }

    // Legacy Admin Password -> update default admin user password hash on migration if changed
    final legacyAdminPass = (appPrefs['admin_password'] ?? '').toString();
    if (legacyAdminPass.isNotEmpty && legacyAdminPass != '123456') {
      await putIfAbsent('migrated_legacy_admin_password', legacyAdminPass);
    }

    // Section Permissions
    final secMap = <String, bool>{
      'accounts': appPrefs['section_accounts_enabled'] as bool? ?? true,
      'transfers': appPrefs['section_transfers_enabled'] as bool? ?? true,
      'customers': appPrefs['section_customers_enabled'] as bool? ?? true,
      'expenses': appPrefs['section_expenses_enabled'] as bool? ?? true,
      'reports': appPrefs['section_reports_enabled'] as bool? ?? true,
      'archive': appPrefs['section_archive_enabled'] as bool? ?? true,
      'sales': true,
      'inventory': true,
    };
    if (appPrefs.keys.any((k) => k.startsWith('section_'))) {
      await putIfAbsent('migrated_section_permissions_json', jsonEncode(secMap));
    }

    // Legacy active license envelope
    final doc = sources.legacyActiveLicenseDoc.trim().isNotEmpty
        ? sources.legacyActiveLicenseDoc.trim()
        : (licenseStore['active_qlic_doc'] ?? '').toString().trim();
    if (doc.isNotEmpty) {
      await putIfAbsent('migrated_legacy_license_doc', doc);
    }
  }
}
