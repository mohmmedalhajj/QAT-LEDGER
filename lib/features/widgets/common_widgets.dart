import 'package:flutter/services.dart';
import 'package:material_ui/material_ui.dart';

import '../../core/money.dart';
import '../../core/theme.dart';
import '../../data/models.dart';

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
