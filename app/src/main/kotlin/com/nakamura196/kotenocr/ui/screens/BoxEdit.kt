package com.nakamura196.kotenocr.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PanToolAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nakamura196.kotenocr.R
import com.nakamura196.kotenocr.ocr.Detection
import com.nakamura196.kotenocr.ui.theme.KotenColor
import com.nakamura196.kotenocr.ui.theme.KotenGlassButton
import com.nakamura196.kotenocr.ui.theme.KotenProminentButton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Box editing tools (`BoxEditor.Tool` in `BoxEditor.swift`).
 */
enum class BoxEditTool {
    SELECT,
    ADD,
    SPLIT,
    MERGE
}

/**
 * State of the box editor (`BoxEditor.swift` + `BoxEditModel`). The geometry rules live in
 * [BoxEditGeometry]; this class holds the boxes, the selection, the tool and the undo stack.
 */
class BoxEditorState(
    initialDetections: List<Detection>,
    val imageWidth: Int,
    val imageHeight: Int
) {
    var detections by mutableStateOf(initialDetections)
        private set

    var selectedIndex by mutableStateOf<Int?>(null)
        private set

    var tool by mutableStateOf(BoxEditTool.SELECT)
        private set

    var pendingIndices by mutableStateOf<Set<Int>>(emptySet())
        private set

    /** A box shown at a temporary position while a finger drags it ([index] null = a box being drawn). */
    data class Preview(val index: Int?, val rect: Rect)

    /**
     * The box under the finger. Only the edit canvas reads it, in its draw phase, so a finger move
     * redraws the boxes and nothing else; [detections] changes once, when the finger lifts
     * (iOS kept this in the screen's model and rebuilt the whole screen on every move).
     */
    var preview by mutableStateOf<Preview?>(null)

    private data class Snapshot(
        val detections: List<Detection>,
        val selectedIndex: Int?,
        val pendingIndices: Set<Int>
    )

    private val undoStack = mutableListOf<Snapshot>()

    val canUndo: Boolean
        get() = undoStack.isNotEmpty()

    val canDelete: Boolean
        get() = selectedIndex != null

    val hasPending: Boolean
        get() = pendingIndices.isNotEmpty()

    /** The whole image, in image pixels. */
    val bounds: Rect
        get() = Rect(0f, 0f, imageWidth.toFloat(), imageHeight.toFloat())

    /** Box of every line, in image pixels. */
    val rects: List<Rect>
        get() = detections.map { boxRect(it.box) }

    private fun pushUndo() {
        undoStack.add(
            Snapshot(
                detections = detections.map { it.copy(box = it.box.clone()) },
                selectedIndex = selectedIndex,
                pendingIndices = pendingIndices.toSet()
            )
        )
        if (undoStack.size > 30) {
            undoStack.removeAt(0)
        }
    }

    fun selectTool(newTool: BoxEditTool) {
        tool = newTool
    }

    fun select(index: Int?) {
        selectedIndex = if (index != null && index in detections.indices) index else null
    }

    fun undo() {
        val last = undoStack.removeLastOrNull() ?: return
        preview = null
        detections = last.detections
        selectedIndex = last.selectedIndex
        pendingIndices = last.pendingIndices
    }

    fun deleteSelected() {
        val idx = selectedIndex ?: return
        if (idx !in detections.indices) return
        pushUndo()
        val updated = detections.toMutableList()
        updated.removeAt(idx)
        detections = reindex(updated)
        pendingIndices = pendingIndices.mapNotNull { p ->
            when {
                p < idx -> p
                p == idx -> null
                else -> p - 1
            }
        }.toSet()
        selectedIndex = null
    }

    /** Commits a moved or resized box (one undo step per drag). */
    fun setRect(index: Int, rect: Rect) {
        if (index !in detections.indices) return
        if (boxRect(detections[index].box) == rect) return
        pushUndo()
        updateBox(index, rect)
    }

    private fun updateBox(index: Int, rect: Rect) {
        val box = intArrayOf(
            rect.left.roundToInt().coerceIn(0, imageWidth),
            rect.top.roundToInt().coerceIn(0, imageHeight),
            rect.right.roundToInt().coerceIn(0, imageWidth),
            rect.bottom.roundToInt().coerceIn(0, imageHeight)
        )
        if (box[2] <= box[0] || box[3] <= box[1]) return
        val updated = detections.toMutableList()
        val cur = updated[index].copy(box = box)
        cur.markReaderConfirmed()
        updated[index] = cur
        detections = updated
        pendingIndices = pendingIndices + index
    }

    fun addBox(from: Offset, to: Offset) {
        val x1 = min(from.x, to.x).coerceIn(0f, imageWidth.toFloat()).roundToInt()
        val y1 = min(from.y, to.y).coerceIn(0f, imageHeight.toFloat()).roundToInt()
        val x2 = max(from.x, to.x).coerceIn(0f, imageWidth.toFloat()).roundToInt()
        val y2 = max(from.y, to.y).coerceIn(0f, imageHeight.toFloat()).roundToInt()
        if (x2 - x1 < 12 || y2 - y1 < 12) return

        pushUndo()
        val newIdx = detections.size
        val newDet = Detection(
            id = newIdx,
            box = intArrayOf(x1, y1, x2, y2),
            score = 1.0f,
            classIndex = 1,
            className = "line_main",
            text = ""
        )
        detections = detections + newDet
        selectedIndex = newIdx
        pendingIndices = pendingIndices + newIdx
        tool = BoxEditTool.SELECT
    }

    fun splitAt(point: Offset) {
        val idx = hitBox(point) ?: return
        val det = detections[idx]
        val r = boxRect(det.box)
        val minPart = 12f
        val isVertical = r.height >= r.width
        val firstBox: IntArray
        val secondBox: IntArray
        if (isVertical) {
            if (point.y - r.top < minPart || r.bottom - point.y < minPart) return
            val cutY = point.y.roundToInt()
            firstBox = intArrayOf(det.box[0], det.box[1], det.box[2], cutY)
            secondBox = intArrayOf(det.box[0], cutY, det.box[2], det.box[3])
        } else {
            if (point.x - r.left < minPart || r.right - point.x < minPart) return
            val cutX = point.x.roundToInt()
            firstBox = intArrayOf(det.box[0], det.box[1], cutX, det.box[3])
            secondBox = intArrayOf(cutX, det.box[1], det.box[2], det.box[3])
        }

        pushUndo()
        val d1 = det.copy(box = firstBox, text = "").also { it.markReaderConfirmed() }
        val d2 = det.copy(box = secondBox, text = "").also { it.markReaderConfirmed() }
        val updated = detections.toMutableList()
        updated[idx] = d1
        updated.add(idx + 1, d2)
        detections = reindex(updated)

        pendingIndices = pendingIndices.map { p -> if (p > idx) p + 1 else p }.toSet() + setOf(idx, idx + 1)
        selectedIndex = idx
        tool = BoxEditTool.SELECT
    }

    fun tapInMergeMode(point: Offset) {
        val tapped = hitBox(point) ?: return
        val first = selectedIndex
        if (first == null || first == tapped || first !in detections.indices) {
            selectedIndex = tapped
            return
        }

        pushUndo()
        val a = detections[first]
        val b = detections[tapped]
        val mergedBox = intArrayOf(
            min(a.box[0], b.box[0]),
            min(a.box[1], b.box[1]),
            max(a.box[2], b.box[2]),
            max(a.box[3], b.box[3])
        )
        val keepIdx = min(first, tapped)
        val dropIdx = max(first, tapped)
        val merged = a.copy(box = mergedBox, text = "").also { it.markReaderConfirmed() }

        val updated = detections.toMutableList()
        updated[keepIdx] = merged
        updated.removeAt(dropIdx)
        detections = reindex(updated)

        pendingIndices = pendingIndices.mapNotNull { p ->
            when {
                p == dropIdx -> null
                p > dropIdx -> p - 1
                else -> p
            }
        }.toSet() + keepIdx
        selectedIndex = keepIdx
        tool = BoxEditTool.SELECT
    }

    fun applyRecognizedPending(recognized: List<Detection>, orderedAll: List<Detection>) {
        detections = reindex(orderedAll)
        pendingIndices = emptySet()
    }

    /** The smallest box under an image point, or null. */
    fun hitBox(point: Offset): Int? = BoxEditGeometry.hitBox(point, rects)

    private fun reindex(list: List<Detection>): List<Detection> =
        list.mapIndexed { i, d -> d.copy(id = i) }

    companion object {
        fun boxRect(box: IntArray): Rect =
            if (box.size >= 4) Rect(box[0].toFloat(), box[1].toFloat(), box[2].toFloat(), box[3].toFloat()) else Rect.Zero
    }
}

/**
 * Turns one-finger touches into edits (`BoxEditGestureLayer` in iOS). Every drag applies the
 * translation since the finger landed to the box as it was then, so a box never runs ahead of or
 * behind the finger. Only the selected box moves or resizes; touching another box selects it when
 * the finger lifts.
 */
class BoxEditTouch(
    private val editor: BoxEditorState,
    private val stage: () -> PageStage,
    private val zoom: () -> PageZoom.State,
    private val onZoom: (PageZoom.State) -> Unit,
    private val grips: BoxEditGeometry.Grips
) : PageTouch {
    private sealed interface Kind {
        data class Move(val index: Int, val start: Rect) : Kind
        data class Resize(val index: Int, val handle: BoxHandle, val start: Rect) : Kind
        data class Draw(val origin: Offset) : Kind
        /** Pans when zoomed; a touch that does not move selects the box under it. */
        data class Pan(val start: PageZoom.State) : Kind
        data object Tap : Kind
    }

    private var kind: Kind? = null
    private var startPoint = Offset.Zero
    /** The selection before the last tap: a double tap zooms, so its first tap must not change it. */
    private var beforeLastTap: Int? = null

    override fun begin(at: Offset) {
        val st = stage()
        startPoint = st.toImage(at)
        val rects = editor.rects
        kind = when (editor.tool) {
            BoxEditTool.ADD -> Kind.Draw(startPoint)
            BoxEditTool.SPLIT, BoxEditTool.MERGE -> Kind.Tap
            BoxEditTool.SELECT -> when (val g = BoxEditGeometry.grab(at, st, rects, editor.selectedIndex, grips)) {
                is BoxEditGeometry.Grab.Resize -> Kind.Resize(g.index, g.handle, rects[g.index])
                is BoxEditGeometry.Grab.Move -> Kind.Move(g.index, rects[g.index])
                BoxEditGeometry.Grab.PanOrSelect -> Kind.Pan(zoom())
            }
        }
    }

    override fun drag(at: Offset, translation: Offset) {
        val st = stage()
        val px = translation * st.pixelsPerScreenPixel
        when (val k = kind) {
            is Kind.Move -> editor.preview = BoxEditorState.Preview(k.index, BoxEditGeometry.moved(k.start, px, editor.bounds))
            is Kind.Resize -> editor.preview = BoxEditorState.Preview(k.index, BoxEditGeometry.resized(k.start, k.handle, px, editor.bounds))
            is Kind.Draw -> {
                val end = st.toImage(at)
                val raw = Rect(min(k.origin.x, end.x), min(k.origin.y, end.y), max(k.origin.x, end.x), max(k.origin.y, end.y))
                    .intersect(editor.bounds)
                editor.preview = if (raw.width > 0f && raw.height > 0f) BoxEditorState.Preview(null, raw) else null
            }
            is Kind.Pan -> if (k.start.zoom > PageZoom.MIN) onZoom(PageZoom.panned(k.start, translation, st.containerSize))
            Kind.Tap, null -> Unit
        }
    }

    override fun end(at: Offset, moved: Boolean) {
        val k = kind
        val p = editor.preview
        kind = null
        editor.preview = null
        if (!moved) beforeLastTap = editor.selectedIndex
        when (k) {
            is Kind.Move, is Kind.Resize -> {
                val index = if (k is Kind.Move) k.index else (k as Kind.Resize).index
                if (moved && p != null && p.index == index) {
                    editor.setRect(index, p.rect)
                } else if (!moved) {
                    // A grip reaches a little outside the selected box: a tap there means "choose the
                    // neighbour", not "resize". A tap inside the selected box keeps it selected.
                    val sel = editor.selectedIndex
                    val inside = sel != null && sel in editor.detections.indices &&
                        BoxEditorState.boxRect(editor.detections[sel].box).contains(startPoint)
                    if (!inside) editor.select(editor.hitBox(startPoint))
                }
            }
            is Kind.Draw -> if (moved) {
                BoxEditGeometry.drawn(k.origin, stage().toImage(at), editor.bounds)?.let {
                    editor.addBox(it.topLeft, it.bottomRight)
                }
            }
            is Kind.Pan -> if (!moved) editor.select(editor.hitBox(startPoint))
            Kind.Tap -> if (!moved) {
                when (editor.tool) {
                    BoxEditTool.SPLIT -> if (editor.hitBox(startPoint) != null) editor.splitAt(startPoint) else editor.select(null)
                    BoxEditTool.MERGE -> editor.tapInMergeMode(startPoint)
                    else -> editor.select(editor.hitBox(startPoint))
                }
            }
            null -> Unit
        }
    }

    override fun cancel() {
        kind = null
        editor.preview = null
    }

    override fun doubleTap(at: Offset) {
        if (editor.tool == BoxEditTool.SELECT) editor.select(beforeLastTap)
    }
}

/**
 * Draws the boxes being edited (`BoxEditCanvasDrawing` in iOS): each line in its palette colour,
 * the selected one over a white under-stroke with grips on its sides and corners. Put it inside the
 * zoomed layer; [zoom] divides the strokes and grips so they keep their size on screen.
 */
@Composable
fun BoxEditDrawing(
    editor: BoxEditorState,
    fit: Rect,
    zoom: () -> Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (editor.imageWidth <= 0) return@Canvas
        val s = fit.width / editor.imageWidth
        val z = max(zoom(), 0.01f)
        val preview = editor.preview
        val dets = editor.detections
        val selected = editor.selectedIndex
        val showGrips = editor.tool == BoxEditTool.SELECT
        fun screen(r: Rect) = Rect(fit.left + r.left * s, fit.top + r.top * s, fit.left + r.right * s, fit.top + r.bottom * s)
        fun rectOf(i: Int): Rect =
            if (preview != null && preview.index == i) preview.rect else BoxEditorState.boxRect(dets[i].box)
        val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx() / z, 3.dp.toPx() / z), 0f)

        for (i in dets.indices) {
            if (i == selected) continue
            val r = screen(rectOf(i))
            val color = KotenColor.box(i)
            val pending = i in editor.pendingIndices
            drawRect(color.copy(alpha = if (pending) 0.16f else 0.06f), r.topLeft, r.size)
            drawRect(
                color.copy(alpha = 0.9f), r.topLeft, r.size,
                style = Stroke(width = 1.5.dp.toPx() / z, pathEffect = if (pending) dash else null)
            )
        }
        if (selected != null && selected in dets.indices) {
            val r = screen(rectOf(selected))
            val color = KotenColor.box(selected)
            drawRect(color.copy(alpha = 0.22f), r.topLeft, r.size)
            // White under-stroke keeps the selected edge visible on dark ink and on a same-colour neighbour.
            drawRect(Color.White.copy(alpha = 0.9f), r.topLeft, r.size, style = Stroke(width = 5.dp.toPx() / z))
            drawRect(color, r.topLeft, r.size, style = Stroke(width = 3.dp.toPx() / z))
            if (showGrips) {
                val long = 22.dp.toPx() / z
                val short = 7.dp.toPx() / z
                for (h in BoxHandle.sides) {
                    val p = BoxEditGeometry.point(h, r)
                    val horizontal = h == BoxHandle.TOP || h == BoxHandle.BOTTOM
                    val size = if (horizontal) Size(long, short) else Size(short, long)
                    val tl = Offset(p.x - size.width / 2f, p.y - size.height / 2f)
                    drawRoundRect(color, tl, size, CornerRadius(short / 2f))
                    drawRoundRect(Color.White, tl, size, CornerRadius(short / 2f), style = Stroke(width = 1.5.dp.toPx() / z))
                }
                val radius = 8.dp.toPx() / z
                for (h in BoxHandle.corners) {
                    val p = BoxEditGeometry.point(h, r)
                    drawCircle(color, radius, p)
                    drawCircle(Color.White, radius, p, style = Stroke(width = 2.dp.toPx() / z))
                }
            }
        }
        if (preview != null && preview.index == null) {
            val r = screen(preview.rect)
            val color = KotenColor.box(dets.size)
            drawRect(color.copy(alpha = 0.2f), r.topLeft, r.size)
            drawRect(color, r.topLeft, r.size, style = Stroke(width = 2.dp.toPx() / z, pathEffect = dash))
        }
    }
}

/**
 * Bottom controls panel for the Box Editor (`BoxEditControls` in `BoxEditView.swift`).
 */
@Composable
fun BoxEditControls(
    editor: BoxEditorState,
    isRereading: Boolean,
    onReread: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit
) {
    val hintText = when (editor.tool) {
        BoxEditTool.SELECT -> stringResource(R.string.box_hint_select)
        BoxEditTool.ADD -> stringResource(R.string.box_hint_add)
        BoxEditTool.SPLIT -> stringResource(R.string.box_hint_split)
        BoxEditTool.MERGE -> stringResource(R.string.box_hint_merge)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
        color = KotenColor.washiCard,
        border = BorderStroke(1.dp, KotenColor.rule),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = hintText,
                fontSize = 12.sp,
                color = KotenColor.inkSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // 4 tool buttons: Select, Add, Split, Merge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ToolButton(
                    icon = Icons.Default.PanToolAlt,
                    label = stringResource(R.string.box_tool_select),
                    selected = editor.tool == BoxEditTool.SELECT,
                    onClick = { editor.selectTool(BoxEditTool.SELECT) },
                    modifier = Modifier.weight(1f)
                )
                ToolButton(
                    icon = Icons.Default.AddBox,
                    label = stringResource(R.string.box_tool_add),
                    selected = editor.tool == BoxEditTool.ADD,
                    onClick = { editor.selectTool(BoxEditTool.ADD) },
                    modifier = Modifier.weight(1f)
                )
                ToolButton(
                    icon = Icons.Default.ContentCut,
                    label = stringResource(R.string.box_tool_split),
                    selected = editor.tool == BoxEditTool.SPLIT,
                    onClick = { editor.selectTool(BoxEditTool.SPLIT) },
                    modifier = Modifier.weight(1f)
                )
                ToolButton(
                    icon = Icons.Default.CallMerge,
                    label = stringResource(R.string.box_tool_merge),
                    selected = editor.tool == BoxEditTool.MERGE,
                    onClick = { editor.selectTool(BoxEditTool.MERGE) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Action row: Undo, Delete, Re-read (with pending count), Cancel, Done
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KotenGlassButton(
                    onClick = { editor.undo() },
                    enabled = editor.canUndo && !isRereading,
                    isCircle = true,
                    modifier = Modifier.size(40.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = stringResource(R.string.box_edit_undo),
                        modifier = Modifier.size(18.dp)
                    )
                }

                KotenGlassButton(
                    onClick = { editor.deleteSelected() },
                    enabled = editor.canDelete && !isRereading,
                    isCircle = true,
                    modifier = Modifier.size(40.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.box_edit_delete),
                        tint = if (editor.canDelete) KotenColor.shu else KotenColor.inkSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                KotenGlassButton(
                    onClick = onReread,
                    enabled = editor.hasPending && !isRereading,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    if (isRereading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = KotenColor.shu,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (editor.pendingIndices.isNotEmpty()) {
                            "${stringResource(R.string.box_edit_reread)} (${editor.pendingIndices.size})"
                        } else {
                            stringResource(R.string.box_edit_reread)
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                KotenGlassButton(
                    onClick = onCancel,
                    enabled = !isRereading,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                KotenProminentButton(
                    onClick = onDone,
                    enabled = !isRereading,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.box_edit_done),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) KotenColor.shuFill else KotenColor.mount,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) KotenColor.shuFill else KotenColor.rule
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) Color.White else KotenColor.ink,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) Color.White else KotenColor.ink,
                maxLines = 1
            )
        }
    }
}
