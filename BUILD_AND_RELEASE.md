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
  1. **On-Device Hardware-Sealed Keys:** On first launch on the owner's Developer phone, `DeveloperKeyManager` generates an Ed25519 keypair and seals the seed via `AndroidKeyStore` (`AES-256-GCM`), or imports an encrypted `.qkey` vault bundle (`QAT_LEDGER_ENC_KEY_V1`, protected by `PBKDF2-HMAC-SHA256` 200,000 iterations + `AES-256-GCM` with passphrase >= 10 chars).
  2. **Public-Only CI Secret (`QAT_LICENSE_PUBLIC_KEYS`):** The developer copies ONLY the public key line (`QLK-XXXXXXXX:<base64UrlRaw32>`) into GitHub Secrets (`QAT_LICENSE_PUBLIC_KEYS`). Neither CI nor any APK ever holds a production private signing key.
  3. **Legacy Format Compatibility & Signed Revocation:** Existing `QL1.` compact tokens and `.qlic` JSON envelopes (both Kotlin `licensing-core` line-v1 and Flutter v2 JSON) remain 100% valid; offline revocation uses additive signed `QLR1.` revocation notices (`QAT_LEDGER_REVOCATION_V1`) without invalidating legacy licenses.

### Production Signing Service Design vs Device-Local Keystore (Security Trade-off Disclosure)
- **Recommended Enterprise Option — Remote Signing Service (Cloud KMS / HSM):**
  - **Architecture:** Place the Ed25519 private key inside a non-exportable HSM / Cloud KMS (`Google Cloud KMS` or dedicated backend API with mTLS + hardware device attestation / MFA). The Developer app sends only canonical `LicenseCanonical` payload bytes over authenticated TLS; the KMS returns the 64-byte Ed25519 signature, while the Customer app remains 100% offline using the embedded KMS public key (`QAT_LICENSE_PUBLIC_KEYS`).
  - **Security Boundary:** Even full compromise or reverse-engineering of a stolen Developer phone/APK cannot extract the master private key.
- **Offline Device-Bound Alternative (Implemented):**
  - **Architecture:** No backend required; Developer phone seals its Ed25519 key inside `AndroidKeyStore` AES-256-GCM and backs up encrypted `.qkey` bundles offline.
  - **Security Boundary:** Protects completely against APK extraction and CI leaks (since zero keys reside in the APK or CI). Risk is strictly bounded to physical compromise + OS-level root extraction on the live unlocked Developer phone itself.

### Key Continuity & Rotation Policy
- **Why persistent keys matter:** Rotating Ed25519 keys without keeping older public keys in `QAT_LICENSE_PUBLIC_KEYS` causes previously issued customer licenses to fail with `UNTRUSTED_KEY`.
- **How to rotate without breaking existing customers:**
  1. Rotate to a new Ed25519 key (`QLK-NEWKEY`) in the Developer Console (or HSM/KMS).
  2. Append the new public key while preserving the older public key(s) in GitHub Secret `QAT_LICENSE_PUBLIC_KEYS`:
     ```text
     QLK-OLDKEY:<oldBase64UrlRaw32>,QLK-NEWKEY:<newBase64UrlRaw32>
     ```
  3. Rebuild the Customer APK so existing customers remain active (`QLK-OLDKEY`) and new customers verify against `QLK-NEWKEY`.

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
  - `.github/workflows/flutter_build.yml` enforces **all six** GitHub Repository Secrets before allowing `is_official_release=true` or publishing an Official GitHub Release:
    1. `KEYSTORE_B64` — Base64 encoding of `my-upload-key.jks` (single line)
    2. `KEYSTORE_PASSWORD` — Keystore password
    3. `KEY_ALIAS` — Key alias (`upload` in original `516e118`)
    4. `KEY_PASSWORD` — Private key password
    5. `RELEASE_KEYSTORE_SHA256` — **Required** 64-hex SHA-256 certificate digest verified via `keytool` before Gradle runs AND matched against `apksigner` output on both final APKs
    6. `QAT_LICENSE_PUBLIC_KEYS` — Trusted Ed25519 **public** key(s) (`QLK-XXXXXXXX:<base64UrlRaw32>`); binary audit verifies each key is embedded in Customer APK `libapp.so` + `classes*.dex`
- **Official Release Gate & Required 10 Release Assets:**
  - `Publish Official GitHub Release` executes **only after** `flutter analyze`, `flutter test`, strict keystore & SHA-256 verification, `apksigner` certificate match, binary isolation + Ed25519 trust-root audit, and `Smoke-test APKs on Android Emulator` (including same-cert in-place reinstall) ALL pass, attaching all 10 artifacts:
    `QAT_LEDGER_CUSTOMER_RELEASE.apk`, `QAT_LEDGER_DEVELOPER_RELEASE.apk`, `QAT_LEDGER_FLUTTER_SOURCE.zip`, `SHA256SUMS.txt`, `BUILD_SIGNING_STATUS.txt`, `customer-signature.txt`, `developer-signature.txt`, `binary-isolation-audit.txt`, `LICENSE_TRUST_AUDIT.txt`, and `FINAL_BUILD_AND_TEST_REPORT.txt`.
- **When Official Secrets Are Missing (`qa_debug_only`):**
  - CI halts publishing any Production GitHub Release (`Publish Official GitHub Release` is skipped).
  - Builds are strictly labeled `QAT_LEDGER_CUSTOMER_QA_DEBUG_ONLY.apk` and `QAT_LEDGER_DEVELOPER_QA_DEBUG_ONLY.apk` for QA verification only.

### Encoding Your Original Keystore for GitHub Secrets
```bash
# Base64-encode the original keystore (single line, no wrapping)
base64 -w 0 my-upload-key.jks

# Inspect the SHA-256 certificate fingerprint (for RELEASE_KEYSTORE_SHA256)
keytool -list -v -keystore my-upload-key.jks -alias upload | grep SHA256
```

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
