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

class SupplierPurchasesView extends StatefulWidget {
  const SupplierPurchasesView({super.key});

  @override
  State<SupplierPurchasesView> createState() => _SupplierPurchasesViewState();
}

class _SupplierPurchasesViewState extends State<SupplierPurchasesView> {
  String _search = '';
  PaymentType? _paymentFilter;

  Future<void> _showPurchaseDetailsDialog(
    BuildContext context,
    AppController ctrl,
    SupplierPurchase p,
  ) async {
    final pal = context.pal;
    await showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Row(
          children: [
            Expanded(child: Text('تفاصيل شراء ${p.purchaseNumber}')),
            CurrencyBadge(currency: p.currency),
          ],
        ),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              ErpDetailRow(label: 'المورد', value: p.supplierName),
              ErpDetailRow(label: 'التاريخ', value: p.dateIso, ltr: true),
              ErpDetailRow(
                label: 'طريقة الدفع',
                value: p.effectivePaymentMethodLabel,
              ),
              if (p.bankName.isNotEmpty)
                ErpDetailRow(label: 'البنك / الصرافة', value: p.bankName),
              if (p.transferNumber.isNotEmpty)
                ErpDetailRow(
                  label: 'رقم الحوالة',
                  value: p.transferNumber,
                  ltr: true,
                ),
              const Divider(height: 14),
              for (final it in p.items)
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
                        Money(it.lineTotalMinor, p.currency).format(),
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
                label: 'الإجمالي الصافي',
                value: Money(p.totalAmountMinor, p.currency).format(),
                bold: true,
              ),
              ErpDetailRow(
                label: 'المدفوع للمورد',
                value: Money(p.paidAmountMinor, p.currency).format(),
                valueColor: pal.success,
              ),
              if (p.overpaymentSurplusMinor > 0)
                ErpDetailRow(
                  label: 'فائض لصالح الوكالة',
                  value: Money(p.overpaymentSurplusMinor, p.currency).format(),
                  valueColor: pal.info,
                )
              else
                ErpDetailRow(
                  label: 'المتبقي للمورد',
                  value: Money(p.remainingAmountMinor, p.currency).format(),
                  valueColor:
                      p.remainingAmountMinor > 0 ? pal.danger : pal.success,
                ),
              if (p.demarMinor > 0)
                ErpDetailRow(
                  label: 'الدمار',
                  value: Money(p.demarMinor, p.currency).format(),
                ),
              if (p.notes.isNotEmpty) ...[
                const SizedBox(height: 8),
                Text(
                  'ملاحظات: ${p.notes}',
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
                  .formatPurchaseMessage(ctrl.settings, p);
              await WhatsappShareService.instance.openWhatsApp(text: msg);
            },
            icon: const Icon(Icons.chat_outlined, size: 16),
            label: const Text('واتساب'),
          ),
          OutlinedButton.icon(
            onPressed: () async {
              final text = ThermalReceiptFormatter.buildSupplierPurchaseText(
                ctrl.settings,
                p,
              );
              await showReceiptPreviewPrintSheet(
                ctx,
                agency: ctrl.settings,
                title: 'شراء ${p.purchaseNumber}',
                receiptText: text,
              );
            },
            icon: const Icon(Icons.receipt_long_outlined, size: 16),
            label: const Text('حراري'),
          ),
          FilledButton.icon(
            onPressed: () async {
              final bytes = await PdfExportService.instance
                  .buildSupplierPurchasePdf(agency: ctrl.settings, purchase: p);
              await Printing.layoutPdf(
                name: PdfExportService.buildArabicPdfFileName(
                  'مشتريات_${p.purchaseNumber}',
                ),
                onLayout: (_) async => bytes,
              );
            },
            icon: const Icon(Icons.picture_as_pdf_outlined, size: 16),
            label: const Text('PDF'),
          ),
        ],
      ),
    );
  }

  Future<void> _editPurchaseDialog(
    BuildContext context,
    AppController ctrl,
    SupplierPurchase p,
  ) async {
    final lineDrafts = <({
      String unit,
      TextEditingController qatCtrl,
      TextEditingController qtyCtrl,
      TextEditingController buyCtrl,
      TextEditingController sellCtrl,
    })>[
      for (final it in p.items)
        (
          unit: it.unit,
          qatCtrl: TextEditingController(text: it.qatTypeName),
          qtyCtrl: TextEditingController(
            text: QuantityMath.formatMilli(it.qtyMilli),
          ),
          buyCtrl: TextEditingController(
            text: Money.formatMinor(it.buyPriceMinor, showZeroDecimals: false)
                .replaceAll(',', ''),
          ),
          sellCtrl: TextEditingController(),
        ),
    ];
    if (lineDrafts.isEmpty) {
      lineDrafts.add((
        unit: 'ربطة',
        qatCtrl: TextEditingController(),
        qtyCtrl: TextEditingController(text: '1'),
        buyCtrl: TextEditingController(text: '0'),
        sellCtrl: TextEditingController(),
      ));
    }
    final discCtrl = TextEditingController(
      text: Money.formatMinor(p.discountMinor, showZeroDecimals: false)
          .replaceAll(',', ''),
    );
    final paidCtrl = TextEditingController(
      text: Money.formatMinor(p.paidAmountMinor, showZeroDecimals: false)
          .replaceAll(',', ''),
    );
    final notesCtrl = TextEditingController(text: p.notes);
    final bankCtrl = TextEditingController(text: p.bankName);
    final transferNoCtrl = TextEditingController(text: p.transferNumber);
    final demarAmountCtrl = TextEditingController(
      text: p.demarMinor > 0
          ? Money.formatMinor(p.demarMinor, showZeroDecimals: false)
              .replaceAll(',', '')
          : '0',
    );
    final demarTypeCtrl = TextEditingController(text: p.demarType);
    final demarNoCtrl = TextEditingController(text: p.demarNumber);
    PaymentType pType = p.paymentType;
    String paymentMethod =
        p.paymentMethod.isNotEmpty ? p.paymentMethod : 'نقداً';
    int purchaseDateMs = p.dateMs;

    List<TextEditingController> allControllers() => [
          discCtrl,
          paidCtrl,
          bankCtrl,
          transferNoCtrl,
          demarAmountCtrl,
          demarTypeCtrl,
          demarNoCtrl,
          notesCtrl,
          for (final d in lineDrafts) ...[
            d.qatCtrl,
            d.qtyCtrl,
            d.buyCtrl,
            d.sellCtrl,
          ],
        ];

    await showDialog<void>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setModalState) => AlertDialog(
          title: Text('تعديل فاتورة شراء ${p.purchaseNumber}'),
          content: SizedBox(
            width: 440,
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  ErpDatePickerTile(
                    label: 'تاريخ الشراء',
                    dateMs: purchaseDateMs,
                    onChanged: (ms) =>
                        setModalState(() => purchaseDateMs = ms),
                  ),
                  const SizedBox(height: 8),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        'الأصناف (${lineDrafts.length})',
                        style: const TextStyle(
                          fontWeight: FontWeight.w700,
                          fontSize: 13,
                        ),
                      ),
                      TextButton.icon(
                        onPressed: () {
                          setModalState(() {
                            lineDrafts.add((
                              unit: 'ربطة',
                              qatCtrl: TextEditingController(),
                              qtyCtrl: TextEditingController(text: '1'),
                              buyCtrl: TextEditingController(text: '0'),
                              sellCtrl: TextEditingController(),
                            ));
                          });
                        },
                        icon: const Icon(Icons.add, size: 16),
                        label: const Text('إضافة صنف'),
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
                                    controller: lineDrafts[idx].qatCtrl,
                                    decoration: InputDecoration(
                                      isDense: true,
                                      labelText: 'الصنف #${idx + 1}',
                                    ),
                                  ),
                                ),
                                if (lineDrafts.length > 1)
                                  IconButton(
                                    icon: Icon(
                                      Icons.delete_outline,
                                      size: 18,
                                      color: ctx.pal.danger,
                                    ),
                                    onPressed: () {
                                      setModalState(() {
                                        final rem = lineDrafts.removeAt(idx);
                                        rem.qatCtrl.dispose();
                                        rem.qtyCtrl.dispose();
                                        rem.buyCtrl.dispose();
                                        rem.sellCtrl.dispose();
                                      });
                                    },
                                  ),
                              ],
                            ),
                            const SizedBox(height: 6),
                            Row(
                              children: [
                                Expanded(
                                  child: AccountingNumberField(
                                    controller: lineDrafts[idx].qtyCtrl,
                                    label: 'الكمية',
                                  ),
                                ),
                                const SizedBox(width: 6),
                                Expanded(
                                  child: AccountingNumberField(
                                    controller: lineDrafts[idx].buyCtrl,
                                    label: 'الشراء',
                                  ),
                                ),
                                const SizedBox(width: 6),
                                Expanded(
                                  child: AccountingNumberField(
                                    controller: lineDrafts[idx].sellCtrl,
                                    label: 'البيع',
                                  ),
                                ),
                              ],
                            ),
                          ],
                        ),
                      ),
                    ),
                  AccountingNumberField(
                    controller: discCtrl,
                    label: 'الخصم',
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
                  if (pType == PaymentType.partial ||
                      pType == PaymentType.paid) ...[
                    const SizedBox(height: 8),
                    AccountingNumberField(
                      controller: paidCtrl,
                      label: 'المدفوع',
                    ),
                  ],
                  if (pType != PaymentType.credit &&
                      paymentMethod != 'نقداً') ...[
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
                          decoration:
                              const InputDecoration(labelText: 'نوع الدمار'),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: TextField(
                          controller: demarNoCtrl,
                          textDirection: TextDirection.ltr,
                          decoration:
                              const InputDecoration(labelText: 'رقم الدمار'),
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
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('إلغاء'),
            ),
            FilledButton(
              onPressed: () async {
                final edited = <NewPurchaseItemInput>[];
                for (final d in lineDrafts) {
                  final qty = QuantityMath.parseMilli(d.qtyCtrl.text);
                  final buy = Money.parseMinorUnits(d.buyCtrl.text);
                  final sell = Money.parseMinorUnits(d.sellCtrl.text);
                  if (d.qatCtrl.text.trim().isEmpty || qty <= 0) {
                    ScaffoldMessenger.of(ctx).showSnackBar(
                      const SnackBar(
                        content: Text('أكمل اسم الصنف والكمية لكل بند'),
                      ),
                    );
                    return;
                  }
                  edited.add(
                    NewPurchaseItemInput(
                      qatTypeName: d.qatCtrl.text.trim(),
                      qtyMilli: qty,
                      unit: d.unit,
                      buyPriceMinor: buy,
                      sellPriceMinor: sell,
                    ),
                  );
                }
                await ctrl.repo.editSupplierPurchase(
                  purchaseId: p.id,
                  newItems: edited,
                  currency: p.currency,
                  discountMinor: Money.parseMinorUnits(discCtrl.text),
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
                  dateMs: purchaseDateMs,
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
    for (final c in allControllers()) {
      c.dispose();
    }
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
                      child: InkWell(
                        onTap: () =>
                            _showPurchaseDetailsDialog(context, ctrl, p),
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
                                '${p.dateIso} • الإجمالي: ${Money(p.totalAmountMinor, p.currency).format()} • ${p.overpaymentSurplusMinor > 0 ? 'فائض لنا: ${Money(p.overpaymentSurplusMinor, p.currency).format()}' : 'المتبقي: ${Money(p.remainingAmountMinor, p.currency).format()}'}'
                                '${p.demarMinor > 0 ? ' • دمار: ${Money(p.demarMinor, p.currency).format()}' : ''}',
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
                                runSpacing: 6,
                                children: [
                                  OutlinedButton.icon(
                                    onPressed: () async {
                                      final bytes = await PdfExportService
                                          .instance
                                          .buildSupplierPurchasePdf(
                                        agency: ctrl.settings,
                                        purchase: p,
                                      );
                                      await Printing.layoutPdf(
                                        name: PdfExportService
                                            .buildArabicPdfFileName(
                                          'مشتريات_${p.purchaseNumber}',
                                        ),
                                        onLayout: (_) async => bytes,
                                      );
                                    },
                                    icon: const Icon(
                                      Icons.picture_as_pdf_outlined,
                                      size: 16,
                                    ),
                                    label: const Text('PDF'),
                                  ),
                                  OutlinedButton.icon(
                                    onPressed: () async {
                                      final text = ThermalReceiptFormatter
                                          .buildSupplierPurchaseText(
                                        ctrl.settings,
                                        p,
                                      );
                                      await showReceiptPreviewPrintSheet(
                                        context,
                                        agency: ctrl.settings,
                                        title: 'سند شراء ${p.purchaseNumber}',
                                        receiptText: text,
                                      );
                                    },
                                    icon: const Icon(
                                      Icons.receipt_long_outlined,
                                      size: 16,
                                    ),
                                    label: const Text('حراري'),
                                  ),
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
                      ),
                    );
                  },
                ),
        ),
      ],
    );
  }
}
