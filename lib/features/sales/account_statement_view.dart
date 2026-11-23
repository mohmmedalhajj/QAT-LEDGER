import 'dart:async';

import 'package:material_ui/material_ui.dart';
import 'package:printing/printing.dart';
import 'package:provider/provider.dart';

import '../../core/date_utils.dart';
import '../../core/money.dart';
import '../../core/theme.dart';
import '../../data/models.dart';
import '../../services/esc_pos_service.dart';
import '../../services/pdf_export_service.dart';
import '../../services/whatsapp_share_service.dart';
import '../../state/app_controller.dart';
import '../transfers/recipients_reports_view.dart';
import '../widgets/common_widgets.dart';
import '../widgets/erp_components.dart';

enum _StatementPartyMode { customer, supplier, worker }

class AccountStatementView extends StatefulWidget {
  const AccountStatementView({
    super.key,
    this.preselectedCustomerId,
    this.preselectedSupplierId,
    this.preselectedWorkerName,
  });

  final int? preselectedCustomerId;
  final int? preselectedSupplierId;
  final String? preselectedWorkerName;

  @override
  State<AccountStatementView> createState() => _AccountStatementViewState();
}

class _AccountStatementViewState extends State<AccountStatementView> {
  static const int _allPartiesSentinelId = -1;

  _StatementPartyMode _mode = _StatementPartyMode.customer;
  int? _partyId;
  String? _workerName;
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
        _mode = _StatementPartyMode.customer;
        _partyId = widget.preselectedCustomerId;
        unawaited(_loadStatement(ctrl));
      } else if (widget.preselectedSupplierId != null) {
        _mode = _StatementPartyMode.supplier;
        _partyId = widget.preselectedSupplierId;
        unawaited(_loadStatement(ctrl));
      } else if (widget.preselectedWorkerName != null) {
        _mode = _StatementPartyMode.worker;
        _workerName = widget.preselectedWorkerName;
        unawaited(_loadStatement(ctrl));
      } else if (ctrl.statementCustomerId != null) {
        _mode = _StatementPartyMode.customer;
        _partyId = ctrl.statementCustomerId;
        unawaited(_loadStatement(ctrl));
      } else if (ctrl.statementSupplierId != null) {
        _mode = _StatementPartyMode.supplier;
        _partyId = ctrl.statementSupplierId;
        unawaited(_loadStatement(ctrl));
      } else if (ctrl.statementWorkerName != null) {
        _mode = _StatementPartyMode.worker;
        _workerName = ctrl.statementWorkerName;
        unawaited(_loadStatement(ctrl));
      }
    });
  }

  @override
  void didUpdateWidget(AccountStatementView oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.preselectedWorkerName != null &&
        widget.preselectedWorkerName != oldWidget.preselectedWorkerName) {
      setState(() {
        _mode = _StatementPartyMode.worker;
        _workerName = widget.preselectedWorkerName;
        _partyId = null;
      });
      unawaited(_loadStatement(context.read<AppController>()));
    } else if (widget.preselectedSupplierId != null &&
        widget.preselectedSupplierId != oldWidget.preselectedSupplierId) {
      setState(() {
        _mode = _StatementPartyMode.supplier;
        _partyId = widget.preselectedSupplierId;
        _workerName = null;
      });
      unawaited(_loadStatement(context.read<AppController>()));
    } else if (widget.preselectedCustomerId != null &&
        widget.preselectedCustomerId != oldWidget.preselectedCustomerId) {
      setState(() {
        _mode = _StatementPartyMode.customer;
        _partyId = widget.preselectedCustomerId;
        _workerName = null;
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
    if (_mode == _StatementPartyMode.worker) {
      final w = (_workerName ?? '').trim();
      if (w.isEmpty) {
        setState(() => _lines = {});
        return;
      }
      setState(() => _loading = true);
      try {
        final res = await ctrl.repo.getWorkerStatement(
          w,
          fromIso: _fromIso,
          toIso: _toIso,
        );
        if (!mounted) return;
        setState(() => _lines = res);
      } finally {
        if (mounted) setState(() => _loading = false);
      }
      return;
    }

    if (_partyId == null) {
      setState(() => _lines = {});
      return;
    }
    setState(() => _loading = true);
    try {
      final Map<CurrencyCode, List<StatementLine>> res;
      if (_mode == _StatementPartyMode.customer) {
        res = _partyId == _allPartiesSentinelId
            ? await ctrl.repo.getAllCustomersStatement(
                fromIso: _fromIso,
                toIso: _toIso,
              )
            : await ctrl.repo.getCustomerStatement(
                _partyId!,
                fromIso: _fromIso,
                toIso: _toIso,
              );
      } else {
        res = _partyId == _allPartiesSentinelId
            ? await ctrl.repo.getAllSuppliersStatement(
                fromIso: _fromIso,
                toIso: _toIso,
              )
            : await ctrl.repo.getSupplierStatement(
                _partyId!,
                fromIso: _fromIso,
                toIso: _toIso,
              );
      }
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
    final partyList = _mode == _StatementPartyMode.customer
        ? ctrl.customers.map((c) => (id: c.id, name: c.name, phone: c.phone)).toList()
        : (_mode == _StatementPartyMode.supplier
            ? ctrl.suppliers
                .map((s) => (id: s.id, name: s.name, phone: s.phone))
                .toList()
            : const <({int id, String name, String phone})>[]);

    final workerNames = <String>{
      for (final r in ctrl.inventoryReceivers)
        if (r.name.trim().isNotEmpty) r.name.trim(),
      for (final h in ctrl.workerHandoffs)
        if (h.workerName.trim().isNotEmpty) h.workerName.trim(),
    }.toList()
      ..sort();

    final isAggregate =
        _mode != _StatementPartyMode.worker && _partyId == _allPartiesSentinelId;
    final selectedMatches = partyList.where((e) => e.id == _partyId).toList();
    final String selectedPartyTitle = switch (_mode) {
      _StatementPartyMode.customer =>
        isAggregate ? 'عام للعملاء' : 'العميل',
      _StatementPartyMode.supplier =>
        isAggregate ? 'عام للموردين' : 'المورد',
      _StatementPartyMode.worker => 'العامل',
    };
    final String selectedPartyName = switch (_mode) {
      _StatementPartyMode.customer => isAggregate
          ? 'كافة العملاء'
          : (selectedMatches.isEmpty ? '' : selectedMatches.first.name),
      _StatementPartyMode.supplier => isAggregate
          ? 'كافة الموردين'
          : (selectedMatches.isEmpty ? '' : selectedMatches.first.name),
      _StatementPartyMode.worker => _workerName ?? '',
    };
    final String selectedPartyPhone =
        (_mode != _StatementPartyMode.worker && selectedMatches.isNotEmpty)
            ? selectedMatches.first.phone
            : '';
    final bool hasSelection = _mode == _StatementPartyMode.worker
        ? (_workerName != null && _workerName!.trim().isNotEmpty)
        : _partyId != null;

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
                  title: 'استخراج كشف حساب تفصيلي أو شامل',
                  icon: Icons.table_chart_outlined,
                ),
                const SizedBox(height: 12),
                SegmentedButton<_StatementPartyMode>(
                  segments: const [
                    ButtonSegment<_StatementPartyMode>(
                      value: _StatementPartyMode.customer,
                      label: Text('العملاء'),
                    ),
                    ButtonSegment<_StatementPartyMode>(
                      value: _StatementPartyMode.supplier,
                      label: Text('الموردون'),
                    ),
                    ButtonSegment<_StatementPartyMode>(
                      value: _StatementPartyMode.worker,
                      label: Text('العمال'),
                    ),
                  ],
                  selected: {_mode},
                  onSelectionChanged: (s) {
                    setState(() {
                      _mode = s.first;
                      _partyId = null;
                      _workerName = null;
                      _lines = {};
                    });
                  },
                ),
                const SizedBox(height: 12),
                if (_mode == _StatementPartyMode.worker)
                  DropdownButtonFormField<String>(
                    key: ValueKey<String>('stmt_worker_$_workerName'),
                    initialValue:
                        workerNames.contains(_workerName) ? _workerName : null,
                    isExpanded: true,
                    decoration: const InputDecoration(
                      labelText: 'اختر العامل',
                    ),
                    items: workerNames
                        .map(
                          (w) => DropdownMenuItem<String>(
                            value: w,
                            child: Text(w),
                          ),
                        )
                        .toList(),
                    onChanged: (v) {
                      setState(() => _workerName = v);
                      unawaited(_loadStatement(ctrl));
                    },
                  )
                else
                  DropdownButtonFormField<int>(
                    key: ValueKey<String>('stmt_party_${_mode}_$_partyId'),
                    initialValue: (_partyId == _allPartiesSentinelId ||
                            partyList.any((e) => e.id == _partyId))
                        ? _partyId
                        : null,
                    isExpanded: true,
                    decoration: InputDecoration(
                      labelText: _mode == _StatementPartyMode.customer
                          ? 'اختر العميل أو كشفاً شاملاً'
                          : 'اختر المورد أو كشفاً شاملاً',
                    ),
                    items: [
                      DropdownMenuItem<int>(
                        value: _allPartiesSentinelId,
                        child: Text(
                          _mode == _StatementPartyMode.customer
                              ? 'كافة العملاء — كشف حساب إجمالي'
                              : 'كافة الموردين — كشف حساب إجمالي',
                          style: TextStyle(
                            fontWeight: FontWeight.w700,
                            color: pal.brand,
                          ),
                        ),
                      ),
                      ...partyList.map(
                        (e) => DropdownMenuItem<int>(
                          value: e.id,
                          child: Text(e.name),
                        ),
                      ),
                    ],
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
                if (hasSelection && _lines.isNotEmpty) ...[
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
                            partyTitle: selectedPartyTitle,
                            partyName: selectedPartyName,
                            linesByCurrency: _lines,
                          );
                          await Printing.layoutPdf(
                            name: PdfExportService.buildArabicPdfFileName(
                              'كشف_${selectedPartyTitle}_$selectedPartyName',
                            ),
                            onLayout: (_) async => bytes,
                          );
                        },
                        icon: const Icon(
                          Icons.picture_as_pdf_outlined,
                          size: 18,
                        ),
                        label: const Text('تصدير كشف الحساب PDF'),
                      ),
                      FilledButton.tonalIcon(
                        onPressed: () async {
                          final txt =
                              ThermalReceiptFormatter.buildStatementReceiptText(
                            ctrl.settings,
                            partyTitle: selectedPartyTitle,
                            partyName: selectedPartyName,
                            linesByCurrency: _lines,
                          );
                          await showReceiptPreviewPrintSheet(
                            context,
                            agency: ctrl.settings,
                            title: 'كشف حساب $selectedPartyName',
                            receiptText: txt,
                            whatsappPhone: selectedPartyPhone,
                          );
                        },
                        icon: const Icon(Icons.print_outlined, size: 18),
                        label: const Text('طباعة حرارية 58/80mm'),
                      ),
                      OutlinedButton.icon(
                        onPressed: () async {
                          final summaryLines = _lines.entries.map((e) {
                            final endBal = e.value.isEmpty
                                ? 0
                                : e.value.last.runningBalanceMinor;
                            return '${e.key.arabicName}: ${Money(endBal, e.key).format()}';
                          }).join('\n');
                          await WhatsappShareService.instance.openWhatsApp(
                            phone: selectedPartyPhone.isNotEmpty
                                ? selectedPartyPhone
                                : null,
                            text:
                                'كشف حساب $selectedPartyName لدى ${ctrl.settings.agencyName}:\n$summaryLines',
                          );
                        },
                        icon: const Icon(Icons.chat_outlined, size: 17),
                        label: const Text('واتساب'),
                      ),
                      if (selectedPartyPhone.isNotEmpty)
                        OutlinedButton.icon(
                          onPressed: () async {
                            final summaryLines = _lines.entries.map((e) {
                              final endBal = e.value.isEmpty
                                  ? 0
                                  : e.value.last.runningBalanceMinor;
                              return '${e.key.arabicName}: ${Money(endBal, e.key).format()}';
                            }).join(' | ');
                            await WhatsappShareService.instance.openSms(
                              phone: selectedPartyPhone,
                              text:
                                  'كشف حساب $selectedPartyName لدى ${ctrl.settings.agencyName}: $summaryLines',
                            );
                          },
                          icon: const Icon(Icons.sms_outlined, size: 17),
                          label: const Text('SMS'),
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
        else if (!hasSelection)
          const EmptyLedgerState(
            icon: Icons.article_outlined,
            title: 'اختر عميلاً أو مورداً أو عاملاً',
            subtitle:
                'يدعم كشف الحساب التفصيلي، الكشف الإجمالي، وفصل العملات تلقائياً',
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
