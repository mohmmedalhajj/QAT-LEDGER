import 'dart:async';

import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../core/money.dart';
import '../../core/platform_bridge.dart';
import '../../core/theme.dart';
import '../../data/models.dart';
import '../../services/esc_pos_service.dart';
import '../../services/whatsapp_share_service.dart';
import '../../state/app_controller.dart';
import '../widgets/common_widgets.dart';
import '../widgets/erp_components.dart';

enum _CustomerFilter { all, withDebt, settled }

enum _CustomerSort { highestDebt, alphabetical, newest }

class CustomersDirectoryView extends StatefulWidget {
  const CustomersDirectoryView({super.key, this.onOpenStatement});

  final ValueChanged<int>? onOpenStatement;

  @override
  State<CustomersDirectoryView> createState() => _CustomersDirectoryViewState();
}

class _CustomersDirectoryViewState extends State<CustomersDirectoryView> {
  String _search = '';
  _CustomerFilter _filter = _CustomerFilter.all;
  _CustomerSort _sort = _CustomerSort.highestDebt;

  Future<void> _openCustomerDialog(
    AppController ctrl, {
    Customer? existing,
  }) async {
    final nameCtrl = TextEditingController(text: existing?.name ?? '');
    final phoneCtrl = TextEditingController(text: existing?.phone ?? '');
    final addrCtrl = TextEditingController(text: existing?.address ?? '');
    final notesCtrl = TextEditingController(text: existing?.notes ?? '');

    await showDialog<void>(
      context: context,
      builder: (ctx) => ErpDialogControllerScope(
        controllers: [nameCtrl, phoneCtrl, addrCtrl, notesCtrl],
        child: AlertDialog(
          title: Text(existing == null ? 'إضافة عميل جديد' : 'تعديل بيانات العميل'),
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
              const SizedBox(height: 10),
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
                controller: addrCtrl,
                decoration: const InputDecoration(labelText: 'العنوان'),
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
              final cleanName = nameCtrl.text.trim();
              if (cleanName.isEmpty) return;
              final duplicate = ctrl.customers.any(
                (c) =>
                    c.id != existing?.id &&
                    c.name.trim().toLowerCase() == cleanName.toLowerCase(),
              );
              if (duplicate) {
                ScaffoldMessenger.of(ctx).showSnackBar(
                  const SnackBar(
                    content: Text('يوجد عميل مسجل بنفس الاسم مسبقاً'),
                  ),
                );
                return;
              }
              await ctrl.repo.upsertCustomer(
                id: existing?.id,
                name: cleanName,
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
      ),
    );
  }

  Future<void> _openCustomerReceiptsDialog(
    AppController ctrl,
    Customer customer,
  ) async {
    final receipts =
        ctrl.customerPayments.where((p) => p.customerId == customer.id).toList();
    await showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('سندات قبض — ${customer.name}'),
        content: SizedBox(
          width: double.maxFinite,
          child: receipts.isEmpty
              ? const Text('لا توجد سندات قبض مسجلة لهذا العميل.')
              : ListView.separated(
                  shrinkWrap: true,
                  itemCount: receipts.length,
                  separatorBuilder: (_, idx) => const Divider(height: 1),
                  itemBuilder: (_, idx) {
                    final r = receipts[idx];
                    return ListTile(
                      dense: true,
                      title: Text(
                        Money(r.amountMinor, r.currency).format(),
                        style: const TextStyle(
                          fontWeight: FontWeight.w700,
                          fontFeatures: [FontFeature.tabularFigures()],
                        ),
                      ),
                      subtitle:
                          Text('${r.dateIso} • ${r.paymentMethod} ${r.notes}'),
                      trailing: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          IconButton(
                            tooltip: 'معاينة / طباعة السند',
                            icon: const Icon(Icons.print_outlined, size: 18),
                            onPressed: () async {
                              final txt = ThermalReceiptFormatter
                                  .buildCustomerPaymentReceiptText(
                                ctrl.settings,
                                customer: customer,
                                amountMinor: r.amountMinor,
                                currency: r.currency,
                                paymentMethod: r.paymentMethod,
                                dateMs: r.dateMs,
                                notes: r.notes,
                              );
                              await showReceiptPreviewPrintSheet(
                                ctx,
                                agency: ctrl.settings,
                                title: 'سند قبض #${r.id}',
                                receiptText: txt,
                                whatsappPhone: customer.phone,
                              );
                            },
                          ),
                          IconButton(
                            tooltip: 'تعديل السند',
                            icon: const Icon(Icons.edit_outlined, size: 18),
                            onPressed: () async {
                              await _openEditPaymentDialog(ctrl, customer, r);
                              if (ctx.mounted) Navigator.pop(ctx);
                            },
                          ),
                          if (ctrl.canDeleteCurrent)
                            IconButton(
                              tooltip: 'حذف السند',
                              icon: const Icon(Icons.delete_outline, size: 18),
                              onPressed: () async {
                                final ok = await showErpConfirmDialog(
                                  ctx,
                                  title: 'حذف سند القبض',
                                  message:
                                      'سيتم حذف سند القبض وعكس أثره على دين العميل. هل أنت متأكد؟',
                                  confirmLabel: 'حذف',
                                  destructive: true,
                                );
                                if (!ok) return;
                                await ctrl.repo.deleteCustomerPayment(r.id);
                                await ctrl.reloadAll();
                                if (ctx.mounted) Navigator.pop(ctx);
                              },
                            ),
                        ],
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
  }

  Future<void> _openEditPaymentDialog(
    AppController ctrl,
    Customer customer,
    CustomerPayment payment,
  ) async {
    final amountCtrl = TextEditingController(
      text: Money.formatMinor(payment.amountMinor, showZeroDecimals: false)
          .replaceAll(',', ''),
    );
    final notesCtrl = TextEditingController(text: payment.notes);
    CurrencyCode selectedCur = payment.currency;
    String paymentMethod =
        payment.paymentMethod.isNotEmpty ? payment.paymentMethod : 'نقدي';

    await showDialog<void>(
      context: context,
      builder: (ctx) => ErpDialogControllerScope(
        controllers: [amountCtrl, notesCtrl],
        child: StatefulBuilder(
          builder: (ctx, setModalState) => AlertDialog(
            title: Text('تعديل سند قبض — ${customer.name}'),
            content: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  CurrencySelectorRow(
                    policy: ctrl.currencyPolicy,
                    selected: selectedCur,
                    onChanged: (c) => setModalState(() => selectedCur = c),
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
                    label: 'المبلغ (${selectedCur.shortSymbol})',
                  ),
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
                  await ctrl.repo.updateCustomerPayment(
                    paymentId: payment.id,
                    amountMinor: minor,
                    currency: selectedCur,
                    paymentMethod: paymentMethod,
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
      ),
    );
  }

  Future<void> _openDirectDebtDialog(
    AppController ctrl,
    Customer customer,
  ) async {
    final amountCtrl = TextEditingController();
    final statementCtrl = TextEditingController(text: 'دين مباشر');
    final refCtrl = TextEditingController();
    final notesCtrl = TextEditingController();
    CurrencyCode selectedCur = ctrl.currencyPolicy.defaultCurrency;
    int debtDateMs = DateTime.now().millisecondsSinceEpoch;

    await showDialog<void>(
      context: context,
      builder: (ctx) => ErpDialogControllerScope(
        controllers: [amountCtrl, statementCtrl, refCtrl, notesCtrl],
        child: StatefulBuilder(
          builder: (ctx, setModalState) => AlertDialog(
            title: Text('تسجيل دين مباشر — ${customer.name}'),
            content: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  ErpDetailRow(
                    label: 'الدين القائم حالياً',
                    value: Money(customer.balanceFor(selectedCur), selectedCur)
                        .format(),
                    valueColor: customer.balanceFor(selectedCur) > 0
                        ? ctx.pal.danger
                        : ctx.pal.success,
                  ),
                  const SizedBox(height: 8),
                  ErpDatePickerTile(
                    label: 'تاريخ الدين',
                    dateMs: debtDateMs,
                    onChanged: (ms) => setModalState(() => debtDateMs = ms),
                  ),
                  const SizedBox(height: 8),
                  CurrencySelectorRow(
                    policy: ctrl.currencyPolicy,
                    selected: selectedCur,
                    onChanged: (c) => setModalState(() => selectedCur = c),
                  ),
                  const SizedBox(height: 10),
                  AccountingNumberField(
                    controller: amountCtrl,
                    label: 'مبلغ الدين (${selectedCur.shortSymbol}) *',
                  ),
                  const SizedBox(height: 8),
                  TextField(
                    controller: statementCtrl,
                    decoration: const InputDecoration(labelText: 'البيان / السبب'),
                  ),
                  const SizedBox(height: 8),
                  TextField(
                    controller: refCtrl,
                    textDirection: TextDirection.ltr,
                    decoration:
                        const InputDecoration(labelText: 'رقم مرجعي (اختياري)'),
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
                  if (minor <= 0) {
                    ScaffoldMessenger.of(ctx).showSnackBar(
                      const SnackBar(
                        content: Text('أدخل مبلغ دين أكبر من الصفر'),
                      ),
                    );
                    return;
                  }
                  await ctrl.repo.addDirectCustomerDebt(
                    customerId: customer.id,
                    customerName: customer.name,
                    amountMinor: minor,
                    currency: selectedCur,
                    statement: statementCtrl.text,
                    referenceNumber: refCtrl.text,
                    notes: notesCtrl.text,
                    dateMs: debtDateMs,
                  );
                  await ctrl.reloadAll();
                  if (ctx.mounted) Navigator.pop(ctx);
                },
                child: const Text('اعتماد الدين'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _openGroupSmsDialog(
    AppController ctrl,
    List<Customer> visibleList,
  ) async {
    final debtorsWithPhone = visibleList
        .where(
          (c) =>
              c.phone.trim().isNotEmpty &&
              (c.balanceFor(CurrencyCode.yer) > 0 ||
                  c.balanceFor(CurrencyCode.sar) > 0),
        )
        .toList();
    if (debtorsWithPhone.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('لا يوجد عملاء مدينون بأرقام هواتف مسجلة في القائمة الحالية'),
        ),
      );
      return;
    }

    final msgCtrl = TextEditingController(
      text:
          'تحية طيبة من ${ctrl.settings.agencyName}، نذكركم بمراجعة رصيد حسابكم القائم لدينا. للاستفسار: ${ctrl.settings.agencyPhone}',
    );
    await showDialog<void>(
      context: context,
      builder: (ctx) => ErpDialogControllerScope(
        controllers: [msgCtrl],
        child: AlertDialog(
          title: Text('رسالة جماعية للديون (${debtorsWithPhone.length} عميل)'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                'سيتم فتح تطبيق الرسائل القصيرة (SMS) لإرسال التذكير دفعة واحدة لـ ${debtorsWithPhone.length} عميل:',
                style: Theme.of(ctx).textTheme.bodySmall,
              ),
              const SizedBox(height: 8),
              TextField(
                controller: msgCtrl,
                maxLines: 3,
                decoration: const InputDecoration(labelText: 'نص الرسالة الجماعية'),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('إلغاء'),
            ),
            FilledButton.icon(
              onPressed: () async {
                final ok = await WhatsappShareService.instance.openGroupSms(
                  phones: debtorsWithPhone.map((e) => e.phone).toList(),
                  text: msgCtrl.text.trim(),
                );
                if (!ctx.mounted) return;
                Navigator.pop(ctx);
                if (!ok && mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('تعذر فتح تطبيق الرسائل على هذا الجهاز')),
                  );
                }
              },
              icon: const Icon(Icons.sms_outlined, size: 18),
              label: const Text('إرسال SMS جماعي'),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _openPaymentDialog(AppController ctrl, Customer customer) async {
    final amountCtrl = TextEditingController();
    final notesCtrl = TextEditingController();
    CurrencyCode selectedCur = ctrl.currencyPolicy.defaultCurrency;
    String paymentMethod = 'نقدي';
    int payDateMs = DateTime.now().millisecondsSinceEpoch;

    await showDialog<void>(
      context: context,
      builder: (ctx) => ErpDialogControllerScope(
        controllers: [amountCtrl, notesCtrl],
        child: StatefulBuilder(
          builder: (ctx, setModalState) => AlertDialog(
            title: Text('سند قبض — ${customer.name}'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                ErpDetailRow(
                  label: 'الرصيد القائم حالياً',
                  value: Money(customer.balanceFor(selectedCur), selectedCur)
                      .format(),
                  valueColor: customer.balanceFor(selectedCur) > 0
                      ? ctx.pal.danger
                      : ctx.pal.success,
                ),
                const SizedBox(height: 8),
                ErpDatePickerTile(
                  label: 'تاريخ السند',
                  dateMs: payDateMs,
                  onChanged: (ms) => setModalState(() => payDateMs = ms),
                ),
                const SizedBox(height: 8),
                CurrencySelectorRow(
                  policy: ctrl.currencyPolicy,
                  selected: selectedCur,
                  onChanged: (c) => setModalState(() => selectedCur = c),
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
                  label: 'المبلغ المقبوض (${selectedCur.shortSymbol})',
                ),
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
                if (minor <= 0) {
                  ScaffoldMessenger.of(ctx).showSnackBar(
                    const SnackBar(content: Text('أدخل مبلغاً أكبر من الصفر')),
                  );
                  return;
                }
                final noteSnapshot = notesCtrl.text;
                await ctrl.repo.addCustomerPayment(
                  customerId: customer.id,
                  customerName: customer.name,
                  amountMinor: minor,
                  currency: selectedCur,
                  paymentMethod: paymentMethod,
                  notes: noteSnapshot,
                  dateMs: payDateMs,
                );
                await ctrl.reloadAll();
                if (!ctx.mounted) return;
                Navigator.pop(ctx);
                final updated = ctrl.customers.firstWhere(
                  (e) => e.id == customer.id,
                  orElse: () => customer,
                );
                if (!mounted) return;
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(
                    content: const Text('تم حفظ سند القبض بنجاح'),
                    action: SnackBarAction(
                      label: 'طباعة السند',
                      onPressed: () {
                        if (!mounted) return;
                        final txt = ThermalReceiptFormatter
                            .buildCustomerPaymentReceiptText(
                          ctrl.settings,
                          customer: updated,
                          amountMinor: minor,
                          currency: selectedCur,
                          paymentMethod: paymentMethod,
                          dateMs: payDateMs,
                          notes: noteSnapshot,
                        );
                        unawaited(
                          showReceiptPreviewPrintSheet(
                            context,
                            agency: ctrl.settings,
                            title: 'سند قبض — ${updated.name}',
                            receiptText: txt,
                            whatsappPhone: updated.phone,
                          ),
                        );
                      },
                    ),
                  ),
                );
              },
              child: const Text('حفظ السند'),
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
    final list = ctrl.customers.where((c) {
      final yer = c.balanceFor(CurrencyCode.yer);
      final sar = c.balanceFor(CurrencyCode.sar);
      if (_filter == _CustomerFilter.withDebt && yer <= 0 && sar <= 0) {
        return false;
      }
      if (_filter == _CustomerFilter.settled && (yer > 0 || sar > 0)) {
        return false;
      }
      if (_search.isEmpty) return true;
      final q = _search.toLowerCase();
      return c.name.toLowerCase().contains(q) ||
          c.phone.contains(q) ||
          c.address.toLowerCase().contains(q);
    }).toList();

    switch (_sort) {
      case _CustomerSort.highestDebt:
        list.sort((a, b) {
          final da = a.balanceFor(CurrencyCode.yer) +
              a.balanceFor(CurrencyCode.sar);
          final db = b.balanceFor(CurrencyCode.yer) +
              b.balanceFor(CurrencyCode.sar);
          return db.compareTo(da);
        });
      case _CustomerSort.alphabetical:
        list.sort((a, b) => a.name.compareTo(b.name));
      case _CustomerSort.newest:
        list.sort((a, b) => b.createdAtMs.compareTo(a.createdAtMs));
    }

    final summaryMetrics = <ErpSummaryMetric>[
      ErpSummaryMetric(label: 'العملاء', value: '${list.length}'),
    ];
    for (final cur in ctrl.currencyPolicy.allowedCurrencies) {
      final totalDebt = list.fold<int>(
        0,
        (sum, c) => sum + (c.balanceFor(cur) > 0 ? c.balanceFor(cur) : 0),
      );
      summaryMetrics.add(
        ErpSummaryMetric(
          label: 'ديون ${cur.code}',
          value: Money(totalDebt, cur).format(),
          toneColor: totalDebt > 0 ? pal.warning : pal.success,
        ),
      );
    }

    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _openCustomerDialog(ctrl),
        icon: const Icon(Icons.person_add_alt_1),
        label: const Text('عميل جديد'),
      ),
      body: Column(
        children: [
          ErpSearchFilterBar(
            searchHint: 'بحث عن عميل بالاسم، الهاتف، أو العنوان...',
            onSearchChanged: (v) => setState(() => _search = v.trim()),
            trailingAction: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                IconButton(
                  tooltip: 'رسالة SMS جماعية للمدينين',
                  icon: const Icon(Icons.campaign_outlined),
                  onPressed: () => _openGroupSmsDialog(ctrl, list),
                ),
                PopupMenuButton<_CustomerSort>(
                  tooltip: 'ترتيب العملاء',
                  icon: const Icon(Icons.sort),
                  onSelected: (s) => setState(() => _sort = s),
                  itemBuilder: (_) => const [
                    PopupMenuItem<_CustomerSort>(
                      value: _CustomerSort.highestDebt,
                      child: Text('الأعلى مديونية'),
                    ),
                    PopupMenuItem<_CustomerSort>(
                      value: _CustomerSort.alphabetical,
                      child: Text('ترتيب أبجدي'),
                    ),
                    PopupMenuItem<_CustomerSort>(
                      value: _CustomerSort.newest,
                      child: Text('الأحدث إضافة'),
                    ),
                  ],
                ),
              ],
            ),
            filters: [
              ChoiceChip(
                label: const Text('الكل'),
                selected: _filter == _CustomerFilter.all,
                onSelected: (_) => setState(() => _filter = _CustomerFilter.all),
              ),
              ChoiceChip(
                label: const Text('مديونون فقط'),
                selected: _filter == _CustomerFilter.withDebt,
                onSelected: (_) =>
                    setState(() => _filter = _CustomerFilter.withDebt),
              ),
              ChoiceChip(
                label: const Text('مسدد / بدون دين'),
                selected: _filter == _CustomerFilter.settled,
                onSelected: (_) =>
                    setState(() => _filter = _CustomerFilter.settled),
              ),
            ],
          ),
          if (ctrl.customers.isNotEmpty)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 10, 16, 0),
              child: ErpSummaryBar(metrics: summaryMetrics),
            ),
          Expanded(
            child: list.isEmpty
                ? const EmptyLedgerState(
                    icon: Icons.people_outline,
                    title: 'لا يوجد عملاء مطابقون',
                    subtitle: 'أضف عملاءك لإدارة الفواتير الآجلة وسندات القبض',
                  )
                : ListView.separated(
                    padding: const EdgeInsets.fromLTRB(16, 10, 16, 84),
                    itemCount: list.length,
                    separatorBuilder: (_, idx) => const SizedBox(height: 10),
                    itemBuilder: (_, idx) {
                      final c = list[idx];
                      final yerDebt = c.balanceFor(CurrencyCode.yer);
                      final sarDebt = c.balanceFor(CurrencyCode.sar);
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
                                      c.name,
                                      style: const TextStyle(
                                        fontWeight: FontWeight.w700,
                                        fontSize: 14.5,
                                      ),
                                    ),
                                  ),
                                  IconButton(
                                    tooltip: 'تعديل',
                                    onPressed: () =>
                                        _openCustomerDialog(ctrl, existing: c),
                                    icon: const Icon(
                                      Icons.edit_outlined,
                                      size: 18,
                                    ),
                                  ),
                                  if (ctrl.canDeleteCurrent)
                                    IconButton(
                                      tooltip: 'حذف العميل',
                                      onPressed: () async {
                                        final ok = await showErpConfirmDialog(
                                          context,
                                          title: 'حذف العميل',
                                          message:
                                              'هل تريد حذف العميل ${c.name}؟ (لا يمكن الحذف إذا كانت له حركات مالية).',
                                          confirmLabel: 'حذف',
                                          destructive: true,
                                        );
                                        if (!ok) return;
                                        try {
                                          await ctrl.repo.deleteCustomer(c.id);
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
                              if (c.phone.isNotEmpty || c.address.isNotEmpty)
                                Text(
                                  [
                                    if (c.phone.isNotEmpty) c.phone,
                                    if (c.address.isNotEmpty) c.address,
                                  ].join(' • '),
                                  style: TextStyle(
                                    color: pal.textSecondary,
                                    fontSize: 12,
                                  ),
                                ),
                              const SizedBox(height: 8),
                              Wrap(
                                spacing: 8,
                                runSpacing: 6,
                                children: [
                                  StatusPill(
                                    label:
                                        'YER: ${Money(yerDebt, CurrencyCode.yer).format()}',
                                    bg: yerDebt > 0
                                        ? pal.warningSoft
                                        : pal.successSoft,
                                    fg: yerDebt > 0
                                        ? pal.warning
                                        : pal.success,
                                  ),
                                  if (sarDebt != 0 ||
                                      ctrl.currencyPolicy.canSwitchCurrency)
                                    StatusPill(
                                      label:
                                          'SAR: ${Money(sarDebt, CurrencyCode.sar).format()}',
                                      bg: sarDebt > 0
                                          ? pal.warningSoft
                                          : pal.successSoft,
                                      fg: sarDebt > 0
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
                                        _openPaymentDialog(ctrl, c),
                                    icon: const Icon(
                                      Icons.payments_outlined,
                                      size: 16,
                                    ),
                                    label: const Text('سند قبض'),
                                  ),
                                  OutlinedButton.icon(
                                    onPressed: () =>
                                        _openDirectDebtDialog(ctrl, c),
                                    icon: const Icon(
                                      Icons.post_add_outlined,
                                      size: 16,
                                    ),
                                    label: const Text('دين مباشر'),
                                  ),
                                  OutlinedButton.icon(
                                    onPressed: () =>
                                        _openCustomerReceiptsDialog(ctrl, c),
                                    icon: const Icon(
                                      Icons.receipt_long_outlined,
                                      size: 16,
                                    ),
                                    label: const Text('السندات'),
                                  ),
                                  if (widget.onOpenStatement != null)
                                    OutlinedButton.icon(
                                      onPressed: () =>
                                          widget.onOpenStatement!(c.id),
                                      icon: const Icon(
                                        Icons.table_chart_outlined,
                                        size: 16,
                                      ),
                                      label: const Text('كشف الحساب'),
                                    ),
                                  if (c.phone.isNotEmpty) ...[
                                    OutlinedButton.icon(
                                      onPressed: () async =>
                                          WhatsappShareService.instance
                                              .openWhatsApp(
                                        phone: c.phone,
                                        text:
                                            'مرحباً ${c.name}، رصيدكم الحالي لدى ${ctrl.settings.agencyName}:\n'
                                            'ريال يمني: ${Money(yerDebt, CurrencyCode.yer).format()}'
                                            '${sarDebt != 0 ? '\nريال سعودي: ${Money(sarDebt, CurrencyCode.sar).format()}' : ''}',
                                      ),
                                      icon: const Icon(
                                        Icons.chat_outlined,
                                        size: 16,
                                      ),
                                      label: const Text('واتساب'),
                                    ),
                                    OutlinedButton.icon(
                                      onPressed: () async =>
                                          WhatsappShareService.instance.openSms(
                                        phone: c.phone,
                                        text:
                                            'مرحباً ${c.name}، رصيدكم الحالي لدى ${ctrl.settings.agencyName}: YER ${Money(yerDebt, CurrencyCode.yer).format()}${sarDebt != 0 ? ' | SAR ${Money(sarDebt, CurrencyCode.sar).format()}' : ''}',
                                      ),
                                      icon: const Icon(
                                        Icons.sms_outlined,
                                        size: 16,
                                      ),
                                      label: const Text('SMS'),
                                    ),
                                  ],
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
