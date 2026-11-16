import 'dart:convert';
import 'package:crypto/crypto.dart';
import 'package:flutter/material.dart';
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
import '../licensing/local_license_store.dart';
import '../licensing/trusted_key_registry.dart';

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

  // Navigation
  int _sectionIndex = 0;
  int _salesSubTab = 0;
  int _inventorySubTab = 0;
  int _transfersSubTab = 0;
  int _settingsSubTab = 0;

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
  Map<CurrencyCode, InventoryValuation> _inventoryValuation = {};
  List<QatType> _qatTypes = [];
  List<InventoryBatch> _batches = [];
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

  int get sectionIndex => _sectionIndex;
  int get salesSubTab => _salesSubTab;
  int get inventorySubTab => _inventorySubTab;
  int get transfersSubTab => _transfersSubTab;
  int get settingsSubTab => _settingsSubTab;

  Map<CurrencyCode, CurrencyDashboardMetrics> get todayMetrics => _todayMetrics;
  Map<CurrencyCode, InventoryValuation> get inventoryValuation => _inventoryValuation;
  List<QatType> get qatTypes => _qatTypes;
  List<InventoryBatch> get batches => _batches;
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
  List<AppUser> get users => _users;
  AppUser? get currentUser => _currentUser;
  SectionPermissions get sectionPermissions => _sectionPermissions;

  bool get canDeleteCurrent => _currentUser?.canDelete ?? true;
  bool get canManageSettingsCurrent => _currentUser?.canManageSettings ?? true;
  bool get canViewReportsCurrent =>
      (_currentUser?.canViewReports ?? true) && _sectionPermissions.reports;

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
              .map((m) => AppUser.fromJson(
                    m.map((k, v) => MapEntry(k.toString(), v)),
                  ))
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
            map.map((k, v) => MapEntry(k.toString(), v)),
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
    if (clean.isEmpty || password.length < 4) {
      throw ArgumentError('اسم المستخدم مطلوب وكلمة المرور 4 أحرف على الأقل.');
    }
    final user = AppUser(
      username: clean,
      displayName: displayName.trim().isEmpty ? clean : displayName.trim(),
      role: role,
      passwordSha256: _hashPassword(password),
      canDelete: canDelete,
      canEditPrices: canEditPrices,
      canManageSettings: canManageSettings,
      canViewReports: canViewReports,
    );
    final next = List<AppUser>.from(_users);
    final idx = next.indexWhere((u) => u.username == clean);
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
    final tempVerifier = await _licenseStore!.loadAndVerify(
      editionConfig: _editionConfig,
      extraDevRegistry: devReg,
    );
    // Save temporarily then verify cleanly; revert if invalid signature/corrupted
    final prev = _licenseStore!.getStoredRawLicense();
    await _licenseStore!.saveRawLicense(rawInput);
    await refreshLicense();
    if (!_licenseStatus.isValid) {
      final result = _licenseStatus;
      if (prev != null) {
        await _licenseStore!.saveRawLicense(prev);
      } else {
        await _licenseStore!.clearLicense();
      }
      _licenseStatus = tempVerifier;
      notifyListeners();
      return result;
    }
    return _licenseStatus;
  }

  Future<void> reloadAll() async {
    final today = QatDateUtils.todayIso();
    await _loadUsersAndPermissions();
    _settings = await _repo.getAgencySettings();
    _todayMetrics = await _repo.getDashboardMetrics(fromIso: today, toIso: today);
    _inventoryValuation = await _repo.getInventoryValuation();
    _qatTypes = await _repo.getQatTypes();
    _batches = await _repo.getInventoryBatches();
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
    notifyListeners();
  }

  void setSection(int index, {int? subTab}) {
    _sectionIndex = index.clamp(0, 4);
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
    notifyListeners();
  }

  void setSalesSubTab(int idx) {
    _salesSubTab = idx;
    notifyListeners();
  }

  void setInventorySubTab(int idx) {
    _inventorySubTab = idx;
    notifyListeners();
  }

  void setTransfersSubTab(int idx) {
    _transfersSubTab = idx;
    notifyListeners();
  }

  void setSettingsSubTab(int idx) {
    _settingsSubTab = idx;
    notifyListeners();
  }

  Future<void> updateSettings(AgencySettings updated) async {
    await _repo.saveAgencySettings(updated);
    _settings = updated;
    notifyListeners();
  }

  Future<void> setThemeChoice(AppThemeModeChoice choice) async {
    await _repo.setSetting('theme_choice', choice.key);
    _settings = AgencySettings(
      agencyName: _settings.agencyName,
      agencyPhone: _settings.agencyPhone,
      agencyAddress: _settings.agencyAddress,
      footerNote: _settings.footerNote,
      logoPath: _settings.logoPath,
      currencyMode: _settings.currencyMode,
      displaySarToYerMilli: _settings.displaySarToYerMilli,
      themeChoice: choice,
      onboardingComplete: _settings.onboardingComplete,
      savedPrinterName: _settings.savedPrinterName,
      savedPrinterAddress: _settings.savedPrinterAddress,
      printerPaperMm: _settings.printerPaperMm,
    );
    notifyListeners();
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
