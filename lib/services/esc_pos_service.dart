import 'dart:typed_data';

import 'package:image/image.dart' as img;
import 'package:pdf/pdf.dart';
import 'package:printing/printing.dart';

import '../core/date_utils.dart';
import '../core/money.dart';
import '../core/platform_bridge.dart';
import '../data/models.dart';
import 'pdf_export_service.dart';

/// Builds crisp 58mm/80mm thermal receipts by rasterizing Arabic PDF layouts
/// into ESC/POS `GS v 0` monochrome bitmap packets so Arabic renders accurately
/// on thermal Bluetooth printers regardless of hardware codepage support.
class EscPosService {
  EscPosService._();
  static final EscPosService instance = EscPosService._();

  /// Encodes an RGBA/grayscale image into ESC/POS `GS v 0` raster command + paper feed/cut.
  static Uint8List encodeImageToEscPosRaster(img.Image source, {int targetWidthPx = 384}) {
    final resized = source.width == targetWidthPx
        ? source
        : img.copyResize(source, width: targetWidthPx);
    final widthBytes = (resized.width + 7) ~/ 8;
    final height = resized.height;

    final out = BytesBuilder();
    // Initialize printer: ESC @
    out.add(const [0x1B, 0x40]);
    // GS v 0 m xL xH yL yH
    out.add([
      0x1D,
      0x76,
      0x30,
      0x00,
      widthBytes & 0xFF,
      (widthBytes >> 8) & 0xFF,
      height & 0xFF,
      (height >> 8) & 0xFF,
    ]);

    for (int y = 0; y < height; y++) {
      for (int bx = 0; bx < widthBytes; bx++) {
        int byteVal = 0;
        for (int bit = 0; bit < 8; bit++) {
          final x = bx * 8 + bit;
          if (x < resized.width) {
            final pixel = resized.getPixel(x, y);
            final luma = img.getLuminance(pixel);
            if (luma < 145) {
              byteVal |= (1 << (7 - bit));
            }
          }
        }
        out.addByte(byteVal);
      }
    }

    // Feed 4 lines + partial cut (GS V 66 0)
    out.add(const [0x1B, 0x64, 0x04, 0x1D, 0x56, 0x42, 0x00]);
    return out.toBytes();
  }

  Future<bool> printPdfBytes({
    required Uint8List pdfBytes,
    required String printerAddress,
    int paperMm = 80,
  }) async {
    final addr = printerAddress.trim();
    if (addr.isEmpty) {
      throw StateError(
        'لم يتم اختيار طابعة حرارية. يرجى اختيار طابعة من إعدادات النسخ والطباعة أولاً.',
      );
    }
    final targetWidthPx = paperMm <= 58 ? 384 : 576;
    final pages = <Uint8List>[];

    await for (final page in Printing.raster(pdfBytes, dpi: 203)) {
      final pngBytes = await page.toPng();
      final decoded = img.decodePng(pngBytes);
      if (decoded != null) {
        pages.add(
          encodeImageToEscPosRaster(decoded, targetWidthPx: targetWidthPx),
        );
      }
    }

    if (pages.isEmpty) {
      throw StateError('فشل تجهيز بيانات الطباعة الحرارية.');
    }
    if (pages.length == 1) {
      return PlatformBridge.instance.printEscPosBytes(addr, pages.first);
    }
    var ok = true;
    for (int i = 0; i < pages.length; i++) {
      final sent = await PlatformBridge.instance.printEscPosBytes(addr, pages[i]);
      ok = ok && sent;
      if (i + 1 < pages.length) {
        await Future<void>.delayed(const Duration(milliseconds: 140));
      }
    }
    return ok;
  }

  Future<bool> printSaleReceipt({
    required AgencySettings agency,
    required Sale sale,
    required String printerAddress,
    int paperMm = 80,
  }) async {
    final rollFormat = paperMm <= 58 ? PdfPageFormat.roll57 : PdfPageFormat.roll80;
    final pdfBytes = await PdfExportService.instance.buildSaleInvoicePdf(
      agency: agency,
      sale: sale,
      format: rollFormat,
    );
    return printPdfBytes(
      pdfBytes: pdfBytes,
      printerAddress: printerAddress,
      paperMm: paperMm,
    );
  }

  Future<bool> printTextReceipt({
    required AgencySettings agency,
    required String title,
    required String receiptText,
    required String printerAddress,
    int paperMm = 80,
  }) async {
    final rollFormat = paperMm <= 58 ? PdfPageFormat.roll57 : PdfPageFormat.roll80;
    final pdfBytes = await PdfExportService.instance.buildTextReceiptPdf(
      agency: agency,
      title: title,
      lines: receiptText.split('\n'),
      format: rollFormat,
    );
    return printPdfBytes(
      pdfBytes: pdfBytes,
      printerAddress: printerAddress,
      paperMm: paperMm,
    );
  }
}

/// Centralized Arabic 58mm (32-col) / 80mm (48-col) thermal & preview receipt builders.
abstract final class ThermalReceiptFormatter {
  static int columnsForMm(int mm) => mm >= 80 ? 48 : 32;

  static String buildSaleReceiptText(AgencySettings agency, Sale sale, {int paperMm = 80}) {
    final sb = StringBuffer()
      ..writeln(agency.agencyName)
      ..writeln('فاتورة مبيعات: ${sale.invoiceNumber}')
      ..writeln('التاريخ: ${QatDateUtils.formatArabicDateTime12h(sale.dateMs)}')
      ..writeln('العميل: ${sale.customerName}')
      ..writeln('العملة: ${sale.currency.arabicName}')
      ..writeln('طريقة الدفع: ${sale.effectivePaymentMethodLabel}')
      ..writeln('--------------------------------');
    for (final it in sale.items) {
      sb.writeln(
        '${it.qatTypeName} (${QuantityMath.formatMilli(it.qtyMilli)} ${it.unit}) = ${Money(it.lineTotalMinor, sale.currency).format()}',
      );
    }
    sb
      ..writeln('--------------------------------')
      ..writeln('الإجمالي: ${Money(sale.totalAmountMinor, sale.currency).format()}')
      ..writeln('المدفوع: ${Money(sale.paidAmountMinor, sale.currency).format()}')
      ..writeln('المتبقي: ${Money(sale.remainingAmountMinor, sale.currency).format()}');
    if (sale.notes.isNotEmpty) sb.writeln('ملاحظات: ${sale.notes}');
    if (agency.footerEnabled && agency.footerNote.isNotEmpty) {
      sb.writeln('--------------------------------');
      sb.writeln(agency.footerNote);
    }
    return sb.toString().trim();
  }

  static String buildTransferVoucherText(
    AgencySettings agency,
    MoneyTransfer transfer, {
    TransferRecipient? recipient,
  }) {
    final sb = StringBuffer()
      ..writeln(agency.agencyName)
      ..writeln('سند حوالة مالية (#${transfer.id})')
      ..writeln('التاريخ: ${QatDateUtils.formatArabicDateTime12h(transfer.dateMs)}')
      ..writeln('المرسل: ${transfer.senderName}')
      ..writeln('المستلم: ${recipient?.name ?? (transfer.receiverName.isEmpty ? 'غير محدد' : transfer.receiverName)}');
    if (recipient != null && recipient.phone.isNotEmpty) {
      sb.writeln('هاتف المستلم: ${recipient.phone}');
    }
    if (transfer.transferCompany.isNotEmpty) {
      sb.writeln('الشركة: ${transfer.transferCompany}');
    }
    if (transfer.transferNumber.isNotEmpty) {
      sb.writeln('رقم الحوالة: ${transfer.transferNumber}');
    }
    sb
      ..writeln('--------------------------------')
      ..writeln('المبلغ: ${Money(transfer.amountMinor, transfer.currency).format()}')
      ..writeln('العمولة: ${Money(transfer.feeMinor, transfer.currency).format()}')
      ..writeln('الحالة: ${transfer.status.arabicLabel}');
    if (transfer.notes.isNotEmpty) sb.writeln('ملاحظات: ${transfer.notes}');
    return sb.toString().trim();
  }

  static String buildSupplierPurchaseText(
    AgencySettings agency,
    SupplierPurchase purchase,
  ) {
    final sb = StringBuffer()
      ..writeln(agency.agencyName)
      ..writeln('سند توريد / فاتورة مورد (${purchase.purchaseNumber})')
      ..writeln('التاريخ: ${QatDateUtils.formatArabicDateTime12h(purchase.dateMs)}')
      ..writeln('المورد: ${purchase.supplierName}')
      ..writeln('--------------------------------');
    for (final it in purchase.items) {
      sb.writeln(
        '${it.qatTypeName} (${QuantityMath.formatMilli(it.qtyMilli)} ${it.unit}) = ${Money(it.lineTotalMinor, purchase.currency).format()}',
      );
    }
    sb
      ..writeln('--------------------------------')
      ..writeln('الإجمالي: ${Money(purchase.totalAmountMinor, purchase.currency).format()}')
      ..writeln('المدفوع: ${Money(purchase.paidAmountMinor, purchase.currency).format()}')
      ..writeln(
        purchase.remainingAmountMinor < 0
            ? 'فائض لصالح الوكالة: ${Money(purchase.remainingAmountMinor.abs(), purchase.currency).format()}'
            : 'المتبقي للمورد: ${Money(purchase.remainingAmountMinor, purchase.currency).format()}',
      );
    if (purchase.bankName.isNotEmpty) sb.writeln('البنك: ${purchase.bankName}');
    if (purchase.transferNumber.isNotEmpty) sb.writeln('رقم الحوالة: ${purchase.transferNumber}');
    if (purchase.notes.isNotEmpty) sb.writeln('ملاحظات: ${purchase.notes}');
    return sb.toString().trim();
  }

  static String buildCustomerPaymentReceiptText(
    AgencySettings agency, {
    required Customer customer,
    required int amountMinor,
    required CurrencyCode currency,
    required String paymentMethod,
    required int dateMs,
    String notes = '',
  }) {
    final sb = StringBuffer()
      ..writeln(agency.agencyName)
      ..writeln('سند قبض مالي من عميل')
      ..writeln('التاريخ: ${QatDateUtils.formatArabicDateTime12h(dateMs)}')
      ..writeln('العميل: ${customer.name}')
      ..writeln('العملة: ${currency.arabicName}')
      ..writeln('طريقة القبض: $paymentMethod')
      ..writeln('--------------------------------')
      ..writeln('المبلغ المقبوض: ${Money(amountMinor, currency).format()}')
      ..writeln('الرصيد المتبقي: ${Money(customer.balanceFor(currency), currency).format()}');
    if (notes.trim().isNotEmpty) sb.writeln('ملاحظات: ${notes.trim()}');
    if (agency.footerEnabled && agency.footerNote.isNotEmpty) {
      sb.writeln('--------------------------------');
      sb.writeln(agency.footerNote);
    }
    return sb.toString().trim();
  }

  static String buildStatementReceiptText(
    AgencySettings agency, {
    required String partyTitle,
    required String partyName,
    required Map<CurrencyCode, List<StatementLine>> linesByCurrency,
  }) {
    final sb = StringBuffer()
      ..writeln(agency.agencyName)
      ..writeln('كشف حساب $partyTitle: $partyName')
      ..writeln('التاريخ: ${QatDateUtils.formatArabicDateTime12h(DateTime.now().millisecondsSinceEpoch)}');
    for (final entry in linesByCurrency.entries) {
      final cur = entry.key;
      final debit = entry.value.fold<int>(0, (s, l) => s + l.debitMinor);
      final credit = entry.value.fold<int>(0, (s, l) => s + l.creditMinor);
      final bal = entry.value.isEmpty ? 0 : entry.value.last.runningBalanceMinor;
      sb
        ..writeln('--------------------------------')
        ..writeln('العملة: ${cur.arabicName}')
        ..writeln('عليه/مدين: ${Money(debit, cur).format()}')
        ..writeln('له/دائن: ${Money(credit, cur).format()}')
        ..writeln('الرصيد: ${Money(bal, cur).format()}');
    }
    if (agency.footerEnabled && agency.footerNote.isNotEmpty) {
      sb.writeln('--------------------------------');
      sb.writeln(agency.footerNote);
    }
    return sb.toString().trim();
  }
}
