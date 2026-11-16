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
  }) async {
    DecodedLicenseInput decoded;
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

    final candidateKeys = <MapEntry<String, SimplePublicKey>>[];
    if (decoded.keyIdHint != null) {
      final k = registry.findById(decoded.keyIdHint!);
      if (k != null) candidateKeys.add(MapEntry(decoded.keyIdHint!, k));
    } else {
      for (final id in registry.keyIds) {
        final k = registry.findById(id);
        if (k != null) candidateKeys.add(MapEntry(id, k));
      }
    }

    if (candidateKeys.isEmpty && allowUntrustedLocalKeyInDev && extraDevRegistry != null) {
      if (decoded.keyIdHint != null) {
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
        reason: registry.isEmpty
            ? 'لا توجد مفاتيح عامة موثوقة مدمجة في هذا البناء (QAT_LICENSE_PUBLIC_KEYS).'
            : 'معرف المفتاح ${decoded.keyIdHint ?? ''} غير موثوق.',
      );
    }

    final messageBytes = LicenseCanonical.canonicalBytes(payload);
    String? matchedKeyId;
    for (final entry in candidateKeys) {
      final sig = Signature(decoded.signatureBytes, publicKey: entry.value);
      final ok = await _ed25519.verify(messageBytes, signature: sig);
      if (ok) {
        matchedKeyId = entry.key;
        break;
      }
    }

    if (matchedKeyId == null) {
      return LicenseVerificationResult(
        kind: LicenseStateKind.invalidSignature,
        payload: payload,
        reason: 'فشل التحقق من التوقيع الرقمي Ed25519.',
      );
    }

    if (!payload.matchesInstallation(currentInstallationId)) {
      return LicenseVerificationResult(
        kind: LicenseStateKind.deviceMismatch,
        payload: payload,
        keyId: matchedKeyId,
        reason: 'معرف الجهاز في الترخيص (${payload.installationId}) لا يطابق الجهاز الحالي ($currentInstallationId).',
      );
    }

    final now = nowEpochMs ?? DateTime.now().millisecondsSinceEpoch;
    final clock = await timeGuard.evaluateNow(now, anchorEpochMs: payload.issuedAtEpochMs);
    if (!clock.trusted) {
      return LicenseVerificationResult(
        kind: LicenseStateKind.clockTampered,
        payload: payload,
        keyId: matchedKeyId,
        reason: 'ساعة النظام متراجعة بمقدار ${(clock.rollbackDeltaMs / 3600000).toStringAsFixed(1)} ساعة.',
      );
    }

    if (!payload.isPerpetual() && clock.effectiveNowMs > payload.expiresAtEpochMs) {
      return LicenseVerificationResult(
        kind: LicenseStateKind.expired,
        payload: payload,
        keyId: matchedKeyId,
        reason: 'انتهت صلاحية الترخيص.',
        remainingDays: 0,
      );
    }

    int? remDays;
    if (!payload.isPerpetual()) {
      final diffMs = payload.expiresAtEpochMs - clock.effectiveNowMs;
      remDays = (diffMs / const Duration(days: 1).inMilliseconds).ceil().clamp(0, 99999);
    }

    return LicenseVerificationResult(
      kind: LicenseStateKind.valid,
      payload: payload,
      keyId: matchedKeyId,
      remainingDays: remDays,
    );
  }
}
