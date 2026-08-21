package com.almaz.mig180.tacho.core

import kotlin.math.PI

object SpeedMath {

    /** Геометрический диаметр ролика по умолчанию, мм. */
    const val DEFAULT_GEOMETRIC_DIAMETER_MM = 29.30

    /** Эффективная окружность по умолчанию, мм (до первой калибровки). */
    const val DEFAULT_CIRCUMFERENCE_MM = 92.05

    fun rpm(periodMs: Double): Double =
        if (periodMs.isFinite() && periodMs > 0.0) 60_000.0 / periodMs else 0.0

    /** rpm * C(мм) * k / 1000 = м/мин. */
    fun metersPerMinute(rpm: Double, circumferenceMm: Double, correction: Double): Double {
        if (!rpm.isFinite() || rpm <= 0.0) return 0.0
        if (!circumferenceMm.isFinite() || circumferenceMm <= 0.0) return 0.0
        if (!correction.isFinite() || correction <= 0.0) return 0.0
        return rpm * circumferenceMm * correction / 1000.0
    }

    /** Сколько сантиметров проволоки выйдет за 20 секунд. */
    fun cmPer20s(metersPerMinute: Double): Double = metersPerMinute * 100.0 / 3.0

    fun circumferenceOf(diameterMm: Double): Double = diameterMm * PI

    fun diameterOf(circumferenceMm: Double): Double =
        if (circumferenceMm.isFinite()) circumferenceMm / PI else Double.NaN

    /**
     * Эффективная окружность по калибровке.
     * @param lengthMm длина вышедшей проволоки, мм
     * @param revolutions накопленные обороты ролика (с дробной частью)
     * @return C, мм; NaN — если данных не хватает
     */
    fun circumferenceFromCalibration(lengthMm: Double, revolutions: Double): Double {
        if (!lengthMm.isFinite() || lengthMm <= 0.0) return Double.NaN
        if (!revolutions.isFinite() || revolutions <= 0.0) return Double.NaN
        return lengthMm / revolutions
    }
}
