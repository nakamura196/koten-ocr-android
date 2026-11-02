package com.nakamura196.kotenocr.ocr

import android.graphics.Bitmap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * Per-line tilt estimation and oriented cropping for vertical text columns,
 * plus post-recognition line filtering (placeholder-only removal and same-column duplicate suppression).
 */
object LineTilt {

    const val MAX_ANGLE = 6.0
    const val ANGLE_STEP = 0.25
    const val MIN_ANGLE = 0.5
    const val MIN_GAIN = 1.03
    const val MIN_ASPECT = 3.0
    const val MIN_HEIGHT = 48
    private const val WORK_ROWS = 256

    data class Plan(
        val angle: Double,
        val gain: Double,
        val centerX: Double,
        val centerY: Double,
        val width: Double,
        val height: Double
    )

    data class CropResult(
        val bitmap: Bitmap,
        val plan: Plan?
    )

    /**
     * Crops [box] (`[x1, y1, x2, y2]`) from [page]. When [enabled] is true and the box is a
     * vertical column with a clear lean (`±6°`), returns a tight rotated crop; otherwise returns
     * the standard axis-aligned crop.
     */
    fun crop(page: Bitmap, box: IntArray, enabled: Boolean = true): CropResult? {
        if (box.size < 4) return null
        val pw = page.width
        val ph = page.height
        val x1 = max(0, min(pw, box[0]))
        val y1 = max(0, min(ph, box[1]))
        val x2 = max(x1, min(pw, box[2]))
        val y2 = max(y1, min(ph, box[3]))
        val bw = x2 - x1
        val bh = y2 - y1
        if (bw <= 0 || bh <= 0) return null

        val plain = Bitmap.createBitmap(page, x1, y1, bw, bh)
        if (!enabled) return CropResult(plain, null)

        val p = plan(plain, originX = x1.toDouble(), originY = y1.toDouble())
            ?: return CropResult(plain, null)
        val oriented = orientedCrop(plain, originX = x1.toDouble(), originY = y1.toDouble(), plan = p)
            ?: return CropResult(plain, null)
        return CropResult(oriented, p)
    }

    /**
     * Estimates the tilt plan of an axis-aligned box crop [boxCrop] whose top-left in page space is
     * `([originX], [originY])`.
     */
    fun plan(boxCrop: Bitmap, originX: Double = 0.0, originY: Double = 0.0): Plan? {
        val w = boxCrop.width
        val h = boxCrop.height
        if (h.toDouble() < MIN_ASPECT * w.toDouble() || h < MIN_HEIGHT || w < 6) return null

        val pixels = IntArray(w * h)
        boxCrop.getPixels(pixels, 0, w, 0, 0, w, h)

        val s = min(1.0, WORK_ROWS.toDouble() / h.toDouble())
        val rw = max(4, (w * s).roundToInt())
        val rh = max(4, (h * s).roundToInt())
        val grey = FloatArray(rw * rh)

        for (ty in 0 until rh) {
            val y0 = (ty * h) / rh
            val y1 = max(y0 + 1, ((ty + 1) * h) / rh)
            for (tx in 0 until rw) {
                val x0 = (tx * w) / rw
                val x1 = max(x0 + 1, ((tx + 1) * w) / rw)
                var sum = 0f
                var cnt = 0
                for (y in y0 until y1) {
                    val row = y * w
                    for (x in x0 until x1) {
                        val c = pixels[row + x]
                        val r = (c shr 16) and 0xFF
                        val g = (c shr 8) and 0xFF
                        val b = c and 0xFF
                        sum += 0.299f * r + 0.587f * g + 0.114f * b
                        cnt++
                    }
                }
                grey[ty * rw + tx] = sum / cnt.toFloat()
            }
        }

        val hist256 = IntArray(256)
        for (v in grey) {
            val idx = v.toInt().coerceIn(0, 255)
            hist256[idx]++
        }
        var acc = 0
        var paper = 220f
        for (i in 0..255) {
            acc += hist256[i]
            if (acc * 4 >= grey.size * 3) {
                paper = i.toFloat()
                break
            }
        }
        val threshold = paper * 0.78f

        var xs = DoubleArray(rw * rh / 2 + 16)
        var ys = DoubleArray(xs.size)
        var count = 0
        val cx = rw * 0.5
        val cy = rh * 0.5
        for (y in 0 until rh) {
            for (x in 0 until rw) {
                if (grey[y * rw + x] < threshold) {
                    if (count == xs.size) {
                        xs = xs.copyOf(xs.size * 2)
                        ys = ys.copyOf(ys.size * 2)
                    }
                    xs[count] = x.toDouble() - cx
                    ys[count] = y.toDouble() - cy
                    count++
                }
            }
        }
        if (count < 30) return null

        val nb = rw + 2 * (rh.toDouble() * tan(6.5 * PI / 180.0)).toInt() + 4
        val off = nb * 0.5
        val steps = (MAX_ANGLE / ANGLE_STEP).roundToInt()

        var bestAngle = 0.0
        var bestScore = 0.0
        var bestHist = DoubleArray(nb)
        var score0 = 0.0

        for (k in -steps..steps) {
            val a = k.toDouble() * ANGLE_STEP
            val t = tan(a * PI / 180.0)
            val bins = DoubleArray(nb)
            for (i in 0 until count) {
                val u = floor(xs[i] - ys[i] * t + off).toInt()
                if (u in 0 until nb) {
                    bins[u] += 1.0
                }
            }
            var sc = 0.0
            for (b in bins) sc += b * b
            if (k == 0) score0 = sc
            if (sc > bestScore) {
                bestScore = sc
                bestAngle = a
                bestHist = bins
            }
        }

        if (score0 <= 0.0) return null
        val gain = bestScore / score0
        if (abs(bestAngle) < MIN_ANGLE || gain < MIN_GAIN) return null

        val p = smooth3(bestHist)
        val c = off.roundToInt()
        val win = max(2, (rw.toDouble() * 0.3).toInt())
        val lo = max(0, c - win)
        val hi = min(p.lastIndex, c + win)
        if (lo >= hi) return null
        var pk = c.coerceIn(lo, hi)
        for (i in lo..hi) {
            if (p[i] > p[pk]) pk = i
        }
        if (p[pk] <= 0.0) return null
        val thr = p[pk] * 0.12
        var l = pk
        var r = pk
        while (l > 0 && p[l - 1] > thr) l--
        while (r < p.lastIndex && p[r + 1] > thr) r++

        val centreOffset = (((l + r + 1).toDouble() * 0.5) - off) / s
        var width = (r - l + 1).toDouble() / s
        val t = bestAngle * PI / 180.0
        val expected = w.toDouble() - h.toDouble() * abs(tan(t))
        width = min(max(width, 0.6 * expected) * 1.12 + 4.0, w.toDouble()) * cos(t)

        val outCx = originX + w.toDouble() * 0.5 + centreOffset
        val outCy = originY + h.toDouble() * 0.5
        val outHeight = h.toDouble() / cos(t)

        return Plan(
            angle = bestAngle,
            gain = gain,
            centerX = outCx,
            centerY = outCy,
            width = max(4.0, width),
            height = max(4.0, outHeight)
        )
    }

    /**
     * Resamples [src] (whose top-left is at `([originX], [originY])` in page space) along the
     * rotated rectangle described by [plan] using bilinear interpolation.
     */
    fun orientedCrop(src: Bitmap, originX: Double = 0.0, originY: Double = 0.0, plan: Plan): Bitmap? {
        val sw = src.width
        val sh = src.height
        if (sw <= 1 || sh <= 1) return null

        val srcPixels = IntArray(sw * sh)
        src.getPixels(srcPixels, 0, sw, 0, 0, sw, sh)

        val outW = max(1, plan.width.roundToInt())
        val outH = max(1, plan.height.roundToInt())
        val outPixels = IntArray(outW * outH)

        val t = plan.angle * PI / 180.0
        val c = cos(t)
        val s = sin(t)
        val cx = plan.centerX - originX
        val cy = plan.centerY - originY
        val bx = cx - c * outW.toDouble() * 0.5 - s * outH.toDouble() * 0.5
        val by = cy + s * outW.toDouble() * 0.5 - c * outH.toDouble() * 0.5

        val maxX = (sw - 1).toDouble()
        val maxY = (sh - 1).toDouble()

        for (j in 0 until outH) {
            val dj = j.toDouble()
            val rowOff = j * outW
            for (i in 0 until outW) {
                val di = i.toDouble()
                val x = (c * di + s * dj + bx).coerceIn(0.0, maxX)
                val y = (-s * di + c * dj + by).coerceIn(0.0, maxY)
                val x0 = floor(x).toInt()
                val y0 = floor(y).toInt()
                val x1 = min(x0 + 1, sw - 1)
                val y1 = min(y0 + 1, sh - 1)
                val fx = x - x0.toDouble()
                val fy = y - y0.toDouble()

                val p00 = srcPixels[y0 * sw + x0]
                val p10 = srcPixels[y0 * sw + x1]
                val p01 = srcPixels[y1 * sw + x0]
                val p11 = srcPixels[y1 * sw + x1]

                val w00 = (1.0 - fx) * (1.0 - fy)
                val w10 = fx * (1.0 - fy)
                val w01 = (1.0 - fx) * fy
                val w11 = fx * fy

                val r = (((p00 shr 16) and 0xFF) * w00 +
                    ((p10 shr 16) and 0xFF) * w10 +
                    ((p01 shr 16) and 0xFF) * w01 +
                    ((p11 shr 16) and 0xFF) * w11).roundToInt().coerceIn(0, 255)
                val g = (((p00 shr 8) and 0xFF) * w00 +
                    ((p10 shr 8) and 0xFF) * w10 +
                    ((p01 shr 8) and 0xFF) * w01 +
                    ((p11 shr 8) and 0xFF) * w11).roundToInt().coerceIn(0, 255)
                val b = ((p00 and 0xFF) * w00 +
                    (p10 and 0xFF) * w10 +
                    (p01 and 0xFF) * w01 +
                    (p11 and 0xFF) * w11).roundToInt().coerceIn(0, 255)

                outPixels[rowOff + i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        out.setPixels(outPixels, 0, outW, 0, 0, outW, outH)
        return out
    }

    private fun smooth3(v: DoubleArray): DoubleArray {
        if (v.size < 3) return v
        val out = DoubleArray(v.size)
        out[0] = (v[0] * 2.0 + v[1]) / 3.0
        for (i in 1 until v.size - 1) {
            out[i] = (v[i - 1] + v[i] + v[i + 1]) / 3.0
        }
        out[v.lastIndex] = (v[v.lastIndex - 1] + v[v.lastIndex] * 2.0) / 3.0
        return out
    }

    // MARK: - Result clean-up helpers

    private val PLACEHOLDER_GLYPHS = setOf('□', '〓', '\uFFFD')

    /** Number of unreadable-character placeholders (`□`, `〓`, `\uFFFD`) in [text]. */
    fun placeholderCount(text: String): Int =
        text.count { it in PLACEHOLDER_GLYPHS }

    /** True when [text] is non-empty and consists solely of placeholder glyphs. */
    fun isPlaceholderOnly(text: String): Boolean =
        text.isNotEmpty() && text.all { it in PLACEHOLDER_GLYPHS }

    /**
     * Drops smaller overlapping detections on the same physical column whose recognized text is
     * already contained in the larger detection's text.
     */
    fun dropSameLineDuplicates(
        detections: List<Detection>,
        minOverlap: Double = 0.6,
        minSimilarity: Double = 0.6
    ): List<Detection> {
        if (detections.size < 2) return detections
        val order = detections.indices.sortedByDescending { idx ->
            val b = detections[idx].box
            if (b.size >= 4) (b[2] - b[0]) * (b[3] - b[1]) else 0
        }
        val keep = BooleanArray(detections.size) { true }
        for (posA in order.indices) {
            val i = order[posA]
            if (!keep[i]) continue
            val a = detections[i].box
            if (a.size < 4) continue
            for (posB in (posA + 1) until order.size) {
                val j = order[posB]
                if (!keep[j]) continue
                val b = detections[j].box
                if (b.size < 4) continue
                val bw = max(1, b[2] - b[0]).toDouble()
                val bh = max(1, b[3] - b[1]).toDouble()
                val ox = (min(a[2], b[2]) - max(a[0], b[0])).toDouble()
                val oy = (min(a[3], b[3]) - max(a[1], b[1])).toDouble()
                if (ox <= 0.0 || oy <= 0.0) continue
                if (ox / bw < minOverlap || oy / bh < minOverlap) continue
                if (substringSimilarity(detections[j].text, detections[i].text) >= minSimilarity) {
                    keep[j] = false
                }
            }
        }
        return detections.filterIndexed { idx, _ -> keep[idx] }
    }

    /**
     * Semi-global edit similarity (`0.0..1.0`): fraction of characters in [small] that match a
     * contiguous substring of [large].
     */
    fun substringSimilarity(small: String, large: String): Double {
        if (small.isEmpty()) return 0.0
        if (large.isEmpty()) return 0.0
        val s = small.toCharArray()
        val l = large.toCharArray()
        if (s.size > l.size) return substringSimilarity(large, small)
        var prev = IntArray(l.size + 1) { 0 }
        for (i in 1..s.size) {
            val cur = IntArray(l.size + 1)
            cur[0] = i
            for (j in 1..l.size) {
                val cost = if (s[i - 1] == l[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            prev = cur
        }
        val best = prev.minOrNull() ?: s.size
        return 1.0 - best.toDouble() / s.size.toDouble()
    }
}
