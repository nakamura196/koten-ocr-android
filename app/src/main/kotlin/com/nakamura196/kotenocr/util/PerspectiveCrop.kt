package com.nakamura196.kotenocr.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A four-corner quadrilateral in top-left origin coordinates (normalized `0..1` or pixel coordinates).
 * Port of `Quad` and `PerspectiveCrop` from `PerspectiveCrop.swift` (v2.1).
 */
data class Quad(
    var topLeft: Offset,
    var topRight: Offset,
    var bottomRight: Offset,
    var bottomLeft: Offset
) {
    /** Axis-aligned rectangle matching [rect]. */
    constructor(rect: RectF) : this(
        topLeft = Offset(rect.left, rect.top),
        topRight = Offset(rect.right, rect.top),
        bottomRight = Offset(rect.right, rect.bottom),
        bottomLeft = Offset(rect.left, rect.bottom)
    )

    fun scaled(width: Float, height: Float): Quad = Quad(
        topLeft = Offset(topLeft.x * width, topLeft.y * height),
        topRight = Offset(topRight.x * width, topRight.y * height),
        bottomRight = Offset(bottomRight.x * width, bottomRight.y * height),
        bottomLeft = Offset(bottomLeft.x * width, bottomLeft.y * height)
    )

    /** Output size that preserves the longer opposite edges of the quad. */
    val rectifiedSize: Pair<Float, Float>
        get() {
            val top = dist(topLeft, topRight)
            val bottom = dist(bottomLeft, bottomRight)
            val left = dist(topLeft, bottomLeft)
            val right = dist(topRight, bottomRight)
            return max(top, bottom) to max(left, right)
        }

    /** True when the four corners wind consistently around a convex non-degenerate polygon. */
    val isConvex: Boolean
        get() {
            val p = arrayOf(topLeft, topRight, bottomRight, bottomLeft)
            var sign = 0f
            for (i in 0 until 4) {
                val a = p[i]
                val b = p[(i + 1) % 4]
                val c = p[(i + 2) % 4]
                val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
                if (abs(cross) < 1e-6f) return false
                if (sign == 0f) {
                    sign = cross
                } else if (sign * cross < 0f) {
                    return false
                }
            }
            return true
        }

    private fun dist(a: Offset, b: Offset): Float = hypot(a.x - b.x, a.y - b.y)

    companion object {
        val UNIT = Quad(
            topLeft = Offset(0f, 0f),
            topRight = Offset(1f, 0f),
            bottomRight = Offset(1f, 1f),
            bottomLeft = Offset(0f, 1f)
        )

        /**
         * Orders four arbitrary points into `(topLeft, topRight, bottomRight, bottomLeft)`
         * using sum and difference of coordinates. Returns `null` if points are not distinct.
         */
        fun normalizing(points: List<Offset>): Quad? {
            if (points.size != 4) return null
            val tl = points.minByOrNull { it.x + it.y } ?: return null
            val br = points.maxByOrNull { it.x + it.y } ?: return null
            val tr = points.maxByOrNull { it.x - it.y } ?: return null
            val bl = points.minByOrNull { it.x - it.y } ?: return null
            val unique = setOf(tl, tr, br, bl)
            if (unique.size < 4) return null
            return Quad(topLeft = tl, topRight = tr, bottomRight = br, bottomLeft = bl)
        }
    }
}

/**
 * Perspective rectification and automatic page-corner detection for the Crop screen.
 */
object PerspectiveCrop {

    /**
     * Warps [normalizedQuad] (`0..1` top-left origin) of [bitmap] into a flat rectangle.
     */
    fun rectify(bitmap: Bitmap, normalizedQuad: Quad): Bitmap? {
        val q = normalizedQuad.scaled(bitmap.width.toFloat(), bitmap.height.toFloat())
        val (outWFloat, outHFloat) = q.rectifiedSize
        if (!q.isConvex || outWFloat < 8f || outHFloat < 8f) return null

        val outW = max(8, outWFloat.roundToInt())
        val outH = max(8, outHFloat.roundToInt())

        val srcPts = floatArrayOf(
            q.topLeft.x, q.topLeft.y,
            q.topRight.x, q.topRight.y,
            q.bottomRight.x, q.bottomRight.y,
            q.bottomLeft.x, q.bottomLeft.y
        )
        val dstPts = floatArrayOf(
            0f, 0f,
            outW.toFloat(), 0f,
            outW.toFloat(), outH.toFloat(),
            0f, outH.toFloat()
        )

        val matrix = Matrix()
        if (!matrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)) return null

        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bitmap, matrix, paint)
        return out
    }

    /**
     * True when every corner of [q] lies within [tolerance] of the full `0..1` image frame.
     */
    fun isNearlyFullFrame(q: Quad, tolerance: Float = 0.03f): Boolean {
        val u = Quad.UNIT
        val corners = listOf(
            q.topLeft to u.topLeft,
            q.topRight to u.topRight,
            q.bottomRight to u.bottomRight,
            q.bottomLeft to u.bottomLeft
        )
        return corners.all { (a, b) ->
            abs(a.x - b.x) <= tolerance && abs(a.y - b.y) <= tolerance
        }
    }

    /**
     * True when [q] is a convex quadrilateral that isn't just the outer frame of the photo.
     */
    fun isUsableDetection(q: Quad): Boolean =
        q.isConvex && !isNearlyFullFrame(q)

    /**
     * Returns [found] only if the user hasn't moved the corners away from [seeded] while
     * background detection was running.
     */
    fun adoptDetected(found: Quad, current: Quad, seeded: Quad): Quad =
        if (current == seeded) found else current

    /**
     * Detects a usable page quad for the Crop screen's "Auto-correct" (`自動補正`) action.
     */
    fun detectForCropScreen(bitmap: Bitmap): Quad? {
        val q = detectPage(bitmap) ?: return null
        return if (isUsableDetection(q)) q else null
    }

    /**
     * Detects the four corners of a lighter document page against a darker background in
     * normalized (`0..1`) top-left coordinates.
     *
     * Uses downscaled luminance Otsu thresholding + extreme corner projections (`x+y` and `x-y`)
     * on the largest bright page region.
     */
    fun detectPage(bitmap: Bitmap): Quad? {
        val sw = bitmap.width
        val sh = bitmap.height
        if (sw < 32 || sh < 32) return null

        val maxDim = 320
        val scale = min(1f, maxDim.toFloat() / max(sw, sh).toFloat())
        val w = max(16, (sw * scale).roundToInt())
        val h = max(16, (sh * scale).roundToInt())
        val small = if (w == sw && h == sh) bitmap else Bitmap.createScaledBitmap(bitmap, w, h, true)

        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        if (small !== bitmap) small.recycle()

        val luma = IntArray(w * h)
        val hist = IntArray(256)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val y = (r * 299 + g * 587 + b * 114) / 1000
            luma[i] = y
            hist[y]++
        }

        // Otsu threshold to separate bright paper from darker desk/background
        val total = w * h
        var sumAll = 0.0
        for (t in 0..255) sumAll += t.toDouble() * hist[t]

        var sumB = 0.0
        var wB = 0
        var maxVar = 0.0
        var threshold = 128

        for (t in 0..255) {
            wB += hist[t]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break
            sumB += t.toDouble() * hist[t]
            val mB = sumB / wB
            val mF = (sumAll - sumB) / wF
            val between = wB.toDouble() * wF.toDouble() * (mB - mF) * (mB - mF)
            if (between > maxVar) {
                maxVar = between
                threshold = t
            }
        }

        // Require meaningful contrast between page and background
        var brightCount = 0
        for (y in luma) {
            if (y >= threshold) brightCount++
        }
        val brightRatio = brightCount.toFloat() / total.toFloat()
        if (brightRatio < 0.20f || brightRatio > 0.96f) return null

        // Find extreme corners `(x+y)` and `(x-y)` of interior-supported bright pixels
        var minSum = Float.MAX_VALUE
        var maxSum = -Float.MAX_VALUE
        var maxDiff = -Float.MAX_VALUE
        var minDiff = Float.MAX_VALUE

        var tl = Offset.Zero
        var br = Offset.Zero
        var tr = Offset.Zero
        var bl = Offset.Zero

        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                if (luma[row + x] < threshold) continue
                // Require 3x3 neighborhood majority to ignore isolated bright specks
                var neighbors = 0
                for (dy in -1..1) {
                    val nr = (y + dy) * w
                    for (dx in -1..1) {
                        if (luma[nr + x + dx] >= threshold) neighbors++
                    }
                }
                if (neighbors < 6) continue

                val nx = x.toFloat() / (w - 1).toFloat()
                val ny = y.toFloat() / (h - 1).toFloat()
                val sum = nx + ny
                val diff = nx - ny

                if (sum < minSum) {
                    minSum = sum
                    tl = Offset(nx, ny)
                }
                if (sum > maxSum) {
                    maxSum = sum
                    br = Offset(nx, ny)
                }
                if (diff > maxDiff) {
                    maxDiff = diff
                    tr = Offset(nx, ny)
                }
                if (diff < minDiff) {
                    minDiff = diff
                    bl = Offset(nx, ny)
                }
            }
        }

        if (minSum == Float.MAX_VALUE) return null
        val quad = Quad.normalizing(listOf(tl, tr, br, bl)) ?: return null
        val (rw, rh) = quad.rectifiedSize
        if (!quad.isConvex || rw < 0.3f || rh < 0.3f) return null
        return quad
    }
}
