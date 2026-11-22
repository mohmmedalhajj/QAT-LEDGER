import 'package:integration_test/integration_test.dart';
import 'package:qat_ledger/main_customer.dart' as customer_app;

import 'flavor_smoke_flow.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  registerFlavorSmokeTest(
    expectedEdition: 'customer',
    expectedApplicationId: 'com.aistudio.tofanqat.qatledger.customer',
    launchApp: customer_app.main,
  );
}
