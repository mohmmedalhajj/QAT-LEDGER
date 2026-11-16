import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../core/money.dart';
import '../core/theme.dart';
import '../data/models.dart';
import '../developer/developer_console_sheet.dart';
import '../state/app_controller.dart';
import 'widgets/common_widgets.dart';

class SplashLoadingView extends StatelessWidget {
  const SplashLoadingView({super.key});

  @override
  Widget build(BuildContext context) {
    final pal = context.pal;
    return Scaffold(
      backgroundColor: pal.isDark ? Colors.black : const Color(0xFF0E382A),
      body: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ClipOval(
              child: Image.asset(
                'assets/images/app_logo.png',
                width: 108,
                height: 108,
              ),
            ),
            const SizedBox(height: 18),
            const Text(
              'ميزان القات',
              style: TextStyle(
                color: Colors.white,
                fontSize: 26,
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: 6),
            Text(
              'QAT LEDGER — الإدارة المحاسبية المتكاملة',
              style: TextStyle(
                color: Colors.white.withValues(alpha: 0.78),
                fontSize: 13,
              ),
            ),
            const SizedBox(height: 24),
            const SizedBox(
              width: 28,
              height: 28,
              child: CircularProgressIndicator(
                strokeWidth: 2.5,
                color: Color(0xFFD4A243),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _userCtrl = TextEditingController(text: 'admin');
  final _passCtrl = TextEditingController();
  bool _obscure = true;
  bool _loading = false;
  String? _error;
  int _logoTapCount = 0;

  @override
  void dispose() {
    _userCtrl.dispose();
    _passCtrl.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    final ctrl = context.read<AppController>();
    final ok = await ctrl.login(_userCtrl.text, _passCtrl.text);
    if (!mounted) return;
    setState(() {
      _loading = false;
      if (!ok) _error = 'اسم المستخدم أو كلمة المرور غير صحيحة';
    });
  }

  Future<void> _handleSecretLogoTap() async {
    _logoTapCount++;
    if (_logoTapCount < 3) return;
    _logoTapCount = 0;
    final ctrl = context.read<AppController>();
    final gateCtrl = TextEditingController();

    final unlocked = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('بوابة الإدارة'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('أدخل رمز الإدارة الخاص لفتح إدارة الأصناف المتقدمة.'),
            const SizedBox(height: 12),
            TextField(
              controller: gateCtrl,
              obscureText: true,
              textDirection: TextDirection.ltr,
              decoration: const InputDecoration(labelText: 'رمز المرور الإداري'),
            ),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('إلغاء')),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, ctrl.verifyAdminGatePasscode(gateCtrl.text)),
            child: const Text('دخول'),
          ),
        ],
      ),
    );

    if (!mounted) return;
    if (unlocked == true) {
      await _showHiddenAdminDialog(ctrl);
    } else if (unlocked == false) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('رمز الإدارة غير صحيح')),
      );
    }
  }

  Future<void> _showHiddenAdminDialog(AppController ctrl) async {
    final nameCtrl = TextEditingController(text: ctrl.settings.agencyName);
    SectionPermissions perms = ctrl.sectionPermissions;

    await showDialog<void>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setModalState) => AlertDialog(
          title: const Text('بوابة الإدارة المخفية'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                TextField(
                  controller: nameCtrl,
                  decoration: const InputDecoration(labelText: 'اسم الوكالة'),
                ),
                const SizedBox(height: 10),
                const Text('صلاحيات ظهور الأقسام:',
                    style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700)),
                SwitchListTile(
                  dense: true,
                  title: const Text('المبيعات والعملاء'),
                  value: perms.sales,
                  onChanged: (v) => setModalState(() => perms = perms.copyWith(sales: v)),
                ),
                SwitchListTile(
                  dense: true,
                  title: const Text('المخزون والموردون'),
                  value: perms.inventory,
                  onChanged: (v) => setModalState(() => perms = perms.copyWith(inventory: v)),
                ),
                SwitchListTile(
                  dense: true,
                  title: const Text('الحوالات'),
                  value: perms.transfers,
                  onChanged: (v) => setModalState(() => perms = perms.copyWith(transfers: v)),
                ),
                SwitchListTile(
                  dense: true,
                  title: const Text('التقارير المالية'),
                  value: perms.reports,
                  onChanged: (v) => setModalState(() => perms = perms.copyWith(reports: v)),
                ),
                SwitchListTile(
                  dense: true,
                  title: const Text('الأرشيف'),
                  value: perms.archive,
                  onChanged: (v) => setModalState(() => perms = perms.copyWith(archive: v)),
                ),
                SwitchListTile(
                  dense: true,
                  title: const Text('المصاريف'),
                  value: perms.expenses,
                  onChanged: (v) => setModalState(() => perms = perms.copyWith(expenses: v)),
                ),
                if (ctrl.isDeveloperEdition && ctrl.developerKeyManager != null) ...[
                  const Divider(),
                  OutlinedButton.icon(
                    onPressed: () async {
                      Navigator.pop(ctx);
                      if (!mounted) return;
                      await Navigator.of(context).push<void>(
                        MaterialPageRoute<void>(
                          builder: (_) => DeveloperConsoleSheet(
                            keyManager: ctrl.developerKeyManager!,
                            devDb: ctrl.developerDb,
                            onKeysUpdated: ctrl.refreshLicense,
                          ),
                        ),
                      );
                    },
                    icon: const Icon(Icons.developer_mode),
                    label: const Text('فتح وحدة تحكم المطور (Ed25519)'),
                  ),
                ],
              ],
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('إغلاق')),
            FilledButton(
              onPressed: () async {
                final cleanName = nameCtrl.text.trim();
                if (cleanName.isNotEmpty && cleanName != ctrl.settings.agencyName) {
                  await ctrl.updateSettings(ctrl.settings.copyWith(agencyName: cleanName));
                }
                await ctrl.updateSectionPermissions(perms);
                if (ctx.mounted) Navigator.pop(ctx);
              },
              child: const Text('حفظ التعديلات'),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final pal = context.pal;
    final ctrl = context.watch<AppController>();

    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 420),
              child: Card(
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Center(
                        child: GestureDetector(
                          key: const Key('login_logo_secret_trigger'),
                          onTap: _handleSecretLogoTap,
                          child: ClipOval(
                            child: Image.asset(
                              'assets/images/app_logo.png',
                              width: 82,
                              height: 82,
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(height: 14),
                      Text(
                        ctrl.settings.agencyName,
                        textAlign: TextAlign.center,
                        style: Theme.of(context).textTheme.titleLarge,
                      ),
                      const SizedBox(height: 4),
                      Text(
                        'تسجيل الدخول إلى دفتر الحسابات',
                        textAlign: TextAlign.center,
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                      const SizedBox(height: 22),
                      TextField(
                        controller: _userCtrl,
                        textDirection: TextDirection.ltr,
                        decoration: const InputDecoration(
                          labelText: 'اسم المستخدم',
                          prefixIcon: Icon(Icons.person_outline),
                        ),
                      ),
                      const SizedBox(height: 12),
                      TextField(
                        controller: _passCtrl,
                        obscureText: _obscure,
                        textDirection: TextDirection.ltr,
                        onSubmitted: (_) => _submit(),
                        decoration: InputDecoration(
                          labelText: 'كلمة المرور',
                          prefixIcon: const Icon(Icons.lock_outline),
                          suffixIcon: IconButton(
                            onPressed: () => setState(() => _obscure = !_obscure),
                            icon: Icon(_obscure ? Icons.visibility_off : Icons.visibility),
                          ),
                        ),
                      ),
                      if (_error != null) ...[
                        const SizedBox(height: 10),
                        Text(_error!, style: TextStyle(color: pal.danger, fontSize: 12.5)),
                      ],
                      const SizedBox(height: 16),
                      FilledButton(
                        onPressed: _loading ? null : _submit,
                        child: Text(_loading ? 'جاري التحقق...' : 'دخول'),
                      ),
                      const SizedBox(height: 12),
                      Text(
                        'بيانات الدخول الافتراضية لأول مرة: admin / 123456 (ينصح بتغييرها من الإعدادات)',
                        textAlign: TextAlign.center,
                        style: TextStyle(fontSize: 11, color: pal.textSecondary),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class AgencyOnboardingScreen extends StatefulWidget {
  const AgencyOnboardingScreen({super.key});

  @override
  State<AgencyOnboardingScreen> createState() => _AgencyOnboardingScreenState();
}

class _AgencyOnboardingScreenState extends State<AgencyOnboardingScreen> {
  late final TextEditingController _nameCtrl;
  late final TextEditingController _phoneCtrl;
  late final TextEditingController _addrCtrl;
  late final TextEditingController _rateCtrl;
  late CurrencyMode _currencyMode;
  late AppThemeModeChoice _themeChoice;

  @override
  void initState() {
    super.initState();
    final s = context.read<AppController>().settings;
    _nameCtrl = TextEditingController(text: s.agencyName);
    _phoneCtrl = TextEditingController(text: s.agencyPhone);
    _addrCtrl = TextEditingController(text: s.agencyAddress);
    _rateCtrl = TextEditingController(
      text: s.displaySarToYerMilli > 0 ? QuantityMath.formatMilli(s.displaySarToYerMilli) : '',
    );
    _currencyMode = s.currencyMode;
    _themeChoice = s.themeChoice;
  }

  @override
  void dispose() {
    _nameCtrl.dispose();
    _phoneCtrl.dispose();
    _addrCtrl.dispose();
    _rateCtrl.dispose();
    super.dispose();
  }

  Future<void> _finish() async {
    final ctrl = context.read<AppController>();
    final prev = ctrl.settings;
    final rateMilli = _currencyMode == CurrencyMode.dualCurrency
        ? QuantityMath.parseMilli(_rateCtrl.text)
        : 0;
    await ctrl.updateSettings(
      AgencySettings(
        agencyName: _nameCtrl.text.trim().isEmpty ? prev.agencyName : _nameCtrl.text.trim(),
        agencyPhone: _phoneCtrl.text.trim(),
        agencyAddress: _addrCtrl.text.trim(),
        footerNote: prev.footerNote,
        logoPath: prev.logoPath,
        currencyMode: _currencyMode,
        displaySarToYerMilli: rateMilli,
        themeChoice: _themeChoice,
        onboardingComplete: true,
        savedPrinterName: prev.savedPrinterName,
        savedPrinterAddress: prev.savedPrinterAddress,
        printerPaperMm: prev.printerPaperMm,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('إعداد الوكالة لأول مرة')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text('بيانات المكتب / الوكالة', style: Theme.of(context).textTheme.titleMedium),
                  const SizedBox(height: 12),
                  TextField(
                    controller: _nameCtrl,
                    decoration: const InputDecoration(labelText: 'اسم الوكالة'),
                  ),
                  const SizedBox(height: 10),
                  TextField(
                    controller: _phoneCtrl,
                    textDirection: TextDirection.ltr,
                    keyboardType: TextInputType.phone,
                    decoration: const InputDecoration(labelText: 'رقم الهاتف'),
                  ),
                  const SizedBox(height: 10),
                  TextField(
                    controller: _addrCtrl,
                    decoration: const InputDecoration(labelText: 'العنوان / السوق'),
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
                  Text('نظام العملة والمظهر', style: Theme.of(context).textTheme.titleMedium),
                  const SizedBox(height: 8),
                  RadioGroup<CurrencyMode>(
                    groupValue: _currencyMode,
                    onChanged: (v) => setState(() => _currencyMode = v ?? CurrencyMode.yerOnly),
                    child: Column(
                      children: [
                        for (final m in CurrencyMode.values)
                          RadioListTile<CurrencyMode>(
                            value: m,
                            title: Text(m.arabicLabel),
                          ),
                      ],
                    ),
                  ),
                  if (_currencyMode == CurrencyMode.dualCurrency) ...[
                    const SizedBox(height: 6),
                    AccountingNumberField(
                      controller: _rateCtrl,
                      label: 'سعر صرف العرض الاختياري (1 SAR -> YER)',
                    ),
                  ],
                  const Divider(height: 24),
                  SegmentedButton<AppThemeModeChoice>(
                    segments: const [
                      ButtonSegment(
                        value: AppThemeModeChoice.lightEnterprise,
                        label: Text('فاتح مؤسسي'),
                        icon: Icon(Icons.light_mode_outlined),
                      ),
                      ButtonSegment(
                        value: AppThemeModeChoice.darkBlack,
                        label: Text('أسود داكن'),
                        icon: Icon(Icons.dark_mode_outlined),
                      ),
                    ],
                    selected: {_themeChoice},
                    onSelectionChanged: (s) {
                      final next = s.first;
                      setState(() => _themeChoice = next);
                      context.read<AppController>().setThemeChoice(next);
                    },
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 18),
          FilledButton.icon(
            onPressed: _finish,
            icon: const Icon(Icons.check_circle_outline),
            label: const Text('حفظ وبدء العمل'),
          ),
        ],
      ),
    );
  }
}
