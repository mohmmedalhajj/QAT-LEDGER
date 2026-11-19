import 'dart:convert';
import 'package:crypto/crypto.dart';
import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../core/date_utils.dart';
import '../core/money.dart';
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

  // Navigation (supports both 12-screen Android routing and legacy 5-section compatibility)
  AppScreenRoute _activeRoute = AppScreenRoute.dashboard;
  int _sectionIndex = 0;
  int _salesSubTab = 0;
  int _inventorySubTab = 0;
  int _transfersSubTab = 0;
  int _settingsSubTab = 0;
  int? _statementCustomerId;
  int? _statementSupplierId;

  // Active Date/Period Filter (Today, Yesterday, Week 1-5, Month, Year, Custom Range, All)
  LedgerPeriodFilter _periodFilter = LedgerPeriodFilter.defaultToday();

  // Boot & Auth
  bool _initialized = false;
  bool _isLoggedIn = false;
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
  List<DailyChartPoint> _chartSeriesUsd = [];
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
  LedgerPeriodFilter get periodFilter => _periodFilter;

  Map<CurrencyCode, CurrencyDashboardMetrics> get todayMetrics => _todayMetrics;
  Map<CurrencyCode, CurrencyDashboardMetrics> get filteredMetrics => _filteredMetrics;
  List<DailyChartPoint> get chartSeriesYer => _chartSeriesYer;
  List<DailyChartPoint> get chartSeriesSar => _chartSeriesSar;
  List<DailyChartPoint> get chartSeriesUsd => _chartSeriesUsd;
  Map<CurrencyCode, List<DailyChartPoint>> get dailyCharts => {
        CurrencyCode.yer: _chartSeriesYer,
        CurrencyCode.sar: _chartSeriesSar,
        CurrencyCode.usd: _chartSeriesUsd,
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
    await _ensureDefaultCredentials();
    await refreshLicense();
    await reloadAll();

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

  Future<void> _ensureDefaultCredentials() async {
    final user = await _repo.getSetting('auth_username');
    final passHash = await _repo.getSetting('auth_password_sha256');
    if (user == null || user.isEmpty) {
      await _repo.setSetting('auth_username', 'admin');
    }
    if (passHash == null || passHash.isEmpty) {
      await _repo.setSetting('auth_password_sha256', _hashPassword('123456'));
    }
    await _loadUsersAndPermissions();
  }

  Future<void> _loadUsersAndPermissions() async {
    final savedUser = (await _repo.getSetting('auth_username')) ?? 'admin';
    final savedHash =
        (await _repo.getSetting('auth_password_sha256')) ?? _hashPassword('123456');
    final usersJson = await _repo.getSetting('app_users_json');
    if (usersJson != null && usersJson.isNotEmpty) {
      try {
        final Object? decoded = jsonDecode(usersJson);
        if (decoded is List<Object?>) {
          _users = decoded
              .whereType<Map<Object?, Object?>>()
              .map(
                (m) => AppUser.fromJson(
                  m.map<String, dynamic>(
                    (k, v) => MapEntry<String, dynamic>(k.toString(), v),
                  ),
                ),
              )
              .toList();
        }
      } catch (_) {
        // Ignore malformed persisted user list and fall back to defaults.
      }
    }
    if (_users.isEmpty) {
      _users = [
        AppUser(
          username: savedUser,
          displayName: 'المدير العام',
          role: UserRole.admin,
          passwordSha256: savedHash,
          canDelete: true,
          canEditPrices: true,
          canManageSettings: true,
          canViewReports: true,
        ),
      ];
      await _repo.setSetting(
        'app_users_json',
        jsonEncode(_users.map((u) => u.toJson()).toList()),
      );
    }

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

  String _hashPassword(String raw) {
    return sha256.convert(utf8.encode('QAT_LEDGER_AUTH:$raw')).toString();
  }

  Future<bool> login(String username, String password) async {
    await _loadUsersAndPermissions();
    final trimmed = username.trim();
    final hash = _hashPassword(password);
    for (final u in _users) {
      if (u.username == trimmed && u.passwordSha256 == hash) {
        _currentUser = u;
        _isLoggedIn = true;
        notifyListeners();
        return true;
      }
    }
    final savedUser = (await _repo.getSetting('auth_username')) ?? 'admin';
    final savedHash =
        (await _repo.getSetting('auth_password_sha256')) ?? _hashPassword('123456');
    if (trimmed == savedUser && hash == savedHash) {
      _currentUser = AppUser(
        username: savedUser,
        displayName: 'المدير العام',
        role: UserRole.admin,
        passwordSha256: savedHash,
      );
      _isLoggedIn = true;
      notifyListeners();
      return true;
    }
    return false;
  }

  void logout() {
    _isLoggedIn = false;
    _currentUser = null;
    notifyListeners();
  }

  Future<void> updateCredentials({
    required String newUsername,
    required String newPassword,
  }) async {
    if (newUsername.trim().isEmpty || newPassword.length < 4) {
      throw ArgumentError('اسم المستخدم مطلوب وكلمة المرور 4 أحرف على الأقل.');
    }
    final cleanUser = newUsername.trim();
    final newHash = _hashPassword(newPassword);
    await _repo.setSetting('auth_username', cleanUser);
    await _repo.setSetting('auth_password_sha256', newHash);

    final targetUsername =
        _currentUser?.username ?? (_users.isEmpty ? cleanUser : _users.first.username);
    final idx = _users.indexWhere((u) => u.username == targetUsername);
    final updatedUser = AppUser(
      username: cleanUser,
      displayName: idx >= 0 ? _users[idx].displayName : 'المدير العام',
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
    await _repo.setSetting(
      'app_users_json',
      jsonEncode(_users.map((u) => u.toJson()).toList()),
    );
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
    if (clean.isEmpty) {
      throw ArgumentError('اسم المستخدم مطلوب.');
    }
    final next = List<AppUser>.from(_users);
    final idx = next.indexWhere((u) => u.username == clean);
    if (idx < 0 && password.length < 4) {
      throw ArgumentError('كلمة المرور يجب أن تكون 4 أحرف على الأقل.');
    }
    if (password.isNotEmpty && password.length < 4) {
      throw ArgumentError('كلمة المرور الجديدة يجب أن تكون 4 أحرف على الأقل.');
    }
    final user = AppUser(
      username: clean,
      displayName: displayName.trim().isEmpty ? clean : displayName.trim(),
      role: role,
      passwordSha256:
          password.isEmpty ? next[idx].passwordSha256 : _hashPassword(password),
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
    _chartSeriesYer = await _repo.getDailyChartSeries(currency: CurrencyCode.yer);
    _chartSeriesSar = await _repo.getDailyChartSeries(currency: CurrencyCode.sar);
    _chartSeriesUsd = await _repo.getDailyChartSeries(currency: CurrencyCode.usd);
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
    await _repo.applyInventoryReconciliation(
      counts: counts,
      notes: notes,
      createdBy: _currentUser?.username ?? 'admin',
    );
    await reloadAll();
  }

  void navigateToRoute(AppScreenRoute route) {
    _activeRoute = route;
    switch (route) {
      case AppScreenRoute.dashboard:
        _sectionIndex = 0;
      case AppScreenRoute.sales:
        _sectionIndex = 1;
        _salesSubTab = 0;
      case AppScreenRoute.customers:
        _sectionIndex = 1;
        _salesSubTab = 2;
      case AppScreenRoute.statement:
        _sectionIndex = 1;
        _salesSubTab = 3;
      case AppScreenRoute.inventory:
        _sectionIndex = 2;
        _inventorySubTab = 0;
      case AppScreenRoute.suppliers:
        _sectionIndex = 2;
        _inventorySubTab = 3;
      case AppScreenRoute.accounts:
        _sectionIndex = 2;
        _inventorySubTab = 2;
      case AppScreenRoute.transfers:
        _sectionIndex = 3;
        _transfersSubTab = 0;
      case AppScreenRoute.reports:
        _sectionIndex = 3;
        _transfersSubTab = 2;
      case AppScreenRoute.archive:
        _sectionIndex = 3;
        _transfersSubTab = 4;
      case AppScreenRoute.expenses:
        _sectionIndex = 4;
        _settingsSubTab = 0;
      case AppScreenRoute.settings:
        _sectionIndex = 4;
        _settingsSubTab = 1;
    }
    notifyListeners();
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
    _sectionIndex = index.clamp(0, 4).toInt();
    if (subTab != null) {
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
    _activeRoute = switch (_sectionIndex) {
      0 => AppScreenRoute.dashboard,
      1 => switch (_salesSubTab) {
          2 => AppScreenRoute.customers,
          3 => AppScreenRoute.statement,
          _ => AppScreenRoute.sales,
        },
      2 => switch (_inventorySubTab) {
          2 => AppScreenRoute.accounts,
          3 || 4 => AppScreenRoute.suppliers,
          _ => AppScreenRoute.inventory,
        },
      3 => switch (_transfersSubTab) {
          2 => AppScreenRoute.reports,
          4 => AppScreenRoute.archive,
          _ => AppScreenRoute.transfers,
        },
      _ => switch (_settingsSubTab) {
          0 => AppScreenRoute.expenses,
          _ => AppScreenRoute.settings,
        },
    };
    notifyListeners();
  }

  void setSalesSubTab(int idx) {
    _salesSubTab = idx;
    _activeRoute = switch (idx) {
      2 => AppScreenRoute.customers,
      3 => AppScreenRoute.statement,
      _ => AppScreenRoute.sales,
    };
    notifyListeners();
  }

  void setInventorySubTab(int idx) {
    _inventorySubTab = idx;
    _activeRoute = switch (idx) {
      2 => AppScreenRoute.accounts,
      3 || 4 => AppScreenRoute.suppliers,
      _ => AppScreenRoute.inventory,
    };
    notifyListeners();
  }

  void setTransfersSubTab(int idx) {
    _transfersSubTab = idx;
    _activeRoute = switch (idx) {
      2 => AppScreenRoute.reports,
      4 => AppScreenRoute.archive,
      _ => AppScreenRoute.transfers,
    };
    notifyListeners();
  }

  void setSettingsSubTab(int idx) {
    _settingsSubTab = idx;
    _activeRoute = idx == 0 ? AppScreenRoute.expenses : AppScreenRoute.settings;
    notifyListeners();
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
    final reset = AgencySettings(
      agencyName: 'وكالة ميزان القات',
      agencyPhone: '',
      agencyAddress: '',
      footerNote: 'شكراً لتعاملكم معنا',
      logoPath: '',
      currencyMode: prev.currencyMode,
      displaySarToYerMilli: prev.displaySarToYerMilli,
      themeChoice: prev.themeChoice,
      onboardingComplete: prev.onboardingComplete,
      savedPrinterName: prev.savedPrinterName,
      savedPrinterAddress: prev.savedPrinterAddress,
      printerPaperMm: prev.printerPaperMm,
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
