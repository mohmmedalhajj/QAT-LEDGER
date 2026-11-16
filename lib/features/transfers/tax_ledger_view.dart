import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../core/money.dart';
import '../../core/theme.dart';
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

  Future<void> _addTaxDialog(BuildContext context, AppController ctrl) async {
    final qtyCtrl = TextEditingController(text: '0');
    final rateCtrl = TextEditingController(text: '0');
    final amountCtrl = TextEditingController();
    final notesCtrl = TextEditingController();
    CurrencyCode cur = ctrl.currencyPolicy.defaultCurrency;

    await showDialog<void>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setModalState) => AlertDialog(
          title: const Text('قيد ضريبة قات'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                CurrencySelectorRow(
                  policy: ctrl.currencyPolicy,
                  selected: cur,
                  onChanged: (c) => setModalState(() => cur = c),
                ),
                const SizedBox(height: 8),
                AccountingNumberField(
                  controller: qtyCtrl,
                  label: 'الكمية الإجمالية',
                ),
                const SizedBox(height: 8),
                AccountingNumberField(
                  controller: rateCtrl,
                  label: 'فئة الضريبة',
                ),
                const SizedBox(height: 8),
                AccountingNumberField(
                  controller: amountCtrl,
                  label: 'إجمالي مبلغ الضريبة *',
                ),
                const SizedBox(height: 8),
                TextField(
                  controller: notesCtrl,
                  decoration: const InputDecoration(labelText: 'ملاحظات'),
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
                if (taxMinor <= 0) return;
                await ctrl.repo.addTaxEntry(
                  totalQtyMilli: QuantityMath.parseMilli(qtyCtrl.text),
                  taxRateMinor: Money.parseMinorUnits(rateCtrl.text),
                  taxAmountMinor: taxMinor,
                  currency: cur,
                  notes: notesCtrl.text,
                );
                await ctrl.reloadAll();
                if (ctx.mounted) Navigator.pop(ctx);
              },
              child: const Text('حفظ'),
            ),
          ],
        ),
      ),
    );
    qtyCtrl.dispose();
    rateCtrl.dispose();
    amountCtrl.dispose();
    notesCtrl.dispose();
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
        onPressed: () => _addTaxDialog(context, ctrl),
        icon: const Icon(Icons.add),
        label: const Text('قيد ضريبة'),
      ),
      body: Column(
        children: [
          if (ctrl.currencyPolicy.canSwitchCurrency)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 10, 16, 0),
              child: Row(
                children: [
                  ChoiceChip(
                    label: const Text('الكل'),
                    selected: _currencyFilter == null,
                    onSelected: (_) => setState(() => _currencyFilter = null),
                  ),
                  const SizedBox(width: 6),
                  for (final cur in ctrl.currencyPolicy.allowedCurrencies) ...[
                    ChoiceChip(
                      label: Text(cur.code),
                      selected: _currencyFilter == cur,
                      onSelected: (_) => setState(() => _currencyFilter = cur),
                    ),
                    const SizedBox(width: 6),
                  ],
                ],
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
                          subtitle:
                              Text('${t.dateIso} • ${t.source} ${t.notes}'),
                          trailing: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
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
