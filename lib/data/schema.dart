/// Normalized SQLite v3 DDL for `qat_ledger_v2.db`.
/// Every financial column uses INTEGER minor units (1/100) and quantities use INTEGER milli units (1/1000).
abstract final class QatLedgerSchema {
  static const int currentVersion = 3;
  static const String databaseFileName = 'qat_ledger_v2.db';

  static const List<String> createStatements = [
    '''
    CREATE TABLE IF NOT EXISTS _migration_history (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      source_path TEXT NOT NULL,
      source_version INTEGER NOT NULL,
      migrated_at_ms INTEGER NOT NULL,
      status TEXT NOT NULL,
      rows_migrated_json TEXT NOT NULL,
      checksum_sha256 TEXT NOT NULL
    )
    ''',
    '''
    CREATE TABLE IF NOT EXISTS _migration_audit (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      table_name TEXT NOT NULL,
      legacy_id INTEGER,
      severity TEXT NOT NULL,
      code TEXT NOT NULL,
      message TEXT NOT NULL,
      created_at_ms INTEGER NOT NULL
    )
    ''',
    '''
    CREATE TABLE IF NOT EXISTS app_settings (
      key TEXT PRIMARY KEY,
      value TEXT NOT NULL,
      updated_at_ms INTEGER NOT NULL
    )
    ''',
    '''
    CREATE TABLE IF NOT EXISTS ledger_accounts (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      code TEXT NOT NULL UNIQUE,
      name TEXT NOT NULL,
      category TEXT NOT NULL,
      is_system INTEGER NOT NULL DEFAULT 0,
      is_active INTEGER NOT NULL DEFAULT 1
    )
    ''',
    '''
    CREATE TABLE IF NOT EXISTS qat_types (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT NOT NULL UNIQUE,
      default_buy_price_minor INTEGER NOT NULL DEFAULT 0 CHECK(default_buy_price_minor >= 0),
      default_sell_price_minor INTEGER NOT NULL DEFAULT 0 CHECK(default_sell_price_minor >= 0),
      default_currency TEXT NOT NULL DEFAULT 'YER' CHECK(default_currency IN ('YER','SAR')),
      notes TEXT NOT NULL DEFAULT '',
      is_hidden INTEGER NOT NULL DEFAULT 0,
      created_at_ms INTEGER NOT NULL
    )
    ''',
    '''
    CREATE TABLE IF NOT EXISTS customers (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT NOT NULL,
      phone TEXT NOT NULL DEFAULT '',
      address TEXT NOT NULL DEFAULT '',
      notes TEXT NOT NULL DEFAULT '',
      created_at_ms INTEGER NOT NULL,
      is_archived INTEGER NOT NULL DEFAULT 0
    )
    ''',
    '''
    CREATE TABLE IF NOT EXISTS customer_balances (
      customer_id INTEGER NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
      currency_code TEXT NOT NULL CHECK(currency_code IN ('YER','SAR')),
      debt_minor INTEGER NOT NULL DEFAULT 0,
      updated_at_ms INTEGER NOT NULL,
      PRIMARY KEY (customer_id, currency_code)
    )
    ''',
    '''
    CREATE TABLE IF NOT EXISTS suppliers (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT NOT NULL,
      phone TEXT NOT NULL DEFAULT '',
      address TEXT NOT NULL DEFAULT '',
      region TEXT NOT NULL DEFAULT '',
      notes TEXT NOT NULL DEFAULT '',
      created_at_ms INTEGER NOT NULL,
      is_archived INTEGER NOT NULL DEFAULT 0
    )
    ''',
    '''
    CREATE TABLE IF NOT EXISTS supplier_balances (
      supplier_id INTEGER NOT NULL REFERENCES suppliers(id) ON DELETE CASCADE,
      currency_code TEXT NOT NULL CHECK(currency_code IN ('YER','SAR')),
      balance_minor INTEGER NOT NULL DEFAULT 0,
      updated_at_ms INTEGER NOT NULL,
      PRIMARY KEY (supplier_id, currency_code)
    )
    ''',
    '''
    CREATE TABLE IF NOT EXISTS inventory_batches (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      qat_type_id INTEGER REFERENCES qat_types(id) ON DELETE RESTRICT,
      qat_type_name TEXT NOT NULL,
      supplier_id INTEGER REFERENCES suppliers(id) ON DELETE SET NULL,
      supplier_name TEXT NOT NULL DEFAULT '',
      remaining_qty_milli INTEGER NOT NULL CHECK(remaining_qty_milli >= 0),
      initial_qty_milli INTEGER NOT NULL DEFAULT 0,
      unit TEXT NOT NULL DEFAULT 'ربطة',
      buy_price_minor INTEGER NOT NULL CHECK(buy_price_minor >= 0),
      sell_price_minor INTEGER NOT NULL CHECK(sell_price_minor >= 0),
      currency_code TEXT NOT NULL CHECK(currency_code IN ('YER','SAR')),
      purchase_date_ms INTEGER NOT NULL,
      purchase_date_iso TEXT NOT NULL,
      is_archived INTEGER NOT NULL DEFAULT 0,
      item_number TEXT NOT NULL DEFAULT '',
      low_stock_threshold_milli INTEGER NOT NULL DEFAULT 0,
      receiver_id INTEGER,
      receiver_name TEXT NOT NULL DEFAULT '',
      notes TEXT NOT NULL DEFAULT ''
    )
    ''',
    '''
    CREATE TABLE IF NOT EXISTS sales (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      invoice_number TEXT NOT NULL UNIQUE,
      customer_id INTEGER REFERENCES customers(id) ON DELETE RESTRICT,
      customer_name TEXT NOT NULL,
      date_ms INTEGER NOT NULL,
      date_iso TEXT NOT NULL,
      currency_code TEXT NOT NULL CHECK(currency_code IN ('YER','SAR')),
      subtotal_minor INTEGER NOT NULL,
      discount_minor INTEGER NOT NULL DEFAULT 0 CHECK(discount_minor >= 0),
      tax_bps INTEGER NOT NULL DEFAULT 0 CHECK(tax_bps >= 0),
      tax_amount_minor INTEGER NOT NULL DEFAULT 0 CHECK(tax_amount_minor >= 0),
      total_amount_minor INTEGER NOT NULL,
      paid_amount_minor INTEGER NOT NULL DEFAULT 0 CHECK(paid_amount_minor >= 0),
      remaining_amount_minor INTEGER NOT NULL DEFAULT 0,
      payment_type TEXT NOT NULL CHECK(payment_type IN ('PAID','PARTIAL','CREDIT')),
      status TEXT NOT NULL,
      notes TEXT NOT NULL DEFAULT '',
      is_archived INTEGER NOT NULL DEFAULT 0,
      payment_method TEXT NOT NULL DEFAULT ''
    )
    ''',
    '''
    CREATE TABLE IF NOT EXISTS sales_invoice_items (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      sale_id INTEGER NOT NULL REFERENCES sales(id) ON DELETE CASCADE,
      inventory_batch_id INTEGER REFERENCES inventory_batches(id) ON DELETE SET NULL,
      qat_type_name TEXT NOT NULL,
      qty_milli INTEGER NOT NULL CHECK(qty_milli > 0),
      unit TEXT NOT NULL,
      unit_price_minor INTEGER NOT NULL CHECK(unit_price_minor >= 0),
      buy_price_minor INTEGER NOT NULL DEFAULT 0 CHECK(buy_price_minor >= 0),
      line_total_minor INTEGER NOT NULL,
      currency_code TEXT NOT NULL CHECK(currency_code IN ('YER','SAR'))
    )
    ''',
    '''
    CREATE TABLE IF NOT EXISTS customer_payments (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      customer_id INTEGER NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
      customer_name TEXT NOT NULL,
      amount_minor INTEGER NOT NULL CHECK(amount_minor > 0),
      currency_code TEXT NOT NULL CHECK(currency_code IN ('YER','SAR')),
      date_ms INTEGER NOT NULL,
      date_iso TEXT NOT NULL,
      payment_method TEXT NOT NULL DEFAULT 'نقدي',
      notes TEXT NOT NULL DEFAULT '',
      is_archived INTEGER NOT NULL DEFAULT 0
    )
    ''',
    '''
    CREATE TABLE IF NOT EXISTS supplier_purchases (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      purchase_number TEXT NOT NULL UNIQUE,
      supplier_id INTEGER NOT NULL REFERENCES suppliers(id) ON DELETE RESTRICT,
      supplier_name TEXT NOT NULL,
      date_ms INTEGER NOT NULL,
      date_iso TEXT NOT NULL,
      currency_code TEXT NOT NULL CHECK(currency_code IN ('YER','SAR')),
      subtotal_minor INTEGER NOT NULL,
      discount_minor INTEGER NOT NULL DEFAULT 0 CHECK(discount_minor >= 0),
      tax_bps INTEGER NOT NULL DEFAULT 0 CHECK(tax_bps >= 0),
      tax_amount_minor INTEGER NOT NULL DEFAULT 0 CHECK(tax_amount_minor >= 0),
      total_amount_minor INTEGER NOT NULL,
      paid_amount_minor INTEGER NOT NULL DEFAULT 0 CHECK(paid_amount_minor >= 0),
      remaining_amount_minor INTEGER NOT NULL DEFAULT 0,
      payment_type TEXT NOT NULL CHECK(payment_type IN ('PAID','PARTIAL','CREDIT')),
      notes TEXT NOT NULL DEFAULT '',
      is_archived INTEGER NOT NULL DEFAULT 0,
      payment_method TEXT NOT NULL DEFAULT '',
      bank_name TEXT NOT NULL DEFAULT '',
      transfer_number TEXT NOT NULL DEFAULT '',
      demar_minor INTEGER NOT NULL DEFAULT 0,
      demar_type TEXT NOT NULL DEFAULT '',
      demar_number TEXT NOT NULL DEFAULT ''
    )
    ''',
    '''
    CREATE TABLE IF NOT EXISTS supplier_purchase_items (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      purchase_id INTEGER NOT NULL REFERENCES supplier_purchases(id) ON DELETE CASCADE,
      qat_type_name TEXT NOT NULL,
      qty_milli INTEGER NOT NULL CHECK(qty_milli > 0),
      unit TEXT NOT NULL,
      buy_price_minor INTEGER NOT NULL CHECK(buy_price_minor >= 0),
      line_total_minor INTEGER NOT NULL,
      currency_code TEXT NOT NULL CHECK(currency_code IN ('YER','SAR'))
    )
    ''',
    '''
    CREATE TABLE IF NOT EXISTS supplier_payments (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      supplier_id INTEGER NOT NULL REFERENCES suppliers(id) ON DELETE RESTRICT,
      supplier_name TEXT NOT NULL,
      amount_minor INTEGER NOT NULL CHECK(amount_minor > 0),
      currency_code TEXT NOT NULL CHECK(currency_code IN ('YER','SAR')),
      date_ms INTEGER NOT NULL,
      date_iso TEXT NOT NULL,
      payment_method TEXT NOT NULL DEFAULT 'نقدي',
      notes TEXT NOT NULL DEFAULT '',
      is_archived INTEGER NOT NULL DEFAULT 0
    )
    ''',
    '''
    CREATE TABLE IF NOT EXISTS expenses (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      description TEXT NOT NULL,
      category TEXT NOT NULL,
      account_id INTEGER REFERENCES ledger_accounts(id) ON DELETE SET NULL,
      amount_minor INTEGER NOT NULL CHECK(amount_minor >= 0),
      currency_code TEXT NOT NULL CHECK(currency_code IN ('YER','SAR')),
      date_ms INTEGER NOT NULL,
      date_iso TEXT NOT NULL,
      notes TEXT NOT NULL DEFAULT '',
      is_archived INTEGER NOT NULL DEFAULT 0
    )
    ''',
    '''
    CREATE TABLE IF NOT EXISTS qat_tax_entries (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      date_ms INTEGER NOT NULL,
      date_iso TEXT NOT NULL,
      total_qty_milli INTEGER NOT NULL DEFAULT 0,
      tax_rate_minor INTEGER NOT NULL DEFAULT 0,
      tax_amount_minor INTEGER NOT NULL DEFAULT 0,
      stall_outflow_minor INTEGER NOT NULL DEFAULT 0,
      labor_outflow_minor INTEGER NOT NULL DEFAULT 0,
      currency_code TEXT NOT NULL DEFAULT 'YER' CHECK(currency_code IN ('YER','SAR')),
      source TEXT NOT NULL DEFAULT 'يدوي',
      notes TEXT NOT NULL DEFAULT '',
      is_archived INTEGER NOT NULL DEFAULT 0
    )
    ''',
    '''
    CREATE TABLE IF NOT EXISTS transfer_recipients (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT NOT NULL,
      phone TEXT NOT NULL DEFAULT '',
      company TEXT NOT NULL DEFAULT '',
      notes TEXT NOT NULL DEFAULT '',
      created_at_ms INTEGER NOT NULL,
      is_active INTEGER NOT NULL DEFAULT 1
    )
    ''',
    '''
    CREATE TABLE IF NOT EXISTS money_transfers (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      sender_name TEXT NOT NULL,
      receiver_name TEXT NOT NULL DEFAULT '',
      recipient_id INTEGER REFERENCES transfer_recipients(id) ON DELETE SET NULL,
      amount_minor INTEGER NOT NULL CHECK(amount_minor >= 0),
      fee_minor INTEGER NOT NULL DEFAULT 0 CHECK(fee_minor >= 0),
      currency_code TEXT NOT NULL CHECK(currency_code IN ('YER','SAR')),
      transfer_company TEXT NOT NULL DEFAULT '',
      transfer_number TEXT NOT NULL DEFAULT '',
      date_ms INTEGER NOT NULL,
      date_iso TEXT NOT NULL,
      status_code TEXT NOT NULL CHECK(status_code IN ('PENDING','DELIVERED','CANCELLED')),
      status_label TEXT NOT NULL,
      notes TEXT NOT NULL DEFAULT '',
      is_archived INTEGER NOT NULL DEFAULT 0
    )
    ''',
    '''
    CREATE TABLE IF NOT EXISTS qat_import_weights (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      date_ms INTEGER NOT NULL,
      date_iso TEXT NOT NULL,
      weight_milli INTEGER NOT NULL DEFAULT 0 CHECK(weight_milli >= 0),
      receiver_name TEXT NOT NULL DEFAULT '',
      notes TEXT NOT NULL DEFAULT '',
      is_archived INTEGER NOT NULL DEFAULT 0
    )
    ''',
  ];

  static const List<String> indexStatements = [
    'CREATE INDEX IF NOT EXISTS idx_sales_date_iso ON sales(date_iso, is_archived)',
    'CREATE INDEX IF NOT EXISTS idx_sales_customer ON sales(customer_id, currency_code)',
    'CREATE INDEX IF NOT EXISTS idx_sales_items_sale ON sales_invoice_items(sale_id)',
    'CREATE INDEX IF NOT EXISTS idx_cust_pay_cust ON customer_payments(customer_id, currency_code, date_iso)',
    'CREATE INDEX IF NOT EXISTS idx_sup_purch_sup ON supplier_purchases(supplier_id, currency_code, date_iso)',
    'CREATE INDEX IF NOT EXISTS idx_sup_items_purch ON supplier_purchase_items(purchase_id)',
    'CREATE INDEX IF NOT EXISTS idx_sup_pay_sup ON supplier_payments(supplier_id, currency_code, date_iso)',
    'CREATE INDEX IF NOT EXISTS idx_expenses_date ON expenses(date_iso, currency_code, is_archived)',
    'CREATE INDEX IF NOT EXISTS idx_tax_date ON qat_tax_entries(date_iso, currency_code, is_archived)',
    'CREATE INDEX IF NOT EXISTS idx_transfers_recipient ON money_transfers(recipient_id, status_code, currency_code)',
    'CREATE INDEX IF NOT EXISTS idx_transfers_date ON money_transfers(date_iso, is_archived)',
    'CREATE INDEX IF NOT EXISTS idx_inventory_name ON inventory_batches(qat_type_name, is_archived)',
    'CREATE INDEX IF NOT EXISTS idx_import_weights_date ON qat_import_weights(date_iso, is_archived)',
  ];

  static const Map<String, List<String>> v3ColumnMigrations = {
    'suppliers': [
      "region TEXT NOT NULL DEFAULT ''",
    ],
    'inventory_batches': [
      "item_number TEXT NOT NULL DEFAULT ''",
      'low_stock_threshold_milli INTEGER NOT NULL DEFAULT 0',
      'receiver_id INTEGER',
      "receiver_name TEXT NOT NULL DEFAULT ''",
      "notes TEXT NOT NULL DEFAULT ''",
    ],
    'sales': [
      "payment_method TEXT NOT NULL DEFAULT ''",
    ],
    'supplier_purchases': [
      "payment_method TEXT NOT NULL DEFAULT ''",
      "bank_name TEXT NOT NULL DEFAULT ''",
      "transfer_number TEXT NOT NULL DEFAULT ''",
      'demar_minor INTEGER NOT NULL DEFAULT 0',
      "demar_type TEXT NOT NULL DEFAULT ''",
      "demar_number TEXT NOT NULL DEFAULT ''",
    ],
    'qat_tax_entries': [
      'stall_outflow_minor INTEGER NOT NULL DEFAULT 0',
      'labor_outflow_minor INTEGER NOT NULL DEFAULT 0',
    ],
    'transfer_recipients': [
      'is_active INTEGER NOT NULL DEFAULT 1',
    ],
  };

  static const List<Map<String, Object>> defaultLedgerAccounts = [
    {'code': 'EXP_TRANSPORT', 'name': 'نقل ومواصلات', 'category': 'نقل ومواصلات', 'is_system': 1},
    {'code': 'EXP_RENT', 'name': 'إيجار المحل والجمارك', 'category': 'إيجار المحل والجمارك', 'is_system': 1},
    {'code': 'EXP_BAGS', 'name': 'تعبئة وتغليف وعمالة', 'category': 'تعبئة وتغليف وعمالة', 'is_system': 1},
    {'code': 'EXP_SALARIES', 'name': 'خدمات وتبرعات وطاقة', 'category': 'خدمات وتبرعات وطاقة', 'is_system': 1},
    {'code': 'EXP_OTHER', 'name': 'مصاريف عامة أخرى', 'category': 'مصاريف عامة أخرى', 'is_system': 1},
  ];
}
