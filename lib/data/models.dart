import 'package:flutter/foundation.dart';
import '../core/money.dart';
import '../core/theme.dart';

enum PaymentType {
  paid('PAID', 'نقداً'),
  partial('PARTIAL', 'دفع جزئي'),
  credit('CREDIT', 'آجل');

  const PaymentType(this.code, this.arabicLabel);
  final String code;
  final String arabicLabel;

  static PaymentType fromRaw(String? raw) {
    final s = (raw ?? '').trim().toUpperCase();
    if (s == 'PAID' ||
        s.contains('نقد') ||
        s.contains('تم الدفع') ||
        s == 'كاش' ||
        s.contains('تحويل') ||
        s.contains('إيداع')) {
      return PaymentType.paid;
    }
    if (s == 'PARTIAL' || s.contains('جزئ')) {
      return PaymentType.partial;
    }
    if (s == 'CREDIT' || s.contains('آجل') || s.contains('اجل') || s.contains('دين')) {
      return PaymentType.credit;
    }
    return PaymentType.paid;
  }
}

enum TransferStatus {
  pending('PENDING', 'جاري التسليم'),
  delivered('DELIVERED', 'تم التسليم'),
  cancelled('CANCELLED', 'ملغي');

  const TransferStatus(this.code, this.arabicLabel);
  final String code;
  final String arabicLabel;

  static TransferStatus fromRaw(String? raw) {
    final s = (raw ?? '').trim().toUpperCase();
    if (s == 'DELIVERED' ||
        s.contains('تم التسليم') ||
        s.contains('مكتمل') ||
        s.contains('مستلم')) {
      return TransferStatus.delivered;
    }
    if (s == 'CANCELLED' || s.contains('ملغ')) {
      return TransferStatus.cancelled;
    }
    return TransferStatus.pending;
  }
}

@immutable
class QatType {
  const QatType({
    required this.id,
    required this.name,
    required this.defaultBuyPriceMinor,
    required this.defaultSellPriceMinor,
    required this.defaultCurrency,
    required this.notes,
    required this.isHidden,
    required this.createdAtMs,
  });

  final int id;
  final String name;
  final int defaultBuyPriceMinor;
  final int defaultSellPriceMinor;
  final CurrencyCode defaultCurrency;
  final String notes;
  final bool isHidden;
  final int createdAtMs;
}

@immutable
class QatImportWeight {
  const QatImportWeight({
    required this.id,
    required this.dateMs,
    required this.dateIso,
    required this.weightMilli,
    required this.receiverName,
    this.notes = '',
    this.isArchived = false,
  });

  final int id;
  final int dateMs;
  final String dateIso;
  final int weightMilli;
  final String receiverName;
  final String notes;
  final bool isArchived;
}

@immutable
class InventoryBatch {
  const InventoryBatch({
    required this.id,
    required this.qatTypeId,
    required this.qatTypeName,
    required this.supplierId,
    required this.supplierName,
    required this.remainingQtyMilli,
    required this.initialQtyMilli,
    required this.unit,
    required this.buyPriceMinor,
    required this.sellPriceMinor,
    required this.currency,
    required this.purchaseDateMs,
    required this.purchaseDateIso,
    required this.isArchived,
    this.itemNumber = '',
    this.lowStockThresholdMilli = 0,
    this.receiverId,
    this.receiverName = '',
    this.notes = '',
  });

  final int id;
  final int? qatTypeId;
  final String qatTypeName;
  final int? supplierId;
  final String supplierName;
  final int remainingQtyMilli;
  final int initialQtyMilli;
  final String unit;
  final int buyPriceMinor;
  final int sellPriceMinor;
  final CurrencyCode currency;
  final int purchaseDateMs;
  final String purchaseDateIso;
  final bool isArchived;
  final String itemNumber;
  final int lowStockThresholdMilli;
  final int? receiverId;
  final String receiverName;
  final String notes;

  int get soldQtyMilli =>
      (initialQtyMilli - remainingQtyMilli).clamp(0, initialQtyMilli).toInt();

  bool get isLowStock =>
      lowStockThresholdMilli > 0 && remainingQtyMilli <= lowStockThresholdMilli;
}

@immutable
class Customer {
  const Customer({
    required this.id,
    required this.name,
    required this.phone,
    required this.address,
    required this.notes,
    required this.createdAtMs,
    required this.isArchived,
    this.balancesByCurrency = const {},
  });

  final int id;
  final String name;
  final String phone;
  final String address;
  final String notes;
  final int createdAtMs;
  final bool isArchived;
  final Map<CurrencyCode, int> balancesByCurrency;

  int balanceFor(CurrencyCode c) => balancesByCurrency[c] ?? 0;

  bool get hasAnyDebt => balancesByCurrency.values.any((v) => v > 0);
}

@immutable
class SaleItem {
  const SaleItem({
    required this.id,
    required this.saleId,
    required this.inventoryBatchId,
    required this.qatTypeName,
    required this.qtyMilli,
    required this.unit,
    required this.unitPriceMinor,
    required this.buyPriceMinor,
    required this.lineTotalMinor,
    required this.currency,
  });

  final int id;
  final int saleId;
  final int? inventoryBatchId;
  final String qatTypeName;
  final int qtyMilli;
  final String unit;
  final int unitPriceMinor;
  final int buyPriceMinor;
  final int lineTotalMinor;
  final CurrencyCode currency;

  int get estimatedCostMinor =>
      InvoiceMath.lineTotalMinor(qtyMilli: qtyMilli, unitPriceMinor: buyPriceMinor);

  int get estimatedProfitMinor => lineTotalMinor - estimatedCostMinor;
}

@immutable
class Sale {
  const Sale({
    required this.id,
    required this.invoiceNumber,
    required this.customerId,
    required this.customerName,
    required this.dateMs,
    required this.dateIso,
    required this.currency,
    required this.subtotalMinor,
    required this.discountMinor,
    required this.taxBps,
    required this.taxAmountMinor,
    required this.totalAmountMinor,
    required this.paidAmountMinor,
    required this.remainingAmountMinor,
    required this.paymentType,
    required this.status,
    required this.notes,
    required this.isArchived,
    this.paymentMethod = '',
    this.items = const [],
  });

  final int id;
  final String invoiceNumber;
  final int? customerId;
  final String customerName;
  final int dateMs;
  final String dateIso;
  final CurrencyCode currency;
  final int subtotalMinor;
  final int discountMinor;
  final int taxBps;
  final int taxAmountMinor;
  final int totalAmountMinor;
  final int paidAmountMinor;
  final int remainingAmountMinor;
  final PaymentType paymentType;
  final String status;
  final String notes;
  final bool isArchived;
  final String paymentMethod;
  final List<SaleItem> items;

  String get effectivePaymentMethodLabel {
    if (paymentMethod.trim().isNotEmpty) return paymentMethod;
    return paymentType.arabicLabel;
  }
}

@immutable
class CustomerPayment {
  const CustomerPayment({
    required this.id,
    required this.customerId,
    required this.customerName,
    required this.amountMinor,
    required this.currency,
    required this.dateMs,
    required this.dateIso,
    required this.paymentMethod,
    required this.notes,
    required this.isArchived,
  });

  final int id;
  final int customerId;
  final String customerName;
  final int amountMinor;
  final CurrencyCode currency;
  final int dateMs;
  final String dateIso;
  final String paymentMethod;
  final String notes;
  final bool isArchived;
}

@immutable
class Supplier {
  const Supplier({
    required this.id,
    required this.name,
    required this.phone,
    required this.address,
    required this.notes,
    required this.createdAtMs,
    required this.isArchived,
    this.region = '',
    this.balancesByCurrency = const {},
  });

  final int id;
  final String name;
  final String phone;
  final String address;
  final String notes;
  final int createdAtMs;
  final bool isArchived;
  final String region;
  final Map<CurrencyCode, int> balancesByCurrency;

  int balanceFor(CurrencyCode c) => balancesByCurrency[c] ?? 0;

  bool get hasAnyPayable => balancesByCurrency.values.any((v) => v > 0);
}

@immutable
class SupplierPurchaseItem {
  const SupplierPurchaseItem({
    required this.id,
    required this.purchaseId,
    required this.qatTypeName,
    required this.qtyMilli,
    required this.unit,
    required this.buyPriceMinor,
    required this.lineTotalMinor,
    required this.currency,
  });

  final int id;
  final int purchaseId;
  final String qatTypeName;
  final int qtyMilli;
  final String unit;
  final int buyPriceMinor;
  final int lineTotalMinor;
  final CurrencyCode currency;
}

@immutable
class SupplierPurchase {
  const SupplierPurchase({
    required this.id,
    required this.purchaseNumber,
    required this.supplierId,
    required this.supplierName,
    required this.dateMs,
    required this.dateIso,
    required this.currency,
    required this.subtotalMinor,
    required this.discountMinor,
    required this.taxBps,
    required this.taxAmountMinor,
    required this.totalAmountMinor,
    required this.paidAmountMinor,
    required this.remainingAmountMinor,
    required this.paymentType,
    required this.notes,
    required this.isArchived,
    this.paymentMethod = '',
    this.bankName = '',
    this.transferNumber = '',
    this.demarMinor = 0,
    this.demarType = '',
    this.demarNumber = '',
    this.items = const [],
  });

  final int id;
  final String purchaseNumber;
  final int supplierId;
  final String supplierName;
  final int dateMs;
  final String dateIso;
  final CurrencyCode currency;
  final int subtotalMinor;
  final int discountMinor;
  final int taxBps;
  final int taxAmountMinor;
  final int totalAmountMinor;
  final int paidAmountMinor;
  final int remainingAmountMinor;
  final PaymentType paymentType;
  final String notes;
  final bool isArchived;
  final String paymentMethod;
  final String bankName;
  final String transferNumber;
  final int demarMinor;
  final String demarType;
  final String demarNumber;
  final List<SupplierPurchaseItem> items;

  int get effectivePaidCappedMinor =>
      paidAmountMinor > totalAmountMinor ? totalAmountMinor : paidAmountMinor;

  int get overpaymentSurplusMinor =>
      paidAmountMinor > totalAmountMinor ? (paidAmountMinor - totalAmountMinor) : 0;
}

@immutable
class SupplierPayment {
  const SupplierPayment({
    required this.id,
    required this.supplierId,
    required this.supplierName,
    required this.amountMinor,
    required this.currency,
    required this.dateMs,
    required this.dateIso,
    required this.paymentMethod,
    required this.notes,
    required this.isArchived,
  });

  final int id;
  final int supplierId;
  final String supplierName;
  final int amountMinor;
  final CurrencyCode currency;
  final int dateMs;
  final String dateIso;
  final String paymentMethod;
  final String notes;
  final bool isArchived;
}

@immutable
class LedgerAccount {
  const LedgerAccount({
    required this.id,
    required this.code,
    required this.name,
    required this.category,
    required this.isSystem,
    required this.isActive,
  });

  final int id;
  final String code;
  final String name;
  final String category;
  final bool isSystem;
  final bool isActive;
}

@immutable
class Expense {
  const Expense({
    required this.id,
    required this.description,
    required this.category,
    required this.accountId,
    required this.amountMinor,
    required this.currency,
    required this.dateMs,
    required this.dateIso,
    required this.notes,
    required this.isArchived,
  });

  final int id;
  final String description;
  final String category;
  final int? accountId;
  final int amountMinor;
  final CurrencyCode currency;
  final int dateMs;
  final String dateIso;
  final String notes;
  final bool isArchived;
}

@immutable
class QatTaxEntry {
  const QatTaxEntry({
    required this.id,
    required this.dateMs,
    required this.dateIso,
    required this.totalQtyMilli,
    required this.taxRateMinor,
    required this.taxAmountMinor,
    required this.currency,
    required this.source,
    required this.notes,
    required this.isArchived,
    this.stallOutflowMinor = 0,
    this.laborOutflowMinor = 0,
  });

  final int id;
  final int dateMs;
  final String dateIso;
  final int totalQtyMilli;
  final int taxRateMinor;
  final int taxAmountMinor;
  final CurrencyCode currency;
  final String source;
  final String notes;
  final bool isArchived;
  final int stallOutflowMinor;
  final int laborOutflowMinor;

  int get totalOutflowMinor =>
      taxAmountMinor + stallOutflowMinor + laborOutflowMinor;
}

@immutable
class TransferRecipient {
  const TransferRecipient({
    required this.id,
    required this.name,
    required this.phone,
    required this.company,
    required this.notes,
    required this.createdAtMs,
    this.isActive = true,
  });

  final int id;
  final String name;
  final String phone;
  final String company;
  final String notes;
  final int createdAtMs;
  final bool isActive;
}

@immutable
class MoneyTransfer {
  const MoneyTransfer({
    required this.id,
    required this.senderName,
    required this.receiverName,
    required this.recipientId,
    required this.amountMinor,
    required this.feeMinor,
    required this.currency,
    required this.transferCompany,
    required this.transferNumber,
    required this.dateMs,
    required this.dateIso,
    required this.status,
    required this.notes,
    required this.isArchived,
  });

  final int id;
  final String senderName;
  final String receiverName;
  final int? recipientId;
  final int amountMinor;
  final int feeMinor;
  final CurrencyCode currency;
  final String transferCompany;
  final String transferNumber;
  final int dateMs;
  final String dateIso;
  final TransferStatus status;
  final String notes;
  final bool isArchived;

  bool get isUnlinked => recipientId == null;
}

@immutable
class TransferRecipientSummary {
  const TransferRecipientSummary({
    required this.recipient,
    required this.totalTransfers,
    required this.deliveredCount,
    required this.pendingCount,
    required this.cancelledCount,
    required this.deliveredByCurrency,
    required this.pendingByCurrency,
    required this.cancelledByCurrency,
    required this.feesByCurrency,
    required this.transfers,
  });

  final TransferRecipient recipient;
  final int totalTransfers;
  final int deliveredCount;
  final int pendingCount;
  final int cancelledCount;
  final Map<CurrencyCode, int> deliveredByCurrency;
  final Map<CurrencyCode, int> pendingByCurrency;
  final Map<CurrencyCode, int> cancelledByCurrency;
  final Map<CurrencyCode, int> feesByCurrency;
  final List<MoneyTransfer> transfers;
}

@immutable
class CurrencyDashboardMetrics {
  const CurrencyDashboardMetrics({
    required this.currency,
    required this.salesTotalMinor,
    required this.salesPaidMinor,
    required this.salesCreditMinor,
    required this.customerReceiptsMinor,
    required this.purchasesTotalMinor,
    required this.purchasesPaidMinor,
    required this.supplierPaymentsMinor,
    required this.expensesMinor,
    required this.taxMinor,
    required this.estimatedCogsMinor,
    required this.totalCustomerDebtMinor,
    required this.totalSupplierPayableMinor,
    required this.transfersDeliveredMinor,
    required this.transfersPendingMinor,
    required this.transfersFeesMinor,
    this.stallOutflowMinor = 0,
    this.laborOutflowMinor = 0,
    this.salesCashOnlyMinor = 0,
    this.salesTransferMinor = 0,
    this.salesDepositMinor = 0,
    this.totalSoldQtyMilli = 0,
    this.supplierOverpaymentSurplusMinor = 0,
  });

  final CurrencyCode currency;
  final int salesTotalMinor;
  final int salesPaidMinor;
  final int salesCreditMinor;
  final int customerReceiptsMinor;
  final int purchasesTotalMinor;
  final int purchasesPaidMinor;
  final int supplierPaymentsMinor;
  final int expensesMinor;
  final int taxMinor;
  final int estimatedCogsMinor;
  final int totalCustomerDebtMinor;
  final int totalSupplierPayableMinor;
  final int transfersDeliveredMinor;
  final int transfersPendingMinor;
  final int transfersFeesMinor;
  final int stallOutflowMinor;
  final int laborOutflowMinor;
  final int salesCashOnlyMinor;
  final int salesTransferMinor;
  final int salesDepositMinor;
  final int totalSoldQtyMilli;
  final int supplierOverpaymentSurplusMinor;

  int get totalTaxAndOutflowsMinor =>
      taxMinor + stallOutflowMinor + laborOutflowMinor;

  int get grossProfitMinor => salesTotalMinor - estimatedCogsMinor;

  int get netProfitMinor =>
      grossProfitMinor - expensesMinor - totalTaxAndOutflowsMinor;

  /// Actual cash flow net of operational outflows (sales paid + collections - supplier paid - expenses - tax/stall/labor).
  int get cashNetFlowMinor =>
      (salesPaidMinor + customerReceiptsMinor) -
      (purchasesPaidMinor +
          supplierPaymentsMinor +
          expensesMinor +
          totalTaxAndOutflowsMinor);

  /// Live Cash Drawer Balance matching Android DashboardScreen/ReportsScreen (also deducts outgoing transfers + fees).
  int get cashBoxBalanceMinor =>
      cashNetFlowMinor -
      (transfersDeliveredMinor + transfersPendingMinor + transfersFeesMinor);
}

@immutable
class DailyChartPoint {
  const DailyChartPoint({
    required this.dateIso,
    required this.shortLabel,
    required this.salesMinor,
    required this.expensesMinor,
  });

  final String dateIso;
  final String shortLabel;
  final int salesMinor;
  final int expensesMinor;

  int get profitMinor => salesMinor - expensesMinor;
  String get labelAr => shortLabel;
}

@immutable
class StatementLine {
  const StatementLine({
    required this.dateMs,
    required this.dateIso,
    required this.reference,
    required this.description,
    required this.currency,
    required this.debitMinor,
    required this.creditMinor,
    required this.runningBalanceMinor,
  });

  final int dateMs;
  final String dateIso;
  final String reference;
  final String description;
  final CurrencyCode currency;
  final int debitMinor;
  final int creditMinor;
  final int runningBalanceMinor;
}

@immutable
class AgencySettings {
  const AgencySettings({
    required this.agencyName,
    required this.agencyPhone,
    required this.agencyAddress,
    required this.footerNote,
    required this.logoPath,
    required this.currencyMode,
    required this.displaySarToYerMilli,
    required this.themeChoice,
    required this.onboardingComplete,
    required this.savedPrinterName,
    required this.savedPrinterAddress,
    required this.printerPaperMm,
    this.ownerName = '',
    this.additionalContact = '',
    this.tagline = 'تجارة وتسويق أجود أنواع القات اليمني',
    this.qatTypesRaw = 'أرحبي, همداني, خولاني, رداعي, يافعي, ضالعي',
    this.commercialDetails = '',
    this.appDisplayName = '',
    this.headerEnabled = true,
    this.footerEnabled = true,
    this.headerText = '',
    this.paletteId = 'emerald_green',
    this.customPrimaryHex = '#267451',
    this.customAccentHex = '#C83E3B',
    this.customCanvasHex = '',
    this.customCardHex = '',
    this.customTextHex = '',
    this.syncColorsWithPdf = true,
    this.rememberMe = true,
    this.appLockEnabled = false,
  });

  final String agencyName;
  final String agencyPhone;
  final String agencyAddress;
  final String footerNote;
  final String logoPath;
  final CurrencyMode currencyMode;
  final int displaySarToYerMilli;
  final AppThemeModeChoice themeChoice;
  final bool onboardingComplete;
  final String savedPrinterName;
  final String savedPrinterAddress;
  final int printerPaperMm;
  final String ownerName;
  final String additionalContact;
  final String tagline;
  final String qatTypesRaw;
  final String commercialDetails;
  final String appDisplayName;
  final bool headerEnabled;
  final bool footerEnabled;
  final String headerText;
  final String paletteId;
  final String customPrimaryHex;
  final String customAccentHex;
  final String customCanvasHex;
  final String customCardHex;
  final String customTextHex;
  final bool syncColorsWithPdf;
  final bool rememberMe;
  final bool appLockEnabled;

  CurrencyPolicy get currencyPolicy => CurrencyPolicy(
        mode: currencyMode,
        userDisplaySarToYerMilli: displaySarToYerMilli,
      );

  AppColorPreset get activeColorPreset => AppColorPreset.resolve(
        paletteId: paletteId,
        customPrimaryHex: customPrimaryHex,
        customAccentHex: customAccentHex,
      );

  List<String> get qatTypeSuggestions => qatTypesRaw
      .split(RegExp(r'[,،\n]+'))
      .map((s) => s.trim())
      .where((s) => s.isNotEmpty)
      .toSet()
      .toList();

  String get effectiveAppTitle =>
      appDisplayName.trim().isNotEmpty ? appDisplayName.trim() : agencyName;

  AgencySettings copyWith({
    String? agencyName,
    String? agencyPhone,
    String? agencyAddress,
    String? footerNote,
    String? logoPath,
    CurrencyMode? currencyMode,
    int? displaySarToYerMilli,
    AppThemeModeChoice? themeChoice,
    bool? onboardingComplete,
    String? savedPrinterName,
    String? savedPrinterAddress,
    int? printerPaperMm,
    String? ownerName,
    String? additionalContact,
    String? tagline,
    String? qatTypesRaw,
    String? commercialDetails,
    String? appDisplayName,
    bool? headerEnabled,
    bool? footerEnabled,
    String? headerText,
    String? paletteId,
    String? customPrimaryHex,
    String? customAccentHex,
    String? customCanvasHex,
    String? customCardHex,
    String? customTextHex,
    bool? syncColorsWithPdf,
    bool? rememberMe,
    bool? appLockEnabled,
  }) {
    return AgencySettings(
      agencyName: agencyName ?? this.agencyName,
      agencyPhone: agencyPhone ?? this.agencyPhone,
      agencyAddress: agencyAddress ?? this.agencyAddress,
      footerNote: footerNote ?? this.footerNote,
      logoPath: logoPath ?? this.logoPath,
      currencyMode: currencyMode ?? this.currencyMode,
      displaySarToYerMilli: displaySarToYerMilli ?? this.displaySarToYerMilli,
      themeChoice: themeChoice ?? this.themeChoice,
      onboardingComplete: onboardingComplete ?? this.onboardingComplete,
      savedPrinterName: savedPrinterName ?? this.savedPrinterName,
      savedPrinterAddress: savedPrinterAddress ?? this.savedPrinterAddress,
      printerPaperMm: printerPaperMm ?? this.printerPaperMm,
      ownerName: ownerName ?? this.ownerName,
      additionalContact: additionalContact ?? this.additionalContact,
      tagline: tagline ?? this.tagline,
      qatTypesRaw: qatTypesRaw ?? this.qatTypesRaw,
      commercialDetails: commercialDetails ?? this.commercialDetails,
      appDisplayName: appDisplayName ?? this.appDisplayName,
      headerEnabled: headerEnabled ?? this.headerEnabled,
      footerEnabled: footerEnabled ?? this.footerEnabled,
      headerText: headerText ?? this.headerText,
      paletteId: paletteId ?? this.paletteId,
      customPrimaryHex: customPrimaryHex ?? this.customPrimaryHex,
      customAccentHex: customAccentHex ?? this.customAccentHex,
      customCanvasHex: customCanvasHex ?? this.customCanvasHex,
      customCardHex: customCardHex ?? this.customCardHex,
      customTextHex: customTextHex ?? this.customTextHex,
      syncColorsWithPdf: syncColorsWithPdf ?? this.syncColorsWithPdf,
      rememberMe: rememberMe ?? this.rememberMe,
      appLockEnabled: appLockEnabled ?? this.appLockEnabled,
    );
  }

  static const defaults = AgencySettings(
    agencyName: 'وكالة ميزان القات',
    agencyPhone: '',
    agencyAddress: '',
    footerNote: 'شكراً لتعاملكم معنا',
    logoPath: '',
    currencyMode: CurrencyMode.yerOnly,
    displaySarToYerMilli: 0,
    themeChoice: AppThemeModeChoice.lightEnterprise,
    onboardingComplete: false,
    savedPrinterName: '',
    savedPrinterAddress: '',
    printerPaperMm: 80,
  );
}

@immutable
class MigrationSummary {
  const MigrationSummary({
    required this.performed,
    required this.sourceVersion,
    required this.rowCounts,
    required this.auditMessages,
    required this.sourceSha256,
    required this.backupSnapshotPath,
  });

  final bool performed;
  final int sourceVersion;
  final Map<String, int> rowCounts;
  final List<String> auditMessages;
  final String sourceSha256;
  final String backupSnapshotPath;
}

enum UserRole {
  admin('ADMIN', 'مدير النظام'),
  cashier('CASHIER', 'بائع / محاسب');

  const UserRole(this.code, this.arabicLabel);
  final String code;
  final String arabicLabel;

  static UserRole fromCode(String? raw) =>
      raw?.toUpperCase() == 'CASHIER' ? UserRole.cashier : UserRole.admin;
}

@immutable
class AppUser {
  const AppUser({
    required this.username,
    required this.displayName,
    required this.role,
    required this.passwordSha256,
    this.canDelete = true,
    this.canEditPrices = true,
    this.canManageSettings = true,
    this.canViewReports = true,
  });

  final String username;
  final String displayName;
  final UserRole role;
  final String passwordSha256;
  final bool canDelete;
  final bool canEditPrices;
  final bool canManageSettings;
  final bool canViewReports;

  Map<String, Object?> toJson() => {
        'username': username,
        'displayName': displayName,
        'role': role.code,
        'passwordSha256': passwordSha256,
        'canDelete': canDelete,
        'canEditPrices': canEditPrices,
        'canManageSettings': canManageSettings,
        'canViewReports': canViewReports,
      };

  static AppUser fromJson(Map<String, dynamic> map) => AppUser(
        username: (map['username'] ?? '').toString(),
        displayName: (map['displayName'] ?? '').toString(),
        role: UserRole.fromCode(map['role']?.toString()),
        passwordSha256: (map['passwordSha256'] ?? '').toString(),
        canDelete: (map['canDelete'] as bool?) ?? true,
        canEditPrices: (map['canEditPrices'] as bool?) ?? true,
        canManageSettings: (map['canManageSettings'] as bool?) ?? true,
        canViewReports: (map['canViewReports'] as bool?) ?? true,
      );
}

@immutable
class SectionPermissions {
  const SectionPermissions({
    this.sales = true,
    this.inventory = true,
    this.transfers = true,
    this.reports = true,
    this.archive = true,
    this.expenses = true,
    this.accounts = true,
    this.customers = true,
  });

  final bool sales;
  final bool inventory;
  final bool transfers;
  final bool reports;
  final bool archive;
  final bool expenses;
  final bool accounts;
  final bool customers;

  Map<String, bool> toJson() => {
        'sales': sales,
        'inventory': inventory,
        'transfers': transfers,
        'reports': reports,
        'archive': archive,
        'expenses': expenses,
        'accounts': accounts,
        'customers': customers,
      };

  static SectionPermissions fromJson(Map<String, dynamic> map) => SectionPermissions(
        sales: (map['sales'] as bool?) ?? true,
        inventory: (map['inventory'] as bool?) ?? true,
        transfers: (map['transfers'] as bool?) ?? true,
        reports: (map['reports'] as bool?) ?? true,
        archive: (map['archive'] as bool?) ?? true,
        expenses: (map['expenses'] as bool?) ?? true,
        accounts: (map['accounts'] as bool?) ?? true,
        customers: (map['customers'] as bool?) ?? true,
      );

  SectionPermissions copyWith({
    bool? sales,
    bool? inventory,
    bool? transfers,
    bool? reports,
    bool? archive,
    bool? expenses,
    bool? accounts,
    bool? customers,
  }) =>
      SectionPermissions(
        sales: sales ?? this.sales,
        inventory: inventory ?? this.inventory,
        transfers: transfers ?? this.transfers,
        reports: reports ?? this.reports,
        archive: archive ?? this.archive,
        expenses: expenses ?? this.expenses,
        accounts: accounts ?? this.accounts,
        customers: customers ?? this.customers,
      );
}

@immutable
class InventoryValuation {
  const InventoryValuation({
    required this.currency,
    required this.batchCount,
    required this.totalQtyMilli,
    required this.totalCostMinor,
    required this.totalRetailMinor,
  });

  final CurrencyCode currency;
  final int batchCount;
  final int totalQtyMilli;
  final int totalCostMinor;
  final int totalRetailMinor;

  int get expectedProfitMinor => totalRetailMinor - totalCostMinor;
}

@immutable
class DailySettlementSnapshot {
  const DailySettlementSnapshot({
    required this.id,
    required this.dateIso,
    required this.epochMs,
    required this.currency,
    required this.expectedCashMinor,
    required this.actualCountedMinor,
    required this.varianceMinor,
    required this.salesCashMinor,
    required this.customerReceiptsMinor,
    required this.purchasesCashMinor,
    required this.supplierPaymentsMinor,
    required this.expensesMinor,
    required this.transferFeesMinor,
    required this.pendingTransfersCount,
    required this.notes,
    required this.createdBy,
  });

  final String id;
  final String dateIso;
  final int epochMs;
  final CurrencyCode currency;
  final int expectedCashMinor;
  final int actualCountedMinor;
  final int varianceMinor;
  final int salesCashMinor;
  final int customerReceiptsMinor;
  final int purchasesCashMinor;
  final int supplierPaymentsMinor;
  final int expensesMinor;
  final int transferFeesMinor;
  final int pendingTransfersCount;
  final String notes;
  final String createdBy;

  Map<String, Object?> toJson() => {
        'id': id,
        'dateIso': dateIso,
        'epochMs': epochMs,
        'currency': currency.code,
        'expectedCashMinor': expectedCashMinor,
        'actualCountedMinor': actualCountedMinor,
        'varianceMinor': varianceMinor,
        'salesCashMinor': salesCashMinor,
        'customerReceiptsMinor': customerReceiptsMinor,
        'purchasesCashMinor': purchasesCashMinor,
        'supplierPaymentsMinor': supplierPaymentsMinor,
        'expensesMinor': expensesMinor,
        'transferFeesMinor': transferFeesMinor,
        'pendingTransfersCount': pendingTransfersCount,
        'notes': notes,
        'createdBy': createdBy,
      };

  static DailySettlementSnapshot fromJson(Map<String, dynamic> map) =>
      DailySettlementSnapshot(
        id: (map['id'] ?? '').toString(),
        dateIso: (map['dateIso'] ?? '').toString(),
        epochMs: (map['epochMs'] as num?)?.toInt() ?? 0,
        currency: CurrencyCode.fromRaw(map['currency']?.toString()),
        expectedCashMinor: (map['expectedCashMinor'] as num?)?.toInt() ?? 0,
        actualCountedMinor: (map['actualCountedMinor'] as num?)?.toInt() ?? 0,
        varianceMinor: (map['varianceMinor'] as num?)?.toInt() ?? 0,
        salesCashMinor: (map['salesCashMinor'] as num?)?.toInt() ?? 0,
        customerReceiptsMinor:
            (map['customerReceiptsMinor'] as num?)?.toInt() ?? 0,
        purchasesCashMinor: (map['purchasesCashMinor'] as num?)?.toInt() ?? 0,
        supplierPaymentsMinor:
            (map['supplierPaymentsMinor'] as num?)?.toInt() ?? 0,
        expensesMinor: (map['expensesMinor'] as num?)?.toInt() ?? 0,
        transferFeesMinor: (map['transferFeesMinor'] as num?)?.toInt() ?? 0,
        pendingTransfersCount:
            (map['pendingTransfersCount'] as num?)?.toInt() ?? 0,
        notes: (map['notes'] ?? '').toString(),
        createdBy: (map['createdBy'] ?? '').toString(),
      );
}

@immutable
class InventoryReconciliationEntry {
  const InventoryReconciliationEntry({
    required this.id,
    required this.dateIso,
    required this.epochMs,
    required this.batchId,
    required this.qatTypeName,
    required this.unit,
    required this.currency,
    required this.systemQtyMilli,
    required this.countedQtyMilli,
    required this.diffQtyMilli,
    required this.unitCostMinor,
    required this.financialImpactMinor,
    required this.notes,
    required this.createdBy,
  });

  final String id;
  final String dateIso;
  final int epochMs;
  final int batchId;
  final String qatTypeName;
  final String unit;
  final CurrencyCode currency;
  final int systemQtyMilli;
  final int countedQtyMilli;
  final int diffQtyMilli;
  final int unitCostMinor;
  final int financialImpactMinor;
  final String notes;
  final String createdBy;

  Map<String, Object?> toJson() => {
        'id': id,
        'dateIso': dateIso,
        'epochMs': epochMs,
        'batchId': batchId,
        'qatTypeName': qatTypeName,
        'unit': unit,
        'currency': currency.code,
        'systemQtyMilli': systemQtyMilli,
        'countedQtyMilli': countedQtyMilli,
        'diffQtyMilli': diffQtyMilli,
        'unitCostMinor': unitCostMinor,
        'financialImpactMinor': financialImpactMinor,
        'notes': notes,
        'createdBy': createdBy,
      };

  static InventoryReconciliationEntry fromJson(Map<String, dynamic> map) =>
      InventoryReconciliationEntry(
        id: (map['id'] ?? '').toString(),
        dateIso: (map['dateIso'] ?? '').toString(),
        epochMs: (map['epochMs'] as num?)?.toInt() ?? 0,
        batchId: (map['batchId'] as num?)?.toInt() ?? 0,
        qatTypeName: (map['qatTypeName'] ?? '').toString(),
        unit: (map['unit'] ?? '').toString(),
        currency: CurrencyCode.fromRaw(map['currency']?.toString()),
        systemQtyMilli: (map['systemQtyMilli'] as num?)?.toInt() ?? 0,
        countedQtyMilli: (map['countedQtyMilli'] as num?)?.toInt() ?? 0,
        diffQtyMilli: (map['diffQtyMilli'] as num?)?.toInt() ?? 0,
        unitCostMinor: (map['unitCostMinor'] as num?)?.toInt() ?? 0,
        financialImpactMinor:
            (map['financialImpactMinor'] as num?)?.toInt() ?? 0,
        notes: (map['notes'] ?? '').toString(),
        createdBy: (map['createdBy'] ?? '').toString(),
      );
}
