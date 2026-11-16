import 'package:flutter/foundation.dart';

/// Supported ledger currencies.
enum CurrencyCode {
  yer('YER', 'ريال يمني', 'ر.ي'),
  sar('SAR', 'ريال سعودي', 'ر.س');

  const CurrencyCode(this.code, this.arabicName, this.shortSymbol);
  final String code;
  final String arabicName;
  final String shortSymbol;

  static CurrencyCode fromRaw(String? raw, {CurrencyCode fallback = CurrencyCode.yer}) {
    final normalized = (raw ?? '').trim().toUpperCase();
    if (normalized == 'SAR' ||
        normalized.contains('سعودي') ||
        normalized == 'ر.س') {
      return CurrencyCode.sar;
    }
    if (normalized == 'YER' ||
        normalized.contains('يمني') ||
        normalized == 'ر.ي') {
      return CurrencyCode.yer;
    }
    return fallback;
  }
}

/// Centralized currency operating mode.
enum CurrencyMode {
  yerOnly('YER_ONLY', 'الريال اليمني فقط'),
  sarOnly('SAR_ONLY', 'الريال السعودي فقط'),
  dualCurrency('DUAL_CURRENCY', 'عملتان (دفاتر منفصلة YER + SAR)');

  const CurrencyMode(this.storageKey, this.arabicLabel);
  final String storageKey;
  final String arabicLabel;

  static CurrencyMode fromKey(String? raw) {
    for (final mode in CurrencyMode.values) {
      if (mode.storageKey == raw) return mode;
    }
    // Compatibility with legacy Kotlin prefs values
    switch ((raw ?? '').trim().toUpperCase()) {
      case 'YER':
        return CurrencyMode.yerOnly;
      case 'SAR':
        return CurrencyMode.sarOnly;
      case 'DUAL':
      case 'BOTH':
        return CurrencyMode.dualCurrency;
      default:
        return CurrencyMode.yerOnly;
    }
  }
}

/// Centralized currency configuration controlling form defaults & visibility.
@immutable
class CurrencyPolicy {
  const CurrencyPolicy({
    required this.mode,
    this.userDisplaySarToYerMilli = 0,
  });

  final CurrencyMode mode;

  /// Optional user-configured SAR -> YER rate in milli units (e.g., 140.00 -> 140000).
  /// Zero means no implicit/hardcoded exchange rate is assumed anywhere.
  final int userDisplaySarToYerMilli;

  CurrencyCode get defaultCurrency =>
      mode == CurrencyMode.sarOnly ? CurrencyCode.sar : CurrencyCode.yer;

  List<CurrencyCode> get allowedCurrencies => switch (mode) {
        CurrencyMode.yerOnly => const [CurrencyCode.yer],
        CurrencyMode.sarOnly => const [CurrencyCode.sar],
        CurrencyMode.dualCurrency => const [CurrencyCode.yer, CurrencyCode.sar],
      };

  bool get canSwitchCurrency => mode == CurrencyMode.dualCurrency;

  bool get hasExplicitDisplayRate =>
      mode == CurrencyMode.dualCurrency && userDisplaySarToYerMilli > 0;

  /// Converts SAR minor units to YER minor units ONLY for explicit display-only preview cards.
  /// Never use this to overwrite or merge persisted accounting ledgers.
  int? convertSarMinorToYerPreview(int sarMinor) {
    if (!hasExplicitDisplayRate) return null;
    final numerator =
        BigInt.from(sarMinor) * BigInt.from(userDisplaySarToYerMilli);
    final BigInt rounded = numerator >= BigInt.zero
        ? (numerator + BigInt.from(500)) ~/ BigInt.from(1000)
        : -(((-numerator) + BigInt.from(500)) ~/ BigInt.from(1000));
    if (!rounded.isValidInt) return null;
    return rounded.toInt();
  }
}

/// Exact integer money value in minor units (1/100 of major currency unit).
@immutable
class Money {
  const Money(this.minorUnits, this.currency);

  final int minorUnits;
  final CurrencyCode currency;

  static const int scale = 100;

  static Money zero(CurrencyCode currency) => Money(0, currency);

  /// Parses Arabic/English decimal text cleanly into exact integer minor units without double rounding drift.
  static int parseMinorUnits(String raw, {int fallback = 0}) {
    final normalized = normalizeDigits(raw).trim().replaceAll(',', '');
    if (normalized.isEmpty) return fallback;
    final neg = normalized.startsWith('-');
    final body = neg ? normalized.substring(1) : normalized;
    if (body.isEmpty) return fallback;

    final parts = body.split('.');
    if (parts.length > 2) return fallback;
    final wholeStr = parts[0].isEmpty ? '0' : parts[0];
    if (!RegExp(r'^\d+$').hasMatch(wholeStr)) return fallback;
    final whole = int.parse(wholeStr);

    int frac = 0;
    if (parts.length == 2 && parts[1].isNotEmpty) {
      final fracRaw = parts[1];
      if (!RegExp(r'^\d+$').hasMatch(fracRaw)) return fallback;
      final padded = '${fracRaw}000'.substring(0, 3);
      final firstTwo = int.parse(padded.substring(0, 2));
      final thirdDigit = int.parse(padded.substring(2, 3));
      frac = thirdDigit >= 5 ? firstTwo + 1 : firstTwo;
    }

    final total = whole * 100 + frac;
    return neg ? -total : total;
  }

  /// Exact half-up conversion from legacy Room REAL/Double values into integer minor units.
  static int fromLegacyDouble(num? value) {
    if (value == null || value.isNaN || value.isInfinite) return 0;
    return parseMinorUnits(value.toStringAsFixed(4));
  }

  static String formatMinor(int minorUnits, {bool showZeroDecimals = false}) {
    final neg = minorUnits < 0;
    final absVal = minorUnits.abs();
    final whole = absVal ~/ 100;
    final frac = absVal % 100;
    final wholeGrouped = _groupThousands(whole);
    final sign = neg ? '-' : '';
    if (frac == 0 && !showZeroDecimals) {
      return '$sign$wholeGrouped';
    }
    final fracStr = frac.toString().padLeft(2, '0');
    return '$sign$wholeGrouped.$fracStr';
  }

  String format({bool withSymbol = true, bool showZeroDecimals = false}) {
    final numStr = formatMinor(minorUnits, showZeroDecimals: showZeroDecimals);
    return withSymbol ? '$numStr ${currency.shortSymbol}' : numStr;
  }

  Money operator +(Money other) {
    assert(currency == other.currency, 'Cannot add ${currency.code} and ${other.currency.code}');
    return Money(minorUnits + other.minorUnits, currency);
  }

  Money operator -(Money other) {
    assert(currency == other.currency, 'Cannot subtract ${currency.code} and ${other.currency.code}');
    return Money(minorUnits - other.minorUnits, currency);
  }
}

/// Exact integer quantity in milli-units (1/1000 of unit, e.g., 1.500 = 1500).
abstract final class QuantityMath {
  static int parseMilli(String raw, {int fallback = 0}) {
    final normalized = normalizeDigits(raw).trim().replaceAll(',', '');
    if (normalized.isEmpty) return fallback;
    final neg = normalized.startsWith('-');
    final body = neg ? normalized.substring(1) : normalized;
    final parts = body.split('.');
    if (parts.length > 2) return fallback;
    final wholeStr = parts[0].isEmpty ? '0' : parts[0];
    if (!RegExp(r'^\d+$').hasMatch(wholeStr)) return fallback;
    final whole = int.parse(wholeStr);

    int frac = 0;
    if (parts.length == 2 && parts[1].isNotEmpty) {
      final fracRaw = parts[1];
      if (!RegExp(r'^\d+$').hasMatch(fracRaw)) return fallback;
      final padded = '${fracRaw}0000'.substring(0, 4);
      final firstThree = int.parse(padded.substring(0, 3));
      final fourth = int.parse(padded.substring(3, 4));
      frac = fourth >= 5 ? firstThree + 1 : firstThree;
    }
    final total = whole * 1000 + frac;
    return neg ? -total : total;
  }

  static int fromLegacyDouble(num? value) {
    if (value == null || value.isNaN || value.isInfinite) return 0;
    return parseMilli(value.toStringAsFixed(5));
  }

  static String formatMilli(int milli) {
    final neg = milli < 0;
    final absVal = milli.abs();
    final whole = absVal ~/ 1000;
    final frac = absVal % 1000;
    final sign = neg ? '-' : '';
    if (frac == 0) return '$sign$whole';
    final trimmed = frac.toString().padLeft(3, '0').replaceFirst(RegExp(r'0+$'), '');
    return '$sign$whole.$trimmed';
  }
}

/// Exact integer invoice & percentage math.
abstract final class InvoiceMath {
  /// Computes line total in minor units using BigInt half-up rounding:
  /// roundHalfUp(qtyMilli * unitPriceMinor / 1000).
  static int lineTotalMinor({required int qtyMilli, required int unitPriceMinor}) {
    final numerator = BigInt.from(qtyMilli) * BigInt.from(unitPriceMinor);
    final BigInt rounded = numerator >= BigInt.zero
        ? (numerator + BigInt.from(500)) ~/ BigInt.from(1000)
        : -(((-numerator) + BigInt.from(500)) ~/ BigInt.from(1000));
    if (!rounded.isValidInt) {
      throw RangeError('Line total exceeds 64-bit integer bounds');
    }
    return rounded.toInt();
  }

  /// Parses percentage text (e.g., "5" or "2.5") into basis points (500 or 250 bps).
  static int parsePercentBps(String raw, {int fallback = 0}) {
    return Money.parseMinorUnits(raw, fallback: fallback);
  }

  static String formatBps(int bps) {
    return '${Money.formatMinor(bps)}%';
  }

  static InvoiceTotals computeTotals({
    required Iterable<int> lineTotalsMinor,
    int discountMinor = 0,
    int taxBps = 0,
    int paidMinor = 0,
  }) {
    final subtotal = lineTotalsMinor.fold<int>(0, (a, b) => a + b);
    final safeDiscount = discountMinor.clamp(0, subtotal < 0 ? 0 : subtotal);
    final taxable = subtotal - safeDiscount;
    final taxAmount = ((BigInt.from(taxable) * BigInt.from(taxBps.clamp(0, 10000))) +
            BigInt.from(5000)) ~/
        BigInt.from(10000);
    final total = taxable + taxAmount.toInt();
    final safePaid = paidMinor.clamp(0, total);
    final remaining = total - safePaid;
    return InvoiceTotals(
      subtotalMinor: subtotal,
      discountMinor: safeDiscount,
      taxBps: taxBps.clamp(0, 10000),
      taxAmountMinor: taxAmount.toInt(),
      totalAmountMinor: total,
      paidAmountMinor: safePaid,
      remainingAmountMinor: remaining,
    );
  }
}

@immutable
class InvoiceTotals {
  const InvoiceTotals({
    required this.subtotalMinor,
    required this.discountMinor,
    required this.taxBps,
    required this.taxAmountMinor,
    required this.totalAmountMinor,
    required this.paidAmountMinor,
    required this.remainingAmountMinor,
  });

  final int subtotalMinor;
  final int discountMinor;
  final int taxBps;
  final int taxAmountMinor;
  final int totalAmountMinor;
  final int paidAmountMinor;
  final int remainingAmountMinor;
}

/// Normalizes Arabic-Indic (٠-٩) and Eastern Arabic-Indic (۰-۹) digits to ASCII 0-9.
String normalizeDigits(String input) {
  const arabicIndic = '٠١٢٣٤٥٦٧٨٩';
  const extArabicIndic = '۰۱۲۳۴۵۶۷۸۹';
  final sb = StringBuffer();
  for (final rune in input.runes) {
    final ch = String.fromCharCode(rune);
    final idx1 = arabicIndic.indexOf(ch);
    if (idx1 != -1) {
      sb.write(idx1);
      continue;
    }
    final idx2 = extArabicIndic.indexOf(ch);
    if (idx2 != -1) {
      sb.write(idx2);
      continue;
    }
    if (ch == '٫' || ch == '،') {
      sb.write('.');
      continue;
    }
    sb.write(ch);
  }
  return sb.toString();
}

String _groupThousands(int value) {
  final str = value.toString();
  if (str.length <= 3) return str;
  final sb = StringBuffer();
  int count = 0;
  for (int i = str.length - 1; i >= 0; i--) {
    sb.write(str[i]);
    count++;
    if (count % 3 == 0 && i != 0) {
      sb.write(',');
    }
  }
  return sb.toString().split('').reversed.join();
}
