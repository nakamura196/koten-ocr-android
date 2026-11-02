package com.nakamura196.kotenocr.ocr

import androidx.compose.ui.geometry.Offset
import com.nakamura196.kotenocr.util.PerspectiveCrop
import com.nakamura196.kotenocr.util.Quad
import com.nakamura196.kotenocr.util.TiltMonitor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KotenOCRV2Test {

    @Test
    fun clampedBox_truncatesAndClampsProperly() {
        val rounded = Detection.clampedBox(
            x1 = 10.6f, y1 = 20.4f, x2 = 100.7f, y2 = 200.2f,
            width = 500, height = 500,
            truncateTowardZero = false
        )
        assertNotNull(rounded)
        assertArrayEquals(intArrayOf(11, 20, 101, 200), rounded)

        val truncated = Detection.clampedBox(
            x1 = 10.9f, y1 = 20.9f, x2 = 100.9f, y2 = 200.9f,
            width = 500, height = 500,
            truncateTowardZero = true
        )
        assertNotNull(truncated)
        assertArrayEquals(intArrayOf(10, 20, 100, 200), truncated)

        // Zero-area or inverted box returns null
        assertNull(
            Detection.clampedBox(
                x1 = 100f, y1 = 20f, x2 = 90f, y2 = 50f,
                width = 500, height = 500
            )
        )
    }

    @Test
    fun confidenceFilter_filtersByThresholdAndPreservesReaderConfirmed() {
        val d1 = Detection(0, intArrayOf(0, 0, 10, 50), 0.28f, 1, "line_main", "低確信")
        val d2 = Detection(1, intArrayOf(20, 0, 30, 50), 0.65f, 1, "line_main", "中確信")
        val d3 = Detection(2, intArrayOf(40, 0, 50, 50), 0.26f, 1, "line_main", "編集済").also {
            it.markReaderConfirmed()
        }
        val list = listOf(d1, d2, d3)

        val vis30 = ConfidenceFilter.visible(list, 0.30f)
        assertEquals(2, vis30.size)
        assertEquals(1, vis30[0].originalIndex)
        assertEquals(2, vis30[1].originalIndex)
        assertEquals("中確信\n編集済", ConfidenceFilter.text(list, 0.30f))
        assertEquals("30%", ConfidenceFilter.percentText(0.30f))
    }

    @Test
    fun lineTilt_placeholderAndDuplicateHelpers() {
        assertTrue(LineTilt.isPlaceholderOnly("□□〓"))
        assertFalse(LineTilt.isPlaceholderOnly("□あ□"))
        assertEquals(2, LineTilt.placeholderCount("□あ□"))

        val outer = Detection(0, intArrayOf(100, 50, 140, 450), 0.9f, 1, "line_main", "いろはにほへとちりぬるを")
        val innerDup = Detection(1, intArrayOf(102, 120, 138, 320), 0.8f, 1, "line_main", "はにほへとちり")
        val separate = Detection(2, intArrayOf(200, 50, 240, 450), 0.9f, 1, "line_main", "わかよたれそつねならむ")

        val deduped = LineTilt.dropSameLineDuplicates(listOf(outer, innerDup, separate))
        assertEquals(2, deduped.size)
        assertEquals("いろはにほへとちりぬるを", deduped[0].text)
        assertEquals("わかよたれそつねならむ", deduped[1].text)
    }

    @Test
    fun ocrEngine_shortLineFollowsPageVerticalMajority() {
        val detections = listOf(
            Detection(0, intArrayOf(300, 20, 330, 400), 0.9f, 1, "line_main"),
            Detection(1, intArrayOf(200, 20, 230, 400), 0.9f, 1, "line_main"),
            Detection(2, intArrayOf(100, 20, 132, 48), 0.9f, 1, "line_main") // near-square 32x28 box
        )
        assertTrue(OCREngine.isVerticalPage(detections))
        // Near-square box (32x28, aspect < 1.5) should follow vertical page direction
        assertTrue(OCREngine.readsVertically(intArrayOf(100, 20, 132, 48), pageVertical = true))
        assertFalse(OCREngine.readsVertically(intArrayOf(100, 20, 132, 48), pageVertical = false))
    }

    @Test
    fun readingOrder_ordersFragmentedVerticalColumnTopToBottom() {
        val processor = ReadingOrderProcessor()
        // Column 1 (right, x ~ 300) split into upper and lower fragments where lower is 2px further right
        val upperRight = Detection(0, intArrayOf(300, 20, 330, 180), 0.9f, 1, "line_main", "上段")
        val lowerRight = Detection(1, intArrayOf(302, 200, 332, 380), 0.9f, 1, "line_main", "下段")
        val leftCol = Detection(2, intArrayOf(100, 20, 130, 380), 0.9f, 1, "line_main", "左行")

        val ordered = processor.process(listOf(lowerRight, leftCol, upperRight), 500, 500)
        assertEquals(listOf("上段", "下段", "左行"), ordered.map { it.text })
    }

    @Test
    fun tiltMonitor_classifiesFlatUprightAndTilted() {
        val flat = TiltMonitor.classify(0.0, 0.0, -1.0)
        assertTrue(flat is TiltMonitor.TiltState.Flat)
        assertFalse(flat.showsHint)

        val upright = TiltMonitor.classify(0.0, -1.0, 0.0)
        assertTrue(upright is TiltMonitor.TiltState.Upright)
        assertFalse(upright.showsHint)

        val tilted45 = TiltMonitor.classify(0.0, -0.707, -0.707)
        assertEquals(TiltMonitor.TiltState.Tilted, tilted45)
    }

    @Test
    fun quad_convexAndUsableDetectionChecks() {
        assertTrue(Quad.UNIT.isConvex)
        assertTrue(PerspectiveCrop.isNearlyFullFrame(Quad.UNIT))
        assertFalse(PerspectiveCrop.isUsableDetection(Quad.UNIT))

        val insetQuad = Quad(
            topLeft = Offset(0.1f, 0.1f),
            topRight = Offset(0.9f, 0.12f),
            bottomRight = Offset(0.88f, 0.9f),
            bottomLeft = Offset(0.12f, 0.88f)
        )
        assertTrue(insetQuad.isConvex)
        assertTrue(PerspectiveCrop.isUsableDetection(insetQuad))
    }
}
