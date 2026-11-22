package com.fabric.ledger.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fabric.ledger.MainViewModel
import com.fabric.ledger.data.Purchase

/** شاشة الصنف: المخزون الحالي، إجمالي المشترى والمباع، وسجل المشتريات */
@Composable
fun FabricScreen(vm: MainViewModel, id: Long, onBack: () -> Unit) {
    val fabrics by vm.fabrics.collectAsStateWithLifecycle()
    val purchases by vm.purchases.collectAsStateWithLifecycle()
    val totals by vm.totals.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var addDlg by remember { mutableStateOf(false) }
    var editPurchase by remember { mutableStateOf<Purchase?>(null) }
    var editDlg by remember { mutableStateOf(false) }
    var openDlg by remember { mutableStateOf(false) }
    var imgDlg by remember { mutableStateOf(false) }

    val f = fabrics.find { it.id == id }
    if (f == null) { Box(Modifier.fillMaxSize()); return }
    val t = totals[id]
    val mine = purchases.filter { it.fabricId == id }

    fun toast(ok: Boolean, msg: String) =
        Toast.makeText(context, if (ok) msg else "تعذرت العملية", Toast.LENGTH_SHORT).show()

    Scaffold(
        topBar = {
            GradientTopBar(
                title = { Text(f.name) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } },
                actions = { IconButton(onClick = { editDlg = true }) { Icon(Icons.Default.Edit, "تعديل الصنف") } }
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                HeroCard(Modifier.fillMaxWidth()) {
                    if (f.imagePath.isNotBlank()) FabricThumb(f.imagePath, f.name, 96.dp, Modifier.clickable { imgDlg = true }, decodePx = 400)
                    val sub = listOf(f.type, f.color).filter { it.isNotBlank() }.joinToString(" • ")
                    if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall)
                    Text("المخزون الحالي")
                    Text("${f.stock.plain()} ${f.unit}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    f.stockPiecesText()?.let { Text("$it  •  طول القطعة ${f.rollLength.plain()} متر", style = MaterialTheme.typography.bodySmall) }
                    if (f.isLow()) Text("⚠ وصل لحد التنبيه (${f.minStock.plain()} ${f.unit})", style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Card3D(Modifier.weight(1f)) {
                        Column(Modifier.padding(14.dp)) {
                            Text("إجمالي المشترى", style = MaterialTheme.typography.bodySmall)
                            Text("${(t?.purchased ?: 0.0).plain()} ${f.unit}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    Card3D(Modifier.weight(1f)) {
                        Column(Modifier.padding(14.dp)) {
                            Text("إجمالي المباع", style = MaterialTheme.typography.bodySmall)
                            Text("${(t?.sold ?: 0.0).plain()} ${f.unit}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (f.sealedBalesEff() > 0) item {
                Button3D(onClick = { openDlg = true }, modifier = Modifier.fillMaxWidth()) { Text("فتح بالة (${f.sealedBalesEff().plain()} مغلقة)") }
            }
            item {
                Button3D(onClick = { addDlg = true }, modifier = Modifier.fillMaxWidth()) { Text("+ تسجيل شراء") }
            }
            if (mine.isEmpty()) item { Text("لا توجد مشتريات مسجّلة لهذا الصنف بعد.") }
            else item { Text("اضغط على أي عملية شراء للتعديل أو الحذف", style = MaterialTheme.typography.bodySmall) }
            items(mine, key = { it.id }) { p ->
                Card3D(Modifier.fillMaxWidth(), onClick = { editPurchase = p }, tint = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("شراء", fontWeight = FontWeight.Bold)
                            Text(p.date.dateStr(), style = MaterialTheme.typography.bodySmall)
                            if (p.note.isNotBlank()) Text(p.note, style = MaterialTheme.typography.bodySmall)
                            if (p.unitCost > 0) Text("سعر ${if (f.supportsPieces()) "المتر" else "الوحدة"}: ${p.unitCost.money(p.currency)}  •  الإجمالي: ${p.total.money(p.currency)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("+ " + qtyText(p.pieces, p.quantity, f.unit, p.bales, p.baleType), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    if (addDlg) PurchaseDialog(
        initial = null, fabrics = fabrics, presetFabricId = id,
        onSave = { np -> addDlg = false; vm.addPurchase(np) { ok -> toast(ok, "تم تسجيل الشراء وإضافته للمخزون") } },
        onDelete = null, onDismiss = { addDlg = false }
    )
    editPurchase?.let { p ->
        PurchaseDialog(
            initial = p, fabrics = fabrics, presetFabricId = id,
            onSave = { np -> editPurchase = null; vm.editPurchase(np) { ok -> toast(ok, "تم التعديل") } },
            onDelete = { editPurchase = null; vm.deletePurchase(p.id) { ok -> toast(ok, "تم الحذف") } },
            onDismiss = { editPurchase = null }
        )
    }
    if (openDlg) OpenBaleDialog(
        f, onDismiss = { openDlg = false },
        onConfirm = { k -> openDlg = false; vm.openBales(id, k) { done -> toast(done > 0, "تم فتح ${done.plain()} ${f.baleName()}") } }
    )
    if (imgDlg) FabricImageDialog(f.imagePath, f.name) { imgDlg = false }
    if (editDlg) FabricDialog(
        initial = f,
        onSave = { vm.saveFabric(it); editDlg = false },
        onDelete = { editDlg = false; vm.deleteFabric(f) { ok -> toast(ok, "تم الحذف"); if (ok) onBack() } },
        onDismiss = { editDlg = false }
    )
}
