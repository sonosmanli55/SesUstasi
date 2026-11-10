package com.aile.sesustasi

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    companion object {
        /** Çocuk modunda PIN/desenle kilit açıldı mı? Uygulama arka plana gidince sıfırlanır. */
        var unlocked = false
    }

    /** Kaydırıcının ne yaptığı: sabit seviye (ebeveyn/çocuk) ya da serbest mod (kilitsiz, doğrudan ses). */
    private enum class SliderKind { LOCK_PARENT, LOCK_CHILD, FREE }

    private lateinit var root: FrameLayout
    private lateinit var ui: Ui
    private val pal get() = ui.pal
    private var updateBox: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        root = FrameLayout(this)
        root.setBackgroundColor(pal.bg)
        setContentView(root)

        window.statusBarColor = pal.bg
        window.navigationBarColor = pal.bg
        if (!pal.dark) {
            var flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            if (Build.VERSION.SDK_INT >= 26) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.decorView.systemUiVisibility = flags
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        UpdateJob.sync(this)
        if (Store.master(this)) VolumeService.refresh(this)
    }

    override fun onResume() {
        super.onResume()
        render()
        Updater.listener = { fillUpdate() }
        if (!Store.childMode(this)) Updater.autoCheck(this)
    }

    override fun onPause() {
        super.onPause()
        Updater.listener = null
    }

    override fun onStop() {
        super.onStop()
        if (Store.childMode(this)) unlocked = false
    }

    // ---------- Yardımcılar ----------

    private fun dp(v: Int) = ui.dp(v)

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun render() {
        if (Store.childMode(this) && !Store.hasCredential(this)) Store.setChildMode(this, false)
        root.removeAllViews()
        updateBox = null
        if (Store.childMode(this) && !unlocked) root.addView(lockView()) else root.addView(mainView())
    }

    /** Bölüm başlığı + "bu bölüm ne işe yarar" tek cümlesi. */
    private fun LinearLayout.titled(title: String, purpose: String) {
        addView(ui.heading(title), ui.lp(ui.wrap, ui.wrap, l = 4, t = 24))
        addView(ui.small(purpose), ui.lp(ui.match, ui.wrap, l = 4, t = 2, b = 8))
    }

    // ---------- Kilit ekranı (çocuk modu) ----------

    private fun lockView(): View {
        val sv = ScrollView(this)
        sv.isFillViewport = true
        val col = ui.vbox()
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.setPadding(dp(24), dp(56), dp(24), dp(32))
        sv.addView(col)

        col.addView(LockGlyph(this, pal.accent), ui.lp(dp(44), dp(52)))
        col.addView(ui.title("Çocuk modu kilitli"), ui.lp(ui.wrap, ui.wrap, t = 20))
        col.addView(
            ui.small("Devam etmek için PIN girin ya da deseni çizin"),
            ui.lp(ui.wrap, ui.wrap, t = 6, b = 24)
        )
        val msg = ui.text("", 14f, pal.danger)
        msg.gravity = Gravity.CENTER

        fun guarded(check: () -> Boolean) {
            val rem = Store.lockRemainingMs(this)
            if (rem > 0) {
                msg.text = "Çok fazla hatalı deneme. ${rem / 1000 + 1} sn bekleyin."
                return
            }
            if (check()) {
                Store.resetFails(this)
                unlocked = true
                render()
            } else {
                Store.registerFail(this)
                msg.text = "Hatalı, tekrar deneyin"
            }
        }

        if (Store.hasPin(this)) {
            val et = ui.field("PIN", true)
            et.gravity = Gravity.CENTER
            col.addView(et, ui.lp(dp(240), ui.wrap))
            col.addView(ui.button("Aç", Ui.Kind.FILLED) {
                val v = et.text.toString()
                et.setText("")
                guarded { Store.checkPin(this, v) }
            }, ui.lp(dp(240), ui.wrap, t = 12))
        }
        if (Store.hasPattern(this)) {
            if (Store.hasPin(this)) {
                col.addView(ui.small("veya deseni çizin"), ui.lp(ui.wrap, ui.wrap, t = 24, b = 8))
            }
            val pv = PatternView(this)
            pv.setColors(pal.line, pal.accent)
            pv.onDone = { s -> guarded { Store.checkPattern(this, s) } }
            col.addView(pv, ui.lp(dp(280), dp(280)))
        }
        col.addView(msg, ui.lp(ui.match, ui.wrap, t = 16))
        return sv
    }

    // ---------- Ana ekran ----------

    private fun mainView(): View {
        val child = Store.childMode(this)
        val special = Store.special(this)
        val master = Store.master(this)
        val free = !child && Store.keyMode(this)

        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        val col = ui.vbox()
        col.setPadding(dp(16), dp(20), dp(16), dp(36))
        sv.addView(col)

        // Başlık + tek cümlelik durum
        col.addView(ui.title("Ses Ustası"), ui.lp(ui.wrap, ui.wrap, l = 4))
        val sentence = when {
            !master -> "Kapalı: ses ayarlarına karışmıyor"
            !special -> "Telefonun kendi ayarı kullanılıyor"
            free -> {
                val kb = Store.keyBoost(this)
                if (kb > 100) "Tuşlarla serbest · yükseltme %$kb" else "Tuşlarla serbest · yükseltme yok"
            }
            else -> "Sabit seviye %${Store.activeLevel(this)}"
        }
        col.addView(
            ui.small(sentence + " · " + (if (child) "Çocuk modu" else "Ebeveyn modu")),
            ui.lp(ui.wrap, ui.wrap, l = 4, t = 2, b = 8)
        )

        // 1) Ana şalter
        col.titled("Uygulama", "Ana şalter. Kapalıyken uygulama sesinize hiç karışmaz.")
        val power = ui.section()
        ui.addRow(
            power,
            ui.row(
                if (master) "Açık" else "Kapalı",
                if (master) "Arka planda çalışıyor" else "Açmak için sağdaki anahtara dokunun",
                trailing = styledSwitch(master) { on ->
                    Store.setMaster(this, on)
                    VolumeService.refresh(this)
                    render()
                }
            )
        )
        col.addView(power)

        // 2) Ses
        col.titled(
            "Ses",
            if (child) "Sesi azaltabilir ya da en fazla telefonun kendi sınırına (%100) çıkarabilirsiniz."
            else "Sesin nasıl yönetileceğini buradan seçin."
        )
        col.addView(levelCard(child, special, master, free))

        // Serbest modda tuş hizmeti kurulumu ve kullanım
        if (free) {
            col.titled("Tuş hizmeti", "Ses tuşlarını dinleyen yardımcı. Çalışması için bir kez izin verilmesi gerekir.")
            col.addView(keyServiceSection())
        }

        // 3) Çocuk modu
        if (!child) {
            col.titled("Çocuk modu", "Çocuk için ses sınırı. Bu mod PIN ya da desenle kilitlenir, yükseltme yoktur.")
            val kid = ui.section()
            val kidInner = ui.vbox()
            kidInner.setPadding(dp(20), dp(16), dp(20), dp(8))
            val kidLabel = ui.body("")
            kidInner.addView(kidLabel)
            kidInner.addView(
                ui.small("Çocuk modundayken ses bu seviyede kilitli kalır (en fazla %100)."),
                ui.lp(ui.wrap, ui.wrap, t = 2)
            )
            kidInner.addView(
                slider(SliderKind.LOCK_CHILD) { v -> kidLabel.text = "Çocuk modu ses seviyesi: %$v" },
                ui.lp(ui.match, ui.wrap, t = 4)
            )
            kid.addView(kidInner)
            val pad = ui.vbox()
            pad.setPadding(dp(16), dp(4), dp(16), dp(16))
            pad.addView(ui.button("Çocuk moduna geç ve kilitle", Ui.Kind.FILLED) {
                if (!Store.hasCredential(this)) {
                    toast("Önce aşağıdaki Güvenlik bölümünden PIN veya desen belirleyin")
                } else {
                    Store.setChildMode(this, true)
                    unlocked = false
                    VolumeService.refresh(this)
                    render()
                }
            }, ui.lp(ui.match, ui.wrap))
            kid.addView(pad)
            col.addView(kid)
        } else {
            col.titled("Mod", "Çocuk modundasınız.")
            val mode = ui.section()
            val pad = ui.vbox()
            pad.setPadding(dp(16), dp(16), dp(16), dp(16))
            pad.addView(ui.button("Ebeveyn moduna geç") {
                Store.setChildMode(this, false)
                VolumeService.refresh(this)
                render()
            }, ui.lp(ui.match, ui.wrap))
            mode.addView(pad)
            col.addView(mode)
            return sv
        }

        // 4) Güvenlik
        col.titled("Güvenlik", "Çocuk modunun kilidi ve uygulamanın silinmesine karşı koruma.")
        val sec = ui.section()
        ui.addRow(
            sec,
            ui.row(
                "PIN", if (Store.hasPin(this)) "Ayarlı" else "Ayarlanmadı",
                trailing = ui.action(if (Store.hasPin(this)) "Değiştir" else "Belirle")
            ) { pinDialog() }
        )
        ui.addRow(
            sec,
            ui.row(
                "Desen", if (Store.hasPattern(this)) "Ayarlı" else "Ayarlanmadı",
                trailing = ui.action(if (Store.hasPattern(this)) "Değiştir" else "Belirle")
            ) { patternDialog() }
        )
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, AdminReceiver::class.java)
        if (dpm.isAdminActive(admin)) {
            ui.addRow(
                sec,
                ui.row("Silinmeye karşı koruma", "Açık: uygulama normal yoldan silinemez", trailing = ui.action("Kapat")) {
                    dpm.removeActiveAdmin(admin)
                    render()
                }
            )
        } else {
            ui.addRow(
                sec,
                ui.row("Silinmeye karşı koruma", "Kapalı", trailing = ui.action("Aç")) {
                    val i = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                    i.putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Etkinleştirilirse uygulama normal yoldan kaldırılamaz."
                    )
                    startActivity(i)
                }
            )
        }
        col.addView(sec)

        // 5) Kesintisiz çalışma
        col.titled("Kesintisiz çalışma", "Telefonun uygulamayı arka planda uyutmasını önler ve pil tüketimini azaltır.")
        val run = ui.section()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            ui.addRow(run, ui.row("Pil kısıtlaması", "Kapalı: uygulama uyutulmaz"))
        } else {
            ui.addRow(
                run,
                ui.row(
                    "Pil kısıtlaması", "Açık: Android uygulamayı uyutabilir. Kaldırmanız önerilir.",
                    summaryColor = pal.danger, trailing = ui.action("Kaldır")
                ) {
                    val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    i.data = Uri.parse("package:$packageName")
                    startActivity(i)
                }
            )
        }
        ui.addRow(
            run,
            ui.row(
                "Pil tasarrufu", "Ekran kapalıyken denetimi seyreltir. Ses yine anında geri çekilir.",
                trailing = styledSwitch(Store.saver(this)) { on ->
                    Store.setSaver(this, on)
                    VolumeService.refresh(this)
                }
            )
        )
        ui.addRow(run, ui.row("Hızlı ayarlar kutucuğu", "Bildirim panelini düzenleyip “Ses Ustası” kutucuğunu ekleyin; tek dokunuşla Telefonun ayarı ↔ Özel ayarım geçer."))
        oemTip()?.let { ui.addRow(run, ui.row("Cihazınıza özel", it)) }
        col.addView(run)

        // 6) Durum kontrolü
        col.titled("Durum kontrolü", "Bir şey çalışmıyorsa nedenini buradan okuyun.")
        col.addView(diagSection(free))

        // 7) Güncelleme
        col.titled("Güncelleme", "Yeni sürümü GitHub'dan alır. Otomatik açıkken siz bir şey yapmazsınız.")
        val box = ui.section()
        updateBox = box
        col.addView(box)
        fillUpdate()

        return sv
    }

    // ---------- Ses kartı ----------

    /** Ses kartı: (1) kim yönetir, (2) nasıl çalışır, (3) değer ve kaydırıcı. */
    private fun levelCard(child: Boolean, special: Boolean, master: Boolean, free: Boolean): View {
        val card = ui.section()
        val inner = ui.vbox()
        inner.setPadding(dp(20), dp(16), dp(20), dp(12))

        if (!child) {
            inner.addView(
                segmented(listOf("Telefonun ayarı", "Özel ayarım"), if (special) 1 else 0) { i ->
                    if (!Store.master(this)) {
                        toast("Önce yukarıdan uygulamayı açın")
                    } else {
                        Store.setSpecial(this, i == 1)
                        VolumeService.refresh(this)
                        render()
                    }
                },
                ui.lp(ui.match, ui.wrap)
            )
            inner.addView(
                ui.small(
                    if (!special) "Uygulama ses işine karışmaz; telefon her zamanki gibi çalışır."
                    else "Uygulamanın ses ayarı geçerli."
                ),
                ui.lp(ui.match, ui.wrap, t = 8)
            )
            if (special) {
                inner.addView(
                    segmented(listOf("Sabit seviye", "Tuşlarla serbest"), if (free) 1 else 0) { i ->
                        Store.setKeyMode(this, i == 1)
                        Store.setKeyBoost(this, 100)
                        VolumeService.refresh(this)
                        render()
                    },
                    ui.lp(ui.match, ui.wrap, t = 16)
                )
                inner.addView(
                    ui.small(
                        if (free) "Ses tuşlarını her zamanki gibi kullanın. Telefon sesi en yükseğe çıkınca " +
                            "ses artır tuşuna basmaya devam edin: ses telefonun sınırının ötesine yükselir. " +
                            "Aşağıdaki çubukla da ayarlayabilirsiniz."
                        else "Ses seçtiğiniz seviyede kilitlenir. Tuşlara basılsa bile geri çekilir."
                    ),
                    ui.lp(ui.match, ui.wrap, t = 8)
                )
            }
        }

        if (child || special) {
            val readout = LinearLayout(this)
            readout.orientation = LinearLayout.HORIZONTAL
            readout.gravity = Gravity.BOTTOM
            val big = ui.text("", 56f, pal.ink, Ui.MEDIUM)
            big.includeFontPadding = false
            val pct = ui.text("%", 24f, pal.inkSoft, Ui.MEDIUM)
            pct.includeFontPadding = false
            val tag = ui.text("", 13f, pal.boost, Ui.MEDIUM)
            tag.setPadding(dp(10), dp(4), dp(10), dp(4))
            tag.visibility = View.GONE
            readout.addView(big)
            readout.addView(pct, ui.lp(ui.wrap, ui.wrap, l = 4, b = 6))
            readout.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            readout.addView(tag, ui.lp(ui.wrap, ui.wrap, b = 8))
            inner.addView(readout, ui.lp(ui.match, ui.wrap, t = 20))

            val hint = ui.small("")
            inner.addView(hint, ui.lp(ui.match, ui.wrap, t = 4))

            fun show(v: Int) {
                big.text = v.toString()
                if (v > 100) {
                    tag.text = "Yükseltme +${VolumeMath.gainMb(v) / 100} dB"
                    tag.background = ui.rounded(pal.boostSoft, 12)
                    tag.visibility = View.VISIBLE
                    if (v > VolumeMath.SAFE_BOOST) {
                        hint.text = "Dikkat: %${VolumeMath.SAFE_BOOST} üstünde ses bozulabilir, hoparlöre ve kulağa zarar verebilir."
                        hint.setTextColor(pal.danger)
                    } else {
                        hint.text = "Telefon sesi en yüksekte, üstüne yükseltme ekleniyor. Sesi telefondan kısarsanız yükseltme kapanır."
                        hint.setTextColor(pal.inkSoft)
                    }
                } else {
                    tag.visibility = View.GONE
                    hint.setTextColor(pal.inkSoft)
                    hint.text = when {
                        free -> "Telefonun kendi sesi. %100 çubuğun sonu değil, yükseltmenin başlangıcıdır."
                        child -> "Telefon sesinin %$v kadarına kilitli."
                        else -> "Telefon sesi bu oranda kilitli."
                    }
                }
            }

            val kind = when {
                child -> SliderKind.LOCK_CHILD
                free -> SliderKind.FREE
                else -> SliderKind.LOCK_PARENT
            }
            inner.addView(slider(kind) { v -> show(v) }, ui.lp(ui.match, ui.wrap, t = 8))

            if (!master) {
                inner.addView(
                    ui.text("Uygulama kapalı: seçimler uygulanmaz.", 12f, pal.danger),
                    ui.lp(ui.match, ui.wrap, t = 4)
                )
            } else if (!child) {
                inner.addView(
                    ui.text("Yükseltme durumu: " + Store.boostStatus(this), 12f, pal.inkSoft),
                    ui.lp(ui.match, ui.wrap, t = 4)
                )
            }
        }
        card.addView(inner)
        return card
    }

    private fun segmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.HORIZONTAL
        box.setPadding(dp(4), dp(4), dp(4), dp(4))
        box.background = ui.rounded(pal.surfaceHi, 16)
        labels.forEachIndexed { i, label ->
            val sel = i == selected
            val t = ui.text(label, 14f, if (sel) pal.onAccent else pal.ink, Ui.MEDIUM)
            t.gravity = Gravity.CENTER
            t.minHeight = dp(44)
            t.setPadding(dp(8), dp(8), dp(8), dp(8))
            if (sel) t.background = ui.rounded(pal.accent, 12)
            t.isClickable = true
            t.isFocusable = true
            t.setOnClickListener { if (!sel) onSelect(i) }
            box.addView(t, LinearLayout.LayoutParams(0, ui.wrap, 1f))
        }
        return box
    }

    private fun styledSwitch(on: Boolean, change: (Boolean) -> Unit): Switch {
        val sw = Switch(this)
        sw.isChecked = on
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        sw.thumbTintList = ColorStateList(states, intArrayOf(pal.onAccent, pal.inkSoft))
        sw.trackTintList = ColorStateList(states, intArrayOf(pal.accent, pal.surfaceHi))
        sw.minimumHeight = dp(48)
        sw.setOnCheckedChangeListener { _, v -> change(v) }
        return sw
    }

    private var lastPush = 0L

    /** Serbest modda çubuk: 0-100 doğrudan telefon sesi, 100 üstü telefon sesi en yüksek + yükseltme. */
    private fun applyFree(p: Int) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        try {
            if (p > 100) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0)
                Store.setKeyBoost(this, p)
            } else {
                Store.setKeyBoost(this, 100)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, VolumeMath.targetVol(p, max), 0)
            }
        } catch (e: SecurityException) {
        }
    }

    /** Kaydırıcı: 0-100 telefonun kendi sesi, üstü yükseltme bölgesi (renk değişir, işaret %100'de). */
    private fun slider(kind: SliderKind, onValue: (Int) -> Unit): View {
        val child = kind == SliderKind.LOCK_CHILD
        val max = if (child) VolumeMath.MAX_CHILD else VolumeMath.MAX_PARENT
        val frame = FrameLayout(this)

        val track = GradientDrawable()
        track.setColor(pal.surfaceHi)
        track.cornerRadius = ui.dpf(4)
        track.setSize(0, dp(8))
        val fill = GradientDrawable()
        fill.setColor(pal.accent)
        fill.cornerRadius = ui.dpf(4)
        fill.setSize(0, dp(8))
        val clip = ClipDrawable(fill, Gravity.START, ClipDrawable.HORIZONTAL)
        val layers = LayerDrawable(arrayOf<Drawable>(track, clip))
        layers.setId(0, android.R.id.background)
        layers.setId(1, android.R.id.progress)

        val thumb = GradientDrawable()
        thumb.shape = GradientDrawable.OVAL
        thumb.setColor(pal.accent)
        thumb.setStroke(dp(4), pal.surface)
        thumb.setSize(dp(32), dp(32))

        val sb = SeekBar(this)
        sb.max = max
        sb.thumb = thumb
        sb.progressDrawable = layers
        sb.splitTrack = false
        sb.setPadding(dp(16), 0, dp(16), 0)

        fun paint(v: Int) {
            val c = if (v > 100) pal.boost else pal.accent
            fill.setColor(c)
            thumb.setColor(c)
            onValue(v)
        }

        val cur = when (kind) {
            SliderKind.FREE -> {
                val boost = Store.keyBoost(this)
                if (boost > 100) boost else {
                    val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    VolumeMath.percentOf(
                        am.getStreamVolume(AudioManager.STREAM_MUSIC),
                        am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    )
                }
            }
            else -> Store.level(this, child)
        }
        sb.progress = cur
        paint(cur)

        fun commit(p: Int) {
            if (kind == SliderKind.FREE) applyFree(p) else Store.setLevel(this, child, p)
            VolumeService.refresh(this)
        }

        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                paint(p)
                if (!fromUser) return
                val now = System.currentTimeMillis()
                if (now - lastPush > 150) {
                    lastPush = now
                    commit(p)
                }
            }

            override fun onStartTrackingTouch(s: SeekBar) {}

            override fun onStopTrackingTouch(s: SeekBar) {
                commit(s.progress)
            }
        })
        frame.addView(sb, FrameLayout.LayoutParams(ui.match, dp(48), Gravity.CENTER_VERTICAL))

        if (child) return frame

        // %100 işareti: telefonun kendi sınırı ile yükseltme bölgesinin sınırı
        val tick = View(this)
        tick.setBackgroundColor(pal.inkSoft)
        frame.addView(tick, FrameLayout.LayoutParams(dp(2), dp(20), Gravity.CENTER_VERTICAL))
        sb.addOnLayoutChangeListener { v, l, _, r, _, _, _, _, _ ->
            val avail = (r - l) - v.paddingLeft - v.paddingRight
            tick.translationX = v.paddingLeft + avail * 100f / max - dp(1)
        }
        val scale = LinearLayout(this)
        scale.orientation = LinearLayout.HORIZONTAL
        scale.setPadding(dp(16), 0, dp(16), 0)
        scale.addView(ui.small("0"), LinearLayout.LayoutParams(0, ui.wrap, 1f))
        scale.addView(ui.small("İşaret: telefonun sınırı (%100)  ·  $max"))
        val wrapCol = ui.vbox()
        wrapCol.addView(frame)
        wrapCol.addView(scale)
        return wrapCol
    }

    // ---------- Tuş hizmeti ve durum kontrolü ----------

    private fun keyServiceEnabled(): Boolean {
        val v = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return v.contains(packageName + "/" + VolumeKeyService::class.java.name)
    }

    private fun keyServiceSection(): View {
        val keys = ui.section()
        val granted = keyServiceEnabled()
        if (!granted) {
            ui.addRow(
                keys,
                ui.row(
                    "1. Kısıtlamayı kaldırın",
                    "Android, dışarıdan kurulan uygulamalarda erişilebilirliği engeller. Açılan sayfada sağ üstteki ⋮ simgesine dokunup “Kısıtlanmış ayarlara izin ver”i seçin.",
                    trailing = ui.action("Aç")
                ) {
                    toast("Sağ üstteki ⋮ → Kısıtlanmış ayarlara izin ver")
                    val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    i.data = Uri.parse("package:$packageName")
                    startActivity(i)
                }
            )
        }
        ui.addRow(
            keys,
            ui.row(
                "2. Erişilebilirlik izni",
                if (granted) "Verildi" else "Gerekli. Listeden “Ses Ustası”nı seçip açın.",
                summaryColor = if (granted) pal.inkSoft else pal.danger,
                trailing = if (granted) null else ui.action("Aç")
            ) {
                if (!granted) {
                    toast("Listeden “Ses Ustası”nı seçip açın")
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
        )
        ui.addRow(
            keys,
            ui.row(
                "Nasıl kullanılır",
                "Müzik ya da video açın. Ses artır tuşuyla telefonun sesini en yükseğe çıkarın, sonra basmaya devam edin: ekranın üstünde yüzde görünür. Ses azalt tuşu önce yükseltmeyi azaltır."
            )
        )
        ui.addRow(
            keys,
            ui.row(
                "Şu anki yükseltme",
                if (Store.keyBoost(this) > 100) "%${Store.keyBoost(this)}" else "Yok",
                trailing = if (Store.keyBoost(this) > 100) ui.action("Sıfırla") else null
            ) {
                if (Store.keyBoost(this) > 100) {
                    Store.setKeyBoost(this, 100)
                    VolumeService.refresh(this)
                    render()
                }
            }
        )
        return keys
    }

    /** Bir şey çalışmıyorsa nedenini gösteren satırlar. */
    private fun diagSection(free: Boolean): View {
        val sec = ui.section()
        val master = Store.master(this)
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Arka plan servisi
        when {
            !master -> ui.addRow(sec, ui.row("Arka plan servisi", "Kapalı: uygulama anahtarı kapalı"))
            VolumeService.running -> ui.addRow(sec, ui.row("Arka plan servisi", "Tamam: çalışıyor"))
            else -> ui.addRow(
                sec,
                ui.row(
                    "Arka plan servisi", "Sorun: durmuş görünüyor. Dokunup yeniden başlatın.",
                    summaryColor = pal.danger, trailing = ui.action("Başlat")
                ) {
                    VolumeService.refresh(this)
                    toast("Yeniden başlatıldı; birkaç saniye sonra Yenile'ye dokunun")
                }
            )
        }

        // Yükseltme efekti
        val boosting = (if (free) Store.keyBoost(this) else Store.activeLevel(this)) > 100
        ui.addRow(
            sec,
            ui.row(
                "Yükseltme efekti",
                if (boosting) Store.boostStatus(this) else "Şu an yükseltme istenmiyor",
                summaryColor = if (boosting && Store.boostStatus(this).startsWith("Bu cihaz")) pal.danger else pal.inkSoft
            )
        )

        // Tuş hizmeti
        if (free) {
            val enabled = keyServiceEnabled()
            when {
                !enabled -> ui.addRow(
                    sec,
                    ui.row("Tuş hizmeti", "Sorun: izin verilmemiş. Yukarıdaki “Tuş hizmeti” adımlarını izleyin.", summaryColor = pal.danger)
                )
                !VolumeKeyService.connected -> ui.addRow(
                    sec,
                    ui.row(
                        "Tuş hizmeti",
                        "Sorun: izin açık ama hizmet bağlı değil. Erişilebilirlik ayarlarında Ses Ustası'nı kapatıp tekrar açın.",
                        summaryColor = pal.danger, trailing = ui.action("Aç")
                    ) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                )
                else -> ui.addRow(sec, ui.row("Tuş hizmeti", "Tamam: bağlı"))
            }
            ui.addRow(
                sec,
                ui.row(
                    "Medya",
                    if (am.isMusicActive) "Tamam: şu an çalıyor"
                    else "Çalmıyor. Tuşla yükseltme yalnızca medya çalarken (ya da son 10 sn içinde çalmışken) devreye girer."
                )
            )
        }

        ui.addRow(sec, ui.row("Yenile", "Bu kutudaki durumları günceller", trailing = ui.action("Yenile")) { render() })
        return sec
    }

    /** Marka bazlı pil/otomatik başlatma yönergesi. */
    private fun oemTip(): String? {
        val m = Build.MANUFACTURER.lowercase()
        return when {
            m.contains("samsung") ->
                "Samsung: Ayarlar → Pil → Arka plan kullanım sınırları → “Uyutulmayacak uygulamalar”a Ses Ustası'nı ekleyin."
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") ->
                "Xiaomi: Ayarlar → Uygulamalar → Ses Ustası → “Otomatik başlat”ı açın, Pil tasarrufunu “Kısıtlama yok” yapın."
            m.contains("huawei") || m.contains("honor") ->
                "Huawei/Honor: Ayarlar → Pil → Uygulama başlatma → Ses Ustası → “Elle yönet”, üç seçeneği de açın."
            m.contains("oppo") || m.contains("realme") || m.contains("oneplus") || m.contains("vivo") ->
                "Ayarlar → Pil → Ses Ustası → arka planda çalışmaya ve otomatik başlatmaya izin verin."
            else -> null
        }
    }

    // ---------- Güncelleme bölümü ----------

    private fun fillUpdate() {
        val box = updateBox ?: return
        box.removeAllViews()
        val st = Updater.state

        val summary = when (st) {
            Updater.State.DOWNLOADING -> "İndiriliyor %${Updater.progress}"
            Updater.State.IDLE -> "Henüz denetlenmedi"
            else -> Updater.message
        }
        val summaryColor = if (st == Updater.State.ERROR || st == Updater.State.NO_REPO) pal.danger else pal.inkSoft
        ui.addRow(box, ui.row("Sürüm ${BuildConfig.VERSION_NAME}", summary, summaryColor = summaryColor))

        val rel = Updater.latest
        if (st == Updater.State.AVAILABLE && rel != null && rel.notes.isNotBlank()) {
            ui.addRow(box, ui.row("Yenilikler", rel.notes.trim().take(400)))
        }

        val pad = ui.vbox()
        pad.setPadding(dp(16), dp(8), dp(16), dp(16))
        val btn = if (st == Updater.State.AVAILABLE && rel != null) {
            ui.button(if (Updater.message.startsWith("Onay penceresi")) "Tekrar dene" else "Güncelle ve kur", Ui.Kind.FILLED) {
                if (Updater.message.startsWith("Onay penceresi")) Updater.retryInstall(this) else startInstall()
            }
        } else {
            val label = when (st) {
                Updater.State.CHECKING -> "Denetleniyor…"
                Updater.State.DOWNLOADING -> "İndiriliyor %${Updater.progress}"
                Updater.State.INSTALLING -> "Tekrar dene"
                else -> "Güncellemeleri denetle"
            }
            val locked = st == Updater.State.CHECKING || st == Updater.State.DOWNLOADING
            ui.button(label, Ui.Kind.TONAL, enabled = !locked) {
                if (st == Updater.State.INSTALLING) Updater.retryInstall(this) else Updater.check(this)
            }
        }
        pad.addView(btn, ui.lp(ui.match, ui.wrap))
        box.addView(pad)

        ui.addRow(
            box,
            ui.row(
                "Otomatik güncelle",
                "Arka planda yaklaşık 6 saatte bir denetler ve yeni sürümü indirir. Android izin verirse sessizce kurar, vermezse bildirimle onay ister.",
                trailing = styledSwitch(Store.autoUpdate(this)) { on ->
                    Store.setAutoUpdate(this, on)
                    UpdateJob.sync(this)
                }
            )
        )
        val repo = Updater.repo(this)
        ui.addRow(
            box,
            ui.row(
                "Güncelleme kaynağı", if (repo.isBlank()) "Ayarlanmadı" else repo,
                trailing = ui.action("Düzenle")
            ) { repoDialog() }
        )
    }

    private fun startInstall() {
        if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            toast("Önce “Bu kaynaktan yüklemeye izin ver”i açın, sonra geri dönüp tekrar dokunun")
            val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            i.data = Uri.parse("package:$packageName")
            startActivity(i)
            return
        }
        Updater.downloadAndInstall(this)
    }

    private fun repoDialog() {
        val l = ui.vbox()
        l.setPadding(dp(20), dp(12), dp(20), 0)
        val repo = ui.field("kullanıcı/depo (örn. ahmet/SesUstasi)", false)
        repo.setText(Updater.repo(this))
        val token = ui.field("Erişim anahtarı (yalnızca özel depo)", false)
        token.setText(Store.updateToken(this))
        l.addView(repo)
        l.addView(token, ui.lp(ui.match, ui.wrap, t = 12))
        l.addView(
            ui.small(
                "Depo özelse GitHub'da yalnızca bu depo için “Contents: Read-only” yetkili bir " +
                    "erişim anahtarı (fine-grained token) oluşturup yapıştırın. Anahtar yalnızca bu cihazda saklanır."
            ),
            ui.lp(ui.match, ui.wrap, t = 8)
        )
        AlertDialog.Builder(this)
            .setTitle("Güncelleme kaynağı")
            .setView(l)
            .setPositiveButton("Kaydet") { _, _ ->
                Store.setUpdateRepo(this, repo.text.toString())
                Store.setUpdateToken(this, token.text.toString())
                Store.setLastUpdateCheck(this, 0L)
                fillUpdate()
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    // ---------- PIN / desen belirleme ----------

    private fun pinDialog() {
        val l = ui.vbox()
        l.setPadding(dp(20), dp(12), dp(20), 0)
        val a = ui.field("Yeni PIN (en az 4 rakam)", true)
        val b = ui.field("PIN tekrar", true)
        l.addView(a)
        l.addView(b, ui.lp(ui.match, ui.wrap, t = 12))
        AlertDialog.Builder(this)
            .setTitle("PIN belirle")
            .setView(l)
            .setPositiveButton("Kaydet") { _, _ ->
                val x = a.text.toString()
                if (x.length < 4 || x != b.text.toString()) {
                    toast("PIN en az 4 haneli olmalı ve iki kutu aynı olmalı")
                } else {
                    Store.setPin(this, x)
                    toast("PIN kaydedildi")
                    render()
                }
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun patternDialog() {
        var first: String? = null
        val msg = ui.small("Deseni çizin (en az 4 nokta)")
        val pv = PatternView(this)
        pv.setColors(pal.line, pal.accent)
        val l = ui.vbox()
        l.gravity = Gravity.CENTER_HORIZONTAL
        l.setPadding(dp(20), dp(12), dp(20), 0)
        l.addView(msg)
        l.addView(pv, ui.lp(dp(260), dp(260), t = 8))
        val dlg = AlertDialog.Builder(this)
            .setTitle("Desen belirle")
            .setView(l)
            .setNegativeButton("İptal", null)
            .create()
        pv.onDone = { s ->
            if (s.length < 4) {
                msg.text = "En az 4 noktayı bağlayın"
            } else if (first == null) {
                first = s
                msg.text = "Aynı deseni tekrar çizin"
            } else if (first == s) {
                Store.setPattern(this, s)
                toast("Desen kaydedildi")
                dlg.dismiss()
                render()
            } else {
                first = null
                msg.text = "Eşleşmedi, baştan çizin"
            }
        }
        dlg.show()
    }
}
