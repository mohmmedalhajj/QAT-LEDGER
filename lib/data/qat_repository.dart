import 'dart:convert';

import 'package:sqflite/sqflite.dart';

import '../core/date_utils.dart';
import '../core/money.dart';
import '../core/theme.dart';
import 'database_service.dart';
import 'models.dart';

class NewSaleItemInput {
  const NewSaleItemInput({
    this.inventoryBatchId,
    required this.qatTypeName,
    required this.qtyMilli,
    required this.unit,
    required this.unitPriceMinor,
    required this.buyPriceMinor,
  });

  final int? inventoryBatchId;
  final String qatTypeName;
  final int qtyMilli;
  final String unit;
  final int unitPriceMinor;
  final int buyPriceMinor;
}

class NewPurchaseItemInput {
  const NewPurchaseItemInput({
    required this.qatTypeName,
    required this.qtyMilli,
    required this.unit,
    required this.buyPriceMinor,
    this.sellPriceMinor = 0,
  });

  final String qatTypeName;
  final int qtyMilli;
  final String unit;
  final int buyPriceMinor;
  final int sellPriceMinor;
}

class QatRepository {
  QatRepository(this.dbService);

  final DatabaseService dbService;

  Future<Database> get _db => dbService.database;

  // ---------------------------------------------------------------------------
  // Settings
  // ---------------------------------------------------------------------------

  Future<AgencySettings> getAgencySettings() async {
    final db = await _db;
    final rows = await db.query('app_settings');
    final map = <String, String>{
      for (final r in rows) (r['key'] ?? '').toString(): (r['value'] ?? '').toString(),
    };
    return AgencySettings(
      agencyName: map['agency_name']?.isNotEmpty == true
          ? map['agency_name']!
          : AgencySettings.defaults.agencyName,
      agencyPhone: map['agency_phone'] ?? '',
      agencyAddress: map['agency_address'] ?? '',
      footerNote: map['footer_note']?.isNotEmpty == true
          ? map['footer_note']!
          : AgencySettings.defaults.footerNote,
      logoPath: map['agency_logo_path'] ?? '',
      currencyMode: CurrencyMode.fromKey(map['currency_mode']),
      displaySarToYerMilli:
          int.tryParse(map['display_sar_to_yer_milli'] ?? '') ?? 0,
      themeChoice: AppThemeModeChoice.fromKey(map['theme_choice']),
      onboardingComplete: map['onboarding_complete'] == '1',
      savedPrinterName: map['saved_printer_name'] ?? '',
      savedPrinterAddress: map['saved_printer_address'] ?? '',
      printerPaperMm: int.tryParse(map['printer_paper_mm'] ?? '') ?? 80,
      ownerName: map['owner_name'] ?? '',
      additionalContact: map['additional_contact'] ?? '',
      tagline: map['tagline']?.isNotEmpty == true
          ? map['tagline']!
          : AgencySettings.defaults.tagline,
      qatTypesRaw: map['qat_types_raw']?.isNotEmpty == true
          ? map['qat_types_raw']!
          : AgencySettings.defaults.qatTypesRaw,
      commercialDetails: map['commercial_details'] ?? '',
      appDisplayName: map['app_display_name'] ?? '',
      headerEnabled: map['header_enabled'] != '0',
      footerEnabled: map['footer_enabled'] != '0',
      headerText: map['header_text'] ?? '',
      paletteId: map['palette_id']?.isNotEmpty == true
          ? map['palette_id']!
          : AgencySettings.defaults.paletteId,
      customPrimaryHex: map['custom_primary_hex']?.isNotEmpty == true
          ? map['custom_primary_hex']!
          : AgencySettings.defaults.customPrimaryHex,
      customAccentHex: map['custom_accent_hex']?.isNotEmpty == true
          ? map['custom_accent_hex']!
          : AgencySettings.defaults.customAccentHex,
      customCanvasHex: map['custom_canvas_hex'] ?? '',
      customCardHex: map['custom_card_hex'] ?? '',
      customTextHex: map['custom_text_hex'] ?? '',
      syncColorsWithPdf: map['sync_colors_with_pdf'] != '0',
      rememberMe: map['remember_me'] != '0',
      appLockEnabled: map['app_lock_enabled'] == '1',
    );
  }

  Future<void> setSetting(String key, String value) async {
    final db = await _db;
    await db.insert(
      'app_settings',
      {
        'key': key,
        'value': value,
        'updated_at_ms': DateTime.now().millisecondsSinceEpoch,
      },
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  Future<String?> getSetting(String key) async {
    final db = await _db;
    final rows = await db.query(
      'app_settings',
      columns: ['value'],
      where: 'key = ?',
      whereArgs: [key],
      limit: 1,
    );
    return rows.isEmpty ? null : rows.first['value']?.toString();
  }

  Future<void> saveAgencySettings(AgencySettings s) async {
    final db = await _db;
    final now = DateTime.now().millisecondsSinceEpoch;
    final pairs = <String, String>{
      'agency_name': s.agencyName,
      'agency_phone': s.agencyPhone,
      'agency_address': s.agencyAddress,
      'footer_note': s.footerNote,
      'agency_logo_path': s.logoPath,
      'currency_mode': s.currencyMode.storageKey,
      'display_sar_to_yer_milli': s.displaySarToYerMilli.toString(),
      'theme_choice': s.themeChoice.key,
      'onboarding_complete': s.onboardingComplete ? '1' : '0',
      'saved_printer_name': s.savedPrinterName,
      'saved_printer_address': s.savedPrinterAddress,
      'printer_paper_mm': s.printerPaperMm.toString(),
      'owner_name': s.ownerName,
      'additional_contact': s.additionalContact,
      'tagline': s.tagline,
      'qat_types_raw': s.qatTypesRaw,
      'commercial_details': s.commercialDetails,
      'app_display_name': s.appDisplayName,
      'header_enabled': s.headerEnabled ? '1' : '0',
      'footer_enabled': s.footerEnabled ? '1' : '0',
      'header_text': s.headerText,
      'palette_id': s.paletteId,
      'custom_primary_hex': s.customPrimaryHex,
      'custom_accent_hex': s.customAccentHex,
      'custom_canvas_hex': s.customCanvasHex,
      'custom_card_hex': s.customCardHex,
      'custom_text_hex': s.customTextHex,
      'sync_colors_with_pdf': s.syncColorsWithPdf ? '1' : '0',
      'remember_me': s.rememberMe ? '1' : '0',
      'app_lock_enabled': s.appLockEnabled ? '1' : '0',
    };
    await db.transaction<void>((txn) async {
      for (final e in pairs.entries) {
        await txn.insert(
          'app_settings',
          {'key': e.key, 'value': e.value, 'updated_at_ms': now},
          conflictAlgorithm: ConflictAlgorithm.replace,
        );
      }
    });
  }

  // ---------------------------------------------------------------------------
  // Qat Types & Inventory Batches
  // ---------------------------------------------------------------------------

  Future<List<QatType>> getQatTypes({bool includeHidden = false}) async {
    final db = await _db;
    final rows = await db.query(
      'qat_types',
      where: includeHidden ? null : 'is_hidden = 0',
      orderBy: 'name ASC',
    );
    return rows.map(_mapQatType).toList();
  }

  Future<int> upsertQatType({
    int? id,
    required String name,
    required int defaultBuyPriceMinor,
    required int defaultSellPriceMinor,
    required CurrencyCode defaultCurrency,
    String notes = '',
    bool isHidden = false,
  }) async {
    final db = await _db;
    final cleanName = name.trim();
    if (cleanName.isEmpty) {
      throw ArgumentError('اسم الصنف مطلوب');
    }
    final data = {
      'name': cleanName,
      'default_buy_price_minor': defaultBuyPriceMinor.clamp(0, 1 << 60).toInt(),
      'default_sell_price_minor': defaultSellPriceMinor.clamp(0, 1 << 60).toInt(),
      'default_currency': defaultCurrency.code,
      'notes': notes.trim(),
      'is_hidden': isHidden ? 1 : 0,
    };
    if (id != null && id > 0) {
      await db.update('qat_types', data, where: 'id = ?', whereArgs: [id]);
      return id;
    }
    return db.insert('qat_types', {
      ...data,
      'created_at_ms': DateTime.now().millisecondsSinceEpoch,
    });
  }

  Future<void> deleteQatType(int id) async {
    final db = await _db;
    await db.delete('qat_types', where: 'id = ?', whereArgs: [id]);
  }

  Future<List<InventoryBatch>> getInventoryBatches({bool includeArchived = false}) async {
    final db = await _db;
    final rows = await db.query(
      'inventory_batches',
      where: includeArchived ? null : 'is_archived = 0',
      orderBy: 'purchase_date_ms DESC, id DESC',
    );
    return rows.map(_mapBatch).toList();
  }

  Future<int> addInventoryBatch({
    int? qatTypeId,
    required String qatTypeName,
    int? supplierId,
    String supplierName = '',
    required int qtyMilli,
    required String unit,
    required int buyPriceMinor,
    required int sellPriceMinor,
    required CurrencyCode currency,
    int? dateMs,
    String itemNumber = '',
    int lowStockThresholdMilli = 0,
    int? receiverId,
    String receiverName = '',
    String notes = '',
  }) async {
    final db = await _db;
    final ts = dateMs ?? DateTime.now().millisecondsSinceEpoch;
    final iso = QatDateUtils.toIsoDate(DateTime.fromMillisecondsSinceEpoch(ts));
    return db.insert('inventory_batches', {
      'qat_type_id': qatTypeId,
      'qat_type_name': qatTypeName.trim(),
      'supplier_id': supplierId,
      'supplier_name': supplierName.trim(),
      'remaining_qty_milli': qtyMilli.clamp(0, 1 << 60).toInt(),
      'initial_qty_milli': qtyMilli.clamp(0, 1 << 60).toInt(),
      'unit': unit.trim().isEmpty ? 'ربطة' : unit.trim(),
      'buy_price_minor': buyPriceMinor.clamp(0, 1 << 60).toInt(),
      'sell_price_minor': sellPriceMinor.clamp(0, 1 << 60).toInt(),
      'currency_code': currency.code,
      'purchase_date_ms': ts,
      'purchase_date_iso': iso,
      'is_archived': 0,
      'item_number': itemNumber.trim(),
      'low_stock_threshold_milli': lowStockThresholdMilli.clamp(0, 1 << 60).toInt(),
      'receiver_id': receiverId,
      'receiver_name': receiverName.trim(),
      'notes': notes.trim(),
    });
  }

  Future<void> updateInventoryBatch({
    required int id,
    required String qatTypeName,
    required int remainingQtyMilli,
    required String unit,
    required int buyPriceMinor,
    required int sellPriceMinor,
    required CurrencyCode currency,
    String? itemNumber,
    int? lowStockThresholdMilli,
    int? receiverId,
    String? receiverName,
    String? notes,
  }) async {
    final db = await _db;
    await db.update(
      'inventory_batches',
      {
        'qat_type_name': qatTypeName.trim(),
        'remaining_qty_milli': remainingQtyMilli.clamp(0, 1 << 60).toInt(),
        'unit': unit.trim().isEmpty ? 'ربطة' : unit.trim(),
        'buy_price_minor': buyPriceMinor.clamp(0, 1 << 60).toInt(),
        'sell_price_minor': sellPriceMinor.clamp(0, 1 << 60).toInt(),
        'currency_code': currency.code,
        if (itemNumber != null) 'item_number': itemNumber.trim(),
        if (lowStockThresholdMilli != null)
          'low_stock_threshold_milli': lowStockThresholdMilli.clamp(0, 1 << 60).toInt(),
        if (receiverId != null || receiverName != null) 'receiver_id': receiverId,
        if (receiverName != null) 'receiver_name': receiverName.trim(),
        if (notes != null) 'notes': notes.trim(),
      },
      where: 'id = ?',
      whereArgs: [id],
    );
  }

  // ---------------------------------------------------------------------------
  // Imported Qat Weights & Daily Receivers (وزن القات المستورد ومستلم المخزون)
  // ---------------------------------------------------------------------------

  Future<List<QatImportWeight>> getImportWeights({bool includeArchived = false}) async {
    final db = await _db;
    final rows = await db.query(
      'qat_import_weights',
      where: includeArchived ? null : 'is_archived = 0',
      orderBy: 'date_ms DESC, id DESC',
    );
    return rows.map(_mapImportWeight).toList();
  }

  Future<int> addImportWeight({
    required int weightMilli,
    required String receiverName,
    String notes = '',
    int? dateMs,
  }) async {
    final db = await _db;
    final ts = dateMs ?? DateTime.now().millisecondsSinceEpoch;
    final iso = QatDateUtils.toIsoDate(DateTime.fromMillisecondsSinceEpoch(ts));
    return db.insert('qat_import_weights', {
      'date_ms': ts,
      'date_iso': iso,
      'weight_milli': weightMilli.clamp(0, 1 << 60).toInt(),
      'receiver_name': receiverName.trim(),
      'notes': notes.trim(),
      'is_archived': 0,
    });
  }

  Future<void> updateImportWeight({
    required int id,
    required int weightMilli,
    required String receiverName,
    String notes = '',
    String? dateIso,
  }) async {
    final db = await _db;
    final parsed = dateIso != null ? QatDateUtils.parseLegacyDate(dateIso) : null;
    await db.update(
      'qat_import_weights',
      {
        'weight_milli': weightMilli.clamp(0, 1 << 60).toInt(),
        'receiver_name': receiverName.trim(),
        'notes': notes.trim(),
        if (parsed != null) 'date_ms': parsed.epochMs,
        if (parsed != null) 'date_iso': parsed.isoDate,
      },
      where: 'id = ?',
      whereArgs: [id],
    );
  }

  Future<void> deleteImportWeight(int id) async {
    final db = await _db;
    await db.delete('qat_import_weights', where: 'id = ?', whereArgs: [id]);
  }

  Future<void> deleteInventoryBatch(int id) async {
    final db = await _db;
    await db.delete('inventory_batches', where: 'id = ?', whereArgs: [id]);
  }

  Future<Map<CurrencyCode, InventoryValuation>> getInventoryValuation() async {
    final batches = await getInventoryBatches();
    final out = <CurrencyCode, InventoryValuation>{};
    for (final cur in CurrencyCode.values) {
      final matching = batches.where((b) => b.currency == cur && b.remainingQtyMilli > 0).toList();
      if (matching.isEmpty) continue;
      int qty = 0;
      int cost = 0;
      int retail = 0;
      for (final b in matching) {
        qty += b.remainingQtyMilli;
        cost += InvoiceMath.lineTotalMinor(
          qtyMilli: b.remainingQtyMilli,
          unitPriceMinor: b.buyPriceMinor,
        );
        retail += InvoiceMath.lineTotalMinor(
          qtyMilli: b.remainingQtyMilli,
          unitPriceMinor: b.sellPriceMinor,
        );
      }
      out[cur] = InventoryValuation(
        currency: cur,
        batchCount: matching.length,
        totalQtyMilli: qty,
        totalCostMinor: cost,
        totalRetailMinor: retail,
      );
    }
    return out;
  }

  // ---------------------------------------------------------------------------
  // Customers & Multi-Currency Balances
  // ---------------------------------------------------------------------------

  Future<List<Customer>> getCustomers({bool includeArchived = false}) async {
    final db = await _db;
    final rows = await db.query(
      'customers',
      where: includeArchived ? null : 'is_archived = 0',
      orderBy: 'name ASC',
    );
    final balRows = await db.query('customer_balances');
    final balMap = <int, Map<CurrencyCode, int>>{};
    for (final b in balRows) {
      final cid = (b['customer_id'] as num).toInt();
      final cur = CurrencyCode.fromRaw(b['currency_code']?.toString());
      final debt = (b['debt_minor'] as num).toInt();
      (balMap[cid] ??= {})[cur] = debt;
    }
    return rows
        .map((r) => _mapCustomer(r, balMap[(r['id'] as num).toInt()] ?? const {}))
        .toList();
  }

  Future<int> upsertCustomer({
    int? id,
    required String name,
    String phone = '',
    String address = '',
    String notes = '',
  }) async {
    final db = await _db;
    final cleanName = name.trim();
    if (cleanName.isEmpty) throw ArgumentError('اسم العميل مطلوب');
    final data = {
      'name': cleanName,
      'phone': phone.trim(),
      'address': address.trim(),
      'notes': notes.trim(),
    };
    if (id != null && id > 0) {
      await db.transaction<void>((txn) async {
        await txn.update('customers', data, where: 'id = ?', whereArgs: [id]);
        await txn.update('sales', {'customer_name': cleanName},
            where: 'customer_id = ?', whereArgs: [id]);
        await txn.update('customer_payments', {'customer_name': cleanName},
            where: 'customer_id = ?', whereArgs: [id]);
      });
      return id;
    }
    return db.insert('customers', {
      ...data,
      'created_at_ms': DateTime.now().millisecondsSinceEpoch,
      'is_archived': 0,
    });
  }

  Future<void> deleteCustomer(int id) async {
    final db = await _db;
    final hasSales = Sqflite.firstIntValue(
          await db.rawQuery('SELECT COUNT(*) FROM sales WHERE customer_id = ?', [id]),
        ) ??
        0;
    final hasPayments = Sqflite.firstIntValue(
          await db.rawQuery('SELECT COUNT(*) FROM customer_payments WHERE customer_id = ?', [id]),
        ) ??
        0;
    if (hasSales > 0 || hasPayments > 0) {
      throw StateError('لا يمكن حذف عميل لديه فواتير أو سندات قبض مسجلة؛ يمكنك أرشفته بدلاً من الحذف.');
    }
    await db.delete('customers', where: 'id = ?', whereArgs: [id]);
  }

  // ---------------------------------------------------------------------------
  // Sales Invoices (Exact Discount + Tax + Stock + Multi-Currency Debt)
  // ---------------------------------------------------------------------------

  Future<List<Sale>> getSales({
    bool includeArchived = false,
    String? fromIso,
    String? toIso,
  }) async {
    final db = await _db;
    final whereParts = <String>[];
    final args = <Object?>[];
    if (!includeArchived) {
      whereParts.add('is_archived = 0');
    }
    if (fromIso != null && fromIso.isNotEmpty) {
      whereParts.add('date_iso >= ?');
      args.add(fromIso);
    }
    if (toIso != null && toIso.isNotEmpty) {
      whereParts.add('date_iso <= ?');
      args.add(toIso);
    }

    final rows = await db.query(
      'sales',
      where: whereParts.isEmpty ? null : whereParts.join(' AND '),
      whereArgs: args,
      orderBy: 'date_ms DESC, id DESC',
    );
    if (rows.isEmpty) return const [];

    final saleIds = rows.map((r) => (r['id'] as num).toInt()).toList();
    final placeholders = List.filled(saleIds.length, '?').join(',');
    final itemRows = await db.query(
      'sales_invoice_items',
      where: 'sale_id IN ($placeholders)',
      whereArgs: saleIds,
      orderBy: 'id ASC',
    );
    final itemsBySale = <int, List<SaleItem>>{};
    for (final ir in itemRows) {
      final item = _mapSaleItem(ir);
      (itemsBySale[item.saleId] ??= []).add(item);
    }
    return rows
        .map((r) => _mapSale(r, itemsBySale[(r['id'] as num).toInt()] ?? const []))
        .toList();
  }

  Future<int> makeSale({
    int? customerId,
    required String customerName,
    required List<NewSaleItemInput> items,
    required CurrencyCode currency,
    int discountMinor = 0,
    int taxBps = 0,
    required PaymentType paymentType,
    int paidAmountMinor = 0,
    String paymentMethod = '',
    String notes = '',
    int? dateMs,
  }) async {
    if (items.isEmpty) {
      throw ArgumentError('يجب إضافة صنف واحد على الأقل للفاتورة.');
    }
    if (paymentType != PaymentType.paid && customerId == null) {
      throw ArgumentError('الفواتير الآجلة أو الجزئية تتطلب اختيار عميل مسجل.');
    }

    final db = await _db;
    final ts = dateMs ?? DateTime.now().millisecondsSinceEpoch;
    final iso = QatDateUtils.toIsoDate(DateTime.fromMillisecondsSinceEpoch(ts));

    final lineTotals = items
        .map((it) => InvoiceMath.lineTotalMinor(
              qtyMilli: it.qtyMilli,
              unitPriceMinor: it.unitPriceMinor,
            ))
        .toList();

    final prelimTotals = InvoiceMath.computeTotals(
      lineTotalsMinor: lineTotals,
      discountMinor: discountMinor,
      taxBps: taxBps,
      paidMinor: 0,
    );

    final effectivePaidMinor = switch (paymentType) {
      PaymentType.paid => prelimTotals.totalAmountMinor,
      PaymentType.credit => 0,
      PaymentType.partial => paidAmountMinor.clamp(0, prelimTotals.totalAmountMinor).toInt(),
    };

    final finalTotals = InvoiceMath.computeTotals(
      lineTotalsMinor: lineTotals,
      discountMinor: discountMinor,
      taxBps: taxBps,
      paidMinor: effectivePaidMinor,
    );

    final effectivePaymentType = finalTotals.remainingAmountMinor == 0
        ? PaymentType.paid
        : (finalTotals.paidAmountMinor == 0 ? PaymentType.credit : PaymentType.partial);
    final effectiveMethod = paymentMethod.trim().isNotEmpty
        ? paymentMethod.trim()
        : effectivePaymentType.arabicLabel;

    return db.transaction<int>((txn) async {
      // 1. Validate and deduct inventory batches atomically
      for (final item in items) {
        if (item.qtyMilli <= 0) {
          throw ArgumentError('الكمية يجب أن تكون أكبر من صفر.');
        }
        if (item.inventoryBatchId != null) {
          final batchRows = await txn.query(
            'inventory_batches',
            where: 'id = ?',
            whereArgs: [item.inventoryBatchId],
            limit: 1,
          );
          if (batchRows.isEmpty) {
            throw StateError('دفعة المخزون غير موجودة.');
          }
          final avail = (batchRows.first['remaining_qty_milli'] as num).toInt();
          if (avail < item.qtyMilli) {
            throw StateError(
              'الكمية المتوفرة من ${item.qatTypeName} (${QuantityMath.formatMilli(avail)}) أقل من المطلوب (${QuantityMath.formatMilli(item.qtyMilli)}).',
            );
          }
          await txn.update(
            'inventory_batches',
            {'remaining_qty_milli': avail - item.qtyMilli},
            where: 'id = ?',
            whereArgs: [item.inventoryBatchId],
          );
        }
      }

      // 2. Insert sale header (assign sequential invoice_number after insert)
      final tempNum = 'TMP-$ts';
      final saleId = await txn.insert('sales', {
        'invoice_number': tempNum,
        'customer_id': customerId,
        'customer_name': customerName.trim().isEmpty ? 'عميل نقدي' : customerName.trim(),
        'date_ms': ts,
        'date_iso': iso,
        'currency_code': currency.code,
        'subtotal_minor': finalTotals.subtotalMinor,
        'discount_minor': finalTotals.discountMinor,
        'tax_bps': finalTotals.taxBps,
        'tax_amount_minor': finalTotals.taxAmountMinor,
        'total_amount_minor': finalTotals.totalAmountMinor,
        'paid_amount_minor': finalTotals.paidAmountMinor,
        'remaining_amount_minor': finalTotals.remainingAmountMinor,
        'payment_type': effectivePaymentType.code,
        'status': effectiveMethod,
        'payment_method': effectiveMethod,
        'notes': notes.trim(),
        'is_archived': 0,
      });

      final invNo = 'INV-${iso.replaceAll('-', '')}-${saleId.toString().padLeft(4, '0')}';
      await txn.update('sales', {'invoice_number': invNo}, where: 'id = ?', whereArgs: [saleId]);

      // 3. Insert line items
      for (int i = 0; i < items.length; i++) {
        final it = items[i];
        await txn.insert('sales_invoice_items', {
          'sale_id': saleId,
          'inventory_batch_id': it.inventoryBatchId,
          'qat_type_name': it.qatTypeName,
          'qty_milli': it.qtyMilli,
          'unit': it.unit,
          'unit_price_minor': it.unitPriceMinor,
          'buy_price_minor': it.buyPriceMinor,
          'line_total_minor': lineTotals[i],
          'currency_code': currency.code,
        });
      }

      // 4. Update customer balance per currency
      if (customerId != null && finalTotals.remainingAmountMinor != 0) {
        await _adjustCustomerBalance(
          txn,
          customerId: customerId,
          currency: currency,
          deltaMinor: finalTotals.remainingAmountMinor,
        );
      }

      return saleId;
    });
  }

  /// Direct customer debt entry without deducting inventory (`تسجيل دين مباشر جديد`).
  Future<int> addDirectCustomerDebt({
    required int customerId,
    required String customerName,
    required int amountMinor,
    required CurrencyCode currency,
    String statement = 'دين مباشر',
    String referenceNumber = '',
    String notes = '',
    int? dateMs,
  }) async {
    if (amountMinor <= 0) throw ArgumentError('مبلغ الدين يجب أن يكون أكبر من صفر.');
    final db = await _db;
    final ts = dateMs ?? DateTime.now().millisecondsSinceEpoch;
    final iso = QatDateUtils.toIsoDate(DateTime.fromMillisecondsSinceEpoch(ts));
    final cleanNote = [
      if (statement.trim().isNotEmpty) statement.trim(),
      if (referenceNumber.trim().isNotEmpty) 'مرجع: ${referenceNumber.trim()}',
      if (notes.trim().isNotEmpty) notes.trim(),
    ].join(' — ');

    return db.transaction<int>((txn) async {
      final saleId = await txn.insert('sales', {
        'invoice_number': 'TMP-DEBT-$ts',
        'customer_id': customerId,
        'customer_name': customerName.trim(),
        'date_ms': ts,
        'date_iso': iso,
        'currency_code': currency.code,
        'subtotal_minor': amountMinor,
        'discount_minor': 0,
        'tax_bps': 0,
        'tax_amount_minor': 0,
        'total_amount_minor': amountMinor,
        'paid_amount_minor': 0,
        'remaining_amount_minor': amountMinor,
        'payment_type': PaymentType.credit.code,
        'status': 'دين مباشر',
        'payment_method': 'آجل',
        'notes': cleanNote,
        'is_archived': 0,
      });
      final invNo = 'DEBT-${iso.replaceAll('-', '')}-${saleId.toString().padLeft(4, '0')}';
      await txn.update('sales', {'invoice_number': invNo}, where: 'id = ?', whereArgs: [saleId]);
      await _adjustCustomerBalance(
        txn,
        customerId: customerId,
        currency: currency,
        deltaMinor: amountMinor,
      );
      return saleId;
    });
  }

  Future<void> editSale({
    required int saleId,
    int? customerId,
    required String customerName,
    required List<NewSaleItemInput> newItems,
    required CurrencyCode currency,
    int discountMinor = 0,
    int taxBps = 0,
    required PaymentType paymentType,
    int paidAmountMinor = 0,
    String paymentMethod = '',
    String notes = '',
  }) async {
    if (newItems.isEmpty) {
      throw ArgumentError('يجب وجود صنف واحد على الأقل في الفاتورة.');
    }
    if (paymentType != PaymentType.paid && customerId == null) {
      throw ArgumentError('الفواتير الآجلة أو الجزئية تتطلب اختيار عميل مسجل.');
    }

    final db = await _db;
    final lineTotals = newItems
        .map((it) => InvoiceMath.lineTotalMinor(
              qtyMilli: it.qtyMilli,
              unitPriceMinor: it.unitPriceMinor,
            ))
        .toList();

    final prelim = InvoiceMath.computeTotals(
      lineTotalsMinor: lineTotals,
      discountMinor: discountMinor,
      taxBps: taxBps,
      paidMinor: 0,
    );
    final effectivePaid = switch (paymentType) {
      PaymentType.paid => prelim.totalAmountMinor,
      PaymentType.credit => 0,
      PaymentType.partial => paidAmountMinor.clamp(0, prelim.totalAmountMinor).toInt(),
    };
    final totals = InvoiceMath.computeTotals(
      lineTotalsMinor: lineTotals,
      discountMinor: discountMinor,
      taxBps: taxBps,
      paidMinor: effectivePaid,
    );
    final normType = totals.remainingAmountMinor == 0
        ? PaymentType.paid
        : (totals.paidAmountMinor == 0 ? PaymentType.credit : PaymentType.partial);
    final effectiveMethod = paymentMethod.trim().isNotEmpty
        ? paymentMethod.trim()
        : normType.arabicLabel;

    await db.transaction<void>((txn) async {
      final rows = await txn.query('sales', where: 'id = ?', whereArgs: [saleId], limit: 1);
      if (rows.isEmpty) throw StateError('فاتورة المبيعات غير موجودة.');
      final old = rows.first;
      final isArchived = (old['is_archived'] as num).toInt() == 1;
      final oldCustId = (old['customer_id'] as num?)?.toInt();
      final oldCurrency = CurrencyCode.fromRaw(old['currency_code']?.toString());
      final oldRemaining = (old['remaining_amount_minor'] as num).toInt();

      // 1. Restore old inventory quantities
      final oldItems = await txn.query(
        'sales_invoice_items',
        where: 'sale_id = ?',
        whereArgs: [saleId],
      );
      for (final item in oldItems) {
        final batchId = (item['inventory_batch_id'] as num?)?.toInt();
        final qtyMilli = (item['qty_milli'] as num).toInt();
        if (batchId != null) {
          await txn.rawUpdate(
            'UPDATE inventory_batches SET remaining_qty_milli = remaining_qty_milli + ? WHERE id = ?',
            [qtyMilli, batchId],
          );
        }
      }

      // 2. Reverse old customer balance impact
      if (!isArchived && oldCustId != null && oldRemaining != 0) {
        await _adjustCustomerBalance(
          txn,
          customerId: oldCustId,
          currency: oldCurrency,
          deltaMinor: -oldRemaining,
        );
      }

      // 3. Validate and deduct new inventory quantities
      for (final item in newItems) {
        if (item.qtyMilli <= 0) {
          throw ArgumentError('الكمية يجب أن تكون أكبر من صفر.');
        }
        if (item.inventoryBatchId != null) {
          final batchRows = await txn.query(
            'inventory_batches',
            where: 'id = ?',
            whereArgs: [item.inventoryBatchId],
            limit: 1,
          );
          if (batchRows.isEmpty) {
            throw StateError('دفعة المخزون غير موجودة.');
          }
          final avail = (batchRows.first['remaining_qty_milli'] as num).toInt();
          if (avail < item.qtyMilli) {
            throw StateError(
              'الكمية المتوفرة من ${item.qatTypeName} (${QuantityMath.formatMilli(avail)}) أقل من المطلوب (${QuantityMath.formatMilli(item.qtyMilli)}).',
            );
          }
          await txn.update(
            'inventory_batches',
            {'remaining_qty_milli': avail - item.qtyMilli},
            where: 'id = ?',
            whereArgs: [item.inventoryBatchId],
          );
        }
      }

      // 4. Update sale header and replace line items
      await txn.update(
        'sales',
        {
          'customer_id': customerId,
          'customer_name': customerName.trim().isEmpty ? 'عميل نقدي' : customerName.trim(),
          'currency_code': currency.code,
          'subtotal_minor': totals.subtotalMinor,
          'discount_minor': totals.discountMinor,
          'tax_bps': totals.taxBps,
          'tax_amount_minor': totals.taxAmountMinor,
          'total_amount_minor': totals.totalAmountMinor,
          'paid_amount_minor': totals.paidAmountMinor,
          'remaining_amount_minor': totals.remainingAmountMinor,
          'payment_type': normType.code,
          'status': effectiveMethod,
          'payment_method': effectiveMethod,
          'notes': notes.trim(),
        },
        where: 'id = ?',
        whereArgs: [saleId],
      );

      await txn.delete('sales_invoice_items', where: 'sale_id = ?', whereArgs: [saleId]);
      for (int i = 0; i < newItems.length; i++) {
        final it = newItems[i];
        await txn.insert('sales_invoice_items', {
          'sale_id': saleId,
          'inventory_batch_id': it.inventoryBatchId,
          'qat_type_name': it.qatTypeName,
          'qty_milli': it.qtyMilli,
          'unit': it.unit,
          'unit_price_minor': it.unitPriceMinor,
          'buy_price_minor': it.buyPriceMinor,
          'line_total_minor': lineTotals[i],
          'currency_code': currency.code,
        });
      }

      // 5. Apply new customer debt delta
      if (!isArchived && customerId != null && totals.remainingAmountMinor != 0) {
        await _adjustCustomerBalance(
          txn,
          customerId: customerId,
          currency: currency,
          deltaMinor: totals.remainingAmountMinor,
        );
      }
    });
  }

  Future<void> deleteSale(int saleId) async {
    final db = await _db;
    await db.transaction<void>((txn) async {
      final rows = await txn.query('sales', where: 'id = ?', whereArgs: [saleId], limit: 1);
      if (rows.isEmpty) return;
      final sale = rows.first;
      final isArchived = (sale['is_archived'] as num).toInt() == 1;
      final customerId = (sale['customer_id'] as num?)?.toInt();
      final currency = CurrencyCode.fromRaw(sale['currency_code']?.toString());
      final remainingMinor = (sale['remaining_amount_minor'] as num).toInt();

      final items = await txn.query('sales_invoice_items', where: 'sale_id = ?', whereArgs: [saleId]);
      for (final item in items) {
        final batchId = (item['inventory_batch_id'] as num?)?.toInt();
        final qtyMilli = (item['qty_milli'] as num).toInt();
        if (batchId != null) {
          await txn.rawUpdate(
            'UPDATE inventory_batches SET remaining_qty_milli = remaining_qty_milli + ? WHERE id = ?',
            [qtyMilli, batchId],
          );
        }
      }

      if (!isArchived && customerId != null && remainingMinor != 0) {
        await _adjustCustomerBalance(
          txn,
          customerId: customerId,
          currency: currency,
          deltaMinor: -remainingMinor,
        );
      }

      await txn.delete('sales', where: 'id = ?', whereArgs: [saleId]);
    });
  }

  // ---------------------------------------------------------------------------
  // Customer Payments
  // ---------------------------------------------------------------------------

  Future<List<CustomerPayment>> getCustomerPayments({
    int? customerId,
    bool includeArchived = false,
  }) async {
    final db = await _db;
    final where = <String>[];
    final args = <Object?>[];
    if (!includeArchived) where.add('is_archived = 0');
    if (customerId != null) {
      where.add('customer_id = ?');
      args.add(customerId);
    }
    final rows = await db.query(
      'customer_payments',
      where: where.isEmpty ? null : where.join(' AND '),
      whereArgs: args,
      orderBy: 'date_ms DESC, id DESC',
    );
    return rows.map(_mapCustomerPayment).toList();
  }

  Future<int> addCustomerPayment({
    required int customerId,
    required String customerName,
    required int amountMinor,
    required CurrencyCode currency,
    String paymentMethod = 'نقدي',
    String notes = '',
    int? dateMs,
  }) async {
    if (amountMinor <= 0) throw ArgumentError('المبلغ يجب أن يكون أكبر من صفر.');
    final db = await _db;
    final ts = dateMs ?? DateTime.now().millisecondsSinceEpoch;
    final iso = QatDateUtils.toIsoDate(DateTime.fromMillisecondsSinceEpoch(ts));

    return db.transaction<int>((txn) async {
      final id = await txn.insert('customer_payments', {
        'customer_id': customerId,
        'customer_name': customerName.trim(),
        'amount_minor': amountMinor,
        'currency_code': currency.code,
        'date_ms': ts,
        'date_iso': iso,
        'payment_method': paymentMethod.trim().isEmpty ? 'نقدي' : paymentMethod.trim(),
        'notes': notes.trim(),
        'is_archived': 0,
      });
      await _adjustCustomerBalance(
        txn,
        customerId: customerId,
        currency: currency,
        deltaMinor: -amountMinor,
      );
      return id;
    });
  }

  Future<void> updateCustomerPayment({
    required int paymentId,
    required int amountMinor,
    required CurrencyCode currency,
    String paymentMethod = 'نقدي',
    String notes = '',
  }) async {
    if (amountMinor <= 0) throw ArgumentError('المبلغ يجب أن يكون أكبر من صفر.');
    final db = await _db;
    await db.transaction<void>((txn) async {
      final rows = await txn.query(
        'customer_payments',
        where: 'id = ?',
        whereArgs: [paymentId],
        limit: 1,
      );
      if (rows.isEmpty) throw StateError('سند القبض غير موجود.');
      final old = rows.first;
      final customerId = (old['customer_id'] as num).toInt();
      final isArchived = (old['is_archived'] as num).toInt() == 1;
      final oldCur = CurrencyCode.fromRaw(old['currency_code']?.toString());
      final oldAmt = (old['amount_minor'] as num).toInt();

      if (!isArchived) {
        await _adjustCustomerBalance(
          txn,
          customerId: customerId,
          currency: oldCur,
          deltaMinor: oldAmt,
        );
        await _adjustCustomerBalance(
          txn,
          customerId: customerId,
          currency: currency,
          deltaMinor: -amountMinor,
        );
      }

      await txn.update(
        'customer_payments',
        {
          'amount_minor': amountMinor,
          'currency_code': currency.code,
          'payment_method': paymentMethod.trim().isEmpty ? 'نقدي' : paymentMethod.trim(),
          'notes': notes.trim(),
        },
        where: 'id = ?',
        whereArgs: [paymentId],
      );
    });
  }

  Future<void> deleteCustomerPayment(int paymentId) async {
    final db = await _db;
    await db.transaction<void>((txn) async {
      final rows = await txn.query(
        'customer_payments',
        where: 'id = ?',
        whereArgs: [paymentId],
        limit: 1,
      );
      if (rows.isEmpty) return;
      final r = rows.first;
      final isArchived = (r['is_archived'] as num).toInt() == 1;
      if (!isArchived) {
        await _adjustCustomerBalance(
          txn,
          customerId: (r['customer_id'] as num).toInt(),
          currency: CurrencyCode.fromRaw(r['currency_code']?.toString()),
          deltaMinor: (r['amount_minor'] as num).toInt(),
        );
      }
      await txn.delete('customer_payments', where: 'id = ?', whereArgs: [paymentId]);
    });
  }

  // ---------------------------------------------------------------------------
  // Suppliers, Purchases & Payments
  // ---------------------------------------------------------------------------

  Future<List<Supplier>> getSuppliers({bool includeArchived = false}) async {
    final db = await _db;
    final rows = await db.query(
      'suppliers',
      where: includeArchived ? null : 'is_archived = 0',
      orderBy: 'name ASC',
    );
    final balRows = await db.query('supplier_balances');
    final balMap = <int, Map<CurrencyCode, int>>{};
    for (final b in balRows) {
      final sid = (b['supplier_id'] as num).toInt();
      final cur = CurrencyCode.fromRaw(b['currency_code']?.toString());
      final bal = (b['balance_minor'] as num).toInt();
      (balMap[sid] ??= {})[cur] = bal;
    }
    return rows
        .map((r) => _mapSupplier(r, balMap[(r['id'] as num).toInt()] ?? const {}))
        .toList();
  }

  Future<int> upsertSupplier({
    int? id,
    required String name,
    String phone = '',
    String address = '',
    String region = '',
    String notes = '',
  }) async {
    final db = await _db;
    final cleanName = name.trim();
    if (cleanName.isEmpty) throw ArgumentError('اسم المورد مطلوب');
    final data = {
      'name': cleanName,
      'phone': phone.trim(),
      'address': address.trim(),
      'region': region.trim(),
      'notes': notes.trim(),
    };
    if (id != null && id > 0) {
      await db.transaction<void>((txn) async {
        await txn.update('suppliers', data, where: 'id = ?', whereArgs: [id]);
        await txn.update('supplier_purchases', {'supplier_name': cleanName},
            where: 'supplier_id = ?', whereArgs: [id]);
        await txn.update('supplier_payments', {'supplier_name': cleanName},
            where: 'supplier_id = ?', whereArgs: [id]);
      });
      return id;
    }
    return db.insert('suppliers', {
      ...data,
      'created_at_ms': DateTime.now().millisecondsSinceEpoch,
      'is_archived': 0,
    });
  }

  Future<void> deleteSupplier(int id) async {
    final db = await _db;
    final hasPurchases = Sqflite.firstIntValue(
          await db.rawQuery('SELECT COUNT(*) FROM supplier_purchases WHERE supplier_id = ?', [id]),
        ) ??
        0;
    final hasPayments = Sqflite.firstIntValue(
          await db.rawQuery('SELECT COUNT(*) FROM supplier_payments WHERE supplier_id = ?', [id]),
        ) ??
        0;
    if (hasPurchases > 0 || hasPayments > 0) {
      throw StateError('لا يمكن حذف مورد لديه فواتير شراء أو سندات صرف مسجلة.');
    }
    await db.delete('suppliers', where: 'id = ?', whereArgs: [id]);
  }

  Future<List<SupplierPurchase>> getSupplierPurchases({
    bool includeArchived = false,
    int? supplierId,
  }) async {
    final db = await _db;
    final where = <String>[];
    final args = <Object?>[];
    if (!includeArchived) where.add('is_archived = 0');
    if (supplierId != null) {
      where.add('supplier_id = ?');
      args.add(supplierId);
    }
    final rows = await db.query(
      'supplier_purchases',
      where: where.isEmpty ? null : where.join(' AND '),
      whereArgs: args,
      orderBy: 'date_ms DESC, id DESC',
    );
    if (rows.isEmpty) return const [];
    final ids = rows.map((r) => (r['id'] as num).toInt()).toList();
    final placeholders = List.filled(ids.length, '?').join(',');
    final itemRows = await db.query(
      'supplier_purchase_items',
      where: 'purchase_id IN ($placeholders)',
      whereArgs: ids,
      orderBy: 'id ASC',
    );
    final itemsByPurchase = <int, List<SupplierPurchaseItem>>{};
    for (final ir in itemRows) {
      final item = _mapPurchaseItem(ir);
      (itemsByPurchase[item.purchaseId] ??= []).add(item);
    }
    return rows
        .map((r) => _mapPurchase(r, itemsByPurchase[(r['id'] as num).toInt()] ?? const []))
        .toList();
  }

  Future<int> makeSupplierPurchase({
    required int supplierId,
    required String supplierName,
    required List<NewPurchaseItemInput> items,
    required CurrencyCode currency,
    int discountMinor = 0,
    int taxBps = 0,
    required PaymentType paymentType,
    int paidAmountMinor = 0,
    String paymentMethod = '',
    String bankName = '',
    String transferNumber = '',
    int demarMinor = 0,
    String demarType = '',
    String demarNumber = '',
    String notes = '',
    bool addToInventory = true,
    int? dateMs,
  }) async {
    if (items.isEmpty) throw ArgumentError('يجب إضافة صنف واحد على الأقل.');
    final db = await _db;
    final ts = dateMs ?? DateTime.now().millisecondsSinceEpoch;
    final iso = QatDateUtils.toIsoDate(DateTime.fromMillisecondsSinceEpoch(ts));

    final lineTotals = items
        .map((it) => InvoiceMath.lineTotalMinor(
              qtyMilli: it.qtyMilli,
              unitPriceMinor: it.buyPriceMinor,
            ))
        .toList();
    final prelim = InvoiceMath.computeTotals(
      lineTotalsMinor: lineTotals,
      discountMinor: discountMinor,
      taxBps: taxBps,
      paidMinor: 0,
    );
    final effectivePaid = switch (paymentType) {
      PaymentType.paid => paidAmountMinor > prelim.totalAmountMinor ? paidAmountMinor : prelim.totalAmountMinor,
      PaymentType.credit => 0,
      PaymentType.partial => paidAmountMinor.clamp(0, 1 << 60).toInt(),
    };
    final totals = InvoiceMath.computeTotals(
      lineTotalsMinor: lineTotals,
      discountMinor: discountMinor,
      taxBps: taxBps,
      paidMinor: effectivePaid.clamp(0, prelim.totalAmountMinor).toInt(),
    );
    final remainingWithSurplus = totals.totalAmountMinor - effectivePaid;
    final normType = remainingWithSurplus <= 0
        ? PaymentType.paid
        : (effectivePaid == 0 ? PaymentType.credit : PaymentType.partial);
    final effectiveMethod = paymentMethod.trim().isNotEmpty
        ? paymentMethod.trim()
        : normType.arabicLabel;

    return db.transaction<int>((txn) async {
      final purchaseId = await txn.insert('supplier_purchases', {
        'purchase_number': 'TMP-$ts',
        'supplier_id': supplierId,
        'supplier_name': supplierName.trim(),
        'date_ms': ts,
        'date_iso': iso,
        'currency_code': currency.code,
        'subtotal_minor': totals.subtotalMinor,
        'discount_minor': totals.discountMinor,
        'tax_bps': totals.taxBps,
        'tax_amount_minor': totals.taxAmountMinor,
        'total_amount_minor': totals.totalAmountMinor,
        'paid_amount_minor': effectivePaid,
        'remaining_amount_minor': remainingWithSurplus,
        'payment_type': normType.code,
        'notes': notes.trim(),
        'is_archived': 0,
        'payment_method': effectiveMethod,
        'bank_name': bankName.trim(),
        'transfer_number': transferNumber.trim(),
        'demar_minor': demarMinor.clamp(0, 1 << 60).toInt(),
        'demar_type': demarType.trim(),
        'demar_number': demarNumber.trim(),
      });

      final purNo = 'PUR-${iso.replaceAll('-', '')}-${purchaseId.toString().padLeft(4, '0')}';
      await txn.update(
        'supplier_purchases',
        {'purchase_number': purNo},
        where: 'id = ?',
        whereArgs: [purchaseId],
      );

      for (int i = 0; i < items.length; i++) {
        final it = items[i];
        await txn.insert('supplier_purchase_items', {
          'purchase_id': purchaseId,
          'qat_type_name': it.qatTypeName.trim(),
          'qty_milli': it.qtyMilli,
          'unit': it.unit,
          'buy_price_minor': it.buyPriceMinor,
          'line_total_minor': lineTotals[i],
          'currency_code': currency.code,
        });

        if (addToInventory) {
          await txn.insert('inventory_batches', {
            'qat_type_id': null,
            'qat_type_name': it.qatTypeName.trim(),
            'supplier_id': supplierId,
            'supplier_name': supplierName.trim(),
            'remaining_qty_milli': it.qtyMilli,
            'initial_qty_milli': it.qtyMilli,
            'unit': it.unit,
            'buy_price_minor': it.buyPriceMinor,
            'sell_price_minor': it.sellPriceMinor > 0 ? it.sellPriceMinor : it.buyPriceMinor,
            'currency_code': currency.code,
            'purchase_date_ms': ts,
            'purchase_date_iso': iso,
            'is_archived': 0,
          });
        }
      }

      if (remainingWithSurplus != 0) {
        await _adjustSupplierBalance(
          txn,
          supplierId: supplierId,
          currency: currency,
          deltaMinor: remainingWithSurplus,
        );
      }

      return purchaseId;
    });
  }

  /// Supplier return voucher (`سند مرتجع لمورد`): deducts returned quantity (if batch linked) and reduces supplier payable.
  Future<int> addSupplierReturn({
    required int supplierId,
    required String supplierName,
    required int amountMinor,
    required CurrencyCode currency,
    int? inventoryBatchId,
    int returnedQtyMilli = 0,
    String itemName = '',
    String notes = '',
    int? dateMs,
  }) async {
    if (amountMinor <= 0) throw ArgumentError('مبلغ المرتجع يجب أن يكون أكبر من صفر.');
    final db = await _db;
    final ts = dateMs ?? DateTime.now().millisecondsSinceEpoch;
    final iso = QatDateUtils.toIsoDate(DateTime.fromMillisecondsSinceEpoch(ts));

    return db.transaction<int>((txn) async {
      if (inventoryBatchId != null && returnedQtyMilli > 0) {
        final rows = await txn.query(
          'inventory_batches',
          where: 'id = ?',
          whereArgs: [inventoryBatchId],
          limit: 1,
        );
        if (rows.isNotEmpty) {
          final curQty = (rows.first['remaining_qty_milli'] as num).toInt();
          final nextQty = (curQty - returnedQtyMilli).clamp(0, 1 << 60).toInt();
          await txn.update(
            'inventory_batches',
            {'remaining_qty_milli': nextQty},
            where: 'id = ?',
            whereArgs: [inventoryBatchId],
          );
        }
      }
      final detail = [
        'سند مرتجع بضاعة للمورد',
        if (itemName.trim().isNotEmpty) 'الصنف: ${itemName.trim()}',
        if (returnedQtyMilli > 0) 'الكمية المرتجعة: ${QuantityMath.formatMilli(returnedQtyMilli)}',
        if (notes.trim().isNotEmpty) notes.trim(),
      ].join(' — ');
      final id = await txn.insert('supplier_payments', {
        'supplier_id': supplierId,
        'supplier_name': supplierName.trim(),
        'amount_minor': amountMinor,
        'currency_code': currency.code,
        'date_ms': ts,
        'date_iso': iso,
        'payment_method': 'مرتجع',
        'notes': detail,
        'is_archived': 0,
      });
      await _adjustSupplierBalance(
        txn,
        supplierId: supplierId,
        currency: currency,
        deltaMinor: -amountMinor,
      );
      return id;
    });
  }

  /// Fixes both legacy Kotlin bugs in `editSupplierPurchaseFull`:
  /// 1. Normalizes PaymentType unambiguously (`PAID` / `PARTIAL` / `CREDIT`).
  /// 2. Updates `buy_price_minor` AND `remaining_qty_milli` cleanly on linked inventory batches.
  Future<void> editSupplierPurchase({
    required int purchaseId,
    required List<NewPurchaseItemInput> newItems,
    required CurrencyCode currency,
    int discountMinor = 0,
    int taxBps = 0,
    required PaymentType paymentType,
    int paidAmountMinor = 0,
    String paymentMethod = '',
    String bankName = '',
    String transferNumber = '',
    int demarMinor = 0,
    String demarType = '',
    String demarNumber = '',
    String notes = '',
  }) async {
    if (newItems.isEmpty) throw ArgumentError('يجب وجود صنف واحد على الأقل.');
    final db = await _db;

    await db.transaction<void>((txn) async {
      final rows = await txn.query(
        'supplier_purchases',
        where: 'id = ?',
        whereArgs: [purchaseId],
        limit: 1,
      );
      if (rows.isEmpty) throw StateError('فاتورة الشراء غير موجودة.');
      final old = rows.first;
      final supplierId = (old['supplier_id'] as num).toInt();
      final supplierName = (old['supplier_name'] ?? '').toString();
      final oldCurrency = CurrencyCode.fromRaw(old['currency_code']?.toString());
      final oldRemaining = (old['remaining_amount_minor'] as num).toInt();
      final oldDateMs = (old['date_ms'] as num).toInt();
      final oldIso = (old['date_iso'] ?? '').toString();

      // Reverse old balance effect
      if (oldRemaining != 0) {
        await _adjustSupplierBalance(
          txn,
          supplierId: supplierId,
          currency: oldCurrency,
          deltaMinor: -oldRemaining,
        );
      }

      final lineTotals = newItems
          .map((it) => InvoiceMath.lineTotalMinor(
                qtyMilli: it.qtyMilli,
                unitPriceMinor: it.buyPriceMinor,
              ))
          .toList();
      final prelim = InvoiceMath.computeTotals(
        lineTotalsMinor: lineTotals,
        discountMinor: discountMinor,
        taxBps: taxBps,
        paidMinor: 0,
      );
      final effectivePaid = switch (paymentType) {
        PaymentType.paid => paidAmountMinor > prelim.totalAmountMinor ? paidAmountMinor : prelim.totalAmountMinor,
        PaymentType.credit => 0,
        PaymentType.partial => paidAmountMinor.clamp(0, 1 << 60).toInt(),
      };
      final totals = InvoiceMath.computeTotals(
        lineTotalsMinor: lineTotals,
        discountMinor: discountMinor,
        taxBps: taxBps,
        paidMinor: effectivePaid.clamp(0, prelim.totalAmountMinor).toInt(),
      );
      final remainingWithSurplus = totals.totalAmountMinor - effectivePaid;
      final normType = remainingWithSurplus <= 0
          ? PaymentType.paid
          : (effectivePaid == 0 ? PaymentType.credit : PaymentType.partial);
      final effectiveMethod = paymentMethod.trim().isNotEmpty
          ? paymentMethod.trim()
          : normType.arabicLabel;

      await txn.update(
        'supplier_purchases',
        {
          'currency_code': currency.code,
          'subtotal_minor': totals.subtotalMinor,
          'discount_minor': totals.discountMinor,
          'tax_bps': totals.taxBps,
          'tax_amount_minor': totals.taxAmountMinor,
          'total_amount_minor': totals.totalAmountMinor,
          'paid_amount_minor': effectivePaid,
          'remaining_amount_minor': remainingWithSurplus,
          'payment_type': normType.code,
          'notes': notes.trim(),
          'payment_method': effectiveMethod,
          'bank_name': bankName.trim(),
          'transfer_number': transferNumber.trim(),
          'demar_minor': demarMinor.clamp(0, 1 << 60).toInt(),
          'demar_type': demarType.trim(),
          'demar_number': demarNumber.trim(),
        },
        where: 'id = ?',
        whereArgs: [purchaseId],
      );

      final oldItems = await txn.query(
        'supplier_purchase_items',
        where: 'purchase_id = ?',
        whereArgs: [purchaseId],
      );
      await txn.delete('supplier_purchase_items', where: 'purchase_id = ?', whereArgs: [purchaseId]);

      for (int i = 0; i < newItems.length; i++) {
        final it = newItems[i];
        await txn.insert('supplier_purchase_items', {
          'purchase_id': purchaseId,
          'qat_type_name': it.qatTypeName.trim(),
          'qty_milli': it.qtyMilli,
          'unit': it.unit,
          'buy_price_minor': it.buyPriceMinor,
          'line_total_minor': lineTotals[i],
          'currency_code': currency.code,
        });

        // Update matching inventory batch if created at purchase timestamp
        final matchedBatch = await txn.query(
          'inventory_batches',
          where: 'supplier_id = ? AND purchase_date_ms = ? AND qat_type_name = ?',
          whereArgs: [supplierId, oldDateMs, it.qatTypeName.trim()],
          limit: 1,
        );
        if (matchedBatch.isNotEmpty) {
          final b = matchedBatch.first;
          final oldBatchQty = (b['initial_qty_milli'] as num).toInt();
          final oldRemainingQty = (b['remaining_qty_milli'] as num).toInt();
          final soldQty = (oldBatchQty - oldRemainingQty).clamp(0, 1 << 60).toInt();
          final newRemainingQty = (it.qtyMilli - soldQty).clamp(0, 1 << 60).toInt();
          await txn.update(
            'inventory_batches',
            {
              'remaining_qty_milli': newRemainingQty,
              'initial_qty_milli': it.qtyMilli,
              'buy_price_minor': it.buyPriceMinor,
              'currency_code': currency.code,
              if (it.sellPriceMinor > 0) 'sell_price_minor': it.sellPriceMinor,
            },
            where: 'id = ?',
            whereArgs: [b['id']],
          );
        } else if (i >= oldItems.length) {
          await txn.insert('inventory_batches', {
            'qat_type_id': null,
            'qat_type_name': it.qatTypeName.trim(),
            'supplier_id': supplierId,
            'supplier_name': supplierName,
            'remaining_qty_milli': it.qtyMilli,
            'initial_qty_milli': it.qtyMilli,
            'unit': it.unit,
            'buy_price_minor': it.buyPriceMinor,
            'sell_price_minor': it.sellPriceMinor > 0 ? it.sellPriceMinor : it.buyPriceMinor,
            'currency_code': currency.code,
            'purchase_date_ms': oldDateMs,
            'purchase_date_iso': oldIso,
            'is_archived': 0,
          });
        }
      }

      if (remainingWithSurplus != 0) {
        await _adjustSupplierBalance(
          txn,
          supplierId: supplierId,
          currency: currency,
          deltaMinor: remainingWithSurplus,
        );
      }
    });
  }

  Future<void> deleteSupplierPurchase(int purchaseId) async {
    final db = await _db;
    await db.transaction<void>((txn) async {
      final rows = await txn.query(
        'supplier_purchases',
        where: 'id = ?',
        whereArgs: [purchaseId],
        limit: 1,
      );
      if (rows.isEmpty) return;
      final r = rows.first;
      final isArchived = (r['is_archived'] as num).toInt() == 1;
      if (!isArchived) {
        await _adjustSupplierBalance(
          txn,
          supplierId: (r['supplier_id'] as num).toInt(),
          currency: CurrencyCode.fromRaw(r['currency_code']?.toString()),
          deltaMinor: -(r['remaining_amount_minor'] as num).toInt(),
        );
      }
      await txn.delete('supplier_purchases', where: 'id = ?', whereArgs: [purchaseId]);
    });
  }

  Future<List<SupplierPayment>> getSupplierPayments({
    int? supplierId,
    bool includeArchived = false,
  }) async {
    final db = await _db;
    final where = <String>[];
    final args = <Object?>[];
    if (!includeArchived) where.add('is_archived = 0');
    if (supplierId != null) {
      where.add('supplier_id = ?');
      args.add(supplierId);
    }
    final rows = await db.query(
      'supplier_payments',
      where: where.isEmpty ? null : where.join(' AND '),
      whereArgs: args,
      orderBy: 'date_ms DESC, id DESC',
    );
    return rows.map(_mapSupplierPayment).toList();
  }

  Future<int> addSupplierPayment({
    required int supplierId,
    required String supplierName,
    required int amountMinor,
    required CurrencyCode currency,
    String paymentMethod = 'نقدي',
    String bankName = '',
    String transferNumber = '',
    String notes = '',
    int? dateMs,
  }) async {
    if (amountMinor <= 0) throw ArgumentError('المبلغ يجب أن يكون أكبر من صفر.');
    final db = await _db;
    final ts = dateMs ?? DateTime.now().millisecondsSinceEpoch;
    final iso = QatDateUtils.toIsoDate(DateTime.fromMillisecondsSinceEpoch(ts));
    final mergedNotes = [
      if (bankName.trim().isNotEmpty) 'البنك/الصرافة: ${bankName.trim()}',
      if (transferNumber.trim().isNotEmpty) 'رقم الحوالة: ${transferNumber.trim()}',
      if (notes.trim().isNotEmpty) notes.trim(),
    ].join(' — ');

    return db.transaction<int>((txn) async {
      final id = await txn.insert('supplier_payments', {
        'supplier_id': supplierId,
        'supplier_name': supplierName.trim(),
        'amount_minor': amountMinor,
        'currency_code': currency.code,
        'date_ms': ts,
        'date_iso': iso,
        'payment_method': paymentMethod.trim().isEmpty ? 'نقدي' : paymentMethod.trim(),
        'notes': mergedNotes,
        'is_archived': 0,
      });
      await _adjustSupplierBalance(
        txn,
        supplierId: supplierId,
        currency: currency,
        deltaMinor: -amountMinor,
      );
      return id;
    });
  }

  Future<void> deleteSupplierPayment(int paymentId) async {
    final db = await _db;
    await db.transaction<void>((txn) async {
      final rows = await txn.query(
        'supplier_payments',
        where: 'id = ?',
        whereArgs: [paymentId],
        limit: 1,
      );
      if (rows.isEmpty) return;
      final r = rows.first;
      final isArchived = (r['is_archived'] as num).toInt() == 1;
      if (!isArchived) {
        await _adjustSupplierBalance(
          txn,
          supplierId: (r['supplier_id'] as num).toInt(),
          currency: CurrencyCode.fromRaw(r['currency_code']?.toString()),
          deltaMinor: (r['amount_minor'] as num).toInt(),
        );
      }
      await txn.delete('supplier_payments', where: 'id = ?', whereArgs: [paymentId]);
    });
  }

  // ---------------------------------------------------------------------------
  // Expenses, Ledger Accounts & Tax Entries
  // ---------------------------------------------------------------------------

  Future<List<LedgerAccount>> getLedgerAccounts() async {
    final db = await _db;
    final rows = await db.query('ledger_accounts', orderBy: 'is_system DESC, name ASC');
    return rows.map(_mapLedgerAccount).toList();
  }

  Future<int> upsertLedgerAccount({
    int? id,
    required String code,
    required String name,
    required String category,
    bool isActive = true,
  }) async {
    final db = await _db;
    final cleanName = name.trim();
    if (cleanName.isEmpty) throw ArgumentError('اسم الحساب مطلوب');
    final data = {
      'code': code.trim().isEmpty ? 'ACC_${DateTime.now().millisecondsSinceEpoch}' : code.trim(),
      'name': cleanName,
      'category': category.trim().isEmpty ? 'مصروفات عامة' : category.trim(),
      'is_active': isActive ? 1 : 0,
    };
    if (id != null && id > 0) {
      await db.update('ledger_accounts', data, where: 'id = ?', whereArgs: [id]);
      return id;
    }
    return db.insert('ledger_accounts', {...data, 'is_system': 0});
  }

  Future<List<Expense>> getExpenses({
    bool includeArchived = false,
    String? fromIso,
    String? toIso,
  }) async {
    final db = await _db;
    final where = <String>[];
    final args = <Object?>[];
    if (!includeArchived) where.add('is_archived = 0');
    if (fromIso != null && fromIso.isNotEmpty) {
      where.add('date_iso >= ?');
      args.add(fromIso);
    }
    if (toIso != null && toIso.isNotEmpty) {
      where.add('date_iso <= ?');
      args.add(toIso);
    }
    final rows = await db.query(
      'expenses',
      where: where.isEmpty ? null : where.join(' AND '),
      whereArgs: args,
      orderBy: 'date_ms DESC, id DESC',
    );
    return rows.map(_mapExpense).toList();
  }

  Future<int> addExpense({
    required String description,
    required String category,
    int? accountId,
    required int amountMinor,
    required CurrencyCode currency,
    String notes = '',
    int? dateMs,
  }) async {
    final db = await _db;
    final ts = dateMs ?? DateTime.now().millisecondsSinceEpoch;
    final iso = QatDateUtils.toIsoDate(DateTime.fromMillisecondsSinceEpoch(ts));
    return db.insert('expenses', {
      'description': description.trim(),
      'category': category.trim().isEmpty ? 'مصروفات عامة' : category.trim(),
      'account_id': accountId,
      'amount_minor': amountMinor.clamp(0, 1 << 60).toInt(),
      'currency_code': currency.code,
      'date_ms': ts,
      'date_iso': iso,
      'notes': notes.trim(),
      'is_archived': 0,
    });
  }

  Future<void> updateExpense({
    required int id,
    required String description,
    required String category,
    int? accountId,
    required int amountMinor,
    required CurrencyCode currency,
    String notes = '',
  }) async {
    final db = await _db;
    await db.update(
      'expenses',
      {
        'description': description.trim(),
        'category': category.trim().isEmpty ? 'مصروفات عامة' : category.trim(),
        'account_id': accountId,
        'amount_minor': amountMinor.clamp(0, 1 << 60).toInt(),
        'currency_code': currency.code,
        'notes': notes.trim(),
      },
      where: 'id = ?',
      whereArgs: [id],
    );
  }

  Future<void> deleteExpense(int id) async {
    final db = await _db;
    await db.delete('expenses', where: 'id = ?', whereArgs: [id]);
  }

  Future<List<QatTaxEntry>> getTaxEntries({
    bool includeArchived = false,
    String? fromIso,
    String? toIso,
  }) async {
    final db = await _db;
    final where = <String>[];
    final args = <Object?>[];
    if (!includeArchived) where.add('is_archived = 0');
    if (fromIso != null && fromIso.isNotEmpty) {
      where.add('date_iso >= ?');
      args.add(fromIso);
    }
    if (toIso != null && toIso.isNotEmpty) {
      where.add('date_iso <= ?');
      args.add(toIso);
    }
    final rows = await db.query(
      'qat_tax_entries',
      where: where.isEmpty ? null : where.join(' AND '),
      whereArgs: args,
      orderBy: 'date_ms DESC, id DESC',
    );
    return rows.map(_mapTaxEntry).toList();
  }

  Future<int> addTaxEntry({
    required int totalQtyMilli,
    required int taxRateMinor,
    required int taxAmountMinor,
    required CurrencyCode currency,
    int stallOutflowMinor = 0,
    int laborOutflowMinor = 0,
    String source = 'يدوي',
    String notes = '',
    int? dateMs,
  }) async {
    final db = await _db;
    final ts = dateMs ?? DateTime.now().millisecondsSinceEpoch;
    final iso = QatDateUtils.toIsoDate(DateTime.fromMillisecondsSinceEpoch(ts));
    return db.insert('qat_tax_entries', {
      'date_ms': ts,
      'date_iso': iso,
      'total_qty_milli': totalQtyMilli.clamp(0, 1 << 60).toInt(),
      'tax_rate_minor': taxRateMinor.clamp(0, 1 << 60).toInt(),
      'tax_amount_minor': taxAmountMinor.clamp(0, 1 << 60).toInt(),
      'stall_outflow_minor': stallOutflowMinor.clamp(0, 1 << 60).toInt(),
      'labor_outflow_minor': laborOutflowMinor.clamp(0, 1 << 60).toInt(),
      'currency_code': currency.code,
      'source': source,
      'notes': notes.trim(),
      'is_archived': 0,
    });
  }

  Future<void> updateTaxEntry({
    required int id,
    required int taxAmountMinor,
    int stallOutflowMinor = 0,
    int laborOutflowMinor = 0,
    String notes = '',
  }) async {
    final db = await _db;
    await db.update(
      'qat_tax_entries',
      {
        'tax_amount_minor': taxAmountMinor.clamp(0, 1 << 60).toInt(),
        'stall_outflow_minor': stallOutflowMinor.clamp(0, 1 << 60).toInt(),
        'labor_outflow_minor': laborOutflowMinor.clamp(0, 1 << 60).toInt(),
        'notes': notes.trim(),
      },
      where: 'id = ?',
      whereArgs: [id],
    );
  }

  Future<void> deleteTaxEntry(int id) async {
    final db = await _db;
    await db.delete('qat_tax_entries', where: 'id = ?', whereArgs: [id]);
  }

  // ---------------------------------------------------------------------------
  // Transfer Recipients & Money Transfers (Strict Independent Reports)
  // ---------------------------------------------------------------------------

  Future<List<TransferRecipient>> getTransferRecipients() async {
    final db = await _db;
    final rows = await db.query('transfer_recipients', orderBy: 'name ASC');
    return rows.map(_mapRecipient).toList();
  }

  Future<int> upsertTransferRecipient({
    int? id,
    required String name,
    String phone = '',
    String company = '',
    String notes = '',
    bool isActive = true,
  }) async {
    final db = await _db;
    final cleanName = name.trim();
    if (cleanName.isEmpty) throw ArgumentError('اسم المستلم مطلوب');
    final data = {
      'name': cleanName,
      'phone': phone.trim(),
      'company': company.trim(),
      'notes': notes.trim(),
      'is_active': isActive ? 1 : 0,
    };
    if (id != null && id > 0) {
      await db.transaction<void>((txn) async {
        await txn.update('transfer_recipients', data, where: 'id = ?', whereArgs: [id]);
        await txn.update('money_transfers', {'receiver_name': cleanName},
            where: 'recipient_id = ?', whereArgs: [id]);
      });
      return id;
    }
    return db.insert('transfer_recipients', {
      ...data,
      'created_at_ms': DateTime.now().millisecondsSinceEpoch,
    });
  }

  Future<void> setTransferRecipientActive(int id, bool isActive) async {
    final db = await _db;
    await db.update(
      'transfer_recipients',
      {'is_active': isActive ? 1 : 0},
      where: 'id = ?',
      whereArgs: [id],
    );
  }

  Future<void> deleteTransferRecipient(int id) async {
    final db = await _db;
    await db.delete('transfer_recipients', where: 'id = ?', whereArgs: [id]);
  }

  Future<List<MoneyTransfer>> getTransfers({
    bool includeArchived = false,
    int? recipientId,
    bool onlyUnlinked = false,
    CurrencyCode? currency,
    TransferStatus? status,
    String? fromIso,
    String? toIso,
  }) async {
    final db = await _db;
    final where = <String>[];
    final args = <Object?>[];
    if (!includeArchived) where.add('is_archived = 0');
    if (onlyUnlinked) {
      where.add('recipient_id IS NULL');
    } else if (recipientId != null) {
      where.add('recipient_id = ?');
      args.add(recipientId);
    }
    if (currency != null) {
      where.add('currency_code = ?');
      args.add(currency.code);
    }
    if (status != null) {
      where.add('status_code = ?');
      args.add(status.code);
    }
    if (fromIso != null && fromIso.isNotEmpty) {
      where.add('date_iso >= ?');
      args.add(fromIso);
    }
    if (toIso != null && toIso.isNotEmpty) {
      where.add('date_iso <= ?');
      args.add(toIso);
    }
    final rows = await db.query(
      'money_transfers',
      where: where.isEmpty ? null : where.join(' AND '),
      whereArgs: args,
      orderBy: 'date_ms DESC, id DESC',
    );
    return rows.map(_mapTransfer).toList();
  }

  Future<int> addTransfer({
    required String senderName,
    String receiverName = '',
    int? recipientId,
    required int amountMinor,
    int feeMinor = 0,
    required CurrencyCode currency,
    String transferCompany = '',
    String transferNumber = '',
    TransferStatus status = TransferStatus.pending,
    String notes = '',
    int? dateMs,
  }) async {
    final db = await _db;
    final ts = dateMs ?? DateTime.now().millisecondsSinceEpoch;
    final iso = QatDateUtils.toIsoDate(DateTime.fromMillisecondsSinceEpoch(ts));

    // Only keep valid positive recipientId; never store synthetic 0!
    final int? safeRecipientId = (recipientId != null && recipientId > 0) ? recipientId : null;
    String resolvedReceiver = receiverName.trim();
    if (safeRecipientId != null && resolvedReceiver.isEmpty) {
      final recRows = await db.query(
        'transfer_recipients',
        columns: ['name'],
        where: 'id = ?',
        whereArgs: [safeRecipientId],
        limit: 1,
      );
      if (recRows.isNotEmpty) {
        resolvedReceiver = (recRows.first['name'] ?? '').toString();
      }
    }

    return db.insert('money_transfers', {
      'sender_name': senderName.trim().isEmpty ? 'الوكالة' : senderName.trim(),
      'receiver_name': resolvedReceiver,
      'recipient_id': safeRecipientId,
      'amount_minor': amountMinor.clamp(0, 1 << 60).toInt(),
      'fee_minor': feeMinor.clamp(0, 1 << 60).toInt(),
      'currency_code': currency.code,
      'transfer_company': transferCompany.trim(),
      'transfer_number': transferNumber.trim(),
      'date_ms': ts,
      'date_iso': iso,
      'status_code': status.code,
      'status_label': status.arabicLabel,
      'notes': notes.trim(),
      'is_archived': 0,
    });
  }

  Future<void> updateTransferStatus(int transferId, TransferStatus status) async {
    final db = await _db;
    await db.update(
      'money_transfers',
      {
        'status_code': status.code,
        'status_label': status.arabicLabel,
      },
      where: 'id = ?',
      whereArgs: [transferId],
    );
  }

  Future<void> updateTransfer({
    required int id,
    required String senderName,
    required String receiverName,
    int? recipientId,
    required int amountMinor,
    int feeMinor = 0,
    required CurrencyCode currency,
    String transferCompany = '',
    String transferNumber = '',
    required TransferStatus status,
    String notes = '',
  }) async {
    final db = await _db;
    final int? safeRecId = (recipientId != null && recipientId > 0) ? recipientId : null;
    await db.update(
      'money_transfers',
      {
        'sender_name': senderName.trim().isEmpty ? 'الوكالة' : senderName.trim(),
        'receiver_name': receiverName.trim(),
        'recipient_id': safeRecId,
        'amount_minor': amountMinor.clamp(0, 1 << 60).toInt(),
        'fee_minor': feeMinor.clamp(0, 1 << 60).toInt(),
        'currency_code': currency.code,
        'transfer_company': transferCompany.trim(),
        'transfer_number': transferNumber.trim(),
        'status_code': status.code,
        'status_label': status.arabicLabel,
        'notes': notes.trim(),
      },
      where: 'id = ?',
      whereArgs: [id],
    );
  }

  Future<void> linkTransferRecipient({
    required int transferId,
    int? recipientId,
    String? receiverName,
  }) async {
    final db = await _db;
    final int? safeRecId = (recipientId != null && recipientId > 0) ? recipientId : null;
    String resolvedName = (receiverName ?? '').trim();
    if (safeRecId != null && resolvedName.isEmpty) {
      final rows = await db.query(
        'transfer_recipients',
        columns: ['name'],
        where: 'id = ?',
        whereArgs: [safeRecId],
        limit: 1,
      );
      if (rows.isNotEmpty) {
        resolvedName = (rows.first['name'] ?? '').toString();
      }
    }
    await db.update(
      'money_transfers',
      {
        'recipient_id': safeRecId,
        if (resolvedName.isNotEmpty) 'receiver_name': resolvedName,
      },
      where: 'id = ?',
      whereArgs: [transferId],
    );
  }

  Future<void> deleteTransfer(int id) async {
    final db = await _db;
    await db.delete('money_transfers', where: 'id = ?', whereArgs: [id]);
  }

  Future<TransferRecipientSummary> getRecipientReport(
    int recipientId, {
    String? fromIso,
    String? toIso,
  }) async {
    final db = await _db;
    final recRows = await db.query(
      'transfer_recipients',
      where: 'id = ?',
      whereArgs: [recipientId],
      limit: 1,
    );
    if (recRows.isEmpty) throw StateError('المستلم غير موجود.');
    final recipient = _mapRecipient(recRows.first);
    final transfers = await getTransfers(
      recipientId: recipientId,
      fromIso: fromIso,
      toIso: toIso,
    );
    return _buildRecipientSummary(recipient, transfers);
  }

  Future<List<TransferRecipientSummary>> getAllRecipientsReports({
    String? fromIso,
    String? toIso,
  }) async {
    final recipients = await getTransferRecipients();
    final allTransfers = await getTransfers(fromIso: fromIso, toIso: toIso);
    final grouped = <int, List<MoneyTransfer>>{};
    for (final t in allTransfers) {
      if (t.recipientId != null) {
        (grouped[t.recipientId!] ??= []).add(t);
      }
    }
    return recipients
        .map((r) => _buildRecipientSummary(r, grouped[r.id] ?? const []))
        .toList();
  }

  TransferRecipientSummary _buildRecipientSummary(
    TransferRecipient recipient,
    List<MoneyTransfer> transfers,
  ) {
    final deliveredByCur = <CurrencyCode, int>{};
    final pendingByCur = <CurrencyCode, int>{};
    final cancelledByCur = <CurrencyCode, int>{};
    final feesByCur = <CurrencyCode, int>{};
    int deliveredCount = 0;
    int pendingCount = 0;
    int cancelledCount = 0;

    for (final t in transfers) {
      switch (t.status) {
        case TransferStatus.delivered:
          deliveredCount++;
          deliveredByCur[t.currency] = (deliveredByCur[t.currency] ?? 0) + t.amountMinor;
          feesByCur[t.currency] = (feesByCur[t.currency] ?? 0) + t.feeMinor;
        case TransferStatus.pending:
          pendingCount++;
          pendingByCur[t.currency] = (pendingByCur[t.currency] ?? 0) + t.amountMinor;
          feesByCur[t.currency] = (feesByCur[t.currency] ?? 0) + t.feeMinor;
        case TransferStatus.cancelled:
          cancelledCount++;
          cancelledByCur[t.currency] = (cancelledByCur[t.currency] ?? 0) + t.amountMinor;
      }
    }

    return TransferRecipientSummary(
      recipient: recipient,
      totalTransfers: transfers.length,
      deliveredCount: deliveredCount,
      pendingCount: pendingCount,
      cancelledCount: cancelledCount,
      deliveredByCurrency: deliveredByCur,
      pendingByCurrency: pendingByCur,
      cancelledByCurrency: cancelledByCur,
      feesByCurrency: feesByCur,
      transfers: transfers,
    );
  }

  // ---------------------------------------------------------------------------
  // Dashboard, Reports & Statements (Zero Currency Mixing)
  // ---------------------------------------------------------------------------

  Future<Map<CurrencyCode, CurrencyDashboardMetrics>> getDashboardMetrics({
    String? fromIso,
    String? toIso,
  }) async {
    final sales = await getSales(fromIso: fromIso, toIso: toIso);
    final custPayments = await getCustomerPayments();
    final purchases = await getSupplierPurchases();
    final supPayments = await getSupplierPayments();
    final expenses = await getExpenses(fromIso: fromIso, toIso: toIso);
    final taxes = await getTaxEntries(fromIso: fromIso, toIso: toIso);
    final transfers = await getTransfers(fromIso: fromIso, toIso: toIso);
    final customers = await getCustomers();
    final suppliers = await getSuppliers();

    bool inDateRange(String iso) {
      if (fromIso != null && fromIso.isNotEmpty && iso.compareTo(fromIso) < 0) return false;
      if (toIso != null && toIso.isNotEmpty && iso.compareTo(toIso) > 0) return false;
      return true;
    }

    final result = <CurrencyCode, CurrencyDashboardMetrics>{};
    for (final cur in CurrencyCode.values) {
      int salesTotal = 0;
      int salesPaid = 0;
      int salesCredit = 0;
      int salesCashOnly = 0;
      int salesTransfer = 0;
      int salesDeposit = 0;
      int totalSoldQtyMilli = 0;
      int cogs = 0;
      for (final s in sales.where((e) => e.currency == cur)) {
        salesTotal += s.totalAmountMinor;
        salesPaid += s.paidAmountMinor;
        salesCredit += s.remainingAmountMinor;
        final pm = s.effectivePaymentMethodLabel;
        if (pm.contains('تحويل')) {
          salesTransfer += s.paidAmountMinor;
        } else if (pm.contains('إيداع') || pm.contains('ايداع')) {
          salesDeposit += s.paidAmountMinor;
        } else {
          salesCashOnly += s.paidAmountMinor;
        }
        for (final it in s.items) {
          cogs += it.estimatedCostMinor;
          totalSoldQtyMilli += it.qtyMilli;
        }
      }

      final custReceipts = custPayments
          .where((p) => p.currency == cur && inDateRange(p.dateIso))
          .fold<int>(0, (a, p) => a + p.amountMinor);

      int purTotal = 0;
      int purPaid = 0;
      int supplierSurplus = 0;
      for (final p in purchases.where((e) => e.currency == cur && inDateRange(e.dateIso))) {
        purTotal += p.totalAmountMinor;
        purPaid += p.paidAmountMinor;
        supplierSurplus += p.overpaymentSurplusMinor;
      }

      final supPay = supPayments
          .where((p) => p.currency == cur && inDateRange(p.dateIso))
          .fold<int>(0, (a, p) => a + p.amountMinor);

      final expTotal = expenses
          .where((e) => e.currency == cur)
          .fold<int>(0, (a, e) => a + e.amountMinor);

      int taxTotal = 0;
      int stallTotal = 0;
      int laborTotal = 0;
      for (final t in taxes.where((t) => t.currency == cur)) {
        taxTotal += t.taxAmountMinor;
        stallTotal += t.stallOutflowMinor;
        laborTotal += t.laborOutflowMinor;
      }

      final totalCustDebt = customers.fold<int>(0, (a, c) => a + c.balanceFor(cur));
      final totalSupPayable = suppliers.fold<int>(0, (a, s) => a + s.balanceFor(cur));

      int delivT = 0;
      int pendT = 0;
      int feesT = 0;
      for (final t in transfers.where((e) => e.currency == cur)) {
        if (t.status == TransferStatus.delivered) {
          delivT += t.amountMinor;
          feesT += t.feeMinor;
        } else if (t.status == TransferStatus.pending) {
          pendT += t.amountMinor;
          feesT += t.feeMinor;
        }
      }

      result[cur] = CurrencyDashboardMetrics(
        currency: cur,
        salesTotalMinor: salesTotal,
        salesPaidMinor: salesPaid,
        salesCreditMinor: salesCredit,
        customerReceiptsMinor: custReceipts,
        purchasesTotalMinor: purTotal,
        purchasesPaidMinor: purPaid,
        supplierPaymentsMinor: supPay,
        expensesMinor: expTotal,
        taxMinor: taxTotal,
        estimatedCogsMinor: cogs,
        totalCustomerDebtMinor: totalCustDebt,
        totalSupplierPayableMinor: totalSupPayable,
        transfersDeliveredMinor: delivT,
        transfersPendingMinor: pendT,
        transfersFeesMinor: feesT,
        stallOutflowMinor: stallTotal,
        laborOutflowMinor: laborTotal,
        salesCashOnlyMinor: salesCashOnly,
        salesTransferMinor: salesTransfer,
        salesDepositMinor: salesDeposit,
        totalSoldQtyMilli: totalSoldQtyMilli,
        supplierOverpaymentSurplusMinor: supplierSurplus,
      );
    }
    return result;
  }

  /// 7-day sales & expenses chart series per currency (matching original Android HomeScreen chart).
  Future<List<DailyChartPoint>> getDailyChartSeries({
    required CurrencyCode currency,
    int days = 7,
    DateTime? anchorDate,
  }) async {
    final anchor = anchorDate ?? DateTime.now();
    final start = DateTime(anchor.year, anchor.month, anchor.day)
        .subtract(Duration(days: days - 1));
    final fromIso = QatDateUtils.toIsoDate(start);
    final toIso = QatDateUtils.toIsoDate(anchor);
    final sales = await getSales(fromIso: fromIso, toIso: toIso);
    final expenses = await getExpenses(fromIso: fromIso, toIso: toIso);
    final taxes = await getTaxEntries(fromIso: fromIso, toIso: toIso);

    final salesByDay = <String, int>{};
    for (final s in sales.where((e) => e.currency == currency)) {
      salesByDay[s.dateIso] = (salesByDay[s.dateIso] ?? 0) + s.totalAmountMinor;
    }
    final expByDay = <String, int>{};
    for (final e in expenses.where((e) => e.currency == currency)) {
      expByDay[e.dateIso] = (expByDay[e.dateIso] ?? 0) + e.amountMinor;
    }
    for (final t in taxes.where((t) => t.currency == currency)) {
      expByDay[t.dateIso] = (expByDay[t.dateIso] ?? 0) + t.totalOutflowMinor;
    }

    final points = <DailyChartPoint>[];
    for (int i = 0; i < days; i++) {
      final d = start.add(Duration(days: i));
      final iso = QatDateUtils.toIsoDate(d);
      final shortLabel = '${d.month}/${d.day}';
      points.add(DailyChartPoint(
        dateIso: iso,
        shortLabel: shortLabel,
        salesMinor: salesByDay[iso] ?? 0,
        expensesMinor: expByDay[iso] ?? 0,
      ));
    }
    return points;
  }

  Future<Map<CurrencyCode, List<StatementLine>>> getCustomerStatement(int customerId) async {
    final allSales = (await getSales()).where((s) => s.customerId == customerId).toList();
    final payments = await getCustomerPayments(customerId: customerId);

    final out = <CurrencyCode, List<StatementLine>>{};
    for (final cur in CurrencyCode.values) {
      final rawEvents = <({int ms, String iso, String ref, String desc, int debit, int credit})>[];
      for (final s in allSales.where((e) => e.currency == cur)) {
        rawEvents.add((
          ms: s.dateMs,
          iso: s.dateIso,
          ref: s.invoiceNumber,
          desc: 'فاتورة مبيعات (${s.paymentType.arabicLabel})',
          debit: s.totalAmountMinor,
          credit: s.paidAmountMinor,
        ));
      }
      for (final p in payments.where((e) => e.currency == cur)) {
        rawEvents.add((
          ms: p.dateMs,
          iso: p.dateIso,
          ref: 'RCV-${p.id}',
          desc: 'سند قبض (${p.paymentMethod}) ${p.notes}'.trim(),
          debit: 0,
          credit: p.amountMinor,
        ));
      }
      if (rawEvents.isEmpty) continue;
      rawEvents.sort((a, b) => a.ms.compareTo(b.ms));
      int running = 0;
      final lines = <StatementLine>[];
      for (final ev in rawEvents) {
        running += ev.debit - ev.credit;
        lines.add(StatementLine(
          dateMs: ev.ms,
          dateIso: ev.iso,
          reference: ev.ref,
          description: ev.desc,
          currency: cur,
          debitMinor: ev.debit,
          creditMinor: ev.credit,
          runningBalanceMinor: running,
        ));
      }
      out[cur] = lines;
    }
    return out;
  }

  Future<Map<CurrencyCode, List<StatementLine>>> getSupplierStatement(int supplierId) async {
    final allPurchases = await getSupplierPurchases(supplierId: supplierId);
    final payments = await getSupplierPayments(supplierId: supplierId);

    final out = <CurrencyCode, List<StatementLine>>{};
    for (final cur in CurrencyCode.values) {
      final rawEvents = <({int ms, String iso, String ref, String desc, int debit, int credit})>[];
      for (final p in allPurchases.where((e) => e.currency == cur)) {
        rawEvents.add((
          ms: p.dateMs,
          iso: p.dateIso,
          ref: p.purchaseNumber,
          desc: 'فاتورة شراء (${p.paymentType.arabicLabel})',
          debit: p.totalAmountMinor,
          credit: p.paidAmountMinor,
        ));
      }
      for (final p in payments.where((e) => e.currency == cur)) {
        rawEvents.add((
          ms: p.dateMs,
          iso: p.dateIso,
          ref: 'PAY-${p.id}',
          desc: 'سند صرف (${p.paymentMethod}) ${p.notes}'.trim(),
          debit: 0,
          credit: p.amountMinor,
        ));
      }
      if (rawEvents.isEmpty) continue;
      rawEvents.sort((a, b) => a.ms.compareTo(b.ms));
      int running = 0;
      final lines = <StatementLine>[];
      for (final ev in rawEvents) {
        running += ev.debit - ev.credit;
        lines.add(StatementLine(
          dateMs: ev.ms,
          dateIso: ev.iso,
          reference: ev.ref,
          description: ev.desc,
          currency: cur,
          debitMinor: ev.debit,
          creditMinor: ev.credit,
          runningBalanceMinor: running,
        ));
      }
      out[cur] = lines;
    }
    return out;
  }

  // ---------------------------------------------------------------------------
  // Daily Settlement & Inventory Reconciliation Audit Logs
  // ---------------------------------------------------------------------------

  Future<List<DailySettlementSnapshot>> getDailySettlements() async {
    final raw = await getSetting('daily_settlements_v1');
    if (raw == null || raw.trim().isEmpty) return const [];
    try {
      final Object? decoded = jsonDecode(raw);
      if (decoded is! List<Object?>) return const [];
      return decoded
          .whereType<Map<Object?, Object?>>()
          .map(
            (m) => DailySettlementSnapshot.fromJson(
              m.map<String, dynamic>(
                (k, v) => MapEntry<String, dynamic>(k.toString(), v),
              ),
            ),
          )
          .toList()
        ..sort((a, b) => b.epochMs.compareTo(a.epochMs));
    } catch (_) {
      return const [];
    }
  }

  Future<void> saveDailySettlement(DailySettlementSnapshot snapshot) async {
    final existing = await getDailySettlements();
    final updated = [snapshot, ...existing.where((e) => e.id != snapshot.id)];
    final trimmed = updated.take(180).map((e) => e.toJson()).toList();
    await setSetting('daily_settlements_v1', jsonEncode(trimmed));
  }

  Future<List<InventoryReconciliationEntry>> getInventoryReconciliations() async {
    final raw = await getSetting('inventory_reconciliation_v1');
    if (raw == null || raw.trim().isEmpty) return const [];
    try {
      final Object? decoded = jsonDecode(raw);
      if (decoded is! List<Object?>) return const [];
      return decoded
          .whereType<Map<Object?, Object?>>()
          .map(
            (m) => InventoryReconciliationEntry.fromJson(
              m.map<String, dynamic>(
                (k, v) => MapEntry<String, dynamic>(k.toString(), v),
              ),
            ),
          )
          .toList()
        ..sort((a, b) => b.epochMs.compareTo(a.epochMs));
    } catch (_) {
      return const [];
    }
  }

  Future<void> applyInventoryReconciliation({
    required List<({InventoryBatch batch, int countedQtyMilli})> counts,
    required String notes,
    required String createdBy,
  }) async {
    if (counts.isEmpty) return;
    final db = await _db;
    final nowMs = DateTime.now().millisecondsSinceEpoch;
    final iso = QatDateUtils.toIsoDate(DateTime.fromMillisecondsSinceEpoch(nowMs));
    final existingLogs = await getInventoryReconciliations();
    final newEntries = <InventoryReconciliationEntry>[];

    await db.transaction<void>((txn) async {
      for (final item in counts) {
        final b = item.batch;
        final safeCounted = item.countedQtyMilli < 0 ? 0 : item.countedQtyMilli;
        final diff = safeCounted - b.remainingQtyMilli;
        if (diff == 0) continue;

        final nextInitial = safeCounted > b.initialQtyMilli
            ? safeCounted
            : b.initialQtyMilli;
        await txn.update(
          'inventory_batches',
          {
            'remaining_qty_milli': safeCounted,
            'initial_qty_milli': nextInitial,
          },
          where: 'id = ?',
          whereArgs: [b.id],
        );

        final impactMinor = ((diff * b.buyPriceMinor) / 1000).round();
        newEntries.add(
          InventoryReconciliationEntry(
            id: '${nowMs}_${b.id}',
            dateIso: iso,
            epochMs: nowMs,
            batchId: b.id,
            qatTypeName: b.qatTypeName,
            unit: b.unit,
            currency: b.currency,
            systemQtyMilli: b.remainingQtyMilli,
            countedQtyMilli: safeCounted,
            diffQtyMilli: diff,
            unitCostMinor: b.buyPriceMinor,
            financialImpactMinor: impactMinor,
            notes: notes.trim(),
            createdBy: createdBy,
          ),
        );
      }
    });

    if (newEntries.isNotEmpty) {
      final merged = [...newEntries, ...existingLogs]
          .take(250)
          .map((e) => e.toJson())
          .toList();
      await setSetting('inventory_reconciliation_v1', jsonEncode(merged));
    }
  }

  // ---------------------------------------------------------------------------
  // Archive Operations
  // ---------------------------------------------------------------------------

  Future<void> setArchived(String tableName, int id, bool archived) async {
    const allowed = {
      'sales',
      'customer_payments',
      'supplier_purchases',
      'supplier_payments',
      'expenses',
      'qat_tax_entries',
      'money_transfers',
      'inventory_batches',
    };
    if (!allowed.contains(tableName)) throw ArgumentError('جدول غير مدعوم للأرشفة: $tableName');
    final db = await _db;

    await db.transaction<void>((txn) async {
      final rows = await txn.query(tableName, where: 'id = ?', whereArgs: [id], limit: 1);
      if (rows.isEmpty) return;
      final r = rows.first;
      final currentlyArchived = (r['is_archived'] as num).toInt() == 1;
      if (currentlyArchived == archived) return;

      await txn.update(tableName, {'is_archived': archived ? 1 : 0},
          where: 'id = ?', whereArgs: [id]);

      final sign = archived ? -1 : 1;
      if (tableName == 'sales') {
        final cid = (r['customer_id'] as num?)?.toInt();
        final rem = (r['remaining_amount_minor'] as num).toInt();
        if (cid != null && rem != 0) {
          await _adjustCustomerBalance(
            txn,
            customerId: cid,
            currency: CurrencyCode.fromRaw(r['currency_code']?.toString()),
            deltaMinor: sign * rem,
          );
        }
      } else if (tableName == 'customer_payments') {
        await _adjustCustomerBalance(
          txn,
          customerId: (r['customer_id'] as num).toInt(),
          currency: CurrencyCode.fromRaw(r['currency_code']?.toString()),
          deltaMinor: -sign * (r['amount_minor'] as num).toInt(),
        );
      } else if (tableName == 'supplier_purchases') {
        await _adjustSupplierBalance(
          txn,
          supplierId: (r['supplier_id'] as num).toInt(),
          currency: CurrencyCode.fromRaw(r['currency_code']?.toString()),
          deltaMinor: sign * (r['remaining_amount_minor'] as num).toInt(),
        );
      } else if (tableName == 'supplier_payments') {
        await _adjustSupplierBalance(
          txn,
          supplierId: (r['supplier_id'] as num).toInt(),
          currency: CurrencyCode.fromRaw(r['currency_code']?.toString()),
          deltaMinor: -sign * (r['amount_minor'] as num).toInt(),
        );
      }
    });
  }

  // ---------------------------------------------------------------------------
  // Internal Balance Helpers & Row Mappers
  // ---------------------------------------------------------------------------

  Future<void> _adjustCustomerBalance(
    DatabaseExecutor txn, {
    required int customerId,
    required CurrencyCode currency,
    required int deltaMinor,
  }) async {
    final rows = await txn.query(
      'customer_balances',
      where: 'customer_id = ? AND currency_code = ?',
      whereArgs: [customerId, currency.code],
      limit: 1,
    );
    final current = rows.isEmpty ? 0 : (rows.first['debt_minor'] as num).toInt();
    final next = current + deltaMinor;
    await txn.insert(
      'customer_balances',
      {
        'customer_id': customerId,
        'currency_code': currency.code,
        'debt_minor': next,
        'updated_at_ms': DateTime.now().millisecondsSinceEpoch,
      },
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  Future<void> _adjustSupplierBalance(
    DatabaseExecutor txn, {
    required int supplierId,
    required CurrencyCode currency,
    required int deltaMinor,
  }) async {
    final rows = await txn.query(
      'supplier_balances',
      where: 'supplier_id = ? AND currency_code = ?',
      whereArgs: [supplierId, currency.code],
      limit: 1,
    );
    final current = rows.isEmpty ? 0 : (rows.first['balance_minor'] as num).toInt();
    final next = current + deltaMinor;
    await txn.insert(
      'supplier_balances',
      {
        'supplier_id': supplierId,
        'currency_code': currency.code,
        'balance_minor': next,
        'updated_at_ms': DateTime.now().millisecondsSinceEpoch,
      },
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  QatType _mapQatType(Map<String, Object?> r) => QatType(
        id: (r['id'] as num).toInt(),
        name: (r['name'] ?? '').toString(),
        defaultBuyPriceMinor: (r['default_buy_price_minor'] as num).toInt(),
        defaultSellPriceMinor: (r['default_sell_price_minor'] as num).toInt(),
        defaultCurrency: CurrencyCode.fromRaw(r['default_currency']?.toString()),
        notes: (r['notes'] ?? '').toString(),
        isHidden: (r['is_hidden'] as num).toInt() == 1,
        createdAtMs: (r['created_at_ms'] as num).toInt(),
      );

  InventoryBatch _mapBatch(Map<String, Object?> r) => InventoryBatch(
        id: (r['id'] as num).toInt(),
        qatTypeId: (r['qat_type_id'] as num?)?.toInt(),
        qatTypeName: (r['qat_type_name'] ?? '').toString(),
        supplierId: (r['supplier_id'] as num?)?.toInt(),
        supplierName: (r['supplier_name'] ?? '').toString(),
        remainingQtyMilli: (r['remaining_qty_milli'] as num).toInt(),
        initialQtyMilli: (r['initial_qty_milli'] as num).toInt(),
        unit: (r['unit'] ?? 'ربطة').toString(),
        buyPriceMinor: (r['buy_price_minor'] as num).toInt(),
        sellPriceMinor: (r['sell_price_minor'] as num).toInt(),
        currency: CurrencyCode.fromRaw(r['currency_code']?.toString()),
        purchaseDateMs: (r['purchase_date_ms'] as num).toInt(),
        purchaseDateIso: (r['purchase_date_iso'] ?? '').toString(),
        isArchived: (r['is_archived'] as num).toInt() == 1,
        itemNumber: (r['item_number'] ?? '').toString(),
        lowStockThresholdMilli: ((r['low_stock_threshold_milli'] as num?) ?? 0).toInt(),
        receiverId: (r['receiver_id'] as num?)?.toInt(),
        receiverName: (r['receiver_name'] ?? '').toString(),
        notes: (r['notes'] ?? '').toString(),
      );

  QatImportWeight _mapImportWeight(Map<String, Object?> r) => QatImportWeight(
        id: (r['id'] as num).toInt(),
        dateMs: (r['date_ms'] as num).toInt(),
        dateIso: (r['date_iso'] ?? '').toString(),
        weightMilli: (r['weight_milli'] as num).toInt(),
        receiverName: (r['receiver_name'] ?? '').toString(),
        notes: (r['notes'] ?? '').toString(),
        isArchived: (r['is_archived'] as num).toInt() == 1,
      );

  Customer _mapCustomer(Map<String, Object?> r, Map<CurrencyCode, int> bals) => Customer(
        id: (r['id'] as num).toInt(),
        name: (r['name'] ?? '').toString(),
        phone: (r['phone'] ?? '').toString(),
        address: (r['address'] ?? '').toString(),
        notes: (r['notes'] ?? '').toString(),
        createdAtMs: (r['created_at_ms'] as num).toInt(),
        isArchived: (r['is_archived'] as num).toInt() == 1,
        balancesByCurrency: bals,
      );

  SaleItem _mapSaleItem(Map<String, Object?> r) => SaleItem(
        id: (r['id'] as num).toInt(),
        saleId: (r['sale_id'] as num).toInt(),
        inventoryBatchId: (r['inventory_batch_id'] as num?)?.toInt(),
        qatTypeName: (r['qat_type_name'] ?? '').toString(),
        qtyMilli: (r['qty_milli'] as num).toInt(),
        unit: (r['unit'] ?? 'ربطة').toString(),
        unitPriceMinor: (r['unit_price_minor'] as num).toInt(),
        buyPriceMinor: (r['buy_price_minor'] as num).toInt(),
        lineTotalMinor: (r['line_total_minor'] as num).toInt(),
        currency: CurrencyCode.fromRaw(r['currency_code']?.toString()),
      );

  Sale _mapSale(Map<String, Object?> r, List<SaleItem> items) => Sale(
        id: (r['id'] as num).toInt(),
        invoiceNumber: (r['invoice_number'] ?? '').toString(),
        customerId: (r['customer_id'] as num?)?.toInt(),
        customerName: (r['customer_name'] ?? '').toString(),
        dateMs: (r['date_ms'] as num).toInt(),
        dateIso: (r['date_iso'] ?? '').toString(),
        currency: CurrencyCode.fromRaw(r['currency_code']?.toString()),
        subtotalMinor: (r['subtotal_minor'] as num).toInt(),
        discountMinor: (r['discount_minor'] as num).toInt(),
        taxBps: (r['tax_bps'] as num).toInt(),
        taxAmountMinor: (r['tax_amount_minor'] as num).toInt(),
        totalAmountMinor: (r['total_amount_minor'] as num).toInt(),
        paidAmountMinor: (r['paid_amount_minor'] as num).toInt(),
        remainingAmountMinor: (r['remaining_amount_minor'] as num).toInt(),
        paymentType: PaymentType.fromRaw(r['payment_type']?.toString()),
        status: (r['status'] ?? '').toString(),
        notes: (r['notes'] ?? '').toString(),
        isArchived: (r['is_archived'] as num).toInt() == 1,
        paymentMethod: (r['payment_method'] ?? '').toString(),
        items: items,
      );

  CustomerPayment _mapCustomerPayment(Map<String, Object?> r) => CustomerPayment(
        id: (r['id'] as num).toInt(),
        customerId: (r['customer_id'] as num).toInt(),
        customerName: (r['customer_name'] ?? '').toString(),
        amountMinor: (r['amount_minor'] as num).toInt(),
        currency: CurrencyCode.fromRaw(r['currency_code']?.toString()),
        dateMs: (r['date_ms'] as num).toInt(),
        dateIso: (r['date_iso'] ?? '').toString(),
        paymentMethod: (r['payment_method'] ?? 'نقدي').toString(),
        notes: (r['notes'] ?? '').toString(),
        isArchived: (r['is_archived'] as num).toInt() == 1,
      );

  Supplier _mapSupplier(Map<String, Object?> r, Map<CurrencyCode, int> bals) => Supplier(
        id: (r['id'] as num).toInt(),
        name: (r['name'] ?? '').toString(),
        phone: (r['phone'] ?? '').toString(),
        address: (r['address'] ?? '').toString(),
        notes: (r['notes'] ?? '').toString(),
        createdAtMs: (r['created_at_ms'] as num).toInt(),
        isArchived: (r['is_archived'] as num).toInt() == 1,
        region: (r['region'] ?? '').toString(),
        balancesByCurrency: bals,
      );

  SupplierPurchaseItem _mapPurchaseItem(Map<String, Object?> r) => SupplierPurchaseItem(
        id: (r['id'] as num).toInt(),
        purchaseId: (r['purchase_id'] as num).toInt(),
        qatTypeName: (r['qat_type_name'] ?? '').toString(),
        qtyMilli: (r['qty_milli'] as num).toInt(),
        unit: (r['unit'] ?? 'ربطة').toString(),
        buyPriceMinor: (r['buy_price_minor'] as num).toInt(),
        lineTotalMinor: (r['line_total_minor'] as num).toInt(),
        currency: CurrencyCode.fromRaw(r['currency_code']?.toString()),
      );

  SupplierPurchase _mapPurchase(Map<String, Object?> r, List<SupplierPurchaseItem> items) =>
      SupplierPurchase(
        id: (r['id'] as num).toInt(),
        purchaseNumber: (r['purchase_number'] ?? '').toString(),
        supplierId: (r['supplier_id'] as num).toInt(),
        supplierName: (r['supplier_name'] ?? '').toString(),
        dateMs: (r['date_ms'] as num).toInt(),
        dateIso: (r['date_iso'] ?? '').toString(),
        currency: CurrencyCode.fromRaw(r['currency_code']?.toString()),
        subtotalMinor: (r['subtotal_minor'] as num).toInt(),
        discountMinor: (r['discount_minor'] as num).toInt(),
        taxBps: (r['tax_bps'] as num).toInt(),
        taxAmountMinor: (r['tax_amount_minor'] as num).toInt(),
        totalAmountMinor: (r['total_amount_minor'] as num).toInt(),
        paidAmountMinor: (r['paid_amount_minor'] as num).toInt(),
        remainingAmountMinor: (r['remaining_amount_minor'] as num).toInt(),
        paymentType: PaymentType.fromRaw(r['payment_type']?.toString()),
        notes: (r['notes'] ?? '').toString(),
        isArchived: (r['is_archived'] as num).toInt() == 1,
        paymentMethod: (r['payment_method'] ?? '').toString(),
        bankName: (r['bank_name'] ?? '').toString(),
        transferNumber: (r['transfer_number'] ?? '').toString(),
        demarMinor: ((r['demar_minor'] as num?) ?? 0).toInt(),
        demarType: (r['demar_type'] ?? '').toString(),
        demarNumber: (r['demar_number'] ?? '').toString(),
        items: items,
      );

  SupplierPayment _mapSupplierPayment(Map<String, Object?> r) => SupplierPayment(
        id: (r['id'] as num).toInt(),
        supplierId: (r['supplier_id'] as num).toInt(),
        supplierName: (r['supplier_name'] ?? '').toString(),
        amountMinor: (r['amount_minor'] as num).toInt(),
        currency: CurrencyCode.fromRaw(r['currency_code']?.toString()),
        dateMs: (r['date_ms'] as num).toInt(),
        dateIso: (r['date_iso'] ?? '').toString(),
        paymentMethod: (r['payment_method'] ?? 'نقدي').toString(),
        notes: (r['notes'] ?? '').toString(),
        isArchived: (r['is_archived'] as num).toInt() == 1,
      );

  LedgerAccount _mapLedgerAccount(Map<String, Object?> r) => LedgerAccount(
        id: (r['id'] as num).toInt(),
        code: (r['code'] ?? '').toString(),
        name: (r['name'] ?? '').toString(),
        category: (r['category'] ?? '').toString(),
        isSystem: (r['is_system'] as num).toInt() == 1,
        isActive: (r['is_active'] as num).toInt() == 1,
      );

  Expense _mapExpense(Map<String, Object?> r) => Expense(
        id: (r['id'] as num).toInt(),
        description: (r['description'] ?? '').toString(),
        category: (r['category'] ?? '').toString(),
        accountId: (r['account_id'] as num?)?.toInt(),
        amountMinor: (r['amount_minor'] as num).toInt(),
        currency: CurrencyCode.fromRaw(r['currency_code']?.toString()),
        dateMs: (r['date_ms'] as num).toInt(),
        dateIso: (r['date_iso'] ?? '').toString(),
        notes: (r['notes'] ?? '').toString(),
        isArchived: (r['is_archived'] as num).toInt() == 1,
      );

  QatTaxEntry _mapTaxEntry(Map<String, Object?> r) => QatTaxEntry(
        id: (r['id'] as num).toInt(),
        dateMs: (r['date_ms'] as num).toInt(),
        dateIso: (r['date_iso'] ?? '').toString(),
        totalQtyMilli: (r['total_qty_milli'] as num).toInt(),
        taxRateMinor: (r['tax_rate_minor'] as num).toInt(),
        taxAmountMinor: (r['tax_amount_minor'] as num).toInt(),
        currency: CurrencyCode.fromRaw(r['currency_code']?.toString()),
        source: (r['source'] ?? 'يدوي').toString(),
        notes: (r['notes'] ?? '').toString(),
        isArchived: (r['is_archived'] as num).toInt() == 1,
        stallOutflowMinor: ((r['stall_outflow_minor'] as num?) ?? 0).toInt(),
        laborOutflowMinor: ((r['labor_outflow_minor'] as num?) ?? 0).toInt(),
      );

  TransferRecipient _mapRecipient(Map<String, Object?> r) => TransferRecipient(
        id: (r['id'] as num).toInt(),
        name: (r['name'] ?? '').toString(),
        phone: (r['phone'] ?? '').toString(),
        company: (r['company'] ?? '').toString(),
        notes: (r['notes'] ?? '').toString(),
        createdAtMs: (r['created_at_ms'] as num).toInt(),
        isActive: ((r['is_active'] as num?) ?? 1).toInt() == 1,
      );

  MoneyTransfer _mapTransfer(Map<String, Object?> r) => MoneyTransfer(
        id: (r['id'] as num).toInt(),
        senderName: (r['sender_name'] ?? '').toString(),
        receiverName: (r['receiver_name'] ?? '').toString(),
        recipientId: (r['recipient_id'] as num?)?.toInt(),
        amountMinor: (r['amount_minor'] as num).toInt(),
        feeMinor: (r['fee_minor'] as num).toInt(),
        currency: CurrencyCode.fromRaw(r['currency_code']?.toString()),
        transferCompany: (r['transfer_company'] ?? '').toString(),
        transferNumber: (r['transfer_number'] ?? '').toString(),
        dateMs: (r['date_ms'] as num).toInt(),
        dateIso: (r['date_iso'] ?? '').toString(),
        status: TransferStatus.fromRaw(r['status_code']?.toString()),
        notes: (r['notes'] ?? '').toString(),
        isArchived: (r['is_archived'] as num).toInt() == 1,
      );
}
