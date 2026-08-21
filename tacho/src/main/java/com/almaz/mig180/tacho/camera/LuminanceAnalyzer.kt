package com.almaz.mig180.tacho.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.almaz.mig180.tacho.core.FrameGeometry
import com.almaz.mig180.tacho.core.Roi
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Считает среднюю яркость (Y-плоскость) внутри круглого ROI.
 *
 * Работает напрямую с ByteBuffer — без декодирования в Bitmap. Вызывается на
 * отдельном исполнителе, не на UI-потоке. Таймстемп берётся из
 * [ImageProxy.getImageInfo], он точнее системных часов.
 */
class LuminanceAnalyzer(
    private val onFrame: (geometry: FrameGeometry, luminance: Double, timestampNs: Long) -> Unit,
) : ImageAnalysis.Analyzer {

    @Volatile
    var roi: Roi? = null

    override fun analyze(image: ImageProxy) {
        try {
            val geometry = FrameGeometry(image.width, image.height, image.imageInfo.rotationDegrees)
            val luminance = averageLuminance(image, roi)
            onFrame(geometry, luminance, image.imageInfo.timestamp)
        } catch (t: Throwable) {
            // Кадр битый — просто пропускаем, измерение не должно падать.
        } finally {
            image.close()
        }
    }

    private fun averageLuminance(image: ImageProxy, roi: Roi?): Double {
        if (roi == null) return Double.NaN
        val plane = image.planes.getOrNull(0) ?: return Double.NaN
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val w = image.width
        val h = image.height
        if (w <= 0 || h <= 0 || rowStride <= 0 || pixelStride <= 0) return Double.NaN

        val cx = roi.x.coerceIn(0, w - 1)
        val cy = roi.y.coerceIn(0, h - 1)
        val r = roi.radius.coerceIn(1, min(w, h))
        val r2 = r * r

        var sum = 0L
        var count = 0

        val yStart = max(0, cy - r)
        val yEnd = min(h - 1, cy + r)
        for (y in yStart..yEnd) {
            val dy = y - cy
            val halfSpan = sqrt((r2 - dy * dy).toDouble()).toInt()
            val xStart = max(0, cx - halfSpan)
            val xEnd = min(w - 1, cx + halfSpan)
            if (xEnd < xStart) continue
            val rowBase = y * rowStride
            var index = rowBase + xStart * pixelStride
            var x = xStart
            while (x <= xEnd) {
                if (index < buffer.limit()) {
                    sum += (buffer.get(index).toInt() and 0xFF)
                    count++
                }
                index += pixelStride
                x++
            }
        }
        return if (count == 0) Double.NaN else sum.toDouble() / count
    }
}
