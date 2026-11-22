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

    /** رصيد كل زبون لكل عملة على حدة (لا تحويل بين الدينار والدولار) = مجموع (الإجمالي - المدفوع) - الدفعات */
    val balances: StateFlow<Map<Long, Map<String, Double>>> =
        combine(sales, payments) { s, p ->
            val m = mutableMapOf<Long, MutableMap<String, Double>>()
            s.filter { it.customerId > 0 }.forEach {
                val c = m.getOrPut(it.customerId) { mutableMapOf() }
                c[it.currency] = (c[it.currency] ?: 0.0) + (it.total - it.paid)
            }
            p.forEach {
                val c = m.getOrPut(it.customerId) { mutableMapOf() }
                c[it.currency] = (c[it.currency] ?: 0.0) - it.amount
            }
            m.mapValues { e -> e.value.toMap() }
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

    // ---------- قفل التطبيق ----------
    private val _lockEnabled = MutableStateFlow(settings.hasPin)
    private val _locked = MutableStateFlow(settings.hasPin)   // يبدأ مقفلاً مع كل تشغيل جديد
    val lockEnabled: StateFlow<Boolean> = _lockEnabled
    val locked: StateFlow<Boolean> = _locked
    fun pinLength(): Int = settings.pinLen
    fun unlock(pin: String): Boolean = settings.checkPin(pin).also { if (it) _locked.value = false }
    fun lockIfEnabled() { if (_lockEnabled.value) _locked.value = true }
    fun enableLock(pin: String) { settings.setPin(pin); _lockEnabled.value = true }
    fun changePin(old: String, new: String): Boolean =
        settings.checkPin(old).also { if (it) settings.setPin(new) }
    fun disableLock(current: String): Boolean =
        settings.checkPin(current).also { if (it) { settings.clearPin(); _lockEnabled.value = false; _locked.value = false } }

    val overdue: StateFlow<Map<Long, Map<String, Overdue>>> = combine(sales, payments, _grace) { s, p, g ->
        val byCustomer = p.groupBy { it.customerId }
        s.filter { it.customerId > 0 }.groupBy { it.customerId }
            .mapValues { (cid, list) ->
                list.groupBy { it.currency }.mapValues { (cur, sl) ->
                    Debt.overdue(sl, (byCustomer[cid] ?: emptyList()).filter { it.currency == cur }, g)
                }
            }
    }.state(emptyMap())

    /** سطور كشف الحساب مرتبة من الأقدم إلى الأحدث */
    suspend fun statementRows(customerId: Long): List<StatementRow> {
        val items = dao.allItemsNow().groupBy { it.saleId }
        val saleRows = dao.salesNow().filter { it.customerId == customerId }.map { s ->
            val list = items[s.id] ?: emptyList()
            val detail = if (list.isEmpty()) (if (s.note.isBlank()) "قائمة بمبلغ فقط" else "قائمة: ${s.note}")
            else list.joinToString("، ") {
                "${it.fabricName} ${qtyText(it.pieces, it.quantity, it.unit)} × ${it.unitPrice.plain()}"
            }
            StatementRow(s.date, "فاتورة", detail, s.total, s.paid, s.currency)
        }
        val payRows = dao.paymentsNow().filter { it.customerId == customerId }.map {
            StatementRow(it.date, "دفعة", it.note, 0.0, it.amount, it.currency)
        }
        return (saleRows + payRows).sortedBy { it.date }
    }

    fun itemsOf(saleId: Long) = dao.itemsOf(saleId)
    suspend fun saleNow(id: Long) = dao.saleById(id)
    suspend fun itemsNow(saleId: Long) = dao.itemsNow(saleId)
    suspend fun fabricsNow() = dao.fabricsNow()

    /** رسائل عامة تُعرض كـ Toast (مثل فشل الحفظ) */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    /** حفظ آمن: أي خطأ في القاعدة يظهر كرسالة بدل إغلاق التطبيق */
    private fun safeSave(block: suspend () -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { block() }.isSuccess }
            if (!ok) _messages.tryEmit("تعذر الحفظ، حاول مجدداً")
        }
    }

    fun saveFabric(f: Fabric) = safeSave { dao.upsertFabric(f) }
    fun deleteFabric(f: Fabric, onDone: (Boolean) -> Unit = {}) = safeDelete(onDone) {
        dao.removeFabric(f)
        com.fabric.ledger.ui.deleteFabricImage(f.imagePath)
    }
    fun saveSupplierPayment(p: SupplierPayment, onDone: (Boolean) -> Unit = {}) = safeDelete(onDone) { dao.upsertSupplierPayment(p) }
    fun deleteSupplierPayment(p: SupplierPayment, onDone: (Boolean) -> Unit = {}) = safeDelete(onDone) { dao.deleteSupplierPayment(p) }
    fun addPurchase(p: Purchase, onDone: (Boolean) -> Unit = {}) = safeDelete(onDone) { dao.addPurchase(p) }
    fun editPurchase(p: Purchase, onDone: (Boolean) -> Unit = {}) = safeDelete(onDone) { dao.editPurchase(p) }
    fun deletePurchase(id: Long, onDone: (Boolean) -> Unit = {}) = safeDelete(onDone) { dao.removePurchase(id) }
    /** فتح n بالة من صنف؛ يُرجع عدد البالات التي فُتحت فعلاً */
    fun openBales(id: Long, n: Double, onDone: (Double) -> Unit = {}) {
        viewModelScope.launch {
            val k = withContext(Dispatchers.IO) { runCatching { dao.openBales(id, n) }.getOrDefault(0.0) }
            onDone(k)
        }
    }
    fun saveCustomer(c: Customer) = safeSave { dao.upsertCustomer(c) }

    fun addPayment(customerId: Long, amount: Double, note: String, currency: String = "IQD") = safeSave {
        dao.upsertPayment(Payment(customerId = customerId, date = System.currentTimeMillis(), amount = amount, note = note, currency = currency))
    }
    fun savePayment(p: Payment) = safeSave { dao.upsertPayment(p) }
    /** كل عمليات الحذف تُنفَّذ خارج الخيط الرئيسي داخل try/catch وتُبلغ بالنتيجة */
    private fun safeDelete(onDone: (Boolean) -> Unit, block: suspend () -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { block() }.isSuccess }
            onDone(ok)
        }
    }
    fun deletePayment(p: Payment, onDone: (Boolean) -> Unit = {}) = safeDelete(onDone) { dao.deletePayment(p) }

    fun saveSale(sale: Sale, items: List<SaleItem>, onDone: (Boolean) -> Unit) = safeDelete(onDone) { dao.saveSale(sale, items) }
    fun updateSale(sale: Sale, items: List<SaleItem>, onDone: (Boolean) -> Unit) = safeDelete(onDone) { dao.updateSale(sale, items) }
    fun deleteSale(id: Long, onDone: (Boolean) -> Unit = {}) = safeDelete(onDone) { dao.deleteSale(id) }

    // ---------- النسخ الاحتياطي (ملف zip: البيانات + صور الأصناف) ----------
    fun exportTo(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val data = dao.snapshot()
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use { BackupFiles.write(it, data) }
                }.isSuccess
            }
            onResult(ok)
        }
    }

    fun importFrom(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val ctx = getApplication<Application>()
                    // القراءة والتحقق أولاً؛ لا يُمسّ شيء من البيانات الحالية إن كان الملف تالفاً
                    val data = ctx.contentResolver.openInputStream(uri)!!.use { BackupFiles.read(ctx, it) }
                    dao.restoreAll(data)
                    BackupFiles.cleanupImages(ctx, data.fabrics.map { it.imagePath })
                }.isSuccess
            }
            onResult(ok)
        }
    }
}
