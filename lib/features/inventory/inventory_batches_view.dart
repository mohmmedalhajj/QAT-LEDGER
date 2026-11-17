import 'package:material_ui/material_ui.dart';
import 'package:printing/printing.dart';
import 'package:provider/provider.dart';

import '../../core/money.dart';
import '../../core/theme.dart';
import '../../data/models.dart';
import '../../services/pdf_export_service.dart';
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
  int _section = 0; // 0 = batches, 1 = import weights
  String _search = '';
  String _weightSearch = '';
  _StockFilter _stockFilter = _StockFilter.all;
  CurrencyCode? _currencyFilter;

  Future<void> _openImportWeightDialog(
    AppController ctrl, {
    QatImportWeight? existing,
  }) async {
    final receiverCtrl =
        TextEditingController(text: existing?.receiverName ?? '');
    final weightCtrl = TextEditingController(
      text: existing != null
          ? QuantityMath.formatMilli(existing.weightMilli)
          : '',
    );
    final notesCtrl = TextEditingController(text: existing?.notes ?? '');

    await showDialog<void>(
      context: context,
      builder: (ctx) => ErpDialogControllerScope(
        controllers: [receiverCtrl, weightCtrl, notesCtrl],
        child: AlertDialog(
          title: Text(
            existing == null
                ? 'تسجيل وزن الوارد اليومي'
                : 'تعديل وزن الوارد',
          ),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: receiverCtrl,
                  decoration: const InputDecoration(
                    labelText: 'اسم المستلم / المورد *',
                  ),
                ),
                const SizedBox(height: 8),
                AccountingNumberField(
                  controller: weightCtrl,
                  label: 'الوزن الإجمالي (كجم) *',
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
                final weightMilli = QuantityMath.parseMilli(weightCtrl.text);
                if (receiverCtrl.text.trim().isEmpty || weightMilli <= 0) return;
                if (existing == null) {
                  await ctrl.repo.addImportWeight(
                    weightMilli: weightMilli,
                    receiverName: receiverCtrl.text.trim(),
                    notes: notesCtrl.text,
                  );
                } else {
                  await ctrl.repo.updateImportWeight(
                    id: existing.id,
                    weightMilli: weightMilli,
                    receiverName: receiverCtrl.text.trim(),
                    notes: notesCtrl.text,
                  );
                }
                await ctrl.reloadAll();
                if (ctx.mounted) Navigator.pop(ctx);
              },
              child: const Text('حفظ الوزن'),
            ),
          ],
        ),
      ),
    );
  }

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
    final itemNoCtrl = TextEditingController(text: existing?.itemNumber ?? '');
    final thresholdCtrl = TextEditingController(
      text: existing != null && existing.lowStockThresholdMilli > 0
          ? QuantityMath.formatMilli(existing.lowStockThresholdMilli)
          : '',
    );
    final receiverCtrl =
        TextEditingController(text: existing?.receiverName ?? '');
    final notesCtrl = TextEditingController(text: existing?.notes ?? '');
    CurrencyCode currency =
        existing?.currency ?? ctrl.currencyPolicy.defaultCurrency;

    await showDialog<void>(
      context: context,
      builder: (ctx) => ErpDialogControllerScope(
        controllers: [
          nameCtrl,
          qtyCtrl,
          unitCtrl,
          buyCtrl,
          sellCtrl,
          itemNoCtrl,
          thresholdCtrl,
          receiverCtrl,
          notesCtrl,
        ],
        child: StatefulBuilder(
          builder: (ctx, setModalState) => AlertDialog(
            title: Text(
              existing == null ? 'إضافة دفعة مخزون' : 'تعديل دفعة مخزون',
            ),
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
                    isExpanded: true,
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
                const SizedBox(height: 8),
                Row(
                  children: [
                    Expanded(
                      child: TextField(
                        controller: itemNoCtrl,
                        textDirection: TextDirection.ltr,
                        decoration: const InputDecoration(
                          labelText: 'رقم الصنف / الشحنة',
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: AccountingNumberField(
                        controller: thresholdCtrl,
                        label: 'حد التنبيه المنخفض',
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                TextField(
                  controller: receiverCtrl,
                  decoration: const InputDecoration(
                    labelText: 'مستلم المخزون (اختياري)',
                  ),
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
                if (nameCtrl.text.trim().isEmpty) return;
                final qtyMilli = QuantityMath.parseMilli(qtyCtrl.text);
                final buyMinor = Money.parseMinorUnits(buyCtrl.text);
                final sellMinor = Money.parseMinorUnits(sellCtrl.text);
                final threshMilli = QuantityMath.parseMilli(thresholdCtrl.text);
                if (qtyMilli < 0) return;
                if (existing == null) {
                  await ctrl.repo.addInventoryBatch(
                    qatTypeName: nameCtrl.text,
                    qtyMilli: qtyMilli,
                    unit: unitCtrl.text,
                    buyPriceMinor: buyMinor,
                    sellPriceMinor: sellMinor,
                    currency: currency,
                    itemNumber: itemNoCtrl.text,
                    lowStockThresholdMilli: threshMilli,
                    receiverName: receiverCtrl.text,
                    notes: notesCtrl.text,
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
                    itemNumber: itemNoCtrl.text,
                    lowStockThresholdMilli: threshMilli,
                    receiverName: receiverCtrl.text,
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

    final filtered = ctrl.batches.where((b) {
      if (_currencyFilter != null && b.currency != _currencyFilter) {
        return false;
      }
      if (_stockFilter == _StockFilter.available && b.remainingQtyMilli <= 0) {
        return false;
      }
      if (_stockFilter == _StockFilter.low && !b.isLowStock) {
        return false;
      }
      if (_stockFilter == _StockFilter.depleted && b.remainingQtyMilli > 0) {
        return false;
      }
      if (_search.isEmpty) return true;
      final q = _search.toLowerCase();
      return b.qatTypeName.toLowerCase().contains(q) ||
          b.supplierName.toLowerCase().contains(q) ||
          b.receiverName.toLowerCase().contains(q) ||
          b.itemNumber.toLowerCase().contains(q);
    }).toList();

    final filteredWeights = ctrl.importWeights.where((w) {
      if (_weightSearch.isEmpty) return true;
      final q = _weightSearch.toLowerCase();
      return w.receiverName.toLowerCase().contains(q) ||
          w.dateIso.contains(q) ||
          w.notes.toLowerCase().contains(q);
    }).toList();

    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _section == 0
            ? _openBatchDialog(ctrl)
            : _openImportWeightDialog(ctrl),
        icon: const Icon(Icons.add_box_outlined),
        label: Text(_section == 0 ? 'إضافة دفعة' : 'تسجيل وزن وارد'),
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 10, 16, 4),
            child: SegmentedButton<int>(
              segments: [
                const ButtonSegment<int>(
                  value: 0,
                  icon: Icon(Icons.inventory_2_outlined, size: 17),
                  label: Text('دفعات المخزون'),
                ),
                ButtonSegment<int>(
                  value: 1,
                  icon: const Icon(Icons.scale_outlined, size: 17),
                  label: Text('أوزان الوارد (${ctrl.importWeights.length})'),
                ),
              ],
              selected: {_section},
              onSelectionChanged: (s) => setState(() => _section = s.first),
            ),
          ),
          if (_section == 1) ...[
            ErpSearchFilterBar(
              searchHint: 'بحث باسم المستلم أو التاريخ...',
              onSearchChanged: (v) => setState(() => _weightSearch = v.trim()),
            ),
            Expanded(
              child: filteredWeights.isEmpty
                  ? EmptyLedgerState(
                      icon: Icons.scale_outlined,
                      title: 'لا توجد أوزان وارد مطابقة',
                      subtitle:
                          'سجل أوزان الشحنات الواردة اليومية ومستلميها بدقة',
                      actionLabel: 'تسجيل وزن وارد',
                      onAction: () => _openImportWeightDialog(ctrl),
                    )
                  : ListView(
                      padding: const EdgeInsets.fromLTRB(16, 8, 16, 84),
                      children: [
                        ErpSummaryBar(
                          metrics: [
                            ErpSummaryMetric(
                              label: 'إجمالي الشحنات',
                              value: '${filteredWeights.length}',
                            ),
                            ErpSummaryMetric(
                              label: 'إجمالي الوزن المسجل',
                              value:
                                  '${QuantityMath.formatMilli(filteredWeights.fold<int>(0, (s, w) => s + w.weightMilli))} كجم',
                              toneColor: pal.brand,
                            ),
                          ],
                        ),
                        const SizedBox(height: 10),
                        for (final w in filteredWeights)
                          Card(
                            margin: const EdgeInsets.only(bottom: 8),
                            child: ListTile(
                              leading: CircleAvatar(
                                backgroundColor: pal.brandSoft,
                                child: Icon(
                                  Icons.scale_outlined,
                                  color: pal.brand,
                                  size: 19,
                                ),
                              ),
                              title: Text(
                                '${QuantityMath.formatMilli(w.weightMilli)} كجم — ${w.receiverName}',
                                style: const TextStyle(
                                  fontWeight: FontWeight.w700,
                                  fontSize: 13.5,
                                ),
                              ),
                              subtitle: Text(
                                '${w.dateIso}${w.notes.isNotEmpty ? ' • ${w.notes}' : ''}',
                              ),
                              trailing: Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  IconButton(
                                    tooltip: 'تعديل',
                                    icon: const Icon(
                                      Icons.edit_outlined,
                                      size: 18,
                                    ),
                                    onPressed: () => _openImportWeightDialog(
                                      ctrl,
                                      existing: w,
                                    ),
                                  ),
                                  if (ctrl.canDeleteCurrent)
                                    IconButton(
                                      tooltip: 'حذف',
                                      icon: Icon(
                                        Icons.delete_outline,
                                        color: pal.danger,
                                        size: 18,
                                      ),
                                      onPressed: () async {
                                        final ok = await showErpConfirmDialog(
                                          context,
                                          title: 'حذف سجل الوزن',
                                          message:
                                              'هل تريد حذف سجل وزن الوارد (${w.receiverName})؟',
                                          confirmLabel: 'حذف',
                                          destructive: true,
                                        );
                                        if (!ok) return;
                                        await ctrl.repo
                                            .deleteImportWeight(w.id);
                                        await ctrl.reloadAll();
                                      },
                                    ),
                                ],
                              ),
                            ),
                          ),
                      ],
                    ),
            ),
          ] else ...[
            ErpSearchFilterBar(
            searchHint: 'بحث باسم الصنف أو المورد أو المستلم...',
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
                                ErpSectionHeader(
                                  title: 'تقييم المخزون الحالي',
                                  icon: Icons.assessment_outlined,
                                  trailing: TextButton.icon(
                                    onPressed: () async {
                                      final bytes = await PdfExportService
                                          .instance
                                          .buildInventoryReportPdf(
                                        agency: ctrl.settings,
                                        batches: filtered,
                                        valuation: ctrl.inventoryValuation,
                                        importWeights: ctrl.importWeights,
                                      );
                                      await Printing.layoutPdf(
                                        onLayout: (_) async => bytes,
                                      );
                                    },
                                    icon: const Icon(
                                      Icons.picture_as_pdf_outlined,
                                      size: 16,
                                    ),
                                    label: const Text('تصدير PDF'),
                                  ),
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
                            final lowStock = b.isLowStock;
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
                                      'شراء: ${Money(b.buyPriceMinor, b.currency).format()} • بيع: ${Money(b.sellPriceMinor, b.currency).format()}'
                                      '${b.supplierName.isNotEmpty ? ' • المورد: ${b.supplierName}' : ''}'
                                      '${b.receiverName.isNotEmpty ? ' • المستلم: ${b.receiverName}' : ''}'
                                      '${b.itemNumber.isNotEmpty ? ' • رقم: ${b.itemNumber}' : ''}',
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
        ],
      ),
    );
  }
}
