import 'dart:async';

import 'package:flutter/material.dart';
import 'package:printing/printing.dart';
import 'package:provider/provider.dart';
import 'package:share_plus/share_plus.dart';

import '../core/money.dart';
import '../core/platform_bridge.dart';
import '../core/theme.dart';
import '../data/models.dart';
import '../data/qat_repository.dart';
import '../services/esc_pos_service.dart';
import '../services/pdf_export_service.dart';
import '../services/spreadsheet_export_service.dart';
import '../services/whatsapp_share_service.dart';
import '../state/app_controller.dart';
import 'widgets/common_widgets.dart';

class SalesCustomersTab extends StatefulWidget {
  const SalesCustomersTab({super.key});

  @override
  State<SalesCustomersTab> createState() => _SalesCustomersTabState();
}

class _SalesCustomersTabState extends State<SalesCustomersTab> {
  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final subTab = ctrl.salesSubTab.clamp(0, 3);

    return Column(
      children: [
        SegmentedStrip(
          tabs: const [
            'فاتورة جديدة',
            'سجل المبيعات',
            'العملاء والديون',
            'كشف الحساب',
          ],
          selectedIndex: subTab,
          onChanged: ctrl.setSalesSubTab,
        ),
        Expanded(
          child: IndexedStack(
            index: subTab,
            children: const [
              _PosInvoiceBuilderView(),
              _SalesLedgerView(),
              _CustomersDirectoryView(),
              _StatementView(),
            ],
          ),
        ),
      ],
    );
  }
}

// -----------------------------------------------------------------------------
// 1. POS Invoice Builder
// -----------------------------------------------------------------------------

class _PosInvoiceBuilderView extends StatefulWidget {
  const _PosInvoiceBuilderView();

  @override
  State<_PosInvoiceBuilderView> createState() => _PosInvoiceBuilderViewState();
}

class _PosInvoiceBuilderViewState extends State<_PosInvoiceBuilderView> {
  CurrencyCode? _currency;
  int? _customerId;
  int? _selectedBatchId;
  PaymentType _paymentType = PaymentType.paid;

  final _customQatCtrl = TextEditingController();
  final _qtyCtrl = TextEditingController(text: '1');
  final _priceCtrl = TextEditingController();
  final _discountCtrl = TextEditingController(text: '0');
  final _taxCtrl = TextEditingController(text: '0');
  final _paidCtrl = TextEditingController(text: '0');
  final _notesCtrl = TextEditingController();

  final List<NewSaleItemInput> _cart = [];
  bool _saving = false;

  @override
  void dispose() {
    _customQatCtrl.dispose();
    _qtyCtrl.dispose();
    _priceCtrl.dispose();
    _discountCtrl.dispose();
    _taxCtrl.dispose();
    _paidCtrl.dispose();
    _notesCtrl.dispose();
    super.dispose();
  }

  void _addCartLine(AppController ctrl, CurrencyCode activeCur) {
    final qtyMilli = QuantityMath.parseMilli(_qtyCtrl.text);
    final unitPriceMinor = Money.parseMinorUnits(_priceCtrl.text);
    if (qtyMilli <= 0 || unitPriceMinor < 0) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('تحقق من الكمية والسعر')),
      );
      return;
    }

    InventoryBatch? batch;
    if (_selectedBatchId != null) {
      for (final b in ctrl.batches) {
        if (b.id == _selectedBatchId) {
          batch = b;
          break;
        }
      }
    }

    final qatName = batch?.qatTypeName ?? _customQatCtrl.text.trim();
    if (qatName.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('اختر صنفاً من المخزون أو اكتب اسم الصنف')),
      );
      return;
    }

    if (batch != null && qtyMilli > batch.remainingQtyMilli) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            'المتاح في الدفعة ${QuantityMath.formatMilli(batch.remainingQtyMilli)} ${batch.unit} فقط',
          ),
        ),
      );
      return;
    }

    setState(() {
      _cart.add(NewSaleItemInput(
        inventoryBatchId: batch?.id,
        qatTypeName: qatName,
        qtyMilli: qtyMilli,
        unit: batch?.unit ?? 'ربطة',
        unitPriceMinor: unitPriceMinor,
        buyPriceMinor: batch?.buyPriceMinor ?? 0,
      ));
      _qtyCtrl.text = '1';
    });
  }

  Future<void> _saveInvoice(AppController ctrl, CurrencyCode activeCur) async {
    if (_cart.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('أضف صنفاً واحداً على الأقل')),
      );
      return;
    }
    if (_paymentType != PaymentType.paid && _customerId == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('الفاتورة الآجلة أو الجزئية تتطلب تحديد عميل')),
      );
      return;
    }

    setState(() => _saving = true);
    try {
      final custName = _customerId == null
          ? 'عميل نقدي'
          : ctrl.customers.firstWhere((c) => c.id == _customerId).name;

      final saleId = await ctrl.repo.makeSale(
        customerId: _customerId,
        customerName: custName,
        items: List<NewSaleItemInput>.from(_cart),
        currency: activeCur,
        discountMinor: Money.parseMinorUnits(_discountCtrl.text),
        taxBps: InvoiceMath.parsePercentBps(_taxCtrl.text),
        paymentType: _paymentType,
        paidAmountMinor: Money.parseMinorUnits(_paidCtrl.text),
        notes: _notesCtrl.text,
      );

      await ctrl.reloadAll();
      if (!mounted) return;
      final matched = ctrl.sales.where((s) => s.id == saleId).toList();
      final created = matched.isEmpty ? null : matched.first;
      setState(() {
        _cart.clear();
        _discountCtrl.text = '0';
        _taxCtrl.text = '0';
        _paidCtrl.text = '0';
        _notesCtrl.text = '';
      });
      if (created != null) {
        await _showPostSaleActions(ctrl, created);
      }
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.toString())));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _showPostSaleActions(AppController ctrl, Sale sale) async {
    await showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('تم حفظ الفاتورة ${sale.invoiceNumber}'),
        content: Text(
          'الإجمالي: ${Money(sale.totalAmountMinor, sale.currency).format()}\n'
          'المتبقي: ${Money(sale.remainingAmountMinor, sale.currency).format()}',
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('إغلاق')),
          OutlinedButton.icon(
            onPressed: () async {
              final msg = WhatsappShareService.instance.formatSaleMessage(ctrl.settings, sale);
              await WhatsappShareService.instance.openWhatsApp(text: msg);
            },
            icon: const Icon(Icons.chat_outlined, size: 18),
            label: const Text('واتساب'),
          ),
          FilledButton.icon(
            onPressed: () async {
              final bytes = await PdfExportService.instance.buildSaleInvoicePdf(
                agency: ctrl.settings,
                sale: sale,
              );
              await Printing.layoutPdf(onLayout: (_) async => bytes);
            },
            icon: const Icon(Icons.picture_as_pdf_outlined, size: 18),
            label: const Text('PDF / طباعة'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final pal = context.pal;
    final policy = ctrl.currencyPolicy;
    final activeCur = (_currency != null && policy.allowedCurrencies.contains(_currency))
        ? _currency!
        : policy.defaultCurrency;

    final availableBatches = ctrl.batches
        .where((b) => b.remainingQtyMilli > 0 && b.currency == activeCur)
        .toList();

    final lineTotals = _cart
        .map((e) => InvoiceMath.lineTotalMinor(qtyMilli: e.qtyMilli, unitPriceMinor: e.unitPriceMinor))
        .toList();
    final preview = InvoiceMath.computeTotals(
      lineTotalsMinor: lineTotals,
      discountMinor: Money.parseMinorUnits(_discountCtrl.text),
      taxBps: InvoiceMath.parsePercentBps(_taxCtrl.text),
      paidMinor: _paymentType == PaymentType.paid
          ? 1 << 50
          : (_paymentType == PaymentType.credit ? 0 : Money.parseMinorUnits(_paidCtrl.text)),
    );

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                CurrencySelectorRow(
                  policy: policy,
                  selected: activeCur,
                  onChanged: (c) => setState(() {
                    _currency = c;
                    _selectedBatchId = null;
                    _cart.clear();
                  }),
                ),
                const SizedBox(height: 12),
                DropdownButtonFormField<int?>(
                  key: ValueKey(
                    'pos_cust_${ctrl.customers.any((c) => c.id == _customerId) ? _customerId : 'none'}',
                  ),
                  initialValue:
                      ctrl.customers.any((c) => c.id == _customerId) ? _customerId : null,
                  decoration: const InputDecoration(labelText: 'العميل'),
                  items: [
                    const DropdownMenuItem<int?>(value: null, child: Text('عميل نقدي (مباشر)')),
                    ...ctrl.customers.map(
                      (c) => DropdownMenuItem<int?>(value: c.id, child: Text(c.name)),
                    ),
                  ],
                  onChanged: (v) => setState(() => _customerId = v),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 12),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text('إضافة أصناف الفاتورة', style: Theme.of(context).textTheme.titleMedium),
                const SizedBox(height: 10),
                DropdownButtonFormField<int?>(
                  key: ValueKey(
                    'pos_batch_${activeCur.code}_${availableBatches.any((b) => b.id == _selectedBatchId) ? _selectedBatchId : 'none'}',
                  ),
                  initialValue: availableBatches.any((b) => b.id == _selectedBatchId)
                      ? _selectedBatchId
                      : null,
                  decoration: const InputDecoration(labelText: 'دفعة المخزون'),
                  items: [
                    const DropdownMenuItem<int?>(
                        value: null, child: Text('إدخال يدوي بدون خصم مخزون')),
                    ...availableBatches.map(
                      (b) => DropdownMenuItem<int?>(
                        value: b.id,
                        child: Text(
                          '${b.qatTypeName} (متاح: ${QuantityMath.formatMilli(b.remainingQtyMilli)} ${b.unit})',
                        ),
                      ),
                    ),
                  ],
                  onChanged: (v) {
                    setState(() {
                      _selectedBatchId = v;
                      if (v != null) {
                        final b = availableBatches.firstWhere((e) => e.id == v);
                        _priceCtrl.text = Money.formatMinor(b.sellPriceMinor, showZeroDecimals: false)
                            .replaceAll(',', '');
                      }
                    });
                  },
                ),
                if (_selectedBatchId == null) ...[
                  const SizedBox(height: 10),
                  TextField(
                    controller: _customQatCtrl,
                    decoration: const InputDecoration(labelText: 'اسم الصنف'),
                  ),
                ],
                const SizedBox(height: 10),
                Row(
                  children: [
                    Expanded(
                      child: AccountingNumberField(controller: _qtyCtrl, label: 'الكمية'),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: AccountingNumberField(
                        controller: _priceCtrl,
                        label: 'سعر الوحدة (${activeCur.shortSymbol})',
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 10),
                OutlinedButton.icon(
                  onPressed: () => _addCartLine(ctrl, activeCur),
                  icon: const Icon(Icons.add_shopping_cart),
                  label: const Text('إضافة إلى الفاتورة'),
                ),
              ],
            ),
          ),
        ),

        if (_cart.isNotEmpty) ...[
          const SizedBox(height: 12),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(14),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text('أصناف الفاتورة (${_cart.length})',
                      style: Theme.of(context).textTheme.titleMedium),
                  const Divider(height: 16),
                  for (int i = 0; i < _cart.length; i++)
                    ListTile(
                      dense: true,
                      contentPadding: EdgeInsets.zero,
                      title: Text(_cart[i].qatTypeName),
                      subtitle: Text(
                        '${QuantityMath.formatMilli(_cart[i].qtyMilli)} ${_cart[i].unit} × ${Money(_cart[i].unitPriceMinor, activeCur).format()}',
                      ),
                      trailing: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text(
                            Money(
                              InvoiceMath.lineTotalMinor(
                                qtyMilli: _cart[i].qtyMilli,
                                unitPriceMinor: _cart[i].unitPriceMinor,
                              ),
                              activeCur,
                            ).format(),
                            style: const TextStyle(fontWeight: FontWeight.w700),
                          ),
                          IconButton(
                            onPressed: () => setState(() => _cart.removeAt(i)),
                            icon: Icon(Icons.delete_outline, color: pal.danger, size: 18),
                          ),
                        ],
                      ),
                    ),
                  const Divider(height: 16),
                  Row(
                    children: [
                      Expanded(
                        child: AccountingNumberField(
                          controller: _discountCtrl,
                          label: 'الخصم',
                          onChanged: (_) => setState(() {}),
                        ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: AccountingNumberField(
                          controller: _taxCtrl,
                          label: 'الضريبة %',
                          onChanged: (_) => setState(() {}),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  SegmentedButton<PaymentType>(
                    segments: const [
                      ButtonSegment(value: PaymentType.paid, label: Text('نقداً')),
                      ButtonSegment(value: PaymentType.partial, label: Text('دفع جزئي')),
                      ButtonSegment(value: PaymentType.credit, label: Text('آجل')),
                    ],
                    selected: {_paymentType},
                    onSelectionChanged: (s) => setState(() => _paymentType = s.first),
                  ),
                  if (_paymentType == PaymentType.partial) ...[
                    const SizedBox(height: 10),
                    AccountingNumberField(
                      controller: _paidCtrl,
                      label: 'المبلغ المدفوع مقدماً',
                      onChanged: (_) => setState(() {}),
                    ),
                  ],
                  const SizedBox(height: 10),
                  TextField(
                    controller: _notesCtrl,
                    decoration: const InputDecoration(labelText: 'ملاحظات الفاتورة'),
                  ),
                  const SizedBox(height: 14),
                  Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: pal.brandSoft,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Column(
                      children: [
                        _TotalLine('المجموع الفرعي',
                            Money(preview.subtotalMinor, activeCur).format()),
                        if (preview.discountMinor > 0)
                          _TotalLine('الخصم', Money(preview.discountMinor, activeCur).format()),
                        if (preview.taxAmountMinor > 0)
                          _TotalLine('الضريبة', Money(preview.taxAmountMinor, activeCur).format()),
                        _TotalLine(
                          'الصافي النهائي',
                          Money(preview.totalAmountMinor, activeCur).format(),
                          bold: true,
                        ),
                        _TotalLine(
                          'المتبقي كدين',
                          Money(preview.remainingAmountMinor, activeCur).format(),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 12),
                  FilledButton.icon(
                    onPressed: _saving ? null : () => _saveInvoice(ctrl, activeCur),
                    icon: const Icon(Icons.check_circle_outline),
                    label: Text(_saving ? 'جاري الحفظ...' : 'اعتماد وحفظ الفاتورة'),
                  ),
                ],
              ),
            ),
          ),
        ],
      ],
    );
  }
}

class _TotalLine extends StatelessWidget {
  const _TotalLine(this.label, this.value, {this.bold = false});
  final String label;
  final String value;
  final bool bold;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label,
              style: TextStyle(fontWeight: bold ? FontWeight.w700 : FontWeight.w500)),
          Text(value,
              style: TextStyle(
                fontWeight: bold ? FontWeight.w700 : FontWeight.w600,
                fontSize: bold ? 15 : 13,
              )),
        ],
      ),
    );
  }
}

// -----------------------------------------------------------------------------
// 2. Sales Ledger
// -----------------------------------------------------------------------------

class _SalesLedgerView extends StatefulWidget {
  const _SalesLedgerView();

  @override
  State<_SalesLedgerView> createState() => _SalesLedgerViewState();
}

class _SalesLedgerViewState extends State<_SalesLedgerView> {
  String _query = '';

  Future<void> _editSaleDialog(AppController ctrl, Sale sale) async {
    final first = sale.items.isEmpty ? null : sale.items.first;
    final itemCtrl = TextEditingController(text: first?.qatTypeName ?? '');
    final qtyCtrl = TextEditingController(
      text: first != null ? QuantityMath.formatMilli(first.qtyMilli) : '1',
    );
    final priceCtrl = TextEditingController(
      text: first != null ? Money.formatMinor(first.unitPriceMinor).replaceAll(',', '') : '0',
    );
    final discCtrl = TextEditingController(
      text: Money.formatMinor(sale.discountMinor).replaceAll(',', ''),
    );
    final taxCtrl = TextEditingController(
      text: Money.formatMinor(sale.taxBps).replaceAll(',', ''),
    );
    final paidCtrl = TextEditingController(
      text: Money.formatMinor(sale.paidAmountMinor).replaceAll(',', ''),
    );
    final notesCtrl = TextEditingController(text: sale.notes);
    PaymentType pType = sale.paymentType;
    int? custId = sale.customerId;

    await showDialog<void>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setModalState) => AlertDialog(
          title: Text('تعديل الفاتورة ${sale.invoiceNumber}'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                DropdownButtonFormField<int?>(
                  initialValue: ctrl.customers.any((c) => c.id == custId) ? custId : null,
                  decoration: const InputDecoration(labelText: 'العميل'),
                  items: [
                    const DropdownMenuItem<int?>(value: null, child: Text('عميل نقدي')),
                    ...ctrl.customers.map(
                      (c) => DropdownMenuItem<int?>(value: c.id, child: Text(c.name)),
                    ),
                  ],
                  onChanged: (v) => setModalState(() => custId = v),
                ),
                const SizedBox(height: 8),
                TextField(controller: itemCtrl, decoration: const InputDecoration(labelText: 'الصنف')),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Expanded(child: AccountingNumberField(controller: qtyCtrl, label: 'الكمية')),
                    const SizedBox(width: 8),
                    Expanded(child: AccountingNumberField(controller: priceCtrl, label: 'السعر')),
                  ],
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Expanded(child: AccountingNumberField(controller: discCtrl, label: 'الخصم')),
                    const SizedBox(width: 8),
                    Expanded(child: AccountingNumberField(controller: taxCtrl, label: 'الضريبة %')),
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
                final price = Money.parseMinorUnits(priceCtrl.text);
                if (itemCtrl.text.trim().isEmpty || qty <= 0) return;
                final custName = custId == null
                    ? 'عميل نقدي'
                    : ctrl.customers.firstWhere((c) => c.id == custId).name;
                final extraItems = sale.items.length > 1
                    ? sale.items
                        .skip(1)
                        .map((e) => NewSaleItemInput(
                              inventoryBatchId: e.inventoryBatchId,
                              qatTypeName: e.qatTypeName,
                              qtyMilli: e.qtyMilli,
                              unit: e.unit,
                              unitPriceMinor: e.unitPriceMinor,
                              buyPriceMinor: e.buyPriceMinor,
                            ))
                        .toList()
                    : const <NewSaleItemInput>[];
                try {
                  await ctrl.repo.editSale(
                    saleId: sale.id,
                    customerId: custId,
                    customerName: custName,
                    newItems: [
                      NewSaleItemInput(
                        inventoryBatchId: first?.inventoryBatchId,
                        qatTypeName: itemCtrl.text.trim(),
                        qtyMilli: qty,
                        unit: first?.unit ?? 'ربطة',
                        unitPriceMinor: price,
                        buyPriceMinor: first?.buyPriceMinor ?? 0,
                      ),
                      ...extraItems,
                    ],
                    currency: sale.currency,
                    discountMinor: Money.parseMinorUnits(discCtrl.text),
                    taxBps: InvoiceMath.parsePercentBps(taxCtrl.text),
                    paymentType: pType,
                    paidAmountMinor: Money.parseMinorUnits(paidCtrl.text),
                    notes: notesCtrl.text,
                  );
                  await ctrl.reloadAll();
                  if (ctx.mounted) Navigator.pop(ctx);
                } catch (e) {
                  if (!ctx.mounted) return;
                  ScaffoldMessenger.of(ctx).showSnackBar(SnackBar(content: Text(e.toString())));
                }
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
    final filtered = ctrl.sales.where((s) {
      if (_query.isEmpty) return true;
      final q = _query.toLowerCase();
      return s.customerName.toLowerCase().contains(q) ||
          s.invoiceNumber.toLowerCase().contains(q);
    }).toList();

    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
          child: Row(
            children: [
              Expanded(
                child: TextField(
                  decoration: const InputDecoration(
                    hintText: 'بحث برقم الفاتورة أو العميل...',
                    prefixIcon: Icon(Icons.search),
                  ),
                  onChanged: (v) => setState(() => _query = v.trim()),
                ),
              ),
              const SizedBox(width: 8),
              IconButton.outlined(
                tooltip: 'تصدير Excel',
                onPressed: filtered.isEmpty
                    ? null
                    : () async {
                        final path = await SpreadsheetExportService.instance
                            .exportSalesXlsx(filtered);
                        await Share.shareXFiles([XFile(path)], text: 'سجل المبيعات (.xlsx)');
                      },
                icon: const Icon(Icons.table_view_outlined),
              ),
            ],
          ),
        ),
        Expanded(
          child: filtered.isEmpty
              ? const EmptyLedgerState(
                  icon: Icons.receipt_long_outlined,
                  title: 'لا توجد فواتير مبيعات',
                  subtitle: 'أصدر فاتورتك الأولى من تبويب فاتورة جديدة',
                )
              : ListView.separated(
                  padding: const EdgeInsets.all(16),
                  itemCount: filtered.length,
                  separatorBuilder: (_, idx) => const SizedBox(height: 10),
                  itemBuilder: (_, idx) {
                    final s = filtered[idx];
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
                                    '${s.invoiceNumber} — ${s.customerName}',
                                    style: const TextStyle(fontWeight: FontWeight.w700),
                                  ),
                                ),
                                CurrencyBadge(currency: s.currency),
                                const SizedBox(width: 6),
                                StatusPill.forPayment(context, s.paymentType),
                              ],
                            ),
                            const SizedBox(height: 6),
                            Text(
                              'التاريخ: ${s.dateIso} • الإجمالي: ${Money(s.totalAmountMinor, s.currency).format()} • المتبقي: ${Money(s.remainingAmountMinor, s.currency).format()}',
                              style: TextStyle(fontSize: 12.5, color: pal.textSecondary),
                            ),
                            const SizedBox(height: 8),
                            Wrap(
                              spacing: 8,
                              children: [
                                OutlinedButton.icon(
                                  onPressed: () async {
                                    final bytes = await PdfExportService.instance
                                        .buildSaleInvoicePdf(agency: ctrl.settings, sale: s);
                                    await Printing.layoutPdf(onLayout: (_) async => bytes);
                                  },
                                  icon: const Icon(Icons.picture_as_pdf_outlined, size: 16),
                                  label: const Text('PDF'),
                                ),
                                if (ctrl.settings.savedPrinterAddress.isNotEmpty)
                                  OutlinedButton.icon(
                                    onPressed: () async {
                                      try {
                                        await EscPosService.instance.printSaleReceipt(
                                          agency: ctrl.settings,
                                          sale: s,
                                          printerAddress: ctrl.settings.savedPrinterAddress,
                                          paperMm: ctrl.settings.printerPaperMm,
                                        );
                                      } catch (e) {
                                        if (!context.mounted) return;
                                        ScaffoldMessenger.of(context)
                                            .showSnackBar(SnackBar(content: Text(e.toString())));
                                      }
                                    },
                                    icon: const Icon(Icons.print_outlined, size: 16),
                                    label: const Text('حراري'),
                                  ),
                                OutlinedButton.icon(
                                  onPressed: () => _editSaleDialog(ctrl, s),
                                  icon: const Icon(Icons.edit_outlined, size: 16),
                                  label: const Text('تعديل'),
                                ),
                                OutlinedButton.icon(
                                  onPressed: () async {
                                    await ctrl.repo.setArchived('sales', s.id, true);
                                    await ctrl.reloadAll();
                                  },
                                  icon: const Icon(Icons.archive_outlined, size: 16),
                                  label: const Text('أرشفة'),
                                ),
                                IconButton(
                                  tooltip: 'حذف وإرجاع المخزون',
                                  onPressed: () async {
                                    await ctrl.repo.deleteSale(s.id);
                                    await ctrl.reloadAll();
                                  },
                                  icon: Icon(Icons.delete_outline, color: pal.danger),
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

// -----------------------------------------------------------------------------
// 3. Customers & Receipts
// -----------------------------------------------------------------------------

class _CustomersDirectoryView extends StatefulWidget {
  const _CustomersDirectoryView();

  @override
  State<_CustomersDirectoryView> createState() => _CustomersDirectoryViewState();
}

class _CustomersDirectoryViewState extends State<_CustomersDirectoryView> {
  String _search = '';

  Future<void> _openCustomerDialog(AppController ctrl, {Customer? existing}) async {
    final nameCtrl = TextEditingController(text: existing?.name ?? '');
    final phoneCtrl = TextEditingController(text: existing?.phone ?? '');
    final addrCtrl = TextEditingController(text: existing?.address ?? '');
    final notesCtrl = TextEditingController(text: existing?.notes ?? '');

    await showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(existing == null ? 'إضافة عميل جديد' : 'تعديل بيانات العميل'),
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
              const SizedBox(height: 10),
              TextField(controller: nameCtrl, decoration: const InputDecoration(labelText: 'الاسم *')),
              const SizedBox(height: 8),
              TextField(
                controller: phoneCtrl,
                textDirection: TextDirection.ltr,
                keyboardType: TextInputType.phone,
                decoration: const InputDecoration(labelText: 'الهاتف'),
              ),
              const SizedBox(height: 8),
              TextField(controller: addrCtrl, decoration: const InputDecoration(labelText: 'العنوان')),
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
              await ctrl.repo.upsertCustomer(
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

  Future<void> _openCustomerReceiptsDialog(AppController ctrl, Customer customer) async {
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
                      title: Text(Money(r.amountMinor, r.currency).format()),
                      subtitle: Text('${r.dateIso} • ${r.paymentMethod} ${r.notes}'),
                      trailing: IconButton(
                        icon: const Icon(Icons.delete_outline, size: 18),
                        onPressed: () async {
                          await ctrl.repo.deleteCustomerPayment(r.id);
                          await ctrl.reloadAll();
                          if (ctx.mounted) Navigator.pop(ctx);
                        },
                      ),
                    );
                  },
                ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('إغلاق')),
        ],
      ),
    );
  }

  Future<void> _openPaymentDialog(AppController ctrl, Customer customer) async {
    final amountCtrl = TextEditingController();
    final notesCtrl = TextEditingController();
    CurrencyCode selectedCur = ctrl.currencyPolicy.defaultCurrency;

    await showDialog<void>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setModalState) => AlertDialog(
          title: Text('سند قبض — ${customer.name}'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              CurrencySelectorRow(
                policy: ctrl.currencyPolicy,
                selected: selectedCur,
                onChanged: (c) => setModalState(() => selectedCur = c),
              ),
              const SizedBox(height: 10),
              AccountingNumberField(
                controller: amountCtrl,
                label: 'المبلغ المقبوض (${selectedCur.shortSymbol})',
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
                await ctrl.repo.addCustomerPayment(
                  customerId: customer.id,
                  customerName: customer.name,
                  amountMinor: minor,
                  currency: selectedCur,
                  notes: notesCtrl.text,
                );
                await ctrl.reloadAll();
                if (ctx.mounted) Navigator.pop(ctx);
              },
              child: const Text('حفظ السند'),
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
    final list = ctrl.customers.where((c) {
      if (_search.isEmpty) return true;
      return c.name.toLowerCase().contains(_search.toLowerCase()) ||
          c.phone.contains(_search);
    }).toList();

    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _openCustomerDialog(ctrl),
        icon: const Icon(Icons.person_add_alt_1),
        label: const Text('عميل جديد'),
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
            child: TextField(
              decoration: const InputDecoration(
                hintText: 'بحث عن عميل بالاسم أو الهاتف...',
                prefixIcon: Icon(Icons.search),
              ),
              onChanged: (v) => setState(() => _search = v.trim()),
            ),
          ),
          Expanded(
            child: list.isEmpty
                ? const EmptyLedgerState(
                    icon: Icons.people_outline,
                    title: 'لا يوجد عملاء بعد',
                    subtitle: 'أضف عملاءك لإدارة الفواتير الآجلة وسندات القبض',
                  )
                : ListView.separated(
                    padding: const EdgeInsets.fromLTRB(16, 8, 16, 84),
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
                                    child: Text(c.name,
                                        style: const TextStyle(fontWeight: FontWeight.w700)),
                                  ),
                                  Row(
                                    mainAxisSize: MainAxisSize.min,
                                    children: [
                                      IconButton(
                                        onPressed: () => _openCustomerDialog(ctrl, existing: c),
                                        icon: const Icon(Icons.edit_outlined, size: 18),
                                      ),
                                      IconButton(
                                        tooltip: 'حذف العميل',
                                        onPressed: () async {
                                          try {
                                            await ctrl.repo.deleteCustomer(c.id);
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
                                ],
                              ),
                              if (c.phone.isNotEmpty)
                                Text(c.phone, style: TextStyle(color: pal.textSecondary, fontSize: 12)),
                              const SizedBox(height: 8),
                              Wrap(
                                spacing: 8,
                                runSpacing: 6,
                                children: [
                                  StatusPill(
                                    label: 'YER: ${Money(yerDebt, CurrencyCode.yer).format()}',
                                    bg: yerDebt > 0 ? pal.warningSoft : pal.successSoft,
                                    fg: yerDebt > 0 ? pal.warning : pal.success,
                                  ),
                                  if (sarDebt != 0 || ctrl.currencyPolicy.canSwitchCurrency)
                                    StatusPill(
                                      label: 'SAR: ${Money(sarDebt, CurrencyCode.sar).format()}',
                                      bg: sarDebt > 0 ? pal.warningSoft : pal.successSoft,
                                      fg: sarDebt > 0 ? pal.warning : pal.success,
                                    ),
                                ],
                              ),
                              const SizedBox(height: 10),
                              Wrap(
                                spacing: 8,
                                runSpacing: 8,
                                children: [
                                  FilledButton.tonalIcon(
                                    onPressed: () => _openPaymentDialog(ctrl, c),
                                    icon: const Icon(Icons.payments_outlined, size: 16),
                                    label: const Text('سند قبض'),
                                  ),
                                  OutlinedButton.icon(
                                    onPressed: () => _openCustomerReceiptsDialog(ctrl, c),
                                    icon: const Icon(Icons.receipt_long_outlined, size: 16),
                                    label: const Text('السندات'),
                                  ),
                                  if (c.phone.isNotEmpty)
                                    OutlinedButton.icon(
                                      onPressed: () async =>
                                          WhatsappShareService.instance.openWhatsApp(
                                        phone: c.phone,
                                        text:
                                            'مرحباً ${c.name}، رصيدكم الحالي لدى ${ctrl.settings.agencyName}:\n'
                                            'ريال يمني: ${Money(yerDebt, CurrencyCode.yer).format()}\n'
                                            '${sarDebt != 0 ? 'ريال سعودي: ${Money(sarDebt, CurrencyCode.sar).format()}' : ''}',
                                      ),
                                      icon: const Icon(Icons.chat_outlined, size: 16),
                                      label: const Text('تذكير'),
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

// -----------------------------------------------------------------------------
// 4. Unified Multi-Currency Account Statement
// -----------------------------------------------------------------------------

class _StatementView extends StatefulWidget {
  const _StatementView();

  @override
  State<_StatementView> createState() => _StatementViewState();
}

class _StatementViewState extends State<_StatementView> {
  bool _isCustomer = true;
  int? _partyId;
  Map<CurrencyCode, List<StatementLine>> _lines = {};
  bool _loading = false;

  Future<void> _loadStatement(AppController ctrl) async {
    if (_partyId == null) {
      setState(() => _lines = {});
      return;
    }
    setState(() => _loading = true);
    final res = _isCustomer
        ? await ctrl.repo.getCustomerStatement(_partyId!)
        : await ctrl.repo.getSupplierStatement(_partyId!);
    if (!mounted) return;
    setState(() {
      _lines = res;
      _loading = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final pal = context.pal;
    final partyList = _isCustomer
        ? ctrl.customers.map((c) => (id: c.id, name: c.name)).toList()
        : ctrl.suppliers.map((s) => (id: s.id, name: s.name)).toList();

    final selectedMatches = partyList.where((e) => e.id == _partyId).toList();
    final selectedPartyName = selectedMatches.isEmpty ? '' : selectedMatches.first.name;

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                SegmentedButton<bool>(
                  segments: const [
                    ButtonSegment(value: true, label: Text('كشف حساب عميل')),
                    ButtonSegment(value: false, label: Text('كشف حساب مورد')),
                  ],
                  selected: {_isCustomer},
                  onSelectionChanged: (s) {
                    setState(() {
                      _isCustomer = s.first;
                      _partyId = null;
                      _lines = {};
                    });
                  },
                ),
                const SizedBox(height: 12),
                DropdownButtonFormField<int>(
                  key: ValueKey('stmt_party_${_isCustomer}_$_partyId'),
                  initialValue: partyList.any((e) => e.id == _partyId) ? _partyId : null,
                  decoration: InputDecoration(
                    labelText: _isCustomer ? 'اختر العميل' : 'اختر المورد',
                  ),
                  items: partyList
                      .map((e) => DropdownMenuItem<int>(value: e.id, child: Text(e.name)))
                      .toList(),
                  onChanged: (v) {
                    setState(() => _partyId = v);
                    unawaited(_loadStatement(ctrl));
                  },
                ),
                if (_partyId != null && _lines.isNotEmpty) ...[
                  const SizedBox(height: 10),
                  FilledButton.icon(
                    onPressed: () async {
                      final bytes = await PdfExportService.instance.buildStatementPdf(
                        agency: ctrl.settings,
                        partyTitle: _isCustomer ? 'العميل' : 'المورد',
                        partyName: selectedPartyName,
                        linesByCurrency: _lines,
                      );
                      await Printing.layoutPdf(onLayout: (_) async => bytes);
                    },
                    icon: const Icon(Icons.picture_as_pdf_outlined, size: 18),
                    label: const Text('تصدير كشف الحساب PDF'),
                  ),
                ],
              ],
            ),
          ),
        ),
        const SizedBox(height: 12),
        if (_loading)
          const Center(child: Padding(padding: EdgeInsets.all(24), child: CircularProgressIndicator()))
        else if (_partyId == null)
          const EmptyLedgerState(
            icon: Icons.article_outlined,
            title: 'اختر عميلاً أو مورداً',
            subtitle: 'سيتم عرض كشف الحساب المفصل مع فصل العملات تلقائياً',
          )
        else if (_lines.isEmpty)
          const EmptyLedgerState(
            icon: Icons.receipt_outlined,
            title: 'لا توجد حركات مالية',
            subtitle: 'لا توجد فواتير أو سندات مسجلة لهذا الحساب',
          )
        else
          for (final entry in _lines.entries) ...[
            Row(
              children: [
                CurrencyBadge(currency: entry.key),
                const SizedBox(width: 8),
                Text('حركات ${entry.key.arabicName}',
                    style: Theme.of(context).textTheme.titleMedium),
              ],
            ),
            const SizedBox(height: 8),
            for (final line in entry.value)
              Card(
                margin: const EdgeInsets.only(bottom: 8),
                child: ListTile(
                  title: Text('${line.reference} — ${line.description}',
                      style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
                  subtitle: Text(
                    'التاريخ: ${line.dateIso}\n'
                    'عليه: ${Money(line.debitMinor, entry.key).format()} • له: ${Money(line.creditMinor, entry.key).format()}',
                    style: TextStyle(fontSize: 12, color: pal.textSecondary),
                  ),
                  trailing: Text(
                    Money(line.runningBalanceMinor, entry.key).format(),
                    style: TextStyle(
                      fontWeight: FontWeight.w700,
                      color: line.runningBalanceMinor > 0 ? pal.danger : pal.success,
                    ),
                  ),
                ),
              ),
            const SizedBox(height: 12),
          ],
      ],
    );
  }
}
