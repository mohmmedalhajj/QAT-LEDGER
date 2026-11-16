import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/money.dart';
import '../core/theme.dart';
import '../data/models.dart';
import '../state/app_controller.dart';
import 'widgets/common_widgets.dart';

class DashboardTab extends StatelessWidget {
  const DashboardTab({super.key});

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final pal = context.pal;
    final policy = ctrl.currencyPolicy;
    final migration = ctrl.migrationSummary;

    final visibleCurrencies = policy.allowedCurrencies;

    return RefreshIndicator(
      onRefresh: ctrl.reloadAll,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          if (migration != null && migration.performed)
            Card(
              color: pal.successSoft,
              margin: const EdgeInsets.only(bottom: 12),
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Row(
                  children: [
                    Icon(Icons.verified_outlined, color: pal.success),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Text(
                        'تم ترحيل بيانات الإصدار القديم بنجاح (${migration.rowCounts.values.fold<int>(0, (a, b) => a + b)} سجل) مع فصل العملات والتحقق من القيود.',
                        style: TextStyle(fontSize: 12, color: pal.textPrimary),
                      ),
                    ),
                    IconButton(
                      onPressed: ctrl.dismissMigrationBanner,
                      icon: const Icon(Icons.close, size: 18),
                    ),
                  ],
                ),
              ),
            ),

          // License status & currency mode header strip
          Card(
            margin: const EdgeInsets.only(bottom: 14),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
              child: Row(
                children: [
                  Icon(
                    ctrl.licenseStatus.isValid
                        ? Icons.verified_user
                        : Icons.info_outline,
                    color: ctrl.licenseStatus.isValid ? pal.success : pal.warning,
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          ctrl.settings.agencyName,
                          style: Theme.of(context).textTheme.titleMedium,
                        ),
                        Text(
                          '${policy.mode.arabicLabel} • الترخيص: ${ctrl.licenseStatus.kind.arabicTitle}',
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                      ],
                    ),
                  ),
                  TextButton(
                    onPressed: () => ctrl.setSection(4, subTab: 3),
                    child: Text(
                      ctrl.licenseStatus.isValid ? 'التفاصيل' : 'تفعيل',
                    ),
                  ),
                ],
              ),
            ),
          ),

          // Quick actions grid
          Text('إجراءات سريعة', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          GridView.count(
            crossAxisCount: 4,
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            mainAxisSpacing: 8,
            crossAxisSpacing: 8,
            childAspectRatio: 0.95,
            children: [
              _QuickActionTile(
                icon: Icons.point_of_sale,
                label: 'فاتورة بيع',
                color: pal.brand,
                onTap: () => ctrl.setSection(1, subTab: 0),
              ),
              _QuickActionTile(
                icon: Icons.payments_outlined,
                label: 'سند قبض',
                color: pal.success,
                onTap: () => ctrl.setSection(1, subTab: 2),
              ),
              _QuickActionTile(
                icon: Icons.swap_horiz,
                label: 'حوالة مالية',
                color: pal.accent,
                onTap: () => ctrl.setSection(3, subTab: 0),
              ),
              _QuickActionTile(
                icon: Icons.receipt_long_outlined,
                label: 'مصروف يومي',
                color: pal.warning,
                onTap: () => ctrl.setSection(4, subTab: 0),
              ),
            ],
          ),
          const SizedBox(height: 18),

          // Per-currency separated KPI sections (never mixing YER + SAR silently)
          for (final cur in visibleCurrencies) ...[
            _CurrencySectionHeader(currency: cur),
            const SizedBox(height: 8),
            _CurrencyKpiGrid(
              currency: cur,
              metrics: ctrl.todayMetrics[cur] ??
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
            ),
            const SizedBox(height: 16),
          ],

          // Optional explicit user-rate preview card (clearly marked estimate only)
          if (policy.hasExplicitDisplayRate) ...[
            _DualCurrencyEstimateCard(
              policy: policy,
              metricsByCurrency: ctrl.todayMetrics,
            ),
            const SizedBox(height: 16),
          ],

          // Unlinked transfers alert if any
          if (ctrl.unlinkedTransfers.isNotEmpty)
            Card(
              color: pal.warningSoft,
              child: ListTile(
                leading: Icon(Icons.link_off, color: pal.warning),
                title: Text(
                  'توجد ${ctrl.unlinkedTransfers.length} حوالات غير مرتبطة ببطاقة مستلم',
                  style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700),
                ),
                subtitle: const Text(
                  'اضغط للمراجعة والربط في قسم الحوالات',
                  style: TextStyle(fontSize: 12),
                ),
                trailing: const Icon(Icons.arrow_forward_ios, size: 15),
                onTap: () => ctrl.setSection(3, subTab: 0),
              ),
            ),
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
        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 10),
        decoration: BoxDecoration(
          color: pal.card,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: pal.border),
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 38,
              height: 38,
              decoration: BoxDecoration(
                color: color.withValues(alpha: 0.13),
                borderRadius: BorderRadius.circular(11),
              ),
              child: Icon(icon, color: color, size: 20),
            ),
            const SizedBox(height: 6),
            Text(
              label,
              textAlign: TextAlign.center,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 11.5, fontWeight: FontWeight.w600),
            ),
          ],
        ),
      ),
    );
  }
}

class _CurrencySectionHeader extends StatelessWidget {
  const _CurrencySectionHeader({required this.currency});
  final CurrencyCode currency;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        CurrencyBadge(currency: currency),
        const SizedBox(width: 8),
        Text(
          'ملخص اليوم — ${currency.arabicName}',
          style: Theme.of(context).textTheme.titleMedium,
        ),
      ],
    );
  }
}

class _CurrencyKpiGrid extends StatelessWidget {
  const _CurrencyKpiGrid({
    required this.currency,
    required this.metrics,
  });

  final CurrencyCode currency;
  final CurrencyDashboardMetrics metrics;

  @override
  Widget build(BuildContext context) {
    final pal = context.pal;
    return GridView.count(
      crossAxisCount: 2,
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      mainAxisSpacing: 10,
      crossAxisSpacing: 10,
      childAspectRatio: 1.55,
      children: [
        KpiMetricCard(
          title: 'مبيعات اليوم',
          value: Money(metrics.salesTotalMinor, currency).format(),
          subtitle: 'نقدي: ${Money(metrics.salesPaidMinor, currency).format()}',
          icon: Icons.trending_up,
          toneColor: pal.brand,
          currency: currency,
        ),
        KpiMetricCard(
          title: 'صافي التدفق النقدي',
          value: Money(metrics.cashNetFlowMinor, currency).format(),
          subtitle: 'سندات قبض: ${Money(metrics.customerReceiptsMinor, currency).format()}',
          icon: Icons.account_balance_wallet_outlined,
          toneColor: metrics.cashNetFlowMinor >= 0 ? pal.success : pal.danger,
          currency: currency,
        ),
        KpiMetricCard(
          title: 'ديون العملاء القائمة',
          value: Money(metrics.totalCustomerDebtMinor, currency).format(),
          subtitle: 'آجل اليوم: ${Money(metrics.salesCreditMinor, currency).format()}',
          icon: Icons.people_outline,
          toneColor: pal.warning,
          currency: currency,
        ),
        KpiMetricCard(
          title: 'مستحقات الموردين',
          value: Money(metrics.totalSupplierPayableMinor, currency).format(),
          subtitle: 'مصروفات اليوم: ${Money(metrics.expensesMinor, currency).format()}',
          icon: Icons.local_shipping_outlined,
          toneColor: pal.accent,
          currency: currency,
        ),
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
                    style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700),
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
