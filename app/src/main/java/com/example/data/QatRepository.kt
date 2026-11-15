package com.example.data

import com.example.util.Helpers
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class QatRepository(private val db: AppDatabase) {

    private val taxDao = db.taxDao()
    private val inventoryDao = db.inventoryDao()
    private val salesDao = db.salesDao()
    private val customerDao = db.customerDao()
    private val supplierDao = db.supplierDao()
    private val expenseDao = db.expenseDao()
    private val transferDao = db.transferDao()
    private val transferRecipientDao = db.transferRecipientDao()
    private val qatImportWeightDao = db.qatImportWeightDao()
    private val agencyProfileDao = db.agencyProfileDao()

    // --- Flows ---
    val allTax: Flow<List<TaxEntry>> = taxDao.getAllTax()
    val allInventoryItems: Flow<List<InventoryItem>> = inventoryDao.getAllItems()
    val allWeights: Flow<List<QatImportWeight>> = qatImportWeightDao.getAllWeights()
    val allInvoices: Flow<List<SalesInvoice>> = salesDao.getAllInvoices()
    val allCustomers: Flow<List<Customer>> = customerDao.getAllCustomers()
    val allSuppliers: Flow<List<Supplier>> = supplierDao.getAllSuppliers()
    val allPurchases: Flow<List<SupplierPurchase>> = supplierDao.getAllPurchases()
    val allExpenses: Flow<List<Expense>> = expenseDao.getAllExpenses()
    val allPaymentsCode: Flow<List<CustomerPayment>> = customerDao.getAllPayments()
    val allTransfers: Flow<List<MoneyTransfer>> = transferDao.getAllTransfers()
    val allTransferRecipients: Flow<List<TransferRecipientEntity>> = transferRecipientDao.getAllRecipients()
    val activeTransferRecipients: Flow<List<TransferRecipientEntity>> = transferRecipientDao.getActiveRecipients()
    val allPurchaseItems: Flow<List<SupplierPurchaseItem>> = supplierDao.getAllPurchaseItems()
    val allInvoiceItems: Flow<List<SalesInvoiceItem>> = salesDao.getAllInvoiceItems()
    val agencyProfile: Flow<AgencyProfile?> = agencyProfileDao.getProfile()

    // --- Agency Profile Operations ---
    suspend fun getAgencyProfileDirect(): AgencyProfile? {
        return agencyProfileDao.getProfileDirect()
    }

    suspend fun saveAgencyProfile(profile: AgencyProfile) {
        agencyProfileDao.upsertProfile(profile.copy(id = 1))
    }

    // --- Tax Operations ---
    suspend fun getTaxAmountForDate(date: String): Double {
        return taxDao.getTaxByDate(date)?.taxAmount ?: 0.0
    }

    suspend fun setTaxForDate(date: String, amount: Double) {
        val existing = taxDao.getTaxByDate(date)
        if (existing != null) {
            taxDao.insertTax(existing.copy(taxAmount = amount))
        } else {
            taxDao.insertTax(TaxEntry(date = date, taxAmount = amount))
        }
    }

    suspend fun getTaxEntryForDate(date: String): TaxEntry? {
        return taxDao.getTaxByDate(date)
    }

    suspend fun saveTaxEntry(taxEntry: TaxEntry) {
        taxDao.insertTax(taxEntry)
    }

    // --- Qat Import Weight Operations ---
    suspend fun getWeightForDate(date: String): QatImportWeight? {
        return qatImportWeightDao.getWeightByDate(date)
    }

    suspend fun getWeightsForDate(date: String): List<QatImportWeight> {
        return qatImportWeightDao.getWeightsByDate(date)
    }

    suspend fun getWeightById(id: Int): QatImportWeight? {
        return qatImportWeightDao.getWeightById(id)
    }

    suspend fun saveWeight(weightEntry: QatImportWeight): Long {
        return qatImportWeightDao.insertWeight(weightEntry)
    }

    suspend fun deleteWeightById(id: Int) {
        db.withTransaction {
            inventoryDao.clearReceiverId(id)
            qatImportWeightDao.deleteWeightById(id)
        }
    }

    suspend fun deleteWeightByDate(date: String) {
        db.withTransaction {
            inventoryDao.clearReceiverIdByDate(date)
            qatImportWeightDao.deleteWeightByDate(date)
        }
    }

    // --- Inventory Operations ---
    suspend fun addOrUpdateInventoryItem(item: InventoryItem): Long {
        return db.withTransaction {
            var updatedItem = item
            val isNewItem = item.id == 0
            var finalSupplierId = item.supplierId
            var finalSupplierName = item.supplierName

            // Link Qat Receiver dynamically using Receiver ID
            var finalReceiverId = item.receiverId
            var finalReceiverName = item.receiverName.trim()

            if (finalReceiverName.isNotEmpty()) {
                val standardizedDate = Helpers.standardizeDateToYmd(item.dateAdded.ifEmpty { Helpers.getCurrentDateTime() }).ifEmpty { Helpers.getCurrentDate() }
                
                var existingWeight: QatImportWeight? = null
                if (finalReceiverId > 0) {
                    existingWeight = qatImportWeightDao.getWeightById(finalReceiverId)
                }
                
                if (existingWeight == null) {
                    val listForDate = qatImportWeightDao.getWeightsByDate(standardizedDate)
                    val matched = listForDate.find { it.receiverName.trim().equals(finalReceiverName, ignoreCase = true) }
                    if (matched != null) {
                        finalReceiverId = matched.id
                        finalReceiverName = matched.receiverName
                    } else {
                        val newRecord = QatImportWeight(
                            id = 0,
                            date = standardizedDate,
                            weight = 0.0,
                            notes = "مضاف تلقائياً مع الصنف ${item.name}",
                            receiverName = finalReceiverName
                        )
                        val newId = qatImportWeightDao.insertWeight(newRecord).toInt()
                        finalReceiverId = newId
                    }
                } else {
                    finalReceiverName = existingWeight.receiverName
                }
                
                updatedItem = updatedItem.copy(receiverId = finalReceiverId, receiverName = finalReceiverName)
            } else {
                updatedItem = updatedItem.copy(receiverId = 0, receiverName = "")
            }

            if (item.supplierName.trim().isNotEmpty()) {
                val existingList = supplierDao.getAllSuppliersDirect()
                val existing = existingList.find { it.name.trim() == item.supplierName.trim() }
                if (existing != null) {
                    finalSupplierId = existing.id
                    finalSupplierName = existing.name
                    updatedItem = updatedItem.copy(supplierId = existing.id, supplierName = existing.name)
                } else {
                    val newSupId = supplierDao.insertSupplier(
                        Supplier(
                            id = 0,
                            name = item.supplierName.trim(),
                            phone = "",
                            region = ""
                        )
                    )
                    finalSupplierId = newSupId.toInt()
                    finalSupplierName = item.supplierName.trim()
                    updatedItem = updatedItem.copy(supplierId = newSupId.toInt(), supplierName = item.supplierName.trim())
                }
            }
            val generatedItemId = inventoryDao.insertItem(updatedItem).toInt()

            // Automatically create a purchase if it's a new item and there is an associated supplier
            if (isNewItem && finalSupplierId > 0 && item.quantity > 0.0) {
                val purchaseDate = item.dateAdded.ifEmpty { Helpers.getCurrentDateTime() }
                val totalBill = item.quantity * item.buyPrice
                val isSar = item.buyPriceCurrency.contains("سعودي")

                val totalBillYer = if (isSar) 0.0 else totalBill
                val totalBillSar = if (isSar) totalBill else 0.0

                val pMethod = "آجل"
                val pMethodYer = if (isSar) "نقداً" else "آجل"
                val pMethodSar = if (isSar) "آجل" else "نقداً"

                val purchase = SupplierPurchase(
                    supplierId = finalSupplierId,
                    date = purchaseDate,
                    totalAmount = totalBill,
                    paidAmount = 0.0,
                    debtRemaining = totalBill,
                    paymentMethod = pMethod,
                    currency = if (isSar) "ريال سعودي" else "ريال يمني",
                    totalAmountYer = totalBillYer,
                    totalAmountSar = totalBillSar,
                    paidAmountYer = 0.0,
                    paidAmountSar = 0.0,
                    debtRemainingYer = totalBillYer,
                    debtRemainingSar = totalBillSar,
                    paymentMethodYer = pMethodYer,
                    paymentMethodSar = pMethodSar
                )

                val purchaseId = supplierDao.insertPurchase(purchase).toInt()

                val purchaseItem = SupplierPurchaseItem(
                    purchaseId = purchaseId,
                    itemId = generatedItemId,
                    itemName = item.name,
                    quantity = item.quantity,
                    unitPrice = item.buyPrice,
                    currency = if (isSar) "ريال سعودي" else "ريال يمني"
                )

                supplierDao.insertPurchaseItem(purchaseItem)

                // Update supplier balance
                supplierDao.updateSupplierBalance(finalSupplierId, totalBill)
            } else {
                // Synchronize modifications to the corresponding SupplierPurchase and SupplierPurchaseItem
                val matchingPurchaseItems = supplierDao.getPurchaseItemsByItemIdDirect(item.id)
                for (pItem in matchingPurchaseItems) {
                    val purchase = supplierDao.getPurchaseByIdDirect(pItem.purchaseId)
                    if (purchase != null) {
                        val isSar = item.buyPriceCurrency.contains("سعودي")
                        val itemCurrency = if (isSar) "ريال سعودي" else "ريال يمني"
                        
                        val updatedPurchaseItem = pItem.copy(
                            itemName = item.name,
                            quantity = item.quantity,
                            unitPrice = item.buyPrice,
                            currency = itemCurrency
                        )
                        supplierDao.insertPurchaseItem(updatedPurchaseItem)

                        val siblingItems = supplierDao.getPurchaseItemsDirect(purchase.id)
                        val updatedSiblings = siblingItems.map { if (it.itemId == item.id) updatedPurchaseItem else it }

                        var totalBillYer = 0.0
                        var totalBillSar = 0.0
                        for (sib in updatedSiblings) {
                            if (sib.currency.contains("سعودي")) {
                                totalBillSar += sib.quantity * sib.unitPrice
                            } else {
                                totalBillYer += sib.quantity * sib.unitPrice
                            }
                        }
                        val newTotalBill = totalBillYer + totalBillSar
                        val isDual = totalBillYer > 0.0 && totalBillSar > 0.0

                        val pMethodYer = purchase.paymentMethodYer
                        val pMethodSar = purchase.paymentMethodSar
                        var finalPaidYer = purchase.paidAmountYer
                        var finalPaidSar = purchase.paidAmountSar

                        if (pMethodYer in listOf("نقداً", "تحويل", "إيداع")) {
                            finalPaidYer = totalBillYer
                        } else if (pMethodYer == "آجل") {
                            finalPaidYer = 0.0
                        }
                        if (pMethodSar in listOf("نقداً", "تحويل", "إيداع")) {
                            finalPaidSar = totalBillSar
                        } else if (pMethodSar == "آجل") {
                            finalPaidSar = 0.0
                        }

                        val debtYer = if (totalBillYer > finalPaidYer) totalBillYer - finalPaidYer else 0.0
                        val debtSar = if (totalBillSar > finalPaidSar) totalBillSar - finalPaidSar else 0.0
                        val debtTotal = debtYer + debtSar

                        val inferredCurrency = if (isDual) "ريال يمني / ريال سعودي" else if (totalBillSar > 0.0) "ريال سعودي" else "ريال يمني"
                        val updatedPurchase = purchase.copy(
                            supplierId = finalSupplierId,
                            date = item.dateAdded.ifEmpty { purchase.date },
                            totalAmount = newTotalBill,
                            paidAmount = finalPaidYer + finalPaidSar,
                            debtRemaining = debtTotal,
                            currency = inferredCurrency,
                            totalAmountYer = totalBillYer,
                            totalAmountSar = totalBillSar,
                            paidAmountYer = finalPaidYer,
                            paidAmountSar = finalPaidSar,
                            debtRemainingYer = debtYer,
                            debtRemainingSar = debtSar
                        )
                        supplierDao.insertPurchase(updatedPurchase)

                        // Update supplier balances
                        supplierDao.updateSupplierBalance(purchase.supplierId, -purchase.debtRemaining)
                        supplierDao.updateSupplierBalance(finalSupplierId, debtTotal)
                    }
                }
            }

            generatedItemId.toLong()
        }
    }

    suspend fun deleteInventoryItem(item: InventoryItem) {
        db.withTransaction {
            val matchingPurchaseItems = supplierDao.getPurchaseItemsByItemIdDirect(item.id)
            for (pItem in matchingPurchaseItems) {
                val purchase = supplierDao.getPurchaseByIdDirect(pItem.purchaseId)
                if (purchase != null) {
                    // Revert old remaining debt from supplier balance
                    supplierDao.updateSupplierBalance(purchase.supplierId, -purchase.debtRemaining)

                    val siblingItems = supplierDao.getPurchaseItemsDirect(purchase.id)
                    val remainingSiblings = siblingItems.filter { it.itemId != item.id }

                    supplierDao.deletePurchaseItemsByPurchaseId(purchase.id)

                    if (remainingSiblings.isEmpty()) {
                        // No items left, delete purchase entirely
                        supplierDao.deletePurchaseById(purchase.id)
                    } else {
                        // Re-insert remaining items
                        for (sib in remainingSiblings) {
                            supplierDao.insertPurchaseItem(sib)
                        }

                        // Recalculate totals
                        var totalBillYer = 0.0
                        var totalBillSar = 0.0
                        for (sib in remainingSiblings) {
                            if (sib.currency.contains("سعودي")) {
                                totalBillSar += sib.quantity * sib.unitPrice
                            } else {
                                totalBillYer += sib.quantity * sib.unitPrice
                            }
                        }
                        val newTotalBill = totalBillYer + totalBillSar
                        val isDual = totalBillYer > 0.0 && totalBillSar > 0.0

                        val pMethodYer = purchase.paymentMethodYer
                        val pMethodSar = purchase.paymentMethodSar
                        var finalPaidYer = purchase.paidAmountYer
                        var finalPaidSar = purchase.paidAmountSar

                        if (pMethodYer in listOf("نقداً", "تحويل", "إيداع")) {
                            finalPaidYer = totalBillYer
                        } else if (pMethodYer == "آجل") {
                            finalPaidYer = 0.0
                        }
                        if (pMethodSar in listOf("نقداً", "تحويل", "إيداع")) {
                            finalPaidSar = totalBillSar
                        } else if (pMethodSar == "آجل") {
                            finalPaidSar = 0.0
                        }

                        val debtYer = if (totalBillYer > finalPaidYer) totalBillYer - finalPaidYer else 0.0
                        val debtSar = if (totalBillSar > finalPaidSar) totalBillSar - finalPaidSar else 0.0
                        val debtTotal = debtYer + debtSar

                        val inferredCurrency = if (isDual) "ريال يمني / ريال سعودي" else if (totalBillSar > 0.0) "ريال سعودي" else "ريال يمني"
                        val updatedPurchase = purchase.copy(
                            totalAmount = newTotalBill,
                            paidAmount = finalPaidYer + finalPaidSar,
                            debtRemaining = debtTotal,
                            currency = inferredCurrency,
                            totalAmountYer = totalBillYer,
                            totalAmountSar = totalBillSar,
                            paidAmountYer = finalPaidYer,
                            paidAmountSar = finalPaidSar,
                            debtRemainingYer = debtYer,
                            debtRemainingSar = debtSar
                        )
                        supplierDao.insertPurchase(updatedPurchase)

                        // Add new remaining debt to supplier balance
                        supplierDao.updateSupplierBalance(purchase.supplierId, debtTotal)
                    }
                }
            }
            inventoryDao.deleteItemById(item.id)
        }
    }

    suspend fun getItemById(id: Int): InventoryItem? {
        return inventoryDao.getItemById(id)
    }

    // --- Sales Operations with stock reduction & debt accumulation ---
    suspend fun makeSale(
        customerName: String,
        date: String, // yyyy-MM-dd HH:mm
        items: List<Pair<InventoryItem, Double>>, // Item & Quantity sold
        taxPercent: Double, // Daily flat tax applied or custom
        discount: Double,
        paidAmount: Double,
        paymentMethod: String = "نقداً",
        currency: String = "ريال يمني",
        paidAmountYer: Double = 0.0,
        paidAmountSar: Double = 0.0,
        paymentMethodYer: String = "نقداً",
        paymentMethodSar: String = "نقداً",
        exchangeRate: Double = 1.0
    ): Long {
        return db.withTransaction {
            val subtotalYer = items.filter {
                val activeCurrency = if (it.first.notes.startsWith("sale_currency:")) {
                    it.first.notes.substringAfter("sale_currency:").substringBefore("|")
                } else {
                    it.first.buyPriceCurrency.ifEmpty { "ريال يمني" }
                }
                activeCurrency.contains("يمني")
            }.sumOf { it.first.sellPrice * it.second }

            val subtotalSar = items.filter {
                val activeCurrency = if (it.first.notes.startsWith("sale_currency:")) {
                    it.first.notes.substringAfter("sale_currency:").substringBefore("|")
                } else {
                    it.first.buyPriceCurrency.ifEmpty { "ريال يمني" }
                }
                activeCurrency.contains("سعودي")
            }.sumOf { it.first.sellPrice * it.second }

            var finalPaidYer = paidAmountYer
            var finalPaidSar = paidAmountSar

            // Compute YER paid based on its specific payment method
            if (subtotalYer > 0.0) {
                if (paymentMethodYer in listOf("نقداً", "تحويل", "إيداع")) {
                    finalPaidYer = subtotalYer
                } else if (paymentMethodYer == "آجل") {
                    finalPaidYer = 0.0
                }
            } else {
                finalPaidYer = 0.0
            }

            // Compute SAR paid based on its specific payment method
            if (subtotalSar > 0.0) {
                if (paymentMethodSar in listOf("نقداً", "تحويل", "إيداع")) {
                    finalPaidSar = subtotalSar
                } else if (paymentMethodSar == "آجل") {
                    finalPaidSar = 0.0
                }
            } else {
                finalPaidSar = 0.0
            }

            // Overrides if paid value is precisely supplied (such as in partial payment)
            if (paymentMethodYer == "دفع جزئي" && paidAmountYer > 0.0) {
                finalPaidYer = paidAmountYer
            }
            if (paymentMethodSar == "دفع جزئي" && paidAmountSar > 0.0) {
                finalPaidSar = paidAmountSar
            }

            // Fallbacks for legacy single-currency calls
            if (subtotalYer > 0.0 && subtotalSar == 0.0 && paidAmount > 0.0 && finalPaidYer == 0.0) {
                finalPaidYer = paidAmount
            }
            if (subtotalSar > 0.0 && subtotalYer == 0.0 && paidAmount > 0.0 && finalPaidSar == 0.0) {
                finalPaidSar = paidAmount
            }

            val debtYer = if (subtotalYer > finalPaidYer) subtotalYer - finalPaidYer else 0.0
            val debtSar = if (subtotalSar > finalPaidSar) subtotalSar - finalPaidSar else 0.0

            val computedTotal = subtotalYer + subtotalSar
            val computedPaid = finalPaidYer + finalPaidSar
            val computedDebt = debtYer + debtSar

            val calculatedCurrency = if (subtotalYer > 0.0 && subtotalSar > 0.0) "ريال يمني / ريال سعودي" else if (subtotalSar > 0.0) "ريال سعودي" else "ريال يمني"
            val calculatedPaymentMethod = if (subtotalYer > 0.0 && subtotalSar > 0.0) {
                "يمني: $paymentMethodYer | سعودي: $paymentMethodSar"
            } else if (subtotalSar > 0.0) {
                paymentMethodSar
            } else {
                paymentMethodYer
            }

            // Create Invoice
            val invoice = SalesInvoice(
                customerName = customerName,
                date = date,
                totalAmount = computedTotal,
                taxApplied = 0.0,
                discount = 0.0,
                paidAmount = computedPaid,
                debtAmount = computedDebt,
                paymentMethod = calculatedPaymentMethod,
                currency = calculatedCurrency,
                totalAmountYer = subtotalYer,
                totalAmountSar = subtotalSar,
                paidAmountYer = finalPaidYer,
                paidAmountSar = finalPaidSar,
                debtAmountYer = debtYer,
                debtAmountSar = debtSar,
                paymentMethodYer = paymentMethodYer,
                paymentMethodSar = paymentMethodSar
            )
            val invoiceId = salesDao.insertInvoice(invoice).toInt()

            // Save items & update stock
            for (p in items) {
                val stockItem = p.first
                val qty = p.second
                val origCurrency = stockItem.buyPriceCurrency.ifEmpty { "ريال يمني" }
                val activeSaleCurrency = if (stockItem.notes.startsWith("sale_currency:")) {
                    stockItem.notes.substringAfter("sale_currency:").substringBefore("|")
                } else {
                    origCurrency
                }
                
                val isBuySar = origCurrency.contains("سعودي")
                val isSaleSar = activeSaleCurrency.contains("سعودي")
                val computedRealProfit = if (isBuySar && !isSaleSar) {
                    val buyPriceConverted = stockItem.buyPrice * exchangeRate
                    (stockItem.sellPrice - buyPriceConverted) * qty
                } else if (!isBuySar && isSaleSar) {
                    val buyPriceConverted = if (exchangeRate > 0.0) stockItem.buyPrice / exchangeRate else stockItem.buyPrice
                    (stockItem.sellPrice - buyPriceConverted) * qty
                } else {
                    (stockItem.sellPrice - stockItem.buyPrice) * qty
                }

                val saleItem = SalesInvoiceItem(
                    invoiceId = invoiceId,
                    itemId = stockItem.id,
                    itemName = stockItem.name,
                    quantity = qty,
                    unitPrice = stockItem.sellPrice,
                    buyPrice = stockItem.buyPrice,
                    currency = activeSaleCurrency,
                    originalCurrency = origCurrency,
                    saleCurrency = activeSaleCurrency,
                    exchangeRateUsed = exchangeRate,
                    originalBuyPrice = stockItem.buyPrice,
                    actualSalePrice = stockItem.sellPrice,
                    realProfit = computedRealProfit
                )
                salesDao.insertInvoiceItem(saleItem)
                
                // Deduct stock
                inventoryDao.updateQuantity(stockItem.id, -qty)
            }

            // Update customer debt if customer name matches a registered customer
            if (customerName.trim().isNotEmpty() && customerName.trim() != "عميل نقدي") {
                val clientList = customerDao.getAllCustomersDirect()
                val registeredCust = clientList.find { it.name.trim().equals(customerName.trim(), ignoreCase = true) }
                if (registeredCust != null) {
                    if (computedDebt > 0.0) {
                        customerDao.updateCustomerDebt(registeredCust.id, computedDebt)
                    }
                } else {
                    // Create customer automatically!
                    customerDao.insertCustomer(
                        Customer(name = customerName.trim(), phone = "", totalDebt = computedDebt)
                    )
                }
            }

            invoiceId.toLong()
        }
    }

    suspend fun deleteSale(invoiceId: Int) {
        db.withTransaction {
            val invoice = salesDao.getInvoiceById(invoiceId) ?: return@withTransaction
            val items = salesDao.getInvoiceItemsDirect(invoiceId)
            
            // Revert stock changes
            for (item in items) {
                inventoryDao.updateQuantity(item.itemId, item.quantity)
            }

            // Revert customer debt
            if (invoice.debtAmount > 0.0) {
                val clientList = customerDao.getAllCustomersDirect()
                val registeredCust = clientList.find { it.name.trim().equals(invoice.customerName.trim(), ignoreCase = true) }
                if (registeredCust != null) {
                    customerDao.updateCustomerDebt(registeredCust.id, -invoice.debtAmount)
                }
            }

            // Wipe records
            salesDao.deleteInvoiceById(invoiceId)
            salesDao.deleteInvoiceItemsByInvoiceId(invoiceId)
        }
    }

    fun getSaleItems(invoiceId: Int): Flow<List<SalesInvoiceItem>> {
        return salesDao.getInvoiceItems(invoiceId)
    }

    suspend fun updateSalesInvoiceFull(
        invoiceId: Int,
        newCustomerName: String,
        newDate: String,
        newCurrency: String,
        newTotalAmount: Double,
        newTaxApplied: Double,
        newDiscount: Double,
        newPaidAmount: Double,
        newDebtAmount: Double,
        newPaymentMethod: String,
        newTotalAmountYer: Double = 0.0,
        newTotalAmountSar: Double = 0.0,
        newPaidAmountYer: Double = 0.0,
        newPaidAmountSar: Double = 0.0,
        newDebtAmountYer: Double = 0.0,
        newDebtAmountSar: Double = 0.0,
        newPaymentMethodYer: String = "نقداً",
        newPaymentMethodSar: String = "نقداً"
    ) {
        db.withTransaction {
            val invoice = salesDao.getInvoiceById(invoiceId) ?: return@withTransaction
            val oldDebt = invoice.debtAmount
            
            val isMulti = (newTotalAmountYer > 0.0 || newTotalAmountSar > 0.0) && (newCurrency.contains("مختلط") || (newTotalAmountYer > 0.0 && newTotalAmountSar > 0.0))
            val calculatedDebt = if (isMulti) {
                newDebtAmountYer + newDebtAmountSar
            } else if (newDebtAmount > 0.0) {
                newDebtAmount
            } else {
                (newTotalAmount - newPaidAmount - newDiscount).coerceAtLeast(0.0)
            }
            
            val isYer = newCurrency.contains("يمني")
            val isSar = newCurrency.contains("سعودي")
            val finalTotalYer = if (isMulti) newTotalAmountYer else if (isYer) newTotalAmount else 0.0
            val finalTotalSar = if (isMulti) newTotalAmountSar else if (isSar) newTotalAmount else 0.0
            val finalPaidYer = if (isMulti) newPaidAmountYer else if (isYer) newPaidAmount else 0.0
            val finalPaidSar = if (isMulti) newPaidAmountSar else if (isSar) newPaidAmount else 0.0
            val finalDebtYer = if (isMulti) newDebtAmountYer else if (isYer) calculatedDebt else 0.0
            val finalDebtSar = if (isMulti) newDebtAmountSar else if (isSar) calculatedDebt else 0.0

            val clientList = customerDao.getAllCustomersDirect()
            
            // 1. Revert old debt from old customer
            val oldCust = clientList.find { it.name.trim().equals(invoice.customerName.trim(), ignoreCase = true) }
            if (oldCust != null && oldDebt > 0.0) {
                customerDao.updateCustomerDebt(oldCust.id, -oldDebt)
            }
            
            // 2. Apply new debt to new customer
            val newCust = clientList.find { it.name.trim().equals(newCustomerName.trim(), ignoreCase = true) }
            if (newCust != null && calculatedDebt > 0.0) {
                customerDao.updateCustomerDebt(newCust.id, calculatedDebt)
            } else if (calculatedDebt > 0.0 && newCustomerName.trim().isNotEmpty() && newCustomerName.trim() != "عميل نقدي") {
                customerDao.insertCustomer(
                    Customer(name = newCustomerName.trim(), phone = "", totalDebt = calculatedDebt)
                )
            }
            
            // 3. Update the invoice record
            val updatedInvoice = invoice.copy(
                customerName = newCustomerName.trim(),
                date = newDate,
                currency = newCurrency,
                totalAmount = newTotalAmount,
                taxApplied = newTaxApplied,
                discount = newDiscount,
                paidAmount = newPaidAmount,
                debtAmount = calculatedDebt,
                paymentMethod = newPaymentMethod,
                totalAmountYer = finalTotalYer,
                totalAmountSar = finalTotalSar,
                paidAmountYer = finalPaidYer,
                paidAmountSar = finalPaidSar,
                debtAmountYer = finalDebtYer,
                debtAmountSar = finalDebtSar,
                paymentMethodYer = if (isMulti) newPaymentMethodYer else newPaymentMethod,
                paymentMethodSar = if (isMulti) newPaymentMethodSar else newPaymentMethod
            )
            salesDao.insertInvoice(updatedInvoice)
        }
    }

    suspend fun updateInvoice(
        invoiceId: Int,
        newCustomerName: String,
        newPaidAmount: Double,
        newPaymentMethod: String
    ) {
        val inv = salesDao.getInvoiceById(invoiceId) ?: return
        val newDebt = (inv.totalAmount - newPaidAmount - inv.discount).coerceAtLeast(0.0)
        updateSalesInvoiceFull(
            invoiceId = invoiceId,
            newCustomerName = newCustomerName,
            newDate = inv.date,
            newCurrency = inv.currency,
            newTotalAmount = inv.totalAmount,
            newTaxApplied = inv.taxApplied,
            newDiscount = inv.discount,
            newPaidAmount = newPaidAmount,
            newDebtAmount = newDebt,
            newPaymentMethod = newPaymentMethod,
            newTotalAmountYer = inv.totalAmountYer,
            newTotalAmountSar = inv.totalAmountSar,
            newPaidAmountYer = if (inv.currency.contains("يمني") || inv.totalAmountYer > 0.0) newPaidAmount else inv.paidAmountYer,
            newPaidAmountSar = if (inv.currency.contains("سعودي") || inv.totalAmountSar > 0.0) newPaidAmount else inv.paidAmountSar,
            newDebtAmountYer = if (inv.totalAmountYer > 0.0) (inv.totalAmountYer - newPaidAmount).coerceAtLeast(0.0) else if (inv.currency.contains("يمني")) newDebt else 0.0,
            newDebtAmountSar = if (inv.totalAmountSar > 0.0) (inv.totalAmountSar - newPaidAmount).coerceAtLeast(0.0) else if (inv.currency.contains("سعودي")) newDebt else 0.0,
            newPaymentMethodYer = newPaymentMethod,
            newPaymentMethodSar = newPaymentMethod
        )
    }

    suspend fun updateCustomerPayment(
        paymentId: Int,
        newAmount: Double,
        newDate: String,
        newNotes: String,
        newPaymentMethod: String,
        newCurrency: String
    ) {
        db.withTransaction {
            val oldPay = customerDao.getPaymentById(paymentId) ?: return@withTransaction
            val diff = oldPay.amount - newAmount
            customerDao.updateCustomerDebt(oldPay.customerId, diff)
            
            val updatedPay = oldPay.copy(
                amount = newAmount,
                date = newDate,
                notes = newNotes,
                paymentMethod = newPaymentMethod,
                currency = newCurrency
            )
            customerDao.insertPayment(updatedPay)
        }
    }

    // --- Customer operations ---
    suspend fun getAllCustomersDirect(): List<Customer> {
        return customerDao.getAllCustomersDirect()
    }

    suspend fun addCustomer(customer: Customer): Long {
        return customerDao.insertCustomer(customer)
    }

    suspend fun updateCustomer(customer: Customer) {
        customerDao.updateCustomer(customer)
    }

    suspend fun deleteCustomer(customer: Customer) {
        customerDao.deleteCustomerById(customer.id)
    }

    suspend fun recordDirectCustomerDebt(
        customerName: String,
        amount: Double,
        date: String,
        statement: String,
        notes: String,
        currency: String = "ريال يمني"
    ): Long {
        return db.withTransaction {
            val paymentMethod = if (notes.trim().isNotEmpty()) {
                "دين مباشر: $statement ($notes)"
            } else {
                "دين مباشر: $statement"
            }
            val isYer = currency.contains("يمني")
            val isSar = currency.contains("سعودي")
            val amountYer = if (isYer) amount else 0.0
            val amountSar = if (isSar) amount else 0.0
            val invoice = SalesInvoice(
                customerName = customerName,
                date = date,
                totalAmount = amount,
                taxApplied = 0.0,
                discount = 0.0,
                paidAmount = 0.0,
                debtAmount = amount,
                paymentMethod = paymentMethod,
                currency = currency,
                totalAmountYer = amountYer,
                totalAmountSar = amountSar,
                paidAmountYer = 0.0,
                paidAmountSar = 0.0,
                debtAmountYer = amountYer,
                debtAmountSar = amountSar
            )
            val invoiceId = salesDao.insertInvoice(invoice)
            
            // Update customer debt
            if (customerName.trim().isNotEmpty() && customerName.trim() != "عميل نقدي") {
                val clientList = customerDao.getAllCustomersDirect()
                val registeredCust = clientList.find { it.name.trim().equals(customerName.trim(), ignoreCase = true) }
                if (registeredCust != null) {
                    customerDao.updateCustomerDebt(registeredCust.id, amount)
                } else {
                    customerDao.insertCustomer(
                        Customer(name = customerName.trim(), phone = "", totalDebt = amount)
                    )
                }
            }
            invoiceId
        }
    }

    suspend fun payCustomerDebt(customerId: Int, amount: Double, date: String, notes: String, paymentMethod: String = "نقداً", currency: String = "ريال يمني") {
        db.withTransaction {
            val pay = CustomerPayment(
                customerId = customerId,
                amount = amount,
                date = date,
                notes = notes,
                paymentMethod = paymentMethod,
                currency = currency
            )
            customerDao.insertPayment(pay)
            customerDao.updateCustomerDebt(customerId, -amount)
        }
    }

    fun getPaymentsForCustomer(customerId: Int): Flow<List<CustomerPayment>> {
        return customerDao.getPaymentsForCustomer(customerId)
    }

    // --- Supplier & purchase operations ---
    suspend fun addSupplier(supplier: Supplier): Long {
        return supplierDao.insertSupplier(supplier)
    }

    suspend fun updateSupplier(supplier: Supplier) {
        supplierDao.updateSupplier(supplier)
    }

    suspend fun deleteSupplier(supplier: Supplier) {
        supplierDao.deleteSupplierById(supplier.id)
    }

    suspend fun makeSupplierPurchase(
        supplierId: Int,
        date: String,
        itemsPriceAndQty: List<Triple<InventoryItem, Double, Double>>, // Item, Quantity, PurchaseUnitPrice
        paidAmount: Double,
        paymentMethod: String = "نقداً",
        paidAmountYer: Double = 0.0,
        paidAmountSar: Double = 0.0,
        paymentMethodYer: String = "نقداً",
        paymentMethodSar: String = "نقداً"
    ): Long {
        return db.withTransaction {
            var totalBillYer = 0.0
            var totalBillSar = 0.0
            for (trip in itemsPriceAndQty) {
                val stockItem = trip.first
                val qty = trip.second
                val buyPrice = trip.third
                val itemCurrency = (stockItem.buyPriceCurrency.ifEmpty { "ريال يمني" })
                if (itemCurrency.contains("سعودي")) {
                    totalBillSar += qty * buyPrice
                } else {
                    totalBillYer += qty * buyPrice
                }
            }

            val totalBill = totalBillYer + totalBillSar

            var finalPaidYer = paidAmountYer
            var finalPaidSar = paidAmountSar

            // Compute YER paid based on its specific payment method
            if (totalBillYer > 0.0) {
                if (paymentMethodYer in listOf("نقداً", "تحويل", "إيداع")) {
                    finalPaidYer = totalBillYer
                } else if (paymentMethodYer == "آجل") {
                    finalPaidYer = 0.0
                }
            } else {
                finalPaidYer = 0.0
            }

            // Compute SAR paid based on its specific payment method
            if (totalBillSar > 0.0) {
                if (paymentMethodSar in listOf("نقداً", "تحويل", "إيداع")) {
                    finalPaidSar = totalBillSar
                } else if (paymentMethodSar == "آجل") {
                    finalPaidSar = 0.0
                }
            } else {
                finalPaidSar = 0.0
            }

            // Overrides if paid value is precisely supplied (such as in partial payment)
            if (paymentMethodYer == "دفع جزئي" && paidAmountYer > 0.0) {
                finalPaidYer = paidAmountYer
            }
            if (paymentMethodSar == "دفع جزئي" && paidAmountSar > 0.0) {
                finalPaidSar = paidAmountSar
            }

            // Fallbacks for legacy single-currency calls
            if (totalBillYer > 0.0 && totalBillSar == 0.0 && paidAmount > 0.0 && finalPaidYer == 0.0) {
                finalPaidYer = paidAmount
            }
            if (totalBillSar > 0.0 && totalBillYer == 0.0 && paidAmount > 0.0 && finalPaidSar == 0.0) {
                finalPaidSar = paidAmount
            }

            val debtYer = if (totalBillYer > finalPaidYer) totalBillYer - finalPaidYer else 0.0
            val debtSar = if (totalBillSar > finalPaidSar) totalBillSar - finalPaidSar else 0.0
            val debtTotal = debtYer + debtSar

            val isDual = totalBillYer > 0.0 && totalBillSar > 0.0
            val inferredCurrency = if (isDual) "ريال يمني / ريال سعودي" else if (totalBillSar > 0.0) "ريال سعودي" else "ريال يمني"
            val calculatedPaymentMethod = if (totalBillYer > 0.0 && totalBillSar > 0.0) {
                "يمني: $paymentMethodYer | سعودي: $paymentMethodSar"
            } else if (totalBillSar > 0.0) {
                paymentMethodSar
            } else {
                paymentMethodYer
            }

            val purchase = SupplierPurchase(
                supplierId = supplierId,
                date = date,
                totalAmount = totalBill,
                paidAmount = finalPaidYer + finalPaidSar,
                debtRemaining = debtTotal,
                paymentMethod = calculatedPaymentMethod,
                currency = inferredCurrency,
                totalAmountYer = totalBillYer,
                totalAmountSar = totalBillSar,
                paidAmountYer = finalPaidYer,
                paidAmountSar = finalPaidSar,
                debtRemainingYer = debtYer,
                debtRemainingSar = debtSar,
                paymentMethodYer = paymentMethodYer,
                paymentMethodSar = paymentMethodSar
            )
            val purchaseId = supplierDao.insertPurchase(purchase).toInt()

            for (trip in itemsPriceAndQty) {
                val stockItem = trip.first
                val qty = trip.second
                val buyPrice = trip.third
                val itemCurrency = if (stockItem.buyPriceCurrency.ifEmpty { "ريال يمني" }.contains("سعودي")) "ريال سعودي" else "ريال يمني"

                // Add purchase item record
                val pItem = SupplierPurchaseItem(
                    purchaseId = purchaseId,
                    itemId = stockItem.id,
                    itemName = stockItem.name,
                    quantity = qty,
                    unitPrice = buyPrice,
                    currency = itemCurrency
                )
                supplierDao.insertPurchaseItem(pItem)

                // Update stock and update average buyPrice!
                val existing = inventoryDao.getItemById(stockItem.id)
                if (existing != null) {
                    val newQuantity = existing.quantity + qty
                    val newBuyPrice = if (newQuantity > 0) {
                        ((existing.quantity * existing.buyPrice) + (qty * buyPrice)) / newQuantity
                    } else buyPrice
                    
                    inventoryDao.updateItem(
                        existing.copy(
                            quantity = newQuantity,
                            buyPrice = newBuyPrice,
                            buyPriceCurrency = itemCurrency,
                            dateAdded = existing.dateAdded.ifEmpty { date.ifEmpty { Helpers.getCurrentDateTime() } },
                            itemNumber = if (stockItem.itemNumber.isNotEmpty()) stockItem.itemNumber else existing.itemNumber,
                            supplierId = if (stockItem.supplierId != 0) stockItem.supplierId else existing.supplierId,
                            supplierName = if (stockItem.supplierName.isNotEmpty()) stockItem.supplierName else existing.supplierName
                        )
                    )
                } else {
                    // Create new stock item
                    inventoryDao.insertItem(
                        InventoryItem(
                            id = stockItem.id,
                            name = stockItem.name,
                            quantity = qty,
                            buyPrice = buyPrice,
                            sellPrice = stockItem.sellPrice,
                            lowStockThreshold = stockItem.lowStockThreshold,
                            buyPriceCurrency = itemCurrency,
                            dateAdded = date.ifEmpty { Helpers.getCurrentDateTime() },
                            itemNumber = stockItem.itemNumber,
                            supplierId = stockItem.supplierId,
                            supplierName = stockItem.supplierName
                        )
                    )
                }
            }

            if (debtTotal > 0.0) {
                supplierDao.updateSupplierBalance(supplierId, debtTotal)
            }

            purchaseId.toLong()
        }
    }

    suspend fun makeSupplierReturn(
        supplierId: Int,
        itemId: Int,
        itemName: String,
        returnedQty: Double,
        returnedPrice: Double,
        returnDate: String,
        refundedAmount: Double,
        notes: String
    ): Long {
        return db.withTransaction {
            val totalValue = returnedQty * returnedPrice
            val debtChange = -(totalValue - refundedAmount)

            val stockItem = inventoryDao.getItemById(itemId)
            val inferredCurrency = stockItem?.buyPriceCurrency ?: "ريال يمني"
            val isSar = inferredCurrency.contains("سعودي")

            val returnPurchase = SupplierPurchase(
                supplierId = supplierId,
                date = returnDate,
                totalAmount = -totalValue,
                paidAmount = -refundedAmount,
                debtRemaining = debtChange,
                paymentMethod = if (notes.trim().isNotEmpty()) "مرتجع: $notes" else "مرتجع بضاعة",
                currency = inferredCurrency,
                totalAmountYer = if (isSar) 0.0 else -totalValue,
                totalAmountSar = if (isSar) -totalValue else 0.0,
                paidAmountYer = if (isSar) 0.0 else -refundedAmount,
                paidAmountSar = if (isSar) -refundedAmount else 0.0,
                debtRemainingYer = if (isSar) 0.0 else debtChange,
                debtRemainingSar = if (isSar) debtChange else 0.0,
                paymentMethodYer = if (isSar) "لا يوجد" else (if (notes.trim().isNotEmpty()) "مرتجع: $notes" else "مرتجع"),
                paymentMethodSar = if (isSar) (if (notes.trim().isNotEmpty()) "مرتجع: $notes" else "مرتجع") else "لا يوجد"
            )
            val purchaseId = supplierDao.insertPurchase(returnPurchase).toInt()

            val pItem = SupplierPurchaseItem(
                purchaseId = purchaseId,
                itemId = itemId,
                itemName = itemName,
                quantity = -returnedQty,
                unitPrice = returnedPrice,
                currency = inferredCurrency
            )
            supplierDao.insertPurchaseItem(pItem)

            inventoryDao.updateQuantity(itemId, -returnedQty)
            supplierDao.updateSupplierBalance(supplierId, debtChange)

            purchaseId.toLong()
        }
    }

    suspend fun deletePurchase(purchaseId: Int) {
        db.withTransaction {
            val p = supplierDao.getAllPurchases().firstOrNull()?.find { it.id == purchaseId } ?: return@withTransaction
            val items = supplierDao.getPurchaseItemsDirect(purchaseId)

            for (item in items) {
                // Return stock
                inventoryDao.updateQuantity(item.itemId, -item.quantity)
            }

            if (p.debtRemaining > 0.0) {
                supplierDao.updateSupplierBalance(p.supplierId, -p.debtRemaining)
            }

            supplierDao.deletePurchaseById(purchaseId)
            supplierDao.deletePurchaseItemsByPurchaseId(purchaseId)
        }
    }

    suspend fun paySupplierInvoice(
        purchaseId: Int,
        payAmount: Double,
        currency: String, // "ريال يمني" or "ريال سعودي"
        paymentDetails: String,
        dateOfPayment: String,
        transferNumber: String = "",
        bankName: String = "",
        demarSar: Double = 0.0,
        demarYer: Double = 0.0,
        demarTypeYer: String = "",
        demarNumberYer: String = "",
        demarTypeSar: String = "",
        demarNumberSar: String = ""
    ) {
        db.withTransaction {
            val p = supplierDao.getAllPurchases().firstOrNull()?.find { it.id == purchaseId } ?: return@withTransaction
            
            // Revert original debt remaining from supplier balance before changing
            if (p.debtRemaining != 0.0) {
                supplierDao.updateSupplierBalance(p.supplierId, -p.debtRemaining)
            }
            
            val isSar = currency.contains("سعودي")
            val updated = if (isSar) {
                val newPaidSar = p.paidAmountSar + payAmount
                val newDebtSar = p.debtRemainingSar - payAmount
                p.copy(
                    paidAmount = p.paidAmount + payAmount,
                    debtRemaining = p.debtRemaining - payAmount,
                    paidAmountSar = newPaidSar,
                    debtRemainingSar = newDebtSar,
                    paymentMethodSar = p.paymentMethodSar + " | سداد: $paymentDetails ($dateOfPayment)",
                    paymentMethod = p.paymentMethod + " | سداد سعودي بقيمة $payAmount ($paymentDetails - $dateOfPayment)",
                    transferNumber = if (transferNumber.isNotEmpty()) transferNumber else p.transferNumber,
                    bankName = bankName,
                    demarSar = if (demarSar > 0.0) demarSar else p.demarSar,
                    demarYer = if (demarYer > 0.0) demarYer else p.demarYer,
                    demarTypeSar = if (demarTypeSar.isNotEmpty()) demarTypeSar else p.demarTypeSar,
                    demarNumberSar = if (demarNumberSar.isNotEmpty()) demarNumberSar else p.demarNumberSar,
                    demarTypeYer = if (demarTypeYer.isNotEmpty()) demarTypeYer else p.demarTypeYer,
                    demarNumberYer = if (demarNumberYer.isNotEmpty()) demarNumberYer else p.demarNumberYer
                )
            } else {
                val newPaidYer = p.paidAmountYer + payAmount
                val newDebtYer = p.debtRemainingYer - payAmount
                p.copy(
                    paidAmount = p.paidAmount + payAmount,
                    debtRemaining = p.debtRemaining - payAmount,
                    paidAmountYer = newPaidYer,
                    debtRemainingYer = newDebtYer,
                    paymentMethodYer = p.paymentMethodYer + " | سداد: $paymentDetails ($dateOfPayment)",
                    paymentMethod = p.paymentMethod + " | سداد يمني بقيمة $payAmount ($paymentDetails - $dateOfPayment)",
                    transferNumber = if (transferNumber.isNotEmpty()) transferNumber else p.transferNumber,
                    bankName = bankName,
                    demarSar = if (demarSar > 0.0) demarSar else p.demarSar,
                    demarYer = if (demarYer > 0.0) demarYer else p.demarYer,
                    demarTypeSar = if (demarTypeSar.isNotEmpty()) demarTypeSar else p.demarTypeSar,
                    demarNumberSar = if (demarNumberSar.isNotEmpty()) demarNumberSar else p.demarNumberSar,
                    demarTypeYer = if (demarTypeYer.isNotEmpty()) demarTypeYer else p.demarTypeYer,
                    demarNumberYer = if (demarNumberYer.isNotEmpty()) demarNumberYer else p.demarNumberYer
                )
            }
            
            // Save updated purchase
            supplierDao.insertPurchase(updated)
            
            // Re-apply the new remaining debt to supplier balance
            if (updated.debtRemaining != 0.0) {
                supplierDao.updateSupplierBalance(updated.supplierId, updated.debtRemaining)
            }
        }
    }

    suspend fun updatePurchaseInvoice(
        purchaseId: Int,
        newPaidAmount: Double,
        newPaymentMethod: String
    ) {
        db.withTransaction {
            val p = supplierDao.getAllPurchases().firstOrNull()?.find { it.id == purchaseId } ?: return@withTransaction
            
            // 1. Revert previous supplier debt from supplier balance
            if (p.debtRemaining > 0.0) {
                supplierDao.updateSupplierBalance(p.supplierId, -p.debtRemaining)
            }
            
            // 2. Compute new remaining debt
            val newDebt = if (p.totalAmount > newPaidAmount) p.totalAmount - newPaidAmount else 0.0
            
            // 3. Apply new remaining debt to supplier balance
            if (newDebt > 0.0) {
                supplierDao.updateSupplierBalance(p.supplierId, newDebt)
            }
            
            // 4. Save updated purchase record
            val updated = p.copy(
                paidAmount = newPaidAmount,
                debtRemaining = newDebt,
                paymentMethod = newPaymentMethod
            )
            supplierDao.insertPurchase(updated)
        }
    }

    suspend fun editSupplierPurchaseItem(
        purchaseItemId: Int,
        newItemName: String,
        newQuantity: Double,
        newUnitPrice: Double,
        newItemNumber: String
    ) {
        db.withTransaction {
            // 1. Get current purchase item
            val pItem = supplierDao.getAllPurchaseItems().firstOrNull()?.find { it.id == purchaseItemId } ?: return@withTransaction
            val purchaseId = pItem.purchaseId
            val itemId = pItem.itemId

            // 2. Get parent purchase and associated inventory item
            val purchase = supplierDao.getAllPurchases().firstOrNull()?.find { it.id == purchaseId } ?: return@withTransaction
            val invItem = inventoryDao.getItemById(itemId)

            // 3. Compute differences
            val qtyDiff = newQuantity - pItem.quantity
            val oldItemTotal = pItem.quantity * pItem.unitPrice
            val newItemTotal = newQuantity * newUnitPrice
            val priceDiff = newItemTotal - oldItemTotal

            // 4. Update purchase item record
            val updatedPItem = pItem.copy(
                itemName = newItemName,
                quantity = newQuantity,
                unitPrice = newUnitPrice
            )
            supplierDao.insertPurchaseItem(updatedPItem)

            // 5. Update inventory item
            if (invItem != null) {
                val updatedInvItem = invItem.copy(
                    name = newItemName,
                    quantity = invItem.quantity + qtyDiff,
                    buyPrice = newUnitPrice,
                    itemNumber = newItemNumber
                )
                inventoryDao.updateItem(updatedInvItem)
            }

            // 6. Update parent purchase total amount and debt remaining
            val isSar = pItem.currency.contains("سعودي")
            val newTotalAmountYer = purchase.totalAmountYer + (if (isSar) 0.0 else priceDiff)
            val newTotalAmountSar = purchase.totalAmountSar + (if (isSar) priceDiff else 0.0)
            val newTotalAmount = newTotalAmountYer + newTotalAmountSar

            val newDebtYer = if (newTotalAmountYer > purchase.paidAmountYer) newTotalAmountYer - purchase.paidAmountYer else 0.0
            val newDebtSar = if (newTotalAmountSar > purchase.paidAmountSar) newTotalAmountSar - purchase.paidAmountSar else 0.0
            val newDebtRemaining = newDebtYer + newDebtSar

            val updatedPurchase = purchase.copy(
                totalAmount = newTotalAmount,
                debtRemaining = newDebtRemaining,
                totalAmountYer = newTotalAmountYer,
                totalAmountSar = newTotalAmountSar,
                debtRemainingYer = newDebtYer,
                debtRemainingSar = newDebtSar
            )
            supplierDao.insertPurchase(updatedPurchase)

            // 7. Update supplier outstanding balance
            val debtChange = newDebtRemaining - purchase.debtRemaining
            if (debtChange != 0.0) {
                supplierDao.updateSupplierBalance(purchase.supplierId, debtChange)
            }
        }
    }

    suspend fun editSupplierPurchaseFull(
        purchaseId: Int,
        newSupplierId: Int,
        newDate: String,
        newItemsPriceAndQty: List<Triple<InventoryItem, Double, Double>>,
        newPaidAmount: Double,
        newPaymentMethod: String,
        paidAmountYer: Double = 0.0,
        paidAmountSar: Double = 0.0,
        paymentMethodYer: String = "نقداً",
        paymentMethodSar: String = "نقداً"
    ) {
        db.withTransaction {
            val oldPurchase = supplierDao.getAllPurchases().firstOrNull()?.find { it.id == purchaseId } ?: return@withTransaction
            val oldItems = supplierDao.getPurchaseItemsDirect(purchaseId)
            
            // Revert old stock changes
            for (item in oldItems) {
                inventoryDao.updateQuantity(item.itemId, -item.quantity)
            }
            
            // Revert old debt from old supplier
            if (oldPurchase.debtRemaining > 0.0) {
                supplierDao.updateSupplierBalance(oldPurchase.supplierId, -oldPurchase.debtRemaining)
            }
            
            var totalBillYer = 0.0
            var totalBillSar = 0.0
            for (trip in newItemsPriceAndQty) {
                val stockItem = trip.first
                val qty = trip.second
                val buyPrice = trip.third
                val itemCurrency = (stockItem.buyPriceCurrency.ifEmpty { "ريال يمني" })
                if (itemCurrency.contains("سعودي")) {
                    totalBillSar += qty * buyPrice
                } else {
                    totalBillYer += qty * buyPrice
                }
            }

            val newTotalBill = totalBillYer + totalBillSar
            val isDual = totalBillYer > 0.0 && totalBillSar > 0.0

            var finalPaidYer = paidAmountYer
            var finalPaidSar = paidAmountSar

            // Compute YER paid based on its specific payment method
            if (totalBillYer > 0.0) {
                if (paymentMethodYer in listOf("نقداً", "تحويل", "إيداع")) {
                    finalPaidYer = totalBillYer
                } else if (paymentMethodYer == "آجل") {
                    finalPaidYer = 0.0
                }
            } else {
                finalPaidYer = 0.0
            }

            // Compute SAR paid based on its specific payment method
            if (totalBillSar > 0.0) {
                if (paymentMethodSar in listOf("نقداً", "تحويل", "إيداع")) {
                    finalPaidSar = totalBillSar
                } else if (paymentMethodSar == "آجل") {
                    finalPaidSar = 0.0
                }
            } else {
                finalPaidSar = 0.0
            }

            // Overrides if paid value is precisely supplied (such as in partial payment)
            if (paymentMethodYer == "دفعة جزئية" && paidAmountYer > 0.0) {
                finalPaidYer = paidAmountYer
            }
            if (paymentMethodSar == "دفعة جزئية" && paidAmountSar > 0.0) {
                finalPaidSar = paidAmountSar
            }

            // Fallbacks for legacy single-currency calls
            if (totalBillYer > 0.0 && totalBillSar == 0.0 && newPaidAmount > 0.0 && finalPaidYer == 0.0) {
                finalPaidYer = newPaidAmount
            }
            if (totalBillSar > 0.0 && totalBillYer == 0.0 && newPaidAmount > 0.0 && finalPaidSar == 0.0) {
                finalPaidSar = newPaidAmount
            }

            val debtYer = if (totalBillYer > finalPaidYer) totalBillYer - finalPaidYer else 0.0
            val debtSar = if (totalBillSar > finalPaidSar) totalBillSar - finalPaidSar else 0.0
            val debtTotal = debtYer + debtSar

            val inferredCurrency = if (isDual) "ريال يمني / ريال سعودي" else if (totalBillSar > 0.0) "ريال سعودي" else "ريال يمني"
            val calculatedPaymentMethod = if (totalBillYer > 0.0 && totalBillSar > 0.0) {
                "يمني: $paymentMethodYer | سعودي: $paymentMethodSar"
            } else if (totalBillSar > 0.0) {
                paymentMethodSar
            } else {
                paymentMethodYer
            }

            val updatedPurchase = oldPurchase.copy(
                supplierId = newSupplierId,
                date = newDate,
                totalAmount = newTotalBill,
                paidAmount = finalPaidYer + finalPaidSar,
                debtRemaining = debtTotal,
                paymentMethod = calculatedPaymentMethod,
                currency = inferredCurrency,
                totalAmountYer = totalBillYer,
                totalAmountSar = totalBillSar,
                paidAmountYer = finalPaidYer,
                paidAmountSar = finalPaidSar,
                debtRemainingYer = debtYer,
                debtRemainingSar = debtSar,
                paymentMethodYer = paymentMethodYer,
                paymentMethodSar = paymentMethodSar
            )
            supplierDao.insertPurchase(updatedPurchase)
            
            supplierDao.deletePurchaseItemsByPurchaseId(purchaseId)
            
            for (trip in newItemsPriceAndQty) {
                val stockItem = trip.first
                val qty = trip.second
                val buyPrice = trip.third
                val itemCurrency = if (stockItem.buyPriceCurrency.ifEmpty { "ريال يمني" }.contains("سعودي")) "ريال سعودي" else "ريال يمني"
                
                val pItem = SupplierPurchaseItem(
                    purchaseId = purchaseId,
                    itemId = stockItem.id,
                    itemName = stockItem.name,
                    quantity = qty,
                    unitPrice = buyPrice,
                    currency = itemCurrency
                )
                supplierDao.insertPurchaseItem(pItem)
                
                val existing = inventoryDao.getItemById(stockItem.id)
                if (existing != null) {
                    val newQuantity = existing.quantity + qty
                    val newBuyPrice = if (newQuantity > 0) {
                        ((existing.quantity * existing.buyPrice) + (qty * buyPrice)) / newQuantity
                    } else buyPrice
                    
                    inventoryDao.updateItem(
                        existing.copy(
                            name = stockItem.name,
                            quantity = newQuantity,
                            buyPrice = buyPrice, // Set direct buy price instead of average if preferred or keeping consistent
                            buyPriceCurrency = itemCurrency,
                            dateAdded = newDate.ifEmpty { existing.dateAdded.ifEmpty { Helpers.getCurrentDateTime() } },
                            itemNumber = if (stockItem.itemNumber.isNotEmpty()) stockItem.itemNumber else existing.itemNumber,
                            supplierId = if (newSupplierId != 0) newSupplierId else existing.supplierId,
                            supplierName = if (stockItem.supplierName.isNotEmpty()) stockItem.supplierName else existing.supplierName
                        )
                    )
                } else {
                    inventoryDao.insertItem(
                        InventoryItem(
                            id = stockItem.id,
                            name = stockItem.name,
                            quantity = qty,
                            buyPrice = buyPrice,
                            sellPrice = stockItem.sellPrice,
                            lowStockThreshold = stockItem.lowStockThreshold,
                            buyPriceCurrency = itemCurrency,
                            dateAdded = newDate.ifEmpty { Helpers.getCurrentDateTime() },
                            itemNumber = stockItem.itemNumber,
                            supplierId = stockItem.supplierId,
                            supplierName = stockItem.supplierName
                        )
                    )
                }
            }
            
            if (debtTotal > 0.0) {
                supplierDao.updateSupplierBalance(newSupplierId, debtTotal)
            }
        }
    }

    fun getPurchaseItems(purchaseId: Int): Flow<List<SupplierPurchaseItem>> {
        return supplierDao.getPurchaseItems(purchaseId)
    }

    // --- Expense Operations ---
    suspend fun addExpense(expense: Expense): Long {
        return expenseDao.insertExpense(expense)
    }

    suspend fun deleteExpense(expense: Expense) {
        expenseDao.deleteExpenseById(expense.id)
    }

    // --- Transfer Recipient Operations ---
    fun getTransfersByRecipientId(recipientId: Int): Flow<List<MoneyTransfer>> {
        return transferDao.getTransfersByRecipientId(recipientId)
    }

    suspend fun getAllRecipientsDirect(): List<TransferRecipientEntity> {
        return transferRecipientDao.getAllRecipientsDirect()
    }

    suspend fun getRecipientById(id: Int): TransferRecipientEntity? {
        return transferRecipientDao.getRecipientById(id)
    }

    suspend fun addTransferRecipient(
        name: String,
        phone: String = "",
        company: String = "",
        notes: String = ""
    ): TransferRecipientEntity {
        val cleanName = name.trim()
        val now = System.currentTimeMillis()
        val entity = TransferRecipientEntity(
            name = cleanName,
            phone = phone.trim(),
            company = company.trim(),
            notes = notes.trim(),
            isActive = true,
            createdAt = now,
            updatedAt = now
        )
        val insertedId = transferRecipientDao.insertRecipient(entity).toInt()
        return entity.copy(id = insertedId)
    }

    suspend fun updateTransferRecipient(recipient: TransferRecipientEntity) {
        val cleanName = recipient.name.trim()
        val updated = recipient.copy(
            name = cleanName,
            phone = recipient.phone.trim(),
            company = recipient.company.trim(),
            notes = recipient.notes.trim(),
            updatedAt = System.currentTimeMillis()
        )
        db.withTransaction {
            transferRecipientDao.updateRecipient(updated)
            if (cleanName.isNotEmpty() && updated.id > 0) {
                transferDao.updateReceiverNameForRecipient(updated.id, cleanName)
            }
        }
    }

    suspend fun setTransferRecipientActive(id: Int, isActive: Boolean) {
        transferRecipientDao.setRecipientActive(id, isActive, System.currentTimeMillis())
    }

    // --- Transfer Operations ---
    suspend fun addTransfer(transfer: MoneyTransfer): Long {
        return db.withTransaction {
            val cleanReceiver = transfer.receiver.trim()
            val resolvedRecipientId: Int
            val resolvedReceiverName: String

            if (transfer.recipientId > 0) {
                val existing = transferRecipientDao.getRecipientById(transfer.recipientId)
                resolvedRecipientId = existing?.id ?: transfer.recipientId
                resolvedReceiverName = existing?.name?.ifBlank { cleanReceiver } ?: cleanReceiver
            } else if (cleanReceiver.isNotEmpty()) {
                val existingByName = transferRecipientDao.getRecipientByExactName(cleanReceiver)
                if (existingByName != null) {
                    resolvedRecipientId = existingByName.id
                    resolvedReceiverName = existingByName.name
                } else {
                    val now = System.currentTimeMillis()
                    val newId = transferRecipientDao.insertRecipient(
                        TransferRecipientEntity(
                            name = cleanReceiver,
                            isActive = true,
                            createdAt = now,
                            updatedAt = now
                        )
                    ).toInt()
                    resolvedRecipientId = newId
                    resolvedReceiverName = cleanReceiver
                }
            } else {
                resolvedRecipientId = 0
                resolvedReceiverName = cleanReceiver
            }

            val cleanTransfer = transfer.copy(
                amount = com.example.util.MoneyMath.bd(transfer.amount)
                    .setScale(2, java.math.RoundingMode.HALF_UP)
                    .toDouble(),
                sender = transfer.sender.trim(),
                receiver = resolvedReceiverName,
                recipientId = resolvedRecipientId,
                currency = com.example.util.MoneyMath.canonicalCurrency(transfer.currency),
                status = transfer.status.ifBlank { "مكتملة" }
            )
            transferDao.insertTransfer(cleanTransfer)
        }
    }

    suspend fun deleteTransfer(transfer: MoneyTransfer) {
        transferDao.deleteTransferById(transfer.id)
    }

    suspend fun clearDatabaseDirectly() {
        db.withTransaction {
            db.clearAllTables()
        }
    }

    suspend fun generateStressTestDataDirectly(invoiceCount: Int, customerCount: Int) {
        db.withTransaction {
            // 1. Create default items in inventory
            val qatNames = listOf("عود ممتاز", "فراد درجة أولى")
            val itemsSaved = mutableListOf<InventoryItem>()
            for (i in qatNames.indices) {
                val item = InventoryItem(
                    id = i + 1,
                    name = qatNames[i],
                    quantity = 250.0 + (i * 50),
                    buyPrice = 4500.0 + (i * 500),
                    sellPrice = 6000.0 + (i * 700),
                    lowStockThreshold = 10.0,
                    qatType = "ممتاز",
                    notes = "صنف ممتاز للتوزيع والبيع اليومي المباشر فائق الجودة",
                    dateAdded = "2026-05-01 10:00"
                )
                inventoryDao.insertItem(item)
                itemsSaved.add(item)
            }

            // 2. Create default suppliers
            val supplierNames = listOf("المورد عادل الهصبري", "المورد جمال الشميري", "المورد غانم الذيفاني")
            val supplierIds = mutableListOf<Int>()
            for (i in supplierNames.indices) {
                val supId = supplierDao.insertSupplier(Supplier(id = i + 1, name = supplierNames[i], phone = "77000000$i", totalBalance = 150000.0 * i))
                supplierIds.add(supId.toInt())
            }

            // 3. Create customers
            val customerNamePrefixes = listOf("الشيخ صالح", "الحاج ردمان", "الأستاذ نبيل", "المقدم علي", "العميد سنان", "التاجر ياسر", "المشرف إبراهيم")
            val customerNameSuffixes = listOf("الصلوي", "الحيمي", "الصبري", "الهمداني", "اليريمي", "الذبحاني", "المقطري")
            val customerIds = mutableListOf<Int>()
            for (i in 1..customerCount) {
                val name = "${customerNamePrefixes[i % customerNamePrefixes.size]} ${customerNameSuffixes[(i + 3) % customerNameSuffixes.size]} ($i)"
                val phone = "77${(1000000..9999999).random()}"
                val debt = (10000..120000).random().toDouble()
                val custId = customerDao.insertCustomer(Customer(name = name, phone = phone, totalDebt = debt, address = "صنعاء - شارع تعز", notes = "عميل دائم مسجل في فحص الضغط"))
                customerIds.add(custId.toInt())
            }

            // 4. Create Invoices
            val paymentMethods = listOf("نقداً", "كريمي شيك", "حوالة نجم", "دين آجل")
            val baseDate = "2026-05-"
            for (i in 1..invoiceCount) {
                val day = String.format("%02d", (1..31).random())
                val hour = String.format("%02d", (8..21).random())
                val minute = String.format("%02d", (0..59).random())
                val invoiceDate = "$baseDate$day $hour:$minute"
                
                // Pick a random customer name
                val custName = if (i % 5 == 0) "عميل نقدي" else {
                    val randomPrefix = customerNamePrefixes.random()
                    val randomSuffix = customerNameSuffixes.random()
                    val randIdx = (1..customerCount).random()
                    "$randomPrefix $randomSuffix ($randIdx)"
                }

                // Random invoice items
                val numItems = (1..3).random()
                var totalAmount = 0.0
                val selectedItems = mutableListOf<SalesInvoiceItem>()
                for (j in 1..numItems) {
                    val item = itemsSaved.random()
                    val qty = (1..5).random().toDouble()
                    val itemTotal = qty * item.sellPrice
                    totalAmount += itemTotal
                }

                val discount = if (totalAmount > 10000 && i % 4 == 0) 500.0 else 0.0
                val netAmount = totalAmount - discount
                val payMethod = paymentMethods.random()
                val paidAmount = if (payMethod == "دين آجل") 0.0 else if (i % 3 == 0) netAmount / 2 else netAmount
                val debtAmount = netAmount - paidAmount

                val invoice = SalesInvoice(
                    customerName = custName,
                    date = invoiceDate,
                    totalAmount = netAmount,
                    taxApplied = netAmount * 0.01,
                    discount = discount,
                    paidAmount = paidAmount,
                    debtAmount = debtAmount,
                    paymentMethod = payMethod,
                    currency = "ريال يمني",
                    totalAmountYer = netAmount,
                    totalAmountSar = 0.0,
                    paidAmountYer = paidAmount,
                    paidAmountSar = 0.0,
                    debtAmountYer = debtAmount,
                    debtAmountSar = 0.0
                )
                val invoiceId = salesDao.insertInvoice(invoice).toInt()

                // Insert invoice items
                for (j in 1..numItems) {
                    val item = itemsSaved.random()
                    val qty = (1..5).random().toDouble()
                    val saleItem = SalesInvoiceItem(
                        invoiceId = invoiceId,
                        itemId = item.id,
                        itemName = item.name,
                        quantity = qty,
                        unitPrice = item.sellPrice,
                        buyPrice = item.buyPrice,
                        currency = "ريال يمني"
                    )
                    salesDao.insertInvoiceItem(saleItem)
                }
            }

            // 5. Create some Customer Payments
            for (i in 1..50) {
                val day = String.format("%02d", (1..31).random())
                val dateStr = "$baseDate$day 16:30"
                val randomCustId = if (customerIds.isNotEmpty()) customerIds.random() else 1
                customerDao.insertPayment(
                    CustomerPayment(
                        customerId = randomCustId,
                        amount = (5000..30000).random().toDouble(),
                        date = dateStr,
                        notes = "دفعة تحت الحساب أثناء اختبار الضغط والاستقرار للمحاسب المطور",
                        paymentMethod = paymentMethods.random()
                    )
                )
            }

            // 6. Create some Expenses
            val expenseCategories = listOf("إيجار المحل", "أجور عمال", "ضريبة بلديات", "تغذية ومصاريف", "كهرباء ومياه", "نقل وتوريد القات")
            for (i in 1..40) {
                val day = String.format("%02d", (1..31).random())
                val dateStr = "$baseDate$day 11:45"
                expenseDao.insertExpense(
                    Expense(
                        category = expenseCategories.random(),
                        amount = (2000..50000).random().toDouble(),
                        date = dateStr,
                        notes = "مصروف تشغيلي دوري مسجل تلقائياً"
                    )
                )
            }

            // 7. Create some Money Transfers
            val dynamicSender = agencyProfileDao.getProfileDirect()?.ownerName?.ifBlank { null } ?: "مندوب الوكالة"
            val receivers = listOf("صرافة النخبة", "شركة صرافة معتمدة", "حوالة إلى عميل")
            for (i in 1..30) {
                val day = String.format("%02d", (1..31).random())
                val dateStr = "$baseDate$day 14:15"
                transferDao.insertTransfer(
                    MoneyTransfer(
                        amount = (50000..800000).random().toDouble(),
                        sender = dynamicSender,
                        receiver = receivers.random(),
                        referenceNumber = "REF-${(100000..999999).random()}",
                        notes = "حوالة مالية روتينية يومية مقيدة للمطابقة",
                        date = dateStr
                    )
                )
            }
        }
    }
}
