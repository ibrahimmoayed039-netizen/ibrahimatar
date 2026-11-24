package com.fabric.ledger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fabric.ledger.MainViewModel
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.fabric.ledger.data.Payment
import com.fabric.ledger.data.Sale

private sealed class Entry(val date: Long) {
    class S(val s: Sale) : Entry(s.date)
    class P(val p: Payment) : Entry(p.date)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerScreen(
    vm: MainViewModel, id: Long,
    onBack: () -> Unit, onNewSale: () -> Unit, onEditSale: (Long) -> Unit
) {
    val customers by vm.customers.collectAsStateWithLifecycle()
    val sales by vm.sales.collectAsStateWithLifecycle()
    val payments by vm.payments.collectAsStateWithLifecycle()
    val balances by vm.balances.collectAsStateWithLifecycle()
    val customer = customers.find { it.id == id }
    val balance = balances[id] ?: emptyMap()
    var payDlg by remember { mutableStateOf(false) }
    var editPay by remember { mutableStateOf<Payment?>(null) }
    var saleView by remember { mutableStateOf<Sale?>(null) }
    var editDlg by remember { mutableStateOf(false) }
    var delDlg by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val overdueMap by vm.overdue.collectAsStateWithLifecycle()
    val lastPay by vm.lastPayment.collectAsStateWithLifecycle()
    val grace by vm.graceDays.collectAsStateWithLifecycle()
    val shop by vm.shopName.collectAsStateWithLifecycle()
    val cc by vm.countryCode.collectAsStateWithLifecycle()
    val od = overdueMap[id] ?: emptyMap()
    val odDue = od.filterValues { it.amount > 0.005 }
    val defCur = balance.entries.firstOrNull { it.value > 0.005 }?.key ?: "IQD"
    var busy by remember { mutableStateOf(false) }

    val entries = (sales.filter { it.customerId == id }.map { Entry.S(it) } +
            payments.filter { it.customerId == id }.map { Entry.P(it) }).sortedByDescending { it.date }

    Scaffold(
        topBar = {
            GradientTopBar(
                title = { Text(customer?.name ?: "") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } },
                actions = {
                    IconButton(onClick = { editDlg = true }, enabled = customer != null) { Icon(Icons.Default.Edit, "تعديل الزبون") }
                    IconButton(onClick = { delDlg = true }, enabled = customer != null) { Icon(Icons.Default.Delete, "حذف الزبون") }
                }
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                HeroCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("الدين الحالي")
                        BalanceLines(balance)
                        if (balance.hasDebt()) Text(lastPaymentText(lastPay[id]), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        if (!customer?.phone.isNullOrBlank()) Text(customer!!.phone, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button3D(onClick = { payDlg = true }, modifier = Modifier.weight(1f)) { Text("تسجيل دفعة") }
                    OutlinedButton(onClick = onNewSale, modifier = Modifier.weight(1f)) { Text("بيع جديد") }
                }
            }
            if (odDue.isNotEmpty() && customer != null) item {
                Card3D(
                    Modifier.fillMaxWidth(),
                    tint = MaterialTheme.colorScheme.errorContainer
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("⚠ دين متأخر: ${odDue.entries.joinToString("  +  ") { it.value.amount.money(it.key) }}", fontWeight = FontWeight.Bold)
                        Text(
                            "أقدم فاتورة غير مسددة قبل ${odDue.values.maxOf { it.oldestDays }} يوماً (مدة السماح $grace يوماً)",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button3D(onClick = {
                            val msg = reminderMessage(shop, customer.name, balance, od, grace, attached = false)
                            val num = normalizePhone(customer.phone, cc)
                            if (customer.phone.isNotBlank() && num.isEmpty()) {
                                Toast.makeText(context, "اكتب الرقم بالصيغة الدولية أو اضبط رمز الدولة من الإعدادات", Toast.LENGTH_LONG).show()
                            }
                            Share.whatsappText(context, num, msg)
                        }) { Text("إرسال تذكير واتساب") }
                    }
                }
            }
            item {
                OutlinedButton(
                    enabled = !busy && customer != null,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        customer?.let { cust ->
                            busy = true
                            scope.launch {
                                try {
                                    val rows = vm.statementRows(id)
                                    val file = withContext(Dispatchers.IO) {
                                        StatementPdf.create(context, shop, cust, rows, balance, od, grace)
                                    }
                                    Share.pdf(context, file, reminderMessage(shop, cust.name, balance, od, grace, attached = true))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "تعذر إنشاء كشف الحساب", Toast.LENGTH_LONG).show()
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    }
                ) { Text(if (busy) "جارٍ إنشاء الملف..." else "كشف حساب PDF وإرساله عبر واتساب") }
            }
            if (entries.isEmpty()) item { Text("لا توجد حركات بعد.") }
            else item { Text("اضغط على أي فاتورة أو دفعة للتعديل أو الحذف", style = MaterialTheme.typography.bodySmall) }
            items(entries, key = { e -> when (e) { is Entry.S -> "s${e.s.id}"; is Entry.P -> "p${e.p.id}" } }) { e ->
                when (e) {
                    is Entry.S -> Card3D(onClick = { saleView = e.s }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("فاتورة بيع", fontWeight = FontWeight.Bold)
                                Text(e.s.date.dateStr(), style = MaterialTheme.typography.bodySmall)
                                if (e.s.note.isNotBlank()) Text(e.s.note, style = MaterialTheme.typography.bodySmall)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(e.s.total.money(e.s.currency), fontWeight = FontWeight.Bold)
                                Text("مدفوع ${e.s.paid.money(e.s.currency)}", style = MaterialTheme.typography.bodySmall)
                                StatusBadge(saleStatus(e.s.total, e.s.paid))
                            }
                        }
                    }
                    is Entry.P -> Card3D(
                        onClick = { editPay = e.p },
                        modifier = Modifier.fillMaxWidth(),
                        tint = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("دفعة", fontWeight = FontWeight.Bold)
                                Text(e.p.date.dateStr(), style = MaterialTheme.typography.bodySmall)
                                if (e.p.note.isNotBlank()) Text(e.p.note, style = MaterialTheme.typography.bodySmall)
                            }
                            Text("- ${e.p.amount.money(e.p.currency)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }

    if (editDlg && customer != null) CustomerDialog(
        initial = customer,
        onSave = { c, _ -> vm.saveCustomer(c); editDlg = false },
        onDismiss = { editDlg = false }
    )
    if (delDlg && customer != null) ConfirmDialog(
        "حذف الزبون «${customer.name}» نهائياً؟ سيُحذف معه كل فواتيره ودفعاته، وتعود كميات فواتيره إلى المخزن.",
        onYes = {
            delDlg = false
            vm.deleteCustomer(id) { ok ->
                Toast.makeText(context, if (ok) "تم حذف الزبون" else "تعذر الحذف", Toast.LENGTH_SHORT).show()
                if (ok) onBack()
            }
        },
        onDismiss = { delDlg = false }
    )
    if (payDlg) PaymentDialog(
        initial = null,
        defaultCurrency = defCur,
        onSave = { amt, note, cur -> vm.addPayment(id, amt, note, cur); payDlg = false },
        onDelete = null,
        onDismiss = { payDlg = false }
    )
    editPay?.let { p ->
        PaymentDialog(
            initial = p,
            onSave = { amt, note, cur -> vm.savePayment(p.copy(amount = amt, note = note, currency = cur)); editPay = null },
            onDelete = { editPay = null; vm.deletePayment(p) { ok -> Toast.makeText(context, if (ok) "تم الحذف" else "تعذر الحذف", Toast.LENGTH_SHORT).show() } },
            onDismiss = { editPay = null }
        )
    }
    saleView?.let { s ->
        SaleDetailDialog(
            vm, s, customer?.name ?: "",
            onEdit = { saleView = null; onEditSale(s.id) },
            onDelete = { saleView = null; vm.deleteSale(s.id) { ok -> Toast.makeText(context, if (ok) "تم الحذف" else "تعذر الحذف", Toast.LENGTH_SHORT).show() } },
            onDismiss = { saleView = null }
        )
    }
}
