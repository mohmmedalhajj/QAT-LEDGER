import 'dart:convert';
import 'dart:typed_data';

import 'package:cryptography/cryptography.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:qat_ledger/licensing/license_canonical.dart';
import 'package:qat_ledger/licensing/license_models.dart';
import 'package:qat_ledger/licensing/license_verifier.dart';
import 'package:qat_ledger/licensing/trusted_key_registry.dart';
import 'package:qat_ledger/licensing/trusted_time_guard.dart';

void main() {
  test('Ed25519 QL1 and .qlic canonical signing, verification, and security guards', () async {
    final ed = Ed25519();
    final seed = List<int>.generate(32, (i) => i + 1);
    final kp = await ed.newKeyPairFromSeed(seed);
    final pub = await kp.extractPublicKey();
    final spkiB64 = TrustedPublicKeyRegistry.encodeSpkiBase64(pub.bytes);
    final keyId = TrustedPublicKeyRegistry.computeKeyIdFromRaw(pub.bytes);
    final registry = TrustedPublicKeyRegistry.fromCsv(spkiB64);

    const payload = LicensePayload(
      format: LicenseCanonical.canonicalPayloadFormat,
      licenseId: 'QAT-2026-ABCD1234',
      product: 'QAT_LEDGER',
      edition: 'CUSTOMER',
      customerName: 'أحمد الصنعاني',
      businessName: 'وكالة الخير',
      planCode: 'PRO_YEARLY',
      issuedAtEpochMs: 1700000000000,
      expiresAtEpochMs: 1731536000000,
      installationId: 'QLD-1111-2222-3333-4444',
      maxDevices: 1,
      features: ['REPORTS', 'BACKUP'],
    );

    final canonicalStr = LicenseCanonical.serializePayloadCanonical(payload);
    // Features must be sorted alphabetically per QAT_LEDGER_CANONICAL_PAYLOAD_V1
    expect(canonicalStr.contains('"features":["BACKUP","REPORTS"]'), true);

    final sig = await ed.sign(Uint8List.fromList(utf8.encode(canonicalStr)), keyPair: kp);
    final compact = LicenseCanonical.encodeCompactToken(payload: payload, signatureBytes: sig.bytes);
    final qlic = LicenseCanonical.encodeQlicJson(
      keyId: keyId,
      payload: payload,
      signatureBytes: sig.bytes,
    );

    final guard = TrustedTimeGuard();
    final verifier = OfflineLicenseVerifier(registry: registry, timeGuard: guard);

    // 1. Valid QL1
    final res1 = await verifier.verify(
      rawLicenseInput: compact,
      currentInstallationId: 'QLD-1111-2222-3333-4444',
      nowEpochMs: 1710000000000,
    );
    expect(res1.isValid, true);
    expect(res1.keyId, keyId);

    // 2. Valid .qlic
    final res2 = await verifier.verify(
      rawLicenseInput: qlic,
      currentInstallationId: 'QLD-1111-2222-3333-4444',
      nowEpochMs: 1710000000000,
    );
    expect(res2.isValid, true);

    // 3. Device mismatch
    final mismatch = await verifier.verify(
      rawLicenseInput: compact,
      currentInstallationId: 'QLD-9999-9999-9999-9999',
      nowEpochMs: 1710000000000,
    );
    expect(mismatch.kind, LicenseStateKind.deviceMismatch);

    // 4. Clock rollback detection (> 6h behind 1710000000000)
    final tamperedClock = await verifier.verify(
      rawLicenseInput: compact,
      currentInstallationId: 'QLD-1111-2222-3333-4444',
      nowEpochMs: 1705000000000,
    );
    expect(tamperedClock.kind, LicenseStateKind.clockTampered);

    // 5. Expired license
    final freshVerifier = OfflineLicenseVerifier(registry: registry, timeGuard: TrustedTimeGuard());
    final expired = await freshVerifier.verify(
      rawLicenseInput: compact,
      currentInstallationId: 'QLD-1111-2222-3333-4444',
      nowEpochMs: 1740000000000,
    );
    expect(expired.kind, LicenseStateKind.expired);

    // 6. Untrusted key
    final emptyVerifier = OfflineLicenseVerifier(
      registry: TrustedPublicKeyRegistry.fromCsv(''),
      timeGuard: TrustedTimeGuard(),
    );
    final untrusted = await emptyVerifier.verify(
      rawLicenseInput: compact,
      currentInstallationId: 'QLD-1111-2222-3333-4444',
      nowEpochMs: 1710000000000,
    );
    expect(untrusted.kind, LicenseStateKind.untrustedKey);
  });
}
