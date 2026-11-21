import 'dart:async';

import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import 'app.dart';
import 'developer/developer_bridge_impl.dart';
import 'state/app_controller.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final controller = AppController(developerBridge: FlutterDeveloperBridge());
  unawaited(controller.initialize());
  runApp(
    ChangeNotifierProvider<AppController>.value(
      value: controller,
      child: const QatLedgerApp(),
    ),
  );
}
