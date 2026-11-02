package com.nakamura196.kotenocr.ocr

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Detection / dedup steps that must match iOS (`koten-ocr-ios` KotenOCR/OCR), so the same page
 * gives the same boxes and lines on both platforms. See docs/notes/box-touch-and-ios-parity.md.
 */
class IosParityTest {

    @Test
    fun classicalBoxesGrowTwoPercentAboveAndBelow() {
        // 400 px tall column → 8 px more on each side; width unchanged.
        assertArrayEquals(intArrayOf(100, 42, 140, 458), RTMDetector.paddedBox(100f, 50f, 140f, 450f, 1000, 1000))
    }

    @Test
    fun classicalPaddingStaysInsideThePage() {
        assertArrayEquals(intArrayOf(10, 0, 50, 300), RTMDetector.paddedBox(10f, 2f, 50f, 298f, 300, 300))
        assertNull(RTMDetector.paddedBox(10f, 50f, 10f, 80f, 300, 300))
    }

    @Test
    fun smallBoxThatReadMoreIsNotTreatedAsADuplicate() {
        // The larger box misread most of the column; the smaller box inside it read the whole line.
        // iOS keeps both (similarity of the small box's text inside the large box's text is low).
        val large = Detection(0, intArrayOf(100, 50, 140, 450), 0.9f, 1, "line_main", "はにほ")
        val small = Detection(1, intArrayOf(102, 60, 138, 440), 0.8f, 1, "line_main", "いろはにほへとちりぬるを")
        assertEquals(2, LineTilt.dropSameLineDuplicates(listOf(large, small)).size)
    }

    @Test
    fun similarityIsNotSymmetric() {
        assertEquals(1.0, LineTilt.substringSimilarity("はにほ", "いろはにほへと"), 1e-9)
        assertEquals(3.0 / 7.0, LineTilt.substringSimilarity("いろはにほへと", "はにほ"), 1e-9)
    }

    @Test
    fun extensionBCharacterCountsAsOneCharacter() {
        // 𠀋 (U+2000B) is two UTF-16 units. One wrong character out of two → 0.5, as in iOS.
        assertEquals(0.5, LineTilt.substringSimilarity("𠀋あ", "いあ"), 1e-9)
    }
}
