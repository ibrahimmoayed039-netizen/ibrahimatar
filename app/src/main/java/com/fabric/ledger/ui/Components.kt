package com.fabric.ledger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fabric.ledger.MainViewModel
import com.fabric.ledger.data.Customer
import com.fabric.ledger.data.Fabric
import com.fabric.ledger.data.FabricTotals
import com.fabric.ledger.data.Payment
import com.fabric.ledger.data.Purchase
import com.fabric.ledger.data.SupplierPayment
import com.fabric.ledger.data.Sale
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Double.fmt(): String = String.format(Locale.US, "%,.2f", this)
fun Double.plain(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()
/** هل الصنف يُباع بالأمتار فيصلح له الإدخال بالقطعة؟ */
fun Fabric.supportsPieces(): Boolean = unit == "متر"

/** نص الكمية: "5 قطعة (250 متر)" إن أُدخلت بالقطعة، وإلا "250 متر" */
fun qtyText(pieces: Double, quantity: Double, unit: String): String =
    if (pieces > 0.0) "${pieces.plain()} قطعة (${quantity.plain()} $unit)" else "${quantity.plain()} $unit"

/** تقريب لثلاث خانات لتفادي 49.99999 عند القسمة */
fun Double.r3(): Double = Math.round(this * 1000.0) / 1000.0

/** المخزون مقدّراً بالقطع (إن كان طول القطعة محدداً) */
fun Fabric.stockPiecesText(): String? =
    if (supportsPieces() && rollLength > 0.0) "≈ ${(stock / rollLength).r3().plain()} قطعة" else null

fun Long.dateStr(): String = SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale.US).format(Date(this))

/** يقبل الأرقام العربية والهندية والفاصلة العشرية */
fun String.toD(): Double {
    val n = map { c -> if (c in '٠'..'٩') '0' + (c - '٠') else c }
        .joinToString("").replace('٫', '.').replace(',', '.')
    return n.trim().toDoubleOrNull() ?: 0.0
}

/** توحيد النص للبحث: الأرقام العربية، الهمزات، التاء المربوطة، الألف المقصورة، وإزالة التشكيل */
fun String.norm(): String = lowercase().map { c ->
    when (c) {
        in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
        '\u0623', '\u0625', '\u0622' -> '\u0627'
        '\u0629' -> '\u0647'
        '\u0649' -> '\u064A'
        else -> c
    }
}.filter { it !in '\u064B'..'\u0652' && it != '\u0640' }.joinToString("")

/** هل أي حقل من الحقول يحتوي نص البحث؟ (فارغ = الكل) */
fun matchesQuery(q: String, vararg fields: String): Boolean {
    if (q.isBlank()) return true
    val n = q.trim().norm()
    return fields.any { it.norm().contains(n) }
}

@Composable
fun SearchFilterBar(
    query: String, onQuery: (String) -> Unit, hint: String,
    filters: List<String>, selected: Int, onSelect: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = query, onValueChange = onQuery, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(hint) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, "مسح") }
            }
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            filters.forEachIndexed { i, label ->
                FilterChip(selected = selected == i, onClick = { onSelect(i) }, label = { Text(label) })
            }
        }
    }
}

/** هل الصنف عند حد التنبيه أو دونه؟ */
fun Fabric.isLow(): Boolean = minStock > 0 && stock <= minStock

@Composable
fun NumField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        singleLine = true, modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

@Composable
fun FabricDialog(initial: Fabric?, onSave: (Fabric) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: "") }
    var color by remember { mutableStateOf(initial?.color ?: "") }
    var unit by remember { mutableStateOf(initial?.unit ?: "متر") }
    var retail by remember { mutableStateOf(initial?.retailPrice?.plain() ?: "") }
    var wholesale by remember { mutableStateOf(initial?.wholesalePrice?.plain() ?: "") }
    var stock by remember { mutableStateOf(initial?.stock?.plain() ?: "") }
    var minStock by remember { mutableStateOf(initial?.minStock?.plain() ?: "") }
    var rollLen by remember { mutableStateOf(initial?.rollLength?.takeIf { it > 0 }?.plain() ?: "") }
    var confirmDel by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "إضافة صنف" else "تعديل صنف") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("اسم القماش") }, singleLine = true)
                OutlinedTextField(type, { type = it }, label = { Text("النوع (قطن، حرير، كتان...)") }, singleLine = true)
                OutlinedTextField(color, { color = it }, label = { Text("اللون") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("متر", "ثوب").forEach { u ->
                        FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(u) })
                    }
                }
                NumField(retail, { retail = it }, "سعر المفرق")
                NumField(wholesale, { wholesale = it }, "سعر الجملة")
                if (unit == "متر") NumField(rollLen, { rollLen = it }, "طول القطعة الكاملة بالمتر (مثلاً 50) — للبيع والشراء بالقطعة")
                NumField(stock, { stock = it }, "الكمية في المخزن ($unit)")
                NumField(minStock, { minStock = it }, "نبّهني عند نزول الكمية إلى ($unit) — اتركه فارغاً لإلغاء التنبيه")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) onSave(
                    Fabric(
                        id = initial?.id ?: 0, name = name.trim(), type = type.trim(), color = color.trim(),
                        unit = unit, retailPrice = retail.toD(), wholesalePrice = wholesale.toD(), stock = stock.toD(), minStock = minStock.toD(),
                        rollLength = if (unit == "متر") rollLen.toD() else 0.0
                    )
                )
            }) { Text("حفظ") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = { confirmDel = true }) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text("إلغاء") }
            }
        }
    )
    if (confirmDel && onDelete != null) ConfirmDialog(
        "حذف هذا الصنف؟ الفواتير القديمة تحتفظ باسمه وتفاصيله.",
        onYes = onDelete, onDismiss = { confirmDel = false }
    )
}

@Composable
fun CustomerDialog(onSave: (Customer) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var wholesale by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة زبون") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("الاسم") }, singleLine = true)
                OutlinedTextField(
                    phone, { phone = it }, label = { Text("رقم الهاتف") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Switch(checked = wholesale, onCheckedChange = { wholesale = it })
                    Spacer(Modifier.width(8.dp))
                    Text("زبون جملة")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) onSave(Customer(name = name.trim(), phone = phone.trim(), isWholesale = wholesale))
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun ConfirmDialog(text: String, onYes: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onYes) { Text("نعم، احذف", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("لا") } }
    )
}

@Composable
fun PaymentDialog(initial: Payment?, onSave: (Double, String) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf(initial?.amount?.plain() ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var confirmDel by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "تسجيل دفعة" else "تعديل دفعة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField(amount, { amount = it }, "المبلغ")
                OutlinedTextField(note, { note = it }, label = { Text("ملاحظة (اختياري)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (amount.toD() > 0) onSave(amount.toD(), note.trim()) }) { Text("حفظ") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = { confirmDel = true }) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text("إلغاء") }
            }
        }
    )
    if (confirmDel && onDelete != null) ConfirmDialog(
        "حذف هذه الدفعة؟ سيرجع مبلغها إلى دين الزبون.",
        onYes = onDelete, onDismiss = { confirmDel = false }
    )
}

@Composable
fun SaleDetailDialog(
    vm: MainViewModel, sale: Sale, customerName: String,
    onEdit: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit
) {
    val flow = remember(sale.id) { vm.itemsOf(sale.id) }
    val items by flow.collectAsStateWithLifecycle(initialValue = emptyList())
    var confirmDel by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("فاتورة $customerName") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(sale.date.dateStr(), style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                items.forEach {
                    Text("${it.fabricName}: ${qtyText(it.pieces, it.quantity, it.unit)} × ${it.unitPrice.fmt()} = ${(it.quantity * it.unitPrice).fmt()}")
                }
                HorizontalDivider()
                Text("الإجمالي: ${sale.total.fmt()}", fontWeight = FontWeight.Bold)
                Text("المدفوع: ${sale.paid.fmt()}")
                Text("المتبقي: ${(sale.total - sale.paid).fmt()}", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
        dismissButton = {
            Row {
                TextButton(onClick = onEdit) { Text("تعديل") }
                TextButton(onClick = { confirmDel = true }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            }
        }
    )
    if (confirmDel) ConfirmDialog(
        "حذف هذه الفاتورة؟ ستعود كمياتها إلى المخزن ويُخصم دينها من الزبون.",
        onYes = onDelete, onDismiss = { confirmDel = false }
    )
}

@Composable
fun SettingsDialog(
    initialGrace: Int, initialShop: String, initialCc: String,
    onSave: (Int, String, String) -> Unit, onDismiss: () -> Unit
) {
    var grace by remember { mutableStateOf(initialGrace.toString()) }
    var shop by remember { mutableStateOf(initialShop) }
    var cc by remember { mutableStateOf(initialCc) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إعدادات الكشف والتذكير") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(shop, { shop = it }, label = { Text("اسم المحل (يظهر في الكشف والرسائل)") }, singleLine = true)
                NumField(grace, { grace = it }, "مدة السماح بالأيام قبل اعتبار الدين متأخراً")
                OutlinedTextField(
                    cc, { cc = it }, label = { Text("رمز الدولة بدون + (مثال 964) لأرقام تبدأ بـ 0") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val g = if (grace.isBlank()) 30 else grace.toD().toInt().coerceAtLeast(0)
                onSave(g, shop.trim(), cc.filter { it.isDigit() })
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun CurrencyChips(selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("IQD" to "دينار عراقي (د.ع)", "USD" to "دولار (\$)").forEach { (code, label) ->
            FilterChip(selected = selected == code, onClick = { onSelect(code) }, label = { Text(label) })
        }
    }
}

/** اختيار طريقة الإدخال: بالمتر أو بالقطعة (الطول الكامل) */
@Composable
fun QtyModeChips(byPiece: Boolean, onChange: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !byPiece, onClick = { onChange(false) }, label = { Text("بالمتر") })
        FilterChip(selected = byPiece, onClick = { onChange(true) }, label = { Text("بالقطعة (طول كامل)") })
    }
}

/** حقول الإدخال بالقطعة: عدد القطع + متر لكل قطعة، مع ناتج الأمتار */
@Composable
fun PieceFields(pieces: String, onPieces: (String) -> Unit, plen: String, onPlen: (String) -> Unit, meters: Double) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumField(pieces, onPieces, "عدد القطع", Modifier.weight(1f))
        NumField(plen, onPlen, "متر/القطعة", Modifier.weight(1f))
    }
    Text("= ${meters.r3().plain()} متر", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
}

/** شراء بضاعة: الصنف، الكمية، العملة، سعر الوحدة، المسدَّد، والمورّد. presetFabricId > 0 يثبّت الصنف */
@Composable
fun PurchaseDialog(
    initial: Purchase?, fabrics: List<Fabric>, presetFabricId: Long,
    onSave: (Purchase) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit
) {
    var fabricId by remember { mutableLongStateOf(initial?.fabricId ?: presetFabricId) }
    var qty by remember { mutableStateOf(initial?.quantity?.plain() ?: "") }
    var byPiece by remember { mutableStateOf((initial?.pieces ?: 0.0) > 0.0) }
    var pieces by remember { mutableStateOf(initial?.pieces?.takeIf { it > 0 }?.plain() ?: "") }
    var plen by remember { mutableStateOf(initial?.takeIf { it.pieces > 0 }?.let { (it.quantity / it.pieces).r3().plain() } ?: "") }
    var cost by remember { mutableStateOf(initial?.unitCost?.takeIf { it > 0 }?.plain() ?: "") }
    var currency by remember { mutableStateOf(initial?.currency ?: "IQD") }
    var paid by remember { mutableStateOf(initial?.paid?.takeIf { it > 0 }?.plain() ?: "") }
    var supplier by remember { mutableStateOf(initial?.supplier ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var menu by remember { mutableStateOf(false) }
    var confirmDel by remember { mutableStateOf(false) }
    val fabric = fabrics.find { it.id == fabricId }
    val canPieces = fabric?.supportsPieces() == true
    val usePieces = byPiece && canPieces
    val meters = if (usePieces) pieces.toD() * plen.toD() else qty.toD()
    val total = meters * cost.toD()
    val paidVal = paid.toD().coerceIn(0.0, total)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "تسجيل شراء" else "تعديل شراء") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initial == null && presetFabricId <= 0) Box {
                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(fabric?.let { "${it.name} ${it.color}".trim() } ?: "اختر الصنف")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        fabrics.forEach { f ->
                            DropdownMenuItem(
                                text = { Text("${f.name} ${f.color} (${f.unit})") },
                                onClick = { fabricId = f.id; menu = false }
                            )
                        }
                    }
                } else Text(fabric?.name ?: "صنف محذوف", fontWeight = FontWeight.Bold)
                if (canPieces) QtyModeChips(byPiece) { on ->
                    byPiece = on
                    if (on && plen.isBlank() && (fabric?.rollLength ?: 0.0) > 0) plen = fabric!!.rollLength.plain()
                }
                if (usePieces) PieceFields(pieces, { pieces = it }, plen, { plen = it }, meters)
                else NumField(qty, { qty = it }, "الكمية المشتراة (${fabric?.unit ?: ""})")
                CurrencyChips(currency) { currency = it }
                NumField(cost, { cost = it }, "سعر ${if (canPieces) "المتر" else "الوحدة"} بال${currencyName(currency)}")
                Text("إجمالي سعر البضاعة: ${total.money(currency)}", fontWeight = FontWeight.Bold)
                NumField(paid, { paid = it }, "المسدَّد للمورّد وقت الشراء (اختياري)")
                Text("المتبقي: ${(total - paidVal).money(currency)}", color = MaterialTheme.colorScheme.error)
                OutlinedTextField(supplier, { supplier = it }, label = { Text("اسم المورّد (اختياري)") }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("ملاحظة (اختياري)") }, singleLine = true)
                if (initial == null) Text("ستُضاف الكمية إلى المخزون تلقائياً.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (fabricId > 0 && meters > 0) onSave(
                    Purchase(
                        id = initial?.id ?: 0, fabricId = fabricId, date = initial?.date ?: System.currentTimeMillis(),
                        quantity = meters, unitCost = cost.toD(), note = note.trim(),
                        currency = currency, paid = paidVal, supplier = supplier.trim(),
                        pieces = if (usePieces) pieces.toD() else 0.0
                    )
                )
            }) { Text("حفظ") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = { confirmDel = true }) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text("إلغاء") }
            }
        }
    )
    if (confirmDel && onDelete != null) ConfirmDialog(
        "حذف عملية الشراء هذه؟ ستُخصم كميتها من المخزون ويُلغى سعرها من حساب المورّدين.",
        onYes = onDelete, onDismiss = { confirmDel = false }
    )
}

/** دفعة تسديد للمورّد بالدينار أو الدولار */
@Composable
fun SupplierPaymentDialog(
    initial: SupplierPayment?, onSave: (SupplierPayment) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit
) {
    var amount by remember { mutableStateOf(initial?.amount?.plain() ?: "") }
    var currency by remember { mutableStateOf(initial?.currency ?: "IQD") }
    var supplier by remember { mutableStateOf(initial?.supplier ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var confirmDel by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "تسجيل دفعة للمورّد" else "تعديل دفعة المورّد") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CurrencyChips(currency) { currency = it }
                NumField(amount, { amount = it }, "المبلغ المسدَّد بال${currencyName(currency)}")
                OutlinedTextField(supplier, { supplier = it }, label = { Text("اسم المورّد (اختياري)") }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("ملاحظة (اختياري)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (amount.toD() > 0) onSave(
                    SupplierPayment(
                        id = initial?.id ?: 0, date = initial?.date ?: System.currentTimeMillis(),
                        amount = amount.toD(), currency = currency, supplier = supplier.trim(), note = note.trim()
                    )
                )
            }) { Text("حفظ") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = { confirmDel = true }) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text("إلغاء") }
            }
        }
    )
    if (confirmDel && onDelete != null) ConfirmDialog(
        "حذف هذه الدفعة؟ سيرجع مبلغها إلى المتبقي للمورّد.",
        onYes = onDelete, onDismiss = { confirmDel = false }
    )
}

/** ترويسة تصنيف (نوع القماش): عدد الأنواع، إجمالي المشترى، والمخزون لكل وحدة */
@Composable
fun CategoryHeader(name: String, list: List<Fabric>, totals: Map<Long, FabricTotals>) {
    val units = list.map { it.unit }.distinct()
    fun sum(u: String, f: (Fabric) -> Double) = list.filter { it.unit == u }.sumOf(f)
    val bought = units.joinToString(" + ") { u -> "${sum(u) { totals[it.id]?.purchased ?: 0.0 }.plain()} $u" }
    val stock = units.joinToString(" + ") { u -> "${sum(u) { it.stock }.plain()} $u" }
    Card3D(Modifier.fillMaxWidth(), tint = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("$name  •  ${list.size} نوع", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("المشترى: $bought     |     المخزون: $stock", style = MaterialTheme.typography.bodySmall)
        }
    }
}
