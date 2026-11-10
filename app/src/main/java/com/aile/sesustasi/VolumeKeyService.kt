package com.aile.sesustasi

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import android.widget.Toast

/**
 * Ses tuşlarını dinler. Telefon sesi en yüksekteyken "ses artır"a basmaya devam edilirse yükseltmeyi
 * artırır; "ses azalt" önce yükseltmeyi azaltır. Diğer tüm durumlarda tuşu sisteme bırakır.
 * Çocuk modunda ve uygulama kapalıyken hiçbir şey yapmaz.
 * Her basışta ekranın üstünde, yükseltme yüzdesini gösteren büyük bir etiket çıkar.
 */
class VolumeKeyService : AccessibilityService() {

    companion object {
        /** Hizmet Android tarafından bağlandı mı? (tanı ekranı için) */
        @Volatile
        var connected = false

        /** Medya durakladıktan sonra tuşların hâlâ yükseltme için sayıldığı süre. */
        private const val MUSIC_GRACE_MS = 10_000L
    }

    private val h = Handler(Looper.getMainLooper())
    private var label: TextView? = null
    private val hideLabel = Runnable { removeLabel() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = true
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        connected = false
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        connected = false
        h.removeCallbacks(hideLabel)
        removeLabel()
        super.onDestroy()
    }

    // Tuş eşleşmesi: sistem bir tuşun "bas" olayını gördüyse "bırak" olayını da görmeli; yoksa
    // telefonun ses penceresi tuş hâlâ basılıymış gibi ekranda takılı kalır.
    private val systemSawDown = HashSet<Int>()
    private val weConsumedDown = HashSet<Int>()
    private var lastStep = 0L

    override fun onKeyEvent(ev: KeyEvent): Boolean {
        val code = ev.keyCode
        if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) return false

        if (ev.action == KeyEvent.ACTION_UP) {
            val sys = systemSawDown.remove(code)
            val ours = weConsumedDown.remove(code)
            // Sistem "bas"ı gördüyse "bırak"ı da görsün; "bas"ı yalnızca biz aldıysak "bırak"ı da biz alırız.
            return !sys && ours
        }
        if (ev.action != KeyEvent.ACTION_DOWN) return false

        val consume = handleDown(code, ev.repeatCount)
        if (consume) weConsumedDown.add(code) else systemSawDown.add(code)
        return consume
    }

    /** true: tuşu biz aldık (sistem görmez), false: sisteme bırak. */
    private fun handleDown(code: Int, repeat: Int): Boolean {
        if (!Store.master(this) || !Store.special(this) || !Store.keyMode(this) || Store.childMode(this)) {
            return false
        }
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val vol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val boost = Store.keyBoost(this)
        val up = code == KeyEvent.KEYCODE_VOLUME_UP

        // Medya çalmıyorsa (ya da yeni durakladıysa) tuşlar zil sesini ayarlıyor olabilir.
        // Kısa duraklamalar ve parça geçişleri tuşu kaçırmasın diye 10 sn'lik tolerans var.
        val now = System.currentTimeMillis()
        if (am.isMusicActive) VolumeService.lastMusic = now
        if (now - VolumeService.lastMusic >= MUSIC_GRACE_MS) {
            if (up && vol >= max && repeat == 0) show("Yükseltme için müzik ya da video çalmalı", false)
            return false
        }

        if (up && vol < max) return false          // normal aralık: telefon kendi halleder
        if (!up && boost <= 100) return false      // yükseltme yok: telefon kendi halleder

        // Basılı tutmada her tekrar bir adım atlamasın: en çok 0,3 sn'de bir adım (kulak koruması).
        if (repeat > 0 && now - lastStep < 300L) return true
        lastStep = now

        val next = VolumeMath.boostStep(boost, up)
        if (next != boost) {
            Store.setKeyBoost(this, next)
            VolumeService.refresh(this)
        }
        val text = when {
            next > 100 && next >= VolumeMath.MAX_PARENT ->
                "Ses %$next (+${VolumeMath.gainMb(next) / 100} dB)  en fazla"
            next > 100 -> "Ses %$next (+${VolumeMath.gainMb(next) / 100} dB)"
            else -> "Ses %100 (normal en yüksek)"
        }
        show(text, next > 100)
        return true
    }

    /** Ekranın üstünde kısa süre görünen büyük sayı etiketi (ek izin gerektirmez). */
    private fun show(text: String, boosted: Boolean) {
        try {
            val d = resources.displayMetrics.density
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            var v = label
            if (v == null) {
                v = TextView(this)
                v.textSize = 26f
                v.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                v.setTextColor(0xFFFFFFFF.toInt())
                v.gravity = Gravity.CENTER
                v.setPadding((24 * d).toInt(), (14 * d).toInt(), (24 * d).toInt(), (14 * d).toInt())
                val lp = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                )
                lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                lp.y = (96 * d).toInt()
                wm.addView(v, lp)
                label = v
            }
            val bg = GradientDrawable()
            bg.cornerRadius = 28 * d
            bg.setColor(if (boosted) 0xFFA84A06.toInt() else 0xFF1D1B17.toInt())
            v.background = bg
            v.text = text
            h.removeCallbacks(hideLabel)
            h.postDelayed(hideLabel, 1600)
        } catch (e: Throwable) {
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeLabel() {
        val v = label ?: return
        label = null
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v)
        } catch (e: Throwable) {
        }
    }
}
