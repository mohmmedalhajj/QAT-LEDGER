import 'package:material_ui/material_ui.dart';
import 'package:printing/printing.dart';
import 'package:provider/provider.dart';

import '../../core/money.dart';
import '../../core/theme.dart';
import '../../data/models.dart';
import '../../data/qat_repository.dart';
import '../../services/esc_pos_service.dart';
import '../../services/pdf_export_service.dart';
import '../../services/whatsapp_share_service.dart';
import '../../state/app_controller.dart';
import '../widgets/common_widgets.dart';
import '../widgets/erp_components.dart';

class PosInvoiceBuilderView extends StatefulWidget {
  const PosInvoiceBuilderView({super.key});

  @override
  State<PosInvoiceBuilderView> createState() => _PosInvoiceBuilderViewState();
}

class _PosInvoiceBuilderViewState extends State<PosInvoiceBuilderView> {
  CurrencyCode? _currency;
  int? _customerId;
  int? _selectedBatchId;
  String _customUnit = 'ربطة';
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

  static const _units = ['ربطة', 'علاقية', 'كيلو', 'حبة'];

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

  void _addCartLine(AppController ctrl) {
    final qtyMilli = QuantityMath.parseMilli(_qtyCtrl.text);
    final unitPriceMinor = Money.parseMinorUnits(_priceCtrl.text);
    if (qtyMilli <= 0 || unitPriceMinor < 0) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('يرجى التحقق من صحة الكمية والسعر')),
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
        const SnackBar(content: Text('اختر دفعة من المخزون أو اكتب اسم الصنف')),
      );
      return;
    }

    // Account for quantity already added in cart from the same batch
    final alreadyInCartMilli = batch == null
        ? 0
        : _cart
            .where((item) => item.inventoryBatchId == batch!.id)
            .fold<int>(0, (sum, item) => sum + item.qtyMilli);
    if (batch != null && qtyMilli + alreadyInCartMilli > batch.remainingQtyMilli) {
      final left = (batch.remainingQtyMilli - alreadyInCartMilli)
          .clamp(0, 1 << 60)
          .toInt();
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            'الكمية تتجاوز المتاح في الدفعة (المتاح الإضافي: ${QuantityMath.formatMilli(left)} ${batch.unit})',
          ),
        ),
      );
      return;
    }

    setState(() {
      final existingIdx = _cart.indexWhere(
        (item) =>
            item.inventoryBatchId == batch?.id &&
            item.qatTypeName == qatName &&
            item.unitPriceMinor == unitPriceMinor &&
            item.unit == (batch?.unit ?? _customUnit),
      );
      if (existingIdx >= 0) {
        final prev = _cart[existingIdx];
        _cart[existingIdx] = NewSaleItemInput(
          inventoryBatchId: prev.inventoryBatchId,
          qatTypeName: prev.qatTypeName,
          qtyMilli: prev.qtyMilli + qtyMilli,
          unit: prev.unit,
          unitPriceMinor: prev.unitPriceMinor,
          buyPriceMinor: prev.buyPriceMinor,
        );
      } else {
        _cart.add(
          NewSaleItemInput(
            inventoryBatchId: batch?.id,
            qatTypeName: qatName,
            qtyMilli: qtyMilli,
            unit: batch?.unit ?? _customUnit,
            unitPriceMinor: unitPriceMinor,
            buyPriceMinor: batch?.buyPriceMinor ?? 0,
          ),
        );
      }
      _qtyCtrl.text = '1';
      if (batch == null) {
        _customQatCtrl.clear();
      }
    });
  }

  Future<void> _saveInvoice(AppController ctrl, CurrencyCode activeCur) async {
    if (_cart.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('أضف صنفاً واحداً على الأقل للفاتورة')),
      );
      return;
    }
    if (_paymentType != PaymentType.paid && _customerId == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('الفاتورة الآجلة أو الجزئية تتطلب اختيار عميل'),
        ),
      );
      return;
    }

    final lineTotals = _cart
        .map(
          (e) => InvoiceMath.lineTotalMinor(
            qtyMilli: e.qtyMilli,
            unitPriceMinor: e.unitPriceMinor,
          ),
        )
        .toList();
    final discount = Money.parseMinorUnits(_discountCtrl.text);
    final taxBps = InvoiceMath.parsePercentBps(_taxCtrl.text);
    final paidInput = Money.parseMinorUnits(_paidCtrl.text);
    final preview = InvoiceMath.computeTotals(
      lineTotalsMinor: lineTotals,
      discountMinor: discount,
      taxBps: taxBps,
      paidMinor: _paymentType == PaymentType.paid
          ? 1 << 50
          : (_paymentType == PaymentType.credit ? 0 : paidInput),
    );
    if (_paymentType == PaymentType.partial &&
        (paidInput <= 0 || paidInput >= preview.totalAmountMinor)) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text(
            'في الدفع الجزئي يجب أن يكون المدفوع أكبر من 0 وأقل من الصافي',
          ),
        ),
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
        discountMinor: discount,
        taxBps: taxBps,
        paymentType: _paymentType,
        paidAmountMinor: paidInput,
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
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(e.toString())));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _showPostSaleActions(AppController ctrl, Sale sale) async {
    final matchingCust = sale.customerId == null
        ? const <Customer>[]
        : ctrl.customers.where((c) => c.id == sale.customerId).toList();
    final custPhone = matchingCust.isEmpty ? null : matchingCust.first.phone;

    await showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Row(
          children: [
            Icon(Icons.check_circle, color: ctx.pal.success),
            const SizedBox(width: 8),
            Expanded(child: Text('تم اعتماد الفاتورة ${sale.invoiceNumber}')),
          ],
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ErpDetailRow(label: 'العميل', value: sale.customerName),
            ErpDetailRow(
              label: 'الإجمالي النهائي',
              value: Money(sale.totalAmountMinor, sale.currency).format(),
              bold: true,
            ),
            ErpDetailRow(
              label: 'المدفوع',
              value: Money(sale.paidAmountMinor, sale.currency).format(),
              valueColor: ctx.pal.success,
            ),
            ErpDetailRow(
              label: 'المتبقي',
              value: Money(sale.remainingAmountMinor, sale.currency).format(),
              valueColor: sale.remainingAmountMinor > 0
                  ? ctx.pal.danger
                  : ctx.pal.textSecondary,
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('إغلاق'),
          ),
          if (ctrl.settings.savedPrinterAddress.isNotEmpty)
            OutlinedButton.icon(
              onPressed: () async {
                try {
                  await EscPosService.instance.printSaleReceipt(
                    agency: ctrl.settings,
                    sale: sale,
                    printerAddress: ctrl.settings.savedPrinterAddress,
                    paperMm: ctrl.settings.printerPaperMm,
                  );
                } catch (e) {
                  if (!ctx.mounted) return;
                  ScaffoldMessenger.of(ctx)
                      .showSnackBar(SnackBar(content: Text(e.toString())));
                }
              },
              icon: const Icon(Icons.print_outlined, size: 17),
              label: const Text('حراري'),
            ),
          OutlinedButton.icon(
            onPressed: () async {
              final msg = WhatsappShareService.instance
                  .formatSaleMessage(ctrl.settings, sale);
              await WhatsappShareService.instance
                  .openWhatsApp(phone: custPhone, text: msg);
            },
            icon: const Icon(Icons.chat_outlined, size: 17),
            label: const Text('واتساب'),
          ),
          FilledButton.icon(
            onPressed: () async {
              final bytes = await PdfExportService.instance
                  .buildSaleInvoicePdf(agency: ctrl.settings, sale: sale);
              await Printing.layoutPdf(onLayout: (_) async => bytes);
            },
            icon: const Icon(Icons.picture_as_pdf_outlined, size: 17),
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
    final activeCur =
        (_currency != null && policy.allowedCurrencies.contains(_currency))
            ? _currency!
            : policy.defaultCurrency;

    final availableBatches = ctrl.batches
        .where((b) => b.remainingQtyMilli > 0 && b.currency == activeCur)
        .toList();

    final lineTotals = _cart
        .map(
          (e) => InvoiceMath.lineTotalMinor(
            qtyMilli: e.qtyMilli,
            unitPriceMinor: e.unitPriceMinor,
          ),
        )
        .toList();
    final preview = InvoiceMath.computeTotals(
      lineTotalsMinor: lineTotals,
      discountMinor: Money.parseMinorUnits(_discountCtrl.text),
      taxBps: InvoiceMath.parsePercentBps(_taxCtrl.text),
      paidMinor: _paymentType == PaymentType.paid
          ? 1 << 50
          : (_paymentType == PaymentType.credit
              ? 0
              : Money.parseMinorUnits(_paidCtrl.text)),
    );

    final wide = MediaQuery.sizeOf(context).width >= 860;

    final itemPickerColumn = Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const ErpSectionHeader(
                  title: 'بيانات الفاتورة والعميل',
                  icon: Icons.person_pin_outlined,
                ),
                const SizedBox(height: 12),
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
                  key: ValueKey<String>(
                    'pos_cust_${ctrl.customers.any((c) => c.id == _customerId) ? _customerId : 'none'}',
                  ),
                  initialValue: ctrl.customers.any((c) => c.id == _customerId)
                      ? _customerId
                      : null,
                  isExpanded: true,
                  decoration: const InputDecoration(
                    labelText: 'العميل',
                    prefixIcon: Icon(Icons.person_outline),
                  ),
                  items: [
                    const DropdownMenuItem<int?>(
                      value: null,
                      child: Text('عميل نقدي (مباشر)'),
                    ),
                    ...ctrl.customers.map(
                      (c) => DropdownMenuItem<int?>(
                        value: c.id,
                        child: Text(
                          '${c.name}${c.balanceFor(activeCur) > 0 ? ' (عليه: ${Money(c.balanceFor(activeCur), activeCur).format()})' : ''}',
                        ),
                      ),
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
                const ErpSectionHeader(
                  title: 'إضافة أصناف الفاتورة',
                  icon: Icons.add_shopping_cart,
                ),
                const SizedBox(height: 12),
                DropdownButtonFormField<int?>(
                  key: ValueKey<String>(
                    'pos_batch_${activeCur.code}_${availableBatches.any((b) => b.id == _selectedBatchId) ? _selectedBatchId : 'none'}',
                  ),
                  initialValue:
                      availableBatches.any((b) => b.id == _selectedBatchId)
                          ? _selectedBatchId
                          : null,
                  isExpanded: true,
                  decoration: const InputDecoration(labelText: 'دفعة المخزون'),
                  items: [
                    const DropdownMenuItem<int?>(
                      value: null,
                      child: Text('إدخال يدوي بدون خصم مخزون'),
                    ),
                    ...availableBatches.map(
                      (b) => DropdownMenuItem<int?>(
                        value: b.id,
                        child: Text(
                          '${b.qatTypeName} • متاح ${QuantityMath.formatMilli(b.remainingQtyMilli)} ${b.unit}',
                        ),
                      ),
                    ),
                  ],
                  onChanged: (v) {
                    setState(() {
                      _selectedBatchId = v;
                      if (v != null) {
                        final b =
                            availableBatches.firstWhere((e) => e.id == v);
                        _priceCtrl.text = Money.formatMinor(
                          b.sellPriceMinor,
                          showZeroDecimals: false,
                        ).replaceAll(',', '');
                      }
                    });
                  },
                ),
                if (_selectedBatchId == null) ...[
                  const SizedBox(height: 10),
                  Row(
                    children: [
                      Expanded(
                        flex: 2,
                        child: TextField(
                          controller: _customQatCtrl,
                          decoration:
                              const InputDecoration(labelText: 'اسم الصنف'),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: DropdownButtonFormField<String>(
                          key: ValueKey<String>('pos_unit_$_customUnit'),
                          initialValue: _customUnit,
                          isExpanded: true,
                          decoration: const InputDecoration(labelText: 'الوحدة'),
                          items: _units
                              .map(
                                (u) => DropdownMenuItem<String>(
                                  value: u,
                                  child: Text(u),
                                ),
                              )
                              .toList(),
                          onChanged: (v) =>
                              setState(() => _customUnit = v ?? 'ربطة'),
                        ),
                      ),
                    ],
                  ),
                ],
                const SizedBox(height: 10),
                Row(
                  children: [
                    Expanded(
                      child: AccountingNumberField(
                        controller: _qtyCtrl,
                        label: 'الكمية',
                      ),
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
                if (!ctrl.canEditPricesCurrent && _selectedBatchId != null)
                  Padding(
                    padding: const EdgeInsets.only(top: 6),
                    child: Text(
                      'صلاحية تعديل الأسعار محدودة لهذا المستخدم — يفضل اعتماد سعر الدفعة.',
                      style: TextStyle(fontSize: 11, color: pal.warning),
                    ),
                  ),
                const SizedBox(height: 10),
                FilledButton.tonalIcon(
                  onPressed: () => _addCartLine(ctrl),
                  icon: const Icon(Icons.add_circle_outline),
                  label: const Text('إضافة إلى سلة الفاتورة'),
                ),
              ],
            ),
          ),
        ),
      ],
    );

    final cartSummaryColumn = Card(
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            ErpSectionHeader(
              title: 'بنود الفاتورة والحساب (${_cart.length})',
              icon: Icons.receipt_outlined,
              trailing: _cart.isEmpty
                  ? null
                  : TextButton(
                      onPressed: () => setState(() => _cart.clear()),
                      child: Text(
                        'تفريغ',
                        style: TextStyle(color: pal.danger),
                      ),
                    ),
            ),
            const Divider(height: 16),
            if (_cart.isEmpty)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 20),
                child: EmptyLedgerState(
                  icon: Icons.shopping_cart_outlined,
                  title: 'سلة الفاتورة فارغة',
                  subtitle: 'أضف صنفاً أو أكثر لاحتساب الإجمالي وحفظ الفاتورة',
                ),
              )
            else ...[
              for (int i = 0; i < _cart.length; i++)
                Container(
                  margin: const EdgeInsets.only(bottom: 8),
                  padding:
                      const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                  decoration: BoxDecoration(
                    color: pal.cardElevated,
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(color: pal.border),
                  ),
                  child: Row(
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              _cart[i].qatTypeName,
                              style: const TextStyle(
                                fontWeight: FontWeight.w700,
                                fontSize: 13,
                              ),
                            ),
                            Text(
                              '${QuantityMath.formatMilli(_cart[i].qtyMilli)} ${_cart[i].unit} × ${Money(_cart[i].unitPriceMinor, activeCur).format()}',
                              style: TextStyle(
                                fontSize: 11.5,
                                color: pal.textSecondary,
                                fontFeatures: const [
                                  FontFeature.tabularFigures(),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ),
                      Text(
                        Money(
                          InvoiceMath.lineTotalMinor(
                            qtyMilli: _cart[i].qtyMilli,
                            unitPriceMinor: _cart[i].unitPriceMinor,
                          ),
                          activeCur,
                        ).format(),
                        style: const TextStyle(
                          fontWeight: FontWeight.w700,
                          fontFeatures: [FontFeature.tabularFigures()],
                        ),
                      ),
                      IconButton(
                        onPressed: () => setState(() => _cart.removeAt(i)),
                        icon: Icon(
                          Icons.delete_outline,
                          color: pal.danger,
                          size: 18,
                        ),
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
                  ButtonSegment<PaymentType>(
                    value: PaymentType.paid,
                    label: Text('نقداً'),
                  ),
                  ButtonSegment<PaymentType>(
                    value: PaymentType.partial,
                    label: Text('دفع جزئي'),
                  ),
                  ButtonSegment<PaymentType>(
                    value: PaymentType.credit,
                    label: Text('آجل'),
                  ),
                ],
                selected: {_paymentType},
                onSelectionChanged: (s) =>
                    setState(() => _paymentType = s.first),
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
                decoration:
                    const InputDecoration(labelText: 'ملاحظات الفاتورة'),
              ),
              const SizedBox(height: 14),
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: pal.brandSoft,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: pal.brand.withValues(alpha: 0.25)),
                ),
                child: Column(
                  children: [
                    ErpDetailRow(
                      label: 'المجموع الفرعي',
                      value: Money(preview.subtotalMinor, activeCur).format(),
                    ),
                    if (preview.discountMinor > 0)
                      ErpDetailRow(
                        label: 'الخصم',
                        value:
                            Money(preview.discountMinor, activeCur).format(),
                        valueColor: pal.warning,
                      ),
                    if (preview.taxAmountMinor > 0)
                      ErpDetailRow(
                        label: 'الضريبة',
                        value:
                            Money(preview.taxAmountMinor, activeCur).format(),
                      ),
                    const Divider(height: 10),
                    ErpDetailRow(
                      label: 'الصافي النهائي',
                      value:
                          Money(preview.totalAmountMinor, activeCur).format(),
                      bold: true,
                      valueColor: pal.brand,
                    ),
                    ErpDetailRow(
                      label: 'المتبقي كدين على العميل',
                      value: Money(preview.remainingAmountMinor, activeCur)
                          .format(),
                      valueColor: preview.remainingAmountMinor > 0
                          ? pal.danger
                          : pal.success,
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 12),
              FilledButton.icon(
                onPressed:
                    _saving ? null : () => _saveInvoice(ctrl, activeCur),
                icon: const Icon(Icons.check_circle_outline),
                label: Text(_saving ? 'جاري الحفظ...' : 'اعتماد وحفظ الفاتورة'),
              ),
            ],
          ],
        ),
      ),
    );

    if (wide) {
      return SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(flex: 5, child: itemPickerColumn),
            const SizedBox(width: 16),
            Expanded(flex: 5, child: cartSummaryColumn),
          ],
        ),
      );
    }

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        itemPickerColumn,
        const SizedBox(height: 12),
        cartSummaryColumn,
      ],
    );
  }
}
