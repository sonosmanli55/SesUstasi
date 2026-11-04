package com.aile.sesustasi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

/** 3x3 desen kilidi. Parmak kalkınca çizilen sıra (örn. "0145") onDone ile bildirilir. */
class PatternView(c: Context) : View(c) {

    var onDone: ((String) -> Unit)? = null

    private val sel = ArrayList<Int>()
    private var fx = 0f
    private var fy = 0f
    private var down = false
    private val density = c.resources.displayMetrics.density

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9E9E9E.toInt() }
    private val onPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0B6B5A.toInt() }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0B6B5A.toInt()
        strokeWidth = 6f * density
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    /** Renkleri tasarım paletinden ayarlar. */
    fun setColors(dot: Int, on: Int) {
        dotPaint.color = dot
        onPaint.color = on
        linePaint.color = on
        invalidate()
    }

    override fun onMeasure(w: Int, h: Int) {
        val s = Math.min(MeasureSpec.getSize(w), MeasureSpec.getSize(h))
        setMeasuredDimension(s, s)
    }

    private fun cx(i: Int) = (i % 3 + 0.5f) * width / 3f
    private fun cy(i: Int) = (i / 3 + 0.5f) * height / 3f

    private fun hit(x: Float, y: Float) {
        for (i in 0..8) {
            if (!sel.contains(i)) {
                val dx = x - cx(i)
                val dy = y - cy(i)
                if (Math.sqrt((dx * dx + dy * dy).toDouble()) < width / 9.0) sel.add(i)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        for (k in 1 until sel.size) {
            canvas.drawLine(cx(sel[k - 1]), cy(sel[k - 1]), cx(sel[k]), cy(sel[k]), linePaint)
        }
        if (down && sel.isNotEmpty()) {
            val last = sel[sel.size - 1]
            canvas.drawLine(cx(last), cy(last), fx, fy, linePaint)
        }
        for (i in 0..8) {
            if (sel.contains(i)) canvas.drawCircle(cx(i), cy(i), 12f * density, onPaint)
            else canvas.drawCircle(cx(i), cy(i), 8f * density, dotPaint)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                sel.clear()
                down = true
                parent?.requestDisallowInterceptTouchEvent(true)
                fx = e.x
                fy = e.y
                hit(e.x, e.y)
            }
            MotionEvent.ACTION_MOVE -> {
                fx = e.x
                fy = e.y
                hit(e.x, e.y)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                down = false
                val s = sel.joinToString("")
                val ok = e.actionMasked == MotionEvent.ACTION_UP && s.isNotEmpty()
                postDelayed({
                    sel.clear()
                    invalidate()
                }, 250)
                if (ok) onDone?.invoke(s)
            }
        }
        invalidate()
        return true
    }
}
