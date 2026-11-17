# BUILD AND RELEASE GUIDE — QAT LEDGER (`ميزان القات`)

## 1. Prerequisites
- **Flutter SDK:** Stable channel (CI verified on Flutter stable / Dart `3.14.0` ecosystem)
- **Java JDK:** 17 (Temurin 17; AGP `9.3.1` / Gradle `9.5.0` / KGP `2.4.20`)
- **Android SDK:** `flutter.compileSdkVersion` / `flutter.targetSdkVersion` (`36` on Flutter stable), Minimum SDK `flutter.minSdkVersion` (`24`)
- **Linux Test Runtime (for `sqflite_common_ffi` unit tests):** `libsqlite3-0` / `libsqlite3-dev`

---

## 2. Flavors & Edition Separation (`customer` vs `developer`)

| Flavor | Application ID | Purpose | Developer Console (`780mohmmed`) |
|---|---|---|---|
| `customer` | `com.aistudio.tofanqat.nxzwmb` | Production end-user app; preserves upgrade path over existing Room v18 Android installs | **Excluded / Hidden** (`APP_EDITION=customer`; `"780mohmmed"` gate opens local Agency Name & Section Permissions only) |
| `developer` | `com.aistudio.tofanqat.nxzwmb.dev` | Internal vendor/developer issuance build | **Enabled** (`APP_EDITION=developer`; exposes Ed25519 Key Manager & License Signer) |

---

## 3. Configuring Trusted Ed25519 Public Keys

Before building a Customer release APK:
1. Open the Developer Edition app -> `الإعدادات -> الترخيص والأمان -> وحدة تحكم المطور -> المفاتيح والتدقيق` and copy the **SPKI Base64** public key.
2. Supply it at build time via EITHER:
   - Dart define: `--dart-define=QAT_LICENSE_PUBLIC_KEYS="<KEY1_SPKI_B64>,<KEY2_SPKI_B64>"`
   - Or Gradle property / environment (`-PQAT_LICENSE_PUBLIC_KEYS="..."` or GitHub Secret `QAT_LICENSE_PUBLIC_KEYS`)

Customer builds with an empty `QAT_LICENSE_PUBLIC_KEYS` safely report `untrustedKey` (`مفتاح غير موثوق`) instead of accepting untrusted tokens or crashing. Never place private Ed25519 seeds in `customer` code, `gradle.properties`, Git, or CI logs.

---

## 4. Release Signing Configuration (Original Keystore Continuity)

`android/app/build.gradle.kts` automatically checks for `android/key.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`):

1. **For production & in-place upgrade over previous installs:**
   - Place your original `.jks` keystore file at `android/app/release.jks` (gitignored).
   - Create `android/key.properties` (gitignored):
     ```properties
     storeFile=release.jks
     storePassword=YOUR_STORE_PASSWORD
     keyAlias=YOUR_KEY_ALIAS
     keyPassword=YOUR_KEY_PASSWORD
     ```
   - **Important:** Android requires the exact same signing certificate to upgrade existing customer installs of `com.aistudio.tofanqat.nxzwmb` in-place while preserving `/data/data/com.aistudio.tofanqat.nxzwmb/databases/tofan_al_aqsa_qat_db`. Without the original `.jks`, Android rejects in-place upgrades (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).
2. **Fallback when `android/key.properties` is absent:**
   - Gradle falls back to Android debug signing with an explicit build warning (`for clean install/QA only — never valid for store/in-place upgrade over production builds`).
3. **For GitHub Actions (`docs/ci/flutter_build.yml`):**
   - Copy `docs/ci/flutter_build.yml` into `.github/workflows/flutter_build.yml` on GitHub.
   - Configure GitHub Repository Secrets: `KEYSTORE_B64` (base64 of `.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, and `QAT_LICENSE_PUBLIC_KEYS`.

---

## 5. Build & Verification Commands

```bash
# 1. Fetch dependencies
flutter pub get

# 2. Static analysis & automated unit/widget/migration tests
flutter analyze
flutter test -r expanded

# 3. Build Customer Release APK
flutter build apk --release --flavor customer \
  --dart-define=APP_EDITION=customer \
  --dart-define=QAT_LICENSE_PUBLIC_KEYS="<SPKI_BASE64>"

# 4. Build Developer Release APK
flutter build apk --release --flavor developer \
  --dart-define=APP_EDITION=developer \
  --dart-define=QAT_LICENSE_PUBLIC_KEYS="<SPKI_BASE64>"
```

Expected output paths after running on a Flutter + Android SDK host:
- Customer Release APK: `build/app/outputs/flutter-apk/app-customer-release.apk`
- Developer Release APK: `build/app/outputs/flutter-apk/app-developer-release.apk`

---

## 6. Current Sandbox & GitHub Actions Build Status
- **APK Binaries Built Locally:** **NONE** (sandbox lacks Flutter/JDK/Android SDK).
- **GitHub Actions Run `44039066878` (`c7a24e1`, current active `.github/workflows/flutter_build.yml`):**
  - Steps `1–15` **ALL PASSED (`success`)**:
    - `flutter pub get`, `flutter analyze` (0 issues), `flutter test -r expanded` (all 8 test suites), `Ensure Android Gradle wrapper exists`,
    - `Configure release signing key (fallback to Debug signing if missing)` (`signed_by=debug_fallback` with automatic warning because GitHub release keystore secrets are not set),
    - `Build Customer Release APK` (`com.aistudio.tofanqat.nxzwmb`, `5m 38s`),
    - `Build Developer Release APK` (`com.aistudio.tofanqat.nxzwmb.dev`, `1m 16s`),
    - `Verify APKs and prepare release files` (`aapt2 dump badging` package checks, `apksigner verify --print-certs --verbose`, `sha256sum`),
    - `Upload release APKs and verification reports` (`qat-ledger-release-apks`, ID `14308807706`, `70,998,074` bytes, ZIP SHA-256 `e8b519c1e1d4e2050dfb518c2e77c70a0f4caf149eac8c0f0fdd3faead341cac`).
  - **Step `16` (`Smoke-test APKs on Android Emulator`) FAILED (`32s`)**: emulator process exited early after launch on the headless GitHub runner; Step `17` uploaded `qat-ledger-emulator-smoke` (`639 bytes`).
- **Signing Note:** Current CI APKs are signed with **Debug signing** (suitable for QA, testing, and fresh installations; not valid for in-place upgrades over previous production builds signed by a different keystore).
- **Thermal Printer Note:** ESC/POS `GS v 0` 58mm/80mm raster printing (`EscPosService` + `MainActivity.kt` SPP) has not been executed on a physical Bluetooth printer and requires real device hardware verification.
- **Thermal Printer Note:** ESC/POS `GS v 0` 58mm/80mm raster printing (`EscPosService` + `MainActivity.kt` SPP) has not been executed on a physical Bluetooth printer and requires real device hardware verification.
