import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'trusted_key_registry.dart';

/// Edition-safe bridge so Customer builds never import or link `lib/developer/**`.
abstract class DeveloperBridge {
  Future<void> initialize(SharedPreferences prefs);

  bool get isReady;

  Future<TrustedPublicKeyRegistry?> getActivePublicKeyRegistry();

  Widget buildConsoleScreen({required VoidCallback onKeysUpdated});
}
