package com.fabric.ledger.ui

import java.util.Locale

/** العملتان المدعومتان: IQD دينار عراقي، USD دولار أمريكي */
val CURRENCIES = listOf("IQD", "USD")

fun currencyName(code: String) = if (code == "USD") "دولار" else "دينار"

/** تنسيق مبلغ بعملته: الدينار بلا كسور (1,250,000 د.ع) والدولار بكسرين ($1,250.00) */
fun Double.money(code: String): String =
    if (code == "USD") "\$" + String.format(Locale.US, "%,.2f", this)
    else String.format(Locale.US, "%,.0f", this) + " د.ع"
