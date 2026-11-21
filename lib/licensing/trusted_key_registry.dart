import 'dart:convert';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';
import 'package:cryptography/cryptography.dart';

/// Manages trusted Ed25519 SPKI/Raw public keys and deterministic `QLK-<8HEX>` key IDs
/// compatible with BOTH Kotlin `TrustedPublicKeyRegistry.kt` (`QLK-XXXXXXXX:<base64UrlRaw32>`)
/// and SPKI/PEM/raw Base64 entries.
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

  SimplePublicKey? findById(String keyId) =>
      _keysById[keyId.trim().toUpperCase()];

  static TrustedPublicKeyRegistry fromCsv(String csv) {
    final map = <String, SimplePublicKey>{};
    var remaining = csv.trim();
    if (remaining.isEmpty) return TrustedPublicKeyRegistry._(map);

    // Strip accidental env-var prefixes (`QAT_LICENSE_PUBLIC_KEYS=...`) & surrounding quotes
    remaining = remaining.replaceFirst(
      RegExp(r'^(export\s+)?(QAT_LICENSE_PUBLIC_KEYS|LICENSE_PUBLIC_KEYS)\s*=\s*'),
      '',
    ).trim();
    if ((remaining.startsWith('"') && remaining.endsWith('"')) ||
        (remaining.startsWith("'") && remaining.endsWith("'"))) {
      remaining = remaining.substring(1, remaining.length - 1).trim();
    }

    // Extract multiline PEM blocks BEFORE splitting by commas/newlines
    final pemPattern = RegExp(
      r'-----BEGIN PUBLIC KEY-----[\s\S]*?-----END PUBLIC KEY-----',
    );
    final extractedEntries = <String>[];
    remaining = remaining.replaceAllMapped(pemPattern, (m) {
      extractedEntries.add(m.group(0)!);
      return ',';
    });
    for (final raw in remaining.split(RegExp(r'[,;\s]+'))) {
      var clean = raw.trim();
      if (clean.isEmpty) continue;
      if ((clean.startsWith('"') && clean.endsWith('"')) ||
          (clean.startsWith("'") && clean.endsWith("'"))) {
        clean = clean.substring(1, clean.length - 1).trim();
      }
      if (clean.isNotEmpty) extractedEntries.add(clean);
    }

    for (final clean in extractedEntries) {
      try {
        // Kotlin `QAT_LICENSE_PUBLIC_KEYS` format: `QLK-XXXXXXXX:<base64UrlRaw32>`
        final colonIdx = clean.indexOf(':');
        if (colonIdx > 0 && colonIdx < clean.length - 1 && !clean.contains('BEGIN PUBLIC KEY')) {
          final declaredId = clean.substring(0, colonIdx).trim().toUpperCase();
          final b64Part = clean.substring(colonIdx + 1).trim();
          final key = parsePublicKey(b64Part);
          final kotlinId = computeKotlinKeyIdFromRaw(key.bytes);
          final spkiId = computeKeyIdFromRaw(key.bytes);
          if (declaredId == kotlinId || declaredId == spkiId) {
            map[declaredId] = key;
          }
          map[kotlinId] = key;
          map[spkiId] = key;
          continue;
        }
        final key = parsePublicKey(clean);
        // Index under BOTH SPKI KeyId and Kotlin raw32 KeyId for full cross-compatibility,
        // keeping the entry's native format ID primary in iteration order.
        final spkiId = computeKeyIdFromRaw(key.bytes);
        final kotlinId = computeKotlinKeyIdFromRaw(key.bytes);
        if (_isSpkiEncoded(clean)) {
          map[spkiId] = key;
          map[kotlinId] = key;
        } else {
          map[kotlinId] = key;
          map[spkiId] = key;
        }
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
      final k = SimplePublicKey(bytes, type: KeyPairType.ed25519);
      map[computeKotlinKeyIdFromRaw(bytes)] = k;
      map[computeKeyIdFromRaw(bytes)] = k;
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
    // Accept both standard Base64 and RFC 4648 Base64Url (with or without padding)
    var norm = s.replaceAll(RegExp(r'\s+'), '').replaceAll('+', '-').replaceAll('/', '_');
    final rem = norm.length % 4;
    if (rem == 2) {
      norm += '==';
    } else if (rem == 3) {
      norm += '=';
    } else if (rem == 1) {
      throw const FormatException('Invalid base64 public key length');
    }
    final bytes = base64Url.decode(norm);
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

  /// Kotlin `TrustedPublicKeyRegistry.formatBuildConfigEntry` (`QLK-<8HEX>:<base64UrlRaw32>`).
  static String formatKotlinBuildConfigEntry(List<int> raw32) {
    final keyId = computeKotlinKeyIdFromRaw(raw32);
    final rawB64Url = base64Url.encode(raw32).replaceAll('=', '');
    return '$keyId:$rawB64Url';
  }

  /// Kotlin `TrustedPublicKeyRegistry.deriveKeyId(raw32)` (`SHA-256(raw32)`).
  static String computeKotlinKeyIdFromRaw(List<int> raw32) {
    if (raw32.length != 32) {
      throw ArgumentError('Ed25519 raw public key must be 32 bytes');
    }
    final digest = sha256.convert(raw32).bytes;
    final hex8 = digest
        .take(4)
        .map((b) => b.toRadixString(16).padLeft(2, '0'))
        .join()
        .toUpperCase();
    return 'QLK-$hex8';
  }

  /// Legacy Flutter SPKI-hashed KeyId (`SHA-256(spki44)`).
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

  static bool _isSpkiEncoded(String rawEntry) {
    if (rawEntry.contains('BEGIN PUBLIC KEY')) return true;
    try {
      var norm = rawEntry
          .replaceAll(RegExp(r'\s+'), '')
          .replaceAll('+', '-')
          .replaceAll('/', '_');
      final rem = norm.length % 4;
      if (rem == 2) {
        norm += '==';
      } else if (rem == 3) {
        norm += '=';
      } else if (rem == 1) {
        return false;
      }
      final bytes = base64Url.decode(norm);
      return bytes.length == 44 && _startsWith(bytes, ed25519SpkiPrefix);
    } catch (_) {
      return false;
    }
  }

  static bool _startsWith(List<int> data, List<int> prefix) {
    if (data.length < prefix.length) return false;
    for (int i = 0; i < prefix.length; i++) {
      if (data[i] != prefix[i]) return false;
    }
    return true;
  }
}
