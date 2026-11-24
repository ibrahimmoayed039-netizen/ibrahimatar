package com.fabric.ledger.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fabric.ledger.MainViewModel
import com.fabric.ledger.data.Payable
import com.fabric.ledger.data.Purchase
import com.fabric.ledger.data.SupplierPayment

private sealed class SupRow(val date: Long, val key: String) {
    class P(val p: Purchase) : SupRow(p.date, "p${p.id}")
    class S(val s: SupplierPayment) : SupRow(s.date, "s${s.id}")
}

/** شاشة المورّد: ملخص حسابه وقوائم مشترياته ودفعاته */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierScreen(vm: MainViewModel, id: Long, onBack: () -> Unit) {
    val suppliers by vm.suppliers.collectAsStateWithLifecycle()
    val purchases by vm.purchases.collectAsStateWithLifecycle()
    val payments by vm.supplierPayments.collectAsStateWithLifecycle()
    val fabrics by vm.fabrics.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sup = suppliers.find { it.id == id }
    val name = sup?.name ?: ""
    val names = suppliers.map { it.name }

    var editDlg by remember { mutableStateOf(false) }
    var delDlg by remember { mutableStateOf(false) }
    var buyDlg by remember { mutableStateOf(false) }
    var payDlg by remember { mutableStateOf(false) }
    var editPurchase by remember { mutableStateOf<Purchase?>(null) }
    var editPay by remember { mutableStateOf<SupplierPayment?>(null) }

    val myPurchases = if (sup == null) emptyList() else purchases.filter { it.supplier == name }
    val myPayments = if (sup == null) emptyList() else payments.filter { it.supplier == name }
    val rows = (myPurchases.map { SupRow.P(it) } + myPayments.map { SupRow.S(it) }).sortedByDescending { it.date }
    fun done(ok: Boolean, msg: String) = Toast.makeText(context, if (ok) msg else "تعذرت العملية", Toast.LENGTH_SHORT).show()

    Scaffold(
        topBar = {
            GradientTopBar(
                title = { Text(name) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } },
                actions = {
                    IconButton(onClick = { editDlg = true }, enabled = sup != null) { Icon(Icons.Default.Edit, "تعديل المورّد") }
                    IconButton(onClick = { delDlg = true }, enabled = sup != null) { Icon(Icons.Default.Delete, "حذف المورّد") }
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
                        val active = CURRENCIES.filter { c -> myPurchases.any { it.currency == c } || myPayments.any { it.currency == c } }
                        if (active.isEmpty()) Text("لا توجد مشتريات من هذا المورّد بعد")
                        active.forEach { c ->
                            val acc = Payable(
                                myPurchases.filter { it.currency == c }.sumOf { it.total },
                                myPurchases.filter { it.currency == c }.sumOf { it.paid } + myPayments.filter { it.currency == c }.sumOf { it.amount }
                            )
                            Text("حساب بال${currencyName(c)}", fontWeight = FontWeight.Bold)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("سعر البضاعة المشتراة"); Text(acc.total.money(c), fontWeight = FontWeight.Bold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("المسدَّد"); Text(acc.paid.money(c), fontWeight = FontWeight.Bold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("المتبقي له")
                                Text(
                                    acc.remaining.money(c), fontWeight = FontWeight.Bold,
                                    color = if (acc.remaining > 0.005) Color(0xFFFFE082) else Color(0xFFA5D6A7)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        if (!sup?.phone.isNullOrBlank()) Text(sup!!.phone, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button3D(onClick = { buyDlg = true }, modifier = Modifier.weight(1f)) { Text("تسجيل شراء") }
                    OutlinedButton(onClick = { payDlg = true }, modifier = Modifier.weight(1f)) { Text("تسجيل دفعة") }
                }
            }
            if (rows.isEmpty()) item { Text("لا توجد قوائم شراء لهذا المورّد.") }
            items(rows, key = { it.key }) { r ->
                when (r) {
                    is SupRow.P -> {
                        val p = r.p
                        val f = fabrics.find { it.id == p.fabricId }
                        Card3D(onClick = { editPurchase = p }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(f?.name ?: "صنف محذوف", fontWeight = FontWeight.Bold)
                                    Text(qtyText(p.pieces, p.quantity, f?.unit ?: "", p.bales, p.baleType), style = MaterialTheme.typography.bodySmall)
                                    Text(p.date.dateStr(), style = MaterialTheme.typography.bodySmall)
                                    if (p.note.isNotBlank()) Text(p.note, style = MaterialTheme.typography.bodySmall)
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
                    is SupRow.S -> {
                        val sp = r.s
                        Card3D(onClick = { editPay = sp }, modifier = Modifier.fillMaxWidth(), tint = MaterialTheme.colorScheme.secondaryContainer) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("دفعة للمورّد", fontWeight = FontWeight.Bold)
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

    if (buyDlg) PurchaseDialog(
        initial = null, fabrics = fabrics, presetFabricId = -1L,
        onSave = { np -> buyDlg = false; vm.addPurchase(np) { ok -> done(ok, "تم تسجيل الشراء وإضافته للمخزون") } },
        onDelete = null, onDismiss = { buyDlg = false }, suppliers = names, presetSupplier = name
    )
    editPurchase?.let { p ->
        PurchaseDialog(
            initial = p, fabrics = fabrics, presetFabricId = p.fabricId,
            onSave = { np -> editPurchase = null; vm.editPurchase(np) { ok -> done(ok, "تم التعديل") } },
            onDelete = { editPurchase = null; vm.deletePurchase(p.id) { ok -> done(ok, "تم الحذف") } },
            onDismiss = { editPurchase = null }, suppliers = names
        )
    }
    if (payDlg) SupplierPaymentDialog(
        initial = null,
        onSave = { np -> payDlg = false; vm.saveSupplierPayment(np) { ok -> done(ok, "تم تسجيل الدفعة") } },
        onDelete = null, onDismiss = { payDlg = false }, suppliers = names, presetSupplier = name
    )
    editPay?.let { sp ->
        SupplierPaymentDialog(
            initial = sp,
            onSave = { np -> editPay = null; vm.saveSupplierPayment(np) { ok -> done(ok, "تم التعديل") } },
            onDelete = { editPay = null; vm.deleteSupplierPayment(sp) { ok -> done(ok, "تم الحذف") } },
            onDismiss = { editPay = null }, suppliers = names
        )
    }
    if (editDlg && sup != null) SupplierDialog(
        initial = sup,
        onSave = { vm.editSupplier(it); editDlg = false },
        onDismiss = { editDlg = false }
    )
    if (delDlg && sup != null) ConfirmDialog(
        "حذف هذا المورّد؟ لا يمكن حذف مورّد له مشتريات أو دفعات مسجّلة.",
        onYes = {
            delDlg = false
            vm.deleteSupplier(sup.id) { ok ->
                if (ok) onBack() else Toast.makeText(context, "لا يمكن حذف مورّد له مشتريات أو دفعات", Toast.LENGTH_LONG).show()
            }
        },
        onDismiss = { delDlg = false }
    )
}
