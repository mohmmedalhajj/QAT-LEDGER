import 'dart:async';

import 'package:material_ui/material_ui.dart';
import 'package:printing/printing.dart';
import 'package:provider/provider.dart';
import 'package:share_plus/share_plus.dart';

import '../core/date_utils.dart';
import '../core/money.dart';
import '../core/platform_bridge.dart';
import '../core/theme.dart';
import '../data/models.dart';
import '../services/pdf_export_service.dart';
import '../services/spreadsheet_export_service.dart';
import '../services/whatsapp_share_service.dart';
import '../state/app_controller.dart';
import 'widgets/common_widgets.dart';

class TransfersReportsTab extends StatelessWidget {
  const TransfersReportsTab({super.key});

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final subTab = ctrl.transfersSubTab.clamp(0, 4);

    return Column(
      children: [
        SegmentedStrip(
          tabs: const [
            'سجل الحوالات',
            'المستلمون وتقاريرهم',
            'التقارير المالية',
            'الضرائب',
            'الأرشيف',
          ],
          selectedIndex: subTab,
          onChanged: ctrl.setTransfersSubTab,
        ),
        Expanded(
          child: IndexedStack(
            index: subTab,
            children: const [
              _TransfersLedgerView(),
              _RecipientsAndReportsView(),
              _FinancialReportsView(),
              _TaxLedgerView(),
              _ArchiveCenterView(),
            ],
          ),
        ),
      ],
    );
  }
}

// -----------------------------------------------------------------------------
// 1. Money Transfers Ledger
// -----------------------------------------------------------------------------

class _TransfersLedgerView extends StatefulWidget {
  const _TransfersLedgerView();

  @override
  State<_TransfersLedgerView> createState() => _TransfersLedgerViewState();
}

class _TransfersLedgerViewState extends State<_TransfersLedgerView> {
  CurrencyCode? _filterCurrency;
  TransferStatus? _filterStatus;
  bool _onlyUnlinked = false;

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
                  initialValue: selectedRecipientId,
                  decoration: const InputDecoration(labelText: 'المستلم المسجل (اختياري)'),
                  items: [
                    const DropdownMenuItem<int?>(
                        value: null, child: Text('بدون ربط بمستلم (حوالة عامة)')),
                    ...ctrl.recipients.map(
                      (r) => DropdownMenuItem<int?>(value: r.id, child: Text(r.name)),
                    ),
                  ],
                  onChanged: (v) {
                    setModalState(() {
                      selectedRecipientId = v;
                      if (v != null) {
                        final rec = ctrl.recipients.firstWhere((e) => e.id == v);
                        customRecCtrl.text = rec.name;
                        if (companyCtrl.text.isEmpty && rec.company.isNotEmpty) {
                          companyCtrl.text = rec.company;
                        }
                      }
                    });
                  },
                ),
                const SizedBox(height: 8),
                TextField(
                  controller: customRecCtrl,
                  decoration: const InputDecoration(labelText: 'اسم المستلم الظاهر'),
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
                      child: AccountingNumberField(controller: feeCtrl, label: 'الرسوم'),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Expanded(
                      child: TextField(
                        controller: companyCtrl,
                        decoration: const InputDecoration(labelText: 'شركة الصرافة'),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: TextField(
                        controller: numberCtrl,
                        textDirection: TextDirection.ltr,
                        decoration: const InputDecoration(labelText: 'رقم الحوالة'),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 10),
                SegmentedButton<TransferStatus>(
                  segments: const [
                    ButtonSegment(value: TransferStatus.pending, label: Text('جاري')),
                    ButtonSegment(value: TransferStatus.delivered, label: Text('تم التسليم')),
                    ButtonSegment(value: TransferStatus.cancelled, label: Text('ملغي')),
                  ],
                  selected: {status},
                  onSelectionChanged: (s) => setModalState(() => status = s.first),
                ),
                const SizedBox(height: 8),
                TextField(controller: notesCtrl, decoration: const InputDecoration(labelText: 'ملاحظات')),
              ],
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('إلغاء')),
            FilledButton(
              onPressed: () async {
                final amountMinor = Money.parseMinorUnits(amountCtrl.text);
                if (amountMinor <= 0) return;
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
  }

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final pal = context.pal;
    final filtered = ctrl.transfers.where((t) {
      if (_filterCurrency != null && t.currency != _filterCurrency) return false;
      if (_filterStatus != null && t.status != _filterStatus) return false;
      if (_onlyUnlinked && !t.isUnlinked) return false;
      return true;
    }).toList();

    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _openNewTransferDialog(ctrl),
        icon: const Icon(Icons.add),
        label: const Text('حوالة جديدة'),
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 10, 16, 6),
            child: SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: [
                  FilterChip(
                    label: const Text('غير مرتبطة'),
                    selected: _onlyUnlinked,
                    onSelected: (v) => setState(() => _onlyUnlinked = v),
                  ),
                  const SizedBox(width: 6),
                  for (final st in TransferStatus.values) ...[
                    FilterChip(
                      label: Text(st.arabicLabel),
                      selected: _filterStatus == st,
                      onSelected: (v) => setState(() => _filterStatus = v ? st : null),
                    ),
                    const SizedBox(width: 6),
                  ],
                  if (ctrl.currencyPolicy.canSwitchCurrency)
                    for (final c in CurrencyCode.values) ...[
                      FilterChip(
                        label: Text(c.code),
                        selected: _filterCurrency == c,
                        onSelected: (v) => setState(() => _filterCurrency = v ? c : null),
                      ),
                      const SizedBox(width: 6),
                    ],
                  IconButton.outlined(
                    tooltip: 'تصدير Excel',
                    onPressed: filtered.isEmpty
                        ? null
                        : () async {
                            final path = await SpreadsheetExportService.instance
                                .exportTransfersXlsx(filtered);
                            await Share.shareXFiles([XFile(path)], text: 'سجل الحوالات (.xlsx)');
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
                            await Share.shareXFiles([XFile(path)], text: 'سجل الحوالات (.csv)');
                          },
                    icon: const Icon(Icons.download_outlined, size: 18),
                  ),
                ],
              ),
            ),
          ),
          Expanded(
            child: filtered.isEmpty
                ? const EmptyLedgerState(
                    icon: Icons.swap_horiz,
                    title: 'لا توجد حوالات مطابقة',
                    subtitle: 'سجل الحوالات الواردة أو الصادرة وتابع حالة تسليمها',
                  )
                : ListView.separated(
                    padding: const EdgeInsets.fromLTRB(16, 8, 16, 84),
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
                                      style: const TextStyle(fontWeight: FontWeight.w700),
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
                                style: TextStyle(fontSize: 12, color: pal.textSecondary),
                              ),
                              if (t.isUnlinked)
                                Padding(
                                  padding: const EdgeInsets.only(top: 6),
                                  child: Wrap(
                                    spacing: 8,
                                    crossAxisAlignment: WrapCrossAlignment.center,
                                    children: [
                                      StatusPill(
                                        label: 'غير مرتبطة ببطاقة مستلم',
                                        bg: pal.warningSoft,
                                        fg: pal.warning,
                                      ),
                                      if (ctrl.recipients.isNotEmpty)
                                        ActionChip(
                                          avatar: const Icon(Icons.link, size: 15),
                                          label: const Text('ربط بمستلم',
                                              style: TextStyle(fontSize: 11)),
                                          onPressed: () async {
                                            final picked = await showDialog<int>(
                                              context: context,
                                              builder: (ctx) => SimpleDialog(
                                                title: const Text('اختر المستلم لربط الحوالة'),
                                                children: ctrl.recipients
                                                    .map(
                                                      (r) => SimpleDialogOption(
                                                        onPressed: () => Navigator.pop(ctx, r.id),
                                                        child: Text(r.name),
                                                      ),
                                                    )
                                                    .toList(),
                                              ),
                                            );
                                            if (picked != null) {
                                              await ctrl.repo.linkTransferRecipient(
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
                                        label: Text(st.arabicLabel,
                                            style: const TextStyle(fontSize: 11)),
                                        onPressed: () async {
                                          await ctrl.repo.updateTransferStatus(t.id, st);
                                          await ctrl.reloadAll();
                                        },
                                      ),
                                  IconButton(
                                    tooltip: 'أرشفة',
                                    onPressed: () async {
                                      await ctrl.repo.setArchived('money_transfers', t.id, true);
                                      await ctrl.reloadAll();
                                    },
                                    icon: const Icon(Icons.archive_outlined, size: 18),
                                  ),
                                  IconButton(
                                    onPressed: () async {
                                      await ctrl.repo.deleteTransfer(t.id);
                                      await ctrl.reloadAll();
                                    },
                                    icon: Icon(Icons.delete_outline, color: pal.danger, size: 18),
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
// 2. Transfer Recipients & Status Reports
// -----------------------------------------------------------------------------

class _RecipientsAndReportsView extends StatelessWidget {
  const _RecipientsAndReportsView();

  Future<void> _openRecipientDialog(
    BuildContext context,
    AppController ctrl, {
    TransferRecipient? existing,
  }) async {
    final nameCtrl = TextEditingController(text: existing?.name ?? '');
    final phoneCtrl = TextEditingController(text: existing?.phone ?? '');
    final compCtrl = TextEditingController(text: existing?.company ?? '');
    final notesCtrl = TextEditingController(text: existing?.notes ?? '');

    await showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(existing == null ? 'مستلم حوالات جديد' : 'تعديل بيانات المستلم'),
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
              const SizedBox(height: 8),
              TextField(controller: nameCtrl, decoration: const InputDecoration(labelText: 'الاسم *')),
              const SizedBox(height: 8),
              TextField(
                controller: phoneCtrl,
                textDirection: TextDirection.ltr,
                keyboardType: TextInputType.phone,
                decoration: const InputDecoration(labelText: 'الهاتف'),
              ),
              const SizedBox(height: 8),
              TextField(
                  controller: compCtrl, decoration: const InputDecoration(labelText: 'شركة الصرافة المعتمدة')),
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
              await ctrl.repo.upsertTransferRecipient(
                id: existing?.id,
                name: nameCtrl.text,
                phone: phoneCtrl.text,
                company: compCtrl.text,
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

  Future<void> _openRecipientReportSheet(
    BuildContext context,
    AppController ctrl,
    int recipientId,
  ) async {
    await Navigator.of(context).push<void>(
      MaterialPageRoute<void>(
        builder: (_) => _RecipientStatusReportPage(recipientId: recipientId),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final pal = context.pal;

    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _openRecipientDialog(context, ctrl),
        icon: const Icon(Icons.person_add_alt_1),
        label: const Text('إضافة مستلم'),
      ),
      body: ctrl.recipientReports.isEmpty
          ? const EmptyLedgerState(
              icon: Icons.badge_outlined,
              title: 'لا يوجد مستلمون مسجلون',
              subtitle: 'أضف مستلمي الحوالات لمتابعة تقاريرهم وحالات التسليم بسهولة',
            )
          : ListView.separated(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 84),
              itemCount: ctrl.recipientReports.length,
              separatorBuilder: (_, idx) => const SizedBox(height: 10),
              itemBuilder: (_, idx) {
                final rep = ctrl.recipientReports[idx];
                final r = rep.recipient;
                return Card(
                  child: Padding(
                    padding: const EdgeInsets.all(14),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Expanded(
                              child: Text(r.name, style: const TextStyle(fontWeight: FontWeight.w700)),
                            ),
                            IconButton(
                              onPressed: () => _openRecipientDialog(context, ctrl, existing: r),
                              icon: const Icon(Icons.edit_outlined, size: 18),
                            ),
                          ],
                        ),
                        if (r.phone.isNotEmpty || r.company.isNotEmpty)
                          Text(
                            '${r.phone} ${r.company.isNotEmpty ? '• ${r.company}' : ''}'.trim(),
                            style: TextStyle(fontSize: 12, color: pal.textSecondary),
                          ),
                        const SizedBox(height: 8),
                        Wrap(
                          spacing: 6,
                          runSpacing: 6,
                          children: [
                            StatusPill(
                              label: 'مسلمة: ${rep.deliveredCount}',
                              bg: pal.successSoft,
                              fg: pal.success,
                            ),
                            StatusPill(
                              label: 'معلقة: ${rep.pendingCount}',
                              bg: pal.warningSoft,
                              fg: pal.warning,
                            ),
                            StatusPill(
                              label: 'ملغاة: ${rep.cancelledCount}',
                              bg: pal.dangerSoft,
                              fg: pal.danger,
                            ),
                          ],
                        ),
                        const SizedBox(height: 10),
                        FilledButton.tonalIcon(
                          onPressed: () => _openRecipientReportSheet(context, ctrl, r.id),
                          icon: const Icon(Icons.assessment_outlined, size: 18),
                          label: const Text('فتح تقرير المستلم الكامل (PDF / Excel / الحالة)'),
                        ),
                      ],
                    ),
                  ),
                );
              },
            ),
    );
  }
}

enum _ReportPeriodPreset {
  all('الكل'),
  daily('اليوم'),
  weekly('أسبوعي'),
  monthly('شهري'),
  yearly('سنوي'),
  custom('مخصص');

  const _ReportPeriodPreset(this.arabicLabel);
  final String arabicLabel;
}

({String? fromIso, String? toIso}) _resolvePresetRange(_ReportPeriodPreset preset) {
  final now = DateTime.now();
  final today = QatDateUtils.toIsoDate(now);
  switch (preset) {
    case _ReportPeriodPreset.all:
      return (fromIso: null, toIso: null);
    case _ReportPeriodPreset.daily:
      return (fromIso: today, toIso: today);
    case _ReportPeriodPreset.weekly:
      final weekStart = now.subtract(const Duration(days: 6));
      return (fromIso: QatDateUtils.toIsoDate(weekStart), toIso: today);
    case _ReportPeriodPreset.monthly:
      final monthStart = DateTime(now.year, now.month, 1);
      return (fromIso: QatDateUtils.toIsoDate(monthStart), toIso: today);
    case _ReportPeriodPreset.yearly:
      final yearStart = DateTime(now.year, 1, 1);
      return (fromIso: QatDateUtils.toIsoDate(yearStart), toIso: today);
    case _ReportPeriodPreset.custom:
      return (fromIso: null, toIso: null);
  }
}

class _RecipientStatusReportPage extends StatefulWidget {
  const _RecipientStatusReportPage({required this.recipientId});
  final int recipientId;

  @override
  State<_RecipientStatusReportPage> createState() => _RecipientStatusReportPageState();
}

class _RecipientStatusReportPageState extends State<_RecipientStatusReportPage> {
  _ReportPeriodPreset _preset = _ReportPeriodPreset.all;
  String? _fromIso;
  String? _toIso;
  TransferRecipientSummary? _summary;

  @override
  void initState() {
    super.initState();
    unawaited(_load());
  }

  Future<void> _load() async {
    final s = await context.read<AppController>().repo.getRecipientReport(
          widget.recipientId,
          fromIso: _fromIso,
          toIso: _toIso,
        );
    if (!mounted) return;
    setState(() => _summary = s);
  }

  Future<void> _selectPreset(_ReportPeriodPreset preset) async {
    if (preset == _ReportPeriodPreset.custom) {
      await _pickDates();
      return;
    }
    final range = _resolvePresetRange(preset);
    setState(() {
      _preset = preset;
      _fromIso = range.fromIso;
      _toIso = range.toIso;
    });
    await _load();
  }

  Future<void> _pickDates() async {
    final now = DateTime.now();
    final range = await showDateRangePicker(
      context: context,
      firstDate: DateTime(2020),
      lastDate: DateTime(now.year + 1),
    );
    if (range == null || !mounted) return;
    setState(() {
      _preset = _ReportPeriodPreset.custom;
      _fromIso = QatDateUtils.toIsoDate(range.start);
      _toIso = QatDateUtils.toIsoDate(range.end);
    });
    await _load();
  }

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final pal = context.pal;
    final s = _summary;

    return Scaffold(
      appBar: AppBar(
        title: Text(s == null ? 'تقرير المستلم' : 'تقرير: ${s.recipient.name}'),
        actions: [
          IconButton(
            tooltip: 'تصفية مخصصة بالتاريخ',
            onPressed: _pickDates,
            icon: const Icon(Icons.date_range_outlined),
          ),
        ],
      ),
      body: s == null
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  child: Row(
                    children: [
                      for (final p in _ReportPeriodPreset.values) ...[
                        ChoiceChip(
                          label: Text(p.arabicLabel),
                          selected: _preset == p,
                          onSelected: (_) => _selectPreset(p),
                        ),
                        const SizedBox(width: 6),
                      ],
                    ],
                  ),
                ),
                const SizedBox(height: 10),
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(14),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('ملخص الحوالات والحالات',
                            style: Theme.of(context).textTheme.titleMedium),
                        const SizedBox(height: 8),
                        Wrap(
                          spacing: 8,
                          runSpacing: 6,
                          children: [
                            StatusPill(
                                label: 'تم التسليم: ${s.deliveredCount}',
                                bg: pal.successSoft,
                                fg: pal.success),
                            StatusPill(
                                label: 'جاري التسليم: ${s.pendingCount}',
                                bg: pal.warningSoft,
                                fg: pal.warning),
                            StatusPill(
                                label: 'ملغي: ${s.cancelledCount}',
                                bg: pal.dangerSoft,
                                fg: pal.danger),
                          ],
                        ),
                        const SizedBox(height: 10),
                        for (final cur in CurrencyCode.values)
                          if ((s.deliveredByCurrency[cur] ?? 0) != 0 ||
                              (s.pendingByCurrency[cur] ?? 0) != 0)
                            Padding(
                              padding: const EdgeInsets.only(bottom: 4),
                              child: Text(
                                '${cur.code}: مسلّم ${Money(s.deliveredByCurrency[cur] ?? 0, cur).format()} • معلق ${Money(s.pendingByCurrency[cur] ?? 0, cur).format()}',
                                style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600),
                              ),
                            ),
                        const SizedBox(height: 10),
                        Wrap(
                          spacing: 8,
                          runSpacing: 8,
                          children: [
                            FilledButton.icon(
                              onPressed: () async {
                                final pdf = await PdfExportService.instance
                                    .buildTransferRecipientReportPdf(
                                  agency: ctrl.settings,
                                  summary: s,
                                  fromIso: _fromIso,
                                  toIso: _toIso,
                                );
                                await Printing.layoutPdf(onLayout: (_) async => pdf);
                              },
                              icon: const Icon(Icons.picture_as_pdf_outlined, size: 16),
                              label: const Text('PDF'),
                            ),
                            OutlinedButton.icon(
                              onPressed: () async {
                                final path = await SpreadsheetExportService.instance
                                    .exportRecipientReportXlsx(s);
                                await Share.shareXFiles([XFile(path)], text: 'تقرير مستلم الحوالات');
                              },
                              icon: const Icon(Icons.table_view_outlined, size: 16),
                              label: const Text('Excel'),
                            ),
                            OutlinedButton.icon(
                              onPressed: () async {
                                final msg = WhatsappShareService.instance
                                    .formatRecipientSummaryMessage(ctrl.settings, s);
                                await WhatsappShareService.instance.openWhatsApp(
                                  phone: s.recipient.phone,
                                  text: msg,
                                );
                              },
                              icon: const Icon(Icons.chat_outlined, size: 16),
                              label: const Text('واتساب'),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 12),
                for (final t in s.transfers)
                  Card(
                    margin: const EdgeInsets.only(bottom: 8),
                    child: ListTile(
                      title: Row(
                        children: [
                          Expanded(
                            child: Text(
                              '${Money(t.amountMinor, t.currency).format()} — ${t.dateIso}',
                              style: const TextStyle(fontWeight: FontWeight.w700),
                            ),
                          ),
                          StatusPill.forTransfer(context, t.status),
                        ],
                      ),
                      subtitle: Text(
                        'المرسل: ${t.senderName} • ${t.transferCompany} ${t.transferNumber.isNotEmpty ? '(#${t.transferNumber})' : ''}',
                      ),
                    ),
                  ),
              ],
            ),
    );
  }
}

// -----------------------------------------------------------------------------
// 3. Financial Reports
// -----------------------------------------------------------------------------

class _FinancialReportsView extends StatefulWidget {
  const _FinancialReportsView();

  @override
  State<_FinancialReportsView> createState() => _FinancialReportsViewState();
}

class _FinancialReportsViewState extends State<_FinancialReportsView> {
  _ReportPeriodPreset _preset = _ReportPeriodPreset.all;
  String? _fromIso;
  String? _toIso;
  Map<CurrencyCode, CurrencyDashboardMetrics>? _metrics;

  @override
  void initState() {
    super.initState();
    unawaited(_load());
  }

  Future<void> _load() async {
    final res = await context.read<AppController>().repo.getDashboardMetrics(
          fromIso: _fromIso,
          toIso: _toIso,
        );
    if (!mounted) return;
    setState(() => _metrics = res);
  }

  Future<void> _selectPreset(_ReportPeriodPreset preset) async {
    if (preset == _ReportPeriodPreset.custom) {
      final range = await showDateRangePicker(
        context: context,
        firstDate: DateTime(2020),
        lastDate: DateTime(DateTime.now().year + 1),
      );
      if (range == null || !mounted) return;
      setState(() {
        _preset = _ReportPeriodPreset.custom;
        _fromIso = QatDateUtils.toIsoDate(range.start);
        _toIso = QatDateUtils.toIsoDate(range.end);
      });
      await _load();
      return;
    }
    final range = _resolvePresetRange(preset);
    setState(() {
      _preset = preset;
      _fromIso = range.fromIso;
      _toIso = range.toIso;
    });
    await _load();
  }

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    if (!ctrl.canViewReportsCurrent) {
      return const EmptyLedgerState(
        icon: Icons.lock_outline,
        title: 'التقارير مقيدة بالصلاحيات',
        subtitle: 'تم إخفاء التقارير المالية لهذا المستخدم من إدارة الصلاحيات',
      );
    }
    final data = _metrics;

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Row(
            children: [
              for (final p in _ReportPeriodPreset.values) ...[
                ChoiceChip(
                  label: Text(p.arabicLabel),
                  selected: _preset == p,
                  onSelected: (_) => _selectPreset(p),
                ),
                const SizedBox(width: 6),
              ],
            ],
          ),
        ),
        const SizedBox(height: 10),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    'الفترة: ${_fromIso ?? 'الكل'} إلى ${_toIso ?? 'اليوم'}',
                    style: const TextStyle(fontWeight: FontWeight.w600),
                  ),
                ),
                OutlinedButton.icon(
                  onPressed: () => _selectPreset(_ReportPeriodPreset.custom),
                  icon: const Icon(Icons.date_range_outlined, size: 16),
                  label: const Text('تحديد الفترة'),
                ),
                const SizedBox(width: 6),
                if (data != null)
                  IconButton.filled(
                    tooltip: 'تصدير PDF',
                    onPressed: () async {
                      final pdf = await PdfExportService.instance.buildFinancialReportPdf(
                        agency: ctrl.settings,
                        metricsByCurrency: data,
                        fromIso: _fromIso,
                        toIso: _toIso,
                      );
                      await Printing.layoutPdf(onLayout: (_) async => pdf);
                    },
                    icon: const Icon(Icons.picture_as_pdf_outlined, size: 18),
                  ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 12),
        if (data == null)
          const Center(child: CircularProgressIndicator())
        else
          for (final cur in ctrl.currencyPolicy.allowedCurrencies)
            if (data[cur] != null) ...[
              Card(
                margin: const EdgeInsets.only(bottom: 12),
                child: Padding(
                  padding: const EdgeInsets.all(14),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          CurrencyBadge(currency: cur),
                          const SizedBox(width: 8),
                          Text('ملخص ${cur.arabicName}',
                              style: Theme.of(context).textTheme.titleMedium),
                        ],
                      ),
                      const Divider(height: 18),
                      _ReportRow('إجمالي المبيعات', Money(data[cur]!.salesTotalMinor, cur).format()),
                      _ReportRow('تكلفة المبيعات المقدرة',
                          Money(data[cur]!.estimatedCogsMinor, cur).format()),
                      _ReportRow('المصروفات', Money(data[cur]!.expensesMinor, cur).format()),
                      _ReportRow('الضرائب', Money(data[cur]!.taxMinor, cur).format()),
                      _ReportRow('صافي الربح التقديري',
                          Money(data[cur]!.netProfitMinor, cur).format(),
                          bold: true),
                    ],
                  ),
                ),
              ),
            ],
      ],
    );
  }
}

class _ReportRow extends StatelessWidget {
  const _ReportRow(this.label, this.value, {this.bold = false});
  final String label;
  final String value;
  final bool bold;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: TextStyle(fontWeight: bold ? FontWeight.w700 : FontWeight.w500)),
          Text(value,
              style: TextStyle(fontWeight: bold ? FontWeight.w700 : FontWeight.w600)),
        ],
      ),
    );
  }
}

// -----------------------------------------------------------------------------
// 4. Tax Ledger
// -----------------------------------------------------------------------------

class _TaxLedgerView extends StatelessWidget {
  const _TaxLedgerView();

  Future<void> _addTaxDialog(BuildContext context, AppController ctrl) async {
    final qtyCtrl = TextEditingController(text: '0');
    final rateCtrl = TextEditingController(text: '0');
    final amountCtrl = TextEditingController();
    final notesCtrl = TextEditingController();
    CurrencyCode cur = ctrl.currencyPolicy.defaultCurrency;

    await showDialog<void>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setModalState) => AlertDialog(
          title: const Text('قيد ضريبة قات'),
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
                AccountingNumberField(controller: qtyCtrl, label: 'الكمية الإجمالية'),
                const SizedBox(height: 8),
                AccountingNumberField(controller: rateCtrl, label: 'فئة الضريبة'),
                const SizedBox(height: 8),
                AccountingNumberField(controller: amountCtrl, label: 'إجمالي مبلغ الضريبة *'),
                const SizedBox(height: 8),
                TextField(controller: notesCtrl, decoration: const InputDecoration(labelText: 'ملاحظات')),
              ],
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('إلغاء')),
            FilledButton(
              onPressed: () async {
                final taxMinor = Money.parseMinorUnits(amountCtrl.text);
                if (taxMinor <= 0) return;
                await ctrl.repo.addTaxEntry(
                  totalQtyMilli: QuantityMath.parseMilli(qtyCtrl.text),
                  taxRateMinor: Money.parseMinorUnits(rateCtrl.text),
                  taxAmountMinor: taxMinor,
                  currency: cur,
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

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final pal = context.pal;

    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _addTaxDialog(context, ctrl),
        icon: const Icon(Icons.add),
        label: const Text('قيد ضريبة'),
      ),
      body: ctrl.taxEntries.isEmpty
          ? const EmptyLedgerState(
              icon: Icons.account_balance_outlined,
              title: 'سجل الضرائب فارغ',
              subtitle: 'وثق قيود الضرائب اليومية منفصلة حسب العملة',
            )
          : ListView.separated(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 84),
              itemCount: ctrl.taxEntries.length,
              separatorBuilder: (_, idx) => const SizedBox(height: 8),
              itemBuilder: (_, idx) {
                final t = ctrl.taxEntries[idx];
                return Card(
                  child: ListTile(
                    title: Row(
                      children: [
                        Expanded(
                          child: Text(
                            Money(t.taxAmountMinor, t.currency).format(),
                            style: const TextStyle(fontWeight: FontWeight.w700),
                          ),
                        ),
                        CurrencyBadge(currency: t.currency),
                      ],
                    ),
                    subtitle: Text('${t.dateIso} • ${t.source} ${t.notes}'),
                    trailing: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        IconButton(
                          tooltip: 'أرشفة',
                          onPressed: () async {
                            await ctrl.repo.setArchived('qat_tax_entries', t.id, true);
                            await ctrl.reloadAll();
                          },
                          icon: const Icon(Icons.archive_outlined, size: 18),
                        ),
                        IconButton(
                          onPressed: () async {
                            await ctrl.repo.deleteTaxEntry(t.id);
                            await ctrl.reloadAll();
                          },
                          icon: Icon(Icons.delete_outline, color: pal.danger, size: 18),
                        ),
                      ],
                    ),
                  ),
                );
              },
            ),
    );
  }
}

// -----------------------------------------------------------------------------
// 5. Archive Center
// -----------------------------------------------------------------------------

class _ArchiveCenterView extends StatefulWidget {
  const _ArchiveCenterView();

  @override
  State<_ArchiveCenterView> createState() => _ArchiveCenterViewState();
}

class _ArchiveCenterViewState extends State<_ArchiveCenterView> {
  Future<List<({String table, int id, String title, String subtitle})>> _loadArchived(
      AppController ctrl) async {
    final items = <({String table, int id, String title, String subtitle})>[];
    final sales = await ctrl.repo.getSales(includeArchived: true);
    for (final s in sales.where((e) => e.isArchived)) {
      items.add((
        table: 'sales',
        id: s.id,
        title: 'فاتورة ${s.invoiceNumber} — ${s.customerName}',
        subtitle: '${s.dateIso} • ${Money(s.totalAmountMinor, s.currency).format()}',
      ));
    }
    final custPays = await ctrl.repo.getCustomerPayments(includeArchived: true);
    for (final p in custPays.where((e) => e.isArchived)) {
      items.add((
        table: 'customer_payments',
        id: p.id,
        title: 'سند قبض عميل — ${p.customerName}',
        subtitle: '${p.dateIso} • ${Money(p.amountMinor, p.currency).format()}',
      ));
    }
    final purchases = await ctrl.repo.getSupplierPurchases(includeArchived: true);
    for (final p in purchases.where((e) => e.isArchived)) {
      items.add((
        table: 'supplier_purchases',
        id: p.id,
        title: 'فاتورة شراء ${p.purchaseNumber} — ${p.supplierName}',
        subtitle: '${p.dateIso} • ${Money(p.totalAmountMinor, p.currency).format()}',
      ));
    }
    final supPays = await ctrl.repo.getSupplierPayments(includeArchived: true);
    for (final p in supPays.where((e) => e.isArchived)) {
      items.add((
        table: 'supplier_payments',
        id: p.id,
        title: 'سند صرف مورد — ${p.supplierName}',
        subtitle: '${p.dateIso} • ${Money(p.amountMinor, p.currency).format()}',
      ));
    }
    final expenses = await ctrl.repo.getExpenses(includeArchived: true);
    for (final e in expenses.where((e) => e.isArchived)) {
      items.add((
        table: 'expenses',
        id: e.id,
        title: 'مصروف: ${e.description}',
        subtitle: '${e.dateIso} • ${Money(e.amountMinor, e.currency).format()}',
      ));
    }
    final taxes = await ctrl.repo.getTaxEntries(includeArchived: true);
    for (final t in taxes.where((e) => e.isArchived)) {
      items.add((
        table: 'qat_tax_entries',
        id: t.id,
        title: 'قيد ضريبة (${t.source})',
        subtitle: '${t.dateIso} • ${Money(t.taxAmountMinor, t.currency).format()}',
      ));
    }
    final transfers = await ctrl.repo.getTransfers(includeArchived: true);
    for (final t in transfers.where((e) => e.isArchived)) {
      items.add((
        table: 'money_transfers',
        id: t.id,
        title: 'حوالة: ${t.receiverName.isEmpty ? t.senderName : t.receiverName}',
        subtitle: '${t.dateIso} • ${Money(t.amountMinor, t.currency).format()}',
      ));
    }
    final batches = await ctrl.repo.getInventoryBatches(includeArchived: true);
    for (final b in batches.where((e) => e.isArchived)) {
      items.add((
        table: 'inventory_batches',
        id: b.id,
        title: 'دفعة مخزون: ${b.qatTypeName}',
        subtitle: b.purchaseDateIso,
      ));
    }
    return items;
  }

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    if (!ctrl.sectionPermissions.archive) {
      return const EmptyLedgerState(
        icon: Icons.lock_outline,
        title: 'الأرشيف مقيد بالصلاحيات',
        subtitle: 'تم إخفاء مركز الأرشيف من إدارة الصلاحيات',
      );
    }
    return FutureBuilder<List<({String table, int id, String title, String subtitle})>>(
      future: _loadArchived(ctrl),
      builder: (context, snap) {
        if (!snap.hasData) return const Center(child: CircularProgressIndicator());
        final items = snap.data!;
        if (items.isEmpty) {
          return const EmptyLedgerState(
            icon: Icons.archive_outlined,
            title: 'الأرشيف فارغ',
            subtitle: 'تظهر هنا السجلات المؤرشفة لإمكانية استعادتها في أي وقت',
          );
        }
        return ListView.separated(
          padding: const EdgeInsets.all(16),
          itemCount: items.length,
          separatorBuilder: (_, idx) => const SizedBox(height: 8),
          itemBuilder: (_, idx) {
            final item = items[idx];
            return Card(
              child: ListTile(
                title: Text(item.title),
                subtitle: Text(item.subtitle),
                trailing: TextButton.icon(
                  onPressed: () async {
                    await ctrl.repo.setArchived(item.table, item.id, false);
                    await ctrl.reloadAll();
                    if (mounted) setState(() {});
                  },
                  icon: const Icon(Icons.unarchive_outlined, size: 16),
                  label: const Text('استعادة'),
                ),
              ),
            );
          },
        );
      },
    );
  }
}
