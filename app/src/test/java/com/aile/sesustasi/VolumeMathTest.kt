package com.aile.sesustasi

import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeMathTest {
    @Test fun childNeverAbove100() {
        assertEquals(100, VolumeMath.clampLevel(true, 500))
        assertEquals(0, VolumeMath.clampLevel(true, -5))
    }

    @Test fun parentCappedAt1000() {
        assertEquals(1000, VolumeMath.clampLevel(false, 5000))
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

    @Test fun percentOfVolume() {
        assertEquals(0, VolumeMath.percentOf(0, 15))
        assertEquals(100, VolumeMath.percentOf(15, 15))
        assertEquals(53, VolumeMath.percentOf(8, 15))
        assertEquals(0, VolumeMath.percentOf(5, 0))
    }

    @Test fun noGainAtOrBelow100() {
        assertEquals(0, VolumeMath.gainMb(100))
        assertEquals(0, VolumeMath.gainMb(40))
    }

    @Test fun gainGrowsAbove100() {
        assertEquals(602, VolumeMath.gainMb(200))
        assertEquals(1397, VolumeMath.gainMb(500))
        assertEquals(2000, VolumeMath.gainMb(1000))
    }

    @Test fun boostStepUpAndDown() {
        assertEquals(125, VolumeMath.boostStep(100, true))
        assertEquals(100, VolumeMath.boostStep(100, false))
        assertEquals(350, VolumeMath.boostStep(300, true))
        assertEquals(300, VolumeMath.boostStep(350, false))
        assertEquals(275, VolumeMath.boostStep(300, false))
        assertEquals(300, VolumeMath.boostStep(275, true))
        assertEquals(1000, VolumeMath.boostStep(1000, true))
    }
}
