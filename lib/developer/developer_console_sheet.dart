import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/services.dart';
import 'package:material_ui/material_ui.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:share_plus/share_plus.dart';

import '../core/date_utils.dart';
import '../core/money.dart';
import '../core/theme.dart';
import '../licensing/license_models.dart';
import 'developer_database.dart';
import 'developer_key_manager.dart';
import 'license_signer.dart';

/// Isolated Developer Console UI (4 tabs matching Kotlin DeveloperConsoleDialog 1:1):
/// 0: التسعير السري | 1: إصدار ترخيص | 2: السجل | 3: المفاتيح والنسخ
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
    with SingleTickerProviderStateMixin<DeveloperConsoleSheet> {
  late final TabController _tabs;
  late final DeveloperLicenseSigner _signer;

  // Pricing Tab controllers
  final _basicCurCtrl = TextEditingController(text: 'ريال يمني');
  final _basicPerpCtrl = TextEditingController();
  final _basicMonCtrl = TextEditingController();
  final _basicQuarCtrl = TextEditingController();
  final _basicYearCtrl = TextEditingController();
  final _proCurCtrl = TextEditingController(text: 'ريال يمني');
  final _proPerpCtrl = TextEditingController();
  final _proMonCtrl = TextEditingController();
  final _proQuarCtrl = TextEditingController();
  final _proYearCtrl = TextEditingController();

  // Issue Tab state & controllers
  final _agencyCtrl = TextEditingController();
  final _ownerCtrl = TextEditingController();
  final _phoneCtrl = TextEditingController();
  final _cityCtrl = TextEditingController();
  final _installCtrl = TextEditingController();
  final _daysCtrl = TextEditingController(text: '365');
  final _priceCtrl = TextEditingController();
  final _paidCtrl = TextEditingController();
  final _currencyCtrl = TextEditingController(text: 'ريال يمني');
  final _notesCtrl = TextEditingController();

  int? _selectedCustomerId;
  PackageTier _packageTier = PackageTier.pro;
  LicenseType _licenseType = LicenseType.perpetual;
  bool _confirmPaidChecked = false;
  SignedLicenseArtifact? _lastIssued;

  // Registry filter state
  String _registryQuery = '';
  String _registryFilter = 'ALL'; // ALL, ACTIVE, UNPAID, REVOKED

  DeveloperKeyInfo? _keyInfo;
  List<Map<String, Object?>> _prices = [];
  List<Map<String, Object?>> _customers = [];
  List<Map<String, Object?>> _issued = [];
  List<Map<String, Object?>> _logs = [];
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    _tabs = TabController(length: 4, initialIndex: 1, vsync: this);
    _signer = DeveloperLicenseSigner(keyManager: widget.keyManager, devDb: widget.devDb);
    unawaited(_loadAll());
  }

  @override
  void dispose() {
    _tabs.dispose();
    for (final c in [
      _basicCurCtrl,
      _basicPerpCtrl,
      _basicMonCtrl,
      _basicQuarCtrl,
      _basicYearCtrl,
      _proCurCtrl,
      _proPerpCtrl,
      _proMonCtrl,
      _proQuarCtrl,
      _proYearCtrl,
      _agencyCtrl,
      _ownerCtrl,
      _phoneCtrl,
      _cityCtrl,
      _installCtrl,
      _daysCtrl,
      _priceCtrl,
      _paidCtrl,
      _currencyCtrl,
      _notesCtrl,
    ]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _loadAll() async {
    await widget.keyManager.getOrGenerateSigningKeyPair();
    final kInfo = await widget.keyManager.getPublicKeyInfo();
    final db = await widget.devDb.database;
    final prices = await db.query('dev_package_prices');
    final customers = await db.query('dev_customers', orderBy: 'updated_at_ms DESC, id DESC');
    final issued = await db.query('issued_licenses', orderBy: 'issued_at_ms DESC', limit: 200);
    final logs = await db.query('developer_audit_logs', orderBy: 'created_at_ms DESC', limit: 200);
    if (!mounted) return;
    setState(() {
      _keyInfo = kInfo;
      _prices = prices;
      _customers = customers;
      _issued = issued;
      _logs = logs;
      for (final pRow in prices) {
        final code = pRow['package_code']?.toString();
        if (code == 'BASIC') {
          _basicCurCtrl.text = (pRow['currency'] ?? 'ريال يمني').toString();
          _basicPerpCtrl.text = pRow['perpetual_price']?.toString() ?? '';
          _basicMonCtrl.text = pRow['monthly_price']?.toString() ?? '';
          _basicQuarCtrl.text = pRow['quarterly_price']?.toString() ?? '';
          _basicYearCtrl.text = pRow['yearly_price']?.toString() ?? '';
        } else if (code == 'PRO') {
          _proCurCtrl.text = (pRow['currency'] ?? 'ريال يمني').toString();
          _proPerpCtrl.text = pRow['perpetual_price']?.toString() ?? '';
          _proMonCtrl.text = pRow['monthly_price']?.toString() ?? '';
          _proQuarCtrl.text = pRow['quarterly_price']?.toString() ?? '';
          _proYearCtrl.text = pRow['yearly_price']?.toString() ?? '';
        }
      }
    });
  }

  void _prefillSuggestedPrice() {
    Map<String, Object?>? row;
    for (final pRow in _prices) {
      if (pRow['package_code'] == _packageTier.code) {
        row = pRow;
        break;
      }
    }
    if (row == null) return;
    _currencyCtrl.text = (row['currency'] ?? 'ريال يمني').toString();
    final d = int.tryParse(normalizeDigits(_daysCtrl.text));
    final Object? suggested = switch (_licenseType) {
      LicenseType.perpetual => row['perpetual_price'],
      LicenseType.subscription => d == 30
          ? row['monthly_price']
          : (d == 90 ? row['quarterly_price'] : (d == 365 ? row['yearly_price'] : null)),
    };
    if (suggested != null && _priceCtrl.text.trim().isEmpty) {
      _priceCtrl.text = suggested.toString();
    }
  }

  Future<void> _issueLicense() async {
    if (_agencyCtrl.text.trim().isEmpty || _ownerCtrl.text.trim().isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('يرجى إدخال اسم الوكالة واسم المالك')),
      );
      return;
    }
    setState(() => _busy = true);
    try {
      final custId = await _signer.upsertDevCustomer(
        existingId: _selectedCustomerId,
        agencyName: _agencyCtrl.text,
        ownerName: _ownerCtrl.text,
        phone: _phoneCtrl.text,
        city: _cityCtrl.text,
      );
      final days = int.tryParse(normalizeDigits(_daysCtrl.text.trim())) ?? 365;
      final res = await _signer.issueOfficialLicense(
        customerId: custId,
        agencyName: _agencyCtrl.text,
        ownerName: _ownerCtrl.text,
        phone: _phoneCtrl.text,
        packageTier: _packageTier,
        licenseType: _licenseType,
        durationDays: _licenseType == LicenseType.subscription ? days : null,
        installationId: _installCtrl.text.trim().isEmpty ? null : _installCtrl.text.trim(),
        priceCharged: double.tryParse(normalizeDigits(_priceCtrl.text.trim())),
        priceCurrency: _currencyCtrl.text,
        paidAmount: double.tryParse(normalizeDigits(_paidCtrl.text.trim())),
        requestedPaidConfirmed: _confirmPaidChecked,
        notes: _notesCtrl.text,
      );
      widget.onKeysUpdated();
      await _loadAll();
      if (!mounted) return;
      setState(() => _lastIssued = res);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('تم توقيع وإصدار الترخيص ${res.payload.licenseId}')),
      );
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _shareQlic(String licenseId, String qlicJson, String compact) async {
    final tmp = await getTemporaryDirectory();
    final f = File(p.join(tmp.path, '$licenseId.qlic'));
    await f.writeAsString(qlicJson);
    await Share.shareXFiles(
      [XFile(f.path)],
      text: 'ترخيص نظام ميزان القات ($licenseId)\n$compact',
    );
  }

  @override
  Widget build(BuildContext context) {
    final pal = context.pal;
    return Scaffold(
      appBar: AppBar(
        title: const Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('لوحة المطور — إدارة الباقات والتراخيص', style: TextStyle(fontSize: 15)),
            Text(
              'Developer Edition • توقيع Ed25519 محلي غير متصل',
              style: TextStyle(fontSize: 11, fontWeight: FontWeight.w500),
            ),
          ],
        ),
        bottom: TabBar(
          controller: _tabs,
          isScrollable: true,
          tabs: [
            const Tab(text: 'التسعير السري'),
            const Tab(text: 'إصدار ترخيص'),
            Tab(text: 'السجل (${_issued.length})'),
            const Tab(text: 'المفاتيح والنسخ'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabs,
        children: [
          _buildPricingTab(pal),
          _buildIssueTab(pal),
          _buildRegistryTab(pal),
          _buildKeysAndAuditTab(pal),
        ],
      ),
    );
  }

  Widget _buildPricingTab(QatPalette pal) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text(
          'تسعير الباقات السري (محفوظ في قاعدة بيانات المطور فقط — لا يُصدر مع نسخة العميل ولا يُعرض فيها)',
          style: TextStyle(color: pal.textSecondary, fontSize: 12),
        ),
        const SizedBox(height: 12),
        _buildPackagePriceCard(
          pal: pal,
          tier: PackageTier.basic,
          curCtrl: _basicCurCtrl,
          perpCtrl: _basicPerpCtrl,
          monCtrl: _basicMonCtrl,
          quarCtrl: _basicQuarCtrl,
          yearCtrl: _basicYearCtrl,
        ),
        const SizedBox(height: 12),
        _buildPackagePriceCard(
          pal: pal,
          tier: PackageTier.pro,
          curCtrl: _proCurCtrl,
          perpCtrl: _proPerpCtrl,
          monCtrl: _proMonCtrl,
          quarCtrl: _proQuarCtrl,
          yearCtrl: _proYearCtrl,
        ),
      ],
    );
  }

  Widget _buildPackagePriceCard({
    required QatPalette pal,
    required PackageTier tier,
    required TextEditingController curCtrl,
    required TextEditingController perpCtrl,
    required TextEditingController monCtrl,
    required TextEditingController quarCtrl,
    required TextEditingController yearCtrl,
  }) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(tier.arabicName, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15)),
            const SizedBox(height: 10),
            TextField(
              controller: curCtrl,
              decoration: const InputDecoration(labelText: 'عملة التسعير (يمني / سعودي / دولار)'),
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: perpCtrl,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(labelText: 'سعر الدائم (اختياري)'),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: TextField(
                    controller: monCtrl,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(labelText: 'شهري'),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: quarCtrl,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(labelText: 'ربع سنوي (90 يوم)'),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: TextField(
                    controller: yearCtrl,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(labelText: 'سنوي (365 يوم)'),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            FilledButton.icon(
              onPressed: () async {
                await _signer.saveSecretPackagePricing(
                  packageTier: tier,
                  currency: curCtrl.text,
                  perpetualPrice: double.tryParse(normalizeDigits(perpCtrl.text.trim())),
                  monthlyPrice: double.tryParse(normalizeDigits(monCtrl.text.trim())),
                  quarterlyPrice: double.tryParse(normalizeDigits(quarCtrl.text.trim())),
                  yearlyPrice: double.tryParse(normalizeDigits(yearCtrl.text.trim())),
                );
                await _loadAll();
                if (!mounted) return;
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(content: Text('تم حفظ تسعير ${tier.code} السري بنجاح')),
                );
              },
              icon: const Icon(Icons.save_outlined),
              label: Text('حفظ التسعير السري لـ ${tier.code}'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildIssueTab(QatPalette pal) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        if (_customers.isNotEmpty) ...[
          Text('اختر عميلاً مسجلاً (أو اتركه جديداً):',
              style: TextStyle(color: pal.textSecondary, fontSize: 12)),
          const SizedBox(height: 6),
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Row(
              children: _customers.take(8).map((c) {
                final cid = (c['id'] as num).toInt();
                return Padding(
                  padding: const EdgeInsets.only(left: 6),
                  child: FilterChip(
                    selected: _selectedCustomerId == cid,
                    label: Text((c['business_name'] ?? '').toString()),
                    onSelected: (_) {
                      setState(() {
                        _selectedCustomerId = cid;
                        _agencyCtrl.text = (c['business_name'] ?? '').toString();
                        _ownerCtrl.text = (c['full_name'] ?? '').toString();
                        _phoneCtrl.text = (c['phone'] ?? '').toString();
                        _cityCtrl.text = (c['city'] ?? '').toString();
                      });
                    },
                  ),
                );
              }).toList(),
            ),
          ),
          const SizedBox(height: 10),
        ],
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                TextField(
                  controller: _agencyCtrl,
                  decoration: const InputDecoration(labelText: 'اسم الوكالة (إلزامي) *'),
                ),
                const SizedBox(height: 10),
                Row(
                  children: [
                    Expanded(
                      child: TextField(
                        controller: _ownerCtrl,
                        decoration: const InputDecoration(labelText: 'اسم الوكيل / المالك *'),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: TextField(
                        controller: _phoneCtrl,
                        keyboardType: TextInputType.phone,
                        decoration: const InputDecoration(labelText: 'رقم الهاتف'),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                const Text('الباقة المطلوبة:', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
                const SizedBox(height: 6),
                Row(
                  children: PackageTier.values.map((t) {
                    return Expanded(
                      child: Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 4),
                        child: FilterChip(
                          selected: _packageTier == t,
                          label: Text(t.code, textAlign: TextAlign.center),
                          onSelected: (_) {
                            setState(() {
                              _packageTier = t;
                              _priceCtrl.clear();
                              _prefillSuggestedPrice();
                            });
                          },
                        ),
                      ),
                    );
                  }).toList(),
                ),
                const SizedBox(height: 10),
                const Text('نوع الترخيص:', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
                const SizedBox(height: 6),
                Row(
                  children: LicenseType.values.map((lt) {
                    return Expanded(
                      child: Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 4),
                        child: FilterChip(
                          selected: _licenseType == lt,
                          label: Text(lt == LicenseType.perpetual ? 'دائم' : 'اشتراك'),
                          onSelected: (_) {
                            setState(() {
                              _licenseType = lt;
                              _priceCtrl.clear();
                              _prefillSuggestedPrice();
                            });
                          },
                        ),
                      ),
                    );
                  }).toList(),
                ),
                if (_licenseType == LicenseType.subscription) ...[
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 8,
                    children: [
                      ActionChip(
                        label: const Text('شهر (30)'),
                        onPressed: () => setState(() {
                          _daysCtrl.text = '30';
                          _priceCtrl.clear();
                          _prefillSuggestedPrice();
                        }),
                      ),
                      ActionChip(
                        label: const Text('3 أشهر (90)'),
                        onPressed: () => setState(() {
                          _daysCtrl.text = '90';
                          _priceCtrl.clear();
                          _prefillSuggestedPrice();
                        }),
                      ),
                      ActionChip(
                        label: const Text('سنة (365)'),
                        onPressed: () => setState(() {
                          _daysCtrl.text = '365';
                          _priceCtrl.clear();
                          _prefillSuggestedPrice();
                        }),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  TextField(
                    controller: _daysCtrl,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(labelText: 'المدة بالأيام'),
                  ),
                ],
                const SizedBox(height: 10),
                TextField(
                  controller: _installCtrl,
                  textDirection: TextDirection.ltr,
                  decoration: const InputDecoration(
                    labelText: 'معرّف التثبيت (اختياري — مثل QAT-XXXX-XXXX-XXXX)',
                  ),
                ),
                const Divider(height: 24),
                Text(
                  'البيانات المالية السرية (تُسجّل عند المطور فقط ولا تُضمّن في الترخيص):',
                  style: TextStyle(color: pal.textSecondary, fontSize: 11.5),
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Expanded(
                      child: TextField(
                        controller: _priceCtrl,
                        keyboardType: TextInputType.number,
                        decoration: const InputDecoration(labelText: 'المبلغ المتفق عليه'),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: TextField(
                        controller: _paidCtrl,
                        keyboardType: TextInputType.number,
                        decoration: const InputDecoration(labelText: 'المبلغ المدفوع'),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                TextField(
                  controller: _currencyCtrl,
                  decoration: const InputDecoration(labelText: 'العملة'),
                ),
                CheckboxListTile(
                  value: _confirmPaidChecked,
                  onChanged: (v) => setState(() => _confirmPaidChecked = v ?? false),
                  contentPadding: EdgeInsets.zero,
                  title: const Text(
                    'أؤكد صراحةً استلام السداد واعتماد حالة الدفع (PAID)',
                    style: TextStyle(fontSize: 12.5),
                  ),
                ),
                TextField(
                  controller: _notesCtrl,
                  maxLines: 2,
                  decoration: const InputDecoration(labelText: 'ملاحظات الإصدار'),
                ),
                const SizedBox(height: 14),
                FilledButton.icon(
                  onPressed: _busy ? null : _issueLicense,
                  icon: const Icon(Icons.verified_user_outlined),
                  label: Text(
                    _busy ? 'جاري التوقيع...' : 'توقيع وإصدار الترخيص دون إنترنت (Ed25519)',
                  ),
                ),
              ],
            ),
          ),
        ),
        if (_lastIssued != null) ...[
          const SizedBox(height: 14),
          _buildArtifactCard(
            pal: pal,
            licenseId: _lastIssued!.payload.licenseId,
            packageCode: _lastIssued!.payload.effectiveTier.code,
            compactToken: _lastIssued!.compactToken,
            qlicJson: _lastIssued!.qlicJson,
          ),
        ],
      ],
    );
  }

  Widget _buildArtifactCard({
    required QatPalette pal,
    required String licenseId,
    required String packageCode,
    required String compactToken,
    required String qlicJson,
  }) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          children: [
            Text(
              'الترخيص الموقّع: $licenseId ($packageCode)',
              style: TextStyle(fontWeight: FontWeight.bold, color: pal.success),
            ),
            const SizedBox(height: 10),
            Container(
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(12),
              ),
              child: QrImageView(data: compactToken, size: 180),
            ),
            const SizedBox(height: 10),
            SelectableText(
              compactToken,
              textDirection: TextDirection.ltr,
              maxLines: 3,
              style: const TextStyle(fontSize: 10.5),
            ),
            const SizedBox(height: 10),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: () {
                      unawaited(Clipboard.setData(ClipboardData(text: compactToken)));
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('تم نسخ كود التفعيل')),
                      );
                    },
                    icon: const Icon(Icons.copy, size: 18),
                    label: const Text('نسخ الكود'),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: FilledButton.icon(
                    onPressed: () => _shareQlic(licenseId, qlicJson, compactToken),
                    icon: const Icon(Icons.share, size: 18),
                    label: const Text('مشاركة .qlic'),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildRegistryTab(QatPalette pal) {
    final q = _registryQuery.trim().toLowerCase();
    final filtered = _issued.where((row) {
      final status = (row['status'] ?? 'ACTIVE').toString();
      final pay = (row['payment_status'] ?? 'UNPAID').toString();
      final matchesFilter = switch (_registryFilter) {
        'ACTIVE' => status == 'ACTIVE',
        'REVOKED' => status == 'REVOKED',
        'UNPAID' => pay != 'PAID',
        _ => true,
      };
      if (!matchesFilter) return false;
      if (q.isEmpty) return true;
      return row.values.any((v) => v != null && v.toString().toLowerCase().contains(q));
    }).toList();

    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 6),
          child: TextField(
            decoration: const InputDecoration(
              prefixIcon: Icon(Icons.search),
              hintText: 'ابحث بالاسم أو الهاتف أو رقم الترخيص أو المعرّف...',
            ),
            onChanged: (v) => setState(() => _registryQuery = v),
          ),
        ),
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
          child: Row(
            children: [
              ('ALL', 'الكل'),
              ('ACTIVE', 'نشط'),
              ('UNPAID', 'غير مكتمل الدفع'),
              ('REVOKED', 'ملغي'),
            ].map((pair) {
              return Padding(
                padding: const EdgeInsets.only(left: 6),
                child: FilterChip(
                  selected: _registryFilter == pair.$1,
                  label: Text(pair.$2),
                  onSelected: (_) => setState(() => _registryFilter = pair.$1),
                ),
              );
            }).toList(),
          ),
        ),
        Expanded(
          child: filtered.isEmpty
              ? const Center(child: Text('لا توجد تراخيص مطابقة.'))
              : ListView.separated(
                  padding: const EdgeInsets.all(16),
                  itemCount: filtered.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 8),
                  itemBuilder: (_, idx) {
                    final r = filtered[idx];
                    final exp = (r['expires_at_ms'] as num).toInt();
                    final expStr = exp <= 0
                        ? 'دائم'
                        : QatDateUtils.toIsoDate(DateTime.fromMillisecondsSinceEpoch(exp));
                    final licId = (r['license_id'] ?? '').toString();
                    final pkg = (r['package_code'] ?? 'PRO').toString();
                    final status = (r['status'] ?? 'ACTIVE').toString();
                    final payStatus = (r['payment_status'] ?? 'UNPAID').toString();
                    return Card(
                      child: InkWell(
                        borderRadius: BorderRadius.circular(12),
                        onTap: () => _openLicenseDetailDialog(r),
                        child: Padding(
                          padding: const EdgeInsets.all(12),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                children: [
                                  Expanded(
                                    child: Text(
                                      '${r['business_name']} ($pkg)',
                                      style: const TextStyle(fontWeight: FontWeight.bold),
                                    ),
                                  ),
                                  Text(licId,
                                      style: TextStyle(color: pal.brand, fontWeight: FontWeight.bold)),
                                ],
                              ),
                              const SizedBox(height: 4),
                              Text(
                                'المالك: ${r['licensee_name']} • ${r['phone_snapshot'] ?? ''}',
                                style: TextStyle(color: pal.textSecondary, fontSize: 12),
                              ),
                              const SizedBox(height: 4),
                              Row(
                                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                children: [
                                  Text('الانتهاء: $expStr', style: TextStyle(color: pal.textSecondary, fontSize: 11.5)),
                                  Text(
                                    'الحالة: $status | السداد: $payStatus',
                                    style: TextStyle(
                                      color: status == 'REVOKED' ? pal.danger : pal.textPrimary,
                                      fontSize: 11.5,
                                      fontWeight: FontWeight.w600,
                                    ),
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ),
                      ),
                    );
                  },
                ),
        ),
      ],
    );
  }

  Future<void> _openLicenseDetailDialog(Map<String, Object?> row) async {
    final pal = context.pal;
    final licId = (row['license_id'] ?? '').toString();
    final paidCtrl = TextEditingController(text: row['paid_amount']?.toString() ?? '');
    final renewCtrl = TextEditingController(text: '365');
    final installCtrl = TextEditingController(
      text: row['installation_id'] == '*' ? '' : (row['installation_id'] ?? '').toString(),
    );
    final installReasonCtrl = TextEditingController();
    final revokeReasonCtrl = TextEditingController();
    bool confirmPaid = row['payment_status'] == 'PAID';

    await showDialog<void>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDlg) {
          final licAudits = _logs.where((a) => a['target_id'] == licId).toList();
          return AlertDialog(
            title: Text('إدارة الترخيص $licId'),
            content: SizedBox(
              width: 440,
              child: SingleChildScrollView(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    _buildArtifactCard(
                      pal: pal,
                      licenseId: licId,
                      packageCode: (row['package_code'] ?? 'PRO').toString(),
                      compactToken: (row['token_compact'] ?? '').toString(),
                      qlicJson: (row['qlic_json'] ?? '').toString(),
                    ),
                    const Divider(height: 20),
                    const Text('تحديث السداد السري:', style: TextStyle(fontWeight: FontWeight.bold)),
                    const SizedBox(height: 6),
                    TextField(
                      controller: paidCtrl,
                      keyboardType: TextInputType.number,
                      decoration: InputDecoration(
                        labelText: 'المبلغ المدفوع (${row['price_currency'] ?? 'ريال يمني'})',
                      ),
                    ),
                    CheckboxListTile(
                      value: confirmPaid,
                      onChanged: (v) => setDlg(() => confirmPaid = v ?? false),
                      contentPadding: EdgeInsets.zero,
                      title: const Text('تأكيد استلام السداد الفعلي (PAID)', style: TextStyle(fontSize: 12)),
                    ),
                    FilledButton(
                      onPressed: () async {
                        await _signer.confirmOrUpdatePayment(
                          licenseId: licId,
                          priceCharged: (row['price_charged'] as num?)?.toDouble(),
                          paidAmount: double.tryParse(normalizeDigits(paidCtrl.text)),
                          explicitlyConfirmedPaid: confirmPaid,
                        );
                        await _loadAll();
                        if (ctx.mounted) Navigator.pop(ctx);
                      },
                      child: const Text('حفظ السداد'),
                    ),
                    const Divider(height: 20),
                    Row(
                      children: [
                        Expanded(
                          child: TextField(
                            controller: renewCtrl,
                            keyboardType: TextInputType.number,
                            decoration: const InputDecoration(labelText: 'أيام التجديد'),
                          ),
                        ),
                        const SizedBox(width: 8),
                        FilledButton(
                          onPressed: () async {
                            final days = int.tryParse(normalizeDigits(renewCtrl.text)) ?? 30;
                            await _signer.renewSubscription(previousLicenseId: licId, additionalDays: days);
                            await _loadAll();
                            if (ctx.mounted) Navigator.pop(ctx);
                          },
                          child: const Text('تجديد'),
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    Row(
                      children: [
                        Expanded(
                          child: OutlinedButton(
                            onPressed: () async {
                              await _signer.upgradeToPerpetual(previousLicenseId: licId);
                              await _loadAll();
                              if (ctx.mounted) Navigator.pop(ctx);
                            },
                            child: const Text('ترقية PRO دائم'),
                          ),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: OutlinedButton(
                            onPressed: () async {
                              await _signer.reissueLicense(licId);
                              await _loadAll();
                              if (ctx.mounted) Navigator.pop(ctx);
                            },
                            child: const Text('إعادة إصدار'),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 10),
                    TextField(
                      controller: installCtrl,
                      textDirection: TextDirection.ltr,
                      decoration: const InputDecoration(labelText: 'معرّف التثبيت الجديد'),
                    ),
                    const SizedBox(height: 6),
                    TextField(
                      controller: installReasonCtrl,
                      decoration: const InputDecoration(labelText: 'سبب تغيير معرّف التثبيت (إلزامي)'),
                    ),
                    const SizedBox(height: 6),
                    OutlinedButton(
                      onPressed: () async {
                        if (installReasonCtrl.text.trim().isEmpty) return;
                        await _signer.changeInstallationId(
                          previousLicenseId: licId,
                          newInstallationId: installCtrl.text.trim().isEmpty ? null : installCtrl.text.trim(),
                          reason: installReasonCtrl.text,
                        );
                        await _loadAll();
                        if (ctx.mounted) Navigator.pop(ctx);
                      },
                      child: const Text('تغيير معرّف التثبيت وتوثيق السبب'),
                    ),
                    const SizedBox(height: 8),
                    TextField(
                      controller: revokeReasonCtrl,
                      decoration: const InputDecoration(labelText: 'سبب الإلغاء في سجل المطور'),
                    ),
                    const SizedBox(height: 6),
                    FilledButton(
                      style: FilledButton.styleFrom(backgroundColor: pal.danger),
                      onPressed: () async {
                        if (revokeReasonCtrl.text.trim().isEmpty) return;
                        await _signer.revokeLicense(licenseId: licId, reason: revokeReasonCtrl.text);
                        await _loadAll();
                        if (ctx.mounted) Navigator.pop(ctx);
                      },
                      child: const Text('إلغاء الترخيص في السجل'),
                    ),
                    if (licAudits.isNotEmpty) ...[
                      const Divider(height: 20),
                      const Text('سجل المراجعة (Audit Trail):', style: TextStyle(fontWeight: FontWeight.bold)),
                      ...licAudits.map((a) => Padding(
                            padding: const EdgeInsets.only(top: 4),
                            child: Text(
                              '• [${a['action']}] ${a['details']}${a['reason'] != null ? ' — السبب: ${a['reason']}' : ''}',
                              style: const TextStyle(fontSize: 11.5),
                            ),
                          )),
                    ],
                  ],
                ),
              ),
            ),
            actions: [
              TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('إغلاق')),
            ],
          );
        },
      ),
    );
    paidCtrl.dispose();
    renewCtrl.dispose();
    installCtrl.dispose();
    installReasonCtrl.dispose();
    revokeReasonCtrl.dispose();
  }

  Widget _buildKeysAndAuditTab(QatPalette pal) {
    final kotlinLine = _keyInfo != null
        ? 'QAT_LICENSE_PUBLIC_KEYS=${_keyInfo!.kotlinConfigLine}'
        : '';
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        if (_keyInfo != null)
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const Text('مفتاح التوقيع النشط (Ed25519)',
                      style: TextStyle(fontWeight: FontWeight.bold)),
                  const SizedBox(height: 6),
                  Text('معرّف Kotlin: ${_keyInfo!.kotlinKeyId}'),
                  Text('معرّف SPKI: ${_keyInfo!.keyId}'),
                  Text(
                    _keyInfo!.hardwareSealed
                        ? 'محمي بمخزن المفاتيح (Android Keystore AES-GCM)'
                        : 'تخزين محلي خاص بالتطبيق',
                  ),
                  const SizedBox(height: 10),
                  const Text('سطر التضمين في gradle.properties:',
                      style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600)),
                  const SizedBox(height: 4),
                  SelectableText(
                    kotlinLine,
                    textDirection: TextDirection.ltr,
                    style: TextStyle(fontSize: 11, color: pal.brand),
                  ),
                  const SizedBox(height: 10),
                  Row(
                    children: [
                      Expanded(
                        child: OutlinedButton.icon(
                          onPressed: () {
                            unawaited(Clipboard.setData(ClipboardData(text: kotlinLine)));
                            ScaffoldMessenger.of(context).showSnackBar(
                              const SnackBar(content: Text('تم نسخ QAT_LICENSE_PUBLIC_KEYS')),
                            );
                          },
                          icon: const Icon(Icons.copy, size: 18),
                          label: const Text('نسخ Public Key'),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: OutlinedButton.icon(
                          onPressed: () async {
                            final confirmed = await showDialog<bool>(
                              context: context,
                              builder: (ctx) => AlertDialog(
                                title: const Text('تدوير مفتاح التوقيع'),
                                content: const Text(
                                  'سيتم إنشاء زوج مفاتيح Ed25519 جديد للتراخيص الجديدة. تذكر تحديث المفتاح العام في نسخة العميل.',
                                ),
                                actions: [
                                  TextButton(
                                    onPressed: () => Navigator.pop(ctx, false),
                                    child: const Text('إلغاء'),
                                  ),
                                  FilledButton(
                                    onPressed: () => Navigator.pop(ctx, true),
                                    child: const Text('تأكيد التدوير'),
                                  ),
                                ],
                              ),
                            );
                            if (confirmed == true) {
                              await widget.keyManager.rotateSigningKeyPair();
                              widget.onKeysUpdated();
                              await _loadAll();
                            }
                          },
                          icon: const Icon(Icons.autorenew, size: 18),
                          label: const Text('تدوير المفتاح'),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        const SizedBox(height: 12),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const Text('النسخ الاحتياطي لسجل المطور والعملاء',
                    style: TextStyle(fontWeight: FontWeight.bold)),
                const SizedBox(height: 6),
                Text(
                  'يصدر ويستعيد الأسعار السرية والعملاء والتراخيص وسجل المراجعة بصيغة QAT_LEDGER_DEV_REGISTRY_V1 دون أي مفاتيح خاصة.',
                  style: TextStyle(color: pal.textSecondary, fontSize: 12),
                ),
                const SizedBox(height: 10),
                Row(
                  children: [
                    Expanded(
                      child: FilledButton.icon(
                        onPressed: () async {
                          final json = await _signer.exportRegistryBackupJson();
                          final tmp = await getTemporaryDirectory();
                          final out = File(p.join(tmp.path, 'qat_developer_registry_${DateTime.now().millisecondsSinceEpoch}.json'));
                          await out.writeAsString(json);
                          await Share.shareXFiles([XFile(out.path)], text: 'نسخة احتياطية لسجل المطور');
                        },
                        icon: const Icon(Icons.upload_file, size: 18),
                        label: const Text('تصدير السجل'),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: OutlinedButton.icon(
                        onPressed: () async {
                          final res = await FilePicker.platform.pickFiles(type: FileType.any);
                          final path = res?.files.single.path;
                          if (path == null) return;
                          final txt = await File(path).readAsString(encoding: utf8);
                          final ok = await _signer.importRegistryBackupJson(txt);
                          await _loadAll();
                          if (!mounted) return;
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(
                              content: Text(
                                ok ? 'تم استيراد سجل المطور بنجاح' : 'ملف النسخة غير صالح',
                              ),
                            ),
                          );
                        },
                        icon: const Icon(Icons.download, size: 18),
                        label: const Text('استيراد السجل'),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 14),
        const Text('سجل التدقيق الأمني الأخير:', style: TextStyle(fontWeight: FontWeight.bold)),
        const SizedBox(height: 8),
        ..._logs.take(30).map((l) {
          final ts = QatDateUtils.toIsoDate(
            DateTime.fromMillisecondsSinceEpoch((l['created_at_ms'] as num).toInt()),
          );
          return ListTile(
            dense: true,
            leading: const Icon(Icons.history, size: 18),
            title: Text('${l['action']} — ${l['target_id']}'),
            subtitle: Text('${l['details']} ($ts)'),
          );
        }),
      ],
    );
  }
}
