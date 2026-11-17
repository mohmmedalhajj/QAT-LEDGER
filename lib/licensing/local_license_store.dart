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
    if (existing != null && existing.trim().isNotEmpty) {
      return existing.trim().toUpperCase();
    }

    // Check if an upgraded legacy Kotlin install already had a `QAT-XXXX-XXXX-XXXX` install ID.
    try {
      final legacy = await PlatformBridge.instance.getLegacyMigrationSources();
      final installPrefs = legacy.legacyPrefs['qat_license_install_prefs'];
      final legacyInstallId =
          (installPrefs?['installation_id'] ?? '').toString().trim().toUpperCase();
      if (RegExp(r'^QAT-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$').hasMatch(legacyInstallId)) {
        await _prefs.setString(_prefInstallId, legacyInstallId);
        return legacyInstallId;
      }
    } catch (_) {
      // Ignore native bridge issues in pure Dart unit test environments
    }

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
        .take(6)
        .map((b) => b.toRadixString(16).padLeft(2, '0'))
        .join()
        .toUpperCase();
    // Matches Kotlin `QAT-XXXX-XXXX-XXXX` 12-char format cleanly!
    final formatted = 'QAT-${hex.substring(0, 4)}-${hex.substring(4, 8)}-${hex.substring(8, 12)}';
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

  /// Imports legacy `.qlic` or compact token from native `noBackupFilesDir/active_license.qlic`
  /// or `qat_active_license_store` / `qat_ledger_license_store` if present during first run.
  Future<void> importLegacyLicenseIfUnset() async {
    final current = getStoredRawLicense();
    if (current != null && current.trim().isNotEmpty) return;
    try {
      final legacy = await PlatformBridge.instance.getLegacyMigrationSources();
      if (legacy.legacyActiveLicenseDoc.trim().isNotEmpty) {
        await saveRawLicense(legacy.legacyActiveLicenseDoc);
        return;
      }
      for (final storeName in const ['qat_active_license_store', 'qat_ledger_license_store']) {
        final store = legacy.legacyPrefs[storeName];
        if (store == null) continue;
        for (final key in const ['signed_license_doc', 'active_license_doc', 'license_json', 'compact_token']) {
          final val = (store[key] ?? '').toString().trim();
          if (val.isNotEmpty) {
            await saveRawLicense(val);
            return;
          }
        }
      }
    } catch (_) {
      // Safe fallback
    }
  }

  Future<LicenseVerificationResult> loadAndVerify({
    required EditionConfig editionConfig,
    TrustedPublicKeyRegistry? extraDevRegistry,
  }) async {
    await importLegacyLicenseIfUnset();
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
