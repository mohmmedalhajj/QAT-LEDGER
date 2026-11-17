import 'package:material_ui/material_ui.dart';

import '../../core/theme.dart';

/// Structured header for any ERP section with icon badge, title, subtitle and trailing control.
class ErpSectionHeader extends StatelessWidget {
  const ErpSectionHeader({
    super.key,
    required this.title,
    required this.icon,
    this.subtitle,
    this.trailing,
    this.toneColor,
  });

  final String title;
  final IconData icon;
  final String? subtitle;
  final Widget? trailing;
  final Color? toneColor;

  @override
  Widget build(BuildContext context) {
    final pal = context.pal;
    final tint = toneColor ?? pal.brand;
    return Row(
      children: [
        Container(
          width: 36,
          height: 36,
          decoration: BoxDecoration(
            color: tint.withValues(alpha: 0.13),
            borderRadius: BorderRadius.circular(10),
          ),
          child: Icon(icon, size: 19, color: tint),
        ),
        const SizedBox(width: 10),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title, style: Theme.of(context).textTheme.titleMedium),
              if (subtitle != null && subtitle!.isNotEmpty)
                Text(
                  subtitle!,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.bodySmall,
                ),
            ],
          ),
        ),
        if (trailing != null) ...[
          const SizedBox(width: 8),
          trailing!,
        ],
      ],
    );
  }
}

/// Compact summary item shown inside [ErpSummaryBar].
class ErpSummaryMetric {
  const ErpSummaryMetric({
    required this.label,
    required this.value,
    this.toneColor,
  });

  final String label;
  final String value;
  final Color? toneColor;
}

/// Horizontal summary bar displaying key totals above lists and tables.
class ErpSummaryBar extends StatelessWidget {
  const ErpSummaryBar({super.key, required this.metrics});

  final List<ErpSummaryMetric> metrics;

  @override
  Widget build(BuildContext context) {
    final pal = context.pal;
    if (metrics.isEmpty) return const SizedBox.shrink();
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: pal.card,
        borderRadius: BorderRadius.circular(ErpRadius.lg),
        border: Border.all(color: pal.border),
      ),
      child: Wrap(
        spacing: 14,
        runSpacing: 10,
        children: [
          for (final m in metrics)
            ConstrainedBox(
              constraints: const BoxConstraints(minWidth: 105),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    m.label,
                    style: TextStyle(
                      fontSize: 11,
                      color: pal.textSecondary,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    m.value,
                    style: TextStyle(
                      fontSize: 13.5,
                      fontWeight: FontWeight.w700,
                      color: m.toneColor ?? pal.textPrimary,
                      fontFeatures: const [FontFeature.tabularFigures()],
                    ),
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }
}

/// Unified search + filter chip + optional sort control bar for ERP lists.
class ErpSearchFilterBar extends StatelessWidget {
  const ErpSearchFilterBar({
    super.key,
    required this.searchHint,
    required this.onSearchChanged,
    this.searchValue = '',
    this.filters = const [],
    this.trailingAction,
  });

  final String searchHint;
  final ValueChanged<String> onSearchChanged;
  final String searchValue;
  final List<Widget> filters;
  final Widget? trailingAction;

  @override
  Widget build(BuildContext context) {
    final pal = context.pal;
    return Container(
      padding: const EdgeInsets.fromLTRB(12, 10, 12, 10),
      decoration: BoxDecoration(
        color: pal.card,
        border: Border(bottom: BorderSide(color: pal.border)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Expanded(
                child: TextFormField(
                  initialValue: searchValue,
                  decoration: InputDecoration(
                    hintText: searchHint,
                    prefixIcon: const Icon(Icons.search, size: 20),
                  ),
                  onChanged: onSearchChanged,
                ),
              ),
              if (trailingAction != null) ...[
                const SizedBox(width: 8),
                trailingAction!,
              ],
            ],
          ),
          if (filters.isNotEmpty) ...[
            const SizedBox(height: 8),
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: [
                  for (int i = 0; i < filters.length; i++) ...[
                    if (i > 0) const SizedBox(width: 6),
                    filters[i],
                  ],
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }
}

enum ErpBannerTone { info, success, warning, danger }

/// Enterprise status / validation / audit banner.
class ErpStatusBanner extends StatelessWidget {
  const ErpStatusBanner({
    super.key,
    required this.tone,
    required this.title,
    this.subtitle,
    this.icon,
    this.action,
  });

  final ErpBannerTone tone;
  final String title;
  final String? subtitle;
  final IconData? icon;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    final pal = context.pal;
    final (Color bg, Color fg, IconData defIcon) = switch (tone) {
      ErpBannerTone.info => (pal.infoSoft, pal.info, Icons.info_outline),
      ErpBannerTone.success => (pal.successSoft, pal.success, Icons.verified_outlined),
      ErpBannerTone.warning => (pal.warningSoft, pal.warning, Icons.warning_amber_rounded),
      ErpBannerTone.danger => (pal.dangerSoft, pal.danger, Icons.error_outline),
    };

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(ErpRadius.md),
        border: Border.all(color: fg.withValues(alpha: 0.3)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Icon(icon ?? defIcon, size: 20, color: fg),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: TextStyle(
                    fontSize: 12.5,
                    fontWeight: FontWeight.w700,
                    color: pal.textPrimary,
                  ),
                ),
                if (subtitle != null && subtitle!.isNotEmpty) ...[
                  const SizedBox(height: 2),
                  Text(
                    subtitle!,
                    style: TextStyle(fontSize: 11.5, color: pal.textSecondary),
                  ),
                ],
              ],
            ),
          ),
          if (action != null) ...[
            const SizedBox(width: 8),
            action!,
          ],
        ],
      ),
    );
  }
}

/// Column definition for [ErpAccountingTable].
class ErpTableColumn {
  const ErpTableColumn({
    required this.title,
    required this.width,
    this.numeric = false,
    this.align = TextAlign.start,
  });

  final String title;
  final double width;
  final bool numeric;
  final TextAlign align;
}

/// Single cell value with optional semantic coloring inside [ErpAccountingTable].
class ErpTableCell {
  const ErpTableCell(
    this.text, {
    this.color,
    this.bold = false,
    this.ltr = false,
  });

  final String text;
  final Color? color;
  final bool bold;
  final bool ltr;
}

/// Structured, RTL-compatible accounting ledger table with alternating row stripes and tabular figures.
class ErpAccountingTable extends StatelessWidget {
  const ErpAccountingTable({
    super.key,
    required this.columns,
    required this.rows,
    this.emptyTitle = 'لا توجد حركات مسجلة',
    this.emptySubtitle = 'لم يتم تسجيل قيود مطابقة للفترة أو الفلتر المحدد.',
  });

  final List<ErpTableColumn> columns;
  final List<List<ErpTableCell>> rows;
  final String emptyTitle;
  final String emptySubtitle;

  @override
  Widget build(BuildContext context) {
    final pal = context.pal;
    if (rows.isEmpty) {
      return Card(
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(Icons.table_rows_outlined, size: 28, color: pal.textSecondary),
                const SizedBox(height: 8),
                Text(emptyTitle, style: Theme.of(context).textTheme.titleSmall),
                const SizedBox(height: 4),
                Text(
                  emptySubtitle,
                  textAlign: TextAlign.center,
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ],
            ),
          ),
        ),
      );
    }

    return Card(
      clipBehavior: Clip.antiAlias,
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              color: pal.tableHeader,
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 10),
              child: Row(
                children: [
                  for (final col in columns)
                    SizedBox(
                      width: col.width,
                      child: Text(
                        col.title,
                        textAlign: col.align,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontSize: 11.5,
                          fontWeight: FontWeight.w700,
                          color: pal.brand,
                        ),
                      ),
                    ),
                ],
              ),
            ),
            for (int r = 0; r < rows.length; r++)
              Container(
                decoration: BoxDecoration(
                  color: r.isEven ? pal.card : pal.tableRowAlt,
                  border: Border(top: BorderSide(color: pal.border, width: 0.8)),
                ),
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 9),
                child: Row(
                  children: [
                    for (int c = 0; c < columns.length && c < rows[r].length; c++)
                      SizedBox(
                        width: columns[c].width,
                        child: Text(
                          rows[r][c].text,
                          textDirection:
                              rows[r][c].ltr ? TextDirection.ltr : null,
                          textAlign: columns[c].align,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            fontSize: 12,
                            fontWeight: rows[r][c].bold || columns[c].numeric
                                ? FontWeight.w700
                                : FontWeight.w500,
                            color: rows[r][c].color ?? pal.textPrimary,
                            fontFeatures: columns[c].numeric || rows[r][c].ltr
                                ? const [FontFeature.tabularFigures()]
                                : null,
                          ),
                        ),
                      ),
                  ],
                ),
              ),
          ],
        ),
      ),
    );
  }
}

/// Key/value structured detail row for ERP dialogs and summary cards.
class ErpDetailRow extends StatelessWidget {
  const ErpDetailRow({
    super.key,
    required this.label,
    required this.value,
    this.valueColor,
    this.bold = false,
    this.ltr = false,
  });

  final String label;
  final String value;
  final Color? valueColor;
  final bool bold;
  final bool ltr;

  @override
  Widget build(BuildContext context) {
    final pal = context.pal;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Expanded(
            child: Text(
              label,
              style: TextStyle(
                fontSize: 12.5,
                fontWeight: bold ? FontWeight.w700 : FontWeight.w500,
                color: bold ? pal.textPrimary : pal.textSecondary,
              ),
            ),
          ),
          const SizedBox(width: 10),
          Text(
            value,
            textDirection: ltr ? TextDirection.ltr : null,
            style: TextStyle(
              fontSize: bold ? 14.0 : 12.5,
              fontWeight: bold ? FontWeight.w700 : FontWeight.w600,
              color: valueColor ?? pal.textPrimary,
              fontFeatures: const [FontFeature.tabularFigures()],
            ),
          ),
        ],
      ),
    );
  }
}

/// Unified confirmation dialog for sensitive accounting actions (delete, archive, restore, settlement).
Future<bool> showErpConfirmDialog(
  BuildContext context, {
  required String title,
  required String message,
  String confirmLabel = 'تأكيد',
  String cancelLabel = 'إلغاء',
  bool destructive = false,
}) async {
  final pal = context.pal;
  final result = await showDialog<bool>(
    context: context,
    builder: (ctx) => AlertDialog(
      title: Row(
        children: [
          Icon(
            destructive ? Icons.warning_amber_rounded : Icons.help_outline,
            color: destructive ? pal.danger : pal.brand,
            size: 22,
          ),
          const SizedBox(width: 8),
          Expanded(child: Text(title)),
        ],
      ),
      content: Text(message, style: const TextStyle(fontSize: 13.5, height: 1.45)),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(ctx, false),
          child: Text(cancelLabel),
        ),
        FilledButton(
          style: destructive
              ? FilledButton.styleFrom(
                  backgroundColor: pal.danger,
                  foregroundColor: Colors.white,
                )
              : null,
          onPressed: () => Navigator.pop(ctx, true),
          child: Text(confirmLabel),
        ),
      ],
    ),
  );
  return result ?? false;
}

/// Disposes dialog controllers only when the dialog widget tree itself unmounts
/// (after any [DialogRoute] exit transition finishes), avoiding premature
/// disposal errors in child [EditableText] fields.
class ErpDialogControllerScope extends StatefulWidget {
  const ErpDialogControllerScope({
    super.key,
    required this.controllers,
    required this.child,
  });

  final List<ChangeNotifier> controllers;
  final Widget child;

  @override
  State<ErpDialogControllerScope> createState() =>
      _ErpDialogControllerScopeState();
}

class _ErpDialogControllerScopeState extends State<ErpDialogControllerScope> {
  @override
  void dispose() {
    for (final c in widget.controllers) {
      c.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => widget.child;
}
