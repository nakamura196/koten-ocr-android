package com.nakamura196.kotenocr.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Resizes images for the models the way Pillow's `Image.resize` does (bicubic, widened when shrinking),
 * instead of the 2x2 bilinear of `Canvas.drawBitmap(..., FILTER_BITMAP_FLAG)`.
 *
 * Android's bitmap filter looks at only 2x2 source pixels per output pixel, so when a page is shrunk
 * 4x for the detector most source pixels are skipped and thin brush strokes break up. The NDL reference
 * (ndlkotenocr-lite, which the models were trained with) uses Pillow, and iOS uses CoreGraphics `.high`.
 * Measured on 27 pages of the NDL みんなで翻刻 dataset: docs/notes/box-touch-and-ios-parity.md (縮小の補間).
 *
 * Port of Pillow's `precompute_coeffs` / `ImagingResample` (horizontal pass, then vertical, each rounded
 * to 8 bits). Works on rows so the source never has to be copied in one piece.
 */
object Resample {

    /** Taps for one axis: output i reads src[min[i] + k] with weight[i * size + k], k < count[i]. */
    internal class Taps(val size: Int, val min: IntArray, val count: IntArray, val weight: FloatArray)

    private const val A = -0.5 // Pillow's bicubic

    private fun cubic(t: Double): Double {
        val x = abs(t)
        return when {
            x < 1.0 -> ((A + 2.0) * x - (A + 3.0)) * x * x + 1.0
            x < 2.0 -> (((x - 5.0) * x + 8.0) * x - 4.0) * A
            else -> 0.0
        }
    }

    internal fun taps(src: Int, dst: Int): Taps {
        require(src > 0 && dst > 0)
        val scale = src.toDouble() / dst
        val filterScale = max(scale, 1.0)
        val support = 2.0 * filterScale
        val size = ceil(support).toInt() * 2 + 1
        val mins = IntArray(dst)
        val counts = IntArray(dst)
        val weight = FloatArray(dst * size)
        val k = DoubleArray(size)
        for (i in 0 until dst) {
            val center = (i + 0.5) * scale
            val lo = max((center - support + 0.5).toInt(), 0)
            val n = min((center + support + 0.5).toInt(), src) - lo
            var sum = 0.0
            for (x in 0 until n) {
                k[x] = cubic((x + lo - center + 0.5) / filterScale); sum += k[x]
            }
            mins[i] = lo
            counts[i] = n
            for (x in 0 until n) weight[i * size + x] = if (sum != 0.0) (k[x] / sum).toFloat() else 0f
        }
        return Taps(size, mins, counts, weight)
    }

    private fun clip8(v: Float): Int {
        val r = (v + 0.5f).toInt()
        return if (r < 0) 0 else if (r > 255) 255 else r
    }

    /**
     * Resizes a [srcW]x[srcH] image to [dstW]x[dstH] and returns opaque ARGB pixels.
     * Only rows `0 until rowsToRead` are read through [readRow] (which fills `srcW` ARGB pixels);
     * the rest are treated as black, which is how the detectors pad a page to a square.
     */
    fun resize(
        srcW: Int,
        srcH: Int,
        dstW: Int,
        dstH: Int,
        rowsToRead: Int = srcH,
        readRow: (y: Int, out: IntArray) -> Unit
    ): IntArray {
        val tx = taps(srcW, dstW)
        val ty = taps(srcH, dstH)
        // Only the source rows some output row reads.
        val needed = BooleanArray(srcH)
        for (o in 0 until dstH) for (j in 0 until ty.count[o]) needed[ty.min[o] + j] = true

        // Horizontal pass into dstW x srcH (8-bit RGB packed in an Int, like Pillow's intermediate image).
        val mid = IntArray(dstW * srcH)
        val row = IntArray(srcW)
        val black = 0xFF000000.toInt()
        for (y in 0 until srcH) {
            if (!needed[y]) continue
            if (y >= rowsToRead) {
                java.util.Arrays.fill(mid, y * dstW, (y + 1) * dstW, black); continue
            }
            readRow(y, row)
            val base = y * dstW
            for (i in 0 until dstW) {
                var r = 0f; var g = 0f; var b = 0f
                val m = tx.min[i]
                val wo = i * tx.size
                for (k in 0 until tx.count[i]) {
                    val p = row[m + k]
                    val w = tx.weight[wo + k]
                    r += ((p shr 16) and 0xFF) * w
                    g += ((p shr 8) and 0xFF) * w
                    b += (p and 0xFF) * w
                }
                mid[base + i] = black or (clip8(r) shl 16) or (clip8(g) shl 8) or clip8(b)
            }
        }

        // Vertical pass.
        val out = IntArray(dstW * dstH)
        val r = FloatArray(dstW); val g = FloatArray(dstW); val b = FloatArray(dstW)
        for (o in 0 until dstH) {
            java.util.Arrays.fill(r, 0f); java.util.Arrays.fill(g, 0f); java.util.Arrays.fill(b, 0f)
            val m = ty.min[o]
            val wo = o * ty.size
            for (k in 0 until ty.count[o]) {
                val w = ty.weight[wo + k]
                val base = (m + k) * dstW
                for (i in 0 until dstW) {
                    val p = mid[base + i]
                    r[i] += ((p shr 16) and 0xFF) * w
                    g[i] += ((p shr 8) and 0xFF) * w
                    b[i] += (p and 0xFF) * w
                }
            }
            val ob = o * dstW
            for (i in 0 until dstW) out[ob + i] = black or (clip8(r[i]) shl 16) or (clip8(g[i]) shl 8) or clip8(b[i])
        }
        return out
    }

    /** Resizes a whole bitmap. */
    fun bitmap(src: Bitmap, dstW: Int, dstH: Int): IntArray =
        resize(src.width, src.height, dstW, dstH) { y, out -> src.getPixels(out, 0, src.width, 0, y, src.width, 1) }

    /**
     * The detectors' input: the page padded with black to a square at the top-left, then resized
     * to [size]x[size]. Same geometry as drawing into a `maxDim` square first, without allocating it.
     *
     * A camera page is ~4x the detector input. While the page is 3x or more the input, it is first
     * halved by the system (at exactly 0.5 the bitmap filter averages each 2x2 block, so this is a
     * box filter, not the skipping bilinear), which cuts the Kotlin work ~4x. Same idea as Pillow's
     * `reducing_gap=1.5`; measured to read the same as plain Pillow (docs/notes, 縮小の補間).
     */
    fun paddedSquare(src: Bitmap, size: Int): IntArray {
        var page = src
        while (max(page.width, page.height) >= size * 3) {
            val half = halve(page)
            if (page !== src) page.recycle()
            page = half
        }
        val w = page.width
        val h = page.height
        val m = max(w, h)
        try {
            return resize(m, m, size, size, rowsToRead = h) { y, out ->
                page.getPixels(out, 0, w, 0, y, w, 1)
                if (m > w) java.util.Arrays.fill(out, w, m, 0xFF000000.toInt())
            }
        } finally {
            if (page !== src) page.recycle()
        }
    }

    /** Halves [src] (rounding up; an odd last row/column is averaged with the black padding). */
    internal fun halve(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap((src.width + 1) / 2, (src.height + 1) / 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)
        canvas.scale(0.5f, 0.5f)
        canvas.drawBitmap(src, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }
}
