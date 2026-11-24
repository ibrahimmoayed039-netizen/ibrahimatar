package com.fabric.ledger.ui

import com.fabric.ledger.data.Fabric
import com.fabric.ledger.data.Overdue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.util.Locale

/** العملتان المدعومتان: IQD دينار عراقي، USD دولار أمريكي */
val CURRENCIES = listOf("IQD", "USD")

fun currencyName(code: String) = if (code == "USD") "دولار" else "دينار"

/** تنسيق مبلغ بعملته: الدينار بلا كسور (1,250,000 د.ع) والدولار بكسرين ($1,250.00) */
fun Double.money(code: String): String =
    if (code == "USD") "\$" + String.format(Locale.US, "%,.2f", this)
    else String.format(Locale.US, "%,.0f", this) + " د.ع"

/** هل في الرصيد دين (موجب) بأي عملة؟ */
fun Map<String, Double>?.hasDebt(): Boolean = this?.values?.any { it > 0.005 } == true

/** نص الرصيد لكل عملة ذات رصيد: "2,350,000 د.ع  +  $120.00"، أو 0 إن لم يوجد */
fun Map<String, Double>?.balanceText(): String {
    val parts = CURRENCIES.mapNotNull { c -> val v = this?.get(c) ?: 0.0; if (kotlin.math.abs(v) > 0.005) v.money(c) else null }
    return if (parts.isEmpty()) "0" else parts.joinToString("  +  ")
}

/** نص الدين المتأخر لكل عملة، أو null إن لم يوجد متأخر */
fun Map<String, Overdue>?.overdueText(): String? {
    val parts = CURRENCIES.mapNotNull { c -> val v = this?.get(c)?.amount ?: 0.0; if (v > 0.005) v.money(c) else null }
    return if (parts.isEmpty()) null else parts.joinToString("  +  ")
}

/** سعر الصنف بعملة الفاتورة (0 = غير محدد) */
fun Fabric.priceFor(currency: String, wholesale: Boolean): Double =
    if (currency == "USD") (if (wholesale) wholesalePriceUsd else retailPriceUsd)
    else (if (wholesale) wholesalePrice else retailPrice)

/** رقم بلا رمز العملة للجداول: الدينار بلا كسور والدولار بكسرين */
fun Double.num(code: String): String =
    if (code == "USD") String.format(Locale.US, "%,.2f", this) else String.format(Locale.US, "%,.0f", this)

/** نص المدة منذ آخر دفعة: "آخر دفعة قبل 12 يوماً" أو "لم يسدد أي دفعة" إن لم توجد */
fun lastPaymentText(last: Long?, now: Long = System.currentTimeMillis()): String {
    if (last == null) return "لم يسدد أي دفعة بعد"
    val days = ((now - last) / 86_400_000L).toInt().coerceAtLeast(0)
    return when (days) {
        0 -> "آخر دفعة: اليوم"
        1 -> "آخر دفعة: أمس"
        else -> "متأخر عن آخر دفعة $days يوماً"
    }
}

/** يعرض الأرقام المكتوبة بفواصل الآلاف (1,250,000) أثناء الكتابة دون تغيير النص المخزَّن */
class ThousandsTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val dot = raw.indexOf('.')
        val intPart = if (dot >= 0) raw.substring(0, dot) else raw
        val rest = if (dot >= 0) raw.substring(dot) else ""
        val plain = intPart.all { it in '0'..'9' } && rest.drop(1).all { it in '0'..'9' }
        if (!plain || intPart.length < 4) return TransformedText(text, OffsetMapping.Identity)
        val len = intPart.length
        val sb = StringBuilder()
        intPart.forEachIndexed { i, c -> if (i > 0 && (len - i) % 3 == 0) sb.append(','); sb.append(c) }
        val formatted = sb.toString() + rest
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val o = offset.coerceIn(0, raw.length)
                val commas = (1..minOf(o, len - 1)).count { (len - it) % 3 == 0 }
                return o + commas
            }
            override fun transformedToOriginal(offset: Int): Int {
                val t = offset.coerceIn(0, formatted.length)
                return t - formatted.take(t).count { it == ',' }
            }
        }
        return TransformedText(AnnotatedString(formatted), mapping)
    }
}
