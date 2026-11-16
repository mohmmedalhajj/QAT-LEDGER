import 'package:material_ui/material_ui.dart';

/// App theme options: Yemeni-soft-inspired Enterprise Light & Pure Black OLED Dark.
enum AppThemeModeChoice {
  lightEnterprise('light_enterprise', 'المظهر الفاتح المؤسسي'),
  darkBlack('dark_black', 'المظهر الأسود الداكن');

  const AppThemeModeChoice(this.key, this.arabicLabel);
  final String key;
  final String arabicLabel;

  static AppThemeModeChoice fromKey(String? raw) {
    return raw == darkBlack.key ? darkBlack : lightEnterprise;
  }
}

/// Extended semantic design palette accessible via `Theme.of(context).extension<QatPalette>()!`.
@immutable
class QatPalette extends ThemeExtension<QatPalette> {
  const QatPalette({
    required this.canvas,
    required this.card,
    required this.cardElevated,
    required this.border,
    required this.brand,
    required this.brandSoft,
    required this.accent,
    required this.accentSoft,
    required this.textPrimary,
    required this.textSecondary,
    required this.success,
    required this.successSoft,
    required this.warning,
    required this.warningSoft,
    required this.danger,
    required this.dangerSoft,
    required this.yerBadgeBg,
    required this.yerBadgeFg,
    required this.sarBadgeBg,
    required this.sarBadgeFg,
    required this.isDark,
  });

  final Color canvas;
  final Color card;
  final Color cardElevated;
  final Color border;
  final Color brand;
  final Color brandSoft;
  final Color accent;
  final Color accentSoft;
  final Color textPrimary;
  final Color textSecondary;
  final Color success;
  final Color successSoft;
  final Color warning;
  final Color warningSoft;
  final Color danger;
  final Color dangerSoft;
  final Color yerBadgeBg;
  final Color yerBadgeFg;
  final Color sarBadgeBg;
  final Color sarBadgeFg;
  final bool isDark;

  static const QatPalette light = QatPalette(
    canvas: Color(0xFFF6F4EF),
    card: Color(0xFFFFFFFF),
    cardElevated: Color(0xFFEFECE4),
    border: Color(0xFFE2DDD1),
    brand: Color(0xFF144D3A),
    brandSoft: Color(0xFFE3F0EA),
    accent: Color(0xFFB8862D),
    accentSoft: Color(0xFFF9F1E1),
    textPrimary: Color(0xFF151B18),
    textSecondary: Color(0xFF56615B),
    success: Color(0xFF157F4D),
    successSoft: Color(0xFFE2F5EB),
    warning: Color(0xFFB56E14),
    warningSoft: Color(0xFFFDF2E2),
    danger: Color(0xFFB92B27),
    dangerSoft: Color(0xFFFDE8E8),
    yerBadgeBg: Color(0xFFE3F0EA),
    yerBadgeFg: Color(0xFF144D3A),
    sarBadgeBg: Color(0xFFF7EEDB),
    sarBadgeFg: Color(0xFF8C6214),
    isDark: false,
  );

  static const QatPalette dark = QatPalette(
    canvas: Color(0xFF000000),
    card: Color(0xFF0D1110),
    cardElevated: Color(0xFF161D1A),
    border: Color(0xFF212B27),
    brand: Color(0xFF2BB673),
    brandSoft: Color(0xFF112C20),
    accent: Color(0xFFE0A94C),
    accentSoft: Color(0xFF2B2212),
    textPrimary: Color(0xFFF3F4F2),
    textSecondary: Color(0xFF9BA5A0),
    success: Color(0xFF34C77B),
    successSoft: Color(0xFF0E291C),
    warning: Color(0xFFF0A33A),
    warningSoft: Color(0xFF2B1F0B),
    danger: Color(0xFFEF5350),
    dangerSoft: Color(0xFF2D1212),
    yerBadgeBg: Color(0xFF102A1E),
    yerBadgeFg: Color(0xFF5FE0A0),
    sarBadgeBg: Color(0xFF2B210E),
    sarBadgeFg: Color(0xFFF0BE66),
    isDark: true,
  );

  @override
  QatPalette copyWith({
    Color? canvas,
    Color? card,
    Color? cardElevated,
    Color? border,
    Color? brand,
    Color? brandSoft,
    Color? accent,
    Color? accentSoft,
    Color? textPrimary,
    Color? textSecondary,
    Color? success,
    Color? successSoft,
    Color? warning,
    Color? warningSoft,
    Color? danger,
    Color? dangerSoft,
    Color? yerBadgeBg,
    Color? yerBadgeFg,
    Color? sarBadgeBg,
    Color? sarBadgeFg,
    bool? isDark,
  }) {
    return QatPalette(
      canvas: canvas ?? this.canvas,
      card: card ?? this.card,
      cardElevated: cardElevated ?? this.cardElevated,
      border: border ?? this.border,
      brand: brand ?? this.brand,
      brandSoft: brandSoft ?? this.brandSoft,
      accent: accent ?? this.accent,
      accentSoft: accentSoft ?? this.accentSoft,
      textPrimary: textPrimary ?? this.textPrimary,
      textSecondary: textSecondary ?? this.textSecondary,
      success: success ?? this.success,
      successSoft: successSoft ?? this.successSoft,
      warning: warning ?? this.warning,
      warningSoft: warningSoft ?? this.warningSoft,
      danger: danger ?? this.danger,
      dangerSoft: dangerSoft ?? this.dangerSoft,
      yerBadgeBg: yerBadgeBg ?? this.yerBadgeBg,
      yerBadgeFg: yerBadgeFg ?? this.yerBadgeFg,
      sarBadgeBg: sarBadgeBg ?? this.sarBadgeBg,
      sarBadgeFg: sarBadgeFg ?? this.sarBadgeFg,
      isDark: isDark ?? this.isDark,
    );
  }

  @override
  QatPalette lerp(ThemeExtension<QatPalette>? other, double t) {
    if (other is! QatPalette) return this;
    return QatPalette(
      canvas: Color.lerp(canvas, other.canvas, t)!,
      card: Color.lerp(card, other.card, t)!,
      cardElevated: Color.lerp(cardElevated, other.cardElevated, t)!,
      border: Color.lerp(border, other.border, t)!,
      brand: Color.lerp(brand, other.brand, t)!,
      brandSoft: Color.lerp(brandSoft, other.brandSoft, t)!,
      accent: Color.lerp(accent, other.accent, t)!,
      accentSoft: Color.lerp(accentSoft, other.accentSoft, t)!,
      textPrimary: Color.lerp(textPrimary, other.textPrimary, t)!,
      textSecondary: Color.lerp(textSecondary, other.textSecondary, t)!,
      success: Color.lerp(success, other.success, t)!,
      successSoft: Color.lerp(successSoft, other.successSoft, t)!,
      warning: Color.lerp(warning, other.warning, t)!,
      warningSoft: Color.lerp(warningSoft, other.warningSoft, t)!,
      danger: Color.lerp(danger, other.danger, t)!,
      dangerSoft: Color.lerp(dangerSoft, other.dangerSoft, t)!,
      yerBadgeBg: Color.lerp(yerBadgeBg, other.yerBadgeBg, t)!,
      yerBadgeFg: Color.lerp(yerBadgeFg, other.yerBadgeFg, t)!,
      sarBadgeBg: Color.lerp(sarBadgeBg, other.sarBadgeBg, t)!,
      sarBadgeFg: Color.lerp(sarBadgeFg, other.sarBadgeFg, t)!,
      isDark: t < 0.5 ? isDark : other.isDark,
    );
  }
}

abstract final class AppTheme {
  static const String fontFamily = 'IBMPlexSansArabic';

  static ThemeData buildLight() => _build(QatPalette.light, Brightness.light);
  static ThemeData buildDark() => _build(QatPalette.dark, Brightness.dark);

  static ThemeData _build(QatPalette p, Brightness brightness) {
    final scheme = ColorScheme(
      brightness: brightness,
      primary: p.brand,
      onPrimary: p.isDark ? const Color(0xFF041C10) : Colors.white,
      primaryContainer: p.brandSoft,
      onPrimaryContainer: p.brand,
      secondary: p.accent,
      onSecondary: p.isDark ? Colors.black : Colors.white,
      secondaryContainer: p.accentSoft,
      onSecondaryContainer: p.accent,
      surface: p.card,
      onSurface: p.textPrimary,
      surfaceContainerHighest: p.cardElevated,
      onSurfaceVariant: p.textSecondary,
      error: p.danger,
      onError: Colors.white,
      outline: p.border,
      outlineVariant: p.border,
    );

    final baseText = ThemeData(brightness: brightness, fontFamily: fontFamily).textTheme.apply(
          fontFamily: fontFamily,
          bodyColor: p.textPrimary,
          displayColor: p.textPrimary,
        );

    return ThemeData(
      useMaterial3: true,
      brightness: brightness,
      fontFamily: fontFamily,
      colorScheme: scheme,
      scaffoldBackgroundColor: p.canvas,
      extensions: [p],
      textTheme: baseText.copyWith(
        headlineMedium: baseText.headlineMedium?.copyWith(fontWeight: FontWeight.w700),
        titleLarge: baseText.titleLarge?.copyWith(fontWeight: FontWeight.w700),
        titleMedium: baseText.titleMedium?.copyWith(fontWeight: FontWeight.w600),
        bodyMedium: baseText.bodyMedium?.copyWith(fontSize: 14, height: 1.45),
        bodySmall: baseText.bodySmall?.copyWith(color: p.textSecondary, fontSize: 12),
      ),
      appBarTheme: AppBarTheme(
        backgroundColor: p.card,
        foregroundColor: p.textPrimary,
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: false,
        titleTextStyle: TextStyle(
          fontFamily: fontFamily,
          fontSize: 18,
          fontWeight: FontWeight.w700,
          color: p.textPrimary,
        ),
        shape: Border(bottom: BorderSide(color: p.border, width: 1)),
      ),
      cardTheme: CardThemeData(
        color: p.card,
        elevation: 0,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(14),
          side: BorderSide(color: p.border, width: 1),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: p.cardElevated,
        contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: p.border),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: p.border),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: p.brand, width: 1.6),
        ),
        labelStyle: TextStyle(color: p.textSecondary, fontSize: 13),
        hintStyle: TextStyle(color: p.textSecondary.withValues(alpha: 0.7), fontSize: 13),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          backgroundColor: p.brand,
          foregroundColor: p.isDark ? const Color(0xFF041C10) : Colors.white,
          minimumSize: const Size(0, 46),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          textStyle: const TextStyle(fontFamily: fontFamily, fontWeight: FontWeight.w700, fontSize: 14),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: p.textPrimary,
          side: BorderSide(color: p.border),
          minimumSize: const Size(0, 44),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          textStyle: const TextStyle(fontFamily: fontFamily, fontWeight: FontWeight.w600, fontSize: 13),
        ),
      ),
      chipTheme: ChipThemeData(
        backgroundColor: p.cardElevated,
        selectedColor: p.brandSoft,
        side: BorderSide(color: p.border),
        labelStyle: TextStyle(color: p.textPrimary, fontFamily: fontFamily, fontSize: 12),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: p.card,
        indicatorColor: p.brandSoft,
        labelTextStyle: WidgetStateProperty.resolveWith((states) {
          final selected = states.contains(WidgetState.selected);
          return TextStyle(
            fontFamily: fontFamily,
            fontSize: 11,
            fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
            color: selected ? p.brand : p.textSecondary,
          );
        }),
        iconTheme: WidgetStateProperty.resolveWith((states) {
          final selected = states.contains(WidgetState.selected);
          return IconThemeData(color: selected ? p.brand : p.textSecondary, size: 22);
        }),
      ),
      dividerTheme: DividerThemeData(color: p.border, thickness: 1, space: 1),
      dialogTheme: DialogThemeData(
        backgroundColor: p.card,
        surfaceTintColor: Colors.transparent,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(18),
          side: BorderSide(color: p.border),
        ),
      ),
      snackBarTheme: SnackBarThemeData(
        backgroundColor: p.isDark ? p.cardElevated : const Color(0xFF151B18),
        contentTextStyle: const TextStyle(fontFamily: fontFamily, color: Colors.white),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
    );
  }
}

extension QatThemeContext on BuildContext {
  QatPalette get pal => Theme.of(this).extension<QatPalette>()!;
}
