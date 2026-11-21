# BUILD AND RELEASE GUIDE — QAT LEDGER (`ميزان القات`)

## 1. Prerequisites
- **Flutter SDK:** Stable channel (CI runs on Flutter stable)
- **Java JDK:** 17 (Temurin 17; AGP `9.3.1` / Gradle `9.5.0` / KGP `2.4.20`)
- **Android SDK:** `flutter.compileSdkVersion` / `flutter.targetSdkVersion`, Minimum SDK `flutter.minSdkVersion` (`24`)
- **Linux Test Runtime (`sqflite_common_ffi`):** `libsqlite3-0` / `libsqlite3-dev`, `zip`, `unzip`

---

## 2. Flavors & Edition Separation (`customer` vs `developer`)

| Flavor | Application ID | Entrypoint | Purpose | Developer Signing / Console (`780mohmmed`) |
|---|---|---|---|---|
| `customer` | `com.aistudio.tofanqat.nxzwmb` | `lib/main_customer.dart` | End-user production app preserving in-place upgrade & Room v18 migration (`tofan_al_aqsa_qat_db`) | **Excluded from binary** (`APP_EDITION=customer`; `"780mohmmed"` gate opens local Agency Name & Section Permissions only; zero `lib/developer/**` imports & no `dev_bootstrap_seed.b64`) |
| `developer` | `com.aistudio.tofanqat.nxzwmb.dev` | `lib/main_developer.dart` | Internal vendor/developer issuance build | **Enabled** (`APP_EDITION=developer`; exposes Ed25519 Key Manager & License Signer with paired bootstrap seed) |

---

## 3. Ed25519 Key Continuity, Rotation & Security Trade-Offs

### Security Model & Trade-off Disclosure
- **Customer APK** contains **ONLY** trusted Ed25519 public keys (`QAT_LICENSE_PUBLIC_KEYS` in `BuildConfig` + `--dart-define`). It never embeds any private key, signing seed, developer database schema, or license-issuance UI.
- **Developer APK** is designed for offline license issuance (`com.aistudio.tofanqat.nxzwmb.dev`). On first launch, `DeveloperKeyManager` imports the build-time `assets/dev_bootstrap_seed.b64` and seals the 32-byte seed into Android Keystore (`AES-256-GCM`) inside private app preferences (`SharedPreferences`).
- **Honest Security Trade-off:** Because offline license issuance does not call an online backend, bundling `dev_bootstrap_seed.b64` inside the Developer APK means anyone who acquires and reverse-engineers the **Developer APK** could extract that bootstrap seed. Therefore:
  1. **NEVER distribute the Developer APK publicly.** Only install it on the owner's/vendor's controlled device(s).
  2. Use `QAT_DEV_ED25519_SEED_B64` stored strictly in **GitHub Repository Secrets** (never in Git) and masked during CI builds.
  3. On a dedicated hardware-only workflow, you can omit `QAT_DEV_ED25519_SEED_B64` from CI, generate/hold the seed solely in an air-gapped Developer device/vault, and embed only the resulting public key line (`QLK-XXXXXXXX:<base64UrlRaw32>`) in `QAT_LICENSE_PUBLIC_KEYS`.

### Key Continuity & Rotation Policy
- **Why persistent keys matter:** If every build generates an ephemeral Ed25519 key without preserving older public keys, previously issued customer licenses become `UNTRUSTED_KEY` after updating the Customer APK (or vice versa if the Developer app rotates keys without updating Customer's trusted list).
- **Production rule enforced in CI:** Official production releases (`release_mode=production_release` or when official release keystore secrets are present) **FAIL FAST** unless persistent `QAT_DEV_ED25519_SEED_B64` is configured.
- **How to rotate Ed25519 keys without breaking existing customers:**
  1. Generate a new 32-byte Ed25519 seed (`openssl genpkey -algorithm ed25519 -outform DER`), extract its 32-byte seed + `QLK-NEWKEY:<base64UrlRaw32>` public key entry.
  2. Keep the previous public key entry in GitHub Secret `QAT_LICENSE_PUBLIC_KEYS`:
     ```text
     QLK-OLDKEY:<oldBase64UrlRaw32>,QLK-NEWKEY:<newBase64UrlRaw32>
     ```
  3. Replace `QAT_DEV_ED25519_SEED_B64` with the new seed so newly built Developer APKs sign with `QLK-NEWKEY` while newly built Customer APKs verify **both** `QLK-OLDKEY` (legacy customers) and `QLK-NEWKEY` (new customers).

---

## 4. Official Release Signing Enforcement (No Debug Fallback for Production)

From the original Kotlin project (`516e118:app/build.gradle.kts`), the original release build used an external keystore (`my-upload-key.jks`, alias `upload`) that was **never committed to Git**.

### Strict CI & Gradle Rules
- **Production Release (`RELEASE_SIGNING_REQUIRED=true` / `release_mode=production_release`):**
  - `android/app/build.gradle.kts` **throws `GradleException` and refuses Debug signing** if `android/key.properties` or the `.jks` keystore file is absent.
  - `.github/workflows/flutter_build.yml` requires **all** of these GitHub Repository Secrets:
    1. `KEYSTORE_B64` — Base64 encoding of the original upload keystore (`my-upload-key.jks` / `.keystore`)
    2. `KEYSTORE_PASSWORD` — Store password (`STORE_PASSWORD` in original Kotlin `516e118`)
    3. `KEY_ALIAS` — Key alias (`upload` in original Kotlin `516e118`)
    4. `KEY_PASSWORD` — Key password (`KEY_PASSWORD` in original Kotlin `516e118`)
    5. `RELEASE_KEYSTORE_SHA256` *(strongly recommended)* — Expected SHA-256 certificate digest of the official signing certificate to verify exact match before signing
    6. `QAT_DEV_ED25519_SEED_B64` — Persistent 32-byte Ed25519 seed (Base64 or 64-hex)
    7. `QAT_LICENSE_PUBLIC_KEYS` *(recommended for rotation)* — Additional comma-separated historical `QLK-...:...` public keys
- **When Official Secrets Are Missing (`qa_debug_only`):**
  - CI halts publishing any Production GitHub Release (`Official GitHub Release` step is skipped).
  - Builds are strictly labeled `QAT_LEDGER_CUSTOMER_QA_DEBUG_ONLY.apk` and `QAT_LEDGER_DEVELOPER_QA_DEBUG_ONLY.apk` and uploaded solely as workflow artifacts for QA testing and binary/emulator verification.
  - Android **will reject in-place upgrades** (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`) over an earlier production install signed by the original `my-upload-key.jks` if installed from a `QA_DEBUG_ONLY` APK.

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
  --dart-define=QAT_LICENSE_PUBLIC_KEYS="<QLK_OR_SPKI_BASE64>"

# 4. Official Production Developer Release APK (requires android/key.properties + release.jks)
flutter build apk --release --flavor developer \
  -t lib/main_developer.dart \
  -PRELEASE_SIGNING_REQUIRED=true \
  --dart-define=APP_EDITION=developer \
  --dart-define=QAT_LICENSE_PUBLIC_KEYS="<QLK_OR_SPKI_BASE64>"
```

## 6. Real-Device Hardware Notes
- **Bluetooth ESC/POS (`EscPosService` + `MainActivity.kt` SPP):** Verified statically and via formatter/raster tests; actual paper feed & Bluetooth RFCOMM transmission require a physical paired 58mm/80mm thermal printer.
- **In-Place Android Upgrade (`tofan_al_aqsa_qat_db` -> Flutter v5):** Schema and full 14-table Room v18 migration verified via `sqflite_common_ffi` tests with `PRAGMA foreign_key_check`; actual Android OS in-place `adb install -r` upgrade over a production `516e118` install can only run once the owner supplies the original `my-upload-key.jks` signing certificate.
