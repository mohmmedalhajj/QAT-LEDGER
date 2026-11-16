import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:share_plus/share_plus.dart';

import '../core/date_utils.dart';
import '../core/money.dart';
import '../core/theme.dart';
import 'developer_database.dart';
import 'developer_key_manager.dart';
import 'license_signer.dart';

class DeveloperConsoleSheet extends StatefulWidget {
  const DeveloperConsoleSheet({
    super.key,
    required this.keyManager,
    required this.devDb,
    required this.onKeysUpdated,
  });

  final DeveloperKeyManager keyManager;
  final DeveloperDatabaseService devDb;
  final VoidCallback onKeysUpdated;

  @override
  State<DeveloperConsoleSheet> createState() => _DeveloperConsoleSheetState();
}

class _DeveloperConsoleSheetState extends State<DeveloperConsoleSheet>
    with SingleTickerProviderStateMixin {
  late final TabController _tabs;
  late final DeveloperLicenseSigner _signer;

  final _nameCtrl = TextEditingController();
  final _bizCtrl = TextEditingController();
  final _installCtrl = TextEditingController(text: '*');
  final _daysCtrl = TextEditingController(text: '365');
  final _notesCtrl = TextEditingController();

  String _selectedPlan = 'PRO_YEARLY';
  DeveloperKeyInfo? _keyInfo;
  SignedLicenseArtifact? _lastIssued;
  List<Map<String, Object?>> _plans = [];
  List<Map<String, Object?>> _issued = [];
  List<Map<String, Object?>> _logs = [];
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    _tabs = TabController(length: 3, vsync: this);
    _signer = DeveloperLicenseSigner(keyManager: widget.keyManager, devDb: widget.devDb);
    unawaited(_loadAll());
  }

  @override
  void dispose() {
    _tabs.dispose();
    _nameCtrl.dispose();
    _bizCtrl.dispose();
    _installCtrl.dispose();
    _daysCtrl.dispose();
    _notesCtrl.dispose();
    super.dispose();
  }

  Future<void> _loadAll() async {
    await widget.keyManager.getOrGenerateSigningKeyPair();
    final kInfo = await widget.keyManager.getPublicKeyInfo();
    final db = await widget.devDb.database;
    final plans = await db.query('license_plans', orderBy: 'duration_days ASC');
    final issued = await db.query('issued_licenses', orderBy: 'issued_at_ms DESC', limit: 50);
    final logs = await db.query('developer_audit_logs', orderBy: 'created_at_ms DESC', limit: 50);
    if (!mounted) return;
    setState(() {
      _keyInfo = kInfo;
      _plans = plans;
      _issued = issued;
      _logs = logs;
    });
  }

  Future<void> _issueLicense() async {
    if (_nameCtrl.text.trim().isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('يرجى إدخال اسم العميل')),
      );
      return;
    }
    setState(() => _busy = true);
    try {
      final days = int.tryParse(normalizeDigits(_daysCtrl.text.trim())) ?? 365;
      final res = await _signer.issueLicense(
        customerName: _nameCtrl.text,
        businessName: _bizCtrl.text,
        planCode: _selectedPlan,
        installationId: _installCtrl.text,
        durationDays: days,
        notes: _notesCtrl.text,
      );
      widget.onKeysUpdated();
      await _loadAll();
      if (!mounted) return;
      setState(() => _lastIssued = res);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _shareQlic(String licenseId, String qlicJson) async {
    final tmp = await getTemporaryDirectory();
    final f = File(p.join(tmp.path, '$licenseId.qlic'));
    await f.writeAsString(qlicJson);
    await Share.shareXFiles([XFile(f.path)], text: 'ملف ترخيص ميزان القات: $licenseId');
  }

  @override
  Widget build(BuildContext context) {
    final pal = context.pal;
    return Scaffold(
      appBar: AppBar(
        title: const Text('وحدة تحكم المطور — إصدار التراخيص (Ed25519)'),
        bottom: TabBar(
          controller: _tabs,
          tabs: const [
            Tab(text: 'إصدار ترخيص'),
            Tab(text: 'سجل التراخيص'),
            Tab(text: 'المفاتيح والتدقيق'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabs,
        children: [
          _buildIssueTab(pal),
          _buildIssuedListTab(pal),
          _buildKeysAndAuditTab(pal),
        ],
      ),
    );
  }

  Widget _buildIssueTab(QatPalette pal) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text('توليد ترخيص موقّع دون إنترنت',
                    style: Theme.of(context).textTheme.titleMedium),
                const SizedBox(height: 12),
                TextField(
                  controller: _nameCtrl,
                  decoration: const InputDecoration(labelText: 'اسم العميل / المرخص له *'),
                ),
                const SizedBox(height: 10),
                TextField(
                  controller: _bizCtrl,
                  decoration: const InputDecoration(labelText: 'اسم الوكالة / المحل'),
                ),
                const SizedBox(height: 10),
                DropdownButtonFormField<String>(
                  key: ValueKey<String>('plan_${_plans.length}_$_selectedPlan'),
                  initialValue:
                      _plans.any((p) => p['code'] == _selectedPlan) ? _selectedPlan : null,
                  decoration: const InputDecoration(labelText: 'الباقة'),
                  items: _plans
                      .map((pl) => DropdownMenuItem<String>(
                            value: pl['code'].toString(),
                            child: Text(pl['name'].toString()),
                          ))
                      .toList(),
                  onChanged: (v) {
                    if (v == null) return;
                    final pl = _plans.firstWhere((e) => e['code'] == v);
                    setState(() {
                      _selectedPlan = v;
                      _daysCtrl.text = pl['duration_days'].toString();
                    });
                  },
                ),
                const SizedBox(height: 10),
                Row(
                  children: [
                    Expanded(
                      child: TextField(
                        controller: _daysCtrl,
                        keyboardType: TextInputType.number,
                        decoration: const InputDecoration(
                          labelText: 'المدة بالأيام (0 = دائم)',
                        ),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: TextField(
                        controller: _installCtrl,
                        textDirection: TextDirection.ltr,
                        decoration: const InputDecoration(
                          labelText: 'معرف الجهاز (* للعام)',
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 10),
                TextField(
                  controller: _notesCtrl,
                  decoration: const InputDecoration(labelText: 'ملاحظات'),
                ),
                const SizedBox(height: 14),
                FilledButton.icon(
                  onPressed: _busy ? null : _issueLicense,
                  icon: const Icon(Icons.verified_user_outlined),
                  label: Text(_busy ? 'جاري التوقيع...' : 'توقيع وإصدار الترخيص'),
                ),
              ],
            ),
          ),
        ),
        if (_lastIssued != null) ...[
          const SizedBox(height: 16),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                children: [
                  Text('تم الإصدار: ${_lastIssued!.payload.licenseId}',
                      style: TextStyle(fontWeight: FontWeight.w700, color: pal.success)),
                  const SizedBox(height: 12),
                  Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: QrImageView(
                      data: _lastIssued!.compactToken,
                      size: 180,
                    ),
                  ),
                  const SizedBox(height: 10),
                  SelectableText(
                    _lastIssued!.compactToken,
                    textDirection: TextDirection.ltr,
                    style: const TextStyle(fontSize: 11),
                  ),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      Expanded(
                        child: OutlinedButton.icon(
                          onPressed: () {
                            unawaited(
                              Clipboard.setData(ClipboardData(text: _lastIssued!.compactToken)),
                            );
                            ScaffoldMessenger.of(context).showSnackBar(
                              const SnackBar(content: Text('تم نسخ رمز الترخيص QL1')),
                            );
                          },
                          icon: const Icon(Icons.copy, size: 18),
                          label: const Text('نسخ QL1'),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: FilledButton.icon(
                          onPressed: () => _shareQlic(
                            _lastIssued!.payload.licenseId,
                            _lastIssued!.qlicJson,
                          ),
                          icon: const Icon(Icons.share, size: 18),
                          label: const Text('مشاركة .qlic'),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ],
      ],
    );
  }

  Widget _buildIssuedListTab(QatPalette pal) {
    if (_issued.isEmpty) {
      return const Center(child: Text('لا توجد تراخيص مصدرة بعد.'));
    }
    return ListView.separated(
      padding: const EdgeInsets.all(16),
      itemCount: _issued.length,
      separatorBuilder: (_, idx) => const SizedBox(height: 10),
      itemBuilder: (_, idx) {
        final item = _issued[idx];
        final expMs = (item['expires_at_ms'] as num).toInt();
        return Card(
          child: ListTile(
            title: Text('${item['licensee_name']} (${item['license_id']})'),
            subtitle: Text(
              'الباقة: ${item['plan_code']} • الجهاز: ${item['installation_id']}\n'
              'الانتهاء: ${expMs <= 0 ? 'دائم' : QatDateUtils.toDisplayDateTime(expMs)}',
            ),
            trailing: IconButton(
              icon: const Icon(Icons.share_outlined),
              onPressed: () => _shareQlic(
                item['license_id'].toString(),
                item['qlic_json'].toString(),
              ),
            ),
          ),
        );
      },
    );
  }

  Widget _buildKeysAndAuditTab(QatPalette pal) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        if (_keyInfo != null)
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('المفتاح العام (QAT_LICENSE_PUBLIC_KEYS)',
                      style: Theme.of(context).textTheme.titleMedium),
                  const SizedBox(height: 6),
                  Text('معرف المفتاح: ${_keyInfo!.keyId}'),
                  Text(
                    _keyInfo!.hardwareSealed
                        ? 'المفتاح الخاص مغلّف بـ Android Keystore AES-GCM'
                        : 'وضع التخزين المحلي (بدون Keystore)',
                    style: TextStyle(color: pal.textSecondary, fontSize: 12),
                  ),
                  const SizedBox(height: 8),
                  SelectableText(
                    _keyInfo!.spkiBase64,
                    textDirection: TextDirection.ltr,
                    style: const TextStyle(fontSize: 11),
                  ),
                  const SizedBox(height: 8),
                  OutlinedButton.icon(
                    onPressed: () {
                      unawaited(Clipboard.setData(ClipboardData(text: _keyInfo!.spkiBase64)));
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('تم نسخ المفتاح العام SPKI Base64')),
                      );
                    },
                    icon: const Icon(Icons.copy, size: 16),
                    label: const Text('نسخ المفتاح العام'),
                  ),
                ],
              ),
            ),
          ),
        const SizedBox(height: 14),
        Text('سجل التدقيق', style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 8),
        ..._logs.map((l) => Card(
              margin: const EdgeInsets.only(bottom: 8),
              child: ListTile(
                dense: true,
                title: Text('${l['action']} — ${l['target_id']}'),
                subtitle: Text('${l['details']}\n${QatDateUtils.toDisplayDateTime((l['created_at_ms'] as num).toInt())}'),
              ),
            )),
      ],
    );
  }
}
