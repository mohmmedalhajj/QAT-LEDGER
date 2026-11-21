# FLUTTER ARCHITECTURE — QAT LEDGER (`ميزان القات`)

## 1. Architectural Principles

1. **Feature-First Clean Layers:**
   - `lib/core/`: Design tokens, themes (Enterprise Light & Pure Black Dark), RTL/Arabic helpers, integer money math (`Money`, `Quantity`, `BasisPoints`), `CurrencyPolicyEngine`, date utilities, error types.
   - `lib/data/`: SQLite engine (`sqflite`), DDL schema (`qat_ledger_v2.db`), Room v18 idempotent migrator, atomic `.qbak` backup/restore service, typed repositories.
   - `lib/licensing/`: Pure-Dart offline Ed25519 verifier, `QL1.`/`QAT_LEDGER_LICENSE_V1` `.qlic` codec, `QAT_LEDGER_CANONICAL_PAYLOAD_V1` canonicalizer, `TrustedPublicKeyRegistry`, `TrustedTimeGuard`, `LocalLicenseStore`, `EditionCapabilities`.
   - `lib/developer/`: Developer-edition-only key manager, Ed25519 signer, plan/customer/issued-license registry DB (`qat_ledger_developer_registry_v2.db`), and Developer Console UI — strictly excluded from Customer edition via `EditionConfig` & flavor gating.
   - `lib/services/`: Arabic RTL PDF generator (`package:pdf` + `arabic_reshaper`/`bidi` + embedded `IBMPlexSansArabic`), Excel (`.xlsx`) & UTF-8 BOM CSV exporter, WhatsApp/SMS invoice formatter, Bluetooth ESC/POS raster/text printer bridge (Android platform channel).
   - `lib/features/`: 5-section navigation shell + splash/login/onboarding + segmented feature workspaces.

---

## 2. 5-Section Navigation Shell (100% RTL Arabic)

| # | Section Tab | Integrated Workspaces | Legacy Screens Replaced |
|---|---|---|---|
| 1 | **الرئيسية** (`Dashboard`) | Currency-safe KPI summary, net cash/debt/profit per currency, quick POS/Expense/Transfer actions | `DashboardScreen` |
| 2 | **المبيعات والعملاء** (`Sales & Customers`) | Segmented tabs: `[فاتورة جديدة / POS]`, `[سجل المبيعات]`, `[العملاء والديون]`, `[كشف الحساب]` | `SalesScreen`, `CustomerScreen`, `StatementScreen` |
| 3 | **المخزون والموردون** (`Inventory & Suppliers`) | Segmented tabs: `[المخزون والدفعات]`, `[أصناف القات]`, `[الموردون والمشتريات]`, `[مدفوعات الموردين]` | `InventoryScreen`, `SupplierScreen` |
| 4 | **الحوالات والتقارير** (`Transfers & Reports`) | Segmented tabs: `[سجل الحوالات]`, `[المستلمون وتقارير الحالة]`, `[التقارير المالية]`, `[الضرائب]`, `[الأرشيف]` | `TransfersScreen`, `TransferRecipientReportDialog`, `ReportsScreen`, `ArchiveScreen` |
| 5 | **المصاريف والإعدادات** (`Expenses & Settings`) | Segmented tabs: `[المصاريف]`, `[الحسابات والتصنيفات]`, `[العملة والهوية والمظهر]`, `[النسخ الاحتياطي والطباعة]`, `[الترخيص]` (+ hidden `780mohmmed` trigger in Developer flavor) | `ExpensesScreen`, `AccountsScreen`, `SettingsScreen` |

---

## 3. Visual Identity & Theme Tokens

- **Typography:** `IBMPlexSansArabic` static TTF family (`Regular`, `Medium`, `SemiBold`, `Bold`), bundled under `assets/fonts/` with full Arabic + Presentation Forms + tabular Latin numerals.
- **Yemeni-Soft Enterprise Light (`light_enterprise`):**
  - Canvas: `#F6F4EF` (warm parchment sand — zero harsh glare)
  - Card Surface: `#FFFFFF` with subtle `#E5DFD3` hairline border
  - Elevated / Sub-surface: `#EFECE4`
  - Primary Brand: `#144D3A` (deep Yemeni emerald)
  - Secondary / Brass Accent: `#B8862D` (traditional Yemeni brass gold)
  - Text Primary / Secondary: `#151B18` / `#58635D`
  - Semantic Status: Success `#157F4D`, Warning `#B56E14`, Error `#B92B27`
- **Pure Black OLED Dark (`dark_black`):**
  - Canvas: `#000000` (true pure black)
  - Card Surface: `#0D1110` with subtle `#1E2623` hairline border
  - Elevated Surface: `#161D1A`
  - Primary Brand: `#2BB673` (high-contrast emerald)
  - Secondary / Gold Accent: `#E0A94C`
  - Text Primary / Secondary: `#F3F4F2` / `#99A39E`
  - Semantic Status: Success `#34C77B`, Warning `#F0A33A`, Error `#EF5350`

---

## 4. Exact Money Math & Currency Policy Engine

- `Money(int minorUnits, CurrencyCode currency)` represents `amount = minorUnits / 100`.
- `Quantity(int milliUnits)` represents `qty = milliUnits / 1000`.
- Invoice calculation order (exact integer arithmetic with `roundHalfUp`):
  1. `lineTotalMinor = (qtyMilli * unitPriceMinor + 500) ~/ 1000`
  2. `subtotalMinor = sum(lineTotalMinor)`
  3. `discountMinor` clamped to `[0, subtotalMinor]`
  4. `taxableMinor = subtotalMinor - discountMinor`
  5. `taxAmountMinor = (taxableMinor * taxBps + 5000) ~/ 10000`
  6. `totalAmountMinor = taxableMinor + taxAmountMinor`
  7. `remainingAmountMinor = totalAmountMinor - paidAmountMinor`
- **Currency Modes (`CurrencyMode`):**
  - `yerOnly`: Strictly locks forms & KPIs to `YER`.
  - `sarOnly`: Strictly locks forms & KPIs to `SAR`.
  - `dualCurrency`: Permits choosing `YER` or `SAR` per invoice/purchase/expense/transfer, keeping ledgers **strictly separated by currency** in all totals, statements, and balances. Optional converted equivalent card only displays if user explicitly supplies a conversion rate (`sarToYerRateMilli > 0`) and is marked `"عرض تقديري فقط — الدفاتر مفصولة حسب العملة"`.

---

## 5. Offline Licensing & Edition Isolation

- **Canonical Compatibility:** Uses exact Kotlin canonical string format:
  - `QAT_LEDGER_CANONICAL_PAYLOAD_V1`
  - Key order: `format`, `licenseId`, `product`, `edition`, `customerName`, `businessName`, `planCode`, `issuedAtEpochMs`, `expiresAtEpochMs`, `installationId`, `maxDevices`, `features`
  - Signed with Ed25519 (`package:cryptography`) and encoded as `QL1.<base64url_payload>.<base64url_signature>` or JSON `.qlic` (`QAT_LEDGER_LICENSE_V1`).
- **Build Flavors:**
  - `customer` flavor (`com.aistudio.tofanqat.nxzwmb`): `APP_EDITION=customer`, verifies licenses against `QAT_LICENSE_PUBLIC_KEYS` (`--dart-define` / Gradle `BuildConfig`), hides all Developer Console UI & issuance logic.
  - `developer` flavor (`com.aistudio.tofanqat.nxzwmb.dev`): `APP_EDITION=developer`, unlocks Developer Key Manager, License Signer, Plans, Customers, Issued Registry, and Audit Log behind the `780mohmmed` gate.

---

## 6. Native Android Bridges (`MethodChannel`)

Minimal, focused Kotlin platform plugin inside `android/app/src/main/kotlin/com/aistudio/tofanqat/nxzwmb/`:
1. `LegacyMigrationBridge`: Exposes Android `databases/tofan_al_aqsa_qat_db` path and reads legacy `SharedPreferences` XML keys (`qat_agency_prefs`, `app_theme_prefs`, `qat_ledger_license_store`, `qat_ledger_developer_store`) cleanly on first launch.
2. `SecureKeystoreBridge`: Hardware/Keystore-backed AES-GCM wrapping for local Ed25519 seed (Developer edition) and license installation salt.
3. `BluetoothThermalBridge`: Scans bonded Bluetooth thermal printers and transmits rendered ESC/POS raster bitmap (`GS v 0`) bytes over RFCOMM SPP (`00001101-0000-1000-8000-00805F9B34FB`) so Arabic receipts print identically on any 58mm/80mm printer regardless of printer codepage!
