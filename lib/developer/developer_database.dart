import 'package:path/path.dart' as p;
import 'package:sqflite/sqflite.dart';

class DeveloperDatabaseService {
  DeveloperDatabaseService({DatabaseFactory? factory, String? customPath})
      : _factory = factory,
        _customPath = customPath;

  static final DeveloperDatabaseService instance = DeveloperDatabaseService();
  static const String dbName = 'qat_ledger_developer_registry_v2.db';

  final DatabaseFactory? _factory;
  final String? _customPath;
  Database? _db;

  Future<Database> get database async {
    if (_db != null && _db!.isOpen) return _db!;
    final f = _factory ?? databaseFactory;
    final dbPath = _customPath ?? p.join(await f.getDatabasesPath(), dbName);
    _db = await f.openDatabase(
      dbPath,
      options: OpenDatabaseOptions(
        version: 2,
        onCreate: (db, _) async => _createSchema(db),
        onUpgrade: (db, _, __) async => _createSchema(db),
        onOpen: (db) async => _ensureColumns(db),
      ),
    );
    return _db!;
  }

  Future<void> _createSchema(Database db) async {
    await db.execute('''
      CREATE TABLE IF NOT EXISTS license_plans (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        code TEXT NOT NULL UNIQUE,
        name TEXT NOT NULL,
        duration_days INTEGER NOT NULL,
        price_minor INTEGER NOT NULL DEFAULT 0,
        currency TEXT NOT NULL DEFAULT 'YER',
        max_devices INTEGER NOT NULL DEFAULT 1,
        is_active INTEGER NOT NULL DEFAULT 1
      )
    ''');

    await db.execute('''
      CREATE TABLE IF NOT EXISTS dev_package_prices (
        package_code TEXT PRIMARY KEY,
        currency TEXT NOT NULL DEFAULT 'ريال يمني',
        perpetual_price REAL,
        monthly_price REAL,
        quarterly_price REAL,
        yearly_price REAL,
        updated_at_ms INTEGER NOT NULL
      )
    ''');

    await db.execute('''
      CREATE TABLE IF NOT EXISTS dev_customers (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        full_name TEXT NOT NULL,
        business_name TEXT NOT NULL DEFAULT '',
        phone TEXT NOT NULL DEFAULT '',
        city TEXT NOT NULL DEFAULT '',
        notes TEXT NOT NULL DEFAULT '',
        created_at_ms INTEGER NOT NULL,
        updated_at_ms INTEGER NOT NULL DEFAULT 0
      )
    ''');

    await db.execute('''
      CREATE TABLE IF NOT EXISTS issued_licenses (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        license_id TEXT NOT NULL UNIQUE,
        plan_code TEXT NOT NULL,
        package_code TEXT NOT NULL DEFAULT 'PRO',
        license_type TEXT NOT NULL DEFAULT 'PERPETUAL',
        duration_days INTEGER,
        customer_id INTEGER REFERENCES dev_customers(id) ON DELETE SET NULL,
        installation_id TEXT NOT NULL,
        licensee_name TEXT NOT NULL,
        business_name TEXT NOT NULL DEFAULT '',
        phone_snapshot TEXT NOT NULL DEFAULT '',
        issued_at_ms INTEGER NOT NULL,
        expires_at_ms INTEGER NOT NULL,
        max_devices INTEGER NOT NULL DEFAULT 1,
        features_csv TEXT NOT NULL DEFAULT '',
        token_compact TEXT NOT NULL,
        qlic_json TEXT NOT NULL,
        key_id TEXT NOT NULL,
        status TEXT NOT NULL DEFAULT 'ACTIVE',
        price_charged REAL,
        price_currency TEXT NOT NULL DEFAULT 'ريال يمني',
        paid_amount REAL,
        payment_status TEXT NOT NULL DEFAULT 'UNPAID',
        payment_confirmed_at_ms INTEGER,
        notes TEXT NOT NULL DEFAULT '',
        created_at_ms INTEGER NOT NULL DEFAULT 0,
        updated_at_ms INTEGER NOT NULL DEFAULT 0
      )
    ''');

    await db.execute('''
      CREATE TABLE IF NOT EXISTS developer_audit_logs (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        action TEXT NOT NULL,
        actor TEXT NOT NULL,
        target_id TEXT NOT NULL,
        details TEXT NOT NULL,
        prev_installation_id TEXT,
        new_installation_id TEXT,
        reason TEXT,
        created_at_ms INTEGER NOT NULL
      )
    ''');

    const defaultPlans = [
      {
        'code': 'TRIAL_14',
        'name': 'تجريبي 14 يوم',
        'duration_days': 14,
        'price_minor': 0,
        'currency': 'YER',
        'max_devices': 1,
        'is_active': 1,
      },
      {
        'code': 'PRO_MONTHLY',
        'name': 'اشتراك شهري (30 يوم)',
        'duration_days': 30,
        'price_minor': 500000,
        'currency': 'YER',
        'max_devices': 1,
        'is_active': 1,
      },
      {
        'code': 'PRO_YEARLY',
        'name': 'اشتراك سنوي (365 يوم)',
        'duration_days': 365,
        'price_minor': 4500000,
        'currency': 'YER',
        'max_devices': 1,
        'is_active': 1,
      },
      {
        'code': 'LIFETIME',
        'name': 'ترخيص دائم',
        'duration_days': 0,
        'price_minor': 10000000,
        'currency': 'YER',
        'max_devices': 1,
        'is_active': 1,
      },
    ];
    for (final pItem in defaultPlans) {
      await db.insert('license_plans', pItem, conflictAlgorithm: ConflictAlgorithm.ignore);
    }
  }

  Future<void> _ensureColumns(Database db) async {
    await _createSchema(db);
    await _addColIfMissing(db, 'dev_customers', 'city', "TEXT NOT NULL DEFAULT ''");
    await _addColIfMissing(db, 'dev_customers', 'updated_at_ms', 'INTEGER NOT NULL DEFAULT 0');
    await _addColIfMissing(db, 'issued_licenses', 'package_code', "TEXT NOT NULL DEFAULT 'PRO'");
    await _addColIfMissing(db, 'issued_licenses', 'license_type', "TEXT NOT NULL DEFAULT 'PERPETUAL'");
    await _addColIfMissing(db, 'issued_licenses', 'duration_days', 'INTEGER');
    await _addColIfMissing(db, 'issued_licenses', 'phone_snapshot', "TEXT NOT NULL DEFAULT ''");
    await _addColIfMissing(db, 'issued_licenses', 'price_charged', 'REAL');
    await _addColIfMissing(db, 'issued_licenses', 'price_currency', "TEXT NOT NULL DEFAULT 'ريال يمني'");
    await _addColIfMissing(db, 'issued_licenses', 'paid_amount', 'REAL');
    await _addColIfMissing(db, 'issued_licenses', 'payment_status', "TEXT NOT NULL DEFAULT 'UNPAID'");
    await _addColIfMissing(db, 'issued_licenses', 'payment_confirmed_at_ms', 'INTEGER');
    await _addColIfMissing(db, 'issued_licenses', 'created_at_ms', 'INTEGER NOT NULL DEFAULT 0');
    await _addColIfMissing(db, 'issued_licenses', 'updated_at_ms', 'INTEGER NOT NULL DEFAULT 0');
    await _addColIfMissing(db, 'developer_audit_logs', 'prev_installation_id', 'TEXT');
    await _addColIfMissing(db, 'developer_audit_logs', 'new_installation_id', 'TEXT');
    await _addColIfMissing(db, 'developer_audit_logs', 'reason', 'TEXT');
  }

  Future<void> _addColIfMissing(Database db, String table, String col, String sqlDef) async {
    final info = await db.rawQuery('PRAGMA table_info($table)');
    final has = info.any((r) => r['name']?.toString() == col);
    if (!has) {
      await db.execute('ALTER TABLE $table ADD COLUMN $col $sqlDef');
    }
  }
}
