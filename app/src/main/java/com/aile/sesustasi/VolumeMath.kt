package com.aile.sesustasi

/** Ses hesapları (saf fonksiyonlar, birim testle doğrulanır). */
object VolumeMath {
    const val MAX_CHILD = 100
    const val MAX_PARENT = 500

    /** Çocuk modu 100'ü, ebeveyn modu 500'ü aşamaz. */
    fun clampLevel(child: Boolean, v: Int): Int =
        if (child) v.coerceIn(0, MAX_CHILD) else v.coerceIn(0, MAX_PARENT)

    /** Seviyeye karşılık gelen sistem medya sesi adımı. 100 üstünde sistem sesi maksimumdur. */
    fun targetVol(level: Int, max: Int): Int =
        if (level <= 100) Math.round(level / 100f * max).coerceIn(0, max) else max

    /** LoudnessEnhancer kazancı (milibel). 100 ve altında yükseltme yoktur. */
    fun gainMb(level: Int): Int =
        if (level <= 100) 0 else (2000.0 * Math.log10(level / 100.0)).toInt()

    const val BOOST_STEP = 25

    /** Ses tuşlarıyla yükseltme: 100 ile 500 arasında, adım adım. */
    fun stepBoost(cur: Int, delta: Int): Int = (cur + delta).coerceIn(100, MAX_PARENT)
}
