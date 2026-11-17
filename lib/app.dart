import 'dart:io';

import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import 'core/money.dart';
import 'core/theme.dart';
import 'developer/developer_console_sheet.dart';
import 'features/dashboard_tab.dart';
import 'features/expenses_settings_tab.dart';
import 'features/inventory_suppliers_tab.dart';
import 'features/sales_customers_tab.dart';
import 'features/splash_login_onboarding.dart';
import 'features/transfers_reports_tab.dart';
import 'licensing/license_models.dart';
import 'state/app_controller.dart';

class QatLedgerApp extends StatelessWidget {
  const QatLedgerApp({super.key});

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final s = ctrl.settings;
    final preset = s.activeColorPreset;

    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: s.effectiveAppTitle,
      locale: const Locale('ar'),
      supportedLocales: const [Locale('ar')],
      localizationsDelegates: const [
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      theme: AppTheme.buildLight(
        preset: preset,
        customCanvasHex: s.customCanvasHex,
        customCardHex: s.customCardHex,
        customTextHex: s.customTextHex,
      ),
      darkTheme: AppTheme.buildDark(
        preset: preset,
        customCanvasHex: s.customCanvasHex,
        customCardHex: s.customCardHex,
        customTextHex: s.customTextHex,
      ),
      themeMode: ctrl.themeMode,
      builder: (context, child) {
        return Directionality(
          textDirection: TextDirection.rtl,
          child: child ?? const SizedBox.shrink(),
        );
      },
      home: const _RootGate(),
    );
  }
}

class _RootGate extends StatelessWidget {
  const _RootGate();

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    if (!ctrl.initialized) {
      return const SplashLoadingScreen();
    }
    if (!ctrl.isLoggedIn) {
      return const LoginScreen();
    }
    if (!ctrl.settings.onboardingComplete) {
      return const OnboardingWizardScreen();
    }
    return const MainShellScreen();
  }
}

class MainShellScreen extends StatefulWidget {
  const MainShellScreen({super.key});

  @override
  State<MainShellScreen> createState() => _MainShellScreenState();
}

class _MainShellScreenState extends State<MainShellScreen> {
  final _scaffoldKey = GlobalKey<ScaffoldState>();

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final pal = context.pal;
    final width = MediaQuery.sizeOf(context).width;
    final wide = width >= 820;

    final bodyContent = IndexedStack(
      index: ctrl.sectionIndex,
      children: [
        TickerMode(enabled: ctrl.sectionIndex == 0, child: const DashboardTab()),
        TickerMode(
          enabled: ctrl.sectionIndex == 1,
          child: const SalesCustomersTab(),
        ),
        TickerMode(
          enabled: ctrl.sectionIndex == 2,
          child: const InventorySuppliersTab(),
        ),
        TickerMode(
          enabled: ctrl.sectionIndex == 3,
          child: const TransfersReportsTab(),
        ),
        TickerMode(
          enabled: ctrl.sectionIndex == 4,
          child: const ExpensesSettingsTab(),
        ),
      ],
    );

    final isDark = ctrl.settings.themeChoice == AppThemeModeChoice.darkBlack;

    return Scaffold(
      key: _scaffoldKey,
      drawer: _ErpCategorizedDrawer(
        onClose: () => _scaffoldKey.currentState?.closeDrawer(),
      ),
      appBar: AppBar(
        leading: IconButton(
          key: const Key('drawer_menu_button'),
          tooltip: 'القائمة الرئيسية',
          icon: const Icon(Icons.menu_rounded),
          onPressed: () => _scaffoldKey.currentState?.openDrawer(),
        ),
        titleSpacing: 4,
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              ctrl.activeRoute == AppScreenRoute.dashboard
                  ? ctrl.settings.effectiveAppTitle
                  : ctrl.activeRoute.arabicTitle,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
            Text(
              ctrl.activeRoute == AppScreenRoute.dashboard
                  ? '${ctrl.currencyPolicy.mode.arabicLabel} • ${ctrl.licenseStatus.isPro ? 'PRO' : 'BASIC'}'
                  : ctrl.settings.effectiveAppTitle,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.bodySmall?.copyWith(fontSize: 11),
            ),
          ],
        ),
        actions: [
          if (width >= 400)
            Padding(
              padding: const EdgeInsetsDirectional.only(end: 2),
              child: Chip(
                visualDensity: VisualDensity.compact,
                side: BorderSide(color: pal.border),
                backgroundColor: pal.brandSoft,
                label: Text(
                  ctrl.currencyPolicy.mode == CurrencyMode.dualCurrency
                      ? 'YER / SAR'
                      : ctrl.currencyPolicy.defaultCurrency.code,
                  style: TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.w700,
                    color: pal.brand,
                  ),
                ),
              ),
            ),
          IconButton(
            tooltip: isDark ? 'تبديل للوضع الفاتح' : 'تبديل للوضع الأسود الداكن',
            onPressed: () => ctrl.setThemeChoice(
              isDark
                  ? AppThemeModeChoice.lightEnterprise
                  : AppThemeModeChoice.darkBlack,
            ),
            icon: Icon(
              isDark ? Icons.light_mode_outlined : Icons.dark_mode_outlined,
            ),
          ),
          IconButton(
            tooltip: 'تحديث البيانات',
            onPressed: ctrl.reloadAll,
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: wide
          ? Row(
              children: [
                NavigationRail(
                  selectedIndex: ctrl.sectionIndex,
                  onDestinationSelected: ctrl.setSection,
                  labelType: NavigationRailLabelType.all,
                  leading: Padding(
                    padding: const EdgeInsets.symmetric(vertical: 10),
                    child: IconButton.filledTonal(
                      tooltip: 'كافة الشاشات (12 شاشة)',
                      onPressed: () => _scaffoldKey.currentState?.openDrawer(),
                      icon: const Icon(Icons.grid_view_rounded),
                    ),
                  ),
                  destinations: const [
                    NavigationRailDestination(
                      icon: Icon(Icons.dashboard_outlined),
                      selectedIcon: Icon(Icons.dashboard),
                      label: Text('الرئيسية'),
                    ),
                    NavigationRailDestination(
                      icon: Icon(Icons.point_of_sale_outlined),
                      selectedIcon: Icon(Icons.point_of_sale),
                      label: Text('المبيعات'),
                    ),
                    NavigationRailDestination(
                      icon: Icon(Icons.inventory_2_outlined),
                      selectedIcon: Icon(Icons.inventory_2),
                      label: Text('المخزون'),
                    ),
                    NavigationRailDestination(
                      icon: Icon(Icons.swap_horiz_outlined),
                      selectedIcon: Icon(Icons.swap_horiz),
                      label: Text('الحوالات'),
                    ),
                    NavigationRailDestination(
                      icon: Icon(Icons.settings_outlined),
                      selectedIcon: Icon(Icons.settings),
                      label: Text('الإعدادات'),
                    ),
                  ],
                ),
                const VerticalDivider(width: 1),
                Expanded(child: bodyContent),
              ],
            )
          : bodyContent,
      bottomNavigationBar: wide
          ? null
          : _QatAgencyBottomBar(
              activeRoute: ctrl.activeRoute,
              onRouteSelected: (route) => ctrl.navigateToRoute(route),
              onMorePressed: () => _scaffoldKey.currentState?.openDrawer(),
            ),
    );
  }
}

/// Exact 6-Tab Bottom Bar matching original Android `QatAgencyBottomBar`:
/// الرئيسية • المبيعات • العملاء • الرعيّة • الكشف • المزيد (opens categorized drawer).
class _QatAgencyBottomBar extends StatelessWidget {
  const _QatAgencyBottomBar({
    required this.activeRoute,
    required this.onRouteSelected,
    required this.onMorePressed,
  });

  final AppScreenRoute activeRoute;
  final ValueChanged<AppScreenRoute> onRouteSelected;
  final VoidCallback onMorePressed;

  static const _bottomTabs = <(AppScreenRoute, String, IconData)>[
    (AppScreenRoute.dashboard, 'الرئيسية', Icons.dashboard_rounded),
    (AppScreenRoute.sales, 'المبيعات', Icons.point_of_sale_rounded),
    (AppScreenRoute.customers, 'العملاء', Icons.people_rounded),
    (AppScreenRoute.suppliers, 'الرعيّة', Icons.local_shipping_rounded),
    (AppScreenRoute.statement, 'الكشف', Icons.description_rounded),
  ];

  @override
  Widget build(BuildContext context) {
    final pal = context.pal;
    final isMoreActive =
        !_bottomTabs.any((entry) => entry.$1 == activeRoute);

    return Material(
      color: pal.card,
      elevation: 0,
      child: Container(
        decoration: BoxDecoration(
          border: Border(top: BorderSide(color: pal.border)),
        ),
        child: SafeArea(
          top: false,
          child: SizedBox(
            height: 62,
            child: Row(
              children: [
                for (final item in _bottomTabs)
                  Expanded(
                    child: _BottomBarItem(
                      label: item.$2,
                      icon: item.$3,
                      selected: activeRoute == item.$1,
                      onTap: () => onRouteSelected(item.$1),
                    ),
                  ),
                Expanded(
                  child: _BottomBarItem(
                    label: 'المزيد',
                    icon: Icons.menu_rounded,
                    selected: isMoreActive,
                    onTap: onMorePressed,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _BottomBarItem extends StatelessWidget {
  const _BottomBarItem({
    required this.label,
    required this.icon,
    required this.selected,
    required this.onTap,
  });

  final String label;
  final IconData icon;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final pal = context.pal;
    final color = selected ? pal.brand : pal.textSecondary;
    return InkWell(
      onTap: onTap,
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
            decoration: BoxDecoration(
              color: selected ? pal.brandSoft : Colors.transparent,
              borderRadius: BorderRadius.circular(12),
            ),
            child: Icon(icon, size: 20, color: color),
          ),
          const SizedBox(height: 2),
          Text(
            label,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              fontSize: 10.5,
              fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
              color: color,
            ),
          ),
        ],
      ),
    );
  }
}

/// Official 4-Group Categorized Drawer matching original Android `MainActivity.kt`.
class _ErpCategorizedDrawer extends StatelessWidget {
  const _ErpCategorizedDrawer({required this.onClose});

  final VoidCallback onClose;

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final pal = context.pal;
    final logoPath = ctrl.settings.logoPath;
    final ImageProvider<Object> logoProvider =
        (logoPath.isNotEmpty && File(logoPath).existsSync())
            ? FileImage(File(logoPath))
            : const AssetImage('assets/images/app_logo.png');

    void go(AppScreenRoute route) {
      onClose();
      ctrl.navigateToRoute(route);
    }

    return Drawer(
      child: SafeArea(
        child: ListView(
          padding: const EdgeInsets.symmetric(vertical: 8),
          children: [
            Container(
              margin: const EdgeInsets.fromLTRB(12, 4, 12, 10),
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: pal.brandSoft,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: pal.brand.withValues(alpha: 0.25)),
              ),
              child: Row(
                children: [
                  CircleAvatar(radius: 24, backgroundImage: logoProvider),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          ctrl.settings.effectiveAppTitle,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontWeight: FontWeight.w700,
                            fontSize: 14.5,
                          ),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          '${ctrl.currentUser?.displayName ?? 'admin'} • ${ctrl.licenseStatus.isPro ? 'الباقة الاحترافية PRO' : 'الباقة الأساسية BASIC'}',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            fontSize: 11.5,
                            color: pal.textSecondary,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            const _DrawerGroupHeader('العمليات اليومية'),
            _DrawerRouteTile(
              route: AppScreenRoute.dashboard,
              selected: ctrl.activeRoute == AppScreenRoute.dashboard,
              onTap: () => go(AppScreenRoute.dashboard),
            ),
            _DrawerRouteTile(
              route: AppScreenRoute.sales,
              selected: ctrl.activeRoute == AppScreenRoute.sales,
              onTap: () => go(AppScreenRoute.sales),
            ),
            _DrawerRouteTile(
              route: AppScreenRoute.customers,
              selected: ctrl.activeRoute == AppScreenRoute.customers,
              onTap: () => go(AppScreenRoute.customers),
            ),
            _DrawerRouteTile(
              route: AppScreenRoute.statement,
              selected: ctrl.activeRoute == AppScreenRoute.statement,
              onTap: () => go(AppScreenRoute.statement),
            ),
            const Divider(height: 14),
            const _DrawerGroupHeader('إدارة النشاط'),
            _DrawerRouteTile(
              route: AppScreenRoute.suppliers,
              selected: ctrl.activeRoute == AppScreenRoute.suppliers,
              onTap: () => go(AppScreenRoute.suppliers),
            ),
            _DrawerRouteTile(
              route: AppScreenRoute.inventory,
              selected: ctrl.activeRoute == AppScreenRoute.inventory,
              onTap: () => go(AppScreenRoute.inventory),
            ),
            _DrawerRouteTile(
              route: AppScreenRoute.accounts,
              selected: ctrl.activeRoute == AppScreenRoute.accounts,
              onTap: () => go(AppScreenRoute.accounts),
            ),
            _DrawerRouteTile(
              route: AppScreenRoute.transfers,
              selected: ctrl.activeRoute == AppScreenRoute.transfers,
              onTap: () => go(AppScreenRoute.transfers),
            ),
            _DrawerRouteTile(
              route: AppScreenRoute.expenses,
              selected: ctrl.activeRoute == AppScreenRoute.expenses,
              onTap: () => go(AppScreenRoute.expenses),
            ),
            const Divider(height: 14),
            const _DrawerGroupHeader('التقارير والمتابعة'),
            _DrawerRouteTile(
              route: AppScreenRoute.reports,
              selected: ctrl.activeRoute == AppScreenRoute.reports,
              proLocked: !ctrl.hasLicenseFeature(LicenseFeature.reports),
              onTap: () => go(AppScreenRoute.reports),
            ),
            _DrawerRouteTile(
              route: AppScreenRoute.archive,
              selected: ctrl.activeRoute == AppScreenRoute.archive,
              proLocked: !ctrl.hasLicenseFeature(LicenseFeature.archive),
              onTap: () => go(AppScreenRoute.archive),
            ),
            const Divider(height: 14),
            const _DrawerGroupHeader('الإعدادات والإدارة'),
            _DrawerRouteTile(
              route: AppScreenRoute.settings,
              selected: ctrl.activeRoute == AppScreenRoute.settings,
              onTap: () => go(AppScreenRoute.settings),
            ),
            if (ctrl.isDeveloperEdition && ctrl.developerKeyManager != null)
              ListTile(
                dense: true,
                leading: Icon(Icons.developer_mode, color: pal.accent),
                title: const Text('لوحة تحكم المطور (الإصدار الخاص)'),
                onTap: () async {
                  onClose();
                  await Navigator.of(context).push<void>(
                    MaterialPageRoute<void>(
                      builder: (_) => DeveloperConsoleSheet(
                        keyManager: ctrl.developerKeyManager!,
                        devDb: ctrl.developerDb,
                        onKeysUpdated: ctrl.refreshLicense,
                      ),
                    ),
                  );
                },
              ),
            ListTile(
              dense: true,
              leading: Icon(Icons.logout, color: pal.danger),
              title: Text(
                'قفل وتسجيل الخروج',
                style: TextStyle(color: pal.danger),
              ),
              onTap: () {
                onClose();
                ctrl.logout();
              },
            ),
          ],
        ),
      ),
    );
  }
}

class _DrawerGroupHeader extends StatelessWidget {
  const _DrawerGroupHeader(this.title);
  final String title;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 4, 16, 2),
      child: Text(
        title,
        style: TextStyle(
          fontSize: 11.5,
          fontWeight: FontWeight.w700,
          color: context.pal.brand,
        ),
      ),
    );
  }
}

class _DrawerRouteTile extends StatelessWidget {
  const _DrawerRouteTile({
    required this.route,
    required this.selected,
    required this.onTap,
    this.proLocked = false,
  });

  final AppScreenRoute route;
  final bool selected;
  final VoidCallback onTap;
  final bool proLocked;

  @override
  Widget build(BuildContext context) {
    final pal = context.pal;
    return ListTile(
      dense: true,
      selected: selected,
      selectedTileColor: pal.brandSoft,
      leading: Icon(
        route.icon,
        size: 20,
        color: selected ? pal.brand : pal.textSecondary,
      ),
      title: Text(
        route.arabicTitle,
        style: TextStyle(
          fontSize: 13,
          fontWeight: selected ? FontWeight.w700 : FontWeight.w600,
        ),
      ),
      trailing: proLocked
          ? Container(
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
              decoration: BoxDecoration(
                color: pal.warningSoft,
                borderRadius: BorderRadius.circular(6),
              ),
              child: Text(
                'PRO',
                style: TextStyle(
                  fontSize: 10,
                  fontWeight: FontWeight.w700,
                  color: pal.warning,
                ),
              ),
            )
          : null,
      onTap: onTap,
    );
  }
}
