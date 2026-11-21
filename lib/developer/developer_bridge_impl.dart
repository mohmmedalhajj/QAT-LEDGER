import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../licensing/developer_bridge.dart';
import '../licensing/trusted_key_registry.dart';
import 'developer_console_sheet.dart';
import 'developer_database.dart';
import 'developer_key_manager.dart';

class FlutterDeveloperBridge extends DeveloperBridge {
  DeveloperKeyManager? _keyManager;

  @override
  bool get isReady => _keyManager != null;

  @override
  Future<void> initialize(SharedPreferences prefs) async {
    _keyManager = DeveloperKeyManager(prefs);
    await _keyManager!.getOrGenerateSigningKeyPair();
  }

  @override
  Future<TrustedPublicKeyRegistry?> getActivePublicKeyRegistry() async {
    final mgr = _keyManager;
    if (mgr == null) return null;
    final info = await mgr.getPublicKeyInfo();
    if (info == null) return null;
    return TrustedPublicKeyRegistry.fromRawPublicKeys([info.rawPublicBytes]);
  }

  @override
  Widget buildConsoleScreen({required VoidCallback onKeysUpdated}) {
    final mgr = _keyManager;
    if (mgr == null) {
      return const Scaffold(
        body: Center(child: Text('وحدة تحكم المطور غير مهيأة بعد')),
      );
    }
    return DeveloperConsoleSheet(
      keyManager: mgr,
      devDb: DeveloperDatabaseService.instance,
      onKeysUpdated: onKeysUpdated,
    );
  }
}
