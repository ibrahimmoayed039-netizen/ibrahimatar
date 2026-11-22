package com.fabric.ledger.data

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

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

    // ---------- قفل التطبيق (رقم سري) ----------
    private val pinHash: String get() = p.getString("pinHash", "") ?: ""
    private val pinSalt: String get() = p.getString("pinSalt", "") ?: ""
    val hasPin: Boolean get() = pinHash.isNotEmpty()
    val pinLen: Int get() = p.getInt("pinLen", 4)

    /** لا يُخزَّن الرمز نفسه بل بصمته مع ملح عشوائي */
    private fun hash(pin: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        var d = (salt + pin).toByteArray(Charsets.UTF_8)
        repeat(5000) { d = md.digest(d) }
        return d.joinToString("") { "%02x".format(it) }
    }

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        p.edit().putString("pinSalt", salt).putString("pinHash", hash(pin, salt)).putInt("pinLen", pin.length).apply()
    }

    fun checkPin(pin: String): Boolean =
        hasPin && MessageDigest.isEqual(hash(pin, pinSalt).toByteArray(), pinHash.toByteArray())

    fun clearPin() {
        p.edit().remove("pinHash").remove("pinSalt").remove("pinLen").apply()
    }
}
