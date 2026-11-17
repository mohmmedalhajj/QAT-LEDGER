import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:path/path.dart' as p;
import 'package:qat_ledger/core/money.dart';
import 'package:qat_ledger/data/database_service.dart';
import 'package:qat_ledger/data/qat_repository.dart';
import 'package:qat_ledger/data/room_migrator.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  sqfliteFfiInit();

  test('migrates Room v18 SQLite database cleanly, preserves all records, separates currencies, and is idempotent', () async {
    final tempDir = await Directory.systemTemp.createTemp('qat_room_mig_test_');
    try {
      final legacyPath = p.join(tempDir.path, 'tofan_al_aqsa_qat_db');
      final targetPath = p.join(tempDir.path, 'qat_ledger_v2.db');

      // 1. Create authentic Room v18 legacy SQLite DB
      final legacyDb = await databaseFactoryFfi.openDatabase(
        legacyPath,
        options: OpenDatabaseOptions(version: 18),
      );
      await legacyDb.execute('PRAGMA user_version = 18');
      await legacyDb.execute('CREATE TABLE qat_types (id INTEGER PRIMARY KEY, name TEXT, defaultBuyPrice REAL, defaultSellPrice REAL, notes TEXT, isHidden INTEGER)');
      await legacyDb.execute('CREATE TABLE inventory (id INTEGER PRIMARY KEY, qatType TEXT, supplierName TEXT, remainingQty REAL, unit TEXT, buyPrice REAL, sellPrice REAL, purchaseDate TEXT, currency TEXT)');
      await legacyDb.execute('CREATE TABLE customers (id INTEGER PRIMARY KEY, name TEXT, phone TEXT, address TEXT, notes TEXT, totalDebt REAL)');
      await legacyDb.execute('CREATE TABLE sales (id INTEGER PRIMARY KEY, customerName TEXT, customerId INTEGER, date TEXT, totalAmount REAL, paidAmount REAL, remainingAmount REAL, paymentType TEXT, status TEXT, notes TEXT, currency TEXT, discount REAL, taxPercent REAL, isArchived INTEGER)');
      await legacyDb.execute('CREATE TABLE sales_invoice_items (id INTEGER PRIMARY KEY, saleId INTEGER, qatType TEXT, quantity REAL, unit TEXT, unitPrice REAL, buyPrice REAL, total REAL, currency TEXT)');
      await legacyDb.execute('CREATE TABLE customer_payments (id INTEGER PRIMARY KEY, customerId INTEGER, customerName TEXT, amount REAL, date TEXT, notes TEXT, currency TEXT, paymentMethod TEXT, isArchived INTEGER)');
      await legacyDb.execute('CREATE TABLE suppliers (id INTEGER PRIMARY KEY, name TEXT, phone TEXT, address TEXT, notes TEXT, totalBalance REAL)');
      await legacyDb.execute('CREATE TABLE supplier_purchases (id INTEGER PRIMARY KEY, supplierId INTEGER, supplierName TEXT, date TEXT, totalAmount REAL, paidAmount REAL, remainingAmount REAL, paymentType TEXT, notes TEXT, currency TEXT, discount REAL, taxPercent REAL, isArchived INTEGER)');
      await legacyDb.execute('CREATE TABLE supplier_purchase_items (id INTEGER PRIMARY KEY, purchaseId INTEGER, qatType TEXT, quantity REAL, unit TEXT, buyPrice REAL, total REAL, currency TEXT)');
      await legacyDb.execute('CREATE TABLE supplier_payments (id INTEGER PRIMARY KEY, supplierId INTEGER, supplierName TEXT, amount REAL, date TEXT, notes TEXT, currency TEXT, paymentMethod TEXT, isArchived INTEGER)');
      await legacyDb.execute('CREATE TABLE expenses (id INTEGER PRIMARY KEY, description TEXT, category TEXT, amount REAL, date TEXT, notes TEXT, currency TEXT, isArchived INTEGER)');
      await legacyDb.execute('CREATE TABLE qat_tax_entries (id INTEGER PRIMARY KEY, date TEXT, totalQty REAL, taxRate REAL, totalTaxAmount REAL, notes TEXT, source TEXT, isArchived INTEGER)');
      await legacyDb.execute('CREATE TABLE transfer_recipients (id INTEGER PRIMARY KEY, name TEXT, phone TEXT, company TEXT, notes TEXT, createdAt INTEGER)');
      await legacyDb.execute('CREATE TABLE money_transfers (id INTEGER PRIMARY KEY, senderName TEXT, receiverName TEXT, recipientId INTEGER, amount REAL, currency TEXT, fee REAL, transferCompany TEXT, transferNumber TEXT, date TEXT, status TEXT, notes TEXT, isArchived INTEGER)');

      await legacyDb.insert('qat_types', {
        'id': 1, 'name': 'همداني ممتاز', 'defaultBuyPrice': 3000.0, 'defaultSellPrice': 4000.0, 'notes': '', 'isHidden': 0,
      });
      await legacyDb.insert('customers', {
        'id': 1, 'name': 'أحمد التاجر', 'phone': '777111222', 'address': 'صنعاء', 'notes': '', 'totalDebt': 10200.0,
      });
      await legacyDb.insert('suppliers', {
        'id': 1, 'name': 'مزارع خولان', 'phone': '777999000', 'address': 'خولان', 'notes': '', 'totalBalance': 5000.0,
      });
      await legacyDb.insert('inventory', {
        'id': 1, 'qatType': 'همداني ممتاز', 'supplierName': 'مزارع خولان', 'remainingQty': 12.5, 'unit': 'ربطة', 'buyPrice': 3000.0, 'sellPrice': 4000.0, 'purchaseDate': '2026-10-01', 'currency': 'YER',
      });
      // Multi-currency sales for same customer: 15,000 YER partial (paid 5,000 -> rem 10,000 YER) + 200 SAR credit
      await legacyDb.insert('sales', {
        'id': 1, 'customerName': 'أحمد التاجر', 'customerId': 1, 'date': '2026-10-01', 'totalAmount': 15000.0, 'paidAmount': 5000.0, 'remainingAmount': 10000.0, 'paymentType': 'دفعة جزئية', 'status': 'دفعة جزئية', 'notes': '', 'currency': 'YER', 'discount': 0.0, 'taxPercent': 0.0, 'isArchived': 0,
      });
      await legacyDb.insert('sales', {
        'id': 2, 'customerName': 'أحمد التاجر', 'customerId': 1, 'date': '2026-10-02', 'totalAmount': 200.0, 'paidAmount': 0.0, 'remainingAmount': 200.0, 'paymentType': 'آجل', 'status': 'آجل', 'notes': '', 'currency': 'SAR', 'discount': 0.0, 'taxPercent': 0.0, 'isArchived': 0,
      });
      await legacyDb.insert('sales_invoice_items', {
        'id': 1, 'saleId': 1, 'qatType': 'همداني ممتاز', 'quantity': 3.0, 'unit': 'ربطة', 'unitPrice': 5000.0, 'buyPrice': 3000.0, 'total': 15000.0, 'currency': 'YER',
      });
      await legacyDb.insert('supplier_purchases', {
        'id': 1, 'supplierId': 1, 'supplierName': 'مزارع خولان', 'date': '2026-10-01', 'totalAmount': 10000.0, 'paidAmount': 5000.0, 'remainingAmount': 5000.0, 'paymentType': 'دفعة جزئية', 'notes': '', 'currency': 'YER', 'discount': 0.0, 'taxPercent': 0.0, 'isArchived': 0,
      });
      await legacyDb.insert('transfer_recipients', {
        'id': 1, 'name': 'صالح المستلم', 'phone': '771000111', 'company': 'الكريمي', 'notes': '', 'createdAt': 1700000000000,
      });
      // Linked transfer + legacy unlinked transfer (recipientId = 0)
      await legacyDb.insert('money_transfers', {
        'id': 1, 'senderName': 'الوكالة', 'receiverName': 'صالح المستلم', 'recipientId': 1, 'amount': 25000.0, 'currency': 'YER', 'fee': 200.0, 'transferCompany': 'الكريمي', 'transferNumber': 'KR-1', 'date': '2026-10-03', 'status': 'تم التسليم', 'notes': '', 'isArchived': 0,
      });
      await legacyDb.insert('money_transfers', {
        'id': 2, 'senderName': 'الوكالة', 'receiverName': '', 'recipientId': 0, 'amount': 500.0, 'currency': 'SAR', 'fee': 10.0, 'transferCompany': 'النجم', 'transferNumber': 'NJ-2', 'date': '2026-10-04', 'status': 'جاري التسليم', 'notes': '', 'isArchived': 0,
      });
      await legacyDb.close();

      final dbService = DatabaseService(factory: databaseFactoryFfi, customPath: targetPath);
      final migrator = RoomMigrator(dbService: dbService, sourceFactory: databaseFactoryFfi);

      final summary = await migrator.migrateIfNeeded(
        explicitSourceDbPath: legacyPath,
        snapshotDirectory: p.join(tempDir.path, 'snaps'),
      );
      expect(summary.performed, true);
      expect(summary.rowCounts['sales'], 2);
      expect(summary.rowCounts['money_transfers'], 2);

      // Re-running migration on identical source must be a no-op (idempotent)
      final second = await migrator.migrateIfNeeded(
        explicitSourceDbPath: legacyPath,
        snapshotDirectory: p.join(tempDir.path, 'snaps'),
      );
      expect(second.performed, false);

      final repo = QatRepository(dbService);
      final customers = await repo.getCustomers();
      expect(customers.single.balanceFor(CurrencyCode.yer), 1000000); // 10,000.00 YER
      expect(customers.single.balanceFor(CurrencyCode.sar), 20000);   // 200.00 SAR (separated!)

      final transfers = await repo.getTransfers();
      final unlinked = transfers.where((t) => t.id == 2).single;
      expect(unlinked.recipientId, isNull);
      expect(unlinked.isUnlinked, true);

      final db = await dbService.database;
      final fkViolations = await db.rawQuery('PRAGMA foreign_key_check');
      expect(fkViolations, isEmpty);

      await dbService.close();
    } finally {
      await tempDir.delete(recursive: true);
    }
  });

  test('migrates Room v18 split dual-currency sales_invoices, qat_import_weights, and suppliers.region cleanly', () async {
    final tempDir = await Directory.systemTemp.createTemp('qat_room_split_test_');
    try {
      final legacyPath = p.join(tempDir.path, 'tofan_al_aqsa_qat_db');
      final targetPath = p.join(tempDir.path, 'qat_ledger_v3.db');

      final legacyDb = await databaseFactoryFfi.openDatabase(
        legacyPath,
        options: OpenDatabaseOptions(version: 18),
      );
      await legacyDb.execute('PRAGMA user_version = 18');
      await legacyDb.execute('CREATE TABLE customers (id INTEGER PRIMARY KEY, name TEXT, phone TEXT, address TEXT, notes TEXT, totalDebt REAL)');
      await legacyDb.execute('CREATE TABLE suppliers (id INTEGER PRIMARY KEY, name TEXT, phone TEXT, address TEXT, region TEXT, notes TEXT, totalBalance REAL)');
      await legacyDb.execute('CREATE TABLE sales_invoices (id INTEGER PRIMARY KEY, customerId INTEGER, customerName TEXT, date TEXT, totalAmount REAL, taxApplied REAL, discount REAL, paidAmount REAL, debtAmount REAL, paymentMethod TEXT, currency TEXT, totalAmountYer REAL, totalAmountSar REAL, paidAmountYer REAL, paidAmountSar REAL, debtAmountYer REAL, debtAmountSar REAL, paymentMethodYer TEXT, paymentMethodSar TEXT, notes TEXT, isArchived INTEGER)');
      await legacyDb.execute('CREATE TABLE qat_import_weights (id INTEGER PRIMARY KEY, date TEXT, weight REAL, notes TEXT, receiverName TEXT)');

      await legacyDb.insert('customers', {'id': 1, 'name': 'عميل مزدوج', 'phone': '', 'address': '', 'notes': '', 'totalDebt': 0});
      await legacyDb.insert('suppliers', {'id': 1, 'name': 'مورد آنس', 'phone': '', 'address': '', 'region': 'آنس', 'notes': '', 'totalBalance': 0});
      // One legacy Room v18 sales_invoices row carrying BOTH YER and SAR amounts simultaneously
      await legacyDb.insert('sales_invoices', {
        'id': 10,
        'customerId': 1,
        'customerName': 'عميل مزدوج',
        'date': '2026-11-10',
        'totalAmount': 10000.0,
        'taxApplied': 0.0,
        'discount': 0.0,
        'paidAmount': 2000.0,
        'debtAmount': 8000.0,
        'paymentMethod': 'آجل',
        'currency': 'YER',
        'totalAmountYer': 10000.0,
        'paidAmountYer': 2000.0,
        'debtAmountYer': 8000.0,
        'paymentMethodYer': 'دفعة جزئية',
        'totalAmountSar': 300.0,
        'paidAmountSar': 50.0,
        'debtAmountSar': 250.0,
        'paymentMethodSar': 'دفعة جزئية',
        'notes': '',
        'isArchived': 0,
      });
      await legacyDb.insert('qat_import_weights', {
        'id': 1,
        'date': '2026-11-10',
        'weight': 45.5,
        'receiverName': 'مورد آنس',
        'notes': 'وارد الصباح',
      });
      await legacyDb.close();

      final dbService = DatabaseService(factory: databaseFactoryFfi, customPath: targetPath);
      final migrator = RoomMigrator(dbService: dbService, sourceFactory: databaseFactoryFfi);
      final summary = await migrator.migrateIfNeeded(
        explicitSourceDbPath: legacyPath,
        snapshotDirectory: p.join(tempDir.path, 'snaps'),
      );
      expect(summary.performed, true);

      final repo = QatRepository(dbService);
      final sales = await repo.getSales();
      // Split into two strictly separated single-currency invoices!
      expect(sales.length, 2);
      final yerSale = sales.firstWhere((s) => s.currency == CurrencyCode.yer);
      final sarSale = sales.firstWhere((s) => s.currency == CurrencyCode.sar);
      expect(yerSale.remainingAmountMinor, 800000);
      expect(sarSale.remainingAmountMinor, 25000);

      final cust = (await repo.getCustomers()).single;
      expect(cust.balanceFor(CurrencyCode.yer), 800000);
      expect(cust.balanceFor(CurrencyCode.sar), 25000);

      final sup = (await repo.getSuppliers()).single;
      expect(sup.region, 'آنس');

      final weights = await repo.getImportWeights();
      expect(weights.length, 1);
      expect(weights.single.weightMilli, 45500);

      await dbService.close();
    } finally {
      await tempDir.delete(recursive: true);
    }
  });
}
