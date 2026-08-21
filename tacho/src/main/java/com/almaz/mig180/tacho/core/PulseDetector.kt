package com.almaz.mig180.tacho.core

import kotlin.math.abs
import kotlin.math.max

/**
 * Детектор проходов метки по яркости ROI.
 *
 * Порог адаптивный: baseline — EMA яркости, dev — EMA модуля отклонения,
 * порог = max(minThreshold, dev * devFactor). Выход из импульса — по
 * гистерезису (0.5 порога), ложные срабатывания режутся дебаунсом
 * max(70 мс, медиана периодов * 0.4).
 *
 * Все методы синхронизированы: update() зовётся с потока анализа кадров,
 * reset()/геттеры — с UI-потока.
 */
class PulseDetector(
    private val alpha: Double = 0.03,
    private val minThreshold: Double = 12.0,
    private val devFactor: Double = 3.5,
    private val hysteresisFactor: Double = 0.5,
    private val minGapMs: Double = 70.0,
    private val gapPeriodFactor: Double = 0.4,
    private val periodWindow: Int = 10,
    private val warmupFrames: Int = 20,
) {
    var baseline: Double = 0.0
        private set
    var deviation: Double = 0.0
        private set
    var threshold: Double = minThreshold
        private set
    var pulseCount: Long = 0L
        private set
    /** Таймстемп последнего принятого импульса, нс (0 — импульсов ещё не было). */
    var lastPulseNs: Long = 0L
        private set

    private var initialized = false
    private var framesSeen = 0
    private var inPulse = false
    private val periods = ArrayDeque<Double>()

    @Synchronized
    fun reset() {
        initialized = false
        framesSeen = 0
        inPulse = false
        baseline = 0.0
        deviation = 0.0
        threshold = minThreshold
        pulseCount = 0L
        lastPulseNs = 0L
        periods.clear()
    }

    /**
     * Скармливает очередной отсчёт яркости.
     * @return true, если на этом кадре зафиксирован новый импульс.
     */
    @Synchronized
    fun update(value: Double, timestampNs: Long): Boolean {
        if (!value.isFinite()) return false

        if (!initialized) {
            initialized = true
            baseline = value
            deviation = 0.0
            threshold = minThreshold
        }

        val diff = value - baseline
        val magnitude = abs(diff)

        baseline += alpha * diff
        deviation += alpha * (magnitude - deviation)
        threshold = max(minThreshold, deviation * devFactor)

        if (framesSeen < Int.MAX_VALUE) framesSeen++
        val warm = framesSeen > warmupFrames

        var pulsed = false
        if (!inPulse) {
            if (magnitude > threshold) {
                inPulse = true
                if (warm && acceptPulse(timestampNs)) pulsed = true
            }
        } else {
            if (magnitude < threshold * hysteresisFactor) inPulse = false
        }
        return pulsed
    }

    private fun acceptPulse(timestampNs: Long): Boolean {
        if (lastPulseNs == 0L) {
            lastPulseNs = timestampNs
            pulseCount++
            return true
        }
        val gapMs = (timestampNs - lastPulseNs) / 1_000_000.0
        if (gapMs <= 0.0) return false
        val median = medianPeriodMsLocked()
        val requiredGap = if (median.isFinite() && median > 0.0) {
            max(minGapMs, median * gapPeriodFactor)
        } else {
            minGapMs
        }
        if (gapMs < requiredGap) return false

        periods.addLast(gapMs)
        while (periods.size > periodWindow) periods.removeFirst()
        lastPulseNs = timestampNs
        pulseCount++
        return true
    }

    @Synchronized
    fun medianPeriodMs(): Double = medianPeriodMsLocked()

    private fun medianPeriodMsLocked(): Double = median(periods)

    @Synchronized
    fun periodCount(): Int = periods.size

    /** Разброс последних 5 периодов, доля от их медианы. NaN — данных мало. */
    @Synchronized
    fun spread(): Double {
        if (periods.size < 5) return Double.NaN
        val tail = periods.toList().takeLast(5)
        val med = median(tail)
        if (!med.isFinite() || med <= 0.0) return Double.NaN
        return (tail.max() - tail.min()) / med
    }

    /** Стабильно — если разброс последних 5 периодов меньше 12 % медианы. */
    @Synchronized
    fun isStable(): Boolean {
        val s = spread()
        return s.isFinite() && s < 0.12
    }

    private fun median(source: Collection<Double>): Double {
        if (source.isEmpty()) return Double.NaN
        val sorted = source.sorted()
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
    }
}
