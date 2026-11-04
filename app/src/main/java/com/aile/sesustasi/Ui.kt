package com.aile.sesustasi

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Renk rolleri. Açık ve koyu şema ayrı tasarlandı (koyu, ters çevirme değildir). */
class Palette(val dark: Boolean) {
    val bg = if (dark) 0xFF12110E.toInt() else 0xFFF4F2EC.toInt()
    val surface = if (dark) 0xFF1D1B17.toInt() else 0xFFFFFFFF.toInt()
    val surfaceHi = if (dark) 0xFF2C2924.toInt() else 0xFFEAE6DC.toInt()
    val ink = if (dark) 0xFFF1EEE7.toInt() else 0xFF1B1A17.toInt()
    val inkSoft = if (dark) 0xFFB8B3A8.toInt() else 0xFF5C5850.toInt()
    val line = if (dark) 0xFF35322B.toInt() else 0xFFE4DFD3.toInt()
    val accent = if (dark) 0xFF5CC9B1.toInt() else 0xFF0B6B5A.toInt()
    val onAccent = if (dark) 0xFF06241E.toInt() else 0xFFFFFFFF.toInt()
    val accentSoft = if (dark) 0xFF1F4239.toInt() else 0xFFD5ECE5.toInt()
    val boost = if (dark) 0xFFF2A65A.toInt() else 0xFFA84A06.toInt()
    val boostSoft = if (dark) 0xFF4A3216.toInt() else 0xFFFBE5CB.toInt()
    val danger = if (dark) 0xFFFF9B92.toInt() else 0xFFB3261E.toInt()
}

/** Programatik arayüz için küçük yapı taşları: yazı rolleri, düğmeler, satırlar, bölümler. */
class Ui(val c: Context) {

    val pal = Palette(
        (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    )
    private val density = c.resources.displayMetrics.density

    fun dp(v: Int) = (v * density + 0.5f).toInt()
    fun dpf(v: Int) = v * density

    companion object {
        const val REGULAR = "sans-serif"
        const val MEDIUM = "sans-serif-medium"
    }

    // ---------- Yazı ----------

    fun text(s: String, sp: Float, color: Int, face: String = REGULAR): TextView {
        val t = TextView(c)
        t.text = s
        t.textSize = sp
        t.setTextColor(color)
        t.typeface = Typeface.create(face, Typeface.NORMAL)
        t.fontFeatureSettings = "tnum"
        t.setLineSpacing(0f, 1.1f)
        return t
    }

    fun title(s: String) = text(s, 30f, pal.ink, MEDIUM)
    fun heading(s: String) = text(s, 14f, pal.inkSoft, MEDIUM)
    fun body(s: String) = text(s, 16f, pal.ink)
    fun small(s: String) = text(s, 13f, pal.inkSoft)

    // ---------- Şekiller ----------

    fun rounded(fill: Int, radiusDp: Int, stroke: Int? = null): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(fill)
        g.cornerRadius = dpf(radiusDp)
        if (stroke != null) g.setStroke(dp(1), stroke)
        return g
    }

    private fun withRipple(content: Drawable, radiusDp: Int): Drawable {
        val mask = GradientDrawable()
        mask.setColor(Color.WHITE)
        mask.cornerRadius = dpf(radiusDp)
        val rc = if (pal.dark) 0x33FFFFFF else 0x1F000000
        return RippleDrawable(ColorStateList.valueOf(rc), content, mask)
    }

    fun lp(w: Int, h: Int, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(w, h)
        p.setMargins(dp(l), dp(t), dp(r), dp(b))
        return p
    }

    val match = ViewGroup.LayoutParams.MATCH_PARENT
    val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

    fun vbox(): LinearLayout {
        val l = LinearLayout(c)
        l.orientation = LinearLayout.VERTICAL
        return l
    }

    // ---------- Düğmeler (48 dp dokunma hedefi) ----------

    enum class Kind { FILLED, TONAL, TEXT }

    fun button(label: String, kind: Kind = Kind.TONAL, enabled: Boolean = true, onClick: () -> Unit): TextView {
        val fill = when (kind) {
            Kind.FILLED -> pal.accent
            Kind.TONAL -> pal.accentSoft
            Kind.TEXT -> Color.TRANSPARENT
        }
        val fg = if (kind == Kind.FILLED) pal.onAccent else pal.accent
        val t = text(label, 15f, fg, MEDIUM)
        t.gravity = Gravity.CENTER
        t.minHeight = dp(48)
        t.setPadding(dp(20), dp(10), dp(20), dp(10))
        t.background = withRipple(rounded(fill, 24), 24)
        t.isClickable = enabled
        t.isFocusable = enabled
        t.alpha = if (enabled) 1f else 0.5f
        if (enabled) t.setOnClickListener { onClick() }
        return t
    }

    // ---------- Bölüm ve satırlar ----------

    /** Düz, tek katmanlı yüzey. İçine satırlar dizilir; kart içinde kart yok. */
    fun section(): LinearLayout {
        val s = vbox()
        s.background = rounded(pal.surface, 20)
        return s
    }

    fun divider(): View {
        val v = View(c)
        v.setBackgroundColor(pal.line)
        return v
    }

    fun addRow(section: LinearLayout, row: View) {
        if (section.childCount > 0) {
            section.addView(divider(), lp(match, 1, l = 20))
        }
        section.addView(row, lp(match, wrap))
    }

    /** Başlık + açıklama solda, isteğe bağlı kontrol sağda. */
    fun row(
        title: String,
        summary: String? = null,
        trailing: View? = null,
        summaryColor: Int = pal.inkSoft,
        onClick: (() -> Unit)? = null
    ): View {
        val r = LinearLayout(c)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.minimumHeight = dp(if (summary != null) 68 else 56)
        r.setPadding(dp(20), dp(10), dp(16), dp(10))
        val col = vbox()
        col.addView(body(title))
        if (summary != null) {
            val s = small(summary)
            s.setTextColor(summaryColor)
            col.addView(s, lp(wrap, wrap, t = 2))
        }
        r.addView(col, LinearLayout.LayoutParams(0, wrap, 1f))
        if (trailing != null) r.addView(trailing, lp(wrap, wrap, l = 12))
        if (onClick != null) {
            r.background = withRipple(rounded(Color.TRANSPARENT, 20), 20)
            r.isClickable = true
            r.isFocusable = true
            r.setOnClickListener { onClick() }
        }
        return r
    }

    /** Satırın sağındaki eylem etiketi ("Değiştir" gibi). */
    fun action(label: String): TextView = text(label, 14f, pal.accent, MEDIUM)

    // ---------- Giriş alanı ----------

    fun field(hint: String, numeric: Boolean): EditText {
        val e = EditText(c)
        e.hint = hint
        e.setHintTextColor(pal.inkSoft)
        e.setTextColor(pal.ink)
        e.textSize = 18f
        e.inputType = if (numeric) {
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT
        }
        e.minHeight = dp(52)
        e.setPadding(dp(16), dp(8), dp(16), dp(8))
        e.background = rounded(pal.surfaceHi, 14)
        return e
    }
}

/** Kilit ekranı için çizilmiş asma kilit simgesi (emoji yerine). */
class LockGlyph(c: Context, private val color: Int) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = c.resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.color = color
        p.style = Paint.Style.STROKE
        p.strokeWidth = 4f * d
        p.strokeCap = Paint.Cap.ROUND
        val sw = w * 0.5f
        val shackle = RectF((w - sw) / 2f, h * 0.08f, (w + sw) / 2f, h * 0.08f + sw)
        canvas.drawArc(shackle, 180f, 180f, false, p)
        canvas.drawLine(shackle.left, shackle.centerY(), shackle.left, h * 0.45f, p)
        canvas.drawLine(shackle.right, shackle.centerY(), shackle.right, h * 0.45f, p)
        p.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(w * 0.15f, h * 0.42f, w * 0.85f, h * 0.95f), 8f * d, 8f * d, p)
    }
}
