package com.almaz.mig180.tacho.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import com.almaz.mig180.tacho.core.ChartSnapshot
import com.almaz.mig180.tacho.ui.theme.Amber
import com.almaz.mig180.tacho.ui.theme.Danger
import com.almaz.mig180.tacho.ui.theme.TextSecondary
import kotlin.math.max
import kotlin.math.min

/**
 * График яркости ROI за последние ~140 кадров с линиями baseline и порога —
 * нужен, чтобы наводить камеру и видеть, «пробивает» ли метка порог.
 */
@Composable
fun BrightnessChart(snapshot: ChartSnapshot, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val n = snapshot.size
        if (n < 2 || size.width <= 0f || size.height <= 0f) return@Canvas

        var minV = Float.MAX_VALUE
        var maxV = -Float.MAX_VALUE
        for (i in 0 until n) {
            val v = snapshot.values[i]
            val b = snapshot.baselines[i]
            val t = snapshot.thresholds[i]
            if (!v.isFinite() || !b.isFinite() || !t.isFinite()) continue
            minV = min(minV, min(v, b - t))
            maxV = max(maxV, max(v, b + t))
        }
        if (minV > maxV) return@Canvas

        var lo = minV
        var hi = maxV
        if (hi - lo < 1f) {
            val center = (hi + lo) / 2f
            lo = center - 0.5f
            hi = center + 0.5f
        }
        val pad = (hi - lo) * 0.08f
        lo -= pad
        hi += pad
        val range = hi - lo
        if (range <= 0f) return@Canvas

        val stepX = size.width / (n - 1)
        fun yOf(value: Float): Float = size.height - (value - lo) / range * size.height

        val upper = Path()
        val lower = Path()
        val baseline = Path()
        val values = Path()
        for (i in 0 until n) {
            val x = i * stepX
            val b = snapshot.baselines[i]
            val t = snapshot.thresholds[i]
            val v = snapshot.values[i]
            if (i == 0) {
                upper.moveTo(x, yOf(b + t))
                lower.moveTo(x, yOf(b - t))
                baseline.moveTo(x, yOf(b))
                values.moveTo(x, yOf(v))
            } else {
                upper.lineTo(x, yOf(b + t))
                lower.lineTo(x, yOf(b - t))
                baseline.lineTo(x, yOf(b))
                values.lineTo(x, yOf(v))
            }
        }

        val dashes = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
        drawPath(baseline, TextSecondary.copy(alpha = 0.5f), style = Stroke(width = 1.5f))
        drawPath(upper, Danger.copy(alpha = 0.8f), style = Stroke(width = 2f, pathEffect = dashes))
        drawPath(lower, Danger.copy(alpha = 0.8f), style = Stroke(width = 2f, pathEffect = dashes))
        drawPath(values, Amber, style = Stroke(width = 2.5f))
        drawRect(color = Color.White.copy(alpha = 0.06f), style = Stroke(width = 1f))
    }
}
