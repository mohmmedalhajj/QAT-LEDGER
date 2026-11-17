import 'package:flutter/services.dart';
import 'package:material_ui/material_ui.dart';
import 'package:printing/printing.dart';
import 'package:share_plus/share_plus.dart';

import '../../core/date_utils.dart';
import '../../core/money.dart';
import '../../core/theme.dart';
import '../../data/models.dart';
import '../../services/esc_pos_service.dart';
import '../../services/pdf_export_service.dart';

class CurrencyBadge extends StatelessWidget {
  const CurrencyBadge({super.key, required this.currency, this.compact = false});

  final CurrencyCode currency;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    final pal = context.pal;
    final isYer = currency == CurrencyCode.yer;
    return Container(
      padding: EdgeInsets.symmetric(
        horizontal: compact ? 6.0 : 8.0,
        vertical: compact ? 2.0 : 3.0,
      ),
      decoration: BoxDecoration(
        color: isYer ? pal.yerBadgeBg : pal.sarBadgeBg,
        borderRadius: BorderRadius.circular(7),
        border: Border.all(
          color: (isYer ? pal.yerBadgeFg : pal.sarBadgeFg).withValues(alpha: 0.22),
        ),
      ),
      child: Text(
        currency.code,
        style: TextStyle(
          fontSize: compact ? 10.0 : 11.0,
          fontWeight: FontWeight.w700,
          color: isYer ? pal.yerBadgeFg : pal.sarBadgeFg,
        ),
      ),
    );
  }
}

class StatusPill extends StatelessWidget {
  const StatusPill({
    super.key,
    required this.label,
    required this.bg,
    required this.fg,
    this.icon,
  });

  factory StatusPill.forTransfer(BuildContext context, TransferStatus status) {
    final pal = context.pal;
    return switch (status) {
      TransferStatus.delivered => StatusPill(
          label: status.arabicLabel,
          bg: pal.successSoft,
          fg: pal.success,
          icon: Icons.check_circle_outline,
        ),
      TransferStatus.pending => StatusPill(
          label: status.arabicLabel,
          bg: pal.warningSoft,
          fg: pal.warning,
          icon: Icons.schedule,
        ),
      TransferStatus.cancelled => StatusPill(
          label: status.arabicLabel,
          bg: pal.dangerSoft,
          fg: pal.danger,
          icon: Icons.cancel_outlined,
        ),
    };
  }

  factory StatusPill.forPayment(BuildContext context, PaymentType type) {
    final pal = context.pal;
    return switch (type) {
      PaymentType.paid => StatusPill(
          label: type.arabicLabel,
          bg: pal.successSoft,
          fg: pal.success,
          icon: Icons.verified_outlined,
        ),
      PaymentType.partial => StatusPill(
          label: type.arabicLabel,
          bg: pal.warningSoft,
          fg: pal.warning,
          icon: Icons.pie_chart_outline,
        ),
      PaymentType.credit => StatusPill(
          label: type.arabicLabel,
          bg: pal.dangerSoft,
          fg: pal.danger,
          icon: Icons.hourglass_bottom,
        ),
    };
  }

  final String label;
  final Color bg;
  final Color fg;
  final IconData? icon;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3.5),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: fg.withValues(alpha: 0.22)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[
            Icon(icon, size: 12, color: fg),
            const SizedBox(width: 4),
          ],
          Text(
            label,
            style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: fg),
          ),
        ],
      ),
    );
  }
}

class KpiMetricCard extends StatelessWidget {
  const KpiMetricCard({
    super.key,
    required this.title,
    required this.value,
    required this.icon,
    this.subtitle,
    this.toneColor,
    this.currency,
    this.onTap,
  });

  final String title;
  final String value;
  final IconData icon;
  final String? subtitle;
  final Color? toneColor;
  final CurrencyCode? currency;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final pal = context.pal;
    final tint = toneColor ?? pal.brand;
    final content = Padding(
      padding: const EdgeInsets.all(12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Row(
            children: [
              Container(
                width: 30,
                height: 30,
                decoration: BoxDecoration(
                  color: tint.withValues(alpha: 0.13),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Icon(icon, size: 16, color: tint),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    fontSize: 11.5,
                    color: pal.textSecondary,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
              if (currency != null) CurrencyBadge(currency: currency!, compact: true),
            ],
          ),
          const SizedBox(height: 6),
          Flexible(
            child: FittedBox(
              fit: BoxFit.scaleDown,
              alignment: AlignmentDirectional.centerStart,
              child: Text(
                value,
                style: TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.w700,
                  color: pal.textPrimary,
                  fontFeatures: const [FontFeature.tabularFigures()],
                ),
              ),
            ),
          ),
          if (subtitle != null && subtitle!.isNotEmpty) ...[
            const SizedBox(height: 4),
            Text(
              subtitle!,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                fontSize: 11,
                color: pal.textSecondary,
                fontFeatures: const [FontFeature.tabularFigures()],
              ),
            ),
          ],
        ],
      ),
    );

    return Card(
      child: onTap == null
          ? content
          : InkWell(
              onTap: onTap,
              borderRadius: BorderRadius.circular(ErpRadius.lg),
              child: content,
            ),
    );
  }
}

class SegmentedStrip extends StatelessWidget {
  const SegmentedStrip({
    super.key,
    required this.tabs,
    required this.selectedIndex,
    required this.onChanged,
    this.icons,
  });

  final List<String> tabs;
  final int selectedIndex;
  final ValueChanged<int> onChanged;
  final List<IconData>? icons;

  @override
  Widget build(BuildContext context) {
    final pal = context.pal;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: pal.card,
        border: Border(bottom: BorderSide(color: pal.border)),
      ),
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        child: Row(
          children: List.generate(tabs.length, (i) {
            final selected = i == selectedIndex;
            final hasIcon = icons != null && i < icons!.length;
            return Padding(
              padding: const EdgeInsetsDirectional.only(end: 8),
              child: InkWell(
                onTap: () => onChanged(i),
                borderRadius: BorderRadius.circular(10),
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 160),
                  padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 8),
                  decoration: BoxDecoration(
                    color: selected ? pal.brand : pal.cardElevated,
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(color: selected ? pal.brand : pal.border),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      if (hasIcon) ...[
                        Icon(
                          icons![i],
                          size: 15,
                          color: selected
                              ? (pal.isDark ? const Color(0xFF031B10) : Colors.white)
                              : pal.textSecondary,
                        ),
                        const SizedBox(width: 6),
                      ],
                      Text(
                        tabs[i],
                        style: TextStyle(
                          fontSize: 12.5,
                          fontWeight: FontWeight.w700,
                          color: selected
                              ? (pal.isDark ? const Color(0xFF031B10) : Colors.white)
                              : pal.textPrimary,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            );
          }),
        ),
      ),
    );
  }
}

class CurrencySelectorRow extends StatelessWidget {
  const CurrencySelectorRow({
    super.key,
    required this.policy,
    required this.selected,
    required this.onChanged,
  });

  final CurrencyPolicy policy;
  final CurrencyCode selected;
  final ValueChanged<CurrencyCode> onChanged;

  @override
  Widget build(BuildContext context) {
    if (!policy.canSwitchCurrency) {
      return Row(
        children: [
          Flexible(
            child: Text(
              'العملة المعتمدة:',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ),
          const SizedBox(width: 8),
          CurrencyBadge(currency: policy.defaultCurrency),
        ],
      );
    }
    return Wrap(
      spacing: 8,
      crossAxisAlignment: WrapCrossAlignment.center,
      children: [
        Text('العملة:', style: Theme.of(context).textTheme.bodySmall),
        for (final c in policy.allowedCurrencies)
          ChoiceChip(
            label: Text('${c.arabicName} (${c.code})'),
            selected: selected == c,
            onSelected: (_) => onChanged(c),
          ),
      ],
    );
  }
}

class AccountingNumberField extends StatelessWidget {
  const AccountingNumberField({
    super.key,
    required this.controller,
    required this.label,
    this.suffixText,
    this.hintText,
    this.allowDecimal = true,
    this.onChanged,
  });

  final TextEditingController controller;
  final String label;
  final String? suffixText;
  final String? hintText;
  final bool allowDecimal;
  final ValueChanged<String>? onChanged;

  @override
  Widget build(BuildContext context) {
    return TextField(
      controller: controller,
      textDirection: TextDirection.ltr,
      keyboardType: TextInputType.numberWithOptions(decimal: allowDecimal),
      style: const TextStyle(fontFeatures: [FontFeature.tabularFigures()]),
      inputFormatters: [
        FilteringTextInputFormatter.allow(
          RegExp(allowDecimal ? r'[0-9٠-٩۰-۹.,٫٬]' : r'[0-9٠-٩۰-۹]'),
        ),
      ],
      decoration: InputDecoration(
        labelText: label,
        hintText: hintText,
        suffixText: suffixText,
      ),
      onChanged: onChanged,
    );
  }
}

class EmptyLedgerState extends StatelessWidget {
  const EmptyLedgerState({
    super.key,
    required this.icon,
    required this.title,
    required this.subtitle,
    this.actionLabel,
    this.onAction,
  });

  final IconData icon;
  final String title;
  final String subtitle;
  final String? actionLabel;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    final pal = context.pal;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(28),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 64,
              height: 64,
              decoration: BoxDecoration(
                color: pal.brandSoft,
                borderRadius: BorderRadius.circular(18),
                border: Border.all(color: pal.brand.withValues(alpha: 0.25)),
              ),
              child: Icon(icon, size: 30, color: pal.brand),
            ),
            const SizedBox(height: 14),
            Text(
              title,
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 6),
            Text(
              subtitle,
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.bodySmall,
            ),
            if (actionLabel != null && onAction != null) ...[
              const SizedBox(height: 14),
              FilledButton.icon(
                onPressed: onAction,
                icon: const Icon(Icons.add, size: 18),
                label: Text(actionLabel!),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

/// Official period filter strip matching Android Dashboard & Reports (Today, Yesterday, Weeks 1-5, Month, Year, Custom, All).
class PeriodFilterStrip extends StatelessWidget {
  const PeriodFilterStrip({
    super.key,
    required this.filter,
    required this.onChanged,
  });

  final LedgerPeriodFilter filter;
  final ValueChanged<LedgerPeriodFilter> onChanged;

  @override
  Widget build(BuildContext context) {
    final pal = context.pal;
    final yesterdayIso =
        QatDateUtils.toIsoDate(DateTime.now().subtract(const Duration(days: 1)));
    final isYesterday = filter.kind == LedgerPeriodKind.custom &&
        filter.customFromIso == yesterdayIso &&
        filter.customToIso == yesterdayIso;

    final options = <(String, bool, Future<void> Function())>[
      (
        'اليوم',
        filter.kind == LedgerPeriodKind.daily,
        () async => onChanged(LedgerPeriodFilter.defaultToday()),
      ),
      (
        'أمس',
        isYesterday,
        () async => onChanged(
          LedgerPeriodFilter(
            kind: LedgerPeriodKind.custom,
            customUnit: CustomCalendarUnit.day,
            customIsoValue: yesterdayIso,
            customFromIso: yesterdayIso,
            customToIso: yesterdayIso,
          ),
        ),
      ),
      (
        'أسبوع ${filter.weekOfMonth}',
        filter.kind == LedgerPeriodKind.weekly,
        () async => onChanged(
          filter.copyWith(
            kind: LedgerPeriodKind.weekly,
            weekOfMonth: filter.weekOfMonth.clamp(1, 5),
          ),
        ),
      ),
      (
        'هذا الشهر',
        filter.kind == LedgerPeriodKind.monthly,
        () async => onChanged(const LedgerPeriodFilter(kind: LedgerPeriodKind.monthly)),
      ),
      (
        'هذه السنة',
        filter.kind == LedgerPeriodKind.yearly,
        () async => onChanged(const LedgerPeriodFilter(kind: LedgerPeriodKind.yearly)),
      ),
      (
        'فترة مخصصة',
        filter.kind == LedgerPeriodKind.custom && !isYesterday,
        () async {
          final range = await showDateRangePicker(
            context: context,
            firstDate: DateTime(2020),
            lastDate: DateTime(DateTime.now().year + 1),
          );
          if (range == null) return;
          final from = QatDateUtils.toIsoDate(range.start);
          final to = QatDateUtils.toIsoDate(range.end);
          onChanged(
            LedgerPeriodFilter(
              kind: LedgerPeriodKind.custom,
              customUnit: CustomCalendarUnit.day,
              customIsoValue: from,
              customFromIso: from,
              customToIso: to,
            ),
          );
        },
      ),
      (
        'الكل',
        filter.kind == LedgerPeriodKind.allTime,
        () async => onChanged(const LedgerPeriodFilter(kind: LedgerPeriodKind.allTime)),
      ),
    ];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Row(
            children: [
              for (final opt in options) ...[
                ChoiceChip(
                  label: Text(opt.$1),
                  selected: opt.$2,
                  onSelected: (_) => opt.$3(),
                ),
                const SizedBox(width: 6),
              ],
            ],
          ),
        ),
        if (filter.kind == LedgerPeriodKind.weekly) ...[
          const SizedBox(height: 6),
          Row(
            children: [
              Text(
                'أسبوع الشهر:',
                style: TextStyle(
                  fontSize: 11.5,
                  color: pal.textSecondary,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(width: 8),
              for (int w = 1; w <= 5; w++) ...[
                ChoiceChip(
                  label: Text('$w'),
                  visualDensity: VisualDensity.compact,
                  selected: filter.weekOfMonth == w,
                  onSelected: (_) => onChanged(
                    filter.copyWith(
                      kind: LedgerPeriodKind.weekly,
                      weekOfMonth: w,
                    ),
                  ),
                ),
                const SizedBox(width: 4),
              ],
            ],
          ),
        ],
      ],
    );
  }
}

/// Customer PRO-tier feature gate shown ONLY for PRO-exclusive modules (Reports & Archive).
class ProFeatureLockedCard extends StatelessWidget {
  const ProFeatureLockedCard({
    super.key,
    required this.featureTitle,
    required this.featureDescription,
    required this.onActivatePressed,
  });

  final String featureTitle;
  final String featureDescription;
  final VoidCallback onActivatePressed;

  @override
  Widget build(BuildContext context) {
    final pal = context.pal;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Card(
          child: Padding(
            padding: const EdgeInsets.all(22),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 64,
                  height: 64,
                  decoration: BoxDecoration(
                    color: pal.warningSoft,
                    borderRadius: BorderRadius.circular(18),
                    border: Border.all(
                      color: pal.warning.withValues(alpha: 0.35),
                    ),
                  ),
                  child: Icon(
                    Icons.workspace_premium_outlined,
                    size: 34,
                    color: pal.warning,
                  ),
                ),
                const SizedBox(height: 14),
                StatusPill(
                  label: 'ميزة احترافية PRO',
                  bg: pal.warningSoft,
                  fg: pal.warning,
                  icon: Icons.verified_outlined,
                ),
                const SizedBox(height: 10),
                Text(
                  featureTitle,
                  textAlign: TextAlign.center,
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                const SizedBox(height: 6),
                Text(
                  featureDescription,
                  textAlign: TextAlign.center,
                  style: Theme.of(context).textTheme.bodySmall,
                ),
                const SizedBox(height: 8),
                Text(
                  'ملاحظة: جميع سجلاتك ودفاترك محفوظة بأمان كامل. الترقية للباقة الاحترافية (PRO) تفتح التقارير المتقدمة والأرشيف الشامل فوراً دون اتصال بالإنترنت.',
                  textAlign: TextAlign.center,
                  style: TextStyle(fontSize: 11.5, color: pal.textSecondary),
                ),
                const SizedBox(height: 16),
                FilledButton.icon(
                  onPressed: onActivatePressed,
                  icon: const Icon(Icons.vpn_key_outlined, size: 18),
                  label: const Text('تفعيل / ترقية للباقة الاحترافية (PRO)'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Unified Thermal 58mm/80mm Preview, Bluetooth ESC/POS Print, PDF & Share sheet.
Future<void> showReceiptPreviewPrintSheet(
  BuildContext context, {
  required AgencySettings agency,
  required String title,
  required String receiptText,
  Uint8List? a4PdfBytes,
}) async {
  await showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    builder: (ctx) {
      final pal = ctx.pal;
      return Padding(
        padding: const EdgeInsets.fromLTRB(16, 14, 16, 20),
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                children: [
                  Icon(Icons.print_outlined, color: pal.brand),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      '$title (${agency.printerPaperMm}mm)',
                      style: Theme.of(ctx).textTheme.titleMedium,
                    ),
                  ),
                  IconButton(
                    onPressed: () => Navigator.pop(ctx),
                    icon: const Icon(Icons.close),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: pal.cardElevated,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: pal.border),
                ),
                child: SelectableText(
                  receiptText,
                  style: const TextStyle(fontSize: 12.5, height: 1.5),
                ),
              ),
              const SizedBox(height: 12),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  if (agency.savedPrinterAddress.isNotEmpty)
                    FilledButton.icon(
                      onPressed: () async {
                        try {
                          await EscPosService.instance.printTextReceipt(
                            agency: agency,
                            title: title,
                            receiptText: receiptText,
                            printerAddress: agency.savedPrinterAddress,
                            paperMm: agency.printerPaperMm,
                          );
                          if (ctx.mounted) {
                            ScaffoldMessenger.of(ctx).showSnackBar(
                              const SnackBar(
                                content: Text('تم إرسال الإيصال للطابعة الحرارية'),
                              ),
                            );
                          }
                        } catch (e) {
                          if (ctx.mounted) {
                            ScaffoldMessenger.of(ctx).showSnackBar(
                              SnackBar(content: Text('تعذر الطباعة الحرارية: $e')),
                            );
                          }
                        }
                      },
                      icon: const Icon(Icons.bluetooth_connected, size: 18),
                      label: Text('طباعة حرارية (${agency.savedPrinterName})'),
                    ),
                  FilledButton.tonalIcon(
                    onPressed: () async {
                      final pdf = a4PdfBytes ??
                          await PdfExportService.instance.buildTextReceiptPdf(
                            agency: agency,
                            title: title,
                            lines: receiptText.split('\n'),
                            format: agency.printerPaperMm <= 58
                                ? PdfPageFormat.roll57
                                : PdfPageFormat.roll80,
                          );
                      await Printing.layoutPdf(onLayout: (_) async => pdf);
                    },
                    icon: const Icon(Icons.picture_as_pdf_outlined, size: 18),
                    label: const Text('طباعة / PDF'),
                  ),
                  OutlinedButton.icon(
                    onPressed: () => Share.share(receiptText),
                    icon: const Icon(Icons.share_outlined, size: 18),
                    label: const Text('مشاركة نص'),
                  ),
                ],
              ),
            ],
          ),
        ),
      );
    },
  );
}
