import 'package:url_launcher/url_launcher.dart';

import '../core/money.dart';
import '../data/models.dart';

class WhatsappShareService {
  WhatsappShareService._();
  static final WhatsappShareService instance = WhatsappShareService._();

  String formatSaleMessage(AgencySettings agency, Sale sale) {
    final sb = StringBuffer()
      ..writeln(agency.agencyName)
      ..writeln('فاتورة مبيعات: ${sale.invoiceNumber}')
      ..writeln('العميل: ${sale.customerName}')
      ..writeln('التاريخ: ${sale.dateIso}')
      ..writeln('----------------');
    for (final it in sale.items) {
      sb.writeln(
        '• ${it.qatTypeName} (${QuantityMath.formatMilli(it.qtyMilli)} ${it.unit}) = ${Money(it.lineTotalMinor, sale.currency).format()}',
      );
    }
    sb
      ..writeln('----------------')
      ..writeln('الإجمالي: ${Money(sale.totalAmountMinor, sale.currency).format()}')
      ..writeln('المدفوع: ${Money(sale.paidAmountMinor, sale.currency).format()}')
      ..writeln('المتبقي: ${Money(sale.remainingAmountMinor, sale.currency).format()}')
      ..writeln('الحالة: ${sale.paymentType.arabicLabel}');
    if (agency.footerNote.isNotEmpty) sb.writeln(agency.footerNote);
    return sb.toString().trim();
  }

  String formatRecipientSummaryMessage(
    AgencySettings agency,
    TransferRecipientSummary summary,
  ) {
    final sb = StringBuffer()
      ..writeln(agency.agencyName)
      ..writeln('تقرير حوالات المستلم: ${summary.recipient.name}')
      ..writeln('إجمالي الحوالات: ${summary.totalTransfers}')
      ..writeln('تم التسليم (${summary.deliveredCount}) • جاري التسليم (${summary.pendingCount}) • ملغي (${summary.cancelledCount})')
      ..writeln('----------------');
    for (final t in summary.transfers.take(15)) {
      sb.writeln(
        '• ${t.dateIso} | ${Money(t.amountMinor, t.currency).format()} | ${t.status.arabicLabel}${t.transferNumber.isNotEmpty ? ' (#${t.transferNumber})' : ''}',
      );
    }
    return sb.toString().trim();
  }

  Future<bool> openWhatsApp({String? phone, required String text}) async {
    final digits = normalizeDigits(phone ?? '').replaceAll(RegExp(r'[^0-9+]'), '');
    final encoded = Uri.encodeComponent(text);
    final url = digits.isEmpty
        ? Uri.parse('https://wa.me/?text=$encoded')
        : Uri.parse('https://wa.me/${digits.replaceAll('+', '')}?text=$encoded');
    if (await canLaunchUrl(url)) {
      return launchUrl(url, mode: LaunchMode.externalApplication);
    }
    return false;
  }

  Future<bool> openSms({required String phone, required String text}) async {
    final digits = normalizeDigits(phone).replaceAll(RegExp(r'[^0-9+]'), '');
    final uri = Uri(scheme: 'smsto', path: digits, queryParameters: {'body': text});
    if (await canLaunchUrl(uri)) {
      return launchUrl(uri);
    }
    return false;
  }
}
