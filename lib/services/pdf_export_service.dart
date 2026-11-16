import 'dart:typed_data';

import 'package:flutter/services.dart' show rootBundle;
import 'package:pdf/pdf.dart';
import 'package:pdf/widgets.dart' as pw;

import '../core/date_utils.dart';
import '../core/money.dart';
import '../data/models.dart';
import 'arabic_pdf_shaper.dart';

class PdfFonts {
  const PdfFonts(this.regular, this.bold);
  final pw.Font regular;
  final pw.Font bold;
}

class PdfExportService {
  PdfExportService._();
  static final PdfExportService instance = PdfExportService._();

  PdfFonts? _cachedFonts;

  Future<PdfFonts> _loadFonts() async {
    if (_cachedFonts != null) return _cachedFonts!;
    final regData = await rootBundle.load('assets/fonts/IBMPlexSansArabic-Regular.ttf');
    final boldData = await rootBundle.load('assets/fonts/IBMPlexSansArabic-Bold.ttf');
    _cachedFonts = PdfFonts(pw.Font.ttf(regData), pw.Font.ttf(boldData));
    return _cachedFonts!;
  }

  static String _ar(String text) => ArabicPdfShaper.shapeForPdf(text);

  Future<Uint8List> buildSaleInvoicePdf({
    required AgencySettings agency,
    required Sale sale,
    PdfPageFormat format = PdfPageFormat.a4,
  }) async {
    final fonts = await _loadFonts();
    final doc = pw.Document();

    doc.addPage(
      pw.MultiPage(
        pageFormat: format,
        margin: const pw.EdgeInsets.all(24),
        theme: pw.ThemeData.withFont(base: fonts.regular, bold: fonts.bold),
        build: (ctx) => [
          _headerBlock(agency, 'فاتورة مبيعات (${sale.invoiceNumber})'),
          pw.SizedBox(height: 12),
          _infoRow('العميل:', sale.customerName),
          _infoRow('التاريخ:', sale.dateIso),
          _infoRow('العملة:', '${sale.currency.arabicName} (${sale.currency.code})'),
          _infoRow('حالة الدفع:', sale.paymentType.arabicLabel),
          pw.SizedBox(height: 12),
          pw.TableHelper.fromTextArray(
            headerStyle: const pw.TextStyle(fontWeight: pw.FontWeight.bold, fontSize: 10, color: PdfColors.white),
            headerDecoration: const pw.BoxDecoration(color: PdfColor.fromInt(0xFF144D3A)),
            cellStyle: const pw.TextStyle(fontSize: 9.5),
            cellAlignment: pw.Alignment.centerRight,
            headers: [
              _ar('الإجمالي'),
              _ar('السعر'),
              _ar('الوحدة'),
              _ar('الكمية'),
              _ar('الصنف'),
            ],
            data: sale.items
                .map((it) => [
                      _ar(Money(it.lineTotalMinor, sale.currency).format()),
                      _ar(Money(it.unitPriceMinor, sale.currency).format()),
                      _ar(it.unit),
                      QuantityMath.formatMilli(it.qtyMilli),
                      _ar(it.qatTypeName),
                    ])
                .toList(),
          ),
          pw.SizedBox(height: 14),
          _summaryBox([
            ('المجموع الفرعي', Money(sale.subtotalMinor, sale.currency).format()),
            if (sale.discountMinor > 0)
              ('الخصم', Money(sale.discountMinor, sale.currency).format()),
            if (sale.taxAmountMinor > 0)
              ('الضريبة (${InvoiceMath.formatBps(sale.taxBps)})',
                  Money(sale.taxAmountMinor, sale.currency).format()),
            ('الإجمالي النهائي', Money(sale.totalAmountMinor, sale.currency).format()),
            ('المدفوع', Money(sale.paidAmountMinor, sale.currency).format()),
            ('المتبقي', Money(sale.remainingAmountMinor, sale.currency).format()),
          ]),
          if (sale.notes.isNotEmpty) ...[
            pw.SizedBox(height: 10),
            _infoRow('ملاحظات:', sale.notes),
          ],
          pw.SizedBox(height: 18),
          pw.Align(
            alignment: pw.Alignment.center,
            child: pw.Text(_ar(agency.footerNote), style: const pw.TextStyle(fontSize: 9)),
          ),
        ],
      ),
    );
    return doc.save();
  }

  Future<Uint8List> buildTransferRecipientReportPdf({
    required AgencySettings agency,
    required TransferRecipientSummary summary,
    String? fromIso,
    String? toIso,
  }) async {
    final fonts = await _loadFonts();
    final doc = pw.Document();

    final summaryLines = <(String, String)>[
      ('إجمالي الحوالات', '${summary.totalTransfers}'),
      ('المسلّمة (${summary.deliveredCount})',
          _formatCurrencyMap(summary.deliveredByCurrency)),
      ('جاري التسليم (${summary.pendingCount})',
          _formatCurrencyMap(summary.pendingByCurrency)),
      ('الملغاة (${summary.cancelledCount})',
          _formatCurrencyMap(summary.cancelledByCurrency)),
      ('إجمالي العمولات', _formatCurrencyMap(summary.feesByCurrency)),
    ];

    doc.addPage(
      pw.MultiPage(
        pageFormat: PdfPageFormat.a4,
        margin: const pw.EdgeInsets.all(24),
        theme: pw.ThemeData.withFont(base: fonts.regular, bold: fonts.bold),
        build: (ctx) => [
          _headerBlock(agency, 'تقرير حوالات المستلم: ${summary.recipient.name}'),
          pw.SizedBox(height: 8),
          if (summary.recipient.phone.isNotEmpty) _infoRow('الهاتف:', summary.recipient.phone),
          if (summary.recipient.company.isNotEmpty)
            _infoRow('الشركة المعتمدة:', summary.recipient.company),
          if (fromIso != null || toIso != null)
            _infoRow('الفترة:', '${fromIso ?? 'البداية'} إلى ${toIso ?? 'الآن'}'),
          pw.SizedBox(height: 10),
          _summaryBox(summaryLines),
          pw.SizedBox(height: 12),
          pw.TableHelper.fromTextArray(
            headerStyle: const pw.TextStyle(fontWeight: pw.FontWeight.bold, fontSize: 9.5, color: PdfColors.white),
            headerDecoration: const pw.BoxDecoration(color: PdfColor.fromInt(0xFF144D3A)),
            cellStyle: const pw.TextStyle(fontSize: 9),
            cellAlignment: pw.Alignment.centerRight,
            headers: [
              _ar('الحالة'),
              _ar('رقم الحوالة'),
              _ar('الشركة'),
              _ar('الرسوم'),
              _ar('المبلغ'),
              _ar('العملة'),
              _ar('المرسل'),
              _ar('التاريخ'),
            ],
            data: summary.transfers
                .map((t) => [
                      _ar(t.status.arabicLabel),
                      t.transferNumber.isEmpty ? '-' : t.transferNumber,
                      _ar(t.transferCompany.isEmpty ? '-' : t.transferCompany),
                      Money(t.feeMinor, t.currency).format(withSymbol: false),
                      Money(t.amountMinor, t.currency).format(withSymbol: false),
                      _ar(t.currency.shortSymbol),
                      _ar(t.senderName),
                      t.dateIso,
                    ])
                .toList(),
          ),
        ],
      ),
    );
    return doc.save();
  }

  Future<Uint8List> buildStatementPdf({
    required AgencySettings agency,
    required String partyTitle,
    required String partyName,
    required Map<CurrencyCode, List<StatementLine>> linesByCurrency,
  }) async {
    final fonts = await _loadFonts();
    final doc = pw.Document();

    doc.addPage(
      pw.MultiPage(
        pageFormat: PdfPageFormat.a4,
        margin: const pw.EdgeInsets.all(24),
        theme: pw.ThemeData.withFont(base: fonts.regular, bold: fonts.bold),
        build: (ctx) {
          final widgets = <pw.Widget>[
            _headerBlock(agency, 'كشف حساب $partyTitle: $partyName'),
            _infoRow('تاريخ الإصدار:', QatDateUtils.todayIso()),
            pw.SizedBox(height: 12),
          ];
          if (linesByCurrency.isEmpty) {
            widgets.add(pw.Text(_ar('لا توجد حركات مالية مسجلة.')));
          } else {
            for (final entry in linesByCurrency.entries) {
              final cur = entry.key;
              final lines = entry.value;
              widgets.add(
                pw.Padding(
                  padding: const pw.EdgeInsets.symmetric(vertical: 6),
                  child: pw.Text(
                    _ar('دفتر العملة: ${cur.arabicName} (${cur.code})'),
                    style: const pw.TextStyle(fontWeight: pw.FontWeight.bold, fontSize: 12),
                  ),
                ),
              );
              widgets.add(
                pw.TableHelper.fromTextArray(
                  headerStyle: const pw.TextStyle(
                      fontWeight: pw.FontWeight.bold, fontSize: 9.5, color: PdfColors.white),
                  headerDecoration: const pw.BoxDecoration(color: PdfColor.fromInt(0xFF144D3A)),
                  cellStyle: const pw.TextStyle(fontSize: 9),
                  cellAlignment: pw.Alignment.centerRight,
                  headers: [
                    _ar('الرصيد'),
                    _ar('له / مسدد'),
                    _ar('عليه / فاتورة'),
                    _ar('البيان'),
                    _ar('المرجع'),
                    _ar('التاريخ'),
                  ],
                  data: lines
                      .map((l) => [
                            _ar(Money(l.runningBalanceMinor, cur).format()),
                            Money(l.creditMinor, cur).format(withSymbol: false),
                            Money(l.debitMinor, cur).format(withSymbol: false),
                            _ar(l.description),
                            l.reference,
                            l.dateIso,
                          ])
                      .toList(),
                ),
              );
              widgets.add(pw.SizedBox(height: 12));
            }
          }
          return widgets;
        },
      ),
    );
    return doc.save();
  }

  Future<Uint8List> buildFinancialReportPdf({
    required AgencySettings agency,
    required Map<CurrencyCode, CurrencyDashboardMetrics> metricsByCurrency,
    String? fromIso,
    String? toIso,
  }) async {
    final fonts = await _loadFonts();
    final doc = pw.Document();

    doc.addPage(
      pw.MultiPage(
        pageFormat: PdfPageFormat.a4,
        margin: const pw.EdgeInsets.all(24),
        theme: pw.ThemeData.withFont(base: fonts.regular, bold: fonts.bold),
        build: (ctx) {
          final widgets = <pw.Widget>[
            _headerBlock(agency, 'التقرير المالي الشامل'),
            _infoRow('النطاق الزمني:', '${fromIso ?? 'الكل'} - ${toIso ?? 'اليوم'}'),
            pw.SizedBox(height: 12),
          ];
          for (final entry in metricsByCurrency.entries) {
            final cur = entry.key;
            final m = entry.value;
            widgets.add(pw.Text(
              _ar('ملخص ${cur.arabicName} (${cur.code})'),
              style: const pw.TextStyle(fontWeight: pw.FontWeight.bold, fontSize: 12),
            ));
            widgets.add(pw.SizedBox(height: 6));
            widgets.add(_summaryBox([
              ('إجمالي المبيعات', Money(m.salesTotalMinor, cur).format()),
              ('المقبوضات النقدية', Money(m.salesPaidMinor + m.customerReceiptsMinor, cur).format()),
              ('إجمالي المشتريات', Money(m.purchasesTotalMinor, cur).format()),
              ('المصروفات', Money(m.expensesMinor, cur).format()),
              ('الضرائب', Money(m.taxMinor, cur).format()),
              ('صافي الربح التقديري', Money(m.netProfitMinor, cur).format()),
              ('ديون العملاء القائمة', Money(m.totalCustomerDebtMinor, cur).format()),
              ('مستحقات الموردين', Money(m.totalSupplierPayableMinor, cur).format()),
            ]));
            widgets.add(pw.SizedBox(height: 12));
          }
          return widgets;
        },
      ),
    );
    return doc.save();
  }

  pw.Widget _headerBlock(AgencySettings agency, String title) {
    return pw.Container(
      padding: const pw.EdgeInsets.all(14),
      decoration: pw.BoxDecoration(
        color: const PdfColor.fromInt(0xFFF6F4EF),
        border: pw.Border.all(color: const PdfColor.fromInt(0xFF144D3A), width: 1.2),
        borderRadius: const pw.BorderRadius.all(pw.Radius.circular(8)),
      ),
      child: pw.Column(
        crossAxisAlignment: pw.CrossAxisAlignment.end,
        children: [
          pw.Text(
            _ar(agency.agencyName),
            style: const pw.TextStyle(
              fontWeight: pw.FontWeight.bold,
              fontSize: 16,
              color: PdfColor.fromInt(0xFF144D3A),
            ),
          ),
          if (agency.agencyPhone.isNotEmpty || agency.agencyAddress.isNotEmpty)
            pw.Text(
              _ar('${agency.agencyPhone}  ${agency.agencyAddress}'.trim()),
              style: const pw.TextStyle(fontSize: 9.5),
            ),
          pw.Divider(color: const PdfColor.fromInt(0xFFB8862D)),
          pw.Text(
            _ar(title),
            style: const pw.TextStyle(fontWeight: pw.FontWeight.bold, fontSize: 13),
          ),
        ],
      ),
    );
  }

  pw.Widget _infoRow(String label, String value) {
    return pw.Padding(
      padding: const pw.EdgeInsets.only(bottom: 3),
      child: pw.Row(
        mainAxisAlignment: pw.MainAxisAlignment.end,
        children: [
          pw.Text(_ar(value), style: const pw.TextStyle(fontSize: 10)),
          pw.SizedBox(width: 6),
          pw.Text(_ar(label), style: const pw.TextStyle(fontWeight: pw.FontWeight.bold, fontSize: 10)),
        ],
      ),
    );
  }

  pw.Widget _summaryBox(List<(String, String)> pairs) {
    return pw.Container(
      padding: const pw.EdgeInsets.all(10),
      decoration: pw.BoxDecoration(
        border: pw.Border.all(color: PdfColors.grey400),
        borderRadius: const pw.BorderRadius.all(pw.Radius.circular(6)),
      ),
      child: pw.Column(
        children: pairs
            .map(
              (pair) => pw.Padding(
                padding: const pw.EdgeInsets.symmetric(vertical: 2),
                child: pw.Row(
                  mainAxisAlignment: pw.MainAxisAlignment.spaceBetween,
                  children: [
                    pw.Text(_ar(pair.$2), style: const pw.TextStyle(fontWeight: pw.FontWeight.bold, fontSize: 10)),
                    pw.Text(_ar(pair.$1), style: const pw.TextStyle(fontSize: 10)),
                  ],
                ),
              ),
            )
            .toList(),
      ),
    );
  }

  String _formatCurrencyMap(Map<CurrencyCode, int> map) {
    if (map.isEmpty) return '0';
    return map.entries.map((e) => Money(e.value, e.key).format()).join(' | ');
  }
}
