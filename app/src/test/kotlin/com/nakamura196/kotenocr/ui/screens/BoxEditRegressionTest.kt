package com.nakamura196.kotenocr.ui.screens

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.nakamura196.kotenocr.ocr.Detection
import com.nakamura196.kotenocr.ui.theme.KotenColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Regression tests for the box editor and the result overlay (iOS `BoxEditRegressionTests`).
 * Each test names a problem a reader met: one colour for every line, a box that jumps or runs
 * away from the finger, no zoom while fixing boxes, numbers drawn inside the boxes.
 */
class BoxEditRegressionTest {

    // Three touching vertical lines on a page shown 1:1 (20 px wide, 300 px tall), grips at 1 px/dp.
    private val stage = PageStage(Size(100f, 400f), PageStage.fitRect(Size(100f, 400f), 100f, 400f), 100f, 400f)
    private val lines = listOf(Rect(20f, 50f, 40f, 350f), Rect(40f, 50f, 60f, 350f), Rect(60f, 50f, 80f, 350f))
    private val grips = BoxEditGeometry.Grips.forDensity(1f)
    private val page = Rect(0f, 0f, 100f, 400f)

    private fun near(a: Float, b: Float, eps: Float = 0.01f) = abs(a - b) <= eps
    private fun assertNear(expected: Offset, actual: Offset) =
        assertTrue("expected $expected, got $actual", near(expected.x, actual.x) && near(expected.y, actual.y))

    // region Colours

    @Test
    fun neighbouringLinesNeverShareAColour() {
        assertTrue("boxes must not all be one colour", KotenColor.boxPalette.toSet().size >= 3)
        for (i in 0 until 40) {
            assertNotEquals("line ${i + 1} and ${i + 2} share a colour", KotenColor.box(i), KotenColor.box(i + 1))
        }
        assertNotEquals(KotenColor.box(0), KotenColor.box(2))
        assertEquals(KotenColor.box(3), KotenColor.box(-1))
    }

    // endregion
    // region What a touch grabs

    @Test
    fun touchingAnUnselectedBoxDoesNotMoveIt() {
        // Middle line selected; a finger well inside the right-hand neighbour.
        assertEquals(BoxEditGeometry.Grab.PanOrSelect, BoxEditGeometry.grab(Offset(75f, 200f), stage, lines, 1, grips))
        // Nothing selected: touching a box never moves it either (it was selected and dragged at once).
        assertEquals(BoxEditGeometry.Grab.PanOrSelect, BoxEditGeometry.grab(Offset(50f, 200f), stage, lines, null, grips))
    }

    @Test
    fun sidesOfAThinLineResizeOnlyItsWidth() {
        assertEquals(BoxEditGeometry.Grab.Resize(1, BoxHandle.LEFT), BoxEditGeometry.grab(Offset(41f, 120f), stage, lines, 1, grips))
        assertEquals(BoxEditGeometry.Grab.Resize(1, BoxHandle.RIGHT), BoxEditGeometry.grab(Offset(62f, 300f), stage, lines, 1, grips))
        assertEquals(BoxEditGeometry.Grab.Resize(1, BoxHandle.TOP), BoxEditGeometry.grab(Offset(50f, 48f), stage, lines, 1, grips))
        assertEquals(BoxEditGeometry.Grab.Move(1), BoxEditGeometry.grab(Offset(50f, 200f), stage, lines, 1, grips))
        val r = BoxEditGeometry.resized(lines[1], BoxHandle.RIGHT, Offset(6f, 40f), page)
        assertEquals("dragging a side must not change the other dimension", lines[1].height, r.height, 0f)
        assertEquals(26f, r.width, 0f)
    }

    @Test
    fun cornersWinOverSidesNearThem() {
        assertEquals(BoxEditGeometry.Grab.Resize(1, BoxHandle.BOTTOM_RIGHT), BoxEditGeometry.grab(Offset(61f, 352f), stage, lines, 1, grips))
        assertEquals(BoxEditGeometry.Grab.Resize(1, BoxHandle.TOP_LEFT), BoxEditGeometry.grab(Offset(38f, 47f), stage, lines, 1, grips))
    }

    @Test
    fun gripsStayFingerSizedWhenZoomedIn() {
        // At 3x the selected line is 60 px wide; a side 15 px outside it still counts.
        val zoomed = stage.copy(zoom = 3f)
        val right = zoomed.toStage(lines[1]).right
        val y = zoomed.toStage(Offset(0f, 180f)).y
        assertEquals(BoxEditGeometry.Grab.Resize(1, BoxHandle.RIGHT), BoxEditGeometry.grab(Offset(right + 15f, y), zoomed, lines, 1, grips))
    }

    // endregion
    // region Dragging

    @Test
    fun resizingKeepsTheFingersOffsetFromTheCorner() {
        // The old code put the corner at the finger: a finger landing 10 px inside the corner made the
        // box jump 10 px smaller before it moved at all. Now only the translation counts.
        val start = Rect(20f, 50f, 40f, 350f)
        assertEquals(start, BoxEditGeometry.resized(start, BoxHandle.BOTTOM_RIGHT, Offset.Zero, page))
        val r = BoxEditGeometry.resized(start, BoxHandle.BOTTOM_RIGHT, Offset(5f, -7f), page)
        assertEquals(Rect(20f, 50f, 45f, 343f), r)
    }

    @Test
    fun resizingStopsAtTheMinimumSizeAndThePageEdge() {
        val start = Rect(20f, 50f, 40f, 350f)
        val shrunk = BoxEditGeometry.resized(start, BoxHandle.RIGHT, Offset(-100f, 0f), page)
        assertEquals(BoxEditGeometry.MIN_SIDE, shrunk.width, 0f)
        assertEquals(start.left, shrunk.left, 0f)
        val grown = BoxEditGeometry.resized(start, BoxHandle.TOP_LEFT, Offset(-500f, -500f), page)
        assertEquals(Rect(0f, 0f, 40f, 350f), grown)
    }

    @Test
    fun movingKeepsTheSizeAndStaysOnThePage() {
        val start = Rect(20f, 50f, 40f, 350f)
        assertEquals(Rect(25f, 60f, 45f, 360f), BoxEditGeometry.moved(start, Offset(5f, 10f), page))
        val clamped = BoxEditGeometry.moved(start, Offset(500f, 500f), page)
        assertEquals(Rect(80f, 100f, 100f, 400f), clamped)
    }

    private fun editor(): BoxEditorState = BoxEditorState(
        listOf(
            Detection(0, intArrayOf(20, 50, 40, 350), 0.9f, 1, "line_main", "a"),
            Detection(1, intArrayOf(40, 50, 60, 350), 0.9f, 1, "line_main", "b")
        ),
        imageWidth = 100,
        imageHeight = 400
    )

    @Test
    fun aDragTouchesOnlyThePreviewUntilTheFingerLifts() {
        val e = editor()
        val before = e.detections
        for (i in 0 until 30) e.preview = BoxEditorState.Preview(0, Rect(20f + i, 50f, 40f + i, 350f))
        assertTrue("a finger move must not change the boxes the screen lists", e.detections === before)
        assertFalse(e.canUndo)
        e.setRect(0, Rect(49f, 50f, 69f, 350f))
        assertEquals(listOf(49, 50, 69, 350), e.detections[0].box.toList())
        assertTrue(0 in e.pendingIndices)
        e.undo()
        assertEquals("one drag is one undo step", listOf(20, 50, 40, 350), e.detections[0].box.toList())
        assertFalse(e.canUndo)
    }

    @Test
    fun onlyTheSelectedBoxMovesThroughTheTouchHandler() {
        val e = editor()
        val touch = BoxEditTouch(e, { stage }, { PageZoom.State() }, {}, grips)
        // Nothing selected: dragging the first line moves nothing, lifting selects nothing new.
        touch.begin(Offset(30f, 200f))
        touch.drag(Offset(45f, 200f), Offset(15f, 0f))
        assertEquals(null, e.preview)
        touch.end(Offset(45f, 200f), moved = true)
        assertEquals(listOf(20, 50, 40, 350), e.detections[0].box.toList())
        // A tap selects it, then a drag inside moves it by exactly the finger's travel.
        touch.begin(Offset(30f, 200f)); touch.end(Offset(30f, 200f), moved = false)
        assertEquals(0, e.selectedIndex)
        touch.begin(Offset(30f, 200f))
        touch.drag(Offset(37f, 210f), Offset(7f, 10f))
        assertEquals(Rect(27f, 60f, 47f, 360f), e.preview?.rect)
        touch.end(Offset(37f, 210f), moved = true)
        assertEquals(null, e.preview)
        assertEquals(listOf(27, 60, 47, 360), e.detections[0].box.toList())
        // A second finger abandons the drag: nothing is committed.
        touch.begin(Offset(37f, 200f))
        touch.drag(Offset(47f, 200f), Offset(10f, 0f))
        touch.cancel()
        assertEquals(null, e.preview)
        assertEquals(listOf(27, 60, 47, 360), e.detections[0].box.toList())
    }

    // endregion
    // region Zoom while editing

    @Test
    fun theStageMapsBothWaysWhenZoomedAndPanned() {
        val fit = PageStage.fitRect(Size(300f, 400f), 1000f, 2000f)
        assertEquals(Rect(50f, 0f, 250f, 400f), fit)
        val st = PageStage(Size(300f, 400f), fit, 1000f, 2000f, zoom = 3f, offset = Offset(-40f, 25f))
        for (p in listOf(Offset(0f, 0f), Offset(500f, 1000f), Offset(1000f, 2000f), Offset(123f, 1789f))) {
            assertNear(p, st.toImage(st.toStage(p)))
        }
        // One screen pixel is fewer image pixels when zoomed: a drag moves the box less on the page.
        assertEquals(1000f / 200f / 3f, st.pixelsPerScreenPixel, 0.0001f)
    }

    @Test
    fun pinchingKeepsThePointUnderTheFingers() {
        val size = Size(300f, 400f)
        val fit = PageStage.fitRect(size, 1000f, 2000f)
        val centroid = Offset(200f, 120f)
        val before = PageStage(size, fit, 1000f, 2000f)
        val under = before.toImage(centroid)
        val z = PageZoom.transformed(PageZoom.State(), centroid, Offset.Zero, 2.5f, size)
        assertEquals(2.5f, z.zoom, 0f)
        val after = PageStage(size, fit, 1000f, 2000f, z.zoom, z.offset)
        assertNear(under, after.toImage(centroid))
    }

    @Test
    fun zoomStaysBetweenOneAndEightAndDoubleTapToggles() {
        val size = Size(300f, 400f)
        assertEquals(PageZoom.State(), PageZoom.transformed(PageZoom.State(), Offset(10f, 10f), Offset.Zero, 0.5f, size))
        assertEquals(PageZoom.MAX, PageZoom.transformed(PageZoom.State(), Offset(150f, 200f), Offset.Zero, 20f, size).zoom, 0f)
        val zoomed = PageZoom.doubleTapped(PageZoom.State(), Offset(150f, 200f), size)
        assertEquals(PageZoom.DOUBLE_TAP, zoomed.zoom, 0f)
        assertEquals(PageZoom.State(), PageZoom.doubleTapped(zoomed, Offset(10f, 10f), size))
        // At 1x a one-finger drag does not move the page; zoomed, the page cannot leave the frame.
        assertEquals(PageZoom.State(), PageZoom.panned(PageZoom.State(), Offset(50f, 50f), size))
        val panned = PageZoom.panned(zoomed, Offset(5000f, -5000f), size)
        assertEquals(size.width * (zoomed.zoom - 1f) / 2f, panned.offset.x, 0.01f)
        assertEquals(-size.height * (zoomed.zoom - 1f) / 2f, panned.offset.y, 0.01f)
    }

    @Test
    fun theEditorCanBeUsedWhileZoomed() {
        // Zoomed to 3x around the middle line, a drag of 30 screen px moves the box 10 image px.
        val e = editor()
        e.select(1)
        val zoom = PageZoom.State(3f, Offset.Zero)
        val st = stage.copy(zoom = zoom.zoom, offset = zoom.offset)
        val touch = BoxEditTouch(e, { st }, { zoom }, {}, grips)
        val inside = st.toStage(Offset(50f, 200f))
        touch.begin(inside)
        touch.drag(inside + Offset(30f, 0f), Offset(30f, 0f))
        touch.end(inside + Offset(30f, 0f), moved = true)
        assertEquals(listOf(50, 50, 70, 350), e.detections[1].box.toList())
    }

    /** Found on the emulator: the first tap of a double tap (zoom) on empty paper dropped the selection. */
    @Test
    fun doubleTapToZoomKeepsTheSelection() {
        val e = editor()
        e.select(1)
        val touch = BoxEditTouch(e, { stage }, { PageZoom.State() }, {}, grips)
        val paper = Offset(5f, 395f)
        // As pageGestures delivers it: the first tap as a tap, the second only as a double tap.
        touch.begin(paper); touch.end(paper, moved = false)
        touch.begin(paper); touch.cancel()
        touch.doubleTap(paper)
        assertEquals(1, e.selectedIndex)
    }

    // endregion
    // region Line-number tags

    /** A crowded page: 24 touching columns 12 px wide, from y = 40 (room above for the tags). */
    private val crowded = (0 until 24).map { i -> Rect(10f + i * 12f, 40f, 22f + i * 12f, 380f) }
    private val crowdedBounds = Rect(0f, 0f, 310f, 400f)

    @Test
    fun tagsSitOutsideTheirBoxesOnACrowdedPage() {
        val tags = LineTagLayout.frames(crowded, crowdedBounds, unit = 2f)
        assertEquals(crowded.size, tags.size)
        for ((i, tag) in tags.withIndex()) {
            assertFalse("tag ${i + 1} covers its own line", tag.overlaps(crowded[i]))
            assertTrue("tag ${i + 1} is off the image", tag.left >= 0f && tag.top >= 0f && tag.right <= 310f && tag.bottom <= 400f)
            for (j in tags.indices) if (j != i) {
                assertFalse("tags ${i + 1} and ${j + 1} overlap", tag.overlaps(tags[j]))
                assertFalse("tag ${i + 1} covers line ${j + 1}", tag.overlaps(crowded[j]))
            }
        }
    }

    @Test
    fun aThinSideNoteStillGetsALegibleTag() {
        val unit = 2f
        val regular = (0 until 6).map { i -> Rect(20f + i * 60f, 60f, 60f + i * 60f, 380f) }
        val note = Rect(62f, 100f, 66f, 200f)   // 4 px wide, between the first two lines
        val rects = regular + note
        val tags = LineTagLayout.frames(rects, Rect(0f, 0f, 420f, 400f), unit)
        assertTrue("side-note tag ${tags.last().height} px is too small", tags.last().height >= LineTagLayout.MINIMUM_LEGIBLE_HEIGHT * unit - 0.01f)
        assertFalse(tags.last().overlaps(note))
    }

    @Test
    fun withNoRoomAboveTheTagGoesInsideTheStartOfTheLine() {
        val line = Rect(10f, 0f, 40f, 300f)
        val tag = LineTagLayout.frames(listOf(line), Rect(0f, 0f, 100f, 300f), unit = 1f).single()
        assertTrue(tag.top >= line.top + LineTagLayout.GAP - 0.01f)
        assertTrue(tag.bottom < line.top + 30f)
    }

    @Test
    fun outlinesFollowThePagesLineThickness() {
        assertEquals(1.5f * 2f, LineTagLayout.boxLineWidth(listOf(Rect(0f, 0f, 200f, 400f)), 2f), 0f)
        assertEquals(0.5f * 2f, LineTagLayout.boxLineWidth(listOf(Rect(0f, 0f, 2f, 400f)), 2f), 0f)
        assertEquals(12f * 0.08f, LineTagLayout.boxLineWidth(crowded, 1f), 0.0001f)
    }

    // endregion
}
