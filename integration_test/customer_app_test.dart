import 'package:integration_test/integration_test.dart';

import 'flavor_smoke_flow.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  registerFlavorSmokeTest(
    expectedEdition: 'customer',
    expectedApplicationId: 'com.aistudio.tofanqat.qatledger.customer',
  );
}
