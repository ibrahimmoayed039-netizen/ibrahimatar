package com.fabric.ledger.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** شاشة القفل: تغطي التطبيق كله وتمنع اللمس عما تحتها حتى يُدخَل الرمز الصحيح */
@Composable
fun LockScreen(pinLen: Int, onSubmit: (String) -> Boolean) {
    val len = if (pinLen in 4..6) pinLen else 4
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var failed by remember { mutableIntStateOf(0) }
    var waitUntil by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val waiting = now < waitUntil
    LaunchedEffect(waitUntil) {
        while (System.currentTimeMillis() < waitUntil) { now = System.currentTimeMillis(); delay(500) }
        now = System.currentTimeMillis()
    }
    val context = LocalContext.current
    BackHandler { (context as? Activity)?.moveTaskToBack(true) }

    fun press(d: String) {
        if (waiting || entered.length >= len) return
        error = false
        entered += d
        if (entered.length == len && !onSubmit(entered)) {
            entered = ""
            error = true
            failed++
            if (failed >= 5) { failed = 0; waitUntil = System.currentTimeMillis() + 30_000; now = System.currentTimeMillis() }
        }
    }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Teal, TealLight)))
            // يمنع وصول اللمس إلى ما تحت شاشة القفل
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .systemBarsPadding().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(44.dp))
            Text("دفتر الأقمشة", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                when {
                    waiting -> "محاولات خاطئة كثيرة، انتظر ${(waitUntil - now) / 1000 + 1} ثانية"
                    error -> "الرمز غير صحيح"
                    else -> "أدخل الرمز السري"
                },
                color = if (error || waiting) Color(0xFFFFCDD2) else Color.White.copy(alpha = 0.85f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(len) { i ->
                    Box(
                        Modifier.size(16.dp).clip(CircleShape)
                            .background(if (i < entered.length) Color.White else Color.White.copy(alpha = 0.3f))
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            // لوحة الأرقام تبقى بترتيب 1-2-3 من اليسار رغم أن التطبيق RTL
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "⌫")).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            row.forEach { k ->
                                Box(
                                    Modifier.size(72.dp).clip(CircleShape)
                                        .background(if (k.isEmpty()) Color.Transparent else Color.White.copy(alpha = 0.16f))
                                        .clickable(enabled = k.isNotEmpty() && !waiting) {
                                            if (k == "⌫") { if (entered.isNotEmpty()) entered = entered.dropLast(1) } else press(k)
                                        },
                                    contentAlignment = Alignment.Center
                                ) { Text(k, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** إعداد القفل: تفعيل برقم، تغيير الرقم، أو إيقاف القفل (يتطلب الرقم الحالي) */
@Composable
fun LockSettingsDialog(
    enabled: Boolean,
    onEnable: (String) -> Unit,
    onChange: (String, String) -> Boolean,
    onDisable: (String) -> Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    // 0 خيارات (القفل مفعّل)، 1 تعيين/تغيير الرمز، 2 إيقاف القفل
    var mode by remember { mutableIntStateOf(if (enabled) 0 else 1) }
    var old by remember { mutableStateOf("") }
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    fun clean(s: String) = s.filter { it.isDigit() }.map { '0' + it.digitToInt() }.joinToString("").take(6)

    @Composable
    fun PinField(value: String, label: String, onValue: (String) -> Unit) {
        OutlinedTextField(
            value = value, onValueChange = { onValue(clean(it)); err = null }, singleLine = true,
            label = { Text(label) }, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(when (mode) { 0 -> "قفل التطبيق"; 1 -> if (enabled) "تغيير الرمز السري" else "تفعيل قفل التطبيق"; else -> "إيقاف القفل" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (mode) {
                    0 -> {
                        Text("القفل مفعّل: يُطلب الرمز عند فتح التطبيق وبعد غيابه نحو نصف دقيقة.")
                        TextButton(onClick = { mode = 1 }) { Text("تغيير الرمز") }
                        TextButton(onClick = { mode = 2 }) { Text("إيقاف القفل", color = MaterialTheme.colorScheme.error) }
                    }
                    1 -> {
                        if (enabled) PinField(old, "الرمز الحالي") { old = it }
                        PinField(p1, "الرمز الجديد (4 إلى 6 أرقام)") { p1 = it }
                        PinField(p2, "تأكيد الرمز") { p2 = it }
                        Text(
                            "تنبيه: إن نسيت الرمز لا يمكن استرجاعه، وقد تضطر لحذف التطبيق وفقدان بياناته. احفظ نسخة احتياطية أولاً. " +
                                "القفل يمنع الفتح العادي فقط ولا يشفّر البيانات.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    else -> PinField(old, "أدخل الرمز الحالي") { old = it }
                }
                err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (mode == 0) TextButton(onClick = onDismiss) { Text("إغلاق") }
            else TextButton(onClick = {
                if (mode == 1) {
                    when {
                        p1.length < 4 -> err = "الرمز يجب أن يكون 4 أرقام على الأقل"
                        p1 != p2 -> err = "الرمزان غير متطابقين"
                        enabled && !onChange(old, p1) -> err = "الرمز الحالي غير صحيح"
                        else -> {
                            if (!enabled) onEnable(p1)
                            Toast.makeText(context, if (enabled) "تم تغيير الرمز" else "تم تفعيل القفل", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    }
                } else {
                    if (onDisable(old)) {
                        Toast.makeText(context, "تم إيقاف القفل", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    } else err = "الرمز غير صحيح"
                }
            }) { Text("حفظ") }
        },
        dismissButton = { if (mode != 0) TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

/** طلب الرمز السري للتطبيق لإظهار معلومة مخفية */
@Composable
fun PinPromptDialog(title: String, onVerify: (String) -> Boolean, onSuccess: () -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pin, onValueChange = { pin = it.filter { c -> c.isDigit() }.take(6); err = false }, singleLine = true,
                    label = { Text("الرمز السري") }, isError = err, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                if (err) Text("الرمز غير صحيح", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (onVerify(pin)) onSuccess() else err = true }) { Text("إظهار") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
