import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../core/money.dart';
import '../../core/platform_bridge.dart';
import '../../core/theme.dart';
import '../../data/models.dart';
import '../../data/qat_repository.dart';
import '../../state/app_controller.dart';
import '../widgets/common_widgets.dart';
import '../widgets/erp_components.dart';

class SuppliersDirectoryView extends StatefulWidget {
  const SuppliersDirectoryView({super.key});

  @override
  State<SuppliersDirectoryView> createState() => _SuppliersDirectoryViewState();
}

class _SuppliersDirectoryViewState extends State<SuppliersDirectoryView> {
  String _search = '';
  bool _onlyPayables = false;

  Future<void> _openSupplierDialog(
    AppController ctrl, {
    Supplier? existing,
  }) async {
    final nameCtrl = TextEditingController(text: existing?.name ?? '');
    final phoneCtrl = TextEditingController(text: existing?.phone ?? '');
    final addrCtrl = TextEditingController(text: existing?.address ?? '');
    final regionCtrl = TextEditingController(text: existing?.region ?? '');
    final notesCtrl = TextEditingController(text: existing?.notes ?? '');

    await showDialog<void>(
      context: context,
      builder: (ctx) => ErpDialogControllerScope(
        controllers: [nameCtrl, phoneCtrl, addrCtrl, regionCtrl, notesCtrl],
        child: AlertDialog(
          title: Text(existing == null ? 'مورد جديد' : 'تعديل بيانات المورد'),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              OutlinedButton.icon(
                onPressed: () async {
                  final picked =
                      await PlatformBridge.instance.pickPhoneContact();
                  if (picked != null) {
                    nameCtrl.text = picked.name;
                    phoneCtrl.text = picked.phone;
                  }
                },
                icon: const Icon(Icons.contacts_outlined, size: 18),
                label: const Text('استيراد من جهات الاتصال'),
              ),
              const SizedBox(height: 8),
              TextField(
                controller: nameCtrl,
                decoration: const InputDecoration(labelText: 'الاسم *'),
              ),
              const SizedBox(height: 8),
              TextField(
                controller: phoneCtrl,
                textDirection: TextDirection.ltr,
                keyboardType: TextInputType.phone,
                decoration: const InputDecoration(labelText: 'الهاتف'),
              ),
              const SizedBox(height: 8),
              TextField(
                controller: regionCtrl,
                decoration: const InputDecoration(labelText: 'المنطقة / المديرية'),
              ),
              const SizedBox(height: 8),
              TextField(
                controller: addrCtrl,
                decoration: const InputDecoration(labelText: 'العنوان التفصيلي'),
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
              final dup = ctrl.suppliers.any(
                (s) =>
                    s.id != existing?.id &&
                    s.name.trim().toLowerCase() == clean.toLowerCase(),
              );
              if (dup) {
                ScaffoldMessenger.of(ctx).showSnackBar(
                  const SnackBar(
                    content: Text('يوجد مورد مسجل بنفس الاسم مسبقاً'),
                  ),
                );
                return;
              }
              await ctrl.repo.upsertSupplier(
                id: existing?.id,
                name: clean,
                phone: phoneCtrl.text,
                address: addrCtrl.text,
                region: regionCtrl.text,
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

  Future<void> _openSupplierReturnDialog(
    AppController ctrl,
    Supplier supplier,
  ) async {
    final amountCtrl = TextEditingController();
    final qtyCtrl = TextEditingController(text: '0');
    final itemCtrl = TextEditingController();
    final notesCtrl = TextEditingController();
    CurrencyCode cur = ctrl.currencyPolicy.defaultCurrency;

    await showDialog<void>(
      context: context,
      builder: (ctx) => ErpDialogControllerScope(
        controllers: [amountCtrl, qtyCtrl, itemCtrl, notesCtrl],
        child: StatefulBuilder(
          builder: (ctx, setModalState) => AlertDialog(
            title: Text('سند مرتجع لمورد — ${supplier.name}'),
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
                  TextField(
                    controller: itemCtrl,
                    decoration: const InputDecoration(
                      labelText: 'الصنف المرتجع (اختياري)',
                    ),
                  ),
                  const SizedBox(height: 8),
                  AccountingNumberField(
                    controller: qtyCtrl,
                    label: 'الكمية المرتجعة',
                  ),
                  const SizedBox(height: 8),
                  AccountingNumberField(
                    controller: amountCtrl,
                    label: 'قيمة المرتجع (${cur.shortSymbol}) *',
                  ),
                  const SizedBox(height: 8),
                  TextField(
                    controller: notesCtrl,
                    decoration: const InputDecoration(labelText: 'سبب المرتجع'),
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
                  final amt = Money.parseMinorUnits(amountCtrl.text);
                  if (amt <= 0) return;
                  await ctrl.repo.addSupplierReturn(
                    supplierId: supplier.id,
                    supplierName: supplier.name,
                    amountMinor: amt,
                    currency: cur,
                    returnedQtyMilli: QuantityMath.parseMilli(qtyCtrl.text),
                    itemName: itemCtrl.text,
                    notes: notesCtrl.text,
                  );
                  await ctrl.reloadAll();
                  if (ctx.mounted) Navigator.pop(ctx);
                },
                child: const Text('اعتماد المرتجع'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _openSupplierPaymentDialog(
    AppController ctrl,
    Supplier supplier,
  ) async {
    final amountCtrl = TextEditingController();
    final bankCtrl = TextEditingController();
    final transferNoCtrl = TextEditingController();
    final notesCtrl = TextEditingController();
    CurrencyCode cur = ctrl.currencyPolicy.defaultCurrency;
    String paymentMethod = 'نقدي';

    await showDialog<void>(
      context: context,
      builder: (ctx) => ErpDialogControllerScope(
        controllers: [amountCtrl, bankCtrl, transferNoCtrl, notesCtrl],
        child: StatefulBuilder(
          builder: (ctx, setModalState) => AlertDialog(
            title: Text('سند صرف لمورد — ${supplier.name}'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                ErpDetailRow(
                  label: 'المستحق للمورد',
                  value: Money(supplier.balanceFor(cur), cur).format(),
                  valueColor: supplier.balanceFor(cur) > 0
                      ? ctx.pal.warning
                      : ctx.pal.success,
                ),
                const SizedBox(height: 8),
                CurrencySelectorRow(
                  policy: ctrl.currencyPolicy,
                  selected: cur,
                  onChanged: (c) => setModalState(() => cur = c),
                ),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 6,
                  children: [
                    for (final m in const ['نقدي', 'تحويل', 'إيداع'])
                      ChoiceChip(
                        label: Text(m),
                        selected: paymentMethod == m,
                        onSelected: (_) =>
                            setModalState(() => paymentMethod = m),
                      ),
                  ],
                ),
                const SizedBox(height: 10),
                AccountingNumberField(
                  controller: amountCtrl,
                  label: 'المبلغ المدفوع (${cur.shortSymbol})',
                ),
                if (paymentMethod != 'نقدي') ...[
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      Expanded(
                        child: TextField(
                          controller: bankCtrl,
                          decoration: const InputDecoration(
                            labelText: 'البنك / الصرافة',
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: TextField(
                          controller: transferNoCtrl,
                          textDirection: TextDirection.ltr,
                          decoration: const InputDecoration(
                            labelText: 'رقم الحوالة / الإيداع',
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
                const SizedBox(height: 10),
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
                if (minor <= 0) return;
                await ctrl.repo.addSupplierPayment(
                  supplierId: supplier.id,
                  supplierName: supplier.name,
                  amountMinor: minor,
                  currency: cur,
                  paymentMethod: paymentMethod,
                  bankName: bankCtrl.text,
                  transferNumber: transferNoCtrl.text,
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
      ),
    );
  }

  Future<void> _openNewPurchaseDialog(
    AppController ctrl,
    Supplier supplier,
  ) async {
    final qatCtrl = TextEditingController();
    final qtyCtrl = TextEditingController(text: '10');
    final buyCtrl = TextEditingController();
    final sellCtrl = TextEditingController();
    final paidCtrl = TextEditingController(text: '0');
    final bankCtrl = TextEditingController();
    final transferNoCtrl = TextEditingController();
    final demarAmountCtrl = TextEditingController(text: '0');
    final demarTypeCtrl = TextEditingController();
    final demarNoCtrl = TextEditingController();
    final notesCtrl = TextEditingController();
    CurrencyCode cur = ctrl.currencyPolicy.defaultCurrency;
    PaymentType pType = PaymentType.credit;
    String paymentMethod = 'نقداً';

    await showDialog<void>(
      context: context,
      builder: (ctx) => ErpDialogControllerScope(
        controllers: [
          qatCtrl,
          qtyCtrl,
          buyCtrl,
          sellCtrl,
          paidCtrl,
          bankCtrl,
          transferNoCtrl,
          demarAmountCtrl,
          demarTypeCtrl,
          demarNoCtrl,
          notesCtrl,
        ],
        child: StatefulBuilder(
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
                if (ctrl.qatTypes.isNotEmpty) ...[
                  DropdownButtonFormField<String>(
                    initialValue: null,
                    isExpanded: true,
                    decoration: const InputDecoration(
                      labelText: 'اختيار من دليل الأصناف (اختياري)',
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
                      final qt = ctrl.qatTypes.firstWhere((e) => e.name == v);
                      qatCtrl.text = qt.name;
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
                ],
                TextField(
                  controller: qatCtrl,
                  decoration: const InputDecoration(labelText: 'الصنف *'),
                ),
                const SizedBox(height: 8),
                AccountingNumberField(
                  controller: qtyCtrl,
                  label: 'الكمية (ربطة)',
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
                        label: 'سعر البيع المقترح',
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
                if (pType != PaymentType.credit) ...[
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 6,
                    children: [
                      for (final m in const ['نقداً', 'تحويل', 'إيداع'])
                        ChoiceChip(
                          label: Text(m),
                          selected: paymentMethod == m,
                          onSelected: (_) =>
                              setModalState(() => paymentMethod = m),
                        ),
                    ],
                  ),
                ],
                if (pType == PaymentType.partial || pType == PaymentType.paid) ...[
                  const SizedBox(height: 8),
                  AccountingNumberField(
                    controller: paidCtrl,
                    label: pType == PaymentType.paid
                        ? 'المدفوع الفعلي (أو اترك 0 لاحتساب الإجمالي)'
                        : 'المدفوع مقدماً',
                  ),
                ],
                if (pType != PaymentType.credit && paymentMethod != 'نقداً') ...[
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      Expanded(
                        child: TextField(
                          controller: bankCtrl,
                          decoration: const InputDecoration(
                            labelText: 'البنك / الصرافة',
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: TextField(
                          controller: transferNoCtrl,
                          textDirection: TextDirection.ltr,
                          decoration: const InputDecoration(
                            labelText: 'رقم الحوالة',
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
                const SizedBox(height: 8),
                AccountingNumberField(
                  controller: demarAmountCtrl,
                  label: 'مبلغ الدمار (اختياري)',
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Expanded(
                      child: TextField(
                        controller: demarTypeCtrl,
                        decoration: const InputDecoration(
                          labelText: 'نوع الدمار (إن وجد)',
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: TextField(
                        controller: demarNoCtrl,
                        textDirection: TextDirection.ltr,
                        decoration: const InputDecoration(
                          labelText: 'رقم الدمار',
                        ),
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
                  paymentMethod:
                      pType == PaymentType.credit ? 'آجل' : paymentMethod,
                  bankName: bankCtrl.text,
                  transferNumber: transferNoCtrl.text,
                  demarMinor: Money.parseMinorUnits(demarAmountCtrl.text),
                  demarType: demarTypeCtrl.text,
                  demarNumber: demarNoCtrl.text,
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
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final pal = context.pal;

    final list = ctrl.suppliers.where((s) {
      final yer = s.balanceFor(CurrencyCode.yer);
      final sar = s.balanceFor(CurrencyCode.sar);
      if (_onlyPayables && yer <= 0 && sar <= 0) return false;
      if (_search.isEmpty) return true;
      final q = _search.toLowerCase();
      return s.name.toLowerCase().contains(q) || s.phone.contains(q);
    }).toList();

    final summaryMetrics = <ErpSummaryMetric>[
      ErpSummaryMetric(label: 'الموردون', value: '${list.length}'),
    ];
    for (final cur in ctrl.currencyPolicy.allowedCurrencies) {
      final totPay = list.fold<int>(
        0,
        (sum, s) => sum + (s.balanceFor(cur) > 0 ? s.balanceFor(cur) : 0),
      );
      summaryMetrics.add(
        ErpSummaryMetric(
          label: 'مستحقات ${cur.code}',
          value: Money(totPay, cur).format(),
          toneColor: totPay > 0 ? pal.warning : pal.success,
        ),
      );
    }

    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _openSupplierDialog(ctrl),
        icon: const Icon(Icons.person_add_outlined),
        label: const Text('مورد جديد'),
      ),
      body: Column(
        children: [
          ErpSearchFilterBar(
            searchHint: 'بحث عن مورد بالاسم أو الهاتف...',
            onSearchChanged: (v) => setState(() => _search = v.trim()),
            filters: [
              ChoiceChip(
                label: const Text('الكل'),
                selected: !_onlyPayables,
                onSelected: (_) => setState(() => _onlyPayables = false),
              ),
              ChoiceChip(
                label: const Text('لهم مستحقات فقط'),
                selected: _onlyPayables,
                onSelected: (_) => setState(() => _onlyPayables = true),
              ),
            ],
          ),
          if (ctrl.suppliers.isNotEmpty)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 10, 16, 0),
              child: ErpSummaryBar(metrics: summaryMetrics),
            ),
          Expanded(
            child: list.isEmpty
                ? const EmptyLedgerState(
                    icon: Icons.local_shipping_outlined,
                    title: 'لا يوجد موردون مطابقون',
                    subtitle: 'أضف الموردين لتسجيل فواتير الشراء والمستحقات',
                  )
                : ListView.separated(
                    padding: const EdgeInsets.fromLTRB(16, 12, 16, 84),
                    itemCount: list.length,
                    separatorBuilder: (_, idx) => const SizedBox(height: 10),
                    itemBuilder: (_, idx) {
                      final s = list[idx];
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
                                    child: Text(
                                      s.name,
                                      style: const TextStyle(
                                        fontWeight: FontWeight.w700,
                                        fontSize: 14.5,
                                      ),
                                    ),
                                  ),
                                  IconButton(
                                    onPressed: () =>
                                        _openSupplierDialog(ctrl, existing: s),
                                    icon: const Icon(
                                      Icons.edit_outlined,
                                      size: 18,
                                    ),
                                  ),
                                  if (ctrl.canDeleteCurrent)
                                    IconButton(
                                      tooltip: 'حذف المورد',
                                      onPressed: () async {
                                        final ok = await showErpConfirmDialog(
                                          context,
                                          title: 'حذف المورد',
                                          message:
                                              'هل تريد حذف المورد ${s.name}؟',
                                          confirmLabel: 'حذف',
                                          destructive: true,
                                        );
                                        if (!ok) return;
                                        try {
                                          await ctrl.repo.deleteSupplier(s.id);
                                          await ctrl.reloadAll();
                                        } catch (e) {
                                          if (!context.mounted) return;
                                          ScaffoldMessenger.of(context)
                                              .showSnackBar(
                                            SnackBar(
                                              content: Text(e.toString()),
                                            ),
                                          );
                                        }
                                      },
                                      icon: Icon(
                                        Icons.delete_outline,
                                        color: pal.danger,
                                        size: 18,
                                      ),
                                    ),
                                ],
                              ),
                              if (s.phone.isNotEmpty || s.region.isNotEmpty)
                                Text(
                                  [
                                    if (s.phone.isNotEmpty) s.phone,
                                    if (s.region.isNotEmpty) s.region,
                                  ].join(' • '),
                                  style: TextStyle(
                                    fontSize: 12,
                                    color: pal.textSecondary,
                                  ),
                                ),
                              const SizedBox(height: 8),
                              Wrap(
                                spacing: 8,
                                children: [
                                  StatusPill(
                                    label:
                                        'YER: ${Money(yerBal, CurrencyCode.yer).format()}',
                                    bg: yerBal > 0
                                        ? pal.warningSoft
                                        : pal.successSoft,
                                    fg: yerBal > 0
                                        ? pal.warning
                                        : pal.success,
                                  ),
                                  if (sarBal != 0 ||
                                      ctrl.currencyPolicy.canSwitchCurrency)
                                    StatusPill(
                                      label:
                                          'SAR: ${Money(sarBal, CurrencyCode.sar).format()}',
                                      bg: sarBal > 0
                                          ? pal.warningSoft
                                          : pal.successSoft,
                                      fg: sarBal > 0
                                          ? pal.warning
                                          : pal.success,
                                    ),
                                ],
                              ),
                              const SizedBox(height: 10),
                              Wrap(
                                spacing: 8,
                                runSpacing: 8,
                                children: [
                                  FilledButton.tonalIcon(
                                    onPressed: () =>
                                        _openNewPurchaseDialog(ctrl, s),
                                    icon: const Icon(
                                      Icons.add_shopping_cart,
                                      size: 16,
                                    ),
                                    label: const Text('فاتورة شراء'),
                                  ),
                                  OutlinedButton.icon(
                                    onPressed: () =>
                                        _openSupplierPaymentDialog(ctrl, s),
                                    icon: const Icon(
                                      Icons.payments_outlined,
                                      size: 16,
                                    ),
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
                                                ? const Text(
                                                    'لا توجد سندات صرف مسجلة.',
                                                  )
                                                : ListView.separated(
                                                    shrinkWrap: true,
                                                    itemCount: payments.length,
                                                    separatorBuilder:
                                                        (_, idx) =>
                                                            const Divider(
                                                      height: 1,
                                                    ),
                                                    itemBuilder: (_, pi) {
                                                      final pay = payments[pi];
                                                      return ListTile(
                                                        dense: true,
                                                        title: Text(
                                                          Money(
                                                            pay.amountMinor,
                                                            pay.currency,
                                                          ).format(),
                                                          style:
                                                              const TextStyle(
                                                            fontWeight:
                                                                FontWeight.w700,
                                                          ),
                                                        ),
                                                        subtitle: Text(
                                                          '${pay.dateIso} • ${pay.paymentMethod} ${pay.notes}',
                                                        ),
                                                        trailing: ctrl
                                                                .canDeleteCurrent
                                                            ? IconButton(
                                                                icon: const Icon(
                                                                  Icons
                                                                      .delete_outline,
                                                                  size: 18,
                                                                ),
                                                                onPressed:
                                                                    () async {
                                                                  final ok =
                                                                      await showErpConfirmDialog(
                                                                    ctx,
                                                                    title:
                                                                        'حذف السند',
                                                                    message:
                                                                        'سيتم حذف سند الصرف وعكس أثره على حساب المورد.',
                                                                    confirmLabel:
                                                                        'حذف',
                                                                    destructive:
                                                                        true,
                                                                  );
                                                                  if (!ok) {
                                                                    return;
                                                                  }
                                                                  await ctrl
                                                                      .repo
                                                                      .deleteSupplierPayment(
                                                                    pay.id,
                                                                  );
                                                                  await ctrl
                                                                      .reloadAll();
                                                                  if (ctx
                                                                      .mounted) {
                                                                    Navigator
                                                                        .pop(
                                                                      ctx,
                                                                    );
                                                                  }
                                                                },
                                                              )
                                                            : null,
                                                      );
                                                    },
                                                  ),
                                          ),
                                          actions: [
                                            TextButton(
                                              onPressed: () =>
                                                  Navigator.pop(ctx),
                                              child: const Text('إغلاق'),
                                            ),
                                          ],
                                        ),
                                      );
                                    },
                                    icon: const Icon(
                                      Icons.receipt_long_outlined,
                                      size: 16,
                                    ),
                                    label: const Text('السندات'),
                                  ),
                                  OutlinedButton.icon(
                                    onPressed: () =>
                                        _openSupplierReturnDialog(ctrl, s),
                                    icon: const Icon(
                                      Icons.assignment_return_outlined,
                                      size: 16,
                                    ),
                                    label: const Text('مرتجع'),
                                  ),
                                  OutlinedButton.icon(
                                    onPressed: () =>
                                        ctrl.openStatementForSupplier(s.id),
                                    icon: const Icon(
                                      Icons.table_chart_outlined,
                                      size: 16,
                                    ),
                                    label: const Text('كشف الحساب'),
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
      ),
    );
  }
}
