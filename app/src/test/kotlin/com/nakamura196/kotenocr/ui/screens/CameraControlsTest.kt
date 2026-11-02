package com.nakamura196.kotenocr.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression tests for the camera's pinch zoom, zoom label and tap-on-button check (iOS parity). */
class CameraControlsTest {
    @Test fun pinchOutScalesFromTheStartRatio() {
        assertEquals(3f, CameraControls.targetZoom(1.5f, 2f, 8f), 0.001f)
    }

    @Test fun pinchInNeverGoesBelowOne() {
        assertEquals(1f, CameraControls.targetZoom(1.2f, 0.3f, 8f), 0.001f)
    }

    @Test fun zoomIsCappedAtTenEvenIfTheDeviceGoesFurther() {
        assertEquals(10f, CameraControls.targetZoom(4f, 5f, 30f), 0.001f)
    }

    @Test fun zoomIsCappedAtTheDeviceLimitWhenBelowTen() {
        assertEquals(4f, CameraControls.targetZoom(2f, 5f, 4f), 0.001f)
    }

    @Test fun deviceLimitBelowOneStillGivesOne() {
        assertEquals(1f, CameraControls.targetZoom(1f, 2f, 0.5f), 0.001f)
    }

    @Test fun nanScaleKeepsTheStartRatio() {
        assertEquals(2f, CameraControls.targetZoom(2f, Float.NaN, 8f), 0.001f)
    }

    @Test fun zoomLabelHasOneDecimalAndX() {
        assertEquals("2.3x", CameraControls.zoomLabel(2.3f))
        assertEquals("1.0x", CameraControls.zoomLabel(1f))
        assertEquals("10.0x", CameraControls.zoomLabel(10f))
    }

    @Test fun tapOnAButtonIsDetected() {
        val shutter = CameraControls.Area(100f, 800f, 200f, 900f)
        val light = CameraControls.Area(300f, 50f, 350f, 100f)
        val buttons = listOf(shutter, light)
        assertTrue(CameraControls.isOnButton(150f, 850f, buttons))
        assertTrue(CameraControls.isOnButton(300f, 50f, buttons))
        assertFalse(CameraControls.isOnButton(200f, 850f, buttons))
        assertFalse(CameraControls.isOnButton(500f, 500f, buttons))
        assertFalse(CameraControls.isOnButton(150f, 850f, emptyList()))
    }
}
