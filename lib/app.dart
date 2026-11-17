import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import 'core/theme.dart';
import 'features/dashboard_tab.dart';
import 'features/expenses_settings_tab.dart';
import 'features/inventory_suppliers_tab.dart';
import 'features/sales_customers_tab.dart';
import 'features/splash_login_onboarding.dart';
import 'features/transfers_reports_tab.dart';
import 'features/widgets/erp_components.dart';
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
    'لوحة التحكم الرئيسية',
    'المبيعات والعملاء',
    'المخزون والمشتريات والجرد',
    'الحوالات والتقارير والأرشيف',
    'المالية وإعدادات النظام',
  ];

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final pal = context.pal;
    final idx = ctrl.sectionIndex;
    final wide = MediaQuery.sizeOf(context).width >= 820;

    final bodyStack = IndexedStack(
      index: idx,
      children: [
        TickerMode(enabled: idx == 0, child: const DashboardTab()),
        TickerMode(enabled: idx == 1, child: const SalesCustomersTab()),
        TickerMode(enabled: idx == 2, child: const InventorySuppliersTab()),
        TickerMode(enabled: idx == 3, child: const TransfersReportsTab()),
        TickerMode(enabled: idx == 4, child: const ExpensesSettingsTab()),
      ],
    );

    return Scaffold(
      appBar: AppBar(
        titleSpacing: 6,
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
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    _titles[idx],
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 15.5, fontWeight: FontWeight.w700),
                  ),
                  Text(
                    '${ctrl.settings.agencyName} • ${ctrl.currentUser?.displayName ?? 'النظام'}',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(fontSize: 11, color: pal.textSecondary),
                  ),
                ],
              ),
            ),
          ],
        ),
        actions: [
          if (wide)
            Padding(
              padding: const EdgeInsetsDirectional.only(end: 6),
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                decoration: BoxDecoration(
                  color: pal.brandSoft,
                  borderRadius: BorderRadius.circular(20),
                ),
                child: Text(
                  ctrl.currencyPolicy.mode.arabicLabel,
                  style: TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.w700,
                    color: pal.brand,
                  ),
                ),
              ),
            ),
          IconButton(
            tooltip: pal.isDark ? 'التبديل للفاتح' : 'التبديل للأسود الداكن',
            onPressed: () => ctrl.setThemeChoice(
              pal.isDark
                  ? AppThemeModeChoice.lightEnterprise
                  : AppThemeModeChoice.darkBlack,
            ),
            icon: Icon(
              pal.isDark ? Icons.light_mode_outlined : Icons.dark_mode_outlined,
            ),
          ),
          IconButton(
            tooltip: 'تحديث البيانات',
            onPressed: ctrl.reloadAll,
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      drawer: _ErpNavigationDrawer(ctrl: ctrl),
      body: wide
          ? Row(
              children: [
                NavigationRail(
                  selectedIndex: idx,
                  extended: MediaQuery.sizeOf(context).width >= 1060,
                  labelType: MediaQuery.sizeOf(context).width >= 1060
                      ? NavigationRailLabelType.none
                      : NavigationRailLabelType.all,
                  onDestinationSelected: (v) => ctrl.setSection(v),
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
                VerticalDivider(width: 1, thickness: 1, color: pal.border),
                Expanded(child: bodyStack),
              ],
            )
          : bodyStack,
      bottomNavigationBar: wide
          ? null
          : NavigationBar(
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

class _ErpNavigationDrawer extends StatelessWidget {
  const _ErpNavigationDrawer({required this.ctrl});
  final AppController ctrl;

  @override
  Widget build(BuildContext context) {
    final pal = context.pal;
    final user = ctrl.currentUser;

    void go(int section, [int? subTab]) {
      Navigator.pop(context);
      ctrl.setSection(section, subTab: subTab);
    }

    return Drawer(
      width: 300,
      child: SafeArea(
        child: Column(
          children: [
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: pal.brandSoft,
                border: Border(bottom: BorderSide(color: pal.border)),
              ),
              child: Row(
                children: [
                  ClipOval(
                    child: Image.asset(
                      'assets/images/app_logo.png',
                      width: 44,
                      height: 44,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          ctrl.settings.agencyName,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            fontSize: 15,
                            fontWeight: FontWeight.w700,
                            color: pal.textPrimary,
                          ),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          '${user?.displayName ?? 'admin'} • ${user?.role.arabicLabel ?? 'مدير'}',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            fontSize: 11.5,
                            color: pal.brand,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            Expanded(
              child: ListView(
                padding: const EdgeInsets.symmetric(vertical: 8),
                children: [
                  const _DrawerGroupLabel('لوحة التحكم والعمليات'),
                  _DrawerTile(
                    icon: Icons.dashboard_outlined,
                    label: 'لوحة التحكم الرئيسية',
                    selected: ctrl.sectionIndex == 0,
                    onTap: () => go(0),
                  ),
                  _DrawerTile(
                    icon: Icons.point_of_sale_outlined,
                    label: 'نقطة البيع وفاتورة جديدة',
                    selected: ctrl.sectionIndex == 1 && ctrl.salesSubTab == 0,
                    onTap: () => go(1, 0),
                  ),
                  _DrawerTile(
                    icon: Icons.receipt_long_outlined,
                    label: 'سجل المبيعات والفواتير',
                    selected: ctrl.sectionIndex == 1 && ctrl.salesSubTab == 1,
                    onTap: () => go(1, 1),
                  ),
                  _DrawerTile(
                    icon: Icons.fact_check_outlined,
                    label: 'التحاسب والجرد اليومي',
                    selected: ctrl.sectionIndex == 2 && ctrl.inventorySubTab == 2,
                    onTap: () => go(2, 2),
                  ),
                  const _DrawerGroupLabel('الأطراف والذمم والكشوفات'),
                  _DrawerTile(
                    icon: Icons.people_outline,
                    label: 'العملاء والديون والسندات',
                    selected: ctrl.sectionIndex == 1 && ctrl.salesSubTab == 2,
                    onTap: () => go(1, 2),
                  ),
                  _DrawerTile(
                    icon: Icons.table_chart_outlined,
                    label: 'كشوفات الحساب التفصيلية',
                    selected: ctrl.sectionIndex == 1 && ctrl.salesSubTab == 3,
                    onTap: () => go(1, 3),
                  ),
                  _DrawerTile(
                    icon: Icons.local_shipping_outlined,
                    label: 'الموردون وحساباتهم',
                    selected: ctrl.sectionIndex == 2 && ctrl.inventorySubTab == 3,
                    onTap: () => go(2, 3),
                  ),
                  _DrawerTile(
                    icon: Icons.shopping_bag_outlined,
                    label: 'فواتير المشتريات والتوريد',
                    selected: ctrl.sectionIndex == 2 && ctrl.inventorySubTab == 4,
                    onTap: () => go(2, 4),
                  ),
                  const _DrawerGroupLabel('المخزون والحوالات'),
                  _DrawerTile(
                    icon: Icons.inventory_2_outlined,
                    label: 'دفعات المخزون والرصيد',
                    selected: ctrl.sectionIndex == 2 && ctrl.inventorySubTab == 0,
                    onTap: () => go(2, 0),
                  ),
                  _DrawerTile(
                    icon: Icons.category_outlined,
                    label: 'أنواع القات والتسعير',
                    selected: ctrl.sectionIndex == 2 && ctrl.inventorySubTab == 1,
                    onTap: () => go(2, 1),
                  ),
                  _DrawerTile(
                    icon: Icons.swap_horiz_outlined,
                    label: 'الحوالات المالية والصرافة',
                    selected: ctrl.sectionIndex == 3 && ctrl.transfersSubTab == 0,
                    onTap: () => go(3, 0),
                  ),
                  _DrawerTile(
                    icon: Icons.badge_outlined,
                    label: 'المستلمون وتقارير الحوالات',
                    selected: ctrl.sectionIndex == 3 && ctrl.transfersSubTab == 1,
                    onTap: () => go(3, 1),
                  ),
                  const _DrawerGroupLabel('المالية والتقارير والأرشيف'),
                  _DrawerTile(
                    icon: Icons.account_balance_wallet_outlined,
                    label: 'المصاريف والصندوق والحسابات',
                    selected: ctrl.sectionIndex == 4 && ctrl.settingsSubTab == 0,
                    onTap: () => go(4, 0),
                  ),
                  _DrawerTile(
                    icon: Icons.analytics_outlined,
                    label: 'التقارير المالية والأرباح',
                    selected: ctrl.sectionIndex == 3 && ctrl.transfersSubTab == 2,
                    onTap: () => go(3, 2),
                  ),
                  _DrawerTile(
                    icon: Icons.gavel_outlined,
                    label: 'دفتر ضرائب القات',
                    selected: ctrl.sectionIndex == 3 && ctrl.transfersSubTab == 3,
                    onTap: () => go(3, 3),
                  ),
                  _DrawerTile(
                    icon: Icons.archive_outlined,
                    label: 'الأرشيف المحاسبي',
                    selected: ctrl.sectionIndex == 3 && ctrl.transfersSubTab == 4,
                    onTap: () => go(3, 4),
                  ),
                  const _DrawerGroupLabel('الإدارة والنظام'),
                  _DrawerTile(
                    icon: Icons.store_outlined,
                    label: 'الهوية والعملة والمظهر',
                    selected: ctrl.sectionIndex == 4 && ctrl.settingsSubTab == 1,
                    onTap: () => go(4, 1),
                  ),
                  _DrawerTile(
                    icon: Icons.backup_outlined,
                    label: 'النسخ الاحتياطي والطباعة',
                    selected: ctrl.sectionIndex == 4 && ctrl.settingsSubTab == 2,
                    onTap: () => go(4, 2),
                  ),
                  _DrawerTile(
                    icon: Icons.verified_user_outlined,
                    label: 'التراخيص والمستخدمون والمطور',
                    selected: ctrl.sectionIndex == 4 && ctrl.settingsSubTab == 3,
                    onTap: () => go(4, 3),
                  ),
                ],
              ),
            ),
            Divider(color: pal.border, height: 1),
            ListTile(
              leading: Icon(Icons.logout, color: pal.danger),
              title: Text(
                'تسجيل الخروج',
                style: TextStyle(
                  color: pal.danger,
                  fontWeight: FontWeight.w700,
                  fontSize: 13.5,
                ),
              ),
              onTap: () async {
                final ok = await showErpConfirmDialog(
                  context,
                  title: 'تسجيل الخروج',
                  message: 'هل تريد إغلاق الجلسة الحالية والعودة لشاشة الدخول؟',
                  confirmLabel: 'خروج',
                );
                if (!ok || !context.mounted) return;
                Navigator.pop(context);
                ctrl.logout();
              },
            ),
          ],
        ),
      ),
    );
  }
}

class _DrawerGroupLabel extends StatelessWidget {
  const _DrawerGroupLabel(this.title);
  final String title;

  @override
  Widget build(BuildContext context) {
    final pal = context.pal;
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
      child: Text(
        title,
        style: TextStyle(
          fontSize: 11,
          fontWeight: FontWeight.w700,
          color: pal.accent,
        ),
      ),
    );
  }
}

class _DrawerTile extends StatelessWidget {
  const _DrawerTile({
    required this.icon,
    required this.label,
    required this.selected,
    required this.onTap,
  });

  final IconData icon;
  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final pal = context.pal;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 1),
      child: ListTile(
        dense: true,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
        selected: selected,
        selectedTileColor: pal.brandSoft,
        leading: Icon(
          icon,
          size: 20,
          color: selected ? pal.brand : pal.textSecondary,
        ),
        title: Text(
          label,
          style: TextStyle(
            fontSize: 13,
            fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
            color: selected ? pal.brand : pal.textPrimary,
          ),
        ),
        onTap: onTap,
      ),
    );
  }
}
