package com.aile.sesustasi

import android.accessibilityservice.AccessibilityService
import android.media.AudioManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/**
 * Ses tuşlarını dinler. Telefon sesi en yüksekteyken "ses artır"a basmaya devam edilirse yükseltmeyi
 * artırır; "ses azalt" önce yükseltmeyi azaltır. Diğer tüm durumlarda tuşu sisteme bırakır.
 * Çocuk modunda ve uygulama kapalıyken hiçbir şey yapmaz.
 */
class VolumeKeyService : AccessibilityService() {

    private var toast: Toast? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onKeyEvent(ev: KeyEvent): Boolean {
        val code = ev.keyCode
        if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        if (!Store.master(this) || !Store.special(this) || !Store.keyMode(this) || Store.childMode(this)) {
            return false
        }
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        // Medya çalmıyorsa tuşlar zil sesini ayarlıyordur: karışma.
        if (!am.isMusicActive) return false
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val vol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val boost = Store.keyBoost(this)

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
            toast?.cancel()
            toast = Toast.makeText(
                this,
                if (next > 100) "Ses yükseltme %$next (+${VolumeMath.gainMb(next) / 100} dB)" else "Ses normal en yüksek seviyede",
                Toast.LENGTH_SHORT
            ).also { it.show() }
        }
        return true
    }
}
