package com.almaz.mig180.tacho.core

/** Снимок графика яркости: значения, baseline и порог, от старых к новым. */
class ChartSnapshot(
    val values: FloatArray,
    val baselines: FloatArray,
    val thresholds: FloatArray,
) {
    val size: Int get() = values.size

    companion object {
        val EMPTY = ChartSnapshot(FloatArray(0), FloatArray(0), FloatArray(0))
    }
}

/** Кольцевой буфер последних отсчётов яркости для отладочного графика. */
class ChartBuffer(private val capacity: Int = 140) {
    private val values = FloatArray(capacity)
    private val baselines = FloatArray(capacity)
    private val thresholds = FloatArray(capacity)
    private var head = 0
    private var size = 0

    fun add(value: Float, baseline: Float, threshold: Float) {
        values[head] = value
        baselines[head] = baseline
        thresholds[head] = threshold
        head = (head + 1) % capacity
        if (size < capacity) size++
    }

    fun clear() {
        head = 0
        size = 0
    }

    fun snapshot(): ChartSnapshot {
        if (size == 0) return ChartSnapshot.EMPTY
        val v = FloatArray(size)
        val b = FloatArray(size)
        val t = FloatArray(size)
        val start = (head - size + capacity) % capacity
        for (i in 0 until size) {
            val idx = (start + i) % capacity
            v[i] = values[idx]
            b[i] = baselines[idx]
            t[i] = thresholds[idx]
        }
        return ChartSnapshot(v, b, t)
    }
}
