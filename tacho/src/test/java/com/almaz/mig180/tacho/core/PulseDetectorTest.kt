package com.almaz.mig180.tacho.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PulseDetectorTest {

    /** Синтетический сигнал: метка проходит каждые [framesPerRevolution] кадров. */
    private fun feed(
        detector: PulseDetector,
        revolutions: Int,
        framesPerRevolution: Int,
        pulseFrames: Int = 2,
        frameIntervalMs: Double = 1000.0 / 60.0,
        baseLuma: Double = 100.0,
        pulseLuma: Double = 200.0,
    ): Int {
        var pulses = 0
        var frame = 0
        repeat(revolutions * framesPerRevolution) {
            val phase = frame % framesPerRevolution
            val value = if (phase < pulseFrames) pulseLuma else baseLuma
            val ts = (frame * frameIntervalMs * 1_000_000.0).toLong()
            if (detector.update(value, ts)) pulses++
            frame++
        }
        return pulses
    }

    @Test
    fun `detects one pulse per revolution and reports the right period`() {
        val detector = PulseDetector()
        // 20 кадров на оборот при 60 к/с = 333.3 мс, то есть 180 об/мин.
        feed(detector, revolutions = 40, framesPerRevolution = 20)

        val period = detector.medianPeriodMs()
        assertTrue("период должен быть посчитан", period.isFinite())
        assertEquals(1000.0 / 60.0 * 20.0, period, 1.0)
        assertEquals(180.0, SpeedMath.rpm(period), 1.0)
    }

    @Test
    fun `steady signal is reported as stable`() {
        val detector = PulseDetector()
        feed(detector, revolutions = 40, framesPerRevolution = 20)
        assertTrue("ровный сигнал должен считаться стабильным", detector.isStable())
        assertTrue(detector.spread() < 0.12)
    }

    @Test
    fun `debounce rejects a second edge inside the same revolution`() {
        val detector = PulseDetector()
        // Разгоняем детектор ровным сигналом, чтобы появилась медиана периодов.
        feed(detector, revolutions = 30, framesPerRevolution = 20)
        val before = detector.pulseCount
        val lastNs = detector.lastPulseNs

        // Дребезг через 20 мс после настоящего импульса — меньше 0.4 * 333 мс.
        detector.update(200.0, lastNs + 20_000_000L)
        assertEquals("дребезг не должен считаться оборотом", before, detector.pulseCount)
    }

    @Test
    fun `flat signal produces no pulses`() {
        val detector = PulseDetector()
        var frame = 0
        var pulses = 0
        repeat(300) {
            val ts = (frame * (1000.0 / 60.0) * 1_000_000.0).toLong()
            if (detector.update(100.0, ts)) pulses++
            frame++
        }
        assertEquals(0, pulses)
        assertFalse(detector.medianPeriodMs().isFinite())
    }

    @Test
    fun `reset clears accumulated state`() {
        val detector = PulseDetector()
        feed(detector, revolutions = 30, framesPerRevolution = 20)
        assertTrue(detector.pulseCount > 0)

        detector.reset()
        assertEquals(0L, detector.pulseCount)
        assertEquals(0, detector.periodCount())
        assertEquals(0L, detector.lastPulseNs)
        assertFalse(detector.isStable())
    }
}
