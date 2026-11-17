import 'dart:io';

import 'package:image_picker/image_picker.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../core/money.dart';
import '../../core/theme.dart';
import '../../data/models.dart';
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
  late TextEditingController _phoneCtrl;
  late TextEditingController _addrCtrl;
  late TextEditingController _footerCtrl;
  late TextEditingController _rateCtrl;
  late CurrencyMode _mode;
  String _logoPath = '';

  @override
  void initState() {
    super.initState();
    final s = context.read<AppController>().settings;
    _nameCtrl = TextEditingController(text: s.agencyName);
    _phoneCtrl = TextEditingController(text: s.agencyPhone);
    _addrCtrl = TextEditingController(text: s.agencyAddress);
    _footerCtrl = TextEditingController(text: s.footerNote);
    _rateCtrl = TextEditingController(
      text: s.displaySarToYerMilli > 0
          ? QuantityMath.formatMilli(s.displaySarToYerMilli)
          : '',
    );
    _mode = s.currencyMode;
    _logoPath = s.logoPath;
  }

  @override
  void dispose() {
    _nameCtrl.dispose();
    _phoneCtrl.dispose();
    _addrCtrl.dispose();
    _footerCtrl.dispose();
    _rateCtrl.dispose();
    super.dispose();
  }

  Future<void> _save(AppController ctrl) async {
    final prev = ctrl.settings;
    final rateMilli = _mode == CurrencyMode.dualCurrency
        ? QuantityMath.parseMilli(_rateCtrl.text)
        : 0;
    await ctrl.updateSettings(
      AgencySettings(
        agencyName: _nameCtrl.text.trim().isEmpty
            ? prev.agencyName
            : _nameCtrl.text.trim(),
        agencyPhone: _phoneCtrl.text.trim(),
        agencyAddress: _addrCtrl.text.trim(),
        footerNote: _footerCtrl.text.trim(),
        logoPath: _logoPath,
        currencyMode: _mode,
        displaySarToYerMilli: rateMilli,
        themeChoice: prev.themeChoice,
        onboardingComplete: true,
        savedPrinterName: prev.savedPrinterName,
        savedPrinterAddress: prev.savedPrinterAddress,
        printerPaperMm: prev.printerPaperMm,
      ),
    );
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('تم حفظ الإعدادات بنجاح')),
    );
  }

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
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
                  'مظهر التطبيق',
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
                  'بيانات وهوية الوكالة',
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
                      child: OutlinedButton.icon(
                        onPressed: !canEdit
                            ? null
                            : () async {
                                final picked = await ImagePicker().pickImage(
                                  source: ImageSource.gallery,
                                );
                                if (!mounted || picked == null) return;
                                setState(() => _logoPath = picked.path);
                              },
                        icon: const Icon(Icons.image_outlined, size: 18),
                        label: const Text('تغيير شعار الوكالة'),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 10),
                TextField(
                  controller: _nameCtrl,
                  enabled: canEdit,
                  decoration: const InputDecoration(labelText: 'اسم الوكالة'),
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
                  controller: _addrCtrl,
                  enabled: canEdit,
                  decoration: const InputDecoration(labelText: 'العنوان'),
                ),
                const SizedBox(height: 8),
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
              ],
            ),
          ),
        ),
      ],
    );
  }
}
