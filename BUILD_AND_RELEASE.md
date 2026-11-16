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
| `customer` | `com.aistudio.tofanqat.nxzwmb` | Production end-user app; preserves upgrade path over existing Room v18 Android installs | **Excluded / Hidden** (`APP_EDITION=customer`) |
| `developer` | `com.aistudio.tofanqat.nxzwmb.dev` | Internal vendor/developer issuance build | **Enabled** (`APP_EDITION=developer`) |

---

## 3. Configuring Trusted Ed25519 Public Keys

Before building a Customer release APK:
1. Open the Developer Edition app -> `الإعدادات -> الترخيص والأمان -> وحدة تحكم المطور -> المفاتيح والتدقيق` and copy the **SPKI Base64** public key.
2. Supply it at build time via EITHER:
   - Dart define: `--dart-define=QAT_LICENSE_PUBLIC_KEYS="<KEY1_SPKI_B64>,<KEY2_SPKI_B64>"`
   - Or Gradle property (`android/gradle.properties` or `-PQAT_LICENSE_PUBLIC_KEYS="..."`)

Customer builds with an empty `QAT_LICENSE_PUBLIC_KEYS` safely report `untrustedKey` (`مفتاح غير موثوق`) when an activation token is entered instead of accepting untrusted keys.

---

## 4. Release Signing Configuration

`android/app/build.gradle.kts` currently uses debug signing defaults so local test builds succeed out of the box. For production Play Store / direct release APK distribution:
1. Generate an upload/release keystore with `keytool`.
2. Add release `signingConfigs` referencing `key.properties` (keep `.jks`/`.keystore` and passwords outside Git).
3. **Upgrade Compatibility Note:** To update existing Android installations in-place and preserve `/data/data/com.aistudio.tofanqat.nxzwmb/databases/tofan_al_aqsa_qat_db` automatically, sign with the same signing key as the original APK (or import the legacy `.db` / `.qbak` manually via `الإعدادات -> النسخ والطباعة -> استعادة نسخة`).

---

## 5. Build Commands

```bash
# 1. Fetch dependencies
flutter pub get

# 2. Static analysis & automated unit/widget/migration tests
flutter analyze
flutter test

# 3. Build Customer Release APK
flutter build apk --release --flavor customer \
  --dart-define=APP_EDITION=customer \
  --dart-define=QAT_LICENSE_PUBLIC_KEYS="<SPKI_BASE64>"

# 4. Build Developer Edition APK
flutter build apk --debug --flavor developer \
  --dart-define=APP_EDITION=developer
```

Output locations:
- Customer APK: `build/app/outputs/flutter-apk/app-customer-release.apk`
- Developer APK: `build/app/outputs/flutter-apk/app-developer-debug.apk`

---

## 6. Thermal Printer & Hardware Scope Notes
- Bluetooth thermal printing (`EscPosService` + `MainActivity.kt`) uses `Printing.raster(..., dpi: 203)` to rasterize Arabic 58mm/80mm receipts into monochrome ESC/POS `GS v 0` bitmap packets over RFCOMM SPP (`00001101-0000-1000-8000-00805F9B34FB`).
- This design avoids printer firmware CP864/CP1256 Arabic encoding defects, though actual hardware print verification requires a physical paired Bluetooth ESC/POS printer on an Android device (not testable in headless CI/sandbox).
