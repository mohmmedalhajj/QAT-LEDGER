import 'dart:convert';
import 'dart:math';

import 'package:cryptography/cryptography.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../core/platform_bridge.dart';
import '../licensing/trusted_key_registry.dart';

class DeveloperKeyInfo {
  const DeveloperKeyInfo({
    required this.keyId,
    required this.spkiBase64,
    required this.spkiPem,
    required this.rawPublicBytes,
    required this.hardwareSealed,
  });

  final String keyId;
  final String spkiBase64;
  final String spkiPem;
  final List<int> rawPublicBytes;
  final bool hardwareSealed;
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

    final rnd = Random.secure();
    final seed = List<int>.generate(32, (_) => rnd.nextInt(256));
    await _persistSeed(seed);
    return _ed25519.newKeyPairFromSeed(seed);
  }

  Future<SimpleKeyPair> rotateSigningKeyPair() async {
    final rnd = Random.secure();
    final seed = List<int>.generate(32, (_) => rnd.nextInt(256));
    await _persistSeed(seed);
    return _ed25519.newKeyPairFromSeed(seed);
  }

  Future<SimpleKeyPair?> loadSigningKeyPair() async {
    final sealed = _prefs.getString(_prefSealedSeed);
    if (sealed != null && sealed.isNotEmpty) {
      final unsealedB64 = await PlatformBridge.instance.keystoreUnseal(sealed);
      if (unsealedB64 != null && unsealedB64.isNotEmpty) {
        final seed = base64.decode(unsealedB64);
        if (seed.length == 32) {
          return _ed25519.newKeyPairFromSeed(seed);
        }
      }
    }
    final fallback = _prefs.getString(_prefFallbackSeed);
    if (fallback != null && fallback.isNotEmpty) {
      final seed = base64.decode(fallback);
      if (seed.length == 32) {
        return _ed25519.newKeyPairFromSeed(seed);
      }
    }
    return null;
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
    final spkiB64 = TrustedPublicKeyRegistry.encodeSpkiBase64(pub.bytes);
    final wrapped = StringBuffer();
    for (int i = 0; i < spkiB64.length; i += 64) {
      final end = (i + 64).clamp(0, spkiB64.length);
      wrapped.writeln(spkiB64.substring(i, end));
    }
    final pem = '-----BEGIN PUBLIC KEY-----\n$wrapped-----END PUBLIC KEY-----';
    return DeveloperKeyInfo(
      keyId: keyId,
      spkiBase64: spkiB64,
      spkiPem: pem,
      rawPublicBytes: pub.bytes,
      hardwareSealed: (_prefs.getString(_prefSealedSeed)?.isNotEmpty ?? false),
    );
  }
}
