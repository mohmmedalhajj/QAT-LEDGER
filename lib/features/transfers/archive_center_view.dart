import 'dart:async';

import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../core/money.dart';
import '../../core/theme.dart';
import '../../licensing/license_models.dart';
import '../../services/whatsapp_share_service.dart';
import '../../state/app_controller.dart';
import '../widgets/common_widgets.dart';
import '../widgets/erp_components.dart';

typedef _ArchivedRecord = ({
  String table,
  String categoryLabel,
  int id,
  String title,
  String subtitle,
});

class ArchiveCenterView extends StatefulWidget {
  const ArchiveCenterView({super.key});

  @override
  State<ArchiveCenterView> createState() => _ArchiveCenterViewState();
}

class _ArchiveCenterViewState extends State<ArchiveCenterView> {
  String _search = '';
  String? _tableFilter;
  bool _wasArchiveActive = false;
  bool _loading = false;
  bool _hasLoaded = false;
  String? _error;
  List<_ArchivedRecord> _archivedItems = const [];

  Future<void> _refreshArchived(AppController ctrl) async {
    if (!mounted) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final items = await _loadArchived(ctrl);
      if (!mounted) return;
      setState(() {
        _archivedItems = items;
        _hasLoaded = true;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = 'تعذر تحميل الأرشيف: $e';
        _hasLoaded = true;
      });
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  Future<List<_ArchivedRecord>> _loadArchived(AppController ctrl) async {
    final items = <_ArchivedRecord>[];
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
    final pal = context.pal;
    final isArchiveActive = ctrl.sectionIndex == 3 && ctrl.transfersSubTab == 4;
    if (!isArchiveActive) {
      _wasArchiveActive = false;
      return const SizedBox.shrink();
    }
    if (!ctrl.hasLicenseFeature(LicenseFeature.archive)) {
      return ProFeatureLockedCard(
        featureTitle: 'مركز الأرشيف الشامل واستعادة القيود',
        featureDescription:
            'يتيح لك الأرشيف الشامل في الباقة الاحترافية (PRO) استعراض واستعادة الفواتير، الدفعات، والحوالات المؤرشفة بضغطة واحدة.',
        onActivatePressed: () => ctrl.setSection(4, subTab: 3),
      );
    }
    if (!ctrl.sectionPermissions.archive &&
        ctrl.currentUser?.role.code != 'ADMIN') {
      return const EmptyLedgerState(
        icon: Icons.lock_outline,
        title: 'الأرشيف مقيد بالصلاحيات',
        subtitle: 'تم إخفاء مركز الأرشيف من إدارة الصلاحيات',
      );
    }
    if (!_wasArchiveActive) {
      _wasArchiveActive = true;
      if (!_loading) {
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted) unawaited(_refreshArchived(ctrl));
        });
      }
    }

    const categories = [
      (null, 'الكل'),
      ('sales', 'مبيعات'),
      ('customer_payments', 'سندات قبض'),
      ('supplier_purchases', 'مشتريات'),
      ('supplier_payments', 'سندات صرف'),
      ('expenses', 'مصاريف'),
      ('money_transfers', 'حوالات'),
      ('inventory_batches', 'مخزون'),
      ('qat_tax_entries', 'ضرائب'),
    ];

    final filteredItems = _archivedItems.where((item) {
      if (_tableFilter != null && item.table != _tableFilter) {
        return false;
      }
      if (_search.isEmpty) return true;
      final q = _search.toLowerCase();
      return item.title.toLowerCase().contains(q) ||
          item.subtitle.toLowerCase().contains(q);
    }).toList();

    return Column(
      children: [
        ErpSearchFilterBar(
          searchHint: 'بحث في السجلات المؤرشفة...',
          onSearchChanged: (v) => setState(() => _search = v.trim()),
          trailingAction: filteredItems.isEmpty
              ? null
              : Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    IconButton.outlined(
                      tooltip: 'مشاركة ملخص الأرشيف واتساب',
                      onPressed: () async {
                        final summary = [
                          'أرشيف ${ctrl.settings.agencyName} (${filteredItems.length} سجل):',
                          for (final it in filteredItems.take(40))
                            '• [${it.categoryLabel}] ${it.title} (${it.subtitle})',
                        ].join('\n');
                        await WhatsappShareService.instance
                            .openWhatsApp(text: summary);
                      },
                      icon: const Icon(Icons.chat_outlined, size: 18),
                    ),
                    const SizedBox(width: 4),
                    IconButton.outlined(
                      tooltip: 'مشاركة SMS',
                      onPressed: () async {
                        final summary = [
                          'أرشيف ${ctrl.settings.agencyName} (${filteredItems.length} سجل):',
                          for (final it in filteredItems.take(20))
                            '• [${it.categoryLabel}] ${it.title}',
                        ].join(' | ');
                        await WhatsappShareService.instance
                            .openSms(text: summary);
                      },
                      icon: const Icon(Icons.sms_outlined, size: 18),
                    ),
                  ],
                ),
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
          child: _error != null
              ? Padding(
                  padding: const EdgeInsets.all(16),
                  child: ErpStatusBanner(
                    tone: ErpBannerTone.danger,
                    title: _error!,
                  ),
                )
              : (_loading && !_hasLoaded)
                  ? const Center(child: CircularProgressIndicator())
                  : filteredItems.isEmpty
                      ? const EmptyLedgerState(
                          icon: Icons.archive_outlined,
                          title: 'لا توجد سجلات مؤرشفة مطابقة',
                          subtitle:
                              'تظهر هنا السجلات المؤرشفة لإمكانية استعادتها في أي وقت',
                        )
                      : ListView.separated(
                          padding: const EdgeInsets.all(16),
                          itemCount: filteredItems.length,
                          separatorBuilder: (_, idx) =>
                              const SizedBox(height: 8),
                          itemBuilder: (_, idx) {
                            final item = filteredItems[idx];
                            return Card(
                              child: ListTile(
                                title: Text(
                                  item.title,
                                  style: const TextStyle(
                                    fontWeight: FontWeight.w700,
                                  ),
                                ),
                                subtitle: Text(
                                  '${item.categoryLabel} • ${item.subtitle}',
                                ),
                                trailing: Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    FilledButton.tonalIcon(
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
                                          await _refreshArchived(ctrl);
                                        }
                                      },
                                      icon: const Icon(
                                        Icons.unarchive_outlined,
                                        size: 16,
                                      ),
                                      label: const Text('استعادة'),
                                    ),
                                    if (ctrl.canDeleteCurrent)
                                      IconButton(
                                        tooltip: 'حذف نهائي من الأرشيف',
                                        onPressed: () async {
                                          final ok = await showErpConfirmDialog(
                                            context,
                                            title: 'حذف نهائي',
                                            message:
                                                'هل تريد حذف "${item.title}" نهائياً من قاعدة البيانات؟ لا يمكن التراجع.',
                                            confirmLabel: 'حذف نهائي',
                                            destructive: true,
                                          );
                                          if (!ok) return;
                                          await ctrl.repo.deletePermanently(
                                            item.table,
                                            item.id,
                                          );
                                          await ctrl.reloadAll();
                                          if (mounted) {
                                            await _refreshArchived(ctrl);
                                          }
                                        },
                                        icon: Icon(
                                          Icons.delete_forever_outlined,
                                          color: pal.danger,
                                          size: 20,
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
    );
  }
}
