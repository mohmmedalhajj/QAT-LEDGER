package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "qat_tax_entries")
data class TaxEntry(
    @PrimaryKey val date: String, // Format: yyyy-MM-dd
    val taxAmount: Double,
    val stallOutflow: Double = 0.0,
    val laborOutflow: Double = 0.0,
    val notes: String = ""
)

@Entity(tableName = "inventory_items")
data class InventoryItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val quantity: Double,
    val buyPrice: Double,
    val sellPrice: Double,
    val lowStockThreshold: Double,
    val qatType: String = "",
    val notes: String = "",
    val dateAdded: String = "",
    val buyPriceCurrency: String = "ريال يمني",
    val itemNumber: String = "",
    val supplierId: Int = 0,
    val supplierName: String = "",
    val receiverName: String = "",
    val receiverId: Int = 0
)

@Entity(tableName = "sales_invoices")
data class SalesInvoice(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val customerName: String,
    val date: String, // Format: yyyy-MM-dd HH:mm
    val totalAmount: Double,
    val taxApplied: Double,
    val discount: Double,
    val paidAmount: Double,
    val debtAmount: Double,
    val paymentMethod: String = "نقداً",
    val currency: String = "ريال يمني",
    val totalAmountYer: Double = 0.0,
    val totalAmountSar: Double = 0.0,
    val paidAmountYer: Double = 0.0,
    val paidAmountSar: Double = 0.0,
    val debtAmountYer: Double = 0.0,
    val debtAmountSar: Double = 0.0,
    val paymentMethodYer: String = "نقداً",
    val paymentMethodSar: String = "نقداً"
)

@Entity(tableName = "sales_invoice_items")
data class SalesInvoiceItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val invoiceId: Int,
    val itemId: Int,
    val itemName: String,
    val quantity: Double,
    val unitPrice: Double,
    val buyPrice: Double, // Stored at the time of sale for accurate profit calculations
    val currency: String = "ريال يمني",
    val originalCurrency: String = "ريال يمني",
    val saleCurrency: String = "ريال يمني",
    val exchangeRateUsed: Double = 1.0,
    val originalBuyPrice: Double = 0.0,
    val actualSalePrice: Double = 0.0,
    val realProfit: Double = 0.0
)

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phone: String,
    val totalDebt: Double = 0.0,
    val address: String = "",
    val notes: String = ""
)

@Entity(tableName = "customer_payments")
data class CustomerPayment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val customerId: Int,
    val amount: Double,
    val date: String, // Format: yyyy-MM-dd HH:mm
    val notes: String,
    val paymentMethod: String = "نقداً",
    val currency: String = "ريال يمني"
)

@Entity(tableName = "suppliers")
data class Supplier(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phone: String,
    val totalBalance: Double = 0.0,
    val region: String = ""
)

@Entity(tableName = "supplier_purchases")
data class SupplierPurchase(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val supplierId: Int,
    val date: String, // Format: yyyy-MM-dd HH:mm
    val totalAmount: Double,
    val paidAmount: Double,
    val debtRemaining: Double,
    val paymentMethod: String = "نقداً",
    val currency: String = "ريال يمني",
    val totalAmountYer: Double = 0.0,
    val totalAmountSar: Double = 0.0,
    val paidAmountYer: Double = 0.0,
    val paidAmountSar: Double = 0.0,
    val debtRemainingYer: Double = 0.0,
    val debtRemainingSar: Double = 0.0,
    val paymentMethodYer: String = "نقداً",
    val paymentMethodSar: String = "نقداً",
    val transferNumber: String = "",
    val bankName: String = "",
    val demarSar: Double = 0.0,
    val demarYer: Double = 0.0,
    val demarTypeYer: String = "",
    val demarNumberYer: String = "",
    val demarTypeSar: String = "",
    val demarNumberSar: String = ""
)

@Entity(tableName = "supplier_purchase_items")
data class SupplierPurchaseItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val purchaseId: Int,
    val itemId: Int,
    val itemName: String,
    val quantity: Double,
    val unitPrice: Double,
    val currency: String = "ريال يمني"
)

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val category: String,
    val amount: Double,
    val date: String, // Format: yyyy-MM-dd HH:mm
    val notes: String,
    val currency: String = "ريال يمني"
)

@Entity(
    tableName = "transfer_recipients",
    indices = [Index(value = ["name"])]
)
data class TransferRecipientEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phone: String = "",
    val company: String = "",
    val notes: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Entity(
    tableName = "money_transfers",
    indices = [Index(value = ["recipientId"]), Index(value = ["date"])]
)
data class MoneyTransfer(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Double,
    val sender: String = "",
    val receiver: String = "",
    val referenceNumber: String = "",
    val statement: String = "",
    val notes: String = "",
    val date: String, // Format: yyyy-MM-dd HH:mm
    val currency: String = "ريال يمني",
    val recipientId: Int = 0,
    val status: String = "مكتملة"
)

@Entity(tableName = "qat_import_weights")
data class QatImportWeight(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String, // Format: yyyy-MM-dd
    val weight: Double,
    val notes: String = "",
    val receiverName: String = ""
)

@Entity(tableName = "agency_profile")
data class AgencyProfile(
    @PrimaryKey val id: Int = 1,
    val agencyName: String = "",
    val ownerName: String = "",
    val phone: String = "",
    val address: String = "",
    val additionalContact: String = "",
    val tagline: String = "",
    val qatTypes: String = "",
    val defaultCurrency: String = "ريال يمني",
    val currencyMode: String = "BOTH",
    val commercialDetails: String = "",
    val appDisplayName: String = "ميزان القات",
    val logoPath: String = "",
    val headerEnabled: Boolean = true,
    val footerEnabled: Boolean = true,
    val headerText: String = "",
    val footerText: String = "",
    val isConfigured: Boolean = false,
    val updatedAt: Long = 0L
)
