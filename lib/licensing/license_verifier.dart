import 'package:cryptography/cryptography.dart';

import 'license_canonical.dart';
import 'license_models.dart';
import 'trusted_key_registry.dart';
import 'trusted_time_guard.dart';

class OfflineLicenseVerifier {
  OfflineLicenseVerifier({
    required this.registry,
    required this.timeGuard,
    Ed25519? ed25519,
  }) : _ed25519 = ed25519 ?? Ed25519();

  final TrustedPublicKeyRegistry registry;
  final TrustedTimeGuard timeGuard;
  final Ed25519 _ed25519;

  Future<LicenseVerificationResult> verify({
    required String rawLicenseInput,
    required String currentInstallationId,
    int? nowEpochMs,
    bool allowUntrustedLocalKeyInDev = false,
    TrustedPublicKeyRegistry? extraDevRegistry,
    Set<String> revokedLicenseIds = const {},
  }) async {
    final DecodedLicenseInput decoded;
    try {
      decoded = LicenseCanonical.parseInput(rawLicenseInput);
    } catch (e) {
      return LicenseVerificationResult(
        kind: LicenseStateKind.corrupted,
        reason: e.toString(),
      );
    }

    final payload = decoded.payload;
    if (decoded.signatureBytes.length != 64) {
      return const LicenseVerificationResult(
        kind: LicenseStateKind.invalidSignature,
        reason: 'طول توقيع Ed25519 غير صحيح.',
      );
    }

    final hasEffectiveKeys = !registry.isEmpty ||
        (allowUntrustedLocalKeyInDev &&
            extraDevRegistry != null &&
            !extraDevRegistry.isEmpty);

    if (!hasEffectiveKeys) {
      return LicenseVerificationResult(
        kind: LicenseStateKind.untrustedKey,
        payload: payload,
        keyId: decoded.keyIdHint,
        reason:
            'لا توجد مفاتيح عامة موثوقة مدمجة في هذا البناء (QAT_LICENSE_PUBLIC_KEYS).',
      );
    }

    if (!decoded.hadExplicitKeyId &&
        (payload.canonicalFlavor == LicenseCanonicalFlavor.kotlinLineV1 ||
            decoded.isQlicJson)) {
      return LicenseVerificationResult(
        kind: LicenseStateKind.unknownKeyId,
        payload: payload,
        reason:
            'معرّف مفتاح التوقيع (keyId) مفقود أو غير معرّف داخل الترخيص (UNKNOWN_KEY_ID).',
      );
    }

    final candidateKeys = <MapEntry<String, SimplePublicKey>>[];
    if (decoded.keyIdHint != null && decoded.keyIdHint!.isNotEmpty) {
      final k = registry.findById(decoded.keyIdHint!);
      if (k != null) candidateKeys.add(MapEntry(decoded.keyIdHint!, k));
    } else {
      for (final id in registry.keyIds) {
        final k = registry.findById(id);
        if (k != null) candidateKeys.add(MapEntry(id, k));
      }
    }

    if (candidateKeys.isEmpty &&
        allowUntrustedLocalKeyInDev &&
        extraDevRegistry != null) {
      if (decoded.keyIdHint != null && decoded.keyIdHint!.isNotEmpty) {
        final k = extraDevRegistry.findById(decoded.keyIdHint!);
        if (k != null) candidateKeys.add(MapEntry(decoded.keyIdHint!, k));
      } else {
        for (final id in extraDevRegistry.keyIds) {
          final k = extraDevRegistry.findById(id);
          if (k != null) candidateKeys.add(MapEntry(id, k));
        }
      }
    }

    if (candidateKeys.isEmpty) {
      return LicenseVerificationResult(
        kind: LicenseStateKind.untrustedKey,
        payload: payload,
        keyId: decoded.keyIdHint,
        reason:
            'مفتاح التوقيع (${decoded.keyIdHint ?? ''}) غير معرّف كـ Public Key موثوق داخل هذه النسخة.',
      );
    }

    final messageBytes = LicenseCanonical.canonicalBytes(payload);
    String? matchedKeyId;
    SimplePublicKey? matchedPubKey;
    for (final entry in candidateKeys) {
      final sig = Signature(decoded.signatureBytes, publicKey: entry.value);
      final ok = await _ed25519.verify(messageBytes, signature: sig);
      if (ok) {
        matchedKeyId = entry.key;
        matchedPubKey = entry.value;
        break;
      }
    }

    if (matchedKeyId == null || matchedPubKey == null) {
      return LicenseVerificationResult(
        kind: LicenseStateKind.invalidSignature,
        payload: payload,
        keyId: decoded.keyIdHint,
        reason: 'فشل التحقق التشفيري Ed25519 — تم تعديل الترخيص أو التوقيع مزوّر.',
      );
    }

    if (decoded.keyIdHint == null || decoded.keyIdHint!.isEmpty) {
      final flavorKeyId = payload.canonicalFlavor == LicenseCanonicalFlavor.kotlinLineV1
          ? TrustedPublicKeyRegistry.computeKotlinKeyIdFromRaw(matchedPubKey.bytes)
          : TrustedPublicKeyRegistry.computeKeyIdFromRaw(matchedPubKey.bytes);
      if (registry.findById(flavorKeyId) != null ||
          (allowUntrustedLocalKeyInDev &&
              extraDevRegistry?.findById(flavorKeyId) != null)) {
        matchedKeyId = flavorKeyId;
      }
    }

    final normalizedRevoked = revokedLicenseIds.map((e) => e.trim().toUpperCase()).toSet();
    if (normalizedRevoked.contains(payload.licenseId.trim().toUpperCase())) {
      return LicenseVerificationResult(
        kind: LicenseStateKind.revoked,
        payload: payload,
        keyId: matchedKeyId,
        reason: 'تم إلغاء هذا الترخيص (${payload.licenseId}) رسمياً ولا يمكن تفعيله.',
      );
    }

    if (!payload.matchesInstallation(currentInstallationId)) {
      return LicenseVerificationResult(
        kind: LicenseStateKind.deviceMismatch,
        payload: payload,
        keyId: matchedKeyId,
        reason:
            'هذا الترخيص مرتبط بمعرّف تثبيت آخر (${payload.installationId}) ولا يطابق جهازك ($currentInstallationId).',
      );
    }

    final now = nowEpochMs ?? DateTime.now().millisecondsSinceEpoch;
    final skewMs = (payload.maxClockSkewSeconds * 1000).clamp(60000, 86400000).toInt();

    // Perpetual licenses don't expire and only require valid signature + install match
    if (payload.isPerpetual()) {
      return LicenseVerificationResult(
        kind: LicenseStateKind.valid,
        payload: payload,
        keyId: matchedKeyId,
      );
    }

    if (payload.expiresAtEpochMs <= payload.issuedAtEpochMs) {
      return LicenseVerificationResult(
        kind: LicenseStateKind.corrupted,
        payload: payload,
        keyId: matchedKeyId,
        reason: 'بيانات تاريخ انتهاء الاشتراك غير صالحة.',
      );
    }

    final clock = await timeGuard.evaluateNow(
      now,
      anchorEpochMs: payload.issuedAtEpochMs,
      overrideMaxRollbackMs: skewMs,
    );
    if (!clock.trusted) {
      return LicenseVerificationResult(
        kind: LicenseStateKind.clockTampered,
        payload: payload,
        keyId: matchedKeyId,
        reason:
            'تم اكتشاف إرجاع ساعة الجهاز للخلف بمقدار ${(clock.rollbackDeltaMs / 3600000).toStringAsFixed(1)} ساعة. عاد النظام إلى الباقة الأساسية (BASIC) مع بقاء جميع بياناتك محفوظة.',
      );
    }

    if (clock.effectiveNowMs > payload.expiresAtEpochMs) {
      return LicenseVerificationResult(
        kind: LicenseStateKind.expired,
        payload: payload,
        keyId: matchedKeyId,
        reason:
            'انتهت مدة صلاحية الاشتراك. تم إيقاف ميزات PRO فقط وبقيت جميع سجلاتك وبياناتك آمنة.',
        remainingDays: 0,
      );
    }

    final remDays = ((payload.expiresAtEpochMs - clock.effectiveNowMs) /
            Duration.millisecondsPerDay)
        .ceil()
        .clamp(1, 99999)
        .toInt();

    return LicenseVerificationResult(
      kind: LicenseStateKind.valid,
      payload: payload,
      keyId: matchedKeyId,
      remainingDays: remDays,
    );
  }

  /// Cryptographically verifies a signed `QLR1.` revocation notice against trusted Ed25519 keys.
  Future<RevocationVerificationResult> verifyRevocationToken({
    required String rawInput,
    required String currentInstallationId,
    bool allowUntrustedLocalKeyInDev = false,
    TrustedPublicKeyRegistry? extraDevRegistry,
  }) async {
    final DecodedRevocationInput decoded;
    try {
      decoded = LicenseCanonical.parseRevocationInput(rawInput);
    } catch (e) {
      return RevocationVerificationResult(
        kind: LicenseStateKind.corrupted,
        reason: e.toString(),
      );
    }

    final p = decoded.payload;
    if (decoded.signatureBytes.length != 64) {
      return const RevocationVerificationResult(
        kind: LicenseStateKind.invalidSignature,
        reason: 'طول توقيع إشعار الإلغاء غير صحيح.',
      );
    }
    if (p.keyId.trim().isEmpty) {
      return RevocationVerificationResult(
        kind: LicenseStateKind.unknownKeyId,
        payload: p,
        reason: 'معرّف مفتاح التوقيع مفقود في إشعار الإلغاء (UNKNOWN_KEY_ID).',
      );
    }

    SimplePublicKey? key = registry.findById(p.keyId);
    if (key == null && allowUntrustedLocalKeyInDev && extraDevRegistry != null) {
      key = extraDevRegistry.findById(p.keyId);
    }
    if (key == null) {
      return RevocationVerificationResult(
        kind: LicenseStateKind.untrustedKey,
        payload: p,
        reason: 'مفتاح إشعار الإلغاء (${p.keyId}) غير موثوق في هذه النسخة.',
      );
    }

    final ok = await _ed25519.verify(
      LicenseCanonical.canonicalRevocationBytes(p),
      signature: Signature(decoded.signatureBytes, publicKey: key),
    );
    if (!ok) {
      return RevocationVerificationResult(
        kind: LicenseStateKind.invalidSignature,
        payload: p,
        reason: 'فشل التحقق التشفيري من توقيع إشعار الإلغاء.',
      );
    }

    if (!p.matchesInstallation(currentInstallationId)) {
      return RevocationVerificationResult(
        kind: LicenseStateKind.deviceMismatch,
        payload: p,
        reason: 'إشعار الإلغاء مخصص لمعرّف تثبيت آخر.',
      );
    }

    return RevocationVerificationResult(
      kind: LicenseStateKind.revoked,
      payload: p,
      isAuthenticRevocation: true,
      reason: 'تم التحقق من إشعار إلغاء الترخيص ${p.licenseId} بنجاح.',
    );
  }
}

class RevocationVerificationResult {
  const RevocationVerificationResult({
    required this.kind,
    this.payload,
    this.isAuthenticRevocation = false,
    this.reason = '',
  });

  final LicenseStateKind kind;
  final RevocationPayload? payload;
  final bool isAuthenticRevocation;
  final String reason;
}
