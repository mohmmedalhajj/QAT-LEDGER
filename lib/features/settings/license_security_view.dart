import 'dart:async';
import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/services.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:share_plus/share_plus.dart';

import '../../core/theme.dart';
import '../../data/models.dart';
import '../../developer/developer_console_sheet.dart';
import '../../licensing/license_models.dart';
import '../../state/app_controller.dart';
import '../widgets/erp_components.dart';

class LicenseAndSecurityView extends StatefulWidget {
  const LicenseAndSecurityView({super.key});

  @override
  State<LicenseAndSecurityView> createState() => _LicenseAndSecurityViewState();
}

class _LicenseAndSecurityViewState extends State<LicenseAndSecurityView> {
  final _tokenCtrl = TextEditingController();
  final _newUserCtrl = TextEditingController(text: 'admin');
  final _newPassCtrl = TextEditingController();

  @override
  void dispose() {
    _tokenCtrl.dispose();
    _newUserCtrl.dispose();
    _newPassCtrl.dispose();
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

  Future<void> _addOrEditUserDialog(AppController ctrl) async {
    final userCtrl = TextEditingController();
    final nameCtrl = TextEditingController();
    final passCtrl = TextEditingController();
    UserRole role = UserRole.cashier;
    bool canDelete = false;
    bool canEditPrices = true;
    bool canManageSettings = false;
    bool canViewReports = true;

    await showDialog<void>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setModalState) => AlertDialog(
          title: const Text('إضافة مستخدم وصلاحياته'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: userCtrl,
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
                  decoration: const InputDecoration(
                    labelText: 'كلمة المرور (4+ أحرف) *',
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
    );
    userCtrl.dispose();
    nameCtrl.dispose();
    passCtrl.dispose();
  }

  Future<void> _openDeveloperConsoleWithPasscode(AppController ctrl) async {
    final passCtrl = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('بوابة المطور'),
        content: TextField(
          controller: passCtrl,
          obscureText: true,
          textDirection: TextDirection.ltr,
          decoration: const InputDecoration(labelText: 'رمز المطور'),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
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
    );
    passCtrl.dispose();
    if (ok == true && mounted && ctrl.developerKeyManager != null) {
      await Navigator.of(context).push<void>(
        MaterialPageRoute<void>(
          builder: (_) => DeveloperConsoleSheet(
            keyManager: ctrl.developerKeyManager!,
            devDb: ctrl.developerDb,
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
                      ? 'العميل: ${lic.payload!.customerName} • الباقة: ${lic.payload!.planCode}'
                          '${lic.remainingDays != null ? ' • متبقي: ${lic.remainingDays} يوم' : ' • ترخيص دائم'}'
                      : (lic.reason.isEmpty
                          ? lic.kind.arabicDescription
                          : lic.reason),
                  style: TextStyle(fontSize: 12.5, color: pal.textSecondary),
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
                  'تغيير بيانات الدخول',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
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
                  controller: _newPassCtrl,
                  obscureText: true,
                  textDirection: TextDirection.ltr,
                  decoration: const InputDecoration(
                    labelText: 'كلمة المرور الجديدة',
                  ),
                ),
                const SizedBox(height: 10),
                Row(
                  children: [
                    Expanded(
                      child: FilledButton.tonal(
                        onPressed: () async {
                          final messenger = ScaffoldMessenger.of(context);
                          try {
                            await ctrl.updateCredentials(
                              newUsername: _newUserCtrl.text,
                              newPassword: _newPassCtrl.text,
                            );
                            _newPassCtrl.clear();
                            if (!mounted) return;
                            messenger.showSnackBar(
                              const SnackBar(
                                content: Text('تم تحديث كلمة المرور بنجاح'),
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
                      onPressed: ctrl.logout,
                      icon: const Icon(Icons.logout, size: 16),
                      label: const Text('قفل'),
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
                    Text(
                      'المستخدمون والصلاحيات (${ctrl.users.length})',
                      style: Theme.of(context).textTheme.titleMedium,
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
                      '${u.role.arabicLabel}'
                      '${u.canDelete ? ' • حذف' : ''}'
                      '${u.canEditPrices ? ' • أسعار' : ''}'
                      '${u.canViewReports ? ' • تقارير' : ''}'
                      '${u.canManageSettings ? ' • إعدادات' : ''}',
                    ),
                    trailing: (ctrl.users.length > 1 &&
                            u.username != ctrl.currentUser?.username &&
                            ctrl.canManageSettingsCurrent)
                        ? IconButton(
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
        if (ctrl.isDeveloperEdition) ...[
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
      ],
    );
  }
}
