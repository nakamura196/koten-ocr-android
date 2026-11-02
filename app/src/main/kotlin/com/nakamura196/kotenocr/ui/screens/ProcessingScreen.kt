package com.nakamura196.kotenocr.ui.screens

import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nakamura196.kotenocr.R
import com.nakamura196.kotenocr.ocr.Detection
import com.nakamura196.kotenocr.ui.screens.ScanProgress.Status
import com.nakamura196.kotenocr.ui.theme.KotenColor
import com.nakamura196.kotenocr.ui.theme.KotenGlassButton
import com.nakamura196.kotenocr.ui.theme.WashiBackground
import com.nakamura196.kotenocr.ui.theme.kotenMount
import com.nakamura196.kotenocr.ui.theme.minchoStyle

/**
 * Processing screen, matched to iOS `ScanningView.swift` (storyboard c):
 * - a 朱 beam sweeps right → left with the engine's progress; the page is grey-sepia ahead of it
 *   and in full colour behind it
 * - a status sheet with a 朱 progress bar; once OCR ends, the found lines appear one by one
 *   (boxes on the page, a running "N lines" count, the newest 4 lines listed)
 * - a text "Cancel" button at the top left, the title in the middle ("Reading" → "N lines")
 * - with the system's "Remove animations" on, no beam (and AppState skips the reveal)
 */
@Composable
fun ProcessingScreen(
    bitmap: Bitmap?,
    progress: Float,
    revealedDetections: List<Detection> = emptyList(),
    revealedCount: Int = 0,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val total = revealedDetections.size
    val finished = total > 0
    val revealed = revealedCount.coerceIn(0, total)

    // The engine reports progress in coarse jumps (0.05 → 0.10 → 0.30 → per batch), so drawing
    // the beam straight from `progress` makes it stutter. Instead, a per-frame loop chases the
    // target smoothly and keeps creeping slowly while no new progress arrives.
    val target by rememberUpdatedState(ScanProgress.sweep(progress))
    var shownSweep by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(finished, reduceMotion) {
        if (finished || reduceMotion) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
            last = now
            val chase = (target - shownSweep) * 3f
            val step = maxOf(chase, 0.015f) * dt
            val ceiling = minOf(target + 0.10f, 0.97f)
            shownSweep = maxOf(shownSweep, minOf(shownSweep + step, ceiling))
        }
    }

    val statusText = when (ScanProgress.status(progress, total, revealed)) {
        Status.PREPARING -> stringResource(R.string.scan_preparing)
        Status.FINDING_LINES -> stringResource(R.string.scan_finding_lines)
        Status.READING_TEXT -> stringResource(R.string.scan_reading_text)
        Status.DONE -> stringResource(R.string.scan_done_format, total)
    }
    val title = if (ScanProgress.titleShowsCount(total, revealed)) {
        stringResource(R.string.lines_count_format, total)
    } else {
        stringResource(R.string.scan_title)
    }
    val largeText = LocalDensity.current.fontScale >= 1.5f

    WashiBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top bar: text Cancel at the left, title in the middle (iOS navBar)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(top = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    style = minchoStyle(size = 18.sp, bold = true, color = KotenColor.ink),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 100.dp)
                        .semantics { heading() }
                )
                KotenGlassButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .heightIn(min = 44.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp)
                ) {
                    Text(text = stringResource(R.string.cancel), fontWeight = FontWeight.Bold)
                }
            }

            // The page: grey-sepia ahead of the beam, full colour behind it
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(top = 8.dp)
                    .heightIn(max = if (largeText) 220.dp else 360.dp),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    ScanningPage(
                        bitmap = bitmap,
                        sweep = { if (finished) 1f else if (reduceMotion) target else shownSweep },
                        showBeam = !finished && !reduceMotion,
                        detections = revealedDetections,
                        revealed = revealed,
                        statusText = statusText
                    )
                } else {
                    CircularProgressIndicator(color = KotenColor.shu)
                }
            }

            Spacer(modifier = Modifier.weight(1f).heightIn(min = 12.dp))

            StatusSheet(
                statusText = statusText,
                finished = finished,
                revealed = revealed,
                bar = ScanProgress.bar(progress, total, revealed),
                listed = ScanProgress.listedRange(total, revealed).map { it to revealedDetections[it].text },
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .padding(bottom = 10.dp)
            )
        }
    }
}

/** Grey, slightly sepia "not yet read" look (iOS `.grayscale(1).colorMultiply(...).brightness(-0.05)`). */
private val unreadFilter: ColorFilter by lazy {
    val r = 0.95f; val g = 0.90f; val b = 0.82f
    val lr = 0.2126f; val lg = 0.7152f; val lb = 0.0722f
    val dark = -0.05f * 255f
    ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                lr * r, lg * r, lb * r, 0f, dark,
                lr * g, lg * g, lb * g, 0f, dark,
                lr * b, lg * b, lb * b, 0f, dark,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
}

@Composable
private fun ScanningPage(
    bitmap: Bitmap,
    sweep: () -> Float,
    showBeam: Boolean,
    detections: List<Detection>,
    revealed: Int,
    statusText: String
) {
    val aspect = (bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)).coerceIn(0.4f, 2.2f)
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val shu = KotenColor.shu

    Box(
        modifier = Modifier
            .aspectRatio(aspect, matchHeightConstraintsFirst = true)
            .kotenMount(padding = 6.dp)
            .clearAndSetSemantics { contentDescription = statusText }
    ) {
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            colorFilter = unreadFilter,
            modifier = Modifier.fillMaxSize().graphicsLayer()
        )
        // Already swept: full colour, revealed from the right. The sweep is read in the draw
        // phase only, so the beam's per-frame movement redraws without recomposing.
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer()
                .drawWithContent {
                    val left = size.width * (1f - sweep())
                    clipRect(left = left) { this@drawWithContent.drawContent() }
                }
        )

        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
            if (showBeam) {
                val x = size.width * (1f - sweep())
                // Faint tail on the already-read (right) side, then the glowing 3dp line (iOS ScanBeam)
                val tail = 70.dp.toPx()
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(shu.copy(alpha = 0.35f), Color.Transparent),
                        startX = x, endX = x + tail
                    ),
                    topLeft = Offset(x, 0f),
                    size = Size(tail, size.height)
                )
                val glow = 14.dp.toPx()
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, shu.copy(alpha = 0.45f), Color.Transparent),
                        startX = x - glow, endX = x + glow
                    ),
                    topLeft = Offset(x - glow, 0f),
                    size = Size(glow * 2, size.height)
                )
                drawLine(
                    color = shu,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Butt
                )
            }

            // Found lines, in reading order; the newest one stronger (iOS RevealedBoxes)
            if (revealed > 0 && bitmap.width > 0 && bitmap.height > 0) {
                val sx = size.width / bitmap.width.toFloat()
                val sy = size.height / bitmap.height.toFloat()
                for (i in 0 until revealed) {
                    val b = detections[i].box
                    if (b.size < 4) continue
                    val topLeft = Offset(b[0] * sx, b[1] * sy)
                    val boxSize = Size((b[2] - b[0]) * sx, (b[3] - b[1]) * sy)
                    val newest = i == revealed - 1
                    drawRect(color = shu.copy(alpha = if (newest) 0.25f else 0.1f), topLeft = topLeft, size = boxSize)
                    drawRect(
                        color = shu,
                        topLeft = topLeft,
                        size = boxSize,
                        style = Stroke(width = (if (newest) 2.5.dp else 1.5.dp).toPx())
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusSheet(
    statusText: String,
    finished: Boolean,
    revealed: Int,
    bar: Float,
    listed: List<Pair<Int, String>>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(34.dp),
        color = KotenColor.washiCard.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, KotenColor.rule.copy(alpha = 0.75f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = statusText,
                    style = minchoStyle(size = 19.sp, bold = true, color = KotenColor.ink),
                    modifier = Modifier
                        .weight(1f)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                )
                if (finished) {
                    Text(
                        text = stringResource(R.string.lines_count_format, revealed),
                        style = minchoStyle(size = 28.sp, bold = true, color = KotenColor.shu),
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clearAndSetSemantics { }
                    )
                }
            }
            LinearProgressIndicator(
                progress = { bar },
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
                color = KotenColor.shu,
                trackColor = KotenColor.rule.copy(alpha = 0.5f),
                drawStopIndicator = {}
            )
            if (listed.isNotEmpty()) {
                Column(
                    modifier = Modifier.clearAndSetSemantics { },
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for ((index, text) in listed) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${index + 1}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = KotenColor.shu,
                                textAlign = TextAlign.End,
                                modifier = Modifier.widthIn(min = 18.dp)
                            )
                            Spacer(modifier = Modifier.padding(start = 12.dp))
                            Text(
                                text = text,
                                style = minchoStyle(size = 17.sp, color = KotenColor.ink),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
