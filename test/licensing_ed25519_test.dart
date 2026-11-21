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

  test('Kotlin line-canonical QL1 format + QLK raw32 key + BASIC/PRO tier gating + PERPETUAL bypass', () async {
    final ed = Ed25519();
    final kp = await ed.newKeyPairFromSeed(List<int>.generate(32, (i) => i + 7));
    final pub = await kp.extractPublicKey();
    final kotlinKeyId = TrustedPublicKeyRegistry.computeKotlinKeyIdFromRaw(pub.bytes);
    final kotlinCsv = TrustedPublicKeyRegistry.formatKotlinBuildConfigEntry(pub.bytes);
    final registry = TrustedPublicKeyRegistry.fromCsv(kotlinCsv);

    // 1. BASIC subscription license -> REPORTS & ARCHIVE locked
    final basicPayload = LicensePayload(
      format: LicenseCanonical.canonicalPayloadFormat,
      canonicalFlavor: LicenseCanonicalFlavor.kotlinLineV1,
      version: 1,
      licenseId: 'QLB-2026K1',
      product: 'QAT_LEDGER',
      edition: 'CUSTOMER',
      installationId: 'QAT-AAAA-BBBB-CCCC',
      customerName: 'مكتب النجاح',
      businessName: 'وكالة النجاح',
      packageTier: PackageTier.basic,
      licenseType: LicenseType.subscription,
      planCode: 'BASIC_SUBSCRIPTION',
      features: LicenseFeature.basicFeatures.map((f) => f.code).toList(),
      issuedAtEpochMs: 1700000000000,
      expiresAtEpochMs: 1731536000000,
      maxDevices: 1,
      maxClockSkewSeconds: 300,
      keyIdField: kotlinKeyId,
    );
    final basicBytes = LicenseCanonical.kotlinCanonicalSigningBytes(basicPayload);
    final basicSig = await ed.sign(basicBytes, keyPair: kp);
    final basicToken = LicenseCanonical.encodeCompactToken(
      payload: basicPayload,
      signatureBytes: basicSig.bytes,
    );

    final verifier = OfflineLicenseVerifier(registry: registry, timeGuard: TrustedTimeGuard());
    final basicRes = await verifier.verify(
      rawLicenseInput: basicToken,
      currentInstallationId: 'QAT-AAAA-BBBB-CCCC',
      nowEpochMs: 1710000000000,
    );
    expect(basicRes.isValid, true);
    expect(basicRes.isProActive, false);
    expect(basicRes.hasFeature(LicenseFeature.sales), true);
    expect(basicRes.hasFeature(LicenseFeature.reports), false);
    expect(basicRes.hasFeature(LicenseFeature.archive), false);

    // 2. PRO PERPETUAL license (expiresAtEpochMs: 0) bypasses expiry regardless of clock date
    final proPerp = LicensePayload(
      format: LicenseCanonical.canonicalPayloadFormat,
      canonicalFlavor: LicenseCanonicalFlavor.kotlinLineV1,
      version: 1,
      licenseId: 'QLP-2026P1',
      product: 'QAT_LEDGER',
      edition: 'CUSTOMER',
      installationId: 'QAT-AAAA-BBBB-CCCC',
      customerName: 'مكتب النجاح',
      businessName: 'وكالة النجاح',
      packageTier: PackageTier.pro,
      licenseType: LicenseType.perpetual,
      planCode: 'PRO_PERPETUAL',
      features: LicenseFeature.proFeatures.map((f) => f.code).toList(),
      issuedAtEpochMs: 1700000000000,
      expiresAtEpochMs: 0,
      maxDevices: 1,
      maxClockSkewSeconds: 300,
      keyIdField: kotlinKeyId,
    );
    final proBytes = LicenseCanonical.kotlinCanonicalSigningBytes(proPerp);
    final proSig = await ed.sign(proBytes, keyPair: kp);
    final proQlic = LicenseCanonical.encodeQlicJson(
      keyId: kotlinKeyId,
      payload: proPerp,
      signatureBytes: proSig.bytes,
    );

    final proRes = await verifier.verify(
      rawLicenseInput: proQlic,
      currentInstallationId: 'QAT-AAAA-BBBB-CCCC',
      nowEpochMs: 1990000000000,
    );
    expect(proRes.isValid, true);
    expect(proRes.isProActive, true);
    expect(proRes.hasFeature(LicenseFeature.reports), true);
    expect(proRes.hasFeature(LicenseFeature.archive), true);
    expect(proRes.remainingDays, isNull);
  });

  test('TrustedPublicKeyRegistry strips env prefixes/PEMs and LicenseCanonical extracts QL1/.qlic from WhatsApp prose', () async {
    final ed = Ed25519();
    final kp = await ed.newKeyPairFromSeed(List<int>.generate(32, (i) => i + 21));
    final pub = await kp.extractPublicKey();
    final spkiB64 = TrustedPublicKeyRegistry.encodeSpkiBase64(pub.bytes);
    final pem = '-----BEGIN PUBLIC KEY-----\n$spkiB64\n-----END PUBLIC KEY-----';

    final prefixedCsv = 'QAT_LICENSE_PUBLIC_KEYS="$pem"';
    final reg = TrustedPublicKeyRegistry.fromCsv(prefixedCsv);
    expect(reg.isEmpty, false);
    expect(
      reg.keyIds,
      containsAll(<String>[
        TrustedPublicKeyRegistry.computeKeyIdFromRaw(pub.bytes),
        TrustedPublicKeyRegistry.computeKotlinKeyIdFromRaw(pub.bytes),
      ]),
    );
    expect(reg.keyIds.length, 2);

    const payload = LicensePayload(
      format: LicenseCanonical.canonicalPayloadFormat,
      licenseId: 'QAT-PROSE-1',
      product: 'QAT_LEDGER',
      edition: 'CUSTOMER',
      customerName: 'عميل واتساب',
      businessName: 'وكالة سبأ',
      planCode: 'PRO_PERPETUAL',
      packageTier: PackageTier.pro,
      licenseType: LicenseType.perpetual,
      issuedAtEpochMs: 1700000000000,
      expiresAtEpochMs: 0,
      installationId: 'QAT-1234-5678-9012',
      maxDevices: 1,
      features: ['REPORTS', 'ARCHIVE', 'BACKUP'],
    );
    final sig = await ed.sign(
      Uint8List.fromList(utf8.encode(LicenseCanonical.serializePayloadCanonical(payload))),
      keyPair: kp,
    );
    final ql1 = LicenseCanonical.encodeCompactToken(payload: payload, signatureBytes: sig.bytes);
    final wrappedSms = 'مرحباً بك في ميزان القات\nرمز التفعيل الخاص بك:\n$ql1\nشكراً لتعاملكم معنا';

    final parsed = LicenseCanonical.parseInput(wrappedSms);
    expect(
      LicenseCanonical.encodeCompactToken(
        payload: parsed.payload,
        signatureBytes: parsed.signatureBytes,
      ),
      ql1,
    );
    expect(parsed.payload.licenseId, 'QAT-PROSE-1');

    final verifier = OfflineLicenseVerifier(registry: reg, timeGuard: TrustedTimeGuard());
    final res = await verifier.verify(
      rawLicenseInput: wrappedSms,
      currentInstallationId: 'QAT-1234-5678-9012',
      nowEpochMs: 1710000000000,
    );
    expect(res.isValid, true);
    expect(res.isProActive, true);
  });

  test('Explicitly distinguishes UNKNOWN_KEY_ID, UNTRUSTED_KEY, and INVALID_SIGNATURE', () async {
    final ed = Ed25519();
    final trustedKp = await ed.newKeyPairFromSeed(List<int>.generate(32, (i) => i + 31));
    final trustedPub = await trustedKp.extractPublicKey();
    final trustedKid = TrustedPublicKeyRegistry.computeKotlinKeyIdFromRaw(trustedPub.bytes);
    final trustedCsv = TrustedPublicKeyRegistry.formatKotlinBuildConfigEntry(trustedPub.bytes);
    final verifier = OfflineLicenseVerifier(
      registry: TrustedPublicKeyRegistry.fromCsv(trustedCsv),
      timeGuard: TrustedTimeGuard(),
    );

    // 1. UNKNOWN_KEY_ID: Kotlin line-v1 payload with empty keyId
    final missingKidPayload = LicensePayload(
      format: LicenseCanonical.canonicalPayloadFormat,
      canonicalFlavor: LicenseCanonicalFlavor.kotlinLineV1,
      version: 1,
      licenseId: 'QL-MISSING-KID',
      product: 'QAT_LEDGER',
      edition: 'CUSTOMER',
      installationId: 'QAT-1111-2222-3333',
      customerName: 'تجربة',
      businessName: 'وكالة تجربة',
      packageTier: PackageTier.pro,
      licenseType: LicenseType.perpetual,
      planCode: 'PRO_PERPETUAL',
      features: LicenseFeature.proFeatures.map((f) => f.code).toList(),
      issuedAtEpochMs: 1700000000000,
      expiresAtEpochMs: 0,
      keyIdField: '',
    );
    final missingSig = await ed.sign(
      LicenseCanonical.canonicalBytes(missingKidPayload),
      keyPair: trustedKp,
    );
    final missingKidToken = LicenseCanonical.encodeCompactToken(
      payload: missingKidPayload,
      signatureBytes: missingSig.bytes,
    );
    final resUnknown = await verifier.verify(
      rawLicenseInput: missingKidToken,
      currentInstallationId: 'QAT-1111-2222-3333',
    );
    expect(resUnknown.kind, LicenseStateKind.unknownKeyId);

    // 2. UNTRUSTED_KEY: signed by an alien Ed25519 key whose QLK-ID is not in registry
    final alienKp = await ed.newKeyPairFromSeed(List<int>.generate(32, (i) => i + 77));
    final alienPub = await alienKp.extractPublicKey();
    final alienKid = TrustedPublicKeyRegistry.computeKotlinKeyIdFromRaw(alienPub.bytes);
    final alienPayload = LicensePayload(
      format: LicenseCanonical.canonicalPayloadFormat,
      canonicalFlavor: LicenseCanonicalFlavor.kotlinLineV1,
      version: 1,
      licenseId: 'QL-ALIEN-KID',
      product: 'QAT_LEDGER',
      edition: 'CUSTOMER',
      installationId: 'QAT-1111-2222-3333',
      customerName: 'غريب',
      businessName: 'وكالة غريبة',
      packageTier: PackageTier.pro,
      licenseType: LicenseType.perpetual,
      planCode: 'PRO_PERPETUAL',
      features: LicenseFeature.proFeatures.map((f) => f.code).toList(),
      issuedAtEpochMs: 1700000000000,
      expiresAtEpochMs: 0,
      keyIdField: alienKid,
    );
    final alienSig = await ed.sign(
      LicenseCanonical.canonicalBytes(alienPayload),
      keyPair: alienKp,
    );
    final alienToken = LicenseCanonical.encodeCompactToken(
      payload: alienPayload,
      signatureBytes: alienSig.bytes,
    );
    final resUntrusted = await verifier.verify(
      rawLicenseInput: alienToken,
      currentInstallationId: 'QAT-1111-2222-3333',
    );
    expect(resUntrusted.kind, LicenseStateKind.untrustedKey);

    // 3. INVALID_SIGNATURE: claims trusted keyId, but payload/signature tampered
    final forgedPayload = LicensePayload(
      format: LicenseCanonical.canonicalPayloadFormat,
      canonicalFlavor: LicenseCanonicalFlavor.kotlinLineV1,
      version: 1,
      licenseId: 'QL-FORGED-SIG',
      product: 'QAT_LEDGER',
      edition: 'CUSTOMER',
      installationId: 'QAT-1111-2222-3333',
      customerName: 'معدل',
      businessName: 'وكالة معدلة',
      packageTier: PackageTier.pro,
      licenseType: LicenseType.perpetual,
      planCode: 'PRO_PERPETUAL',
      features: LicenseFeature.proFeatures.map((f) => f.code).toList(),
      issuedAtEpochMs: 1700000000000,
      expiresAtEpochMs: 0,
      keyIdField: trustedKid,
    );
    final forgedToken = LicenseCanonical.encodeCompactToken(
      payload: forgedPayload,
      signatureBytes: List<int>.filled(64, 0x42),
    );
    final resInvalid = await verifier.verify(
      rawLicenseInput: forgedToken,
      currentInstallationId: 'QAT-1111-2222-3333',
    );
    expect(resInvalid.kind, LicenseStateKind.invalidSignature);
  });
}
