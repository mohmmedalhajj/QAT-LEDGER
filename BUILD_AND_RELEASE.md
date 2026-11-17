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
- **GitHub Actions Run `44020629330` (`7c3c3f6`):**
  - Steps `1–14` **PASSED**: `flutter pub get`, `flutter analyze`, `flutter test -r expanded` (all 8 test suites), `Build Customer Release APK`, `Build Developer Release APK`, and `Verify APK manifests, package IDs, signatures, and bundle deliverables` (`aapt2` + `apksigner` + `sha256sum`).
  - **Step `15` (`Smoke-test Customer & Developer APKs on Android Emulator`) HUNG** for >47 minutes because `adb wait-for-device` lacked a timeout when `/dev/kvm` was present, hitting the 45-minute job timeout (`cancelled` at `2026-11-17T05:51:56Z`) before Step `16` (`Upload release APKs and verification reports`) could run.
  - **Uploaded Artifacts:** `0` (`{"total_count":0,"artifacts":[]}`).
  - **Fix Prepared:** Updated `docs/ci/flutter_build.yml` (and `.github/workflows/flutter_build.yml`) so `upload-artifact@v4` runs immediately after Step `14`, and Step `16` (emulator smoke test) is bounded (`timeout-minutes: 10`, `emulator -accel-check`, `EMU_PID` liveness check, bounded 180s ADB/boot wait, clean teardown).
- **Thermal Printer Note:** ESC/POS `GS v 0` 58mm/80mm raster printing (`EscPosService` + `MainActivity.kt` SPP) has not been executed on a physical Bluetooth printer and requires real device hardware verification.
