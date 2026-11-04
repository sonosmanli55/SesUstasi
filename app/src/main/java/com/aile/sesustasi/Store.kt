package com.aile.sesustasi

import android.content.Context
import java.security.MessageDigest
import java.util.UUID

/** Tüm ayarlar burada saklanır (kalıcı). */
object Store {
    private fun p(c: Context) =
        c.applicationContext.getSharedPreferences("ses_prefs", Context.MODE_PRIVATE)

    // Uygulama (servis) açık/kapalı
    fun master(c: Context) = p(c).getBoolean("master", false)
    fun setMaster(c: Context, v: Boolean) = p(c).edit().putBoolean("master", v).apply()

    // true: Özel ayarlarım uygulanıyor, false: telefonun normal ayarı
    fun special(c: Context) = p(c).getBoolean("special", true)
    fun setSpecial(c: Context, v: Boolean) = p(c).edit().putBoolean("special", v).apply()

    // Mod
    fun childMode(c: Context) = p(c).getBoolean("child_mode", false)
    fun setChildMode(c: Context, v: Boolean) = p(c).edit().putBoolean("child_mode", v).apply()

    // Seviye 0..500 (%). 0-100: sistem sesi kilidi, 100-500: maksimum ses + yükseltme
    // Çocuk modu en fazla 100 olabilir (yükseltme YOK). Yükseltme yalnızca ebeveyn modunda.
    fun level(c: Context, child: Boolean): Int {
        val v = p(c).getInt(if (child) "level_child" else "level_parent", if (child) 50 else 100)
        return VolumeMath.clampLevel(child, v)
    }

    fun setLevel(c: Context, child: Boolean, v: Int) {
        val x = VolumeMath.clampLevel(child, v)
        p(c).edit().putInt(if (child) "level_child" else "level_parent", x).apply()
    }

    // --- Güncelleme denetleyicisi ---
    fun updateRepo(c: Context) = p(c).getString("upd_repo", "") ?: ""
    fun setUpdateRepo(c: Context, v: String) = p(c).edit().putString("upd_repo", v.trim()).apply()
    fun updateToken(c: Context) = p(c).getString("upd_token", "") ?: ""
    fun setUpdateToken(c: Context, v: String) = p(c).edit().putString("upd_token", v.trim()).apply()
    fun lastUpdateCheck(c: Context) = p(c).getLong("upd_last", 0L)
    fun setLastUpdateCheck(c: Context, v: Long) = p(c).edit().putLong("upd_last", v).apply()

    fun activeLevel(c: Context): Int = level(c, childMode(c))

    // Telefonun normal ses seviyesi (özel ayara geçerken kaydedilir, normale dönünce geri yüklenir)
    fun normalVol(c: Context) = p(c).getInt("normal_vol", -1)
    fun setNormalVol(c: Context, v: Int) = p(c).edit().putInt("normal_vol", v).apply()
    fun engaged(c: Context) = p(c).getBoolean("engaged", false)
    fun setEngaged(c: Context, v: Boolean) = p(c).edit().putBoolean("engaged", v).apply()

    // Yükseltme durumu (arayüzde gösterilir)
    fun boostStatus(c: Context) = p(c).getString("boost_status", "Yükseltme kapalı") ?: ""
    fun setBoostStatus(c: Context, s: String) = p(c).edit().putString("boost_status", s).apply()

    // --- PIN / desen ---
    private fun salt(c: Context): String {
        val s = p(c).getString("salt", null)
        if (s != null) return s
        val n = UUID.randomUUID().toString()
        p(c).edit().putString("salt", n).apply()
        return n
    }

    private fun hash(c: Context, v: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest((salt(c) + v).toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }

    fun hasPin(c: Context) = p(c).getString("pin", null) != null
    fun setPin(c: Context, pin: String) = p(c).edit().putString("pin", hash(c, "pin:$pin")).apply()
    fun checkPin(c: Context, pin: String) = p(c).getString("pin", null) == hash(c, "pin:$pin")

    fun hasPattern(c: Context) = p(c).getString("pattern", null) != null
    fun setPattern(c: Context, s: String) = p(c).edit().putString("pattern", hash(c, "pat:$s")).apply()
    fun checkPattern(c: Context, s: String) = p(c).getString("pattern", null) == hash(c, "pat:$s")

    fun hasCredential(c: Context) = hasPin(c) || hasPattern(c)

    // Hatalı deneme sınırı: 5 hatadan sonra 30 sn bekletir
    fun lockRemainingMs(c: Context): Long =
        maxOf(0L, p(c).getLong("lock_until", 0L) - System.currentTimeMillis())

    fun registerFail(c: Context) {
        val n = p(c).getInt("fails", 0) + 1
        if (n >= 5) {
            p(c).edit().putInt("fails", 0)
                .putLong("lock_until", System.currentTimeMillis() + 30_000L).apply()
        } else {
            p(c).edit().putInt("fails", n).apply()
        }
    }

    fun resetFails(c: Context) = p(c).edit().putInt("fails", 0).apply()
}
