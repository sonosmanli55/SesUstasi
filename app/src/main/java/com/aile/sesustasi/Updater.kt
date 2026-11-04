package com.aile.sesustasi

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Güncelleme denetleyicisi. GitHub'daki en son sürümü (Release) sorgular; yeni sürüm varsa
 * APK'yı indirip sistemin paket yükleyicisine verir. Kurulum onayı Android tarafından istenir.
 *
 * Sürüm numarası: workflow her derlemede etiketi "v<derleme no>" yapar ve APK'nın versionCode'u
 * aynı numaradır; karşılaştırma bu iki sayı üzerinden yapılır.
 */
object Updater {

    enum class State { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, INSTALLING, ERROR, NO_REPO }

    class Release(val tag: String, val build: Int, val notes: String, val apkUrl: String, val size: Long)

    const val ACTION_INSTALL = "com.aile.sesustasi.INSTALL_RESULT"
    private const val SIX_HOURS = 6L * 60 * 60 * 1000

    var state = State.IDLE
        private set
    var message = ""
        private set
    var progress = 0
        private set
    var latest: Release? = null
        private set

    /** Arayüz bunu ayarlar; durum her değiştiğinde ana iş parçacığında çağrılır. */
    var listener: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())

    private fun setState(s: State, msg: String = "", pct: Int = 0) {
        main.post {
            state = s
            message = msg
            progress = pct
            listener?.invoke()
        }
    }

    /** Alıcı (InstallReceiver) ve arayüz için dışarıya açık durum bildirimi. */
    fun report(s: State, msg: String = "") = setState(s, msg)

    fun repo(c: Context): String {
        val own = Store.updateRepo(c)
        return if (own.isNotBlank()) own else BuildConfig.REPO_SLUG
    }

    fun currentBuild(): Int = BuildConfig.VERSION_CODE

    /** Açılışta sessiz denetim: en fazla 6 saatte bir, hata göstermez. */
    fun autoCheck(c: Context) {
        if (repo(c).isBlank()) return
        if (System.currentTimeMillis() - Store.lastUpdateCheck(c) < SIX_HOURS) return
        check(c, silent = true)
    }

    fun check(c: Context, silent: Boolean = false) {
        if (state == State.CHECKING || state == State.DOWNLOADING || state == State.INSTALLING) return
        val ctx = c.applicationContext
        val repo = repo(ctx)
        if (repo.isBlank()) {
            if (!silent) setState(State.NO_REPO, "Güncelleme deposu ayarlanmamış.")
            return
        }
        if (!silent) setState(State.CHECKING, "Denetleniyor…")
        val token = Store.updateToken(ctx)
        Thread {
            try {
                val con = open("https://api.github.com/repos/$repo/releases/latest", token, "application/vnd.github+json")
                try {
                    val code = con.responseCode
                    if (code != 200) throw IOException(explain(code))
                    val json = JSONObject(con.inputStream.bufferedReader().readText())
                    val tag = json.optString("tag_name")
                    val build = Regex("\\d+").find(tag)?.value?.toIntOrNull() ?: 0
                    var apk = ""
                    var size = 0L
                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val a = assets.getJSONObject(i)
                            if (a.optString("name").endsWith(".apk")) {
                                apk = a.optString("url")
                                size = a.optLong("size")
                                break
                            }
                        }
                    }
                    Store.setLastUpdateCheck(ctx, System.currentTimeMillis())
                    if (apk.isBlank()) throw IOException("Sürümde APK dosyası bulunamadı.")
                    if (build > currentBuild()) {
                        val rel = Release(tag, build, json.optString("body"), apk, size)
                        main.post { latest = rel }
                        setState(State.AVAILABLE, "Yeni sürüm hazır: $tag")
                    } else {
                        setState(State.UP_TO_DATE, "Uygulama güncel.")
                    }
                } finally {
                    con.disconnect()
                }
            } catch (e: Exception) {
                if (silent) setState(State.IDLE) else setState(State.ERROR, e.message ?: "Denetim başarısız.")
            }
        }.start()
    }

    fun downloadAndInstall(c: Context) {
        val rel = latest ?: return
        if (state == State.DOWNLOADING || state == State.INSTALLING) return
        val ctx = c.applicationContext
        val token = Store.updateToken(ctx)
        setState(State.DOWNLOADING, "İndiriliyor…", 0)
        Thread {
            try {
                val file = File(ctx.cacheDir, "update.apk")
                if (file.exists()) file.delete()
                val con = open(rel.apkUrl, token, "application/octet-stream")
                try {
                    val code = con.responseCode
                    if (code != 200) throw IOException(explain(code))
                    val total = if (rel.size > 0) rel.size else con.contentLengthLong
                    var done = 0L
                    var lastPct = -1
                    con.inputStream.use { input ->
                        file.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                if (total > 0) {
                                    val pct = (done * 100 / total).toInt()
                                    if (pct != lastPct) {
                                        lastPct = pct
                                        setState(State.DOWNLOADING, "İndiriliyor…", pct)
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    con.disconnect()
                }
                if (file.length() < 100_000) throw IOException("İndirilen dosya eksik görünüyor.")
                install(ctx, file)
                setState(State.INSTALLING, "Kurulum onayı bekleniyor. Açılan pencerede “Güncelle”ye dokunun.")
            } catch (e: Exception) {
                setState(State.ERROR, e.message ?: "İndirme başarısız.")
            }
        }.start()
    }

    /**
     * Arka plan işi için eşzamanlı yol: yeni sürüm varsa indirir ve kurulumu başlatır.
     * Android 12+'da, uygulama kendi kendini daha önce kurduysa onay istenmeden kurulur;
     * aksi halde InstallReceiver onay için bildirim gösterir.
     */
    fun backgroundUpdate(c: Context) {
        if (state == State.CHECKING || state == State.DOWNLOADING || state == State.INSTALLING) return
        val ctx = c.applicationContext
        val repo = repo(ctx)
        if (repo.isBlank()) return
        val token = Store.updateToken(ctx)
        var apkUrl = ""
        var size = 0L
        val con = open("https://api.github.com/repos/$repo/releases/latest", token, "application/vnd.github+json")
        try {
            if (con.responseCode != 200) return
            val json = JSONObject(con.inputStream.bufferedReader().readText())
            val build = Regex("\\d+").find(json.optString("tag_name"))?.value?.toIntOrNull() ?: 0
            Store.setLastUpdateCheck(ctx, System.currentTimeMillis())
            if (build <= currentBuild()) return
            val assets = json.optJSONArray("assets") ?: return
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) {
                    apkUrl = a.optString("url")
                    size = a.optLong("size")
                    break
                }
            }
        } finally {
            con.disconnect()
        }
        if (apkUrl.isBlank()) return
        val file = File(ctx.cacheDir, "update.apk")
        if (file.exists()) file.delete()
        val dl = open(apkUrl, token, "application/octet-stream")
        try {
            if (dl.responseCode != 200) return
            dl.inputStream.use { input -> file.outputStream().use { out -> input.copyTo(out) } }
        } finally {
            dl.disconnect()
        }
        if (file.length() < 100_000 || (size > 0 && file.length() != size)) return
        install(ctx, file)
    }

    private fun install(c: Context, file: File) {
        val pi = c.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = pi.createSession(params)
        pi.openSession(id).use { session ->
            file.inputStream().use { input ->
                session.openWrite("update", 0, file.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val intent = Intent(c, InstallReceiver::class.java).setAction(ACTION_INSTALL)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(c, id, intent, flags)
            session.commit(pending.intentSender)
        }
    }

    /** Yönlendirmeleri elle izler: GitHub dosya indirmesi başka bir sunucuya yönlenir ve oraya anahtar gönderilmemelidir. */
    private fun open(url: String, token: String, accept: String): HttpURLConnection {
        var u = url
        var auth = token.isNotBlank()
        for (i in 0 until 5) {
            val con = URL(u).openConnection() as HttpURLConnection
            con.instanceFollowRedirects = false
            con.connectTimeout = 15000
            con.readTimeout = 30000
            con.setRequestProperty("Accept", accept)
            con.setRequestProperty("User-Agent", "SesUstasi")
            if (auth) con.setRequestProperty("Authorization", "Bearer $token")
            val code = con.responseCode
            if (code in 300..399) {
                val loc = con.getHeaderField("Location")
                con.disconnect()
                if (loc == null) throw IOException("Yönlendirme hatası.")
                u = loc
                auth = false
                continue
            }
            return con
        }
        throw IOException("Çok fazla yönlendirme.")
    }

    private fun explain(code: Int): String = when (code) {
        401, 403 -> "Erişim reddedildi. Özel depo için geçerli bir erişim anahtarı girin."
        404 -> "Sürüm bulunamadı. Depo adını kontrol edin; özel depoda erişim anahtarı gerekir."
        else -> "Sunucu hatası ($code)."
    }
}
