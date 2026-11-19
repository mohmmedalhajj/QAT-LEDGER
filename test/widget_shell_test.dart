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

void main() {
  sqfliteFfiInit();

  testWidgets(
      'App boots in RTL into first-run onboarding (no default credentials), verifies Login 3-tap gate, 6-tab bar + drawer, PRO gating, multi-user permissions, and themes',
      (tester) async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final tempDir = Directory.systemTemp.createTempSync('qat_widget_test_');
    final dbService = DatabaseService(
      factory: databaseFactoryFfiNoIsolate,
      customPath: p.join(tempDir.path, 'qat_test.db'),
    );

    final controller = AppController(dbService: dbService, prefs: prefs);
    await tester.runAsync(controller.initialize);

    // No default admin / 123456 credentials exist on fresh install
    expect(controller.needsOnboarding, true);
    expect(controller.isLoggedIn, false);
    final defaultLogin = await tester.runAsync<bool>(
      () => controller.login('admin', '123456'),
    );
    expect(defaultLogin, false);

    // Test small phone viewport (360x640) on First-Run Onboarding screen
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

    expect(find.byKey(const Key('onboarding_username')), findsOneWidget);
    expect(find.byKey(const Key('onboarding_password')), findsOneWidget);

    // Complete first-run onboarding with owner account, then logout to verify Login screen
    await tester.runAsync(
      () => controller.completeFirstRunOnboarding(
        agencySettings: controller.settings.copyWith(agencyName: 'وكالة الاختبار'),
        ownerDisplayName: 'سالم المالك',
        username: 'salem_owner',
        password: 'ownerPass99',
        confirmPassword: 'ownerPass99',
      ),
    );
    expect(controller.needsOnboarding, false);
    expect(controller.isLoggedIn, true);
    await tester.runAsync(controller.logout);
    await tester.pumpAndSettle();

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

    // Login as configured owner
    final ownerOk = await tester.runAsync<bool>(
      () => controller.login('salem_owner', 'ownerPass99'),
    );
    expect(ownerOk, true);
    await tester.pumpAndSettle();

    // Navigate all 5 sections and sub-tabs on small viewport without overflow
    for (int i = 0; i < 5; i++) {
      controller.setSection(i);
      await tester.pumpAndSettle();
    }
    controller.setSection(2, subTab: 0); // Inventory Batches + Receivers & Matching
    await tester.pumpAndSettle();
    final receiversSeg = find.textContaining('المستلمون والمطابقة');
    expect(receiversSeg, findsOneWidget);
    await tester.ensureVisible(receiversSeg);
    await tester.tap(receiversSeg);
    await tester.pumpAndSettle();
    expect(find.text('المطابقة اليومية الشاملة للكميات والمبيعات'), findsOneWidget);

    controller.setSection(2, subTab: 2); // Reconciliation & Settlement
    await tester.pumpAndSettle();
    expect(find.text('جرد المخزون والمطابقة'), findsOneWidget);
    expect(find.text('التحاسب اليومي للصندوق'), findsOneWidget);

    controller.setSection(1, subTab: 3); // Account statements
    await tester.pumpAndSettle();

    // Unlicensed customer starts in BASIC: PRO-only Reports & Archive show upgrade card
    controller.navigateToRoute(AppScreenRoute.reports);
    await tester.pumpAndSettle();
    expect(find.text('ميزة احترافية PRO'), findsOneWidget);

    // Test large phone viewport (412x915) with 6-Tab Bottom Bar & Categorized Drawer
    tester.view.physicalSize = const Size(412, 915);
    await tester.pumpAndSettle();

    controller.navigateToRoute(AppScreenRoute.dashboard);
    await tester.pumpAndSettle();
    expect(find.text('الرئيسية'), findsWidgets);
    expect(find.text('المبيعات'), findsWidgets);
    expect(find.text('العملاء'), findsOneWidget);
    expect(find.text('الرعيّة'), findsOneWidget);
    expect(find.text('الكشف'), findsOneWidget);
    expect(find.text('المزيد'), findsOneWidget);
    expect(find.text('إجراءات سريعة'), findsOneWidget);
    expect(find.text('فاتورة بيع'), findsOneWidget);

    // Open Categorized Drawer and verify all 4 categories
    await tester.tap(find.byKey(const Key('drawer_menu_button')));
    await tester.pumpAndSettle();
    expect(find.text('العمليات اليومية'), findsOneWidget);
    expect(find.text('إدارة النشاط'), findsOneWidget);
    expect(find.text('التقارير والمتابعة'), findsOneWidget);
    expect(find.text('الإعدادات والإدارة'), findsOneWidget);
    await tester.tapAt(const Offset(10, 450));
    await tester.pumpAndSettle();

    // Tablet wide viewport (1024x768) renders NavigationRail
    tester.view.physicalSize = const Size(1024, 768);
    await tester.pumpAndSettle();
    expect(find.byType(NavigationRail), findsOneWidget);

    tester.view.physicalSize = const Size(412, 915);
    await tester.pumpAndSettle();

    // Switch to Pure Black Dark mode + Sapphire Blue preset
    await tester.runAsync(
      () => controller.setThemeChoice(AppThemeModeChoice.darkBlack),
    );
    await tester.runAsync(
      () => controller.setColorPalette(paletteId: 'sapphire_blue'),
    );
    await tester.pumpAndSettle();
    expect(controller.themeMode, ThemeMode.dark);
    expect(controller.settings.paletteId, 'sapphire_blue');

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
    await tester.runAsync(controller.logout);
    final cashierOk = await tester.runAsync<bool>(
      () => controller.login('cashier1', 'pass123'),
    );
    expect(cashierOk, true);
    expect(controller.canDeleteCurrent, false);
    expect(controller.canViewReportsCurrent, false);

    // Navigate to Settings -> License tab: Customer flavor hides Developer Console
    controller.setSection(4, subTab: 3);
    await tester.pumpAndSettle();
    expect(find.text('وحدة تحكم المطور (Developer Edition)'), findsNothing);

    await tester.runAsync(dbService.close);
    tempDir.deleteSync(recursive: true);
  });
}
