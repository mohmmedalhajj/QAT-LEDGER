import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';
import 'package:share_plus/share_plus.dart';

import '../../core/money.dart';
import '../../core/theme.dart';
import '../../data/models.dart';
import '../../services/spreadsheet_export_service.dart';
import '../../state/app_controller.dart';
import '../widgets/common_widgets.dart';
import '../widgets/erp_components.dart';

class TransfersLedgerView extends StatefulWidget {
  const TransfersLedgerView({super.key});

  @override
  State<TransfersLedgerView> createState() => _TransfersLedgerViewState();
}

class _TransfersLedgerViewState extends State<TransfersLedgerView> {
  String _search = '';
  CurrencyCode? _filterCurrency;
  TransferStatus? _filterStatus;
  bool _onlyUnlinked = false;
  bool _showExchangeCalc = false;

  final _calcAmountCtrl = TextEditingController(text: '100');
  final _calcRateCtrl = TextEditingController();
  bool _sarToYer = true;

  @override
  void dispose() {
    _calcAmountCtrl.dispose();
    _calcRateCtrl.dispose();
    super.dispose();
  }

  Future<void> _openNewTransferDialog(AppController ctrl) async {
    final senderCtrl = TextEditingController(text: ctrl.settings.agencyName);
    final customRecCtrl = TextEditingController();
    final amountCtrl = TextEditingController();
    final feeCtrl = TextEditingController(text: '0');
    final companyCtrl = TextEditingController();
    final numberCtrl = TextEditingController();
    final notesCtrl = TextEditingController();

    int? selectedRecipientId;
    CurrencyCode currency = ctrl.currencyPolicy.defaultCurrency;
    TransferStatus status = TransferStatus.pending;

    await showDialog<void>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setModalState) => AlertDialog(
          title: const Text('تسجيل حوالة مالية'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                CurrencySelectorRow(
                  policy: ctrl.currencyPolicy,
                  selected: currency,
                  onChanged: (c) => setModalState(() => currency = c),
                ),
                const SizedBox(height: 8),
                DropdownButtonFormField<int?>(
                  key: ValueKey<String>(
                    'tr_rec_${ctrl.recipients.any((r) => r.id == selectedRecipientId) ? selectedRecipientId : 'none'}',
                  ),
                  initialValue:
                      ctrl.recipients.any((r) => r.id == selectedRecipientId)
                          ? selectedRecipientId
                          : null,
                  isExpanded: true,
                  decoration: const InputDecoration(
                    labelText: 'المستلم المسجل (اختياري)',
                  ),
                  items: [
                    const DropdownMenuItem<int?>(
                      value: null,
                      child: Text('بدون ربط بمستلم (حوالة عامة)'),
                    ),
                    ...ctrl.recipients.map(
                      (r) => DropdownMenuItem<int?>(
                        value: r.id,
                        child: Text(r.name),
                      ),
                    ),
                  ],
                  onChanged: (v) {
                    setModalState(() {
                      selectedRecipientId = v;
                      if (v != null) {
                        final rec =
                            ctrl.recipients.firstWhere((e) => e.id == v);
                        customRecCtrl.text = rec.name;
                        if (companyCtrl.text.isEmpty &&
                            rec.company.isNotEmpty) {
                          companyCtrl.text = rec.company;
                        }
                      }
                    });
                  },
                ),
                const SizedBox(height: 8),
                TextField(
                  controller: customRecCtrl,
                  decoration:
                      const InputDecoration(labelText: 'اسم المستلم الظاهر'),
                ),
                const SizedBox(height: 8),
                TextField(
                  controller: senderCtrl,
                  decoration: const InputDecoration(labelText: 'اسم المرسل *'),
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Expanded(
                      child: AccountingNumberField(
                        controller: amountCtrl,
                        label: 'المبلغ (${currency.shortSymbol}) *',
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: AccountingNumberField(
                        controller: feeCtrl,
                        label: 'الرسوم',
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Expanded(
                      child: TextField(
                        controller: companyCtrl,
                        decoration:
                            const InputDecoration(labelText: 'شركة الصرافة'),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: TextField(
                        controller: numberCtrl,
                        textDirection: TextDirection.ltr,
                        decoration:
                            const InputDecoration(labelText: 'رقم الحوالة'),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 10),
                SegmentedButton<TransferStatus>(
                  segments: const [
                    ButtonSegment<TransferStatus>(
                      value: TransferStatus.pending,
                      label: Text('جاري'),
                    ),
                    ButtonSegment<TransferStatus>(
                      value: TransferStatus.delivered,
                      label: Text('تم التسليم'),
                    ),
                    ButtonSegment<TransferStatus>(
                      value: TransferStatus.cancelled,
                      label: Text('ملغي'),
                    ),
                  ],
                  selected: {status},
                  onSelectionChanged: (s) =>
                      setModalState(() => status = s.first),
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
                final amountMinor = Money.parseMinorUnits(amountCtrl.text);
                if (amountMinor <= 0) {
                  ScaffoldMessenger.of(ctx).showSnackBar(
                    const SnackBar(
                      content: Text('أدخل مبلغ الحوالة أكبر من الصفر'),
                    ),
                  );
                  return;
                }
                await ctrl.repo.addTransfer(
                  senderName: senderCtrl.text,
                  receiverName: customRecCtrl.text,
                  recipientId: selectedRecipientId,
                  amountMinor: amountMinor,
                  feeMinor: Money.parseMinorUnits(feeCtrl.text),
                  currency: currency,
                  transferCompany: companyCtrl.text,
                  transferNumber: numberCtrl.text,
                  status: status,
                  notes: notesCtrl.text,
                );
                await ctrl.reloadAll();
                if (ctx.mounted) Navigator.pop(ctx);
              },
              child: const Text('حفظ الحوالة'),
            ),
          ],
        ),
      ),
    );
    senderCtrl.dispose();
    customRecCtrl.dispose();
    amountCtrl.dispose();
    feeCtrl.dispose();
    companyCtrl.dispose();
    numberCtrl.dispose();
    notesCtrl.dispose();
  }

  Widget _buildExchangeCalculatorCard(AppController ctrl, QatPalette pal) {
    final configuredRateMilli = ctrl.currencyPolicy.userDisplaySarToYerMilli;
    if (_calcRateCtrl.text.isEmpty && configuredRateMilli > 0) {
      _calcRateCtrl.text = QuantityMath.formatMilli(configuredRateMilli);
    }
    final inputMinor = Money.parseMinorUnits(_calcAmountCtrl.text);
    final rateMilli = QuantityMath.parseMilli(_calcRateCtrl.text);

    String outputStr = 'أدخل مبلغاً وسعر صرف';
    if (inputMinor > 0 && rateMilli > 0) {
      if (_sarToYer) {
        final yerMinor = (BigInt.from(inputMinor) * BigInt.from(rateMilli)) ~/
            BigInt.from(1000);
        if (yerMinor.isValidInt) {
          outputStr = Money(yerMinor.toInt(), CurrencyCode.yer).format();
        }
      } else {
        final sarMinor = (BigInt.from(inputMinor) * BigInt.from(1000)) ~/
            BigInt.from(rateMilli);
        if (sarMinor.isValidInt) {
          outputStr = Money(sarMinor.toInt(), CurrencyCode.sar).format();
        }
      }
    }

    return Card(
      color: pal.accentSoft,
      margin: const EdgeInsets.fromLTRB(16, 8, 16, 0),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Icon(Icons.currency_exchange, size: 18, color: pal.accent),
                const SizedBox(width: 8),
                const Expanded(
                  child: Text(
                    'حاسبة الصرف المرجعية (للعرض فقط — لا تخلط الدفاتر)',
                    style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700),
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.close, size: 18),
                  onPressed: () => setState(() => _showExchangeCalc = false),
                ),
              ],
            ),
            const SizedBox(height: 8),
            SegmentedButton<bool>(
              segments: const [
                ButtonSegment<bool>(
                  value: true,
                  label: Text('سعودي -> يمني'),
                ),
                ButtonSegment<bool>(
                  value: false,
                  label: Text('يمني -> سعودي'),
                ),
              ],
              selected: {_sarToYer},
              onSelectionChanged: (s) => setState(() => _sarToYer = s.first),
            ),
            const SizedBox(height: 10),
            Row(
              children: [
                Expanded(
                  child: AccountingNumberField(
                    controller: _calcAmountCtrl,
                    label: _sarToYer ? 'المبلغ (SAR)' : 'المبلغ (YER)',
                    onChanged: (_) => setState(() {}),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: AccountingNumberField(
                    controller: _calcRateCtrl,
                    label: 'سعر الصرف (1 SAR)',
                    onChanged: (_) => setState(() {}),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 10),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              decoration: BoxDecoration(
                color: pal.card,
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: pal.border),
              ),
              child: ErpDetailRow(
                label: 'المعادل التقديري',
                value: outputStr,
                bold: true,
                valueColor: pal.brand,
              ),
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
    final filtered = ctrl.transfers.where((t) {
      if (_filterCurrency != null && t.currency != _filterCurrency) {
        return false;
      }
      if (_filterStatus != null && t.status != _filterStatus) return false;
      if (_onlyUnlinked && !t.isUnlinked) return false;
      if (_search.isEmpty) return true;
      final q = _search.toLowerCase();
      return t.receiverName.toLowerCase().contains(q) ||
          t.senderName.toLowerCase().contains(q) ||
          t.transferCompany.toLowerCase().contains(q) ||
          t.transferNumber.toLowerCase().contains(q);
    }).toList();

    final summaryMetrics = <ErpSummaryMetric>[
      ErpSummaryMetric(label: 'الحوالات', value: '${filtered.length}'),
    ];
    for (final cur in ctrl.currencyPolicy.allowedCurrencies) {
      final byCur = filtered.where((t) => t.currency == cur);
      if (byCur.isNotEmpty) {
        final tot = byCur.fold<int>(0, (s, e) => s + e.amountMinor);
        final fees = byCur.fold<int>(0, (s, e) => s + e.feeMinor);
        summaryMetrics.add(
          ErpSummaryMetric(
            label: 'مبالغ ${cur.code}',
            value: Money(tot, cur).format(),
          ),
        );
        if (fees > 0) {
          summaryMetrics.add(
            ErpSummaryMetric(
              label: 'عمولات ${cur.code}',
              value: Money(fees, cur).format(),
              toneColor: pal.accent,
            ),
          );
        }
      }
    }

    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _openNewTransferDialog(ctrl),
        icon: const Icon(Icons.add),
        label: const Text('حوالة جديدة'),
      ),
      body: Column(
        children: [
          ErpSearchFilterBar(
            searchHint: 'بحث بالمرسل، المستلم، الشركة، أو رقم الحوالة...',
            onSearchChanged: (v) => setState(() => _search = v.trim()),
            trailingAction: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                IconButton.outlined(
                  tooltip: 'حاسبة الصرف المرجعية',
                  onPressed: () =>
                      setState(() => _showExchangeCalc = !_showExchangeCalc),
                  icon: Icon(
                    Icons.currency_exchange,
                    color: _showExchangeCalc ? pal.brand : null,
                  ),
                ),
                const SizedBox(width: 4),
                IconButton.outlined(
                  tooltip: 'تصدير Excel',
                  onPressed: filtered.isEmpty
                      ? null
                      : () async {
                          final path = await SpreadsheetExportService.instance
                              .exportTransfersXlsx(filtered);
                          await Share.shareXFiles(
                            [XFile(path)],
                            text: 'سجل الحوالات (.xlsx)',
                          );
                        },
                  icon: const Icon(Icons.table_view_outlined, size: 18),
                ),
                const SizedBox(width: 4),
                IconButton.outlined(
                  tooltip: 'تصدير CSV',
                  onPressed: filtered.isEmpty
                      ? null
                      : () async {
                          final path = await SpreadsheetExportService.instance
                              .exportTransfersCsv(filtered);
                          await Share.shareXFiles(
                            [XFile(path)],
                            text: 'سجل الحوالات (.csv)',
                          );
                        },
                  icon: const Icon(Icons.download_outlined, size: 18),
                ),
              ],
            ),
            filters: [
              FilterChip(
                label: const Text('غير مرتبطة'),
                selected: _onlyUnlinked,
                onSelected: (v) => setState(() => _onlyUnlinked = v),
              ),
              for (final st in TransferStatus.values)
                FilterChip(
                  label: Text(st.arabicLabel),
                  selected: _filterStatus == st,
                  onSelected: (v) =>
                      setState(() => _filterStatus = v ? st : null),
                ),
              if (ctrl.currencyPolicy.canSwitchCurrency)
                for (final c in CurrencyCode.values)
                  FilterChip(
                    label: Text(c.code),
                    selected: _filterCurrency == c,
                    onSelected: (v) =>
                        setState(() => _filterCurrency = v ? c : null),
                  ),
            ],
          ),
          if (_showExchangeCalc) _buildExchangeCalculatorCard(ctrl, pal),
          if (filtered.isNotEmpty)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 10, 16, 0),
              child: ErpSummaryBar(metrics: summaryMetrics),
            ),
          Expanded(
            child: filtered.isEmpty
                ? const EmptyLedgerState(
                    icon: Icons.swap_horiz,
                    title: 'لا توجد حوالات مطابقة',
                    subtitle: 'سجل الحوالات الواردة أو الصادرة وتابع حالة تسليمها',
                  )
                : ListView.separated(
                    padding: const EdgeInsets.fromLTRB(16, 10, 16, 84),
                    itemCount: filtered.length,
                    separatorBuilder: (_, idx) => const SizedBox(height: 10),
                    itemBuilder: (_, idx) {
                      final t = filtered[idx];
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
                                      t.receiverName.isEmpty
                                          ? 'حوالة غير محددة المستلم'
                                          : 'إلى: ${t.receiverName}',
                                      style: const TextStyle(
                                        fontWeight: FontWeight.w700,
                                        fontSize: 14,
                                      ),
                                    ),
                                  ),
                                  CurrencyBadge(currency: t.currency),
                                  const SizedBox(width: 6),
                                  StatusPill.forTransfer(context, t.status),
                                ],
                              ),
                              const SizedBox(height: 6),
                              Text(
                                'المبلغ: ${Money(t.amountMinor, t.currency).format()} • الرسوم: ${Money(t.feeMinor, t.currency).format()}\n'
                                'المرسل: ${t.senderName} • ${t.transferCompany} ${t.transferNumber.isNotEmpty ? '(#${t.transferNumber})' : ''} • ${t.dateIso}',
                                style: TextStyle(
                                  fontSize: 12,
                                  color: pal.textSecondary,
                                  fontFeatures: const [
                                    FontFeature.tabularFigures(),
                                  ],
                                ),
                              ),
                              if (t.isUnlinked)
                                Padding(
                                  padding: const EdgeInsets.only(top: 6),
                                  child: Wrap(
                                    spacing: 8,
                                    crossAxisAlignment:
                                        WrapCrossAlignment.center,
                                    children: [
                                      StatusPill(
                                        label: 'غير مرتبطة ببطاقة مستلم',
                                        bg: pal.warningSoft,
                                        fg: pal.warning,
                                      ),
                                      if (ctrl.recipients.isNotEmpty)
                                        ActionChip(
                                          avatar:
                                              const Icon(Icons.link, size: 15),
                                          label: const Text(
                                            'ربط بمستلم',
                                            style: TextStyle(fontSize: 11),
                                          ),
                                          onPressed: () async {
                                            final picked =
                                                await showDialog<int>(
                                              context: context,
                                              builder: (ctx) => SimpleDialog(
                                                title: const Text(
                                                  'اختر المستلم لربط الحوالة',
                                                ),
                                                children: ctrl.recipients
                                                    .map(
                                                      (r) =>
                                                          SimpleDialogOption(
                                                        onPressed: () =>
                                                            Navigator.pop(
                                                          ctx,
                                                          r.id,
                                                        ),
                                                        child: Text(r.name),
                                                      ),
                                                    )
                                                    .toList(),
                                              ),
                                            );
                                            if (picked != null) {
                                              await ctrl.repo
                                                  .linkTransferRecipient(
                                                transferId: t.id,
                                                recipientId: picked,
                                              );
                                              await ctrl.reloadAll();
                                            }
                                          },
                                        ),
                                    ],
                                  ),
                                ),
                              const SizedBox(height: 8),
                              Wrap(
                                spacing: 6,
                                children: [
                                  for (final st in TransferStatus.values)
                                    if (st != t.status)
                                      ActionChip(
                                        label: Text(
                                          st.arabicLabel,
                                          style:
                                              const TextStyle(fontSize: 11),
                                        ),
                                        onPressed: () async {
                                          if (st == TransferStatus.cancelled) {
                                            final ok =
                                                await showErpConfirmDialog(
                                              context,
                                              title: 'إلغاء الحوالة',
                                              message:
                                                  'هل تريد تحويل حالة الحوالة إلى ملغاة؟',
                                              confirmLabel: 'تأكيد الإلغاء',
                                              destructive: true,
                                            );
                                            if (!ok) return;
                                          }
                                          await ctrl.repo.updateTransferStatus(
                                            t.id,
                                            st,
                                          );
                                          await ctrl.reloadAll();
                                        },
                                      ),
                                  IconButton(
                                    tooltip: 'أرشفة',
                                    onPressed: () async {
                                      await ctrl.repo.setArchived(
                                        'money_transfers',
                                        t.id,
                                        true,
                                      );
                                      await ctrl.reloadAll();
                                    },
                                    icon: const Icon(
                                      Icons.archive_outlined,
                                      size: 18,
                                    ),
                                  ),
                                  if (ctrl.canDeleteCurrent)
                                    IconButton(
                                      tooltip: 'حذف',
                                      onPressed: () async {
                                        final ok = await showErpConfirmDialog(
                                          context,
                                          title: 'حذف الحوالة',
                                          message:
                                              'هل تريد حذف هذه الحوالة نهائياً؟',
                                          confirmLabel: 'حذف',
                                          destructive: true,
                                        );
                                        if (!ok) return;
                                        await ctrl.repo.deleteTransfer(t.id);
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
          ),
        ],
      ),
    );
  }
}
