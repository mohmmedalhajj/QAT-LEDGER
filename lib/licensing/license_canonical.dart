import 'dart:convert';
import 'dart:typed_data';

import 'license_models.dart';

/// Dual-format canonical codec compatible byte-for-byte with:
/// 1. Official Kotlin `com.qatledger.licensing.LicenseCanonicalCodec` (`packageId`/`licenseType`/`features` + line-based `QAT_LEDGER_CANONICAL_PAYLOAD_V1`).
/// 2. Legacy Flutter v2 JSON payload (`planCode`/`businessName` + compact JSON `QAT_LEDGER_CANONICAL_PAYLOAD_V1`).
abstract final class LicenseCanonical {
  static const String canonicalPayloadFormat = 'QAT_LEDGER_CANONICAL_PAYLOAD_V1';
  static const String licenseEnvelopeFormat = 'QAT_LEDGER_LICENSE_V1';
  static const String revocationFormat = 'QAT_LEDGER_REVOCATION_V1';
  static const String compactPrefix = 'QL1.';
  static const String revocationPrefix = 'QLR1.';
  static const String algorithmEd25519 = 'Ed25519';
  static final RegExp _ql1Regex = RegExp(
    'QL1\\.[A-Za-z0-9_+/=-]+(?:\\r?\\n[A-Za-z0-9_+/=-]+)*\\.[A-Za-z0-9_+/=-]+(?:\\r?\\n[A-Za-z0-9_+/=-]+)*',
  );
  static final RegExp _qlr1Regex = RegExp(
    'QLR1\\.[A-Za-z0-9_+/=-]+(?:\\r?\\n[A-Za-z0-9_+/=-]+)*\\.[A-Za-z0-9_+/=-]+(?:\\r?\\n[A-Za-z0-9_+/=-]+)*',
  );

  static List<String> normalizeFeatures(Iterable<String> features) {
    final set = <String>{};
    for (final f in features) {
      final clean = f.trim();
      if (clean.isNotEmpty) set.add(clean);
    }
    return set.toList()..sort();
  }

  /// Official Kotlin `LicenseCanonicalCodec.canonicalSigningBytes` format.
  static Uint8List kotlinCanonicalSigningBytes(LicensePayload p) {
    final sortedFeatureCodes = normalizeFeatures(
      p.features.map((e) => e.trim().toUpperCase()),
    ).join(',');
    final normInstallId = p.installationId.trim() == '*' || p.installationId.trim().toUpperCase() == 'ANY'
        ? ''
        : p.installationId.trim().toUpperCase();
    final normExpires = p.expiresAtEpochMs <= 0 ? 'NONE' : p.expiresAtEpochMs.toString();

    String esc(String raw) => encodeBase64UrlNoPad(utf8.encode(raw.trim()));

    final lines = [
      canonicalPayloadFormat,
      'licenseId=${esc(p.licenseId)}',
      'version=${p.version}',
      'packageId=${p.effectiveTier.code}',
      'licenseType=${p.effectiveLicenseType.code}',
      'features=$sortedFeatureCodes',
      'agencyName=${esc(p.businessName)}',
      'ownerName=${esc(p.customerName)}',
      'phone=${esc(p.phone)}',
      'installationId=${esc(normInstallId)}',
      'issuedAtEpochMs=${p.issuedAtEpochMs}',
      'expiresAtEpochMs=$normExpires',
      'keyId=${esc(p.keyIdField)}',
      'maxClockSkewSeconds=${p.maxClockSkewSeconds}',
      'notes=${esc(p.notes)}',
    ];
    return Uint8List.fromList(utf8.encode(lines.join('\n')));
  }

  /// Exact deterministic compact JSON string signed in Flutter v2 JSON flavor.
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

  static Map<String, Object?> payloadToKotlinJsonMap(LicensePayload p) {
    final sortedFeatures = normalizeFeatures(
      p.features.map((e) => e.trim().toUpperCase()),
    );
    final normInstall = p.installationId.trim();
    final hasInstall = normInstall.isNotEmpty && normInstall != '*' && normInstall.toUpperCase() != 'ANY';
    return <String, Object?>{
      'licenseId': p.licenseId.trim(),
      'version': p.version,
      'packageId': p.effectiveTier.code,
      'licenseType': p.effectiveLicenseType.code,
      'features': sortedFeatures,
      'agencyName': p.businessName.trim(),
      'ownerName': p.customerName.trim(),
      'phone': p.phone.trim(),
      'installationId': hasInstall ? normInstall.toUpperCase() : null,
      'issuedAtEpochMs': p.issuedAtEpochMs,
      'expiresAtEpochMs': p.expiresAtEpochMs <= 0 ? null : p.expiresAtEpochMs,
      'keyId': p.keyIdField.trim(),
      'maxClockSkewSeconds': p.maxClockSkewSeconds,
      'notes': p.notes.trim(),
    };
  }

  static Uint8List canonicalBytes(LicensePayload p) {
    if (p.canonicalFlavor == LicenseCanonicalFlavor.kotlinLineV1) {
      return kotlinCanonicalSigningBytes(p);
    }
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
    final payloadBytes = payload.canonicalFlavor == LicenseCanonicalFlavor.kotlinLineV1
        ? utf8.encode(jsonEncode(payloadToKotlinJsonMap(payload)))
        : canonicalBytes(payload);
    final payloadB64 = encodeBase64UrlNoPad(payloadBytes);
    final sigB64 = encodeBase64UrlNoPad(signatureBytes);
    return '$compactPrefix$payloadB64.$sigB64';
  }

  static String encodeQlicJson({
    required String keyId,
    required LicensePayload payload,
    required List<int> signatureBytes,
  }) {
    final Object? decodedPayload = payload.canonicalFlavor == LicenseCanonicalFlavor.kotlinLineV1
        ? payloadToKotlinJsonMap(payload)
        : jsonDecode(serializePayloadCanonical(payload));
    final obj = <String, Object?>{
      'format': licenseEnvelopeFormat,
      if (payload.canonicalFlavor != LicenseCanonicalFlavor.kotlinLineV1) 'keyId': keyId,
      'algorithm': algorithmEd25519,
      'payload': decodedPayload,
      'signature': encodeBase64UrlNoPad(signatureBytes),
    };
    return const JsonEncoder.withIndent('  ').convert(obj);
  }

  /// Parses either `QL1.<payload>.<sig>` compact token (even wrapped inside WhatsApp/SMS prose
  /// or split across lines) OR `.qlic` JSON envelope, supporting BOTH Kotlin `licensing-core`
  /// payloads and Flutter v2 payloads.
  static DecodedLicenseInput parseInput(String rawInput) {
    final trimmed = rawInput.trim();
    if (trimmed.isEmpty) {
      throw const FormatException('نص الترخيص فارغ');
    }

    String? jsonCandidate;
    if (trimmed.startsWith('{')) {
      jsonCandidate = trimmed;
    } else if (trimmed.contains('"$licenseEnvelopeFormat"') ||
        (trimmed.contains('{') && trimmed.contains('"signature"'))) {
      final firstBrace = trimmed.indexOf('{');
      final lastBrace = trimmed.lastIndexOf('}');
      if (firstBrace >= 0 && lastBrace > firstBrace) {
        jsonCandidate = trimmed.substring(firstBrace, lastBrace + 1);
      }
    }

    if (jsonCandidate != null) {
      final root = jsonDecode(jsonCandidate) as Map<String, dynamic>;
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
      final parsedPayload = _parsePayloadMap(payloadRaw);
      final rootKeyHint = (root['keyId'] ?? '').toString().trim();
      final effectiveHint = rootKeyHint.isNotEmpty
          ? rootKeyHint
          : parsedPayload.keyIdField.trim();
      return DecodedLicenseInput(
        payload: parsedPayload,
        signatureBytes: decodeBase64UrlLenient(sigStr),
        keyIdHint: effectiveHint.isEmpty ? null : effectiveHint,
        hadExplicitKeyId: effectiveHint.isNotEmpty,
        isQlicJson: true,
      );
    }

    // Extract QL1.<payload>.<sig> even when wrapped inside WhatsApp/SMS prose or line-wrapped.
    final ql1Match = _ql1Regex.firstMatch(trimmed);
    final normalized = (ql1Match?.group(0) ?? trimmed).replaceAll(RegExp(r'\s+'), '');
    if (normalized.startsWith(compactPrefix)) {
      final body = normalized.substring(compactPrefix.length);
      final parts = body.split('.');
      if (parts.length != 2) {
        throw const FormatException('صيغة رمز QL1 غير صالحة');
      }
      final payloadBytes = decodeBase64UrlLenient(parts[0]);
      final sigBytes = decodeBase64UrlLenient(parts[1]);
      final payloadMap = jsonDecode(utf8.decode(payloadBytes)) as Map<String, dynamic>;
      final payload = _parsePayloadMap(payloadMap);
      final hint = payload.keyIdField.trim();
      return DecodedLicenseInput(
        payload: payload,
        signatureBytes: sigBytes,
        keyIdHint: hint.isEmpty ? null : hint,
        hadExplicitKeyId: hint.isNotEmpty,
        isQlicJson: false,
      );
    }

    throw const FormatException('صيغة ترخيص غير معروفة (يقبل QL1. أو ملف .qlic)');
  }

  static bool isRevocationInput(String rawInput) {
    final trimmed = rawInput.trim();
    if (trimmed.isEmpty) return false;
    return _qlr1Regex.hasMatch(trimmed) ||
        trimmed.contains('"$revocationFormat"');
  }

  static Uint8List canonicalRevocationBytes(RevocationPayload p) {
    String esc(String raw) => encodeBase64UrlNoPad(utf8.encode(raw.trim()));
    final lines = [
      revocationFormat,
      'licenseId=${esc(p.licenseId.toUpperCase())}',
      'installationId=${esc(p.installationId.toUpperCase())}',
      'keyId=${esc(p.keyId)}',
      'revokedAtEpochMs=${p.revokedAtEpochMs}',
      'reason=${esc(p.reason)}',
    ];
    return Uint8List.fromList(utf8.encode(lines.join('\n')));
  }

  static String encodeRevocationToken({
    required RevocationPayload payload,
    required List<int> signatureBytes,
  }) {
    final map = <String, Object?>{
      'format': revocationFormat,
      'licenseId': payload.licenseId.trim().toUpperCase(),
      'installationId': payload.installationId.trim().toUpperCase(),
      'keyId': payload.keyId.trim(),
      'revokedAtEpochMs': payload.revokedAtEpochMs,
      'reason': payload.reason.trim(),
    };
    final payloadB64 = encodeBase64UrlNoPad(utf8.encode(jsonEncode(map)));
    final sigB64 = encodeBase64UrlNoPad(signatureBytes);
    return '$revocationPrefix$payloadB64.$sigB64';
  }

  static DecodedRevocationInput parseRevocationInput(String rawInput) {
    final trimmed = rawInput.trim();
    final match = _qlr1Regex.firstMatch(trimmed);
    final normalized = (match?.group(0) ?? trimmed).replaceAll(RegExp(r'\s+'), '');
    if (!normalized.startsWith(revocationPrefix)) {
      throw const FormatException('صيغة إشعار الإلغاء غير صالحة');
    }
    final body = normalized.substring(revocationPrefix.length);
    final parts = body.split('.');
    if (parts.length != 2) {
      throw const FormatException('صيغة إشعار QLR1 غير مكتملة');
    }
    final payloadMap =
        jsonDecode(utf8.decode(decodeBase64UrlLenient(parts[0]))) as Map<String, dynamic>;
    if (payloadMap['format'] != revocationFormat) {
      throw FormatException('صيغة إشعار إلغاء غير مدعومة: ${payloadMap['format']}');
    }
    final licenseId = (payloadMap['licenseId'] ?? '').toString().trim().toUpperCase();
    if (licenseId.isEmpty) {
      throw const FormatException('معرّف الترخيص مفقود في إشعار الإلغاء');
    }
    final payload = RevocationPayload(
      licenseId: licenseId,
      installationId: (payloadMap['installationId'] ?? '*').toString().trim().toUpperCase(),
      keyId: (payloadMap['keyId'] ?? '').toString().trim(),
      revokedAtEpochMs: (payloadMap['revokedAtEpochMs'] as num).toInt(),
      reason: (payloadMap['reason'] ?? '').toString().trim(),
    );
    return DecodedRevocationInput(
      payload: payload,
      signatureBytes: decodeBase64UrlLenient(parts[1]),
    );
  }

  static LicensePayload _parsePayloadMap(Map<String, dynamic> map) {
    // 1. Detect Kotlin `licensing-core` payload (`packageId` & `licenseType`)
    if (map.containsKey('packageId') && map.containsKey('licenseType')) {
      final licenseId = (map['licenseId'] ?? '').toString().trim();
      final version = (map['version'] as num?)?.toInt() ?? 1;
      final pkg = PackageTier.fromRaw(map['packageId']?.toString());
      final lType = LicenseType.fromRaw(map['licenseType']?.toString());
      final features = <String>[];
      final rawFeatures = map['features'];
      if (rawFeatures is List<Object?>) {
        for (final f in rawFeatures) {
          features.add(f.toString());
        }
      }
      if (licenseId.isEmpty || version <= 0 || features.isEmpty) {
        throw const FormatException('بيانات حمولة الترخيص غير مكتملة');
      }
      final keyId = (map['keyId'] ?? '').toString().trim();
      final installRaw = map['installationId']?.toString().trim().toUpperCase() ?? '';
      final expiresRaw = map['expiresAtEpochMs'];
      final expiresMs = expiresRaw == null ? 0 : (expiresRaw as num).toInt();

      return LicensePayload(
        format: canonicalPayloadFormat,
        licenseId: licenseId,
        product: 'QAT_LEDGER',
        edition: 'CUSTOMER',
        customerName: (map['ownerName'] ?? '').toString().trim(),
        businessName: (map['agencyName'] ?? '').toString().trim(),
        planCode: '${pkg.code}_${lType.code}',
        issuedAtEpochMs: (map['issuedAtEpochMs'] as num).toInt(),
        expiresAtEpochMs: expiresMs,
        installationId: installRaw.isEmpty ? '*' : installRaw,
        maxDevices: 1,
        features: normalizeFeatures(features),
        canonicalFlavor: LicenseCanonicalFlavor.kotlinLineV1,
        version: version,
        packageTier: pkg,
        licenseType: lType,
        phone: (map['phone'] ?? '').toString().trim(),
        keyIdField: keyId,
        maxClockSkewSeconds: ((map['maxClockSkewSeconds'] as num?)?.toInt() ?? 300),
        notes: (map['notes'] ?? '').toString().trim(),
      );
    }

    // 2. Flutter JSON v2 payload
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
      canonicalFlavor: LicenseCanonicalFlavor.flutterJsonV1,
    );
  }
}

class DecodedLicenseInput {
  const DecodedLicenseInput({
    required this.payload,
    required this.signatureBytes,
    required this.keyIdHint,
    this.hadExplicitKeyId = true,
    this.isQlicJson = false,
  });

  final LicensePayload payload;
  final Uint8List signatureBytes;
  final String? keyIdHint;
  final bool hadExplicitKeyId;
  final bool isQlicJson;
}

class RevocationPayload {
  const RevocationPayload({
    required this.licenseId,
    required this.installationId,
    required this.keyId,
    required this.revokedAtEpochMs,
    this.reason = '',
  });

  final String licenseId;
  final String installationId;
  final String keyId;
  final int revokedAtEpochMs;
  final String reason;

  bool matchesInstallation(String currentId) {
    final target = installationId.trim().toUpperCase();
    return target.isEmpty ||
        target == '*' ||
        target == 'ANY' ||
        target == currentId.trim().toUpperCase();
  }
}

class DecodedRevocationInput {
  const DecodedRevocationInput({
    required this.payload,
    required this.signatureBytes,
  });

  final RevocationPayload payload;
  final Uint8List signatureBytes;
}
