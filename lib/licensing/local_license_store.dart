import 'dart:convert';
import 'dart:math';

import 'package:crypto/crypto.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../core/platform_bridge.dart';
import 'license_models.dart';
import 'license_verifier.dart';
import 'trusted_key_registry.dart';
import 'trusted_time_guard.dart';

class LocalLicenseStore {
  LocalLicenseStore(this._prefs);

  static const String _prefInstallId = 'qat_install_id_v2';
  static const String _prefActiveLicense = 'qat_active_license_raw_v2';

  final SharedPreferences _prefs;

  Future<String> getOrCreateInstallationId() async {
    final existing = _prefs.getString(_prefInstallId);
    if (existing != null && existing.isNotEmpty) return existing;

    final anchor = await PlatformBridge.instance.getHardwareAnchor();
    final androidId = anchor['androidId'] ?? '';
    final rnd = Random.secure();
    final nonce = List<int>.generate(16, (_) => rnd.nextInt(256));
    final seedInput = utf8.encode(
      'QAT_LEDGER_INSTALL:$androidId:${anchor['brand']}:${anchor['model']}:${base64.encode(nonce)}',
    );
    final hex = sha256
        .convert(seedInput)
        .bytes
        .take(8)
        .map((b) => b.toRadixString(16).padLeft(2, '0'))
        .join()
        .toUpperCase();
    final formatted = 'QLD-${hex.substring(0, 4)}-${hex.substring(4, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}';
    await _prefs.setString(_prefInstallId, formatted);
    return formatted;
  }

  String? getStoredRawLicense() => _prefs.getString(_prefActiveLicense);

  Future<void> saveRawLicense(String raw) async {
    await _prefs.setString(_prefActiveLicense, raw.trim());
  }

  Future<void> clearLicense() async {
    await _prefs.remove(_prefActiveLicense);
  }

  Future<LicenseVerificationResult> loadAndVerify({
    required EditionConfig editionConfig,
    TrustedPublicKeyRegistry? extraDevRegistry,
  }) async {
    final raw = getStoredRawLicense();
    if (raw == null || raw.trim().isEmpty) {
      return const LicenseVerificationResult(kind: LicenseStateKind.unlicensed);
    }
    final installId = await getOrCreateInstallationId();
    final registry = TrustedPublicKeyRegistry.fromCsv(editionConfig.publicKeysCsv);
    final verifier = OfflineLicenseVerifier(
      registry: registry,
      timeGuard: TrustedTimeGuard(prefs: _prefs),
    );
    return verifier.verify(
      rawLicenseInput: raw,
      currentInstallationId: installId,
      allowUntrustedLocalKeyInDev: editionConfig.isDeveloper,
      extraDevRegistry: extraDevRegistry,
    );
  }
}
