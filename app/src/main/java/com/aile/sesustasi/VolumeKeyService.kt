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

    private val h = Handler(Looper.getMainLooper())
    private var label: TextView? = null
    private val hideLabel = Runnable { removeLabel() }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        h.removeCallbacks(hideLabel)
        removeLabel()
        super.onDestroy()
    }

    override fun onKeyEvent(ev: KeyEvent): Boolean {
        val code = ev.keyCode
        if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        if (!Store.master(this) || !Store.special(this) || !Store.keyMode(this) || Store.childMode(this)) {
            return false
        }
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val vol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val boost = Store.keyBoost(this)

        // Medya çalmıyorsa tuşlar zil sesini ayarlıyordur: karışma. Yalnızca en yüksekte ve artırırken ipucu göster.
        if (!am.isMusicActive) {
            if (code == KeyEvent.KEYCODE_VOLUME_UP && vol >= max && ev.action == KeyEvent.ACTION_DOWN) {
                show("Yükseltme için müzik ya da video çalmalı", false)
            }
            return false
        }

        val delta: Int = if (code == KeyEvent.KEYCODE_VOLUME_UP) {
            if (vol < max) return false
            VolumeMath.BOOST_STEP
        } else {
            if (boost <= 100) return false
            -VolumeMath.BOOST_STEP
        }
        if (ev.action == KeyEvent.ACTION_DOWN) {
            val next = VolumeMath.stepBoost(boost, delta)
            if (next != boost) {
                Store.setKeyBoost(this, next)
                VolumeService.refresh(this)
            }
            val text = when {
                next > 100 && next >= VolumeMath.MAX_PARENT -> "Ses %$next (+${VolumeMath.gainMb(next) / 100} dB)  en fazla"
                next > 100 -> "Ses %$next (+${VolumeMath.gainMb(next) / 100} dB)"
                else -> "Ses %100 (normal en yüksek)"
            }
            show(text, next > 100)
        }
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
