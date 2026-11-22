package com.fabric.ledger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fabric.ledger.MainViewModel
import com.fabric.ledger.data.Fabric
import com.fabric.ledger.data.Payable
import com.fabric.ledger.data.Purchase
import com.fabric.ledger.data.Sale
import com.fabric.ledger.data.SupplierPayment
import androidx.compose.material.icons.automirrored.filled.List
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** صف في دفتر المشتريات: عملية شراء أو دفعة للمورّد */
private sealed class LedgerRow(val date: Long, val key: String) {
    class P(val p: Purchase) : LedgerRow(p.date, "p${p.id}")
    class S(val s: SupplierPayment) : LedgerRow(s.date, "s${s.id}")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: MainViewModel, onOpenCustomer: (Long) -> Unit, onNewSale: () -> Unit,
    onEditSale: (Long) -> Unit, onOpenFabric: (Long) -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    val fabrics by vm.fabrics.collectAsStateWithLifecycle()
    val customers by vm.customers.collectAsStateWithLifecycle()
    val sales by vm.sales.collectAsStateWithLifecycle()
    val balances by vm.balances.collectAsStateWithLifecycle()
    val overdue by vm.overdue.collectAsStateWithLifecycle()
    val totals by vm.totals.collectAsStateWithLifecycle()
    val purchases by vm.purchases.collectAsStateWithLifecycle()
    val supplierPayments by vm.supplierPayments.collectAsStateWithLifecycle()
    val payables by vm.payables.collectAsStateWithLifecycle()
    val grace by vm.graceDays.collectAsStateWithLifecycle()
    val shop by vm.shopName.collectAsStateWithLifecycle()
    val cc by vm.countryCode.collectAsStateWithLifecycle()
    var settingsDlg by remember { mutableStateOf(false) }

    var fabricDlg by remember { mutableStateOf(false) }
    var fabricEdit by remember { mutableStateOf<Fabric?>(null) }
    var customerDlg by remember { mutableStateOf(false) }
    var saleView by remember { mutableStateOf<Sale?>(null) }
    var purchaseDlg by remember { mutableStateOf(false) }
    var editPurchase by remember { mutableStateOf<Purchase?>(null) }
    var supPayDlg by remember { mutableStateOf(false) }
    var editSupPay by remember { mutableStateOf<SupplierPayment?>(null) }

    var custQuery by remember { mutableStateOf("") }
    var custFilter by remember { mutableIntStateOf(0) }
    var fabQuery by remember { mutableStateOf("") }
    var fabFilter by remember { mutableIntStateOf(0) }

    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportTo(uri) { ok ->
            Toast.makeText(context, if (ok) "تم حفظ النسخة الاحتياطية" else "فشل حفظ النسخة", Toast.LENGTH_LONG).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestore = uri
    }

    val tabs = listOf("الزبائن" to Icons.Default.Person, "الأصناف" to Icons.Default.Menu, "المبيعات" to Icons.Default.ShoppingCart,
        "المشتريات" to Icons.AutoMirrored.Filled.List)

    Scaffold(
        topBar = {
            GradientTopBar(
                title = { Text("دفتر الأقمشة") },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "المزيد") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("حفظ نسخة احتياطية") },
                                onClick = {
                                    menu = false
                                    val day = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                                    exportLauncher.launch("fabric-ledger-backup-$day.json")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("استرجاع من نسخة") },
                                onClick = { menu = false; importLauncher.launch(arrayOf("*/*")) }
                            )
                            DropdownMenuItem(
                                text = { Text("إعدادات الكشف والتذكير") },
                                onClick = { menu = false; settingsDlg = true }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.shadow(18.dp, NavShape).clip(NavShape),
                containerColor = Color.White
            ) {
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Icon(icon, contentDescription = null) }, label = { Text(label) }
                    )
                }
            }
        },
        floatingActionButton = {
            when (tab) {
                0 -> FloatingActionButton(containerColor = Copper, contentColor = Color.White, onClick = { customerDlg = true }) { Icon(Icons.Default.Add, "إضافة زبون") }
                1 -> FloatingActionButton(containerColor = Copper, contentColor = Color.White, onClick = { fabricEdit = null; fabricDlg = true }) { Icon(Icons.Default.Add, "إضافة صنف") }
                2 -> ExtendedFloatingActionButton(
                    containerColor = Copper, contentColor = Color.White,
                    onClick = onNewSale, icon = { Icon(Icons.Default.Add, null) }, text = { Text("بيع جديد") }
                )
                else -> ExtendedFloatingActionButton(
                    containerColor = Copper, contentColor = Color.White,
                    onClick = { purchaseDlg = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("تسجيل شراء") }
                )
            }
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val low = fabrics.filter { it.isLow() }
            if (low.isNotEmpty()) item {
                Card3D(
                    onClick = { tab = 1 },
                    modifier = Modifier.fillMaxWidth(),
                    tint = MaterialTheme.colorScheme.errorContainer
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("⚠ تنبيه مخزون: ${low.size} صنف وصل لحد النقص", fontWeight = FontWeight.Bold)
                        Text(
                            low.take(3).joinToString("، ") { it.name } + if (low.size > 3) " ..." else "",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            when (tab) {
                0 -> {
                    val debtByCur = CURRENCIES.associateWith { c -> balances.values.sumOf { (it[c] ?: 0.0).coerceAtLeast(0.0) } }
                    item {
                        HeroCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text("إجمالي الديون على الزبائن")
                                BalanceLines(debtByCur.filterValues { it > 0.005 })
                            }
                        }
                    }
                    item(key = "search_customers") {
                        SearchFilterBar(
                            custQuery, { custQuery = it }, "ابحث بالاسم أو رقم الهاتف",
                            listOf("الكل", "عليهم دين", "جملة", "متأخرون"), custFilter, { custFilter = it }
                        )
                    }
                    val shownCustomers = customers.filter { c ->
                        matchesQuery(custQuery, c.name, c.phone) && when (custFilter) {
                            1 -> balances[c.id].hasDebt()
                            2 -> c.isWholesale
                            3 -> overdue[c.id]?.values?.any { it.amount > 0.005 } == true
                            else -> true
                        }
                    }
                    if (customers.isEmpty()) item { Text("لا يوجد زبائن بعد. اضغط + للإضافة.") }
                    else if (shownCustomers.isEmpty()) item { Text("لا توجد نتائج مطابقة.") }
                    items(shownCustomers, key = { it.id }) { c ->
                        val b = balances[c.id]
                        Card3D(onClick = { onOpenCustomer(c.id) }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    val sub = listOfNotNull(c.phone.ifBlank { null }, if (c.isWholesale) "جملة" else null).joinToString(" • ")
                                    if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall)
                                    val odTxt = overdue[c.id].overdueText()
                                    if (odTxt != null) {
                                        Text("⚠ متأخر: $odTxt", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Text(
                                    b.balanceText(), fontWeight = FontWeight.Bold,
                                    color = if (b.hasDebt()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                1 -> {
                    item(key = "search_fabrics") {
                        SearchFilterBar(
                            fabQuery, { fabQuery = it }, "ابحث بالاسم أو النوع أو اللون",
                            listOf("الكل", "منخفض المخزون", "نفد"), fabFilter, { fabFilter = it }
                        )
                    }
                    val shownFabrics = fabrics.filter { f ->
                        matchesQuery(fabQuery, f.name, f.type, f.color) && when (fabFilter) {
                            1 -> f.isLow()
                            2 -> f.stock <= 0
                            else -> true
                        }
                    }
                    if (fabrics.isEmpty()) item { Text("لا توجد أصناف بعد. اضغط + للإضافة.") }
                    else if (shownFabrics.isEmpty()) item { Text("لا توجد نتائج مطابقة.") }
                    shownFabrics.groupBy { it.type.trim().ifBlank { "بدون تصنيف" } }.toSortedMap().forEach { (cat, grp) ->
                    item(key = "cat_$cat") { CategoryHeader(cat, grp, totals) }
                    items(grp, key = { it.id }) { f ->
                        Card3D(onClick = { onOpenFabric(f.id) }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FabricThumb(f.imagePath, f.name, 72.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(f.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                val sub = listOf(f.type, f.color).filter { it.isNotBlank() }.joinToString(" • ")
                                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "المخزون: ${f.stock.plain()} ${f.unit}" + (f.stockPiecesText()?.let { "  ($it)" } ?: "") + if (f.isLow()) "  ⚠ منخفض" else "",
                                    color = if (f.isLow()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (f.isLow()) FontWeight.Bold else FontWeight.Normal
                                )
                                Text("مشترى: ${(totals[f.id]?.purchased ?: 0.0).plain()}     مباع: ${(totals[f.id]?.sold ?: 0.0).plain()} ${f.unit}", style = MaterialTheme.typography.bodySmall)
                                if (f.supportsPieces() && f.rollLength > 0) Text("طول القطعة الكاملة: ${f.rollLength.plain()} متر", style = MaterialTheme.typography.bodySmall)
                                if (f.minStock > 0) Text("حد التنبيه: ${f.minStock.plain()} ${f.unit}", style = MaterialTheme.typography.bodySmall)
                                Text("مفرق: ${f.retailPrice.money("IQD")}    جملة: ${f.wholesalePrice.money("IQD")}")
                                if (f.retailPriceUsd > 0 || f.wholesalePriceUsd > 0) Text("مفرق: ${f.retailPriceUsd.money("USD")}    جملة: ${f.wholesalePriceUsd.money("USD")}")
                            }
                            }
                        }
                    }
                    }
                }
                2 -> {
                    if (sales.isEmpty()) item { Text("لا توجد مبيعات بعد.") }
                    items(sales, key = { it.id }) { s ->
                        val name = if (s.customerId == 0L) "بيع نقدي" else customers.find { it.id == s.customerId }?.name ?: "—"
                        Card3D(onClick = { saleView = s }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(name, fontWeight = FontWeight.Bold)
                                    Text(s.date.dateStr(), style = MaterialTheme.typography.bodySmall)
                                    if (s.note.isNotBlank()) Text(s.note, style = MaterialTheme.typography.bodySmall)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(s.total.money(s.currency), fontWeight = FontWeight.Bold)
                                    val rem = s.total - s.paid
                                    if (rem > 0.005) Text("متبقي ${rem.money(s.currency)}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                else -> {
                    // ---------- المشتريات: سعر البضاعة، المسدَّد، والمحصلة لكل عملة ----------
                    CURRENCIES.filter { c -> (payables[c]?.total ?: 0.0) > 0.005 || (payables[c]?.paid ?: 0.0) > 0.005 }.forEach { c ->
                        val s = payables[c] ?: Payable(0.0, 0.0)
                        item(key = "payable_$c") {
                            HeroCard(Modifier.fillMaxWidth()) {
                                Text("مشتريات بال${currencyName(c)}", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("سعر البضاعة المشتراة"); Text(s.total.money(c), fontWeight = FontWeight.Bold)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("المسدَّد منها"); Text(s.paid.money(c), fontWeight = FontWeight.Bold)
                                }
                                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.4f))
                                Text("المحصلة: المتبقي للمورّدين")
                                Text(
                                    s.remaining.money(c), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                                    color = if (s.remaining > 0.005) Color(0xFFFFE082) else Color(0xFFA5D6A7)
                                )
                            }
                        }
                    }
                    item(key = "supplier_pay_btn") {
                        Button3D(onClick = { supPayDlg = true }, modifier = Modifier.fillMaxWidth()) { Text("تسجيل دفعة للمورّد") }
                    }
                    val rows = (purchases.map { LedgerRow.P(it) } + supplierPayments.map { LedgerRow.S(it) }).sortedByDescending { it.date }
                    if (rows.isEmpty()) item { Text("لا توجد مشتريات بعد. اضغط «تسجيل شراء» لإضافة أول عملية.") }
                    items(rows, key = { it.key }) { r ->
                        when (r) {
                            is LedgerRow.P -> {
                                val p = r.p
                                val f = fabrics.find { it.id == p.fabricId }
                                Card3D(onClick = { editPurchase = p }, modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(f?.name ?: "صنف محذوف", fontWeight = FontWeight.Bold)
                                            Text(
                                                qtyText(p.pieces, p.quantity, f?.unit ?: "", p.bales, p.baleType) + if (p.supplier.isNotBlank()) "  •  ${p.supplier}" else "",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Text(p.date.dateStr(), style = MaterialTheme.typography.bodySmall)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(p.total.money(p.currency), fontWeight = FontWeight.Bold)
                                            if (p.paid > 0.005) Text("مسدَّد ${p.paid.money(p.currency)}", style = MaterialTheme.typography.bodySmall)
                                            val rem = p.total - p.paid
                                            if (rem > 0.005) Text("متبقي ${rem.money(p.currency)}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                            is LedgerRow.S -> {
                                val sp = r.s
                                Card3D(onClick = { editSupPay = sp }, modifier = Modifier.fillMaxWidth(), tint = MaterialTheme.colorScheme.secondaryContainer) {
                                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("دفعة للمورّد", fontWeight = FontWeight.Bold)
                                            if (sp.supplier.isNotBlank()) Text(sp.supplier, style = MaterialTheme.typography.bodySmall)
                                            Text(sp.date.dateStr(), style = MaterialTheme.typography.bodySmall)
                                            if (sp.note.isNotBlank()) Text(sp.note, style = MaterialTheme.typography.bodySmall)
                                        }
                                        Text("- ${sp.amount.money(sp.currency)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun done(ok: Boolean, msg: String) =
        Toast.makeText(context, if (ok) msg else "تعذرت العملية", Toast.LENGTH_SHORT).show()
    if (purchaseDlg) PurchaseDialog(
        initial = null, fabrics = fabrics, presetFabricId = -1L,
        onSave = { np -> purchaseDlg = false; vm.addPurchase(np) { ok -> done(ok, "تم تسجيل الشراء وإضافته للمخزون") } },
        onDelete = null, onDismiss = { purchaseDlg = false }
    )
    editPurchase?.let { p ->
        PurchaseDialog(
            initial = p, fabrics = fabrics, presetFabricId = p.fabricId,
            onSave = { np -> editPurchase = null; vm.editPurchase(np) { ok -> done(ok, "تم التعديل") } },
            onDelete = { editPurchase = null; vm.deletePurchase(p.id) { ok -> done(ok, "تم الحذف") } },
            onDismiss = { editPurchase = null }
        )
    }
    if (supPayDlg) SupplierPaymentDialog(
        initial = null,
        onSave = { np -> supPayDlg = false; vm.saveSupplierPayment(np) { ok -> done(ok, "تم تسجيل الدفعة") } },
        onDelete = null, onDismiss = { supPayDlg = false }
    )
    editSupPay?.let { sp ->
        SupplierPaymentDialog(
            initial = sp,
            onSave = { np -> editSupPay = null; vm.saveSupplierPayment(np) { ok -> done(ok, "تم التعديل") } },
            onDelete = { editSupPay = null; vm.deleteSupplierPayment(sp) { ok -> done(ok, "تم الحذف") } },
            onDismiss = { editSupPay = null }
        )
    }

    if (fabricDlg) FabricDialog(
        initial = fabricEdit,
        onSave = { vm.saveFabric(it); fabricDlg = false },
        onDelete = fabricEdit?.let { f -> { fabricDlg = false; vm.deleteFabric(f) { ok -> Toast.makeText(context, if (ok) "تم الحذف" else "تعذر الحذف", Toast.LENGTH_SHORT).show() } } },
        onDismiss = { fabricDlg = false }
    )
    if (customerDlg) CustomerDialog(onSave = { vm.saveCustomer(it); customerDlg = false }, onDismiss = { customerDlg = false })
    if (settingsDlg) SettingsDialog(
        initialGrace = grace, initialShop = shop, initialCc = cc,
        onSave = { g, sh, c -> vm.saveSettings(g, sh, c); settingsDlg = false },
        onDismiss = { settingsDlg = false }
    )
    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("استرجاع نسخة احتياطية") },
            text = { Text("سيتم حذف كل البيانات الحالية واستبدالها بمحتوى النسخة. هل تريد المتابعة؟") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    vm.importFrom(uri) { ok ->
                        Toast.makeText(context, if (ok) "تم الاسترجاع بنجاح" else "فشل الاسترجاع: الملف غير صالح", Toast.LENGTH_LONG).show()
                    }
                }) { Text("استرجاع", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("إلغاء") } }
        )
    }
    saleView?.let { s ->
        val name = if (s.customerId == 0L) "بيع نقدي" else customers.find { it.id == s.customerId }?.name ?: "—"
        SaleDetailDialog(
            vm, s, name,
            onEdit = { saleView = null; onEditSale(s.id) },
            onDelete = { saleView = null; vm.deleteSale(s.id) { ok -> Toast.makeText(context, if (ok) "تم الحذف" else "تعذر الحذف", Toast.LENGTH_SHORT).show() } },
            onDismiss = { saleView = null }
        )
    }
}
