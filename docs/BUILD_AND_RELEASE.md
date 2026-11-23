# BUILD AND RELEASE GUIDE — QAT LEDGER (`ميزان القات`)

## 1. Prerequisites
- **Flutter SDK:** Stable channel (CI runs on Flutter stable)
- **Java JDK:** 17 (Temurin 17; AGP `9.3.1` / Gradle `9.5.0` / KGP `2.4.20`)
- **Android SDK:** `flutter.compileSdkVersion` / `flutter.targetSdkVersion`, Minimum SDK `flutter.minSdkVersion` (`24`)
- **Linux Test Runtime (`sqflite_common_ffi`):** `libsqlite3-0` / `libsqlite3-dev`, `zip`, `unzip`

---

## 2. Flavors & Edition Separation (`customer` vs `developer`)

| Flavor | Application ID | Entrypoint | Purpose | Private Keys in APK |
|---|---|---|---|---|
| `customer` | `com.aistudio.tofanqat.qatledger.customer` (owner-approved permanent ID; distribution-channel availability still needs verification) | `lib/main_customer.dart` | New end-user app; independent install beside legacy package; Room v18 migration (`tofan_al_aqsa_qat_db`) | **Zero** (`APP_EDITION=customer`; zero `lib/developer/**` imports; only public keys via `QAT_LICENSE_PUBLIC_KEYS`) |
| `developer` | `com.aistudio.tofanqat.qatledger.developer` (owner-approved permanent ID; distribution-channel availability still needs verification) | `lib/main_developer.dart` | New, separate Developer app identity | **Zero extractable private keys in APK/CI** (Ed25519 keys generated/sealed on device inside `AndroidKeyStore` AES-256-GCM or imported via encrypted `.qkey`) |

**Identity and upgrade safety:** The owner approved both proposed new IDs as permanent identities; store/registry availability still needs verification. The legacy Customer package is `com.aistudio.tofanqat.nxzwmb` (reported legacy version `2.0.0 (200)`). The proposed new IDs above are distinct from that legacy ID and from each other; they install as separate apps and are **not direct updates**. Keep the legacy installation and its data untouched. The version is `2.1.0+201` (`versionCode=201`) for both new flavors; Developer's display version has the `-dev` suffix. The namespace remains the legacy Kotlin source namespace only; packaged IDs come from the flavor `applicationId`s. These package IDs are long-lived Android identities: owner approval is recorded; confirmation that they are available in the intended distribution channels is still required before distributing any build.

---

## 3. Ed25519 Security Architecture, Key Rotation & Signing Boundaries

### Zero-Private-Key-in-APK Policy
- **Customer APK** contains **ONLY** trusted Ed25519 public keys (`QAT_LICENSE_PUBLIC_KEYS` via `BuildConfig` + `--dart-define`). It never embeds any private key, developer database schema, or license-issuance UI.
- **Developer APK** NEVER bundles any plaintext private seed (`dev_bootstrap_seed.b64` is banned from APKs, Git, and CI archives). Instead:
  1. **On-Device Hardware-Sealed Keys:** On first launch on the owner's Developer phone, `DeveloperKeyManager` generates an Ed25519 keypair and seals the seed via `AndroidKeyStore` (`AES-256-GCM`), or imports an encrypted `.qkey` vault bundle (`QAT_LEDGER_ENC_KEY_V1`, protected by `PBKDF2-HMAC-SHA256` 200,000 iterations + `AES-256-GCM` with passphrase >= 10 chars). AndroidKeyStore errors fail closed; Android builds never persist a newly generated seed to plaintext preferences. Legacy fallback seeds are sealed into AndroidKeyStore before use, and remain untouched if sealing fails. If a stored sealed key cannot be opened, the Developer bridge disables signing rather than replacing the key, while app initialization continues so accounting data remains accessible.
  2. **Public-Only CI Secret (`QAT_LICENSE_PUBLIC_KEYS`):** The developer copies ONLY the public key line (`QLK-XXXXXXXX:<base64UrlRaw32>`) into GitHub Secrets (`QAT_LICENSE_PUBLIC_KEYS`). Neither CI nor any APK ever holds a production private signing key.
  3. **Format support is not identity recovery:** `QL1.` compact tokens and `.qlic` JSON envelopes (Kotlin `licensing-core` line-v1 and Flutter v2 JSON) are supported only when their signer is present in the app's trusted public keys. Do not claim compatibility with licenses signed by an unrecovered historical key. Offline revocation uses additive signed `QLR1.` revocation notices (`QAT_LEDGER_REVOCATION_V1`); do not remove or replace trusted keys as a substitute for revocation.

### Production Signing Service Design vs Device-Local Keystore (Security Trade-off Disclosure)
- **Recommended Enterprise Option — Remote Signing Service (Cloud KMS / HSM):**
  - **Architecture:** Place the Ed25519 private key inside a non-exportable HSM / Cloud KMS (`Google Cloud KMS` or dedicated backend API with mTLS + hardware device attestation / MFA). The Developer app sends only canonical `LicenseCanonical` payload bytes over authenticated TLS; the KMS returns the 64-byte Ed25519 signature, while the Customer app remains 100% offline using the embedded KMS public key (`QAT_LICENSE_PUBLIC_KEYS`).
  - **Security Boundary:** Even full compromise or reverse-engineering of a stolen Developer phone/APK cannot extract the master private key.
- **Offline Device-Bound Alternative (Implemented):**
  - **Architecture:** No backend required; Developer phone seals its Ed25519 key inside `AndroidKeyStore` AES-256-GCM and backs up encrypted `.qkey` bundles offline.
  - **Security Boundary:** Protects completely against APK extraction and CI leaks (since zero keys reside in the APK or CI). Risk is strictly bounded to physical compromise + OS-level root extraction on the live unlocked Developer phone itself.

### Key Recovery, Continuity & Owner-Approved Transition
- **Current historical identity status:** The historical Developer Ed25519 private identity and its public key have not been recovered or independently verified. No public-key entries were found in the inspected repository/archive; this does not prove no key exists on the owner's devices or backups. Do not generate a replacement or claim old-license compatibility until legitimate recovery has been attempted.
- **Safe recovery sequence (owner-controlled device only):**
  1. Preserve the Developer phone and encrypted `.qkey` backups. Do not clear app data, reinstall, reset the device, export an unencrypted seed, or paste seed/key-vault contents into chat, CI, source control, or logs.
  2. On the original device, use the Developer console to inspect/export only the public key line and key ID. Preserve an encrypted backup of any currently working key before attempting recovery. Search owner-controlled offline backups for an encrypted `.qkey` vault; do not import over an existing identity. Recover it only on a separate owner-controlled secure Developer device, or after an owner-approved backup/restore plan. Never upload a `.qkey` or private seed.
  3. Compare the recovered public key with any documented historical key and known license-validation evidence. Treat a missing/locked key as unrecovered; do not silently replace it.
  4. If recovery fails, stop for explicit owner approval of an identity transition. Generate any replacement only on the owner-controlled Developer device or approved HSM, and document that licenses signed by an unavailable old key may not verify. Keep every recovered historical public key additively in the Customer trust set; never claim continuity without test evidence.
  5. Before production, configure `QAT_LICENSE_PUBLIC_KEYS` with public keys only and verify a test license for every retained key on Customer builds. A signing-key transition does not authorize data deletion or a licensing-semantics change.
- **General rotation after identity recovery:** Add the new public key while preserving every historical key in `QAT_LICENSE_PUBLIC_KEYS` (for example `QLK-OLD:<oldBase64UrlRaw32>,QLK-NEW:<newBase64UrlRaw32>`), rebuild, and test verification against each key before any approved release.

---

## 4. Official Android APK Signing & Google Play App Signing Audit

From the original Kotlin repository (`516e118:app/build.gradle.kts` & `516e118:README.md`):
- The original build referenced `${rootDir}/my-upload-key.jks` (`keyAlias = "upload"`) via `KEYSTORE_PATH` / `STORE_PASSWORD` / `KEY_PASSWORD`, which was **never committed to Git**.
- `516e118:app/build.gradle.kts` and `516e118:README.md` contain legacy Play-upload configuration, but the requested deliverables here are direct APKs. Do not infer the Play App Signing certificate from that legacy upload-key material.
- **New production signing identity:** After QA passes, the owner must generate a new RSA-3072 JKS key on a trusted owner-controlled computer, retain two secure backups, and configure protected Environment secrets. The identity is only for the two new package IDs; never use it with the legacy package. Follow [`RELEASE_KEY_SETUP.md`](RELEASE_KEY_SETUP.md).

### Strict CI & Gradle Rules
- **Production Release (`RELEASE_SIGNING_REQUIRED=true` / `release_mode=production_release`):**
  - `android/app/build.gradle.kts` **throws `GradleException` and refuses Debug signing** if `android/key.properties` or the `.jks` keystore file is absent.
  - `.github/workflows/flutter_build.yml` reads production credentials only for an explicit `workflow_dispatch` from `main` after the `production` Environment gate; store all six as **Environment secrets**, not repository-wide secrets:
    1. `KEYSTORE_B64` — Base64 encoding of the new owner-generated release keystore (single line)
    2. `KEYSTORE_PASSWORD` — Keystore password
    3. `KEY_ALIAS` — Verified key alias (do not assume the historical `upload` alias)
    4. `KEY_PASSWORD` — Private key password
    5. `RELEASE_KEYSTORE_SHA256` — Required 64-hex SHA-256 certificate digest, independently recorded from the owner's trusted signing certificate; CI verifies it via `keytool` before Gradle and matches both final APKs via `apksigner`
    6. `QAT_LICENSE_PUBLIC_KEYS` — Trusted Ed25519 **public** key(s) only (`QLK-XXXXXXXX:<base64UrlRaw32>`); binary audit verifies each key in Customer APK `libapp.so` + `classes*.dex`
  - Never generate an Android release identity in CI or Arena. The new private key has not been generated; no keystore or passwords are present here. After the owner generates it, `RELEASE_KEYSTORE_SHA256` in the protected `production` Environment is the owner-approved fingerprint pin; CI checks the JKS entry, alias, subject, RSA-3072/SHA256withRSA parameters and matching Customer/Developer APK signatures. The new package IDs remain independent of the old Customer ID. Secret names/values have not been verified from this session.
- **Production owner-only dispatch controls:** Pushes and pull requests are QA-only and can never access the `production` Environment or publish. A production run requires an explicit `workflow_dispatch` from the current `main` tip, `release_mode=production_release`, and a typed confirmation exactly equal to `RELEASE <full 40-character commit SHA>`. The workflow rejects any other ref, stale/non-tip commit, missing/mismatched confirmation, or a production Environment that allows any branch except exactly `main`, enables administrator bypass, or has a GitHub required-reviewers rule. Do not configure independent reviewers or treat self-approval as review; the typed confirmation is the owner's deliberate release authorization. The build job has read-only permissions; a separate publication job receives only `contents: write`, and only after every build, signing, binary, trust-root, and emulator gate succeeds. The publisher verifies checksums, serializes publication, reserves an immutable version-and-commit tag (`qat-ledger-v<semver>-build.<build-number>-<full SHA>`) with a non-forcing GitHub API ref creation, and refuses to overwrite an existing tag/release or create a duplicate. If release creation fails after the tag has been reserved, stop for manual investigation; do not force-move or silently reuse the tag. Push/PR executions and QA dispatches cannot enter that publication path.
- **Environment prerequisite (latest inspection):** GitHub currently reports `can_admins_bypass=false`, exactly one custom deployment branch policy (`main`), and no required-reviewers rule. This matches the single-owner protection model; do not add an independent reviewer or treat self-approval as one. The production Environment secret-name endpoint returned HTTP 403 to this session, so the presence or values of signing/public-key secrets remain unknown. The production workflow will fail closed if the environment policy changes or required secrets are absent/malformed.
- **Workflow supply chain:** Third-party GitHub Actions in this workflow are referenced by immutable full commit SHAs (with the corresponding release tag in a comment); update them only after verifying the upstream release and reviewed commit.
- **Official Release Gate & Required Assets:** Publication requires `flutter analyze`, `flutter test`, strict owner-approved keystore/certificate identity verification, matching `apksigner` certificates, binary isolation and Ed25519 trust-root audits, emulator smoke tests for separate Customer/Developer identities, and actual owner-run physical-device/data-restore tests for the exact commit. No legacy in-place upgrade is expected or tested because the new IDs intentionally differ. The immutable versioned tag is `qat-ledger-v<semver>-build.<build-number>-<full-commit-sha>`. The public release contains exactly `QAT-Ledger-Customer.apk`, `QAT-Ledger-Developer.apk`, `SHA256SUMS.txt`, and `RELEASE-NOTES.md`; the checksum manifest covers both APKs and the notes/evidence file. Release notes include version/build/commit/date, minimum Android API, certificate fingerprint, separate-install `.qatbackup` migration guidance, and the explicit non-update relationship to the legacy app. The publisher downloads the actual release assets again and rechecks their checksums and APK signatures before the workflow succeeds.
- **QA-only mode:** Pushes, PRs, and non-production dispatches use QA signing, never access production secrets, and cannot publish a production release. If no verified historical public keys are available, QA builds embed no license trust root; CI does not generate a replacement key, reports licensing as `BLOCKED`, and labels APKs `QAT_LEDGER_CUSTOMER_QA_DEBUG_ONLY.apk` and `QAT_LEDGER_DEVELOPER_QA_DEBUG_ONLY.apk`.

### Customer data migration to the new independent package
The new Customer package ID intentionally differs from the legacy ID, so install the new app next to the old app; do **not** uninstall, overwrite, or clear the legacy Customer app/data. A `.qatbackup` restore creates data in the new installation, not an in-place upgrade.

1. In the old Customer app, open **Settings → backup/restore** and export the `.qatbackup` using the Android system “Save as” document picker. Save it to a user-visible location such as Documents, Downloads, removable storage, or another independently backed-up provider—not the app's internal folder or `Android/data`.
2. Keep two copies outside the phone's app-private storage. Re-open the file picker and confirm the exported file exists and is non-empty. Preserve the original unchanged; do not uninstall or clear the old app at any point in this migration. Never attach the backup to GitHub, CI artifacts, or a public link. If possible, copy it to a second owner-controlled device or computer.
3. Install the new Customer alongside the legacy Customer, without uninstalling either app. In the new app, choose **Settings → backup/restore → inspect/restore**, select the exported `.qatbackup`, and review the validation summary before confirming restore. The importer verifies the package/database and SQLite integrity, restores transactionally with a rollback snapshot, and preserves the legacy app's separate private data.
4. After restore, verify agency/account setup, YER/SAR balances, customers, invoices/sales, suppliers, stock, expenses, taxes, transfers, and transaction history against the old device's last reports. Keep the external backup unchanged until those checks are complete. Do not delete the old device or its copy beforehand.
5. A license/installation ID is device-bound and is not part of the accounting database backup; the customer may need a new license. Historical licenses signed by an unrecovered Ed25519 key may not validate. Do not promise continuity until the corresponding public key and a test license have been verified.

The codebase contains synthetic Room migration and backup/restore tests, but **no actual `.qatbackup` restore inside the new Customer build or physical-device migration was tested in this mission**. The release gate therefore requires an owner-authored GitHub issue/PR comment URL for the exact commit containing these lines: `two_device_license_transfer=PASS`, `license_restart_persistence=PASS`, `customer_backup_saved_outside_app_data=PASS`, `legacy_customer_and_new_customer_coexist=PASS`, `legacy_customer_data_unchanged=PASS`, `customer_backup_restore_on_new_install=PASS`, `all_tables_and_records_verified=PASS`, `all_financial_totals_verified=PASS`, `settings_and_logo_verified=PASS`, `sqlite_integrity_check=PASS`, `foreign_key_check=PASS`, `clean_install_launch=PASS`, and `commit_sha=<full SHA>`. Use synthetic data only; never attach or upload `.qatbackup` data. This is an owner evidence record, not an independent CI hardware test; the report labels it as owner-attested.

### Owner-only Release key and encrypted-secret setup
Follow [`RELEASE_KEY_SETUP.md`](RELEASE_KEY_SETUP.md) after QA passes. The owner creates and backs up the new key off Arena, verifies the public certificate fingerprint, and sets the required encrypted secrets only in the protected `production` Environment. No key or password has been created, disclosed, or stored by this session. Production remains blocked until the physical-device data restore, historical license verification, `apksigner` verification, Environment setup, and separate final owner approval all pass.

---

## 5. Build & Verification Commands

```bash
# 1. Fetch dependencies
flutter pub get

# 2. Static analysis & automated unit/widget/migration/E2E tests
flutter analyze
flutter test -r expanded

# 3. Official Production Customer Release APK (requires android/key.properties + qat-ledger-production.jks)
flutter build apk --release --flavor customer \
  -t lib/main_customer.dart \
  -PRELEASE_SIGNING_REQUIRED=true \
  --dart-define=APP_EDITION=customer \
  --dart-define=QAT_LICENSE_PUBLIC_KEYS="<QLK_ENTRY_CSV>"

# 4. Official Production Developer Release APK (requires android/key.properties + qat-ledger-production.jks)
flutter build apk --release --flavor developer \
  -t lib/main_developer.dart \
  -PRELEASE_SIGNING_REQUIRED=true \
  --dart-define=APP_EDITION=developer \
  --dart-define=QAT_LICENSE_PUBLIC_KEYS="<QLK_ENTRY_CSV>"
```
