package com.nakamura196.kotenocr.ui.screens

/**
 * Pure logic behind the crop screen's pinch zoom (iOS `CropView.pinchGesture` / zoom indicator).
 * Kept free of Android types so the JVM can test it.
 */
object CropZoom {
    const val MIN_SCALE = 1f

    /** iOS `CropView` caps the pinch at 8x. */
    const val MAX_SCALE = 8f

    /** Above this the view counts as zoomed: the label and the reset button show (iOS: > 1.01). */
    const val ZOOMED_THRESHOLD = 1.01f

    fun isZoomed(scale: Float): Boolean = scale > ZOOMED_THRESHOLD

    /** Scale after a pinch step: current x pinch factor, clamped to 1x .. 8x. */
    fun nextScale(current: Float, pinch: Float): Float {
        val raw = current * pinch
        if (raw.isNaN()) return current.coerceIn(MIN_SCALE, MAX_SCALE)
        return raw.coerceIn(MIN_SCALE, MAX_SCALE)
    }

    /**
     * Largest pan along one axis for a centre-pivoted scale: the scaled content
     * may slide until its edge meets the container edge.
     */
    fun maxPan(containerLength: Float, scale: Float): Float =
        (containerLength * (scale - 1f) * 0.5f).coerceAtLeast(0f)

    /** Pan offset kept inside [-maxPan, maxPan]. */
    fun clampPan(pan: Float, containerLength: Float, scale: Float): Float {
        val m = maxPan(containerLength, scale)
        return pan.coerceIn(-m, m)
    }

    /**
     * Handles are drawn inside the zoomed layer, so a size given in local pixels would grow with the zoom.
     * Dividing by the scale keeps them the same size on screen (and keeps the touch radius the same).
     */
    fun onScreen(localPx: Float, scale: Float): Float = localPx / scale.coerceAtLeast(MIN_SCALE)

    /**
     * Screen point (relative to the container centre) -> point in the layer's own coordinates,
     * also relative to the centre. The layer is scaled about its centre, then shifted by [pan].
     */
    fun toLocal(screenFromCentreX: Float, screenFromCentreY: Float, scale: Float, panX: Float, panY: Float): Pair<Float, Float> {
        val z = scale.coerceAtLeast(MIN_SCALE)
        return Pair((screenFromCentreX - panX) / z, (screenFromCentreY - panY) / z)
    }

    /**
     * Pan after a pinch step so the image point under the fingers stays under the fingers
     * ([centroid] is the fingers' midpoint relative to the container centre, one axis at a time).
     */
    fun panAfterPinch(
        pan: Float, centroid: Float, fingerMove: Float, oldScale: Float, newScale: Float
    ): Float {
        val ratio = newScale / oldScale.coerceAtLeast(MIN_SCALE)
        return centroid + fingerMove - ratio * (centroid - pan)
    }

    /** Text such as "2.3x" (same format as the camera). */
    fun label(scale: Float): String = CameraControls.zoomLabel(scale)
}
