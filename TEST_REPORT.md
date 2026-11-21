# TEST & VERIFICATION REPORT — QAT LEDGER (`ميزان القات`)

**Repository:** `mohmmedalhajj/QAT-LEDGER`  
**Branch:** `arena/e82ef661-qat-ledger` (branched from `516e118fc3c2dc476049c6de47b7cffe7b9fdc66`)  
**Verified CI Run:** `44039066878` (commit `c7a24e142e58d5ae26f1b001482453afe6b6b1be`)  
**Date:** 2026-11-17  

---

## 1. Summary Matrix (Honest Execution Status)

| Stage / Check | Executed? | Status | Evidence / Notes |
|---|---|---|---|
| **Pre-Change Kotlin Backup (`backup/qat_ledger_kotlin_original_516e118.zip`)** | YES | **PASS** | 103 tracked files (`787 KB`), `unzip -t` zero errors, SHA-256 `ec5b2c7435467dcd2780ea4b427de041d8960e3a6eb3d93e2e64475d3b36c55c`. |
| **`flutter pub get` (Run `44039066878`, Step 7)** | YES (CI) | **PASS** | Resolved all dependencies cleanly on Flutter stable (`9s`). |
| **`flutter analyze` (Run `44039066878`, Step 8)** | YES (CI) | **PASS** | Zero errors/warnings/lints under strict `analysis_options.yaml` (`15s`). |
| **`flutter test -r expanded` — 8 test suites (Run `44039066878`, Step 9)** | YES (CI) | **PASS** | All 8 test files passed cleanly (`36s`). |
| **Release Signing Fallback (Run `44039066878`, Step 11)** | YES (CI) | **PASS (Debug Fallback)** | Secrets absent; automatically selected Debug signing (`android/app/build.gradle.kts`) with warning. |
| **Build Customer Release APK (Run `44039066878`, Step 12)** | YES (CI) | **PASS** | Built `app-customer-release.apk` (`com.aistudio.tofanqat.nxzwmb`, `5m 38s`). |
| **Build Developer Release APK (Run `44039066878`, Step 13)** | YES (CI) | **PASS** | Built `app-developer-release.apk` (`com.aistudio.tofanqat.nxzwmb.dev`, `1m 16s`). |
| **Verify APK Manifests, Package IDs & Signatures (Run `44039066878`, Step 14)** | YES (CI) | **PASS** | `aapt2 dump badging`, `apksigner verify --print-certs --verbose`, and `sha256sum` succeeded (`1s`). |
| **GitHub Artifacts Upload (`qat-ledger-release-apks`, Step 15)** | YES (CI) | **PASS** | Uploaded `qat-ledger-release-apks` (ID `14308807706`, `70,998,074` bytes, ZIP SHA-256 `e8b519c1e1d4e2050dfb518c2e77c70a0f4caf149eac8c0f0fdd3faead341cac`). |
| **Android Emulator Smoke Test (Run `44039066878`, Step 16)** | YES (CI) | **FAIL** | Emulator process exited early (`32s`); APK runtime launch on emulator was **NOT** completed (`qat-ledger-emulator-smoke` `639 bytes` uploaded in Step 17). |
| **Real User Room v18 DB Migration (`tofan_al_aqsa_qat_db`)** | **NO** | **NOT RUN** | Real user device DB not provided; only synthetic Room v18 migration tested in `test/room_migration_test.dart`. |
| **Physical Bluetooth ESC/POS Printer (58mm/80mm)** | **NO** | **NOT RUN** | Requires physical paired Bluetooth printer; packet encoding verified in `test/export_and_escpos_test.dart`. |

---

## 2. Dart Unit & Widget Test Suite (`test/`) — Passed in Run `44039066878`

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
