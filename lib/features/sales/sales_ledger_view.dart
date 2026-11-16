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
        ],
      ),
    );
  }

  Future<void> _editSaleDialog(AppController ctrl, Sale sale) async {
    final first = sale.items.isEmpty ? null : sale.items.first;
    final itemCtrl = TextEditingController(text: first?.qatTypeName ?? '');
    final qtyCtrl = TextEditingController(
      text: first != null ? QuantityMath.formatMilli(first.qtyMilli) : '1',
    );
    final priceCtrl = TextEditingController(
      text: first != null
          ? Money.formatMinor(first.unitPriceMinor, showZeroDecimals: false)
              .replaceAll(',', '')
          : '0',
    );
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
                  key: ValueKey<String>(
                    'edit_sale_cust_${ctrl.customers.any((c) => c.id == custId) ? custId : 'none'}',
                  ),
                  initialValue: ctrl.customers.any((c) => c.id == custId)
                      ? custId
                      : null,
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
                const SizedBox(height: 8),
                TextField(
                  controller: itemCtrl,
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
                        controller: priceCtrl,
                        label: 'السعر',
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
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
                final price = Money.parseMinorUnits(priceCtrl.text);
                if (itemCtrl.text.trim().isEmpty || qty <= 0) return;
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
                final extraItems = sale.items.length > 1
                    ? sale.items
                        .skip(1)
                        .map(
                          (e) => NewSaleItemInput(
                            inventoryBatchId: e.inventoryBatchId,
                            qatTypeName: e.qatTypeName,
                            qtyMilli: e.qtyMilli,
                            unit: e.unit,
                            unitPriceMinor: e.unitPriceMinor,
                            buyPriceMinor: e.buyPriceMinor,
                          ),
                        )
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
    itemCtrl.dispose();
    qtyCtrl.dispose();
    priceCtrl.dispose();
    discCtrl.dispose();
    taxCtrl.dispose();
    paidCtrl.dispose();
    notesCtrl.dispose();
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
                                  Text(
                                    '${s.items.length} أصناف',
                                    style: TextStyle(
                                      fontSize: 12,
                                      color: pal.textSecondary,
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
