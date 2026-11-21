import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../state/app_controller.dart';
import 'inventory/inventory_batches_view.dart';
import 'inventory/purchases_ledger_view.dart';
import 'inventory/qat_types_pricing_view.dart';
import 'inventory/reconciliation_settlement_view.dart';
import 'inventory/suppliers_directory_view.dart';
import 'widgets/common_widgets.dart';
import 'widgets/erp_components.dart';

class InventorySuppliersTab extends StatelessWidget {
  const InventorySuppliersTab({super.key});

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final subTab = ctrl.inventorySubTab.clamp(0, 4).toInt();
    final isTabActive = ctrl.sectionIndex == 2;

    if (!ctrl.sectionPermissions.inventory &&
        ctrl.currentUser?.role.code != 'ADMIN') {
      return const Padding(
        padding: EdgeInsets.all(16),
        child: ErpStatusBanner(
          tone: ErpBannerTone.warning,
          title: 'قسم المخزون والموردين مقيد',
          subtitle: 'تم إخفاء هذا القسم من صلاحيات الأقسام في الإدارة.',
        ),
      );
    }

    return Column(
      children: [
        SegmentedStrip(
          tabs: const [
            'المخزون والدفعات',
            'الأصناف والتسعير',
            'التحاسب والجرد',
            'الموردون',
            'فواتير المشتريات',
          ],
          icons: const [
            Icons.inventory_2_outlined,
            Icons.category_outlined,
            Icons.fact_check_outlined,
            Icons.local_shipping_outlined,
            Icons.shopping_bag_outlined,
          ],
          selectedIndex: subTab,
          onChanged: ctrl.setInventorySubTab,
        ),
        Expanded(
          child: IndexedStack(
            index: subTab,
            children: [
              TickerMode(enabled: isTabActive && subTab == 0, child: const InventoryBatchesView()),
              TickerMode(enabled: isTabActive && subTab == 1, child: const QatTypesPricingView()),
              TickerMode(enabled: isTabActive && subTab == 2, child: const ReconciliationSettlementView()),
              TickerMode(enabled: isTabActive && subTab == 3, child: const SuppliersDirectoryView()),
              TickerMode(enabled: isTabActive && subTab == 4, child: const SupplierPurchasesView()),
            ],
          ),
        ),
      ],
    );
  }
}
