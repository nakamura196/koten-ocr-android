package com.nakamura196.kotenocr.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Result screen layout, matched to iOS 2.1.2 (`selectedLineBar` / `stepSelection`). */
class ResultLayoutTest {
    @Test fun nextStartsAtTheFirstLineAndPreviousAtTheLast() {
        assertEquals(0, BoxEditGeometry.stepSelection(null, 1, 5))
        assertEquals(4, BoxEditGeometry.stepSelection(null, -1, 5))
    }

    @Test fun stepsWrapRoundAtEitherEnd() {
        assertEquals(3, BoxEditGeometry.stepSelection(2, 1, 5))
        assertEquals(0, BoxEditGeometry.stepSelection(4, 1, 5))
        assertEquals(4, BoxEditGeometry.stepSelection(0, -1, 5))
    }

    @Test fun noLinesMeansNothingChosen() {
        assertNull(BoxEditGeometry.stepSelection(null, 1, 0))
        assertNull(BoxEditGeometry.stepSelection(3, -1, 0))
    }

    @Test fun aStaleChoiceRestartsFromTheEnd() {
        // After a delete the chosen index can point past the last line.
        assertEquals(0, BoxEditGeometry.stepSelection(7, 1, 3))
        assertEquals(2, BoxEditGeometry.stepSelection(7, -1, 3))
    }
}
