# PROJECT AUDIT — QAT LEDGER (`ميزان القات`)

**Repository:** `mohmmedalhajj/QAT-LEDGER`  
**Audited Baseline Commit:** `516e118fc3c2dc476049c6de47b7cffe7b9fdc66` (`main`)  
**Pre-Change Backup Archive:** `backup/qat_ledger_kotlin_original_516e118.zip` (SHA-256: `ec5b2c7435467dcd2780ea4b427de041d8960e3a6eb3d93e2e64475d3b36c55c`)  
**Target Package ID:** `com.aistudio.tofanqat.nxzwmb` (developer variant `.dev`)  
**Legacy Room Database:** `tofan_al_aqsa_qat_db` (v18, 14 entities) + `qat_ledger_developer_registry.db` (v1, 4 entities)

---

## 1. Executive Summary

The original repository is a multi-module Android Kotlin / Jetpack Compose accounting application (`:app`, `:licensing-core`, `:developer-licensing`) built for Yemeni Qat wholesalers and agencies (`ميزان القات` / `QAT LEDGER`). While feature-rich and already containing an offline Ed25519 licensing engine (`licensing-core`) and a standalone developer issuance console (`developer-licensing`), a full line-by-line audit uncovered severe accounting precision hazards, currency-mixing bugs, missing relational constraints, repository logic inconsistencies, UI complexity/density issues, broken Arabic PDF font embedding (`cairo.ttf` contains zero Arabic glyphs), and unhardened backup/print pipelines.

This audit documents every module, screen, entity, defect, and architectural requirement driving the full migration to a clean **Flutter + Dart** codebase.

---

## 2. Original Module Structure

| Module | Purpose | Key Files |
|---|---|---|
| `:app` | Main Jetpack Compose UI, Room v18 persistence, `QatRepository`, `AppViewModel`, PDF/Bluetooth print/backup helpers, flavor `EditionCapabilities` | `MainActivity.kt`, `Entities.kt`, `Daos.kt`, `AppDatabase.kt`, `QatRepository.kt` (1,706 lines), `Helpers.kt` (3,124 lines), `AppViewModel.kt`, 11 screens in `ui/screens/` |
| `:licensing-core` | Pure-Kotlin/Android offline Ed25519 license verifier (`QL1.` compact + `.qlic` JSON), canonical payload serializer (`QAT_LEDGER_CANONICAL_PAYLOAD_V1`), `TrustedTimeGuard`, `InstallationIdProvider`, `LocalLicenseStore`, `QrCodeCodec` | `LicenseCanonical.kt`, `LicenseCrypto.kt`, `LicenseVerifier.kt`, `TrustedPublicKeyRegistry.kt`, `TrustedTimeGuard.kt`, `LocalLicenseStore.kt` |
| `:developer-licensing` | Isolated Android Keystore Ed25519 key generator/wrapper (`DeveloperKeyManager`), license signer (`LicenseSigner`), developer Room DB (`qat_ledger_developer_registry.db`), developer issuance/audit UI | `DeveloperKeyManager.kt`, `LicenseSigner.kt`, `DeveloperEntities.kt`, `DeveloperDatabase.kt`, `DeveloperRepository.kt`, `DeveloperConsoleDialog.kt` |

---

## 3. Complete Screen & Navigation Inventory

| Legacy File / Screen | Function & Features | Known UX / Functional Gaps |
|---|---|---|
| `SplashAndLogin.kt` | Animated splash, local PIN/password gate (default `admin` / `123456`), hidden 3-tap logo trigger (`login_logo_secret_trigger`) requiring passcode `780mohmmed` for Qat types & branding/developer access | Cluttered trigger behavior; hardcoded password checks in plain string comparisons; splash routing depends on preference flags spread across files |
| `AgencySetupScreen.kt` | Onboarding setup (agency name, phone, address, logo) | Minimal validation; writes directly into `SharedPreferences` (`qat_agency_prefs`) |
| `DashboardScreen.kt` | Daily summary cards (sales, purchases, profit, expenses, debt, net cash), quick actions | **Currency mixing:** converts SAR/YER via hardcoded `150.0` default preference; still contains dead `WavingPalestineFlag` composable |
| `InventoryScreen.kt` | Qat type definitions (`QatType`) + live batches/lots (`InventoryItem`), add/edit/delete inventory | Allows floating-point weight & price entry (`Double`); batch edits lack strict audit trail |
| `SalesScreen.kt` | POS invoice builder, multi-item cart, paid/partial/debt payment modes, thermal/PDF print, edit & delete invoice | Uses `"دفع جزئي"` while supplier edit checks `"دفعة جزئية"`; customer linking relies on `customerName` string matching if `customerId` is missing; `discount` and `taxPercent` fields exist on `Sale` entity & invoice builder but `makeSale` ignores them in debt/cash math! |
| `CustomerScreen.kt` | Customer ledger, add/edit/delete customer, receipt payments (`CustomerPayment`), import from phone contacts | `customers.totalDebt` is a single `Double` regardless of whether invoices were `YER` or `SAR`! Delete/recompute relies on `customerName` matching |
| `SupplierScreen.kt` | Supplier directory, multi-item purchases (`SupplierPurchase` + `SupplierPurchaseItem`), supplier payments (`SupplierPayment`), archive & edit | `suppliers.totalBalance` mixes currencies in one `Double`; `editSupplierPurchaseFull` checks `"دفعة جزئية"` instead of `"دفع جزئي"` and computes `newBuyPrice` on line 628 then ignores it when updating inventory! |
| `TransfersScreen.kt` | Money transfers (`MoneyTransfer`), currency & status filter (`جاري التسليم`, `تم التسليم`, `ملغي`), separate `TransferRecipient` directory & report dialog | Blank receiver creates `recipientId = 0` (broken orphan reference); `TransferRecipientReportDialog` omits `transfer.status` in displayed cards, summary text, and PDF; lacks Excel/CSV export and status breakdown per recipient |
| `ReportsScreen.kt` | Date-filtered sales, purchases, tax, profit, expense summaries + PDF export | Silently blends YER & SAR totals using a single preference exchange rate (`150.0`); missing Excel/CSV export |
| `StatementScreen.kt` | Unified account statement (customer/supplier chronological movements + running balance + print/share) | Running balance assumes single currency when a party has multi-currency entries; raw Android Canvas PDF |
| `ExpenseAndSettingsScreen.kt` | Hosts 4 screens (`ExpensesScreen`, `AccountsScreen`, `ArchiveScreen`, `SettingsScreen`), section visibility toggles, currency settings, theme picker, backup/restore, Bluetooth printer, license card, hidden developer tab (`780mohmmed`) | `SettingsScreen` mixes dark-first tokens (`PalBlackDark`, `PalBlackNormal`); displays legacy `tofan_backups` fallback label; backup has no SHA-256 manifest, integrity check, or pre-restore rollback snapshot |

---

## 4. Database Schema Audit (Room v18)

Database file: `tofan_al_aqsa_qat_db` (SQLite user_version = `18`, exported via Room migrations `9->10` through `17->18`).

### 4.1 All 14 Legacy Entities
1. `qat_types` (`id`, `name`, `defaultBuyPrice`, `defaultSellPrice`, `notes`, `isHidden`)
2. `inventory` (`id`, `qatType`, `supplierName`, `remainingQty`, `unit`, `buyPrice`, `sellPrice`, `purchaseDate`, `currency`)
3. `customers` (`id`, `name`, `phone`, `address`, `notes`, `totalDebt`)
4. `sales` (`id`, `customerName`, `customerId`, `date`, `totalAmount`, `paidAmount`, `remainingAmount`, `paymentType`, `status`, `notes`, `currency`, `discount`, `taxPercent`, `isArchived`)
5. `sales_invoice_items` (`id`, `saleId`, `qatType`, `quantity`, `unit`, `unitPrice`, `buyPrice`, `total`, `currency`)
6. `customer_payments` (`id`, `customerId`, `customerName`, `amount`, `date`, `notes`, `currency`, `paymentMethod`, `isArchived`)
7. `suppliers` (`id`, `name`, `phone`, `address`, `notes`, `totalBalance`)
8. `supplier_purchases` (`id`, `supplierId`, `supplierName`, `date`, `totalAmount`, `paidAmount`, `remainingAmount`, `paymentType`, `notes`, `currency`, `discount`, `taxPercent`, `isArchived`)
9. `supplier_purchase_items` (`id`, `purchaseId`, `qatType`, `quantity`, `unit`, `buyPrice`, `total`, `currency`)
10. `supplier_payments` (`id`, `supplierId`, `supplierName`, `amount`, `date`, `notes`, `currency`, `paymentMethod`, `isArchived`)
11. `expenses` (`id`, `description`, `category`, `amount`, `date`, `notes`, `currency`, `isArchived`)
12. `transfer_recipients` (`id`, `name`, `phone`, `company`, `notes`, `createdAt`)
13. `money_transfers` (`id`, `senderName`, `receiverName`, `recipientId`, `amount`, `currency`, `fee`, `transferCompany`, `transferNumber`, `date`, `status`, `notes`, `isArchived`)
14. `qat_tax_entries` (`id`, `date`, `totalQty`, `taxRate`, `totalTaxAmount`, `notes`, `source`, `isArchived`)

### 4.2 Critical Schema Defects
- **Zero Relational Integrity:** None of the 14 Room tables declare `@ForeignKey` constraints (`sales_invoice_items.saleId`, `customer_payments.customerId`, `supplier_purchases.supplierId`, `supplier_purchase_items.purchaseId`, `supplier_payments.supplierId`, `money_transfers.recipientId`). Deleting a parent record in raw SQL leaves orphans unless repository code manually cleans up by `name` or `id`.
- **Floating-Point Money (`REAL` / `Double`):** Every price, quantity, discount, fee, tax, payment, and debt column uses IEEE-754 `REAL` (`Double`), introducing cumulative rounding drift across multi-item invoices, partial payments, and running balances.
- **Unscoped Party Balances:** `customers.totalDebt` and `suppliers.totalBalance` are single scalar `Double` values with no currency code, guaranteeing silent corruption whenever a customer/supplier transacts in both `YER` and `SAR`.
- **Missing Currency on Tax Ledger:** `qat_tax_entries` has no `currency` column (implicitly assumed YER).
- **Synthetic `recipientId = 0`:** `MIGRATION_17_18` and `QatRepository.addTransfer` allow `recipientId = 0` when receiver text is blank or unmatched, preventing real FK enforcement unless converted to nullable `NULL` (`ON DELETE SET NULL`).
- **Developer DB (`qat_ledger_developer_registry.db` v1):** Contains `license_plans` (`id`, `code`, `name`, `durationDays`, `priceAmount`, `currency`, `maxDevices`, `isActive`), `dev_customers` (`id`, `fullName`, `businessName`, `phone`, `notes`, `createdAt`), `issued_licenses` (`id`, `licenseId`, `planCode`, `customerId`, `installationId`, `licenseeName`, `businessName`, `issuedAtEpochMs`, `expiresAtEpochMs`, `maxDevices`, `featuresCsv`, `tokenCompact`, `qlicJson`, `keyId`, `status`, `notes`), and `developer_audit_logs` (`id`, `action`, `actor`, `targetId`, `details`, `createdAtEpochMs`).

---

## 5. Critical Accounting, Security & UX Findings

1. **Money Math & Currency Mixing (`CurrencySystem.kt`, `AppViewModel.kt`, `DashboardScreen.kt`, `ReportsScreen.kt`):**
   - `DEFAULT_EXCHANGE_RATE = 150.0` (SAR->YER) is hardcoded and baked into prefs; conversions are performed silently in UI cards without separating `YER` vs `SAR` ledger buckets.
   - **Flutter Remedy:** Store all money in integer minor units (`amountMinor`, 1/100 of currency unit, e.g., `15000` for `150.00`), quantities in integer scaled millis (`qtyMilli`, 1/1000 of unit, e.g., `1500` for `1.500`), zero cross-currency auto-mixing in accounting totals (optional explicit display-only dual-currency KPI equivalent only when the user explicitly opts into Dual Currency Mode with a user-configured rate and clear conversion badge).

2. **Repository Logic Bugs (`QatRepository.kt`):**
   - **Partial Payment String Bug:** `makeSale` / `makeSupplierPurchase` check `"دفع جزئي"` while `editSupplierPurchaseFull` (line 554) checks `"دفعة جزئية"`, causing partial supplier purchase edits to miscalculate supplier balance!
   - **Ignored `newBuyPrice` in Edit:** `editSupplierPurchaseFull` computes `val newBuyPrice = ...` (line 628) and updates inventory quantity while leaving old `buyPrice` untouched.
   - **Ignored Invoice Discount & Tax:** `Sale` and `SupplierPurchase` carry `discount` and `taxPercent`, yet `makeSale` and `makeSupplierPurchase` calculate `remainingAmount = totalAmount - paidAmount` using raw item sum without applying `discount` and `taxPercent` consistently.
   - **Name-Based Linkage:** `deleteSale` / customer reconciliation matches `customerName` strings in places where `customerId` is `null` or stale.

3. **Transfer Recipients & Reports (`TransfersScreen.kt`):**
   - `TransferRecipientReportDialog` hides `transfer.status` from recipient cards, text summaries, and PDF tables.
   - Blank receiver rows create `recipientId = 0` rather than nullable unlinked (`recipientId = NULL`) with honest UI flagging (`مستلم غير محدد / غير مرتبط`).
   - Missing recipient-status breakdown (Pending / Delivered / Cancelled totals per currency) and missing Excel (`.xlsx`) & UTF-8 BOM CSV exports.

4. **Embedded Font Defect (`app/src/main/res/font/cairo.ttf`):**
   - Binary `cmap` table inspection proved `cairo.ttf` (33 KB) contains **0 Arabic glyphs** (Latin ASCII + Latin-1 only). Android Compose fell back silently to system fonts, whereas any PDF engine embedding this file directly would fail to render Arabic!
   - **Flutter Remedy:** Bundle complete static `IBMPlexSansArabic` TTF family (`Regular`, `Medium`, `SemiBold`, `Bold` + `OFL.txt`) verified to include `U+0600..U+06FF`, Presentation Forms-A (`U+FB50..`), and Presentation Forms-B (`U+FE70..U+FEFC`) for flawless UI and RTL Arabic PDF rendering.

5. **Licensing Configuration & Isolation (`licensing-core`, `developer-licensing`, `gradle.properties`):**
   - `QAT_LICENSE_PUBLIC_KEYS=` is empty in `gradle.properties`, meaning release builds without configuring a key fail open/untrusted or require debug keys.
   - Canonical format `QAT_LEDGER_CANONICAL_PAYLOAD_V1`, `QL1.<b64u_json>.<b64u_sig>`, `.qlic` JSON envelope (`QAT_LEDGER_LICENSE_V1`), key fingerprint `QLK-<first 8 hex of SHA-256(spki)>`, `TrustedTimeGuard` monotonic clock rollback detection, and strict separation of `:developer-licensing` from Customer builds are solid and must be preserved 100% compatibly in Dart.

6. **Backup/Restore & Printing (`Helpers.kt`):**
   - `backupDatabase` exports a raw SQLite copy without `VACUUM INTO` / WAL checkpoint, SHA-256 checksum manifest, or atomic restore verification.
   - `BluetoothPrinterManager` sends raw UTF-8 strings over RFCOMM (`UUID 00001101-0000-1000-8000-00805F9B34FB`), which many cheap thermal printers corrupt for Arabic unless shaped into CP1256/CP864 or rendered as ESC/POS raster bitmap (`GS v 0`).

7. **Branding & Legacy Artifacts:**
   - Dead `WavingPalestineFlag` composable in `DashboardScreen.kt`, `stripFlags` helper, `tofan_backups` folder label in `SettingsScreen.kt`, `#173B2F` hardcoded `TaskDescription` color in `LauncherIdentityManager.kt`, unused `purple_200`/`teal_200` in `res/values/colors.xml`, and `tofan-al-aqsa` package/README residue.
   - Note: `ic_qat_ledger_icon.png` is already a clean emerald leaf + balance scale emblem, which we upgrade to a crisp adaptive icon (`mipmap-anydpi-v26` with monochrome Android 13+ layer + multi-density PNGs + 1024x1024 store icon).

---

## 6. Architectural Decisions for Flutter Overhaul

1. **Exact Integer Money & Quantities:** All monetary columns stored as `INTEGER` (`*_minor`), quantities as `INTEGER` (`*_milli`), percentages in basis points (`*_bps`), zero `REAL` columns for financial state.
2. **Normalized Multi-Currency Ledger:** Per-currency totals & `party_currency_balances` views/tables keyed by `(party_type, party_id, currency_code)`; currency modes (`YER_ONLY`, `SAR_ONLY`, `DUAL_CURRENCY`) managed by one central `CurrencyPolicyEngine`.
3. **5-Section Consolidated UX:** Consolidate 11 fragmented screens into 5 clean RTL bottom-nav sections with segmented sub-tabs, Yemeni-soft-inspired Enterprise Light & Pure Black OLED Dark themes.
4. **Full Offline Compatibility:** Preserve `com.aistudio.tofanqat.nxzwmb`, preserve `QL1.` / `.qlic` Ed25519 compatibility, preserve hidden `780mohmmed` trigger (only exposing Developer Console when built in `developer` flavor!), and provide an idempotent Room v18 -> Flutter SQLite v2 migrator + atomic `.qbak` backup/restore.
