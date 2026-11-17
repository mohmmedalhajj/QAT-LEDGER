import 'dart:io';
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
    final regData =
        await rootBundle.load('assets/fonts/IBMPlexSansArabic-Regular.ttf');
    final boldData =
        await rootBundle.load('assets/fonts/IBMPlexSansArabic-Bold.ttf');
    _cachedFonts = PdfFonts(pw.Font.ttf(regData), pw.Font.ttf(boldData));
    return _cachedFonts!;
  }

  static String _ar(String text) => ArabicPdfShaper.shapeForPdf(text);

  PdfColor _primaryColor(AgencySettings agency) {
    if (!agency.syncColorsWithPdf) {
      return const PdfColor.fromInt(0xFF144D3A);
    }
    final c = agency.activeColorPreset.primaryNormal;
    return PdfColor.fromInt(c.toARGB32());
  }

  PdfColor _accentColor(AgencySettings agency) {
    if (!agency.syncColorsWithPdf) {
      return const PdfColor.fromInt(0xFFB8862D);
    }
    final c = agency.activeColorPreset.accentNormal;
    return PdfColor.fromInt(c.toARGB32());
  }

  pw.MemoryImage? _tryLoadLogo(AgencySettings agency) {
    final p = agency.logoPath.trim();
    if (p.isEmpty) return null;
    try {
      final f = File(p);
      if (!f.existsSync()) return null;
      return pw.MemoryImage(f.readAsBytesSync());
    } catch (_) {
      return null;
    }
  }

  Future<Uint8List> buildSaleInvoicePdf({
    required AgencySettings agency,
    required Sale sale,
    PdfPageFormat format = PdfPageFormat.a4,
  }) async {
    final fonts = await _loadFonts();
    final doc = pw.Document();
    final primary = _primaryColor(agency);

    doc.addPage(
      pw.MultiPage(
        pageFormat: format,
        margin: const pw.EdgeInsets.all(20),
        theme: pw.ThemeData.withFont(base: fonts.regular, bold: fonts.bold),
        build: (ctx) => [
          _headerBlock(agency, 'فاتورة مبيعات (${sale.invoiceNumber})'),
          pw.SizedBox(height: 10),
          _infoRow('العميل:', sale.customerName),
          _infoRow('التاريخ:', QatDateUtils.formatArabicDateTime12h(sale.dateMs)),
          _infoRow('العملة:', '${sale.currency.arabicName} (${sale.currency.code})'),
          _infoRow('طريقة/حالة الدفع:', sale.effectivePaymentMethodLabel),
          pw.SizedBox(height: 10),
          if (sale.items.isNotEmpty)
            pw.TableHelper.fromTextArray(
              headerStyle: pw.TextStyle(
                fontWeight: pw.FontWeight.bold,
                fontSize: 10,
                color: PdfColors.white,
              ),
              headerDecoration: pw.BoxDecoration(color: primary),
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
          pw.SizedBox(height: 12),
          _summaryBox([
            ('المجموع الفرعي', Money(sale.subtotalMinor, sale.currency).format()),
            if (sale.discountMinor > 0)
              ('الخصم', Money(sale.discountMinor, sale.currency).format()),
            if (sale.taxAmountMinor > 0)
              (
                'الضريبة (${InvoiceMath.formatBps(sale.taxBps)})',
                Money(sale.taxAmountMinor, sale.currency).format()
              ),
            ('الإجمالي النهائي', Money(sale.totalAmountMinor, sale.currency).format()),
            ('المدفوع', Money(sale.paidAmountMinor, sale.currency).format()),
            ('المتبقي', Money(sale.remainingAmountMinor, sale.currency).format()),
          ]),
          if (sale.notes.isNotEmpty) ...[
            pw.SizedBox(height: 10),
            _infoRow('ملاحظات:', sale.notes),
          ],
          if (agency.footerEnabled && agency.footerNote.isNotEmpty) ...[
            pw.SizedBox(height: 14),
            pw.Align(
              alignment: pw.Alignment.center,
              child: pw.Text(_ar(agency.footerNote), style: const pw.TextStyle(fontSize: 9)),
            ),
          ],
        ],
      ),
    );
    return doc.save();
  }

  Future<Uint8List> buildSupplierPurchasePdf({
    required AgencySettings agency,
    required SupplierPurchase purchase,
    PdfPageFormat format = PdfPageFormat.a4,
  }) async {
    final fonts = await _loadFonts();
    final doc = pw.Document();
    final primary = _primaryColor(agency);

    doc.addPage(
      pw.MultiPage(
        pageFormat: format,
        margin: const pw.EdgeInsets.all(20),
        theme: pw.ThemeData.withFont(base: fonts.regular, bold: fonts.bold),
        build: (ctx) => [
          _headerBlock(agency, 'سند/فاتورة توريد من المورد (${purchase.purchaseNumber})'),
          pw.SizedBox(height: 10),
          _infoRow('المورد / الرعية:', purchase.supplierName),
          _infoRow('التاريخ:', QatDateUtils.formatArabicDateTime12h(purchase.dateMs)),
          _infoRow('العملة:', '${purchase.currency.arabicName} (${purchase.currency.code})'),
          _infoRow(
            'طريقة الدفع:',
            purchase.paymentMethod.isNotEmpty
                ? purchase.paymentMethod
                : purchase.paymentType.arabicLabel,
          ),
          if (purchase.bankName.isNotEmpty) _infoRow('البنك/الصرافة:', purchase.bankName),
          if (purchase.transferNumber.isNotEmpty)
            _infoRow('رقم الحوالة/المرجع:', purchase.transferNumber),
          pw.SizedBox(height: 10),
          if (purchase.items.isNotEmpty)
            pw.TableHelper.fromTextArray(
              headerStyle: pw.TextStyle(
                fontWeight: pw.FontWeight.bold,
                fontSize: 10,
                color: PdfColors.white,
              ),
              headerDecoration: pw.BoxDecoration(color: primary),
              cellStyle: const pw.TextStyle(fontSize: 9.5),
              cellAlignment: pw.Alignment.centerRight,
              headers: [
                _ar('الإجمالي'),
                _ar('سعر الشراء'),
                _ar('الوحدة'),
                _ar('الكمية'),
                _ar('الصنف'),
              ],
              data: purchase.items
                  .map((it) => [
                        _ar(Money(it.lineTotalMinor, purchase.currency).format()),
                        _ar(Money(it.buyPriceMinor, purchase.currency).format()),
                        _ar(it.unit),
                        QuantityMath.formatMilli(it.qtyMilli),
                        _ar(it.qatTypeName),
                      ])
                  .toList(),
            ),
          pw.SizedBox(height: 12),
          _summaryBox([
            ('إجمالي الفاتورة', Money(purchase.totalAmountMinor, purchase.currency).format()),
            ('المدفوع للمورد', Money(purchase.paidAmountMinor, purchase.currency).format()),
            (
              purchase.remainingAmountMinor < 0 ? 'فائض لصالح الوكالة' : 'المتبقي للمورد',
              Money(purchase.remainingAmountMinor.abs(), purchase.currency).format()
            ),
            if (purchase.demarMinor > 0)
              (
                'الدمار (${purchase.demarType})',
                Money(purchase.demarMinor, purchase.currency).format()
              ),
          ]),
          if (purchase.notes.isNotEmpty) ...[
            pw.SizedBox(height: 10),
            _infoRow('ملاحظات:', purchase.notes),
          ],
          if (agency.footerEnabled && agency.footerNote.isNotEmpty) ...[
            pw.SizedBox(height: 14),
            pw.Align(
              alignment: pw.Alignment.center,
              child: pw.Text(_ar(agency.footerNote), style: const pw.TextStyle(fontSize: 9)),
            ),
          ],
        ],
      ),
    );
    return doc.save();
  }

  /// Universal receipt/voucher PDF renderer for thermal 58mm/80mm rasterization or A4 PDF export.
  Future<Uint8List> buildTextReceiptPdf({
    required AgencySettings agency,
    required String title,
    required List<String> lines,
    PdfPageFormat format = PdfPageFormat.roll80,
  }) async {
    final fonts = await _loadFonts();
    final doc = pw.Document();
    final primary = _primaryColor(agency);

    doc.addPage(
      pw.MultiPage(
        pageFormat: format,
        margin: const pw.EdgeInsets.symmetric(horizontal: 10, vertical: 12),
        theme: pw.ThemeData.withFont(base: fonts.regular, bold: fonts.bold),
        build: (ctx) => [
          if (agency.headerEnabled) ...[
            pw.Center(
              child: pw.Text(
                _ar(agency.agencyName),
                textAlign: pw.TextAlign.center,
                style: pw.TextStyle(
                  fontWeight: pw.FontWeight.bold,
                  fontSize: 13,
                  color: primary,
                ),
              ),
            ),
            if (agency.agencyPhone.isNotEmpty)
              pw.Center(
                child: pw.Text(_ar(agency.agencyPhone),
                    style: const pw.TextStyle(fontSize: 8.5)),
              ),
            pw.Divider(color: primary),
          ],
          pw.Center(
            child: pw.Text(
              _ar(title),
              textAlign: pw.TextAlign.center,
              style: pw.TextStyle(fontWeight: pw.FontWeight.bold, fontSize: 11),
            ),
          ),
          pw.SizedBox(height: 6),
          ...lines.map((raw) {
            final clean = raw.trimRight();
            if (clean.isEmpty) return pw.SizedBox(height: 3);
            if (clean.startsWith('===') || clean.startsWith('---')) {
              return pw.Divider(thickness: 0.7, color: PdfColors.grey600);
            }
            return pw.Padding(
              padding: const pw.EdgeInsets.symmetric(vertical: 1.5),
              child: pw.Align(
                alignment: pw.Alignment.centerRight,
                child: pw.Text(
                  _ar(clean),
                  textAlign: pw.TextAlign.right,
                  style: const pw.TextStyle(fontSize: 9.2),
                ),
              ),
            );
          }),
          if (agency.footerEnabled && agency.footerNote.isNotEmpty) ...[
            pw.Divider(color: PdfColors.grey600),
            pw.Center(
              child: pw.Text(
                _ar(agency.footerNote),
                textAlign: pw.TextAlign.center,
                style: const pw.TextStyle(fontSize: 8.5),
              ),
            ),
          ],
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
    final primary = _primaryColor(agency);

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
            headerStyle: pw.TextStyle(
              fontWeight: pw.FontWeight.bold,
              fontSize: 9.5,
              color: PdfColors.white,
            ),
            headerDecoration: pw.BoxDecoration(color: primary),
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
    final primary = _primaryColor(agency);

    doc.addPage(
      pw.MultiPage(
        pageFormat: PdfPageFormat.a4,
        margin: const pw.EdgeInsets.all(24),
        theme: pw.ThemeData.withFont(base: fonts.regular, bold: fonts.bold),
        build: (ctx) {
          final widgets = <pw.Widget>[
            _headerBlock(agency, 'كشف حساب $partyTitle: $partyName'),
            _infoRow('تاريخ الإصدار:', QatDateUtils.formatArabicDateTime12h(DateTime.now().millisecondsSinceEpoch)),
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
                    style: pw.TextStyle(fontWeight: pw.FontWeight.bold, fontSize: 12),
                  ),
                ),
              );
              widgets.add(
                pw.TableHelper.fromTextArray(
                  headerStyle: pw.TextStyle(
                    fontWeight: pw.FontWeight.bold,
                    fontSize: 9.5,
                    color: PdfColors.white,
                  ),
                  headerDecoration: pw.BoxDecoration(color: primary),
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
              style: pw.TextStyle(fontWeight: pw.FontWeight.bold, fontSize: 12),
            ));
            widgets.add(pw.SizedBox(height: 6));
            widgets.add(_summaryBox([
              ('إجمالي المبيعات', Money(m.salesTotalMinor, cur).format()),
              ('المقبوضات النقدية', Money(m.salesPaidMinor + m.customerReceiptsMinor, cur).format()),
              ('إجمالي المشتريات', Money(m.purchasesTotalMinor, cur).format()),
              ('المصروفات', Money(m.expensesMinor, cur).format()),
              ('الضرائب والمصاريف اليومية', Money(m.totalTaxAndOutflowsMinor, cur).format()),
              ('صافي الربح التقديري', Money(m.netProfitMinor, cur).format()),
              ('رصيد الصندوق النقدي', Money(m.cashBoxBalanceMinor, cur).format()),
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

  Future<Uint8List> buildInventoryReportPdf({
    required AgencySettings agency,
    required List<InventoryBatch> batches,
    required Map<CurrencyCode, InventoryValuation> valuation,
    List<QatImportWeight> importWeights = const [],
  }) async {
    final fonts = await _loadFonts();
    final doc = pw.Document();
    final primary = _primaryColor(agency);

    doc.addPage(
      pw.MultiPage(
        pageFormat: PdfPageFormat.a4,
        margin: const pw.EdgeInsets.all(20),
        theme: pw.ThemeData.withFont(base: fonts.regular, bold: fonts.bold),
        build: (ctx) => [
          _headerBlock(agency, 'تقرير جرد وتقييم المخزون'),
          pw.SizedBox(height: 8),
          _infoRow('تاريخ الجرد:', QatDateUtils.formatArabicDateTime12h(DateTime.now().millisecondsSinceEpoch)),
          pw.SizedBox(height: 10),
          if (valuation.isNotEmpty) ...[
            _summaryBox([
              for (final v in valuation.values) ...[
                ('عدد الدفعات (${v.currency.code})', '${v.batchCount}'),
                ('الكمية (${v.currency.code})', QuantityMath.formatMilli(v.totalQtyMilli)),
                ('القيمة بالتكلفة (${v.currency.code})', Money(v.totalCostMinor, v.currency).format()),
                ('القيمة البيعية (${v.currency.code})', Money(v.totalRetailMinor, v.currency).format()),
              ],
            ]),
            pw.SizedBox(height: 12),
          ],
          if (batches.isNotEmpty)
            pw.TableHelper.fromTextArray(
              headerStyle: pw.TextStyle(
                fontWeight: pw.FontWeight.bold,
                fontSize: 9,
                color: PdfColors.white,
              ),
              headerDecoration: pw.BoxDecoration(color: primary),
              cellStyle: const pw.TextStyle(fontSize: 8.5),
              cellAlignment: pw.Alignment.centerRight,
              headers: [
                _ar('المتبقي'),
                _ar('سعر البيع'),
                _ar('سعر الشراء'),
                _ar('المستلم / المورد'),
                _ar('الصنف'),
              ],
              data: batches
                  .map(
                    (b) => [
                      _ar('${QuantityMath.formatMilli(b.remainingQtyMilli)} ${b.unit}'),
                      _ar(Money(b.sellPriceMinor, b.currency).format()),
                      _ar(Money(b.buyPriceMinor, b.currency).format()),
                      _ar(b.receiverName.isNotEmpty ? b.receiverName : b.supplierName),
                      _ar(b.qatTypeName),
                    ],
                  )
                  .toList(),
            ),
          if (importWeights.isNotEmpty) ...[
            pw.SizedBox(height: 14),
            _infoRow(
              'إجمالي أوزان الوارد:',
              '${QuantityMath.formatMilli(importWeights.fold<int>(0, (s, w) => s + w.weightMilli))} كجم (${importWeights.length} شحنة)',
            ),
          ],
        ],
      ),
    );
    return doc.save();
  }

  Future<Uint8List> buildExpensesReportPdf({
    required AgencySettings agency,
    required List<Expense> expenses,
  }) async {
    final fonts = await _loadFonts();
    final doc = pw.Document();
    final primary = _primaryColor(agency);

    final totalsByCur = <CurrencyCode, int>{};
    for (final e in expenses) {
      totalsByCur[e.currency] = (totalsByCur[e.currency] ?? 0) + e.amountMinor;
    }

    doc.addPage(
      pw.MultiPage(
        pageFormat: PdfPageFormat.a4,
        margin: const pw.EdgeInsets.all(20),
        theme: pw.ThemeData.withFont(base: fonts.regular, bold: fonts.bold),
        build: (ctx) => [
          _headerBlock(agency, 'كشف المصروفات التشغيلية'),
          pw.SizedBox(height: 8),
          _infoRow('عدد البنود:', '${expenses.length}'),
          _infoRow('إجمالي المصروفات:', _formatCurrencyMap(totalsByCur)),
          pw.SizedBox(height: 10),
          if (expenses.isNotEmpty)
            pw.TableHelper.fromTextArray(
              headerStyle: pw.TextStyle(
                fontWeight: pw.FontWeight.bold,
                fontSize: 9.5,
                color: PdfColors.white,
              ),
              headerDecoration: pw.BoxDecoration(color: primary),
              cellStyle: const pw.TextStyle(fontSize: 8.5),
              cellAlignment: pw.Alignment.centerRight,
              headers: [
                _ar('المبلغ'),
                _ar('التصنيف'),
                _ar('البيان'),
                _ar('التاريخ'),
              ],
              data: expenses
                  .map(
                    (e) => [
                      _ar(Money(e.amountMinor, e.currency).format()),
                      _ar(e.category),
                      _ar(e.description),
                      e.dateIso,
                    ],
                  )
                  .toList(),
            ),
        ],
      ),
    );
    return doc.save();
  }

  pw.Widget _headerBlock(AgencySettings agency, String title) {
    final primary = _primaryColor(agency);
    final accent = _accentColor(agency);
    final logo = _tryLoadLogo(agency);

    return pw.Container(
      padding: const pw.EdgeInsets.all(14),
      decoration: pw.BoxDecoration(
        color: const PdfColor.fromInt(0xFFF6F4EF),
        border: pw.Border.all(color: primary, width: 1.2),
        borderRadius: const pw.BorderRadius.all(pw.Radius.circular(8)),
      ),
      child: pw.Row(
        mainAxisAlignment: pw.MainAxisAlignment.spaceBetween,
        crossAxisAlignment: pw.CrossAxisAlignment.center,
        children: [
          if (logo != null)
            pw.Container(
              width: 48,
              height: 48,
              margin: const pw.EdgeInsets.only(right: 10),
              child: pw.Image(logo, fit: pw.BoxFit.contain),
            ),
          pw.Expanded(
            child: pw.Column(
              crossAxisAlignment: pw.CrossAxisAlignment.end,
              children: [
                if (agency.headerEnabled) ...[
                  pw.Text(
                    _ar(agency.headerText.isNotEmpty
                        ? agency.headerText
                        : agency.agencyName),
                    style: pw.TextStyle(
                      fontWeight: pw.FontWeight.bold,
                      fontSize: 16,
                      color: primary,
                    ),
                  ),
                  if (agency.tagline.isNotEmpty)
                    pw.Text(
                      _ar(agency.tagline),
                      style: const pw.TextStyle(fontSize: 9),
                    ),
                  if (agency.agencyPhone.isNotEmpty || agency.agencyAddress.isNotEmpty)
                    pw.Text(
                      _ar('${agency.agencyPhone}  ${agency.agencyAddress}'.trim()),
                      style: const pw.TextStyle(fontSize: 9.5),
                    ),
                  if (agency.commercialDetails.isNotEmpty)
                    pw.Text(
                      _ar(agency.commercialDetails),
                      style: const pw.TextStyle(fontSize: 8.5),
                    ),
                ],
                pw.Divider(color: accent),
                pw.Text(
                  _ar(title),
                  style: pw.TextStyle(fontWeight: pw.FontWeight.bold, fontSize: 13),
                ),
              ],
            ),
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
          pw.Text(_ar(label), style: pw.TextStyle(fontWeight: pw.FontWeight.bold, fontSize: 10)),
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
                    pw.Text(_ar(pair.$2),
                        style: pw.TextStyle(fontWeight: pw.FontWeight.bold, fontSize: 10)),
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
