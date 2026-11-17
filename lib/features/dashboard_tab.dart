import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../core/date_utils.dart';
import '../core/money.dart';
import '../core/theme.dart';
import '../data/models.dart';
import '../state/app_controller.dart';
import 'widgets/common_widgets.dart';
import 'widgets/erp_components.dart';

class DashboardTab extends StatelessWidget {
  const DashboardTab({super.key});

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final pal = context.pal;
    final policy = ctrl.currencyPolicy;
    final migration = ctrl.migrationSummary;
    final visibleCurrencies = policy.allowedCurrencies;
    final width = MediaQuery.sizeOf(context).width;
    final isWide = width >= 780;

    final lowStockCount = ctrl.batches
        .where((b) => b.remainingQtyMilli > 0 && b.remainingQtyMilli <= 5000)
        .length;
    final pendingTransfersCount = ctrl.transfers
        .where((t) => t.status == TransferStatus.pending)
        .length;

    return RefreshIndicator(
      onRefresh: ctrl.reloadAll,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          if (migration != null && migration.performed) ...[
            ErpStatusBanner(
              tone: ErpBannerTone.success,
              title: 'تم ترحيل بيانات الإصدار القديم بنجاح',
              subtitle:
                  'تم استيراد ${migration.rowCounts.values.fold<int>(0, (a, b) => a + b)} سجل مع فصل العملات والتحقق من القيود.',
              action: IconButton(
                onPressed: ctrl.dismissMigrationBanner,
                icon: const Icon(Icons.close, size: 18),
              ),
            ),
            const SizedBox(height: 12),
          ],

          // Executive Agency & License Strip
          Card(
            child: Padding(
              padding: const EdgeInsets.all(14),
              child: Row(
                children: [
                  Container(
                    width: 44,
                    height: 44,
                    decoration: BoxDecoration(
                      color: ctrl.licenseStatus.isValid
                          ? pal.successSoft
                          : pal.warningSoft,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Icon(
                      ctrl.licenseStatus.isValid
                          ? Icons.verified_user
                          : Icons.shield_outlined,
                      color: ctrl.licenseStatus.isValid
                          ? pal.success
                          : pal.warning,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Expanded(
                              child: Text(
                                ctrl.settings.agencyName,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: Theme.of(context).textTheme.titleMedium,
                              ),
                            ),
                            const SizedBox(width: 6),
                            StatusPill(
                              label: ctrl.licenseStatus.kind.arabicTitle,
                              bg: ctrl.licenseStatus.isValid
                                  ? pal.successSoft
                                  : pal.warningSoft,
                              fg: ctrl.licenseStatus.isValid
                                  ? pal.success
                                  : pal.warning,
                            ),
                          ],
                        ),
                        const SizedBox(height: 4),
                        Text(
                          '${policy.mode.arabicLabel} • ${QatDateUtils.todayIso()} • ${ctrl.currentUser?.displayName ?? 'admin'}',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                      ],
                    ),
                  ),
                  TextButton(
                    onPressed: () => ctrl.setSection(4, subTab: 3),
                    child: Text(ctrl.licenseStatus.isValid ? 'التفاصيل' : 'تفعيل'),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),

          // Period Filter Strip (Today / Yesterday / Weeks 1-5 / Month / Year / Custom / All)
          PeriodFilterStrip(
            filter: ctrl.periodFilter,
            onChanged: ctrl.setPeriodFilter,
          ),
          const SizedBox(height: 14),

          // Quick ERP Actions Grid
          Text('إجراءات سريعة', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          GridView.count(
            crossAxisCount: isWide ? 8 : 4,
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            mainAxisSpacing: 8,
            crossAxisSpacing: 8,
            childAspectRatio: isWide ? 0.98 : 0.92,
            children: [
              _QuickActionTile(
                icon: Icons.point_of_sale,
                label: 'فاتورة بيع',
                color: pal.brand,
                onTap: () => ctrl.navigateToRoute(AppScreenRoute.sales),
              ),
              _QuickActionTile(
                icon: Icons.payments_outlined,
                label: 'سند قبض',
                color: pal.success,
                onTap: () => ctrl.navigateToRoute(AppScreenRoute.customers),
              ),
              _QuickActionTile(
                icon: Icons.fact_check_outlined,
                label: 'التحاسب والجرد',
                color: pal.info,
                onTap: () => ctrl.navigateToRoute(AppScreenRoute.accounts),
              ),
              _QuickActionTile(
                icon: Icons.swap_horiz,
                label: 'حوالة مالية',
                color: pal.accent,
                onTap: () => ctrl.navigateToRoute(AppScreenRoute.transfers),
              ),
              _QuickActionTile(
                icon: Icons.receipt_long_outlined,
                label: 'مصروف يومي',
                color: pal.warning,
                onTap: () => ctrl.navigateToRoute(AppScreenRoute.expenses),
              ),
              _QuickActionTile(
                icon: Icons.table_chart_outlined,
                label: 'كشف حساب',
                color: pal.brand,
                onTap: () => ctrl.navigateToRoute(AppScreenRoute.statement),
              ),
              _QuickActionTile(
                icon: Icons.analytics_outlined,
                label: 'التقارير',
                color: pal.success,
                onTap: () => ctrl.navigateToRoute(AppScreenRoute.reports),
              ),
              _QuickActionTile(
                icon: Icons.backup_outlined,
                label: 'نسخ احتياطي',
                color: pal.accent,
                onTap: () => ctrl.setSection(4, subTab: 2),
              ),
            ],
          ),
          const SizedBox(height: 18),

          // Separated Per-Currency Financial Pulse + Inventory Valuation + 7-Day Chart
          for (final cur in visibleCurrencies) ...[
            _CurrencySectionHeader(
              currency: cur,
              periodLabel: ctrl.periodFilter.arabicLabel,
            ),
            const SizedBox(height: 8),
            _CurrencyKpiGrid(
              currency: cur,
              isWide: isWide,
              periodLabel: ctrl.periodFilter.arabicLabel,
              metrics: ctrl.filteredMetrics[cur] ??
                  ctrl.todayMetrics[cur] ??
                  CurrencyDashboardMetrics(
                    currency: cur,
                    salesTotalMinor: 0,
                    salesPaidMinor: 0,
                    salesCreditMinor: 0,
                    customerReceiptsMinor: 0,
                    purchasesTotalMinor: 0,
                    purchasesPaidMinor: 0,
                    supplierPaymentsMinor: 0,
                    expensesMinor: 0,
                    taxMinor: 0,
                    estimatedCogsMinor: 0,
                    totalCustomerDebtMinor: 0,
                    totalSupplierPayableMinor: 0,
                    transfersDeliveredMinor: 0,
                    transfersPendingMinor: 0,
                    transfersFeesMinor: 0,
                  ),
              valuation: ctrl.inventoryValuation[cur],
            ),
            const SizedBox(height: 12),
            if ((cur == CurrencyCode.yer
                    ? ctrl.chartSeriesYer
                    : ctrl.chartSeriesSar)
                .any((p) => p.salesMinor > 0 || p.profitMinor != 0)) ...[
              _DailyTrendChartCard(
                currency: cur,
                points: cur == CurrencyCode.yer
                    ? ctrl.chartSeriesYer
                    : ctrl.chartSeriesSar,
              ),
              const SizedBox(height: 16),
            ],
          ],

          // Optional explicit user-rate preview card (strictly display only)
          if (policy.hasExplicitDisplayRate) ...[
            _DualCurrencyEstimateCard(
              policy: policy,
              metricsByCurrency: ctrl.filteredMetrics.isNotEmpty
                  ? ctrl.filteredMetrics
                  : ctrl.todayMetrics,
            ),
            const SizedBox(height: 16),
          ],

          // Operational Alerts
          if (ctrl.unlinkedTransfers.isNotEmpty ||
              lowStockCount > 0 ||
              pendingTransfersCount > 0) ...[
            Text(
              'تنبيهات تشغيلية ومحاسبية',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 8),
            if (ctrl.unlinkedTransfers.isNotEmpty) ...[
              ErpStatusBanner(
                tone: ErpBannerTone.warning,
                icon: Icons.link_off,
                title:
                    'توجد ${ctrl.unlinkedTransfers.length} حوالات غير مرتبطة ببطاقة مستلم',
                subtitle: 'مراجعة وربط المستلمين تمنع تشتت كشوفات الحوالات.',
                action: TextButton(
                  onPressed: () => ctrl.setSection(3, subTab: 0),
                  child: const Text('مراجعة'),
                ),
              ),
              const SizedBox(height: 8),
            ],
            if (lowStockCount > 0) ...[
              ErpStatusBanner(
                tone: ErpBannerTone.info,
                icon: Icons.inventory_outlined,
                title: 'تنبيه مخزون: $lowStockCount دفعات قاربت على النفاد',
                subtitle: 'افتح شاشة المخزون أو التحاسب والجرد لمراجعة الكميات.',
                action: TextButton(
                  onPressed: () => ctrl.setSection(2, subTab: 0),
                  child: const Text('عرض'),
                ),
              ),
              const SizedBox(height: 8),
            ],
            if (pendingTransfersCount > 0)
              ErpStatusBanner(
                tone: ErpBannerTone.warning,
                icon: Icons.schedule,
                title: 'حوالات قيد التسليم: $pendingTransfersCount حوالة',
                subtitle: 'تابع حالة التسليم أو تحديثها في قسم الحوالات.',
                action: TextButton(
                  onPressed: () => ctrl.setSection(3, subTab: 1),
                  child: const Text('التفاصيل'),
                ),
              ),
            const SizedBox(height: 16),
          ],

          // Recent Invoices Snapshot Table
          if (ctrl.sales.isNotEmpty) ...[
            ErpSectionHeader(
              title: 'آخر الفواتير المسجلة',
              icon: Icons.receipt_long_outlined,
              trailing: TextButton(
                onPressed: () => ctrl.setSection(1, subTab: 1),
                child: const Text('سجل المبيعات'),
              ),
            ),
            const SizedBox(height: 8),
            ErpAccountingTable(
              columns: const [
                ErpTableColumn(title: 'الفاتورة', width: 92),
                ErpTableColumn(title: 'العميل', width: 120),
                ErpTableColumn(title: 'الحالة', width: 78),
                ErpTableColumn(
                  title: 'الإجمالي',
                  width: 112,
                  numeric: true,
                  align: TextAlign.end,
                ),
                ErpTableColumn(
                  title: 'المتبقي',
                  width: 105,
                  numeric: true,
                  align: TextAlign.end,
                ),
              ],
              rows: [
                for (final s in ctrl.sales.take(5))
                  [
                    ErpTableCell(s.invoiceNumber, bold: true, ltr: true),
                    ErpTableCell(s.customerName),
                    ErpTableCell(
                      s.paymentType.arabicLabel,
                      color: s.paymentType == PaymentType.paid
                          ? pal.success
                          : (s.paymentType == PaymentType.partial
                              ? pal.warning
                              : pal.danger),
                    ),
                    ErpTableCell(
                      Money(s.totalAmountMinor, s.currency).format(),
                      bold: true,
                    ),
                    ErpTableCell(
                      Money(s.remainingAmountMinor, s.currency).format(),
                      color: s.remainingAmountMinor > 0
                          ? pal.danger
                          : pal.textSecondary,
                    ),
                  ],
              ],
            ),
          ],
        ],
      ),
    );
  }
}

class _QuickActionTile extends StatelessWidget {
  const _QuickActionTile({
    required this.icon,
    required this.label,
    required this.color,
    required this.onTap,
  });

  final IconData icon;
  final String label;
  final Color color;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final pal = context.pal;
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(14),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 8),
        decoration: BoxDecoration(
          color: pal.card,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: pal.border),
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 36,
              height: 36,
              decoration: BoxDecoration(
                color: color.withValues(alpha: 0.13),
                borderRadius: BorderRadius.circular(10),
              ),
              child: Icon(icon, color: color, size: 19),
            ),
            const SizedBox(height: 6),
            Flexible(
              child: Text(
                label,
                textAlign: TextAlign.center,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _CurrencySectionHeader extends StatelessWidget {
  const _CurrencySectionHeader({
    required this.currency,
    required this.periodLabel,
  });
  final CurrencyCode currency;
  final String periodLabel;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        CurrencyBadge(currency: currency),
        const SizedBox(width: 8),
        Expanded(
          child: Text(
            'ملخص ($periodLabel) — ${currency.arabicName}',
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.titleMedium,
          ),
        ),
      ],
    );
  }
}

class _CurrencyKpiGrid extends StatelessWidget {
  const _CurrencyKpiGrid({
    required this.currency,
    required this.metrics,
    required this.isWide,
    required this.periodLabel,
    this.valuation,
  });

  final CurrencyCode currency;
  final CurrencyDashboardMetrics metrics;
  final bool isWide;
  final String periodLabel;
  final InventoryValuation? valuation;

  @override
  Widget build(BuildContext context) {
    final pal = context.pal;
    final val = valuation;
    return Column(
      children: [
        GridView.count(
          crossAxisCount: isWide ? 4 : 2,
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          mainAxisSpacing: 10,
          crossAxisSpacing: 10,
          childAspectRatio: isWide ? 1.38 : 1.28,
          children: [
            KpiMetricCard(
              title: 'مبيعات ($periodLabel)',
              value: Money(metrics.salesTotalMinor, currency).format(),
              subtitle:
                  'نقدي: ${Money(metrics.salesPaidMinor, currency).format()}',
              icon: Icons.trending_up,
              toneColor: pal.brand,
              currency: currency,
            ),
            KpiMetricCard(
              title: 'رصيد الصندوق النقدي',
              value: Money(metrics.cashBoxBalanceMinor, currency).format(),
              subtitle:
                  'سندات قبض: ${Money(metrics.customerReceiptsMinor, currency).format()}',
              icon: Icons.account_balance_wallet_outlined,
              toneColor:
                  metrics.cashBoxBalanceMinor >= 0 ? pal.success : pal.danger,
              currency: currency,
            ),
            KpiMetricCard(
              title: 'ديون العملاء القائمة',
              value: Money(metrics.totalCustomerDebtMinor, currency).format(),
              subtitle:
                  'آجل الفترة: ${Money(metrics.salesCreditMinor, currency).format()}',
              icon: Icons.people_outline,
              toneColor: pal.warning,
              currency: currency,
            ),
            KpiMetricCard(
              title: 'مستحقات الموردين',
              value: Money(metrics.totalSupplierPayableMinor, currency).format(),
              subtitle:
                  'مصاريف وضرائب: ${Money(metrics.expensesMinor + metrics.totalTaxAndOutflowsMinor, currency).format()}',
              icon: Icons.local_shipping_outlined,
              toneColor: pal.accent,
              currency: currency,
            ),
          ],
        ),
        if (val != null && val.batchCount > 0) ...[
          const SizedBox(height: 8),
          ErpSummaryBar(
            metrics: [
              ErpSummaryMetric(
                label: 'الدفعات المتوفرة',
                value: '${val.batchCount}',
              ),
              ErpSummaryMetric(
                label: 'رصيد الكمية',
                value: QuantityMath.formatMilli(val.totalQtyMilli),
              ),
              ErpSummaryMetric(
                label: 'قيمة المخزون بالتكلفة',
                value: Money(val.totalCostMinor, currency).format(),
              ),
              ErpSummaryMetric(
                label: 'الربح المتوقع بالمخزون',
                value: Money(val.expectedProfitMinor, currency).format(),
                toneColor: pal.success,
              ),
            ],
          ),
        ],
      ],
    );
  }
}

class _DailyTrendChartCard extends StatelessWidget {
  const _DailyTrendChartCard({
    required this.currency,
    required this.points,
  });

  final CurrencyCode currency;
  final List<DailyChartPoint> points;

  @override
  Widget build(BuildContext context) {
    final pal = context.pal;
    final maxVal = points.fold<int>(
      1,
      (m, p) {
        final high = p.salesMinor > p.profitMinor.abs()
            ? p.salesMinor
            : p.profitMinor.abs();
        return high > m ? high : m;
      },
    );

    return Card(
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
                    'اتجاه المبيعات وصافي الربح (آخر 7 أيام — ${currency.code})',
                    style: const TextStyle(
                      fontSize: 12.5,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            SizedBox(
              height: 112,
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  for (final pt in points)
                    Expanded(
                      child: Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 3),
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
                                          .clamp(0.05, 1.0),
                                      child: Container(
                                        width: 10,
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
                                              .clamp(0.05, 1.0),
                                      child: Container(
                                        width: 10,
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
            const SizedBox(height: 8),
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                _LegendDot(color: pal.brand, label: 'المبيعات'),
                const SizedBox(width: 14),
                _LegendDot(color: pal.success, label: 'صافي الربح'),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _LegendDot extends StatelessWidget {
  const _LegendDot({required this.color, required this.label});
  final Color color;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 9,
          height: 9,
          decoration: BoxDecoration(
            color: color,
            borderRadius: BorderRadius.circular(3),
          ),
        ),
        const SizedBox(width: 4),
        Text(label, style: const TextStyle(fontSize: 11)),
      ],
    );
  }
}

class _DualCurrencyEstimateCard extends StatelessWidget {
  const _DualCurrencyEstimateCard({
    required this.policy,
    required this.metricsByCurrency,
  });

  final CurrencyPolicy policy;
  final Map<CurrencyCode, CurrencyDashboardMetrics> metricsByCurrency;

  @override
  Widget build(BuildContext context) {
    final pal = context.pal;
    final yerSales = metricsByCurrency[CurrencyCode.yer]?.salesTotalMinor ?? 0;
    final sarSales = metricsByCurrency[CurrencyCode.sar]?.salesTotalMinor ?? 0;
    final sarAsYer = policy.convertSarMinorToYerPreview(sarSales);
    final combinedBig = sarAsYer == null
        ? null
        : BigInt.from(yerSales) + BigInt.from(sarAsYer);
    final combinedYer =
        combinedBig != null && combinedBig.isValidInt ? combinedBig.toInt() : null;

    return Card(
      color: pal.accentSoft,
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.currency_exchange, color: pal.accent, size: 18),
                const SizedBox(width: 8),
                const Expanded(
                  child: Text(
                    'إجمالي المبيعات التقديري بالريال اليمني (للعرض فقط)',
                    style:
                        TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 6),
            Text(
              combinedYer == null
                  ? 'تجاوز الحد الأقصى للعرض'
                  : Money(combinedYer, CurrencyCode.yer).format(),
              style: TextStyle(
                fontSize: 19,
                fontWeight: FontWeight.w700,
                color: pal.textPrimary,
                fontFeatures: const [FontFeature.tabularFigures()],
              ),
            ),
            const SizedBox(height: 4),
            Text(
              'سعر الصرف المعتمد من الإعدادات: 1 SAR = ${QuantityMath.formatMilli(policy.userDisplaySarToYerMilli)} YER — الدفاتر الفعلية مفصولة كلياً.',
              style: TextStyle(fontSize: 11, color: pal.textSecondary),
            ),
          ],
        ),
      ),
    );
  }
}
