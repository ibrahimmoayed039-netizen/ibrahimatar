package com.fabric.ledger.ui

import com.fabric.ledger.data.Fabric
import com.fabric.ledger.data.Overdue
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
