import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:path/path.dart' as p;
import 'package:qat_ledger/core/money.dart';
import 'package:qat_ledger/data/backup_service.dart';
import 'package:qat_ledger/data/database_service.dart';
import 'package:qat_ledger/data/models.dart';
import 'package:qat_ledger/data/qat_repository.dart';
import 'package:qat_ledger/data/room_migrator.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

import '_ci_diag_helper.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  sqfliteFfiInit();

  test('repository full accounting lifecycle: purchase edit, sale stock+debt, recipient report, atomic .qbak backup/restore', () async {
    await runWithCiDiag('repository_accounting_test', () async {
    final tempDir = await Directory.systemTemp.createTemp('qat_repo_test_');
    try {
      final dbPath = p.join(tempDir.path, 'qat_ledger_v2.db');
      final dbService = DatabaseService(factory: databaseFactoryFfi, customPath: dbPath);
      final repo = QatRepository(dbService);
      final migrator = RoomMigrator(dbService: dbService, sourceFactory: databaseFactoryFfi);
      final backupService = BackupService(dbService: dbService, migrator: migrator);

      final supId = await repo.upsertSupplier(name: 'مورد صنعاء');
      final custId = await repo.upsertCustomer(name: 'عميل عدن');

      // 1. Purchase 10 bundles @ 2000 YER partial paid 5000 -> payable 15,000 YER
      final purchaseId = await repo.makeSupplierPurchase(
        supplierId: supId,
        supplierName: 'مورد صنعاء',
        items: const [
          NewPurchaseItemInput(
            qatTypeName: 'أرحبي',
            qtyMilli: 10000,
            unit: 'ربطة',
            buyPriceMinor: 200000,
            sellPriceMinor: 350000,
          ),
        ],
        currency: CurrencyCode.yer,
        paymentType: PaymentType.partial,
        paidAmountMinor: 500000,
      );

      // Edit purchase buy price to 2200 -> total 22,000 YER, paid 5000 -> payable 17,000 YER, and verify batch buyPrice updates!
      await repo.editSupplierPurchase(
        purchaseId: purchaseId,
        newItems: const [
          NewPurchaseItemInput(
            qatTypeName: 'أرحبي',
            qtyMilli: 10000,
            unit: 'ربطة',
            buyPriceMinor: 220000,
            sellPriceMinor: 350000,
          ),
        ],
        currency: CurrencyCode.yer,
        paymentType: PaymentType.partial,
        paidAmountMinor: 500000,
      );

      final batches = await repo.getInventoryBatches();
      expect(batches.single.buyPriceMinor, 220000);
      final sups = await repo.getSuppliers();
      expect(sups.single.balanceFor(CurrencyCode.yer), 1700000);

      // 2. Make sale with discount & tax deducting 2 bundles from batch
      final saleId = await repo.makeSale(
        customerId: custId,
        customerName: 'عميل عدن',
        items: [
          NewSaleItemInput(
            inventoryBatchId: batches.single.id,
            qatTypeName: 'أرحبي',
            qtyMilli: 2000,
            unit: 'ربطة',
            unitPriceMinor: 350000,
            buyPriceMinor: 220000,
          ),
        ],
        currency: CurrencyCode.yer,
        discountMinor: 50000, // 7000 - 500 = 6500
        taxBps: 0,
        paymentType: PaymentType.credit,
      );

      expect((await repo.getInventoryBatches()).single.remainingQtyMilli, 8000);
      expect((await repo.getCustomers()).single.balanceFor(CurrencyCode.yer), 650000);

      // Edit sale: increase quantity to 3 bundles @ 3600 YER, discount 300 YER -> total 10,500 YER, partial paid 2500 -> remaining 8000 YER
      await repo.editSale(
        saleId: saleId,
        customerId: custId,
        customerName: 'عميل عدن',
        newItems: [
          NewSaleItemInput(
            inventoryBatchId: batches.single.id,
            qatTypeName: 'أرحبي',
            qtyMilli: 3000,
            unit: 'ربطة',
            unitPriceMinor: 360000,
            buyPriceMinor: 220000,
          ),
        ],
        currency: CurrencyCode.yer,
        discountMinor: 30000,
        taxBps: 0,
        paymentType: PaymentType.partial,
        paidAmountMinor: 250000,
      );
      expect((await repo.getInventoryBatches()).single.remainingQtyMilli, 7000);
      expect((await repo.getCustomers()).single.balanceFor(CurrencyCode.yer), 800000);

      // Verify inventory valuation (7 bundles remaining @ 2200 buy / 3500 sell)
      final val = await repo.getInventoryValuation();
      expect(val[CurrencyCode.yer]?.totalQtyMilli, 7000);
      expect(val[CurrencyCode.yer]?.totalCostMinor, 1540000);
      expect(val[CurrencyCode.yer]?.totalRetailMinor, 2450000);

      // Delete sale -> restores stock back to 10.000 and customer debt back to 0
      await repo.deleteSale(saleId);
      expect((await repo.getInventoryBatches()).single.remainingQtyMilli, 10000);
      expect((await repo.getCustomers()).single.balanceFor(CurrencyCode.yer), 0);

      // 3. Transfer recipient report preserves status & separates currencies & links unlinked transfers
      final recId = await repo.upsertTransferRecipient(name: 'يحيى المستلم', company: 'الكريمي');
      await repo.addTransfer(
        senderName: 'الوكالة',
        recipientId: recId,
        amountMinor: 5000000,
        feeMinor: 15000,
        currency: CurrencyCode.yer,
        status: TransferStatus.delivered,
        dateMs: DateTime(2026, 11, 10).millisecondsSinceEpoch,
      );
      await repo.addTransfer(
        senderName: 'الوكالة',
        recipientId: recId,
        amountMinor: 30000,
        currency: CurrencyCode.sar,
        status: TransferStatus.pending,
        dateMs: DateTime(2026, 11, 15).millisecondsSinceEpoch,
      );
      final unlinkedId = await repo.addTransfer(
        senderName: 'الوكالة',
        recipientId: null,
        amountMinor: 100000,
        currency: CurrencyCode.yer,
        status: TransferStatus.cancelled,
        dateMs: DateTime(2026, 11, 15).millisecondsSinceEpoch,
      );
      await repo.linkTransferRecipient(transferId: unlinkedId, recipientId: recId);

      final rep = await repo.getRecipientReport(recId);
      expect(rep.deliveredCount, 1);
      expect(rep.pendingCount, 1);
      expect(rep.cancelledCount, 1);
      expect(rep.deliveredByCurrency[CurrencyCode.yer], 5000000);
      expect(rep.pendingByCurrency[CurrencyCode.sar], 30000);

      final customRange = await repo.getRecipientReport(
        recId,
        fromIso: '2026-11-14',
        toIso: '2026-11-16',
      );
      expect(customRange.totalTransfers, 2);

      // Expenses + tax + multi-currency customer statement
      final expId = await repo.addExpense(
        description: 'إيجار يومي',
        category: 'إيجار',
        amountMinor: 100000,
        currency: CurrencyCode.yer,
      );
      await repo.setArchived('expenses', expId, true);
      expect(await repo.getExpenses(includeArchived: false), isEmpty);
      expect((await repo.getExpenses(includeArchived: true)).length, 1);
      await repo.setArchived('expenses', expId, false);
      expect((await repo.getExpenses(includeArchived: false)).length, 1);

      await repo.addTaxEntry(
        totalQtyMilli: 5000,
        taxRateMinor: 10000,
        taxAmountMinor: 50000,
        currency: CurrencyCode.sar,
      );
      final metrics = await repo.getDashboardMetrics();
      expect(metrics[CurrencyCode.sar]?.taxMinor, 50000);
      expect(metrics[CurrencyCode.yer]?.expensesMinor, 100000);

      // 4. Atomic .qbak backup + wipe + SHA-256 verified restore
      final backupDir = p.join(tempDir.path, 'backups');
      final backup = await backupService.createAtomicBackup(outputDir: backupDir);
      expect(File(backup.filePath).existsSync(), true);

      // Mutate live DB (delete recipient) then restore
      await repo.deleteTransferRecipient(recId);
      expect(await repo.getTransferRecipients(), isEmpty);

      final restored = await backupService.restoreFromFile(backup.filePath);
      expect(restored.verifiedSha256, backup.dbSha256);
      expect((await repo.getTransferRecipients()).length, 1);

      // 5. Physical inventory reconciliation & daily cash settlement persistence
      final currentBatches = await repo.getInventoryBatches();
      expect(currentBatches.single.remainingQtyMilli, 10000);
      await repo.applyInventoryReconciliation(
        counts: [(batch: currentBatches.single, countedQtyMilli: 9500)],
        notes: 'تلف ربطة ونصف أثناء الفرز',
        createdBy: 'admin',
      );
      final updatedBatches = await repo.getInventoryBatches();
      expect(updatedBatches.single.remainingQtyMilli, 9500);
      final reconLogs = await repo.getInventoryReconciliations();
      expect(reconLogs.length, 1);
      expect(reconLogs.single.diffQtyMilli, -500);
      expect(reconLogs.single.financialImpactMinor, -110000);

      await repo.saveDailySettlement(
        const DailySettlementSnapshot(
          id: 'test_settle_1',
          dateIso: '2026-11-16',
          epochMs: 1794800000000,
          currency: CurrencyCode.yer,
          expectedCashMinor: 500000,
          actualCountedMinor: 495000,
          varianceMinor: -5000,
          salesCashMinor: 600000,
          customerReceiptsMinor: 0,
          purchasesCashMinor: 0,
          supplierPaymentsMinor: 0,
          expensesMinor: 100000,
          transferFeesMinor: 0,
          pendingTransfersCount: 0,
          notes: 'إغلاق تجريبي',
          createdBy: 'admin',
        ),
      );
      final settlements = await repo.getDailySettlements();
      expect(settlements.length, 1);
      expect(settlements.single.varianceMinor, -5000);

      await dbService.close();
    } finally {
      await tempDir.delete(recursive: true);
    }
    });
  });
}
