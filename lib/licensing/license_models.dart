import 'package:flutter/foundation.dart';

enum LicenseStateKind {
  unlicensed('غير مفعل', 'لا يوجد ترخيص نشط على هذا الجهاز'),
  valid('مفعل', 'الترخيص صالح ويعمل دون إنترنت'),
  expired('منتهي الصلاحية', 'انتهت مدة صلاحية هذا الترخيص'),
  deviceMismatch('غير مطابق للجهاز', 'هذا الترخيص مخصص لمعرف جهاز آخر'),
  clockTampered('خطأ في ساعة الجهاز', 'تم رصد تراجع في ساعة النظام عن آخر توقيت موثوق'),
  invalidSignature('توقيع غير صالح', 'فشل التحقق من التوقيع الرقمي Ed25519'),
  untrustedKey('مفتاح غير موثوق', 'مفتاح الإصدار غير مدرج ضمن المفاتيح الموثوقة في التطبيق'),
  corrupted('ملف ترخيص تالف', 'تعذر قراءة صيغة الترخيص');

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
  });

  final String format;
  final String licenseId;
  final String product;
  final String edition;
  final String customerName;
  final String businessName;
  final String planCode;
  final int issuedAtEpochMs;
  final int expiresAtEpochMs;
  final String installationId;
  final int maxDevices;
  final List<String> features;

  bool isPerpetual() => expiresAtEpochMs <= 0;

  bool matchesInstallation(String currentId) {
    final target = installationId.trim().toUpperCase();
    return target == '*' || target == 'ANY' || target == currentId.trim().toUpperCase();
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
}
