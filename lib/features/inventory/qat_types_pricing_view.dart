import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../core/money.dart';
import '../../core/theme.dart';
import '../../data/models.dart';
import '../../state/app_controller.dart';
import '../widgets/common_widgets.dart';
import '../widgets/erp_components.dart';

class QatTypesPricingView extends StatefulWidget {
  const QatTypesPricingView({super.key});

  @override
  State<QatTypesPricingView> createState() => _QatTypesPricingViewState();
}

class _QatTypesPricingViewState extends State<QatTypesPricingView> {
  String _search = '';

  Future<void> _openQatTypeDialog(
    BuildContext context,
    AppController ctrl, {
    QatType? existing,
  }) async {
    final nameCtrl = TextEditingController(text: existing?.name ?? '');
    final buyCtrl = TextEditingController(
      text: existing != null
          ? Money.formatMinor(
              existing.defaultBuyPriceMinor,
              showZeroDecimals: false,
            ).replaceAll(',', '')
          : '0',
    );
    final sellCtrl = TextEditingController(
      text: existing != null
          ? Money.formatMinor(
              existing.defaultSellPriceMinor,
              showZeroDecimals: false,
            ).replaceAll(',', '')
          : '0',
    );
    final notesCtrl = TextEditingController(text: existing?.notes ?? '');
    CurrencyCode cur =
        existing?.defaultCurrency ?? ctrl.currencyPolicy.defaultCurrency;

    await showDialog<void>(
      context: context,
      builder: (ctx) => ErpDialogControllerScope(
        controllers: [nameCtrl, buyCtrl, sellCtrl, notesCtrl],
        child: StatefulBuilder(
          builder: (ctx, setModalState) => AlertDialog(
            title: Text(existing == null ? 'صنف قات جديد' : 'تعديل الصنف والتسعير'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: nameCtrl,
                  decoration: const InputDecoration(labelText: 'اسم الصنف *'),
                ),
                const SizedBox(height: 8),
                CurrencySelectorRow(
                  policy: ctrl.currencyPolicy,
                  selected: cur,
                  onChanged: (c) => setModalState(() => cur = c),
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Expanded(
                      child: AccountingNumberField(
                        controller: buyCtrl,
                        label: 'سعر الشراء',
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: AccountingNumberField(
                        controller: sellCtrl,
                        label: 'سعر البيع',
                      ),
                    ),
                  ],
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
                final clean = nameCtrl.text.trim();
                if (clean.isEmpty) return;
                final dup = ctrl.qatTypes.any(
                  (q) =>
                      q.id != existing?.id &&
                      q.name.trim().toLowerCase() == clean.toLowerCase(),
                );
                if (dup) {
                  ScaffoldMessenger.of(ctx).showSnackBar(
                    const SnackBar(
                      content: Text('هذا الصنف معرف مسبقاً'),
                    ),
                  );
                  return;
                }
                await ctrl.repo.upsertQatType(
                  id: existing?.id,
                  name: clean,
                  defaultBuyPriceMinor: Money.parseMinorUnits(buyCtrl.text),
                  defaultSellPriceMinor: Money.parseMinorUnits(sellCtrl.text),
                  defaultCurrency: cur,
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
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final pal = context.pal;
    final list = ctrl.qatTypes.where((qt) {
      if (_search.isEmpty) return true;
      return qt.name.toLowerCase().contains(_search.toLowerCase());
    }).toList();

    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _openQatTypeDialog(context, ctrl),
        icon: const Icon(Icons.add),
        label: const Text('إضافة صنف'),
      ),
      body: Column(
        children: [
          ErpSearchFilterBar(
            searchHint: 'بحث في دليل الأصناف والأسعار...',
            onSearchChanged: (v) => setState(() => _search = v.trim()),
          ),
          Expanded(
            child: list.isEmpty
                ? const EmptyLedgerState(
                    icon: Icons.eco_outlined,
                    title: 'لا توجد أصناف معرفة',
                    subtitle:
                        'عرّف أنواع القات وأسعارها الافتراضية لتسريع البيع والشراء',
                  )
                : ListView.separated(
                    padding: const EdgeInsets.fromLTRB(16, 12, 16, 84),
                    itemCount: list.length,
                    separatorBuilder: (_, idx) => const SizedBox(height: 10),
                    itemBuilder: (_, idx) {
                      final qt = list[idx];
                      final margin = qt.defaultSellPriceMinor -
                          qt.defaultBuyPriceMinor;
                      final pct = qt.defaultBuyPriceMinor > 0
                          ? ((margin * 100) / qt.defaultBuyPriceMinor).round()
                          : 0;
                      return Card(
                        child: ListTile(
                          title: Row(
                            children: [
                              Expanded(
                                child: Text(
                                  qt.name,
                                  style: const TextStyle(
                                    fontWeight: FontWeight.w700,
                                  ),
                                ),
                              ),
                              CurrencyBadge(currency: qt.defaultCurrency),
                              const SizedBox(width: 6),
                              StatusPill(
                                label: margin >= 0
                                    ? 'هامش +$pct%'
                                    : 'خسارة $pct%',
                                bg: margin >= 0
                                    ? pal.successSoft
                                    : pal.dangerSoft,
                                fg: margin >= 0 ? pal.success : pal.danger,
                              ),
                            ],
                          ),
                          subtitle: Padding(
                            padding: const EdgeInsets.only(top: 4),
                            child: Text(
                              'الشراء: ${Money(qt.defaultBuyPriceMinor, qt.defaultCurrency).format()} • البيع: ${Money(qt.defaultSellPriceMinor, qt.defaultCurrency).format()}',
                              style: const TextStyle(
                                fontSize: 12.5,
                                fontFeatures: [FontFeature.tabularFigures()],
                              ),
                            ),
                          ),
                          trailing: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              IconButton(
                                onPressed: () => _openQatTypeDialog(
                                  context,
                                  ctrl,
                                  existing: qt,
                                ),
                                icon: const Icon(Icons.edit_outlined, size: 18),
                              ),
                              if (ctrl.canDeleteCurrent)
                                IconButton(
                                  onPressed: () async {
                                    final ok = await showErpConfirmDialog(
                                      context,
                                      title: 'حذف الصنف',
                                      message:
                                          'هل تريد حذف صنف "${qt.name}" من الدليل؟',
                                      confirmLabel: 'حذف',
                                      destructive: true,
                                    );
                                    if (!ok) return;
                                    await ctrl.repo.deleteQatType(qt.id);
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
