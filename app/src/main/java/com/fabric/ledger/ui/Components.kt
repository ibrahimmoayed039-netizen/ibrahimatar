package com.fabric.ledger.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.fabric.ledger.data.AutoBackup
import com.fabric.ledger.data.AutoBackupState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
/** رقم مقروء: حتى 3 خانات عشرية بلا أصفار زائدة (يمنع 12.350000000000001) */
fun Double.plain(): String =
    if (isNaN() || isInfinite()) "0"
    else java.math.BigDecimal.valueOf(this).setScale(3, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
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

/** وصف مختصر لطريقة الصنف: بالة (قطع × طول) أو طول */
fun Fabric.kindText(): String? = when {
    piecesPerBale > 0 && rollLength > 0 -> "بالة: ${piecesPerBale.plain()} قطعة × ${rollLength.plain()} متر"
    rollLength > 0 -> "طول: ${rollLength.plain()} متر"
    else -> null
}

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
        // أرقام وفاصلة عشرية فقط (يمنع الإشارة السالبة وأي نص)
        value = value, onValueChange = { onChange(it.filter { c -> c.isDigit() || c == '.' || c == ',' || c == '\u066B' }) }, label = { Text(label) },
        singleLine = true, modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

@Composable
fun FabricDialog(initial: Fabric?, onSave: (Fabric) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: "") }
    var color by remember { mutableStateOf(initial?.color ?: "") }
    // 0 = بالة، 1 = طول  (الصنف ذو القطع داخل البالة = بالة، وغيره = طول)
    var kind by remember { mutableIntStateOf(if (initial == null || initial.piecesPerBale > 0) 0 else 1) }
    var perBale by remember { mutableStateOf(initial?.piecesPerBale?.takeIf { it > 0 }?.plain() ?: "") }
    var rollLen by remember { mutableStateOf(initial?.rollLength?.takeIf { it > 0 }?.plain() ?: "") }
    var buyCur by remember { mutableStateOf(initial?.purchaseCurrency ?: "USD") }
    var buyPrice by remember { mutableStateOf(initial?.purchasePrice?.takeIf { it > 0 }?.plain() ?: "") }
    var qty by remember { mutableStateOf("") }   // عدد البالات أو عدد الأطوال
    var stock by remember { mutableStateOf(initial?.stock?.plain() ?: "") }   // تصحيح يدوي عند التعديل فقط
    var minStock by remember { mutableStateOf(initial?.minStock?.takeIf { it > 0 }?.plain() ?: "") }
    // أسعار البيع اختيارية: السعر يتغير عادة من بيع لآخر فيُكتب عند كل عملية بيع
    var retail by remember { mutableStateOf(initial?.retailPrice?.takeIf { it > 0 }?.plain() ?: "") }
    var wholesale by remember { mutableStateOf(initial?.wholesalePrice?.takeIf { it > 0 }?.plain() ?: "") }
    var retailUsd by remember { mutableStateOf(initial?.retailPriceUsd?.takeIf { it > 0 }?.plain() ?: "") }
    var wholesaleUsd by remember { mutableStateOf(initial?.wholesalePriceUsd?.takeIf { it > 0 }?.plain() ?: "") }
    var showSell by remember {
        mutableStateOf(initial != null && listOf(initial.retailPrice, initial.wholesalePrice, initial.retailPriceUsd, initial.wholesalePriceUsd).any { it > 0 })
    }
    var err by remember { mutableStateOf<String?>(null) }
    var imagePath by remember { mutableStateOf(initial?.imagePath ?: "") }
    val created = remember { mutableStateListOf<String>() }
    /** حذف الصور الجديدة غير المستخدمة (ملفات أُنشئت ثم أُلغيت) */
    fun cleanup(keep: String) { created.filter { it != keep }.forEach { deleteFabricImage(it) } }
    val dismiss = { cleanup(initial?.imagePath ?: ""); onDismiss() }
    var confirmDel by remember { mutableStateOf(false) }

    val perB = perBale.toD()
    val len = rollLen.toD()
    val q = qty.toD()
    /** الكمية الكلية بالمتر محسوبة من العدد المكتوب (بالات أو أطوال) */
    val autoStock: Double? = when {
        q <= 0 || len <= 0 -> null
        kind == 0 -> if (perB > 0) (q * perB * len).r3() else null
        else -> (q * len).r3()
    }

    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(if (initial == null) "إضافة صنف" else "تعديل صنف") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FabricImagePicker(imagePath, name) { np -> if (np.isNotBlank()) created.add(np); imagePath = np }
                OutlinedTextField(name, { name = it; err = null }, label = { Text("اسم القماش") }, singleLine = true)
                OutlinedTextField(type, { type = it }, label = { Text("النوع (قطن، حرير، كتان...)") }, singleLine = true)
                OutlinedTextField(color, { color = it }, label = { Text("اللون") }, singleLine = true)

                Text("طريقة الشراء والتخزين", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = kind == 0, onClick = { kind = 0; err = null }, label = { Text("بالة") })
                    FilterChip(selected = kind == 1, onClick = { kind = 1; err = null }, label = { Text("طول") })
                }
                if (initial?.unit == "ثوب") {
                    Text(
                        "كان هذا الصنف مسجلاً بوحدة «ثوب». بعد الحفظ تُعتبر الكمية بالأمتار، فراجعها.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
                    )
                }
                if (kind == 0) {
                    NumField(perBale, { perBale = it; err = null }, "عدد القطع داخل البالة")
                    NumField(rollLen, { rollLen = it; err = null }, "طول القطعة الواحدة بالمتر")
                } else {
                    NumField(rollLen, { rollLen = it; err = null }, "عدد الأمتار في الطول الواحد")
                }

                Text("سعر الشراء", fontWeight = FontWeight.Bold)
                CurrencyChips(buyCur) { buyCur = it }
                NumField(buyPrice, { buyPrice = it }, "سعر شراء المتر بال${currencyName(buyCur)}")
                if (buyPrice.toD() > 0 && len > 0) {
                    val pieceCost = buyPrice.toD() * len
                    if (kind == 0) {
                        Text("سعر شراء القطعة: ${pieceCost.money(buyCur)}", style = MaterialTheme.typography.bodySmall)
                        if (perB > 0) Text("سعر شراء البالة: ${(pieceCost * perB).money(buyCur)}", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("سعر شراء الطول الواحد: ${pieceCost.money(buyCur)}", style = MaterialTheme.typography.bodySmall)
                    }
                }

                NumField(qty, { qty = it; err = null }, if (kind == 0) "عدد البالات" else "الكمية (عدد الأطوال)")
                if (autoStock != null) {
                    Text("= ${autoStock.plain()} متر", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                if (initial != null) {
                    Text(
                        "المخزون الحالي: ${initial.stock.plain()} متر. اترك الكمية أعلاه فارغة لإبقائه، أو اكتب عدداً جديداً ليحل محله.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    NumField(stock, { stock = it }, "تصحيح المخزون يدوياً بالمتر (اختياري)")
                }
                NumField(minStock, { minStock = it }, "نبّهني عند نزول الكمية إلى (متر) — اتركه فارغاً لإلغاء التنبيه")

                TextButton(onClick = { showSell = !showSell }) {
                    Text(if (showSell) "إخفاء أسعار البيع" else "أسعار البيع الافتراضية (اختياري)")
                }
                if (showSell) {
                    Text(
                        "سعر البيع يختلف عادة من بيع لآخر. اترك هذه الحقول فارغة واكتب السعر عند كل عملية بيع.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    NumField(retail, { retail = it }, "سعر المفرق بالدينار (اختياري)")
                    NumField(wholesale, { wholesale = it }, "سعر الجملة بالدينار (اختياري)")
                    NumField(retailUsd, { retailUsd = it }, "سعر المفرق بالدولار (اختياري)")
                    NumField(wholesaleUsd, { wholesaleUsd = it }, "سعر الجملة بالدولار (اختياري)")
                }
                err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    name.isBlank() -> err = "اكتب اسم القماش"
                    kind == 0 && perB <= 0 -> err = "اكتب عدد القطع داخل البالة"
                    kind == 0 && len <= 0 -> err = "اكتب طول القطعة الواحدة بالمتر"
                    kind == 1 && q > 0 && len <= 0 -> err = "اكتب عدد الأمتار في الطول الواحد"
                    else -> {
                        cleanup(imagePath)
                        initial?.imagePath?.takeIf { it.isNotBlank() && it != imagePath }?.let { deleteFabricImage(it) }
                        onSave(
                            Fabric(
                                id = initial?.id ?: 0, name = name.trim(), type = type.trim(), color = color.trim(),
                                unit = "متر",
                                retailPrice = retail.toD(), wholesalePrice = wholesale.toD(),
                                retailPriceUsd = retailUsd.toD(), wholesalePriceUsd = wholesaleUsd.toD(),
                                stock = autoStock ?: stock.toD(), minStock = minStock.toD(),
                                rollLength = len,
                                baleType = if (kind == 0) initial?.baleType ?: "" else "",
                                piecesPerBale = if (kind == 0) perB else 0.0,
                                purchasePrice = buyPrice.toD(), purchaseCurrency = buyCur,
                                sealedBales = if (kind == 0) { if (autoStock != null) q else initial?.sealedBales ?: 0.0 } else 0.0,
                                sealedMeters = if (kind == 0) { if (autoStock != null) autoStock else initial?.sealedMeters ?: 0.0 } else 0.0,
                                imagePath = imagePath
                            )
                        )
                    }
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
fun CustomerDialog(onSave: (Customer) -> Unit, onDismiss: () -> Unit, initial: Customer? = null) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var wholesale by remember { mutableStateOf(initial?.isWholesale ?: false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "إضافة زبون" else "تعديل الزبون") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("الاسم") }, singleLine = true)
                OutlinedTextField(
                    phone, { phone = it }, label = { Text("رقم الهاتف") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = wholesale, onCheckedChange = { wholesale = it })
                    Spacer(Modifier.width(8.dp))
                    Text("زبون جملة")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) onSave(Customer(id = initial?.id ?: 0, name = name.trim(), phone = phone.trim(), isWholesale = wholesale))
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

/** حالة الفاتورة: نقداً (مسدَّدة كاملة) / أجل (لم يُدفع شيء) / جزئي (دُفع جزء) */
class SaleStatus(val label: String, val color: Color)

fun saleStatus(total: Double, paid: Double): SaleStatus = when {
    total - paid <= 0.005 -> SaleStatus("نقداً", Color(0xFF2E7D32))
    paid <= 0.005 -> SaleStatus("أجل", Copper)
    else -> SaleStatus("جزئي", Color(0xFF8A5A00))
}

@Composable
fun StatusBadge(s: SaleStatus) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(s.color).padding(horizontal = 12.dp, vertical = 3.dp)) {
        Text(s.label, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun InvoiceRow(cells: List<String>, bold: Boolean, bg: Color) {
    val w = listOf(2.2f, 1.8f, 1.4f, 1.7f)
    Row(Modifier.fillMaxWidth().background(bg).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        cells.forEachIndexed { i, t ->
            Text(
                t, Modifier.weight(w[i]), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.bodySmall, textAlign = if (i == 0) TextAlign.Start else TextAlign.Center
            )
        }
    }
}

/** جدول القائمة: الصنف | الكمية | السعر | المبلغ */
@Composable
fun InvoiceTable(rows: List<List<String>>) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))) {
        InvoiceRow(listOf("الصنف", "الكمية", "السعر", "المبلغ"), true, MaterialTheme.colorScheme.primaryContainer)
        rows.forEachIndexed { i, r -> InvoiceRow(r, false, if (i % 2 == 0) Color.White else Mist) }
    }
}

/** نص الكمية داخل خلية الجدول (سطران إن كانت بالقطعة) */
fun qtyCell(pieces: Double, quantity: Double, unit: String): String =
    if (pieces > 0.0) "${pieces.plain()} قطعة\n(${quantity.plain()} $unit)" else "${quantity.plain()} $unit"

@Composable
fun SaleDetailDialog(
    vm: MainViewModel, sale: Sale, customerName: String,
    onEdit: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit
) {
    val flow = remember(sale.id) { vm.itemsOf(sale.id) }
    val items by flow.collectAsStateWithLifecycle(initialValue = emptyList())
    var confirmDel by remember { mutableStateOf(false) }
    val cur = sale.currency
    val rem = (sale.total - sale.paid).coerceAtLeast(0.0)
    val st = saleStatus(sale.total, sale.paid)
    val frac = if (sale.total > 0.0) (sale.paid / sale.total).coerceIn(0.0, 1.0).toFloat() else 1f
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color.White, modifier = Modifier.fillMaxWidth(0.96f).heightIn(max = 640.dp)) {
            Column {
                // الترويسة: اسم الزبون + التاريخ + ختم الحالة (نقداً / أجل / جزئي)
                Row(
                    Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Teal, TealLight))).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("فاتورة", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                        Text(customerName.ifBlank { "بيع نقدي" }, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text(sale.date.dateStr(), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                    }
                    StatusBadge(st)
                }
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("العملة: ${currencyName(cur)}", style = MaterialTheme.typography.bodySmall)
                    if (items.isEmpty()) Text(if (sale.note.isBlank()) "قائمة بمبلغ فقط (بدون أصناف)" else "قائمة بمبلغ فقط: ${sale.note}")
                    else InvoiceTable(items.map {
                        listOf(it.fabricName, qtyCell(it.pieces, it.quantity, it.unit), it.unitPrice.num(cur), (it.quantity * it.unitPrice).num(cur))
                    })
                    // الإجمالي والمدفوع والمتبقي + شريط نسبة التسديد
                    Card3D(Modifier.fillMaxWidth(), tint = MaterialTheme.colorScheme.primaryContainer) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("الإجمالي", fontWeight = FontWeight.Bold); Text(sale.total.money(cur), fontWeight = FontWeight.Bold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("المدفوع"); Text(sale.paid.money(cur))
                            }
                            Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFD3E1E3))) {
                                Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(st.color))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("المتبقي (دين)", fontWeight = FontWeight.Bold)
                                Text(rem.money(cur), fontWeight = FontWeight.Bold, color = if (rem > 0.005) MaterialTheme.colorScheme.error else Color(0xFF2E7D32))
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Row {
                        TextButton(onClick = onEdit) { Text("تعديل") }
                        TextButton(onClick = { confirmDel = true }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                    }
                    TextButton(onClick = onDismiss) { Text("إغلاق") }
                }
            }
        }
    }
    if (confirmDel) ConfirmDialog(
        "حذف هذه الفاتورة؟ ستعود كمياتها إلى المخزن ويُخصم دينها من الزبون.",
        onYes = onDelete, onDismiss = { confirmDel = false }
    )
}

@Composable
fun AutoBackupDialog(
    state: AutoBackupState,
    onSave: (Boolean, String, String) -> Unit,
    onShareNow: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(state.enabled) }
    var mode by remember { mutableStateOf(state.mode) }
    var tree by remember { mutableStateOf(state.tree) }
    var err by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            tree = uri.toString(); err = ""
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("النسخ الاحتياطي التلقائي") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                    Spacer(Modifier.width(8.dp))
                    Text("نسخة أسبوعية تلقائية")
                }
                if (enabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = mode == "folder", onClick = { mode = "folder" }, label = { Text("إلى مجلد") })
                        FilterChip(selected = mode == "whatsapp", onClick = { mode = "whatsapp" }, label = { Text("إلى واتساب") })
                    }
                    if (mode == "folder") {
                        Text(
                            "تُحفظ نسخة كل أسبوع صامتةً عند فتح التطبيق، ويُبقى آخر 4 نسخ فقط. اختر مجلداً يُزامَن مع Google Drive لتصل النسخة لحسابك أيضاً.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedButton(onClick = { picker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (tree.isBlank()) "اختيار المجلد" else "المجلد: ${AutoBackup.folderName(context, Uri.parse(tree))}")
                        }
                    } else {
                        Text(
                            "كل أسبوع يظهر تذكير عند فتح التطبيق، وبضغطة واحدة تُرسل النسخة إلى واتساب (مثلاً إلى محادثتك مع نفسك أو مجموعة خاصة بك).",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (err.isNotBlank()) Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    if (state.last > 0L) "آخر نسخة تلقائية: ${state.last.dateStr()}" else "لم تُحفظ نسخة تلقائية بعد",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(onClick = onShareNow, modifier = Modifier.fillMaxWidth()) { Text("إرسال نسخة الآن عبر واتساب") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (enabled && mode == "folder" && tree.isBlank()) err = "اختر المجلد أولاً"
                else onSave(enabled, mode, tree)
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
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
    val paidVal = paid.toD().coerceIn(0.0, maxOf(total, 0.0))
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
