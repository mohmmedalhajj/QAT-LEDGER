import 'dart:io';

import 'package:image_picker/image_picker.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../core/logo_storage.dart';
import '../../core/money.dart';
import '../../core/theme.dart';
import '../../state/app_controller.dart';
import '../widgets/common_widgets.dart';
import '../widgets/erp_components.dart';

class BrandingCurrencyThemeView extends StatefulWidget {
  const BrandingCurrencyThemeView({super.key});

  @override
  State<BrandingCurrencyThemeView> createState() =>
      _BrandingCurrencyThemeViewState();
}

class _BrandingCurrencyThemeViewState extends State<BrandingCurrencyThemeView> {
  late TextEditingController _nameCtrl;
  late TextEditingController _displayAppCtrl;
  late TextEditingController _ownerCtrl;
  late TextEditingController _phoneCtrl;
  late TextEditingController _extraContactCtrl;
  late TextEditingController _addrCtrl;
  late TextEditingController _taglineCtrl;
  late TextEditingController _commercialCtrl;
  late TextEditingController _qatTypesRawCtrl;
  late TextEditingController _headerTextCtrl;
  late TextEditingController _footerCtrl;
  late TextEditingController _rateCtrl;
  late TextEditingController _customPrimaryCtrl;
  late TextEditingController _customAccentCtrl;

  late CurrencyMode _mode;
  late String _paletteId;
  late bool _headerEnabled;
  late bool _footerEnabled;
  late bool _syncPdfColors;
  String _logoPath = '';
  AppController? _boundCtrl;

  @override
  void initState() {
    super.initState();
    final ctrl = context.read<AppController>();
    _boundCtrl = ctrl;
    final s = ctrl.settings;
    _nameCtrl = TextEditingController(text: s.agencyName);
    _displayAppCtrl = TextEditingController(text: s.appDisplayName);
    _ownerCtrl = TextEditingController(text: s.ownerName);
    _phoneCtrl = TextEditingController(text: s.agencyPhone);
    _extraContactCtrl = TextEditingController(text: s.additionalContact);
    _addrCtrl = TextEditingController(text: s.agencyAddress);
    _taglineCtrl = TextEditingController(text: s.tagline);
    _commercialCtrl = TextEditingController(text: s.commercialDetails);
    _qatTypesRawCtrl = TextEditingController(text: s.qatTypesRaw);
    _headerTextCtrl = TextEditingController(text: s.headerText);
    _footerCtrl = TextEditingController(text: s.footerNote);
    _rateCtrl = TextEditingController(
      text: s.displaySarToYerMilli > 0
          ? QuantityMath.formatMilli(s.displaySarToYerMilli)
          : '',
    );
    _customPrimaryCtrl = TextEditingController(text: s.customPrimaryHex);
    _customAccentCtrl = TextEditingController(text: s.customAccentHex);
    _mode = s.currencyMode;
    _paletteId = s.paletteId;
    _headerEnabled = s.headerEnabled;
    _footerEnabled = s.footerEnabled;
    _syncPdfColors = s.syncColorsWithPdf;
    _logoPath = s.logoPath;
    ctrl.registerUnsavedGuard('branding_settings', _hasUnsavedEdits);
  }

  bool _hasUnsavedEdits() {
    final s = _boundCtrl?.settings;
    if (s == null) return false;
    return _nameCtrl.text.trim() != s.agencyName ||
        _displayAppCtrl.text.trim() != s.appDisplayName ||
        _ownerCtrl.text.trim() != s.ownerName ||
        _phoneCtrl.text.trim() != s.agencyPhone ||
        _extraContactCtrl.text.trim() != s.additionalContact ||
        _addrCtrl.text.trim() != s.agencyAddress ||
        _taglineCtrl.text.trim() != s.tagline ||
        _commercialCtrl.text.trim() != s.commercialDetails ||
        _qatTypesRawCtrl.text.trim() != s.qatTypesRaw ||
        _headerTextCtrl.text.trim() != s.headerText ||
        _footerCtrl.text.trim() != s.footerNote ||
        _logoPath != s.logoPath ||
        _mode != s.currencyMode ||
        _headerEnabled != s.headerEnabled ||
        _footerEnabled != s.footerEnabled;
  }

  @override
  void dispose() {
    _boundCtrl?.unregisterUnsavedGuard('branding_settings');
    _nameCtrl.dispose();
    _displayAppCtrl.dispose();
    _ownerCtrl.dispose();
    _phoneCtrl.dispose();
    _extraContactCtrl.dispose();
    _addrCtrl.dispose();
    _taglineCtrl.dispose();
    _commercialCtrl.dispose();
    _qatTypesRawCtrl.dispose();
    _headerTextCtrl.dispose();
    _footerCtrl.dispose();
    _rateCtrl.dispose();
    _customPrimaryCtrl.dispose();
    _customAccentCtrl.dispose();
    super.dispose();
  }

  Future<void> _save(AppController ctrl) async {
    final prev = ctrl.settings;
    final rateMilli = _mode == CurrencyMode.dualCurrency
        ? QuantityMath.parseMilli(_rateCtrl.text)
        : 0;
    await ctrl.updateSettings(
      prev.copyWith(
        agencyName: _nameCtrl.text.trim().isEmpty
            ? prev.agencyName
            : _nameCtrl.text.trim(),
        appDisplayName: _displayAppCtrl.text.trim(),
        ownerName: _ownerCtrl.text.trim(),
        agencyPhone: _phoneCtrl.text.trim(),
        additionalContact: _extraContactCtrl.text.trim(),
        agencyAddress: _addrCtrl.text.trim(),
        tagline: _taglineCtrl.text.trim(),
        commercialDetails: _commercialCtrl.text.trim(),
        qatTypesRaw: _qatTypesRawCtrl.text.trim(),
        headerText: _headerTextCtrl.text.trim(),
        footerNote: _footerCtrl.text.trim(),
        logoPath: _logoPath,
        currencyMode: _mode,
        displaySarToYerMilli: rateMilli,
        onboardingComplete: true,
        headerEnabled: _headerEnabled,
        footerEnabled: _footerEnabled,
        paletteId: _paletteId,
        customPrimaryHex: _customPrimaryCtrl.text.trim(),
        customAccentHex: _customAccentCtrl.text.trim(),
        syncColorsWithPdf: _syncPdfColors,
      ),
    );
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('تم حفظ الإعدادات وتحديث الهوية بنجاح')),
    );
  }

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final pal = context.pal;
    final canEdit = ctrl.canManageSettingsCurrent;
    final ImageProvider<Object> logoProvider =
        (_logoPath.isNotEmpty && File(_logoPath).existsSync())
            ? FileImage(File(_logoPath))
            : const AssetImage('assets/images/app_logo.png');

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        if (!canEdit) ...[
          const ErpStatusBanner(
            tone: ErpBannerTone.warning,
            title: 'عرض فقط',
            subtitle:
                'تعديل هوية الوكالة والعملة متاح فقط للمستخدمين بصلاحية إدارة الإعدادات.',
          ),
          const SizedBox(height: 12),
        ],
        Card(
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  'مظهر التطبيق والألوان المؤسسية',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                const SizedBox(height: 10),
                SegmentedButton<AppThemeModeChoice>(
                  segments: const [
                    ButtonSegment<AppThemeModeChoice>(
                      value: AppThemeModeChoice.lightEnterprise,
                      label: Text('المظهر الفاتح المؤسسي'),
                      icon: Icon(Icons.light_mode_outlined),
                    ),
                    ButtonSegment<AppThemeModeChoice>(
                      value: AppThemeModeChoice.darkBlack,
                      label: Text('المظهر الأسود الداكن'),
                      icon: Icon(Icons.dark_mode_outlined),
                    ),
                  ],
                  selected: {ctrl.settings.themeChoice},
                  onSelectionChanged: (s) => ctrl.setThemeChoice(s.first),
                ),
                const SizedBox(height: 14),
                const Text(
                  'السمات اللونية الرسمية (8 سمات + لون مخصص):',
                  style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700),
                ),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    for (final preset in AppColorPreset.allPresets)
                      ChoiceChip(
                        avatar: CircleAvatar(
                          backgroundColor: preset.primaryNormal,
                          radius: 9,
                        ),
                        label: Text(preset.nameAr.split('(').first.trim()),
                        selected: _paletteId == preset.id,
                        onSelected: !canEdit
                            ? null
                            : (_) async {
                                setState(() => _paletteId = preset.id);
                                await ctrl.setColorPalette(paletteId: preset.id);
                              },
                      ),
                    ChoiceChip(
                      avatar: CircleAvatar(
                        backgroundColor: AppColorPreset.tryParseHex(
                              _customPrimaryCtrl.text,
                            ) ??
                            pal.brand,
                        radius: 9,
                      ),
                      label: const Text('لون مخصص (Hex)'),
                      selected: _paletteId == 'custom',
                      onSelected: !canEdit
                          ? null
                          : (_) async {
                              setState(() => _paletteId = 'custom');
                              await ctrl.setColorPalette(
                                paletteId: 'custom',
                                customPrimaryHex: _customPrimaryCtrl.text,
                                customAccentHex: _customAccentCtrl.text,
                              );
                            },
                    ),
                  ],
                ),
                if (_paletteId == 'custom') ...[
                  const SizedBox(height: 10),
                  Row(
                    children: [
                      Expanded(
                        child: TextField(
                          controller: _customPrimaryCtrl,
                          enabled: canEdit,
                          textDirection: TextDirection.ltr,
                          decoration: const InputDecoration(
                            labelText: 'اللون الأساسي (#RRGGBB)',
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: TextField(
                          controller: _customAccentCtrl,
                          enabled: canEdit,
                          textDirection: TextDirection.ltr,
                          decoration: const InputDecoration(
                            labelText: 'اللون المساعد (#RRGGBB)',
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
                const SizedBox(height: 6),
                SwitchListTile(
                  dense: true,
                  contentPadding: EdgeInsets.zero,
                  title: const Text('مزامنة ألوان المظهر مع فواتير وتقارير PDF'),
                  value: _syncPdfColors,
                  onChanged: !canEdit
                      ? null
                      : (v) async {
                          setState(() => _syncPdfColors = v);
                          await ctrl.setColorPalette(
                            paletteId: _paletteId,
                            syncColorsWithPdf: v,
                          );
                        },
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
                  'إدارة العملة المركزية',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                const SizedBox(height: 6),
                RadioGroup<CurrencyMode>(
                  groupValue: _mode,
                  onChanged: (v) {
                    if (!canEdit) return;
                    setState(() => _mode = v ?? CurrencyMode.yerOnly);
                  },
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
                if (_mode == CurrencyMode.dualCurrency) ...[
                  const SizedBox(height: 6),
                  AccountingNumberField(
                    controller: _rateCtrl,
                    label: 'سعر صرف تقديري للعرض فقط (1 SAR -> YER)',
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
                  'بيانات وهوية الوكالة والتطبيق',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                const SizedBox(height: 10),
                Row(
                  children: [
                    CircleAvatar(
                      radius: 26,
                      backgroundImage: logoProvider,
                    ),
                    const SizedBox(width: 12),
                    Flexible(
                      child: Wrap(
                        spacing: 8,
                        runSpacing: 6,
                        children: [
                          OutlinedButton.icon(
                            onPressed: !canEdit
                                ? null
                                : () async {
                                    final messenger =
                                        ScaffoldMessenger.of(context);
                                    final picked = await ImagePicker()
                                        .pickImage(source: ImageSource.gallery);
                                    if (!mounted || picked == null) return;
                                    try {
                                      final durablePath =
                                          await LogoStorage.persistLogoFile(
                                        picked.path,
                                      );
                                      if (!mounted) return;
                                      setState(() => _logoPath = durablePath);
                                    } catch (e) {
                                      if (!mounted) return;
                                      messenger.showSnackBar(
                                        SnackBar(
                                          content: Text('تعذر حفظ الشعار: $e'),
                                        ),
                                      );
                                    }
                                  },
                            icon: const Icon(Icons.image_outlined, size: 18),
                            label: const Text('تغيير شعار الوكالة'),
                          ),
                          if (_logoPath.isNotEmpty)
                            TextButton.icon(
                              onPressed: !canEdit
                                  ? null
                                  : () => setState(() => _logoPath = ''),
                              icon: const Icon(
                                Icons.delete_outline,
                                size: 17,
                              ),
                              label: const Text('إزالة الشعار'),
                            ),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                ListenableBuilder(
                  listenable: Listenable.merge([
                    _nameCtrl,
                    _taglineCtrl,
                    _headerTextCtrl,
                    _footerCtrl,
                  ]),
                  builder: (context, child) => Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: pal.cardElevated,
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(
                        color: pal.brand.withValues(alpha: 0.3),
                      ),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        Row(
                          children: [
                            CircleAvatar(
                              radius: 16,
                              backgroundImage: logoProvider,
                            ),
                            const SizedBox(width: 10),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    _nameCtrl.text.trim().isEmpty
                                        ? 'اسم الوكالة'
                                        : _nameCtrl.text.trim(),
                                    style: TextStyle(
                                      fontWeight: FontWeight.w800,
                                      color: pal.brand,
                                    ),
                                  ),
                                  if (_taglineCtrl.text.trim().isNotEmpty)
                                    Text(
                                      _taglineCtrl.text.trim(),
                                      style: TextStyle(
                                        fontSize: 11,
                                        color: pal.textSecondary,
                                      ),
                                    ),
                                ],
                              ),
                            ),
                            StatusPill(
                              label: 'معاينة الترويسة',
                              bg: pal.brandSoft,
                              fg: pal.brand,
                            ),
                          ],
                        ),
                        if (_headerEnabled &&
                            _headerTextCtrl.text.trim().isNotEmpty) ...[
                          const Divider(height: 12),
                          Text(
                            _headerTextCtrl.text.trim(),
                            textAlign: TextAlign.center,
                            style: const TextStyle(fontSize: 11.5),
                          ),
                        ],
                        if (_footerEnabled &&
                            _footerCtrl.text.trim().isNotEmpty) ...[
                          const Divider(height: 12),
                          Text(
                            _footerCtrl.text.trim(),
                            textAlign: TextAlign.center,
                            style: TextStyle(
                              fontSize: 11,
                              color: pal.textSecondary,
                            ),
                          ),
                        ],
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 10),
                TextField(
                  controller: _nameCtrl,
                  enabled: canEdit,
                  decoration: const InputDecoration(labelText: 'اسم الوكالة الرسمي'),
                ),
                const SizedBox(height: 8),
                TextField(
                  controller: _displayAppCtrl,
                  enabled: canEdit,
                  decoration: const InputDecoration(
                    labelText: 'الاسم الظاهر في الشريط واللانشر',
                  ),
                ),
                const SizedBox(height: 8),
                TextField(
                  controller: _ownerCtrl,
                  enabled: canEdit,
                  decoration: const InputDecoration(labelText: 'اسم المالك / المدير'),
                ),
                const SizedBox(height: 8),
                TextField(
                  controller: _phoneCtrl,
                  enabled: canEdit,
                  textDirection: TextDirection.ltr,
                  decoration: const InputDecoration(labelText: 'رقم الهاتف'),
                ),
                const SizedBox(height: 8),
                TextField(
                  controller: _extraContactCtrl,
                  enabled: canEdit,
                  textDirection: TextDirection.ltr,
                  decoration: const InputDecoration(
                    labelText: 'رقم إضافي / واتساب / بريد (اختياري)',
                  ),
                ),
                const SizedBox(height: 8),
                TextField(
                  controller: _addrCtrl,
                  enabled: canEdit,
                  decoration: const InputDecoration(labelText: 'العنوان'),
                ),
                const SizedBox(height: 8),
                TextField(
                  controller: _taglineCtrl,
                  enabled: canEdit,
                  decoration: const InputDecoration(labelText: 'الوصف / الشعار النصي'),
                ),
                const SizedBox(height: 8),
                TextField(
                  controller: _commercialCtrl,
                  enabled: canEdit,
                  decoration: const InputDecoration(
                    labelText: 'السجل التجاري / بيانات مرجعية',
                  ),
                ),
                const SizedBox(height: 8),
                TextField(
                  controller: _qatTypesRawCtrl,
                  enabled: canEdit,
                  decoration: const InputDecoration(
                    labelText: 'الأصناف الافتراضية للوكالة (مفصولة بفاصلة)',
                  ),
                ),
                const SizedBox(height: 8),
                SwitchListTile(
                  dense: true,
                  contentPadding: EdgeInsets.zero,
                  title: const Text('تفعيل ترويسة الوكالة في الفواتير'),
                  value: _headerEnabled,
                  onChanged:
                      canEdit ? (v) => setState(() => _headerEnabled = v) : null,
                ),
                if (_headerEnabled) ...[
                  TextField(
                    controller: _headerTextCtrl,
                    enabled: canEdit,
                    decoration: const InputDecoration(
                      labelText: 'نص الترويسة المخصص (اختياري)',
                    ),
                  ),
                  const SizedBox(height: 8),
                ],
                SwitchListTile(
                  dense: true,
                  contentPadding: EdgeInsets.zero,
                  title: const Text('تفعيل تذييل الفواتير'),
                  value: _footerEnabled,
                  onChanged:
                      canEdit ? (v) => setState(() => _footerEnabled = v) : null,
                ),
                if (_footerEnabled)
                  TextField(
                    controller: _footerCtrl,
                    enabled: canEdit,
                    decoration:
                        const InputDecoration(labelText: 'تذييل الفواتير'),
                  ),
                const SizedBox(height: 14),
                FilledButton.icon(
                  onPressed: canEdit ? () => _save(ctrl) : null,
                  icon: const Icon(Icons.save_outlined),
                  label: const Text('حفظ التعديلات'),
                ),
                if (canEdit) ...[
                  const SizedBox(height: 8),
                  OutlinedButton.icon(
                    onPressed: () async {
                      final ok = await showErpConfirmDialog(
                        context,
                        title: 'إعادة تعيين الهوية الافتراضية',
                        message:
                            'سيتم إعادة تعيين الاسم والشعار والألوان الافتراضية للوكالة دون المساس بالدفاتر المالية.',
                        confirmLabel: 'إعادة التعيين',
                        destructive: true,
                      );
                      if (!ok || !mounted) return;
                      await ctrl.resetBrandingToDefaults();
                      if (!mounted) return;
                      final next = ctrl.settings;
                      setState(() {
                        _nameCtrl.text = next.agencyName;
                        _displayAppCtrl.text = next.appDisplayName;
                        _ownerCtrl.text = next.ownerName;
                        _phoneCtrl.text = next.agencyPhone;
                        _extraContactCtrl.text = next.additionalContact;
                        _addrCtrl.text = next.agencyAddress;
                        _taglineCtrl.text = next.tagline;
                        _commercialCtrl.text = next.commercialDetails;
                        _qatTypesRawCtrl.text = next.qatTypesRaw;
                        _headerTextCtrl.text = next.headerText;
                        _footerCtrl.text = next.footerNote;
                        _logoPath = next.logoPath;
                        _paletteId = next.paletteId;
                      });
                    },
                    icon: const Icon(Icons.restart_alt),
                    label: const Text('إعادة تعيين الهوية الافتراضية'),
                  ),
                ],
              ],
            ),
          ),
        ),
      ],
    );
  }
}
