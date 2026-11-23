import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../licensing/developer_bridge.dart';
import '../licensing/trusted_key_registry.dart';
import 'developer_console_sheet.dart';
import 'developer_database.dart';
import 'developer_key_manager.dart';

class FlutterDeveloperBridge extends DeveloperBridge {
  DeveloperKeyManager? _keyManager;
  String? _keyUnavailableReason;

  @override
  bool get isReady => _keyManager != null;

  @override
  Future<void> initialize(SharedPreferences prefs) async {
    final manager = DeveloperKeyManager(prefs);
    try {
      // Do not create signing material merely because the Developer app starts.
      // Opening the Developer console is the explicit action that creates a first key.
      await manager.loadSigningKeyPair();
      _keyManager = manager;
      _keyUnavailableReason = null;
    } catch (_) {
      // Keep the Developer app's accounting database usable even if signing storage fails.
      // License issuance remains unavailable; the key manager itself fails closed.
      _keyManager = null;
      _keyUnavailableReason =
          'تعذر فتح مفتاح التوقيع المحمي. لم يتم إنشاء مفتاح بديل، ويمكنك إعادة المحاولة بعد استعادة AndroidKeyStore أو ملف النسخة المشفرة.';
    }
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
      return Scaffold(
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Text(
              _keyUnavailableReason ?? 'وحدة تحكم المطور غير مهيأة بعد',
              textAlign: TextAlign.center,
            ),
          ),
        ),
      );
    }
    return DeveloperConsoleSheet(
      keyManager: mgr,
      devDb: DeveloperDatabaseService.instance,
      onKeysUpdated: onKeysUpdated,
    );
  }
}
