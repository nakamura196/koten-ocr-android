package com.nakamura196.kotenocr.ui.screens

import android.graphics.RectF
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlin.math.abs
import kotlin.math.hypot
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
 * Corner handles on a selected detection box (`BoxEditGeometry.Corner`).
 */
enum class BoxCorner {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}

/**
 * Pure state machine and geometry helpers for the interactive Box Editor
 * (`BoxEditor.swift` + `BoxEditGeometry.swift` v2.1 port).
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

    fun beginGestureEdit() {
        pushUndo()
    }

    fun moveBox(index: Int, startRect: RectF, dx: Float, dy: Float) {
        if (index !in detections.indices) return
        val w = startRect.width()
        val h = startRect.height()
        val nl = (startRect.left + dx).coerceIn(0f, max(0f, imageWidth - w))
        val nt = (startRect.top + dy).coerceIn(0f, max(0f, imageHeight - h))
        val nr = RectF(nl, nt, nl + w, nt + h)
        updateBox(index, nr)
    }

    fun resizeBox(index: Int, startRect: RectF, corner: BoxCorner, point: Offset) {
        if (index !in detections.indices) return
        val minSize = 12f
        val px = point.x.coerceIn(0f, imageWidth.toFloat())
        val py = point.y.coerceIn(0f, imageHeight.toFloat())
        val nr = RectF(startRect)
        when (corner) {
            BoxCorner.TOP_LEFT -> {
                nr.left = min(px, startRect.right - minSize)
                nr.top = min(py, startRect.bottom - minSize)
            }
            BoxCorner.TOP_RIGHT -> {
                nr.right = max(px, startRect.left + minSize)
                nr.top = min(py, startRect.bottom - minSize)
            }
            BoxCorner.BOTTOM_LEFT -> {
                nr.left = min(px, startRect.right - minSize)
                nr.bottom = max(py, startRect.top + minSize)
            }
            BoxCorner.BOTTOM_RIGHT -> {
                nr.right = max(px, startRect.left + minSize)
                nr.bottom = max(py, startRect.top + minSize)
            }
        }
        updateBox(index, nr)
    }

    private fun updateBox(index: Int, rect: RectF) {
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
        val r = det.rect
        val minPart = 12f
        val isVertical = r.height() >= r.width()
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

    fun hitBox(point: Offset): Int? {
        var bestIdx: Int? = null
        var bestArea = Float.MAX_VALUE
        for ((idx, det) in detections.withIndex()) {
            val r = det.rect
            if (point.x in r.left..r.right && point.y in r.top..r.bottom) {
                val area = r.width() * r.height()
                if (area < bestArea) {
                    bestArea = area
                    bestIdx = idx
                }
            }
        }
        return bestIdx
    }

    fun hitCorner(point: Offset, hitRadius: Float): Pair<Int, BoxCorner>? {
        val idx = selectedIndex ?: return null
        if (idx !in detections.indices) return null
        val r = detections[idx].rect
        val corners = listOf(
            BoxCorner.TOP_LEFT to Offset(r.left, r.top),
            BoxCorner.TOP_RIGHT to Offset(r.right, r.top),
            BoxCorner.BOTTOM_LEFT to Offset(r.left, r.bottom),
            BoxCorner.BOTTOM_RIGHT to Offset(r.right, r.bottom)
        )
        var bestCorner: BoxCorner? = null
        var bestDist = hitRadius
        for ((corner, pt) in corners) {
            val d = hypot(point.x - pt.x, point.y - pt.y)
            if (d <= bestDist) {
                bestDist = d
                bestCorner = corner
            }
        }
        return bestCorner?.let { idx to it }
    }

    private fun reindex(list: List<Detection>): List<Detection> =
        list.mapIndexed { i, d -> d.copy(id = i) }
}

/**
 * Interactive Box Edit overlay canvas (`BoxEditCanvas` in `BoxEditView.swift`).
 */
@Composable
fun BoxEditCanvas(
    editor: BoxEditorState,
    displayRect: RectF,
    modifier: Modifier = Modifier
) {
    val shuColor = KotenColor.shu
    val shuFill = KotenColor.shuFill
    var dragPreviewRect by remember { mutableStateOf<RectF?>(null) }

    fun toImagePoint(viewOffset: Offset): Offset {
        val sx = editor.imageWidth.toFloat() / displayRect.width().coerceAtLeast(1f)
        val sy = editor.imageHeight.toFloat() / displayRect.height().coerceAtLeast(1f)
        return Offset(
            (viewOffset.x - displayRect.left) * sx,
            (viewOffset.y - displayRect.top) * sy
        )
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(editor.tool, displayRect) {
                detectTapGestures { pos ->
                    val imgPt = toImagePoint(pos)
                    when (editor.tool) {
                        BoxEditTool.SELECT -> {
                            editor.select(editor.hitBox(imgPt))
                        }
                        BoxEditTool.SPLIT -> {
                            editor.splitAt(imgPt)
                        }
                        BoxEditTool.MERGE -> {
                            editor.tapInMergeMode(imgPt)
                        }
                        BoxEditTool.ADD -> Unit
                    }
                }
            }
            .pointerInput(editor.tool, displayRect) {
                var dragMode = 0 // 0=none, 1=move, 2=resize, 3=add
                var activeIndex = -1
                var activeCorner = BoxCorner.TOP_LEFT
                var startRect = RectF()
                var addStart = Offset.Zero
                var accumImgDelta = Offset.Zero

                detectDragGestures(
                    onDragStart = { pos ->
                        val sx = editor.imageWidth.toFloat() / displayRect.width().coerceAtLeast(1f)
                        val imgPt = toImagePoint(pos)
                        val hitR = 26.dp.toPx() * sx

                        when (editor.tool) {
                            BoxEditTool.ADD -> {
                                dragMode = 3
                                addStart = imgPt
                                dragPreviewRect = RectF(imgPt.x, imgPt.y, imgPt.x, imgPt.y)
                            }
                            BoxEditTool.SELECT -> {
                                val cornerHit = editor.hitCorner(imgPt, hitR)
                                if (cornerHit != null) {
                                    dragMode = 2
                                    activeIndex = cornerHit.first
                                    activeCorner = cornerHit.second
                                    startRect = RectF(editor.detections[activeIndex].rect)
                                    accumImgDelta = Offset.Zero
                                    editor.beginGestureEdit()
                                } else {
                                    val boxIdx = editor.hitBox(imgPt)
                                    if (boxIdx != null) {
                                        editor.select(boxIdx)
                                        dragMode = 1
                                        activeIndex = boxIdx
                                        startRect = RectF(editor.detections[boxIdx].rect)
                                        accumImgDelta = Offset.Zero
                                        editor.beginGestureEdit()
                                    } else {
                                        dragMode = 0
                                    }
                                }
                            }
                            else -> {
                                dragMode = 0
                            }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val sx = editor.imageWidth.toFloat() / displayRect.width().coerceAtLeast(1f)
                        val sy = editor.imageHeight.toFloat() / displayRect.height().coerceAtLeast(1f)
                        accumImgDelta += Offset(dragAmount.x * sx, dragAmount.y * sy)
                        val curImgPt = toImagePoint(change.position)

                        when (dragMode) {
                            1 -> {
                                editor.moveBox(
                                    index = activeIndex,
                                    startRect = startRect,
                                    dx = accumImgDelta.x,
                                    dy = accumImgDelta.y
                                )
                            }
                            2 -> {
                                editor.resizeBox(
                                    index = activeIndex,
                                    startRect = startRect,
                                    corner = activeCorner,
                                    point = curImgPt
                                )
                            }
                            3 -> {
                                dragPreviewRect = RectF(
                                    min(addStart.x, curImgPt.x),
                                    min(addStart.y, curImgPt.y),
                                    max(addStart.x, curImgPt.x),
                                    max(addStart.y, curImgPt.y)
                                )
                            }
                        }
                    },
                    onDragEnd = {
                        if (dragMode == 3) {
                            val r = dragPreviewRect
                            if (r != null) {
                                editor.addBox(Offset(r.left, r.top), Offset(r.right, r.bottom))
                            }
                            dragPreviewRect = null
                        }
                        dragMode = 0
                    },
                    onDragCancel = {
                        dragPreviewRect = null
                        dragMode = 0
                    }
                )
            }
    ) {
        val sx = displayRect.width() / editor.imageWidth.toFloat().coerceAtLeast(1f)
        val sy = displayRect.height() / editor.imageHeight.toFloat().coerceAtLeast(1f)
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)

        val numPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 10.dp.toPx()
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }

        editor.detections.forEachIndexed { idx, det ->
            val r = det.rect
            val left = displayRect.left + r.left * sx
            val top = displayRect.top + r.top * sy
            val w = r.width() * sx
            val h = r.height() * sy
            val isSelected = editor.selectedIndex == idx
            val isPending = idx in editor.pendingIndices

            drawRoundRect(
                color = shuFill.copy(alpha = if (isSelected) 0.28f else 0.12f),
                topLeft = Offset(left, top),
                size = Size(w, h),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
            drawRoundRect(
                color = shuColor,
                topLeft = Offset(left, top),
                size = Size(w, h),
                cornerRadius = CornerRadius(2.dp.toPx()),
                style = Stroke(
                    width = (if (isSelected) 2.5.dp else 1.5.dp).toPx(),
                    pathEffect = if (isPending) dashEffect else null
                )
            )

            // Number badge at top-left of box
            val badgeR = 9.dp.toPx()
            val bx = left + badgeR
            val by = (top - badgeR - 2.dp.toPx()).coerceAtLeast(displayRect.top + badgeR)
            drawCircle(color = shuFill, radius = badgeR, center = Offset(bx, by))
            drawContext.canvas.nativeCanvas.drawText(
                "${idx + 1}",
                bx,
                by + 3.5.dp.toPx(),
                numPaint
            )

            // 4 corner handles when selected in SELECT mode
            if (isSelected && editor.tool == BoxEditTool.SELECT) {
                val handlePts = listOf(
                    Offset(left, top),
                    Offset(left + w, top),
                    Offset(left, top + h),
                    Offset(left + w, top + h)
                )
                for (pt in handlePts) {
                    drawCircle(color = shuFill, radius = 7.dp.toPx(), center = pt)
                    drawCircle(
                        color = Color.White,
                        radius = 7.dp.toPx(),
                        center = pt,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }

        // Preview rectangle while dragging in ADD mode
        dragPreviewRect?.let { pr ->
            val left = displayRect.left + pr.left * sx
            val top = displayRect.top + pr.top * sy
            val w = pr.width() * sx
            val h = pr.height() * sy
            drawRoundRect(
                color = shuFill.copy(alpha = 0.22f),
                topLeft = Offset(left, top),
                size = Size(w, h),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
            drawRoundRect(
                color = shuColor,
                topLeft = Offset(left, top),
                size = Size(w, h),
                cornerRadius = CornerRadius(2.dp.toPx()),
                style = Stroke(width = 2.dp.toPx(), pathEffect = dashEffect)
            )
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
