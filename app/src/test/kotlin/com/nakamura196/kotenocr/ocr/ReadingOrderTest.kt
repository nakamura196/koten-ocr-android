package com.nakamura196.kotenocr.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingOrderTest {

    private fun col(i: Int, x0: Int, y0: Int, y1: Int, w: Int = 40) =
        Detection(i, intArrayOf(x0, y0, x0 + w, y1), 0.9f, 1, "line_main", "c$i")

    /** 16 tall columns on a wide scroll, read right to left; c6 starts higher than its neighbours. */
    @Test
    fun wideScroll_oneColumnStartingHigher_staysInRightToLeftOrder() {
        val imageW = 1600
        val cols = (1..16).map { k ->
            val x0 = imageW - k * 95
            val y0 = if (k == 6) 10 else 80
            col(k, x0, y0, 560)
        }
        val ordered = ReadingOrderProcessor().process(cols.shuffled(java.util.Random(1)), imageW, 600)
        assertEquals((1..16).map { "c$it" }, ordered.map { it.text })
    }

    /** Same, with ragged starts and ends on every column (as real detections are). */
    @Test
    fun wideScroll_raggedColumns_stayInRightToLeftOrder() {
        val imageW = 1600
        val tops = intArrayOf(80, 75, 90, 82, 70, 5, 85, 78, 88, 72, 80, 95, 76, 84, 79, 81)
        val bottoms = intArrayOf(560, 540, 565, 530, 555, 570, 545, 560, 520, 558, 562, 548, 556, 539, 561, 550)
        val cols = (1..16).map { k -> col(k, imageW - k * 95 + (k % 3) * 3, tops[k - 1], bottoms[k - 1], 36 + k % 4 * 3) }
        val ordered = ReadingOrderProcessor().process(cols.reversed(), imageW, 600)
        assertEquals((1..16).map { "c$it" }, ordered.map { it.text })
    }

    @Test
    fun horizontalRows_topToBottom() {
        val rows = (1..5).map { k -> Detection(k, intArrayOf(20, k * 50, 400, k * 50 + 30), 0.9f, 1, "line_main", "r$k") }
        val ordered = ReadingOrderProcessor().process(rows.reversed(), 500, 400)
        assertEquals((1..5).map { "r$it" }, ordered.map { it.text })
    }
}
