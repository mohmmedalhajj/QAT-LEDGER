import 'dart:math';

import 'package:cryptography/cryptography.dart';

import '../licensing/license_canonical.dart';
import '../licensing/license_models.dart';
import '../licensing/trusted_key_registry.dart';
import 'developer_database.dart';
import 'developer_key_manager.dart';

class SignedLicenseArtifact {
  const SignedLicenseArtifact({
    required this.payload,
    required this.keyId,
    required this.compactToken,
    required this.qlicJson,
  });

  final LicensePayload payload;
  final String keyId;
  final String compactToken;
  final String qlicJson;
}

class DeveloperLicenseSigner {
  DeveloperLicenseSigner({
    required this.keyManager,
    required this.devDb,
  });

  final DeveloperKeyManager keyManager;
  final DeveloperDatabaseService devDb;
  final Ed25519 _ed25519 = Ed25519();

  Future<SignedLicenseArtifact> issueLicense({
    required String customerName,
    required String businessName,
    required String planCode,
    required String installationId,
    required int durationDays,
    int maxDevices = 1,
    List<String> features = const ['OFFLINE_PRO', 'BACKUP', 'REPORTS'],
    String notes = '',
  }) async {
    final kp = await keyManager.getOrGenerateSigningKeyPair();
    final pub = await kp.extractPublicKey();
    final keyId = TrustedPublicKeyRegistry.computeKeyIdFromRaw(pub.bytes);

    final nowMs = DateTime.now().millisecondsSinceEpoch;
    final expiresMs = durationDays <= 0
        ? 0
        : nowMs + Duration(days: durationDays).inMilliseconds;

    final rndHex = List<int>.generate(4, (_) => Random.secure().nextInt(256))
        .map((b) => b.toRadixString(16).padLeft(2, '0'))
        .join()
        .toUpperCase();
    final licenseId = 'QAT-${DateTime.now().year}-$rndHex';

    final payload = LicensePayload(
      format: LicenseCanonical.canonicalPayloadFormat,
      licenseId: licenseId,
      product: 'QAT_LEDGER',
      edition: 'CUSTOMER',
      customerName: customerName.trim(),
      businessName: businessName.trim(),
      planCode: planCode.trim(),
      issuedAtEpochMs: nowMs,
      expiresAtEpochMs: expiresMs,
      installationId: installationId.trim().isEmpty ? '*' : installationId.trim().toUpperCase(),
      maxDevices: maxDevices.clamp(1, 999).toInt(),
      features: LicenseCanonical.normalizeFeatures(features),
    );

    final canonical = LicenseCanonical.canonicalBytes(payload);
    final signature = await _ed25519.sign(canonical, keyPair: kp);
    final compact = LicenseCanonical.encodeCompactToken(
      payload: payload,
      signatureBytes: signature.bytes,
    );
    final qlic = LicenseCanonical.encodeQlicJson(
      keyId: keyId,
      payload: payload,
      signatureBytes: signature.bytes,
    );

    final db = await devDb.database;
    await db.transaction<void>((txn) async {
      await txn.insert('issued_licenses', {
        'license_id': licenseId,
        'plan_code': payload.planCode,
        'customer_id': null,
        'installation_id': payload.installationId,
        'licensee_name': payload.customerName,
        'business_name': payload.businessName,
        'issued_at_ms': nowMs,
        'expires_at_ms': expiresMs,
        'max_devices': payload.maxDevices,
        'features_csv': payload.features.join(','),
        'token_compact': compact,
        'qlic_json': qlic,
        'key_id': keyId,
        'status': 'ACTIVE',
        'notes': notes.trim(),
      });
      await txn.insert('developer_audit_logs', {
        'action': 'ISSUE_LICENSE',
        'actor': 'developer',
        'target_id': licenseId,
        'details': 'plan=$planCode install=${payload.installationId} key=$keyId',
        'created_at_ms': nowMs,
      });
    });

    return SignedLicenseArtifact(
      payload: payload,
      keyId: keyId,
      compactToken: compact,
      qlicJson: qlic,
    );
  }
}
