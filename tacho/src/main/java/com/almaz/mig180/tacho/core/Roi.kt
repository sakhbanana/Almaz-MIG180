package com.almaz.mig180.tacho.core

/** Область интереса в координатах буфера кадра (не повёрнутого). */
data class Roi(val x: Int, val y: Int, val radius: Int)

/** Геометрия кадра анализа: размер буфера и поворот до «вертикального» вида. */
data class FrameGeometry(
    val width: Int = 0,
    val height: Int = 0,
    val rotationDegrees: Int = 0,
) {
    val valid: Boolean get() = width > 0 && height > 0
}

/** Прямоугольник кадра внутри вью при FIT_CENTER. */
data class FitInfo(
    val offsetX: Float,
    val offsetY: Float,
    val width: Float,
    val height: Float,
    val scale: Float,
)

/**
 * Перевод координат «превью <-> буфер кадра».
 *
 * PreviewView работает в режиме FIT_CENTER, поэтому кадр вписан в вью целиком.
 * Буфер анализа приходит неповёрнутым, поворот до вертикального вида задаёт
 * [FrameGeometry.rotationDegrees].
 */
object RoiMapper {

    const val DEFAULT_RADIUS_PX = 16

    /** Размер кадра после поворота (то, что видит пользователь). */
    fun uprightSize(geometry: FrameGeometry): Pair<Int, Int> =
        if (geometry.rotationDegrees % 180 == 0) {
            geometry.width to geometry.height
        } else {
            geometry.height to geometry.width
        }

    fun fit(viewWidth: Float, viewHeight: Float, geometry: FrameGeometry): FitInfo? {
        if (!geometry.valid || viewWidth <= 0f || viewHeight <= 0f) return null
        val (upW, upH) = uprightSize(geometry)
        if (upW <= 0 || upH <= 0) return null
        val scale = minOf(viewWidth / upW, viewHeight / upH)
        val w = upW * scale
        val h = upH * scale
        return FitInfo((viewWidth - w) / 2f, (viewHeight - h) / 2f, w, h, scale)
    }

    /**
     * Тап по превью -> точка в буфере кадра.
     * @return null, если тап пришёлся вне изображения (поля letterbox).
     */
    fun viewToBuffer(
        tapX: Float,
        tapY: Float,
        viewWidth: Float,
        viewHeight: Float,
        geometry: FrameGeometry,
        radius: Int = DEFAULT_RADIUS_PX,
    ): Roi? {
        val fit = fit(viewWidth, viewHeight, geometry) ?: return null
        val u = (tapX - fit.offsetX) / fit.width
        val v = (tapY - fit.offsetY) / fit.height
        if (u < 0f || u > 1f || v < 0f || v > 1f) return null

        val w = geometry.width
        val h = geometry.height
        val x: Float
        val y: Float
        when (normalizedRotation(geometry.rotationDegrees)) {
            90 -> { x = v * w; y = (1f - u) * h }
            180 -> { x = (1f - u) * w; y = (1f - v) * h }
            270 -> { x = (1f - v) * w; y = u * h }
            else -> { x = u * w; y = v * h }
        }
        return Roi(
            x = x.toInt().coerceIn(0, w - 1),
            y = y.toInt().coerceIn(0, h - 1),
            radius = radius,
        )
    }

    /**
     * Точка буфера -> координаты во вью (центр и радиус в px вью).
     * @return null, если геометрия ещё неизвестна.
     */
    fun bufferToView(
        roi: Roi,
        viewWidth: Float,
        viewHeight: Float,
        geometry: FrameGeometry,
    ): Triple<Float, Float, Float>? {
        val fit = fit(viewWidth, viewHeight, geometry) ?: return null
        val w = geometry.width.toFloat()
        val h = geometry.height.toFloat()
        if (w <= 0f || h <= 0f) return null
        val u: Float
        val v: Float
        when (normalizedRotation(geometry.rotationDegrees)) {
            90 -> { u = 1f - roi.y / h; v = roi.x / w }
            180 -> { u = 1f - roi.x / w; v = 1f - roi.y / h }
            270 -> { u = roi.y / h; v = 1f - roi.x / w }
            else -> { u = roi.x / w; v = roi.y / h }
        }
        return Triple(
            fit.offsetX + u * fit.width,
            fit.offsetY + v * fit.height,
            roi.radius * fit.scale,
        )
    }

    private fun normalizedRotation(degrees: Int): Int {
        val r = ((degrees % 360) + 360) % 360
        return when {
            r < 45 || r >= 315 -> 0
            r < 135 -> 90
            r < 225 -> 180
            else -> 270
        }
    }
}
