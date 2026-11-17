import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

@immutable
class EditionConfig {
  const EditionConfig({
    required this.edition,
    required this.publicKeysCsv,
    required this.applicationId,
    required this.versionName,
    required this.versionCode,
  });

  final String edition;
  final String publicKeysCsv;
  final String applicationId;
  final String versionName;
  final int versionCode;

  bool get isDeveloper => edition == 'developer';
  bool get isCustomer => !isDeveloper;
}

@immutable
class LegacyMigrationSources {
  const LegacyMigrationSources({
    required this.legacyRoomDbExists,
    required this.legacyRoomDbPath,
    required this.legacyDevDbExists,
    required this.legacyDevDbPath,
    required this.legacyPrefs,
    this.legacyActiveLicenseDoc = '',
  });

  final bool legacyRoomDbExists;
  final String legacyRoomDbPath;
  final bool legacyDevDbExists;
  final String legacyDevDbPath;
  final Map<String, Map<String, Object?>> legacyPrefs;
  final String legacyActiveLicenseDoc;

  static const empty = LegacyMigrationSources(
    legacyRoomDbExists: false,
    legacyRoomDbPath: '',
    legacyDevDbExists: false,
    legacyDevDbPath: '',
    legacyPrefs: {},
    legacyActiveLicenseDoc: '',
  );
}

@immutable
class BondedPrinter {
  const BondedPrinter({
    required this.name,
    required this.address,
  });
  final String name;
  final String address;

  bool get isLikelyPrinter {
    final lower = name.toLowerCase();
    const audioKeywords = [
      'earbud', 'airpod', 'headphone', 'speaker', 'watch', 'band',
      'tv', 'car', 'buds', 'jbl', 'sony', 'bose', 'beats', 'soundbar',
      'سماعة', 'ساعة', 'تلفاز', 'سيارة',
    ];
    if (audioKeywords.any(lower.contains)) return false;
    const printerKeywords = [
      'printer', 'pos', 'print', 'thermal', 'mtp', 'rpp', 'xp-', 'pt-',
      'gp-', 'zj', 'innerprinter', 'bixolon', 'epson', 'star', 'zjiang',
      'gprinter', 'xprinter', 'sunmi', 'imin', 'rongta', 'milestone', 'طابعة',
    ];
    return printerKeywords.any(lower.contains);
  }

  String get deviceCategoryLabel =>
      isLikelyPrinter ? 'طابعة حرارية مرشحة' : 'جهاز بلوتوث عام (تحقق قبل الطباعة)';
}

@immutable
class PickedContact {
  const PickedContact({required this.name, required this.phone});
  final String name;
  final String phone;
}

/// Bridge to native Android channels with clean offline/unit-test fallbacks.
class PlatformBridge {
  PlatformBridge._();
  static final PlatformBridge instance = PlatformBridge._();

  static const MethodChannel _channel = MethodChannel('com.qatledger.app/platform');

  static const String _dartDefineEdition =
      String.fromEnvironment('APP_EDITION', defaultValue: '');
  static const String _dartDefineKeys =
      String.fromEnvironment('QAT_LICENSE_PUBLIC_KEYS', defaultValue: '');

  static bool _isUninitializedBindingError(FlutterError error) =>
      error.message.contains('Binding has not yet been initialized');

  Future<EditionConfig> getEditionConfig() async {
    try {
      final raw = await _channel.invokeMapMethod<String, dynamic>('getEditionConfig');
      if (raw != null) {
        final nativeEdition = (raw['edition'] as String?) ?? 'customer';
        final nativeKeys = (raw['publicKeysCsv'] as String?) ?? '';
        return EditionConfig(
          edition: _dartDefineEdition.isNotEmpty ? _dartDefineEdition : nativeEdition,
          publicKeysCsv: _dartDefineKeys.isNotEmpty ? _dartDefineKeys : nativeKeys,
          applicationId: (raw['applicationId'] as String?) ?? 'com.aistudio.tofanqat.nxzwmb',
          versionName: (raw['versionName'] as String?) ?? '2.0.0',
          versionCode: (raw['versionCode'] as int?) ?? 200,
        );
      }
    } on MissingPluginException {
      // Fallback for unit tests / non-Android desktop hosts
    } on FlutterError catch (e) {
      if (!_isUninitializedBindingError(e)) rethrow;
    }
    return const EditionConfig(
      edition: _dartDefineEdition == 'developer' ? 'developer' : 'customer',
      publicKeysCsv: _dartDefineKeys,
      applicationId: 'com.aistudio.tofanqat.nxzwmb',
      versionName: '2.0.0',
      versionCode: 200,
    );
  }

  Future<Map<String, String>> getHardwareAnchor() async {
    try {
      final raw = await _channel.invokeMapMethod<String, dynamic>('getInstallationHardwareAnchor');
      if (raw != null) {
        return raw.map<String, String>(
          (k, v) => MapEntry<String, String>(k, (v ?? '').toString()),
        );
      }
    } on MissingPluginException {
      // Test/desktop fallback
    } on FlutterError catch (e) {
      if (!_isUninitializedBindingError(e)) rethrow;
    }
    return const {'androidId': '', 'brand': 'local', 'model': 'host'};
  }

  Future<LegacyMigrationSources> getLegacyMigrationSources() async {
    try {
      final raw = await _channel.invokeMapMethod<String, dynamic>('getLegacyMigrationSources');
      if (raw == null) return LegacyMigrationSources.empty;
      final prefsRaw = raw['legacyPrefs'];
      final prefs = <String, Map<String, Object?>>{};
      if (prefsRaw is Map<Object?, Object?>) {
        for (final entry in prefsRaw.entries) {
          final sub = entry.value;
          if (sub is Map<Object?, Object?>) {
            prefs[entry.key.toString()] = sub.map<String, Object?>(
              (k, v) => MapEntry<String, Object?>(k.toString(), v),
            );
          }
        }
      }
      return LegacyMigrationSources(
        legacyRoomDbExists: (raw['legacyRoomDbExists'] as bool?) ?? false,
        legacyRoomDbPath: (raw['legacyRoomDbPath'] as String?) ?? '',
        legacyDevDbExists: (raw['legacyDevDbExists'] as bool?) ?? false,
        legacyDevDbPath: (raw['legacyDevDbPath'] as String?) ?? '',
        legacyPrefs: prefs,
        legacyActiveLicenseDoc: (raw['legacyActiveLicenseDoc'] as String?) ?? '',
      );
    } on MissingPluginException {
      return LegacyMigrationSources.empty;
    } on FlutterError catch (e) {
      if (!_isUninitializedBindingError(e)) rethrow;
      return LegacyMigrationSources.empty;
    }
  }

  Future<String?> keystoreSeal(String plaintextB64) async {
    try {
      return await _channel.invokeMethod<String>('keystoreSeal', {
        'plaintextB64': plaintextB64,
      });
    } on MissingPluginException {
      return null;
    } on FlutterError catch (e) {
      if (!_isUninitializedBindingError(e)) rethrow;
      return null;
    }
  }

  Future<String?> keystoreUnseal(String sealedPayload) async {
    try {
      return await _channel.invokeMethod<String>('keystoreUnseal', {
        'sealedPayload': sealedPayload,
      });
    } on MissingPluginException {
      return null;
    } on FlutterError catch (e) {
      if (!_isUninitializedBindingError(e)) rethrow;
      return null;
    }
  }

  Future<List<BondedPrinter>> getBondedPrinters() async {
    try {
      final items = await _channel.invokeListMethod<dynamic>('getBondedPrinters');
      if (items == null) return const [];
      final printers = items
          .whereType<Map<dynamic, dynamic>>()
          .map(
            (m) => BondedPrinter(
              name: (m['name'] ?? 'طابعة بلوتوث').toString(),
              address: (m['address'] ?? '').toString(),
            ),
          )
          .where((p) => p.address.isNotEmpty)
          .toList();
      printers.sort((a, b) => (b.isLikelyPrinter ? 1 : 0) - (a.isLikelyPrinter ? 1 : 0));
      return printers;
    } on MissingPluginException {
      return const [];
    } on FlutterError catch (e) {
      if (!_isUninitializedBindingError(e)) rethrow;
      return const [];
    }
  }

  Future<bool> printEscPosBytes(String address, Uint8List bytes) async {
    final res = await _channel.invokeMethod<bool>('printEscPosBytes', {
      'address': address,
      'bytes': bytes,
    });
    return res ?? false;
  }

  Future<PickedContact?> pickPhoneContact() async {
    try {
      final map = await _channel.invokeMapMethod<String, dynamic>('pickPhoneContact');
      if (map == null) return null;
      final name = (map['name'] ?? '').toString().trim();
      final phone = (map['phone'] ?? '').toString().trim();
      if (name.isEmpty && phone.isEmpty) return null;
      return PickedContact(name: name, phone: phone);
    } on MissingPluginException {
      return null;
    } on FlutterError catch (e) {
      if (!_isUninitializedBindingError(e)) rethrow;
      return null;
    }
  }

  Future<bool> applyLauncherIdentity({
    required String appName,
    String logoPath = '',
  }) async {
    try {
      final ok = await _channel.invokeMethod<bool>('applyLauncherIdentity', {
        'appName': appName,
        'logoPath': logoPath,
      });
      return ok ?? false;
    } on MissingPluginException {
      return false;
    } on FlutterError catch (e) {
      if (!_isUninitializedBindingError(e)) rethrow;
      return false;
    }
  }
}
