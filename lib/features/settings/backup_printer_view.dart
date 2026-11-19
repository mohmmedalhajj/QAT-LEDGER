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
  String? _btStatusMessage;
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
                  label: const Text('جلب الطابعات المقترنة'),
                ),
                if (_searchedPrinters && _printers.isEmpty) ...[
                  const SizedBox(height: 8),
                  ErpStatusBanner(
                    tone: ErpBannerTone.warning,
                    title: 'لم يتم العثور على طابعات مقترنة',
                    subtitle: _btStatusMessage ??
                        'تأكد من تشغيل البلوتوث ومنح صلاحية الأجهزة القريبة ثم اقترن بالطابعة من إعدادات Android أولاً.',
                  ),
                ],
                if (ctrl.settings.savedPrinterAddress.isNotEmpty) ...[
                  const SizedBox(height: 8),
                  FilledButton.tonalIcon(
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
                        messenger.showSnackBar(
                          const SnackBar(
                            content:
                                Text('تم إرسال إيصال الاختبار للطابعة بنجاح'),
                          ),
                        );
                      } catch (e) {
                        if (!mounted) return;
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
                ],
                for (final p in _printers)
                  ListTile(
                    dense: true,
                    leading: Icon(
                      p.isLikelyPrinter
                          ? Icons.print_outlined
                          : Icons.bluetooth_connected,
                      color: p.isLikelyPrinter ? pal.success : pal.textSecondary,
                    ),
                    title: Text(p.name),
                    subtitle: Text('${p.address} • ${p.deviceCategoryLabel}'),
                    trailing: TextButton(
                      onPressed: () async {
                        final prev = ctrl.settings;
                        await ctrl.updateSettings(
                          prev.copyWith(
                            savedPrinterName: p.name,
                            savedPrinterAddress: p.address,
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
