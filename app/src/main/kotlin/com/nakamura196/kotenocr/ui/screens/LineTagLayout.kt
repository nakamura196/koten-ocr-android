package com.nakamura196.kotenocr.ui.screens

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.max
import kotlin.math.min

/**
 * Places the line-number tags (`LineTagLayout` in iOS `ResultOverlayView.swift`).
 *
 * Each tag goes outside the start of its line: above a vertical line, left of a horizontal one.
 * A tag is first sized to its line's width, so tags of neighbouring lines stay apart however
 * crowded the page is; on a dense page they are small until the reader zooms in (tags zoom with
 * the image). A tag much smaller than the page's typical one (a thin side note, 傍書) is then grown
 * where it covers no other box or tag. When there is no room before the image edge the tag goes
 * inside the start of its box.
 *
 * Rectangles are in screen pixels of the unzoomed image; [unit] is pixels per dp (the iOS sizes
 * are points, used here as dp).
 */
object LineTagLayout {
    /** Clear space between a tag and its box, in dp. Not shrunk with small tags. */
    const val GAP = 3f
    /** Regular tag height in dp; the font is three quarters of it. */
    const val REGULAR_HEIGHT = 12f
    /** A tag on a thin line is never smaller than this (dp), so its number stays legible. */
    const val MINIMUM_LEGIBLE_HEIGHT = 9f
    /** A line is thin when it is under this share of the page's typical line thickness. */
    const val THIN_SHARE = 0.6f

    fun regularSize(number: Int, unit: Float): Size =
        Size((8f + 5f * number.toString().length) * unit, REGULAR_HEIGHT * unit)

    /**
     * Box outline width for a page: 8% of its typical line thickness (the median of the boxes'
     * short sides), between 0.5 and 1.5 dp.
     */
    fun boxLineWidth(rects: List<Rect>, unit: Float): Float {
        val thickness = rects.map { min(it.width, it.height) }.sorted()
        if (thickness.isEmpty()) return 1.5f * unit
        return min(1.5f * unit, max(0.5f * unit, thickness[thickness.size / 2] * 0.08f))
    }

    fun frames(rects: List<Rect>, bounds: Rect?, unit: Float): List<Rect> {
        val area = bounds ?: Rect(-Float.MAX_VALUE / 4, -Float.MAX_VALUE / 4, Float.MAX_VALUE / 4, Float.MAX_VALUE / 4)
        val gap = GAP * unit
        val tags = rects.mapIndexed { index, rect ->
            val regular = regularSize(index + 1, unit)
            val vertical = rect.height >= rect.width
            val scale = min(1f, if (vertical) rect.width / regular.width else rect.height / regular.height)
            place(Size(regular.width * scale, regular.height * scale), rect, area, gap)
        }.toMutableList()
        if (tags.isEmpty()) return tags

        // A thin line among regular ones would get a tag far smaller than its neighbours'. Grow it
        // toward the page's typical tag height, as far as it stays clear of other boxes and tags.
        val heights = tags.map { it.height }.sorted()
        val typical = heights[heights.size / 2]
        for ((index, rect) in rects.withIndex()) {
            if (tags[index].height >= typical - 0.001f) continue
            val regular = regularSize(index + 1, unit)
            val natural = tags[index].height
            for (step in 0 until 4) {
                val height = typical - (typical - natural) * step / 4f
                val size = Size(regular.width * height / regular.height, height)
                val tag = place(size, rect, area, gap)
                // Half the box gap of clear space, so a grown tag never squeezes against a neighbour.
                val room = tag.inflate(gap / 2f)
                val blocked = rects.indices.any { other ->
                    other != index && (rects[other].overlaps(room) || tags[other].overlaps(room))
                }
                // Only outside the box: a larger tag inside it would hide more of the first characters.
                if (!blocked && !tag.overlaps(rect)) {
                    tags[index] = tag
                    break
                }
            }
        }

        // Last resort for thin lines: a floor on the tag size, even where it touches neighbours.
        val thickness = rects.map { min(it.width, it.height) }.sorted()
        val typicalThickness = thickness[thickness.size / 2]
        val floor = MINIMUM_LEGIBLE_HEIGHT * unit
        for ((index, rect) in rects.withIndex()) {
            if (tags[index].height >= floor - 0.001f) continue
            if (min(rect.width, rect.height) >= typicalThickness * THIN_SHARE) continue
            val regular = regularSize(index + 1, unit)
            val size = Size(regular.width * floor / regular.height, floor)
            val base = place(size, rect, area, gap)
            // Step further out along the line (up for a vertical line, left for a horizontal one)
            // while it would cover another box or tag; keep the first spot if none is clear.
            val vertical = rect.height >= rect.width
            var chosen = base
            for (step in 0 until 3) {
                val shift = step * (floor + 2f * unit)
                val candidate = if (vertical) base.translate(Offset(0f, -shift)) else base.translate(Offset(-shift, 0f))
                if (!area.containsRect(candidate)) break
                val blocked = rects.indices.any { other ->
                    other != index && (rects[other].overlaps(candidate) || tags[other].overlaps(candidate))
                }
                if (!blocked) {
                    chosen = candidate
                    break
                }
            }
            tags[index] = chosen
        }
        return tags
    }

    /** Outside the start of the line, or inside it when there is no room before the image edge. */
    private fun place(size: Size, rect: Rect, area: Rect, gap: Float): Rect {
        val vertical = rect.height >= rect.width
        val outside = if (vertical) {
            Rect(Offset(rect.center.x - size.width / 2f, rect.top - gap - size.height), size)
        } else {
            Rect(Offset(rect.left - gap - size.width, rect.center.y - size.height / 2f), size)
        }
        if (area.containsRect(outside)) return outside
        return if (vertical) {
            Rect(Offset(rect.center.x - size.width / 2f, rect.top + gap), size)
        } else {
            Rect(Offset(rect.left + gap, rect.center.y - size.height / 2f), size)
        }
    }

    private fun Rect.containsRect(r: Rect) =
        r.left >= left && r.top >= top && r.right <= right && r.bottom <= bottom
}
