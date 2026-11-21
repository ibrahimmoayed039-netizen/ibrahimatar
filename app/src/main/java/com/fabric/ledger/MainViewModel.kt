package com.fabric.ledger

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fabric.ledger.data.*
import com.fabric.ledger.ui.plain
import com.fabric.ledger.ui.qtyText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).dao()

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initial)

    val fabrics = dao.fabrics().state(emptyList())
    val customers = dao.customers().state(emptyList())
    val sales = dao.sales().state(emptyList())
    val payments = dao.payments().state(emptyList())
    val purchases = dao.purchases().state(emptyList())
    private val allItems = dao.allItems().state(emptyList())
    val supplierPayments = dao.supplierPayments().state(emptyList())

    /** لكل عملة: سعر البضاعة المشتراة، المسدَّد (وقت الشراء + دفعات المورّدين)، والمتبقي */
    val payables: StateFlow<Map<String, Payable>> = combine(purchases, supplierPayments) { p, sp ->
        listOf("IQD", "USD").associateWith { c ->
            val mine = p.filter { it.currency == c }
            Payable(mine.sumOf { it.total }, mine.sumOf { it.paid } + sp.filter { it.currency == c }.sumOf { it.amount })
        }
    }.state(emptyMap())

    /** لكل صنف: إجمالي ما اشتُري وإجمالي ما بيع */
    val totals: StateFlow<Map<Long, FabricTotals>> = combine(purchases, allItems) { p, i ->
        val bought = p.groupBy { it.fabricId }.mapValues { e -> e.value.sumOf { it.quantity } }
        val sold = i.groupBy { it.fabricId }.mapValues { e -> e.value.sumOf { it.quantity } }
        (bought.keys + sold.keys).associateWith { FabricTotals(bought[it] ?: 0.0, sold[it] ?: 0.0) }
    }.state(emptyMap())

    /** رصيد كل زبون = مجموع (الإجمالي - المدفوع) من الفواتير - الدفعات */
    val balances: StateFlow<Map<Long, Double>> =
        combine(sales, payments) { s, p ->
            val m = mutableMapOf<Long, Double>()
            s.filter { it.customerId > 0 }.forEach {
                m[it.customerId] = (m[it.customerId] ?: 0.0) + (it.total - it.paid)
            }
            p.forEach { m[it.customerId] = (m[it.customerId] ?: 0.0) - it.amount }
            m.toMap()
        }.state(emptyMap())

    // ---------- الإعدادات والدين المتأخر ----------
    private val settings = AppSettings(app)
    private val _grace = MutableStateFlow(settings.graceDays)
    private val _shop = MutableStateFlow(settings.shopName)
    private val _cc = MutableStateFlow(settings.countryCode)
    val graceDays: StateFlow<Int> = _grace
    val shopName: StateFlow<String> = _shop
    val countryCode: StateFlow<String> = _cc

    fun saveSettings(grace: Int, shop: String, cc: String) {
        settings.graceDays = grace; settings.shopName = shop; settings.countryCode = cc
        _grace.value = grace; _shop.value = shop; _cc.value = cc
    }

    val overdue: StateFlow<Map<Long, Overdue>> = combine(sales, payments, _grace) { s, p, g ->
        val byCustomer = p.groupBy { it.customerId }
        s.filter { it.customerId > 0 }.groupBy { it.customerId }
            .mapValues { (cid, list) -> Debt.overdue(list, byCustomer[cid] ?: emptyList(), g) }
    }.state(emptyMap())

    /** سطور كشف الحساب مرتبة من الأقدم إلى الأحدث */
    suspend fun statementRows(customerId: Long): List<StatementRow> {
        val items = dao.allItemsNow().groupBy { it.saleId }
        val saleRows = dao.salesNow().filter { it.customerId == customerId }.map { s ->
            val detail = (items[s.id] ?: emptyList()).joinToString("، ") {
                "${it.fabricName} ${qtyText(it.pieces, it.quantity, it.unit)} × ${it.unitPrice.plain()}"
            }
            StatementRow(s.date, "فاتورة", detail, s.total, s.paid)
        }
        val payRows = dao.paymentsNow().filter { it.customerId == customerId }.map {
            StatementRow(it.date, "دفعة", it.note, 0.0, it.amount)
        }
        return (saleRows + payRows).sortedBy { it.date }
    }

    fun itemsOf(saleId: Long) = dao.itemsOf(saleId)
    suspend fun saleNow(id: Long) = dao.saleById(id)
    suspend fun itemsNow(saleId: Long) = dao.itemsNow(saleId)
    suspend fun fabricsNow() = dao.fabricsNow()

    fun saveFabric(f: Fabric) { viewModelScope.launch { dao.upsertFabric(f) } }
    fun deleteFabric(f: Fabric, onDone: (Boolean) -> Unit = {}) = safeDelete(onDone) { dao.removeFabric(f) }
    fun saveSupplierPayment(p: SupplierPayment, onDone: (Boolean) -> Unit = {}) = safeDelete(onDone) { dao.upsertSupplierPayment(p) }
    fun deleteSupplierPayment(p: SupplierPayment, onDone: (Boolean) -> Unit = {}) = safeDelete(onDone) { dao.deleteSupplierPayment(p) }
    fun addPurchase(p: Purchase, onDone: (Boolean) -> Unit = {}) = safeDelete(onDone) { dao.addPurchase(p) }
    fun editPurchase(p: Purchase, onDone: (Boolean) -> Unit = {}) = safeDelete(onDone) { dao.editPurchase(p) }
    fun deletePurchase(id: Long, onDone: (Boolean) -> Unit = {}) = safeDelete(onDone) { dao.removePurchase(id) }
    fun saveCustomer(c: Customer) { viewModelScope.launch { dao.upsertCustomer(c) } }

    fun addPayment(customerId: Long, amount: Double, note: String) {
        viewModelScope.launch {
            dao.upsertPayment(Payment(customerId = customerId, date = System.currentTimeMillis(), amount = amount, note = note))
        }
    }
    fun savePayment(p: Payment) { viewModelScope.launch { dao.upsertPayment(p) } }
    /** كل عمليات الحذف تُنفَّذ خارج الخيط الرئيسي داخل try/catch وتُبلغ بالنتيجة */
    private fun safeDelete(onDone: (Boolean) -> Unit, block: suspend () -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { block() }.isSuccess }
            onDone(ok)
        }
    }
    fun deletePayment(p: Payment, onDone: (Boolean) -> Unit = {}) = safeDelete(onDone) { dao.deletePayment(p) }

    fun saveSale(sale: Sale, items: List<SaleItem>, onDone: () -> Unit) {
        viewModelScope.launch { dao.saveSale(sale, items); onDone() }
    }
    fun updateSale(sale: Sale, items: List<SaleItem>, onDone: () -> Unit) {
        viewModelScope.launch { dao.updateSale(sale, items); onDone() }
    }
    fun deleteSale(id: Long, onDone: (Boolean) -> Unit = {}) = safeDelete(onDone) { dao.deleteSale(id) }

    // ---------- النسخ الاحتياطي ----------
    fun exportTo(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val json = Backup.toJson(dao.snapshot())
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use {
                        it.write(json.toByteArray(Charsets.UTF_8))
                    }
                }.isSuccess
            }
            onResult(ok)
        }
    }

    fun importFrom(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val text = getApplication<Application>().contentResolver.openInputStream(uri)!!.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    }
                    dao.restoreAll(Backup.fromJson(text))
                }.isSuccess
            }
            onResult(ok)
        }
    }
}
