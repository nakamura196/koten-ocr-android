package com.nakamura196.kotenocr.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Global small-angle (±10°) skew estimation and correction for document images.
 *
 * Uses a local-mean adaptive threshold (integral image) + projection-profile variance maximization
 * (coarse 0.5° step, fine 0.1° step) with a 3-strip consistency gate so photos with 2D perspective
 * fan are left to per-line tilt correction ([LineTilt]).
 */
object Deskew {

    const val SETTINGS_KEY = "autoDeskew"

    /** Largest tilt magnitude (degrees) considered by the search. */
    const val MAX_ANGLE = 10.0

    /** Angles smaller than this (degrees) are treated as already straight. */
    const val MIN_ANGLE = 0.3

    /** Minimum ratio of best projection score to 0° score required before rotating. */
    const val MIN_GAIN = 1.01

    /** Minimum number of dark pixels needed to trust the estimate. */
    const val MIN_INK_PIXELS = 200

    /** Maximum disagreement (degrees) allowed between 3 strips across the page. */
    const val MAX_STRIP_SPREAD = 2.0

    /** Strip angle noise floor (degrees) when checking for opposite-sign perspective fan. */
    private const val STRIP_SIGN_NOISE = 0.5

    private const val WORKING_LONG_SIDE = 1000
    private const val MAX_POINTS = 80_000

    enum class TextOrientation {
        VERTICAL,
        HORIZONTAL,
        AUTO
    }

    data class Estimate(
        val angle: Double,
        val gain: Double,
        val inkPixels: Int,
        val paperColor: Int,
        val stripAngles: List<Double?> = listOf(null, null, null)
    ) {
        val stripsAgree: Boolean
            get() {
                val valid = stripAngles.filterNotNull()
                if (valid.size < 2) return true
                val hi = valid.max()
                val lo = valid.min()
                if (hi - lo > MAX_STRIP_SPREAD + 1e-9) return false
                return !(hi > STRIP_SIGN_NOISE && lo < -STRIP_SIGN_NOISE)
            }

        val shouldRotate: Boolean
            get() = abs(angle) >= MIN_ANGLE &&
                gain >= MIN_GAIN &&
                inkPixels >= MIN_INK_PIXELS &&
                stripsAgree
    }

    /**
     * Returns a deskewed copy of [bitmap] when a clear small tilt is found, or [bitmap] unchanged.
     */
    fun deskew(
        bitmap: Bitmap,
        orientation: TextOrientation = TextOrientation.AUTO
    ): Pair<Bitmap, Estimate> {
        val est = estimateSkew(bitmap, orientation)
        if (!est.shouldRotate) return bitmap to est
        val rotated = rotate(bitmap, byDegrees = -est.angle, fillColor = est.paperColor)
        return rotated to est
    }

    /**
     * Estimates the page tilt of [bitmap] in degrees within `[-MAX_ANGLE, +MAX_ANGLE]`.
     */
    fun estimateSkew(
        bitmap: Bitmap,
        orientation: TextOrientation = TextOrientation.AUTO
    ): Estimate {
        val sw = bitmap.width
        val sh = bitmap.height
        if (sw <= 1 || sh <= 1) {
            return Estimate(0.0, 1.0, 0, Color.WHITE)
        }

        val scale = min(1.0, WORKING_LONG_SIDE.toDouble() / max(sw, sh).toDouble())
        val w = max(1, (sw * scale).roundToInt())
        val h = max(1, (sh * scale).roundToInt())
        val pixels = if (w == sw && h == sh) {
            IntArray(w * h).also { bitmap.getPixels(it, 0, w, 0, 0, w, h) }
        } else {
            Resample.bitmap(bitmap, w, h)
        }

        val gray = IntArray(w * h)
        val stride = w + 1
        val integral = LongArray(stride * (h + 1))

        for (y in 0 until h) {
            var rowSum = 0L
            val rowOff = y * w
            val intRowOff = (y + 1) * stride
            val prevIntRowOff = y * stride
            for (x in 0 until w) {
                val c = pixels[rowOff + x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val v = (r * 299 + g * 587 + b * 114) / 1000
                gray[rowOff + x] = v
                rowSum += v
                integral[intRowOff + x + 1] = integral[prevIntRowOff + x + 1] + rowSum
            }
        }

        val radius = max(7, max(w, h) / 40)
        val margin = max(2, (0.015 * max(w, h)).toInt())
        val cx = w * 0.5f
        val cy = h * 0.5f

        var xsList = FloatArray(min(w * h / 4, MAX_POINTS * 2))
        var ysList = FloatArray(xsList.size)
        var pointCount = 0

        var paperR = 0L
        var paperG = 0L
        var paperB = 0L
        var paperCount = 0L

        for (y in 0 until h) {
            val y0 = max(0, y - radius)
            val y1 = min(h, y + radius + 1)
            val rowOff = y * w
            val inYMargin = y >= margin && y < h - margin
            val inYPaper = y > h / 5 && y < (h * 4) / 5
            for (x in 0 until w) {
                val x0 = max(0, x - radius)
                val x1 = min(w, x + radius + 1)
                val area = (y1 - y0).toLong() * (x1 - x0).toLong()
                val sum = integral[y1 * stride + x1] -
                    integral[y0 * stride + x1] -
                    integral[y1 * stride + x0] +
                    integral[y0 * stride + x0]
                val g = gray[rowOff + x].toLong()
                if (g * area * 100L < sum * 85L - 400L * area) {
                    if (inYMargin && x >= margin && x < w - margin) {
                        if (pointCount == xsList.size) {
                            val newCap = xsList.size * 2
                            xsList = xsList.copyOf(newCap)
                            ysList = ysList.copyOf(newCap)
                        }
                        xsList[pointCount] = x - cx
                        ysList[pointCount] = y - cy
                        pointCount++
                    }
                } else if (inYPaper && x > w / 5 && x < (w * 4) / 5 && g * area >= sum) {
                    val c = pixels[rowOff + x]
                    paperR += (c shr 16) and 0xFF
                    paperG += (c shr 8) and 0xFF
                    paperB += c and 0xFF
                    paperCount++
                }
            }
        }

        val xs: FloatArray
        val ys: FloatArray
        if (pointCount > MAX_POINTS) {
            xs = FloatArray(MAX_POINTS)
            ys = FloatArray(MAX_POINTS)
            val step = pointCount.toDouble() / MAX_POINTS.toDouble()
            var k = 0.0
            for (i in 0 until MAX_POINTS) {
                val idx = min(pointCount - 1, k.toInt())
                xs[i] = xsList[idx]
                ys[i] = ysList[idx]
                k += step
            }
        } else {
            xs = xsList.copyOf(pointCount)
            ys = ysList.copyOf(pointCount)
        }

        val paperColor = if (paperCount > 0) {
            Color.rgb(
                (paperR / paperCount).toInt().coerceIn(0, 255),
                (paperG / paperCount).toInt().coerceIn(0, 255),
                (paperB / paperCount).toInt().coerceIn(0, 255)
            )
        } else {
            Color.WHITE
        }

        val n = xs.size
        if (n < MIN_INK_PIXELS) {
            return Estimate(0.0, 1.0, n, paperColor)
        }

        val bins = sqrt((w * w + h * h).toDouble()).toInt() + 2
        val offset = (bins / 2).toFloat()
        val hist = IntArray(bins)

        fun score(deg: Double, vertical: Boolean, px: FloatArray, py: FloatArray): Double {
            val t = deg * PI / 180.0
            val c = cos(t).toFloat()
            val s = sin(t).toFloat()
            hist.fill(0)
            if (vertical) {
                for (i in px.indices) {
                    val u = px[i] * c - py[i] * s
                    val k = floor(u + offset).toInt().coerceIn(0, bins - 1)
                    hist[k]++
                }
            } else {
                for (i in px.indices) {
                    val u = px[i] * s + py[i] * c
                    val k = floor(u + offset).toInt().coerceIn(0, bins - 1)
                    hist[k]++
                }
            }
            var total = 0.0
            for (v in hist) {
                val dv = v.toDouble()
                total += dv * dv
            }
            return total
        }

        data class SearchResult(val angle: Double, val best: Double, val zero: Double)

        fun search(vertical: Boolean, px: FloatArray, py: FloatArray): SearchResult {
            var bestAngle = 0.0
            var best = -1.0
            val coarseSteps = (MAX_ANGLE / 0.5).roundToInt()
            for (k in -coarseSteps..coarseSteps) {
                val d = k * 0.5
                val sc = score(d, vertical, px, py)
                if (sc > best) {
                    best = sc
                    bestAngle = d
                }
            }
            val coarse = bestAngle
            for (k in -5..5) {
                if (k == 0) continue
                val d = coarse + k * 0.1
                if (abs(d) > MAX_ANGLE + 1e-6) continue
                val sc = score(d, vertical, px, py)
                if (sc > best) {
                    best = sc
                    bestAngle = d
                }
            }
            val zero = score(0.0, vertical, px, py)
            return SearchResult((bestAngle * 10.0).roundToInt() / 10.0, best, zero)
        }

        val result: SearchResult
        val isVertical: Boolean
        when (orientation) {
            TextOrientation.VERTICAL -> {
                result = search(true, xs, ys)
                isVertical = true
            }
            TextOrientation.HORIZONTAL -> {
                result = search(false, xs, ys)
                isVertical = false
            }
            TextOrientation.AUTO -> {
                val v = search(true, xs, ys)
                val hz = search(false, xs, ys)
                isVertical = v.best >= hz.best
                result = if (isVertical) v else hz
            }
        }

        val gain = if (result.zero > 0) result.best / result.zero else 1.0

        val along = if (isVertical) xs else ys
        val sortedAlong = along.clone().also { it.sort() }
        fun percentile(q: Float): Float {
            val pos = max(0f, min((n - 1).toFloat(), q * (n - 1).toFloat()))
            val loIdx = floor(pos).toInt()
            val hiIdx = ceil(pos).toInt()
            val f = pos - loIdx
            return sortedAlong[loIdx] * (1f - f) + sortedAlong[hiIdx] * f
        }
        val lo = percentile(0.02f)
        val hi = percentile(0.98f)
        val stripAngles = MutableList<Double?>(3) { null }
        if (hi > lo) {
            for (i in 0 until 3) {
                val a = lo + (hi - lo) * (i.toFloat() / 3f)
                val b = lo + (hi - lo) * ((i + 1).toFloat() / 3f)
                var count = 0
                for (k in 0 until n) {
                    val v = along[k]
                    if (v >= a && (if (i < 2) v < b else v <= b)) count++
                }
                if (count >= MIN_INK_PIXELS) {
                    val sx = FloatArray(count)
                    val sy = FloatArray(count)
                    var idx = 0
                    for (k in 0 until n) {
                        val v = along[k]
                        if (v >= a && (if (i < 2) v < b else v <= b)) {
                            sx[idx] = xs[k]
                            sy[idx] = ys[k]
                            idx++
                        }
                    }
                    stripAngles[i] = search(isVertical, sx, sy).angle
                }
            }
        }

        return Estimate(
            angle = result.angle,
            gain = gain,
            inkPixels = n,
            paperColor = paperColor,
            stripAngles = stripAngles
        )
    }

    /**
     * Rotates [bitmap] counter-clockwise by [byDegrees] on screen, expanding the canvas
     * so corners are not clipped and filling exposed triangles with [fillColor].
     */
    fun rotate(
        bitmap: Bitmap,
        byDegrees: Double,
        fillColor: Int = Color.WHITE
    ): Bitmap {
        val w = bitmap.width.toDouble()
        val h = bitmap.height.toDouble()
        val t = byDegrees * PI / 180.0
        val c = abs(cos(t))
        val s = abs(sin(t))
        val nw = max(1, ceil(w * c + h * s).toInt())
        val nh = max(1, ceil(w * s + h * c).toInt())

        val out = Bitmap.createBitmap(nw, nh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(fillColor)
        canvas.translate(nw * 0.5f, nh * 0.5f)
        // Android Canvas.rotate(deg) rotates clockwise for positive angles in y-down coordinates,
        // so rotating counter-clockwise by `byDegrees` visually is `canvas.rotate(-byDegrees.toFloat())`.
        canvas.rotate((-byDegrees).toFloat())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bitmap, (-w * 0.5).toFloat(), (-h * 0.5).toFloat(), paint)
        return out
    }
}
