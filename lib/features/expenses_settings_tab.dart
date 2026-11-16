import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:image_picker/image_picker.dart';
import 'package:path_provider/path_provider.dart';
import 'package:provider/provider.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:share_plus/share_plus.dart';

import '../core/date_utils.dart';
import '../core/money.dart';
import '../core/platform_bridge.dart';
import '../core/theme.dart';
import '../data/models.dart';
import '../developer/developer_console_sheet.dart';
import '../services/esc_pos_service.dart';
import '../state/app_controller.dart';
import 'widgets/common_widgets.dart';

class ExpensesSettingsTab extends StatelessWidget {
  const ExpensesSettingsTab({super.key});

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final subTab = ctrl.settingsSubTab.clamp(0, 3);

    return Column(
      children: [
        SegmentedStrip(
          tabs: const [
            'المصاريف والحسابات',
            'الهوية والعملة والمظهر',
            'النسخ والطباعة',
            'الترخيص والأمان',
          ],
          selectedIndex: subTab,
          onChanged: ctrl.setSettingsSubTab,
        ),
        Expanded(
          child: IndexedStack(
            index: subTab,
            children: const [
              _ExpensesAndAccountsView(),
              _BrandingCurrencyThemeView(),
              _BackupAndPrinterView(),
              _LicenseAndSecurityView(),
            ],
          ),
        ),
      ],
    );
  }
}

// -----------------------------------------------------------------------------
// 1. Expenses & Ledger Accounts
// -----------------------------------------------------------------------------

class _ExpensesAndAccountsView extends StatelessWidget {
  const _ExpensesAndAccountsView();

  Future<void> _addExpenseDialog(BuildContext context, AppController ctrl) async {
    final descCtrl = TextEditingController();
    final amountCtrl = TextEditingController();
    final notesCtrl = TextEditingController();
    CurrencyCode cur = ctrl.currencyPolicy.defaultCurrency;
    LedgerAccount? selectedAcc = ctrl.accounts.isEmpty ? null : ctrl.accounts.first;

    await showDialog<void>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setModalState) => AlertDialog(
          title: const Text('تسجيل مصروف يومي'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                CurrencySelectorRow(
                  policy: ctrl.currencyPolicy,
                  selected: cur,
                  onChanged: (c) => setModalState(() => cur = c),
                ),
                const SizedBox(height: 8),
                if (ctrl.accounts.isNotEmpty)
                  DropdownButtonFormField<int>(
                    value: selectedAcc?.id,
                    decoration: const InputDecoration(labelText: 'حساب / بند المصروف'),
                  items: ctrl.accounts
                      .where((a) => a.isActive)
                      .map((a) => DropdownMenuItem<int>(value: a.id, child: Text(a.name)))
                      .toList(),
                    onChanged: (v) {
                      setModalState(() {
                        selectedAcc = ctrl.accounts.firstWhere((e) => e.id == v);
                      });
                    },
                  ),
                const SizedBox(height: 8),
                TextField(controller: descCtrl, decoration: const InputDecoration(labelText: 'البيان / الوصف *')),
                const SizedBox(height: 8),
                AccountingNumberField(
                  controller: amountCtrl,
                  label: 'المبلغ (${cur.shortSymbol}) *',
                ),
                const SizedBox(height: 8),
                TextField(controller: notesCtrl, decoration: const InputDecoration(labelText: 'ملاحظات')),
              ],
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('إلغاء')),
            FilledButton(
              onPressed: () async {
                final minor = Money.parseMinorUnits(amountCtrl.text);
                if (descCtrl.text.trim().isEmpty || minor <= 0) return;
                await ctrl.repo.addExpense(
                  description: descCtrl.text,
                  category: selectedAcc?.name ?? 'مصروفات عامة',
                  accountId: selectedAcc?.id,
                  amountMinor: minor,
                  currency: cur,
                  notes: notesCtrl.text,
                );
                await ctrl.reloadAll();
                if (ctx.mounted) Navigator.pop(ctx);
              },
              child: const Text('حفظ'),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _addAccountDialog(BuildContext context, AppController ctrl) async {
    final nameCtrl = TextEditingController();
    final catCtrl = TextEditingController(text: 'مصروفات تشغيلية');
    await showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('إضافة حساب / تصنيف'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(controller: nameCtrl, decoration: const InputDecoration(labelText: 'اسم الحساب *')),
            const SizedBox(height: 8),
            TextField(controller: catCtrl, decoration: const InputDecoration(labelText: 'التصنيف')),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('إلغاء')),
          FilledButton(
            onPressed: () async {
              if (nameCtrl.text.trim().isEmpty) return;
              await ctrl.repo.upsertLedgerAccount(
                code: '',
                name: nameCtrl.text,
                category: catCtrl.text,
              );
              await ctrl.reloadAll();
              if (ctx.mounted) Navigator.pop(ctx);
            },
            child: const Text('إضافة'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final pal = context.pal;

    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _addExpenseDialog(context, ctrl),
        icon: const Icon(Icons.add),
        label: const Text('مصروف جديد'),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 84),
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('دليل الحسابات والبنود (${ctrl.accounts.length})',
                  style: Theme.of(context).textTheme.titleMedium),
              TextButton.icon(
                onPressed: () => _addAccountDialog(context, ctrl),
                icon: const Icon(Icons.add, size: 16),
                label: const Text('بند جديد'),
              ),
            ],
          ),
          Wrap(
            spacing: 6,
            runSpacing: 6,
            children: ctrl.accounts
                .map((a) => Chip(
                      label: Text(a.name, style: const TextStyle(fontSize: 11.5)),
                    ))
                .toList(),
          ),
          const Divider(height: 24),
          Text('سجل المصروفات', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          if (ctrl.expenses.isEmpty)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 28),
              child: EmptyLedgerState(
                icon: Icons.receipt_long_outlined,
                title: 'لا توجد مصروفات مسجلة',
                subtitle: 'سجل مصروفات النقل والإيجار والأكياس مفصولة حسب العملة',
              ),
            )
          else
            for (final e in ctrl.expenses)
              Card(
                margin: const EdgeInsets.only(bottom: 8),
                child: ListTile(
                  title: Row(
                    children: [
                      Expanded(
                        child: Text(e.description,
                            style: const TextStyle(fontWeight: FontWeight.w700)),
                      ),
                      CurrencyBadge(currency: e.currency),
                    ],
                  ),
                  subtitle: Text(
                    '${Money(e.amountMinor, e.currency).format()} • ${e.category} • ${e.dateIso}',
                  ),
                  trailing: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      IconButton(
                        tooltip: 'أرشفة',
                        onPressed: () async {
                          await ctrl.repo.setArchived('expenses', e.id, true);
                          await ctrl.reloadAll();
                        },
                        icon: const Icon(Icons.archive_outlined, size: 18),
                      ),
                      IconButton(
                        onPressed: () async {
                          await ctrl.repo.deleteExpense(e.id);
                          await ctrl.reloadAll();
                        },
                        icon: Icon(Icons.delete_outline, color: pal.danger, size: 18),
                      ),
                    ],
                  ),
                ),
              ),
        ],
      ),
    );
  }
}

// -----------------------------------------------------------------------------
// 2. Branding, Currency Mode & Theme
// -----------------------------------------------------------------------------

class _BrandingCurrencyThemeView extends StatefulWidget {
  const _BrandingCurrencyThemeView();

  @override
  State<_BrandingCurrencyThemeView> createState() => _BrandingCurrencyThemeViewState();
}

class _BrandingCurrencyThemeViewState extends State<_BrandingCurrencyThemeView> {
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
      text: s.displaySarToYerMilli > 0 ? QuantityMath.formatMilli(s.displaySarToYerMilli) : '',
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
        agencyName: _nameCtrl.text.trim().isEmpty ? prev.agencyName : _nameCtrl.text.trim(),
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

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text('مظهر التطبيق', style: Theme.of(context).textTheme.titleMedium),
                const SizedBox(height: 10),
                SegmentedButton<AppThemeModeChoice>(
                  segments: const [
                    ButtonSegment(
                      value: AppThemeModeChoice.lightEnterprise,
                      label: Text('المظهر الفاتح المؤسسي'),
                      icon: Icon(Icons.light_mode_outlined),
                    ),
                    ButtonSegment(
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
                Text('إدارة العملة المركزية', style: Theme.of(context).textTheme.titleMedium),
                const SizedBox(height: 6),
                for (final m in CurrencyMode.values)
                  RadioListTile<CurrencyMode>(
                    value: m,
                    groupValue: _mode,
                    title: Text(m.arabicLabel),
                    onChanged: (v) => setState(() => _mode = v ?? CurrencyMode.yerOnly),
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
                Text('بيانات وهوية الوكالة', style: Theme.of(context).textTheme.titleMedium),
                const SizedBox(height: 10),
                Row(
                  children: [
                    CircleAvatar(
                      radius: 26,
                      backgroundImage: (_logoPath.isNotEmpty && File(_logoPath).existsSync())
                          ? FileImage(File(_logoPath))
                          : const AssetImage('assets/images/app_logo.png') as ImageProvider,
                    ),
                    const SizedBox(width: 12),
                    OutlinedButton.icon(
                      onPressed: () async {
                        final picked =
                            await ImagePicker().pickImage(source: ImageSource.gallery);
                        if (picked != null) {
                          setState(() => _logoPath = picked.path);
                        }
                      },
                      icon: const Icon(Icons.image_outlined, size: 18),
                      label: const Text('تغيير شعار الوكالة'),
                    ),
                  ],
                ),
                const SizedBox(height: 10),
                TextField(controller: _nameCtrl, decoration: const InputDecoration(labelText: 'اسم الوكالة')),
                const SizedBox(height: 8),
                TextField(
                  controller: _phoneCtrl,
                  textDirection: TextDirection.ltr,
                  decoration: const InputDecoration(labelText: 'رقم الهاتف'),
                ),
                const SizedBox(height: 8),
                TextField(controller: _addrCtrl, decoration: const InputDecoration(labelText: 'العنوان')),
                const SizedBox(height: 8),
                TextField(controller: _footerCtrl, decoration: const InputDecoration(labelText: 'تذييل الفواتير')),
                const SizedBox(height: 14),
                FilledButton.icon(
                  onPressed: () => _save(ctrl),
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

// -----------------------------------------------------------------------------
// 3. Atomic Backup/Restore & Bluetooth Thermal Printer
// -----------------------------------------------------------------------------

class _BackupAndPrinterView extends StatefulWidget {
  const _BackupAndPrinterView();

  @override
  State<_BackupAndPrinterView> createState() => _BackupAndPrinterViewState();
}

class _BackupAndPrinterViewState extends State<_BackupAndPrinterView> {
  List<BondedPrinter> _printers = [];
  bool _busy = false;
  String? _lastBackupInfo;

  Future<void> _createBackup(AppController ctrl) async {
    setState(() => _busy = true);
    try {
      final docs = await getApplicationDocumentsDirectory();
      final res = await ctrl.backupService.createAtomicBackup(
        outputDir: '${docs.path}/qat_ledger_backups',
        appEdition: ctrl.editionConfig.edition,
        agencyLogoPath: ctrl.settings.logoPath,
      );
      if (!mounted) return;
      setState(() {
        _lastBackupInfo =
            'تم إنشاء النسخة (${(res.sizeBytes / 1024).toStringAsFixed(1)} KB)\nSHA-256: ${res.dbSha256.substring(0, 16)}...';
      });
      await Share.shareXFiles([XFile(res.filePath)], text: 'نسخة احتياطية ميزان القات (.qbak)');
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.toString())));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _restoreBackup(AppController ctrl) async {
    final picked = await FilePicker.platform.pickFiles(type: FileType.any);
    final path = picked?.files.single.path;
    if (path == null) return;

    setState(() => _busy = true);
    try {
      final res = await ctrl.backupService.restoreFromFile(path);
      await ctrl.reloadAll();
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(res.wasLegacyRoomDb
              ? 'تم ترحيل قاعدة Room القديمة بنجاح والتحقق من القيود!'
              : 'تمت الاستعادة بنجاح مع التحقق من SHA-256!'),
        ),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.toString())));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<AppController>();
    final pal = context.pal;

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text('النسخ الاحتياطي الذري والاستعادة (.qbak / Room .db)',
                    style: Theme.of(context).textTheme.titleMedium),
                const SizedBox(height: 6),
                Text(
                  'يتحقق النظام تلقائياً من بصمة SHA-256 وسلامة SQLite وينشئ نقطة رجوع قبل أي استعادة.',
                  style: TextStyle(fontSize: 12, color: pal.textSecondary),
                ),
                const SizedBox(height: 12),
                FilledButton.icon(
                  onPressed: _busy ? null : () => _createBackup(ctrl),
                  icon: const Icon(Icons.backup_outlined),
                  label: const Text('إنشاء ومشاركة نسخة احتياطية (.qbak)'),
                ),
                const SizedBox(height: 8),
                OutlinedButton.icon(
                  onPressed: _busy ? null : () => _restoreBackup(ctrl),
                  icon: const Icon(Icons.restore_page_outlined),
                  label: const Text('استعادة نسخة (.qbak) أو استيراد Room (.db)'),
                ),
                if (_lastBackupInfo != null) ...[
                  const SizedBox(height: 10),
                  Text(_lastBackupInfo!, style: TextStyle(fontSize: 12, color: pal.success)),
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
                Text('الطابعة الحرارية (Bluetooth ESC/POS)',
                    style: Theme.of(context).textTheme.titleMedium),
                const SizedBox(height: 6),
                Text(
                  ctrl.settings.savedPrinterName.isEmpty
                      ? 'لا توجد طابعة محفوظة'
                      : 'الطابعة المحفوظة: ${ctrl.settings.savedPrinterName} (${ctrl.settings.savedPrinterAddress})',
                  style: TextStyle(fontSize: 12.5, color: pal.textSecondary),
                ),
                const SizedBox(height: 10),
                SegmentedButton<int>(
                  segments: const [
                    ButtonSegment<int>(value: 58, label: Text('عرض 58 مم')),
                    ButtonSegment<int>(value: 80, label: Text('عرض 80 مم')),
                  ],
                  selected: {ctrl.settings.printerPaperMm == 58 ? 58 : 80},
                  onSelectionChanged: (s) async {
                    final prev = ctrl.settings;
                    await ctrl.updateSettings(
                      AgencySettings(
                        agencyName: prev.agencyName,
                        agencyPhone: prev.agencyPhone,
                        agencyAddress: prev.agencyAddress,
                        footerNote: prev.footerNote,
                        logoPath: prev.logoPath,
                        currencyMode: prev.currencyMode,
                        displaySarToYerMilli: prev.displaySarToYerMilli,
                        themeChoice: prev.themeChoice,
                        onboardingComplete: prev.onboardingComplete,
                        savedPrinterName: prev.savedPrinterName,
                        savedPrinterAddress: prev.savedPrinterAddress,
                        printerPaperMm: s.first,
                      ),
                    );
                  },
                ),
                const SizedBox(height: 10),
                OutlinedButton.icon(
                  onPressed: () async {
                    final list = await PlatformBridge.instance.getBondedPrinters();
                    if (!mounted) return;
                    setState(() => _printers = list);
                  },
                  icon: const Icon(Icons.bluetooth_searching),
                  label: const Text('جلب الطابعات المقترنة'),
                ),
                if (ctrl.settings.savedPrinterAddress.isNotEmpty) ...[
                  const SizedBox(height: 8),
                  FilledButton.tonalIcon(
                    onPressed: () async {
                      final nowMs = DateTime.now().millisecondsSinceEpoch;
                      final testSale = Sale(
                        id: 0,
                        invoiceNumber: 'TEST-ESC-POS',
                        customerId: null,
                        customerName: 'عميل تجريبي',
                        dateMs: nowMs,
                        dateIso: QatDateUtils.todayIso(),
                        currency: ctrl.currencyPolicy.defaultCurrency,
                        subtotalMinor: 500000,
                        discountMinor: 0,
                        taxBps: 0,
                        taxAmountMinor: 0,
                        totalAmountMinor: 500000,
                        paidAmountMinor: 500000,
                        remainingAmountMinor: 0,
                        paymentType: PaymentType.paid,
                        status: PaymentType.paid.arabicLabel,
                        notes: 'اختبار طباعة حرارية ${ctrl.settings.printerPaperMm}mm',
                        isArchived: false,
                        items: [
                          SaleItem(
                            id: 0,
                            saleId: 0,
                            inventoryBatchId: null,
                            qatTypeName: 'صنف اختباري',
                            qtyMilli: 1000,
                            unit: 'ربطة',
                            unitPriceMinor: 500000,
                            buyPriceMinor: 350000,
                            lineTotalMinor: 500000,
                            currency: ctrl.currencyPolicy.defaultCurrency,
                          ),
                        ],
                      );
                      try {
                        await EscPosService.instance.printSaleReceipt(
                          agency: ctrl.settings,
                          sale: testSale,
                          printerAddress: ctrl.settings.savedPrinterAddress,
                          paperMm: ctrl.settings.printerPaperMm,
                        );
                        if (!context.mounted) return;
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(content: Text('تم إرسال إيصال الاختبار للطابعة بنجاح')),
                        );
                      } catch (e) {
                        if (!context.mounted) return;
                        ScaffoldMessenger.of(context)
                            .showSnackBar(SnackBar(content: Text(e.toString())));
                      }
                    },
                    icon: const Icon(Icons.print_outlined),
                    label: Text('اختبار طباعة (${ctrl.settings.printerPaperMm} مم)'),
                  ),
                ],
                for (final p in _printers)
                  ListTile(
                    dense: true,
                    leading: const Icon(Icons.print_outlined),
                    title: Text(p.name),
                    subtitle: Text(p.address),
                    trailing: TextButton(
                      onPressed: () async {
                        final prev = ctrl.settings;
                        await ctrl.updateSettings(
                          AgencySettings(
                            agencyName: prev.agencyName,
                            agencyPhone: prev.agencyPhone,
                            agencyAddress: prev.agencyAddress,
                            footerNote: prev.footerNote,
                            logoPath: prev.logoPath,
                            currencyMode: prev.currencyMode,
                            displaySarToYerMilli: prev.displaySarToYerMilli,
                            themeChoice: prev.themeChoice,
                            onboardingComplete: prev.onboardingComplete,
                            savedPrinterName: p.name,
                            savedPrinterAddress: p.address,
                            printerPaperMm: prev.printerPaperMm,
                          ),
                        );
                      },
                      child: const Text('اعتماد'),
                    ),
                  ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

// -----------------------------------------------------------------------------
// 4. Offline Licensing, Credentials & Developer Gate
// -----------------------------------------------------------------------------

class _LicenseAndSecurityView extends StatefulWidget {
  const _LicenseAndSecurityView();

  @override
  State<_LicenseAndSecurityView> createState() => _LicenseAndSecurityViewState();
}

class _LicenseAndSecurityViewState extends State<_LicenseAndSecurityView> {
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
                  decoration: const InputDecoration(labelText: 'كلمة المرور (4+ أحرف) *'),
                ),
                const SizedBox(height: 8),
                SegmentedButton<UserRole>(
                  segments: const [
                    ButtonSegment<UserRole>(value: UserRole.cashier, label: Text('بائع / محاسب')),
                    ButtonSegment<UserRole>(value: UserRole.admin, label: Text('مدير')),
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
            TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('إلغاء')),
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
                  ScaffoldMessenger.of(ctx).showSnackBar(SnackBar(content: Text(e.toString())));
                }
              },
              child: const Text('حفظ المستخدم'),
            ),
          ],
        ),
      ),
    );
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
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('إلغاء')),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, ctrl.verifyAdminGatePasscode(passCtrl.text)),
            child: const Text('دخول'),
          ),
        ],
      ),
    );
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
                      : (lic.reason.isEmpty ? lic.kind.arabicDescription : lic.reason),
                  style: TextStyle(fontSize: 12.5, color: pal.textSecondary),
                ),
                const Divider(height: 20),
                const Text('معرف تثبيت الجهاز (Installation ID):',
                    style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600)),
                const SizedBox(height: 6),
                Row(
                  children: [
                    Expanded(
                      child: SelectableText(
                        ctrl.installationId,
                        textDirection: TextDirection.ltr,
                        style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 13),
                      ),
                    ),
                    IconButton(
                      onPressed: () {
                        Clipboard.setData(ClipboardData(text: ctrl.installationId));
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(content: Text('تم نسخ معرف الجهاز')),
                        );
                      },
                      icon: const Icon(Icons.copy, size: 18),
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
                        final res = await FilePicker.platform.pickFiles(type: FileType.any);
                        final path = res?.files.single.path;
                        if (path == null) return;
                        final content = await File(path).readAsString();
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
                Text('تغيير بيانات الدخول', style: Theme.of(context).textTheme.titleMedium),
                const SizedBox(height: 10),
                TextField(
                  controller: _newUserCtrl,
                  textDirection: TextDirection.ltr,
                  decoration: const InputDecoration(labelText: 'اسم المستخدم الجديد'),
                ),
                const SizedBox(height: 8),
                TextField(
                  controller: _newPassCtrl,
                  obscureText: true,
                  textDirection: TextDirection.ltr,
                  decoration: const InputDecoration(labelText: 'كلمة المرور الجديدة'),
                ),
                const SizedBox(height: 10),
                Row(
                  children: [
                    Expanded(
                      child: FilledButton.tonal(
                        onPressed: () async {
                          try {
                            await ctrl.updateCredentials(
                              newUsername: _newUserCtrl.text,
                              newPassword: _newPassCtrl.text,
                            );
                            _newPassCtrl.clear();
                            if (!context.mounted) return;
                            ScaffoldMessenger.of(context).showSnackBar(
                              const SnackBar(content: Text('تم تحديث كلمة المرور بنجاح')),
                            );
                          } catch (e) {
                            if (!context.mounted) return;
                            ScaffoldMessenger.of(context)
                                .showSnackBar(SnackBar(content: Text(e.toString())));
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
                    Text('المستخدمون والصلاحيات (${ctrl.users.length})',
                        style: Theme.of(context).textTheme.titleMedium),
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
                      u.role == UserRole.admin ? Icons.admin_panel_settings : Icons.person_outline,
                    ),
                    title: Text('${u.displayName} (${u.username})'),
                    subtitle: Text(
                      '${u.role.arabicLabel}'
                      '${u.canDelete ? ' • حذف' : ''}'
                      '${u.canViewReports ? ' • تقارير' : ''}'
                      '${u.canManageSettings ? ' • إعدادات' : ''}',
                    ),
                    trailing: (ctrl.users.length > 1 &&
                            u.username != ctrl.currentUser?.username &&
                            ctrl.canManageSettingsCurrent)
                        ? IconButton(
                            onPressed: () => ctrl.deleteUser(u.username),
                            icon: Icon(Icons.delete_outline, color: pal.danger, size: 18),
                          )
                        : null,
                  ),
              ],
            ),
          ),
        ),
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
