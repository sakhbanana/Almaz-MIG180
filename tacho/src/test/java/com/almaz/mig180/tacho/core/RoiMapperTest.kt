package com.almaz.mig180.tacho.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RoiMapperTest {

    private val viewWidth = 1080f
    private val viewHeight = 1920f

    @Test
    fun `tap maps back to the same view point for every rotation`() {
        for (rotation in listOf(0, 90, 180, 270)) {
            val geometry = FrameGeometry(1280, 720, rotation)
            val fit = RoiMapper.fit(viewWidth, viewHeight, geometry)
            assertNotNull("геометрия должна вписываться", fit)
            val inside = fit!!
            val tapX = inside.offsetX + inside.width * 0.3f
            val tapY = inside.offsetY + inside.height * 0.7f

            val roi = RoiMapper.viewToBuffer(tapX, tapY, viewWidth, viewHeight, geometry)
            assertNotNull("поворот $rotation: тап внутри кадра должен дать ROI", roi)

            val back = RoiMapper.bufferToView(roi!!, viewWidth, viewHeight, geometry)
            assertNotNull(back)
            assertEquals("поворот $rotation, x", tapX, back!!.first, 3f)
            assertEquals("поворот $rotation, y", tapY, back.second, 3f)
        }
    }

    @Test
    fun `tap outside the frame is ignored`() {
        // Кадр 16:9 без поворота внутри портретного вью — сверху и снизу остаются поля.
        val geometry = FrameGeometry(1280, 720, 0)
        val fit = RoiMapper.fit(viewWidth, viewHeight, geometry)!!
        assertNull(RoiMapper.viewToBuffer(viewWidth / 2f, fit.offsetY - 20f, viewWidth, viewHeight, geometry))
        assertNull(RoiMapper.viewToBuffer(viewWidth / 2f, fit.offsetY + fit.height + 20f, viewWidth, viewHeight, geometry))
        assertNull(RoiMapper.viewToBuffer(-10f, viewHeight / 2f, viewWidth, viewHeight, geometry))
    }

    @Test
    fun `unknown geometry yields no roi`() {
        assertNull(RoiMapper.viewToBuffer(100f, 100f, viewWidth, viewHeight, FrameGeometry()))
        assertNull(RoiMapper.fit(viewWidth, viewHeight, FrameGeometry(0, 0, 0)))
        assertNull(RoiMapper.fit(0f, 0f, FrameGeometry(1280, 720, 0)))
    }

    @Test
    fun `roi stays inside the buffer`() {
        val geometry = FrameGeometry(1280, 720, 0)
        val fit = RoiMapper.fit(viewWidth, viewHeight, geometry)!!
        val roi = RoiMapper.viewToBuffer(
            fit.offsetX + fit.width - 0.5f,
            fit.offsetY + fit.height - 0.5f,
            viewWidth,
            viewHeight,
            geometry,
        )!!
        assertEquals(true, roi.x in 0..1279)
        assertEquals(true, roi.y in 0..719)
    }
}
