import 'dart:convert';
import 'dart:math';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';

/// Pure-Dart PBKDF2-HMAC-SHA256 password hashing & legacy SHA-256 verification.
///
/// New credentials are always hashed with a 16-byte cryptographic salt and
/// 10,000 PBKDF2-HMAC-SHA256 iterations (`pbkdf2_sha256$<saltHex>$<hashHex>`).
/// Migrated legacy databases using unsalted SHA-256 remain verifiable and are
/// transparently upgraded upon successful login.
abstract final class PasswordSecurity {
  static const String prefix = 'pbkdf2_sha256';
  static const int _iterations = 10000;
  static const int _saltBytes = 16;
  static const int _dkLen = 32;

  static final String _legacyDefaultWeakHash = sha256
      .convert(utf8.encode('QAT_LEDGER_AUTH:${String.fromCharCodes(const [49, 50, 51, 52, 53, 54])}'))
      .toString();

  static String hashPassword(String plain) {
    final rnd = Random.secure();
    final salt = Uint8List(_saltBytes);
    for (int i = 0; i < _saltBytes; i++) {
      salt[i] = rnd.nextInt(256);
    }
    final derived = _pbkdf2Sha256(utf8.encode(plain), salt, _iterations, _dkLen);
    return '$prefix\$${_toHex(salt)}\$${_toHex(derived)}';
  }

  static bool verifyPassword(String plain, String storedHash) {
    final trimmedHash = storedHash.trim();
    if (trimmedHash.isEmpty || plain.isEmpty) return false;

    if (trimmedHash.startsWith('$prefix\$')) {
      final parts = trimmedHash.split('\$');
      if (parts.length != 3) return false;
      final salt = _fromHex(parts[1]);
      final expected = _fromHex(parts[2]);
      if (salt == null || expected == null || expected.isEmpty) return false;
      final actual = _pbkdf2Sha256(utf8.encode(plain), salt, _iterations, expected.length);
      return _constantTimeEquals(actual, expected);
    }

    // Legacy Flutter unsalted SHA-256 (`QAT_LEDGER_AUTH:<pass>`)
    final legacySaltedStatic = sha256.convert(utf8.encode('QAT_LEDGER_AUTH:$plain')).toString();
    if (_constantTimeEqualsAscii(trimmedHash.toLowerCase(), legacySaltedStatic)) {
      return true;
    }

    // Legacy Kotlin plain SHA-256 (`sha256(pass)`)
    final rawSha256 = sha256.convert(utf8.encode(plain)).toString();
    if (_constantTimeEqualsAscii(trimmedHash.toLowerCase(), rawSha256)) {
      return true;
    }

    return false;
  }

  static bool needsRehash(String storedHash) =>
      !storedHash.trim().startsWith('$prefix\$');

  static bool isKnownWeakLegacyHash(String storedHash) =>
      storedHash.trim().toLowerCase() == _legacyDefaultWeakHash;

  static Uint8List _pbkdf2Sha256(
    List<int> password,
    Uint8List salt,
    int iterations,
    int dkLen,
  ) {
    final hmac = Hmac(sha256, password);
    final blockCount = (dkLen + 31) ~/ 32;
    final out = BytesBuilder(copy: false);

    for (int block = 1; block <= blockCount; block++) {
      final blockInput = Uint8List(salt.length + 4)
        ..setRange(0, salt.length, salt)
        ..[salt.length] = (block >> 24) & 0xff
        ..[salt.length + 1] = (block >> 16) & 0xff
        ..[salt.length + 2] = (block >> 8) & 0xff
        ..[salt.length + 3] = block & 0xff;

      var u = Uint8List.fromList(hmac.convert(blockInput).bytes);
      final t = Uint8List.fromList(u);
      for (int i = 1; i < iterations; i++) {
        u = Uint8List.fromList(hmac.convert(u).bytes);
        for (int j = 0; j < t.length; j++) {
          t[j] ^= u[j];
        }
      }
      out.add(t);
    }

    final raw = out.takeBytes();
    return Uint8List.fromList(raw.sublist(0, dkLen));
  }

  static String _toHex(List<int> bytes) =>
      bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();

  static Uint8List? _fromHex(String hex) {
    if (hex.isEmpty || hex.length.isOdd) return null;
    final out = Uint8List(hex.length ~/ 2);
    for (int i = 0; i < out.length; i++) {
      final v = int.tryParse(hex.substring(i * 2, i * 2 + 2), radix: 16);
      if (v == null) return null;
      out[i] = v;
    }
    return out;
  }

  static bool _constantTimeEquals(List<int> a, List<int> b) {
    if (a.length != b.length) return false;
    int diff = 0;
    for (int i = 0; i < a.length; i++) {
      diff |= a[i] ^ b[i];
    }
    return diff == 0;
  }

  static bool _constantTimeEqualsAscii(String a, String b) {
    if (a.length != b.length) return false;
    int diff = 0;
    for (int i = 0; i < a.length; i++) {
      diff |= a.codeUnitAt(i) ^ b.codeUnitAt(i);
    }
    return diff == 0;
  }
}
