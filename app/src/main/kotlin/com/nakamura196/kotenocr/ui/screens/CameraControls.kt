package com.nakamura196.kotenocr.ui.screens

import java.util.Locale

/**
 * Pure logic behind the camera's tap-to-focus, pinch-to-zoom and torch controls
 * (iOS `CameraView.handleTapFocus` / `handlePinchZoom`). Kept free of Android types so the JVM can test it.
 */
object CameraControls {
    /** iOS caps the pinch zoom at 10x even when the device could go further. */
    const val MAX_ZOOM_CAP = 10f
    const val MIN_ZOOM = 1f

    /** Delay before the zoom label disappears after the fingers lift (iOS: 1.5 s). */
    const val ZOOM_LABEL_HIDE_DELAY_MS = 1500L

    /** The indicator starts 1.3x large, shrinks to 1x, stays about a second, then fades. */
    const val FOCUS_START_SCALE = 1.3f
    const val FOCUS_SHRINK_MS = 300
    const val FOCUS_HOLD_MS = 500L
    const val FOCUS_FADE_MS = 500

    /** Highest ratio the pinch may reach: the device limit, but never above 10x (and never below 1x). */
    fun maxZoom(deviceMax: Float): Float = minOf(deviceMax, MAX_ZOOM_CAP).coerceAtLeast(MIN_ZOOM)

    /** Zoom ratio for a pinch: start ratio x pinch scale, clamped to 1x .. min(device max, 10x). */
    fun targetZoom(startRatio: Float, pinchScale: Float, deviceMax: Float): Float {
        val raw = startRatio * pinchScale
        if (raw.isNaN()) return startRatio.coerceIn(MIN_ZOOM, maxZoom(deviceMax))
        return raw.coerceIn(MIN_ZOOM, maxZoom(deviceMax))
    }

    /** Text such as "2.3x". */
    fun zoomLabel(ratio: Float): String = String.format(Locale.US, "%.1fx", ratio)

    /** A plain rectangle in screen pixels (left/top inclusive, right/bottom exclusive). */
    data class Area(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        fun contains(x: Float, y: Float): Boolean = x >= left && x < right && y >= top && y < bottom
    }

    /** True when a tap landed on one of the buttons (shutter, light...) and so must not move the focus. */
    fun isOnButton(x: Float, y: Float, buttons: Collection<Area>): Boolean =
        buttons.any { it.contains(x, y) }
}
