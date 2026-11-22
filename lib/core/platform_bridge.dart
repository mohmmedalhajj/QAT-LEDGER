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
    this.majorDeviceClass = 7936,
    this.deviceClass = 0,
    this.isImagingDevice = false,
    this.supportsSppUuid = false,
    this.isAudioOrWearable = false,
  });

  final String name;
  final String address;
  final int majorDeviceClass;
  final int deviceClass;
  final bool isImagingDevice;
  final bool supportsSppUuid;
  final bool isAudioOrWearable;

  static const _audioKeywords = [
    'earbud', 'airpod', 'headphone', 'speaker', 'watch', 'band',
    'tv', 'car', 'buds', 'jbl', 'sony', 'bose', 'beats', 'soundbar',
    'سماعة', 'ساعة', 'تلفاز', 'سيارة', 'سماعه',
  ];

  static const _printerKeywords = [
    'printer', 'pos', 'print', 'thermal', 'mtp', 'rpp', 'xp-', 'pt-',
    'gp-', 'zj', 'innerprinter', 'bixolon', 'epson', 'star', 'zjiang',
    'gprinter', 'xprinter', 'sunmi', 'imin', 'rongta', 'milestone', 'طابعة',
    'حرارية',
  ];

  bool get _hasAudioName {
    final lower = name.toLowerCase();
    return _audioKeywords.any(lower.contains);
  }

  bool get _hasPrinterName {
    final lower = name.toLowerCase();
    return _printerKeywords.any(lower.contains);
  }

  bool get isBlockedNonPrinter =>
      (isAudioOrWearable || _hasAudioName) && !isImagingDevice && !_hasPrinterName;

  bool get isLikelyPrinter {
    if (isBlockedNonPrinter) return false;
    if (isImagingDevice) return true;
    if (_hasPrinterName) return true;
    // Uncategorized / Peripheral devices advertising Bluetooth SPP serial port
    if (supportsSppUuid && (majorDeviceClass == 7936 || majorDeviceClass == 1280)) {
      return true;
    }
    return false;
  }

  String get deviceCategoryLabel {
    if (isBlockedNonPrinter) {
      return 'جهاز غير طابعة (سماعة/ساعة/هاتف) — مستبعد من الطباعة';
    }
    if (isImagingDevice) {
      return 'طابعة حرارية معتمدة (Bluetooth IMAGING)';
    }
    if (isLikelyPrinter) {
      return supportsSppUuid
          ? 'طابعة حرارية مرشحة (SPP Serial)'
          : 'طابعة حرارية مرشحة';
    }
    return 'جهاز بلوتوث عام — تحقق من أنه طابعة قبل الاعتماد';
  }
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
        final combinedKeys = mergePublicKeyLists(nativeKeys, _dartDefineKeys);
        return EditionConfig(
          edition: _dartDefineEdition.isNotEmpty ? _dartDefineEdition : nativeEdition,
          publicKeysCsv: combinedKeys,
          applicationId: (raw['applicationId'] as String?) ?? 'com.aistudio.tofanqat.qatledger.customer',
          versionName: (raw['versionName'] as String?) ?? '2.1.0',
          versionCode: (raw['versionCode'] as int?) ?? 201,
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
      applicationId: 'com.aistudio.tofanqat.qatledger.customer',
      versionName: '2.1.0',
      versionCode: 201,
    );
  }

  /// Merges native (BuildConfig/gradle.properties) and `--dart-define` public keys
  /// without letting one source shadow the other, preserving multiline PEM blocks.
  static String mergePublicKeyLists(String primaryRaw, String secondaryRaw) {
    final seen = <String>{};
    final out = <String>[];
    for (final raw in [primaryRaw, secondaryRaw]) {
      final trimmed = raw.trim();
      if (trimmed.isEmpty) continue;
      for (final token in _splitPublicKeyTokens(trimmed)) {
        final normalized = token.trim();
        if (normalized.isEmpty) continue;
        final dedupKey = normalized.replaceAll(RegExp(r'\s+'), '');
        if (seen.add(dedupKey)) {
          out.add(normalized);
        }
      }
    }
    return out.join(',');
  }

  static List<String> _splitPublicKeyTokens(String raw) {
    final tokens = <String>[];
    final buf = StringBuffer();
    bool inPem = false;
    for (final line in raw.replaceAll('\r', '').split('\n')) {
      final t = line.trim();
      if (t.contains('-----BEGIN')) {
        if (buf.isNotEmpty) {
          for (final part in buf.toString().split(RegExp(r'[,;]+'))) {
            if (part.trim().isNotEmpty) tokens.add(part.trim());
          }
          buf.clear();
        }
        inPem = true;
        buf.writeln(t);
        continue;
      }
      if (inPem) {
        buf.writeln(t);
        if (t.contains('-----END')) {
          tokens.add(buf.toString().trim());
          buf.clear();
          inPem = false;
        }
        continue;
      }
      for (final part in t.split(RegExp(r'[,;]+'))) {
        final cleaned = part.trim();
        if (cleaned.isNotEmpty) tokens.add(cleaned);
      }
    }
    if (buf.isNotEmpty) {
      for (final part in buf.toString().split(RegExp(r'[,;]+'))) {
        if (part.trim().isNotEmpty) tokens.add(part.trim());
      }
    }
    return tokens;
  }

  Future<String?> getDeveloperBootstrapSeedB64() async {
    try {
      final raw = await _channel.invokeMethod<String>('getDeveloperBootstrapSeedB64');
      final trimmed = raw?.trim() ?? '';
      return trimmed.isEmpty ? null : trimmed;
    } on MissingPluginException {
      return null;
    } on PlatformException {
      return null;
    } on FlutterError catch (e) {
      if (!_isUninitializedBindingError(e)) rethrow;
      return null;
    }
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
    } on PlatformException {
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
    } on PlatformException {
      return null;
    } on FlutterError catch (e) {
      if (!_isUninitializedBindingError(e)) rethrow;
      return null;
    }
  }

  Future<String?> decodeQrFromImagePath(String imagePath) async {
    try {
      return await _channel.invokeMethod<String>('decodeQrFromImagePath', {
        'path': imagePath,
      });
    } on MissingPluginException {
      return null;
    } on PlatformException {
      return null;
    } on FlutterError catch (e) {
      if (!_isUninitializedBindingError(e)) rethrow;
      return null;
    }
  }

  Future<List<BondedPrinter>> getBondedPrinters() async {
    final res = await getBondedPrintersWithStatus();
    return res.printers;
  }

  Future<({List<BondedPrinter> printers, String? statusMessage})>
      getBondedPrintersWithStatus() async {
    try {
      final items = await _channel.invokeListMethod<dynamic>('getBondedPrinters');
      if (items == null) return (printers: const <BondedPrinter>[], statusMessage: null);
      final printers = items
          .whereType<Map<dynamic, dynamic>>()
          .map(
            (m) => BondedPrinter(
              name: (m['name'] ?? 'طابعة بلوتوث').toString(),
              address: (m['address'] ?? '').toString(),
              majorDeviceClass: (m['majorDeviceClass'] as num?)?.toInt() ?? 7936,
              deviceClass: (m['deviceClass'] as num?)?.toInt() ?? 0,
              isImagingDevice: m['isImagingDevice'] == true,
              supportsSppUuid: m['supportsSppUuid'] == true,
              isAudioOrWearable: m['isAudioOrWearable'] == true,
            ),
          )
          .where((p) => p.address.isNotEmpty)
          .toList();
      printers.sort((a, b) {
        final scoreA = a.isImagingDevice ? 3 : (a.isLikelyPrinter ? 2 : (a.isBlockedNonPrinter ? 0 : 1));
        final scoreB = b.isImagingDevice ? 3 : (b.isLikelyPrinter ? 2 : (b.isBlockedNonPrinter ? 0 : 1));
        return scoreB.compareTo(scoreA);
      });
      final likelyCount = printers.where((p) => p.isLikelyPrinter).length;
      final statusMsg = printers.isEmpty
          ? 'لا توجد أجهزة بلوتوث مقترنة في إعدادات الجهاز حالياً. اقترن بالطابعة الحرارية من إعدادات Android أولاً.'
          : (likelyCount == 0
              ? 'تم العثور على ${printers.length} جهاز مقترن، لكن لم يُرصد أي جهاز مصنّف كطابعة حرارية (IMAGING/SPP).'
              : 'تم العثور على $likelyCount طابعة مرشحة من أصل ${printers.length} جهاز مقترن.');
      return (
        printers: printers,
        statusMessage: statusMsg,
      );
    } on MissingPluginException {
      return (printers: const <BondedPrinter>[], statusMessage: null);
    } on PlatformException catch (e) {
      return (
        printers: const <BondedPrinter>[],
        statusMessage: e.message ?? 'تعذر الوصول إلى البلوتوث.',
      );
    } on FlutterError catch (e) {
      if (!_isUninitializedBindingError(e)) rethrow;
      return (printers: const <BondedPrinter>[], statusMessage: null);
    }
  }

  Future<bool> printEscPosBytes(String address, Uint8List bytes) async {
    final res = await _channel.invokeMethod<bool>('printEscPosBytes', {
      'address': address,
      'bytes': bytes,
    });
    return res ?? false;
  }

  static String sanitizePickedPhone(String raw) {
    final buf = StringBuffer();
    for (final codeUnit in raw.runes) {
      if (codeUnit >= 0x0660 && codeUnit <= 0x0669) {
        buf.writeCharCode(0x30 + (codeUnit - 0x0660));
      } else if (codeUnit >= 0x06F0 && codeUnit <= 0x06F9) {
        buf.writeCharCode(0x30 + (codeUnit - 0x06F0));
      } else if ((codeUnit >= 0x30 && codeUnit <= 0x39) ||
          (codeUnit == 0x2B && buf.isEmpty)) {
        buf.writeCharCode(codeUnit);
      }
    }
    return buf.toString();
  }

  Future<PickedContact?> pickPhoneContact() async {
    try {
      final map = await _channel.invokeMapMethod<String, dynamic>('pickPhoneContact');
      if (map == null) return null;
      final name = (map['name'] ?? '').toString().trim();
      final phone = sanitizePickedPhone((map['phone'] ?? '').toString());
      if (name.isEmpty && phone.isEmpty) return null;
      return PickedContact(name: name, phone: phone);
    } on MissingPluginException {
      return null;
    } on PlatformException {
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
