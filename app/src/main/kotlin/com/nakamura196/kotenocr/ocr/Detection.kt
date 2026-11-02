package com.nakamura196.kotenocr.ocr

import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class Detection(
    val id: Int,
    /** [x1, y1, x2, y2] in pixel coordinates */
    var box: IntArray,
    var score: Float,
    val classIndex: Int,
    val className: String,
    var text: String = "",
    var predCharCount: Float = 100f
) {
    /** Whether this detection represents a text line (`line_*` class). */
    val isTextLine: Boolean
        get() = className.startsWith("line_")

    /** Pixel-space rectangle matching [box]. */
    val rect: RectF
        get() = if (box.size >= 4) {
            RectF(
                box[0].toFloat(),
                box[1].toFloat(),
                max(box[0], box[2]).toFloat(),
                max(box[1], box[3]).toFloat()
            )
        } else {
            RectF()
        }

    /**
     * Marks this detection as confirmed/edited by the reader so that
     * the confidence filter slider never hides it.
     */
    fun markReaderConfirmed() {
        score = 1.0f
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Detection) return false
        return id == other.id &&
            box.contentEquals(other.box) &&
            score == other.score &&
            classIndex == other.classIndex &&
            className == other.className &&
            text == other.text &&
            predCharCount == other.predCharCount
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + box.contentHashCode()
        result = 31 * result + score.hashCode()
        result = 31 * result + classIndex
        result = 31 * result + className.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + predCharCount.hashCode()
        return result
    }

    companion object {
        /**
         * Clamps `[x1, y1, x2, y2]` into `0..width` and `0..height`, rounds to integer pixel coordinates,
         * and returns `null` when any input is non-finite or the clamped box has zero/negative area.
         */
        fun clampedBox(
            x1: Float,
            y1: Float,
            x2: Float,
            y2: Float,
            width: Int,
            height: Int,
            truncateTowardZero: Boolean = false
        ): IntArray? {
            if (!x1.isFinite() || !y1.isFinite() || !x2.isFinite() || !y2.isFinite()) return null
            if (width <= 0 || height <= 0) return null
            val maxX = width.toFloat()
            val maxY = height.toFloat()
            fun clampAndRound(v: Float, hi: Float): Int {
                val c = min(hi, max(0f, v))
                return if (truncateTowardZero) c.toInt() else c.roundToInt()
            }
            val bx1 = clampAndRound(x1, maxX)
            val by1 = clampAndRound(y1, maxY)
            val bx2 = clampAndRound(x2, maxX)
            val by2 = clampAndRound(y2, maxY)
            if (bx2 <= bx1 || by2 <= by1) return null
            return intArrayOf(bx1, by1, bx2, by2)
        }
    }
}
