package com.aile.sesustasi

/** Ses hesapları (saf fonksiyonlar, birim testle doğrulanır). */
object VolumeMath {
    const val MAX_CHILD = 100

    /** Ebeveyn modunda en yüksek seviye: %1000 = 10 kat genlik = +20 dB. */
    const val MAX_PARENT = 1000

    /** Bu seviyenin üstünde ses bozulabilir ve hoparlöre zarar verebilir. */
    const val SAFE_BOOST = 300

    /** Çocuk modu 100'ü, ebeveyn modu 1000'i aşamaz. */
    fun clampLevel(child: Boolean, v: Int): Int =
        if (child) v.coerceIn(0, MAX_CHILD) else v.coerceIn(0, MAX_PARENT)

    /** Seviyeye karşılık gelen sistem medya sesi adımı. 100 üstünde sistem sesi maksimumdur. */
    fun targetVol(level: Int, max: Int): Int =
        if (level <= 100) Math.round(level / 100f * max).coerceIn(0, max) else max

    /** Sistem ses adımından yüzde (0-100). */
    fun percentOf(vol: Int, max: Int): Int =
        if (max <= 0) 0 else Math.round(vol * 100f / max).coerceIn(0, 100)

    /** LoudnessEnhancer kazancı (milibel). 100 ve altında yükseltme yoktur. */
    fun gainMb(level: Int): Int =
        if (level <= 100) 0 else (2000.0 * Math.log10(level / 100.0)).toInt()

    /**
     * Ses tuşlarıyla yükseltme adımı: 300'e kadar 25'er, sonrası 50'şer.
     * Yukarı ve aşağı aynı basamaklardan geçer (300 → 350 → 300).
     */
    fun boostStep(cur: Int, up: Boolean): Int {
        val next = if (up) cur + (if (cur < SAFE_BOOST) 25 else 50)
        else cur - (if (cur <= SAFE_BOOST) 25 else 50)
        return next.coerceIn(100, MAX_PARENT)
    }
}
