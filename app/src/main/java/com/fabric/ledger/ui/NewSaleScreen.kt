package com.fabric.ledger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fabric.ledger.MainViewModel
import com.fabric.ledger.data.Fabric
import com.fabric.ledger.data.Sale
import com.fabric.ledger.data.SaleItem

private data class CartLine(
    val fabric: Fabric? = null, val qty: String = "", val price: String = "",
    /** الإدخال بالقطعة: عدد القطع × متر لكل قطعة */
    val byPiece: Boolean = false, val pieces: String = "", val plen: String = ""
) {
    /** الكمية الفعلية بالأمتار (أو بوحدة الصنف) */
    fun meters(): Double = if (byPiece) pieces.toD() * plen.toD() else qty.toD()
}

/**
 * presetCustomerId: -1 = لم يُختر بعد، 0 = بيع نقدي، غير ذلك = معرّف الزبون
 * editSaleId: أكبر من 0 = وضع تعديل فاتورة موجودة
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSaleScreen(vm: MainViewModel, presetCustomerId: Long, editSaleId: Long, onDone: () -> Unit) {
    val customers by vm.customers.collectAsStateWithLifecycle()
    val fabrics by vm.fabrics.collectAsStateWithLifecycle()
    val isEdit = editSaleId > 0
    var customerId by remember { mutableLongStateOf(presetCustomerId) }
    val lines = remember { mutableStateListOf(CartLine()) }
    var paid by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var custMenu by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(!isEdit) }
    var origDate by remember { mutableLongStateOf(0L) }
    var oldQty by remember { mutableStateOf<Map<Long, Double>>(emptyMap()) }

    LaunchedEffect(editSaleId) {
        if (editSaleId > 0) {
            val s = vm.saleNow(editSaleId)
            if (s == null) { onDone(); return@LaunchedEffect }
            val fs = vm.fabricsNow()
            val old = vm.itemsNow(editSaleId)
            customerId = s.customerId
            origDate = s.date
            paid = if (s.customerId == 0L) "" else s.paid.plain()
            oldQty = old.groupBy { it.fabricId }.mapValues { e -> e.value.sumOf { it.quantity } }
            lines.clear()
            old.forEach { i ->
                val f = fs.find { it.id == i.fabricId } ?: Fabric(id = i.fabricId, name = i.fabricName, unit = i.unit)
                lines.add(
                    if (i.pieces > 0.0) CartLine(f, i.quantity.plain(), i.unitPrice.plain(), true, i.pieces.plain(), (i.quantity / i.pieces).r3().plain())
                    else CartLine(f, i.quantity.plain(), i.unitPrice.plain())
                )
            }
            if (lines.isEmpty()) lines.add(CartLine())
            loaded = true
        }
    }

    val customer = customers.find { it.id == customerId }
    val total = lines.sumOf { it.meters() * it.price.toD() }
    val isCash = customerId == 0L
    val paidVal = if (isCash) total else paid.toD().coerceIn(0.0, total)

    fun save() {
        val valid = lines.filter { it.fabric != null && it.meters() > 0 }
        if (customerId < 0) { error = "اختر الزبون أو البيع النقدي"; return }
        if (valid.isEmpty()) { error = "أضف صنفاً واحداً على الأقل مع الكمية"; return }
        val over = valid.groupBy { it.fabric!!.id }.values.firstOrNull { g ->
            val fid = g.first().fabric!!.id
            // المتاح = المخزون الحالي + ما كانت تحتويه الفاتورة القديمة (في وضع التعديل)
            val available = (fabrics.find { it.id == fid }?.stock ?: 0.0) + (oldQty[fid] ?: 0.0)
            g.sumOf { it.meters() } > available + 1e-9
        }
        if (over != null) { error = "الكمية المطلوبة أكبر من المخزون: ${over.first().fabric!!.name}"; return }
        val items = valid.map { l ->
            val f = l.fabric!!
            SaleItem(fabricId = f.id, fabricName = f.name, unit = f.unit, quantity = l.meters(), unitPrice = l.price.toD(),
                pieces = if (l.byPiece) l.pieces.toD() else 0.0)
        }
        if (isEdit) {
            vm.updateSale(
                Sale(id = editSaleId, customerId = customerId, date = origDate, total = total, paid = paidVal),
                items
            ) { onDone() }
        } else {
            vm.saveSale(
                Sale(customerId = customerId, date = System.currentTimeMillis(), total = total, paid = paidVal),
                items
            ) { onDone() }
        }
    }

    Scaffold(
        topBar = {
            GradientTopBar(
                title = { Text(if (isEdit) "تعديل فاتورة" else "بيع جديد") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, modifier = Modifier.navigationBarsPadding()) {
                Button3D(onClick = { save() }, enabled = loaded, modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(if (isEdit) "حفظ التعديلات" else "حفظ البيع")
                }
            }
        }
    ) { pad ->
        if (!loaded) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box {
                OutlinedButton(onClick = { custMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        when {
                            customerId == 0L -> "بيع نقدي"
                            customer != null -> customer.name + if (customer.isWholesale) " (جملة)" else ""
                            else -> "اختر الزبون"
                        }
                    )
                }
                DropdownMenu(expanded = custMenu, onDismissRequest = { custMenu = false }) {
                    DropdownMenuItem(text = { Text("بيع نقدي") }, onClick = { customerId = 0; custMenu = false })
                    customers.forEach { c ->
                        DropdownMenuItem(
                            text = { Text(c.name + if (c.isWholesale) " (جملة)" else "") },
                            onClick = { customerId = c.id; custMenu = false }
                        )
                    }
                }
            }

            if (fabrics.isEmpty()) Text("أضف أصنافاً أولاً من تبويب الأصناف.", color = MaterialTheme.colorScheme.error)

            lines.forEachIndexed { i, line ->
                var open by remember { mutableStateOf(false) }
                Card3D(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box {
                            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(line.fabric?.let { "${it.name} — المخزون ${it.stock.plain()} ${it.unit}" } ?: "اختر الصنف")
                            }
                            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                                fabrics.forEach { f ->
                                    DropdownMenuItem(
                                        text = { Text("${f.name} ${f.color} (${f.stock.plain()} ${f.unit})") },
                                        onClick = {
                                            val p = if (customer?.isWholesale == true) f.wholesalePrice else f.retailPrice
                                            val keep = lines[i].byPiece && f.supportsPieces()
                                            lines[i] = lines[i].copy(
                                                fabric = f, price = p.plain(), byPiece = keep,
                                                plen = if (f.rollLength > 0) f.rollLength.plain() else if (keep) lines[i].plen else ""
                                            )
                                            open = false
                                        }
                                    )
                                }
                            }
                        }
                        val canPieces = line.fabric?.supportsPieces() == true
                        if (canPieces) QtyModeChips(line.byPiece) { on ->
                            val rl = line.fabric?.rollLength ?: 0.0
                            lines[i] = lines[i].copy(byPiece = on, plen = if (on && lines[i].plen.isBlank() && rl > 0) rl.plain() else lines[i].plen)
                        }
                        if (line.byPiece && canPieces) {
                            PieceFields(line.pieces, { lines[i] = lines[i].copy(pieces = it) }, line.plen, { lines[i] = lines[i].copy(plen = it) }, line.meters())
                            NumField(line.price, { lines[i] = lines[i].copy(price = it) }, "سعر المتر", Modifier.fillMaxWidth())
                            if (line.plen.toD() > 0 && line.price.toD() > 0)
                                Text("سعر القطعة الواحدة: ${(line.plen.toD() * line.price.toD()).fmt()}", style = MaterialTheme.typography.bodySmall)
                        } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumField(line.qty, { lines[i] = lines[i].copy(qty = it) }, "الكمية (${line.fabric?.unit ?: ""})", Modifier.weight(1f))
                            NumField(line.price, { lines[i] = lines[i].copy(price = it) }, if (canPieces) "سعر المتر" else "السعر", Modifier.weight(1f))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("المجموع: ${(line.meters() * line.price.toD()).fmt()}", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            if (lines.size > 1) IconButton(onClick = { lines.removeAt(i) }) {
                                Icon(Icons.Default.Delete, "حذف", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            TextButton(onClick = { lines.add(CartLine()) }) { Text("+ إضافة صنف آخر") }

            Card3D(Modifier.fillMaxWidth(), tint = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("إجمالي الفاتورة: ${total.fmt()}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    if (isCash) {
                        Text("بيع نقدي: يُسجَّل المبلغ مدفوعاً بالكامل")
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            NumField(paid, { paid = it }, "المبلغ المدفوع", Modifier.weight(1f))
                            TextButton(onClick = { paid = total.plain() }) { Text("دفع كامل") }
                        }
                        Text("يُضاف للدين: ${(total - paidVal).fmt()}", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            val lowWarn = lines.filter { it.fabric != null && it.meters() > 0 }
                .groupBy { it.fabric!!.id }
                .mapNotNull { (fid, g) ->
                    val f = fabrics.find { it.id == fid } ?: return@mapNotNull null
                    val after = f.stock + (oldQty[fid] ?: 0.0) - g.sumOf { it.meters() }
                    if (f.minStock > 0 && after <= f.minStock) "${f.name}: سيبقى ${after.plain()} ${f.unit} (حد التنبيه ${f.minStock.plain()})" else null
                }
            if (lowWarn.isNotEmpty()) Card3D(
                Modifier.fillMaxWidth(),
                tint = MaterialTheme.colorScheme.errorContainer
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("⚠ بعد هذا البيع سينخفض المخزون:", fontWeight = FontWeight.Bold)
                    lowWarn.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
