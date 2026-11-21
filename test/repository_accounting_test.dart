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

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  sqfliteFfiInit();

  test('repository full accounting lifecycle: purchase edit, sale stock+debt, recipient report, atomic .qbak backup/restore', () async {
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

      // 6. Date-filtered customer/supplier statements with opening balance, plus updates & permanent archive deletion
      await repo.makeSale(
        customerId: custId,
        customerName: 'عميل عدن',
        items: [
          NewSaleItemInput(
            inventoryBatchId: updatedBatches.single.id,
            qatTypeName: 'أرحبي',
            qtyMilli: 1000,
            unit: 'ربطة',
            unitPriceMinor: 350000,
            buyPriceMinor: 220000,
          ),
        ],
        currency: CurrencyCode.yer,
        paymentType: PaymentType.credit,
        dateMs: DateTime(2026, 11, 10).millisecondsSinceEpoch,
      );
      await repo.addCustomerPayment(
        customerId: custId,
        customerName: 'عميل عدن',
        amountMinor: 100000,
        currency: CurrencyCode.yer,
        dateMs: DateTime(2026, 11, 15).millisecondsSinceEpoch,
      );
      final stmt = await repo.getCustomerStatement(
        custId,
        fromIso: '2026-11-12',
        toIso: '2026-11-16',
      );
      expect(stmt[CurrencyCode.yer]?.first.reference, 'OPEN');
      expect(stmt[CurrencyCode.yer]?.first.runningBalanceMinor, 350000);
      expect(stmt[CurrencyCode.yer]?.last.runningBalanceMinor, 250000);

      await repo.updateExpense(
        id: expId,
        description: 'إيجار محدث',
        category: 'إيجار',
        amountMinor: 120000,
        currency: CurrencyCode.yer,
      );
      expect((await repo.getExpenses()).single.amountMinor, 120000);

      final iwId = await repo.addImportWeight(weightMilli: 15000, receiverName: 'سالم');
      await repo.updateImportWeight(id: iwId, weightMilli: 18500, receiverName: 'سالم المحدث');
      expect((await repo.getImportWeights()).single.weightMilli, 18500);

      await repo.setArchived('expenses', expId, true);
      await repo.deletePermanently('expenses', expId);
      expect(await repo.getExpenses(includeArchived: true), isEmpty);

      // 7. Direct customer debt, customer payment edit, supplier return, tax outflows & transfer update
      final directDebtSaleId = await repo.addDirectCustomerDebt(
        customerId: custId,
        customerName: 'عميل عدن',
        amountMinor: 50000,
        currency: CurrencyCode.yer,
        statement: 'دين مباشر للاختبار',
      );
      expect(directDebtSaleId, greaterThan(0));
      expect((await repo.getCustomers()).single.balanceFor(CurrencyCode.yer), 300000);

      final custPays = await repo.getCustomerPayments(customerId: custId);
      await repo.updateCustomerPayment(
        paymentId: custPays.single.id,
        amountMinor: 150000,
        currency: CurrencyCode.yer,
        paymentMethod: 'تحويل',
      );
      expect((await repo.getCustomers()).single.balanceFor(CurrencyCode.yer), 250000);

      await repo.addSupplierReturn(
        supplierId: supId,
        supplierName: 'مورد صنعاء',
        amountMinor: 200000,
        currency: CurrencyCode.yer,
      );
      expect((await repo.getSuppliers()).single.balanceFor(CurrencyCode.yer), 1500000);

      final taxList = await repo.getTaxEntries();
      await repo.updateTaxEntry(
        id: taxList.single.id,
        taxAmountMinor: 55000,
        stallOutflowMinor: 10000,
        laborOutflowMinor: 15000,
      );
      final updatedTax = (await repo.getTaxEntries()).single;
      expect(updatedTax.totalOutflowMinor, 80000);

      await repo.setTransferRecipientActive(recId, false);
      expect((await repo.getTransferRecipients()).single.isActive, false);

      // 8. InventoryReceiver CRUD, DailyInventoryMatching, and custom dateMs persistence
      final recvId = await repo.upsertInventoryReceiver(
        name: 'سالم أمين المستودع',
        phone: '777888999',
        notes: 'وردية الصباح',
      );
      expect((await repo.getInventoryReceivers()).single.name, 'سالم أمين المستودع');

      final customMs = DateTime(2026, 10, 15, 11, 30).millisecondsSinceEpoch;
      final batchDatedId = await repo.addInventoryBatch(
        qatTypeName: 'همداني ممتاز',
        qtyMilli: 5000,
        unit: 'ربطة',
        buyPriceMinor: 100000,
        sellPriceMinor: 150000,
        currency: CurrencyCode.yer,
        receiverId: recvId,
        receiverName: 'سالم أمين المستودع',
      );
      await repo.addImportWeight(
        weightMilli: 12500,
        receiverName: 'سالم أمين المستودع',
        dateMs: customMs,
      );
      final datedSaleId = await repo.makeSale(
        customerId: custId,
        customerName: 'عميل عدن',
        items: [
          NewSaleItemInput(
            inventoryBatchId: batchDatedId,
            qatTypeName: 'همداني ممتاز',
            qtyMilli: 2000,
            unit: 'ربطة',
            unitPriceMinor: 150000,
            buyPriceMinor: 100000,
          ),
        ],
        currency: CurrencyCode.yer,
        paymentType: PaymentType.paid,
        paidAmountMinor: 300000,
        paymentMethod: 'نقداً',
        dateMs: DateTime(2026, 10, 14, 11, 30).millisecondsSinceEpoch,
      );
      await repo.editSale(
        saleId: datedSaleId,
        customerId: custId,
        customerName: 'عميل عدن',
        newItems: [
          NewSaleItemInput(
            inventoryBatchId: batchDatedId,
            qatTypeName: 'همداني ممتاز',
            qtyMilli: 1000,
            unit: 'ربطة',
            unitPriceMinor: 150000,
            buyPriceMinor: 100000,
          ),
        ],
        currency: CurrencyCode.yer,
        paymentType: PaymentType.paid,
        paidAmountMinor: 150000,
        paymentMethod: 'نقداً',
        dateMs: customMs,
      );
      final updatedDatedSale =
          (await repo.getSales()).firstWhere((s) => s.id == datedSaleId);
      expect(updatedDatedSale.dateIso, '2026-10-15');

      final matching = await repo.getDailyInventoryMatching(
        fromIso: '2026-10-15',
        toIso: '2026-10-15',
      );
      expect(matching.importedWeightMilli, 12500);
      expect(matching.soldQtyMilli, 1000);
      expect(matching.cashSalesMinorByCurrency[CurrencyCode.yer], 150000);

      await repo.deleteInventoryReceiver(recvId);
      expect(await repo.getInventoryReceivers(), isEmpty);

      // 9. Supplier return ('مرتجع') excludes cash outflow, Direct debt ('DEBT-*') excludes retail sales,
      //    DailyChartPoint deducts COGS, and deleteSupplierPurchase cleans linked stock batches.
      final freshSupId = await repo.upsertSupplier(name: 'مورد المرتجع والحذف');
      final freshPurId = await repo.makeSupplierPurchase(
        supplierId: freshSupId,
        supplierName: 'مورد المرتجع والحذف',
        items: const [
          NewPurchaseItemInput(
            qatTypeName: 'صنف حذفي',
            qtyMilli: 4000,
            unit: 'ربطة',
            buyPriceMinor: 50000,
            sellPriceMinor: 80000,
          ),
        ],
        currency: CurrencyCode.yer,
        paymentType: PaymentType.credit,
        dateMs: customMs,
      );
      final batchesBeforeDel = (await repo.getInventoryBatches())
          .where((b) => b.supplierId == freshSupId)
          .toList();
      expect(batchesBeforeDel.length, 1);

      await repo.addSupplierReturn(
        supplierId: freshSupId,
        supplierName: 'مورد المرتجع والحذف',
        amountMinor: 50000,
        currency: CurrencyCode.yer,
        inventoryBatchId: batchesBeforeDel.first.id,
        returnedQtyMilli: 1000,
        itemName: 'صنف حذفي',
        dateMs: customMs,
      );

      final freshCustId = await repo.upsertCustomer(name: 'عميل الدين المباشر');
      await repo.addDirectCustomerDebt(
        customerId: freshCustId,
        customerName: 'عميل الدين المباشر',
        amountMinor: 75000,
        currency: CurrencyCode.yer,
        statement: 'دين مباشر قديم',
        dateMs: customMs,
      );

      final dayMetrics = await repo.getDashboardMetrics(
        fromIso: '2026-10-15',
        toIso: '2026-10-15',
      );
      final mYer = dayMetrics[CurrencyCode.yer]!;
      // Only the 150000 retail sale counts in salesTotalMinor (NOT the 75000 direct debt)
      expect(mYer.salesTotalMinor, 150000);
      // Supplier return ('مرتجع') must NOT inflate cash-box supplierPaymentsMinor
      expect(mYer.supplierPaymentsMinor, 0);
      // Direct debt must still count in customer debt balance
      expect(
        (await repo.getCustomers())
            .firstWhere((c) => c.id == freshCustId)
            .balanceFor(CurrencyCode.yer),
        75000,
      );

      final dayChart = await repo.getDailyChartSeries(
        currency: CurrencyCode.yer,
        fromIso: '2026-10-15',
        toIso: '2026-10-15',
      );
      expect(dayChart.single.salesMinor, 150000);
      expect(dayChart.single.cogsMinor, 100000);
      expect(dayChart.single.profitMinor, 50000);

      await repo.deleteSupplierPurchase(freshPurId);
      final batchesAfterDel = (await repo.getInventoryBatches())
          .where((b) => b.supplierId == freshSupId)
          .toList();
      expect(batchesAfterDel, isEmpty);

      // 10. FIFO multi-batch deduction + restore, receiver/importWeight purchase link,
      //     worker handoffs + worker statement, aggregate statements, and 14-table archiving.
      final fifoSupId = await repo.upsertSupplier(name: 'مورد الدفعات المتتابعة');
      final fifoRecvId = await repo.upsertInventoryReceiver(
        name: 'أمين مستلم ١',
        phone: '777001122',
      );
      final fifoWeightId = await repo.addImportWeight(
        weightMilli: 25000,
        receiverName: 'أمين مستلم ١',
      );
      final fifoPurId = await repo.makeSupplierPurchase(
        supplierId: fifoSupId,
        supplierName: 'مورد الدفعات المتتابعة',
        receiverId: fifoRecvId,
        receiverName: 'أمين مستلم ١',
        importWeightId: fifoWeightId,
        items: const [
          NewPurchaseItemInput(
            qatTypeName: 'صنف فيفو',
            qtyMilli: 3000,
            unit: 'ربطة',
            buyPriceMinor: 100000,
            sellPriceMinor: 150000,
          ),
        ],
        currency: CurrencyCode.yer,
        paymentType: PaymentType.credit,
        dateMs: DateTime(2026, 11, 1).millisecondsSinceEpoch,
      );
      final secondBatchId = await repo.addInventoryBatch(
        qatTypeName: 'صنف فيفو',
        qtyMilli: 4000,
        unit: 'ربطة',
        buyPriceMinor: 110000,
        sellPriceMinor: 150000,
        currency: CurrencyCode.yer,
        supplierId: fifoSupId,
        supplierName: 'مورد الدفعات المتتابعة',
        dateMs: DateTime(2026, 11, 2).millisecondsSinceEpoch,
      );
      // Sell 5.000 bundles via automatic FIFO (deducts 3.000 from oldest batch + 2.000 from second batch)
      final fifoSaleId = await repo.makeSale(
        customerId: freshCustId,
        customerName: 'عميل الدين المباشر',
        items: const [
          NewSaleItemInput(
            inventoryBatchId: null,
            deductByFifo: true,
            qatTypeName: 'صنف فيفو',
            qtyMilli: 5000,
            unit: 'ربطة',
            unitPriceMinor: 150000,
            buyPriceMinor: 0,
          ),
        ],
        currency: CurrencyCode.yer,
        paymentType: PaymentType.paid,
        paidAmountMinor: 750000,
      );
      final batchesAfterFifo = (await repo.getInventoryBatches())
          .where((b) => b.qatTypeName == 'صنف فيفو')
          .toList()
        ..sort((a, b) => a.purchaseDateMs.compareTo(b.purchaseDateMs));
      expect(batchesAfterFifo[0].remainingQtyMilli, 0);
      expect(batchesAfterFifo[1].id, secondBatchId);
      expect(batchesAfterFifo[1].remainingQtyMilli, 2000);

      // Deleting purchase linked to a batch that already has retail sales must throw StateError
      await expectLater(
        () => repo.deleteSupplierPurchase(fifoPurId),
        throwsStateError,
      );

      // Deleting the FIFO sale restores exact deductions across both batches!
      await repo.deleteSale(fifoSaleId);
      final batchesRestored = (await repo.getInventoryBatches())
          .where((b) => b.qatTypeName == 'صنف فيفو')
          .toList()
        ..sort((a, b) => a.purchaseDateMs.compareTo(b.purchaseDateMs));
      expect(batchesRestored[0].remainingQtyMilli, 3000);
      expect(batchesRestored[1].remainingQtyMilli, 4000);

      // Worker handoffs & worker statement
      final wh1 = await repo.addWorkerHandoff(
        workerName: 'نبيل العامل',
        receiverId: fifoRecvId,
        direction: WorkerHandoffDirection.toWorker,
        amountMinor: 500000,
        currency: CurrencyCode.yer,
        notes: 'عهدة صباحية',
      );
      final wh2 = await repo.addWorkerHandoff(
        workerName: 'نبيل العامل',
        receiverId: fifoRecvId,
        direction: WorkerHandoffDirection.fromWorker,
        amountMinor: 350000,
        currency: CurrencyCode.yer,
        notes: 'توريد مبيعات',
      );
      expect(await repo.getDistinctWorkerNames(), contains('نبيل العامل'));
      final workerStmt = await repo.getWorkerStatement('نبيل العامل');
      expect(workerStmt[CurrencyCode.yer]?.last.runningBalanceMinor, 150000);

      // Aggregate statements across all customers & all suppliers
      final allCustStmt = await repo.getAllCustomersStatement();
      final allSupStmt = await repo.getAllSuppliersStatement();
      expect(allCustStmt[CurrencyCode.yer], isNotEmpty);
      expect(allSupStmt[CurrencyCode.yer], isNotEmpty);

      // Archive support across newly expanded entities (worker_handoffs, inventory_receivers, customers)
      await repo.setArchived('worker_handoffs', wh1, true);
      expect((await repo.getWorkerHandoffs(includeArchived: false)).length, 1);
      await repo.setArchived('worker_handoffs', wh1, false);
      expect((await repo.getWorkerHandoffs(includeArchived: false)).length, 2);
      await repo.deleteWorkerHandoff(wh2);
      expect((await repo.getWorkerHandoffs(includeArchived: false)).length, 1);

      // BackupService.resetAllAccountingData wipes transactional tables while keeping safety snapshot
      final resetSafety = await backupService.resetAllAccountingData(
        rollbackDir: p.join(tempDir.path, 'safety_resets'),
        fullFactoryReset: false,
      );
      expect(File(resetSafety.filePath).existsSync(), true);
      expect(await repo.getSales(), isEmpty);
      expect(await repo.getWorkerHandoffs(includeArchived: true), isEmpty);

      await dbService.close();
    } finally {
      await tempDir.delete(recursive: true);
    }
  });
}
