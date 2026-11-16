import 'money.dart';

/// Date parsing & ISO-8601 formatting utilities preserving compatibility with legacy Room date strings.
abstract final class QatDateUtils {
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

  static String todayIso() => toIsoDate(DateTime.now());

  static int startOfDayMs(DateTime dt) =>
      DateTime(dt.year, dt.month, dt.day).millisecondsSinceEpoch;

  static int endOfDayMs(DateTime dt) =>
      DateTime(dt.year, dt.month, dt.day, 23, 59, 59, 999).millisecondsSinceEpoch;

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
