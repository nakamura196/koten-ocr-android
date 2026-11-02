package com.nakamura196.kotenocr.ui.screens

import com.nakamura196.kotenocr.ui.screens.ScanProgress.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Processing screen, matched to iOS `ScanningView` / `ContentView.finishScan`. */
class ScanProgressTest {
    @Test fun beamReachesTheLeftEdgeWhenRecognitionEnds() {
        assertEquals(0f, ScanProgress.sweep(0f), 0f)
        assertEquals(0.5f, ScanProgress.sweep(0.45f), 1e-6f)
        assertEquals(1f, ScanProgress.sweep(0.9f), 0f)
        assertEquals(1f, ScanProgress.sweep(1.2f), 0f)
    }

    @Test fun statusFollowsProgressThenTheReveal() {
        assertEquals(Status.PREPARING, ScanProgress.status(0.1f, 0, 0))
        assertEquals(Status.FINDING_LINES, ScanProgress.status(0.3f, 0, 0))
        assertEquals(Status.READING_TEXT, ScanProgress.status(0.7f, 0, 0))
        // While lines appear, iOS keeps saying "reading"; "Read N lines" only at the end.
        assertEquals(Status.READING_TEXT, ScanProgress.status(1f, 12, 5))
        assertEquals(Status.DONE, ScanProgress.status(1f, 12, 12))
    }

    @Test fun barShowsProgressThenRevealedShare() {
        assertEquals(0.4f, ScanProgress.bar(0.4f, 0, 0), 0f)
        assertEquals(0.25f, ScanProgress.bar(1f, 8, 2), 1e-6f)
        assertEquals(1f, ScanProgress.bar(1f, 8, 8), 0f)
    }

    @Test fun titleShowsTheCountOnlyWhenAllLinesAreShown() {
        assertFalse(ScanProgress.titleShowsCount(0, 0))
        assertFalse(ScanProgress.titleShowsCount(10, 9))
        assertTrue(ScanProgress.titleShowsCount(10, 10))
    }

    @Test fun revealTakesAtMostEightTenthsOfASecond() {
        assertEquals(40L, ScanProgress.revealStepMs(5))
        assertEquals(40L, ScanProgress.revealStepMs(20))
        // 100 lines: the old Android floor of 12 ms made this 1.2 s; iOS caps it at 0.8 s.
        assertEquals(8L, ScanProgress.revealStepMs(100))
        assertTrue(ScanProgress.revealStepMs(300) * 300 <= 800)
        assertEquals(0L, ScanProgress.revealStepMs(0))
    }

    @Test fun listsTheNewestFourLines() {
        assertEquals(IntRange.EMPTY, ScanProgress.listedRange(10, 0))
        assertEquals(0 until 3, ScanProgress.listedRange(10, 3))
        assertEquals(6 until 10, ScanProgress.listedRange(10, 10))
        assertEquals(6 until 10, ScanProgress.listedRange(10, 99))
    }
}
