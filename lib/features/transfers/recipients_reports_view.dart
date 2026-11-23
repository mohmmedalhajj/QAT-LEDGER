import 'dart:async';

import 'package:material_ui/material_ui.dart';
import 'package:printing/printing.dart';
import 'package:provider/provider.dart';
import 'package:share_plus/share_plus.dart';

import '../../core/date_utils.dart';
import '../../core/money.dart';
import '../../core/platform_bridge.dart';
import '../../core/theme.dart';
import '../../data/models.dart';
import '../../services/pdf_export_service.dart';
import '../../services/spreadsheet_export_service.dart';
import '../../services/whatsapp_share_service.dart';
import '../../state/app_controller.dart';
import '../widgets/common_widgets.dart';
import '../widgets/erp_components.dart';

enum ReportPeriodPreset {
  all('الكل'),
  daily('اليوم'),
  weekly('أسبوعي'),
  monthly('شهري'),
  yearly('سنوي'),
  custom('مخصص');

  const ReportPeriodPreset(this.arabicLabel);
  final String arabicLabel;
}

({String? fromIso, String? toIso}) resolveReportPresetRange(
  ReportPeriodPreset preset,
) {
  final now = DateTime.now();
  final today = QatDateUtils.toIsoDate(now);
  switch (preset) {
    case ReportPeriodPreset.all:
      return (fromIso: null, toIso: null);
    case ReportPeriodPreset.daily:
      return (fromIso: today, toIso: today);
    case ReportPeriodPreset.weekly:
      final weekStart = now.subtract(const Duration(days: 6));
      return (fromIso: QatDateUtils.toIsoDate(weekStart), toIso: today);
    case ReportPeriodPreset.monthly:
      final monthStart = DateTime(now.year, now.month, 1);
      return (fromIso: QatDateUtils.toIsoDate(monthStart), toIso: today);
    case ReportPeriodPreset.yearly:
      final yearStart = DateTime(now.year, 1, 1);
      return (fromIso: QatDateUtils.toIsoDate(yearStart), toIso: today);
    case ReportPeriodPreset.custom:
      return (fromIso: null, toIso: null);
  }
}

class RecipientsAndReportsView extends StatefulWidget {
  const RecipientsAndReportsView({super.key});

  @override
  State<RecipientsAndReportsView> createState() =>
      _RecipientsAndReportsViewState();
}

class _RecipientsAndReportsViewState extends State<RecipientsAndReportsView> {
  String _search = '';

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
      builder: (ctx) => ErpDialogControllerScope(
        controllers: [nameCtrl, phoneCtrl, compCtrl, notesCtrl],
        child: AlertDialog(
          title: Text(
            existing == null ? 'مستلم حوالات جديد' : 'تعديل بيانات المستلم',
          ),
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
                controller: compCtrl,
                decoration:
                    const InputDecoration(labelText: 'شركة الصرافة المعتمدة'),
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
              final dup = ctrl.recipients.any(
                (r) =>
                    r.id != existing?.id &&
                    r.name.trim().toLowerCase() == clean.toLowerCase(),
              );
              if (dup) {
                ScaffoldMessenger.of(ctx).showSnackBar(
                  const SnackBar(
                    content: Text('يوجد مستلم مسجل بنفس الاسم مسبقاً'),
                  ),
                );
                return;
              }
              await ctrl.repo.upsertTransferRecipient(
                id: existing?.id,
                name: clean,
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
      ),
    );
  }

  Future<void> _openRecipientReportSheet(
    BuildContext context,
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
    final list = ctrl.recipientReports.where((rep) {
      if (_search.isEmpty) return true;
      final q = _search.toLowerCase();
      return rep.recipient.name.toLowerCase().contains(q) ||
          rep.recipient.phone.contains(q) ||
          rep.recipient.company.toLowerCase().contains(q);
    }).toList();

    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _openRecipientDialog(context, ctrl),
        icon: const Icon(Icons.person_add_alt_1),
        label: const Text('إضافة مستلم'),
      ),
      body: Column(
        children: [
          ErpSearchFilterBar(
            searchHint: 'بحث باسم المستلم، الهاتف، أو شركة الصرافة...',
            onSearchChanged: (v) => setState(() => _search = v.trim()),
          ),
          Expanded(
            child: list.isEmpty
                ? const EmptyLedgerState(
                    icon: Icons.badge_outlined,
                    title: 'لا يوجد مستلمون مسجلون',
                    subtitle:
                        'أضف مستلمي الحوالات لمتابعة تقاريرهم وحالات التسليم بسهولة',
                  )
                : ListView.separated(
                    padding: const EdgeInsets.fromLTRB(16, 12, 16, 84),
                    itemCount: list.length,
                    separatorBuilder: (_, idx) => const SizedBox(height: 10),
                    itemBuilder: (_, idx) {
                      final rep = list[idx];
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
                                    child: Row(
                                      children: [
                                        Flexible(
                                          child: Text(
                                            r.name,
                                            style: const TextStyle(
                                              fontWeight: FontWeight.w700,
                                              fontSize: 14.5,
                                            ),
                                          ),
                                        ),
                                        if (!r.isActive) ...[
                                          const SizedBox(width: 6),
                                          StatusPill(
                                            label: 'موقوف',
                                            bg: pal.dangerSoft,
                                            fg: pal.danger,
                                          ),
                                        ],
                                      ],
                                    ),
                                  ),
                                  IconButton(
                                    tooltip: r.isActive
                                        ? 'إيقاف المستلم'
                                        : 'تفعيل المستلم',
                                    onPressed: () async {
                                      await ctrl.repo.setTransferRecipientActive(
                                        r.id,
                                        !r.isActive,
                                      );
                                      await ctrl.reloadAll();
                                    },
                                    icon: Icon(
                                      r.isActive
                                          ? Icons.toggle_on_outlined
                                          : Icons.toggle_off_outlined,
                                      color: r.isActive
                                          ? pal.success
                                          : pal.textSecondary,
                                      size: 22,
                                    ),
                                  ),
                                  IconButton(
                                    tooltip: 'تعديل المستلم',
                                    onPressed: () => _openRecipientDialog(
                                      context,
                                      ctrl,
                                      existing: r,
                                    ),
                                    icon: const Icon(
                                      Icons.edit_outlined,
                                      size: 18,
                                    ),
                                  ),
                                  if (ctrl.canDeleteCurrent &&
                                      rep.totalTransfers == 0)
                                    IconButton(
                                      tooltip: 'حذف المستلم',
                                      onPressed: () async {
                                        final ok = await showErpConfirmDialog(
                                          context,
                                          title: 'حذف المستلم',
                                          message:
                                              'هل تريد حذف المستلم "${r.name}"؟',
                                          confirmLabel: 'حذف',
                                          destructive: true,
                                        );
                                        if (!ok) return;
                                        await ctrl.repo
                                            .deleteTransferRecipient(r.id);
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
                              if (r.phone.isNotEmpty || r.company.isNotEmpty)
                                Text(
                                  '${r.phone} ${r.company.isNotEmpty ? '• ${r.company}' : ''}'
                                      .trim(),
                                  style: TextStyle(
                                    fontSize: 12,
                                    color: pal.textSecondary,
                                  ),
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
                              Wrap(
                                spacing: 8,
                                runSpacing: 8,
                                children: [
                                  FilledButton.tonalIcon(
                                    onPressed: () => _openRecipientReportSheet(
                                      context,
                                      r.id,
                                    ),
                                    icon: const Icon(
                                      Icons.assessment_outlined,
                                      size: 18,
                                    ),
                                    label: const Text(
                                      'تقرير المستلم (PDF / Excel / الحالة)',
                                    ),
                                  ),
                                  if (r.phone.isNotEmpty) ...[
                                    OutlinedButton.icon(
                                      onPressed: () async {
                                        final msg = WhatsappShareService
                                            .instance
                                            .formatRecipientSummaryMessage(
                                          ctrl.settings,
                                          rep,
                                        );
                                        await WhatsappShareService.instance
                                            .openWhatsApp(
                                          phone: r.phone,
                                          text: msg,
                                        );
                                      },
                                      icon: const Icon(
                                        Icons.chat_outlined,
                                        size: 16,
                                      ),
                                      label: const Text('واتساب'),
                                    ),
                                    OutlinedButton.icon(
                                      onPressed: () async {
                                        final msg = WhatsappShareService
                                            .instance
                                            .formatRecipientSummaryMessage(
                                          ctrl.settings,
                                          rep,
                                        );
                                        await WhatsappShareService.instance
                                            .openSms(
                                          phone: r.phone,
                                          text: msg,
                                        );
                                      },
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

class _RecipientStatusReportPage extends StatefulWidget {
  const _RecipientStatusReportPage({required this.recipientId});
  final int recipientId;

  @override
  State<_RecipientStatusReportPage> createState() =>
      _RecipientStatusReportPageState();
}

class _RecipientStatusReportPageState
    extends State<_RecipientStatusReportPage> {
  ReportPeriodPreset _preset = ReportPeriodPreset.all;
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

  Future<void> _selectPreset(ReportPeriodPreset preset) async {
    if (preset == ReportPeriodPreset.custom) {
      await _pickDates();
      return;
    }
    final range = resolveReportPresetRange(preset);
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
      _preset = ReportPeriodPreset.custom;
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
                      for (final p in ReportPeriodPreset.values) ...[
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
                        Text(
                          'ملخص الحوالات والحالات',
                          style: Theme.of(context).textTheme.titleMedium,
                        ),
                        const SizedBox(height: 8),
                        Wrap(
                          spacing: 8,
                          runSpacing: 6,
                          children: [
                            StatusPill(
                              label: 'تم التسليم: ${s.deliveredCount}',
                              bg: pal.successSoft,
                              fg: pal.success,
                            ),
                            StatusPill(
                              label: 'جاري التسليم: ${s.pendingCount}',
                              bg: pal.warningSoft,
                              fg: pal.warning,
                            ),
                            StatusPill(
                              label: 'ملغي: ${s.cancelledCount}',
                              bg: pal.dangerSoft,
                              fg: pal.danger,
                            ),
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
                                style: const TextStyle(
                                  fontSize: 12.5,
                                  fontWeight: FontWeight.w600,
                                  fontFeatures: [FontFeature.tabularFigures()],
                                ),
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
                                await Printing.layoutPdf(
                                  name: PdfExportService.buildArabicPdfFileName(
                                    'حوالات_${s.recipient.name}',
                                  ),
                                  onLayout: (_) async => pdf,
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
                                final path = await SpreadsheetExportService
                                    .instance
                                    .exportRecipientReportXlsx(s);
                                await Share.shareXFiles(
                                  [XFile(path)],
                                  text: 'تقرير مستلم الحوالات',
                                );
                              },
                              icon: const Icon(
                                Icons.table_view_outlined,
                                size: 16,
                              ),
                              label: const Text('Excel'),
                            ),
                            OutlinedButton.icon(
                              onPressed: () async {
                                final msg = WhatsappShareService.instance
                                    .formatRecipientSummaryMessage(
                                  ctrl.settings,
                                  s,
                                );
                                await showReceiptPreviewPrintSheet(
                                  context,
                                  agency: ctrl.settings,
                                  title: 'تقرير ${s.recipient.name}',
                                  receiptText: msg,
                                  whatsappPhone: s.recipient.phone,
                                );
                              },
                              icon: const Icon(
                                Icons.print_outlined,
                                size: 16,
                              ),
                              label: const Text('حراري'),
                            ),
                            OutlinedButton.icon(
                              onPressed: () async {
                                final msg = WhatsappShareService.instance
                                    .formatRecipientSummaryMessage(
                                  ctrl.settings,
                                  s,
                                );
                                await WhatsappShareService.instance
                                    .openWhatsApp(
                                  phone: s.recipient.phone,
                                  text: msg,
                                );
                              },
                              icon: const Icon(Icons.chat_outlined, size: 16),
                              label: const Text('واتساب'),
                            ),
                            if (s.recipient.phone.isNotEmpty)
                              OutlinedButton.icon(
                                onPressed: () async {
                                  final msg = WhatsappShareService.instance
                                      .formatRecipientSummaryMessage(
                                    ctrl.settings,
                                    s,
                                  );
                                  await WhatsappShareService.instance.openSms(
                                    phone: s.recipient.phone,
                                    text: msg,
                                  );
                                },
                                icon: const Icon(Icons.sms_outlined, size: 16),
                                label: const Text('SMS'),
                              ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 12),
                ErpAccountingTable(
                  columns: const [
                    ErpTableColumn(title: 'التاريخ', width: 92),
                    ErpTableColumn(title: 'المرسل', width: 112),
                    ErpTableColumn(title: 'الشركة', width: 96),
                    ErpTableColumn(title: 'الحالة', width: 88),
                    ErpTableColumn(
                      title: 'المبلغ',
                      width: 110,
                      numeric: true,
                      align: TextAlign.end,
                    ),
                  ],
                  rows: [
                    for (final t in s.transfers)
                      [
                        ErpTableCell(t.dateIso, ltr: true),
                        ErpTableCell(t.senderName),
                        ErpTableCell(
                          t.transferCompany.isEmpty ? '-' : t.transferCompany,
                        ),
                        ErpTableCell(
                          t.status.arabicLabel,
                          color: t.status == TransferStatus.delivered
                              ? pal.success
                              : (t.status == TransferStatus.pending
                                  ? pal.warning
                                  : pal.danger),
                        ),
                        ErpTableCell(
                          Money(t.amountMinor, t.currency).format(),
                          bold: true,
                        ),
                      ],
                  ],
                ),
              ],
            ),
    );
  }
}
