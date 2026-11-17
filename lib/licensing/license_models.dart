import 'package:flutter/foundation.dart';

enum PackageTier {
  basic('BASIC', 'الباقة الأساسية (BASIC)', 'QAT LEDGER BASIC'),
  pro('PRO', 'الباقة الاحترافية (PRO)', 'QAT LEDGER PRO');

  const PackageTier(this.code, this.arabicName, this.englishName);
  final String code;
  final String arabicName;
  final String englishName;

  static PackageTier fromRaw(String? raw) {
    final s = (raw ?? '').trim().toUpperCase();
    if (s.contains('PRO') || s == 'LIFETIME' || s == 'TRIAL_7' || s == 'TRIAL_14') {
      return PackageTier.pro;
    }
    return PackageTier.basic;
  }
}

enum LicenseType {
  perpetual('PERPETUAL', 'ترخيص دائم (مدى الحياة)'),
  subscription('SUBSCRIPTION', 'اشتراك محدد المدة');

  const LicenseType(this.code, this.arabicName);
  final String code;
  final String arabicName;

  static LicenseType fromRaw(String? raw) {
    final s = (raw ?? '').trim().toUpperCase();
    if (s == 'SUBSCRIPTION' || s.contains('اشتراك') || s.contains('MONTHLY') || s.contains('YEARLY') || s.contains('TRIAL')) {
      return LicenseType.subscription;
    }
    return LicenseType.perpetual;
  }
}

/// Official 12 commercial feature entitlements matching Kotlin `com.qatledger.licensing.LicenseFeature`.
/// Only `reports` (`REPORTS`) and `archive` (`ARCHIVE`) are PRO-only; all 10 core screens stay active in BASIC.
enum LicenseFeature {
  dashboard('DASHBOARD', 'لوحة الجلسة اليومية', false),
  statement('STATEMENT', 'كشف الحسابات المعتمد', false),
  sales('SALES', 'فواتير ومبيعات القات', false),
  customers('CUSTOMERS', 'العملاء والديون', false),
  accounts('ACCOUNTS', 'التحاسب وجرد الحسابات', false),
  transfers('TRANSFERS', 'الحوالات والصرافة', false),
  suppliers('SUPPLIERS', 'الرعيّة والمورّدين', false),
  inventory('INVENTORY', 'مخزون أصناف القات', false),
  expenses('EXPENSES', 'المصروفات ونثريات المفرش', false),
  settingsBasic('SETTINGS_BASIC', 'الإعدادات الأساسية والنسخ الاحتياطي', false),
  reports('REPORTS', 'التقارير المالية والأرباح', true),
  archive('ARCHIVE', 'الأرشيف الحسابي الشامل', true);

  const LicenseFeature(this.code, this.arabicTitle, this.isProOnly);
  final String code;
  final String arabicTitle;
  final bool isProOnly;

  static List<LicenseFeature> get basicFeatures =>
      values.where((f) => !f.isProOnly).toList(growable: false);

  static List<LicenseFeature> get proFeatures => values.toList(growable: false);

  static List<LicenseFeature> defaultFeaturesFor(PackageTier tier) =>
      tier == PackageTier.pro ? proFeatures : basicFeatures;

  static LicenseFeature? fromCode(String raw) {
    final clean = raw.trim().toUpperCase();
    for (final f in values) {
      if (f.code == clean) return f;
    }
    return null;
  }
}

enum LicenseCanonicalFlavor {
  kotlinLineV1,
  flutterJsonV1,
}

enum LicenseStateKind {
  unlicensed('النسخة الأساسية النشطة (بدون ترخيص PRO)', 'لا يوجد ترخيص PRO مفعّل — جميع الشاشات الأساسية تعمل بكفاءة'),
  valid('مرخّص ونشط', 'الترخيص صالح ويعمل دون إنترنت'),
  expired('انتهت صلاحية الاشتراك — رجوع تلقائي إلى BASIC', 'انتهت مدة صلاحية هذا الترخيص مع حفظ كافة البيانات'),
  deviceMismatch('الترخيص مقيّد بمعرّف تثبيت آخر', 'هذا الترخيص مخصص لمعرّف تثبيت آخر'),
  clockTampered('تم رصد تلاعب في ساعة الجهاز — ميزات PRO موقفة', 'تم رصد تراجع في ساعة النظام عن آخر توقيت موثوق'),
  invalidSignature('فشل التحقق التشفيري من التوقيع', 'فشل التحقق من التوقيع الرقمي Ed25519'),
  untrustedKey('مفتاح التوقيع غير معترف به في هذه النسخة', 'مفتاح الإصدار غير مدرج ضمن المفاتيح الموثوقة في التطبيق'),
  corrupted('صيغة الترخيص أو الكود غير صالحة', 'تعذر قراءة صيغة الترخيص');

  const LicenseStateKind(this.arabicTitle, this.arabicDescription);
  final String arabicTitle;
  final String arabicDescription;
}

@immutable
class LicensePayload {
  const LicensePayload({
    required this.format,
    required this.licenseId,
    required this.product,
    required this.edition,
    required this.customerName,
    required this.businessName,
    required this.planCode,
    required this.issuedAtEpochMs,
    required this.expiresAtEpochMs,
    required this.installationId,
    required this.maxDevices,
    required this.features,
    this.canonicalFlavor = LicenseCanonicalFlavor.flutterJsonV1,
    this.version = 1,
    this.packageTier,
    this.licenseType,
    this.phone = '',
    this.keyIdField = '',
    this.maxClockSkewSeconds = 300,
    this.notes = '',
  });

  final String format;
  final String licenseId;
  final String product;
  final String edition;
  final String customerName;
  final String businessName;
  final String planCode;
  final int issuedAtEpochMs;
  final int expiresAtEpochMs; // 0 => perpetual
  final String installationId; // '' or '*' or 'ANY' => unbound
  final int maxDevices;
  final List<String> features;
  final LicenseCanonicalFlavor canonicalFlavor;
  final int version;
  final PackageTier? packageTier;
  final LicenseType? licenseType;
  final String phone;
  final String keyIdField;
  final int maxClockSkewSeconds;
  final String notes;

  PackageTier get effectiveTier =>
      packageTier ?? PackageTier.fromRaw(planCode);

  LicenseType get effectiveLicenseType =>
      licenseType ?? (expiresAtEpochMs <= 0 ? LicenseType.perpetual : LicenseType.subscription);

  bool isPerpetual() => expiresAtEpochMs <= 0;

  bool matchesInstallation(String currentId) {
    final target = installationId.trim().toUpperCase();
    return target.isEmpty ||
        target == '*' ||
        target == 'ANY' ||
        target == currentId.trim().toUpperCase();
  }

  bool allowsFeature(LicenseFeature feature) {
    if (!feature.isProOnly) return true;
    if (effectiveTier != PackageTier.pro) return false;
    final norm = features.map((e) => e.trim().toUpperCase()).toSet();
    if (norm.contains(feature.code)) return true;
    // Legacy Flutter issued PRO tokens using `OFFLINE_PRO`
    if (norm.contains('OFFLINE_PRO')) return true;
    return false;
  }
}

@immutable
class LicenseEnvelope {
  const LicenseEnvelope({
    required this.format,
    required this.keyId,
    required this.algorithm,
    required this.payload,
    required this.signatureBase64Url,
  });

  final String format;
  final String keyId;
  final String algorithm;
  final LicensePayload payload;
  final String signatureBase64Url;
}

@immutable
class LicenseVerificationResult {
  const LicenseVerificationResult({
    required this.kind,
    this.payload,
    this.keyId,
    this.reason = '',
    this.remainingDays,
  });

  final LicenseStateKind kind;
  final LicensePayload? payload;
  final String? keyId;
  final String reason;
  final int? remainingDays;

  bool get isValid => kind == LicenseStateKind.valid;

  PackageTier get effectivePackage {
    if (!isValid || payload == null) return PackageTier.basic;
    return payload!.effectiveTier;
  }

  bool get isProActive => isValid && effectivePackage == PackageTier.pro;
  bool get isPro => isProActive;

  bool hasFeature(LicenseFeature feature) {
    if (!feature.isProOnly) return true;
    if (!isValid || payload == null) return false;
    return payload!.allowsFeature(feature);
  }

  String get arabicStatusSummary {
    if (kind == LicenseStateKind.unlicensed) {
      return LicenseStateKind.unlicensed.arabicTitle;
    }
    if (!isValid) {
      return reason.isNotEmpty ? reason : kind.arabicTitle;
    }
    final tier = effectivePackage;
    final perp = payload?.isPerpetual() ?? true;
    if (tier == PackageTier.pro) {
      return perp
          ? 'مرخّص — باقة PRO دائمة'
          : 'مرخّص — اشتراك PRO نشط${remainingDays != null ? ' (متبقي $remainingDays يوم)' : ''}';
    }
    return perp
        ? 'مرخّص — باقة BASIC دائمة'
        : 'مرخّص — اشتراك BASIC نشط${remainingDays != null ? ' (متبقي $remainingDays يوم)' : ''}';
  }
}
