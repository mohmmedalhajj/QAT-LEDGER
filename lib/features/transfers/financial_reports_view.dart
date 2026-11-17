import 'dart:async';

import 'package:material_ui/material_ui.dart';
import 'package:printing/printing.dart';
import 'package:provider/provider.dart';

import '../../core/date_utils.dart';
import '../../core/money.dart';
import '../../core/theme.dart';
import '../../data/models.dart';
import '../../services/pdf_export_service.dart';
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
                if (data != null)
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
                      await Printing.layoutPdf(onLayout: (_) async => pdf);
                    },
                    icon: const Icon(Icons.picture_as_pdf_outlined, size: 18),
                  ),
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
              const SizedBox(height: 16),
            ],
      ],
    );
  }
}
