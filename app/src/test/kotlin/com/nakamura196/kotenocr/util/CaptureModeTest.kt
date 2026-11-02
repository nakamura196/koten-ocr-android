package com.nakamura196.kotenocr.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression tests for the "last way of taking a page" memory (iOS `CaptureMode`). */
class CaptureModeTest {

    private fun store(initial: String? = null): Pair<CaptureModeStore, () -> String?> {
        var value = initial
        return CaptureModeStore({ value }, { value = it }) to { value }
    }

    @Test
    fun firstLaunchIsTheNormalCamera() {
        assertEquals(CaptureMode.CAMERA, store().first.load())
    }

    @Test
    fun unknownStoredValueFallsBackToCamera() {
        assertEquals(CaptureMode.CAMERA, store("hologram").first.load())
    }

    @Test
    fun savedModeIsReadBack() {
        val (s, raw) = store()
        s.save(CaptureMode.SCAN)
        assertEquals("scan", raw())
        assertEquals(CaptureMode.SCAN, s.load())
        s.save(CaptureMode.CAMERA)
        assertEquals(CaptureMode.CAMERA, s.load())
    }

    @Test
    fun storedKeyMatchesIos() {
        assertEquals("lastCaptureMode", CaptureMode.PREF_KEY)
    }

    @Test
    fun scannerOpensByItselfOnlyAfterScannerUse() {
        assertTrue(CaptureMode.shouldAutoOpenScanner(CaptureMode.SCAN, true, false))
        assertFalse(CaptureMode.shouldAutoOpenScanner(CaptureMode.CAMERA, true, false))
    }

    @Test
    fun scannerDoesNotAutoOpenWhenUnsupportedOrAlreadyHandled() {
        assertFalse(CaptureMode.shouldAutoOpenScanner(CaptureMode.SCAN, false, false))
        assertFalse(CaptureMode.shouldAutoOpenScanner(CaptureMode.SCAN, true, true))
    }
}
