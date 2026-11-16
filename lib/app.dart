import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import 'core/theme.dart';
import 'features/dashboard_tab.dart';
import 'features/expenses_settings_tab.dart';
import 'features/inventory_suppliers_tab.dart';
import 'features/sales_customers_tab.dart';
import 'features/splash_login_onboarding.dart';
import 'features/transfers_reports_tab.dart';
import 'state/app_controller.dart';

class QatLedgerApp extends StatelessWidget {
  const QatLedgerApp({super.key});

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();

    return MaterialApp(
      title: 'ميزان القات',
      debugShowCheckedModeBanner: false,
      locale: const Locale('ar'),
      supportedLocales: const [Locale('ar'), Locale('en')],
      localizationsDelegates: GlobalMaterialLocalizations.delegates,
      theme: AppTheme.buildLight(),
      darkTheme: AppTheme.buildDark(),
      themeMode: ctrl.themeMode,
      builder: (context, child) => Directionality(
        textDirection: TextDirection.rtl,
        child: child ?? const SizedBox.shrink(),
      ),
      home: _resolveRootScreen(ctrl),
    );
  }

  Widget _resolveRootScreen(AppController ctrl) {
    if (!ctrl.initialized) return const SplashLoadingView();
    if (!ctrl.isLoggedIn) return const LoginScreen();
    if (!ctrl.settings.onboardingComplete) return const AgencyOnboardingScreen();
    return const MainShellScreen();
  }
}

class MainShellScreen extends StatelessWidget {
  const MainShellScreen({super.key});

  static const _titles = [
    'الرئيسية',
    'المبيعات والعملاء',
    'المخزون والموردون',
    'الحوالات والتقارير',
    'المصاريف والإعدادات',
  ];

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final pal = context.pal;
    final idx = ctrl.sectionIndex;

    return Scaffold(
      appBar: AppBar(
        title: Row(
          children: [
            ClipOval(
              child: Image.asset(
                'assets/images/app_logo.png',
                width: 30,
                height: 30,
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                _titles[idx],
                overflow: TextOverflow.ellipsis,
              ),
            ),
          ],
        ),
        actions: [
          IconButton(
            tooltip: pal.isDark ? 'التبديل للفاتح' : 'التبديل للأسود الداكن',
            onPressed: () => ctrl.setThemeChoice(
              pal.isDark
                  ? AppThemeModeChoice.lightEnterprise
                  : AppThemeModeChoice.darkBlack,
            ),
            icon: Icon(pal.isDark ? Icons.light_mode_outlined : Icons.dark_mode_outlined),
          ),
          IconButton(
            tooltip: 'تحديث',
            onPressed: ctrl.reloadAll,
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: IndexedStack(
        index: idx,
        children: const [
          DashboardTab(),
          SalesCustomersTab(),
          InventorySuppliersTab(),
          TransfersReportsTab(),
          ExpensesSettingsTab(),
        ],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: idx,
        onDestinationSelected: (v) => ctrl.setSection(v),
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.dashboard_outlined),
            selectedIcon: Icon(Icons.dashboard),
            label: 'الرئيسية',
          ),
          NavigationDestination(
            icon: Icon(Icons.point_of_sale_outlined),
            selectedIcon: Icon(Icons.point_of_sale),
            label: 'المبيعات',
          ),
          NavigationDestination(
            icon: Icon(Icons.inventory_2_outlined),
            selectedIcon: Icon(Icons.inventory_2),
            label: 'المخزون',
          ),
          NavigationDestination(
            icon: Icon(Icons.swap_horiz_outlined),
            selectedIcon: Icon(Icons.swap_horiz),
            label: 'الحوالات',
          ),
          NavigationDestination(
            icon: Icon(Icons.settings_outlined),
            selectedIcon: Icon(Icons.settings),
            label: 'الإعدادات',
          ),
        ],
      ),
    );
  }
}
