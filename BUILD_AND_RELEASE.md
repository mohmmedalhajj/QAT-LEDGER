# BUILD AND RELEASE GUIDE — QAT LEDGER (`ميزان القات`)

## 1. Prerequisites
- **Flutter SDK:** Stable channel (`>= 3.29`)
- **Java JDK:** 17
- **Android SDK:** Compile/Target SDK `35`, Minimum SDK `24`
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

## 6. Current Sandbox Build Status
- **APK Binaries Built Locally:** **NONE** (no Flutter/JDK/Android SDK locally; `dl.google.com` & `pub.dev` blocked; CI skipped per user instruction).
- **Thermal Printer Note:** ESC/POS `GS v 0` 58mm/80mm raster printing (`EscPosService` + `MainActivity.kt` SPP) has not been executed on a physical Bluetooth printer and requires real device hardware verification.
