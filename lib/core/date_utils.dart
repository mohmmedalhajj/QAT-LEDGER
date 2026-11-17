import 'money.dart';

enum LedgerPeriodKind {
  daily('يومي'),
  weekly('أسبوعي'),
  monthly('شهري'),
  yearly('سنوي'),
  allTime('الكل'),
  custom('تقويم مخصص');

  const LedgerPeriodKind(this.arabicLabel);
  final String arabicLabel;
}

enum CustomCalendarUnit {
  day('يوم'),
  month('شهر'),
  year('سنة');

  const CustomCalendarUnit(this.arabicLabel);
  final String arabicLabel;
}

class LedgerPeriodFilter {
  const LedgerPeriodFilter({
    this.kind = LedgerPeriodKind.daily,
    this.weekYearMonth = '',
    this.weekOfMonth = 1,
    this.customUnit = CustomCalendarUnit.day,
    this.customIsoValue = '',
    this.customFromIso = '',
    this.customToIso = '',
  });

  final LedgerPeriodKind kind;
  final String weekYearMonth; // yyyy-MM
  final int weekOfMonth; // 1..5
  final CustomCalendarUnit customUnit;
  final String customIsoValue; // yyyy-MM-dd or yyyy-MM or yyyy
  final String customFromIso;
  final String customToIso;

  String get arabicLabel => QatDateUtils.formatPeriodDescription(this);

  ({String fromIso, String toIso}) resolveBounds() {
    if (kind == LedgerPeriodKind.custom &&
        customFromIso.isNotEmpty &&
        customToIso.isNotEmpty) {
      return (fromIso: customFromIso, toIso: customToIso);
    }
    return QatDateUtils.periodBounds(this);
  }

  LedgerPeriodFilter copyWith({
    LedgerPeriodKind? kind,
    String? weekYearMonth,
    int? weekOfMonth,
    CustomCalendarUnit? customUnit,
    String? customIsoValue,
    String? customFromIso,
    String? customToIso,
  }) {
    return LedgerPeriodFilter(
      kind: kind ?? this.kind,
      weekYearMonth: weekYearMonth ?? this.weekYearMonth,
      weekOfMonth: weekOfMonth ?? this.weekOfMonth,
      customUnit: customUnit ?? this.customUnit,
      customIsoValue: customIsoValue ?? this.customIsoValue,
      customFromIso: customFromIso ?? this.customFromIso,
      customToIso: customToIso ?? this.customToIso,
    );
  }

  static LedgerPeriodFilter defaultToday() {
    final now = DateTime.now();
    final ym = '${now.year.toString().padLeft(4, '0')}-${now.month.toString().padLeft(2, '0')}';
    return LedgerPeriodFilter(
      kind: LedgerPeriodKind.daily,
      weekYearMonth: ym,
      weekOfMonth: QatDateUtils.weekOfMonth(now),
      customUnit: CustomCalendarUnit.day,
      customIsoValue: QatDateUtils.toIsoDate(now),
    );
  }
}

/// Date parsing & ISO-8601 formatting utilities preserving compatibility with legacy Room date strings.
abstract final class QatDateUtils {
  static const List<String> arabicMonths = [
    'يناير',
    'فبراير',
    'مارس',
    'أبريل',
    'مايو',
    'يونيو',
    'يوليو',
    'أغسطس',
    'سبتمبر',
    'أكتوبر',
    'نوفمبر',
    'ديسمبر',
  ];

  static String toIsoDate(DateTime dt) {
    final y = dt.year.toString().padLeft(4, '0');
    final m = dt.month.toString().padLeft(2, '0');
    final d = dt.day.toString().padLeft(2, '0');
    return '$y-$m-$d';
  }

  static String toDisplayDateTime(int epochMs) {
    final dt = DateTime.fromMillisecondsSinceEpoch(epochMs);
    final hh = dt.hour.toString().padLeft(2, '0');
    final mm = dt.minute.toString().padLeft(2, '0');
    return '${toIsoDate(dt)} $hh:$mm';
  }

  /// 12-hour Arabic format matching legacy Helpers.getCurrentDateTime() ("yyyy-MM-dd hh:mm ص/م").
  static String toDisplayDateTime12h(int epochMs) {
    final dt = DateTime.fromMillisecondsSinceEpoch(epochMs);
    final amPm = dt.hour < 12 ? 'ص' : 'م';
    final h12 = dt.hour % 12 == 0 ? 12 : dt.hour % 12;
    final hh = h12.toString().padLeft(2, '0');
    final mm = dt.minute.toString().padLeft(2, '0');
    return '${toIsoDate(dt)} $hh:$mm $amPm';
  }

  static String formatArabicDateTime12h(int epochMs) =>
      toDisplayDateTime12h(epochMs);

  static String formatDateArabic(String isoDate) {
    final clean = isoDate.trim();
    final match = RegExp(r'^(\d{4})-(\d{1,2})-(\d{1,2})').firstMatch(clean);
    if (match == null) return isoDate;
    final y = int.tryParse(match.group(1)!) ?? 2026;
    final m = (int.tryParse(match.group(2)!) ?? 1).clamp(1, 12);
    final d = int.tryParse(match.group(3)!) ?? 1;
    return '$d ${arabicMonths[m - 1]} $y';
  }

  static String formatMonthArabic(String yyyyMm) {
    final match = RegExp(r'^(\d{4})-(\d{1,2})').firstMatch(yyyyMm.trim());
    if (match == null) return yyyyMm;
    final y = int.tryParse(match.group(1)!) ?? 2026;
    final m = (int.tryParse(match.group(2)!) ?? 1).clamp(1, 12);
    return '${arabicMonths[m - 1]} $y';
  }

  static String todayIso() => toIsoDate(DateTime.now());

  static int startOfDayMs(DateTime dt) =>
      DateTime(dt.year, dt.month, dt.day).millisecondsSinceEpoch;

  static int endOfDayMs(DateTime dt) =>
      DateTime(dt.year, dt.month, dt.day, 23, 59, 59, 999).millisecondsSinceEpoch;

  static int weekOfMonth(DateTime dt) {
    if (dt.day <= 7) return 1;
    if (dt.day <= 14) return 2;
    if (dt.day <= 21) return 3;
    if (dt.day <= 28) return 4;
    return 5;
  }

  static String weekLabelArabic(int weekNum) => switch (weekNum) {
        1 => 'الأسبوع الأول (1 - 7)',
        2 => 'الأسبوع الثاني (8 - 14)',
        3 => 'الأسبوع الثالث (15 - 21)',
        4 => 'الأسبوع الرابع (22 - 28)',
        _ => 'الأسبوع الخامس (29 - نهاية الشهر)',
      };

  static ({String fromIso, String toIso}) periodBounds(LedgerPeriodFilter filter) {
    final now = DateTime.now();
    final today = toIsoDate(now);
    switch (filter.kind) {
      case LedgerPeriodKind.daily:
        return (fromIso: today, toIso: today);
      case LedgerPeriodKind.weekly:
        final ym = filter.weekYearMonth.isNotEmpty
            ? filter.weekYearMonth
            : '${now.year.toString().padLeft(4, '0')}-${now.month.toString().padLeft(2, '0')}';
        final parts = ym.split('-');
        final y = int.tryParse(parts.first) ?? now.year;
        final m = parts.length > 1 ? (int.tryParse(parts[1]) ?? now.month).clamp(1, 12) : now.month;
        final daysInMonth = DateTime(y, m + 1, 0).day;
        final w = filter.weekOfMonth.clamp(1, 5);
        final startDay = (w - 1) * 7 + 1;
        if (startDay > daysInMonth) {
          final last = toIsoDate(DateTime(y, m, daysInMonth));
          return (fromIso: last, toIso: last);
        }
        final endDay = (w == 5 ? daysInMonth : (w * 7)).clamp(startDay, daysInMonth);
        return (
          fromIso: toIsoDate(DateTime(y, m, startDay)),
          toIso: toIsoDate(DateTime(y, m, endDay)),
        );
      case LedgerPeriodKind.monthly:
        final first = DateTime(now.year, now.month, 1);
        final last = DateTime(now.year, now.month + 1, 0);
        return (fromIso: toIsoDate(first), toIso: toIsoDate(last));
      case LedgerPeriodKind.yearly:
        return (fromIso: '${now.year}-01-01', toIso: '${now.year}-12-31');
      case LedgerPeriodKind.allTime:
        return (fromIso: '1970-01-01', toIso: '2099-12-31');
      case LedgerPeriodKind.custom:
        final raw = filter.customIsoValue.trim();
        switch (filter.customUnit) {
          case CustomCalendarUnit.day:
            final iso = raw.length >= 10 ? raw.substring(0, 10) : today;
            return (fromIso: iso, toIso: iso);
          case CustomCalendarUnit.month:
            final ym = raw.length >= 7 ? raw.substring(0, 7) : today.substring(0, 7);
            final parts = ym.split('-');
            final y = int.tryParse(parts.first) ?? now.year;
            final m = parts.length > 1 ? (int.tryParse(parts[1]) ?? now.month).clamp(1, 12) : now.month;
            return (
              fromIso: toIsoDate(DateTime(y, m, 1)),
              toIso: toIsoDate(DateTime(y, m + 1, 0)),
            );
          case CustomCalendarUnit.year:
            final yr = raw.length >= 4 ? raw.substring(0, 4) : now.year.toString();
            return (fromIso: '$yr-01-01', toIso: '$yr-12-31');
        }
    }
  }

  static bool matchesPeriod(String dateIso, LedgerPeriodFilter filter) {
    if (filter.kind == LedgerPeriodKind.allTime) return true;
    final norm = dateIso.length >= 10 ? dateIso.substring(0, 10) : dateIso;
    final bounds = periodBounds(filter);
    return norm.compareTo(bounds.fromIso) >= 0 && norm.compareTo(bounds.toIso) <= 0;
  }

  static String formatPeriodDescription(LedgerPeriodFilter filter) {
    final now = DateTime.now();
    switch (filter.kind) {
      case LedgerPeriodKind.daily:
        return 'اليوم (${formatDateArabic(toIsoDate(now))})';
      case LedgerPeriodKind.weekly:
        final ym = filter.weekYearMonth.isNotEmpty
            ? filter.weekYearMonth
            : '${now.year.toString().padLeft(4, '0')}-${now.month.toString().padLeft(2, '0')}';
        return '${weekLabelArabic(filter.weekOfMonth)} — ${formatMonthArabic(ym)}';
      case LedgerPeriodKind.monthly:
        return 'شهر ${formatMonthArabic(toIsoDate(now).substring(0, 7))}';
      case LedgerPeriodKind.yearly:
        return 'سنة ${now.year}';
      case LedgerPeriodKind.allTime:
        return 'الأرشيف الكلي (جميع الأوقات)';
      case LedgerPeriodKind.custom:
        final val = filter.customIsoValue.trim();
        switch (filter.customUnit) {
          case CustomCalendarUnit.day:
            return formatDateArabic(val.isEmpty ? todayIso() : val);
          case CustomCalendarUnit.month:
            return formatMonthArabic(val.isEmpty ? todayIso().substring(0, 7) : val);
          case CustomCalendarUnit.year:
            return 'سنة ${val.isEmpty ? now.year : val}';
        }
    }
  }

  /// Parses legacy Kotlin dates (`yyyy-MM-dd`, `yyyy/MM/dd`, `dd/MM/yyyy`, `yyyy-MM-dd HH:mm`, epoch strings).
  static ParsedDate parseLegacyDate(String? raw, {int? fallbackMs}) {
    final fallback = fallbackMs ?? DateTime.now().millisecondsSinceEpoch;
    final clean = normalizeDigits((raw ?? '').trim());
    if (clean.isEmpty) {
      final dt = DateTime.fromMillisecondsSinceEpoch(fallback);
      return ParsedDate(epochMs: fallback, isoDate: toIsoDate(dt));
    }

    if (RegExp(r'^\d{10,13}$').hasMatch(clean)) {
      final numVal = int.parse(clean);
      final ms = clean.length == 10 ? numVal * 1000 : numVal;
      final dt = DateTime.fromMillisecondsSinceEpoch(ms);
      return ParsedDate(epochMs: ms, isoDate: toIsoDate(dt));
    }

    final isoMatch = RegExp(r'^(\d{4})[-/](\d{1,2})[-/](\d{1,2})(?:[ T](\d{1,2}):(\d{1,2}))?').firstMatch(clean);
    if (isoMatch != null) {
      final y = int.parse(isoMatch.group(1)!);
      final m = int.parse(isoMatch.group(2)!).clamp(1, 12).toInt();
      final d = int.parse(isoMatch.group(3)!).clamp(1, 31).toInt();
      final hh = int.tryParse(isoMatch.group(4) ?? '12') ?? 12;
      final mm = int.tryParse(isoMatch.group(5) ?? '0') ?? 0;
      final dt = DateTime(y, m, d, hh.clamp(0, 23).toInt(), mm.clamp(0, 59).toInt());
      return ParsedDate(epochMs: dt.millisecondsSinceEpoch, isoDate: toIsoDate(dt));
    }

    final dmyMatch = RegExp(r'^(\d{1,2})[-/](\d{1,2})[-/](\d{4})').firstMatch(clean);
    if (dmyMatch != null) {
      final d = int.parse(dmyMatch.group(1)!).clamp(1, 31).toInt();
      final m = int.parse(dmyMatch.group(2)!).clamp(1, 12).toInt();
      final y = int.parse(dmyMatch.group(3)!);
      final dt = DateTime(y, m, d, 12, 0);
      return ParsedDate(epochMs: dt.millisecondsSinceEpoch, isoDate: toIsoDate(dt));
    }

    final dt = DateTime.fromMillisecondsSinceEpoch(fallback);
    return ParsedDate(epochMs: fallback, isoDate: toIsoDate(dt));
  }
}

class ParsedDate {
  const ParsedDate({required this.epochMs, required this.isoDate});
  final int epochMs;
  final String isoDate;
}
