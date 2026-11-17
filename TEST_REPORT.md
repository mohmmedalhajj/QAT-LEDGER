# TEST & VERIFICATION REPORT — QAT LEDGER (`ميزان القات`)

**Repository:** `mohmmedalhajj/QAT-LEDGER`  
**Branch:** `arena/e82ef661-qat-ledger` (branched from `516e118fc3c2dc476049c6de47b7cffe7b9fdc66`)  
**Last Verified GitHub Actions Run:** `44020629330` (commit `7c3c3f631f24ff929d6821d35401842fe069067d`)  
**Date:** 2026-11-17  

---

## 1. Summary Matrix (Honest Execution Status)

| Stage / Check | Executed? | Status | Evidence / Notes |
|---|---|---|---|
| **Pre-Change Kotlin Backup (`backup/qat_ledger_kotlin_original_516e118.zip`)** | YES | **PASS** | 103 tracked files (`787 KB`), `unzip -t` zero errors, SHA-256 `ec5b2c7435467dcd2780ea4b427de041d8960e3a6eb3d93e2e64475d3b36c55c`. |
| **`flutter pub get` (CI Run `44020629330`, Step 7)** | YES (CI) | **PASS** | Resolved all dependencies cleanly on Flutter stable (`8s`). |
| **`flutter analyze` (CI Run `44020629330`, Step 8)** | YES (CI) | **PASS** | Zero errors/warnings/lints under strict `analysis_options.yaml` (`12s`). |
| **`flutter test -r expanded` — 8 test suites (CI Run `44020629330`, Step 9)** | YES (CI) | **PASS** | All 8 test files passed cleanly (`30s`). |
| **Build Customer Release APK (CI Run `44020629330`, Step 12)** | YES (CI) | **PASS** | Built `app-customer-release.apk` (`com.aistudio.tofanqat.nxzwmb`, `4m 51s`). |
| **Build Developer Release APK (CI Run `44020629330`, Step 13)** | YES (CI) | **PASS** | Built `app-developer-release.apk` (`com.aistudio.tofanqat.nxzwmb.dev`, `59s`). |
| **Verify APK manifests, package IDs, signatures & SHA-256 (CI Run `44020629330`, Step 14)** | YES (CI) | **PASS** | Verified `aapt2 dump badging`, `apksigner verify --print-certs --verbose`, and `sha256sum` into `ci-dist/` (`2s`). |
| **Android Emulator Smoke Test (CI Run `44020629330`, Step 15)** | **TIMED OUT** | **FAIL / HUNG** | Started `05:04:19Z` and hung indefinitely (`adb wait-for-device` without timeout when `/dev/kvm` existed) until job hit `timeout-minutes: 45` (`cancelled` at `05:51:56Z`). |
| **GitHub Artifacts Upload (`qat-ledger-release-apks`, Step 16)** | **NOT RUN** | **MISSING (0 artifacts)** | Step 16 remained `pending` because Step 15 hang caused runner kill before upload. `gh api .../artifacts` returns `total_count: 0`. |
| **Real User Room v18 DB Migration (`tofan_al_aqsa_qat_db`)** | **NO** | **NOT RUN** | Real user device DB not provided yet; only synthetic Room v18 migration tested in `test/room_migration_test.dart`. |
| **Physical Bluetooth ESC/POS Printer (58mm/80mm)** | **NO** | **NOT RUN** | Requires physical paired Bluetooth printer; packet encoding only verified in `test/export_and_escpos_test.dart`. |

---

## 2. Dart Unit & Widget Test Suite (`test/`) — Passed in Run `44020629330`

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

## 3. Root Cause of Step 15 Hang & Remaining Blocker

1. **Why APKs are missing from GitHub Artifacts (`total_count: 0`):**
   - In `.github/workflows/flutter_build.yml`, `Upload release APKs and verification reports` (Step 16) was scheduled **after** `Smoke-test Customer & Developer APKs on Android Emulator` (Step 15).
   - Because `/dev/kvm` existed on `ubuntu-latest`, Step 15 attempted to boot the emulator in background (`nohup ... &`) and waited indefinitely on unbounded `adb wait-for-device` with no step timeout and no `kill -0 "$EMU_PID"` guard, exhausting the 45-minute job limit before Step 16 could upload `ci-dist/`.
2. **Fix Prepared:**
   - Updated `docs/ci/flutter_build.yml` and `.github/workflows/flutter_build.yml` to upload `qat-ledger-release-apks` immediately after Step 14, and bound/harden the emulator smoke test (`timeout-minutes: 10`, `sudo chmod a+rw /dev/kvm || true`, `emulator -accel-check`, `-no-snapshot -no-metrics`, `EMU_PID` liveness checks, 180s bounded wait, clean `adb emu kill` + `adb kill-server` teardown, separate `qat-ledger-emulator-smoke` upload).
   - Cleaned temporary `LoggingOutputInternal` / `gradle.buildFinished` diagnostic hook from `android/settings.gradle.kts`.
3. **Delivery Blockers Remaining:**
   - GitHub App token (`GH_TOKEN`) lacks `workflows` permission to push `.github/workflows/flutter_build.yml` directly (must be updated via GitHub Web UI or a token with `workflows` scope from `docs/ci/flutter_build.yml`), after which a clean CI run will produce and retain `QAT_LEDGER_CUSTOMER_RELEASE.apk` and `QAT_LEDGER_DEVELOPER_RELEASE.apk` artifacts.
   - Real device migration test (`tofan_al_aqsa_qat_db`) and physical Bluetooth printer test remain unexecuted outside real hardware.
