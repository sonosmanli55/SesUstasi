package com.aile.sesustasi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings

/**
 * Arka planda sürekli çalışan servis:
 *  - Medya ses seviyesini kilitler (kullanıcı değiştirse bile geri çeker)
 *  - Seviye %100'ün üzerindeyse sistem sesini maksimuma alıp LoudnessEnhancer ile yükseltir
 */
class VolumeService : Service() {

    companion object {
        const val ACTION_REFRESH = "com.aile.sesustasi.REFRESH"
        const val ACTION_TOGGLE = "com.aile.sesustasi.TOGGLE"
        private const val CH = "ses_kanal"
        private const val NID = 17

        fun refresh(c: Context) {
            val i = Intent(c, VolumeService::class.java).setAction(ACTION_REFRESH)
            try {
                c.startForegroundService(i)
            } catch (e: Throwable) {
            }
        }
    }

    private lateinit var am: AudioManager
    private val h = Handler(Looper.getMainLooper())

    private var enhGlobal: LoudnessEnhancer? = null
    private val sessionEnh = HashMap<Int, LoudnessEnhancer>()
    private val openSessions = HashSet<Int>()
    private var gainMb = 0
    private lateinit var pm: PowerManager
    private var lastFx = 0L

    /** Yoklama aralığı: pil tasarrufu açıkken ekran kapalıyken ve tuş modunda seyrek. Anlık tepki yine olay dinleyicilerinden gelir. */
    private fun interval(): Long {
        if (!Store.saver(this)) return 400L
        if (keyModeOn()) return 3000L
        return if (pm.isInteractive) 600L else 2500L
    }

    private val tick = object : Runnable {
        override fun run() {
            // Uygulama kapalı ya da "telefonun normal ayarı" seçiliyken hiç uyanma: döngü durur.
            if (!Store.master(this@VolumeService) || !Store.special(this@VolumeService)) return
            try {
                enforce()
                val now = System.currentTimeMillis()
                if (gainMb > 0 && now - lastFx > 4000L) {
                    lastFx = now
                    ensureEffects()
                }
            } catch (e: Throwable) {
            }
            h.postDelayed(this, interval())
        }
    }

    private val screenRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            // Ekran açılınca hemen denetle ve sık aralığa dön.
            h.removeCallbacks(tick)
            if (Store.master(c) && Store.special(c)) h.post(tick)
        }
    }

    private val volumeRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (Store.master(c) && Store.special(c)) enforce()
        }
    }

    private val effectRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val sid = i.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
            if (sid <= 0) return
            if (i.action == AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION) {
                openSessions.add(sid)
                if (gainMb > 0) ensureEffects()
            } else {
                openSessions.remove(sid)
                sessionEnh.remove(sid)?.let {
                    try {
                        it.release()
                    } catch (e: Throwable) {
                    }
                }
            }
        }
    }

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (Store.master(this@VolumeService) && Store.special(this@VolumeService)) enforce()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        UpdateJob.sync(this)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CH, "Ses Ustası", NotificationManager.IMPORTANCE_LOW)
        )

        val vf = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        val ef = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(volumeRx, vf, Context.RECEIVER_EXPORTED)
            registerReceiver(effectRx, ef, Context.RECEIVER_EXPORTED)
            registerReceiver(screenRx, IntentFilter(Intent.ACTION_SCREEN_ON), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(volumeRx, vf)
            registerReceiver(effectRx, ef)
            registerReceiver(screenRx, IntentFilter(Intent.ACTION_SCREEN_ON))
        }
        contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startFg()
        if (intent?.action == ACTION_TOGGLE && !Store.childMode(this)) {
            Store.setSpecial(this, !Store.special(this))
        }
        apply()
        return START_STICKY
    }

    override fun onDestroy() {
        h.removeCallbacks(tick)
        try {
            unregisterReceiver(volumeRx)
            unregisterReceiver(effectRx)
            unregisterReceiver(screenRx)
            contentResolver.unregisterContentObserver(observer)
        } catch (e: Throwable) {
        }
        releaseEffects()
        super.onDestroy()
    }

    // ---------- Ana mantık ----------

    private fun apply() {
        h.removeCallbacks(tick)
        if (!Store.master(this)) {
            shutdown()
            return
        }
        if (Store.special(this)) h.post(tick)
        if (Store.special(this)) {
            if (!Store.engaged(this)) {
                Store.setNormalVol(this, am.getStreamVolume(AudioManager.STREAM_MUSIC))
                Store.setEngaged(this, true)
            }
            enforce()
            gainMb = gainFor()
            ensureEffects()
        } else if (Store.engaged(this)) {
            gainMb = 0
            ensureEffects()
            restoreNormal()
        }
        updateNotification()
    }

    private fun shutdown() {
        h.removeCallbacks(tick)
        gainMb = 0
        releaseEffects()
        if (Store.engaged(this)) restoreNormal()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun restoreNormal() {
        val v = Store.normalVol(this)
        if (v >= 0) {
            try {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
            } catch (e: Throwable) {
            }
        }
        Store.setEngaged(this, false)
    }

    private fun targetVol(): Int {
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return VolumeMath.targetVol(Store.activeLevel(this), max)
    }

    private fun keyModeOn() = Store.keyMode(this) && !Store.childMode(this)

    private fun gainFor(): Int =
        VolumeMath.gainMb(if (keyModeOn()) Store.keyBoost(this) else Store.activeLevel(this))

    private fun enforce() {
        // Tuşla yükseltme modunda ses kilitlenmez; kullanıcı tuşlarla serbestçe ayarlar.
        if (keyModeOn()) {
            guardKeyBoost()
            return
        }
        val t = targetVol()
        try {
            if (am.getStreamVolume(AudioManager.STREAM_MUSIC) != t) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, t, 0)
            }
        } catch (e: Throwable) {
        }
    }

    /** Telefon sesi başka yolla düşürüldüyse yükseltmeyi sıfırla (kulak koruması). */
    private fun guardKeyBoost() {
        if (Store.keyBoost(this) <= 100) return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (am.getStreamVolume(AudioManager.STREAM_MUSIC) < max) {
            Store.setKeyBoost(this, 100)
            gainMb = 0
            ensureEffects()
            updateNotification()
        }
    }

    // ---------- Ses yükseltme ----------

    private fun ensureEffects() {
        if (gainMb <= 0) {
            releaseEffects()
            Store.setBoostStatus(this, "Yükseltme kapalı")
            return
        }
        var ok = false
        try {
            val e = enhGlobal ?: LoudnessEnhancer(0).also { enhGlobal = it }
            e.setTargetGain(gainMb)
            if (!e.getEnabled()) e.setEnabled(true)
            ok = true
        } catch (t: Throwable) {
            try {
                enhGlobal?.release()
            } catch (x: Throwable) {
            }
            enhGlobal = null
        }
        if (ok) {
            releaseSessions()
            Store.setBoostStatus(this, "Aktif: genel ses efekti (+${gainMb / 100} dB)")
        } else {
            for (sid in openSessions.toList()) {
                try {
                    val e = sessionEnh[sid] ?: LoudnessEnhancer(sid).also { sessionEnh[sid] = it }
                    e.setTargetGain(gainMb)
                    if (!e.getEnabled()) e.setEnabled(true)
                } catch (t: Throwable) {
                    sessionEnh.remove(sid)?.let {
                        try {
                            it.release()
                        } catch (x: Throwable) {
                        }
                    }
                }
            }
            Store.setBoostStatus(
                this,
                if (sessionEnh.isNotEmpty()) "Aktif: uygulama bazlı efekt"
                else "Bu cihaz genel yükseltmeyi desteklemiyor"
            )
        }
    }

    private fun releaseSessions() {
        for (e in sessionEnh.values) {
            try {
                e.setEnabled(false)
                e.release()
            } catch (x: Throwable) {
            }
        }
        sessionEnh.clear()
    }

    private fun releaseEffects() {
        try {
            enhGlobal?.setEnabled(false)
            enhGlobal?.release()
        } catch (x: Throwable) {
        }
        enhGlobal = null
        releaseSessions()
    }

    // ---------- Bildirim ----------

    private fun startFg() {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NID, n)
        }
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val child = Store.childMode(this)
        val special = Store.special(this)
        val txt = (if (child) "Çocuk modu" else "Ebeveyn modu") + " · " +
            (if (!special) "Telefonun normal ayarı"
            else if (keyModeOn()) "Tuşla yükseltme %${Store.keyBoost(this)}"
            else "Özel ayar %${Store.activeLevel(this)}")
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val b = Notification.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle("Ses Ustası çalışıyor")
            .setContentText(txt)
            .setContentIntent(open)
            .setOngoing(true)
        if (!child) {
            val pi = PendingIntent.getForegroundService(
                this, 1,
                Intent(this, VolumeService::class.java).setAction(ACTION_TOGGLE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            b.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_tile),
                    if (special) "Normale dön" else "Özel ayara geç",
                    pi
                ).build()
            )
        }
        return b.build()
    }
}
