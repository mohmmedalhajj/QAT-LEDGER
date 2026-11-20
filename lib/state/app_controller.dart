import 'dart:convert';
import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../core/date_utils.dart';
import '../core/money.dart';
import '../core/password_security.dart';
import '../core/platform_bridge.dart';
import '../core/theme.dart';
import '../data/backup_service.dart';
import '../data/database_service.dart';
import '../data/models.dart';
import '../data/qat_repository.dart';
import '../data/room_migrator.dart';
import '../developer/developer_database.dart';
import '../developer/developer_key_manager.dart';
import '../licensing/license_models.dart';
import '../licensing/license_verifier.dart';
import '../licensing/local_license_store.dart';
import '../licensing/trusted_key_registry.dart';
import '../licensing/trusted_time_guard.dart';

/// Official 12-screen navigation structure matching original Android QAT LEDGER.
enum AppScreenRoute {
  dashboard('لوحة التحكم الرئيسية', Icons.dashboard_rounded),
  sales('إدارة المبيعات', Icons.point_of_sale_rounded),
  customers('العملاء والديون', Icons.people_rounded),
  suppliers('الرعيّة والموردين', Icons.local_shipping_rounded),
  statement('كشف الحساب المعتمد', Icons.description_rounded),
  inventory('المخزون وأوزان الوارد', Icons.inventory_2_rounded),
  accounts('الميزانية والحسابات', Icons.account_balance_wallet_rounded),
  transfers('الحوالات والمستلمين', Icons.swap_horiz_rounded),
  expenses('المصروفات والضريبة', Icons.receipt_long_rounded),
  reports('التقارير المالية والأرباح', Icons.analytics_rounded),
  archive('الأرشيف الشامل', Icons.archive_rounded),
  settings('الإعدادات والنظام', Icons.settings_rounded);

  const AppScreenRoute(this.arabicTitle, this.icon);
  final String arabicTitle;
  final IconData icon;
}

class AppController extends ChangeNotifier {
  AppController({
    DatabaseService? dbService,
    SharedPreferences? prefs,
  })  : _dbService = dbService ?? DatabaseService.instance,
        _overridePrefs = prefs {
    _repo = QatRepository(_dbService);
    _migrator = RoomMigrator(dbService: _dbService);
    _backupService = BackupService(dbService: _dbService, migrator: _migrator);
  }

  final DatabaseService _dbService;
  final SharedPreferences? _overridePrefs;

  late final QatRepository _repo;
  late final RoomMigrator _migrator;
  late final BackupService _backupService;

  SharedPreferences? _prefs;
  LocalLicenseStore? _licenseStore;
  DeveloperKeyManager? _devKeyManager;

  static const String _prefSessionLoggedIn = 'qat_session_logged_in_v1';
  static const String _prefSessionUsername = 'qat_session_username_v1';
  static const String _prefSessionIssuedMs = 'qat_session_issued_ms_v1';
  static const String _prefRememberedUsername = 'qat_last_username_v1';

  // Navigation (supports 12-screen routing, history back-stack & unsaved-edit guards)
  AppScreenRoute _activeRoute = AppScreenRoute.dashboard;
  int _sectionIndex = 0;
  int _salesSubTab = 0;
  int _inventorySubTab = 0;
  int _transfersSubTab = 0;
  int _settingsSubTab = 0;
  int? _statementCustomerId;
  int? _statementSupplierId;
  final List<({AppScreenRoute route, int section, int subTab})> _navHistory = [];
  final Map<String, bool Function()> _unsavedGuards = {};

  // Active Date/Period Filter (Today, Yesterday, Week 1-5, Month, Year, Custom Range, All)
  LedgerPeriodFilter _periodFilter = LedgerPeriodFilter.defaultToday();

  // Boot & Auth
  bool _initialized = false;
  bool _isLoggedIn = false;
  String? _rememberedUsername;
  bool _currentUserLegacyWeakPassword = false;
  EditionConfig _editionConfig = const EditionConfig(
    edition: 'customer',
    publicKeysCsv: '',
    applicationId: 'com.aistudio.tofanqat.nxzwmb',
    versionName: '2.0.0',
    versionCode: 200,
  );
  String _installationId = '';
  LicenseVerificationResult _licenseStatus =
      const LicenseVerificationResult(kind: LicenseStateKind.unlicensed);
  MigrationSummary? _migrationSummary;

  // Live Domain State
  AgencySettings _settings = AgencySettings.defaults;
  Map<CurrencyCode, CurrencyDashboardMetrics> _todayMetrics = {};
  Map<CurrencyCode, CurrencyDashboardMetrics> _filteredMetrics = {};
  List<DailyChartPoint> _chartSeriesYer = [];
  List<DailyChartPoint> _chartSeriesSar = [];
  Map<CurrencyCode, InventoryValuation> _inventoryValuation = {};
  List<QatType> _qatTypes = [];
  List<InventoryBatch> _batches = [];
  List<InventoryReceiver> _inventoryReceivers = [];
  List<QatImportWeight> _importWeights = [];
  List<Customer> _customers = [];
  List<Sale> _sales = [];
  List<CustomerPayment> _customerPayments = [];
  List<Supplier> _suppliers = [];
  List<SupplierPurchase> _purchases = [];
  List<SupplierPayment> _supplierPayments = [];
  List<LedgerAccount> _accounts = [];
  List<Expense> _expenses = [];
  List<QatTaxEntry> _taxEntries = [];
  List<TransferRecipient> _recipients = [];
  List<MoneyTransfer> _transfers = [];
  List<TransferRecipientSummary> _recipientReports = [];
  List<DailySettlementSnapshot> _dailySettlements = [];
  List<InventoryReconciliationEntry> _reconciliationLogs = [];
  List<AppUser> _users = [];
  AppUser? _currentUser;
  SectionPermissions _sectionPermissions = const SectionPermissions();

  // Getters
  bool get initialized => _initialized;
  bool get isLoggedIn => _isLoggedIn;
  bool get needsOnboarding => !_settings.onboardingComplete || _users.isEmpty;
  String? get rememberedUsername => _rememberedUsername;
  bool get currentUserLegacyWeakPassword => _currentUserLegacyWeakPassword;
  EditionConfig get editionConfig => _editionConfig;
  bool get isDeveloperEdition => _editionConfig.isDeveloper;
  String get installationId => _installationId;
  LicenseVerificationResult get licenseStatus => _licenseStatus;
  MigrationSummary? get migrationSummary => _migrationSummary;
  AgencySettings get settings => _settings;
  CurrencyPolicy get currencyPolicy => _settings.currencyPolicy;
  ThemeMode get themeMode => _settings.themeChoice == AppThemeModeChoice.darkBlack
      ? ThemeMode.dark
      : ThemeMode.light;

  AppScreenRoute get activeRoute => _activeRoute;
  int get sectionIndex => _sectionIndex;
  int get salesSubTab => _salesSubTab;
  int get inventorySubTab => _inventorySubTab;
  int get transfersSubTab => _transfersSubTab;
  int get settingsSubTab => _settingsSubTab;
  int? get statementCustomerId => _statementCustomerId;
  int? get statementSupplierId => _statementSupplierId;
  bool get canNavigateBack => _navHistory.isNotEmpty;
  bool get hasUnsavedChanges => _unsavedGuards.values.any((fn) {
        try {
          return fn();
        } catch (_) {
          return false;
        }
      });
  LedgerPeriodFilter get periodFilter => _periodFilter;

  Map<CurrencyCode, CurrencyDashboardMetrics> get todayMetrics => _todayMetrics;
  Map<CurrencyCode, CurrencyDashboardMetrics> get filteredMetrics => _filteredMetrics;
  List<DailyChartPoint> get chartSeriesYer => _chartSeriesYer;
  List<DailyChartPoint> get chartSeriesSar => _chartSeriesSar;
  Map<CurrencyCode, List<DailyChartPoint>> get dailyCharts => {
        CurrencyCode.yer: _chartSeriesYer,
        CurrencyCode.sar: _chartSeriesSar,
      };
  Map<CurrencyCode, InventoryValuation> get inventoryValuation => _inventoryValuation;
  List<QatType> get qatTypes => _qatTypes;
  List<InventoryBatch> get batches => _batches;
  List<InventoryReceiver> get inventoryReceivers => _inventoryReceivers;
  List<QatImportWeight> get importWeights => _importWeights;
  List<Customer> get customers => _customers;
  List<Sale> get sales => _sales;
  List<CustomerPayment> get customerPayments => _customerPayments;
  List<Supplier> get suppliers => _suppliers;
  List<SupplierPurchase> get purchases => _purchases;
  List<SupplierPayment> get supplierPayments => _supplierPayments;
  List<LedgerAccount> get accounts => _accounts;
  List<Expense> get expenses => _expenses;
  List<QatTaxEntry> get taxEntries => _taxEntries;
  List<TransferRecipient> get recipients => _recipients;
  List<MoneyTransfer> get transfers => _transfers;
  List<TransferRecipientSummary> get recipientReports => _recipientReports;
  List<MoneyTransfer> get unlinkedTransfers =>
      _transfers.where((t) => t.isUnlinked).toList();
  List<DailySettlementSnapshot> get dailySettlements => _dailySettlements;
  List<InventoryReconciliationEntry> get reconciliationLogs =>
      _reconciliationLogs;
  List<AppUser> get users => _users;
  AppUser? get currentUser => _currentUser;
  SectionPermissions get sectionPermissions => _sectionPermissions;

  bool get canDeleteCurrent => _currentUser?.canDelete ?? true;
  bool get canEditPricesCurrent => _currentUser?.canEditPrices ?? true;
  bool get canManageSettingsCurrent => _currentUser?.canManageSettings ?? true;
  bool get canViewReportsCurrent =>
      (_currentUser?.canViewReports ?? true) && _sectionPermissions.reports;

  bool hasLicenseFeature(LicenseFeature feature) {
    if (!feature.isProOnly) return true;
    return _licenseStatus.hasFeature(feature);
  }

  QatRepository get repo => _repo;
  BackupService get backupService => _backupService;
  DeveloperKeyManager? get developerKeyManager => _devKeyManager;
  DeveloperDatabaseService get developerDb => DeveloperDatabaseService.instance;

  Future<void> initialize() async {
    if (_initialized) return;
    _prefs = _overridePrefs ?? await SharedPreferences.getInstance();
    _editionConfig = await PlatformBridge.instance.getEditionConfig();
    _licenseStore = LocalLicenseStore(_prefs!);
    _installationId = await _licenseStore!.getOrCreateInstallationId();
    if (_editionConfig.isDeveloper) {
      _devKeyManager = DeveloperKeyManager(_prefs!);
    }

    _migrationSummary = await _migrator.migrateIfNeeded();
    await _consumeLegacyMigratedSecurityAndLicense();
    await _loadUsersAndPermissions();
    await refreshLicense();
    await reloadAll();
    await _restorePersistedSession();

    // Sync launcher display identity from migrated/saved branding
    if (_settings.appDisplayName.isNotEmpty || _settings.logoPath.isNotEmpty) {
      await PlatformBridge.instance.applyLauncherIdentity(
        appName: _settings.appDisplayName.isNotEmpty
            ? _settings.appDisplayName
            : _settings.agencyName,
        logoPath: _settings.logoPath,
      );
    }

    _initialized = true;
    notifyListeners();
  }

  Future<void> _consumeLegacyMigratedSecurityAndLicense() async {
    final migPerms = await _repo.getSetting('migrated_section_permissions_json');
    final existingPerms = await _repo.getSetting('section_permissions_json');
    if (migPerms != null &&
        migPerms.isNotEmpty &&
        (existingPerms == null || existingPerms.isEmpty)) {
      await _repo.setSetting('section_permissions_json', migPerms);
    }

    final migPass = await _repo.getSetting('migrated_legacy_admin_password');
    final existingUsers = await _repo.getSetting('app_users_json');
    if (migPass != null &&
        migPass.isNotEmpty &&
        (existingUsers == null || existingUsers.isEmpty)) {
      final legacyUser = (await _repo.getSetting('auth_username'))?.trim();
      final uname = (legacyUser != null && legacyUser.isNotEmpty) ? legacyUser : 'owner';
      final user = AppUser(
        username: uname,
        displayName: 'مدير الوكالة',
        role: UserRole.admin,
        passwordSha256: PasswordSecurity.hashPassword(migPass),
        canDelete: true,
        canEditPrices: true,
        canManageSettings: true,
        canViewReports: true,
      );
      await _repo.setSetting('app_users_json', jsonEncode([user.toJson()]));
      await _repo.setSetting('migrated_legacy_admin_password', '');
    }

    final migLicense = await _repo.getSetting('migrated_legacy_license_doc');
    if (migLicense != null && migLicense.isNotEmpty && _licenseStore != null) {
      final existingRaw = _licenseStore!.getStoredRawLicense();
      if (existingRaw == null || existingRaw.trim().isEmpty) {
        final candidate = await activateLicense(migLicense);
        if (candidate.isValid) {
          await _repo.setSetting('migrated_legacy_license_doc', '');
        }
      }
    }
  }

  Future<void> _loadUsersAndPermissions() async {
    final loaded = <AppUser>[];
    final usersJson = await _repo.getSetting('app_users_json');
    if (usersJson != null && usersJson.isNotEmpty) {
      try {
        final Object? decoded = jsonDecode(usersJson);
        if (decoded is List<Object?>) {
          loaded.addAll(
            decoded.whereType<Map<Object?, Object?>>().map(
                  (m) => AppUser.fromJson(
                    m.map<String, dynamic>(
                      (k, v) => MapEntry<String, dynamic>(k.toString(), v),
                    ),
                  ),
                ),
          );
        }
      } catch (_) {
        // Ignore malformed persisted user list.
      }
    }

    // Legacy Flutter DB compatibility: if an existing migrated database has
    // auth_username + auth_password_sha256 without app_users_json, preserve it.
    if (loaded.isEmpty) {
      final legacyUser = (await _repo.getSetting('auth_username'))?.trim() ?? '';
      final legacyHash = (await _repo.getSetting('auth_password_sha256'))?.trim() ?? '';
      if (legacyUser.isNotEmpty && legacyHash.isNotEmpty) {
        loaded.add(
          AppUser(
            username: legacyUser,
            displayName: 'مدير الوكالة',
            role: UserRole.admin,
            passwordSha256: legacyHash,
            canDelete: true,
            canEditPrices: true,
            canManageSettings: true,
            canViewReports: true,
          ),
        );
        await _repo.setSetting(
          'app_users_json',
          jsonEncode(loaded.map((u) => u.toJson()).toList()),
        );
      }
    }
    _users = loaded;

    final secJson = await _repo.getSetting('section_permissions_json');
    if (secJson != null && secJson.isNotEmpty) {
      try {
        final Object? map = jsonDecode(secJson);
        if (map is Map<Object?, Object?>) {
          _sectionPermissions = SectionPermissions.fromJson(
            map.map<String, dynamic>(
              (k, v) => MapEntry<String, dynamic>(k.toString(), v),
            ),
          );
        }
      } catch (_) {
        // Ignore malformed persisted section permissions JSON.
      }
    }
  }

  Future<void> _restorePersistedSession() async {
    final prefs = _prefs;
    if (prefs == null) return;
    final rem = prefs.getString(_prefRememberedUsername)?.trim();
    if (rem != null && rem.isNotEmpty && _users.any((u) => u.username == rem)) {
      _rememberedUsername = rem;
    } else {
      _rememberedUsername = null;
    }

    final sessionActive = prefs.getBool(_prefSessionLoggedIn) ?? false;
    final sessionUser = prefs.getString(_prefSessionUsername)?.trim() ?? '';
    if (!sessionActive || sessionUser.isEmpty || _settings.appLockEnabled) {
      _isLoggedIn = false;
      _currentUser = null;
      return;
    }

    final matched = _users.where((u) => u.username == sessionUser).toList();
    if (matched.isEmpty || !_settings.onboardingComplete) {
      await _clearSessionStorage(clearRemembered: false);
      _isLoggedIn = false;
      _currentUser = null;
      return;
    }
    _currentUser = matched.first;
    _isLoggedIn = true;
    _currentUserLegacyWeakPassword =
        PasswordSecurity.isKnownWeakLegacyHash(matched.first.passwordSha256);
  }

  Future<void> _persistSession(AppUser user, {bool rememberUsername = true}) async {
    final prefs = _prefs;
    if (prefs == null) return;
    await prefs.setBool(_prefSessionLoggedIn, true);
    await prefs.setString(_prefSessionUsername, user.username);
    await prefs.setInt(_prefSessionIssuedMs, DateTime.now().millisecondsSinceEpoch);
    if (rememberUsername && _settings.rememberMe) {
      await prefs.setString(_prefRememberedUsername, user.username);
      _rememberedUsername = user.username;
    } else if (!_settings.rememberMe) {
      await prefs.remove(_prefRememberedUsername);
      _rememberedUsername = null;
    }
  }

  Future<void> _clearSessionStorage({bool clearRemembered = false}) async {
    final prefs = _prefs;
    if (prefs == null) return;
    await prefs.remove(_prefSessionLoggedIn);
    await prefs.remove(_prefSessionUsername);
    await prefs.remove(_prefSessionIssuedMs);
    if (clearRemembered) {
      await prefs.remove(_prefRememberedUsername);
      _rememberedUsername = null;
    }
  }

  Future<void> completeFirstRunOnboarding({
    required AgencySettings agencySettings,
    required String ownerDisplayName,
    required String username,
    required String password,
    required String confirmPassword,
  }) async {
    final cleanAgency = agencySettings.agencyName.trim();
    final cleanOwner = ownerDisplayName.trim();
    final cleanUser = username.trim();

    if (cleanAgency.isEmpty) {
      throw ArgumentError('يرجى إدخال اسم الوكالة.');
    }
    if (cleanOwner.isEmpty) {
      throw ArgumentError('يرجى إدخال اسم المالك أو المدير.');
    }
    if (cleanUser.length < 3 || cleanUser.contains(' ')) {
      throw ArgumentError('اسم المستخدم يجب أن يكون 3 أحرف على الأقل وبدون مسافات.');
    }
    if (password.length < 6) {
      throw ArgumentError('كلمة المرور يجب أن تكون 6 أحرف على الأقل.');
    }
    if (password != confirmPassword) {
      throw ArgumentError('كلمتا المرور غير متطابقتين.');
    }

    final ownerUser = AppUser(
      username: cleanUser,
      displayName: cleanOwner,
      role: UserRole.admin,
      passwordSha256: PasswordSecurity.hashPassword(password),
      canDelete: true,
      canEditPrices: true,
      canManageSettings: true,
      canViewReports: true,
    );

    final nextUsers = _users.isEmpty
        ? <AppUser>[ownerUser]
        : <AppUser>[
            ownerUser,
            ..._users.where((u) => u.username != cleanUser),
          ];

    final finalSettings = agencySettings.copyWith(
      agencyName: cleanAgency,
      ownerName: cleanOwner,
      onboardingComplete: true,
    );
    await _repo.saveAgencySettings(finalSettings);
    await _repo.setSetting(
      'app_users_json',
      jsonEncode(nextUsers.map((u) => u.toJson()).toList()),
    );
    await _repo.setSetting('auth_username', '');
    await _repo.setSetting('auth_password_sha256', '');

    _settings = finalSettings;
    _users = nextUsers;
    _currentUser = ownerUser;
    _isLoggedIn = true;
    _currentUserLegacyWeakPassword = false;
    _navHistory.clear();
    _activeRoute = AppScreenRoute.dashboard;
    _sectionIndex = 0;

    await _persistSession(ownerUser);
    await PlatformBridge.instance.applyLauncherIdentity(
      appName: finalSettings.appDisplayName.isNotEmpty
          ? finalSettings.appDisplayName
          : finalSettings.agencyName,
      logoPath: finalSettings.logoPath,
    );
    await reloadAll();
  }

  Future<bool> login(
    String username,
    String password, {
    bool rememberMe = true,
  }) async {
    await _loadUsersAndPermissions();
    final trimmed = username.trim();
    if (trimmed.isEmpty || password.isEmpty) return false;

    for (int i = 0; i < _users.length; i++) {
      final u = _users[i];
      if (u.username == trimmed &&
          PasswordSecurity.verifyPassword(password, u.passwordSha256)) {
        final wasWeak = PasswordSecurity.isKnownWeakLegacyHash(u.passwordSha256);
        var activeUser = u;
        if (PasswordSecurity.needsRehash(u.passwordSha256)) {
          activeUser = AppUser(
            username: u.username,
            displayName: u.displayName,
            role: u.role,
            passwordSha256: PasswordSecurity.hashPassword(password),
            canDelete: u.canDelete,
            canEditPrices: u.canEditPrices,
            canManageSettings: u.canManageSettings,
            canViewReports: u.canViewReports,
          );
          _users[i] = activeUser;
          await _repo.setSetting(
            'app_users_json',
            jsonEncode(_users.map((x) => x.toJson()).toList()),
          );
        }
        if (_settings.rememberMe != rememberMe) {
          _settings = _settings.copyWith(rememberMe: rememberMe);
          await _repo.setSetting('remember_me', rememberMe ? '1' : '0');
        }
        _currentUser = activeUser;
        _isLoggedIn = true;
        _currentUserLegacyWeakPassword = wasWeak;
        await _persistSession(activeUser, rememberUsername: rememberMe);
        notifyListeners();
        return true;
      }
    }
    return false;
  }

  Future<void> logout() async {
    await _clearSessionStorage(clearRemembered: false);
    _isLoggedIn = false;
    _currentUser = null;
    _currentUserLegacyWeakPassword = false;
    _navHistory.clear();
    _unsavedGuards.clear();
    _sectionIndex = 0;
    _activeRoute = AppScreenRoute.dashboard;
    notifyListeners();
  }

  Future<void> handleDatabaseRestored() async {
    _navHistory.clear();
    _unsavedGuards.clear();
    await _loadUsersAndPermissions();
    await reloadAll();
    if (_users.isEmpty || !_settings.onboardingComplete) {
      await _clearSessionStorage(clearRemembered: true);
      _isLoggedIn = false;
      _currentUser = null;
    } else if (_currentUser != null) {
      final match =
          _users.where((u) => u.username == _currentUser!.username).toList();
      if (match.isEmpty) {
        await _clearSessionStorage(clearRemembered: true);
        _isLoggedIn = false;
        _currentUser = null;
      } else {
        _currentUser = match.first;
        await _persistSession(match.first);
      }
    }
    await PlatformBridge.instance.applyLauncherIdentity(
      appName: _settings.appDisplayName.isNotEmpty
          ? _settings.appDisplayName
          : _settings.agencyName,
      logoPath: _settings.logoPath,
    );
    notifyListeners();
  }

  Future<void> updateCredentials({
    required String newUsername,
    required String newPassword,
    String? currentPassword,
  }) async {
    final cleanUser = newUsername.trim();
    if (cleanUser.length < 3 || cleanUser.contains(' ')) {
      throw ArgumentError('اسم المستخدم يجب أن يكون 3 أحرف على الأقل وبدون مسافات.');
    }
    if (newPassword.length < 6) {
      throw ArgumentError('كلمة المرور يجب أن تكون 6 أحرف على الأقل.');
    }
    if (currentPassword != null && _currentUser != null) {
      if (!PasswordSecurity.verifyPassword(
        currentPassword,
        _currentUser!.passwordSha256,
      )) {
        throw ArgumentError('كلمة المرور الحالية غير صحيحة.');
      }
    }

    final newHash = PasswordSecurity.hashPassword(newPassword);
    final targetUsername =
        _currentUser?.username ?? (_users.isEmpty ? cleanUser : _users.first.username);
    if (cleanUser != targetUsername &&
        _users.any((u) => u.username == cleanUser)) {
      throw ArgumentError('اسم المستخدم "$cleanUser" مستخدم بالفعل لحساب آخر.');
    }

    final idx = _users.indexWhere((u) => u.username == targetUsername);
    final updatedUser = AppUser(
      username: cleanUser,
      displayName: idx >= 0
          ? _users[idx].displayName
          : (_settings.ownerName.isNotEmpty ? _settings.ownerName : cleanUser),
      role: idx >= 0 ? _users[idx].role : UserRole.admin,
      passwordSha256: newHash,
      canDelete: idx >= 0 ? _users[idx].canDelete : true,
      canEditPrices: idx >= 0 ? _users[idx].canEditPrices : true,
      canManageSettings: idx >= 0 ? _users[idx].canManageSettings : true,
      canViewReports: idx >= 0 ? _users[idx].canViewReports : true,
    );
    if (idx >= 0) {
      _users[idx] = updatedUser;
    } else {
      _users.add(updatedUser);
    }
    _currentUser = updatedUser;
    _currentUserLegacyWeakPassword = false;
    await _repo.setSetting(
      'app_users_json',
      jsonEncode(_users.map((u) => u.toJson()).toList()),
    );
    await _persistSession(updatedUser);
    notifyListeners();
  }

  Future<void> addOrUpdateUser({
    required String username,
    required String displayName,
    required String password,
    required UserRole role,
    required bool canDelete,
    required bool canEditPrices,
    required bool canManageSettings,
    required bool canViewReports,
  }) async {
    final clean = username.trim();
    if (clean.length < 3 || clean.contains(' ')) {
      throw ArgumentError('اسم المستخدم يجب أن يكون 3 أحرف على الأقل وبدون مسافات.');
    }
    final next = List<AppUser>.from(_users);
    final idx = next.indexWhere((u) => u.username == clean);
    if (idx < 0 && password.length < 6) {
      throw ArgumentError('كلمة المرور يجب أن تكون 6 أحرف على الأقل.');
    }
    if (password.isNotEmpty && password.length < 6) {
      throw ArgumentError('كلمة المرور الجديدة يجب أن تكون 6 أحرف على الأقل.');
    }
    final user = AppUser(
      username: clean,
      displayName: displayName.trim().isEmpty ? clean : displayName.trim(),
      role: role,
      passwordSha256: password.isEmpty
          ? next[idx].passwordSha256
          : PasswordSecurity.hashPassword(password),
      canDelete: canDelete,
      canEditPrices: canEditPrices,
      canManageSettings: canManageSettings,
      canViewReports: canViewReports,
    );
    if (idx >= 0) {
      next[idx] = user;
    } else {
      next.add(user);
    }
    _users = next;
    await _repo.setSetting(
      'app_users_json',
      jsonEncode(_users.map((u) => u.toJson()).toList()),
    );
    if (_currentUser?.username == clean) {
      _currentUser = user;
    }
    notifyListeners();
  }

  Future<void> deleteUser(String username) async {
    if (_users.length <= 1 || username == _currentUser?.username) {
      throw StateError('لا يمكن حذف المستخدم الحالي أو آخر مستخدم في النظام.');
    }
    _users = _users.where((u) => u.username != username).toList();
    await _repo.setSetting(
      'app_users_json',
      jsonEncode(_users.map((u) => u.toJson()).toList()),
    );
    notifyListeners();
  }

  Future<void> updateSectionPermissions(SectionPermissions perms) async {
    _sectionPermissions = perms;
    await _repo.setSetting('section_permissions_json', jsonEncode(perms.toJson()));
    notifyListeners();
  }

  bool verifyAdminGatePasscode(String input) {
    return input.trim() == '780mohmmed';
  }

  Future<void> refreshLicense() async {
    if (_licenseStore == null) return;
    TrustedPublicKeyRegistry? devReg;
    if (_editionConfig.isDeveloper && _devKeyManager != null) {
      final info = await _devKeyManager!.getPublicKeyInfo();
      if (info != null) {
        devReg = TrustedPublicKeyRegistry.fromRawPublicKeys([info.rawPublicBytes]);
      }
    }
    _licenseStatus = await _licenseStore!.loadAndVerify(
      editionConfig: _editionConfig,
      extraDevRegistry: devReg,
    );
    notifyListeners();
  }

  /// Verifies strictly BEFORE overwriting any valid active license.
  Future<LicenseVerificationResult> activateLicense(String rawInput) async {
    if (_licenseStore == null) {
      return const LicenseVerificationResult(kind: LicenseStateKind.corrupted);
    }
    TrustedPublicKeyRegistry? devReg;
    if (_editionConfig.isDeveloper && _devKeyManager != null) {
      final info = await _devKeyManager!.getPublicKeyInfo();
      if (info != null) {
        devReg = TrustedPublicKeyRegistry.fromRawPublicKeys([info.rawPublicBytes]);
      }
    }
    final installId = await _licenseStore!.getOrCreateInstallationId();
    final registry = TrustedPublicKeyRegistry.fromCsv(_editionConfig.publicKeysCsv);
    final verifier = OfflineLicenseVerifier(
      registry: registry,
      timeGuard: TrustedTimeGuard(prefs: _prefs),
    );
    final candidateResult = await verifier.verify(
      rawLicenseInput: rawInput,
      currentInstallationId: installId,
      allowUntrustedLocalKeyInDev: _editionConfig.isDeveloper,
      extraDevRegistry: devReg,
    );
    if (!candidateResult.isValid) {
      return candidateResult;
    }
    await _licenseStore!.saveRawLicense(rawInput);
    await refreshLicense();
    return _licenseStatus;
  }

  Future<void> clearActiveLicense() async {
    if (_licenseStore == null) return;
    await _licenseStore!.clearLicense();
    await refreshLicense();
  }

  Future<void> reloadAll() async {
    final today = QatDateUtils.todayIso();
    final range = _periodFilter.resolveBounds();
    await _loadUsersAndPermissions();
    _settings = await _repo.getAgencySettings();
    _todayMetrics = await _repo.getDashboardMetrics(fromIso: today, toIso: today);
    _filteredMetrics = await _repo.getDashboardMetrics(
      fromIso: range.fromIso,
      toIso: range.toIso,
    );
    _chartSeriesYer = await _repo.getDailyChartSeries(
      currency: CurrencyCode.yer,
      fromIso: range.fromIso,
      toIso: range.toIso,
    );
    _chartSeriesSar = await _repo.getDailyChartSeries(
      currency: CurrencyCode.sar,
      fromIso: range.fromIso,
      toIso: range.toIso,
    );
    _inventoryValuation = await _repo.getInventoryValuation();
    _qatTypes = await _repo.getQatTypes();
    _batches = await _repo.getInventoryBatches();
    _inventoryReceivers = await _repo.getInventoryReceivers();
    _importWeights = await _repo.getImportWeights();
    _customers = await _repo.getCustomers();
    _sales = await _repo.getSales();
    _customerPayments = await _repo.getCustomerPayments();
    _suppliers = await _repo.getSuppliers();
    _purchases = await _repo.getSupplierPurchases();
    _supplierPayments = await _repo.getSupplierPayments();
    _accounts = await _repo.getLedgerAccounts();
    _expenses = await _repo.getExpenses();
    _taxEntries = await _repo.getTaxEntries();
    _recipients = await _repo.getTransferRecipients();
    _transfers = await _repo.getTransfers();
    _recipientReports = await _repo.getAllRecipientsReports();
    _dailySettlements = await _repo.getDailySettlements();
    _reconciliationLogs = await _repo.getInventoryReconciliations();
    notifyListeners();
  }

  Future<void> setPeriodFilter(LedgerPeriodFilter filter) async {
    _periodFilter = filter;
    final range = filter.resolveBounds();
    _filteredMetrics = await _repo.getDashboardMetrics(
      fromIso: range.fromIso,
      toIso: range.toIso,
    );
    _chartSeriesYer = await _repo.getDailyChartSeries(
      currency: CurrencyCode.yer,
      fromIso: range.fromIso,
      toIso: range.toIso,
    );
    _chartSeriesSar = await _repo.getDailyChartSeries(
      currency: CurrencyCode.sar,
      fromIso: range.fromIso,
      toIso: range.toIso,
    );
    notifyListeners();
  }

  Future<void> saveDailySettlement(DailySettlementSnapshot snapshot) async {
    await _repo.saveDailySettlement(snapshot);
    await reloadAll();
  }

  Future<void> applyInventoryReconciliation({
    required List<({InventoryBatch batch, int countedQtyMilli})> counts,
    required String notes,
  }) async {
    final actor = _currentUser?.username ??
        (_settings.ownerName.isNotEmpty ? _settings.ownerName : 'النظام');
    await _repo.applyInventoryReconciliation(
      counts: counts,
      notes: notes,
      createdBy: actor,
    );
    await reloadAll();
  }

  void registerUnsavedGuard(String key, bool Function() checker) {
    _unsavedGuards[key] = checker;
  }

  void unregisterUnsavedGuard(String key) {
    _unsavedGuards.remove(key);
  }

  bool canAccessRoute(AppScreenRoute route) {
    final isAdmin = _currentUser?.role == UserRole.admin;
    if (isAdmin) return true;
    return switch (route) {
      AppScreenRoute.dashboard || AppScreenRoute.settings => true,
      AppScreenRoute.sales ||
      AppScreenRoute.customers ||
      AppScreenRoute.statement =>
        _sectionPermissions.sales,
      AppScreenRoute.inventory ||
      AppScreenRoute.suppliers ||
      AppScreenRoute.accounts =>
        _sectionPermissions.inventory,
      AppScreenRoute.transfers => _sectionPermissions.transfers,
      AppScreenRoute.reports =>
        _sectionPermissions.reports && (_currentUser?.canViewReports ?? true),
      AppScreenRoute.archive => _sectionPermissions.archive,
      AppScreenRoute.expenses => _sectionPermissions.expenses,
    };
  }

  bool canAccessSection(int index) {
    final isAdmin = _currentUser?.role == UserRole.admin;
    if (isAdmin) return true;
    return switch (index) {
      0 || 4 => true,
      1 => _sectionPermissions.sales,
      2 => _sectionPermissions.inventory,
      3 => _sectionPermissions.transfers ||
          _sectionPermissions.reports ||
          _sectionPermissions.archive,
      _ => true,
    };
  }

  int _currentSubTabForSection(int section) => switch (section) {
        1 => _salesSubTab,
        2 => _inventorySubTab,
        3 => _transfersSubTab,
        4 => _settingsSubTab,
        _ => 0,
      };

  void _pushCurrentToHistoryIfChanged(
    AppScreenRoute nextRoute,
    int nextSection,
    int nextSubTab,
  ) {
    if (_activeRoute == nextRoute &&
        _sectionIndex == nextSection &&
        _currentSubTabForSection(_sectionIndex) == nextSubTab) {
      return;
    }
    final entry = (
      route: _activeRoute,
      section: _sectionIndex,
      subTab: _currentSubTabForSection(_sectionIndex),
    );
    if (_navHistory.isEmpty || _navHistory.last != entry) {
      _navHistory.add(entry);
      if (_navHistory.length > 40) {
        _navHistory.removeAt(0);
      }
    }
  }

  bool tryNavigateToRoute(AppScreenRoute route) {
    if (!canAccessRoute(route)) return false;
    final ({int section, int subTab}) target = switch (route) {
      AppScreenRoute.dashboard => (section: 0, subTab: 0),
      AppScreenRoute.sales => (section: 1, subTab: 0),
      AppScreenRoute.customers => (section: 1, subTab: 2),
      AppScreenRoute.statement => (section: 1, subTab: 3),
      AppScreenRoute.inventory => (section: 2, subTab: 0),
      AppScreenRoute.accounts => (section: 2, subTab: 2),
      AppScreenRoute.suppliers => (section: 2, subTab: 3),
      AppScreenRoute.transfers => (section: 3, subTab: 0),
      AppScreenRoute.reports => (section: 3, subTab: 2),
      AppScreenRoute.archive => (section: 3, subTab: 4),
      AppScreenRoute.expenses => (section: 4, subTab: 0),
      AppScreenRoute.settings => (section: 4, subTab: 1),
    };
    if (_activeRoute == route &&
        _sectionIndex == target.section &&
        _currentSubTabForSection(_sectionIndex) == target.subTab) {
      return true;
    }
    _pushCurrentToHistoryIfChanged(route, target.section, target.subTab);
    _applyRouteTarget(route, target.section, target.subTab);
    notifyListeners();
    return true;
  }

  void _applyRouteTarget(AppScreenRoute route, int section, int subTab) {
    _activeRoute = route;
    _sectionIndex = section.clamp(0, 4).toInt();
    switch (_sectionIndex) {
      case 1:
        _salesSubTab = subTab;
      case 2:
        _inventorySubTab = subTab;
      case 3:
        _transfersSubTab = subTab;
      case 4:
        _settingsSubTab = subTab;
    }
  }

  void navigateToRoute(AppScreenRoute route) {
    tryNavigateToRoute(route);
  }

  bool popNavigationHistory() {
    while (_navHistory.isNotEmpty) {
      final prev = _navHistory.removeLast();
      if (canAccessRoute(prev.route) &&
          (prev.route != _activeRoute ||
              prev.section != _sectionIndex ||
              prev.subTab != _currentSubTabForSection(_sectionIndex))) {
        _applyRouteTarget(prev.route, prev.section, prev.subTab);
        notifyListeners();
        return true;
      }
    }
    if (_sectionIndex != 0) {
      _applyRouteTarget(AppScreenRoute.dashboard, 0, 0);
      notifyListeners();
      return true;
    }
    return false;
  }

  void openStatementForCustomer(int customerId) {
    _statementCustomerId = customerId;
    _statementSupplierId = null;
    navigateToRoute(AppScreenRoute.statement);
  }

  void openStatementForSupplier(int supplierId) {
    _statementSupplierId = supplierId;
    _statementCustomerId = null;
    navigateToRoute(AppScreenRoute.statement);
  }

  void setSection(int index, {int? subTab}) {
    final targetSection = index.clamp(0, 4).toInt();
    if (!canAccessSection(targetSection)) return;
    final targetSub = subTab ?? _currentSubTabForSection(targetSection);
    final nextRoute = _resolveRoute(targetSection, targetSub);
    if (!canAccessRoute(nextRoute)) return;
    if (_sectionIndex == targetSection &&
        _currentSubTabForSection(_sectionIndex) == targetSub &&
        _activeRoute == nextRoute) {
      return;
    }
    _pushCurrentToHistoryIfChanged(nextRoute, targetSection, targetSub);
    _applyRouteTarget(nextRoute, targetSection, targetSub);
    notifyListeners();
  }

  AppScreenRoute _resolveRoute(int section, int subTab) => switch (section) {
        0 => AppScreenRoute.dashboard,
        1 => switch (subTab) {
            2 => AppScreenRoute.customers,
            3 => AppScreenRoute.statement,
            _ => AppScreenRoute.sales,
          },
        2 => switch (subTab) {
            2 => AppScreenRoute.accounts,
            3 || 4 => AppScreenRoute.suppliers,
            _ => AppScreenRoute.inventory,
          },
        3 => switch (subTab) {
            2 => AppScreenRoute.reports,
            4 => AppScreenRoute.archive,
            _ => AppScreenRoute.transfers,
          },
        _ => switch (subTab) {
            0 => AppScreenRoute.expenses,
            _ => AppScreenRoute.settings,
          },
      };

  void setSalesSubTab(int idx) {
    setSection(1, subTab: idx);
  }

  void setInventorySubTab(int idx) {
    setSection(2, subTab: idx);
  }

  void setTransfersSubTab(int idx) {
    setSection(3, subTab: idx);
  }

  void setSettingsSubTab(int idx) {
    setSection(4, subTab: idx);
  }

  Future<void> updateSettings(AgencySettings updated) async {
    await _repo.saveAgencySettings(updated);
    _settings = updated;
    await PlatformBridge.instance.applyLauncherIdentity(
      appName: updated.appDisplayName.isNotEmpty
          ? updated.appDisplayName
          : updated.agencyName,
      logoPath: updated.logoPath,
    );
    notifyListeners();
  }

  Future<void> resetBrandingToDefaults() async {
    final prev = _settings;
    final reset = prev.copyWith(
      agencyName: 'وكالة ميزان القات',
      appDisplayName: '',
      agencyPhone: '',
      additionalContact: '',
      agencyAddress: '',
      tagline: '',
      commercialDetails: '',
      headerText: '',
      footerNote: 'شكراً لتعاملكم معنا',
      logoPath: '',
      headerEnabled: true,
      footerEnabled: true,
    );
    await updateSettings(reset);
  }

  Future<void> setThemeChoice(AppThemeModeChoice choice) async {
    await _repo.setSetting('theme_choice', choice.key);
    _settings = _settings.copyWith(themeChoice: choice);
    notifyListeners();
  }

  Future<void> setColorPalette({
    required String paletteId,
    String? customPrimaryHex,
    String? customAccentHex,
    String? customCanvasHex,
    String? customCardHex,
    String? customTextHex,
    bool? syncColorsWithPdf,
  }) async {
    final updated = _settings.copyWith(
      paletteId: paletteId,
      customPrimaryHex: customPrimaryHex ?? _settings.customPrimaryHex,
      customAccentHex: customAccentHex ?? _settings.customAccentHex,
      customCanvasHex: customCanvasHex ?? _settings.customCanvasHex,
      customCardHex: customCardHex ?? _settings.customCardHex,
      customTextHex: customTextHex ?? _settings.customTextHex,
      syncColorsWithPdf: syncColorsWithPdf ?? _settings.syncColorsWithPdf,
    );
    await updateSettings(updated);
  }

  Future<void> setCurrencyMode(CurrencyMode mode, {int? displayRateMilli}) async {
    await _repo.setSetting('currency_mode', mode.storageKey);
    if (displayRateMilli != null) {
      await _repo.setSetting('display_sar_to_yer_milli', displayRateMilli.toString());
    }
    await reloadAll();
  }

  Future<void> dismissMigrationBanner() async {
    _migrationSummary = null;
    notifyListeners();
  }
}
