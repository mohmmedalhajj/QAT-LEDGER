import 'package:file_picker/file_picker.dart';
import 'package:material_ui/material_ui.dart';
import 'package:path_provider/path_provider.dart';
import 'package:provider/provider.dart';
import 'package:share_plus/share_plus.dart';

import '../../core/date_utils.dart';
import '../../core/money.dart';
import '../../core/platform_bridge.dart';
import '../../core/theme.dart';
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
      await Share.shareXFiles(
        [XFile(res.filePath)],
        text: 'نسخة احتياطية ميزان القات (.qbak)',
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
    if (path == null) return;
    if (!mounted) return;

    final confirm = await showErpConfirmDialog(
      context,
      title: 'تأكيد استعادة البيانات',
      message:
          'سيتم إنشاء نقطة رجوع تلقائية ثم استبدال الدفاتر الحالية بمحتوى الملف المختار. هل تريد المتابعة؟',
      confirmLabel: 'استعادة الآن',
    );
    if (!confirm || !mounted) return;

    setState(() => _busy = true);
    try {
      final res = await ctrl.backupService.restoreFromFile(path);
      await ctrl.reloadAll();
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            res.wasLegacyRoomDb
                ? 'تم ترحيل قاعدة Room القديمة بنجاح والتحقق من القيود!'
                : 'تمت الاستعادة بنجاح مع التحقق من SHA-256!',
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
                  'النسخ الاحتياطي الذري والاستعادة (.qbak / Room .db)',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                const SizedBox(height: 6),
                Text(
                  'يتحقق النظام تلقائياً من بصمة SHA-256 وسلامة SQLite وينشئ نقطة رجوع قبل أي استعادة.',
                  style: TextStyle(fontSize: 12, color: pal.textSecondary),
                ),
                if (_busy) ...[
                  const SizedBox(height: 10),
                  const LinearProgressIndicator(),
                ],
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
                  ErpStatusBanner(
                    tone: ErpBannerTone.success,
                    title: 'آخر نسخة احتياطية محلية',
                    subtitle: _lastBackupInfo!,
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
                    final list =
                        await PlatformBridge.instance.getBondedPrinters();
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
