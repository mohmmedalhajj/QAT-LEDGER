# DATABASE MIGRATION — Room v18 (`tofan_al_aqsa_qat_db`) to Normalized Flutter SQLite v2

**Legacy Database File:** `tofan_al_aqsa_qat_db` (`user_version = 18`, Room schema)  
**Target Flutter Database File:** `qat_ledger_v2.db` (`PRAGMA user_version = 2`, `PRAGMA foreign_keys = ON`)  
**Developer Database File (Developer Flavor Only):** `qat_ledger_developer_registry_v2.db` (migrated from `qat_ledger_developer_registry.db`)

---

## 1. Core Financial Representation Rules

1. **Zero Floating-Point Monetary Storage:**
   - Every monetary value (`totalAmount`, `paidAmount`, `remainingAmount`, `unitPrice`, `buyPrice`, `sellPrice`, `fee`, `discount`, `taxAmount`, `amount`) is converted at migration time using exact decimal rounding (`roundHalfUp(realValue * 100)`) into **64-bit SQLite `INTEGER` minor units** (`*_minor`, where `1.00` = `100` minor units; e.g., `15,000.50 YER` = `1500050`).
   - Every quantity (`remainingQty`, `quantity`, `totalQty`) is converted into **64-bit SQLite `INTEGER` scaled millis** (`*_milli`, `roundHalfUp(realValue * 1000)`, supporting `0.001` precision for `ربطة` / `علاقة` / `كيلو` / `جرام`).
   - Percentages (`taxPercent`, `taxRate`) are stored in **basis points** (`*_bps`, `roundHalfUp(percent * 100)`, e.g., `5.0%` = `500 bps`).
2. **Normalized Currency Scope (`currency_code`):**
   - Valid ISO codes: `'YER'` and `'SAR'`. Legacy values (`null`, `''`, `'ريال يمني'`, `'يمني'`) normalize deterministically to `'YER'`; `'ريال سعودي'` / `'سعودي'` / `'SAR'` normalize to `'SAR'`.
   - **`customers.totalDebt` & `suppliers.totalBalance` are NO LONGER single-scalar ambiguous doubles:** Party balances are stored per `(party_id, currency_code)` in normalized balance tables (`customer_balances`, `supplier_balances`) and reconciled deterministically from ledger transactions (`sales`, `customer_payments`, `supplier_purchases`, `supplier_payments`) during migration! Any discrepancy between the legacy scalar `totalDebt`/`totalBalance` and the ledger sum is logged cleanly in `_migration_audit` without fabricating adjustment transactions.
3. **Strict Foreign Keys (`PRAGMA foreign_keys = ON`):**
   - `sales.customer_id` -> `customers(id) ON DELETE RESTRICT` (nullable only for explicit cash walk-in sales `عميل نقدي`; legacy unlinked sales with non-cash names resolve or create a reconciled customer record only when deterministic, otherwise remain walk-in/flagged).
   - `sales_invoice_items.sale_id` -> `sales(id) ON DELETE CASCADE`
   - `customer_payments.customer_id` -> `customers(id) ON DELETE RESTRICT`
   - `supplier_purchases.supplier_id` -> `suppliers(id) ON DELETE RESTRICT`
   - `supplier_purchase_items.purchase_id` -> `supplier_purchases(id) ON DELETE CASCADE`
   - `supplier_payments.supplier_id` -> `suppliers(id) ON DELETE RESTRICT`
   - `money_transfers.recipient_id` -> `transfer_recipients(id) ON DELETE SET NULL` (nullable! Legacy `recipientId = 0` or orphaned IDs become `NULL` — **never** inventing fake recipient rows!).
4. **Canonical Payment Status Normalization:**
   - Legacy `"دفعة جزئية"` and `"دفع جزئي"` are normalized cleanly to canonical code `'PARTIAL'` (`'PAID'` for `"نقداً"` / `"تم الدفع"`, `'CREDIT'` for `"آجل"` / `"دين"`, `'PARTIAL'` for partial). Display labels remain Arabic (`نقداً`, `دفع جزئي`, `آجل`).
   - Legacy transfer statuses normalize to `'PENDING'` (`جاري التسليم`), `'DELIVERED'` (`تم التسليم`), `'CANCELLED'` (`ملغي`).

---

## 2. Target Normalized SQLite Schema (`qat_ledger_v2.db`)

### 2.1 System & Configuration Tables
- **`_migration_history`** (`id INTEGER PRIMARY KEY`, `source_path TEXT`, `source_version INTEGER`, `migrated_at_ms INTEGER`, `status TEXT`, `rows_migrated_json TEXT`, `checksum_sha256 TEXT`)
- **`_migration_audit`** (`id INTEGER PRIMARY KEY`, `table_name TEXT`, `legacy_id INTEGER`, `severity TEXT`, `code TEXT`, `message TEXT`, `created_at_ms INTEGER`)
- **`app_settings`** (`key TEXT PRIMARY KEY`, `value TEXT NOT NULL`, `updated_at_ms INTEGER NOT NULL`) — stores currency mode (`YER_ONLY` / `SAR_ONLY` / `DUAL_CURRENCY`), optional user-entered display exchange rate (`SAR_TO_YER_RATE_MILLI`), agency profile (`agency_name`, `agency_phone`, `agency_address`, `agency_logo_path`, `footer_note`), theme (`light_enterprise` / `dark_black`), pin hash, and section flags.
- **`ledger_accounts`** (`id INTEGER PRIMARY KEY`, `code TEXT UNIQUE`, `name TEXT NOT NULL`, `category TEXT NOT NULL`, `is_system INTEGER NOT NULL DEFAULT 0`, `is_active INTEGER NOT NULL DEFAULT 1`) — supports expense categories and customizable accounting sections.

### 2.2 Master Entities & Inventory
- **`qat_types`** (`id INTEGER PRIMARY KEY AUTOINCREMENT`, `name TEXT NOT NULL UNIQUE`, `default_buy_price_minor INTEGER NOT NULL DEFAULT 0`, `default_sell_price_minor INTEGER NOT NULL DEFAULT 0`, `default_currency TEXT NOT NULL DEFAULT 'YER'`, `notes TEXT NOT NULL DEFAULT ''`, `is_hidden INTEGER NOT NULL DEFAULT 0`, `created_at_ms INTEGER NOT NULL`)
- **`inventory_batches`** (`id INTEGER PRIMARY KEY AUTOINCREMENT`, `qat_type_id INTEGER REFERENCES qat_types(id) ON DELETE RESTRICT`, `qat_type_name TEXT NOT NULL`, `supplier_id INTEGER REFERENCES suppliers(id) ON DELETE SET NULL`, `supplier_name TEXT NOT NULL DEFAULT ''`, `remaining_qty_milli INTEGER NOT NULL CHECK(remaining_qty_milli >= 0)`, `initial_qty_milli INTEGER NOT NULL DEFAULT 0`, `unit TEXT NOT NULL DEFAULT 'ربطة'`, `buy_price_minor INTEGER NOT NULL CHECK(buy_price_minor >= 0)`, `sell_price_minor INTEGER NOT NULL CHECK(sell_price_minor >= 0)`, `currency_code TEXT NOT NULL CHECK(currency_code IN ('YER','SAR'))`, `purchase_date_ms INTEGER NOT NULL`, `purchase_date_iso TEXT NOT NULL`, `is_archived INTEGER NOT NULL DEFAULT 0`)

### 2.3 Customers & Sales
- **`customers`** (`id INTEGER PRIMARY KEY AUTOINCREMENT`, `name TEXT NOT NULL`, `phone TEXT NOT NULL DEFAULT ''`, `address TEXT NOT NULL DEFAULT ''`, `notes TEXT NOT NULL DEFAULT ''`, `created_at_ms INTEGER NOT NULL`, `is_archived INTEGER NOT NULL DEFAULT 0`)
- **`customer_balances`** (`customer_id INTEGER NOT NULL REFERENCES customers(id) ON DELETE CASCADE`, `currency_code TEXT NOT NULL CHECK(currency_code IN ('YER','SAR'))`, `debt_minor INTEGER NOT NULL DEFAULT 0`, `updated_at_ms INTEGER NOT NULL`, `PRIMARY KEY (customer_id, currency_code)`)
- **`sales`** (`id INTEGER PRIMARY KEY AUTOINCREMENT`, `invoice_number TEXT NOT NULL UNIQUE`, `customer_id INTEGER REFERENCES customers(id) ON DELETE RESTRICT`, `customer_name TEXT NOT NULL`, `date_ms INTEGER NOT NULL`, `date_iso TEXT NOT NULL`, `currency_code TEXT NOT NULL CHECK(currency_code IN ('YER','SAR'))`, `subtotal_minor INTEGER NOT NULL`, `discount_minor INTEGER NOT NULL DEFAULT 0`, `tax_bps INTEGER NOT NULL DEFAULT 0`, `tax_amount_minor INTEGER NOT NULL DEFAULT 0`, `total_amount_minor INTEGER NOT NULL`, `paid_amount_minor INTEGER NOT NULL DEFAULT 0`, `remaining_amount_minor INTEGER NOT NULL DEFAULT 0`, `payment_type TEXT NOT NULL CHECK(payment_type IN ('PAID','PARTIAL','CREDIT'))`, `status TEXT NOT NULL`, `notes TEXT NOT NULL DEFAULT ''`, `is_archived INTEGER NOT NULL DEFAULT 0`)
- **`sales_invoice_items`** (`id INTEGER PRIMARY KEY AUTOINCREMENT`, `sale_id INTEGER NOT NULL REFERENCES sales(id) ON DELETE CASCADE`, `inventory_batch_id INTEGER REFERENCES inventory_batches(id) ON DELETE SET NULL`, `qat_type_name TEXT NOT NULL`, `qty_milli INTEGER NOT NULL CHECK(qty_milli > 0)`, `unit TEXT NOT NULL`, `unit_price_minor INTEGER NOT NULL`, `buy_price_minor INTEGER NOT NULL DEFAULT 0`, `line_total_minor INTEGER NOT NULL`, `currency_code TEXT NOT NULL CHECK(currency_code IN ('YER','SAR'))`)
- **`customer_payments`** (`id INTEGER PRIMARY KEY AUTOINCREMENT`, `customer_id INTEGER NOT NULL REFERENCES customers(id) ON DELETE RESTRICT`, `customer_name TEXT NOT NULL`, `amount_minor INTEGER NOT NULL CHECK(amount_minor > 0)`, `currency_code TEXT NOT NULL CHECK(currency_code IN ('YER','SAR'))`, `date_ms INTEGER NOT NULL`, `date_iso TEXT NOT NULL`, `payment_method TEXT NOT NULL DEFAULT 'نقدي'`, `notes TEXT NOT NULL DEFAULT ''`, `is_archived INTEGER NOT NULL DEFAULT 0`)

### 2.4 Suppliers & Purchases
- **`suppliers`** (`id INTEGER PRIMARY KEY AUTOINCREMENT`, `name TEXT NOT NULL`, `phone TEXT NOT NULL DEFAULT ''`, `address TEXT NOT NULL DEFAULT ''`, `notes TEXT NOT NULL DEFAULT ''`, `created_at_ms INTEGER NOT NULL`, `is_archived INTEGER NOT NULL DEFAULT 0`)
- **`supplier_balances`** (`supplier_id INTEGER NOT NULL REFERENCES suppliers(id) ON DELETE CASCADE`, `currency_code TEXT NOT NULL CHECK(currency_code IN ('YER','SAR'))`, `balance_minor INTEGER NOT NULL DEFAULT 0`, `updated_at_ms INTEGER NOT NULL`, `PRIMARY KEY (supplier_id, currency_code)`)
- **`supplier_purchases`** (`id INTEGER PRIMARY KEY AUTOINCREMENT`, `purchase_number TEXT NOT NULL UNIQUE`, `supplier_id INTEGER NOT NULL REFERENCES suppliers(id) ON DELETE RESTRICT`, `supplier_name TEXT NOT NULL`, `date_ms INTEGER NOT NULL`, `date_iso TEXT NOT NULL`, `currency_code TEXT NOT NULL CHECK(currency_code IN ('YER','SAR'))`, `subtotal_minor INTEGER NOT NULL`, `discount_minor INTEGER NOT NULL DEFAULT 0`, `tax_bps INTEGER NOT NULL DEFAULT 0`, `tax_amount_minor INTEGER NOT NULL DEFAULT 0`, `total_amount_minor INTEGER NOT NULL`, `paid_amount_minor INTEGER NOT NULL DEFAULT 0`, `remaining_amount_minor INTEGER NOT NULL DEFAULT 0`, `payment_type TEXT NOT NULL CHECK(payment_type IN ('PAID','PARTIAL','CREDIT'))`, `notes TEXT NOT NULL DEFAULT ''`, `is_archived INTEGER NOT NULL DEFAULT 0`)
- **`supplier_purchase_items`** (`id INTEGER PRIMARY KEY AUTOINCREMENT`, `purchase_id INTEGER NOT NULL REFERENCES supplier_purchases(id) ON DELETE CASCADE`, `qat_type_name TEXT NOT NULL`, `qty_milli INTEGER NOT NULL CHECK(qty_milli > 0)`, `unit TEXT NOT NULL`, `buy_price_minor INTEGER NOT NULL`, `line_total_minor INTEGER NOT NULL`, `currency_code TEXT NOT NULL CHECK(currency_code IN ('YER','SAR'))`)
- **`supplier_payments`** (`id INTEGER PRIMARY KEY AUTOINCREMENT`, `supplier_id INTEGER NOT NULL REFERENCES suppliers(id) ON DELETE RESTRICT`, `supplier_name TEXT NOT NULL`, `amount_minor INTEGER NOT NULL CHECK(amount_minor > 0)`, `currency_code TEXT NOT NULL CHECK(currency_code IN ('YER','SAR'))`, `date_ms INTEGER NOT NULL`, `date_iso TEXT NOT NULL`, `payment_method TEXT NOT NULL DEFAULT 'نقدي'`, `notes TEXT NOT NULL DEFAULT ''`, `is_archived INTEGER NOT NULL DEFAULT 0`)

### 2.5 Expenses, Taxes & Money Transfers
- **`expenses`** (`id INTEGER PRIMARY KEY AUTOINCREMENT`, `description TEXT NOT NULL`, `category TEXT NOT NULL`, `account_id INTEGER REFERENCES ledger_accounts(id) ON DELETE SET NULL`, `amount_minor INTEGER NOT NULL CHECK(amount_minor >= 0)`, `currency_code TEXT NOT NULL CHECK(currency_code IN ('YER','SAR'))`, `date_ms INTEGER NOT NULL`, `date_iso TEXT NOT NULL`, `notes TEXT NOT NULL DEFAULT ''`, `is_archived INTEGER NOT NULL DEFAULT 0`)
- **`qat_tax_entries`** (`id INTEGER PRIMARY KEY AUTOINCREMENT`, `date_ms INTEGER NOT NULL`, `date_iso TEXT NOT NULL`, `total_qty_milli INTEGER NOT NULL DEFAULT 0`, `tax_rate_bps_or_minor INTEGER NOT NULL DEFAULT 0`, `tax_amount_minor INTEGER NOT NULL DEFAULT 0`, `currency_code TEXT NOT NULL DEFAULT 'YER' CHECK(currency_code IN ('YER','SAR'))`, `source TEXT NOT NULL DEFAULT 'يدوي'`, `notes TEXT NOT NULL DEFAULT ''`, `is_archived INTEGER NOT NULL DEFAULT 0`)
- **`transfer_recipients`** (`id INTEGER PRIMARY KEY AUTOINCREMENT`, `name TEXT NOT NULL`, `phone TEXT NOT NULL DEFAULT ''`, `company TEXT NOT NULL DEFAULT ''`, `notes TEXT NOT NULL DEFAULT ''`, `created_at_ms INTEGER NOT NULL`)
- **`money_transfers`** (`id INTEGER PRIMARY KEY AUTOINCREMENT`, `sender_name TEXT NOT NULL`, `receiver_name TEXT NOT NULL DEFAULT ''`, `recipient_id INTEGER REFERENCES transfer_recipients(id) ON DELETE SET NULL`, `amount_minor INTEGER NOT NULL CHECK(amount_minor >= 0)`, `fee_minor INTEGER NOT NULL DEFAULT 0`, `currency_code TEXT NOT NULL CHECK(currency_code IN ('YER','SAR'))`, `transfer_company TEXT NOT NULL DEFAULT ''`, `transfer_number TEXT NOT NULL DEFAULT ''`, `date_ms INTEGER NOT NULL`, `date_iso TEXT NOT NULL`, `status_code TEXT NOT NULL CHECK(status_code IN ('PENDING','DELIVERED','CANCELLED'))`, `status_label TEXT NOT NULL`, `notes TEXT NOT NULL DEFAULT ''`, `is_archived INTEGER NOT NULL DEFAULT 0`)

---

## 3. Room v18 -> Flutter SQLite v2 Migration Pipeline

### 3.1 Safety & Idempotency Guarantees
1. **Detection:** On startup (or manual restore in Settings), check if `tofan_al_aqsa_qat_db` exists in the app database directory and has not already been recorded in `_migration_history` with `status = 'COMPLETED'`.
2. **Pre-Migration Verification & Snapshot:**
   - Run `PRAGMA integrity_check` on the source Room database; abort immediately if non-`ok`.
   - Copy source DB (`+ -wal/-shm` merged via `VACUUM INTO` or byte-copy after checkpoint) to `backups/pre_migration_room_v<N>_<timestamp>.db` with SHA-256 verification before reading/mutating anything.
3. **Single Atomic Transaction:**
   - Open target `qat_ledger_v2.db` (or clean stage DB swapped atomically via rename on success), execute `BEGIN IMMEDIATE`, migrate all 14 tables in dependency order preserving primary keys (`id`) so historic references never shift:
     1. `qat_types`
     2. `customers` -> resolve walk-in / unlinked `sales`
     3. `suppliers`
     4. `inventory` -> `inventory_batches` (linking `qat_type_id` by exact name match when present)
     5. `sales` & `sales_invoice_items` (fixing legacy `subtotal`, `discount`, `taxPercent` consistency; recording `INV-LEGACY-<id>`)
     6. `customer_payments`
     7. Recompute `customer_balances` per `(customer_id, currency_code)` from `SUM(sales.remaining_amount_minor) - ...` or net `SUM(sales credit total - sales paid - standalone customer_payments)` and audit against legacy `customers.totalDebt`!
     8. `supplier_purchases` & `supplier_purchase_items`
     9. `supplier_payments`
     10. Recompute `supplier_balances` per `(supplier_id, currency_code)` and audit against legacy `suppliers.totalBalance`!
     11. `expenses` & `qat_tax_entries`
     12. `transfer_recipients` -> `money_transfers` (setting `recipient_id = NULL` whenever legacy `recipientId <= 0` or points to a non-existent recipient, logging an `INFO` row in `_migration_audit`, never inventing a recipient name).
   - Migrate legacy Android SharedPreferences XML (`qat_agency_prefs.xml`, `app_theme_prefs.xml`, `qat_ledger_license_store.xml`) via platform channel / XML parser into `app_settings` + `LocalLicenseStore`.
4. **Post-Migration Integrity Verification:**
   - Execute `PRAGMA foreign_key_check` -> assert 0 violations.
   - Verify row count parity for all 14 legacy tables (`source_count == migrated_count`).
   - Record entry in `_migration_history` with per-table JSON counts & SHA-256 of source file so subsequent startups skip migration idempotently.

---

## 4. Backup & Restore Protocol (`.qbak`)

- Backups export a self-contained `.zip` bundle (`QatLedger_Backup_<YYYYMMDD_HHmmss>.qbak`) containing:
  - `qat_ledger_v2.db` (created atomically via SQLite `VACUUM INTO`)
  - `manifest.json` (`format_version: 2`, `app_edition`, `db_version: 2`, `created_at_ms`, `row_counts`, `db_sha256`, `device_label`)
  - `agency_assets/` (logo image if configured)
- Restore workflow validates ZIP integrity, reads `manifest.json`, verifies `sha256(qat_ledger_v2.db) == manifest.db_sha256`, opens staged DB to run `PRAGMA integrity_check` + `PRAGMA foreign_key_check` (or automatically triggers the Room v18 migrator if user picks a legacy `.db` file!), captures a local automatic pre-restore rollback snapshot, swaps the DB atomically, and rolls back automatically on any verification error.
