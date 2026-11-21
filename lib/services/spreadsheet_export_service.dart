import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:archive/archive.dart';
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
    const headers = [
      'رقم الفاتورة',
      'التاريخ',
      'العميل',
      'العملة',
      'المجموع الفرعي',
      'الخصم',
      'الضريبة',
      'الإجمالي',
      'المدفوع',
      'المتبقي',
      'الحالة',
    ];
    sheet.appendRow(<CellValue?>[for (final h in headers) TextCellValue(h)]);
    for (final s in sales) {
      sheet.appendRow(<CellValue?>[
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
      bytes: applyRtlToXlsxBytes(excel.encode()!),
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
    const headers = [
      'التاريخ',
      'المرسل',
      'المستلم',
      'حالة الربط',
      'المبلغ',
      'العمولة',
      'العملة',
      'الشركة',
      'رقم الحوالة',
      'الحالة',
      'ملاحظات',
    ];
    sheet.appendRow(<CellValue?>[for (final h in headers) TextCellValue(h)]);
    for (final t in transfers) {
      sheet.appendRow(<CellValue?>[
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
      bytes: applyRtlToXlsxBytes(excel.encode()!),
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

    void addSummaryRow(String label, CellValue value) {
      sumSheet.appendRow(<CellValue?>[TextCellValue(label), value]);
    }

    addSummaryRow('المستلم', TextCellValue(summary.recipient.name));
    addSummaryRow('الهاتف', TextCellValue(summary.recipient.phone));
    addSummaryRow('الشركة', TextCellValue(summary.recipient.company));
    addSummaryRow('إجمالي الحوالات', IntCellValue(summary.totalTransfers));
    addSummaryRow('تم التسليم (عدد)', IntCellValue(summary.deliveredCount));
    addSummaryRow('جاري التسليم (عدد)', IntCellValue(summary.pendingCount));
    addSummaryRow('ملغي (عدد)', IntCellValue(summary.cancelledCount));

    for (final cur in CurrencyCode.values) {
      sumSheet.appendRow(<CellValue?>[
        TextCellValue('المسلّم (${cur.code})'),
        TextCellValue(Money.formatMinor(summary.deliveredByCurrency[cur] ?? 0, showZeroDecimals: true)),
      ]);
      sumSheet.appendRow(<CellValue?>[
        TextCellValue('المعلق (${cur.code})'),
        TextCellValue(Money.formatMinor(summary.pendingByCurrency[cur] ?? 0, showZeroDecimals: true)),
      ]);
    }

    final detailSheet = excel['تفاصيل الحوالات'];
    const detailHeaders = [
      'التاريخ',
      'المرسل',
      'المبلغ',
      'العمولة',
      'العملة',
      'الشركة',
      'رقم الحوالة',
      'الحالة',
      'ملاحظات',
    ];
    detailSheet.appendRow(<CellValue?>[for (final h in detailHeaders) TextCellValue(h)]);
    for (final t in summary.transfers) {
      detailSheet.appendRow(<CellValue?>[
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
      bytes: applyRtlToXlsxBytes(excel.encode()!),
      fileName: 'QatLedger_Recipient_${summary.recipient.id}_${DateTime.now().millisecondsSinceEpoch}.xlsx',
      outputDir: outputDir,
    );
  }

  /// Injects `rightToLeft="1"` on every worksheet `<sheetView>` inside an OOXML `.xlsx` archive
  /// so Excel / LibreOffice / Calc render Arabic workbooks natively right-to-left.
  static Uint8List applyRtlToXlsxBytes(List<int> rawBytes) {
    final archive = ZipDecoder().decodeBytes(rawBytes);
    final out = Archive();
    for (final file in archive.files) {
      if (!file.isFile) {
        out.addFile(ArchiveFile.noCompress(file.name, 0, const <int>[]));
        continue;
      }
      if (file.name.startsWith('xl/worksheets/sheet') &&
          file.name.endsWith('.xml')) {
        var xml = utf8.decode(file.content as List<int>);
        if (xml.contains('<sheetView ')) {
          if (!xml.contains('rightToLeft=')) {
            xml = xml.replaceFirst(
              '<sheetView ',
              '<sheetView rightToLeft="1" ',
            );
          }
        } else if (xml.contains('<sheetViews/>')) {
          xml = xml.replaceFirst(
            '<sheetViews/>',
            '<sheetViews><sheetView rightToLeft="1" workbookViewId="0"/></sheetViews>',
          );
        } else if (xml.contains('<sheetViews>')) {
          xml = xml.replaceFirst(
            '<sheetViews>',
            '<sheetViews><sheetView rightToLeft="1" workbookViewId="0"/>',
          );
        } else {
          xml = xml.replaceFirstMapped(
            RegExp(r'<worksheet[^>]*>'),
            (m) =>
                '${m[0]}<sheetViews><sheetView rightToLeft="1" workbookViewId="0"/></sheetViews>',
          );
        }
        final encoded = utf8.encode(xml);
        out.addFile(ArchiveFile(file.name, encoded.length, encoded));
      } else {
        final content = file.content as List<int>;
        out.addFile(ArchiveFile(file.name, content.length, content));
      }
    }
    return Uint8List.fromList(ZipEncoder().encode(out)!);
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
