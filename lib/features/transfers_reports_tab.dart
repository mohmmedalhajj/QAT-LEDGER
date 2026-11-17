import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../state/app_controller.dart';
import 'transfers/archive_center_view.dart';
import 'transfers/financial_reports_view.dart';
import 'transfers/recipients_reports_view.dart';
import 'transfers/tax_ledger_view.dart';
import 'transfers/transfers_ledger_view.dart';
import 'widgets/common_widgets.dart';
import 'widgets/erp_components.dart';

class TransfersReportsTab extends StatelessWidget {
  const TransfersReportsTab({super.key});

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final subTab = ctrl.transfersSubTab.clamp(0, 4).toInt();

    if (!ctrl.sectionPermissions.transfers &&
        subTab < 2 &&
        ctrl.currentUser?.role.code != 'ADMIN') {
      return const Padding(
        padding: EdgeInsets.all(16),
        child: ErpStatusBanner(
          tone: ErpBannerTone.warning,
          title: 'قسم الحوالات مقيد',
          subtitle: 'تم إخفاء هذا القسم من صلاحيات الأقسام في الإدارة.',
        ),
      );
    }

    return Column(
      children: [
        SegmentedStrip(
          tabs: const [
            'الحوالات والصرافة',
            'المستلمون وتقاريرهم',
            'التقارير المالية',
            'الضرائب',
            'الأرشيف',
          ],
          icons: const [
            Icons.swap_horiz_outlined,
            Icons.badge_outlined,
            Icons.analytics_outlined,
            Icons.gavel_outlined,
            Icons.archive_outlined,
          ],
          selectedIndex: subTab,
          onChanged: ctrl.setTransfersSubTab,
        ),
        const Expanded(
          child: _TransfersTabBody(),
        ),
      ],
    );
  }
}

class _TransfersTabBody extends StatelessWidget {
  const _TransfersTabBody();

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final subTab = ctrl.transfersSubTab.clamp(0, 4).toInt();
    final isTabActive = ctrl.sectionIndex == 3;
    return IndexedStack(
      index: subTab,
      children: [
        TickerMode(enabled: isTabActive && subTab == 0, child: const TransfersLedgerView()),
        TickerMode(enabled: isTabActive && subTab == 1, child: const RecipientsAndReportsView()),
        TickerMode(enabled: isTabActive && subTab == 2, child: const FinancialReportsView()),
        TickerMode(enabled: isTabActive && subTab == 3, child: const TaxLedgerView()),
        TickerMode(enabled: isTabActive && subTab == 4, child: const ArchiveCenterView()),
      ],
    );
  }
}
