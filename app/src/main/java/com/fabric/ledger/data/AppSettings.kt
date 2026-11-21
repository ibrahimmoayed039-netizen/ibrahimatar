package com.fabric.ledger.data

import android.content.Context

/** إعدادات بسيطة تُحفظ في SharedPreferences */
class AppSettings(context: Context) {
    private val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** مدة السماح بالأيام قبل اعتبار الدين متأخراً */
    var graceDays: Int
        get() = p.getInt("graceDays", 30)
        set(v) = p.edit().putInt("graceDays", v).apply()

    var shopName: String
        get() = p.getString("shopName", "") ?: ""
        set(v) = p.edit().putString("shopName", v).apply()

    /** رمز الدولة بدون + (مثال 964) لتحويل الأرقام المحلية التي تبدأ بـ 0 */
    var countryCode: String
        get() = p.getString("countryCode", "") ?: ""
        set(v) = p.edit().putString("countryCode", v).apply()
}
