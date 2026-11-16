import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../core/money.dart';
import '../../core/theme.dart';
import '../../data/models.dart';
import '../../state/app_controller.dart';
import '../widgets/common_widgets.dart';
import '../widgets/erp_components.dart';

enum _StockFilter { all, available, low, depleted }

class InventoryBatchesView extends StatefulWidget {
  const InventoryBatchesView({super.key});

  @override
  State<InventoryBatchesView> createState() => _InventoryBatchesViewState();
}

class _InventoryBatchesViewState extends State<InventoryBatchesView> {
  String _search = '';
  _StockFilter _stockFilter = _StockFilter.all;
  CurrencyCode? _currencyFilter;

  Future<void> _openBatchDialog(
    AppController ctrl, {
    InventoryBatch? existing,
  }) async {
    final nameCtrl = TextEditingController(text: existing?.qatTypeName ?? '');
    final qtyCtrl = TextEditingController(
      text: existing != null
          ? QuantityMath.formatMilli(existing.remainingQtyMilli)
          : '1',
    );
    final unitCtrl = TextEditingController(text: existing?.unit ?? 'ربطة');
    final buyCtrl = TextEditingController(
      text: existing != null
          ? Money.formatMinor(existing.buyPriceMinor, showZeroDecimals: false)
              .replaceAll(',', '')
          : '',
    );
    final sellCtrl = TextEditingController(
      text: existing != null
          ? Money.formatMinor(existing.sellPriceMinor, showZeroDecimals: false)
              .replaceAll(',', '')
          : '',
    );
    CurrencyCode currency =
        existing?.currency ?? ctrl.currencyPolicy.defaultCurrency;

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
                    initialValue: null,
                    decoration: const InputDecoration(
                      labelText: 'اختيار من الأصناف المعرفة',
                    ),
                    items: ctrl.qatTypes
                        .map(
                          (qt) => DropdownMenuItem<String>(
                            value: qt.name,
                            child: Text(qt.name),
                          ),
                        )
                        .toList(),
                    onChanged: (v) {
                      if (v == null) return;
                      nameCtrl.text = v;
                      final qt = ctrl.qatTypes.firstWhere((e) => e.name == v);
                      buyCtrl.text = Money.formatMinor(
                        qt.defaultBuyPriceMinor,
                        showZeroDecimals: false,
                      ).replaceAll(',', '');
                      sellCtrl.text = Money.formatMinor(
                        qt.defaultSellPriceMinor,
                        showZeroDecimals: false,
                      ).replaceAll(',', '');
                    },
                  ),
                const SizedBox(height: 8),
                TextField(
                  controller: nameCtrl,
                  decoration: const InputDecoration(labelText: 'اسم الصنف *'),
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
                      child: TextField(
                        controller: unitCtrl,
                        decoration: const InputDecoration(labelText: 'الوحدة'),
                      ),
                    ),
                  ],
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
                if (nameCtrl.text.trim().isEmpty) return;
                final qtyMilli = QuantityMath.parseMilli(qtyCtrl.text);
                final buyMinor = Money.parseMinorUnits(buyCtrl.text);
                final sellMinor = Money.parseMinorUnits(sellCtrl.text);
                if (qtyMilli < 0) return;
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
    nameCtrl.dispose();
    qtyCtrl.dispose();
    unitCtrl.dispose();
    buyCtrl.dispose();
    sellCtrl.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final pal = context.pal;

    final filtered = ctrl.batches.where((b) {
      if (_currencyFilter != null && b.currency != _currencyFilter) {
        return false;
      }
      if (_stockFilter == _StockFilter.available && b.remainingQtyMilli <= 0) {
        return false;
      }
      if (_stockFilter == _StockFilter.low &&
          (b.remainingQtyMilli <= 0 || b.remainingQtyMilli > 5000)) {
        return false;
      }
      if (_stockFilter == _StockFilter.depleted && b.remainingQtyMilli > 0) {
        return false;
      }
      if (_search.isEmpty) return true;
      final q = _search.toLowerCase();
      return b.qatTypeName.toLowerCase().contains(q) ||
          b.supplierName.toLowerCase().contains(q);
    }).toList();

    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _openBatchDialog(ctrl),
        icon: const Icon(Icons.add_box_outlined),
        label: const Text('إضافة دفعة'),
      ),
      body: Column(
        children: [
          ErpSearchFilterBar(
            searchHint: 'بحث باسم الصنف أو المورد...',
            onSearchChanged: (v) => setState(() => _search = v.trim()),
            filters: [
              ChoiceChip(
                label: const Text('الكل'),
                selected: _stockFilter == _StockFilter.all,
                onSelected: (_) =>
                    setState(() => _stockFilter = _StockFilter.all),
              ),
              ChoiceChip(
                label: const Text('متوفر'),
                selected: _stockFilter == _StockFilter.available,
                onSelected: (_) =>
                    setState(() => _stockFilter = _StockFilter.available),
              ),
              ChoiceChip(
                label: const Text('منخفض'),
                selected: _stockFilter == _StockFilter.low,
                onSelected: (_) =>
                    setState(() => _stockFilter = _StockFilter.low),
              ),
              ChoiceChip(
                label: const Text('نفد'),
                selected: _stockFilter == _StockFilter.depleted,
                onSelected: (_) =>
                    setState(() => _stockFilter = _StockFilter.depleted),
              ),
              if (ctrl.currencyPolicy.canSwitchCurrency) ...[
                const SizedBox(width: 6),
                for (final cur in ctrl.currencyPolicy.allowedCurrencies)
                  FilterChip(
                    label: Text(cur.code),
                    selected: _currencyFilter == cur,
                    onSelected: (on) =>
                        setState(() => _currencyFilter = on ? cur : null),
                  ),
              ],
            ],
          ),
          Expanded(
            child: filtered.isEmpty
                ? const EmptyLedgerState(
                    icon: Icons.inventory_2_outlined,
                    title: 'لا توجد دفعات مطابقة',
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
                                const ErpSectionHeader(
                                  title: 'تقييم المخزون الحالي',
                                  icon: Icons.assessment_outlined,
                                ),
                                const SizedBox(height: 10),
                                for (final v in ctrl.inventoryValuation.values)
                                  Padding(
                                    padding: const EdgeInsets.only(bottom: 6),
                                    child: ErpSummaryBar(
                                      metrics: [
                                        ErpSummaryMetric(
                                          label: 'العملة',
                                          value: v.currency.code,
                                        ),
                                        ErpSummaryMetric(
                                          label: 'الدفعات / الكمية',
                                          value:
                                              '${v.batchCount} (${QuantityMath.formatMilli(v.totalQtyMilli)})',
                                        ),
                                        ErpSummaryMetric(
                                          label: 'بالتكلفة',
                                          value: Money(
                                            v.totalCostMinor,
                                            v.currency,
                                          ).format(),
                                        ),
                                        ErpSummaryMetric(
                                          label: 'القيمة البيعية',
                                          value: Money(
                                            v.totalRetailMinor,
                                            v.currency,
                                          ).format(),
                                          toneColor: pal.success,
                                        ),
                                      ],
                                    ),
                                  ),
                              ],
                            ),
                          ),
                        ),
                      for (final b in filtered) ...[
                        Builder(
                          builder: (context) {
                            final lowStock = b.remainingQtyMilli > 0 &&
                                b.remainingQtyMilli <= 5000;
                            final depleted = b.remainingQtyMilli <= 0;
                            final ratio = b.initialQtyMilli > 0
                                ? (b.remainingQtyMilli / b.initialQtyMilli)
                                    .clamp(0.0, 1.0)
                                    .toDouble()
                                : 0.0;
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
                                              fontSize: 14.5,
                                            ),
                                          ),
                                        ),
                                        CurrencyBadge(currency: b.currency),
                                        const SizedBox(width: 6),
                                        StatusPill(
                                          label:
                                              '${QuantityMath.formatMilli(b.remainingQtyMilli)} ${b.unit}',
                                          bg: depleted
                                              ? pal.dangerSoft
                                              : (lowStock
                                                  ? pal.warningSoft
                                                  : pal.successSoft),
                                          fg: depleted
                                              ? pal.danger
                                              : (lowStock
                                                  ? pal.warning
                                                  : pal.success),
                                        ),
                                      ],
                                    ),
                                    const SizedBox(height: 8),
                                    LinearProgressIndicator(
                                      value: ratio,
                                      minHeight: 5,
                                      borderRadius: BorderRadius.circular(6),
                                      color: depleted
                                          ? pal.danger
                                          : (lowStock
                                              ? pal.warning
                                              : pal.brand),
                                      backgroundColor: pal.cardElevated,
                                    ),
                                    const SizedBox(height: 8),
                                    Text(
                                      'شراء: ${Money(b.buyPriceMinor, b.currency).format()} • بيع: ${Money(b.sellPriceMinor, b.currency).format()}${b.supplierName.isNotEmpty ? ' • المورد: ${b.supplierName}' : ''}',
                                      style: TextStyle(
                                        fontSize: 12,
                                        color: pal.textSecondary,
                                      ),
                                    ),
                                    const SizedBox(height: 8),
                                    Wrap(
                                      spacing: 8,
                                      children: [
                                        OutlinedButton.icon(
                                          onPressed: () => _openBatchDialog(
                                            ctrl,
                                            existing: b,
                                          ),
                                          icon: const Icon(
                                            Icons.edit_outlined,
                                            size: 16,
                                          ),
                                          label: const Text('تعديل'),
                                        ),
                                        OutlinedButton.icon(
                                          onPressed: () async {
                                            final ok =
                                                await showErpConfirmDialog(
                                              context,
                                              title: 'أرشفة دفعة المخزون',
                                              message:
                                                  'نقل دفعة ${b.qatTypeName} إلى الأرشيف؟',
                                              confirmLabel: 'أرشفة',
                                            );
                                            if (!ok) return;
                                            await ctrl.repo.setArchived(
                                              'inventory_batches',
                                              b.id,
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
                                              final ok =
                                                  await showErpConfirmDialog(
                                                context,
                                                title: 'حذف دفعة المخزون',
                                                message:
                                                    'هل تريد حذف دفعة ${b.qatTypeName} نهائياً؟',
                                                confirmLabel: 'حذف',
                                                destructive: true,
                                              );
                                              if (!ok) return;
                                              await ctrl.repo
                                                  .deleteInventoryBatch(b.id);
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
                      ],
                    ],
                  ),
          ),
        ],
      ),
    );
  }
}
