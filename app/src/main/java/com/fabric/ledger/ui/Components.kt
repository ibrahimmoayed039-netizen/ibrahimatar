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
fun qtyText(pieces: Double, quantity: Double, unit: String, bales: Double = 0.0, baleType: String = ""): String =
    if (bales > 0.0) "${bales.plain()} ${if (baleType.isBlank()) "بالة" else "بالة $baleType"} (${pieces.plain()} قطعة، ${quantity.plain()} $unit)"
    else if (pieces > 0.0) "${pieces.plain()} قطعة (${quantity.plain()} $unit)" else "${quantity.plain()} $unit"

/** تقريب لثلاث خانات لتفادي 49.99999 عند القسمة */
fun Double.r3(): Double = Math.round(this * 1000.0) / 1000.0

/** عدد البالات المغلقة فعلاً (إن نزل المخزون عن أمتارها بسبب المبيعات تُحسب بالنسبة) */
fun Fabric.sealedBalesEff(): Double =
    if (sealedBales <= 0.0 || sealedMeters <= 0.0) 0.0
    else if (sealedMeters <= stock) sealedBales
    else Math.floor(sealedBales * stock.coerceAtLeast(0.0) / sealedMeters)

/** أمتار البالات المغلقة فعلاً */
fun Fabric.sealedMetersEff(): Double =
    if (sealedBales <= 0.0 || sealedMeters <= 0.0) 0.0 else sealedMeters / sealedBales * sealedBalesEff()

/** الأمتار المفتوحة (خارج البالات المغلقة) */
fun Fabric.looseMeters(): Double = (stock - sealedMetersEff()).coerceAtLeast(0.0)

fun Fabric.baleName(): String = if (baleType.isBlank()) "بالة" else "بالة $baleType"

/** المخزون مقدّراً بالقطع (إن كان طول القطعة محدداً)، مع تفصيل المغلق والمفتوح إن وُجدت بالات مغلقة */
fun Fabric.stockPiecesText(): String? =
    if (supportsPieces() && rollLength > 0.0) {
        val sb = sealedBalesEff()
        if (sb > 0.0) "${sb.plain()} ${baleName()} مغلقة + ${(looseMeters() / rollLength).r3().plain()} قطعة مفتوحة"
        else "≈ ${(stock / rollLength).r3().plain()} قطعة"
    } else null

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
    var retailUsd by remember { mutableStateOf(initial?.retailPriceUsd?.takeIf { it > 0 }?.plain() ?: "") }
    var wholesaleUsd by remember { mutableStateOf(initial?.wholesalePriceUsd?.takeIf { it > 0 }?.plain() ?: "") }
    var stock by remember { mutableStateOf(initial?.stock?.plain() ?: "") }
    var minStock by remember { mutableStateOf(initial?.minStock?.plain() ?: "") }
    var rollLen by remember { mutableStateOf(initial?.rollLength?.takeIf { it > 0 }?.plain() ?: "") }
    var baleType by remember { mutableStateOf(initial?.baleType ?: "") }
    var perBale by remember { mutableStateOf(initial?.piecesPerBale?.takeIf { it > 0 }?.plain() ?: "") }
    var buyPrice by remember { mutableStateOf(initial?.purchasePrice?.takeIf { it > 0 }?.plain() ?: "") }
    var buyCur by remember { mutableStateOf(initial?.purchaseCurrency ?: "IQD") }
    var baleCount by remember { mutableStateOf("") }
    var imagePath by remember { mutableStateOf(initial?.imagePath ?: "") }
    val created = remember { mutableStateListOf<String>() }
    /** حذف الصور الجديدة غير المستخدمة (ملفات أُنشئت ثم أُلغيت) */
    fun cleanup(keep: String) { created.filter { it != keep }.forEach { deleteFabricImage(it) } }
    val dismiss = { cleanup(initial?.imagePath ?: ""); onDismiss() }
    var confirmDel by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(if (initial == null) "إضافة صنف" else "تعديل صنف") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FabricImagePicker(imagePath, name) { np -> if (np.isNotBlank()) created.add(np); imagePath = np }
                OutlinedTextField(name, { name = it }, label = { Text("اسم القماش") }, singleLine = true)
                OutlinedTextField(type, { type = it }, label = { Text("النوع (قطن، حرير، كتان...)") }, singleLine = true)
                OutlinedTextField(color, { color = it }, label = { Text("اللون") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("متر", "ثوب").forEach { u ->
                        FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(u) })
                    }
                }
                NumField(retail, { retail = it }, "سعر المفرق بالدينار")
                NumField(wholesale, { wholesale = it }, "سعر الجملة بالدينار")
                NumField(retailUsd, { retailUsd = it }, "سعر المفرق بالدولار (اختياري)")
                NumField(wholesaleUsd, { wholesaleUsd = it }, "سعر الجملة بالدولار (اختياري)")
                if (unit == "متر") {
                    NumField(rollLen, { rollLen = it }, "طول القطعة الكاملة بالمتر (مثلاً 50) — للبيع والشراء بالقطعة")
                    OutlinedTextField(baleType, { baleType = it }, label = { Text("نوع البالة (مثلاً: بالة كبيرة)") }, singleLine = true)
                    NumField(perBale, { perBale = it }, "عدد القطع داخل البالة الواحدة")
                }
                Text("سعر الشراء", fontWeight = FontWeight.Bold)
                CurrencyChips(buyCur) { buyCur = it }
                NumField(buyPrice, { buyPrice = it }, "سعر شراء ${if (unit == "متر") "المتر" else "الوحدة"} بال${currencyName(buyCur)}")
                if (unit == "متر" && buyPrice.toD() > 0 && rollLen.toD() > 0) {
                    val pieceCost = buyPrice.toD() * rollLen.toD()
                    Text("سعر شراء القطعة: ${pieceCost.money(buyCur)}", style = MaterialTheme.typography.bodySmall)
                    if (perBale.toD() > 0) Text("سعر شراء البالة: ${(pieceCost * perBale.toD()).money(buyCur)}", style = MaterialTheme.typography.bodySmall)
                }
                if (unit == "متر" && perBale.toD() > 0 && rollLen.toD() > 0) {
                    NumField(baleCount, {
                        baleCount = it
                        stock = (it.toD() * perBale.toD() * rollLen.toD()).r3().plain()
                    }, "عدد البالات في المخزن — تحسب الكمية تلقائياً")
                }
                NumField(stock, { stock = it }, "الكمية في المخزن ($unit)")
                NumField(minStock, { minStock = it }, "نبّهني عند نزول الكمية إلى ($unit) — اتركه فارغاً لإلغاء التنبيه")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    cleanup(imagePath)
                    initial?.imagePath?.takeIf { it.isNotBlank() && it != imagePath }?.let { deleteFabricImage(it) }
                    onSave(
                    Fabric(
                        id = initial?.id ?: 0, name = name.trim(), type = type.trim(), color = color.trim(),
                        unit = unit, retailPrice = retail.toD(), wholesalePrice = wholesale.toD(), stock = stock.toD(), minStock = minStock.toD(),
                        rollLength = if (unit == "متر") rollLen.toD() else 0.0,
                        retailPriceUsd = retailUsd.toD(), wholesalePriceUsd = wholesaleUsd.toD(),
                        baleType = if (unit == "متر") baleType.trim() else "",
                        piecesPerBale = if (unit == "متر") perBale.toD() else 0.0,
                        purchasePrice = buyPrice.toD(), purchaseCurrency = buyCur,
                        sealedBales = if (unit == "متر" && baleCount.toD() > 0) baleCount.toD() else initial?.sealedBales ?: 0.0,
                        sealedMeters = if (unit == "متر" && baleCount.toD() > 0) baleCount.toD() * perBale.toD() * rollLen.toD() else initial?.sealedMeters ?: 0.0,
                        imagePath = imagePath
                    )
                    )
                }
            }) { Text("حفظ") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = { confirmDel = true }) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = dismiss) { Text("إلغاء") }
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
fun PaymentDialog(
    initial: Payment?, onSave: (Double, String, String) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit,
    defaultCurrency: String = "IQD"
) {
    var currency by remember { mutableStateOf(initial?.currency ?: defaultCurrency) }
    var amount by remember { mutableStateOf(initial?.amount?.plain() ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var confirmDel by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "تسجيل دفعة" else "تعديل دفعة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CurrencyChips(currency) { currency = it }
                NumField(amount, { amount = it }, "المبلغ بال${currencyName(currency)}")
                OutlinedTextField(note, { note = it }, label = { Text("ملاحظة (اختياري)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (amount.toD() > 0) onSave(amount.toD(), note.trim(), currency) }) { Text("حفظ") }
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
                if (items.isEmpty()) Text(if (sale.note.isBlank()) "قائمة بمبلغ فقط (بدون أصناف)" else "قائمة بمبلغ فقط: ${sale.note}")
                items.forEach {
                    Text("${it.fabricName}: ${qtyText(it.pieces, it.quantity, it.unit)} × ${it.unitPrice.money(sale.currency)} = ${(it.quantity * it.unitPrice).money(sale.currency)}")
                }
                HorizontalDivider()
                Text("الإجمالي: ${sale.total.money(sale.currency)}", fontWeight = FontWeight.Bold)
                Text("المدفوع: ${sale.paid.money(sale.currency)}")
                Text("المتبقي: ${(sale.total - sale.paid).money(sale.currency)}", color = MaterialTheme.colorScheme.error)
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

/** رصيد زبون: سطر لكل عملة ذات رصيد (يُستخدم داخل البطاقات الرئيسية) */
@Composable
fun BalanceLines(balance: Map<String, Double>) {
    val shown = CURRENCIES.filter { kotlin.math.abs(balance[it] ?: 0.0) > 0.005 }
    if (shown.isEmpty()) Text("0", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    else shown.forEach { c ->
        Text(
            balance[c]!!.money(c), fontWeight = FontWeight.Bold,
            style = if (shown.size > 1) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium
        )
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

/** فتح بالات: تتحول من مغلقة إلى قطع مفتوحة دون تغيير المخزون الكلي */
@Composable
fun OpenBaleDialog(f: Fabric, onConfirm: (Double) -> Unit, onDismiss: () -> Unit) {
    var n by remember { mutableStateOf("1") }
    val avail = f.sealedBalesEff()
    val k = n.toD().coerceIn(0.0, avail)
    val avgMeters = if (avail > 0) f.sealedMetersEff() / avail else 0.0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("فتح بالة — ${f.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("المتوفر: ${avail.plain()} ${f.baleName()} مغلقة")
                NumField(n, { n = it }, "عدد البالات المراد فتحها", Modifier.fillMaxWidth())
                if (k > 0) {
                    val meters = (k * avgMeters).r3()
                    val pcs = if (f.rollLength > 0) " = ${(meters / f.rollLength).r3().plain()} قطعة" else ""
                    Text("ستتحول إلى قطع مفتوحة:$pcs (${meters.plain()} متر)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Text("المخزون الكلي لا يتغير، فقط تنتقل الكمية من «مغلقة» إلى «مفتوحة».", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { if (k > 0) onConfirm(k) }) { Text("فتح") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

/** طريقة إدخال الشراء: 0 بالمتر، 1 بالقطعة، 2 بالبالة */
@Composable
fun PurchaseModeChips(mode: Int, canPieces: Boolean, onChange: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = mode == 0, onClick = { onChange(0) }, label = { Text("بالمتر") })
        if (canPieces) {
            FilterChip(selected = mode == 1, onClick = { onChange(1) }, label = { Text("بالقطعة") })
            FilterChip(selected = mode == 2, onClick = { onChange(2) }, label = { Text("بالبالة") })
        }
    }
}

/** شراء بضاعة: الصنف، الكمية (بالمتر/القطعة/البالة)، العملة، سعر المتر، المسدَّد، والمورّد. presetFabricId > 0 يثبّت الصنف
 *  الإجمالي = سعر المتر × متر القطعة × قطع البالة × عدد البالات */
@Composable
fun PurchaseDialog(
    initial: Purchase?, fabrics: List<Fabric>, presetFabricId: Long,
    onSave: (Purchase) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit
) {
    val f0 = fabrics.find { it.id == (initial?.fabricId ?: presetFabricId) }
    var fabricId by remember { mutableLongStateOf(initial?.fabricId ?: presetFabricId) }
    var qty by remember { mutableStateOf(initial?.quantity?.plain() ?: "") }
    // 0 = بالمتر، 1 = بالقطعة، 2 = بالبالة
    var mode by remember {
        mutableIntStateOf(
            when {
                initial != null -> if (initial.bales > 0.0) 2 else if (initial.pieces > 0.0) 1 else 0
                f0 != null && f0.supportsPieces() && f0.piecesPerBale > 0 && f0.rollLength > 0 -> 2
                else -> 0
            }
        )
    }
    var pieces by remember { mutableStateOf(initial?.takeIf { it.bales <= 0 }?.pieces?.takeIf { it > 0 }?.plain() ?: "") }
    var plen by remember {
        mutableStateOf(
            initial?.takeIf { it.pieces > 0 }?.let { (it.quantity / it.pieces).r3().plain() }
                ?: f0?.rollLength?.takeIf { it > 0 }?.plain() ?: ""
        )
    }
    var bales by remember { mutableStateOf(initial?.bales?.takeIf { it > 0 }?.plain() ?: "") }
    var perBale by remember {
        mutableStateOf(
            initial?.takeIf { it.bales > 0 }?.let { (it.pieces / it.bales).r3().plain() }
                ?: f0?.piecesPerBale?.takeIf { it > 0 }?.plain() ?: ""
        )
    }
    var baleType by remember { mutableStateOf(initial?.baleType ?: f0?.baleType ?: "") }
    var cost by remember {
        mutableStateOf(initial?.unitCost?.takeIf { it > 0 }?.plain() ?: if (initial == null) f0?.purchasePrice?.takeIf { it > 0 }?.plain() ?: "" else "")
    }
    var currency by remember { mutableStateOf(initial?.currency ?: f0?.purchaseCurrency ?: "IQD") }
    var paid by remember { mutableStateOf(initial?.paid?.takeIf { it > 0 }?.plain() ?: "") }
    var supplier by remember { mutableStateOf(initial?.supplier ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var menu by remember { mutableStateOf(false) }
    var confirmDel by remember { mutableStateOf(false) }
    val fabric = fabrics.find { it.id == fabricId }
    val canPieces = fabric?.supportsPieces() == true
    val effMode = if (canPieces) mode else 0
    val piecesCount = when (effMode) { 2 -> bales.toD() * perBale.toD(); 1 -> pieces.toD(); else -> 0.0 }
    val meters = if (effMode >= 1) piecesCount * plen.toD() else qty.toD()
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
                                leadingIcon = { FabricThumb(f.imagePath, f.name, 36.dp, decodePx = 96) },
                                onClick = {
                                    fabricId = f.id; menu = false
                                    // تعبئة الإعدادات الافتراضية للصنف المختار
                                    if (f.rollLength > 0) plen = f.rollLength.plain()
                                    if (f.piecesPerBale > 0) perBale = f.piecesPerBale.plain()
                                    baleType = f.baleType
                                    if (f.purchasePrice > 0) { cost = f.purchasePrice.plain(); currency = f.purchaseCurrency }
                                    mode = if (f.supportsPieces() && f.piecesPerBale > 0 && f.rollLength > 0) 2 else 0
                                }
                            )
                        }
                    }
                } else Text(fabric?.name ?: "صنف محذوف", fontWeight = FontWeight.Bold)
                if (canPieces) PurchaseModeChips(effMode, true) { m ->
                    mode = m
                    if (m >= 1 && plen.isBlank() && (fabric?.rollLength ?: 0.0) > 0) plen = fabric!!.rollLength.plain()
                    if (m == 2) {
                        if (perBale.isBlank() && (fabric?.piecesPerBale ?: 0.0) > 0) perBale = fabric!!.piecesPerBale.plain()
                        if (baleType.isBlank()) baleType = fabric?.baleType ?: ""
                    }
                }
                when (effMode) {
                    2 -> {
                        OutlinedTextField(baleType, { baleType = it }, label = { Text("نوع البالة") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        NumField(bales, { bales = it }, "عدد البالات", Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumField(perBale, { perBale = it }, "قطعة/البالة", Modifier.weight(1f))
                            NumField(plen, { plen = it }, "متر/القطعة", Modifier.weight(1f))
                        }
                        Text(
                            "= ${piecesCount.r3().plain()} قطعة = ${meters.r3().plain()} متر",
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary
                        )
                    }
                    1 -> PieceFields(pieces, { pieces = it }, plen, { plen = it }, meters)
                    else -> NumField(qty, { qty = it }, "الكمية المشتراة (${fabric?.unit ?: ""})")
                }
                CurrencyChips(currency) { currency = it }
                NumField(cost, { cost = it }, "سعر ${if (canPieces) "المتر" else "الوحدة"} بال${currencyName(currency)}")
                if (effMode == 2 && cost.toD() > 0 && meters > 0) {
                    Text(
                        "${cost.toD().plain()} × ${plen.toD().plain()} متر × ${perBale.toD().plain()} قطعة × ${bales.toD().plain()} بالة",
                        style = MaterialTheme.typography.bodySmall
                    )
                    val pieceCost = cost.toD() * plen.toD()
                    Text(
                        "سعر القطعة: ${pieceCost.money(currency)}  •  سعر البالة: ${(pieceCost * perBale.toD()).money(currency)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
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
                        pieces = piecesCount,
                        bales = if (effMode == 2) bales.toD() else 0.0,
                        baleType = if (effMode == 2) baleType.trim() else ""
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
