import 'dart:async';

import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import 'app.dart';
import 'state/app_controller.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final controller = AppController();
  // Begin initialization immediately; SplashLoadingView handles frame 1 cleanly while SQLite + migration run.
  unawaited(controller.initialize());
  runApp(
    ChangeNotifierProvider<AppController>.value(
      value: controller,
      child: const QatLedgerApp(),
    ),
  );
}
