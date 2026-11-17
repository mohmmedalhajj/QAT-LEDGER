import 'package:material_ui/material_ui.dart';

/// App theme options: Enterprise Light and Pure Black OLED Dark.
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

/// 8 built-in institutional palettes matching original Android `AppPalettes`, plus Custom Hex.
@immutable
class AppColorPreset {
  const AppColorPreset({
    required this.id,
    required this.nameAr,
    required this.subtitleAr,
    required this.primaryDark,
    required this.primaryNormal,
    required this.primaryLight,
    required this.primarySoftLight,
    required this.accentNormal,
    required this.accentLight,
    required this.goldColor,
  });

  final String id;
  final String nameAr;
  final String subtitleAr;
  final Color primaryDark;
  final Color primaryNormal;
  final Color primaryLight;
  final Color primarySoftLight;
  final Color accentNormal;
  final Color accentLight;
  final Color goldColor;

  static const AppColorPreset emeraldGreen = AppColorPreset(
    id: 'emerald_green',
    nameAr: 'أخضر ميزان القات المؤسسي (الرسمي)',
    subtitleAr: 'الهوية الرسمية الهادئة لنظام ميزان القات — QAT LEDGER',
    primaryDark: Color(0xFF104E39),
    primaryNormal: Color(0xFF267451),
    primaryLight: Color(0xFF2EC87E),
    primarySoftLight: Color(0xFFE2EFE9),
    accentNormal: Color(0xFFB67D24),
    accentLight: Color(0xFFDFA94A),
    goldColor: Color(0xFFC7A45B),
  );

  static const AppColorPreset sapphireBlue = AppColorPreset(
    id: 'sapphire_blue',
    nameAr: 'الأزرق الملكي الياقوتي',
    subtitleAr: 'نمط مؤسسي رفيع يعكس الدقة والاحترافية الحسابية',
    primaryDark: Color(0xFF0D2B52),
    primaryNormal: Color(0xFF15539E),
    primaryLight: Color(0xFF3B82F6),
    primarySoftLight: Color(0xFFE3EDF7),
    accentNormal: Color(0xFFD97706),
    accentLight: Color(0xFFF59E0B),
    goldColor: Color(0xFFFFB300),
  );

  static const AppColorPreset royalBurgundy = AppColorPreset(
    id: 'royal_burgundy',
    nameAr: 'العنابي والياقوت الإمبراطوري',
    subtitleAr: 'هيبة راقية وفخامة عربية متميزة للتقارير والواجهات',
    primaryDark: Color(0xFF4A0E17),
    primaryNormal: Color(0xFF8B1E2D),
    primaryLight: Color(0xFFF87171),
    primarySoftLight: Color(0xFFF7E8EB),
    accentNormal: Color(0xFF0284C7),
    accentLight: Color(0xFF38BDF8),
    goldColor: Color(0xFFD4AF37),
  );

  static const AppColorPreset deepTeal = AppColorPreset(
    id: 'deep_teal',
    nameAr: 'الفيروزي المحيطي الأنيق',
    subtitleAr: 'ألوان عصرية مريحة للعين مع تباين فائق للبيانات',
    primaryDark: Color(0xFF00363A),
    primaryNormal: Color(0xFF00695C),
    primaryLight: Color(0xFF14B8A6),
    primarySoftLight: Color(0xFFDCEDED),
    accentNormal: Color(0xFFEA580C),
    accentLight: Color(0xFFFB923C),
    goldColor: Color(0xFFFFD54F),
  );

  static const AppColorPreset imperialGold = AppColorPreset(
    id: 'imperial_gold',
    nameAr: 'الذهبي الملكي والعنبر الفاخر',
    subtitleAr: 'طابع الثراء التجاري المالي والتميز الموثوق',
    primaryDark: Color(0xFF4E3800),
    primaryNormal: Color(0xFF7A5900),
    primaryLight: Color(0xFFEAB308),
    primarySoftLight: Color(0xFFF7EED6),
    accentNormal: Color(0xFF15803D),
    accentLight: Color(0xFF4ADE80),
    goldColor: Color(0xFFFFD700),
  );

  static const AppColorPreset royalAmethyst = AppColorPreset(
    id: 'royal_amethyst',
    nameAr: 'البنفسجي الإمبراطوري الراقي',
    subtitleAr: 'لمسة أرستقراطية فريدة وجذابة لقراءة الحسابات',
    primaryDark: Color(0xFF2E114D),
    primaryNormal: Color(0xFF5B2396),
    primaryLight: Color(0xFFA855F7),
    primarySoftLight: Color(0xFFEDE2F5),
    accentNormal: Color(0xFF0D9488),
    accentLight: Color(0xFF2DD4BF),
    goldColor: Color(0xFFD4AF37),
  );

  static const AppColorPreset slateCharcoal = AppColorPreset(
    id: 'slate_charcoal',
    nameAr: 'الرمادي الفحمي والأونيكس المودرن',
    subtitleAr: 'طابع تقني حديث وهادئ يركز على تباين الأرقام',
    primaryDark: Color(0xFF1E272E),
    primaryNormal: Color(0xFF34495E),
    primaryLight: Color(0xFF94A3B8),
    primarySoftLight: Color(0xFFEAEDED),
    accentNormal: Color(0xFFD35400),
    accentLight: Color(0xFFF97316),
    goldColor: Color(0xFFF1C40F),
  );

  static const AppColorPreset navyBronze = AppColorPreset(
    id: 'navy_bronze',
    nameAr: 'الكحلي التجاري والبرونزي',
    subtitleAr: 'توازن مثالي بين الكحلي الداكن ولمسات البرونز والذهب',
    primaryDark: Color(0xFF0A192F),
    primaryNormal: Color(0xFF1B3A57),
    primaryLight: Color(0xFF38BDF8),
    primarySoftLight: Color(0xFFE2EBF4),
    accentNormal: Color(0xFFB4690E),
    accentLight: Color(0xFFDFA94A),
    goldColor: Color(0xFFD4AF37),
  );

  static const List<AppColorPreset> allPresets = [
    emeraldGreen,
    sapphireBlue,
    royalBurgundy,
    deepTeal,
    imperialGold,
    royalAmethyst,
    slateCharcoal,
    navyBronze,
  ];

  static Color? tryParseHex(String hex) {
    final cleaned = hex.trim().replaceFirst('#', '');
    if (cleaned.length == 6 && RegExp(r'^[0-9a-fA-F]{6}$').hasMatch(cleaned)) {
      return Color(int.parse('FF$cleaned', radix: 16));
    }
    if (cleaned.length == 8 && RegExp(r'^[0-9a-fA-F]{8}$').hasMatch(cleaned)) {
      return Color(int.parse(cleaned, radix: 16));
    }
    return null;
  }

  static String toHex(Color color) {
    final r = (color.r * 255).round().clamp(0, 255).toRadixString(16).padLeft(2, '0');
    final g = (color.g * 255).round().clamp(0, 255).toRadixString(16).padLeft(2, '0');
    final b = (color.b * 255).round().clamp(0, 255).toRadixString(16).padLeft(2, '0');
    return '#${r.toUpperCase()}${g.toUpperCase()}${b.toUpperCase()}';
  }

  static Color _scale(Color c, double factor) {
    return Color.from(
      alpha: 1.0,
      red: (c.r * factor).clamp(0.0, 1.0),
      green: (c.g * factor).clamp(0.0, 1.0),
      blue: (c.b * factor).clamp(0.0, 1.0),
    );
  }

  static AppColorPreset createCustom({
    required String primaryHex,
    String accentHex = '#C83E3B',
  }) {
    final p = tryParseHex(primaryHex) ?? emeraldGreen.primaryNormal;
    final a = tryParseHex(accentHex) ?? const Color(0xFFC83E3B);
    return AppColorPreset(
      id: 'custom',
      nameAr: 'النمط المخصص الفاخر',
      subtitleAr: 'ألوان مخصصة من اختيارك متناسقة للواجهات وملفات PDF',
      primaryDark: _scale(p, 0.58),
      primaryNormal: p,
      primaryLight: Color.lerp(p, Colors.white, 0.35)!,
      primarySoftLight: Color.lerp(p, Colors.white, 0.88)!,
      accentNormal: a,
      accentLight: Color.lerp(a, Colors.white, 0.3)!,
      goldColor: const Color(0xFFC7A45B),
    );
  }

  static AppColorPreset resolve({
    required String paletteId,
    String customPrimaryHex = '#267451',
    String customAccentHex = '#C83E3B',
  }) {
    if (paletteId == 'custom') {
      return createCustom(primaryHex: customPrimaryHex, accentHex: customAccentHex);
    }
    for (final p in allPresets) {
      if (p.id == paletteId) return p;
    }
    return emeraldGreen;
  }
}

/// Consistent ERP spacing tokens.
abstract final class ErpSpacing {
  static const double xs = 4;
  static const double sm = 8;
  static const double md = 12;
  static const double lg = 16;
  static const double xl = 24;
  static const double xxl = 32;
}

/// Consistent ERP corner radii.
abstract final class ErpRadius {
  static const double sm = 8;
  static const double md = 12;
  static const double lg = 16;
  static const double xl = 20;
}

/// Extended semantic design palette accessible via `context.pal`.
@immutable
class QatPalette extends ThemeExtension<QatPalette> {
  const QatPalette({
    required this.canvas,
    required this.card,
    required this.cardElevated,
    required this.tableHeader,
    required this.tableRowAlt,
    required this.border,
    required this.borderStrong,
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
    required this.info,
    required this.infoSoft,
    required this.yerBadgeBg,
    required this.yerBadgeFg,
    required this.sarBadgeBg,
    required this.sarBadgeFg,
    required this.isDark,
  });

  final Color canvas;
  final Color card;
  final Color cardElevated;
  final Color tableHeader;
  final Color tableRowAlt;
  final Color border;
  final Color borderStrong;
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
  final Color info;
  final Color infoSoft;
  final Color yerBadgeBg;
  final Color yerBadgeFg;
  final Color sarBadgeBg;
  final Color sarBadgeFg;
  final bool isDark;

  static QatPalette forPreset(
    AppColorPreset preset, {
    required bool isDark,
    String customCanvasHex = '',
    String customCardHex = '',
    String customTextHex = '',
  }) {
    final base = isDark ? dark : light;
    if (preset.id == AppColorPreset.emeraldGreen.id &&
        customCanvasHex.isEmpty &&
        customCardHex.isEmpty &&
        customTextHex.isEmpty) {
      return base;
    }

    final brand = isDark ? preset.primaryLight : preset.primaryDark;
    final brandSoft = isDark
        ? Color.lerp(preset.primaryDark, Colors.black, 0.55)!
        : preset.primarySoftLight;
    final accent = isDark ? preset.accentLight : preset.accentNormal;
    final accentSoft = isDark
        ? Color.lerp(preset.accentNormal, Colors.black, 0.72)!
        : Color.lerp(preset.accentNormal, Colors.white, 0.88)!;

    final canvasOverride = AppColorPreset.tryParseHex(customCanvasHex);
    final cardOverride = AppColorPreset.tryParseHex(customCardHex);
    final textOverride = AppColorPreset.tryParseHex(customTextHex);

    return base.copyWith(
      canvas: canvasOverride ?? base.canvas,
      card: cardOverride ?? base.card,
      textPrimary: textOverride ?? base.textPrimary,
      brand: brand,
      brandSoft: brandSoft,
      accent: accent,
      accentSoft: accentSoft,
      tableHeader: isDark ? brandSoft : preset.primarySoftLight,
      yerBadgeBg: brandSoft,
      yerBadgeFg: brand,
    );
  }

  static const QatPalette light = QatPalette(
    canvas: Color(0xFFF4F2EC),
    card: Color(0xFFFFFFFF),
    cardElevated: Color(0xFFECE8DF),
    tableHeader: Color(0xFFE7EFEA),
    tableRowAlt: Color(0xFFFAF9F5),
    border: Color(0xFFE0DBD0),
    borderStrong: Color(0xFFC8C0B0),
    brand: Color(0xFF104E39),
    brandSoft: Color(0xFFE2EFE9),
    accent: Color(0xFFB67D24),
    accentSoft: Color(0xFFFBF3E4),
    textPrimary: Color(0xFF111916),
    textSecondary: Color(0xFF53615A),
    success: Color(0xFF137A48),
    successSoft: Color(0xFFE1F5EA),
    warning: Color(0xFFB4690E),
    warningSoft: Color(0xFFFEF2DC),
    danger: Color(0xFFB42318),
    dangerSoft: Color(0xFFFEECEB),
    info: Color(0xFF155EEF),
    infoSoft: Color(0xFFEFF4FF),
    yerBadgeBg: Color(0xFFE2EFE9),
    yerBadgeFg: Color(0xFF104E39),
    sarBadgeBg: Color(0xFFF8EED9),
    sarBadgeFg: Color(0xFF8C5F13),
    isDark: false,
  );

  static const QatPalette dark = QatPalette(
    canvas: Color(0xFF000000),
    card: Color(0xFF0B100E),
    cardElevated: Color(0xFF141C19),
    tableHeader: Color(0xFF0D2319),
    tableRowAlt: Color(0xFF070B09),
    border: Color(0xFF202B26),
    borderStrong: Color(0xFF2F3E37),
    brand: Color(0xFF2EC87E),
    brandSoft: Color(0xFF0E291D),
    accent: Color(0xFFDFA94A),
    accentSoft: Color(0xFF282010),
    textPrimary: Color(0xFFF2F5F3),
    textSecondary: Color(0xFF98A69F),
    success: Color(0xFF34D38B),
    successSoft: Color(0xFF0B271B),
    warning: Color(0xFFF5A623),
    warningSoft: Color(0xFF291E0B),
    danger: Color(0xFFF87171),
    dangerSoft: Color(0xFF2C1212),
    info: Color(0xFF60A5FA),
    infoSoft: Color(0xFF101D33),
    yerBadgeBg: Color(0xFF0E2A1E),
    yerBadgeFg: Color(0xFF5EE3A4),
    sarBadgeBg: Color(0xFF29200E),
    sarBadgeFg: Color(0xFFF2C166),
    isDark: true,
  );

  @override
  QatPalette copyWith({
    Color? canvas,
    Color? card,
    Color? cardElevated,
    Color? tableHeader,
    Color? tableRowAlt,
    Color? border,
    Color? borderStrong,
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
    Color? info,
    Color? infoSoft,
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
      tableHeader: tableHeader ?? this.tableHeader,
      tableRowAlt: tableRowAlt ?? this.tableRowAlt,
      border: border ?? this.border,
      borderStrong: borderStrong ?? this.borderStrong,
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
      info: info ?? this.info,
      infoSoft: infoSoft ?? this.infoSoft,
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
      tableHeader: Color.lerp(tableHeader, other.tableHeader, t)!,
      tableRowAlt: Color.lerp(tableRowAlt, other.tableRowAlt, t)!,
      border: Color.lerp(border, other.border, t)!,
      borderStrong: Color.lerp(borderStrong, other.borderStrong, t)!,
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
      info: Color.lerp(info, other.info, t)!,
      infoSoft: Color.lerp(infoSoft, other.infoSoft, t)!,
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

  static ThemeData buildLight({
    AppColorPreset? preset,
    String customCanvasHex = '',
    String customCardHex = '',
    String customTextHex = '',
  }) =>
      _build(
        preset == null
            ? QatPalette.light
            : QatPalette.forPreset(
                preset,
                isDark: false,
                customCanvasHex: customCanvasHex,
                customCardHex: customCardHex,
                customTextHex: customTextHex,
              ),
        Brightness.light,
      );

  static ThemeData buildDark({
    AppColorPreset? preset,
    String customCanvasHex = '',
    String customCardHex = '',
    String customTextHex = '',
  }) =>
      _build(
        preset == null
            ? QatPalette.dark
            : QatPalette.forPreset(
                preset,
                isDark: true,
                customCanvasHex: customCanvasHex,
                customCardHex: customCardHex,
                customTextHex: customTextHex,
              ),
        Brightness.dark,
      );

  static ThemeData _build(QatPalette p, Brightness brightness) {
    final scheme = ColorScheme(
      brightness: brightness,
      primary: p.brand,
      onPrimary: p.isDark ? const Color(0xFF031B10) : Colors.white,
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
      outlineVariant: p.borderStrong,
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
        headlineMedium: baseText.headlineMedium?.copyWith(
          fontWeight: FontWeight.w700,
          fontSize: 24,
          height: 1.3,
        ),
        titleLarge: baseText.titleLarge?.copyWith(
          fontWeight: FontWeight.w700,
          fontSize: 19,
          height: 1.35,
        ),
        titleMedium: baseText.titleMedium?.copyWith(
          fontWeight: FontWeight.w700,
          fontSize: 15.5,
          height: 1.35,
        ),
        titleSmall: baseText.titleSmall?.copyWith(
          fontWeight: FontWeight.w600,
          fontSize: 13.5,
          height: 1.35,
        ),
        bodyLarge: baseText.bodyLarge?.copyWith(fontSize: 15, height: 1.45),
        bodyMedium: baseText.bodyMedium?.copyWith(fontSize: 13.5, height: 1.45),
        bodySmall: baseText.bodySmall?.copyWith(
          color: p.textSecondary,
          fontSize: 12,
          height: 1.4,
        ),
        labelLarge: baseText.labelLarge?.copyWith(
          fontWeight: FontWeight.w700,
          fontSize: 13,
        ),
        labelMedium: baseText.labelMedium?.copyWith(
          fontWeight: FontWeight.w600,
          fontSize: 11.5,
          color: p.textSecondary,
        ),
      ),
      appBarTheme: AppBarTheme(
        backgroundColor: p.card,
        foregroundColor: p.textPrimary,
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: false,
        titleTextStyle: TextStyle(
          fontFamily: fontFamily,
          fontSize: 17,
          fontWeight: FontWeight.w700,
          color: p.textPrimary,
        ),
        shape: Border(bottom: BorderSide(color: p.border, width: 1)),
      ),
      drawerTheme: DrawerThemeData(
        backgroundColor: p.card,
        surfaceTintColor: Colors.transparent,
        shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
      ),
      cardTheme: CardThemeData(
        color: p.card,
        elevation: 0,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(ErpRadius.lg),
          side: BorderSide(color: p.border, width: 1),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: p.cardElevated,
        isDense: true,
        contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(ErpRadius.md),
          borderSide: BorderSide(color: p.border),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(ErpRadius.md),
          borderSide: BorderSide(color: p.border),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(ErpRadius.md),
          borderSide: BorderSide(color: p.brand, width: 1.6),
        ),
        errorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(ErpRadius.md),
          borderSide: BorderSide(color: p.danger, width: 1.2),
        ),
        labelStyle: TextStyle(color: p.textSecondary, fontSize: 13),
        hintStyle: TextStyle(
          color: p.textSecondary.withValues(alpha: 0.72),
          fontSize: 13,
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          backgroundColor: p.brand,
          foregroundColor: p.isDark ? const Color(0xFF031B10) : Colors.white,
          minimumSize: const Size(0, 44),
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(ErpRadius.md),
          ),
          textStyle: const TextStyle(
            fontFamily: fontFamily,
            fontWeight: FontWeight.w700,
            fontSize: 13.5,
          ),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: p.textPrimary,
          side: BorderSide(color: p.borderStrong),
          minimumSize: const Size(0, 42),
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 9),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(ErpRadius.md),
          ),
          textStyle: const TextStyle(
            fontFamily: fontFamily,
            fontWeight: FontWeight.w600,
            fontSize: 13,
          ),
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          foregroundColor: p.brand,
          textStyle: const TextStyle(
            fontFamily: fontFamily,
            fontWeight: FontWeight.w700,
            fontSize: 13,
          ),
        ),
      ),
      chipTheme: ChipThemeData(
        backgroundColor: p.cardElevated,
        selectedColor: p.brandSoft,
        side: BorderSide(color: p.border),
        labelStyle: TextStyle(
          color: p.textPrimary,
          fontFamily: fontFamily,
          fontSize: 12,
          fontWeight: FontWeight.w600,
        ),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(ErpRadius.sm),
        ),
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: p.card,
        indicatorColor: p.brandSoft,
        height: 72,
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
          return IconThemeData(
            color: selected ? p.brand : p.textSecondary,
            size: 22,
          );
        }),
      ),
      navigationRailTheme: NavigationRailThemeData(
        backgroundColor: p.card,
        indicatorColor: p.brandSoft,
        selectedIconTheme: IconThemeData(color: p.brand, size: 22),
        unselectedIconTheme: IconThemeData(color: p.textSecondary, size: 21),
        selectedLabelTextStyle: TextStyle(
          fontFamily: fontFamily,
          fontWeight: FontWeight.w700,
          fontSize: 12,
          color: p.brand,
        ),
        unselectedLabelTextStyle: TextStyle(
          fontFamily: fontFamily,
          fontWeight: FontWeight.w500,
          fontSize: 12,
          color: p.textSecondary,
        ),
      ),
      dividerTheme: DividerThemeData(color: p.border, thickness: 1, space: 1),
      dialogTheme: DialogThemeData(
        backgroundColor: p.card,
        surfaceTintColor: Colors.transparent,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(ErpRadius.xl),
          side: BorderSide(color: p.borderStrong),
        ),
      ),
      bottomSheetTheme: BottomSheetThemeData(
        backgroundColor: p.card,
        surfaceTintColor: Colors.transparent,
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(22)),
        ),
      ),
      snackBarTheme: SnackBarThemeData(
        backgroundColor: p.isDark ? p.cardElevated : const Color(0xFF131D18),
        contentTextStyle: const TextStyle(
          fontFamily: fontFamily,
          color: Colors.white,
          fontSize: 13,
        ),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
    );
  }
}

extension QatThemeContext on BuildContext {
  QatPalette get pal => Theme.of(this).extension<QatPalette>()!;
}
