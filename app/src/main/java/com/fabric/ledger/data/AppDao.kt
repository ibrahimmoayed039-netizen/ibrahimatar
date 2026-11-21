package com.fabric.ledger.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AppDao {
    // ---------- الأصناف ----------
    @Query("SELECT * FROM fabrics ORDER BY name")
    abstract fun fabrics(): Flow<List<Fabric>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertFabric(f: Fabric)
    @Delete
    abstract suspend fun deleteFabric(f: Fabric)
    @Query("SELECT * FROM fabrics")
    abstract suspend fun fabricsNow(): List<Fabric>
    @Query("UPDATE fabrics SET stock = stock - :qty WHERE id = :id")
    abstract suspend fun reduceStock(id: Long, qty: Double)
    @Query("UPDATE fabrics SET stock = stock + :qty WHERE id = :id")
    abstract suspend fun addStock(id: Long, qty: Double)

    // ---------- المشتريات ----------
    @Query("SELECT * FROM purchases ORDER BY date DESC")
    abstract fun purchases(): Flow<List<Purchase>>
    @Query("SELECT * FROM sale_items")
    abstract fun allItems(): Flow<List<SaleItem>>
    @Query("SELECT * FROM purchases WHERE id = :id")
    abstract suspend fun purchaseById(id: Long): Purchase?
    @Insert
    abstract suspend fun insertPurchase(p: Purchase): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertPurchase(p: Purchase)
    @Query("DELETE FROM purchases WHERE id = :id")
    abstract suspend fun deletePurchaseRow(id: Long)
    @Query("DELETE FROM purchases WHERE fabricId = :fabricId")
    abstract suspend fun deletePurchasesOf(fabricId: Long)

    /** تسجيل شراء: يُضاف إلى المخزون */
    @Transaction
    open suspend fun addPurchase(p: Purchase) {
        insertPurchase(p)
        addStock(p.fabricId, p.quantity)
    }

    /** تعديل شراء: تُسترجع الكمية القديمة ثم تُضاف الجديدة */
    @Transaction
    open suspend fun editPurchase(p: Purchase) {
        purchaseById(p.id)?.let { addStock(it.fabricId, -it.quantity) }
        upsertPurchase(p)
        addStock(p.fabricId, p.quantity)
    }

    /** حذف شراء: تُخصم كميته من المخزون */
    @Transaction
    open suspend fun removePurchase(id: Long) {
        val old = purchaseById(id) ?: return
        addStock(old.fabricId, -old.quantity)
        deletePurchaseRow(id)
    }

    /** حذف صنف مع سجل مشترياته */
    @Transaction
    open suspend fun removeFabric(f: Fabric) {
        deletePurchasesOf(f.id)
        deleteFabric(f)
    }

    // ---------- دفعات المورّدين ----------
    @Query("SELECT * FROM supplier_payments ORDER BY date DESC")
    abstract fun supplierPayments(): Flow<List<SupplierPayment>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertSupplierPayment(p: SupplierPayment)
    @Delete
    abstract suspend fun deleteSupplierPayment(p: SupplierPayment)
    @Query("SELECT * FROM supplier_payments") abstract suspend fun supplierPaymentsNow(): List<SupplierPayment>
    @Query("DELETE FROM supplier_payments") abstract suspend fun clearSupplierPayments()
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertSupplierPayments(l: List<SupplierPayment>)

    // ---------- الزبائن ----------
    @Query("SELECT * FROM customers ORDER BY name")
    abstract fun customers(): Flow<List<Customer>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertCustomer(c: Customer)

    // ---------- الفواتير ----------
    @Query("SELECT * FROM sales ORDER BY date DESC")
    abstract fun sales(): Flow<List<Sale>>
    @Query("SELECT * FROM sales WHERE id = :id")
    abstract suspend fun saleById(id: Long): Sale?
    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    abstract fun itemsOf(saleId: Long): Flow<List<SaleItem>>
    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    abstract suspend fun itemsNow(saleId: Long): List<SaleItem>
    @Insert
    abstract suspend fun insertSale(s: Sale): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertSale(s: Sale)
    @Insert
    abstract suspend fun insertItems(items: List<SaleItem>)
    @Query("DELETE FROM sale_items WHERE saleId = :saleId")
    abstract suspend fun deleteItemsOf(saleId: Long)
    @Query("DELETE FROM sales WHERE id = :saleId")
    abstract suspend fun deleteSaleRow(saleId: Long)

    @Transaction
    open suspend fun saveSale(sale: Sale, items: List<SaleItem>) {
        val saleId = insertSale(sale)
        insertItems(items.map { it.copy(saleId = saleId) })
        items.forEach { reduceStock(it.fabricId, it.quantity) }
    }

    /** حذف فاتورة: تعود كمياتها إلى المخزن */
    @Transaction
    open suspend fun deleteSale(saleId: Long) {
        itemsNow(saleId).forEach { addStock(it.fabricId, it.quantity) }
        deleteItemsOf(saleId)
        deleteSaleRow(saleId)
    }

    /** تعديل فاتورة: إرجاع كميات القديمة ثم خصم كميات الجديدة */
    @Transaction
    open suspend fun updateSale(sale: Sale, items: List<SaleItem>) {
        itemsNow(sale.id).forEach { addStock(it.fabricId, it.quantity) }
        deleteItemsOf(sale.id)
        upsertSale(sale)
        insertItems(items.map { it.copy(saleId = sale.id) })
        items.forEach { reduceStock(it.fabricId, it.quantity) }
    }

    // ---------- الدفعات ----------
    @Query("SELECT * FROM payments ORDER BY date DESC")
    abstract fun payments(): Flow<List<Payment>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertPayment(p: Payment)
    @Delete
    abstract suspend fun deletePayment(p: Payment)

    // ---------- النسخ الاحتياطي ----------
    @Query("SELECT * FROM customers")
    abstract suspend fun customersNow(): List<Customer>
    @Query("SELECT * FROM sales")
    abstract suspend fun salesNow(): List<Sale>
    @Query("SELECT * FROM sale_items")
    abstract suspend fun allItemsNow(): List<SaleItem>
    @Query("SELECT * FROM payments")
    abstract suspend fun paymentsNow(): List<Payment>

    @Query("DELETE FROM fabrics") abstract suspend fun clearFabrics()
    @Query("DELETE FROM customers") abstract suspend fun clearCustomers()
    @Query("DELETE FROM sales") abstract suspend fun clearSales()
    @Query("DELETE FROM sale_items") abstract suspend fun clearItems()
    @Query("DELETE FROM payments") abstract suspend fun clearPayments()
    @Query("DELETE FROM purchases") abstract suspend fun clearPurchases()
    @Query("SELECT * FROM purchases") abstract suspend fun purchasesNow(): List<Purchase>
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertPurchases(l: List<Purchase>)

    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertFabrics(l: List<Fabric>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertCustomers(l: List<Customer>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertSales(l: List<Sale>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertPayments(l: List<Payment>)

    @Transaction
    open suspend fun snapshot(): BackupData =
        BackupData(fabricsNow(), customersNow(), salesNow(), allItemsNow(), paymentsNow(), purchasesNow(), supplierPaymentsNow())

    @Transaction
    open suspend fun restoreAll(b: BackupData) {
        clearItems(); clearSales(); clearPayments(); clearPurchases(); clearSupplierPayments(); clearCustomers(); clearFabrics()
        insertFabrics(b.fabrics)
        insertCustomers(b.customers)
        insertSales(b.sales)
        insertItems(b.items)
        insertPayments(b.payments)
        insertPurchases(b.purchases)
        insertSupplierPayments(b.supplierPayments)
    }
}
