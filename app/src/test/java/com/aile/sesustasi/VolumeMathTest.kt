package com.aile.sesustasi

import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeMathTest {
    @Test fun childNeverAbove100() {
        assertEquals(100, VolumeMath.clampLevel(true, 500))
        assertEquals(0, VolumeMath.clampLevel(true, -5))
    }

    @Test fun parentCappedAt500() {
        assertEquals(500, VolumeMath.clampLevel(false, 900))
        assertEquals(250, VolumeMath.clampLevel(false, 250))
    }

    @Test fun targetVolScalesBelow100() {
        assertEquals(0, VolumeMath.targetVol(0, 15))
        assertEquals(8, VolumeMath.targetVol(50, 15))
        assertEquals(15, VolumeMath.targetVol(100, 15))
    }

    @Test fun targetVolMaxedAbove100() {
        assertEquals(15, VolumeMath.targetVol(300, 15))
    }

    @Test fun noGainAtOrBelow100() {
        assertEquals(0, VolumeMath.gainMb(100))
        assertEquals(0, VolumeMath.gainMb(40))
    }

    @Test fun gainGrowsAbove100() {
        assertEquals(602, VolumeMath.gainMb(200))
        assertEquals(1397, VolumeMath.gainMb(500))
    }
}
