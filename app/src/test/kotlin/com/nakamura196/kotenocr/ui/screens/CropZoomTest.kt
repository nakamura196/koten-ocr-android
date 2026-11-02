package com.nakamura196.kotenocr.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Crop screen pinch zoom: 8x cap like iOS, "2.3x" label, handles keep their size on screen. */
class CropZoomTest {
    @Test fun pinchReachesEightTimesLikeIos() {
        assertEquals(8f, CropZoom.MAX_SCALE, 0f)
        assertEquals(6f, CropZoom.nextScale(4f, 1.5f), 1e-4f) // above the old 5x cap
        assertEquals(8f, CropZoom.nextScale(6f, 2f), 0f)
        assertEquals(8f, CropZoom.nextScale(8f, 1.2f), 0f)
    }

    @Test fun pinchNeverGoesBelowOneTimes() {
        assertEquals(1f, CropZoom.nextScale(1.2f, 0.5f), 0f)
        assertEquals(2f, CropZoom.nextScale(2f, Float.NaN), 0f)
    }

    @Test fun labelAndThreshold() {
        assertEquals("2.3x", CropZoom.label(2.26f))
        assertEquals("8.0x", CropZoom.label(8f))
        assertFalse(CropZoom.isZoomed(1.005f))
        assertTrue(CropZoom.isZoomed(1.02f))
    }

    @Test fun panStaysInsideTheZoomedImage() {
        // 1000 px wide at 8x: the content is 8000 px, so it may slide 3500 px each way.
        assertEquals(3500f, CropZoom.maxPan(1000f, 8f), 1e-3f)
        assertEquals(3500f, CropZoom.clampPan(9999f, 1000f, 8f), 1e-3f)
        assertEquals(-3500f, CropZoom.clampPan(-9999f, 1000f, 8f), 1e-3f)
        assertEquals(0f, CropZoom.clampPan(50f, 1000f, 1f), 0f)
    }

    @Test fun handlesKeepTheirScreenSizeWhenZoomed() {
        // A 20 px handle drawn inside the 8x layer must be 2.5 px locally to look 20 px on screen.
        assertEquals(2.5f, CropZoom.onScreen(20f, 8f), 1e-4f)
        assertEquals(20f, CropZoom.onScreen(20f, 8f) * 8f, 1e-4f)
        assertEquals(20f, CropZoom.onScreen(20f, 1f), 0f)
    }

    @Test fun pinchKeepsThePointUnderTheFingers() {
        // Fingers 200 px right of centre, image not shifted, 2x -> 4x: the point under them
        // (local 100) must still be at 200 px, so the image shifts left by 200.
        val pan = CropZoom.panAfterPinch(pan = 0f, centroid = 200f, fingerMove = 0f, oldScale = 2f, newScale = 4f)
        assertEquals(-200f, pan, 1e-4f)
        assertEquals(100f, CropZoom.toLocal(200f, 0f, 4f, pan, 0f).first, 1e-4f)
        // Moving both fingers just slides the image.
        assertEquals(30f, CropZoom.panAfterPinch(0f, 50f, 30f, 3f, 3f), 1e-4f)
    }

    @Test fun screenPointMapsIntoTheZoomedLayer() {
        val (x, y) = CropZoom.toLocal(400f, -80f, 8f, 0f, 0f)
        assertEquals(50f, x, 1e-4f)
        assertEquals(-10f, y, 1e-4f)
    }
}
