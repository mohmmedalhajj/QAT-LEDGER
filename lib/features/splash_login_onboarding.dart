import 'dart:async';

import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../core/money.dart';
import '../core/theme.dart';
import '../data/models.dart';
import '../developer/developer_console_sheet.dart';
import '../state/app_controller.dart';
import 'widgets/common_widgets.dart';
import 'widgets/erp_components.dart';

class SplashLoadingView extends StatelessWidget {
  const SplashLoadingView({super.key});

  @override
  Widget build(BuildContext context) {
    final pal = context.pal;
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 112,
                  height: 112,
                  padding: const EdgeInsets.all(6),
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: pal.brandSoft,
                    border: Border.all(color: pal.accent, width: 2.2),
                  ),
                  child: ClipOval(
                    child: Image.asset('assets/images/app_logo.png'),
                  ),
                ),
                const SizedBox(height: 20),
                Text(
                  'ميزان القات',
                  style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                        color: pal.brand,
                      ),
                ),
                const SizedBox(height: 6),
                Text(
                  'النظام المحاسبي المتكامل لوكالات وتجارة القات',
                  textAlign: TextAlign.center,
                  style: Theme.of(context).textTheme.bodySmall,
                ),
                const SizedBox(height: 24),
                SizedBox(
                  width: 180,
                  child: LinearProgressIndicator(
                    borderRadius: BorderRadius.circular(8),
                    minHeight: 5,
                  ),
                ),
                const SizedBox(height: 10),
                Text(
                  'جاري تهيئة الدفاتر المحاسبية...',
                  style: TextStyle(fontSize: 12, color: pal.textSecondary),
                ),
              ],
            ),
          ),
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

  int _secretTapCount = 0;
  DateTime? _lastTapAt;

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
    final ok = await context
        .read<AppController>()
        .login(_userCtrl.text, _passCtrl.text);
    if (!mounted) return;
    setState(() {
      _loading = false;
      if (!ok) {
        _error = 'اسم المستخدم أو كلمة المرور غير صحيحة.';
      }
    });
  }

  Future<void> _handleSecretLogoTap() async {
    final now = DateTime.now();
    if (_lastTapAt == null ||
        now.difference(_lastTapAt!) > const Duration(seconds: 2)) {
      _secretTapCount = 1;
    } else {
      _secretTapCount += 1;
    }
    _lastTapAt = now;
    if (_secretTapCount < 3) return;
    _secretTapCount = 0;

    final ctrl = context.read<AppController>();
    if (ctrl.isDeveloperEdition && ctrl.developerKeyManager != null) {
      final action = await showDialog<String>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: const Text('أدوات إصدار المطور'),
          content: const Text(
            'اختر الإجراء المطلوب من بوابة المطور السرية:',
            style: TextStyle(fontSize: 13),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('إلغاء'),
            ),
            OutlinedButton(
              onPressed: () => Navigator.pop(ctx, 'admin'),
              child: const Text('بوابة الصلاحيات'),
            ),
            FilledButton.icon(
              onPressed: () => Navigator.pop(ctx, 'dev'),
              icon: const Icon(Icons.developer_mode, size: 17),
              label: const Text('لوحة المطور (Ed25519)'),
            ),
          ],
        ),
      );
      if (!mounted) return;
      if (action == 'dev') {
        await Navigator.of(context).push<void>(
          MaterialPageRoute<void>(
            builder: (_) => DeveloperConsoleSheet(
              keyManager: ctrl.developerKeyManager!,
              devDb: ctrl.developerDb,
              onKeysUpdated: ctrl.refreshLicense,
            ),
          ),
        );
        return;
      }
      if (action != 'admin') return;
    }

    final passCtrl = TextEditingController();
    final verified = await showDialog<bool>(
      context: context,
      builder: (ctx) => ErpDialogControllerScope(
        controllers: [passCtrl],
        child: AlertDialog(
          title: const Text('بوابة الإدارة'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const Text(
                'أدخل رمز الإدارة السري للدخول إلى إعدادات الصلاحيات:',
                style: TextStyle(fontSize: 13),
              ),
              const SizedBox(height: 10),
              TextField(
                controller: passCtrl,
                obscureText: true,
                textDirection: TextDirection.ltr,
                decoration: const InputDecoration(labelText: 'رمز الإدارة'),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('إلغاء'),
            ),
            FilledButton(
              onPressed: () {
                final ok = ctrl.verifyAdminGatePasscode(passCtrl.text);
                Navigator.pop(ctx, ok);
              },
              child: const Text('دخول'),
            ),
          ],
        ),
      ),
    );
    if (!mounted) return;
    if (verified == true) {
      await _showHiddenAdminDialog(ctrl);
    } else if (verified == false) {
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
      builder: (ctx) => ErpDialogControllerScope(
        controllers: [nameCtrl],
        child: StatefulBuilder(
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
                  const Text(
                    'صلاحيات ظهور الأقسام:',
                    style:
                        TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700),
                  ),
                  SwitchListTile(
                    dense: true,
                    title: const Text('المبيعات والعملاء'),
                    value: perms.sales,
                    onChanged: (v) =>
                        setModalState(() => perms = perms.copyWith(sales: v)),
                  ),
                  SwitchListTile(
                    dense: true,
                    title: const Text('المخزون والموردون'),
                    value: perms.inventory,
                    onChanged: (v) => setModalState(
                      () => perms = perms.copyWith(inventory: v),
                    ),
                  ),
                  SwitchListTile(
                    dense: true,
                    title: const Text('الحوالات'),
                    value: perms.transfers,
                    onChanged: (v) => setModalState(
                      () => perms = perms.copyWith(transfers: v),
                    ),
                  ),
                  SwitchListTile(
                    dense: true,
                    title: const Text('التقارير المالية'),
                    value: perms.reports,
                    onChanged: (v) => setModalState(
                      () => perms = perms.copyWith(reports: v),
                    ),
                  ),
                  SwitchListTile(
                    dense: true,
                    title: const Text('الأرشيف'),
                    value: perms.archive,
                    onChanged: (v) => setModalState(
                      () => perms = perms.copyWith(archive: v),
                    ),
                  ),
                  SwitchListTile(
                    dense: true,
                    title: const Text('المصاريف'),
                    value: perms.expenses,
                    onChanged: (v) => setModalState(
                      () => perms = perms.copyWith(expenses: v),
                    ),
                  ),
                ],
              ),
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(ctx),
                child: const Text('إغلاق'),
              ),
              FilledButton(
                onPressed: () async {
                  final trimmed = nameCtrl.text.trim();
                  if (trimmed.isNotEmpty) {
                    await ctrl.updateSettings(
                      ctrl.settings.copyWith(agencyName: trimmed),
                    );
                  }
                  await ctrl.updateSectionPermissions(perms);
                  if (!ctx.mounted) return;
                  Navigator.pop(ctx);
                },
                child: const Text('حفظ التغييرات'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final pal = context.pal;
    final ctrl = context.watch<AppController>();
    final wide = MediaQuery.sizeOf(context).width >= 760;

    final loginCard = Card(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Flexible(
                  child: Container(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                    decoration: BoxDecoration(
                      color: pal.brandSoft,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Text(
                      'نظام محاسبي مؤسسي',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                        color: pal.brand,
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                IconButton(
                  tooltip: pal.isDark ? 'المظهر الفاتح' : 'المظهر الداكن',
                  onPressed: () => ctrl.setThemeChoice(
                    pal.isDark
                        ? AppThemeModeChoice.lightEnterprise
                        : AppThemeModeChoice.darkBlack,
                  ),
                  icon: Icon(
                    pal.isDark
                        ? Icons.light_mode_outlined
                        : Icons.dark_mode_outlined,
                    size: 20,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Center(
              child: GestureDetector(
                key: const Key('login_logo_secret_trigger'),
                onTap: _handleSecretLogoTap,
                child: Container(
                  width: 88,
                  height: 88,
                  padding: const EdgeInsets.all(4),
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    border: Border.all(color: pal.accent, width: 2),
                    color: pal.brandSoft,
                  ),
                  child: ClipOval(
                    child: Image.asset(
                      'assets/images/app_logo.png',
                      width: 80,
                      height: 80,
                    ),
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
              'تسجيل الدخول إلى دفتر الحسابات والمبيعات',
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.bodySmall,
            ),
            const SizedBox(height: 20),
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
                  icon: Icon(
                    _obscure ? Icons.visibility_off : Icons.visibility,
                  ),
                ),
              ),
            ),
            if (_error != null) ...[
              const SizedBox(height: 12),
              ErpStatusBanner(
                tone: ErpBannerTone.danger,
                title: _error!,
              ),
            ],
            const SizedBox(height: 16),
            FilledButton.icon(
              onPressed: _loading ? null : _submit,
              icon: const Icon(Icons.login),
              label: Text(_loading ? 'جاري التحقق...' : 'دخول إلى النظام'),
            ),
            const SizedBox(height: 12),
            Text(
              'الحساب الافتراضي لأول تشغيل: admin / 123456',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 11, color: pal.textSecondary),
            ),
          ],
        ),
      ),
    );

    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(20),
            child: ConstrainedBox(
              constraints: BoxConstraints(maxWidth: wide ? 860.0 : 420.0),
              child: wide
                  ? Row(
                      children: [
                        Expanded(
                          child: Container(
                            padding: const EdgeInsets.all(28),
                            decoration: BoxDecoration(
                              color: pal.brandSoft,
                              borderRadius: BorderRadius.circular(20),
                              border: Border.all(color: pal.border),
                            ),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Text(
                                  'ميزان القات',
                                  style: Theme.of(context)
                                      .textTheme
                                      .headlineMedium
                                      ?.copyWith(color: pal.brand),
                                ),
                                const SizedBox(height: 8),
                                Text(
                                  'منظومة محاسبة متكاملة لوكالات وتجارة القات بدقة دفترية كاملة.',
                                  style: Theme.of(context).textTheme.bodyMedium,
                                ),
                                const SizedBox(height: 18),
                                const _FeatureBullet(
                                  Icons.account_balance_outlined,
                                  'فصل صارم للعملات (YER / SAR) بدون خلط مالي',
                                ),
                                const _FeatureBullet(
                                  Icons.fact_check_outlined,
                                  'التحاسب اليومي وجرد المخزون وكشوفات الحساب',
                                ),
                                const _FeatureBullet(
                                  Icons.print_outlined,
                                  'طباعة حرارية 58/80 مم وتقارير PDF وExcel',
                                ),
                                const _FeatureBullet(
                                  Icons.verified_user_outlined,
                                  'نسخ احتياطي ذري محمي وتراخيص Ed25519',
                                ),
                              ],
                            ),
                          ),
                        ),
                        const SizedBox(width: 20),
                        Expanded(child: loginCard),
                      ],
                    )
                  : loginCard,
            ),
          ),
        ),
      ),
    );
  }
}

class _FeatureBullet extends StatelessWidget {
  const _FeatureBullet(this.icon, this.text);
  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    final pal = context.pal;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 5),
      child: Row(
        children: [
          Icon(icon, size: 18, color: pal.brand),
          const SizedBox(width: 10),
          Expanded(
            child: Text(text, style: const TextStyle(fontSize: 12.5)),
          ),
        ],
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
      text: s.displaySarToYerMilli > 0
          ? QuantityMath.formatMilli(s.displaySarToYerMilli)
          : '',
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
        agencyName: _nameCtrl.text.trim().isEmpty
            ? prev.agencyName
            : _nameCtrl.text.trim(),
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
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 680),
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              const ErpStatusBanner(
                tone: ErpBannerTone.info,
                title: 'مرحباً بك في ميزان القات',
                subtitle: 'أكمل البيانات الأساسية لتخصيص الفواتير والعملات والمظهر.',
              ),
              const SizedBox(height: 12),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      const ErpSectionHeader(
                        title: 'بيانات المكتب / الوكالة',
                        icon: Icons.storefront_outlined,
                      ),
                      const SizedBox(height: 14),
                      TextField(
                        controller: _nameCtrl,
                        decoration:
                            const InputDecoration(labelText: 'اسم الوكالة'),
                      ),
                      const SizedBox(height: 10),
                      TextField(
                        controller: _phoneCtrl,
                        textDirection: TextDirection.ltr,
                        keyboardType: TextInputType.phone,
                        decoration:
                            const InputDecoration(labelText: 'رقم الهاتف'),
                      ),
                      const SizedBox(height: 10),
                      TextField(
                        controller: _addrCtrl,
                        decoration:
                            const InputDecoration(labelText: 'العنوان / السوق'),
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
                      const ErpSectionHeader(
                        title: 'نظام العملة والمظهر',
                        icon: Icons.currency_exchange,
                      ),
                      const SizedBox(height: 8),
                      RadioGroup<CurrencyMode>(
                        groupValue: _currencyMode,
                        onChanged: (v) => setState(
                          () => _currencyMode = v ?? CurrencyMode.yerOnly,
                        ),
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
                          ButtonSegment<AppThemeModeChoice>(
                            value: AppThemeModeChoice.lightEnterprise,
                            label: Text('فاتح مؤسسي'),
                            icon: Icon(Icons.light_mode_outlined),
                          ),
                          ButtonSegment<AppThemeModeChoice>(
                            value: AppThemeModeChoice.darkBlack,
                            label: Text('أسود داكن'),
                            icon: Icon(Icons.dark_mode_outlined),
                          ),
                        ],
                        selected: {_themeChoice},
                        onSelectionChanged: (s) {
                          final next = s.first;
                          setState(() => _themeChoice = next);
                          unawaited(
                            context.read<AppController>().setThemeChoice(next),
                          );
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
        ),
      ),
    );
  }
}
