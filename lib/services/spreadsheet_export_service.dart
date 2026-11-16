import 'dart:convert';
import 'dart:io';

import 'package:excel/excel.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

import '../core/money.dart';
import '../data/models.dart';

class SpreadsheetExportService {
  SpreadsheetExportService._();
  static final SpreadsheetExportService instance = SpreadsheetExportService._();

  Future<String> exportSalesXlsx(List<Sale> sales, {String? outputDir}) async {
    final excel = Excel.createExcel();
    final sheet = excel['المبيعات'];
    excel.delete('Sheet1');
    sheet.appendRow([
      TextCellValue('رقم الفاتورة'),
      TextCellValue('التاريخ'),
      TextCellValue('العميل'),
      TextCellValue('العملة'),
      TextCellValue('المجموع الفرعي'),
      TextCellValue('الخصم'),
      TextCellValue('الضريبة'),
      TextCellValue('الإجمالي'),
      TextCellValue('المدفوع'),
      TextCellValue('المتبقي'),
      TextCellValue('الحالة'),
    ]);
    for (final s in sales) {
      sheet.appendRow([
        TextCellValue(s.invoiceNumber),
        TextCellValue(s.dateIso),
        TextCellValue(s.customerName),
        TextCellValue(s.currency.code),
        TextCellValue(Money.formatMinor(s.subtotalMinor, showZeroDecimals: true)),
        TextCellValue(Money.formatMinor(s.discountMinor, showZeroDecimals: true)),
        TextCellValue(Money.formatMinor(s.taxAmountMinor, showZeroDecimals: true)),
        TextCellValue(Money.formatMinor(s.totalAmountMinor, showZeroDecimals: true)),
        TextCellValue(Money.formatMinor(s.paidAmountMinor, showZeroDecimals: true)),
        TextCellValue(Money.formatMinor(s.remainingAmountMinor, showZeroDecimals: true)),
        TextCellValue(s.paymentType.arabicLabel),
      ]);
    }
    return _writeBytes(
      bytes: excel.encode()!,
      fileName: 'QatLedger_Sales_${DateTime.now().millisecondsSinceEpoch}.xlsx',
      outputDir: outputDir,
    );
  }

  Future<String> exportTransfersXlsx(
    List<MoneyTransfer> transfers, {
    String title = 'الحوالات',
    String? outputDir,
  }) async {
    final excel = Excel.createExcel();
    final sheet = excel[title];
    excel.delete('Sheet1');
    sheet.appendRow([
      TextCellValue('التاريخ'),
      TextCellValue('المرسل'),
      TextCellValue('المستلم'),
      TextCellValue('حالة الربط'),
      TextCellValue('المبلغ'),
      TextCellValue('العمولة'),
      TextCellValue('العملة'),
      TextCellValue('الشركة'),
      TextCellValue('رقم الحوالة'),
      TextCellValue('الحالة'),
      TextCellValue('ملاحظات'),
    ]);
    for (final t in transfers) {
      sheet.appendRow([
        TextCellValue(t.dateIso),
        TextCellValue(t.senderName),
        TextCellValue(t.receiverName.isEmpty ? 'غير محدد' : t.receiverName),
        TextCellValue(t.isUnlinked ? 'غير مرتبط بمستلم' : 'مرتبط'),
        TextCellValue(Money.formatMinor(t.amountMinor, showZeroDecimals: true)),
        TextCellValue(Money.formatMinor(t.feeMinor, showZeroDecimals: true)),
        TextCellValue(t.currency.code),
        TextCellValue(t.transferCompany),
        TextCellValue(t.transferNumber),
        TextCellValue(t.status.arabicLabel),
        TextCellValue(t.notes),
      ]);
    }
    return _writeBytes(
      bytes: excel.encode()!,
      fileName: 'QatLedger_Transfers_${DateTime.now().millisecondsSinceEpoch}.xlsx',
      outputDir: outputDir,
    );
  }

  Future<String> exportRecipientReportXlsx(
    TransferRecipientSummary summary, {
    String? outputDir,
  }) async {
    final excel = Excel.createExcel();
    final sumSheet = excel['ملخص المستلم'];
    excel.delete('Sheet1');
    sumSheet.appendRow([TextCellValue('المستلم'), TextCellValue(summary.recipient.name)]);
    sumSheet.appendRow([TextCellValue('الهاتف'), TextCellValue(summary.recipient.phone)]);
    sumSheet.appendRow([TextCellValue('الشركة'), TextCellValue(summary.recipient.company)]);
    sumSheet.appendRow([TextCellValue('إجمالي الحوالات'), IntCellValue(summary.totalTransfers)]);
    sumSheet.appendRow([TextCellValue('تم التسليم (عدد)'), IntCellValue(summary.deliveredCount)]);
    sumSheet.appendRow([TextCellValue('جاري التسليم (عدد)'), IntCellValue(summary.pendingCount)]);
    sumSheet.appendRow([TextCellValue('ملغي (عدد)'), IntCellValue(summary.cancelledCount)]);

    for (final cur in CurrencyCode.values) {
      sumSheet.appendRow([
        TextCellValue('المسلّم (${cur.code})'),
        TextCellValue(Money.formatMinor(summary.deliveredByCurrency[cur] ?? 0, showZeroDecimals: true)),
      ]);
      sumSheet.appendRow([
        TextCellValue('المعلق (${cur.code})'),
        TextCellValue(Money.formatMinor(summary.pendingByCurrency[cur] ?? 0, showZeroDecimals: true)),
      ]);
    }

    final detailSheet = excel['تفاصيل الحوالات'];
    detailSheet.appendRow([
      TextCellValue('التاريخ'),
      TextCellValue('المرسل'),
      TextCellValue('المبلغ'),
      TextCellValue('العمولة'),
      TextCellValue('العملة'),
      TextCellValue('الشركة'),
      TextCellValue('رقم الحوالة'),
      TextCellValue('الحالة'),
      TextCellValue('ملاحظات'),
    ]);
    for (final t in summary.transfers) {
      detailSheet.appendRow([
        TextCellValue(t.dateIso),
        TextCellValue(t.senderName),
        TextCellValue(Money.formatMinor(t.amountMinor, showZeroDecimals: true)),
        TextCellValue(Money.formatMinor(t.feeMinor, showZeroDecimals: true)),
        TextCellValue(t.currency.code),
        TextCellValue(t.transferCompany),
        TextCellValue(t.transferNumber),
        TextCellValue(t.status.arabicLabel),
        TextCellValue(t.notes),
      ]);
    }
    return _writeBytes(
      bytes: excel.encode()!,
      fileName: 'QatLedger_Recipient_${summary.recipient.id}_${DateTime.now().millisecondsSinceEpoch}.xlsx',
      outputDir: outputDir,
    );
  }

  /// Generates a UTF-8 with BOM CSV file so Excel opens Arabic columns cleanly without mojibake.
  Future<String> exportTransfersCsv(List<MoneyTransfer> transfers, {String? outputDir}) async {
    final rows = <List<String>>[
      [
        'التاريخ',
        'المرسل',
        'المستلم',
        'المبلغ',
        'العمولة',
        'العملة',
        'الشركة',
        'رقم الحوالة',
        'الحالة',
        'ملاحظات',
      ],
      ...transfers.map((t) => [
            t.dateIso,
            t.senderName,
            t.receiverName.isEmpty ? 'غير محدد' : t.receiverName,
            Money.formatMinor(t.amountMinor, showZeroDecimals: true),
            Money.formatMinor(t.feeMinor, showZeroDecimals: true),
            t.currency.code,
            t.transferCompany,
            t.transferNumber,
            t.status.arabicLabel,
            t.notes,
          ]),
    ];
    final csvText = rows.map((r) => r.map(_escapeCsv).join(',')).join('\r\n');
    final bomUtf8 = <int>[0xEF, 0xBB, 0xBF, ...utf8.encode(csvText)];
    return _writeBytes(
      bytes: bomUtf8,
      fileName: 'QatLedger_Transfers_${DateTime.now().millisecondsSinceEpoch}.csv',
      outputDir: outputDir,
    );
  }

  String _escapeCsv(String val) {
    final cleaned = val.replaceAll('"', '""');
    return '"$cleaned"';
  }

  Future<String> _writeBytes({
    required List<int> bytes,
    required String fileName,
    String? outputDir,
  }) async {
    final dirPath = outputDir ?? (await getTemporaryDirectory()).path;
    await Directory(dirPath).create(recursive: true);
    final outPath = p.join(dirPath, fileName);
    await File(outPath).writeAsBytes(bytes, flush: true);
    return outPath;
  }
}
