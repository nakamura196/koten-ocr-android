package com.nakamura196.kotenocr.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The models' input must be resized like Pillow (what the NDL models were trained with), not the 2x2 bilinear of
 * `Canvas.drawBitmap(FILTER_BITMAP_FLAG)`. See docs/notes/box-touch-and-ios-parity.md (縮小の補間).
 */
class ResampleTest {

    private fun gray(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    private fun lum(p: Int) = p and 0xFF

    private fun run(src: IntArray, sw: Int, sh: Int, dw: Int, dh: Int, rows: Int = sh) =
        Resample.resize(sw, sh, dw, dh, rows) { y, out -> System.arraycopy(src, y * sw, out, 0, sw) }

    private fun pattern(w: Int, h: Int) = IntArray(w * h) { i ->
        val x = i % w; val y = i / w
        gray((x * 37 + y * 91 + (x * y) % 13) % 256)
    }

    private fun assertMatchesPillow(expected: IntArray, actual: IntArray) {
        assertEquals(expected.size, actual.size)
        for (i in expected.indices) {
            assertTrue("pixel $i: Pillow ${expected[i]}, got ${lum(actual[i])}", kotlin.math.abs(expected[i] - lum(actual[i])) <= 1)
        }
    }

    @Test
    fun thinStrokesShrunkFiveTimesStayGrayInsteadOfVanishing() {
        // 1 px black line every 5 px, shrunk 5x. The old Canvas bilinear gave all 0 on the emulator
        // (it hit the line every time); Pillow gives 200..205.
        val sw = 500; val sh = 10
        val src = IntArray(sw * sh) { i -> if ((i % sw) % 5 == 2) gray(0) else gray(255) }
        val out = run(src, sw, sh, 100, 2)
        for (p in out) assertTrue(lum(p).toString(), lum(p) in 199..206)
    }

    @Test
    fun shrinkingMatchesPillow() {
        // PIL: Image.new('L', (23, 17)).putdata(pattern).resize((7, 5)), ndlkotenocr-lite's default.
        val expected = intArrayOf(
            105, 136, 115, 138, 117, 137, 129, 129, 121, 132, 121, 134, 128, 137, 118, 128, 120, 131,
            121, 134, 127, 129, 116, 127, 117, 131, 123, 130, 132, 117, 132, 132, 118, 127, 124
        )
        assertMatchesPillow(expected, run(pattern(23, 17), 23, 17, 7, 5))
    }

    @Test
    fun enlargingOneAxisWhileShrinkingTheOtherMatchesPillow() {
        // A short line crop: wider in the model than on the page, but taller on the page. 9x40 -> 30x8.
        val expected = intArrayOf(
            87, 90, 100, 116, 131, 132, 118, 101, 95, 105, 121, 134, 138, 138, 135, 133, 126, 121, 121, 131, 146, 153, 143, 121, 101, 100, 114, 131, 142, 145,
            126, 125, 124, 122, 119, 119, 118, 119, 121, 128, 136, 140, 133, 118, 106, 108, 123, 141, 147, 135, 115, 103, 107, 122, 135, 139, 133, 125, 121, 121,
            158, 156, 146, 128, 112, 110, 123, 138, 142, 129, 109, 97, 105, 121, 137, 138, 127, 112, 104, 108, 115, 122, 126, 128, 129, 127, 120, 113, 109, 108,
            109, 113, 119, 130, 138, 135, 120, 103, 97, 105, 118, 128, 128, 122, 116, 115, 112, 112, 117, 132, 150, 160, 150, 125, 104, 102, 117, 134, 145, 149,
            126, 124, 119, 111, 104, 104, 106, 112, 120, 134, 149, 158, 149, 127, 108, 106, 121, 140, 149, 146, 136, 130, 130, 134, 138, 133, 121, 109, 103, 105,
            150, 149, 139, 123, 109, 109, 125, 144, 152, 143, 127, 116, 118, 126, 135, 139, 141, 140, 139, 132, 124, 121, 128, 143, 154, 153, 137, 120, 110, 107,
            96, 100, 109, 124, 138, 143, 138, 129, 125, 124, 125, 127, 133, 142, 147, 146, 133, 118, 114, 126, 144, 154, 148, 129, 113, 112, 124, 138, 147, 150,
            129, 129, 132, 135, 138, 136, 125, 115, 113, 125, 142, 152, 143, 122, 103, 101, 114, 131, 140, 135, 126, 119, 122, 130, 137, 140, 140, 138, 137, 140
        )
        assertMatchesPillow(expected, run(pattern(9, 40), 9, 40, 30, 8))
    }

    @Test
    fun fractionalShrinkKeepsAFlatImageFlat() {
        val sw = 1003; val sh = 997
        val src = IntArray(sw * sh) { gray(137) }
        val out = run(src, sw, sh, 256, 241)
        assertTrue(out.all { lum(it) == 137 })
    }

    @Test
    fun rowsNotReadArePaddingBlack() {
        // The detectors pad the page to a square: skipping the rows below the page must give the
        // same result as reading explicit black rows.
        val s = 40
        val page = pattern(s, s)
        val padded = IntArray(s * s) { i -> if (i / s < 23) page[i] else gray(0) }
        val skipped = run(page, s, s, 10, 10, rows = 23)
        val explicit = run(padded, s, s, 10, 10)
        assertTrue(skipped.contentEquals(explicit))
        assertTrue(skipped.all { (it ushr 24) == 0xFF })
    }

    @Test
    fun tapWeightsSumToOne() {
        for ((s, d) in listOf(4096 to 1024, 3801 to 1024, 37 to 384, 1000 to 999, 5 to 5)) {
            val t = Resample.taps(s, d)
            for (i in 0 until d) {
                var w = 0f
                for (k in 0 until t.count[i]) w += t.weight[i * t.size + k]
                assertEquals("$s->$d at $i", 1f, w, 1e-4f)
            }
        }
    }
}
