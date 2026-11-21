import 'dart:async';
import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:material_ui/material_ui.dart';
import 'package:path_provider/path_provider.dart';
import 'package:provider/provider.dart';
import 'package:share_plus/share_plus.dart';

import '../../core/date_utils.dart';
import '../../core/platform_bridge.dart';
import '../../core/theme.dart';
import '../../data/backup_service.dart';
import '../../data/models.dart';
import '../../services/esc_pos_service.dart';
import '../../state/app_controller.dart';
import '../widgets/erp_components.dart';

class BackupAndPrinterView extends StatefulWidget {
  const BackupAndPrinterView({super.key});

  @override
  State<BackupAndPrinterView> createState() => _BackupAndPrinterViewState();
}

class _BackupAndPrinterViewState extends State<BackupAndPrinterView> {
  List<BondedPrinter> _printers = [];
  List<File> _localBackups = [];
  bool _searchedPrinters = false;
  bool _hideNonPrinters = true;
  String? _btStatusMessage;
  String? _lastPrintResultNote;
  bool _busy = false;
  String? _lastBackupInfo;

  @override
  void initState() {
    super.initState();
    unawaited(_refreshLocalBackups());
  }

  Future<void> _refreshLocalBackups() async {
    try {
      final docs = await getApplicationDocumentsDirectory();
      final dir = Directory('${docs.path}/qat_ledger_backups');
      if (!dir.existsSync()) return;
      final files = dir
          .listSync()
          .whereType<File>()
          .where(
            (f) => f.path.endsWith('.qatbackup') || f.path.endsWith('.qbak'),
          )
          .toList()
        ..sort(
          (a, b) => b.statSync().modified.compareTo(a.statSync().modified),
        );
      if (mounted) setState(() => _localBackups = files);
    } catch (_) {}
  }

  Future<void> _createBackup(
    AppController ctrl, {
    bool saveToDevice = false,
  }) async {
    setState(() => _busy = true);
    try {
      final docs = await getApplicationDocumentsDirectory();
      final pkg = await ctrl.backupService.buildBackupPackageBytes(
        stagingDir: '${docs.path}/qat_ledger_backups',
        appEdition: ctrl.editionConfig.edition,
        agencyLogoPath: ctrl.settings.logoPath,
      );
      await _refreshLocalBackups();
      if (!mounted) return;
      setState(() {
        _lastBackupInfo =
            '${pkg.metadata.fileName} (${(pkg.metadata.sizeBytes / 1024).toStringAsFixed(1)} KB)\nSHA-256: ${pkg.metadata.dbSha256.substring(0, 16)}...';
      });
      if (saveToDevice) {
        final savedPath = await FilePicker.platform.saveFile(
          dialogTitle: 'حفظ النسخة الاحتياطية (.qatbackup)',
          fileName: pkg.metadata.fileName,
          bytes: pkg.bytes,
        );
        if (!mounted) return;
        if (savedPath != null && savedPath.isNotEmpty) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(
                'تم حفظ النسخة الاحتياطية بنجاح: ${pkg.metadata.fileName}',
              ),
            ),
          );
        }
      } else {
        await Share.shareXFiles(
          [XFile(pkg.metadata.filePath)],
          text: 'نسخة احتياطية ميزان القات (.qatbackup)',
        );
      }
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(e.toString())),
      );
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _restoreFromSpecificPath(
    AppController ctrl,
    String path,
  ) async {
    setState(() => _busy = true);
    final BackupInspectionResult info;
    try {
      info = await ctrl.backupService.inspectBackupFile(path);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
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

    final shortSha = info.verifiedSha256.length >= 16
        ? '${info.verifiedSha256.substring(0, 16)}...'
        : info.verifiedSha256;
    final confirm = await showErpConfirmDialog(
      context,
      title: 'تأكيد استعادة البيانات',
      message:
          'الملف: ${info.fileName}\n'
          'المحتوى: ${info.summaryLine}\n'
          'SHA-256: $shortSha\n\n'
          'سيتم إنشاء نقطة رجوع تلقائية ثم استبدال الدفاتر الحالية. هل تريد الاستعادة الآن؟',
      confirmLabel: 'استعادة الآن',
      destructive: true,
    );
    if (!confirm || !mounted) return;

    setState(() => _busy = true);
    try {
      final res = await ctrl.backupService.restoreFromFile(path);
      await ctrl.handleDatabaseRestored();
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            res.wasLegacyRoomDb
                ? 'تم ترحيل قاعدة Room القديمة بنجاح والتحقق من القيود!'
                : 'تمت الاستعادة بنجاح مع التحقق من SHA-256 وسلامة القاعدة!',
          ),
        ),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(e.toString())),
      );
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _restoreBackup(AppController ctrl) async {
    final picked = await FilePicker.platform.pickFiles(type: FileType.any);
    final path = picked?.files.single.path;
    if (path == null || !mounted) return;
    await _restoreFromSpecificPath(ctrl, path);
  }

  Future<void> _confirmAndResetSystem(AppController ctrl) async {
    final passCtrl = TextEditingController();
    bool fullFactory = false;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDlgState) => ErpDialogControllerScope(
          controllers: [passCtrl],
          child: AlertDialog(
            title: const Text('تهيئة ومسح شامل للنظام'),
            content: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const Text(
                    'تنبيه حرج: سيتم حذف جميع المبيعات، المشتريات، العملاء، الموردين، المخزون، المصروفات، الضرائب، والحوالات بشكل نهائي.\n'
                    'سيقوم النظام تلقائياً بحفظ نسخة احتياطية آمنة (.qatbackup) قبل المسح.',
                    style: TextStyle(fontSize: 12.5, height: 1.5),
                  ),
                  const SizedBox(height: 10),
                  RadioListTile<bool>(
                    dense: true,
                    contentPadding: EdgeInsets.zero,
                    value: false,
                    groupValue: fullFactory,
                    onChanged: (v) => setDlgState(() => fullFactory = v ?? false),
                    title: const Text('تصفير الدفاتر والحسابات فقط'),
                    subtitle: const Text(
                      'الإبقاء على اسم الوكالة والشعار وحسابات الدخول',
                      style: TextStyle(fontSize: 11),
                    ),
                  ),
                  RadioListTile<bool>(
                    dense: true,
                    contentPadding: EdgeInsets.zero,
                    value: true,
                    groupValue: fullFactory,
                    onChanged: (v) => setDlgState(() => fullFactory = v ?? true),
                    title: const Text('إعادة ضبط المصنع بالكامل'),
                    subtitle: const Text(
                      'حذف كل الدفاتر والإعدادات والمستخدمين والبدء من شاشة التأسيس',
                      style: TextStyle(fontSize: 11),
                    ),
                  ),
                  const SizedBox(height: 8),
                  TextField(
                    controller: passCtrl,
                    obscureText: true,
                    textDirection: TextDirection.ltr,
                    decoration: const InputDecoration(
                      labelText: 'أدخل كلمة المرور الحالية للتأكيد',
                      prefixIcon: Icon(Icons.lock_outline),
                    ),
                  ),
                ],
              ),
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(ctx, false),
                child: const Text('إلغاء'),
              ),
              FilledButton(
                style: FilledButton.styleFrom(
                  backgroundColor: context.pal.danger,
                ),
                onPressed: () => Navigator.pop(ctx, true),
                child: const Text('تنفيذ المسح الشامل'),
              ),
            ],
          ),
        ),
      ),
    );
    final password = passCtrl.text;
    passCtrl.dispose();
    if (confirmed != true || !mounted) return;

    setState(() => _busy = true);
    try {
      final docs = await getApplicationDocumentsDirectory();
      final rollback = await ctrl.resetSystemLedgerData(
        passwordConfirmation: password,
        backupDir: '${docs.path}/qat_ledger_backups',
        fullFactoryReset: fullFactory,
      );
      await _refreshLocalBackups();
      if (!mounted) return;
      setState(() {
        _lastBackupInfo =
            'نسخة ما قبل التهيئة: ${rollback.fileName} (${(rollback.sizeBytes / 1024).toStringAsFixed(1)} KB)';
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            fullFactory
                ? 'تمت إعادة ضبط النظام بالكامل مع حفظ نسخة أمان (${rollback.fileName}).'
                : 'تم مسح وتصفير جميع الدفاتر بنجاح مع حفظ نسخة أمان (${rollback.fileName}).',
          ),
        ),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(e.toString())),
      );
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
                Text(
                  'النسخ الاحتياطي الذري والاستعادة (.qatbackup / .qbak / Room .db)',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                const SizedBox(height: 6),
                Text(
                  'يتحقق النظام تلقائياً من بصمة SHA-256 وسلامة SQLite والقيود المرجعية، وينشئ نقطة رجوع قبل أي استعادة.',
                  style: TextStyle(fontSize: 12, color: pal.textSecondary),
                ),
                if (_busy) ...[
                  const SizedBox(height: 10),
                  const LinearProgressIndicator(),
                ],
                const SizedBox(height: 12),
                FilledButton.icon(
                  onPressed: _busy
                      ? null
                      : () => _createBackup(ctrl, saveToDevice: true),
                  icon: const Icon(Icons.save_alt_outlined),
                  label: const Text('حفظ نسخة احتياطية في الهاتف (.qatbackup)'),
                ),
                const SizedBox(height: 8),
                FilledButton.tonalIcon(
                  onPressed: _busy
                      ? null
                      : () => _createBackup(ctrl, saveToDevice: false),
                  icon: const Icon(Icons.backup_outlined),
                  label: const Text('إنشاء ومشاركة نسخة احتياطية (.qatbackup)'),
                ),
                const SizedBox(height: 8),
                OutlinedButton.icon(
                  onPressed: _busy ? null : () => _restoreBackup(ctrl),
                  icon: const Icon(Icons.restore_page_outlined),
                  label: const Text('فحص واستعادة نسخة (.qatbackup / .qbak / .db)'),
                ),
                if (_lastBackupInfo != null) ...[
                  const SizedBox(height: 10),
                  ErpStatusBanner(
                    tone: ErpBannerTone.success,
                    title: 'آخر نسخة احتياطية محلية',
                    subtitle: _lastBackupInfo!,
                  ),
                ],
                if (_localBackups.isNotEmpty) ...[
                  const Divider(height: 20),
                  Text(
                    'النسخ المحفوظة محلياً (${_localBackups.length}):',
                    style: const TextStyle(
                      fontSize: 12.5,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: 6),
                  for (final f in _localBackups.take(5))
                    ListTile(
                      dense: true,
                      contentPadding: EdgeInsets.zero,
                      leading: Icon(Icons.inventory_2_outlined, color: pal.brand),
                      title: Text(
                        f.path.split('/').last,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        textDirection: TextDirection.ltr,
                        style: const TextStyle(fontSize: 12),
                      ),
                      subtitle: Text(
                        '${(f.lengthSync() / 1024).toStringAsFixed(1)} KB',
                        style: const TextStyle(fontSize: 11),
                      ),
                      trailing: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          IconButton(
                            tooltip: 'مشاركة',
                            icon: const Icon(Icons.share_outlined, size: 18),
                            onPressed: () => Share.shareXFiles(
                              [XFile(f.path)],
                              text: 'نسخة احتياطية ميزان القات',
                            ),
                          ),
                          TextButton(
                            onPressed: _busy
                                ? null
                                : () => _restoreFromSpecificPath(ctrl, f.path),
                            child: const Text('استعادة'),
                          ),
                        ],
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
                  'الطابعة الحرارية (Bluetooth ESC/POS)',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
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
                      prev.copyWith(printerPaperMm: s.first),
                    );
                  },
                ),
                const SizedBox(height: 10),
                OutlinedButton.icon(
                  onPressed: () async {
                    final res = await PlatformBridge.instance
                        .getBondedPrintersWithStatus();
                    if (!mounted) return;
                    setState(() {
                      _printers = res.printers;
                      _btStatusMessage = res.statusMessage;
                      _searchedPrinters = true;
                    });
                  },
                  icon: const Icon(Icons.bluetooth_searching),
                  label: const Text('فحص وجلب الطابعات المقترنة'),
                ),
                SwitchListTile(
                  dense: true,
                  contentPadding: EdgeInsets.zero,
                  title: const Text(
                    'تصفية الطابعات الحرارية فقط',
                    style: TextStyle(fontSize: 12.5),
                  ),
                  subtitle: const Text(
                    'إخفاء سماعات البلوتوث والساعات والهواتف المقترنة',
                    style: TextStyle(fontSize: 11),
                  ),
                  value: _hideNonPrinters,
                  onChanged: (v) => setState(() => _hideNonPrinters = v),
                ),
                if (_searchedPrinters && _btStatusMessage != null) ...[
                  const SizedBox(height: 6),
                  ErpStatusBanner(
                    tone: _printers.any((p) => p.isLikelyPrinter)
                        ? ErpBannerTone.success
                        : ErpBannerTone.warning,
                    title: _printers.any((p) => p.isLikelyPrinter)
                        ? 'نتيجة فحص البلوتوث'
                        : 'تنبيه اكتشاف الطابعات',
                    subtitle: _btStatusMessage!,
                  ),
                ],
                if (_lastPrintResultNote != null) ...[
                  const SizedBox(height: 8),
                  ErpStatusBanner(
                    tone: ErpBannerTone.info,
                    title: 'حالة آخر عملية إرسال للطابعة',
                    subtitle: _lastPrintResultNote!,
                  ),
                ],
                if (ctrl.settings.savedPrinterAddress.isNotEmpty) ...[
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      Expanded(
                        child: FilledButton.tonalIcon(
                          onPressed: () async {
                            final messenger = ScaffoldMessenger.of(context);
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
                              notes:
                                  'اختبار طباعة حرارية ${ctrl.settings.printerPaperMm}mm',
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
                              if (!mounted) return;
                              setState(() {
                                _lastPrintResultNote =
                                    'تم إرسال بيانات ESC/POS (${ctrl.settings.printerPaperMm} مم) عبر منفذ SPP للطابعة ${ctrl.settings.savedPrinterName}. إذا لم يخرج ورق تحقق من وجود بكرة ورق وشحن الطابعة.';
                              });
                              messenger.showSnackBar(
                                const SnackBar(
                                  content: Text(
                                    'تم إرسال حزمة الاختبار للطابعة بنجاح',
                                  ),
                                ),
                              );
                            } catch (e) {
                              if (!mounted) return;
                              setState(() {
                                _lastPrintResultNote = 'تعذر الإرسال للطابعة: $e';
                              });
                              messenger.showSnackBar(
                                SnackBar(content: Text(e.toString())),
                              );
                            }
                          },
                          icon: const Icon(Icons.print_outlined),
                          label: Text(
                            'اختبار طباعة (${ctrl.settings.printerPaperMm} مم)',
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      IconButton(
                        tooltip: 'إزالة الطابعة المحفوظة',
                        onPressed: () async {
                          final prev = ctrl.settings;
                          await ctrl.updateSettings(
                            prev.copyWith(
                              savedPrinterName: '',
                              savedPrinterAddress: '',
                            ),
                          );
                        },
                        icon: Icon(Icons.link_off, color: pal.danger),
                      ),
                    ],
                  ),
                ],
                for (final p in _printers.where(
                  (p) => !_hideNonPrinters || !p.isBlockedNonPrinter,
                ))
                  ListTile(
                    dense: true,
                    leading: Icon(
                      p.isLikelyPrinter
                          ? Icons.print_outlined
                          : (p.isBlockedNonPrinter
                              ? Icons.headset_off_outlined
                              : Icons.bluetooth_connected),
                      color: p.isLikelyPrinter
                          ? pal.success
                          : (p.isBlockedNonPrinter
                              ? pal.danger
                              : pal.textSecondary),
                    ),
                    title: Text(p.name),
                    subtitle: Text('${p.address} • ${p.deviceCategoryLabel}'),
                    trailing: TextButton(
                      onPressed: p.isBlockedNonPrinter
                          ? null
                          : () async {
                              final prev = ctrl.settings;
                              await ctrl.updateSettings(
                                prev.copyWith(
                                  savedPrinterName: p.name,
                                  savedPrinterAddress: p.address,
                                ),
                              );
                            },
                      child: Text(p.isBlockedNonPrinter ? 'غير مدعوم' : 'اعتماد'),
                    ),
                  ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 12),
        Card(
          color: pal.dangerSoft,
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  children: [
                    Icon(Icons.delete_forever_outlined, color: pal.danger),
                    const SizedBox(width: 8),
                    Text(
                      'منطقة الخطر — تهيئة ومسح شامل للنظام',
                      style: Theme.of(context)
                          .textTheme
                          .titleMedium
                          ?.copyWith(color: pal.danger),
                    ),
                  ],
                ),
                const SizedBox(height: 6),
                Text(
                  'تصفير جميع الدفاتر المالية والعملاء والموردين والمخزون والمصروفات والحوالات، مع حفظ نسخة احتياطية أوتوماتيكية قبل التنفيذ.',
                  style: TextStyle(fontSize: 12, color: pal.textSecondary),
                ),
                const SizedBox(height: 10),
                OutlinedButton.icon(
                  style: OutlinedButton.styleFrom(
                    foregroundColor: pal.danger,
                    side: BorderSide(color: pal.danger),
                  ),
                  onPressed: _busy ? null : () => _confirmAndResetSystem(ctrl),
                  icon: const Icon(Icons.cleaning_services_outlined),
                  label: const Text('تهيئة / مسح شامل للنظام'),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}
