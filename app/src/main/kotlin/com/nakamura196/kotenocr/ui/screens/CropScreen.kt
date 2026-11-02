package com.nakamura196.kotenocr.ui.screens

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nakamura196.kotenocr.R
import com.nakamura196.kotenocr.data.CoachMark
import com.nakamura196.kotenocr.data.SettingsManager
import com.nakamura196.kotenocr.ui.theme.CoachBubble
import com.nakamura196.kotenocr.ui.theme.KotenColor
import com.nakamura196.kotenocr.ui.theme.KotenGlassButton
import com.nakamura196.kotenocr.ui.theme.KotenProminentButton
import com.nakamura196.kotenocr.ui.theme.WashiBackground
import com.nakamura196.kotenocr.ui.theme.minchoStyle
import com.nakamura196.kotenocr.util.ImageIntake
import com.nakamura196.kotenocr.util.PerspectiveCrop
import com.nakamura196.kotenocr.util.Quad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class RectHandle {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
    TOP, BOTTOM, LEFT, RIGHT,
    MOVE, NONE
}

/** Which part of the crop box a touch at [pos] grabs (all values in the same coordinates). */
private fun rectHandleAt(pos: Offset, left: Float, top: Float, right: Float, bottom: Float, hitR: Float): RectHandle = when {
    hypot(pos.x - left, pos.y - top) <= hitR -> RectHandle.TOP_LEFT
    hypot(pos.x - right, pos.y - top) <= hitR -> RectHandle.TOP_RIGHT
    hypot(pos.x - left, pos.y - bottom) <= hitR -> RectHandle.BOTTOM_LEFT
    hypot(pos.x - right, pos.y - bottom) <= hitR -> RectHandle.BOTTOM_RIGHT
    abs(pos.y - top) <= hitR && pos.x in left..right -> RectHandle.TOP
    abs(pos.y - bottom) <= hitR && pos.x in left..right -> RectHandle.BOTTOM
    abs(pos.x - left) <= hitR && pos.y in top..bottom -> RectHandle.LEFT
    abs(pos.x - right) <= hitR && pos.y in top..bottom -> RectHandle.RIGHT
    pos.x in left..right && pos.y in top..bottom -> RectHandle.MOVE
    else -> RectHandle.NONE
}

/**
 * Interactive Crop screen (`CropView.swift` + `QuadCropOverlay.swift` + `CropGeometry.swift` v2.1 port).
 * Supports:
 * - Rectangular crop with rule-of-thirds grid, edge/corner handles, and cumulative drag (no exponential drift)
 * - 4-corner perspective crop (`4点補正`, [Quad] homography warp via [PerspectiveCrop.rectify])
 * - Automatic page corner detection & rectification (`自動補正` / `元に戻す`)
 * - 90° clockwise rotation (`回転` button with text label) preserved on Skip (`スキップ`) and Re-crop (`再切り取り`)
 * - Pinch-to-zoom (1x..8x, iOS parity) with a "2.3x" label + Reset Zoom button
 */
@Composable
fun CropScreen(
    bitmap: Bitmap,
    settingsManager: SettingsManager,
    onCropped: (croppedBitmap: Bitmap, rotatedSourceBitmap: Bitmap) -> Unit,
    onSkip: (rotatedSourceBitmap: Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var currentBitmap by remember(bitmap) { mutableStateOf(bitmap) }
    var preCorrectionBitmap by remember(bitmap) { mutableStateOf<Bitmap?>(null) }
    var isQuadMode by remember { mutableStateOf(false) }
    var isDetectingQuad by remember { mutableStateOf(false) }

    // Normalized rectangle [0..1]
    var cropRect by remember(bitmap) {
        mutableStateOf(RectF(0f, 0f, 1f, 1f))
    }
    // Normalized 4-corner quad [0..1]
    var quad by remember(bitmap) {
        mutableStateOf(Quad(RectF(0f, 0f, 1f, 1f)))
    }

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    var showCornerCoach by remember {
        mutableStateOf(!settingsManager.hasSeenCoachMark(CoachMark.CROP_CORNER))
    }

    fun dismissCornerCoach() {
        if (!showCornerCoach) return
        settingsManager.markCoachMarkSeen(CoachMark.CROP_CORNER)
        showCornerCoach = false
    }

    LaunchedEffect(Unit) {
        if (showCornerCoach) {
            kotlinx.coroutines.delay(6_000)
            dismissCornerCoach()
        }
    }

    fun resetZoom() {
        zoomScale = 1f
        panOffset = Offset.Zero
    }

    fun rotateImage() {
        currentBitmap = ImageIntake.rotate90Clockwise(currentBitmap)
        preCorrectionBitmap = null
        cropRect = RectF(0f, 0f, 1f, 1f)
        quad = Quad(cropRect)
        resetZoom()
    }

    fun enterQuadMode() {
        resetZoom()
        val seeded = Quad(cropRect)
        quad = seeded
        isQuadMode = true
        val img = currentBitmap
        isDetectingQuad = true
        scope.launch {
            val found = withContext(Dispatchers.Default) {
                PerspectiveCrop.detectPage(img)
            }
            isDetectingQuad = false
            if (found != null && isQuadMode) {
                quad = PerspectiveCrop.adoptDetected(found, current = quad, seeded = seeded)
            }
        }
    }

    fun autoCorrect() {
        if (isDetectingQuad) return
        resetZoom()
        val img = currentBitmap
        isDetectingQuad = true
        scope.launch {
            val detected = withContext(Dispatchers.Default) {
                PerspectiveCrop.detectForCropScreen(img)
            }
            if (detected != null) {
                val warped = withContext(Dispatchers.Default) {
                    PerspectiveCrop.rectify(img, detected)
                }
                isDetectingQuad = false
                if (warped != null) {
                    preCorrectionBitmap = img
                    currentBitmap = warped
                    isQuadMode = false
                    cropRect = RectF(0f, 0f, 1f, 1f)
                    quad = Quad(cropRect)
                    return@launch
                }
            }
            isDetectingQuad = false
            // Fallback: switch to 4-corner mode so the user can drag the corners manually
            quad = Quad(cropRect)
            isQuadMode = true
        }
    }

    fun undoCorrection() {
        val original = preCorrectionBitmap ?: return
        currentBitmap = original
        preCorrectionBitmap = null
        cropRect = RectF(0f, 0f, 1f, 1f)
        quad = Quad(cropRect)
        resetZoom()
    }

    fun applyCrop() {
        val src = currentBitmap
        if (isQuadMode) {
            val warped = PerspectiveCrop.rectify(src, quad) ?: src
            onCropped(warped, src)
        } else {
            val x = (cropRect.left * src.width).roundToInt().coerceIn(0, src.width - 1)
            val y = (cropRect.top * src.height).roundToInt().coerceIn(0, src.height - 1)
            val w = (cropRect.width() * src.width).roundToInt().coerceIn(1, src.width - x)
            val h = (cropRect.height() * src.height).roundToInt().coerceIn(1, src.height - y)
            val cropped = Bitmap.createBitmap(src, x, y, w, h)
            onCropped(cropped, src)
        }
    }

    WashiBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top bar: Retake | Title | Zoom Reset + Rotate (with label)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KotenGlassButton(
                    onClick = onCancel,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = stringResource(R.string.ocr_error_retake),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = stringResource(R.string.crop_title),
                    style = minchoStyle(size = 18.sp, bold = true, color = KotenColor.ink),
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (CropZoom.isZoomed(zoomScale)) {
                        KotenGlassButton(
                            onClick = ::resetZoom,
                            isCircle = true,
                            modifier = Modifier.size(40.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomOutMap,
                                contentDescription = stringResource(R.string.crop_reset_zoom),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    KotenGlassButton(
                        onClick = ::rotateImage,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.RotateRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.crop_rotate),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Image & Crop Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp)
                    .background(KotenColor.docBackground, RoundedCornerShape(6.dp))
                    // Zoomed, the image would spill over the header and buttons; keep it inside the frame.
                    // At 1x it stays unclipped so the corner dots are not cut at the frame edge.
                    .then(if (CropZoom.isZoomed(zoomScale)) Modifier.clipToBounds() else Modifier)
                    .onSizeChanged { containerSize = it }
                    .pointerInput(showCornerCoach) {
                        if (!showCornerCoach) return@pointerInput
                        awaitPointerEventScope {
                            awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                            dismissCornerCoach()
                        }
                    }
                    // Zoom and pan (iOS `CropView`). Two fingers pinch and slide the image, zooming about
                    // the fingers. One finger slides the zoomed image only when it lands outside the crop
                    // box; on a dot, an edge or inside the box it is left to the box. (Before, the image
                    // took every one-finger drag once zoomed, so the box could not be resized.)
                    // Runs on the Initial pass so a drag taken here never also reaches the box.
                    .pointerInput(isQuadMode) {
                        if (isQuadMode) return@pointerInput
                        fun applyStep(zoom: Float, centroid: Offset, move: Offset) {
                            val old = zoomScale
                            val newScale = CropZoom.nextScale(old, zoom)
                            zoomScale = newScale
                            val w = containerSize.width.toFloat()
                            val h = containerSize.height.toFloat()
                            panOffset = if (!CropZoom.isZoomed(newScale)) Offset.Zero else Offset(
                                CropZoom.clampPan(CropZoom.panAfterPinch(panOffset.x, centroid.x - w / 2f, move.x, old, newScale), w, newScale),
                                CropZoom.clampPan(CropZoom.panAfterPinch(panOffset.y, centroid.y - h / 2f, move.y, old, newScale), h, newScale)
                            )
                        }
                        fun landsOnCropBox(p: Offset): Boolean {
                            val w = containerSize.width.toFloat()
                            val h = containerSize.height.toFloat()
                            val d = computeFittedRect(w, h, currentBitmap.width.toFloat(), currentBitmap.height.toFloat(), 0f)
                            val (lx, ly) = CropZoom.toLocal(p.x - w / 2f, p.y - h / 2f, zoomScale, panOffset.x, panOffset.y)
                            val local = Offset(lx + w / 2f, ly + h / 2f)
                            return rectHandleAt(
                                local,
                                d.left + cropRect.left * d.width(), d.top + cropRect.top * d.height(),
                                d.left + cropRect.right * d.width(), d.top + cropRect.bottom * d.height(),
                                CropZoom.onScreen(32.dp.toPx(), zoomScale)
                            ) != RectHandle.NONE
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            var oneFingerPans = CropZoom.isZoomed(zoomScale) && !landsOnCropBox(down.position)
                            var panning = false
                            var travel = Offset.Zero
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                if (pressed.size >= 2) {
                                    applyStep(event.calculateZoom(), event.calculateCentroid(useCurrent = false), event.calculatePan())
                                    event.changes.forEach { it.consume() }
                                    oneFingerPans = false
                                } else if (oneFingerPans) {
                                    val c = pressed[0]
                                    if (!panning) {
                                        travel += c.positionChange()
                                        panning = travel.getDistance() > viewConfiguration.touchSlop
                                    }
                                    if (panning) {
                                        applyStep(1f, c.position, c.positionChange())
                                        c.consume()
                                    }
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                val displayRect = remember(containerSize, currentBitmap) {
                    computeFittedRect(
                        containerW = containerSize.width.toFloat(),
                        containerH = containerSize.height.toFloat(),
                        imageW = currentBitmap.width.toFloat(),
                        imageH = currentBitmap.height.toFloat(),
                        inset = 0f
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoomScale
                            scaleY = zoomScale
                            translationX = panOffset.x
                            translationY = panOffset.y
                        }
                ) {
                  // Drags inside the zoomed layer are measured in its own (unscaled) pixels, so at 8x
                  // a dot only started moving after 8 times the usual finger travel. Scale the start
                  // threshold down so it begins after the same travel on screen at any zoom.
                  val baseVc = LocalViewConfiguration.current
                  val layerVc = remember(baseVc, zoomScale) {
                      object : ViewConfiguration by baseVc {
                          override val touchSlop: Float get() = CropZoom.onScreen(baseVc.touchSlop, zoomScale)
                      }
                  }
                  CompositionLocalProvider(LocalViewConfiguration provides layerVc) {
                    Image(
                        bitmap = currentBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )

                    if (displayRect.width() > 0f && displayRect.height() > 0f) {
                        if (isQuadMode) {
                            QuadCropCanvas(
                                quad = quad,
                                displayRect = displayRect,
                                zoom = zoomScale,
                                isDetecting = isDetectingQuad,
                                onQuadChanged = { newQuad ->
                                    if (showCornerCoach) {
                                        settingsManager.markCoachMarkSeen(CoachMark.CROP_CORNER)
                                        showCornerCoach = false
                                    }
                                    quad = newQuad
                                }
                            )
                        } else {
                            RectCropCanvas(
                                cropRect = cropRect,
                                displayRect = displayRect,
                                zoom = zoomScale,
                                onCropRectChanged = { newRect ->
                                    if (showCornerCoach) {
                                        settingsManager.markCoachMarkSeen(CoachMark.CROP_CORNER)
                                        showCornerCoach = false
                                    }
                                    cropRect = newRect
                                }
                            )
                        }
                    }
                  }
                }

                // Zoom level label at the top right, outside the zoomed layer (iOS `CropView`)
                if (CropZoom.isZoomed(zoomScale)) {
                    Text(
                        text = CropZoom.label(zoomScale),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // One-time corner coach mark (iOS: near the top-left corner, pointer up,
                // no hit testing, auto-dismiss after 6 s or on the first touch).
                if (showCornerCoach && displayRect.width() > 0f) {
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val cornerX = displayRect.left + cropRect.left * displayRect.width()
                    val cornerY = displayRect.top + cropRect.top * displayRect.height()
                    val margin = with(density) { 12.dp.toPx() }
                    val maxW = with(density) { 260.dp.toPx() }
                    CoachBubble(
                        text = stringResource(R.string.coach_crop_corner),
                        pointerUp = true,
                        pointerX = 0.18f,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .widthIn(max = with(density) { (containerSize.width - margin * 2).coerceAtMost(maxW).toDp() })
                            .offset {
                                IntOffset(
                                    x = (cornerX - with(density) { 20.dp.toPx() })
                                        .coerceIn(margin, (containerSize.width - maxW - margin).coerceAtLeast(margin))
                                        .roundToInt(),
                                    y = (cornerY + with(density) { 22.dp.toPx() }).roundToInt()
                                )
                            }
                    )
                }
            }

            // Hint under the image (iOS `crop_tip`)
            Text(
                text = stringResource(R.string.crop_tip),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = KotenColor.inkSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 26.dp, end = 26.dp, top = 12.dp)
            )

            // Mode picker (矩形 / 4点補正) + 自動補正 / 元に戻す
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
            ) {
                Surface(
                    modifier = Modifier.weight(1f, fill = false).widthIn(max = 260.dp),
                    shape = RoundedCornerShape(50),
                    color = KotenColor.mount.copy(alpha = 0.7f)
                ) {
                    Row(modifier = Modifier.padding(3.dp)) {
                        ModeSegmentButton(
                            title = stringResource(R.string.crop_mode_rect),
                            selected = !isQuadMode,
                            onClick = { isQuadMode = false },
                            modifier = Modifier.weight(1f)
                        )
                        ModeSegmentButton(
                            title = stringResource(R.string.crop_mode_quad),
                            selected = isQuadMode,
                            onClick = { if (!isQuadMode) enterQuadMode() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                val correcting = isDetectingQuad && !isQuadMode
                KotenGlassButton(
                    onClick = { if (preCorrectionBitmap != null) undoCorrection() else autoCorrect() },
                    enabled = !isDetectingQuad,
                    modifier = Modifier.height(40.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                ) {
                    if (correcting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = KotenColor.shu,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = stringResource(
                            if (preCorrectionBitmap != null) R.string.crop_undo_correct else R.string.crop_auto_correct
                        ),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            // Bottom glass toolbar: Skip ... Crop
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                shape = RoundedCornerShape(36.dp),
                color = KotenColor.washiCard.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, KotenColor.rule.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    KotenGlassButton(
                        onClick = { onSkip(currentBitmap) },
                        modifier = Modifier.height(50.dp),
                        contentPadding = PaddingValues(horizontal = 22.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.crop_skip),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    KotenProminentButton(
                        onClick = ::applyCrop,
                        enabled = !isQuadMode || quad.isConvex,
                        modifier = Modifier
                            .height(50.dp)
                            .widthIn(min = 120.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.crop_apply),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeSegmentButton(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = if (selected) KotenColor.washiCard else Color.Transparent,
        shadowElevation = if (selected) 2.dp else 0.dp
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) KotenColor.ink else KotenColor.inkSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp)
        )
    }
}

@Composable
private fun RectCropCanvas(
    cropRect: RectF,
    displayRect: RectF,
    zoom: Float,
    onCropRectChanged: (RectF) -> Unit
) {
    val shuColor = Color(0xFFE0684C)
    val shuFill = KotenColor.shuFill
    // pointerInput is keyed on displayRect only, so read the latest values through these.
    val latestRect by androidx.compose.runtime.rememberUpdatedState(cropRect)
    val latestOnChange by androidx.compose.runtime.rememberUpdatedState(onCropRectChanged)
    val latestZoom by androidx.compose.runtime.rememberUpdatedState(zoom)
    var activeHandle by remember { mutableStateOf(RectHandle.NONE) }
    var dragStartRect by remember { mutableStateOf(RectF()) }
    var totalDrag by remember { mutableStateOf(Offset.Zero) }

    // The handles sit near the screen edges; keep Android's edge-swipe Back from stealing them.
    val exclusion = run {
        val l = displayRect.left + cropRect.left * displayRect.width()
        val t = displayRect.top + cropRect.top * displayRect.height()
        val r = displayRect.left + cropRect.right * displayRect.width()
        val b = displayRect.top + cropRect.bottom * displayRect.height()
        val my = (t + b) / 2f
        rememberHandleGestureExclusion(
            listOf(Offset(l, t), Offset(l, my), Offset(l, b), Offset(r, t), Offset(r, my), Offset(r, b))
        )
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .then(exclusion)
            .pointerInput(displayRect) {
                detectDragGestures(
                    onDragStart = { pos ->
                        // Same reach on screen at any zoom (the canvas lives inside the zoomed layer).
                        val hitR = CropZoom.onScreen(32.dp.toPx(), latestZoom)
                        val left = displayRect.left + latestRect.left * displayRect.width()
                        val top = displayRect.top + latestRect.top * displayRect.height()
                        val right = displayRect.left + latestRect.right * displayRect.width()
                        val bottom = displayRect.top + latestRect.bottom * displayRect.height()

                        activeHandle = rectHandleAt(pos, left, top, right, bottom, hitR)
                        dragStartRect = RectF(latestRect)
                        totalDrag = Offset.Zero
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (activeHandle == RectHandle.NONE) return@detectDragGestures
                        totalDrag += dragAmount
                        val dx = totalDrag.x / displayRect.width().coerceAtLeast(1f)
                        val dy = totalDrag.y / displayRect.height().coerceAtLeast(1f)
                        val minSpan = 0.08f
                        val s = dragStartRect
                        val next = RectF(s)

                        when (activeHandle) {
                            RectHandle.TOP_LEFT -> {
                                next.left = (s.left + dx).coerceIn(0f, s.right - minSpan)
                                next.top = (s.top + dy).coerceIn(0f, s.bottom - minSpan)
                            }
                            RectHandle.TOP_RIGHT -> {
                                next.right = (s.right + dx).coerceIn(s.left + minSpan, 1f)
                                next.top = (s.top + dy).coerceIn(0f, s.bottom - minSpan)
                            }
                            RectHandle.BOTTOM_LEFT -> {
                                next.left = (s.left + dx).coerceIn(0f, s.right - minSpan)
                                next.bottom = (s.bottom + dy).coerceIn(s.top + minSpan, 1f)
                            }
                            RectHandle.BOTTOM_RIGHT -> {
                                next.right = (s.right + dx).coerceIn(s.left + minSpan, 1f)
                                next.bottom = (s.bottom + dy).coerceIn(s.top + minSpan, 1f)
                            }
                            RectHandle.TOP -> {
                                next.top = (s.top + dy).coerceIn(0f, s.bottom - minSpan)
                            }
                            RectHandle.BOTTOM -> {
                                next.bottom = (s.bottom + dy).coerceIn(s.top + minSpan, 1f)
                            }
                            RectHandle.LEFT -> {
                                next.left = (s.left + dx).coerceIn(0f, s.right - minSpan)
                            }
                            RectHandle.RIGHT -> {
                                next.right = (s.right + dx).coerceIn(s.left + minSpan, 1f)
                            }
                            RectHandle.MOVE -> {
                                val w = s.width()
                                val h = s.height()
                                val nl = (s.left + dx).coerceIn(0f, 1f - w)
                                val nt = (s.top + dy).coerceIn(0f, 1f - h)
                                next.set(nl, nt, nl + w, nt + h)
                            }
                            RectHandle.NONE -> Unit
                        }
                        latestOnChange(next)
                    },
                    onDragEnd = { activeHandle = RectHandle.NONE },
                    onDragCancel = { activeHandle = RectHandle.NONE }
                )
            }
    ) {
        val left = displayRect.left + cropRect.left * displayRect.width()
        val top = displayRect.top + cropRect.top * displayRect.height()
        val right = displayRect.left + cropRect.right * displayRect.width()
        val bottom = displayRect.top + cropRect.bottom * displayRect.height()
        val w = right - left
        val h = bottom - top

        fun z(px: Float) = CropZoom.onScreen(px, zoom)

        // Dim area outside crop rect
        val holePath = Path().apply {
            addRect(Rect(left, top, right, bottom))
        }
        clipPath(holePath, clipOp = ClipOp.Difference) {
            drawRect(
                color = Color.Black.copy(alpha = 0.55f),
                topLeft = Offset(displayRect.left, displayRect.top),
                size = Size(displayRect.width(), displayRect.height())
            )
        }

        // Crop border
        drawRect(
            color = Color.White,
            topLeft = Offset(left, top),
            size = Size(w, h),
            style = Stroke(width = z(2.dp.toPx()))
        )

        // Rule of thirds grid
        for (i in 1..2) {
            val gx = left + w * (i / 3f)
            val gy = top + h * (i / 3f)
            drawLine(
                color = Color.White.copy(alpha = 0.32f),
                start = Offset(gx, top),
                end = Offset(gx, bottom),
                strokeWidth = z(0.8.dp.toPx())
            )
            drawLine(
                color = Color.White.copy(alpha = 0.32f),
                start = Offset(left, gy),
                end = Offset(right, gy),
                strokeWidth = z(0.8.dp.toPx())
            )
        }

        // Corner handles: 朱 dot (20dp) with a white 3dp ring and a soft shadow (iOS `cornerHandle`)
        val corners = listOf(
            Offset(left, top),
            Offset(right, top),
            Offset(left, bottom),
            Offset(right, bottom)
        )
        val r = z(10.dp.toPx())
        for (pt in corners) {
            drawCircle(color = Color.Black.copy(alpha = 0.22f), radius = r + z(2.dp.toPx()), center = pt + Offset(0f, z(2.dp.toPx())))
            drawCircle(color = shuFill, radius = r, center = pt)
            drawCircle(color = Color.White, radius = r, center = pt, style = Stroke(width = z(3.dp.toPx())))
        }

        // Edge handles: white bars at the middle of each side (iOS `edgeHandle`, 44 x 14)
        val longSide = z(44.dp.toPx())
        val shortSide = z(14.dp.toPx())
        val cr = androidx.compose.ui.geometry.CornerRadius(z(3.dp.toPx()))
        val midX = (left + right) / 2f
        val midY = (top + bottom) / 2f
        for ((c, horizontal) in listOf(
            Offset(midX, top) to true, Offset(midX, bottom) to true,
            Offset(left, midY) to false, Offset(right, midY) to false
        )) {
            val bw = if (horizontal) longSide else shortSide
            val bh = if (horizontal) shortSide else longSide
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.18f),
                topLeft = Offset(c.x - bw / 2f, c.y - bh / 2f + z(1.dp.toPx())),
                size = Size(bw, bh), cornerRadius = cr
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(c.x - bw / 2f, c.y - bh / 2f),
                size = Size(bw, bh), cornerRadius = cr
            )
        }
    }
}

@Composable
private fun QuadCropCanvas(
    quad: Quad,
    displayRect: RectF,
    zoom: Float,
    isDetecting: Boolean,
    onQuadChanged: (Quad) -> Unit
) {
    val shuColor = Color(0xFFE0684C)
    val shuFill = KotenColor.shuFill
    val latestQuad by androidx.compose.runtime.rememberUpdatedState(quad)
    val latestOnQuad by androidx.compose.runtime.rememberUpdatedState(onQuadChanged)
    val latestZoom by androidx.compose.runtime.rememberUpdatedState(zoom)
    var activeCorner by remember { mutableStateOf(-1) }
    var dragStartCorner by remember { mutableStateOf(Offset.Zero) }
    var totalDrag by remember { mutableStateOf(Offset.Zero) }

    Box(modifier = Modifier.fillMaxSize()) {
        val exclusion = rememberHandleGestureExclusion(
            listOf(quad.topLeft, quad.topRight, quad.bottomRight, quad.bottomLeft).map {
                Offset(displayRect.left + it.x * displayRect.width(), displayRect.top + it.y * displayRect.height())
            }
        )
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(exclusion)
                .pointerInput(displayRect) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            val hitR = CropZoom.onScreen(40.dp.toPx(), latestZoom)
                            val pts = listOf(latestQuad.topLeft, latestQuad.topRight, latestQuad.bottomRight, latestQuad.bottomLeft)
                            var bestIdx = -1
                            var bestDist = hitR
                            pts.forEachIndexed { idx, norm ->
                                val px = displayRect.left + norm.x * displayRect.width()
                                val py = displayRect.top + norm.y * displayRect.height()
                                val d = hypot(pos.x - px, pos.y - py)
                                if (d <= bestDist) {
                                    bestDist = d
                                    bestIdx = idx
                                }
                            }
                            activeCorner = bestIdx
                            if (bestIdx in 0..3) {
                                dragStartCorner = pts[bestIdx]
                                totalDrag = Offset.Zero
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (activeCorner !in 0..3) return@detectDragGestures
                            totalDrag += dragAmount
                            val nx = (dragStartCorner.x + totalDrag.x / displayRect.width().coerceAtLeast(1f))
                                .coerceIn(0f, 1f)
                            val ny = (dragStartCorner.y + totalDrag.y / displayRect.height().coerceAtLeast(1f))
                                .coerceIn(0f, 1f)
                            val newPt = Offset(nx, ny)
                            val updated = when (activeCorner) {
                                0 -> latestQuad.copy(topLeft = newPt)
                                1 -> latestQuad.copy(topRight = newPt)
                                2 -> latestQuad.copy(bottomRight = newPt)
                                else -> latestQuad.copy(bottomLeft = newPt)
                            }
                            latestOnQuad(updated)
                        },
                        onDragEnd = { activeCorner = -1 },
                        onDragCancel = { activeCorner = -1 }
                    )
                }
        ) {
            fun z(px: Float) = CropZoom.onScreen(px, zoom)

            fun viewPt(norm: Offset): Offset = Offset(
                displayRect.left + norm.x * displayRect.width(),
                displayRect.top + norm.y * displayRect.height()
            )

            val tl = viewPt(quad.topLeft)
            val tr = viewPt(quad.topRight)
            val br = viewPt(quad.bottomRight)
            val bl = viewPt(quad.bottomLeft)

            val polyPath = Path().apply {
                moveTo(tl.x, tl.y)
                lineTo(tr.x, tr.y)
                lineTo(br.x, br.y)
                lineTo(bl.x, bl.y)
                close()
            }

            clipPath(polyPath, clipOp = ClipOp.Difference) {
                drawRect(
                    color = Color.Black.copy(alpha = 0.55f),
                    topLeft = Offset(displayRect.left, displayRect.top),
                    size = Size(displayRect.width(), displayRect.height())
                )
            }

            // Bilinear 3x3 perspective grid inside quad
            fun lerp(a: Offset, b: Offset, t: Float): Offset =
                Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

            for (i in 1..2) {
                val t = i / 3f
                drawLine(
                    color = Color.White.copy(alpha = 0.35f),
                    start = lerp(tl, tr, t),
                    end = lerp(bl, br, t),
                    strokeWidth = z(0.8.dp.toPx())
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.35f),
                    start = lerp(tl, bl, t),
                    end = lerp(tr, br, t),
                    strokeWidth = z(0.8.dp.toPx())
                )
            }

            drawPath(
                path = polyPath,
                color = if (quad.isConvex) shuColor else Color.Yellow,
                style = Stroke(width = z(2.5.dp.toPx()), join = StrokeJoin.Round)
            )

            for (pt in listOf(tl, tr, br, bl)) {
                drawCircle(color = shuFill, radius = z(11.dp.toPx()), center = pt)
                drawCircle(
                    color = Color.White,
                    radius = z(11.dp.toPx()),
                    center = pt,
                    style = Stroke(width = z(2.5.dp.toPx()))
                )
            }
        }

        if (isDetecting) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    .padding(12.dp)
                    .size(28.dp)
            )
        }
    }
}

/**
 * Keeps Android's edge-swipe Back from stealing the crop handles near the screen edges.
 * Sets the window exclusion rects directly so they follow the handles on every change
 * (the `systemGestureExclusion` modifier only refreshes on re-layout).
 */
@Composable
private fun rememberHandleGestureExclusion(centers: List<Offset>): Modifier {
    val view = androidx.compose.ui.platform.LocalView.current
    val half = with(androidx.compose.ui.platform.LocalDensity.current) { 26.dp.toPx() }
    var coords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    androidx.compose.runtime.SideEffect {
        val c = coords
        if (c != null && c.isAttached) {
            view.systemGestureExclusionRects = centers.map { pt ->
                val w = c.localToWindow(pt)
                android.graphics.Rect(
                    (w.x - half).roundToInt(), (w.y - half).roundToInt(),
                    (w.x + half).roundToInt(), (w.y + half).roundToInt()
                )
            }
        }
    }
    androidx.compose.runtime.DisposableEffect(view) {
        onDispose { view.systemGestureExclusionRects = emptyList() }
    }
    return Modifier.onGloballyPositioned { coords = it }
}

private fun computeFittedRect(
    containerW: Float,
    containerH: Float,
    imageW: Float,
    imageH: Float,
    inset: Float
): RectF {
    val availW = max(1f, containerW - inset * 2f)
    val availH = max(1f, containerH - inset * 2f)
    if (imageW <= 0f || imageH <= 0f) return RectF(0f, 0f, availW, availH)
    val imgAspect = imageW / imageH
    val boxAspect = availW / availH
    val drawW: Float
    val drawH: Float
    if (imgAspect > boxAspect) {
        drawW = availW
        drawH = availW / imgAspect
    } else {
        drawH = availH
        drawW = availH * imgAspect
    }
    val left = inset + (availW - drawW) * 0.5f
    val top = inset + (availH - drawH) * 0.5f
    return RectF(left, top, left + drawW, top + drawH)
}
