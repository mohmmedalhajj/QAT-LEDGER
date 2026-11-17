import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../core/money.dart';
import '../../core/theme.dart';
import '../../data/models.dart';
import '../../state/app_controller.dart';
import '../widgets/common_widgets.dart';
import '../widgets/erp_components.dart';

class ExpensesAndAccountsView extends StatefulWidget {
  const ExpensesAndAccountsView({super.key});

  @override
  State<ExpensesAndAccountsView> createState() =>
      _ExpensesAndAccountsViewState();
}

class _ExpensesAndAccountsViewState extends State<ExpensesAndAccountsView> {
  String _search = '';
  CurrencyCode? _currencyFilter;

  Future<void> _addExpenseDialog(
    BuildContext context,
    AppController ctrl,
  ) async {
    final descCtrl = TextEditingController();
    final amountCtrl = TextEditingController();
    final notesCtrl = TextEditingController();
    CurrencyCode cur = ctrl.currencyPolicy.defaultCurrency;
    final activeAccounts = ctrl.accounts.where((a) => a.isActive).toList();
    LedgerAccount? selectedAcc =
        activeAccounts.isEmpty ? null : activeAccounts.first;

    await showDialog<void>(
      context: context,
      builder: (ctx) => ErpDialogControllerScope(
        controllers: [descCtrl, amountCtrl, notesCtrl],
        child: StatefulBuilder(
          builder: (ctx, setModalState) => AlertDialog(
            title: const Text('تسجيل مصروف يومي'),
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
                if (activeAccounts.isNotEmpty)
                  DropdownButtonFormField<int>(
                    key: ValueKey<String>(
                      'exp_acc_${selectedAcc?.id ?? 'none'}',
                    ),
                    initialValue: selectedAcc?.id,
                    isExpanded: true,
                    decoration:
                        const InputDecoration(labelText: 'حساب / بند المصروف'),
                    items: activeAccounts
                        .map(
                          (a) => DropdownMenuItem<int>(
                            value: a.id,
                            child: Text(a.name),
                          ),
                        )
                        .toList(),
                    onChanged: (v) {
                      if (v == null) return;
                      setModalState(() {
                        selectedAcc =
                            activeAccounts.firstWhere((e) => e.id == v);
                      });
                    },
                  ),
                const SizedBox(height: 8),
                TextField(
                  controller: descCtrl,
                  decoration:
                      const InputDecoration(labelText: 'البيان / الوصف *'),
                ),
                const SizedBox(height: 8),
                AccountingNumberField(
                  controller: amountCtrl,
                  label: 'المبلغ (${cur.shortSymbol}) *',
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
                final minor = Money.parseMinorUnits(amountCtrl.text);
                if (descCtrl.text.trim().isEmpty || minor <= 0) {
                  ScaffoldMessenger.of(ctx).showSnackBar(
                    const SnackBar(
                      content: Text('أدخل البيان والمبلغ أكبر من الصفر'),
                    ),
                  );
                  return;
                }
                await ctrl.repo.addExpense(
                  description: descCtrl.text,
                  category: selectedAcc?.name ?? 'مصروفات عامة',
                  accountId: selectedAcc?.id,
                  amountMinor: minor,
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
      ),
    );
  }

  Future<void> _addAccountDialog(
    BuildContext context,
    AppController ctrl,
  ) async {
    final nameCtrl = TextEditingController();
    final catCtrl = TextEditingController(text: 'مصروفات تشغيلية');
    await showDialog<void>(
      context: context,
      builder: (ctx) => ErpDialogControllerScope(
        controllers: [nameCtrl, catCtrl],
        child: AlertDialog(
          title: const Text('إضافة حساب / تصنيف'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: nameCtrl,
              decoration: const InputDecoration(labelText: 'اسم الحساب *'),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: catCtrl,
              decoration: const InputDecoration(labelText: 'التصنيف'),
            ),
          ],
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
              await ctrl.repo.upsertLedgerAccount(
                code: '',
                name: clean,
                category: catCtrl.text,
              );
              await ctrl.reloadAll();
              if (ctx.mounted) Navigator.pop(ctx);
            },
            child: const Text('إضافة'),
          ),
        ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final pal = context.pal;

    if (!ctrl.sectionPermissions.expenses &&
        ctrl.currentUser?.role.code != 'ADMIN') {
      return const Padding(
        padding: EdgeInsets.all(16),
        child: ErpStatusBanner(
          tone: ErpBannerTone.warning,
          title: 'قسم المصاريف مقيد',
          subtitle: 'تم إخفاء سجل المصاريف عن هذا المستخدم في الصلاحيات.',
        ),
      );
    }

    final filtered = ctrl.expenses.where((e) {
      if (_currencyFilter != null && e.currency != _currencyFilter) {
        return false;
      }
      if (_search.isEmpty) return true;
      final q = _search.toLowerCase();
      return e.description.toLowerCase().contains(q) ||
          e.category.toLowerCase().contains(q) ||
          e.notes.toLowerCase().contains(q);
    }).toList();

    final summaryMetrics = <ErpSummaryMetric>[
      ErpSummaryMetric(label: 'المصاريف', value: '${filtered.length}'),
    ];
    for (final cur in ctrl.currencyPolicy.allowedCurrencies) {
      final byCur = filtered.where((e) => e.currency == cur);
      if (byCur.isNotEmpty) {
        final tot = byCur.fold<int>(0, (s, e) => s + e.amountMinor);
        summaryMetrics.add(
          ErpSummaryMetric(
            label: 'إجمالي ${cur.code}',
            value: Money(tot, cur).format(),
            toneColor: pal.danger,
          ),
        );
      }
    }

    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _addExpenseDialog(context, ctrl),
        icon: const Icon(Icons.add),
        label: const Text('مصروف جديد'),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 84),
        children: [
          ErpSectionHeader(
            title: 'دليل الحسابات والبنود (${ctrl.accounts.length})',
            icon: Icons.account_tree_outlined,
            trailing: TextButton.icon(
              onPressed: () => _addAccountDialog(context, ctrl),
              icon: const Icon(Icons.add, size: 16),
              label: const Text('بند جديد'),
            ),
          ),
          const SizedBox(height: 6),
          Wrap(
            spacing: 6,
            runSpacing: 6,
            children: ctrl.accounts
                .map(
                  (a) => Chip(
                    label: Text(
                      a.name,
                      style: const TextStyle(fontSize: 11.5),
                    ),
                  ),
                )
                .toList(),
          ),
          const Divider(height: 24),
          ErpSearchFilterBar(
            searchHint: 'بحث في المصروفات أو التصنيف...',
            onSearchChanged: (v) => setState(() => _search = v.trim()),
            filters: [
              if (ctrl.currencyPolicy.canSwitchCurrency) ...[
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
            ],
          ),
          const SizedBox(height: 10),
          if (filtered.isNotEmpty) ...[
            ErpSummaryBar(metrics: summaryMetrics),
            const SizedBox(height: 12),
          ],
          const ErpSectionHeader(
            title: 'سجل المصروفات التشغيلية',
            icon: Icons.receipt_long_outlined,
          ),
          const SizedBox(height: 8),
          if (filtered.isEmpty)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 28),
              child: EmptyLedgerState(
                icon: Icons.receipt_long_outlined,
                title: 'لا توجد مصروفات مسجلة',
                subtitle: 'سجل مصروفات النقل والإيجار والأكياس مفصولة حسب العملة',
              ),
            )
          else
            for (final e in filtered)
              Card(
                margin: const EdgeInsets.only(bottom: 8),
                child: ListTile(
                  title: Row(
                    children: [
                      Expanded(
                        child: Text(
                          e.description,
                          style: const TextStyle(fontWeight: FontWeight.w700),
                        ),
                      ),
                      CurrencyBadge(currency: e.currency),
                    ],
                  ),
                  subtitle: Text(
                    '${Money(e.amountMinor, e.currency).format()} • ${e.category} • ${e.dateIso}',
                    style: const TextStyle(
                      fontFeatures: [FontFeature.tabularFigures()],
                    ),
                  ),
                  trailing: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      IconButton(
                        tooltip: 'أرشفة',
                        onPressed: () async {
                          await ctrl.repo.setArchived('expenses', e.id, true);
                          await ctrl.reloadAll();
                        },
                        icon: const Icon(Icons.archive_outlined, size: 18),
                      ),
                      if (ctrl.canDeleteCurrent)
                        IconButton(
                          tooltip: 'حذف',
                          onPressed: () async {
                            final ok = await showErpConfirmDialog(
                              context,
                              title: 'حذف المصروف',
                              message: 'هل تريد حذف "${e.description}" نهائياً؟',
                              confirmLabel: 'حذف',
                              destructive: true,
                            );
                            if (!ok) return;
                            await ctrl.repo.deleteExpense(e.id);
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
              ),
        ],
      ),
    );
  }
}
