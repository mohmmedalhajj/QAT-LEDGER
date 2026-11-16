# TEST & VERIFICATION REPORT — QAT LEDGER (`ميزان القات`)

**Repository:** `mohmmedalhajj/QAT-LEDGER`  
**Branch:** `arena/e82ef661-qat-ledger` (branched from `516e118fc3c2dc476049c6de47b7cffe7b9fdc66`)  
**Verification Environment:** Debian GNU/Linux 12 (`x86_64`), Python 3.11.2 (`sqlite3 3.40.1`, `cryptography 50.0.2`, `Pillow 13.0.0`), `zip`/`unzip`  
**Date:** 2026-11-16  

---

## 1. Summary Matrix (Honest Execution Status)

| Stage / Check | Executed? | Status | Evidence / Notes |
|---|---|---|---|
| **Pre-Change Kotlin Backup (`backup/qat_ledger_kotlin_original_516e118.zip`)** | YES | **PASS** | 103 tracked files (`787 KB`), `unzip -t` zero errors, SHA-256 `ec5b2c7435467dcd2780ea4b427de041d8960e3a6eb3d93e2e64475d3b36c55c` verified via `sha256sum -c`. |
| **SQLite v2 DDL (20 tables, 12 indexes) & Synthetic Room v18 Migration** | YES (Python `sqlite3`) | **PASS (Synthetic Only)** | Extracted exact DDL from `lib/data/schema.dart`; verified `PRAGMA integrity_check == 'ok'`, `PRAGMA foreign_key_check == []`, YER/SAR separation (`1500000` / `25050`), nullable unlinked `recipient_id`. **Does NOT replace testing on user's real device DB** (not provided yet). |
| **Ed25519 Canonical Payload (`QL1.` & `.qlic`) & KeyID (`QLK-646D6BE4`)** | YES (Python `cryptography`) | **PASS** | Verified 44-byte X.509 SPKI DER header `302a300506032b6570032100`, deterministic `QLK-` ID, canonical JSON serialization, 64-byte signature verify. |
| **Font `cmap` Arabic + Presentation Forms-B Coverage** | YES (Python binary parser) | **PASS** | `IBMPlexSansArabic-{Regular,Medium,SemiBold,Bold}.ttf` contain `U+0627` & `U+FEFB` Presentation Forms-B glyphs (unlike legacy Latin-only `cairo.ttf`). |
| **Dart Source Structural Audit (43 `.dart` files)** | YES (Python lexer & resolver) | **PASS** | Balanced delimiters (`{}`, `()`, `[]`, string `${...}`), all relative & `package:` imports match `pubspec.yaml`, zero hardcoded `150` FX rate, zero embedded private keys, zero `.firstOrNull` hazards. |
| **Android XML & Adaptive Icon Asset Audit** | YES (Python XML/PIL) | **PASS** | 13 Android XML files parsed cleanly; mipmap PNGs (`48..192 px`) and adaptive foreground/monochrome (`432x432 px`) verified. |
| **`flutter pub get`** | **NO** | **NOT RUN** | Flutter/Dart SDK is unavailable in sandbox; `pub.dev` and `storage.googleapis.com` are blocked by sandbox network rules; GitHub Actions CI was skipped per user selection (`skip_ci_report_only`). |
| **`flutter analyze`** | **NO** | **NOT RUN** | Requires Dart/Flutter SDK (`flutter` analyzer). |
| **`flutter test -r expanded` (8 Dart test files)** | **NO** | **NOT RUN** | Requires Flutter test runner + Dart VM (`money_and_invoice_test.dart`, `room_migration_test.dart`, `repository_accounting_test.dart`, `licensing_ed25519_test.dart`, `developer_licensing_e2e_test.dart`, `arabic_pdf_shaper_test.dart`, `export_and_escpos_test.dart`, `widget_shell_test.dart`). |
| **`flutter build apk --release` (`customer` & `developer`)** | **NO** | **NOT RUN** | Requires Flutter SDK, JDK 17, Android SDK 35, Gradle repos (`dl.google.com` blocked locally), GitHub Actions `.github/workflows/` permission on token, and owner's `.jks` keystore (`have_keystore` pending owner configuration). **No `.apk` binary was built.** |
| **Real User Room v18 DB Migration (`tofan_al_aqsa_qat_db`)** | **NO** | **NOT RUN** | Real device DB was not provided (`no_real_db`). Must be verified on actual user backup/DB before live production migration. |
| **Physical Bluetooth ESC/POS Printer (58mm/80mm)** | **NO** | **NOT RUN** | Requires real paired thermal printer + Android hardware; ESC/POS `GS v 0` packet encoding only verified at code/unit-test level (`test/export_and_escpos_test.dart`, unexecuted without Flutter). |

---

## 2. Dart Unit & Widget Test Suite Ready to Run (`test/`)

| Test File | Coverage |
|---|---|
| `test/money_and_invoice_test.dart` | Integer minor (`1/100`) & milli (`1/1000`) parsing (ASCII + Arabic-Indic digits), invoice discount + bps tax math, strict YER/SAR non-mixing policy |
| `test/room_migration_test.dart` | Room v18 -> SQLite v2 migration, `qat_types`, `inventory_batches`, `sales`, `customers`, `suppliers`, `money_transfers` (`recipientId=0 -> NULL`), idempotency, balance reconciliation |
| `test/repository_accounting_test.dart` | `addPurchase`, `editSupplierPurchaseFull` (`buyPriceMinor` + `sellPriceMinor` + `discountMinor` + balance sync), `makeSale`, `editSale`, `deleteSale`, `getInventoryValuation`, `linkTransferRecipient`, period recipient reports, expenses/taxes, archive/restore, atomic `.qbak` backup/restore |
| `test/licensing_ed25519_test.dart` | Ed25519 SPKI key registry, `QL1.` + `.qlic` verify, tampered signature rejection, wrong installation ID rejection, clock rollback detection |
| `test/developer_licensing_e2e_test.dart` | `DeveloperKeyManager` + `DeveloperLicenseSigner` -> `DeveloperDatabaseService` (`issued_licenses`, `developer_audit_logs`) -> `OfflineLicenseVerifier` -> non-destructive behavior on license expiry |
| `test/arabic_pdf_shaper_test.dart` | Arabic letter shaping to Presentation Forms-B (`U+FE70..`), Lam-Alef ligatures, LTR numbers/invoice code preservation |
| `test/export_and_escpos_test.dart` | `PdfExportService` (`%PDF-` output with `IBMPlexSansArabic`), `SpreadsheetExportService` (`.xlsx` ZIP workbook + UTF-8 BOM `.csv`), `EscPosService.encodeImageToEscPosRaster` (`58mm`/`384px` & `80mm`/`576px` `GS v 0` headers) |
| `test/widget_shell_test.dart` | RTL MaterialApp across small (`360x640`) & large (`412x915`) phone viewports, hidden `"780mohmmed"` admin gate, multi-user role permissions (`cashier1` restrictions), Customer flavor isolation of Developer Console, Enterprise Light + OLED Pure Black Dark switching |

---

## 3. Exact Unexecuted Causes & Next Required Actions

1. **Why Flutter & APK build did not execute automatically:**
   - Sandbox lacks Flutter SDK, Dart VM, Java JDK, and Android SDK, and blocks outbound access to `pub.dev`, `storage.googleapis.com`, `dl.google.com`, and `repo.maven.apache.org`.
   - The sandbox GitHub App installation token (`contents: write`, `actions: read`) was rejected by GitHub when pushing `.github/workflows/flutter_build.yml` (`refusing to allow a GitHub App to create or update workflow without 'workflows' permission`), and the user chose `skip_ci_report_only` for now.
2. **To run full verification & produce final signed APKs:**
   - Copy `docs/ci/flutter_build.yml` to `.github/workflows/flutter_build.yml` on GitHub (or run locally with Flutter stable + JDK 17 + Android SDK 35).
   - Provide your original release keystore via `android/key.properties` (locally) or GitHub Secrets (`KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) and your Ed25519 SPKI public key (`QAT_LICENSE_PUBLIC_KEYS`).
   - Before deploying an update over existing customer phones, test migration on a real anonymized copy of `tofan_al_aqsa_qat_db` (`الإعدادات -> النسخ والطباعة -> استعادة نسخة`).
