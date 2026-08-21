package com.almaz.mig180.tacho.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedMathTest {

    @Test
    fun `rpm from period`() {
        assertEquals(180.0, SpeedMath.rpm(333.333), 0.01)
        assertEquals(60.0, SpeedMath.rpm(1000.0), 0.001)
    }

    @Test
    fun `speed uses circumference and correction`() {
        // 180 об/мин при C = 92.05 мм даёт 16.569 м/мин.
        val speed = SpeedMath.metersPerMinute(180.0, 92.05, 1.0)
        assertEquals(16.569, speed, 0.001)
        assertEquals(speed * 1.05, SpeedMath.metersPerMinute(180.0, 92.05, 1.05), 0.001)
    }

    @Test
    fun `cm per twenty seconds is a third of the metres per minute`() {
        assertEquals(100.0, SpeedMath.cmPer20s(3.0), 0.001)
    }

    @Test
    fun `no division by zero anywhere`() {
        assertEquals(0.0, SpeedMath.rpm(0.0), 0.0)
        assertEquals(0.0, SpeedMath.rpm(Double.NaN), 0.0)
        assertEquals(0.0, SpeedMath.metersPerMinute(0.0, 92.05, 1.0), 0.0)
        assertEquals(0.0, SpeedMath.metersPerMinute(180.0, 0.0, 1.0), 0.0)
        assertEquals(0.0, SpeedMath.metersPerMinute(180.0, 92.05, 0.0), 0.0)
        assertFalse(SpeedMath.circumferenceFromCalibration(1000.0, 0.0).isFinite())
        assertFalse(SpeedMath.circumferenceFromCalibration(0.0, 10.0).isFinite())
    }

    @Test
    fun `default circumference matches the default diameter`() {
        assertEquals(
            SpeedMath.DEFAULT_CIRCUMFERENCE_MM,
            SpeedMath.circumferenceOf(SpeedMath.DEFAULT_GEOMETRIC_DIAMETER_MM),
            0.01,
        )
    }

    @Test
    fun `calibration inverts the speed formula`() {
        // 12 оборотов вытянули 110 см проволоки.
        val circumference = SpeedMath.circumferenceFromCalibration(1100.0, 12.0)
        assertEquals(91.666, circumference, 0.001)
        assertTrue(SpeedMath.diameterOf(circumference) > 0.0)
    }
}
