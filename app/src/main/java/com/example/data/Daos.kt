package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaxDao {
    @Query("SELECT * FROM qat_tax_entries WHERE date = :date LIMIT 1")
    suspend fun getTaxByDate(date: String): TaxEntry?

    @Query("SELECT * FROM qat_tax_entries ORDER BY date DESC")
    fun getAllTax(): Flow<List<TaxEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTax(taxEntry: TaxEntry)
}

@Dao
interface InventoryDao {
    @Query("SELECT * FROM inventory_items ORDER BY name ASC")
    fun getAllItems(): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: Int): InventoryItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: InventoryItem): Long

    @Update
    suspend fun updateItem(item: InventoryItem)

    @Delete
    suspend fun deleteItem(item: InventoryItem)

    @Query("DELETE FROM inventory_items WHERE id = :id")
    suspend fun deleteItemById(id: Int)

    @Query("UPDATE inventory_items SET quantity = quantity + :change WHERE id = :id")
    suspend fun updateQuantity(id: Int, change: Double)

    @Query("UPDATE inventory_items SET receiverId = 0, receiverName = '' WHERE receiverId = :receiverId")
    suspend fun clearReceiverId(receiverId: Int)

    @Query("UPDATE inventory_items SET receiverId = 0, receiverName = '' WHERE receiverId IN (SELECT id FROM qat_import_weights WHERE date = :date)")
    suspend fun clearReceiverIdByDate(date: String)
}

@Dao
interface SalesDao {
    @Query("SELECT * FROM sales_invoices ORDER BY id DESC")
    fun getAllInvoices(): Flow<List<SalesInvoice>>

    @Query("SELECT * FROM sales_invoice_items WHERE invoiceId = :invoiceId")
    fun getInvoiceItems(invoiceId: Int): Flow<List<SalesInvoiceItem>>

    @Query("SELECT * FROM sales_invoice_items")
    fun getAllInvoiceItems(): Flow<List<SalesInvoiceItem>>

    @Query("SELECT * FROM sales_invoice_items WHERE invoiceId = :invoiceId")
    suspend fun getInvoiceItemsDirect(invoiceId: Int): List<SalesInvoiceItem>

    @Query("SELECT * FROM sales_invoices WHERE id = :id LIMIT 1")
    suspend fun getInvoiceById(id: Int): SalesInvoice?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: SalesInvoice): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoiceItem(item: SalesInvoiceItem)

    @Query("DELETE FROM sales_invoices WHERE id = :id")
    suspend fun deleteInvoiceById(id: Int)

    @Query("DELETE FROM sales_invoice_items WHERE invoiceId = :invoiceId")
    suspend fun deleteInvoiceItemsByInvoiceId(invoiceId: Int)
}

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun getAllCustomers(): Flow<List<Customer>>

    @Query("SELECT * FROM customers ORDER BY name ASC")
    suspend fun getAllCustomersDirect(): List<Customer>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getCustomerById(id: Int): Customer?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: Customer): Long

    @Update
    suspend fun updateCustomer(customer: Customer)

    @Delete
    suspend fun deleteCustomer(customer: Customer)

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun deleteCustomerById(id: Int)

    @Query("SELECT * FROM customer_payments ORDER BY date DESC")
    fun getAllPayments(): Flow<List<CustomerPayment>>

    @Query("SELECT * FROM customer_payments WHERE customerId = :customerId ORDER BY date DESC")
    fun getPaymentsForCustomer(customerId: Int): Flow<List<CustomerPayment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: CustomerPayment): Long

    @Query("DELETE FROM customer_payments WHERE id = :id")
    suspend fun deletePaymentById(id: Int)

    @Query("SELECT * FROM customer_payments WHERE id = :id LIMIT 1")
    suspend fun getPaymentById(id: Int): CustomerPayment?

    @Update
    suspend fun updatePayment(payment: CustomerPayment)

    @Query("UPDATE customers SET totalDebt = totalDebt + :change WHERE id = :customerId")
    suspend fun updateCustomerDebt(customerId: Int, change: Double)
}

@Dao
interface SupplierDao {
    @Query("SELECT * FROM suppliers ORDER BY name ASC")
    fun getAllSuppliers(): Flow<List<Supplier>>

    @Query("SELECT * FROM suppliers")
    suspend fun getAllSuppliersDirect(): List<Supplier>

    @Query("SELECT * FROM suppliers WHERE id = :id LIMIT 1")
    suspend fun getSupplierById(id: Int): Supplier?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupplier(supplier: Supplier): Long

    @Update
    suspend fun updateSupplier(supplier: Supplier)

    @Delete
    suspend fun deleteSupplier(supplier: Supplier)

    @Query("DELETE FROM suppliers WHERE id = :id")
    suspend fun deleteSupplierById(id: Int)

    @Query("SELECT * FROM supplier_purchases ORDER BY date DESC")
    fun getAllPurchases(): Flow<List<SupplierPurchase>>

    @Query("SELECT * FROM supplier_purchases WHERE supplierId = :supplierId ORDER BY date DESC")
    fun getPurchasesForSupplier(supplierId: Int): Flow<List<SupplierPurchase>>

    @Query("SELECT * FROM supplier_purchase_items WHERE purchaseId = :purchaseId")
    fun getPurchaseItems(purchaseId: Int): Flow<List<SupplierPurchaseItem>>

    @Query("SELECT * FROM supplier_purchase_items")
    fun getAllPurchaseItems(): Flow<List<SupplierPurchaseItem>>

    @Query("SELECT * FROM supplier_purchase_items WHERE purchaseId = :purchaseId")
    suspend fun getPurchaseItemsDirect(purchaseId: Int): List<SupplierPurchaseItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchase(purchase: SupplierPurchase): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchaseItem(item: SupplierPurchaseItem)

    @Query("SELECT * FROM supplier_purchases WHERE id = :id LIMIT 1")
    suspend fun getPurchaseByIdDirect(id: Int): SupplierPurchase?

    @Query("SELECT * FROM supplier_purchase_items WHERE itemId = :itemId")
    suspend fun getPurchaseItemsByItemIdDirect(itemId: Int): List<SupplierPurchaseItem>

    @Query("DELETE FROM supplier_purchases WHERE id = :id")
    suspend fun deletePurchaseById(id: Int)

    @Query("DELETE FROM supplier_purchase_items WHERE purchaseId = :purchaseId")
    suspend fun deletePurchaseItemsByPurchaseId(purchaseId: Int)

    @Query("UPDATE suppliers SET totalBalance = totalBalance + :change WHERE id = :supplierId")
    suspend fun updateSupplierBalance(supplierId: Int, change: Double)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense): Long

    @Delete
    suspend fun deleteExpense(expense: Expense)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpenseById(id: Int)
}

@Dao
interface TransferRecipientDao {
    @Query("SELECT * FROM transfer_recipients ORDER BY isActive DESC, name ASC")
    fun getAllRecipients(): Flow<List<TransferRecipientEntity>>

    @Query("SELECT * FROM transfer_recipients WHERE isActive = 1 ORDER BY name ASC")
    fun getActiveRecipients(): Flow<List<TransferRecipientEntity>>

    @Query("SELECT * FROM transfer_recipients ORDER BY name ASC")
    suspend fun getAllRecipientsDirect(): List<TransferRecipientEntity>

    @Query("SELECT * FROM transfer_recipients WHERE id = :id LIMIT 1")
    suspend fun getRecipientById(id: Int): TransferRecipientEntity?

    @Query("SELECT * FROM transfer_recipients WHERE TRIM(name) = TRIM(:name) LIMIT 1")
    suspend fun getRecipientByExactName(name: String): TransferRecipientEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipient(recipient: TransferRecipientEntity): Long

    @Update
    suspend fun updateRecipient(recipient: TransferRecipientEntity)

    @Query("UPDATE transfer_recipients SET isActive = :isActive, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setRecipientActive(id: Int, isActive: Boolean, updatedAt: Long)
}

@Dao
interface TransferDao {
    @Query("SELECT * FROM money_transfers ORDER BY date DESC, id DESC")
    fun getAllTransfers(): Flow<List<MoneyTransfer>>

    @Query("SELECT * FROM money_transfers WHERE recipientId = :recipientId ORDER BY date DESC, id DESC")
    fun getTransfersByRecipientId(recipientId: Int): Flow<List<MoneyTransfer>>

    @Query("SELECT * FROM money_transfers WHERE recipientId = :recipientId ORDER BY date DESC, id DESC")
    suspend fun getTransfersByRecipientIdDirect(recipientId: Int): List<MoneyTransfer>

    @Query("UPDATE money_transfers SET receiver = :receiverName WHERE recipientId = :recipientId")
    suspend fun updateReceiverNameForRecipient(recipientId: Int, receiverName: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransfer(transfer: MoneyTransfer): Long

    @Delete
    suspend fun deleteTransfer(transfer: MoneyTransfer)

    @Query("DELETE FROM money_transfers WHERE id = :id")
    suspend fun deleteTransferById(id: Int)
}

@Dao
interface QatImportWeightDao {
    @Query("SELECT * FROM qat_import_weights WHERE date = :date LIMIT 1")
    suspend fun getWeightByDate(date: String): QatImportWeight?

    @Query("SELECT * FROM qat_import_weights WHERE date = :date")
    suspend fun getWeightsByDate(date: String): List<QatImportWeight>

    @Query("SELECT * FROM qat_import_weights WHERE id = :id LIMIT 1")
    suspend fun getWeightById(id: Int): QatImportWeight?

    @Query("SELECT * FROM qat_import_weights ORDER BY date DESC")
    fun getAllWeights(): Flow<List<QatImportWeight>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeight(weight: QatImportWeight): Long

    @Delete
    suspend fun deleteWeight(weight: QatImportWeight)

    @Query("DELETE FROM qat_import_weights WHERE id = :id")
    suspend fun deleteWeightById(id: Int)

    @Query("DELETE FROM qat_import_weights WHERE date = :date")
    suspend fun deleteWeightByDate(date: String)
}

@Dao
interface AgencyProfileDao {
    @Query("SELECT * FROM agency_profile WHERE id = 1 LIMIT 1")
    fun getProfile(): Flow<AgencyProfile?>

    @Query("SELECT * FROM agency_profile WHERE id = 1 LIMIT 1")
    suspend fun getProfileDirect(): AgencyProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: AgencyProfile)
}
