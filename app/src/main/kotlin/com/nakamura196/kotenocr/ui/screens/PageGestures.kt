package com.nakamura196.kotenocr.ui.screens

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * One-finger touches on the page image, in stage pixels (the unzoomed image area).
 * [drag] gets the translation since the finger landed (never the step since the last event),
 * so a drag cannot accelerate or drift.
 */
interface PageTouch {
    fun begin(at: Offset)
    fun drag(at: Offset, translation: Offset)
    /** The finger lifted; [moved] is false for a tap (it stayed within the tap slop). */
    fun end(at: Offset, moved: Boolean)
    /** A second finger came down: whatever the first finger was doing is abandoned. */
    fun cancel()
    /**
     * The second tap of a double tap (the page zooms). The first one already came as a tap; the
     * second comes only here (after [cancel]), never as [end].
     */
    fun doubleTap(at: Offset) {}
}

/**
 * Pinch-zoom, two-finger pan and double-tap zoom of the page (iOS result screen), plus one-finger
 * touches passed to [touch]. Put it on the unzoomed image area; the image itself is drawn in a
 * `graphicsLayer` with the zoom and offset of [zoom].
 */
fun Modifier.pageGestures(
    key: Any?,
    zoom: () -> PageZoom.State,
    onZoom: (PageZoom.State) -> Unit,
    touch: PageTouch
): Modifier = pointerInput(key) {
    val slop = 6.dp.toPx()
    val doubleTapSlop = 32.dp.toPx()
    val doubleTapMillis = 300L
    var lastTapAt = -1L
    var lastTapPos = Offset.Zero
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val start = down.position
        val container = Size(size.width.toFloat(), size.height.toFloat())
        touch.begin(start)
        var moved = false
        var pinching = false
        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) {
                if (pinching) break
                val up = event.changes.firstOrNull()
                val at = up?.position ?: start
                val t = up?.uptimeMillis ?: -1L
                val isDoubleTap = !moved && up != null && lastTapAt >= 0 &&
                    t - lastTapAt <= doubleTapMillis && (at - lastTapPos).getDistance() <= doubleTapSlop
                if (isDoubleTap) {
                    // The second tap is not a tap of its own: it only zooms.
                    touch.cancel()
                    touch.doubleTap(at)
                    onZoom(PageZoom.doubleTapped(zoom(), at, container))
                    lastTapAt = -1L
                } else {
                    touch.end(at, moved)
                    if (!moved && up != null) {
                        lastTapAt = t
                        lastTapPos = at
                    } else {
                        lastTapAt = -1L
                    }
                }
                break
            }
            if (pressed.size >= 2) {
                if (!pinching) {
                    pinching = true
                    touch.cancel()
                }
                val factor = event.calculateZoom()
                val pan = event.calculatePan()
                val centroid = event.calculateCentroid()
                if (centroid != Offset.Unspecified) {
                    onZoom(PageZoom.transformed(zoom(), centroid, pan, factor, container))
                }
                event.changes.forEach { it.consume() }
                continue
            }
            // After a pinch, the finger left on the glass does nothing until it lifts.
            if (pinching) continue
            val change = pressed.first()
            val translation = change.position - start
            if (!moved && translation.getDistance() > slop) moved = true
            if (moved) {
                touch.drag(change.position, translation)
                change.consume()
            }
        }
    }
}
