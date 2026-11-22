import 'package:integration_test/integration_test.dart';
import 'package:qat_ledger/main_developer.dart' as developer_app;

import 'flavor_smoke_flow.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  registerFlavorSmokeTest(
    expectedEdition: 'developer',
    expectedApplicationId: 'com.aistudio.tofanqat.qatledger.developer',
    launchApp: developer_app.main,
  );
}
