import 'dart:convert';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';
import 'package:cryptography/cryptography.dart';

/// Manages trusted Ed25519 SPKI/Raw public keys and deterministic `QLK-<8HEX>` key IDs
/// compatible with `TrustedPublicKeyRegistry.kt`.
class TrustedPublicKeyRegistry {
  TrustedPublicKeyRegistry._(this._keysById);

  final Map<String, SimplePublicKey> _keysById;

  /// ASN.1 DER X.509 SubjectPublicKeyInfo header for Ed25519 (OID 1.3.101.112).
  static const List<int> ed25519SpkiPrefix = [
    0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00,
  ];

  bool get isEmpty => _keysById.isEmpty;
  int get count => _keysById.length;
  Iterable<String> get keyIds => _keysById.keys;
  Iterable<SimplePublicKey> get publicKeys => _keysById.values;

  SimplePublicKey? findById(String keyId) => _keysById[keyId.trim().toUpperCase()];

  static TrustedPublicKeyRegistry fromCsv(String csv) {
    final map = <String, SimplePublicKey>{};
    final entries = csv.split(RegExp(r'[,;\n]+'));
    for (final raw in entries) {
      final clean = raw.trim();
      if (clean.isEmpty) continue;
      try {
        final key = parsePublicKey(clean);
        final id = computeKeyIdFromRaw(key.bytes);
        map[id] = key;
      } catch (_) {
        // Skip malformed entry safely without crashing startup
      }
    }
    return TrustedPublicKeyRegistry._(map);
  }

  static TrustedPublicKeyRegistry fromRawPublicKeys(List<List<int>> rawKeys32) {
    final map = <String, SimplePublicKey>{};
    for (final bytes in rawKeys32) {
      if (bytes.length != 32) continue;
      final id = computeKeyIdFromRaw(bytes);
      map[id] = SimplePublicKey(bytes, type: KeyPairType.ed25519);
    }
    return TrustedPublicKeyRegistry._(map);
  }

  static SimplePublicKey parsePublicKey(String encoded) {
    var s = encoded.trim();
    if (s.contains('BEGIN PUBLIC KEY')) {
      s = s
          .replaceAll('-----BEGIN PUBLIC KEY-----', '')
          .replaceAll('-----END PUBLIC KEY-----', '')
          .replaceAll(RegExp(r'\s+'), '');
    }
    final bytes = base64.decode(s);
    if (bytes.length == 32) {
      return SimplePublicKey(bytes, type: KeyPairType.ed25519);
    }
    if (bytes.length == 44 && _startsWith(bytes, ed25519SpkiPrefix)) {
      return SimplePublicKey(bytes.sublist(12), type: KeyPairType.ed25519);
    }
    throw const FormatException('Unsupported Ed25519 public key length');
  }

  static Uint8List raw32ToSpkiDer44(List<int> raw32) {
    if (raw32.length != 32) {
      throw ArgumentError('Ed25519 raw public key must be 32 bytes');
    }
    return Uint8List.fromList([...ed25519SpkiPrefix, ...raw32]);
  }

  static String encodeSpkiBase64(List<int> raw32) {
    return base64.encode(raw32ToSpkiDer44(raw32));
  }

  static String computeKeyIdFromRaw(List<int> raw32) {
    final spki = raw32ToSpkiDer44(raw32);
    final digest = sha256.convert(spki).bytes;
    final hex8 = digest
        .take(4)
        .map((b) => b.toRadixString(16).padLeft(2, '0'))
        .join()
        .toUpperCase();
    return 'QLK-$hex8';
  }

  static bool _startsWith(List<int> data, List<int> prefix) {
    if (data.length < prefix.length) return false;
    for (int i = 0; i < prefix.length; i++) {
      if (data[i] != prefix[i]) return false;
    }
    return true;
  }
}
