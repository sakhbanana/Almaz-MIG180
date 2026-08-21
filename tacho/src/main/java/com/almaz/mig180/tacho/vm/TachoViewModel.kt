package com.almaz.mig180.tacho.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.almaz.mig180.tacho.camera.LuminanceAnalyzer
import com.almaz.mig180.tacho.core.ChartBuffer
import com.almaz.mig180.tacho.core.ChartSnapshot
import com.almaz.mig180.tacho.core.FrameGeometry
import com.almaz.mig180.tacho.core.PulseDetector
import com.almaz.mig180.tacho.core.Roi
import com.almaz.mig180.tacho.core.SpeedMath
import com.almaz.mig180.tacho.data.LogPoint
import com.almaz.mig180.tacho.data.LogRepository
import com.almaz.mig180.tacho.data.SettingsRepository
import com.almaz.mig180.tacho.data.TachoSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** Состояние экрана измерения. Обновляется с потока анализа кадров. */
data class MeasureUiState(
    val geometry: FrameGeometry = FrameGeometry(),
    val roi: Roi? = null,
    val pulseCount: Long = 0L,
    val periodsCollected: Int = 0,
    val periodMs: Double = Double.NaN,
    val rpm: Double = 0.0,
    val speedMMin: Double = 0.0,
    val cmPer20s: Double = 0.0,
    val stable: Boolean = false,
    val spread: Double = Double.NaN,
    val msSinceLastPulse: Long = 0L,
    val everPulsed: Boolean = false,
    val fps: Double = 0.0,
    val chart: ChartSnapshot = ChartSnapshot.EMPTY,
)

/** Результат мастера калибровки. */
data class CalibrationResult(
    val circumferenceMm: Double,
    val effectiveDiameterMm: Double,
    val revolutions: Double,
    val lengthMm: Double,
    /** Отклонение эквивалентного диаметра от геометрического, %. NaN — не с чем сравнивать. */
    val deviationPercent: Double,
)

data class CalibrationUiState(
    val running: Boolean = false,
    val revolutions: Double = 0.0,
    val pulses: Int = 0,
    /** Замер остановлен, обороты зафиксированы — можно вводить длину. */
    val measured: Boolean = false,
    val result: CalibrationResult? = null,
    val error: String? = null,
)

class TachoViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val logRepo = LogRepository(application)

    val settings: StateFlow<TachoSettings> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, TachoSettings())

    val logPoints: StateFlow<List<LogPoint>> =
        logRepo.points.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _ui = MutableStateFlow(MeasureUiState())
    val ui: StateFlow<MeasureUiState> = _ui.asStateFlow()

    private val _calibration = MutableStateFlow(CalibrationUiState())
    val calibration: StateFlow<CalibrationUiState> = _calibration.asStateFlow()

    private val detector = PulseDetector()
    private val chart = ChartBuffer(CHART_CAPACITY)
    private val pendingReset = AtomicBoolean(false)

    @Volatile
    private var settingsSnapshot = TachoSettings()

    // Состояние потока анализа — трогается только из onFrame.
    private var sessionStartNs = 0L
    private var lastEmitNs = 0L
    @Volatile
    private var lastFrameNs = 0L
    private var frameIntervalMs = 0.0

    // Состояние мастера калибровки.
    private val calLock = Any()
    @Volatile
    private var calRunning = false
    private var calStartNs = 0L
    private var calFirstPulseNs = 0L
    private var calLastPulseNs = 0L
    private var calPulses = 0

    val analyzer = LuminanceAnalyzer(::onFrame)

    init {
        viewModelScope.launch {
            settings.collect { value ->
                settingsSnapshot = value
                _ui.update { state -> state.withSpeed(state.periodMs, value) }
            }
        }
    }

    // --- Экран измерения -------------------------------------------------

    /** Пользователь тапнул по метке. Тап мимо кадра сюда не доходит. */
    fun setRoi(roi: Roi) {
        analyzer.roi = roi
        pendingReset.set(true)
        _ui.update { it.copy(roi = roi) }
    }

    /** Сброс накопленных периодов, счётчика импульсов и графика. */
    fun resetMeasurement() {
        pendingReset.set(true)
        _ui.update {
            it.copy(
                pulseCount = 0L,
                periodsCollected = 0,
                periodMs = Double.NaN,
                rpm = 0.0,
                speedMMin = 0.0,
                cmPer20s = 0.0,
                stable = false,
                spread = Double.NaN,
                msSinceLastPulse = 0L,
                everPulsed = false,
                chart = ChartSnapshot.EMPTY,
            )
        }
    }

    /** Камера остановлена — счётчик времени «метка не видна» не должен врать. */
    fun onCameraStopped() {
        sessionStartNs = 0L
        lastFrameNs = 0L
    }

    private fun onFrame(geometry: FrameGeometry, luminance: Double, timestampNs: Long) {
        if (pendingReset.getAndSet(false)) {
            detector.reset()
            chart.clear()
            sessionStartNs = timestampNs
        }
        if (sessionStartNs == 0L) sessionStartNs = timestampNs

        if (lastFrameNs != 0L) {
            val dtMs = (timestampNs - lastFrameNs) / 1_000_000.0
            if (dtMs > 0.0 && dtMs < 1_000.0) {
                frameIntervalMs = if (frameIntervalMs <= 0.0) dtMs else frameIntervalMs + 0.1 * (dtMs - frameIntervalMs)
            }
        }
        lastFrameNs = timestampNs

        var pulsed = false
        if (luminance.isFinite()) {
            pulsed = detector.update(luminance, timestampNs)
            chart.add(luminance.toFloat(), detector.baseline.toFloat(), detector.threshold.toFloat())
            if (pulsed) onCalibrationPulse(timestampNs)
        }

        if (calRunning) updateCalibrationLive(timestampNs)

        val sinceEmitMs = (timestampNs - lastEmitNs) / 1_000_000.0
        if (!pulsed && lastEmitNs != 0L && sinceEmitMs < EMIT_INTERVAL_MS) return
        lastEmitNs = timestampNs

        emitState(geometry, timestampNs)
    }

    private fun emitState(geometry: FrameGeometry, timestampNs: Long) {
        val periodMs = detector.medianPeriodMs()
        val lastPulseNs = detector.lastPulseNs
        val referenceNs = if (lastPulseNs != 0L) lastPulseNs else sessionStartNs
        val sinceMs = ((timestampNs - referenceNs) / 1_000_000.0).toLong().coerceAtLeast(0L)
        val snapshot = chart.snapshot()

        _ui.update { state ->
            state.copy(
                geometry = geometry,
                pulseCount = detector.pulseCount,
                periodsCollected = detector.periodCount(),
                stable = detector.isStable(),
                spread = detector.spread(),
                msSinceLastPulse = sinceMs,
                everPulsed = lastPulseNs != 0L,
                fps = if (frameIntervalMs > 0.0) 1_000.0 / frameIntervalMs else 0.0,
                chart = snapshot,
            ).withSpeed(periodMs, settingsSnapshot)
        }
    }

    private fun MeasureUiState.withSpeed(periodMs: Double, config: TachoSettings): MeasureUiState {
        val rpm = SpeedMath.rpm(periodMs)
        val speed = SpeedMath.metersPerMinute(rpm, config.circumferenceMm, config.correction)
        return copy(
            periodMs = periodMs,
            rpm = rpm,
            speedMMin = speed,
            cmPer20s = SpeedMath.cmPer20s(speed),
        )
    }

    // --- Мастер калибровки ------------------------------------------------

    fun startCalibration() {
        synchronized(calLock) {
            calRunning = true
            calStartNs = 0L
            calFirstPulseNs = 0L
            calLastPulseNs = 0L
            calPulses = 0
        }
        _calibration.value = CalibrationUiState(running = true)
    }

    fun stopCalibration() {
        val revolutions: Double
        val pulses: Int
        synchronized(calLock) {
            calRunning = false
            revolutions = revolutionsAt(lastFrameNs)
            pulses = calPulses
        }
        _calibration.update {
            it.copy(
                running = false,
                revolutions = revolutions,
                pulses = pulses,
                measured = true,
                error = if (revolutions <= 0.0) "Обороты не зафиксированы — метка не детектировалась" else null,
            )
        }
    }

    fun cancelCalibration() {
        synchronized(calLock) {
            calRunning = false
            calPulses = 0
        }
        _calibration.value = CalibrationUiState()
    }

    /** @param lengthCm длина вышедшей проволоки, см. */
    fun applyCalibration(lengthCm: Double) {
        val state = _calibration.value
        val revolutions = state.revolutions
        val lengthMm = lengthCm * 10.0
        val circumference = SpeedMath.circumferenceFromCalibration(lengthMm, revolutions)
        if (!circumference.isFinite() || circumference <= 0.0) {
            _calibration.update { it.copy(error = "Нужны обороты > 0 и длина > 0") }
            return
        }
        val effectiveDiameter = SpeedMath.diameterOf(circumference)
        val geometric = settingsSnapshot.geometricDiameterMm
        val deviation = if (geometric.isFinite() && geometric > 0.0) {
            (effectiveDiameter - geometric) / geometric * 100.0
        } else {
            Double.NaN
        }
        _calibration.update {
            it.copy(
                error = null,
                result = CalibrationResult(
                    circumferenceMm = circumference,
                    effectiveDiameterMm = effectiveDiameter,
                    revolutions = revolutions,
                    lengthMm = lengthMm,
                    deviationPercent = deviation,
                ),
            )
        }
        viewModelScope.launch {
            settingsRepo.setCircumference(circumference, System.currentTimeMillis())
        }
    }

    private fun onCalibrationPulse(timestampNs: Long) {
        synchronized(calLock) {
            if (!calRunning) return
            if (calStartNs == 0L) calStartNs = timestampNs
            if (calFirstPulseNs == 0L) calFirstPulseNs = timestampNs
            calLastPulseNs = timestampNs
            calPulses++
        }
    }

    private fun updateCalibrationLive(timestampNs: Long) {
        val revolutions: Double
        val pulses: Int
        synchronized(calLock) {
            if (!calRunning) return
            if (calStartNs == 0L) calStartNs = timestampNs
            revolutions = revolutionsAt(timestampNs)
            pulses = calPulses
        }
        _calibration.update {
            if (!it.running) it else it.copy(revolutions = revolutions, pulses = pulses)
        }
    }

    /**
     * Обороты между «Старт» и [endNs]: целые обороты между первым и последним
     * импульсом плюс дробные хвосты по фазе, оценённые через медианный период.
     */
    private fun revolutionsAt(endNs: Long): Double {
        if (calPulses <= 0 || calStartNs == 0L || endNs <= 0L) return 0.0
        val periodMs = fallbackPeriodMs()
        if (!periodMs.isFinite() || periodMs <= 0.0) return (calPulses - 1).toDouble().coerceAtLeast(0.0)
        val head = ((calFirstPulseNs - calStartNs) / 1_000_000.0 / periodMs).coerceIn(0.0, 1.0)
        val tail = ((endNs - calLastPulseNs) / 1_000_000.0 / periodMs).coerceIn(0.0, 1.0)
        return (calPulses - 1) + head + tail
    }

    private fun fallbackPeriodMs(): Double {
        val median = detector.medianPeriodMs()
        if (median.isFinite() && median > 0.0) return median
        if (calPulses > 1 && calLastPulseNs > calFirstPulseNs) {
            return (calLastPulseNs - calFirstPulseNs) / 1_000_000.0 / (calPulses - 1)
        }
        return Double.NaN
    }

    // --- Настройки --------------------------------------------------------

    fun setCircumference(mm: Double) {
        viewModelScope.launch { settingsRepo.setCircumference(mm) }
    }

    fun setDiameter(mm: Double) {
        viewModelScope.launch { settingsRepo.setCircumference(SpeedMath.circumferenceOf(mm)) }
    }

    fun setGeometricDiameter(mm: Double) {
        viewModelScope.launch { settingsRepo.setGeometricDiameter(mm) }
    }

    fun setCorrection(factor: Double) {
        viewModelScope.launch { settingsRepo.setCorrection(factor) }
    }

    fun resetToGeometry() {
        viewModelScope.launch { settingsRepo.resetToGeometry() }
        _calibration.value = CalibrationUiState()
    }

    // --- Журнал -----------------------------------------------------------

    fun recordPoint(label: String) {
        val state = _ui.value
        val config = settingsSnapshot
        viewModelScope.launch {
            logRepo.add(
                LogPoint(
                    timestampMs = System.currentTimeMillis(),
                    label = label.trim(),
                    speedMMin = state.speedMMin,
                    rpm = state.rpm,
                    circumferenceMm = config.circumferenceMm,
                    correction = config.correction,
                )
            )
        }
    }

    fun removePoint(timestampMs: Long) {
        viewModelScope.launch { logRepo.remove(timestampMs) }
    }

    fun clearLog() {
        viewModelScope.launch { logRepo.clear() }
    }

    fun buildCsv(): String = LogRepository.toCsv(logPoints.value)

    private companion object {
        const val CHART_CAPACITY = 140
        const val EMIT_INTERVAL_MS = 33.0
    }
}
