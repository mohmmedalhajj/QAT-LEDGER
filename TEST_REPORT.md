# TEST & VERIFICATION REPORT — QAT LEDGER (`ميزان القات`)

**Repository:** `mohmmedalhajj/QAT-LEDGER`  
**Branch:** `arena/e82ef661-qat-ledger` (branched from `516e118fc3c2dc476049c6de47b7cffe7b9fdc66`)  
**Latest Verified CI Run:** `44037519961` (commit `3527b42e5ba4677dfc12c43240e0cb790af0bcb4`)  
**Previous APK Build Verification Run:** `44020629330` (commit `7c3c3f631f24ff929d6821d35401842fe069067d`)  
**Date:** 2026-11-17  

---

## 1. Summary Matrix (Honest Execution Status)

| Stage / Check | Executed? | Status | Evidence / Notes |
|---|---|---|---|
| **Pre-Change Kotlin Backup (`backup/qat_ledger_kotlin_original_516e118.zip`)** | YES | **PASS** | 103 tracked files (`787 KB`), `unzip -t` zero errors, SHA-256 `ec5b2c7435467dcd2780ea4b427de041d8960e3a6eb3d93e2e64475d3b36c55c`. |
| **`flutter pub get` (Run `44037519961`, Step 7)** | YES (CI) | **PASS** | Resolved all dependencies cleanly on Flutter stable. |
| **`flutter analyze` (Run `44037519961`, Step 8)** | YES (CI) | **PASS** | Zero errors/warnings/lints under strict `analysis_options.yaml`. |
| **`flutter test -r expanded` — 8 test suites (Run `44037519961`, Step 9)** | YES (CI) | **PASS** | All 8 test files passed cleanly. |
| **Configure original release signing key (Run `44037519961`, Step 11)** | YES (CI) | **FAIL** | `.github/workflows/flutter_build.yml` (`e737a59`) exits with code `1` when GitHub Secrets (`KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) are absent (`Release signing secrets are missing. Build stopped to prevent a Debug-signed release APK.`). |
| **Build Customer Release APK (Run `44037519961` vs `44020629330`)** | **SKIPPED in `44037519961`** / YES in `44020629330` | **BLOCKED BY STEP 11** | Skipped in `44037519961` due to Step 11 failure; passed in `44020629330` (`7c3c3f6`) before `e737a59` blocked debug fallback. |
| **Build Developer Release APK (Run `44037519961` vs `44020629330`)** | **SKIPPED in `44037519961`** / YES in `44020629330` | **BLOCKED BY STEP 11** | Skipped in `44037519961` due to Step 11 failure; passed in `44020629330` (`7c3c3f6`) before `e737a59` blocked debug fallback. |
| **GitHub Artifacts (`qat-ledger-release-apks`)** | **NOT UPLOADED** | **MISSING** | Only `qat-ledger-emulator-smoke` (`198 bytes`) was uploaded in `44037519961`; `ci-dist/` had no files because Steps 12–14 were skipped. |
| **Android Emulator Smoke Test (Run `44037519961`, Step 16)** | **NOT EXECUTED** | **NOT RUN** | Required APK files were missing (`NOT_EXECUTED`). |
| **Real User Room v18 DB Migration (`tofan_al_aqsa_qat_db`)** | **NO** | **NOT RUN** | Real user device DB not provided; only synthetic Room v18 migration tested in `test/room_migration_test.dart`. |
| **Physical Bluetooth ESC/POS Printer (58mm/80mm)** | **NO** | **NOT RUN** | Requires physical paired Bluetooth printer; packet encoding verified in `test/export_and_escpos_test.dart`. |

---

## 2. Dart Unit & Widget Test Suite (`test/`) — Passed in Run `44037519961`

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

## 3. Exact Blocker Preventing APK Artifact Delivery from This Sandbox

1. In commit `e737a59`, `.github/workflows/flutter_build.yml` on GitHub was updated to hard-fail Step 11 (`exit 1`) whenever `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` secrets are absent (`"Release signing secrets are missing. Build stopped to prevent a Debug-signed release APK."`).
2. `android/app/build.gradle.kts` (`3527b42`) and `docs/ci/flutter_build.yml` are configured to fall back cleanly to Debug signing when no keystore is present, but this sandbox cannot modify `.github/workflows/flutter_build.yml` (`HTTP 403: refusing to allow a GitHub App to create or update workflow '.github/workflows/flutter_build.yml' without 'workflows' permission`) and cannot read/write GitHub Secrets (`HTTP 403: Resource not accessible by integration`), nor compile Android APKs locally (no Flutter/JDK/Android SDK; `pub.dev`, `storage.googleapis.com`, `dl.google.com`, `services.gradle.org` blocked by sandbox egress policy).
