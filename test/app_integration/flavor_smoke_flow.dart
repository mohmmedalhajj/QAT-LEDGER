import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';
import 'package:qat_ledger/app.dart';
import 'package:qat_ledger/core/theme.dart';
import 'package:qat_ledger/data/database_service.dart';
import 'package:qat_ledger/features/settings/license_security_view.dart';
import 'package:qat_ledger/licensing/developer_bridge.dart';
import 'package:qat_ledger/licensing/trusted_key_registry.dart';
import 'package:qat_ledger/state/app_controller.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

const _platformChannel = MethodChannel('com.qatledger.app/platform');

class _NoKeyDeveloperBridge implements DeveloperBridge {
  @override
  bool get isReady => true;

  @override
  Future<void> initialize(SharedPreferences prefs) async {}

  @override
  Future<TrustedPublicKeyRegistry?> getActivePublicKeyRegistry() async => null;

  @override
  Widget buildConsoleScreen({required VoidCallback onKeysUpdated}) =>
      const SizedBox.shrink();
}

void registerFlavorSmokeTest({
  required String expectedEdition,
  required String expectedApplicationId,
}) {
  testWidgets('$expectedEdition app boot, synthetic onboarding and edition isolation',
      (tester) async {
    sqfliteFfiInit();
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final tempDir = Directory.systemTemp.createTempSync('qat_${expectedEdition}_integration_');
    addTearDown(() => tempDir.deleteSync(recursive: true));

    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(_platformChannel, (call) async {
      switch (call.method) {
        case 'getEditionConfig':
          return <String, Object?>{
            'edition': expectedEdition,
            'publicKeysCsv': '',
            'applicationId': expectedApplicationId,
            'versionName': expectedEdition == 'developer' ? '2.1.0-dev' : '2.1.0',
            'versionCode': 201,
          };
        case 'getLegacyMigrationSources':
          return null;
        case 'getInstallationHardwareAnchor':
          return <String, String>{'androidId': 'synthetic-anchor', 'brand': 'test', 'model': 'host'};
        default:
          return null;
      }
    });
    addTearDown(() => TestDefaultBinaryMessengerBinding
        .instance.defaultBinaryMessenger
        .setMockMethodCallHandler(_platformChannel, null));

    final dbService = DatabaseService(
      factory: databaseFactoryFfiNoIsolate,
      customPath: '${tempDir.path}/integration.db',
    );
    final controller = AppController(
      dbService: dbService,
      prefs: prefs,
      developerBridge: expectedEdition == 'developer'
          ? _NoKeyDeveloperBridge()
          : null,
    );
    addTearDown(controller.dispose);
    await tester.runAsync(controller.initialize);

    await tester.pumpWidget(
      ChangeNotifierProvider<AppController>.value(
        value: controller,
        child: const QatLedgerApp(),
      ),
    );
    await tester.pump();

    expect(controller.initialized, isTrue);
    expect(controller.editionConfig.edition, expectedEdition);
    expect(controller.editionConfig.applicationId, expectedApplicationId);
    expect(controller.needsOnboarding, isTrue);
    expect(find.byKey(const Key('onboarding_username')), findsOneWidget);
    expect(find.byKey(const Key('onboarding_password')), findsOneWidget);
    expect(
      await tester.runAsync(() => controller.login('admin', '123456')),
      isFalse,
      reason: 'A fresh installation must not accept default credentials.',
    );

    await tester.runAsync(
      () => controller.completeFirstRunOnboarding(
        agencySettings: controller.settings.copyWith(
          agencyName: 'Arena Synthetic Agency',
        ),
        ownerDisplayName: 'QA Owner',
        username: 'qa_owner',
        password: 'SyntheticPass#2026',
        confirmPassword: 'SyntheticPass#2026',
      ),
    );
    await tester.pump();
    expect(controller.isLoggedIn, isTrue);
    expect(controller.needsOnboarding, isFalse);

    await tester.pumpWidget(
      ChangeNotifierProvider<AppController>.value(
        value: controller,
        child: MaterialApp(
          theme: AppTheme.buildLight(preset: controller.settings.activeColorPreset),
          home: const LicenseAndSecurityView(),
        ),
      ),
    );
    await tester.pumpAndSettle();
    final developerConsole = find.text('وحدة تحكم المطور (Developer Edition)');
    if (expectedEdition == 'developer') {
      await tester.scrollUntilVisible(
        developerConsole,
        500,
        scrollable: find.byType(Scrollable).last,
      );
      await tester.pumpAndSettle();
      expect(developerConsole, findsOneWidget);
    } else {
      expect(developerConsole, findsNothing,
          reason: 'Customer must not expose the Developer console.');
    }
    expect(tester.takeException(), isNull);
    await tester.runAsync(dbService.close);
  });
}
