import 'dart:io';

import 'package:archive/archive.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:image/image.dart' as img;
import 'package:qat_ledger/core/money.dart';
import 'package:qat_ledger/core/theme.dart';
import 'package:qat_ledger/data/models.dart';
import 'package:qat_ledger/services/esc_pos_service.dart';
import 'package:qat_ledger/services/pdf_export_service.dart';
import 'package:qat_ledger/services/spreadsheet_export_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('PDF export service builds valid Arabic PDF documents with bundled IBMPlexSansArabic', () async {
    const agency = AgencySettings(
      agencyName: 'وكالة ميزان القات',
      agencyPhone: '777000111',
      agencyAddress: 'صنعاء',
      footerNote: 'شكراً لتعاملكم',
      logoPath: '',
      currencyMode: CurrencyMode.dualCurrency,
      displaySarToYerMilli: 0,
      themeChoice: AppThemeModeChoice.lightEnterprise,
      onboardingComplete: true,
      savedPrinterName: '',
      savedPrinterAddress: '',
      printerPaperMm: 80,
    );

    const sale = Sale(
      id: 1,
      invoiceNumber: 'INV-20261115-0001',
      customerId: 1,
      customerName: 'محمد الهمداني',
      dateMs: 1763200000000,
      dateIso: '2026-11-15',
      currency: CurrencyCode.yer,
      subtotalMinor: 1000000,
      discountMinor: 50000,
      taxBps: 500,
      taxAmountMinor: 47500,
      totalAmountMinor: 997500,
      paidAmountMinor: 500000,
      remainingAmountMinor: 497500,
      paymentType: PaymentType.partial,
      status: 'دفع جزئي',
      notes: 'فاتورة اختبارية',
      isArchived: false,
      items: [
        SaleItem(
          id: 1,
          saleId: 1,
          inventoryBatchId: 1,
          qatTypeName: 'همداني',
          qtyMilli: 2000,
          unit: 'ربطة',
          unitPriceMinor: 500000,
          buyPriceMinor: 350000,
          lineTotalMinor: 1000000,
          currency: CurrencyCode.yer,
        ),
      ],
    );

    final pdfBytes = await PdfExportService.instance.buildSaleInvoicePdf(
      agency: agency,
      sale: sale,
    );
    expect(pdfBytes.length, greaterThan(1500));
    expect(String.fromCharCodes(pdfBytes.take(5)), '%PDF-');

    const summary = TransferRecipientSummary(
      recipient: TransferRecipient(
        id: 1,
        name: 'عادل المستلم',
        phone: '771222333',
        company: 'الكريمي',
        notes: '',
        createdAtMs: 1763200000000,
      ),
      totalTransfers: 1,
      deliveredCount: 1,
      pendingCount: 0,
      cancelledCount: 0,
      deliveredByCurrency: {CurrencyCode.yer: 2500000},
      pendingByCurrency: {},
      cancelledByCurrency: {},
      feesByCurrency: {CurrencyCode.yer: 5000},
      transfers: [
        MoneyTransfer(
          id: 1,
          senderName: 'الوكالة',
          receiverName: 'عادل المستلم',
          recipientId: 1,
          amountMinor: 2500000,
          feeMinor: 5000,
          currency: CurrencyCode.yer,
          transferCompany: 'الكريمي',
          transferNumber: 'KR-99',
          dateMs: 1763200000000,
          dateIso: '2026-11-15',
          status: TransferStatus.delivered,
          notes: '',
          isArchived: false,
        ),
      ],
    );

    final recPdf = await PdfExportService.instance.buildTransferRecipientReportPdf(
      agency: agency,
      summary: summary,
    );
    expect(String.fromCharCodes(recPdf.take(5)), '%PDF-');
  });

  test('Spreadsheet export generates valid XLSX and UTF-8 BOM CSV, and ESC/POS raster supports 58mm & 80mm', () async {
    final tempDir = await Directory.systemTemp.createTemp('qat_export_test_');
    try {
      const transfers = [
        MoneyTransfer(
          id: 1,
          senderName: 'الوكالة',
          receiverName: 'ياسر',
          recipientId: 1,
          amountMinor: 500000,
          feeMinor: 1000,
          currency: CurrencyCode.sar,
          transferCompany: 'النجم',
          transferNumber: 'NJ-10',
          dateMs: 1763200000000,
          dateIso: '2026-11-15',
          status: TransferStatus.pending,
          notes: 'قيد التسليم',
          isArchived: false,
        ),
      ];

      final xlsxPath = await SpreadsheetExportService.instance.exportTransfersXlsx(
        transfers,
        outputDir: tempDir.path,
      );
      final xlsxBytes = await File(xlsxPath).readAsBytes();
      final decodedZip = ZipDecoder().decodeBytes(xlsxBytes, verify: true);
      expect(decodedZip.findFile('xl/workbook.xml'), isNotNull);

      final csvPath = await SpreadsheetExportService.instance.exportTransfersCsv(
        transfers,
        outputDir: tempDir.path,
      );
      final csvBytes = await File(csvPath).readAsBytes();
      expect(csvBytes.sublist(0, 3), [0xEF, 0xBB, 0xBF]);

      // ESC/POS 58mm (384px) & 80mm (576px) raster packet checks
      final canvas = img.Image(width: 200, height: 100);
      img.fill(canvas, color: img.ColorRgb8(255, 255, 255));
      img.fillRect(canvas, x1: 20, y1: 20, x2: 80, y2: 60, color: img.ColorRgb8(0, 0, 0));

      final pkt58 = EscPosService.encodeImageToEscPosRaster(canvas, targetWidthPx: 384);
      final pkt80 = EscPosService.encodeImageToEscPosRaster(canvas, targetWidthPx: 576);
      expect(pkt58.sublist(0, 5), [0x1B, 0x40, 0x1D, 0x76, 0x30]); // ESC @ + GS v 0
      expect(pkt58[5], 48); // 384 / 8 = 48 bytes per line
      expect(pkt80[5], 72); // 576 / 8 = 72 bytes per line
    } finally {
      await tempDir.delete(recursive: true);
    }
  });
}
