package com.fabric.ledger.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "fabrics")
data class Fabric(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String = "",
    val color: String = "",
    val unit: String = "متر",
    val retailPrice: Double = 0.0,
    val wholesalePrice: Double = 0.0,
    val stock: Double = 0.0,
    /** حد التنبيه: عند نزول المخزون إلى هذه الكمية أو أقل يظهر تنبيه (0 = بدون تنبيه) */
    @ColumnInfo(defaultValue = "0") val minStock: Double = 0.0,
    /** طول القطعة (الطول الكامل) بالمتر؛ يُستخدم كقيمة افتراضية عند الإدخال بالقطعة (0 = غير محدد) */
    @ColumnInfo(defaultValue = "0") val rollLength: Double = 0.0,
    /** أسعار الدولار (retailPrice وwholesalePrice أعلاه هي بالدينار)؛ 0 = لا يوجد سعر بالدولار */
    @ColumnInfo(defaultValue = "0") val retailPriceUsd: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val wholesalePriceUsd: Double = 0.0,
    /** نوع البالة (مثلاً: بالة كبيرة، بالة صغيرة) */
    @ColumnInfo(defaultValue = "''") val baleType: String = "",
    /** عدد القطع داخل البالة الواحدة (0 = غير محدد) */
    @ColumnInfo(defaultValue = "0") val piecesPerBale: Double = 0.0,
    /** سعر شراء المتر (الافتراضي عند تسجيل الشراء) بعملة purchaseCurrency؛ 0 = غير محدد */
    @ColumnInfo(defaultValue = "0") val purchasePrice: Double = 0.0,
    /** عملة سعر الشراء: IQD أو USD */
    @ColumnInfo(defaultValue = "'IQD'") val purchaseCurrency: String = "IQD",
    /** عدد البالات المغلقة (غير المفتوحة) الداخلة ضمن المخزون */
    @ColumnInfo(defaultValue = "0") val sealedBales: Double = 0.0,
    /** مجموع أمتار البالات المغلقة؛ المخزون (stock) يشملها، وما زاد عنها هو القطع المفتوحة */
    @ColumnInfo(defaultValue = "0") val sealedMeters: Double = 0.0,
    /** مسار صورة الصنف داخل مجلد التطبيق ("" = بدون صورة) */
    @ColumnInfo(defaultValue = "''") val imagePath: String = ""
)

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val isWholesale: Boolean = false
)

/** customerId = 0 يعني بيع نقدي */
@Entity(tableName = "sales")
data class Sale(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val date: Long,
    val total: Double,
    val paid: Double,
    /** ملاحظة/رقم القائمة؛ تُستخدم خصوصاً في «قائمة بمبلغ فقط» (بدون أصناف) */
    @ColumnInfo(defaultValue = "''") val note: String = "",
    /** عملة الفاتورة: IQD أو USD */
    @ColumnInfo(defaultValue = "'IQD'") val currency: String = "IQD"
)

@Entity(tableName = "sale_items")
data class SaleItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long = 0,
    val fabricId: Long,
    val fabricName: String,
    val unit: String,
    /** الكمية الإجمالية بالأمتار */
    val quantity: Double,
    val unitPrice: Double,
    /** عدد القطع إن أُدخل البيع بالقطعة (0 = أُدخل بالمتر)؛ quantity يبقى مجموع الأمتار */
    @ColumnInfo(defaultValue = "0") val pieces: Double = 0.0
)

@Entity(tableName = "payments")
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val date: Long,
    val amount: Double,
    val note: String = "",
    /** عملة الدفعة: IQD أو USD (تُخصم من دين الزبون بنفس العملة) */
    @ColumnInfo(defaultValue = "'IQD'") val currency: String = "IQD"
)

/** عملية شراء (إدخال بضاعة للمخزن) لصنف معيّن بسعرها وعملتها؛ تزيد المخزون عند تسجيلها */
@Entity(tableName = "purchases")
data class Purchase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fabricId: Long,
    val date: Long,
    val quantity: Double,
    /** سعر شراء الوحدة بعملة الشراء */
    val unitCost: Double = 0.0,
    val note: String = "",
    /** عملة الشراء: IQD أو USD */
    val currency: String = "IQD",
    /** ما دُفع للمورّد وقت الشراء */
    val paid: Double = 0.0,
    val supplier: String = "",
    /** عدد القطع إن أُدخل الشراء بالقطعة (0 = أُدخل بالمتر)؛ quantity يبقى مجموع الأمتار */
    @ColumnInfo(defaultValue = "0") val pieces: Double = 0.0,
    /** عدد البالات إن أُدخل الشراء بالبالة (0 = لم يُدخل بالبالة)؛ pieces = البالات × القطع في البالة */
    @ColumnInfo(defaultValue = "0") val bales: Double = 0.0,
    /** نوع البالة وقت الشراء */
    @ColumnInfo(defaultValue = "''") val baleType: String = ""
) {
    /** إجمالي سعر البضاعة المشتراة = سعر المتر × الأمتار (الأمتار = البالات × القطع × متر القطعة) */
    val total: Double get() = quantity * unitCost
}

/** دفعة تسديد للمورّد (تُخصم من دين المشتريات بنفس العملة) */
@Entity(tableName = "supplier_payments")
data class SupplierPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val amount: Double,
    val currency: String = "IQD",
    val supplier: String = "",
    val note: String = ""
)

/** مجاميع صنف: إجمالي المشترى وإجمالي المباع */
data class FabricTotals(val purchased: Double, val sold: Double)

/** حساب المشتريات بعملة واحدة: إجمالي السعر، المسدَّد، والمتبقي (المحصلة) */
data class Payable(val total: Double, val paid: Double) {
    val remaining: Double get() = total - paid
}

/** ملاحظة القائمة التي تمثّل حساب الزبون السابق (رصيد مرحَّل) */
const val PREVIOUS_ACCOUNT_NOTE = "حساب سابق"

/** حساب سابق للزبون عند إضافته: المبلغ المتبقي عليه وعملته وتاريخ آخر حساب */
data class PreviousAccount(val amount: Double, val currency: String, val date: Long)

/** مورّد: يُربط بعمليات الشراء والدفعات بالاسم (supplier) */
@Entity(tableName = "suppliers", indices = [Index(value = ["name"], unique = true)])
data class Supplier(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = ""
)
