import 'dart:async';

import 'package:material_ui/material_ui.dart';
import 'package:printing/printing.dart';
import 'package:provider/provider.dart';

import '../../core/date_utils.dart';
import '../../core/money.dart';
import '../../core/theme.dart';
import '../../data/models.dart';
import '../../licensing/license_models.dart';
import '../../services/pdf_export_service.dart';
import '../../services/whatsapp_share_service.dart';
import '../../state/app_controller.dart';
import '../widgets/common_widgets.dart';
import '../widgets/erp_components.dart';
import 'recipients_reports_view.dart';

class FinancialReportsView extends StatefulWidget {
  const FinancialReportsView({super.key});

  @override
  State<FinancialReportsView> createState() => _FinancialReportsViewState();
}

class _FinancialReportsViewState extends State<FinancialReportsView> {
  ReportPeriodPreset _preset = ReportPeriodPreset.all;
  String? _fromIso;
  String? _toIso;
  Map<CurrencyCode, CurrencyDashboardMetrics>? _metrics;
  bool _loading = false;
  String? _error;
  bool _wasActive = false;

  String _buildReportSummaryText(
    AppController ctrl,
    Map<CurrencyCode, CurrencyDashboardMetrics> data,
  ) {
    return [
      'التقرير المالي الشامل — ${ctrl.settings.agencyName}',
      'الفترة: ${_fromIso ?? 'من البداية'} إلى ${_toIso ?? 'حتى اليوم'}',
      for (final cur in ctrl.currencyPolicy.allowedCurrencies)
        if (data[cur] != null) ...[
          '--- ${cur.arabicName} (${cur.code}) ---',
          'المبيعات: ${Money(data[cur]!.salesTotalMinor, cur).format()}',
          'نقد: ${Money(data[cur]!.salesCashOnlyMinor, cur).format()} | تحويل: ${Money(data[cur]!.salesTransferMinor, cur).format()} | إيداع: ${Money(data[cur]!.salesDepositMinor, cur).format()}',
          'المصروفات: ${Money(data[cur]!.expensesMinor, cur).format()}',
          'الضرائب/الفرش/العمال: ${Money(data[cur]!.totalTaxAndOutflowsMinor, cur).format()}',
          'صافي الربح التقديري: ${Money(data[cur]!.netProfitMinor, cur).format()}',
          'رصيد الصندوق: ${Money(data[cur]!.cashBoxBalanceMinor, cur).format()}',
          'ديون العملاء: ${Money(data[cur]!.totalCustomerDebtMinor, cur).format()}',
          'مستحقات الموردين: ${Money(data[cur]!.totalSupplierPayableMinor, cur).format()}',
        ],
    ].join('\n');
  }

  Widget _buildReportChartCard(
    QatPalette pal,
    CurrencyCode cur,
    List<DailyChartPoint> points,
  ) {
    final maxVal = points.fold<int>(1, (m, p) {
      final hi =
          p.salesMinor > p.profitMinor.abs() ? p.salesMinor : p.profitMinor.abs();
      return hi > m ? hi : m;
    });
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Icon(Icons.bar_chart_rounded, color: pal.brand, size: 18),
                const SizedBox(width: 6),
                Expanded(
                  child: Text(
                    'اتجاه المبيعات والأرباح (7 أيام — ${cur.code})',
                    style: const TextStyle(
                      fontSize: 12.5,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 10),
            SizedBox(
              height: 104,
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  for (final pt in points)
                    Expanded(
                      child: Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 2),
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.end,
                          children: [
                            Expanded(
                              child: Row(
                                mainAxisAlignment: MainAxisAlignment.center,
                                crossAxisAlignment: CrossAxisAlignment.end,
                                children: [
                                  Flexible(
                                    child: FractionallySizedBox(
                                      heightFactor: (pt.salesMinor / maxVal)
                                          .clamp(0.05, 1.0)
                                          .toDouble(),
                                      child: Container(
                                        width: 9,
                                        decoration: BoxDecoration(
                                          color: pal.brand,
                                          borderRadius:
                                              BorderRadius.circular(4),
                                        ),
                                      ),
                                    ),
                                  ),
                                  const SizedBox(width: 2),
                                  Flexible(
                                    child: FractionallySizedBox(
                                      heightFactor:
                                          (pt.profitMinor.abs() / maxVal)
                                              .clamp(0.05, 1.0)
                                              .toDouble(),
                                      child: Container(
                                        width: 9,
                                        decoration: BoxDecoration(
                                          color: pt.profitMinor >= 0
                                              ? pal.success
                                              : pal.danger,
                                          borderRadius:
                                              BorderRadius.circular(4),
                                        ),
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              pt.labelAr.isNotEmpty
                                  ? pt.labelAr
                                  : pt.dateIso.substring(5),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: TextStyle(
                                fontSize: 10,
                                color: pal.textSecondary,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _load() async {
    if (!mounted) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final res = await context.read<AppController>().repo.getDashboardMetrics(
            fromIso: _fromIso,
            toIso: _toIso,
          );
      if (!mounted) return;
      setState(() => _metrics = res);
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = 'تعذر تحميل التقرير المالي: $e');
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  Future<void> _selectPreset(ReportPeriodPreset preset) async {
    if (preset == ReportPeriodPreset.custom) {
      final range = await showDateRangePicker(
        context: context,
        firstDate: DateTime(2020),
        lastDate: DateTime(DateTime.now().year + 1),
      );
      if (range == null || !mounted) return;
      setState(() {
        _preset = ReportPeriodPreset.custom;
        _fromIso = QatDateUtils.toIsoDate(range.start);
        _toIso = QatDateUtils.toIsoDate(range.end);
      });
      await _load();
      return;
    }
    final range = resolveReportPresetRange(preset);
    setState(() {
      _preset = preset;
      _fromIso = range.fromIso;
      _toIso = range.toIso;
    });
    await _load();
  }

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final pal = context.pal;
    final isActive = ctrl.sectionIndex == 3 && ctrl.transfersSubTab == 2;
    if (!isActive) {
      _wasActive = false;
      return const SizedBox.shrink();
    }
    if (!ctrl.hasLicenseFeature(LicenseFeature.reports)) {
      return ProFeatureLockedCard(
        featureTitle: 'التقارير المالية والأرباح المتقدمة',
        featureDescription:
            'تتيح لك الباقة الاحترافية (PRO) تحليل المبيعات، الأرباح الصافية، كفاءة الموردين، وتصدير التقارير المعتمدة PDF.',
        onActivatePressed: () => ctrl.setSection(4, subTab: 3),
      );
    }
    if (!ctrl.canViewReportsCurrent) {
      return const EmptyLedgerState(
        icon: Icons.lock_outline,
        title: 'التقارير مقيدة بالصلاحيات',
        subtitle: 'تم إخفاء التقارير المالية لهذا المستخدم من إدارة الصلاحيات',
      );
    }
    if (!_wasActive) {
      _wasActive = true;
      if (!_loading) {
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted) unawaited(_load());
        });
      }
    }
    final data = _metrics;

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Row(
            children: [
              for (final p in ReportPeriodPreset.values) ...[
                ChoiceChip(
                  label: Text(p.arabicLabel),
                  selected: _preset == p,
                  onSelected: (_) => _selectPreset(p),
                ),
                const SizedBox(width: 6),
              ],
            ],
          ),
        ),
        const SizedBox(height: 10),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'النطاق المحاسبي المحدد',
                        style: TextStyle(fontSize: 11.5),
                      ),
                      Text(
                        '${_fromIso ?? 'من البداية'} — ${_toIso ?? 'حتى اليوم'}',
                        style: const TextStyle(fontWeight: FontWeight.w700),
                      ),
                    ],
                  ),
                ),
                OutlinedButton.icon(
                  onPressed: () => _selectPreset(ReportPeriodPreset.custom),
                  icon: const Icon(Icons.date_range_outlined, size: 16),
                  label: const Text('تحديد الفترة'),
                ),
                const SizedBox(width: 6),
                if (data != null) ...[
                  IconButton.outlined(
                    tooltip: 'طباعة حرارية 58/80mm',
                    onPressed: () => showReceiptPreviewPrintSheet(
                      context,
                      agency: ctrl.settings,
                      title: 'التقرير المالي الشامل',
                      receiptText: _buildReportSummaryText(ctrl, data),
                    ),
                    icon: const Icon(Icons.print_outlined, size: 18),
                  ),
                  const SizedBox(width: 4),
                  IconButton.outlined(
                    tooltip: 'مشاركة التقرير عبر واتساب',
                    onPressed: () async {
                      await WhatsappShareService.instance
                          .openWhatsApp(text: _buildReportSummaryText(ctrl, data));
                    },
                    icon: const Icon(Icons.chat_outlined, size: 18),
                  ),
                  const SizedBox(width: 4),
                  IconButton.outlined(
                    tooltip: 'مشاركة SMS',
                    onPressed: () async {
                      await WhatsappShareService.instance
                          .openSms(text: _buildReportSummaryText(ctrl, data));
                    },
                    icon: const Icon(Icons.sms_outlined, size: 18),
                  ),
                  const SizedBox(width: 4),
                  IconButton.filled(
                    tooltip: 'تصدير PDF',
                    onPressed: () async {
                      final pdf = await PdfExportService.instance
                          .buildFinancialReportPdf(
                        agency: ctrl.settings,
                        metricsByCurrency: data,
                        fromIso: _fromIso,
                        toIso: _toIso,
                      );
                      await Printing.layoutPdf(
                        name: PdfExportService.buildArabicPdfFileName('المالي'),
                        onLayout: (_) async => pdf,
                      );
                    },
                    icon: const Icon(Icons.picture_as_pdf_outlined, size: 18),
                  ),
                ],
              ],
            ),
          ),
        ),
        const SizedBox(height: 12),
        if (_error != null)
          ErpStatusBanner(
            tone: ErpBannerTone.danger,
            title: _error!,
          )
        else if (data == null)
          const Center(
            child: Padding(
              padding: EdgeInsets.all(24),
              child: CircularProgressIndicator(),
            ),
          )
        else
          for (final cur in ctrl.currencyPolicy.allowedCurrencies)
            if (data[cur] != null) ...[
              Row(
                children: [
                  CurrencyBadge(currency: cur),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'القائمة المالية — ${cur.arabicName}',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              ErpAccountingTable(
                columns: const [
                  ErpTableColumn(title: 'البند المحاسبي', width: 200),
                  ErpTableColumn(
                    title: 'القيمة',
                    width: 140,
                    numeric: true,
                    align: TextAlign.end,
                  ),
                ],
                rows: [
                  [
                    const ErpTableCell('إجمالي المبيعات'),
                    ErpTableCell(
                      Money(data[cur]!.salesTotalMinor, cur).format(),
                      bold: true,
                    ),
                  ],
                  [
                    const ErpTableCell('المقبوضات النقدية وسندات العملاء'),
                    ErpTableCell(
                      Money(
                        data[cur]!.salesPaidMinor +
                            data[cur]!.customerReceiptsMinor,
                        cur,
                      ).format(),
                      color: pal.success,
                    ),
                  ],
                  [
                    const ErpTableCell('تفصيل طرق قبض المبيعات (نقد / تحويل / إيداع)'),
                    ErpTableCell(
                      '${Money(data[cur]!.salesCashOnlyMinor, cur).format()} / ${Money(data[cur]!.salesTransferMinor, cur).format()} / ${Money(data[cur]!.salesDepositMinor, cur).format()}',
                    ),
                  ],
                  [
                    const ErpTableCell('إجمالي المشتريات'),
                    ErpTableCell(
                      Money(data[cur]!.purchasesTotalMinor, cur).format(),
                    ),
                  ],
                  [
                    const ErpTableCell('تكلفة البضاعة المباعة المقدرة'),
                    ErpTableCell(
                      Money(data[cur]!.estimatedCogsMinor, cur).format(),
                    ),
                  ],
                  [
                    const ErpTableCell('المصروفات التشغيلية'),
                    ErpTableCell(
                      Money(data[cur]!.expensesMinor, cur).format(),
                      color: pal.danger,
                    ),
                  ],
                  [
                    const ErpTableCell('ضرائب القات'),
                    ErpTableCell(
                      Money(data[cur]!.taxMinor, cur).format(),
                      color: pal.warning,
                    ),
                  ],
                  if (data[cur]!.stallOutflowMinor > 0 ||
                      data[cur]!.laborOutflowMinor > 0)
                    [
                      const ErpTableCell('مصروفات الفرش والعمال اليومية'),
                      ErpTableCell(
                        '${Money(data[cur]!.stallOutflowMinor, cur).format()} / ${Money(data[cur]!.laborOutflowMinor, cur).format()}',
                        color: pal.warning,
                      ),
                    ],
                  [
                    const ErpTableCell('صافي الربح التقديري', bold: true),
                    ErpTableCell(
                      Money(data[cur]!.netProfitMinor, cur).format(),
                      bold: true,
                      color: data[cur]!.netProfitMinor >= 0
                          ? pal.success
                          : pal.danger,
                    ),
                  ],
                  [
                    const ErpTableCell('صافي التدفق النقدي'),
                    ErpTableCell(
                      Money(data[cur]!.cashNetFlowMinor, cur).format(),
                      bold: true,
                      color: data[cur]!.cashNetFlowMinor >= 0
                          ? pal.brand
                          : pal.danger,
                    ),
                  ],
                  [
                    const ErpTableCell('إجمالي ديون العملاء القائمة'),
                    ErpTableCell(
                      Money(data[cur]!.totalCustomerDebtMinor, cur).format(),
                      color: pal.warning,
                    ),
                  ],
                  [
                    const ErpTableCell('إجمالي مستحقات الموردين القائمة'),
                    ErpTableCell(
                      Money(data[cur]!.totalSupplierPayableMinor, cur).format(),
                      color: pal.accent,
                    ),
                  ],
                ],
              ),
              const SizedBox(height: 10),
              if ((ctrl.dailyCharts[cur] ?? const <DailyChartPoint>[]).isNotEmpty)
                _buildReportChartCard(pal, cur, ctrl.dailyCharts[cur]!),
              const SizedBox(height: 8),
            ],
        if (data != null) ...[
          const ErpSectionHeader(
            title: 'سجل العمليات الزمني الموحد',
            icon: Icons.timeline_outlined,
          ),
          const SizedBox(height: 8),
          ..._buildTimelineWidgets(ctrl, pal),
        ],
      ],
    );
  }

  List<Widget> _buildTimelineWidgets(AppController ctrl, QatPalette pal) {
    bool inRange(String iso) {
      if (_fromIso != null && iso.compareTo(_fromIso!) < 0) return false;
      if (_toIso != null && iso.compareTo(_toIso!) > 0) return false;
      return true;
    }

    final events = <({
      int ms,
      String iso,
      String kind,
      String title,
      String amountLabel,
      Color color,
    })>[
      for (final s in ctrl.sales.where((e) => inRange(e.dateIso)))
        (
          ms: s.dateMs,
          iso: s.dateIso,
          kind: 'مبيعات',
          title: '${s.invoiceNumber} — ${s.customerName}',
          amountLabel: Money(s.totalAmountMinor, s.currency).format(),
          color: pal.brand,
        ),
      for (final p in ctrl.customerPayments.where((e) => inRange(e.dateIso)))
        (
          ms: p.dateMs,
          iso: p.dateIso,
          kind: 'سند قبض',
          title: '${p.customerName} (${p.paymentMethod})',
          amountLabel: Money(p.amountMinor, p.currency).format(),
          color: pal.success,
        ),
      for (final p in ctrl.purchases.where((e) => inRange(e.dateIso)))
        (
          ms: p.dateMs,
          iso: p.dateIso,
          kind: 'مشتريات',
          title: '${p.purchaseNumber} — ${p.supplierName}',
          amountLabel: Money(p.totalAmountMinor, p.currency).format(),
          color: pal.accent,
        ),
      for (final p in ctrl.supplierPayments.where((e) => inRange(e.dateIso)))
        (
          ms: p.dateMs,
          iso: p.dateIso,
          kind: 'سند صرف',
          title: '${p.supplierName} (${p.paymentMethod})',
          amountLabel: Money(p.amountMinor, p.currency).format(),
          color: pal.warning,
        ),
      for (final e in ctrl.expenses.where((e) => inRange(e.dateIso)))
        (
          ms: e.dateMs,
          iso: e.dateIso,
          kind: 'مصروف',
          title: '${e.description} (${e.category})',
          amountLabel: Money(e.amountMinor, e.currency).format(),
          color: pal.danger,
        ),
      for (final t in ctrl.transfers.where((e) => inRange(e.dateIso)))
        (
          ms: t.dateMs,
          iso: t.dateIso,
          kind: 'حوالة',
          title:
              '${t.receiverName.isEmpty ? t.senderName : t.receiverName} (${t.status.arabicLabel})',
          amountLabel: Money(t.amountMinor, t.currency).format(),
          color: pal.info,
        ),
    ]..sort((a, b) => b.ms.compareTo(a.ms));

    if (events.isEmpty) {
      return const [
        Padding(
          padding: EdgeInsets.symmetric(vertical: 12),
          child: Text('لا توجد حركات زمنية مسجلة في هذه الفترة'),
        ),
      ];
    }

    return [
      for (final ev in events.take(40))
        Card(
          margin: const EdgeInsets.only(bottom: 6),
          child: ListTile(
            dense: true,
            leading: StatusPill(
              label: ev.kind,
              bg: ev.color.withValues(alpha: 0.14),
              fg: ev.color,
            ),
            title: Text(
              ev.title,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 13),
            ),
            subtitle: Text(ev.iso, textDirection: TextDirection.ltr),
            trailing: Text(
              ev.amountLabel,
              style: TextStyle(
                fontWeight: FontWeight.w700,
                color: ev.color,
                fontFeatures: const [FontFeature.tabularFigures()],
              ),
            ),
          ),
        ),
    ];
  }
}
