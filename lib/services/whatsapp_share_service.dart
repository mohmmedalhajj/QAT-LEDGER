import 'package:url_launcher/url_launcher.dart';

import '../core/date_utils.dart';
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
      ..writeln('التاريخ: ${QatDateUtils.formatArabicDateTime12h(sale.dateMs)}')
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
      ..writeln('الدفع: ${sale.effectivePaymentMethodLabel}');
    if (agency.footerNote.isNotEmpty) sb.writeln(agency.footerNote);
    return sb.toString().trim();
  }

  String formatPurchaseMessage(
    AgencySettings agency,
    SupplierPurchase purchase,
  ) {
    final sb = StringBuffer()
      ..writeln(agency.agencyName)
      ..writeln('فاتورة مشتريات: ${purchase.purchaseNumber}')
      ..writeln('المورد: ${purchase.supplierName}')
      ..writeln('التاريخ: ${QatDateUtils.formatArabicDateTime12h(purchase.dateMs)}')
      ..writeln('----------------');
    for (final it in purchase.items) {
      sb.writeln(
        '• ${it.qatTypeName} (${QuantityMath.formatMilli(it.qtyMilli)} ${it.unit}) = ${Money(it.lineTotalMinor, purchase.currency).format()}',
      );
    }
    sb
      ..writeln('----------------')
      ..writeln('الإجمالي: ${Money(purchase.totalAmountMinor, purchase.currency).format()}')
      ..writeln('المدفوع: ${Money(purchase.paidAmountMinor, purchase.currency).format()}')
      ..writeln('المتبقي: ${Money(purchase.remainingAmountMinor, purchase.currency).format()}')
      ..writeln('الدفع: ${purchase.effectivePaymentMethodLabel}');
    if (purchase.bankName.isNotEmpty || purchase.transferNumber.isNotEmpty) {
      sb.writeln(
        'البنك / الحوالة: ${purchase.bankName} ${purchase.transferNumber}'.trim(),
      );
    }
    if (purchase.demarMinor > 0) {
      sb.writeln(
        'الديمار (${purchase.demarType}): ${Money(purchase.demarMinor, purchase.currency).format()}',
      );
    }
    if (agency.footerNote.isNotEmpty) sb.writeln(agency.footerNote);
    return sb.toString().trim();
  }

  String formatCustomerDebtReminder(AgencySettings agency, Customer customer) {
    final sb = StringBuffer()
      ..writeln(agency.agencyName)
      ..writeln('تذكير رصيد حساب: ${customer.name}')
      ..writeln('----------------');
    for (final cur in CurrencyCode.values) {
      final bal = customer.balanceFor(cur);
      if (bal != 0) {
        sb.writeln('• الرصيد (${cur.arabicName}): ${Money(bal, cur).format()}');
      }
    }
    if (agency.agencyPhone.isNotEmpty) {
      sb.writeln('للتواصل: ${agency.agencyPhone}');
    }
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
      ..writeln(
        'تم التسليم (${summary.deliveredCount}) • جاري التسليم (${summary.pendingCount}) • ملغي (${summary.cancelledCount})',
      )
      ..writeln('----------------');
    for (final t in summary.transfers.take(15)) {
      sb.writeln(
        '• ${t.dateIso} | ${Money(t.amountMinor, t.currency).format()} | ${t.status.arabicLabel}${t.transferNumber.isNotEmpty ? ' (#${t.transferNumber})' : ''}',
      );
    }
    return sb.toString().trim();
  }

  static String formatLicenseRequestMessage({
    required String agencyName,
    required String installationId,
    required String appVersion,
  }) {
    return 'طلب ترخيص ميزان القات\n'
        'الوكالة: $agencyName\n'
        'معرّف التثبيت: $installationId\n'
        'إصدار التطبيق: $appVersion';
  }

  static Uri buildWhatsAppUri({String? phone, required String text}) {
    final trimmed = (phone ?? '').trim();
    var digits = normalizeDigits(trimmed).replaceAll(RegExp(r'[^0-9+]'), '');
    if (digits.startsWith('00')) {
      digits = digits.substring(2);
    }
    final plainDigits = digits.replaceAll('+', '');
    final hasCountryCode =
        trimmed.startsWith('+') || trimmed.startsWith('00') || plainDigits.length >= 11;
    final encoded = Uri.encodeComponent(text);
    if (plainDigits.isEmpty || !hasCountryCode) {
      return Uri.parse('https://wa.me/?text=$encoded');
    }
    return Uri.parse('https://wa.me/$plainDigits?text=$encoded');
  }

  Future<bool> openWhatsApp({String? phone, required String text}) async {
    final url = buildWhatsAppUri(phone: phone, text: text);
    if (await canLaunchUrl(url)) {
      return launchUrl(url, mode: LaunchMode.externalApplication);
    }
    return false;
  }

  Future<bool> openSms({String phone = '', required String text}) async {
    final digits = normalizeDigits(phone).replaceAll(RegExp(r'[^0-9+]'), '');
    final uri = Uri(scheme: 'smsto', path: digits, queryParameters: {'body': text});
    if (await canLaunchUrl(uri)) {
      return launchUrl(uri);
    }
    return false;
  }

  /// Launches Android/platform group SMS composer with multiple recipient phone numbers (`smsto:a;b;c`).
  Future<bool> openGroupSms({
    required List<String> phones,
    required String text,
  }) async {
    final cleaned = phones
        .map((p) => normalizeDigits(p).replaceAll(RegExp(r'[^0-9+]'), ''))
        .where((p) => p.isNotEmpty)
        .toSet()
        .toList();
    if (cleaned.isEmpty) return false;
    final joined = cleaned.join(';');
    final uri = Uri(scheme: 'smsto', path: joined, queryParameters: {'body': text});
    if (await canLaunchUrl(uri)) {
      return launchUrl(uri);
    }
    final fallbackUri = Uri(
      scheme: 'sms',
      path: cleaned.join(','),
      queryParameters: {'body': text},
    );
    if (await canLaunchUrl(fallbackUri)) {
      return launchUrl(fallbackUri);
    }
    return false;
  }
}
