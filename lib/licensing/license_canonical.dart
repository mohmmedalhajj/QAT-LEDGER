import 'dart:convert';
import 'dart:typed_data';

import 'license_models.dart';

/// Byte-for-byte compatible with Kotlin `licensing-core` (`LicenseCanonical.kt`):
/// - `QAT_LEDGER_CANONICAL_PAYLOAD_V1`
/// - `QL1.<base64urlNoPad(canonicalJson)>.<base64urlNoPad(ed25519Sig64)>`
/// - `QAT_LEDGER_LICENSE_V1` (.qlic JSON)
abstract final class LicenseCanonical {
  static const String canonicalPayloadFormat = 'QAT_LEDGER_CANONICAL_PAYLOAD_V1';
  static const String licenseEnvelopeFormat = 'QAT_LEDGER_LICENSE_V1';
  static const String compactPrefix = 'QL1.';
  static const String algorithmEd25519 = 'Ed25519';

  static List<String> normalizeFeatures(Iterable<String> features) {
    final set = <String>{};
    for (final f in features) {
      final clean = f.trim();
      if (clean.isNotEmpty) set.add(clean);
    }
    final list = set.toList()..sort();
    return list;
  }

  /// Exact deterministic compact JSON string signed by Ed25519.
  static String serializePayloadCanonical(LicensePayload p) {
    final normFeatures = normalizeFeatures(p.features);
    final sb = StringBuffer()
      ..write('{')
      ..write('"format":${jsonEncode(canonicalPayloadFormat)},')
      ..write('"licenseId":${jsonEncode(p.licenseId)},')
      ..write('"product":${jsonEncode(p.product)},')
      ..write('"edition":${jsonEncode(p.edition)},')
      ..write('"customerName":${jsonEncode(p.customerName)},')
      ..write('"businessName":${jsonEncode(p.businessName)},')
      ..write('"planCode":${jsonEncode(p.planCode)},')
      ..write('"issuedAtEpochMs":${p.issuedAtEpochMs},')
      ..write('"expiresAtEpochMs":${p.expiresAtEpochMs},')
      ..write('"installationId":${jsonEncode(p.installationId)},')
      ..write('"maxDevices":${p.maxDevices},')
      ..write('"features":[${normFeatures.map(jsonEncode).join(',')}]')
      ..write('}');
    return sb.toString();
  }

  static Uint8List canonicalBytes(LicensePayload p) {
    return Uint8List.fromList(utf8.encode(serializePayloadCanonical(p)));
  }

  static String encodeBase64UrlNoPad(List<int> bytes) {
    return base64Url.encode(bytes).replaceAll('=', '');
  }

  static Uint8List decodeBase64UrlLenient(String raw) {
    var clean = raw.trim().replaceAll(RegExp(r'\s+'), '');
    clean = clean.replaceAll('+', '-').replaceAll('/', '_');
    final rem = clean.length % 4;
    if (rem == 2) {
      clean += '==';
    } else if (rem == 3) {
      clean += '=';
    } else if (rem == 1) {
      throw const FormatException('Invalid base64url length');
    }
    return Uint8List.fromList(base64Url.decode(clean));
  }

  static String encodeCompactToken({
    required LicensePayload payload,
    required List<int> signatureBytes,
  }) {
    final payloadB64 = encodeBase64UrlNoPad(canonicalBytes(payload));
    final sigB64 = encodeBase64UrlNoPad(signatureBytes);
    return '$compactPrefix$payloadB64.$sigB64';
  }

  static String encodeQlicJson({
    required String keyId,
    required LicensePayload payload,
    required List<int> signatureBytes,
  }) {
    final obj = <String, Object?>{
      'format': licenseEnvelopeFormat,
      'keyId': keyId,
      'algorithm': algorithmEd25519,
      'payload': jsonDecode(serializePayloadCanonical(payload)) as Object?,
      'signature': encodeBase64UrlNoPad(signatureBytes),
    };
    return const JsonEncoder.withIndent('  ').convert(obj);
  }

  /// Parses either `QL1.<payload>.<sig>` compact token OR `.qlic` JSON envelope.
  static DecodedLicenseInput parseInput(String rawInput) {
    final trimmed = rawInput.trim();
    if (trimmed.isEmpty) {
      throw const FormatException('نص الترخيص فارغ');
    }
    if (trimmed.startsWith(compactPrefix)) {
      final body = trimmed.substring(compactPrefix.length);
      final parts = body.split('.');
      if (parts.length != 2) {
        throw const FormatException('صيغة رمز QL1 غير صالحة');
      }
      final payloadBytes = decodeBase64UrlLenient(parts[0]);
      final sigBytes = decodeBase64UrlLenient(parts[1]);
      final payloadMap = jsonDecode(utf8.decode(payloadBytes)) as Map<String, dynamic>;
      final payload = _parsePayloadMap(payloadMap);
      return DecodedLicenseInput(
        payload: payload,
        signatureBytes: sigBytes,
        keyIdHint: null,
      );
    }

    if (trimmed.startsWith('{')) {
      final root = jsonDecode(trimmed) as Map<String, dynamic>;
      final format = (root['format'] ?? '').toString();
      if (format != licenseEnvelopeFormat) {
        throw FormatException('صيغة .qlic غير مدعومة: $format');
      }
      final alg = (root['algorithm'] ?? algorithmEd25519).toString();
      if (alg.toUpperCase() != algorithmEd25519.toUpperCase()) {
        throw FormatException('خوارزمية توقيع غير مدعومة: $alg');
      }
      final payloadRaw = root['payload'];
      if (payloadRaw is! Map<String, dynamic>) {
        throw const FormatException('حقل payload مفقود في ملف الترخيص');
      }
      final sigStr = (root['signature'] ?? '').toString();
      if (sigStr.isEmpty) {
        throw const FormatException('حقل signature مفقود');
      }
      return DecodedLicenseInput(
        payload: _parsePayloadMap(payloadRaw),
        signatureBytes: decodeBase64UrlLenient(sigStr),
        keyIdHint: root['keyId']?.toString(),
      );
    }

    throw const FormatException('صيغة ترخيص غير معروفة (يقبل QL1. أو ملف .qlic)');
  }

  static LicensePayload _parsePayloadMap(Map<String, dynamic> map) {
    final fmt = (map['format'] ?? '').toString();
    if (fmt != canonicalPayloadFormat) {
      throw FormatException('نسخة حمولة الترخيص غير مدعومة: $fmt');
    }
    final Object? rawFeatures = map['features'];
    final features = <String>[];
    if (rawFeatures is List<Object?>) {
      for (final f in rawFeatures) {
        features.add(f.toString());
      }
    }
    return LicensePayload(
      format: fmt,
      licenseId: (map['licenseId'] ?? '').toString(),
      product: (map['product'] ?? 'QAT_LEDGER').toString(),
      edition: (map['edition'] ?? 'CUSTOMER').toString(),
      customerName: (map['customerName'] ?? '').toString(),
      businessName: (map['businessName'] ?? '').toString(),
      planCode: (map['planCode'] ?? '').toString(),
      issuedAtEpochMs: (map['issuedAtEpochMs'] as num).toInt(),
      expiresAtEpochMs: (map['expiresAtEpochMs'] as num).toInt(),
      installationId: (map['installationId'] ?? '').toString(),
      maxDevices: ((map['maxDevices'] as num?)?.toInt() ?? 1).clamp(1, 999).toInt(),
      features: normalizeFeatures(features),
    );
  }
}

class DecodedLicenseInput {
  const DecodedLicenseInput({
    required this.payload,
    required this.signatureBytes,
    required this.keyIdHint,
  });

  final LicensePayload payload;
  final Uint8List signatureBytes;
  final String? keyIdHint;
}
