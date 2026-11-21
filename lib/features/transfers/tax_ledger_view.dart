import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../core/money.dart';
import '../../core/theme.dart';
import '../../data/models.dart';
import '../../state/app_controller.dart';
import '../widgets/common_widgets.dart';
import '../widgets/erp_components.dart';

class TaxLedgerView extends StatefulWidget {
  const TaxLedgerView({super.key});

  @override
  State<TaxLedgerView> createState() => _TaxLedgerViewState();
}

class _TaxLedgerViewState extends State<TaxLedgerView> {
  CurrencyCode? _currencyFilter;

  Future<void> _openTaxDialog(
    BuildContext context,
    AppController ctrl, {
    QatTaxEntry? existing,
  }) async {
    final qtyCtrl = TextEditingController(
      text: existing != null
          ? QuantityMath.formatMilli(existing.totalQtyMilli)
          : '0',
    );
    final rateCtrl = TextEditingController(
      text: existing != null
          ? Money.formatMinor(existing.taxRateMinor, showZeroDecimals: false)
              .replaceAll(',', '')
          : '0',
    );
    final amountCtrl = TextEditingController(
      text: existing != null
          ? Money.formatMinor(existing.taxAmountMinor, showZeroDecimals: false)
              .replaceAll(',', '')
          : '',
    );
    final notesCtrl = TextEditingController(text: existing?.notes ?? '');
    CurrencyCode cur = existing?.currency ?? ctrl.currencyPolicy.defaultCurrency;
    int taxDateMs = existing?.dateMs ?? DateTime.now().millisecondsSinceEpoch;

    void tryAutoCalcTax() {
      final qtyMilli = QuantityMath.parseMilli(qtyCtrl.text);
      final rateMinor = Money.parseMinorUnits(rateCtrl.text);
      if (qtyMilli > 0 && rateMinor > 0) {
        final computed = InvoiceMath.lineTotalMinor(
          qtyMilli: qtyMilli,
          unitPriceMinor: rateMinor,
        );
        amountCtrl.text =
            Money.formatMinor(computed, showZeroDecimals: false)
                .replaceAll(',', '');
      }
    }

    await showDialog<void>(
      context: context,
      builder: (ctx) => ErpDialogControllerScope(
        controllers: [
          qtyCtrl,
          rateCtrl,
          amountCtrl,
          notesCtrl,
        ],
        child: StatefulBuilder(
          builder: (ctx, setModalState) => AlertDialog(
            title: Text(
              existing == null
                  ? 'قيد ضريبة القات اليومية'
                  : 'تعديل قيد ضريبة القات',
            ),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (existing == null) ...[
                  ErpDatePickerTile(
                    label: 'تاريخ الضريبة',
                    dateMs: taxDateMs,
                    onChanged: (ms) => setModalState(() => taxDateMs = ms),
                  ),
                  const SizedBox(height: 8),
                  CurrencySelectorRow(
                    policy: ctrl.currencyPolicy,
                    selected: cur,
                    onChanged: (c) => setModalState(() => cur = c),
                  ),
                  const SizedBox(height: 8),
                  AccountingNumberField(
                    controller: qtyCtrl,
                    label: 'الكمية الإجمالية الخاضعة للضريبة',
                    onChanged: (_) => tryAutoCalcTax(),
                  ),
                  const SizedBox(height: 8),
                  AccountingNumberField(
                    controller: rateCtrl,
                    label: 'فئة الضريبة للوحدة (اختياري)',
                    onChanged: (_) => tryAutoCalcTax(),
                  ),
                  const SizedBox(height: 8),
                ],
                AccountingNumberField(
                  controller: amountCtrl,
                  label: 'مبلغ ضريبة القات (${cur.shortSymbol}) *',
                ),
                const SizedBox(height: 8),
                TextField(
                  controller: notesCtrl,
                  decoration: const InputDecoration(labelText: 'ملاحظات الضريبة'),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('إلغاء'),
            ),
            FilledButton(
              onPressed: () async {
                final taxMinor = Money.parseMinorUnits(amountCtrl.text);
                if (taxMinor <= 0 &&
                    (existing?.stallOutflowMinor ?? 0) <= 0 &&
                    (existing?.laborOutflowMinor ?? 0) <= 0) {
                  return;
                }
                if (existing == null) {
                  await ctrl.repo.addTaxEntry(
                    totalQtyMilli: QuantityMath.parseMilli(qtyCtrl.text),
                    taxRateMinor: Money.parseMinorUnits(rateCtrl.text),
                    taxAmountMinor: taxMinor,
                    stallOutflowMinor: 0,
                    laborOutflowMinor: 0,
                    currency: cur,
                    notes: notesCtrl.text,
                    dateMs: taxDateMs,
                  );
                } else {
                  await ctrl.repo.updateTaxEntry(
                    id: existing.id,
                    taxAmountMinor: taxMinor,
                    stallOutflowMinor: existing.stallOutflowMinor,
                    laborOutflowMinor: existing.laborOutflowMinor,
                    notes: notesCtrl.text,
                  );
                }
                await ctrl.reloadAll();
                if (ctx.mounted) Navigator.pop(ctx);
              },
              child: const Text('حفظ'),
            ),
          ],
        ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final pal = context.pal;
    final list = ctrl.taxEntries.where((t) {
      if (_currencyFilter != null && t.currency != _currencyFilter) {
        return false;
      }
      return true;
    }).toList();

    final summaryMetrics = <ErpSummaryMetric>[
      ErpSummaryMetric(label: 'قيود الضرائب', value: '${list.length}'),
    ];
    for (final cur in ctrl.currencyPolicy.allowedCurrencies) {
      final byCur = list.where((t) => t.currency == cur);
      if (byCur.isNotEmpty) {
        final tot = byCur.fold<int>(0, (s, e) => s + e.taxAmountMinor);
        summaryMetrics.add(
          ErpSummaryMetric(
            label: 'إجمالي ${cur.code}',
            value: Money(tot, cur).format(),
            toneColor: pal.warning,
          ),
        );
      }
    }

    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _openTaxDialog(context, ctrl),
        icon: const Icon(Icons.add),
        label: const Text('قيد ضريبة'),
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 10, 16, 0),
            child: ErpStatusBanner(
              tone: ErpBannerTone.info,
              title: 'دفتر ضريبة القات مفصول عن المصروفات اليومية',
              subtitle:
                  'يُخصّص هذا السجل لضريبة القات فقط. مصروفات إيجار المفرش وأجور العمال والكهرباء تُسجَّل في تبويب المصاريف التشغيلية.',
              trailing: TextButton(
                onPressed: () => ctrl.setSection(4, subTab: 0),
                child: const Text('فتح المصاريف'),
              ),
            ),
          ),
          if (ctrl.currencyPolicy.canSwitchCurrency)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 10, 16, 0),
              child: SizedBox(
                width: double.infinity,
                child: Wrap(
                  spacing: 6,
                  runSpacing: 6,
                  children: [
                    ChoiceChip(
                      label: const Text('الكل'),
                      selected: _currencyFilter == null,
                      onSelected: (_) => setState(() => _currencyFilter = null),
                    ),
                    for (final cur in ctrl.currencyPolicy.allowedCurrencies)
                      ChoiceChip(
                        label: Text(cur.code),
                        selected: _currencyFilter == cur,
                        onSelected: (_) => setState(() => _currencyFilter = cur),
                      ),
                  ],
                ),
              ),
            ),
          if (list.isNotEmpty)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 10, 16, 0),
              child: ErpSummaryBar(metrics: summaryMetrics),
            ),
          Expanded(
            child: list.isEmpty
                ? const EmptyLedgerState(
                    icon: Icons.account_balance_outlined,
                    title: 'سجل الضرائب فارغ',
                    subtitle: 'وثق قيود الضرائب اليومية منفصلة حسب العملة',
                  )
                : ListView.separated(
                    padding: const EdgeInsets.fromLTRB(16, 12, 16, 84),
                    itemCount: list.length,
                    separatorBuilder: (_, idx) => const SizedBox(height: 8),
                    itemBuilder: (_, idx) {
                      final t = list[idx];
                      return Card(
                        child: ListTile(
                          title: Row(
                            children: [
                              Expanded(
                                child: Text(
                                  Money(t.taxAmountMinor, t.currency).format(),
                                  style: const TextStyle(
                                    fontWeight: FontWeight.w700,
                                    fontFeatures: [
                                      FontFeature.tabularFigures(),
                                    ],
                                  ),
                                ),
                              ),
                              CurrencyBadge(currency: t.currency),
                            ],
                          ),
                          subtitle: Text(
                            '${t.dateIso} • ${t.source}'
                            '${t.stallOutflowMinor > 0 ? ' • قعادة: ${Money(t.stallOutflowMinor, t.currency).format()}' : ''}'
                            '${t.laborOutflowMinor > 0 ? ' • عمال: ${Money(t.laborOutflowMinor, t.currency).format()}' : ''}'
                            '${t.notes.isNotEmpty ? ' • ${t.notes}' : ''}',
                          ),
                          trailing: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              IconButton(
                                tooltip: 'تعديل',
                                onPressed: () => _openTaxDialog(
                                  context,
                                  ctrl,
                                  existing: t,
                                ),
                                icon: const Icon(
                                  Icons.edit_outlined,
                                  size: 18,
                                ),
                              ),
                              IconButton(
                                tooltip: 'أرشفة',
                                onPressed: () async {
                                  await ctrl.repo.setArchived(
                                    'qat_tax_entries',
                                    t.id,
                                    true,
                                  );
                                  await ctrl.reloadAll();
                                },
                                icon: const Icon(
                                  Icons.archive_outlined,
                                  size: 18,
                                ),
                              ),
                              if (ctrl.canDeleteCurrent)
                                IconButton(
                                  tooltip: 'حذف',
                                  onPressed: () async {
                                    final ok = await showErpConfirmDialog(
                                      context,
                                      title: 'حذف قيد الضريبة',
                                      message:
                                          'هل تريد حذف قيد الضريبة نهائياً؟',
                                      confirmLabel: 'حذف',
                                      destructive: true,
                                    );
                                    if (!ok) return;
                                    await ctrl.repo.deleteTaxEntry(t.id);
                                    await ctrl.reloadAll();
                                  },
                                  icon: Icon(
                                    Icons.delete_outline,
                                    color: pal.danger,
                                    size: 18,
                                  ),
                                ),
                            ],
                          ),
                        ),
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }
}
