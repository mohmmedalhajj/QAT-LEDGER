import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../core/money.dart';
import '../../core/theme.dart';
import '../../data/models.dart';

class CurrencyBadge extends StatelessWidget {
  const CurrencyBadge({super.key, required this.currency});

  final CurrencyCode currency;

  @override
  Widget build(BuildContext context) {
    final pal = context.pal;
    final isYer = currency == CurrencyCode.yer;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: isYer ? pal.yerBadgeBg : pal.sarBadgeBg,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        currency.code,
        style: TextStyle(
          fontSize: 11,
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
  });

  factory StatusPill.forTransfer(BuildContext context, TransferStatus status) {
    final pal = context.pal;
    return switch (status) {
      TransferStatus.delivered => StatusPill(
          label: status.arabicLabel,
          bg: pal.successSoft,
          fg: pal.success,
        ),
      TransferStatus.pending => StatusPill(
          label: status.arabicLabel,
          bg: pal.warningSoft,
          fg: pal.warning,
        ),
      TransferStatus.cancelled => StatusPill(
          label: status.arabicLabel,
          bg: pal.dangerSoft,
          fg: pal.danger,
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
        ),
      PaymentType.partial => StatusPill(
          label: type.arabicLabel,
          bg: pal.warningSoft,
          fg: pal.warning,
        ),
      PaymentType.credit => StatusPill(
          label: type.arabicLabel,
          bg: pal.dangerSoft,
          fg: pal.danger,
        ),
    };
  }

  final String label;
  final Color bg;
  final Color fg;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 4),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        label,
        style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: fg),
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
  });

  final String title;
  final String value;
  final IconData icon;
  final String? subtitle;
  final Color? toneColor;
  final CurrencyCode? currency;

  @override
  Widget build(BuildContext context) {
    final pal = context.pal;
    final tint = toneColor ?? pal.brand;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Row(
              children: [
                Container(
                  width: 32,
                  height: 32,
                  decoration: BoxDecoration(
                    color: tint.withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(9),
                  ),
                  child: Icon(icon, size: 18, color: tint),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(fontSize: 12, color: pal.textSecondary, fontWeight: FontWeight.w600),
                  ),
                ),
                if (currency != null) CurrencyBadge(currency: currency!),
              ],
            ),
            const SizedBox(height: 10),
            FittedBox(
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
            if (subtitle != null && subtitle!.isNotEmpty) ...[
              const SizedBox(height: 4),
              Text(
                subtitle!,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(fontSize: 11, color: pal.textSecondary),
              ),
            ],
          ],
        ),
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
  });

  final List<String> tabs;
  final int selectedIndex;
  final ValueChanged<int> onChanged;

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
            return Padding(
              padding: const EdgeInsetsDirectional.only(end: 8),
              child: InkWell(
                onTap: () => onChanged(i),
                borderRadius: BorderRadius.circular(10),
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 180),
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                  decoration: BoxDecoration(
                    color: selected ? pal.brand : pal.cardElevated,
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(color: selected ? pal.brand : pal.border),
                  ),
                  child: Text(
                    tabs[i],
                    style: TextStyle(
                      fontSize: 12.5,
                      fontWeight: FontWeight.w700,
                      color: selected
                          ? (pal.isDark ? const Color(0xFF041C10) : Colors.white)
                          : pal.textPrimary,
                    ),
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
          Text('العملة المعتمدة:', style: Theme.of(context).textTheme.bodySmall),
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
    this.allowDecimal = true,
    this.onChanged,
  });

  final TextEditingController controller;
  final String label;
  final String? suffixText;
  final bool allowDecimal;
  final ValueChanged<String>? onChanged;

  @override
  Widget build(BuildContext context) {
    return TextField(
      controller: controller,
      textDirection: TextDirection.ltr,
      keyboardType: TextInputType.numberWithOptions(decimal: allowDecimal),
      inputFormatters: [
        FilteringTextInputFormatter.allow(
          RegExp(allowDecimal ? r'[0-9٠-٩۰-۹.,٫]' : r'[0-9٠-٩۰-۹]'),
        ),
      ],
      decoration: InputDecoration(
        labelText: label,
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
  });

  final IconData icon;
  final String title;
  final String subtitle;

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
              width: 60,
              height: 60,
              decoration: BoxDecoration(
                color: pal.brandSoft,
                borderRadius: BorderRadius.circular(18),
              ),
              child: Icon(icon, size: 30, color: pal.brand),
            ),
            const SizedBox(height: 14),
            Text(title, style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 6),
            Text(
              subtitle,
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ],
        ),
      ),
    );
  }
}
