import 'package:path/path.dart' as p;
import 'package:sqflite/sqflite.dart';
import 'schema.dart';

class DatabaseService {
  DatabaseService({
    DatabaseFactory? factory,
    String? customPath,
  })  : _factory = factory,
        _customPath = customPath;

  static final DatabaseService instance = DatabaseService();

  final DatabaseFactory? _factory;
  final String? _customPath;
  Database? _db;

  DatabaseFactory get effectiveFactory => _factory ?? databaseFactory;

  Future<String> resolveDatabasePath() async {
    if (_customPath != null) return _customPath;
    final dir = await effectiveFactory.getDatabasesPath();
    return p.join(dir, QatLedgerSchema.databaseFileName);
  }

  Future<Database> get database async {
    if (_db != null && _db!.isOpen) return _db!;
    final dbPath = await resolveDatabasePath();
    _db = await effectiveFactory.openDatabase(
      dbPath,
      options: OpenDatabaseOptions(
        version: QatLedgerSchema.currentVersion,
        onConfigure: (db) async {
          await db.execute('PRAGMA foreign_keys = ON');
          // Enable WAL where supported (not in-memory)
          if (dbPath != ':memory:') {
            try {
              await db.rawQuery('PRAGMA journal_mode = WAL');
            } catch (_) {
              // Fall back to default SQLite journal mode when WAL is unsupported.
            }
          }
        },
        onCreate: (db, version) async {
          await _createSchema(db);
        },
        onUpgrade: (db, oldVersion, newVersion) async {
          await _createSchema(db);
          await _ensureV3Columns(db);
        },
        onOpen: (db) async {
          await _createSchema(db);
          await _ensureV3Columns(db);
        },
      ),
    );
    return _db!;
  }

  Future<void> _createSchema(Database db) async {
    for (final sql in QatLedgerSchema.createStatements) {
      await db.execute(sql);
    }
    for (final idx in QatLedgerSchema.indexStatements) {
      await db.execute(idx);
    }
    final batch = db.batch();
    for (final acc in QatLedgerSchema.defaultLedgerAccounts) {
      batch.insert(
        'ledger_accounts',
        acc,
        conflictAlgorithm: ConflictAlgorithm.ignore,
      );
    }
    await batch.commit(noResult: true);
  }

  Future<void> _ensureV3Columns(Database db) async {
    for (final entry in QatLedgerSchema.v3ColumnMigrations.entries) {
      final table = entry.key;
      final info = await db.rawQuery('PRAGMA table_info($table)');
      final existingCols = info
          .map((r) => (r['name'] ?? '').toString().toLowerCase())
          .toSet();
      if (existingCols.isEmpty) continue;
      for (final colDef in entry.value) {
        final colName = colDef.split(RegExp(r'\s+')).first.toLowerCase();
        if (!existingCols.contains(colName)) {
          await db.execute('ALTER TABLE $table ADD COLUMN $colDef');
        }
      }
    }
  }

  Future<void> close() async {
    final current = _db;
    _db = null;
    if (current != null && current.isOpen) {
      await current.close();
    }
  }

  Future<void> reopenAfterRestore() async {
    await close();
    await database;
  }
}
