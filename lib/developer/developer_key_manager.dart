import 'dart:convert';
import 'dart:math';

import 'package:cryptography/cryptography.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../core/platform_bridge.dart';
import '../licensing/trusted_key_registry.dart';

class DeveloperKeyInfo {
  const DeveloperKeyInfo({
    required this.keyId,
    required this.kotlinKeyId,
    required this.kotlinConfigLine,
    required this.spkiBase64,
    required this.spkiPem,
    required this.rawPublicBytes,
    required this.hardwareSealed,
    this.hasBootstrapSeed = false,
    this.matchesBootstrapSeed = false,
  });

  final String keyId;
  final String kotlinKeyId;
  final String kotlinConfigLine;
  final String spkiBase64;
  final String spkiPem;
  final List<int> rawPublicBytes;
  final bool hardwareSealed;
  final bool hasBootstrapSeed;
  final bool matchesBootstrapSeed;
}

/// Manages Ed25519 signing keys for Developer Edition builds, sealing private seeds
/// with Android Keystore AES-GCM when running on Android.
class DeveloperKeyManager {
  DeveloperKeyManager(this._prefs);

  static const String _prefSealedSeed = 'qat_dev_ed25519_sealed_seed_v2';
  static const String _prefFallbackSeed = 'qat_dev_ed25519_fallback_seed_v2';

  final SharedPreferences _prefs;
  final Ed25519 _ed25519 = Ed25519();

  Future<bool> hasSigningKey() async {
    return (_prefs.getString(_prefSealedSeed)?.isNotEmpty ?? false) ||
        (_prefs.getString(_prefFallbackSeed)?.isNotEmpty ?? false);
  }

  Future<SimpleKeyPair> getOrGenerateSigningKeyPair() async {
    final existing = await loadSigningKeyPair();
    if (existing != null) return existing;

    final bootstrapRaw = await PlatformBridge.instance.getDeveloperBootstrapSeedB64();
    if (bootstrapRaw != null && bootstrapRaw.trim().isNotEmpty) {
      try {
        final seed = decodeSeed(bootstrapRaw);
        await _persistSeed(seed);
        return _ed25519.newKeyPairFromSeed(seed);
      } catch (_) {
        // Fall back to fresh random seed only if bundled bootstrap seed is malformed.
      }
    }

    final rnd = Random.secure();
    final seed = List<int>.generate(32, (_) => rnd.nextInt(256));
    await _persistSeed(seed);
    return _ed25519.newKeyPairFromSeed(seed);
  }

  Future<bool> hasBootstrapSeed() async {
    final raw = await PlatformBridge.instance.getDeveloperBootstrapSeedB64();
    if (raw == null || raw.trim().isEmpty) return false;
    try {
      decodeSeed(raw);
      return true;
    } catch (_) {
      return false;
    }
  }

  Future<bool> restoreBootstrapSeedIfAvailable() async {
    final raw = await PlatformBridge.instance.getDeveloperBootstrapSeedB64();
    if (raw == null || raw.trim().isEmpty) return false;
    final seed = decodeSeed(raw);
    await _persistSeed(seed);
    return true;
  }

  static List<int> decodeSeed(String rawSeedInput) {
    final clean = rawSeedInput.trim().replaceAll(RegExp(r'\s+'), '');
    if (clean.isEmpty) {
      throw const FormatException('البذرة فارغة');
    }
    List<int> seed;
    if (RegExp(r'^[0-9a-fA-F]{64}$').hasMatch(clean)) {
      seed = [
        for (int i = 0; i < 64; i += 2)
          int.parse(clean.substring(i, i + 2), radix: 16),
      ];
    } else {
      var norm = clean.replaceAll('+', '-').replaceAll('/', '_');
      final rem = norm.length % 4;
      if (rem == 2) {
        norm += '==';
      } else if (rem == 3) {
        norm += '=';
      } else if (rem == 1) {
        throw const FormatException('صيغة Base64 غير صالحة لبذرة Ed25519');
      }
      seed = base64Url.decode(norm);
    }
    if (seed.length != 32) {
      throw FormatException(
        'طول بذرة Ed25519 يجب أن يكون 32 بايت (تم استلام ${seed.length})',
      );
    }
    return seed;
  }

  Future<SimpleKeyPair> rotateSigningKeyPair() async {
    final rnd = Random.secure();
    final seed = List<int>.generate(32, (_) => rnd.nextInt(256));
    await _persistSeed(seed);
    return _ed25519.newKeyPairFromSeed(seed);
  }

  /// Exports the 32-byte Ed25519 private seed as Base64 for secure offline backup by the developer.
  Future<String?> exportSigningSeedB64() async {
    final seed = await _loadSeedBytes();
    if (seed == null || seed.length != 32) return null;
    return base64.encode(seed);
  }

  /// Imports a 32-byte Ed25519 seed (Base64, Base64Url, or 64-char Hex) so the developer
  /// can restore or synchronize their signing identity across builds/devices.
  Future<DeveloperKeyInfo> importSigningSeedB64(String rawSeedInput) async {
    final seed = decodeSeed(rawSeedInput);
    await _persistSeed(seed);
    final info = await getPublicKeyInfo();
    if (info == null) {
      throw StateError('تعذر استخراج معلومات المفتاح العام بعد الاستيراد');
    }
    return info;
  }

  Future<List<int>?> _loadSeedBytes() async {
    final sealed = _prefs.getString(_prefSealedSeed);
    if (sealed != null && sealed.isNotEmpty) {
      final unsealedB64 = await PlatformBridge.instance.keystoreUnseal(sealed);
      if (unsealedB64 != null && unsealedB64.isNotEmpty) {
        final seed = base64.decode(unsealedB64);
        if (seed.length == 32) return seed;
      }
    }
    final fallback = _prefs.getString(_prefFallbackSeed);
    if (fallback != null && fallback.isNotEmpty) {
      final seed = base64.decode(fallback);
      if (seed.length == 32) return seed;
    }
    return null;
  }

  Future<SimpleKeyPair?> loadSigningKeyPair() async {
    final seed = await _loadSeedBytes();
    if (seed == null) return null;
    return _ed25519.newKeyPairFromSeed(seed);
  }

  Future<void> _persistSeed(List<int> seed32) async {
    final seedB64 = base64.encode(seed32);
    final sealed = await PlatformBridge.instance.keystoreSeal(seedB64);
    if (sealed != null && sealed.isNotEmpty) {
      await _prefs.setString(_prefSealedSeed, sealed);
      await _prefs.remove(_prefFallbackSeed);
    } else {
      await _prefs.setString(_prefFallbackSeed, seedB64);
    }
  }

  Future<DeveloperKeyInfo?> getPublicKeyInfo() async {
    final kp = await loadSigningKeyPair();
    if (kp == null) return null;
    final pub = await kp.extractPublicKey();
    final keyId = TrustedPublicKeyRegistry.computeKeyIdFromRaw(pub.bytes);
    final kotlinKeyId = TrustedPublicKeyRegistry.computeKotlinKeyIdFromRaw(pub.bytes);
    final kotlinLine = TrustedPublicKeyRegistry.formatKotlinBuildConfigEntry(pub.bytes);
    final spkiB64 = TrustedPublicKeyRegistry.encodeSpkiBase64(pub.bytes);
    final wrapped = StringBuffer();
    for (int i = 0; i < spkiB64.length; i += 64) {
      final end = (i + 64).clamp(0, spkiB64.length).toInt();
      wrapped.writeln(spkiB64.substring(i, end));
    }
    final pem = '-----BEGIN PUBLIC KEY-----\n$wrapped-----END PUBLIC KEY-----';
    bool hasBootstrap = false;
    bool matchesBootstrap = false;
    final bootstrapRaw = await PlatformBridge.instance.getDeveloperBootstrapSeedB64();
    if (bootstrapRaw != null && bootstrapRaw.trim().isNotEmpty) {
      try {
        final bSeed = decodeSeed(bootstrapRaw);
        hasBootstrap = true;
        final bKp = await _ed25519.newKeyPairFromSeed(bSeed);
        final bPub = await bKp.extractPublicKey();
        matchesBootstrap = bPub.bytes.length == pub.bytes.length &&
            List<bool>.generate(
              pub.bytes.length,
              (i) => pub.bytes[i] == bPub.bytes[i],
            ).every((e) => e);
      } catch (_) {
        hasBootstrap = false;
      }
    }
    return DeveloperKeyInfo(
      keyId: keyId,
      kotlinKeyId: kotlinKeyId,
      kotlinConfigLine: kotlinLine,
      spkiBase64: spkiB64,
      spkiPem: pem,
      rawPublicBytes: pub.bytes,
      hardwareSealed: (_prefs.getString(_prefSealedSeed)?.isNotEmpty ?? false),
      hasBootstrapSeed: hasBootstrap,
      matchesBootstrapSeed: matchesBootstrap,
    );
  }
}
