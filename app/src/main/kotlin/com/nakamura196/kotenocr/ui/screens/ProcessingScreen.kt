package com.nakamura196.kotenocr.ui.screens

import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nakamura196.kotenocr.R
import com.nakamura196.kotenocr.ocr.Detection
import com.nakamura196.kotenocr.ui.theme.KotenColor
import com.nakamura196.kotenocr.ui.theme.KotenGlassButton
import com.nakamura196.kotenocr.ui.theme.KotenSeal
import com.nakamura196.kotenocr.ui.theme.WashiBackground
import com.nakamura196.kotenocr.ui.theme.kotenMount
import com.nakamura196.kotenocr.ui.theme.minchoStyle

/**
 * Scanning / Processing screen (`ScanningView.swift` v2.1 port).
 * - Sweeps a glowing vermilion (`朱`) beam from right to left across the page as `progress` advances
 * - Once recognition completes, reveals detected line boxes and the last 4 recognized lines one by one
 *   in reading order before transitioning to the Result screen
 */
@Composable
fun ProcessingScreen(
    bitmap: Bitmap?,
    progress: Float,
    revealedDetections: List<Detection> = emptyList(),
    revealedCount: Int = 0,
    onCancel: () -> Unit
) {
    val scanned = revealedDetections.isNotEmpty()
    // The engine reports progress in coarse jumps (0.05 → 0.10 → 0.30 → per batch), so drawing
    // the beam straight from `progress` makes it stutter. Instead, a per-frame loop chases the
    // target smoothly and keeps creeping slowly while no new progress arrives.
    val target by rememberUpdatedState((progress / 0.9f).coerceIn(0f, 1f))
    var shownSweep by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(scanned) {
        if (scanned) return@LaunchedEffect
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

    val statusText = when {
        scanned -> stringResource(R.string.scan_done_format, revealedCount)
        progress < 0.2f -> stringResource(R.string.scan_preparing)
        progress < 0.5f -> stringResource(R.string.scan_finding_lines)
        else -> stringResource(R.string.scan_reading_text)
    }

    val grayscaleFilter = remember {
        val cm = ColorMatrix().apply { setToSaturation(0.15f) }
        ColorFilter.colorMatrix(cm)
    }

    WashiBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar: Seal + Title + Cancel button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KotenSeal(size = 26.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.scan_title),
                    style = minchoStyle(size = 18.sp, bold = true, color = KotenColor.ink)
                )
                Spacer(modifier = Modifier.weight(1f))
                KotenGlassButton(
                    onClick = onCancel,
                    isCircle = true,
                    modifier = Modifier.size(40.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cancel),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Center: Mounted manuscript with right-to-left vermilion scan beam & box reveal
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    val aspect = (bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f))
                        .coerceIn(0.4f, 2.2f)
                    val shuColor = KotenColor.shu
                    val shuFill = KotenColor.shuFill
                    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

                    Box(
                        modifier = Modifier
                            .aspectRatio(aspect, matchHeightConstraintsFirst = true)
                            .kotenMount(padding = 6.dp)
                    ) {
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            colorFilter = if (scanned) null else grayscaleFilter,
                            // Own layer so the beam's per-frame redraw doesn't repaint the page image
                            modifier = Modifier.fillMaxSize().graphicsLayer()
                        )

                        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
                            // Read the animated value only here (draw phase) → redraw, no recomposition
                            val sweep = if (scanned) 1f else shownSweep
                            val beamX = size.width * (1f - sweep)

                            // Glowing vermilion beam sweeping right-to-left
                            if (!scanned && sweep > 0f && sweep < 1f) {
                                val glowW = 36.dp.toPx()
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            shuColor.copy(alpha = 0.32f),
                                            shuColor,
                                            shuColor.copy(alpha = 0.25f),
                                            Color.Transparent
                                        ),
                                        startX = beamX - glowW * 0.5f,
                                        endX = beamX + glowW * 0.5f
                                    ),
                                    topLeft = Offset(beamX - glowW * 0.5f, 0f),
                                    size = Size(glowW, size.height)
                                )
                                drawLine(
                                    color = shuColor,
                                    start = Offset(beamX, 0f),
                                    end = Offset(beamX, size.height),
                                    strokeWidth = 2.5.dp.toPx()
                                )
                            }

                            // Revealed boxes in reading order
                            if (scanned && bitmap.width > 0 && bitmap.height > 0) {
                                val sx = size.width / bitmap.width.toFloat()
                                val sy = size.height / bitmap.height.toFloat()
                                val count = minOf(revealedCount, revealedDetections.size)
                                for (i in 0 until count) {
                                    val b = revealedDetections[i].box
                                    if (b.size < 4) continue
                                    val left = b[0] * sx
                                    val top = b[1] * sy
                                    val w = (b[2] - b[0]) * sx
                                    val h = (b[3] - b[1]) * sy
                                    val isNewest = i == count - 1
                                    drawRoundRect(
                                        color = shuFill.copy(alpha = if (isNewest) 0.28f else 0.14f),
                                        topLeft = Offset(left, top),
                                        size = Size(w, h),
                                        cornerRadius = CornerRadius(3.dp.toPx())
                                    )
                                    drawRoundRect(
                                        color = shuColor,
                                        topLeft = Offset(left, top),
                                        size = Size(w, h),
                                        cornerRadius = CornerRadius(3.dp.toPx()),
                                        style = Stroke(width = (if (isNewest) 2.2.dp else 1.5.dp).toPx())
                                    )
                                }
                            }
                        }
                    }
                } else {
                    CircularProgressIndicator(color = KotenColor.shu)
                }
            }

            // Bottom status card with last 4 revealed lines
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                shape = RoundedCornerShape(16.dp),
                color = KotenColor.washiCard,
                border = BorderStroke(1.dp, KotenColor.rule)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = statusText,
                        style = minchoStyle(size = 17.sp, bold = true, color = KotenColor.ink)
                    )

                    if (scanned && revealedCount > 0) {
                        val visibleSlice = revealedDetections.take(revealedCount)
                        val startIdx = maxOf(0, visibleSlice.size - 4)
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            for (i in startIdx until visibleSlice.size) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${i + 1}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = KotenColor.shu
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = visibleSlice[i].text,
                                        style = minchoStyle(size = 15.sp, color = KotenColor.ink),
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
    }
}
