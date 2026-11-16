import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../core/money.dart';
import '../../core/theme.dart';
import '../../data/models.dart';
import '../../data/qat_repository.dart';
import '../../state/app_controller.dart';
import '../widgets/common_widgets.dart';
import '../widgets/erp_components.dart';

class SupplierPurchasesView extends StatefulWidget {
  const SupplierPurchasesView({super.key});

  @override
  State<SupplierPurchasesView> createState() => _SupplierPurchasesViewState();
}

class _SupplierPurchasesViewState extends State<SupplierPurchasesView> {
  String _search = '';
  PaymentType? _paymentFilter;

  Future<void> _editPurchaseDialog(
    BuildContext context,
    AppController ctrl,
    SupplierPurchase p,
  ) async {
    final first = p.items.isEmpty ? null : p.items.first;
    final qatCtrl = TextEditingController(text: first?.qatTypeName ?? '');
    final qtyCtrl = TextEditingController(
      text: first != null ? QuantityMath.formatMilli(first.qtyMilli) : '1',
    );
    final buyCtrl = TextEditingController(
      text: first != null
          ? Money.formatMinor(first.buyPriceMinor, showZeroDecimals: false)
              .replaceAll(',', '')
          : '0',
    );
    final sellCtrl = TextEditingController();
    final discCtrl = TextEditingController(
      text: Money.formatMinor(p.discountMinor, showZeroDecimals: false)
          .replaceAll(',', ''),
    );
    final paidCtrl = TextEditingController(
      text: Money.formatMinor(p.paidAmountMinor, showZeroDecimals: false)
          .replaceAll(',', ''),
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
                TextField(
                  controller: qatCtrl,
                  decoration: const InputDecoration(labelText: 'الصنف'),
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Expanded(
                      child: AccountingNumberField(
                        controller: qtyCtrl,
                        label: 'الكمية',
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: AccountingNumberField(
                        controller: buyCtrl,
                        label: 'سعر الشراء',
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Expanded(
                      child: AccountingNumberField(
                        controller: sellCtrl,
                        label: 'سعر البيع (اختياري)',
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: AccountingNumberField(
                        controller: discCtrl,
                        label: 'الخصم',
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 10),
                SegmentedButton<PaymentType>(
                  segments: const [
                    ButtonSegment<PaymentType>(
                      value: PaymentType.paid,
                      label: Text('نقداً'),
                    ),
                    ButtonSegment<PaymentType>(
                      value: PaymentType.partial,
                      label: Text('جزئي'),
                    ),
                    ButtonSegment<PaymentType>(
                      value: PaymentType.credit,
                      label: Text('آجل'),
                    ),
                  ],
                  selected: {pType},
                  onSelectionChanged: (s) =>
                      setModalState(() => pType = s.first),
                ),
                if (pType == PaymentType.partial) ...[
                  const SizedBox(height: 8),
                  AccountingNumberField(
                    controller: paidCtrl,
                    label: 'المدفوع',
                  ),
                ],
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
                final qty = QuantityMath.parseMilli(qtyCtrl.text);
                final buy = Money.parseMinorUnits(buyCtrl.text);
                final sell = Money.parseMinorUnits(sellCtrl.text);
                if (qatCtrl.text.trim().isEmpty || qty <= 0) return;
                final extraItems = p.items.length > 1
                    ? p.items
                        .skip(1)
                        .map(
                          (e) => NewPurchaseItemInput(
                            qatTypeName: e.qatTypeName,
                            qtyMilli: e.qtyMilli,
                            unit: e.unit,
                            buyPriceMinor: e.buyPriceMinor,
                          ),
                        )
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
    qatCtrl.dispose();
    qtyCtrl.dispose();
    buyCtrl.dispose();
    sellCtrl.dispose();
    discCtrl.dispose();
    paidCtrl.dispose();
    notesCtrl.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final pal = context.pal;

    final list = ctrl.purchases.where((p) {
      if (_paymentFilter != null && p.paymentType != _paymentFilter) {
        return false;
      }
      if (_search.isEmpty) return true;
      final q = _search.toLowerCase();
      return p.supplierName.toLowerCase().contains(q) ||
          p.purchaseNumber.toLowerCase().contains(q);
    }).toList();

    return Column(
      children: [
        ErpSearchFilterBar(
          searchHint: 'بحث برقم فاتورة الشراء أو المورد...',
          onSearchChanged: (v) => setState(() => _search = v.trim()),
          filters: [
            ChoiceChip(
              label: const Text('الكل'),
              selected: _paymentFilter == null,
              onSelected: (_) => setState(() => _paymentFilter = null),
            ),
            for (final pt in PaymentType.values)
              ChoiceChip(
                label: Text(pt.arabicLabel),
                selected: _paymentFilter == pt,
                onSelected: (_) => setState(() => _paymentFilter = pt),
              ),
          ],
        ),
        Expanded(
          child: list.isEmpty
              ? const EmptyLedgerState(
                  icon: Icons.shopping_bag_outlined,
                  title: 'لا توجد فواتير مشتريات مطابقة',
                  subtitle:
                      'يمكنك إنشاء فاتورة شراء من بطاقة المورد في التبويب المجاور',
                )
              : ListView.separated(
                  padding: const EdgeInsets.all(16),
                  itemCount: list.length,
                  separatorBuilder: (_, idx) => const SizedBox(height: 10),
                  itemBuilder: (_, idx) {
                    final p = list[idx];
                    return Card(
                      child: Padding(
                        padding: const EdgeInsets.all(14),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                Expanded(
                                  child: Text(
                                    '${p.purchaseNumber} — ${p.supplierName}',
                                    style: const TextStyle(
                                      fontWeight: FontWeight.w700,
                                    ),
                                  ),
                                ),
                                CurrencyBadge(currency: p.currency),
                                const SizedBox(width: 6),
                                StatusPill.forPayment(context, p.paymentType),
                              ],
                            ),
                            const SizedBox(height: 6),
                            Text(
                              '${p.dateIso} • الإجمالي: ${Money(p.totalAmountMinor, p.currency).format()} • المتبقي: ${Money(p.remainingAmountMinor, p.currency).format()}',
                              style: TextStyle(
                                fontSize: 12.5,
                                color: pal.textSecondary,
                                fontFeatures: const [
                                  FontFeature.tabularFigures(),
                                ],
                              ),
                            ),
                            const SizedBox(height: 8),
                            Wrap(
                              spacing: 8,
                              children: [
                                OutlinedButton.icon(
                                  onPressed: () =>
                                      _editPurchaseDialog(context, ctrl, p),
                                  icon: const Icon(
                                    Icons.edit_outlined,
                                    size: 16,
                                  ),
                                  label: const Text('تعديل'),
                                ),
                                OutlinedButton.icon(
                                  onPressed: () async {
                                    final ok = await showErpConfirmDialog(
                                      context,
                                      title: 'أرشفة فاتورة الشراء',
                                      message:
                                          'نقل فاتورة ${p.purchaseNumber} إلى الأرشيف وتحديث رصيد المورد؟',
                                      confirmLabel: 'أرشفة',
                                    );
                                    if (!ok) return;
                                    await ctrl.repo.setArchived(
                                      'supplier_purchases',
                                      p.id,
                                      true,
                                    );
                                    await ctrl.reloadAll();
                                  },
                                  icon: const Icon(
                                    Icons.archive_outlined,
                                    size: 16,
                                  ),
                                  label: const Text('أرشفة'),
                                ),
                                if (ctrl.canDeleteCurrent)
                                  IconButton(
                                    tooltip: 'حذف',
                                    onPressed: () async {
                                      final ok = await showErpConfirmDialog(
                                        context,
                                        title: 'حذف فاتورة الشراء',
                                        message:
                                            'هل تريد حذف فاتورة ${p.purchaseNumber} وعكس أثرها على المورد؟',
                                        confirmLabel: 'حذف',
                                        destructive: true,
                                      );
                                      if (!ok) return;
                                      await ctrl.repo
                                          .deleteSupplierPurchase(p.id);
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
