import 'dart:async';
import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/services.dart';
import 'package:image_picker/image_picker.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:share_plus/share_plus.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../core/platform_bridge.dart';
import '../../core/theme.dart';
import '../../data/models.dart';
import '../../licensing/license_models.dart';
import '../../services/whatsapp_share_service.dart';
import '../../state/app_controller.dart';
import '../widgets/common_widgets.dart';
import '../widgets/erp_components.dart';

class LicenseAndSecurityView extends StatefulWidget {
  const LicenseAndSecurityView({super.key});

  @override
  State<LicenseAndSecurityView> createState() => _LicenseAndSecurityViewState();
}

class _LicenseAndSecurityViewState extends State<LicenseAndSecurityView> {
  final _tokenCtrl = TextEditingController();
  final _newUserCtrl = TextEditingController();
  final _currentPassCtrl = TextEditingController();
  final _newPassCtrl = TextEditingController();
  final _confirmPassCtrl = TextEditingController();
  bool _seededUsername = false;
  bool _obscureCurrent = true;
  bool _obscureNew = true;
  bool _obscureConfirm = true;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (!_seededUsername) {
      _seededUsername = true;
      _newUserCtrl.text =
          context.read<AppController>().currentUser?.username ?? '';
    }
  }

  @override
  void dispose() {
    _tokenCtrl.dispose();
    _newUserCtrl.dispose();
    _currentPassCtrl.dispose();
    _newPassCtrl.dispose();
    _confirmPassCtrl.dispose();
    super.dispose();
  }

  Future<void> _activateRaw(AppController ctrl, String raw) async {
    final res = await ctrl.activateLicense(raw);
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          res.isValid
              ? 'تم تفعيل الترخيص بنجاح!'
              : '${res.kind.arabicTitle}: ${res.reason}',
        ),
      ),
    );
    if (res.isValid) _tokenCtrl.clear();
  }

  Future<void> _scanQrAndActivate(
    AppController ctrl,
    ImageSource source,
  ) async {
    final messenger = ScaffoldMessenger.of(context);
    try {
      final picked = await ImagePicker().pickImage(source: source);
      if (picked == null) return;
      final decoded =
          await PlatformBridge.instance.decodeQrFromImagePath(picked.path);
      if (!mounted) return;
      if (decoded == null || decoded.trim().isEmpty) {
        messenger.showSnackBar(
          const SnackBar(
            content: Text(
              'تعذر قراءة باركود QR صالح من الصورة. تأكد من وضوح الكود أو ألصق النص مباشرة.',
            ),
          ),
        );
        return;
      }
      _tokenCtrl.text = decoded.trim();
      await _activateRaw(ctrl, decoded);
    } catch (e) {
      if (!mounted) return;
      messenger.showSnackBar(
        SnackBar(content: Text('تعذر تشغيل الماسح: $e')),
      );
    }
  }

  Future<void> _addOrEditUserDialog(
    AppController ctrl, {
    AppUser? existing,
  }) async {
    final userCtrl = TextEditingController(text: existing?.username ?? '');
    final nameCtrl = TextEditingController(text: existing?.displayName ?? '');
    final passCtrl = TextEditingController();
    UserRole role = existing?.role ?? UserRole.cashier;
    bool canDelete = existing?.canDelete ?? false;
    bool canEditPrices = existing?.canEditPrices ?? true;
    bool canManageSettings = existing?.canManageSettings ?? false;
    bool canViewReports = existing?.canViewReports ?? true;

    await showDialog<void>(
      context: context,
      builder: (ctx) => ErpDialogControllerScope(
        controllers: [userCtrl, nameCtrl, passCtrl],
        child: StatefulBuilder(
          builder: (ctx, setModalState) => AlertDialog(
            title: Text(
              existing == null
                  ? 'إضافة مستخدم وصلاحياته'
                  : 'تعديل صلاحيات ${existing.displayName}',
            ),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: userCtrl,
                  enabled: existing == null,
                  textDirection: TextDirection.ltr,
                  decoration: const InputDecoration(labelText: 'اسم الدخول *'),
                ),
                const SizedBox(height: 8),
                TextField(
                  controller: nameCtrl,
                  decoration: const InputDecoration(labelText: 'الاسم الظاهر'),
                ),
                const SizedBox(height: 8),
                TextField(
                  controller: passCtrl,
                  obscureText: true,
                  textDirection: TextDirection.ltr,
                  decoration: InputDecoration(
                    labelText: existing == null
                        ? 'كلمة المرور (4+ أحرف) *'
                        : 'كلمة مرور جديدة (اتركها فارغة للإبقاء)',
                  ),
                ),
                const SizedBox(height: 8),
                SegmentedButton<UserRole>(
                  segments: const [
                    ButtonSegment<UserRole>(
                      value: UserRole.cashier,
                      label: Text('بائع / محاسب'),
                    ),
                    ButtonSegment<UserRole>(
                      value: UserRole.admin,
                      label: Text('مدير'),
                    ),
                  ],
                  selected: {role},
                  onSelectionChanged: (s) => setModalState(() => role = s.first),
                ),
                SwitchListTile(
                  dense: true,
                  title: const Text('صلاحية الحذف'),
                  value: canDelete,
                  onChanged: (v) => setModalState(() => canDelete = v),
                ),
                SwitchListTile(
                  dense: true,
                  title: const Text('تعديل الأسعار'),
                  value: canEditPrices,
                  onChanged: (v) => setModalState(() => canEditPrices = v),
                ),
                SwitchListTile(
                  dense: true,
                  title: const Text('عرض التقارير المالية'),
                  value: canViewReports,
                  onChanged: (v) => setModalState(() => canViewReports = v),
                ),
                SwitchListTile(
                  dense: true,
                  title: const Text('إدارة الإعدادات والمستخدمين'),
                  value: canManageSettings,
                  onChanged: (v) => setModalState(() => canManageSettings = v),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('إلغاء'),
            ),
            FilledButton(
              onPressed: () async {
                try {
                  await ctrl.addOrUpdateUser(
                    username: userCtrl.text,
                    displayName: nameCtrl.text,
                    password: passCtrl.text,
                    role: role,
                    canDelete: canDelete,
                    canEditPrices: canEditPrices,
                    canManageSettings: canManageSettings,
                    canViewReports: canViewReports,
                  );
                  if (ctx.mounted) Navigator.pop(ctx);
                } catch (e) {
                  if (!ctx.mounted) return;
                  ScaffoldMessenger.of(ctx).showSnackBar(
                    SnackBar(content: Text(e.toString())),
                  );
                }
              },
              child: const Text('حفظ المستخدم'),
            ),
          ],
        ),
        ),
      ),
    );
  }

  Future<void> _openDeveloperConsoleWithPasscode(AppController ctrl) async {
    final passCtrl = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => ErpDialogControllerScope(
        controllers: [passCtrl],
        child: AlertDialog(
          title: const Text('بوابة المطور'),
          content: TextField(
            controller: passCtrl,
            obscureText: true,
            textDirection: TextDirection.ltr,
            decoration: const InputDecoration(labelText: 'رمز المطور'),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('إلغاء'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(
                ctx,
                ctrl.verifyAdminGatePasscode(passCtrl.text),
              ),
              child: const Text('دخول'),
            ),
          ],
        ),
      ),
    );
    final devBridge = ctrl.developerBridge;
    if (ok == true && mounted && devBridge != null) {
      await Navigator.of(context).push<void>(
        MaterialPageRoute<void>(
          builder: (_) => devBridge.buildConsoleScreen(
            onKeysUpdated: ctrl.refreshLicense,
          ),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final pal = context.pal;
    final lic = ctrl.licenseStatus;
    final perms = ctrl.sectionPermissions;

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        if (ctrl.editionConfig.isCustomer &&
            ctrl.editionConfig.publicKeysCsv.trim().isEmpty) ...[
          const ErpStatusBanner(
            tone: ErpBannerTone.warning,
            icon: Icons.key_off_outlined,
            title: 'تنبيه بناء نسخة العميل (QAT_LICENSE_PUBLIC_KEYS فارغ)',
            subtitle:
                'هذه النسخة مبنية بدون مفتاح عام مدمج للمطور. أدخل QAT_LICENSE_PUBLIC_KEYS عند البناء أو في gradle.properties لتفعيل التراخيص الموقّعة.',
          ),
          const SizedBox(height: 12),
        ],
        Card(
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  children: [
                    Icon(
                      lic.isValid ? Icons.verified_user : Icons.lock_outline,
                      color: lic.isValid ? pal.success : pal.warning,
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        'حالة الترخيص: ${lic.kind.arabicTitle}',
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 6),
                Text(
                  lic.isValid && lic.payload != null
                      ? 'العميل: ${lic.payload!.customerName} • الباقة: ${lic.payload!.effectiveTier.arabicName} (${lic.payload!.effectiveLicenseType.arabicName})${lic.remainingDays != null ? ' • متبقي: ${lic.remainingDays} يوم' : ' • ترخيص دائم'}'
                      : (lic.reason.isEmpty
                          ? lic.kind.arabicDescription
                          : lic.reason),
                  style: TextStyle(fontSize: 12.5, color: pal.textSecondary),
                ),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 6,
                  runSpacing: 6,
                  children: [
                    StatusPill(
                      label: lic.isPro ? 'الباقة الاحترافية (PRO)' : 'الباقة الأساسية (BASIC)',
                      bg: lic.isPro ? pal.successSoft : pal.warningSoft,
                      fg: lic.isPro ? pal.success : pal.warning,
                      icon: lic.isPro ? Icons.workspace_premium : Icons.shield_outlined,
                    ),
                    StatusPill(
                      label: lic.hasFeature(LicenseFeature.reports)
                          ? 'التقارير: مفعّلة'
                          : 'التقارير: PRO فقط',
                      bg: lic.hasFeature(LicenseFeature.reports)
                          ? pal.successSoft
                          : pal.cardElevated,
                      fg: lic.hasFeature(LicenseFeature.reports)
                          ? pal.success
                          : pal.textSecondary,
                    ),
                    StatusPill(
                      label: lic.hasFeature(LicenseFeature.archive)
                          ? 'الأرشيف: مفعّل'
                          : 'الأرشيف: PRO فقط',
                      bg: lic.hasFeature(LicenseFeature.archive)
                          ? pal.successSoft
                          : pal.cardElevated,
                      fg: lic.hasFeature(LicenseFeature.archive)
                          ? pal.success
                          : pal.textSecondary,
                    ),
                  ],
                ),
                const Divider(height: 20),
                const Text(
                  'معرف تثبيت الجهاز (Installation ID):',
                  style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
                ),
                const SizedBox(height: 6),
                Row(
                  children: [
                    Expanded(
                      child: SelectableText(
                        ctrl.installationId,
                        textDirection: TextDirection.ltr,
                        style: const TextStyle(
                          fontWeight: FontWeight.w700,
                          fontSize: 13,
                        ),
                      ),
                    ),
                    IconButton(
                      tooltip: 'نسخ',
                      onPressed: () {
                        unawaited(
                          Clipboard.setData(
                            ClipboardData(text: ctrl.installationId),
                          ),
                        );
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(content: Text('تم نسخ معرف الجهاز')),
                        );
                      },
                      icon: const Icon(Icons.copy, size: 18),
                    ),
                    IconButton(
                      tooltip: 'مشاركة طلب ترخيص',
                      onPressed: () async {
                        await Share.share(
                          'طلب ترخيص ميزان القات\n'
                          'الوكالة: ${ctrl.settings.agencyName}\n'
                          'معرف الجهاز: ${ctrl.installationId}',
                        );
                      },
                      icon: const Icon(Icons.share_outlined, size: 18),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                Center(
                  child: Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: QrImageView(data: ctrl.installationId, size: 120),
                  ),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: _tokenCtrl,
                  textDirection: TextDirection.ltr,
                  maxLines: 2,
                  decoration: const InputDecoration(
                    labelText: 'لصق رمز الترخيص (QL1... أو JSON)',
                  ),
                ),
                const SizedBox(height: 10),
                Row(
                  children: [
                    Expanded(
                      child: FilledButton(
                        onPressed: () => _activateRaw(ctrl, _tokenCtrl.text),
                        child: const Text('تفعيل بالرمز'),
                      ),
                    ),
                    const SizedBox(width: 8),
                    OutlinedButton.icon(
                      onPressed: () async {
                        final res = await FilePicker.platform.pickFiles(
                          type: FileType.any,
                        );
                        final path = res?.files.single.path;
                        if (path == null) return;
                        final content = await File(path).readAsString();
                        if (!mounted) return;
                        await _activateRaw(ctrl, content);
                      },
                      icon: const Icon(Icons.file_open_outlined, size: 16),
                      label: const Text('ملف .qlic'),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    OutlinedButton.icon(
                      onPressed: () =>
                          _scanQrAndActivate(ctrl, ImageSource.camera),
                      icon: const Icon(Icons.qr_code_scanner, size: 17),
                      label: const Text('مسح QR بالكاميرا'),
                    ),
                    OutlinedButton.icon(
                      onPressed: () =>
                          _scanQrAndActivate(ctrl, ImageSource.gallery),
                      icon: const Icon(Icons.qr_code_2_outlined, size: 17),
                      label: const Text('استيراد صورة QR'),
                    ),
                    OutlinedButton.icon(
                      onPressed: () async {
                        final clip = await Clipboard.getData('text/plain');
                        final txt = clip?.text?.trim() ?? '';
                        if (txt.isEmpty) {
                          if (!context.mounted) return;
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(content: Text('الحافظة فارغة')),
                          );
                          return;
                        }
                        _tokenCtrl.text = txt;
                        await _activateRaw(ctrl, txt);
                      },
                      icon: const Icon(Icons.content_paste_go, size: 17),
                      label: const Text('لصق وتفعيل'),
                    ),
                  ],
                ),
                if (lic.payload != null) ...[
                  const SizedBox(height: 8),
                  Align(
                    alignment: AlignmentDirectional.centerEnd,
                    child: TextButton.icon(
                      onPressed: () async {
                        final ok = await showErpConfirmDialog(
                          context,
                          title: 'إزالة الترخيص الحالي',
                          message:
                              'هل تريد إزالة رمز الترخيص المخزن والعودة إلى الوضع الأساسي (BASIC)؟ لن تتأثر سجلاتك المالية.',
                          confirmLabel: 'إزالة الترخيص',
                          destructive: true,
                        );
                        if (!ok || !context.mounted) return;
                        await ctrl.clearActiveLicense();
                        if (!context.mounted) return;
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(
                            content: Text('تم إزالة الترخيص والعودة للباقة الأساسية'),
                          ),
                        );
                      },
                      icon: Icon(Icons.link_off, size: 16, color: pal.danger),
                      label: Text(
                        'إزالة الترخيص النشط',
                        style: TextStyle(color: pal.danger),
                      ),
                    ),
                  ),
                ],
              ],
            ),
          ),
        ),
        const SizedBox(height: 12),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  'تغيير بيانات الدخول وأمان الجلسة',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                if (ctrl.currentUserLegacyWeakPassword) ...[
                  const SizedBox(height: 8),
                  const ErpStatusBanner(
                    tone: ErpBannerTone.warning,
                    title: 'تنبيه أمني للحساب المرحّل',
                    subtitle:
                        'هذا الحساب يستخدم كلمة مرور قديمة مرحّلة؛ يرجى تحديثها بكلمة مرور قوية الآن.',
                  ),
                ],
                const SizedBox(height: 10),
                TextField(
                  controller: _newUserCtrl,
                  textDirection: TextDirection.ltr,
                  decoration: const InputDecoration(
                    labelText: 'اسم المستخدم الجديد',
                  ),
                ),
                const SizedBox(height: 8),
                TextField(
                  controller: _currentPassCtrl,
                  obscureText: _obscureCurrent,
                  textDirection: TextDirection.ltr,
                  decoration: InputDecoration(
                    labelText: 'كلمة المرور الحالية',
                    suffixIcon: IconButton(
                      onPressed: () =>
                          setState(() => _obscureCurrent = !_obscureCurrent),
                      icon: Icon(
                        _obscureCurrent
                            ? Icons.visibility_off
                            : Icons.visibility,
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 8),
                TextField(
                  controller: _newPassCtrl,
                  obscureText: _obscureNew,
                  textDirection: TextDirection.ltr,
                  decoration: InputDecoration(
                    labelText: 'كلمة المرور الجديدة (6 أحرف على الأقل)',
                    suffixIcon: IconButton(
                      onPressed: () =>
                          setState(() => _obscureNew = !_obscureNew),
                      icon: Icon(
                        _obscureNew ? Icons.visibility_off : Icons.visibility,
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 8),
                TextField(
                  controller: _confirmPassCtrl,
                  obscureText: _obscureConfirm,
                  textDirection: TextDirection.ltr,
                  decoration: InputDecoration(
                    labelText: 'تأكيد كلمة المرور الجديدة',
                    suffixIcon: IconButton(
                      onPressed: () =>
                          setState(() => _obscureConfirm = !_obscureConfirm),
                      icon: Icon(
                        _obscureConfirm
                            ? Icons.visibility_off
                            : Icons.visibility,
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 8),
                SwitchListTile(
                  dense: true,
                  contentPadding: EdgeInsets.zero,
                  title: const Text('طلب تسجيل الدخول عند كل فتح للتطبيق'),
                  subtitle: const Text(
                    'عند التعطيل تبقى الجلسة محفوظة حتى تسجيل الخروج يدوياً',
                  ),
                  value: ctrl.settings.appLockEnabled,
                  onChanged: (v) async {
                    await ctrl.updateSettings(
                      ctrl.settings.copyWith(appLockEnabled: v),
                    );
                  },
                ),
                const SizedBox(height: 10),
                Row(
                  children: [
                    Expanded(
                      child: FilledButton.tonal(
                        onPressed: () async {
                          final messenger = ScaffoldMessenger.of(context);
                          if (_currentPassCtrl.text.isEmpty) {
                            messenger.showSnackBar(
                              const SnackBar(
                                content: Text('أدخل كلمة المرور الحالية للتأكيد'),
                              ),
                            );
                            return;
                          }
                          if (_newPassCtrl.text != _confirmPassCtrl.text) {
                            messenger.showSnackBar(
                              const SnackBar(
                                content: Text('تأكيد كلمة المرور الجديدة غير مطابق'),
                              ),
                            );
                            return;
                          }
                          final ok = await showErpConfirmDialog(
                            context,
                            title: 'تأكيد تغيير بيانات الدخول',
                            message:
                                'سيتم تحديث اسم المستخدم وكلمة المرور للحساب الحالي، ولن تتأثر بيانات الوكالة أو الدفاتر.',
                            confirmLabel: 'تحديث الآن',
                          );
                          if (!ok || !mounted) return;
                          try {
                            await ctrl.updateCredentials(
                              newUsername: _newUserCtrl.text,
                              newPassword: _newPassCtrl.text,
                              currentPassword: _currentPassCtrl.text,
                            );
                            _currentPassCtrl.clear();
                            _newPassCtrl.clear();
                            _confirmPassCtrl.clear();
                            if (!mounted) return;
                            messenger.showSnackBar(
                              const SnackBar(
                                content: Text('تم تحديث بيانات الدخول بنجاح'),
                              ),
                            );
                          } catch (e) {
                            if (!mounted) return;
                            messenger.showSnackBar(
                              SnackBar(content: Text(e.toString())),
                            );
                          }
                        },
                        child: const Text('تحديث الدخول'),
                      ),
                    ),
                    const SizedBox(width: 8),
                    OutlinedButton.icon(
                      onPressed: () async {
                        final ok = await showErpConfirmDialog(
                          context,
                          title: 'قفل النظام وتسجيل الخروج',
                          message:
                              'هل تريد قفل التطبيق والعودة إلى شاشة الدخول؟ ستبقى جميع بيانات الوكالة والحسابات محفوظة.',
                          confirmLabel: 'خروج وقفل',
                        );
                        if (ok) await ctrl.logout();
                      },
                      icon: const Icon(Icons.logout, size: 16),
                      label: const Text('تسجيل الخروج'),
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
            padding: const EdgeInsets.all(14),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Expanded(
                      child: Text(
                        'المستخدمون والصلاحيات (${ctrl.users.length})',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                    ),
                    if (ctrl.canManageSettingsCurrent)
                      TextButton.icon(
                        onPressed: () => _addOrEditUserDialog(ctrl),
                        icon: const Icon(Icons.person_add_outlined, size: 16),
                        label: const Text('إضافة مستخدم'),
                      ),
                  ],
                ),
                for (final u in ctrl.users)
                  ListTile(
                    dense: true,
                    contentPadding: EdgeInsets.zero,
                    leading: Icon(
                      u.role == UserRole.admin
                          ? Icons.admin_panel_settings
                          : Icons.person_outline,
                    ),
                    title: Text('${u.displayName} (${u.username})'),
                    subtitle: Text(
                      '${u.role.arabicLabel}${u.canDelete ? ' • حذف' : ''}${u.canEditPrices ? ' • أسعار' : ''}${u.canViewReports ? ' • تقارير' : ''}${u.canManageSettings ? ' • إعدادات' : ''}',
                    ),
                    trailing: ctrl.canManageSettingsCurrent
                        ? Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              IconButton(
                                tooltip: 'تعديل الصلاحيات',
                                onPressed: () =>
                                    _addOrEditUserDialog(ctrl, existing: u),
                                icon: const Icon(Icons.edit_outlined, size: 18),
                              ),
                              if (ctrl.users.length > 1 &&
                                  u.username != ctrl.currentUser?.username)
                                IconButton(
                                  tooltip: 'حذف المستخدم',
                                  onPressed: () async {
                                    final ok = await showErpConfirmDialog(
                                      context,
                                      title: 'حذف المستخدم',
                                      message:
                                          'هل تريد حذف المستخدم "${u.displayName}"؟',
                                      confirmLabel: 'حذف',
                                      destructive: true,
                                    );
                                    if (!ok) return;
                                    await ctrl.deleteUser(u.username);
                                  },
                                  icon: Icon(
                                    Icons.delete_outline,
                                    color: pal.danger,
                                    size: 18,
                                  ),
                                ),
                            ],
                          )
                        : null,
                  ),
              ],
            ),
          ),
        ),
        if (ctrl.canManageSettingsCurrent) ...[
          const SizedBox(height: 12),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(14),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text(
                    'صلاحيات عرض الأقسام (لغير المدراء)',
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  const SizedBox(height: 4),
                  SwitchListTile(
                    dense: true,
                    title: const Text('إتاحة قسم المبيعات والعملاء'),
                    value: perms.sales,
                    onChanged: (v) =>
                        ctrl.updateSectionPermissions(perms.copyWith(sales: v)),
                  ),
                  SwitchListTile(
                    dense: true,
                    title: const Text('إتاحة قسم المخزون والمشتريات'),
                    value: perms.inventory,
                    onChanged: (v) => ctrl.updateSectionPermissions(
                      perms.copyWith(inventory: v),
                    ),
                  ),
                  SwitchListTile(
                    dense: true,
                    title: const Text('إتاحة قسم الحوالات'),
                    value: perms.transfers,
                    onChanged: (v) => ctrl.updateSectionPermissions(
                      perms.copyWith(transfers: v),
                    ),
                  ),
                  SwitchListTile(
                    dense: true,
                    title: const Text('إتاحة التقارير المالية'),
                    value: perms.reports,
                    onChanged: (v) => ctrl.updateSectionPermissions(
                      perms.copyWith(reports: v),
                    ),
                  ),
                  SwitchListTile(
                    dense: true,
                    title: const Text('إتاحة المصاريف'),
                    value: perms.expenses,
                    onChanged: (v) => ctrl.updateSectionPermissions(
                      perms.copyWith(expenses: v),
                    ),
                  ),
                  SwitchListTile(
                    dense: true,
                    title: const Text('إتاحة مركز الأرشيف'),
                    value: perms.archive,
                    onChanged: (v) => ctrl.updateSectionPermissions(
                      perms.copyWith(archive: v),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
        if (ctrl.isDeveloperEdition && ctrl.developerBridge != null) ...[
          const SizedBox(height: 12),
          Card(
            color: pal.accentSoft,
            child: ListTile(
              leading: Icon(Icons.developer_mode, color: pal.accent),
              title: const Text('وحدة تحكم المطور (Developer Edition)'),
              subtitle: const Text('إدارة مفاتيح Ed25519 وتوقيع تراخيص العملاء'),
              trailing: const Icon(Icons.arrow_forward_ios, size: 16),
              onTap: () => _openDeveloperConsoleWithPasscode(ctrl),
            ),
          ),
        ],
        const SizedBox(height: 12),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  'طلب الترخيص والدعم الفني والمطور',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                const SizedBox(height: 6),
                const Text(
                  'م. محمد أمين ردمان الحاج • Eng. Mohammed Amin Radman Al-Hajj',
                  style: TextStyle(fontSize: 13, fontWeight: FontWeight.w700),
                ),
                const SizedBox(height: 4),
                Text(
                  'للحصول على كود التفعيل للباقة الاحترافية (PRO) أو الدعم الفني، أرسل معرّف تثبيت الجهاز عبر واتساب أو اتصل مباشرة.',
                  style: TextStyle(fontSize: 12, color: pal.textSecondary),
                ),
                const SizedBox(height: 8),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      '780961823',
                      textDirection: TextDirection.ltr,
                      style: TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w800,
                        color: pal.brand,
                      ),
                    ),
                    Text(
                      'الحقوق محفوظة © 2026',
                      style: TextStyle(fontSize: 11.5, color: pal.textSecondary),
                    ),
                  ],
                ),
                const SizedBox(height: 10),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    FilledButton.icon(
                      onPressed: () async {
                        final messenger = ScaffoldMessenger.of(context);
                        final dialUri = Uri(scheme: 'tel', path: '780961823');
                        try {
                          final launched = await launchUrl(
                            dialUri,
                            mode: LaunchMode.externalApplication,
                          );
                          if (!launched && mounted) {
                            messenger.showSnackBar(
                              const SnackBar(
                                content: Text(
                                  'تعذر بدء الاتصال تلقائياً، الرقم هو: 780961823',
                                ),
                              ),
                            );
                          }
                        } catch (_) {
                          if (!mounted) return;
                          messenger.showSnackBar(
                            const SnackBar(
                              content: Text(
                                'تعذر بدء الاتصال تلقائياً، الرقم هو: 780961823',
                              ),
                            ),
                          );
                        }
                      },
                      icon: const Icon(Icons.phone_outlined, size: 17),
                      label: const Text('اتصال مباشر'),
                    ),
                    FilledButton.tonalIcon(
                      onPressed: () async {
                        await WhatsappShareService.instance.openWhatsApp(
                          phone: '780961823',
                          text:
                              'مرحباً، أرغب في تفعيل ترخيص ميزان القات (PRO):\n'
                              'الوكالة: ${ctrl.settings.agencyName}\n'
                              'معرّف التثبيت: ${ctrl.installationId}',
                        );
                      },
                      icon: const Icon(Icons.chat_outlined, size: 17),
                      label: const Text('طلب التفعيل عبر واتساب'),
                    ),
                    OutlinedButton.icon(
                      onPressed: () async {
                        await Share.share(
                          'طلب ترخيص ميزان القات\n'
                          'الوكالة: ${ctrl.settings.agencyName}\n'
                          'معرّف التثبيت: ${ctrl.installationId}',
                        );
                      },
                      icon: const Icon(Icons.share_outlined, size: 17),
                      label: const Text('مشاركة طلب الترخيص'),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}
