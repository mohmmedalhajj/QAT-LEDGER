import 'package:material_ui/material_ui.dart';
import 'package:printing/printing.dart';
import 'package:provider/provider.dart';
import 'package:share_plus/share_plus.dart';

import '../../core/money.dart';
import '../../core/theme.dart';
import '../../data/models.dart';
import '../../data/qat_repository.dart';
import '../../services/esc_pos_service.dart';
import '../../services/pdf_export_service.dart';
import '../../services/spreadsheet_export_service.dart';
import '../../services/whatsapp_share_service.dart';
import '../../state/app_controller.dart';
import '../widgets/common_widgets.dart';
import '../widgets/erp_components.dart';

enum _SalesSortMode { newest, highestTotal, highestRemaining }

class SalesLedgerView extends StatefulWidget {
  const SalesLedgerView({super.key});

  @override
  State<SalesLedgerView> createState() => _SalesLedgerViewState();
}

class _SalesLedgerViewState extends State<SalesLedgerView> {
  String _query = '';
  PaymentType? _paymentFilter;
  CurrencyCode? _currencyFilter;
  _SalesSortMode _sortMode = _SalesSortMode.newest;

  Future<void> _showSaleDetailsDialog(AppController ctrl, Sale sale) async {
    final pal = context.pal;
    await showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Row(
          children: [
            Expanded(child: Text('تفاصيل الفاتورة ${sale.invoiceNumber}')),
            CurrencyBadge(currency: sale.currency),
          ],
        ),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              ErpDetailRow(label: 'العميل', value: sale.customerName),
              ErpDetailRow(label: 'التاريخ', value: sale.dateIso, ltr: true),
              ErpDetailRow(
                label: 'الحالة',
                value: sale.paymentType.arabicLabel,
              ),
              const Divider(height: 14),
              Text(
                'الأصناف (${sale.items.length})',
                style: TextStyle(
                  fontWeight: FontWeight.w700,
                  fontSize: 12.5,
                  color: pal.brand,
                ),
              ),
              const SizedBox(height: 6),
              for (final it in sale.items)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 3),
                  child: Row(
                    children: [
                      Expanded(
                        child: Text(
                          '${it.qatTypeName} (${QuantityMath.formatMilli(it.qtyMilli)} ${it.unit})',
                          style: const TextStyle(fontSize: 12.5),
                        ),
                      ),
                      Text(
                        Money(it.lineTotalMinor, sale.currency).format(),
                        style: const TextStyle(
                          fontWeight: FontWeight.w700,
                          fontSize: 12.5,
                          fontFeatures: [FontFeature.tabularFigures()],
                        ),
                      ),
                    ],
                  ),
                ),
              const Divider(height: 14),
              ErpDetailRow(
                label: 'الإجمالي النهائي',
                value: Money(sale.totalAmountMinor, sale.currency).format(),
                bold: true,
              ),
              ErpDetailRow(
                label: 'المدفوع',
                value: Money(sale.paidAmountMinor, sale.currency).format(),
                valueColor: pal.success,
              ),
              ErpDetailRow(
                label: 'المتبقي',
                value: Money(sale.remainingAmountMinor, sale.currency).format(),
                valueColor:
                    sale.remainingAmountMinor > 0 ? pal.danger : pal.success,
              ),
              if (sale.notes.isNotEmpty) ...[
                const SizedBox(height: 8),
                Text(
                  'ملاحظات: ${sale.notes}',
                  style: TextStyle(fontSize: 12, color: pal.textSecondary),
                ),
              ],
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('إغلاق'),
          ),
          OutlinedButton.icon(
            onPressed: () async {
              final msg = WhatsappShareService.instance
                  .formatSaleMessage(ctrl.settings, sale);
              await WhatsappShareService.instance.openWhatsApp(text: msg);
            },
            icon: const Icon(Icons.chat_outlined, size: 16),
            label: const Text('واتساب'),
          ),
          FilledButton.icon(
            onPressed: () async {
              final bytes = await PdfExportService.instance
                  .buildSaleInvoicePdf(agency: ctrl.settings, sale: sale);
              await Printing.layoutPdf(onLayout: (_) async => bytes);
            },
            icon: const Icon(Icons.picture_as_pdf_outlined, size: 16),
            label: const Text('PDF'),
          ),
          OutlinedButton.icon(
            onPressed: () async {
              final text = ThermalReceiptFormatter.buildSaleReceiptText(
                ctrl.settings,
                sale,
              );
              await showReceiptPreviewPrintSheet(
                ctx,
                agency: ctrl.settings,
                title: 'فاتورة ${sale.invoiceNumber}',
                receiptText: text,
              );
            },
            icon: const Icon(Icons.receipt_long_outlined, size: 16),
            label: const Text('حراري'),
          ),
        ],
      ),
    );
  }

  Future<void> _editSaleDialog(AppController ctrl, Sale sale) async {
    final lineDrafts = <({
      int? batchId,
      String unit,
      int buyPriceMinor,
      TextEditingController nameCtrl,
      TextEditingController qtyCtrl,
      TextEditingController priceCtrl,
    })>[
      for (final it in sale.items)
        (
          batchId: it.inventoryBatchId,
          unit: it.unit,
          buyPriceMinor: it.buyPriceMinor,
          nameCtrl: TextEditingController(text: it.qatTypeName),
          qtyCtrl: TextEditingController(
            text: QuantityMath.formatMilli(it.qtyMilli),
          ),
          priceCtrl: TextEditingController(
            text: Money.formatMinor(it.unitPriceMinor, showZeroDecimals: false)
                .replaceAll(',', ''),
          ),
        ),
    ];
    if (lineDrafts.isEmpty) {
      lineDrafts.add((
        batchId: null,
        unit: 'ربطة',
        buyPriceMinor: 0,
        nameCtrl: TextEditingController(),
        qtyCtrl: TextEditingController(text: '1'),
        priceCtrl: TextEditingController(text: '0'),
      ));
    }

    final discCtrl = TextEditingController(
      text: Money.formatMinor(sale.discountMinor, showZeroDecimals: false)
          .replaceAll(',', ''),
    );
    final taxCtrl = TextEditingController(
      text: Money.formatMinor(sale.taxBps, showZeroDecimals: false)
          .replaceAll(',', ''),
    );
    final paidCtrl = TextEditingController(
      text: Money.formatMinor(sale.paidAmountMinor, showZeroDecimals: false)
          .replaceAll(',', ''),
    );
    final notesCtrl = TextEditingController(text: sale.notes);
    PaymentType pType = sale.paymentType;
    String paymentMethod =
        sale.paymentMethod.isNotEmpty ? sale.paymentMethod : 'نقداً';
    int? custId = sale.customerId;
    int saleDateMs = sale.dateMs;

    List<TextEditingController> allControllers() => [
          discCtrl,
          taxCtrl,
          paidCtrl,
          notesCtrl,
          for (final d in lineDrafts) ...[d.nameCtrl, d.qtyCtrl, d.priceCtrl],
        ];

    await showDialog<void>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setModalState) => AlertDialog(
          title: Text('تعديل الفاتورة ${sale.invoiceNumber}'),
          content: SizedBox(
            width: 440,
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  ErpDatePickerTile(
                    label: 'تاريخ الفاتورة',
                    dateMs: saleDateMs,
                    onChanged: (ms) => setModalState(() => saleDateMs = ms),
                  ),
                  const SizedBox(height: 8),
                  DropdownButtonFormField<int?>(
                    key: ValueKey<String>(
                      'edit_sale_cust_${ctrl.customers.any((c) => c.id == custId) ? custId : 'none'}',
                    ),
                    initialValue: ctrl.customers.any((c) => c.id == custId)
                        ? custId
                        : null,
                    isExpanded: true,
                    decoration: const InputDecoration(labelText: 'العميل'),
                    items: [
                      const DropdownMenuItem<int?>(
                        value: null,
                        child: Text('عميل نقدي'),
                      ),
                      ...ctrl.customers.map(
                        (c) => DropdownMenuItem<int?>(
                          value: c.id,
                          child: Text(c.name),
                        ),
                      ),
                    ],
                    onChanged: (v) => setModalState(() => custId = v),
                  ),
                  const SizedBox(height: 10),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        'بنود الفاتورة (${lineDrafts.length})',
                        style: const TextStyle(
                          fontWeight: FontWeight.w700,
                          fontSize: 13,
                        ),
                      ),
                      TextButton.icon(
                        onPressed: () {
                          setModalState(() {
                            lineDrafts.add((
                              batchId: null,
                              unit: 'ربطة',
                              buyPriceMinor: 0,
                              nameCtrl: TextEditingController(),
                              qtyCtrl: TextEditingController(text: '1'),
                              priceCtrl: TextEditingController(text: '0'),
                            ));
                          });
                        },
                        icon: const Icon(Icons.add, size: 16),
                        label: const Text('إضافة بند'),
                      ),
                    ],
                  ),
                  for (int idx = 0; idx < lineDrafts.length; idx++)
                    Card(
                      margin: const EdgeInsets.only(bottom: 8),
                      child: Padding(
                        padding: const EdgeInsets.all(10),
                        child: Column(
                          children: [
                            Row(
                              children: [
                                Expanded(
                                  child: TextField(
                                    controller: lineDrafts[idx].nameCtrl,
                                    decoration: InputDecoration(
                                      isDense: true,
                                      labelText: 'الصنف #${idx + 1}',
                                    ),
                                  ),
                                ),
                                if (lineDrafts.length > 1)
                                  IconButton(
                                    tooltip: 'حذف البند',
                                    icon: Icon(
                                      Icons.delete_outline,
                                      size: 18,
                                      color: ctx.pal.danger,
                                    ),
                                    onPressed: () {
                                      setModalState(() {
                                        final removed = lineDrafts.removeAt(idx);
                                        removed.nameCtrl.dispose();
                                        removed.qtyCtrl.dispose();
                                        removed.priceCtrl.dispose();
                                      });
                                    },
                                  ),
                              ],
                            ),
                            const SizedBox(height: 8),
                            Row(
                              children: [
                                Expanded(
                                  child: AccountingNumberField(
                                    controller: lineDrafts[idx].qtyCtrl,
                                    label: 'الكمية (${lineDrafts[idx].unit})',
                                  ),
                                ),
                                const SizedBox(width: 8),
                                Expanded(
                                  child: AccountingNumberField(
                                    controller: lineDrafts[idx].priceCtrl,
                                    label: 'السعر',
                                  ),
                                ),
                              ],
                            ),
                          ],
                        ),
                      ),
                    ),
                  Row(
                    children: [
                      Expanded(
                        child: AccountingNumberField(
                          controller: discCtrl,
                          label: 'الخصم',
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: AccountingNumberField(
                          controller: taxCtrl,
                          label: 'الضريبة %',
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
                  const SizedBox(height: 8),
                  TextField(
                    controller: notesCtrl,
                    decoration: const InputDecoration(labelText: 'ملاحظات'),
                  ),
                ],
              ),
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('إلغاء'),
            ),
            FilledButton(
              onPressed: () async {
                final editedItems = <NewSaleItemInput>[];
                for (final d in lineDrafts) {
                  final qty = QuantityMath.parseMilli(d.qtyCtrl.text);
                  final price = Money.parseMinorUnits(d.priceCtrl.text);
                  if (d.nameCtrl.text.trim().isEmpty || qty <= 0) {
                    ScaffoldMessenger.of(ctx).showSnackBar(
                      const SnackBar(
                        content: Text('تأكد من اسم الصنف والكمية لكل بند'),
                      ),
                    );
                    return;
                  }
                  editedItems.add(
                    NewSaleItemInput(
                      inventoryBatchId: d.batchId,
                      qatTypeName: d.nameCtrl.text.trim(),
                      qtyMilli: qty,
                      unit: d.unit,
                      unitPriceMinor: price,
                      buyPriceMinor: d.buyPriceMinor,
                    ),
                  );
                }
                if (pType != PaymentType.paid && custId == null) {
                  ScaffoldMessenger.of(ctx).showSnackBar(
                    const SnackBar(
                      content: Text('الفاتورة الآجلة أو الجزئية تتطلب عميلاً'),
                    ),
                  );
                  return;
                }
                final custName = custId == null
                    ? 'عميل نقدي'
                    : ctrl.customers.firstWhere((c) => c.id == custId).name;
                try {
                  await ctrl.repo.editSale(
                    saleId: sale.id,
                    customerId: custId,
                    customerName: custName,
                    newItems: editedItems,
                    currency: sale.currency,
                    discountMinor: Money.parseMinorUnits(discCtrl.text),
                    taxBps: InvoiceMath.parsePercentBps(taxCtrl.text),
                    paymentType: pType,
                    paidAmountMinor: Money.parseMinorUnits(paidCtrl.text),
                    paymentMethod:
                        pType == PaymentType.credit ? 'آجل' : paymentMethod,
                    notes: notesCtrl.text,
                    dateMs: saleDateMs,
                  );
                  await ctrl.reloadAll();
                  if (ctx.mounted) Navigator.pop(ctx);
                } catch (e) {
                  if (!ctx.mounted) return;
                  ScaffoldMessenger.of(ctx)
                      .showSnackBar(SnackBar(content: Text(e.toString())));
                }
              },
              child: const Text('حفظ التعديل'),
            ),
          ],
        ),
      ),
    );
    for (final c in allControllers()) {
      c.dispose();
    }
  }

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final pal = context.pal;
    final filtered = ctrl.sales.where((s) {
      if (_paymentFilter != null && s.paymentType != _paymentFilter) {
        return false;
      }
      if (_currencyFilter != null && s.currency != _currencyFilter) {
        return false;
      }
      if (_query.isEmpty) return true;
      final q = _query.toLowerCase();
      return s.customerName.toLowerCase().contains(q) ||
          s.invoiceNumber.toLowerCase().contains(q) ||
          s.dateIso.contains(q);
    }).toList();

    switch (_sortMode) {
      case _SalesSortMode.newest:
        filtered.sort((a, b) => b.dateMs.compareTo(a.dateMs));
      case _SalesSortMode.highestTotal:
        filtered
            .sort((a, b) => b.totalAmountMinor.compareTo(a.totalAmountMinor));
      case _SalesSortMode.highestRemaining:
        filtered.sort(
          (a, b) => b.remainingAmountMinor.compareTo(a.remainingAmountMinor),
        );
    }

    final summaryMetrics = <ErpSummaryMetric>[
      ErpSummaryMetric(label: 'عدد الفواتير', value: '${filtered.length}'),
    ];
    for (final cur in ctrl.currencyPolicy.allowedCurrencies) {
      final byCur = filtered.where((s) => s.currency == cur);
      if (byCur.isNotEmpty) {
        final tot = byCur.fold<int>(0, (s, e) => s + e.totalAmountMinor);
        final rem = byCur.fold<int>(0, (s, e) => s + e.remainingAmountMinor);
        summaryMetrics.add(
          ErpSummaryMetric(
            label: 'إجمالي ${cur.code}',
            value: Money(tot, cur).format(),
            toneColor: pal.brand,
          ),
        );
        if (rem > 0) {
          summaryMetrics.add(
            ErpSummaryMetric(
              label: 'متبقي ${cur.code}',
              value: Money(rem, cur).format(),
              toneColor: pal.danger,
            ),
          );
        }
      }
    }

    return Column(
      children: [
        ErpSearchFilterBar(
          searchHint: 'بحث برقم الفاتورة، العميل، أو التاريخ...',
          onSearchChanged: (v) => setState(() => _query = v.trim()),
          trailingAction: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              PopupMenuButton<_SalesSortMode>(
                tooltip: 'ترتيب الفواتير',
                icon: const Icon(Icons.sort),
                onSelected: (m) => setState(() => _sortMode = m),
                itemBuilder: (_) => const [
                  PopupMenuItem<_SalesSortMode>(
                    value: _SalesSortMode.newest,
                    child: Text('الأحدث أولاً'),
                  ),
                  PopupMenuItem<_SalesSortMode>(
                    value: _SalesSortMode.highestTotal,
                    child: Text('الأعلى قيمة'),
                  ),
                  PopupMenuItem<_SalesSortMode>(
                    value: _SalesSortMode.highestRemaining,
                    child: Text('الأكبر ديناً متبقياً'),
                  ),
                ],
              ),
              IconButton.outlined(
                tooltip: 'تصدير Excel',
                onPressed: filtered.isEmpty
                    ? null
                    : () async {
                        final path = await SpreadsheetExportService.instance
                            .exportSalesXlsx(filtered);
                        await Share.shareXFiles(
                          [XFile(path)],
                          text: 'سجل المبيعات (.xlsx)',
                        );
                      },
                icon: const Icon(Icons.table_view_outlined),
              ),
            ],
          ),
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
            if (ctrl.currencyPolicy.canSwitchCurrency) ...[
              const SizedBox(width: 6),
              for (final cur in ctrl.currencyPolicy.allowedCurrencies)
                FilterChip(
                  label: Text(cur.code),
                  selected: _currencyFilter == cur,
                  onSelected: (on) => setState(
                    () => _currencyFilter = on ? cur : null,
                  ),
                ),
            ],
          ],
        ),
        if (filtered.isNotEmpty)
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 10, 16, 0),
            child: ErpSummaryBar(metrics: summaryMetrics),
          ),
        Expanded(
          child: filtered.isEmpty
              ? const EmptyLedgerState(
                  icon: Icons.receipt_long_outlined,
                  title: 'لا توجد فواتير مطابقة',
                  subtitle: 'أصدر فاتورة جديدة أو عدّل خيارات البحث والفلتر',
                )
              : ListView.separated(
                  padding: const EdgeInsets.all(16),
                  itemCount: filtered.length,
                  separatorBuilder: (_, idx) => const SizedBox(height: 10),
                  itemBuilder: (_, idx) {
                    final s = filtered[idx];
                    return Card(
                      child: InkWell(
                        onTap: () => _showSaleDetailsDialog(ctrl, s),
                        borderRadius: BorderRadius.circular(ErpRadius.lg),
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
                                      style: const TextStyle(
                                        fontWeight: FontWeight.w700,
                                        fontSize: 14,
                                      ),
                                    ),
                                  ),
                                  CurrencyBadge(currency: s.currency),
                                  const SizedBox(width: 6),
                                  StatusPill.forPayment(context, s.paymentType),
                                ],
                              ),
                              const SizedBox(height: 8),
                              Row(
                                children: [
                                  Text(
                                    s.dateIso,
                                    textDirection: TextDirection.ltr,
                                    style: TextStyle(
                                      fontSize: 12,
                                      color: pal.textSecondary,
                                    ),
                                  ),
                                  const SizedBox(width: 12),
                                  Expanded(
                                    child: Text(
                                      '${s.items.length} أصناف',
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                      style: TextStyle(
                                        fontSize: 12,
                                        color: pal.textSecondary,
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                              const SizedBox(height: 8),
                              Wrap(
                                spacing: 14,
                                runSpacing: 4,
                                children: [
                                  Text(
                                    'الإجمالي: ${Money(s.totalAmountMinor, s.currency).format()}',
                                    style: const TextStyle(
                                      fontWeight: FontWeight.w700,
                                      fontSize: 13,
                                      fontFeatures: [
                                        FontFeature.tabularFigures(),
                                      ],
                                    ),
                                  ),
                                  Text(
                                    'المدفوع: ${Money(s.paidAmountMinor, s.currency).format()}',
                                    style: TextStyle(
                                      fontSize: 12.5,
                                      color: pal.success,
                                      fontFeatures: const [
                                        FontFeature.tabularFigures(),
                                      ],
                                    ),
                                  ),
                                  if (s.remainingAmountMinor > 0)
                                    Text(
                                      'المتبقي: ${Money(s.remainingAmountMinor, s.currency).format()}',
                                      style: TextStyle(
                                        fontSize: 12.5,
                                        fontWeight: FontWeight.w700,
                                        color: pal.danger,
                                        fontFeatures: const [
                                          FontFeature.tabularFigures(),
                                        ],
                                      ),
                                    ),
                                ],
                              ),
                              const SizedBox(height: 10),
                              Wrap(
                                spacing: 8,
                                runSpacing: 6,
                                children: [
                                  OutlinedButton.icon(
                                    onPressed: () async {
                                      final bytes = await PdfExportService
                                          .instance
                                          .buildSaleInvoicePdf(
                                        agency: ctrl.settings,
                                        sale: s,
                                      );
                                      await Printing.layoutPdf(
                                        onLayout: (_) async => bytes,
                                      );
                                    },
                                    icon: const Icon(
                                      Icons.picture_as_pdf_outlined,
                                      size: 16,
                                    ),
                                    label: const Text('PDF'),
                                  ),
                                  if (ctrl.settings.savedPrinterAddress
                                      .isNotEmpty)
                                    OutlinedButton.icon(
                                      onPressed: () async {
                                        try {
                                          await EscPosService.instance
                                              .printSaleReceipt(
                                            agency: ctrl.settings,
                                            sale: s,
                                            printerAddress: ctrl
                                                .settings.savedPrinterAddress,
                                            paperMm:
                                                ctrl.settings.printerPaperMm,
                                          );
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
                                      icon: const Icon(
                                        Icons.print_outlined,
                                        size: 16,
                                      ),
                                      label: const Text('حراري'),
                                    ),
                                  OutlinedButton.icon(
                                    onPressed: () => _editSaleDialog(ctrl, s),
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
                                        title: 'أرشفة الفاتورة',
                                        message:
                                            'سيتم نقل الفاتورة ${s.invoiceNumber} إلى الأرشيف وتحديث رصيد العميل. هل تريد المتابعة؟',
                                        confirmLabel: 'أرشفة',
                                      );
                                      if (!ok) return;
                                      await ctrl.repo.setArchived(
                                        'sales',
                                        s.id,
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
                                      tooltip: 'حذف وإرجاع المخزون',
                                      onPressed: () async {
                                        final ok = await showErpConfirmDialog(
                                          context,
                                          title: 'حذف الفاتورة نهائياً',
                                          message:
                                              'سيتم حذف الفاتورة ${s.invoiceNumber} نهائياً وإرجاع الكميات للمخزون وعكس دين العميل. هل أنت متأكد؟',
                                          confirmLabel: 'حذف نهائي',
                                          destructive: true,
                                        );
                                        if (!ok) return;
                                        await ctrl.repo.deleteSale(s.id);
                                        await ctrl.reloadAll();
                                      },
                                      icon: Icon(
                                        Icons.delete_outline,
                                        color: pal.danger,
                                      ),
                                    ),
                                ],
                              ),
                            ],
                          ),
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
