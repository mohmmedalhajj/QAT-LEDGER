import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/money.dart';
import '../core/platform_bridge.dart';
import '../core/theme.dart';
import '../data/models.dart';
import '../data/qat_repository.dart';
import '../state/app_controller.dart';
import 'widgets/common_widgets.dart';

class InventorySuppliersTab extends StatelessWidget {
  const InventorySuppliersTab({super.key});

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final subTab = ctrl.inventorySubTab.clamp(0, 3);

    return Column(
      children: [
        SegmentedStrip(
          tabs: const [
            'المخزون والدفعات',
            'أصناف القات',
            'الموردون',
            'فواتير المشتريات',
          ],
          selectedIndex: subTab,
          onChanged: ctrl.setInventorySubTab,
        ),
        Expanded(
          child: IndexedStack(
            index: subTab,
            children: const [
              _InventoryBatchesView(),
              _QatTypesView(),
              _SuppliersDirectoryView(),
              _SupplierPurchasesView(),
            ],
          ),
        ),
      ],
    );
  }
}

// -----------------------------------------------------------------------------
// 1. Inventory Batches
// -----------------------------------------------------------------------------

class _InventoryBatchesView extends StatefulWidget {
  const _InventoryBatchesView();

  @override
  State<_InventoryBatchesView> createState() => _InventoryBatchesViewState();
}

class _InventoryBatchesViewState extends State<_InventoryBatchesView> {
  Future<void> _openBatchDialog(AppController ctrl, {InventoryBatch? existing}) async {
    final nameCtrl = TextEditingController(text: existing?.qatTypeName ?? '');
    final qtyCtrl = TextEditingController(
      text: existing != null ? QuantityMath.formatMilli(existing.remainingQtyMilli) : '1',
    );
    final unitCtrl = TextEditingController(text: existing?.unit ?? 'ربطة');
    final buyCtrl = TextEditingController(
      text: existing != null
          ? Money.formatMinor(existing.buyPriceMinor).replaceAll(',', '')
          : '',
    );
    final sellCtrl = TextEditingController(
      text: existing != null
          ? Money.formatMinor(existing.sellPriceMinor).replaceAll(',', '')
          : '',
    );
    CurrencyCode currency = existing?.currency ?? ctrl.currencyPolicy.defaultCurrency;

    await showDialog<void>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setModalState) => AlertDialog(
          title: Text(existing == null ? 'إضافة دفعة مخزون' : 'تعديل دفعة مخزون'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                CurrencySelectorRow(
                  policy: ctrl.currencyPolicy,
                  selected: currency,
                  onChanged: (c) => setModalState(() => currency = c),
                ),
                const SizedBox(height: 10),
                if (existing == null && ctrl.qatTypes.isNotEmpty)
                  DropdownButtonFormField<String>(
                    decoration: const InputDecoration(labelText: 'اختيار من الأصناف المعرفة'),
                    items: ctrl.qatTypes
                        .map((qt) => DropdownMenuItem(value: qt.name, child: Text(qt.name)))
                        .toList(),
                    onChanged: (v) {
                      if (v == null) return;
                      nameCtrl.text = v;
                      final qt = ctrl.qatTypes.firstWhere((e) => e.name == v);
                      buyCtrl.text = Money.formatMinor(qt.defaultBuyPriceMinor).replaceAll(',', '');
                      sellCtrl.text =
                          Money.formatMinor(qt.defaultSellPriceMinor).replaceAll(',', '');
                    },
                  ),
                const SizedBox(height: 8),
                TextField(controller: nameCtrl, decoration: const InputDecoration(labelText: 'اسم الصنف *')),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Expanded(child: AccountingNumberField(controller: qtyCtrl, label: 'الكمية')),
                    const SizedBox(width: 8),
                    Expanded(
                      child: TextField(controller: unitCtrl, decoration: const InputDecoration(labelText: 'الوحدة')),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Expanded(child: AccountingNumberField(controller: buyCtrl, label: 'سعر الشراء')),
                    const SizedBox(width: 8),
                    Expanded(child: AccountingNumberField(controller: sellCtrl, label: 'سعر البيع')),
                  ],
                ),
              ],
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('إلغاء')),
            FilledButton(
              onPressed: () async {
                if (nameCtrl.text.trim().isEmpty) return;
                final qtyMilli = QuantityMath.parseMilli(qtyCtrl.text);
                final buyMinor = Money.parseMinorUnits(buyCtrl.text);
                final sellMinor = Money.parseMinorUnits(sellCtrl.text);
                if (existing == null) {
                  await ctrl.repo.addInventoryBatch(
                    qatTypeName: nameCtrl.text,
                    qtyMilli: qtyMilli,
                    unit: unitCtrl.text,
                    buyPriceMinor: buyMinor,
                    sellPriceMinor: sellMinor,
                    currency: currency,
                  );
                } else {
                  await ctrl.repo.updateInventoryBatch(
                    id: existing.id,
                    qatTypeName: nameCtrl.text,
                    remainingQtyMilli: qtyMilli,
                    unit: unitCtrl.text,
                    buyPriceMinor: buyMinor,
                    sellPriceMinor: sellMinor,
                    currency: currency,
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
    );
  }

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final pal = context.pal;

    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _openBatchDialog(ctrl),
        icon: const Icon(Icons.add_box_outlined),
        label: const Text('إضافة دفعة'),
      ),
      body: ctrl.batches.isEmpty
          ? const EmptyLedgerState(
              icon: Icons.inventory_2_outlined,
              title: 'المخزون فارغ',
              subtitle: 'أضف دفعة مخزون يدوياً أو عبر فاتورة شراء مورد',
            )
          : ListView(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 84),
              children: [
                if (ctrl.inventoryValuation.isNotEmpty)
                  Card(
                    color: pal.brandSoft,
                    margin: const EdgeInsets.only(bottom: 12),
                    child: Padding(
                      padding: const EdgeInsets.all(14),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('ملخص الجرد والتقييم المالي للمخزون',
                              style: Theme.of(context).textTheme.titleMedium),
                          const SizedBox(height: 8),
                          for (final v in ctrl.inventoryValuation.values)
                            Padding(
                              padding: const EdgeInsets.only(bottom: 4),
                              child: Text(
                                '${v.currency.code}: ${v.batchCount} دفعات • إجمالي الكمية: ${QuantityMath.formatMilli(v.totalQtyMilli)} • التكلفة: ${Money(v.totalCostMinor, v.currency).format()} • القيمة البيعية: ${Money(v.totalRetailMinor, v.currency).format()}',
                                style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600),
                              ),
                            ),
                        ],
                      ),
                    ),
                  ),
                for (final b in ctrl.batches) ...[
                  Builder(builder: (context) {
                    final lowStock = b.remainingQtyMilli <= 2000;
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
                                  child: Text(b.qatTypeName,
                                      style: const TextStyle(fontWeight: FontWeight.w700)),
                                ),
                                CurrencyBadge(currency: b.currency),
                                const SizedBox(width: 6),
                                StatusPill(
                                  label: '${QuantityMath.formatMilli(b.remainingQtyMilli)} ${b.unit}',
                                  bg: lowStock ? pal.warningSoft : pal.successSoft,
                                  fg: lowStock ? pal.warning : pal.success,
                                ),
                              ],
                            ),
                            const SizedBox(height: 6),
                            Text(
                              'شراء: ${Money(b.buyPriceMinor, b.currency).format()} • بيع: ${Money(b.sellPriceMinor, b.currency).format()}'
                              '${b.supplierName.isNotEmpty ? ' • المورد: ${b.supplierName}' : ''}',
                              style: TextStyle(fontSize: 12, color: pal.textSecondary),
                            ),
                            const SizedBox(height: 6),
                            Row(
                              children: [
                                OutlinedButton.icon(
                                  onPressed: () => _openBatchDialog(ctrl, existing: b),
                                  icon: const Icon(Icons.edit_outlined, size: 16),
                                  label: const Text('تعديل'),
                                ),
                                const SizedBox(width: 8),
                                IconButton(
                                  onPressed: () async {
                                    await ctrl.repo.setArchived('inventory_batches', b.id, true);
                                    await ctrl.reloadAll();
                                  },
                                  icon: const Icon(Icons.archive_outlined, size: 18),
                                ),
                                IconButton(
                                  onPressed: () async {
                                    await ctrl.repo.deleteInventoryBatch(b.id);
                                    await ctrl.reloadAll();
                                  },
                                  icon: Icon(Icons.delete_outline, color: pal.danger, size: 18),
                                ),
                              ],
                            ),
                          ],
                        ),
                      ),
                    );
                  }),
                ],
              ],
            ),
    );
  }
}

// -----------------------------------------------------------------------------
// 2. Qat Types Catalog
// -----------------------------------------------------------------------------

class _QatTypesView extends StatelessWidget {
  const _QatTypesView();

  Future<void> _openQatTypeDialog(BuildContext context, AppController ctrl, {QatType? existing}) async {
    final nameCtrl = TextEditingController(text: existing?.name ?? '');
    final buyCtrl = TextEditingController(
      text: existing != null
          ? Money.formatMinor(existing.defaultBuyPriceMinor).replaceAll(',', '')
          : '0',
    );
    final sellCtrl = TextEditingController(
      text: existing != null
          ? Money.formatMinor(existing.defaultSellPriceMinor).replaceAll(',', '')
          : '0',
    );
    final notesCtrl = TextEditingController(text: existing?.notes ?? '');
    CurrencyCode cur = existing?.defaultCurrency ?? ctrl.currencyPolicy.defaultCurrency;

    await showDialog<void>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setModalState) => AlertDialog(
          title: Text(existing == null ? 'صنف قات جديد' : 'تعديل الصنف'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(controller: nameCtrl, decoration: const InputDecoration(labelText: 'اسم الصنف *')),
                const SizedBox(height: 8),
                CurrencySelectorRow(
                  policy: ctrl.currencyPolicy,
                  selected: cur,
                  onChanged: (c) => setModalState(() => cur = c),
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Expanded(child: AccountingNumberField(controller: buyCtrl, label: 'شراء افتراضي')),
                    const SizedBox(width: 8),
                    Expanded(child: AccountingNumberField(controller: sellCtrl, label: 'بيع افتراضي')),
                  ],
                ),
                const SizedBox(height: 8),
                TextField(controller: notesCtrl, decoration: const InputDecoration(labelText: 'ملاحظات')),
              ],
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('إلغاء')),
            FilledButton(
              onPressed: () async {
                if (nameCtrl.text.trim().isEmpty) return;
                await ctrl.repo.upsertQatType(
                  id: existing?.id,
                  name: nameCtrl.text,
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
    );
  }

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final pal = context.pal;

    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _openQatTypeDialog(context, ctrl),
        icon: const Icon(Icons.add),
        label: const Text('إضافة صنف'),
      ),
      body: ctrl.qatTypes.isEmpty
          ? const EmptyLedgerState(
              icon: Icons.eco_outlined,
              title: 'لا توجد أصناف معرفة',
              subtitle: 'عرّف أنواع القات وأسعارها الافتراضية لتسريع البيع والشراء',
            )
          : ListView.separated(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 84),
              itemCount: ctrl.qatTypes.length,
              separatorBuilder: (_, idx) => const SizedBox(height: 10),
              itemBuilder: (_, idx) {
                final qt = ctrl.qatTypes[idx];
                return Card(
                  child: ListTile(
                    title: Text(qt.name, style: const TextStyle(fontWeight: FontWeight.w700)),
                    subtitle: Text(
                      'الشراء: ${Money(qt.defaultBuyPriceMinor, qt.defaultCurrency).format()} • البيع: ${Money(qt.defaultSellPriceMinor, qt.defaultCurrency).format()}',
                    ),
                    trailing: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        IconButton(
                          onPressed: () => _openQatTypeDialog(context, ctrl, existing: qt),
                          icon: const Icon(Icons.edit_outlined, size: 18),
                        ),
                        IconButton(
                          onPressed: () async {
                            await ctrl.repo.deleteQatType(qt.id);
                            await ctrl.reloadAll();
                          },
                          icon: Icon(Icons.delete_outline, color: pal.danger, size: 18),
                        ),
                      ],
                    ),
                  ),
                );
              },
            ),
    );
  }
}

// -----------------------------------------------------------------------------
// 3. Suppliers Directory & Purchase Creation / Payment
// -----------------------------------------------------------------------------

class _SuppliersDirectoryView extends StatefulWidget {
  const _SuppliersDirectoryView();

  @override
  State<_SuppliersDirectoryView> createState() => _SuppliersDirectoryViewState();
}

class _SuppliersDirectoryViewState extends State<_SuppliersDirectoryView> {
  Future<void> _openSupplierDialog(AppController ctrl, {Supplier? existing}) async {
    final nameCtrl = TextEditingController(text: existing?.name ?? '');
    final phoneCtrl = TextEditingController(text: existing?.phone ?? '');
    final addrCtrl = TextEditingController(text: existing?.address ?? '');
    final notesCtrl = TextEditingController(text: existing?.notes ?? '');

    await showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(existing == null ? 'مورد جديد' : 'تعديل بيانات المورد'),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              OutlinedButton.icon(
                onPressed: () async {
                  final picked = await PlatformBridge.instance.pickPhoneContact();
                  if (picked != null) {
                    nameCtrl.text = picked.name;
                    phoneCtrl.text = picked.phone;
                  }
                },
                icon: const Icon(Icons.contacts_outlined, size: 18),
                label: const Text('استيراد من جهات الاتصال'),
              ),
              const SizedBox(height: 8),
              TextField(controller: nameCtrl, decoration: const InputDecoration(labelText: 'الاسم *')),
              const SizedBox(height: 8),
              TextField(
                controller: phoneCtrl,
                textDirection: TextDirection.ltr,
                keyboardType: TextInputType.phone,
                decoration: const InputDecoration(labelText: 'الهاتف'),
              ),
              const SizedBox(height: 8),
              TextField(controller: addrCtrl, decoration: const InputDecoration(labelText: 'المنطقة')),
              const SizedBox(height: 8),
              TextField(controller: notesCtrl, decoration: const InputDecoration(labelText: 'ملاحظات')),
            ],
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('إلغاء')),
          FilledButton(
            onPressed: () async {
              if (nameCtrl.text.trim().isEmpty) return;
              await ctrl.repo.upsertSupplier(
                id: existing?.id,
                name: nameCtrl.text,
                phone: phoneCtrl.text,
                address: addrCtrl.text,
                notes: notesCtrl.text,
              );
              await ctrl.reloadAll();
              if (ctx.mounted) Navigator.pop(ctx);
            },
            child: const Text('حفظ'),
          ),
        ],
      ),
    );
  }

  Future<void> _openSupplierPaymentDialog(AppController ctrl, Supplier supplier) async {
    final amountCtrl = TextEditingController();
    final notesCtrl = TextEditingController();
    CurrencyCode cur = ctrl.currencyPolicy.defaultCurrency;

    await showDialog<void>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setModalState) => AlertDialog(
          title: Text('سند صرف لمورد — ${supplier.name}'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              CurrencySelectorRow(
                policy: ctrl.currencyPolicy,
                selected: cur,
                onChanged: (c) => setModalState(() => cur = c),
              ),
              const SizedBox(height: 10),
              AccountingNumberField(
                controller: amountCtrl,
                label: 'المبلغ المدفوع (${cur.shortSymbol})',
              ),
              const SizedBox(height: 10),
              TextField(controller: notesCtrl, decoration: const InputDecoration(labelText: 'ملاحظات')),
            ],
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('إلغاء')),
            FilledButton(
              onPressed: () async {
                final minor = Money.parseMinorUnits(amountCtrl.text);
                if (minor <= 0) return;
                await ctrl.repo.addSupplierPayment(
                  supplierId: supplier.id,
                  supplierName: supplier.name,
                  amountMinor: minor,
                  currency: cur,
                  notes: notesCtrl.text,
                );
                await ctrl.reloadAll();
                if (ctx.mounted) Navigator.pop(ctx);
              },
              child: const Text('اعتماد السند'),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _openNewPurchaseDialog(AppController ctrl, Supplier supplier) async {
    final qatCtrl = TextEditingController();
    final qtyCtrl = TextEditingController(text: '10');
    final buyCtrl = TextEditingController();
    final sellCtrl = TextEditingController();
    final paidCtrl = TextEditingController(text: '0');
    final notesCtrl = TextEditingController();
    CurrencyCode cur = ctrl.currencyPolicy.defaultCurrency;
    PaymentType pType = PaymentType.credit;

    await showDialog<void>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setModalState) => AlertDialog(
          title: Text('فاتورة شراء من ${supplier.name}'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                CurrencySelectorRow(
                  policy: ctrl.currencyPolicy,
                  selected: cur,
                  onChanged: (c) => setModalState(() => cur = c),
                ),
                const SizedBox(height: 8),
                TextField(controller: qatCtrl, decoration: const InputDecoration(labelText: 'الصنف *')),
                const SizedBox(height: 8),
                AccountingNumberField(controller: qtyCtrl, label: 'الكمية (ربطة)'),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Expanded(child: AccountingNumberField(controller: buyCtrl, label: 'سعر الشراء')),
                    const SizedBox(width: 8),
                    Expanded(child: AccountingNumberField(controller: sellCtrl, label: 'سعر البيع المقترح')),
                  ],
                ),
                const SizedBox(height: 10),
                SegmentedButton<PaymentType>(
                  segments: const [
                    ButtonSegment(value: PaymentType.paid, label: Text('نقداً')),
                    ButtonSegment(value: PaymentType.partial, label: Text('جزئي')),
                    ButtonSegment(value: PaymentType.credit, label: Text('آجل')),
                  ],
                  selected: {pType},
                  onSelectionChanged: (s) => setModalState(() => pType = s.first),
                ),
                if (pType == PaymentType.partial) ...[
                  const SizedBox(height: 8),
                  AccountingNumberField(controller: paidCtrl, label: 'المدفوع مقدماً'),
                ],
                const SizedBox(height: 8),
                TextField(controller: notesCtrl, decoration: const InputDecoration(labelText: 'ملاحظات')),
              ],
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('إلغاء')),
            FilledButton(
              onPressed: () async {
                final qty = QuantityMath.parseMilli(qtyCtrl.text);
                final buy = Money.parseMinorUnits(buyCtrl.text);
                final sell = Money.parseMinorUnits(sellCtrl.text);
                if (qatCtrl.text.trim().isEmpty || qty <= 0) return;
                await ctrl.repo.makeSupplierPurchase(
                  supplierId: supplier.id,
                  supplierName: supplier.name,
                  items: [
                    NewPurchaseItemInput(
                      qatTypeName: qatCtrl.text.trim(),
                      qtyMilli: qty,
                      unit: 'ربطة',
                      buyPriceMinor: buy,
                      sellPriceMinor: sell > 0 ? sell : buy,
                    ),
                  ],
                  currency: cur,
                  paymentType: pType,
                  paidAmountMinor: Money.parseMinorUnits(paidCtrl.text),
                  notes: notesCtrl.text,
                );
                await ctrl.reloadAll();
                if (ctx.mounted) Navigator.pop(ctx);
              },
              child: const Text('حفظ وترحيل للمخزون'),
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

    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _openSupplierDialog(ctrl),
        icon: const Icon(Icons.person_add_outlined),
        label: const Text('مورد جديد'),
      ),
      body: ctrl.suppliers.isEmpty
          ? const EmptyLedgerState(
              icon: Icons.local_shipping_outlined,
              title: 'لا يوجد موردون بعد',
              subtitle: 'أضف الموردين لتسجيل فواتير الشراء والمستحقات بكل عملة',
            )
          : ListView.separated(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 84),
              itemCount: ctrl.suppliers.length,
              separatorBuilder: (_, idx) => const SizedBox(height: 10),
              itemBuilder: (_, idx) {
                final s = ctrl.suppliers[idx];
                final yerBal = s.balanceFor(CurrencyCode.yer);
                final sarBal = s.balanceFor(CurrencyCode.sar);
                return Card(
                  child: Padding(
                    padding: const EdgeInsets.all(14),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Expanded(
                              child: Text(s.name, style: const TextStyle(fontWeight: FontWeight.w700)),
                            ),
                            IconButton(
                              onPressed: () => _openSupplierDialog(ctrl, existing: s),
                              icon: const Icon(Icons.edit_outlined, size: 18),
                            ),
                            IconButton(
                              tooltip: 'حذف المورد',
                              onPressed: () async {
                                try {
                                  await ctrl.repo.deleteSupplier(s.id);
                                  await ctrl.reloadAll();
                                } catch (e) {
                                  if (!context.mounted) return;
                                  ScaffoldMessenger.of(context).showSnackBar(
                                    SnackBar(content: Text(e.toString())),
                                  );
                                }
                              },
                              icon: Icon(Icons.delete_outline, color: pal.danger, size: 18),
                            ),
                          ],
                        ),
                        if (s.phone.isNotEmpty)
                          Text(s.phone, style: TextStyle(fontSize: 12, color: pal.textSecondary)),
                        const SizedBox(height: 8),
                        Wrap(
                          spacing: 8,
                          children: [
                            StatusPill(
                              label: 'YER: ${Money(yerBal, CurrencyCode.yer).format()}',
                              bg: yerBal > 0 ? pal.warningSoft : pal.successSoft,
                              fg: yerBal > 0 ? pal.warning : pal.success,
                            ),
                            if (sarBal != 0 || ctrl.currencyPolicy.canSwitchCurrency)
                              StatusPill(
                                label: 'SAR: ${Money(sarBal, CurrencyCode.sar).format()}',
                                bg: sarBal > 0 ? pal.warningSoft : pal.successSoft,
                                fg: sarBal > 0 ? pal.warning : pal.success,
                              ),
                          ],
                        ),
                        const SizedBox(height: 10),
                        Wrap(
                          spacing: 8,
                          runSpacing: 8,
                          children: [
                            FilledButton.tonalIcon(
                              onPressed: () => _openNewPurchaseDialog(ctrl, s),
                              icon: const Icon(Icons.add_shopping_cart, size: 16),
                              label: const Text('فاتورة شراء'),
                            ),
                            OutlinedButton.icon(
                              onPressed: () => _openSupplierPaymentDialog(ctrl, s),
                              icon: const Icon(Icons.payments_outlined, size: 16),
                              label: const Text('سند صرف'),
                            ),
                            OutlinedButton.icon(
                              onPressed: () async {
                                final payments = ctrl.supplierPayments
                                    .where((p) => p.supplierId == s.id)
                                    .toList();
                                await showDialog<void>(
                                  context: context,
                                  builder: (ctx) => AlertDialog(
                                    title: Text('سندات صرف — ${s.name}'),
                                    content: SizedBox(
                                      width: double.maxFinite,
                                      child: payments.isEmpty
                                          ? const Text('لا توجد سندات صرف مسجلة.')
                                          : ListView.separated(
                                              shrinkWrap: true,
                                              itemCount: payments.length,
                                              separatorBuilder: (_, idx) => const Divider(height: 1),
                                              itemBuilder: (_, pi) {
                                                final pay = payments[pi];
                                                return ListTile(
                                                  dense: true,
                                                  title: Text(Money(pay.amountMinor, pay.currency).format()),
                                                  subtitle: Text('${pay.dateIso} • ${pay.paymentMethod} ${pay.notes}'),
                                                  trailing: IconButton(
                                                    icon: const Icon(Icons.delete_outline, size: 18),
                                                    onPressed: () async {
                                                      await ctrl.repo.deleteSupplierPayment(pay.id);
                                                      await ctrl.reloadAll();
                                                      if (ctx.mounted) Navigator.pop(ctx);
                                                    },
                                                  ),
                                                );
                                              },
                                            ),
                                    ),
                                    actions: [
                                      TextButton(
                                        onPressed: () => Navigator.pop(ctx),
                                        child: const Text('إغلاق'),
                                      ),
                                    ],
                                  ),
                                );
                              },
                              icon: const Icon(Icons.receipt_long_outlined, size: 16),
                              label: const Text('السندات'),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                );
              },
            ),
    );
  }
}

// -----------------------------------------------------------------------------
// 4. Supplier Purchases List & Edit
// -----------------------------------------------------------------------------

class _SupplierPurchasesView extends StatelessWidget {
  const _SupplierPurchasesView();

  Future<void> _editPurchaseDialog(
      BuildContext context, AppController ctrl, SupplierPurchase p) async {
    final first = p.items.isEmpty ? null : p.items.first;
    final qatCtrl = TextEditingController(text: first?.qatTypeName ?? '');
    final qtyCtrl = TextEditingController(
      text: first != null ? QuantityMath.formatMilli(first.qtyMilli) : '1',
    );
    final buyCtrl = TextEditingController(
      text: first != null ? Money.formatMinor(first.buyPriceMinor).replaceAll(',', '') : '0',
    );
    final sellCtrl = TextEditingController();
    final discCtrl = TextEditingController(
      text: Money.formatMinor(p.discountMinor).replaceAll(',', ''),
    );
    final paidCtrl = TextEditingController(
      text: Money.formatMinor(p.paidAmountMinor).replaceAll(',', ''),
    );
    final notesCtrl = TextEditingController(text: p.notes);
    PaymentType pType = p.paymentType;

    await showDialog<void>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setModalState) => AlertDialog(
          title: Text('تعديل فاتورة شراء ${p.purchaseNumber}'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(controller: qatCtrl, decoration: const InputDecoration(labelText: 'الصنف')),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Expanded(child: AccountingNumberField(controller: qtyCtrl, label: 'الكمية')),
                    const SizedBox(width: 8),
                    Expanded(child: AccountingNumberField(controller: buyCtrl, label: 'سعر الشراء')),
                  ],
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Expanded(child: AccountingNumberField(controller: sellCtrl, label: 'سعر البيع (اختياري)')),
                    const SizedBox(width: 8),
                    Expanded(child: AccountingNumberField(controller: discCtrl, label: 'الخصم')),
                  ],
                ),
                const SizedBox(height: 10),
                SegmentedButton<PaymentType>(
                  segments: const [
                    ButtonSegment(value: PaymentType.paid, label: Text('نقداً')),
                    ButtonSegment(value: PaymentType.partial, label: Text('جزئي')),
                    ButtonSegment(value: PaymentType.credit, label: Text('آجل')),
                  ],
                  selected: {pType},
                  onSelectionChanged: (s) => setModalState(() => pType = s.first),
                ),
                if (pType == PaymentType.partial) ...[
                  const SizedBox(height: 8),
                  AccountingNumberField(controller: paidCtrl, label: 'المدفوع'),
                ],
                const SizedBox(height: 8),
                TextField(controller: notesCtrl, decoration: const InputDecoration(labelText: 'ملاحظات')),
              ],
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('إلغاء')),
            FilledButton(
              onPressed: () async {
                final qty = QuantityMath.parseMilli(qtyCtrl.text);
                final buy = Money.parseMinorUnits(buyCtrl.text);
                final sell = Money.parseMinorUnits(sellCtrl.text);
                if (qatCtrl.text.trim().isEmpty || qty <= 0) return;
                final extraItems = p.items.length > 1
                    ? p.items
                        .skip(1)
                        .map((e) => NewPurchaseItemInput(
                              qatTypeName: e.qatTypeName,
                              qtyMilli: e.qtyMilli,
                              unit: e.unit,
                              buyPriceMinor: e.buyPriceMinor,
                            ))
                        .toList()
                    : const <NewPurchaseItemInput>[];
                await ctrl.repo.editSupplierPurchase(
                  purchaseId: p.id,
                  newItems: [
                    NewPurchaseItemInput(
                      qatTypeName: qatCtrl.text.trim(),
                      qtyMilli: qty,
                      unit: first?.unit ?? 'ربطة',
                      buyPriceMinor: buy,
                      sellPriceMinor: sell,
                    ),
                    ...extraItems,
                  ],
                  currency: p.currency,
                  discountMinor: Money.parseMinorUnits(discCtrl.text),
                  paymentType: pType,
                  paidAmountMinor: Money.parseMinorUnits(paidCtrl.text),
                  notes: notesCtrl.text,
                );
                await ctrl.reloadAll();
                if (ctx.mounted) Navigator.pop(ctx);
              },
              child: const Text('حفظ التعديل'),
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

    if (ctrl.purchases.isEmpty) {
      return const EmptyLedgerState(
        icon: Icons.shopping_bag_outlined,
        title: 'لا توجد فواتير مشتريات',
        subtitle: 'يمكنك إنشاء فاتورة شراء من بطاقة المورد في التبويب المجاور',
      );
    }

    return ListView.separated(
      padding: const EdgeInsets.all(16),
      itemCount: ctrl.purchases.length,
      separatorBuilder: (_, idx) => const SizedBox(height: 10),
      itemBuilder: (_, idx) {
        final p = ctrl.purchases[idx];
        return Card(
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text('${p.purchaseNumber} — ${p.supplierName}',
                          style: const TextStyle(fontWeight: FontWeight.w700)),
                    ),
                    CurrencyBadge(currency: p.currency),
                    const SizedBox(width: 6),
                    StatusPill.forPayment(context, p.paymentType),
                  ],
                ),
                const SizedBox(height: 6),
                Text(
                  '${p.dateIso} • الإجمالي: ${Money(p.totalAmountMinor, p.currency).format()} • المتبقي: ${Money(p.remainingAmountMinor, p.currency).format()}',
                  style: TextStyle(fontSize: 12.5, color: pal.textSecondary),
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    OutlinedButton.icon(
                      onPressed: () => _editPurchaseDialog(context, ctrl, p),
                      icon: const Icon(Icons.edit_outlined, size: 16),
                      label: const Text('تعديل'),
                    ),
                    const SizedBox(width: 8),
                    IconButton(
                      tooltip: 'أرشفة',
                      onPressed: () async {
                        await ctrl.repo.setArchived('supplier_purchases', p.id, true);
                        await ctrl.reloadAll();
                      },
                      icon: const Icon(Icons.archive_outlined, size: 18),
                    ),
                    IconButton(
                      onPressed: () async {
                        await ctrl.repo.deleteSupplierPurchase(p.id);
                        await ctrl.reloadAll();
                      },
                      icon: Icon(Icons.delete_outline, color: pal.danger, size: 18),
                    ),
                  ],
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}
