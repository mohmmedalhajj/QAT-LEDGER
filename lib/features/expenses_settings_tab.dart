import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../state/app_controller.dart';
import 'settings/backup_printer_view.dart';
import 'settings/branding_currency_theme_view.dart';
import 'settings/expenses_accounts_view.dart';
import 'settings/license_security_view.dart';
import 'widgets/common_widgets.dart';

class ExpensesSettingsTab extends StatelessWidget {
  const ExpensesSettingsTab({super.key});

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final subTab = ctrl.settingsSubTab.clamp(0, 3).toInt();

    return Column(
      children: [
        SegmentedStrip(
          tabs: const [
            'المصاريف والحسابات',
            'الهوية والعملة والمظهر',
            'النسخ والطباعة',
            'الترخيص والأمان',
          ],
          icons: const [
            Icons.payments_outlined,
            Icons.palette_outlined,
            Icons.backup_outlined,
            Icons.verified_user_outlined,
          ],
          selectedIndex: subTab,
          onChanged: ctrl.setSettingsSubTab,
        ),
        const Expanded(
          child: _SettingsTabBody(),
        ),
      ],
    );
  }
}

class _SettingsTabBody extends StatelessWidget {
  const _SettingsTabBody();

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final subTab = ctrl.settingsSubTab.clamp(0, 3).toInt();
    final isTabActive = ctrl.sectionIndex == 4;
    return IndexedStack(
      index: subTab,
      children: [
        TickerMode(enabled: isTabActive && subTab == 0, child: const ExpensesAndAccountsView()),
        TickerMode(enabled: isTabActive && subTab == 1, child: const BrandingCurrencyThemeView()),
        TickerMode(enabled: isTabActive && subTab == 2, child: const BackupAndPrinterView()),
        TickerMode(enabled: isTabActive && subTab == 3, child: const LicenseAndSecurityView()),
      ],
    );
  }
}
