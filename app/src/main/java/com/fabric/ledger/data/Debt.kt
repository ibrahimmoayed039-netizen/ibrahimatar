package com.fabric.ledger.data

/** سطر في كشف الحساب: الفاتورة = مدين (الإجمالي) ودائن (المدفوع وقت البيع)، والدفعة = دائن */
data class StatementRow(
    val date: Long,
    val title: String,
    val detail: String,
    val debit: Double,
    val credit: Double,
    val currency: String = "IQD"
)

data class Overdue(val amount: Double, val oldestDays: Int)

object Debt {
    private const val DAY = 86_400_000L

    /**
     * الدين المتأخر: تُوزَّع الدفعات على أقدم الفواتير أولاً (FIFO)،
     * وما يبقى من فواتير مضى عليها أكثر من graceDays يوماً يُعدّ متأخراً.
     */
    fun overdue(sales: List<Sale>, payments: List<Payment>, graceDays: Int, now: Long = System.currentTimeMillis()): Overdue {
        var pool = payments.sumOf { it.amount }
        var amount = 0.0
        var oldest = 0
        sales.sortedBy { it.date }.forEach { s ->
            var rem = s.total - s.paid
            if (rem <= 0.005) return@forEach
            val used = minOf(pool, rem)
            pool -= used
            rem -= used
            if (rem > 0.005) {
                val days = ((now - s.date) / DAY).toInt()
                if (days > graceDays) {
                    amount += rem
                    oldest = maxOf(oldest, days)
                }
            }
        }
        return Overdue(amount, oldest)
    }
}
