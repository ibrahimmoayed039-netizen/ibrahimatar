package com.fabric.ledger.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.fabric.ledger.data.Customer
import com.fabric.ledger.data.Overdue
import com.fabric.ledger.data.StatementRow
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** رقم دولي بأرقام فقط لرابط wa.me؛ فارغ إذا تعذّر تحديده */
fun normalizePhone(raw: String, countryCode: String): String {
    val d = raw.norm().filter { it.isDigit() || it == '+' }
    return when {
        d.isEmpty() -> ""
        d.startsWith("+") -> d.drop(1).filter { it.isDigit() }
        d.startsWith("00") -> d.drop(2)
        d.startsWith("0") -> if (countryCode.isNotBlank()) countryCode.filter { it.isDigit() } + d.drop(1) else ""
        else -> d
    }
}

fun reminderMessage(shop: String, name: String, balance: Map<String, Double>, od: Map<String, Overdue>?, graceDays: Int, attached: Boolean): String {
    val lines = mutableListOf("السلام عليكم $name،")
    if (attached) lines.add("مرفق كشف حسابكم.")
    if (balance.hasDebt()) {
        val debts = balance.filterValues { it > 0.005 }
        lines.add("الرصيد المستحق: " + CURRENCIES.filter { it in debts }.joinToString(" + ") { debts[it]!!.money(it) })
        val odText = od.overdueText()
        if (odText != null) {
            lines.add("منه متأخر: $odText (مضى أكثر من $graceDays يوماً).")
            lines.add("نرجو التفضل بالتسديد في أقرب وقت.")
        } else if (!attached) {
            lines.add("نرجو التفضل بالتسديد.")
        }
    } else if (!attached) {
        lines.add("لا يوجد رصيد مستحق عليكم حالياً.")
    }
    lines.add("شكراً لكم" + if (shop.isNotBlank()) " — $shop" else "")
    return lines.joinToString("\n")
}

object Share {
    /** مشاركة ملف PDF عبر واتساب (أو واتساب للأعمال)، وإلا قائمة المشاركة العامة */
    fun pdf(context: Context, file: File, message: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        fun build(pkg: String?) = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, message)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (pkg != null) setPackage(pkg)
        }
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
            try { context.startActivity(build(pkg)); return } catch (_: ActivityNotFoundException) { }
        }
        context.startActivity(Intent.createChooser(build(null), "مشاركة كشف الحساب"))
    }

    /** رسالة نصية: مباشرة إلى رقم الزبون إن عُرف، وإلا قائمة المشاركة */
    fun whatsappText(context: Context, number: String, message: String) {
        if (number.isNotEmpty()) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number?text=${Uri.encode(message)}")))
                return
            } catch (_: ActivityNotFoundException) { }
        }
        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, message) }
        context.startActivity(Intent.createChooser(send, "إرسال تذكير"))
    }
}

object StatementPdf {
    private const val PW = 595
    private const val PH = 842
    private const val LEFT = 36f
    private const val RIGHT = 559f
    private const val BOTTOM = 795f

    // الأعمدة من اليمين إلى اليسار: (يسار، يمين)
    private val colDate = 481f to 559f
    private val colDesc = 306f to 481f
    private val colDebit = 216f to 306f
    private val colCredit = 126f to 216f
    private val colBal = 36f to 126f

    private fun layout(t: String, w: Int, p: TextPaint, a: Layout.Alignment): StaticLayout =
        StaticLayout.Builder.obtain(t, 0, t.length, p, w)
            .setAlignment(a)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
            .build()

    private class State(val doc: PdfDocument) {
        var page: PdfDocument.Page? = null
        var no = 0
        var y = 0f
        val canvas: Canvas get() = page!!.canvas
    }

    fun create(
        context: Context, shop: String, customer: Customer, rows: List<StatementRow>,
        balance: Map<String, Double>, overdue: Map<String, Overdue>?, graceDays: Int
    ): File {
        val st = State(PdfDocument())
        val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = Color.BLACK }
        val bold = TextPaint(text).apply { isFakeBoldText = true }
        val big = TextPaint(bold).apply { textSize = 20f }
        val mid = TextPaint(bold).apply { textSize = 13f }
        val red = TextPaint(bold).apply { color = Color.rgb(198, 40, 40) }
        val small = TextPaint(text).apply { textSize = 8.5f; color = Color.DKGRAY }
        val teal = Color.rgb(11, 79, 92)
        val fill = Paint().apply { color = teal; style = Paint.Style.FILL }
        val zebra = Paint().apply { color = Color.rgb(242, 247, 247); style = Paint.Style.FILL }
        val white = TextPaint(bold).apply { color = Color.WHITE }
        val whiteMid = TextPaint(white).apply { textSize = 14f }
        val whiteBig = TextPaint(white).apply { textSize = 24f }
        val whiteSmall = TextPaint(text).apply { color = Color.rgb(214, 238, 240) }
        val valueP = TextPaint(bold).apply { textSize = 17f; color = teal }
        val valueRed = TextPaint(bold).apply { textSize = 17f; color = Color.rgb(198, 40, 40) }
        val hair = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.6f }
        val dateFmt = SimpleDateFormat("yyyy/MM/dd", Locale.US)

        fun footer() {
            val l = layout("صفحة ${st.no}", PW - 72, small, Layout.Alignment.ALIGN_CENTER)
            st.canvas.save(); st.canvas.translate(LEFT, 812f); l.draw(st.canvas); st.canvas.restore()
        }
        fun startPage() {
            st.page?.let { footer(); st.doc.finishPage(it) }
            st.no++
            st.page = st.doc.startPage(PdfDocument.PageInfo.Builder(PW, PH, st.no).create())
            st.y = 36f
        }
        /** فقرة بعرض الصفحة، تُحاذى يميناً */
        fun block(t: String, p: TextPaint, gap: Float = 4f) {
            val l = layout(t, (RIGHT - LEFT).toInt(), p, Layout.Alignment.ALIGN_NORMAL)
            st.canvas.save(); st.canvas.translate(LEFT, st.y); l.draw(st.canvas); st.canvas.restore()
            st.y += l.height + gap
        }
        fun cell(t: String, col: Pair<Float, Float>, p: TextPaint, a: Layout.Alignment, y: Float) {
            val l = layout(t, (col.second - col.first - 6f).toInt(), p, a)
            st.canvas.save(); st.canvas.translate(col.first + 3f, y); l.draw(st.canvas); st.canvas.restore()
        }
        fun tableHeader() {
            st.canvas.drawRect(LEFT, st.y, RIGHT, st.y + 20f, fill)
            val c = Layout.Alignment.ALIGN_CENTER
            cell("التاريخ", colDate, white, c, st.y + 4f)
            cell("البيان", colDesc, white, c, st.y + 4f)
            cell("مدين", colDebit, white, c, st.y + 4f)
            cell("دائن", colCredit, white, c, st.y + 4f)
            cell("الرصيد", colBal, white, c, st.y + 4f)
            st.y += 22f
        }
        fun money(v: Double, c: String) = if (v > 0.005) v.money(c) else "-"

        startPage()
        // ترويسة متدرجة بشريط ذهبي
        val band = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, PW.toFloat(), 0f, Color.rgb(11, 79, 92), Color.rgb(26, 138, 150), Shader.TileMode.CLAMP)
        }
        st.canvas.drawRect(0f, 0f, PW.toFloat(), 118f, band)
        st.canvas.drawRect(0f, 118f, PW.toFloat(), 122f, Paint().apply { color = Color.rgb(217, 130, 43) })
        st.y = 20f
        if (shop.isNotBlank()) block(shop, whiteMid, 2f)
        block("كشف حساب", whiteBig, 4f)
        block("الزبون: ${customer.name}" + if (customer.phone.isNotBlank()) "   |   ${customer.phone}" else "", white, 2f)
        block("تاريخ الكشف: ${dateFmt.format(Date())}", whiteSmall, 0f)
        st.y = 140f

        /** بطاقة مجسمة: ظل متدرج + حافة ملونة */
        fun card(l: Float, r: Float, label: String, value: String, vp: TextPaint, accent: Int) {
            val top = st.y
            val h = 58f
            for (i in 4 downTo 1) {
                val sh = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(14 + (4 - i) * 6, 0, 60, 70) }
                st.canvas.drawRoundRect(RectF(l + i * 0.6f, top + i * 1.6f, r + i * 0.6f, top + h + i * 1.6f), 12f, 12f, sh)
            }
            val rect = RectF(l, top, r, top + h)
            st.canvas.drawRoundRect(rect, 12f, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
            st.canvas.drawRoundRect(rect, 12f, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 1.3f; color = accent
            })
            cell(label, l to r, small, Layout.Alignment.ALIGN_CENTER, top + 9f)
            cell(value, l to r, vp, Layout.Alignment.ALIGN_CENTER, top + 26f)
        }
        val odText = overdue.overdueText()
        if (odText != null) {
            card(291f, RIGHT, "الرصيد المستحق", balance.balanceText(), valueP, teal)
            card(LEFT, 277f, "منه متأخر (أكثر من $graceDays يوماً)", odText, valueRed, Color.rgb(198, 40, 40))
        } else {
            card(LEFT, RIGHT, "الرصيد المستحق", balance.balanceText(), valueP, teal)
        }
        st.y += 80f
        tableHeader()

        val running = mutableMapOf<String, Double>()
        val center = Layout.Alignment.ALIGN_CENTER
        rows.forEachIndexed { idx, r ->
            running[r.currency] = (running[r.currency] ?: 0.0) + r.debit - r.credit
            val desc = r.title + if (r.detail.isNotBlank()) ": ${r.detail}" else ""
            val cells = listOf(
                Triple(dateFmt.format(Date(r.date)), colDate, center),
                Triple(desc, colDesc, Layout.Alignment.ALIGN_NORMAL),
                Triple(money(r.debit, r.currency), colDebit, center),
                Triple(money(r.credit, r.currency), colCredit, center),
                Triple((running[r.currency] ?: 0.0).money(r.currency), colBal, center)
            )
            val layouts = cells.map { (t, col, a) -> layout(t, (col.second - col.first - 6f).toInt(), text, a) }
            val h = layouts.maxOf { it.height } + 8f
            if (st.y + h > BOTTOM) { startPage(); tableHeader() }
            if (idx % 2 == 1) st.canvas.drawRect(LEFT, st.y, RIGHT, st.y + h, zebra)
            layouts.forEachIndexed { i, l ->
                st.canvas.save(); st.canvas.translate(cells[i].second.first + 3f, st.y + 4f); l.draw(st.canvas); st.canvas.restore()
            }
            st.y += h
            st.canvas.drawLine(LEFT, st.y, RIGHT, st.y, hair)
        }
        if (rows.isEmpty()) block("لا توجد حركات.", text)
        st.y += 10f
        if (st.y > BOTTOM) startPage()
        block("الرصيد النهائي: ${running.toMap().balanceText()}", mid)

        st.page?.let { footer(); st.doc.finishPage(it) }
        val dir = File(context.cacheDir, "statements").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val safe = customer.name.replace(Regex("[\\/:*?\"<>|\\s]+"), "_")
        val file = File(dir, "كشف-حساب-$safe-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.pdf")
        FileOutputStream(file).use { st.doc.writeTo(it) }
        st.doc.close()
        return file
    }
}
