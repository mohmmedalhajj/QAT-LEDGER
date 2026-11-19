import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:path/path.dart' as p;
import 'package:qat_ledger/core/logo_storage.dart';
import 'package:qat_ledger/core/password_security.dart';
import 'package:qat_ledger/data/database_service.dart';
import 'package:qat_ledger/state/app_controller.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

void main() {
  sqfliteFfiInit();

  test('PasswordSecurity hashes with PBKDF2-HMAC-SHA256, verifies legacy SHA-256, and detects weak legacy hashes', () {
    final hashA = PasswordSecurity.hashPassword('SecretPass2026!');
    final hashB = PasswordSecurity.hashPassword('SecretPass2026!');

    expect(hashA.startsWith('pbkdf2_sha256\$'), true);
    expect(hashA, isNot(hashB)); // unique random salt per hash
    expect(PasswordSecurity.verifyPassword('SecretPass2026!', hashA), true);
    expect(PasswordSecurity.verifyPassword('WrongPass', hashA), false);
    expect(PasswordSecurity.needsRehash(hashA), false);

    final legacySha256 = sha256.convert(utf8.encode('oldStrong99')).toString();
    expect(PasswordSecurity.verifyPassword('oldStrong99', legacySha256), true);
    expect(PasswordSecurity.needsRehash(legacySha256), true);
    expect(PasswordSecurity.isKnownWeakLegacyHash(legacySha256), false);

    final weak123456 = sha256.convert(utf8.encode('123456')).toString();
    expect(PasswordSecurity.isKnownWeakLegacyHash(weak123456), true);
  });

  test('LogoStorage.persistLogoFile atomically copies logo into agency_assets and prunes old copies', () async {
    final tempDir = Directory.systemTemp.createTempSync('qat_logo_test_');
    addTearDown(() => tempDir.deleteSync(recursive: true));

    final src1 = File(p.join(tempDir.path, 'picked_one.png'));
    src1.writeAsBytesSync(<int>[137, 80, 78, 71, 1, 2, 3, 4]);
    final saved1 = await LogoStorage.persistLogoFile(
      src1.path,
      customDirectoryPath: tempDir.path,
    );
    expect(File(saved1).existsSync(), true);
    expect(p.basename(p.dirname(saved1)), 'agency_assets');

    await Future<void>.delayed(const Duration(milliseconds: 5));
    final src2 = File(p.join(tempDir.path, 'picked_two.jpg'));
    src2.writeAsBytesSync(<int>[255, 216, 255, 224, 5, 6, 7, 8]);
    final saved2 = await LogoStorage.persistLogoFile(
      src2.path,
      customDirectoryPath: tempDir.path,
    );
    expect(File(saved2).existsSync(), true);
    expect(File(saved1).existsSync(), false);
  });

  test('First-run onboarding, session persistence across restarts, appLockEnabled, credential verification, and navigation stack work cleanly', () async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final tempDir = Directory.systemTemp.createTempSync('qat_auth_nav_test_');
    final dbPath = p.join(tempDir.path, 'qat_auth.db');
    addTearDown(() => tempDir.deleteSync(recursive: true));

    final dbService1 = DatabaseService(
      factory: databaseFactoryFfiNoIsolate,
      customPath: dbPath,
    );
    final ctrl1 = AppController(dbService: dbService1, prefs: prefs);
    await ctrl1.initialize();

    expect(ctrl1.needsOnboarding, true);
    expect(ctrl1.isLoggedIn, false);
    expect(await ctrl1.login('admin', '123456'), false);

    // Reject weak password (< 6 chars)
    await expectLater(
      () => ctrl1.completeFirstRunOnboarding(
        agencySettings: ctrl1.settings.copyWith(agencyName: 'وكالة النور'),
        ownerDisplayName: 'محمد النور',
        username: 'mohammed',
        password: '123',
        confirmPassword: '123',
      ),
      throwsArgumentError,
    );

    // Complete onboarding cleanly
    await ctrl1.completeFirstRunOnboarding(
      agencySettings: ctrl1.settings.copyWith(agencyName: 'وكالة النور'),
      ownerDisplayName: 'محمد النور',
      username: 'mohammed',
      password: 'NoorPassword#1',
      confirmPassword: 'NoorPassword#1',
    );
    expect(ctrl1.needsOnboarding, false);
    expect(ctrl1.isLoggedIn, true);
    expect(ctrl1.users.single.passwordSha256.startsWith('pbkdf2_sha256\$'), true);
    expect(await ctrl1.login('admin', '123456'), false);

    // Navigation history & unsaved changes guard
    ctrl1.navigateToRoute(AppScreenRoute.sales);
    ctrl1.navigateToRoute(AppScreenRoute.customers);
    expect(ctrl1.activeRoute, AppScreenRoute.customers);
    expect(ctrl1.canNavigateBack, true);
    expect(ctrl1.popNavigationHistory(), true);
    expect(ctrl1.activeRoute, AppScreenRoute.sales);
    expect(ctrl1.popNavigationHistory(), true);
    expect(ctrl1.activeRoute, AppScreenRoute.dashboard);

    ctrl1.registerUnsavedGuard('test_form', () => true);
    expect(ctrl1.hasUnsavedChanges, true);
    ctrl1.unregisterUnsavedGuard('test_form');
    expect(ctrl1.hasUnsavedChanges, false);

    await dbService1.close();

    // Restart #1 with appLockEnabled == false -> auto-restores logged-in session
    final dbService2 = DatabaseService(
      factory: databaseFactoryFfiNoIsolate,
      customPath: dbPath,
    );
    final ctrl2 = AppController(dbService: dbService2, prefs: prefs);
    await ctrl2.initialize();
    expect(ctrl2.needsOnboarding, false);
    expect(ctrl2.isLoggedIn, true);
    expect(ctrl2.currentUser?.username, 'mohammed');

    // Update credentials verifies currentPassword
    await expectLater(
      () => ctrl2.updateCredentials(
        newUsername: 'mohammed_owner',
        newPassword: 'UpdatedPass#2',
        currentPassword: 'WrongCurrentPassword',
      ),
      throwsArgumentError,
    );
    await ctrl2.updateCredentials(
      newUsername: 'mohammed_owner',
      newPassword: 'UpdatedPass#2',
      currentPassword: 'NoorPassword#1',
    );
    expect(ctrl2.currentUser?.username, 'mohammed_owner');

    // Enable appLockEnabled -> restart requires explicit login
    await ctrl2.updateSettings(ctrl2.settings.copyWith(appLockEnabled: true));
    await dbService2.close();

    final dbService3 = DatabaseService(
      factory: databaseFactoryFfiNoIsolate,
      customPath: dbPath,
    );
    final ctrl3 = AppController(dbService: dbService3, prefs: prefs);
    await ctrl3.initialize();
    expect(ctrl3.isLoggedIn, false);
    expect(ctrl3.rememberedUsername, 'mohammed_owner');
    expect(await ctrl3.login('mohammed_owner', 'NoorPassword#1'), false);
    expect(await ctrl3.login('mohammed_owner', 'UpdatedPass#2'), true);

    await ctrl3.logout();
    expect(ctrl3.isLoggedIn, false);
    await dbService3.close();
  });
}
