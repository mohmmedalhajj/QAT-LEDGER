import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:path/path.dart' as p;
import 'package:provider/provider.dart';
import 'package:qat_ledger/app.dart';
import 'package:qat_ledger/core/theme.dart';
import 'package:qat_ledger/data/database_service.dart';
import 'package:qat_ledger/data/models.dart';
import 'package:qat_ledger/state/app_controller.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

import '_ci_diag_helper.dart';

void main() {
  sqfliteFfiInit();

  testWidgets('App boots in RTL across small & large phone sizes, tests hidden admin gate, multi-user permissions, and themes', (tester) async {
    final steps = <String>[];
    final prevOnError = FlutterError.onError;
    FlutterError.onError = (FlutterErrorDetails details) {
      emitCiAnnotation(
        'error',
        'widget_shell_test FlutterError (${steps.isEmpty ? "start" : steps.last})',
        '${details.exceptionAsString()}\n${details.stack}',
      );
      prevOnError?.call(details);
    };
    try {
      steps.add('init');
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final tempDir = Directory.systemTemp.createTempSync('qat_widget_test_');
      final dbService = DatabaseService(
        factory: databaseFactoryFfiNoIsolate,
        customPath: p.join(tempDir.path, 'qat_test.db'),
      );

      final controller = AppController(dbService: dbService, prefs: prefs);
      await tester.runAsync(controller.initialize);
      steps.add('pump_login');

      // Test small phone viewport (360x640) on Login screen + hidden admin 3-tap gate
      tester.view.physicalSize = const Size(360, 640);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });

      await tester.pumpWidget(
        ChangeNotifierProvider<AppController>.value(
          value: controller,
          child: const QatLedgerApp(),
        ),
      );
      await tester.pumpAndSettle();
      steps.add('logo_tap');

      // 3 consecutive taps on login logo opens hidden admin password gate
      final logoFinder = find.byKey(const Key('login_logo_secret_trigger'));
      expect(logoFinder, findsOneWidget);
      await tester.tap(logoFinder);
      await tester.tap(logoFinder);
      await tester.tap(logoFinder);
      await tester.pumpAndSettle();
      expect(find.text('بوابة الإدارة'), findsOneWidget);
      await tester.tap(find.text('إلغاء'));
      await tester.pumpAndSettle();
      steps.add('login');

      // Login & complete onboarding
      await tester.runAsync(() => controller.login('admin', '123456'));
      await tester.runAsync(
        () => controller.updateSettings(
          controller.settings.copyWith(onboardingComplete: true),
        ),
      );
      await tester.pumpAndSettle();
      steps.add('sections_0_4');

      // Navigate all 5 sections and sub-tabs on small viewport without overflow
      for (int i = 0; i < 5; i++) {
        controller.setSection(i);
        await tester.pumpAndSettle();
        steps.add('sec_$i');
      }
      controller.setSection(2, subTab: 2); // Reconciliation & Settlement
      await tester.pumpAndSettle();
      expect(find.text('جرد المخزون والمطابقة'), findsOneWidget);
      expect(find.text('التحاسب اليومي للصندوق'), findsOneWidget);
      steps.add('sec_1_3');

      controller.setSection(1, subTab: 3); // Account statements
      await tester.pumpAndSettle();
      steps.add('sec_3_2');
      controller.setSection(3, subTab: 2); // Financial reports
      await tester.pumpAndSettle();
      steps.add('viewport_412');

      // Test large phone viewport (412x915)
      tester.view.physicalSize = const Size(412, 915);
      await tester.pumpAndSettle();

      controller.setSection(0);
      await tester.pumpAndSettle();
      expect(find.text('الرئيسية'), findsWidgets);
      expect(find.text('المبيعات'), findsOneWidget);
      expect(find.text('المخزون'), findsOneWidget);
      expect(find.text('الحوالات'), findsOneWidget);
      expect(find.text('الإعدادات'), findsOneWidget);
      expect(find.text('إجراءات سريعة'), findsOneWidget);
      expect(find.text('فاتورة بيع'), findsOneWidget);
      steps.add('viewport_1024');

      // Tablet wide viewport (1024x768) renders NavigationRail
      tester.view.physicalSize = const Size(1024, 768);
      await tester.pumpAndSettle();
      expect(find.byType(NavigationRail), findsOneWidget);

      tester.view.physicalSize = const Size(412, 915);
      await tester.pumpAndSettle();
      steps.add('dark_mode');

      // Switch to Pure Black Dark mode
      await tester.runAsync(
        () => controller.setThemeChoice(AppThemeModeChoice.darkBlack),
      );
      await tester.pumpAndSettle();
      expect(controller.themeMode, ThemeMode.dark);
      steps.add('cashier');

      // Multi-user permission restriction test
      await tester.runAsync(
        () => controller.addOrUpdateUser(
          username: 'cashier1',
          displayName: 'بائع الصالة',
          password: 'pass123',
          role: UserRole.cashier,
          canDelete: false,
          canEditPrices: false,
          canManageSettings: false,
          canViewReports: false,
        ),
      );
      controller.logout();
      final cashierOk = await tester.runAsync<bool>(
        () => controller.login('cashier1', 'pass123'),
      );
      expect(cashierOk, true);
      expect(controller.canDeleteCurrent, false);
      expect(controller.canViewReportsCurrent, false);
      steps.add('sec_4_3');

      // Navigate to Settings -> License tab: Customer flavor hides Developer Console
      controller.setSection(4, subTab: 3);
      await tester.pumpAndSettle();
      expect(find.text('وحدة تحكم المطور (Developer Edition)'), findsNothing);

      await tester.runAsync(dbService.close);
      tempDir.deleteSync(recursive: true);
      steps.add('done');
    } catch (e, st) {
      emitCiAnnotation('error', 'widget_shell_test (${steps.isEmpty ? "start" : steps.last})', '$e\n$st');
      rethrow;
    } finally {
      FlutterError.onError = prevOnError;
      emitCiAnnotation('warning', 'widget_shell_test trace', steps.join(' -> '));
    }
  });
}
