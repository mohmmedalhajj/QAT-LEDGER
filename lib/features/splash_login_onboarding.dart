import 'dart:async';
import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:image_picker/image_picker.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../core/logo_storage.dart';
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
  final _userCtrl = TextEditingController();
  final _passCtrl = TextEditingController();
  bool _obscure = true;
  bool _loading = false;
  bool _rememberMe = true;
  bool _prefilledFromRemembered = false;
  String? _error;

  int _secretTapCount = 0;
  DateTime? _lastTapAt;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (!_prefilledFromRemembered) {
      _prefilledFromRemembered = true;
      final ctrl = context.read<AppController>();
      _rememberMe = ctrl.settings.rememberMe;
      final rem = ctrl.rememberedUsername?.trim() ?? '';
      if (rem.isNotEmpty) {
        _userCtrl.text = rem;
      }
    }
  }

  @override
  void dispose() {
    _userCtrl.dispose();
    _passCtrl.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final user = _userCtrl.text.trim();
    final pass = _passCtrl.text;
    if (user.isEmpty) {
      setState(() => _error = 'يرجى إدخال اسم المستخدم.');
      return;
    }
    if (pass.isEmpty) {
      setState(() => _error = 'يرجى إدخال كلمة المرور.');
      return;
    }

    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final ok = await context.read<AppController>().login(
            user,
            pass,
            rememberMe: _rememberMe,
          );
      if (!mounted) return;
      setState(() {
        _loading = false;
        if (!ok) {
          _error = 'اسم المستخدم أو كلمة المرور غير صحيحة.';
        }
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _error = 'تعذر التحقق من بيانات الدخول: $e';
      });
    }
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
    final logoPath = ctrl.settings.logoPath.trim();
    final hasAgencyLogo = logoPath.isNotEmpty && File(logoPath).existsSync();

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
                      color: ctrl.isDeveloperEdition
                          ? pal.accentSoft
                          : pal.brandSoft,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Text(
                      ctrl.isDeveloperEdition
                          ? 'إصدار المطور — نظام محاسبي مؤسسي'
                          : 'نظام محاسبي مؤسسي',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                        color: ctrl.isDeveloperEdition ? pal.accent : pal.brand,
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
            const SizedBox(height: 10),
            Center(
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  GestureDetector(
                    key: const Key('login_logo_secret_trigger'),
                    onTap: _handleSecretLogoTap,
                    child: Container(
                      width: 82,
                      height: 82,
                      padding: const EdgeInsets.all(4),
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        border: Border.all(color: pal.accent, width: 2),
                        color: pal.brandSoft,
                      ),
                      child: ClipOval(
                        child: Image.asset(
                          'assets/images/app_logo.png',
                          width: 74,
                          height: 74,
                          fit: BoxFit.cover,
                        ),
                      ),
                    ),
                  ),
                  if (hasAgencyLogo) ...[
                    const SizedBox(width: 12),
                    Container(
                      width: 82,
                      height: 82,
                      padding: const EdgeInsets.all(4),
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        border: Border.all(color: pal.brand, width: 2),
                        color: pal.cardElevated,
                      ),
                      child: ClipOval(
                        child: Image.file(
                          File(logoPath),
                          width: 74,
                          height: 74,
                          fit: BoxFit.cover,
                        ),
                      ),
                    ),
                  ],
                ],
              ),
            ),
            const SizedBox(height: 14),
            Text(
              ctrl.settings.agencyName,
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.titleLarge,
            ),
            if (ctrl.settings.tagline.trim().isNotEmpty) ...[
              const SizedBox(height: 3),
              Text(
                ctrl.settings.tagline.trim(),
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 11.5, color: pal.textSecondary),
              ),
            ],
            const SizedBox(height: 4),
            Text(
              'تسجيل الدخول إلى دفتر الحسابات والمبيعات',
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.bodySmall,
            ),
            const SizedBox(height: 20),
            TextField(
              key: const Key('login_username_field'),
              controller: _userCtrl,
              textDirection: TextDirection.ltr,
              textInputAction: TextInputAction.next,
              onChanged: (_) {
                if (_error != null) setState(() => _error = null);
              },
              decoration: const InputDecoration(
                labelText: 'اسم المستخدم',
                prefixIcon: Icon(Icons.person_outline),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              key: const Key('login_password_field'),
              controller: _passCtrl,
              obscureText: _obscure,
              textDirection: TextDirection.ltr,
              textInputAction: TextInputAction.done,
              onChanged: (_) {
                if (_error != null) setState(() => _error = null);
              },
              onSubmitted: (_) => _submit(),
              decoration: InputDecoration(
                labelText: 'كلمة المرور',
                prefixIcon: const Icon(Icons.lock_outline),
                suffixIcon: IconButton(
                  tooltip: _obscure ? 'إظهار كلمة المرور' : 'إخفاء كلمة المرور',
                  onPressed: () => setState(() => _obscure = !_obscure),
                  icon: Icon(
                    _obscure ? Icons.visibility_off : Icons.visibility,
                  ),
                ),
              ),
            ),
            const SizedBox(height: 6),
            CheckboxListTile(
              dense: true,
              contentPadding: EdgeInsets.zero,
              value: _rememberMe,
              onChanged: (v) => setState(() => _rememberMe = v ?? true),
              title: const Text(
                'إبقاء الجلسة مفتوحة وتذكر اسم المستخدم',
                style: TextStyle(fontSize: 12.5),
              ),
              controlAffinity: ListTileControlAffinity.leading,
            ),
            if (_error != null) ...[
              const SizedBox(height: 8),
              ErpStatusBanner(
                tone: ErpBannerTone.danger,
                title: _error!,
              ),
            ],
            const SizedBox(height: 14),
            FilledButton.icon(
              key: const Key('login_submit_button'),
              onPressed: _loading ? null : _submit,
              icon: _loading
                  ? const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.login),
              label: Text(_loading ? 'جاري التحقق...' : 'دخول إلى النظام'),
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
              constraints: BoxConstraints(maxWidth: wide ? 860.0 : 430.0),
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
  late final TextEditingController _ownerCtrl;
  late final TextEditingController _phoneCtrl;
  late final TextEditingController _addrCtrl;
  late final TextEditingController _rateCtrl;
  final _usernameCtrl = TextEditingController();
  final _passwordCtrl = TextEditingController();
  final _confirmPasswordCtrl = TextEditingController();

  late CurrencyMode _currencyMode;
  late AppThemeModeChoice _themeChoice;
  String _logoPath = '';
  bool _obscurePass = true;
  bool _obscureConfirm = true;
  bool _saving = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    final s = context.read<AppController>().settings;
    _nameCtrl = TextEditingController(
      text: s.onboardingComplete ? s.agencyName : '',
    );
    _ownerCtrl = TextEditingController(text: s.ownerName);
    _phoneCtrl = TextEditingController(text: s.agencyPhone);
    _addrCtrl = TextEditingController(text: s.agencyAddress);
    _rateCtrl = TextEditingController(
      text: s.displaySarToYerMilli > 0
          ? QuantityMath.formatMilli(s.displaySarToYerMilli)
          : '',
    );
    _currencyMode = s.currencyMode;
    _themeChoice = s.themeChoice;
    _logoPath = s.logoPath;
  }

  @override
  void dispose() {
    _nameCtrl.dispose();
    _ownerCtrl.dispose();
    _phoneCtrl.dispose();
    _addrCtrl.dispose();
    _rateCtrl.dispose();
    _usernameCtrl.dispose();
    _passwordCtrl.dispose();
    _confirmPasswordCtrl.dispose();
    super.dispose();
  }

  Future<void> _pickLogo() async {
    try {
      final picked = await ImagePicker().pickImage(source: ImageSource.gallery);
      if (!mounted || picked == null) return;
      final durable = await LogoStorage.persistLogoFile(picked.path);
      if (!mounted) return;
      setState(() => _logoPath = durable);
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = 'تعذر تحميل شعار الوكالة: $e');
    }
  }

  Future<void> _restoreFromExistingBackup() async {
    final ctrl = context.read<AppController>();
    try {
      final picked = await FilePicker.platform.pickFiles(type: FileType.any);
      final path = picked?.files.single.path;
      if (path == null || !mounted) return;

      final info = await ctrl.backupService.inspectBackupFile(path);
      if (!mounted) return;
      if (!info.isValid) {
        await showErpConfirmDialog(
          context,
          title: 'ملف نسخة احتياطية غير صالح',
          message: info.errorMessage ?? 'تعذر التحقق من سلامة الملف المختار.',
          confirmLabel: 'حسناً',
          cancelLabel: 'إغلاق',
        );
        return;
      }

      final ok = await showErpConfirmDialog(
        context,
        title: 'استعادة بيانات الوكالة',
        message:
            'تم التحقق من سلامة النسخة (${info.summaryLine}).\n'
            'ملاحظة أمنية: ملفات الترخيص ومفاتيح الأمان مرتبطة بكل جهاز على حدة، وبعد الاستعادة قد يلزم تفعيل الترخيص على هذا الجهاز.\nهل تريد الاستعادة الآن؟',
        confirmLabel: 'استعادة الآن',
      );
      if (!ok || !mounted) return;

      setState(() {
        _saving = true;
        _error = null;
      });
      await ctrl.backupService.restoreFromFile(path);
      await ctrl.handleDatabaseRestored();
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('تم استعادة بيانات الوكالة بنجاح والتحقق من سلامتها.'),
        ),
      );
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = 'فشل استعادة النسخة: $e');
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _finish() async {
    final ctrl = context.read<AppController>();
    final prev = ctrl.settings;
    final agencyName = _nameCtrl.text.trim();
    final ownerName = _ownerCtrl.text.trim();
    final username = _usernameCtrl.text.trim();
    final password = _passwordCtrl.text;
    final confirmPassword = _confirmPasswordCtrl.text;

    if (agencyName.isEmpty) {
      setState(() => _error = 'يرجى إدخال اسم المكتب أو الوكالة.');
      return;
    }
    if (ownerName.isEmpty) {
      setState(() => _error = 'يرجى إدخال اسم المالك / المدير.');
      return;
    }
    if (username.length < 3 || username.contains(' ')) {
      setState(
        () => _error =
            'اسم المستخدم يجب أن لا يقل عن 3 أحرف وبدون مسافات.',
      );
      return;
    }
    if (password.length < 6) {
      setState(() => _error = 'كلمة المرور يجب أن لا تقل عن 6 أحرف.');
      return;
    }
    if (password != confirmPassword) {
      setState(() => _error = 'تأكيد كلمة المرور غير مطابق.');
      return;
    }

    final rateMilli = _currencyMode == CurrencyMode.dualCurrency
        ? QuantityMath.parseMilli(_rateCtrl.text)
        : 0;

    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      await ctrl.completeFirstRunOnboarding(
        agencySettings: prev.copyWith(
          agencyName: agencyName,
          ownerName: ownerName,
          agencyPhone: _phoneCtrl.text.trim(),
          agencyAddress: _addrCtrl.text.trim(),
          logoPath: _logoPath,
          currencyMode: _currencyMode,
          displaySarToYerMilli: rateMilli,
          themeChoice: _themeChoice,
          onboardingComplete: true,
        ),
        ownerDisplayName: ownerName,
        username: username,
        password: password,
        confirmPassword: confirmPassword,
      );
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _saving = false;
        _error = e.toString();
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final pal = context.pal;
    final isLegacyPendingOwner =
        ctrl.settings.onboardingComplete && ctrl.users.isEmpty;
    final hasLogo = _logoPath.isNotEmpty && File(_logoPath).existsSync();

    return Scaffold(
      appBar: AppBar(title: const Text('تهيئة الوكالة وحساب المالك')),
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 700),
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              ErpStatusBanner(
                tone: isLegacyPendingOwner
                    ? ErpBannerTone.warning
                    : ErpBannerTone.info,
                title: isLegacyPendingOwner
                    ? 'تم ترحيل بيانات الوكالة — أنشئ حساب المالك للمتابعة'
                    : 'مرحباً بك في ميزان القات',
                subtitle: isLegacyPendingOwner
                    ? 'أنشئ اسم مستخدم وكلمة مرور جديدة خاصة بك لحماية الدفاتر المرحّلة.'
                    : 'أعدّ بيانات وكالتك وحساب المالك الخاص بك للبدء فوراً بدون أي حسابات افتراضية.',
              ),
              const SizedBox(height: 12),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      const ErpSectionHeader(
                        title: 'بيانات وهوية المكتب / الوكالة',
                        icon: Icons.storefront_outlined,
                      ),
                      const SizedBox(height: 12),
                      Row(
                        children: [
                          CircleAvatar(
                            radius: 28,
                            backgroundColor: pal.brandSoft,
                            backgroundImage: hasLogo
                                ? FileImage(File(_logoPath)) as ImageProvider<Object>
                                : const AssetImage('assets/images/app_logo.png'),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Wrap(
                              spacing: 8,
                              runSpacing: 6,
                              children: [
                                OutlinedButton.icon(
                                  onPressed: _saving ? null : _pickLogo,
                                  icon: const Icon(Icons.add_photo_alternate_outlined, size: 18),
                                  label: Text(hasLogo ? 'تغيير شعار الوكالة' : 'رفع شعار الوكالة (اختياري)'),
                                ),
                                if (hasLogo)
                                  TextButton.icon(
                                    onPressed: _saving
                                        ? null
                                        : () => setState(() => _logoPath = ''),
                                    icon: const Icon(Icons.delete_outline, size: 17),
                                    label: const Text('إزالة'),
                                  ),
                              ],
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 14),
                      TextField(
                        key: const Key('onboarding_agency_name'),
                        controller: _nameCtrl,
                        decoration: const InputDecoration(
                          labelText: 'اسم المكتب / الوكالة *',
                        ),
                      ),
                      const SizedBox(height: 10),
                      TextField(
                        key: const Key('onboarding_owner_name'),
                        controller: _ownerCtrl,
                        decoration: const InputDecoration(
                          labelText: 'اسم المالك / المدير *',
                        ),
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
                        title: 'حساب المالك وصلاحية الدخول',
                        icon: Icons.admin_panel_settings_outlined,
                      ),
                      const SizedBox(height: 12),
                      TextField(
                        key: const Key('onboarding_username'),
                        controller: _usernameCtrl,
                        textDirection: TextDirection.ltr,
                        decoration: const InputDecoration(
                          labelText: 'اسم المستخدم للمالك *',
                          hintText: 'مثال: salem_admin',
                          prefixIcon: Icon(Icons.person_outline),
                        ),
                      ),
                      const SizedBox(height: 10),
                      TextField(
                        key: const Key('onboarding_password'),
                        controller: _passwordCtrl,
                        obscureText: _obscurePass,
                        textDirection: TextDirection.ltr,
                        decoration: InputDecoration(
                          labelText: 'كلمة المرور (6 أحرف على الأقل) *',
                          prefixIcon: const Icon(Icons.lock_outline),
                          suffixIcon: IconButton(
                            onPressed: () =>
                                setState(() => _obscurePass = !_obscurePass),
                            icon: Icon(
                              _obscurePass
                                  ? Icons.visibility_off
                                  : Icons.visibility,
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(height: 10),
                      TextField(
                        key: const Key('onboarding_confirm_password'),
                        controller: _confirmPasswordCtrl,
                        obscureText: _obscureConfirm,
                        textDirection: TextDirection.ltr,
                        decoration: InputDecoration(
                          labelText: 'تأكيد كلمة المرور *',
                          prefixIcon: const Icon(Icons.lock_reset_outlined),
                          suffixIcon: IconButton(
                            onPressed: () => setState(
                              () => _obscureConfirm = !_obscureConfirm,
                            ),
                            icon: Icon(
                              _obscureConfirm
                                  ? Icons.visibility_off
                                  : Icons.visibility,
                            ),
                          ),
                        ),
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
                          unawaited(ctrl.setThemeChoice(next));
                        },
                      ),
                    ],
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
                key: const Key('onboarding_submit_button'),
                onPressed: _saving ? null : _finish,
                icon: _saving
                    ? const SizedBox(
                        width: 16,
                        height: 16,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.check_circle_outline),
                label: Text(_saving ? 'جاري الحفظ...' : 'حفظ الحساب وبدء العمل'),
              ),
              const SizedBox(height: 10),
              OutlinedButton.icon(
                onPressed: _saving ? null : _restoreFromExistingBackup,
                icon: const Icon(Icons.restore_page_outlined),
                label: const Text('استعادة نسخة احتياطية موجودة (.qatbackup / .qbak)'),
              ),
              const SizedBox(height: 20),
            ],
          ),
        ),
      ),
    );
  }
}
