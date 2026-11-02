package com.nakamura196.kotenocr.ui.screens

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * A grip on the selected box (`BoxEditGeometry.Handle` in iOS): four corners change width and
 * height, four sides change only one of them.
 */
enum class BoxHandle {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
    TOP, BOTTOM, LEFT, RIGHT;

    val movesMinX get() = this == TOP_LEFT || this == BOTTOM_LEFT || this == LEFT
    val movesMaxX get() = this == TOP_RIGHT || this == BOTTOM_RIGHT || this == RIGHT
    val movesMinY get() = this == TOP_LEFT || this == TOP_RIGHT || this == TOP
    val movesMaxY get() = this == BOTTOM_LEFT || this == BOTTOM_RIGHT || this == BOTTOM

    companion object {
        val corners = listOf(TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT)
        val sides = listOf(TOP, BOTTOM, LEFT, RIGHT)
    }
}

/**
 * Pure geometry for the box editor (`BoxEditGeometry.swift`). Rectangles are in image pixels
 * unless a name says "stage" (screen pixels of the image area, after zoom and pan).
 * No Android framework types, so the rules can be unit-tested on the JVM.
 */
object BoxEditGeometry {
    /** Smallest side of a box, in image pixels. */
    const val MIN_SIDE = 12f

    /** Moves [start] by the cumulative [translation], kept inside [bounds]. */
    fun moved(start: Rect, translation: Offset, bounds: Rect): Rect {
        val l = (start.left + translation.x).coerceIn(bounds.left, max(bounds.left, bounds.right - start.width))
        val t = (start.top + translation.y).coerceIn(bounds.top, max(bounds.top, bounds.bottom - start.height))
        return Rect(l, t, l + start.width, t + start.height)
    }

    /**
     * Drags one handle of [start] by the cumulative [translation] since the drag began.
     * The opposite side stays fixed, a side handle changes only width or height, the dragged edge
     * stays inside [bounds], and the box never gets smaller than [minSide].
     * (The finger's offset from the edge is kept: the edge never jumps to the finger.)
     */
    fun resized(start: Rect, handle: BoxHandle, translation: Offset, bounds: Rect, minSide: Float = MIN_SIDE): Rect {
        var l = start.left; var r = start.right; var t = start.top; var b = start.bottom
        if (handle.movesMinX) l = min(max(start.left + translation.x, bounds.left), start.right - minSide)
        if (handle.movesMaxX) r = max(min(start.right + translation.x, bounds.right), start.left + minSide)
        if (handle.movesMinY) t = min(max(start.top + translation.y, bounds.top), start.bottom - minSide)
        if (handle.movesMaxY) b = max(min(start.bottom + translation.y, bounds.bottom), start.top + minSide)
        return Rect(l, t, r, b)
    }

    fun point(handle: BoxHandle, rect: Rect): Offset = when (handle) {
        BoxHandle.TOP_LEFT -> Offset(rect.left, rect.top)
        BoxHandle.TOP_RIGHT -> Offset(rect.right, rect.top)
        BoxHandle.BOTTOM_LEFT -> Offset(rect.left, rect.bottom)
        BoxHandle.BOTTOM_RIGHT -> Offset(rect.right, rect.bottom)
        BoxHandle.TOP -> Offset(rect.center.x, rect.top)
        BoxHandle.BOTTOM -> Offset(rect.center.x, rect.bottom)
        BoxHandle.LEFT -> Offset(rect.left, rect.center.y)
        BoxHandle.RIGHT -> Offset(rect.right, rect.center.y)
    }

    /** Touch sizes for [hitHandle], in screen pixels (the iOS values are points: 22 / 18 / 14). */
    data class Grips(val cornerRadius: Float, val outside: Float, val inside: Float, val cornerBias: Float) {
        companion object {
            fun forDensity(density: Float) = Grips(22f * density, 18f * density, 14f * density, 6f * density)
        }
    }

    /**
     * What a finger at [point] grabs on the selected box [rect] (both stage pixels).
     * A corner counts within the corner radius; a side counts anywhere along its length, from
     * `outside` beyond it to a band inside it (at most a third of the box, so a thin line can still
     * be moved from its middle). The nearest grip wins, so on a thin line the top side is not hidden
     * by the two top corners. Returns null for "move" or "missed".
     */
    fun hitHandle(point: Offset, rect: Rect, grips: Grips): BoxHandle? {
        val candidates = mutableListOf<Pair<BoxHandle, Float>>()
        for (c in BoxHandle.corners) {
            val p = point(c, rect)
            val d = hypot(p.x - point.x, p.y - point.y)
            // A corner is preferred over a side the finger is equally close to.
            if (d <= grips.cornerRadius) candidates += c to (d - grips.cornerBias)
        }
        val inX = min(grips.inside, rect.width / 3f)
        val inY = min(grips.inside, rect.height / 3f)
        val o = grips.outside
        val withinRows = point.y >= rect.top - o && point.y <= rect.bottom + o
        val withinCols = point.x >= rect.left - o && point.x <= rect.right + o
        if (withinRows) {
            if (point.x >= rect.left - o && point.x <= rect.left + inX) candidates += BoxHandle.LEFT to abs(point.x - rect.left)
            if (point.x <= rect.right + o && point.x >= rect.right - inX) candidates += BoxHandle.RIGHT to abs(point.x - rect.right)
        }
        if (withinCols) {
            if (point.y >= rect.top - o && point.y <= rect.top + inY) candidates += BoxHandle.TOP to abs(point.y - rect.top)
            if (point.y <= rect.bottom + o && point.y >= rect.bottom - inY) candidates += BoxHandle.BOTTOM to abs(point.y - rect.bottom)
        }
        return candidates.minByOrNull { it.second }?.first
    }

    /** What a touch starting at a stage point does with the Select tool. */
    sealed interface Grab {
        data class Resize(val index: Int, val handle: BoxHandle) : Grab
        data class Move(val index: Int) : Grab
        /** Pan the page if zoomed; when the finger lifts without moving, select the box under it. */
        data object PanOrSelect : Grab
    }

    /**
     * Decides what a touch grabs. Only the selected box can be resized or moved; touching any other
     * box never moves it (it is selected when the finger lifts), so brushing a neighbour on a
     * crowded page cannot drag it away.
     */
    fun grab(location: Offset, stage: PageStage, rects: List<Rect>, selected: Int?, grips: Grips): Grab {
        val sel = selected ?: return Grab.PanOrSelect
        if (sel !in rects.indices) return Grab.PanOrSelect
        val rect = rects[sel]
        val stageRect = stage.toStage(rect)
        // Outside the selected box but inside a neighbour, a side reaches only a third of the
        // neighbour into it, so a finger well inside the neighbour selects it instead of resizing
        // the selected line (on a crowded page the full reach would cover the whole neighbour).
        var outside = grips.outside
        if (!stageRect.contains(location)) {
            for ((i, r) in rects.withIndex()) {
                if (i == sel) continue
                val other = stage.toStage(r)
                if (other.contains(location)) outside = min(outside, min(other.width, other.height) / 3f)
            }
        }
        hitHandle(location, stageRect, grips.copy(outside = outside))?.let { return Grab.Resize(sel, it) }
        if (rect.contains(stage.toImage(location))) return Grab.Move(sel)
        return Grab.PanOrSelect
    }

    /** Index of the box under [point] (the smallest if several overlap), or null. */
    fun hitBox(point: Offset, rects: List<Rect>): Int? =
        rects.withIndex()
            .filter { it.value.containsInclusive(point) }
            .minByOrNull { it.value.width * it.value.height }?.index

    /** A box drawn from [a] to [b], clipped to [bounds]; null when too small to be a line. */
    fun drawn(a: Offset, b: Offset, bounds: Rect, minSide: Float = MIN_SIDE): Rect? {
        val r = Rect(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y)).intersect(bounds)
        if (r.width < minSide || r.height < minSide) return null
        return r
    }

    private fun Rect.containsInclusive(p: Offset) = p.x in left..right && p.y in top..bottom
}

/**
 * Maps between the image area on screen (the "stage") and image pixels, with zoom and pan.
 * [fit] is the aspect-fit rectangle of the image inside a container of [containerSize]; the zoom is
 * about the container centre, then [offset] is added. Same as a `graphicsLayer` with
 * scale = [zoom] and translation = [offset] on a container-sized layer (`BoxEditGeometry.Stage`).
 */
data class PageStage(
    val containerSize: Size,
    val fit: Rect,
    val imageWidth: Float,
    val imageHeight: Float,
    val zoom: Float = 1f,
    val offset: Offset = Offset.Zero
) {
    private val s get() = if (imageWidth > 0f) fit.width / imageWidth else 1f
    private val cx get() = containerSize.width / 2f
    private val cy get() = containerSize.height / 2f

    fun toStage(p: Offset): Offset {
        val fx = fit.left + p.x * s
        val fy = fit.top + p.y * s
        return Offset(cx + offset.x + zoom * (fx - cx), cy + offset.y + zoom * (fy - cy))
    }

    fun toImage(p: Offset): Offset {
        if (zoom <= 0f || s <= 0f) return Offset.Zero
        val fx = cx + (p.x - cx - offset.x) / zoom
        val fy = cy + (p.y - cy - offset.y) / zoom
        return Offset((fx - fit.left) / s, (fy - fit.top) / s)
    }

    fun toStage(r: Rect): Rect {
        val a = toStage(r.topLeft)
        val b = toStage(r.bottomRight)
        return Rect(a, b)
    }

    /** Image pixels per one screen pixel at the current zoom. */
    val pixelsPerScreenPixel: Float get() = if (s > 0f && zoom > 0f) 1f / (s * zoom) else 1f

    companion object {
        /** Aspect-fit (ContentScale.Fit, centred) rectangle of an image in a container. */
        fun fitRect(containerSize: Size, imageWidth: Float, imageHeight: Float): Rect {
            if (imageWidth <= 0f || imageHeight <= 0f || containerSize.width <= 0f || containerSize.height <= 0f) return Rect.Zero
            val k = min(containerSize.width / imageWidth, containerSize.height / imageHeight)
            val w = imageWidth * k
            val h = imageHeight * k
            val l = (containerSize.width - w) / 2f
            val t = (containerSize.height - h) / 2f
            return Rect(l, t, l + w, t + h)
        }
    }
}

/**
 * Pinch, pan and double-tap zoom of the page image (the iOS result screen): the point under the
 * fingers stays under them, zoom never goes below 1, and at 1 the page is centred again.
 */
object PageZoom {
    const val MIN = 1f
    const val MAX = 8f
    const val DOUBLE_TAP = 3f

    data class State(val zoom: Float = 1f, val offset: Offset = Offset.Zero)

    /**
     * Applies one step of a two-finger gesture: scale by [factor] about [centroid] (stage pixels,
     * container origin) and move by [pan].
     */
    fun transformed(state: State, centroid: Offset, pan: Offset, factor: Float, containerSize: Size): State {
        val newZoom = (state.zoom * factor).coerceIn(MIN, MAX)
        val ratio = newZoom / state.zoom
        val p = centroid - Offset(containerSize.width / 2f, containerSize.height / 2f)
        val off = p - (p - state.offset) * ratio + pan
        return clamped(State(newZoom, off), containerSize)
    }

    /** One-finger pan; does nothing at zoom 1. */
    fun panned(state: State, pan: Offset, containerSize: Size): State =
        if (state.zoom <= MIN) state else clamped(state.copy(offset = state.offset + pan), containerSize)

    /** Double tap: zoom in to [DOUBLE_TAP] around the tap, or back to the whole page. */
    fun doubleTapped(state: State, at: Offset, containerSize: Size): State {
        if (state.zoom > MIN) return State()
        val p = at - Offset(containerSize.width / 2f, containerSize.height / 2f)
        return clamped(State(DOUBLE_TAP, p - p * DOUBLE_TAP), containerSize)
    }

    /** Keeps the zoomed page covering the container (no panning the page out of sight). */
    fun clamped(state: State, containerSize: Size): State {
        if (state.zoom <= MIN) return State()
        val mx = containerSize.width * (state.zoom - 1f) / 2f
        val my = containerSize.height * (state.zoom - 1f) / 2f
        return State(state.zoom, Offset(state.offset.x.coerceIn(-mx, mx), state.offset.y.coerceIn(-my, my)))
    }
}
