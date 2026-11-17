import 'dart:async';

import 'package:material_ui/material_ui.dart';
import 'package:printing/printing.dart';
import 'package:provider/provider.dart';

import '../../core/date_utils.dart';
import '../../core/money.dart';
import '../../core/theme.dart';
import '../../data/models.dart';
import '../../services/pdf_export_service.dart';
import '../../services/whatsapp_share_service.dart';
import '../../state/app_controller.dart';
import '../transfers/recipients_reports_view.dart';
import '../widgets/common_widgets.dart';
import '../widgets/erp_components.dart';

class AccountStatementView extends StatefulWidget {
  const AccountStatementView({
    super.key,
    this.preselectedCustomerId,
    this.preselectedSupplierId,
  });

  final int? preselectedCustomerId;
  final int? preselectedSupplierId;

  @override
  State<AccountStatementView> createState() => _AccountStatementViewState();
}

class _AccountStatementViewState extends State<AccountStatementView> {
  bool _isCustomer = true;
  int? _partyId;
  ReportPeriodPreset _preset = ReportPeriodPreset.all;
  String? _fromIso;
  String? _toIso;
  Map<CurrencyCode, List<StatementLine>> _lines = {};
  bool _loading = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final ctrl = context.read<AppController>();
      if (widget.preselectedCustomerId != null) {
        _isCustomer = true;
        _partyId = widget.preselectedCustomerId;
        unawaited(_loadStatement(ctrl));
      } else if (ctrl.statementCustomerId != null) {
        _isCustomer = true;
        _partyId = ctrl.statementCustomerId;
        unawaited(_loadStatement(ctrl));
      } else if (ctrl.statementSupplierId != null) {
        _isCustomer = false;
        _partyId = ctrl.statementSupplierId;
        unawaited(_loadStatement(ctrl));
      }
    });
  }

  @override
  void didUpdateWidget(AccountStatementView oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.preselectedSupplierId != null &&
        widget.preselectedSupplierId != oldWidget.preselectedSupplierId) {
      setState(() {
        _isCustomer = false;
        _partyId = widget.preselectedSupplierId;
      });
      unawaited(_loadStatement(context.read<AppController>()));
    } else if (widget.preselectedCustomerId != null &&
        widget.preselectedCustomerId != oldWidget.preselectedCustomerId) {
      setState(() {
        _isCustomer = true;
        _partyId = widget.preselectedCustomerId;
      });
      unawaited(_loadStatement(context.read<AppController>()));
    }
  }

  Future<void> _selectPreset(
    AppController ctrl,
    ReportPeriodPreset preset,
  ) async {
    if (preset == ReportPeriodPreset.custom) {
      final range = await showDateRangePicker(
        context: context,
        firstDate: DateTime(2020),
        lastDate: DateTime(DateTime.now().year + 1),
      );
      if (range == null || !mounted) return;
      setState(() {
        _preset = ReportPeriodPreset.custom;
        _fromIso = QatDateUtils.toIsoDate(range.start);
        _toIso = QatDateUtils.toIsoDate(range.end);
      });
      await _loadStatement(ctrl);
      return;
    }
    final range = resolveReportPresetRange(preset);
    setState(() {
      _preset = preset;
      _fromIso = range.fromIso;
      _toIso = range.toIso;
    });
    await _loadStatement(ctrl);
  }

  Future<void> _loadStatement(AppController ctrl) async {
    if (_partyId == null) {
      setState(() => _lines = {});
      return;
    }
    setState(() => _loading = true);
    try {
      final res = _isCustomer
          ? await ctrl.repo.getCustomerStatement(
              _partyId!,
              fromIso: _fromIso,
              toIso: _toIso,
            )
          : await ctrl.repo.getSupplierStatement(
              _partyId!,
              fromIso: _fromIso,
              toIso: _toIso,
            );
      if (!mounted) return;
      setState(() => _lines = res);
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final pal = context.pal;
    final partyList = _isCustomer
        ? ctrl.customers.map((c) => (id: c.id, name: c.name, phone: c.phone)).toList()
        : ctrl.suppliers.map((s) => (id: s.id, name: s.name, phone: s.phone)).toList();

    final selectedMatches = partyList.where((e) => e.id == _partyId).toList();
    final selectedPartyName =
        selectedMatches.isEmpty ? '' : selectedMatches.first.name;
    final selectedPartyPhone =
        selectedMatches.isEmpty ? '' : selectedMatches.first.phone;

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const ErpSectionHeader(
                  title: 'استخراج كشف حساب تفصيلي',
                  icon: Icons.table_chart_outlined,
                ),
                const SizedBox(height: 12),
                SegmentedButton<bool>(
                  segments: const [
                    ButtonSegment<bool>(
                      value: true,
                      label: Text('كشف حساب عميل'),
                    ),
                    ButtonSegment<bool>(
                      value: false,
                      label: Text('كشف حساب مورد'),
                    ),
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
                  key: ValueKey<String>('stmt_party_${_isCustomer}_$_partyId'),
                  initialValue:
                      partyList.any((e) => e.id == _partyId) ? _partyId : null,
                  isExpanded: true,
                  decoration: InputDecoration(
                    labelText: _isCustomer ? 'اختر العميل' : 'اختر المورد',
                  ),
                  items: partyList
                      .map(
                        (e) => DropdownMenuItem<int>(
                          value: e.id,
                          child: Text(e.name),
                        ),
                      )
                      .toList(),
                  onChanged: (v) {
                    setState(() => _partyId = v);
                    unawaited(_loadStatement(ctrl));
                  },
                ),
                const SizedBox(height: 10),
                SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  child: Row(
                    children: [
                      for (final p in ReportPeriodPreset.values) ...[
                        ChoiceChip(
                          label: Text(p.arabicLabel),
                          selected: _preset == p,
                          onSelected: (_) => unawaited(_selectPreset(ctrl, p)),
                        ),
                        const SizedBox(width: 6),
                      ],
                    ],
                  ),
                ),
                if (_partyId != null && _lines.isNotEmpty) ...[
                  const SizedBox(height: 12),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: [
                      FilledButton.icon(
                        onPressed: () async {
                          final bytes = await PdfExportService.instance
                              .buildStatementPdf(
                            agency: ctrl.settings,
                            partyTitle: _isCustomer ? 'العميل' : 'المورد',
                            partyName: selectedPartyName,
                            linesByCurrency: _lines,
                          );
                          await Printing.layoutPdf(onLayout: (_) async => bytes);
                        },
                        icon: const Icon(
                          Icons.picture_as_pdf_outlined,
                          size: 18,
                        ),
                        label: const Text('تصدير كشف الحساب PDF'),
                      ),
                      if (selectedPartyPhone.isNotEmpty)
                        OutlinedButton.icon(
                          onPressed: () async {
                            final summaryLines = _lines.entries.map((e) {
                              final endBal = e.value.isEmpty
                                  ? 0
                                  : e.value.last.runningBalanceMinor;
                              return '${e.key.arabicName}: ${Money(endBal, e.key).format()}';
                            }).join('\n');
                            await WhatsappShareService.instance.openWhatsApp(
                              phone: selectedPartyPhone,
                              text:
                                  'كشف حساب $selectedPartyName لدى ${ctrl.settings.agencyName}:\n$summaryLines',
                            );
                          },
                          icon: const Icon(Icons.chat_outlined, size: 17),
                          label: const Text('إرسال ملخص واتساب'),
                        ),
                    ],
                  ),
                ],
              ],
            ),
          ),
        ),
        const SizedBox(height: 12),
        if (_loading)
          const Center(
            child: Padding(
              padding: EdgeInsets.all(24),
              child: CircularProgressIndicator(),
            ),
          )
        else if (_partyId == null)
          const EmptyLedgerState(
            icon: Icons.article_outlined,
            title: 'اختر عميلاً أو مورداً',
            subtitle: 'سيتم عرض جدول القيود المحاسبية مع فصل العملات تلقائياً',
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
                Expanded(
                  child: Text(
                    'دفتر ${entry.key.arabicName}',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            ErpSummaryBar(
              metrics: [
                ErpSummaryMetric(
                  label: 'إجمالي عليه / مدين',
                  value: Money(
                    entry.value.fold<int>(0, (s, l) => s + l.debitMinor),
                    entry.key,
                  ).format(),
                ),
                ErpSummaryMetric(
                  label: 'إجمالي له / دائن',
                  value: Money(
                    entry.value.fold<int>(0, (s, l) => s + l.creditMinor),
                    entry.key,
                  ).format(),
                  toneColor: pal.success,
                ),
                ErpSummaryMetric(
                  label: 'الرصيد الختامي',
                  value: Money(
                    entry.value.isEmpty
                        ? 0
                        : entry.value.last.runningBalanceMinor,
                    entry.key,
                  ).format(),
                  toneColor: (entry.value.isEmpty
                              ? 0
                              : entry.value.last.runningBalanceMinor) >
                          0
                      ? pal.danger
                      : pal.success,
                ),
              ],
            ),
            const SizedBox(height: 8),
            ErpAccountingTable(
              columns: const [
                ErpTableColumn(title: 'التاريخ', width: 92),
                ErpTableColumn(title: 'المرجع', width: 92),
                ErpTableColumn(title: 'البيان', width: 148),
                ErpTableColumn(
                  title: 'عليه / مدين',
                  width: 96,
                  numeric: true,
                  align: TextAlign.end,
                ),
                ErpTableColumn(
                  title: 'له / دائن',
                  width: 96,
                  numeric: true,
                  align: TextAlign.end,
                ),
                ErpTableColumn(
                  title: 'الرصيد',
                  width: 106,
                  numeric: true,
                  align: TextAlign.end,
                ),
              ],
              rows: [
                for (final l in entry.value)
                  [
                    ErpTableCell(l.dateIso, ltr: true),
                    ErpTableCell(l.reference, ltr: true, bold: true),
                    ErpTableCell(l.description),
                    ErpTableCell(
                      l.debitMinor > 0
                          ? Money(l.debitMinor, entry.key)
                              .format(withSymbol: false)
                          : '-',
                    ),
                    ErpTableCell(
                      l.creditMinor > 0
                          ? Money(l.creditMinor, entry.key)
                              .format(withSymbol: false)
                          : '-',
                      color: l.creditMinor > 0 ? pal.success : null,
                    ),
                    ErpTableCell(
                      Money(l.runningBalanceMinor, entry.key).format(),
                      bold: true,
                      color: l.runningBalanceMinor > 0
                          ? pal.danger
                          : pal.success,
                    ),
                  ],
              ],
            ),
            const SizedBox(height: 16),
          ],
      ],
    );
  }
}
