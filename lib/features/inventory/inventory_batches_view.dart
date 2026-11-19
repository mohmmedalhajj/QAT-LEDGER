import 'dart:async';

import 'package:material_ui/material_ui.dart';
import 'package:printing/printing.dart';
import 'package:provider/provider.dart';

import '../../core/date_utils.dart';
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
  int _section = 0; // 0 = batches, 1 = import weights, 2 = receivers & daily matching
  String _search = '';
  String _weightSearch = '';
  _StockFilter _stockFilter = _StockFilter.all;
  CurrencyCode? _currencyFilter;
  String _matchFromIso = QatDateUtils.todayIso();
  String _matchToIso = QatDateUtils.todayIso();
  DailyInventoryMatching? _matching;
  bool _loadingMatching = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) unawaited(_loadMatching(context.read<AppController>()));
    });
  }

  Future<void> _loadMatching(AppController ctrl) async {
    setState(() => _loadingMatching = true);
    try {
      final m = await ctrl.repo.getDailyInventoryMatching(
        fromIso: _matchFromIso,
        toIso: _matchToIso,
      );
      if (mounted) setState(() => _matching = m);
    } finally {
      if (mounted) setState(() => _loadingMatching = false);
    }
  }

  Future<void> _openReceiverDialog(
    AppController ctrl, {
    InventoryReceiver? existing,
  }) async {
    final nameCtrl = TextEditingController(text: existing?.name ?? '');
    final phoneCtrl = TextEditingController(text: existing?.phone ?? '');
    final notesCtrl = TextEditingController(text: existing?.notes ?? '');
    bool isActive = existing?.isActive ?? true;

    await showDialog<void>(
      context: context,
      builder: (ctx) => ErpDialogControllerScope(
        controllers: [nameCtrl, phoneCtrl, notesCtrl],
        child: StatefulBuilder(
          builder: (ctx, setModalState) => AlertDialog(
            title: Text(
              existing == null ? 'إضافة مستلم مخزون' : 'تعديل مستلم مخزون',
            ),
            content: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  TextField(
                    controller: nameCtrl,
                    decoration: const InputDecoration(labelText: 'اسم المستلم *'),
                  ),
                  const SizedBox(height: 8),
                  TextField(
                    controller: phoneCtrl,
                    textDirection: TextDirection.ltr,
                    keyboardType: TextInputType.phone,
                    decoration: const InputDecoration(labelText: 'رقم الهاتف'),
                  ),
                  const SizedBox(height: 8),
                  TextField(
                    controller: notesCtrl,
                    decoration: const InputDecoration(labelText: 'ملاحظات'),
                  ),
                  const SizedBox(height: 8),
                  SwitchListTile(
                    dense: true,
                    contentPadding: EdgeInsets.zero,
                    title: const Text('نشط حالياً'),
                    value: isActive,
                    onChanged: (v) => setModalState(() => isActive = v),
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
                  await ctrl.repo.upsertInventoryReceiver(
                    id: existing?.id,
                    name: nameCtrl.text,
                    phone: phoneCtrl.text,
                    notes: notesCtrl.text,
                    isActive: isActive,
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
    int weightDateMs = existing?.dateMs ?? DateTime.now().millisecondsSinceEpoch;

    await showDialog<void>(
      context: context,
      builder: (ctx) => ErpDialogControllerScope(
        controllers: [receiverCtrl, weightCtrl, notesCtrl],
        child: StatefulBuilder(
          builder: (ctx, setModalState) => AlertDialog(
            title: Text(
              existing == null
                  ? 'تسجيل وزن الوارد اليومي'
                  : 'تعديل وزن الوارد',
            ),
            content: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  ErpDatePickerTile(
                    label: 'التاريخ',
                    dateMs: weightDateMs,
                    onChanged: (ms) => setModalState(() => weightDateMs = ms),
                  ),
                  const SizedBox(height: 8),
                  if (ctrl.inventoryReceivers.isNotEmpty) ...[
                    DropdownButtonFormField<String>(
                      initialValue: null,
                      isExpanded: true,
                      decoration: const InputDecoration(
                        labelText: 'اختيار من قائمة المستلمين',
                      ),
                      items: ctrl.inventoryReceivers
                          .where((r) => r.isActive)
                          .map(
                            (r) => DropdownMenuItem<String>(
                              value: r.name,
                              child: Text(r.name),
                            ),
                          )
                          .toList(),
                      onChanged: (v) {
                        if (v != null) receiverCtrl.text = v;
                      },
                    ),
                    const SizedBox(height: 8),
                  ],
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
                  if (receiverCtrl.text.trim().isEmpty || weightMilli <= 0) {
                    return;
                  }
                  if (existing == null) {
                    await ctrl.repo.addImportWeight(
                      weightMilli: weightMilli,
                      receiverName: receiverCtrl.text.trim(),
                      notes: notesCtrl.text,
                      dateMs: weightDateMs,
                    );
                  } else {
                    await ctrl.repo.updateImportWeight(
                      id: existing.id,
                      weightMilli: weightMilli,
                      receiverName: receiverCtrl.text.trim(),
                      notes: notesCtrl.text,
                      dateIso: QatDateUtils.toIsoDate(
                        DateTime.fromMillisecondsSinceEpoch(weightDateMs),
                      ),
                    );
                  }
                  await ctrl.reloadAll();
                  await _loadMatching(ctrl);
                  if (ctx.mounted) Navigator.pop(ctx);
                },
                child: const Text('حفظ الوزن'),
              ),
            ],
          ),
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
    int? receiverId = existing?.receiverId;

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
                if (ctrl.inventoryReceivers.isNotEmpty) ...[
                  DropdownButtonFormField<int?>(
                    key: ValueKey<String>(
                      'batch_recv_${ctrl.inventoryReceivers.any((r) => r.id == receiverId) ? receiverId : 'none'}',
                    ),
                    initialValue: ctrl.inventoryReceivers
                            .any((r) => r.id == receiverId)
                        ? receiverId
                        : null,
                    isExpanded: true,
                    decoration: const InputDecoration(
                      labelText: 'اختيار من قائمة المستلمين',
                    ),
                    items: [
                      const DropdownMenuItem<int?>(
                        value: null,
                        child: Text('إدخال اسم مستلم يدوياً'),
                      ),
                      ...ctrl.inventoryReceivers.map(
                        (r) => DropdownMenuItem<int?>(
                          value: r.id,
                          child: Text(r.name),
                        ),
                      ),
                    ],
                    onChanged: (v) {
                      setModalState(() => receiverId = v);
                      if (v != null) {
                        receiverCtrl.text = ctrl.inventoryReceivers
                            .firstWhere((r) => r.id == v)
                            .name;
                      }
                    },
                  ),
                  const SizedBox(height: 8),
                ],
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
                    receiverId: receiverId,
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
                    receiverId: receiverId,
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
        onPressed: () {
          if (_section == 0) {
            unawaited(_openBatchDialog(ctrl));
          } else if (_section == 1) {
            unawaited(_openImportWeightDialog(ctrl));
          } else {
            unawaited(_openReceiverDialog(ctrl));
          }
        },
        icon: const Icon(Icons.add_box_outlined),
        label: Text(
          _section == 0
              ? 'إضافة دفعة'
              : (_section == 1 ? 'تسجيل وزن وارد' : 'إضافة مستلم'),
        ),
      ),
      body: Column(
        children: [
          SegmentedStrip(
            tabs: [
              'الدفعات (${ctrl.batches.length})',
              'أوزان الوارد (${ctrl.importWeights.length})',
              'المستلمون والمطابقة (${ctrl.inventoryReceivers.length})',
            ],
            icons: const [
              Icons.inventory_2_outlined,
              Icons.scale_outlined,
              Icons.people_alt_outlined,
            ],
            selectedIndex: _section,
            onChanged: (idx) {
              setState(() => _section = idx);
              if (idx == 2) unawaited(_loadMatching(ctrl));
            },
          ),
          if (_section == 2) ...[
            Expanded(
              child: ListView(
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 84),
                children: [
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(14),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          ErpSectionHeader(
                            title: 'المطابقة اليومية الشاملة للكميات والمبيعات',
                            icon: Icons.compare_arrows_outlined,
                            trailing: IconButton(
                              tooltip: 'تحديث',
                              onPressed: () => _loadMatching(ctrl),
                              icon: const Icon(Icons.refresh, size: 18),
                            ),
                          ),
                          const SizedBox(height: 10),
                          Wrap(
                            spacing: 8,
                            runSpacing: 8,
                            children: [
                              OutlinedButton.icon(
                                onPressed: () async {
                                  final init =
                                      QatDateUtils.parseLegacyDate(_matchFromIso)
                                          .date;
                                  final picked = await showDatePicker(
                                    context: context,
                                    initialDate: init,
                                    firstDate: DateTime(2020),
                                    lastDate: DateTime(DateTime.now().year + 1),
                                  );
                                  if (picked != null && mounted) {
                                    setState(() {
                                      _matchFromIso =
                                          QatDateUtils.toIsoDate(picked);
                                      if (_matchToIso.compareTo(_matchFromIso) <
                                          0) {
                                        _matchToIso = _matchFromIso;
                                      }
                                    });
                                    await _loadMatching(ctrl);
                                  }
                                },
                                icon: const Icon(
                                  Icons.calendar_today_outlined,
                                  size: 16,
                                ),
                                label: Text('من: $_matchFromIso'),
                              ),
                              OutlinedButton.icon(
                                onPressed: () async {
                                  final init =
                                      QatDateUtils.parseLegacyDate(_matchToIso)
                                          .date;
                                  final picked = await showDatePicker(
                                    context: context,
                                    initialDate: init,
                                    firstDate: DateTime(2020),
                                    lastDate: DateTime(DateTime.now().year + 1),
                                  );
                                  if (picked != null && mounted) {
                                    setState(() {
                                      _matchToIso =
                                          QatDateUtils.toIsoDate(picked);
                                    });
                                    await _loadMatching(ctrl);
                                  }
                                },
                                icon: const Icon(
                                  Icons.event_available_outlined,
                                  size: 16,
                                ),
                                label: Text('إلى: $_matchToIso'),
                              ),
                            ],
                          ),
                          const SizedBox(height: 12),
                          if (_loadingMatching)
                            const Padding(
                              padding: EdgeInsets.all(16),
                              child: Center(child: CircularProgressIndicator()),
                            )
                          else if (_matching != null) ...[
                            ErpSummaryBar(
                              metrics: [
                                ErpSummaryMetric(
                                  label: 'وزن الوارد (كجم)',
                                  value: QuantityMath.formatMilli(
                                    _matching!.importedWeightMilli,
                                  ),
                                  toneColor: pal.brand,
                                ),
                                ErpSummaryMetric(
                                  label: 'كمية التوريدات',
                                  value: QuantityMath.formatMilli(
                                    _matching!.purchasedQtyMilli,
                                  ),
                                ),
                                ErpSummaryMetric(
                                  label: 'المباع',
                                  value: QuantityMath.formatMilli(
                                    _matching!.soldQtyMilli,
                                  ),
                                  toneColor: pal.success,
                                ),
                                ErpSummaryMetric(
                                  label: 'المتبقي بالمخزون',
                                  value: QuantityMath.formatMilli(
                                    _matching!.remainingQtyMilli,
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: 10),
                            for (final cur
                                in ctrl.currencyPolicy.allowedCurrencies)
                              if ((_matching!.totalSalesMinorByCurrency[cur] ??
                                          0) >
                                      0 ||
                                  (_matching!.totalPurchasesMinorByCurrency[
                                              cur] ??
                                          0) >
                                      0)
                                Card(
                                  color: pal.cardElevated,
                                  margin: const EdgeInsets.only(bottom: 8),
                                  child: Padding(
                                    padding: const EdgeInsets.all(10),
                                    child: Column(
                                      crossAxisAlignment:
                                          CrossAxisAlignment.start,
                                      children: [
                                        CurrencyBadge(currency: cur),
                                        const SizedBox(height: 6),
                                        ErpDetailRow(
                                          label: 'مبيعات نقدية / تحويل / إيداع',
                                          value:
                                              '${Money(_matching!.cashSalesMinorByCurrency[cur] ?? 0, cur).format()} • ${Money(_matching!.transferSalesMinorByCurrency[cur] ?? 0, cur).format()} • ${Money(_matching!.depositSalesMinorByCurrency[cur] ?? 0, cur).format()}',
                                        ),
                                        ErpDetailRow(
                                          label: 'مبيعات آجلة',
                                          value: Money(
                                            _matching!.creditSalesMinorByCurrency[
                                                    cur] ??
                                                0,
                                            cur,
                                          ).format(),
                                          valueColor: pal.warning,
                                        ),
                                        ErpDetailRow(
                                          label: 'المشتريات / تكلفة المباع',
                                          value:
                                              '${Money(_matching!.totalPurchasesMinorByCurrency[cur] ?? 0, cur).format()} / ${Money(_matching!.estimatedCostSoldMinorByCurrency[cur] ?? 0, cur).format()}',
                                        ),
                                      ],
                                    ),
                                  ),
                                ),
                          ],
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 12),
                  ErpSectionHeader(
                    title: 'دليل مستلمي المخزون (${ctrl.inventoryReceivers.length})',
                    icon: Icons.badge_outlined,
                    trailing: TextButton.icon(
                      onPressed: () => _openReceiverDialog(ctrl),
                      icon: const Icon(Icons.person_add_outlined, size: 17),
                      label: const Text('إضافة مستلم'),
                    ),
                  ),
                  const SizedBox(height: 6),
                  if (ctrl.inventoryReceivers.isEmpty)
                    EmptyLedgerState(
                      icon: Icons.people_outline,
                      title: 'لا يوجد مستلمون مسجلون',
                      subtitle:
                          'أضف أسماء مستلمي المخزون لربط أوزان الوارد والدفعات بهم بدقة',
                      actionLabel: 'إضافة مستلم',
                      onAction: () => _openReceiverDialog(ctrl),
                    )
                  else
                    for (final rec in ctrl.inventoryReceivers)
                      Card(
                        margin: const EdgeInsets.only(bottom: 8),
                        child: ListTile(
                          leading: CircleAvatar(
                            backgroundColor: rec.isActive
                                ? pal.brandSoft
                                : pal.cardElevated,
                            child: Icon(
                              Icons.person_outline,
                              color:
                                  rec.isActive ? pal.brand : pal.textSecondary,
                            ),
                          ),
                          title: Text(
                            rec.name,
                            style: const TextStyle(fontWeight: FontWeight.w700),
                          ),
                          subtitle: Text(
                            [
                              if (rec.phone.isNotEmpty) rec.phone,
                              if (rec.notes.isNotEmpty) rec.notes,
                              if (!rec.isActive) 'غير نشط',
                            ].join(' • '),
                          ),
                          trailing: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              IconButton(
                                tooltip: 'تعديل',
                                icon: const Icon(Icons.edit_outlined, size: 18),
                                onPressed: () =>
                                    _openReceiverDialog(ctrl, existing: rec),
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
                                      title: 'حذف مستلم المخزون',
                                      message:
                                          'هل تريد حذف المستلم ${rec.name}؟ (ستبقى الدفعات المسجلة باسمه محفوظة).',
                                      confirmLabel: 'حذف',
                                      destructive: true,
                                    );
                                    if (!ok) return;
                                    await ctrl.repo
                                        .deleteInventoryReceiver(rec.id);
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
          ] else if (_section == 1) ...[
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
                                        name: PdfExportService
                                            .buildArabicPdfFileName('جرد_المخزون'),
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
