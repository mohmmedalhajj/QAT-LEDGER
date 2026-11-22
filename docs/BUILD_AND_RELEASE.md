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
| `customer` | `com.aistudio.tofanqat.nxzwmb` | `lib/main_customer.dart` | End-user production app preserving in-place upgrade & Room v18 migration (`tofan_al_aqsa_qat_db`) | **Zero** (`APP_EDITION=customer`; zero `lib/developer/**` imports; only public keys via `QAT_LICENSE_PUBLIC_KEYS`) |
| `developer` | `com.aistudio.tofanqat.nxzwmb.dev` | `lib/main_developer.dart` | Internal vendor/developer issuance build | **Zero extractable private keys in APK/CI** (Ed25519 keys generated/sealed on device inside `AndroidKeyStore` AES-256-GCM or imported via encrypted `.qkey`) |

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
- `516e118:app/build.gradle.kts` set `dependenciesInfo { includeInApk = false; includeInBundle = true }`, and `516e118:README.md` explicitly referenced Google Play Console upload key reset — indicating **Google Play App Signing** architecture:
  - **Play App Signing Key (held by Google Play):** Signs final Play-distributed APKs derived from uploaded `.aab` bundles.
  - **Upload Key (`my-upload-key.jks`, alias `upload`):** Used by the developer/CI to sign `.aab` uploads to Play Console (or direct sideload APKs if distributed directly outside Play).
  - **If `my-upload-key.jks` is lost:**
    1. If distributed via **Google Play (Play App Signing enabled)**: Generate a new upload keystore (`keytool -genkeypair ... -alias upload`), export its PEM certificate (`keytool -export -rfc ...`), and follow Google Play Console **Setup → App integrity → Request upload key reset** — **Play preserves the underlying App Signing key so users still update seamlessly**.
    2. If distributed as **direct sideload APKs outside Play**: A lost keystore cannot produce in-place upgrades over earlier direct installs; installing a new keystore requires uninstalling the older build (backup `.qatbackup` first!) or recovering the original `my-upload-key.jks` from the original AI Studio / developer machine.

### Strict CI & Gradle Rules
- **Production Release (`RELEASE_SIGNING_REQUIRED=true` / `release_mode=production_release`):**
  - `android/app/build.gradle.kts` **throws `GradleException` and refuses Debug signing** if `android/key.properties` or the `.jks` keystore file is absent.
  - `.github/workflows/flutter_build.yml` reads production credentials only for an explicit `workflow_dispatch` from `main` after the `production` Environment gate; store all six as **Environment secrets**, not repository-wide secrets:
    1. `KEYSTORE_B64` — Base64 encoding of the original owner-controlled release keystore (single line)
    2. `KEYSTORE_PASSWORD` — Keystore password
    3. `KEY_ALIAS` — Verified key alias (do not assume the historical `upload` alias)
    4. `KEY_PASSWORD` — Private key password
    5. `RELEASE_KEYSTORE_SHA256` — Required 64-hex SHA-256 certificate digest, independently recorded from the owner's trusted signing certificate; CI verifies it via `keytool` before Gradle and matches both final APKs via `apksigner`
    6. `QAT_LICENSE_PUBLIC_KEYS` — Trusted Ed25519 **public** key(s) only (`QLK-XXXXXXXX:<base64UrlRaw32>`); binary audit verifies each key in Customer APK `libapp.so` + `classes*.dex`
  - Never generate an Android release identity in CI. The owner must recover/confirm the original keystore and compare its certificate fingerprint with a trusted existing record or original signed artifact before setting secrets. If that cannot be done, stop for an owner-approved signing transition; a changed certificate may block direct-APK in-place upgrades.
- **Production owner-only dispatch controls:** Pushes and pull requests are QA-only and can never access the `production` Environment or publish. A production run requires an explicit `workflow_dispatch` from the current `main` tip, `release_mode=production_release`, and a typed confirmation exactly equal to `RELEASE <full 40-character commit SHA>`. The workflow rejects any other ref, stale/non-tip commit, missing/mismatched confirmation, or a production Environment that allows any branch except exactly `main`, enables administrator bypass, or has a GitHub required-reviewers rule. Do not configure independent reviewers or treat self-approval as review; the typed confirmation is the owner's deliberate release authorization. The build job has read-only permissions; a separate publication job receives only `contents: write`, and only after every build, signing, binary, trust-root, and emulator gate succeeds. The publisher verifies checksums, serializes publication, reserves an immutable per-commit tag (`qat-ledger-<full SHA>`) with a non-forcing GitHub API ref creation, and refuses to overwrite an existing tag/release or create a duplicate. If release creation fails after the tag has been reserved, stop for manual investigation; do not force-move or silently reuse the tag. Push/PR executions and QA dispatches cannot enter that publication path.
- **Environment prerequisite:** `production` must have administrator bypass disabled and a deployment branch policy allowing only `main`; it must have no required-reviewers protection rule for the single-owner model. As of the last settings inspection, this repository's environment still had one required reviewer configured, which does not satisfy this model. The Arena GitHub integration returned HTTP 403 when asked to update environment protection settings, so the owner must remove that rule in repository Settings → Environments → production before production dispatch can pass. Do not rely on a successful QA run as evidence that production settings are ready.
- **Workflow supply chain:** Third-party GitHub Actions in this workflow are referenced by immutable full commit SHAs (with the corresponding release tag in a comment); update them only after verifying the upstream release and reviewed commit.
- **Official Release Gate & Required 10 Release Assets:**
  - Publication requires `flutter analyze`, `flutter test`, strict keystore and fingerprint verification, matching `apksigner` certificates, binary isolation and Ed25519 trust-root audits, plus emulator smoke test and same-cert reinstall. Only these 10 audited assets are staged for the separate publisher: `QAT_LEDGER_CUSTOMER_RELEASE.apk`, `QAT_LEDGER_DEVELOPER_RELEASE.apk`, `QAT_LEDGER_FLUTTER_SOURCE.zip`, `SHA256SUMS.txt`, `BUILD_SIGNING_STATUS.txt`, `customer-signature.txt`, `developer-signature.txt`, `binary-isolation-audit.txt`, `LICENSE_TRUST_AUDIT.txt`, and `FINAL_BUILD_AND_TEST_REPORT.txt`.
- **QA-only mode:** Pushes, PRs, and non-production dispatches use QA signing and an ephemeral QA public trust root, do not access production secrets, and cannot publish a production release. QA artifacts remain explicitly labeled `QAT_LEDGER_CUSTOMER_QA_DEBUG_ONLY.apk` and `QAT_LEDGER_DEVELOPER_QA_DEBUG_ONLY.apk`.

### Owner-only preparation of the original signing identity
Run these checks only on the owner's trusted machine, after locating the original keystore and confirming the correct alias. Do not paste terminal output containing key material or passwords into chat, CI, issues, or logs. The certificate fingerprint is public; independently compare it with a trusted old record or original signed APK before putting it into `RELEASE_KEYSTORE_SHA256`.
```bash
# Inspect the certificate SHA-256 fingerprint; keytool prompts for the password.
keytool -list -v -keystore /secure/path/original-release.jks -alias "$KEY_ALIAS" | grep SHA256

# Base64-encode locally for the protected GitHub production Environment secret only.
base64 -w 0 /secure/path/original-release.jks
```
Store the encoded keystore, store password, alias, key password, independently verified fingerprint, and public-only `QAT_LICENSE_PUBLIC_KEYS` as secrets on the `production` Environment only after disabling administrator bypass, removing all required-reviewer rules (single-owner policy), and restricting that Environment to `main`. Remove any repository-level copies of these production secrets. Never configure the Developer private Ed25519 seed as a GitHub secret. For an authorized dispatch, select `production_release` and enter `RELEASE <the exact current main commit SHA>` in `production_confirmation`; do not run production until the historical signing identity and Ed25519 public trust root are confirmed and all QA prerequisites have passed.

### Owner-authorized new Android signing identity (only if recovery fails)
Do not run this while the original identity may still be recoverable. First complete the recovery checks above, have the owner explicitly approve an identity transition, and assess whether any existing direct-APK installation needs the old signing certificate. A new certificate can prevent in-place updates. Run only on the owner's trusted machine, never in Arena or CI:
```bash
umask 077
install -d -m 700 "$HOME/qat-ledger-signing"
KEYSTORE_PATH="$HOME/qat-ledger-signing/qat-ledger-release.jks"
keytool -genkeypair -v \
  -keystore "$KEYSTORE_PATH" \
  -storetype JKS \
  -alias qat-ledger-release \
  -keyalg RSA -keysize 3072 -validity 10000

# keytool prompts interactively; do not put passwords on the command line.
# Record the public certificate fingerprint independently:
keytool -list -v -keystore "$KEYSTORE_PATH" -alias qat-ledger-release | grep 'SHA256:'
```
Back up the keystore securely in two owner-controlled locations, independently compare the SHA-256 fingerprint, and only then provision production Environment secrets. Never commit or artifact the keystore, use a QA/debug certificate for production, or create an identity just to unblock CI.

---

## 5. Build & Verification Commands

```bash
# 1. Fetch dependencies
flutter pub get

# 2. Static analysis & automated unit/widget/migration/E2E tests
flutter analyze
flutter test -r expanded

# 3. Official Production Customer Release APK (requires android/key.properties + release.jks)
flutter build apk --release --flavor customer \
  -t lib/main_customer.dart \
  -PRELEASE_SIGNING_REQUIRED=true \
  --dart-define=APP_EDITION=customer \
  --dart-define=QAT_LICENSE_PUBLIC_KEYS="<QLK_ENTRY_CSV>"

# 4. Official Production Developer Release APK (requires android/key.properties + release.jks)
flutter build apk --release --flavor developer \
  -t lib/main_developer.dart \
  -PRELEASE_SIGNING_REQUIRED=true \
  --dart-define=APP_EDITION=developer \
  --dart-define=QAT_LICENSE_PUBLIC_KEYS="<QLK_ENTRY_CSV>"
```
