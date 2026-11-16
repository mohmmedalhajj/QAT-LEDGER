import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../core/date_utils.dart';
import '../../core/money.dart';
import '../../core/theme.dart';
import '../../data/models.dart';
import '../../services/whatsapp_share_service.dart';
import '../../state/app_controller.dart';
import '../widgets/common_widgets.dart';
import '../widgets/erp_components.dart';

/// Enterprise Settlement & Inventory Reconciliation module (`التحاسب والجرد`):
/// 1) Physical Inventory Count & Variance Adjustment (`جرد المخزون والمطابقة`)
/// 2) Daily Cash Drawer & Ledger Settlement (`التحاسب اليومي وإغلاق الصندوق`)
class ReconciliationSettlementView extends StatefulWidget {
  const ReconciliationSettlementView({super.key});

  @override
  State<ReconciliationSettlementView> createState() =>
      _ReconciliationSettlementViewState();
}

class _ReconciliationSettlementViewState
    extends State<ReconciliationSettlementView> {
  int _mode = 0; // 0 = Inventory Count, 1 = Daily Cash Settlement
  final Map<int, TextEditingController> _countCtrls = {};
  final _reconNotesCtrl = TextEditingController();
  bool _applyingRecon = false;

  CurrencyCode? _settlementCurrency;
  final _actualCashCtrl = TextEditingController();
  final _settlementNotesCtrl = TextEditingController();
  bool _savingSettlement = false;

  @override
  void dispose() {
    for (final c in _countCtrls.values) {
      c.dispose();
    }
    _reconNotesCtrl.dispose();
    _actualCashCtrl.dispose();
    _settlementNotesCtrl.dispose();
    super.dispose();
  }

  TextEditingController _ctrlForBatch(InventoryBatch b) {
    return _countCtrls.putIfAbsent(
      b.id,
      () => TextEditingController(
        text: QuantityMath.formatMilli(b.remainingQtyMilli),
      ),
    );
  }

  Future<void> _applyReconciliation(AppController ctrl) async {
    final activeBatches =
        ctrl.batches.where((b) => !b.isArchived).toList();
    final changes = <({InventoryBatch batch, int countedQtyMilli})>[];
    for (final b in activeBatches) {
      final ctrlTxt = _ctrlForBatch(b).text;
      final counted = QuantityMath.parseMilli(ctrlTxt);
      if (counted != b.remainingQtyMilli) {
        changes.add((batch: b, countedQtyMilli: counted));
      }
    }
    if (changes.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('جميع الكميات مطابقة للدفاتر، لا توجد فروقات لتسويتها'),
        ),
      );
      return;
    }

    final ok = await showErpConfirmDialog(
      context,
      title: 'اعتماد تسوية الجرد',
      message:
          'سيتم تعديل أرصدة ${changes.length} دفعة مخزون وفق الكميات الفعلية المعدودة وتوثيق الفروقات في سجل التدقيق. هل تريد المتابعة؟',
      confirmLabel: 'اعتماد الجرد',
    );
    if (!ok || !mounted) return;

    setState(() => _applyingRecon = true);
    try {
      await ctrl.applyInventoryReconciliation(
        counts: changes,
        notes: _reconNotesCtrl.text,
      );
      for (final c in _countCtrls.values) {
        c.dispose();
      }
      _countCtrls.clear();
      _reconNotesCtrl.clear();
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('تم اعتماد تسوية الجرد لـ ${changes.length} دفعات بنجاح'),
        ),
      );
    } finally {
      if (mounted) setState(() => _applyingRecon = false);
    }
  }

  Future<void> _saveSettlement(
    AppController ctrl,
    CurrencyCode cur,
    CurrencyDashboardMetrics m,
  ) async {
    final expectedCash = m.cashNetFlowMinor;
    final actualCounted = Money.parseMinorUnits(_actualCashCtrl.text);
    final variance = actualCounted - expectedCash;
    final pendingTransfers = ctrl.transfers
        .where(
          (t) =>
              t.currency == cur && t.status == TransferStatus.pending,
        )
        .length;

    final ok = await showErpConfirmDialog(
      context,
      title: 'اعتماد التحاسب اليومي (${cur.code})',
      message:
          'الصافي المتوقع: ${Money(expectedCash, cur).format()}\n'
          'المعدود فعلياً: ${Money(actualCounted, cur).format()}\n'
          'الفرق: ${Money(variance, cur).format()}\n\nهل تريد توثيق هذا الإغلاق؟',
      confirmLabel: 'توثيق التحاسب',
    );
    if (!ok || !mounted) return;

    setState(() => _savingSettlement = true);
    try {
      final nowMs = DateTime.now().millisecondsSinceEpoch;
      final snap = DailySettlementSnapshot(
        id: '${nowMs}_${cur.code}',
        dateIso: QatDateUtils.todayIso(),
        epochMs: nowMs,
        currency: cur,
        expectedCashMinor: expectedCash,
        actualCountedMinor: actualCounted,
        varianceMinor: variance,
        salesCashMinor: m.salesPaidMinor,
        customerReceiptsMinor: m.customerReceiptsMinor,
        purchasesCashMinor: m.purchasesPaidMinor,
        supplierPaymentsMinor: m.supplierPaymentsMinor,
        expensesMinor: m.expensesMinor,
        transferFeesMinor: m.transfersFeesMinor,
        pendingTransfersCount: pendingTransfers,
        notes: _settlementNotesCtrl.text.trim(),
        createdBy: ctrl.currentUser?.username ?? 'admin',
      );
      await ctrl.saveDailySettlement(snap);
      _settlementNotesCtrl.clear();
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('تم توثيق التحاسب اليومي وحفظه في السجل')),
      );
    } finally {
      if (mounted) setState(() => _savingSettlement = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final pal = context.pal;
    final policy = ctrl.currencyPolicy;
    final activeCur = (_settlementCurrency != null &&
            policy.allowedCurrencies.contains(_settlementCurrency))
        ? _settlementCurrency!
        : policy.defaultCurrency;

    final todayM = ctrl.todayMetrics[activeCur] ??
        CurrencyDashboardMetrics(
          currency: activeCur,
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
        );

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        SegmentedButton<int>(
          segments: const [
            ButtonSegment<int>(
              value: 0,
              icon: Icon(Icons.inventory_outlined, size: 17),
              label: Text('جرد المخزون والمطابقة'),
            ),
            ButtonSegment<int>(
              value: 1,
              icon: Icon(Icons.point_of_sale_outlined, size: 17),
              label: Text('التحاسب اليومي للصندوق'),
            ),
          ],
          selected: {_mode},
          onSelectionChanged: (s) => setState(() => _mode = s.first),
        ),
        const SizedBox(height: 14),
        if (_mode == 0)
          _buildInventoryCountSection(ctrl, pal)
        else
          _buildDailySettlementSection(ctrl, pal, policy, activeCur, todayM),
      ],
    );
  }

  Widget _buildInventoryCountSection(AppController ctrl, QatPalette pal) {
    final activeBatches = ctrl.batches;
    int changedBatches = 0;
    final impactByCur = <CurrencyCode, int>{};
    for (final b in activeBatches) {
      final counted = QuantityMath.parseMilli(_ctrlForBatch(b).text);
      final diff = counted - b.remainingQtyMilli;
      if (diff != 0) {
        changedBatches += 1;
        final impact = ((diff * b.buyPriceMinor) / 1000).round();
        impactByCur[b.currency] = (impactByCur[b.currency] ?? 0) + impact;
      }
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const ErpStatusBanner(
          tone: ErpBannerTone.info,
          title: 'الجرد الفعلي ومطابقة الدفعات',
          subtitle:
              'أدخل الكمية الفعلية الموجودة في المستودع لاحتساب العجز أو الزيادة وتحديث الرصيد.',
        ),
        const SizedBox(height: 12),
        if (activeBatches.isEmpty)
          const EmptyLedgerState(
            icon: Icons.inventory_2_outlined,
            title: 'لا توجد دفعات مخزون للجرد',
            subtitle: 'أضف دفعات مخزون أولاً للبدء في عملية الجرد الفعلي',
          )
        else ...[
          for (final b in activeBatches)
            Builder(
              builder: (context) {
                final c = _ctrlForBatch(b);
                final counted = QuantityMath.parseMilli(c.text);
                final diff = counted - b.remainingQtyMilli;
                final impactMinor = ((diff * b.buyPriceMinor) / 1000).round();
                return Card(
                  margin: const EdgeInsets.only(bottom: 10),
                  child: Padding(
                    padding: const EdgeInsets.all(14),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Expanded(
                              child: Text(
                                b.qatTypeName,
                                style: const TextStyle(
                                  fontWeight: FontWeight.w700,
                                  fontSize: 14,
                                ),
                              ),
                            ),
                            CurrencyBadge(currency: b.currency),
                            const SizedBox(width: 6),
                            StatusPill(
                              label: diff == 0
                                  ? 'مطابق'
                                  : (diff > 0
                                      ? 'زيادة +${QuantityMath.formatMilli(diff)}'
                                      : 'عجز ${QuantityMath.formatMilli(diff)}'),
                              bg: diff == 0
                                  ? pal.successSoft
                                  : (diff > 0
                                      ? pal.infoSoft
                                      : pal.dangerSoft),
                              fg: diff == 0
                                  ? pal.success
                                  : (diff > 0 ? pal.info : pal.danger),
                            ),
                          ],
                        ),
                        const SizedBox(height: 8),
                        Row(
                          children: [
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    'الرصيد الدفتري: ${QuantityMath.formatMilli(b.remainingQtyMilli)} ${b.unit}',
                                    style: TextStyle(
                                      fontSize: 12.5,
                                      color: pal.textSecondary,
                                    ),
                                  ),
                                  Text(
                                    'تكلفة الوحدة: ${Money(b.buyPriceMinor, b.currency).format()}',
                                    style: TextStyle(
                                      fontSize: 12,
                                      color: pal.textSecondary,
                                    ),
                                  ),
                                  if (diff != 0)
                                    Text(
                                      'الأثر المالي: ${Money(impactMinor, b.currency).format()}',
                                      style: TextStyle(
                                        fontSize: 12,
                                        fontWeight: FontWeight.w700,
                                        color: diff > 0
                                            ? pal.success
                                            : pal.danger,
                                      ),
                                    ),
                                ],
                              ),
                            ),
                            const SizedBox(width: 12),
                            SizedBox(
                              width: 120,
                              child: AccountingNumberField(
                                controller: c,
                                label: 'الفعلي (${b.unit})',
                                onChanged: (_) => setState(() {}),
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                );
              },
            ),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(14),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  ErpSummaryBar(
                    metrics: [
                      ErpSummaryMetric(
                        label: 'الدفعات المعدلة',
                        value: changedBatches.toString(),
                      ),
                      for (final e in impactByCur.entries)
                        ErpSummaryMetric(
                          label: 'صافي فرق التكلفة (${e.key.code})',
                          value: Money(e.value, e.key).format(),
                          toneColor:
                              e.value < 0 ? pal.danger : pal.success,
                        ),
                    ],
                  ),
                  const SizedBox(height: 10),
                  TextField(
                    controller: _reconNotesCtrl,
                    decoration: const InputDecoration(
                      labelText: 'سبب الفروقات / ملاحظات لجنة الجرد',
                    ),
                  ),
                  const SizedBox(height: 12),
                  FilledButton.icon(
                    onPressed: _applyingRecon
                        ? null
                        : () => _applyReconciliation(ctrl),
                    icon: const Icon(Icons.fact_check_outlined),
                    label: Text(
                      _applyingRecon
                          ? 'جاري اعتماد الجرد...'
                          : 'اعتماد وتعديل أرصدة المخزون',
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
        if (ctrl.reconciliationLogs.isNotEmpty) ...[
          const SizedBox(height: 18),
          const ErpSectionHeader(
            title: 'سجل تسويات الجرد السابقة',
            icon: Icons.history,
          ),
          const SizedBox(height: 8),
          ErpAccountingTable(
            columns: const [
              ErpTableColumn(title: 'التاريخ', width: 92),
              ErpTableColumn(title: 'الصنف', width: 112),
              ErpTableColumn(
                title: 'دفتري',
                width: 72,
                numeric: true,
                align: TextAlign.end,
              ),
              ErpTableColumn(
                title: 'فعلي',
                width: 72,
                numeric: true,
                align: TextAlign.end,
              ),
              ErpTableColumn(
                title: 'الفرق',
                width: 76,
                numeric: true,
                align: TextAlign.end,
              ),
              ErpTableColumn(
                title: 'الأثر المالي',
                width: 110,
                numeric: true,
                align: TextAlign.end,
              ),
            ],
            rows: [
              for (final log in ctrl.reconciliationLogs.take(20))
                [
                  ErpTableCell(log.dateIso, ltr: true),
                  ErpTableCell(log.qatTypeName, bold: true),
                  ErpTableCell(QuantityMath.formatMilli(log.systemQtyMilli)),
                  ErpTableCell(QuantityMath.formatMilli(log.countedQtyMilli)),
                  ErpTableCell(
                    QuantityMath.formatMilli(log.diffQtyMilli),
                    color: log.diffQtyMilli < 0 ? pal.danger : pal.success,
                  ),
                  ErpTableCell(
                    Money(log.financialImpactMinor, log.currency).format(),
                    bold: true,
                    color: log.financialImpactMinor < 0
                        ? pal.danger
                        : pal.success,
                  ),
                ],
            ],
          ),
        ],
      ],
    );
  }

  Widget _buildDailySettlementSection(
    AppController ctrl,
    QatPalette pal,
    CurrencyPolicy policy,
    CurrencyCode activeCur,
    CurrencyDashboardMetrics m,
  ) {
    final expectedCash = m.cashNetFlowMinor;
    final actualCounted = Money.parseMinorUnits(_actualCashCtrl.text);
    final variance = actualCounted - expectedCash;
    final hasInput = _actualCashCtrl.text.trim().isNotEmpty;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const ErpSectionHeader(
                  title: 'مطابقة وإغلاق الصندوق اليومي',
                  icon: Icons.account_balance_wallet_outlined,
                ),
                const SizedBox(height: 12),
                CurrencySelectorRow(
                  policy: policy,
                  selected: activeCur,
                  onChanged: (c) => setState(() => _settlementCurrency = c),
                ),
                const Divider(height: 18),
                ErpDetailRow(
                  label: '+ المبيعات النقدية اليوم',
                  value: Money(m.salesPaidMinor, activeCur).format(),
                  valueColor: pal.success,
                ),
                ErpDetailRow(
                  label: '+ سندات قبض العملاء اليوم',
                  value: Money(m.customerReceiptsMinor, activeCur).format(),
                  valueColor: pal.success,
                ),
                ErpDetailRow(
                  label: '- المشتريات النقدية اليوم',
                  value: Money(m.purchasesPaidMinor, activeCur).format(),
                  valueColor: pal.danger,
                ),
                ErpDetailRow(
                  label: '- سندات صرف الموردين اليوم',
                  value: Money(m.supplierPaymentsMinor, activeCur).format(),
                  valueColor: pal.danger,
                ),
                ErpDetailRow(
                  label: '- المصروفات اليومية',
                  value: Money(m.expensesMinor, activeCur).format(),
                  valueColor: pal.danger,
                ),
                ErpDetailRow(
                  label: '- عمولات الحوالات',
                  value: Money(m.transfersFeesMinor, activeCur).format(),
                  valueColor: pal.danger,
                ),
                const Divider(height: 16),
                ErpDetailRow(
                  label: 'صافي النقد المتوقع بالصندوق',
                  value: Money(expectedCash, activeCur).format(),
                  bold: true,
                  valueColor: pal.brand,
                ),
                const SizedBox(height: 12),
                AccountingNumberField(
                  controller: _actualCashCtrl,
                  label: 'النقد الفعلي المعدود في الدرج (${activeCur.shortSymbol})',
                  onChanged: (_) => setState(() {}),
                ),
                if (hasInput) ...[
                  const SizedBox(height: 10),
                  ErpStatusBanner(
                    tone: variance == 0
                        ? ErpBannerTone.success
                        : (variance > 0
                            ? ErpBannerTone.info
                            : ErpBannerTone.danger),
                    title: variance == 0
                        ? 'الصندوق مطابق تماماً (0 فرق)'
                        : (variance > 0
                            ? 'فائض في الصندوق: ${Money(variance, activeCur).format()}'
                            : 'عجز في الصندوق: ${Money(variance.abs(), activeCur).format()}'),
                    subtitle:
                        'الآجل القائم للعملاء: ${Money(m.totalCustomerDebtMinor, activeCur).format()}',
                  ),
                ],
                const SizedBox(height: 10),
                TextField(
                  controller: _settlementNotesCtrl,
                  decoration: const InputDecoration(
                    labelText: 'ملاحظات التحاسب وتقفيل الوردية',
                  ),
                ),
                const SizedBox(height: 12),
                FilledButton.icon(
                  onPressed: _savingSettlement
                      ? null
                      : () => _saveSettlement(ctrl, activeCur, m),
                  icon: const Icon(Icons.lock_clock_outlined),
                  label: Text(
                    _savingSettlement
                        ? 'جاري الحفظ...'
                        : 'اعتماد وتوثيق التحاسب اليومي',
                  ),
                ),
              ],
            ),
          ),
        ),
        if (ctrl.dailySettlements.isNotEmpty) ...[
          const SizedBox(height: 18),
          const ErpSectionHeader(
            title: 'سجل التحاسب والإغلاقات الموثقة',
            icon: Icons.history_edu_outlined,
          ),
          const SizedBox(height: 8),
          for (final snap in ctrl.dailySettlements.take(15))
            Card(
              margin: const EdgeInsets.only(bottom: 8),
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Expanded(
                          child: Text(
                            'تحاسب ${snap.dateIso} • ${snap.createdBy}',
                            style: const TextStyle(fontWeight: FontWeight.w700),
                          ),
                        ),
                        CurrencyBadge(currency: snap.currency),
                        const SizedBox(width: 6),
                        StatusPill(
                          label: snap.varianceMinor == 0
                              ? 'مطابق'
                              : (snap.varianceMinor > 0
                                  ? 'فائض ${Money(snap.varianceMinor, snap.currency).format()}'
                                  : 'عجز ${Money(snap.varianceMinor.abs(), snap.currency).format()}'),
                          bg: snap.varianceMinor == 0
                              ? pal.successSoft
                              : (snap.varianceMinor > 0
                                  ? pal.infoSoft
                                  : pal.dangerSoft),
                          fg: snap.varianceMinor == 0
                              ? pal.success
                              : (snap.varianceMinor > 0
                                  ? pal.info
                                  : pal.danger),
                        ),
                      ],
                    ),
                    const SizedBox(height: 6),
                    Text(
                      'المتوقع: ${Money(snap.expectedCashMinor, snap.currency).format()} • الفعلي: ${Money(snap.actualCountedMinor, snap.currency).format()}',
                      style: TextStyle(
                        fontSize: 12.5,
                        color: pal.textSecondary,
                        fontFeatures: const [FontFeature.tabularFigures()],
                      ),
                    ),
                    if (snap.notes.isNotEmpty)
                      Padding(
                        padding: const EdgeInsets.only(top: 4),
                        child: Text(
                          snap.notes,
                          style: TextStyle(fontSize: 12, color: pal.textSecondary),
                        ),
                      ),
                    Align(
                      alignment: AlignmentDirectional.centerEnd,
                      child: TextButton.icon(
                        onPressed: () async {
                          await WhatsappShareService.instance.openWhatsApp(
                            text:
                                '${ctrl.settings.agencyName} — تقرير التحاسب اليومي (${snap.dateIso})\n'
                                'العملة: ${snap.currency.arabicName}\n'
                                'المتوقع: ${Money(snap.expectedCashMinor, snap.currency).format()}\n'
                                'الفعلي: ${Money(snap.actualCountedMinor, snap.currency).format()}\n'
                                'الفرق: ${Money(snap.varianceMinor, snap.currency).format()}',
                          );
                        },
                        icon: const Icon(Icons.chat_outlined, size: 15),
                        label: const Text('مشاركة واتساب'),
                      ),
                    ),
                  ],
                ),
              ),
            ),
        ],
      ],
    );
  }
}
