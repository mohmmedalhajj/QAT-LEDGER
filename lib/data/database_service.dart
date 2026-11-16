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
            } catch (_) {}
          }
        },
        onCreate: (db, version) async {
          await _createSchema(db);
        },
        onUpgrade: (db, oldVersion, newVersion) async {
          await _createSchema(db);
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
