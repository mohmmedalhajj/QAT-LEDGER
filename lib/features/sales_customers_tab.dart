import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../state/app_controller.dart';
import 'sales/account_statement_view.dart';
import 'sales/customers_debt_view.dart';
import 'sales/pos_invoice_view.dart';
import 'sales/sales_ledger_view.dart';
import 'widgets/common_widgets.dart';
import 'widgets/erp_components.dart';

class SalesCustomersTab extends StatefulWidget {
  const SalesCustomersTab({super.key});

  @override
  State<SalesCustomersTab> createState() => _SalesCustomersTabState();
}

class _SalesCustomersTabState extends State<SalesCustomersTab> {
  int? _statementCustomerId;

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final subTab = ctrl.salesSubTab.clamp(0, 3).toInt();
    final isTabActive = ctrl.sectionIndex == 1;

    if (!ctrl.sectionPermissions.sales && ctrl.currentUser?.role.code != 'ADMIN') {
      return const Padding(
        padding: EdgeInsets.all(16),
        child: ErpStatusBanner(
          tone: ErpBannerTone.warning,
          title: 'قسم المبيعات والعملاء مقيد',
          subtitle: 'تم إخفاء هذا القسم من صلاحيات الأقسام في الإدارة.',
        ),
      );
    }

    return Column(
      children: [
        SegmentedStrip(
          tabs: const [
            'فاتورة جديدة',
            'سجل المبيعات',
            'العملاء والديون',
            'كشف الحساب',
          ],
          icons: const [
            Icons.point_of_sale_outlined,
            Icons.receipt_long_outlined,
            Icons.people_outline,
            Icons.table_chart_outlined,
          ],
          selectedIndex: subTab,
          onChanged: ctrl.setSalesSubTab,
        ),
        Expanded(
          child: IndexedStack(
            index: subTab,
            children: [
              TickerMode(enabled: isTabActive && subTab == 0, child: const PosInvoiceBuilderView()),
              TickerMode(enabled: isTabActive && subTab == 1, child: const SalesLedgerView()),
              TickerMode(
                enabled: isTabActive && subTab == 2,
                child: CustomersDirectoryView(
                  onOpenStatement: (customerId) {
                    setState(() => _statementCustomerId = customerId);
                    ctrl.setSalesSubTab(3);
                  },
                ),
              ),
              TickerMode(
                enabled: isTabActive && subTab == 3,
                child: AccountStatementView(
                  preselectedCustomerId:
                      _statementCustomerId ?? ctrl.statementCustomerId,
                  preselectedSupplierId: ctrl.statementSupplierId,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}
