import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../core/money.dart';
import '../../state/app_controller.dart';
import '../widgets/common_widgets.dart';
import '../widgets/erp_components.dart';

class ArchiveCenterView extends StatefulWidget {
  const ArchiveCenterView({super.key});

  @override
  State<ArchiveCenterView> createState() => _ArchiveCenterViewState();
}

class _ArchiveCenterViewState extends State<ArchiveCenterView> {
  String _search = '';
  String? _tableFilter;
  bool _wasArchiveActive = false;
  Future<
      List<
          ({
            String table,
            String categoryLabel,
            int id,
            String title,
            String subtitle,
          })>>? _archivedFuture;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _archivedFuture ??= _loadArchived(context.read<AppController>());
  }

  Future<
      List<
          ({
            String table,
            String categoryLabel,
            int id,
            String title,
            String subtitle,
          })>> _loadArchived(AppController ctrl) async {
    final items = <({
      String table,
      String categoryLabel,
      int id,
      String title,
      String subtitle,
    })>[];
    final sales = await ctrl.repo.getSales(includeArchived: true);
    for (final s in sales.where((e) => e.isArchived)) {
      items.add((
        table: 'sales',
        categoryLabel: 'مبيعات',
        id: s.id,
        title: 'فاتورة ${s.invoiceNumber} — ${s.customerName}',
        subtitle:
            '${s.dateIso} • ${Money(s.totalAmountMinor, s.currency).format()}',
      ));
    }
    final custPays =
        await ctrl.repo.getCustomerPayments(includeArchived: true);
    for (final p in custPays.where((e) => e.isArchived)) {
      items.add((
        table: 'customer_payments',
        categoryLabel: 'سندات قبض',
        id: p.id,
        title: 'سند قبض عميل — ${p.customerName}',
        subtitle:
            '${p.dateIso} • ${Money(p.amountMinor, p.currency).format()}',
      ));
    }
    final purchases =
        await ctrl.repo.getSupplierPurchases(includeArchived: true);
    for (final p in purchases.where((e) => e.isArchived)) {
      items.add((
        table: 'supplier_purchases',
        categoryLabel: 'مشتريات',
        id: p.id,
        title: 'فاتورة شراء ${p.purchaseNumber} — ${p.supplierName}',
        subtitle:
            '${p.dateIso} • ${Money(p.totalAmountMinor, p.currency).format()}',
      ));
    }
    final supPays =
        await ctrl.repo.getSupplierPayments(includeArchived: true);
    for (final p in supPays.where((e) => e.isArchived)) {
      items.add((
        table: 'supplier_payments',
        categoryLabel: 'سندات صرف',
        id: p.id,
        title: 'سند صرف مورد — ${p.supplierName}',
        subtitle:
            '${p.dateIso} • ${Money(p.amountMinor, p.currency).format()}',
      ));
    }
    final expenses = await ctrl.repo.getExpenses(includeArchived: true);
    for (final e in expenses.where((e) => e.isArchived)) {
      items.add((
        table: 'expenses',
        categoryLabel: 'مصاريف',
        id: e.id,
        title: 'مصروف: ${e.description}',
        subtitle:
            '${e.dateIso} • ${Money(e.amountMinor, e.currency).format()}',
      ));
    }
    final taxes = await ctrl.repo.getTaxEntries(includeArchived: true);
    for (final t in taxes.where((e) => e.isArchived)) {
      items.add((
        table: 'qat_tax_entries',
        categoryLabel: 'ضرائب',
        id: t.id,
        title: 'قيد ضريبة (${t.source})',
        subtitle:
            '${t.dateIso} • ${Money(t.taxAmountMinor, t.currency).format()}',
      ));
    }
    final transfers = await ctrl.repo.getTransfers(includeArchived: true);
    for (final t in transfers.where((e) => e.isArchived)) {
      items.add((
        table: 'money_transfers',
        categoryLabel: 'حوالات',
        id: t.id,
        title: 'حوالة: ${t.receiverName.isEmpty ? t.senderName : t.receiverName}',
        subtitle:
            '${t.dateIso} • ${Money(t.amountMinor, t.currency).format()}',
      ));
    }
    final batches =
        await ctrl.repo.getInventoryBatches(includeArchived: true);
    for (final b in batches.where((e) => e.isArchived)) {
      items.add((
        table: 'inventory_batches',
        categoryLabel: 'مخزون',
        id: b.id,
        title: 'دفعة مخزون: ${b.qatTypeName}',
        subtitle: b.purchaseDateIso,
      ));
    }
    return items;
  }

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final isArchiveActive = ctrl.sectionIndex == 3 && ctrl.transfersSubTab == 4;
    if (isArchiveActive && !_wasArchiveActive) {
      _archivedFuture = _loadArchived(ctrl);
    }
    _wasArchiveActive = isArchiveActive;
    if (!ctrl.sectionPermissions.archive &&
        ctrl.currentUser?.role.code != 'ADMIN') {
      return const EmptyLedgerState(
        icon: Icons.lock_outline,
        title: 'الأرشيف مقيد بالصلاحيات',
        subtitle: 'تم إخفاء مركز الأرشيف من إدارة الصلاحيات',
      );
    }

    const categories = [
      (null, 'الكل'),
      ('sales', 'مبيعات'),
      ('customer_payments', 'سندات قبض'),
      ('supplier_purchases', 'مشتريات'),
      ('expenses', 'مصاريف'),
      ('money_transfers', 'حوالات'),
      ('inventory_batches', 'مخزون'),
      ('qat_tax_entries', 'ضرائب'),
    ];

    return Column(
      children: [
        ErpSearchFilterBar(
          searchHint: 'بحث في السجلات المؤرشفة...',
          onSearchChanged: (v) => setState(() => _search = v.trim()),
          filters: [
            for (final cat in categories)
              ChoiceChip(
                label: Text(cat.$2),
                selected: _tableFilter == cat.$1,
                onSelected: (_) => setState(() => _tableFilter = cat.$1),
              ),
          ],
        ),
        Expanded(
          child: FutureBuilder<
              List<
                  ({
                    String table,
                    String categoryLabel,
                    int id,
                    String title,
                    String subtitle,
                  })>>(
            future: _archivedFuture,
            builder: (context, snap) {
              if (!snap.hasData) {
                return const Center(child: CircularProgressIndicator());
              }
              final all = snap.data!;
              final items = all.where((item) {
                if (_tableFilter != null && item.table != _tableFilter) {
                  return false;
                }
                if (_search.isEmpty) return true;
                final q = _search.toLowerCase();
                return item.title.toLowerCase().contains(q) ||
                    item.subtitle.toLowerCase().contains(q);
              }).toList();

              if (items.isEmpty) {
                return const EmptyLedgerState(
                  icon: Icons.archive_outlined,
                  title: 'لا توجد سجلات مؤرشفة مطابقة',
                  subtitle:
                      'تظهر هنا السجلات المؤرشفة لإمكانية استعادتها في أي وقت',
                );
              }
              return ListView.separated(
                padding: const EdgeInsets.all(16),
                itemCount: items.length,
                separatorBuilder: (_, idx) => const SizedBox(height: 8),
                itemBuilder: (_, idx) {
                  final item = items[idx];
                  return Card(
                    child: ListTile(
                      title: Text(
                        item.title,
                        style: const TextStyle(fontWeight: FontWeight.w700),
                      ),
                      subtitle: Text(
                        '${item.categoryLabel} • ${item.subtitle}',
                      ),
                      trailing: FilledButton.tonalIcon(
                        onPressed: () async {
                          final ok = await showErpConfirmDialog(
                            context,
                            title: 'استعادة من الأرشيف',
                            message:
                                'هل تريد استعادة "${item.title}" وإرجاعها للدفاتر النشطة؟',
                            confirmLabel: 'استعادة',
                          );
                          if (!ok) return;
                          await ctrl.repo.setArchived(
                            item.table,
                            item.id,
                            false,
                          );
                          await ctrl.reloadAll();
                          if (mounted) {
                            setState(() {
                              _archivedFuture = _loadArchived(ctrl);
                            });
                          }
                        },
                        icon: const Icon(Icons.unarchive_outlined, size: 16),
                        label: const Text('استعادة'),
                      ),
                    ),
                  );
                },
              );
            },
          ),
        ),
      ],
    );
  }
}
