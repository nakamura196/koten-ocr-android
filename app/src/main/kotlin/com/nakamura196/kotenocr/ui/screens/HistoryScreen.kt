package com.nakamura196.kotenocr.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nakamura196.kotenocr.R
import com.nakamura196.kotenocr.data.HistoryManager
import com.nakamura196.kotenocr.models.HistoryItem
import com.nakamura196.kotenocr.ui.theme.KotenColor
import com.nakamura196.kotenocr.ui.theme.KotenGlassButton
import com.nakamura196.kotenocr.ui.theme.KotenProminentButton
import com.nakamura196.kotenocr.ui.theme.KotenStamp
import com.nakamura196.kotenocr.ui.theme.WashiBackground
import com.nakamura196.kotenocr.ui.theme.minchoStyle
import java.text.DateFormat
import java.util.Date

/**
 * Scan History screen (`HistoryListView.swift` v2.1 port).
 * Features:
 * - Brush-circle (`円相`) + vermilion `KotenStamp` empty state illustration and "Take Photo" action
 * - Downsampled async thumbnail loading (`HistoryManager.loadThumbnail`)
 * - Single-item delete confirmation dialog + Delete All confirmation dialog
 */
@Composable
fun HistoryScreen(
    historyManager: HistoryManager,
    onSelectItem: (HistoryItem) -> Unit,
    onStartCamera: (() -> Unit)? = null,
    onClose: () -> Unit
) {
    val items by historyManager.items.collectAsState()
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<HistoryItem?>(null) }

    WashiBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top bar: Delete All | Title | Close
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (items.isNotEmpty()) {
                    TextButton(onClick = { showDeleteAllDialog = true }) {
                        Text(
                            text = stringResource(R.string.history_delete_all),
                            color = KotenColor.shu,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(64.dp))
                }

                Text(
                    text = stringResource(R.string.history_title),
                    style = minchoStyle(size = 20.sp, bold = true, color = KotenColor.ink),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )

                KotenGlassButton(
                    onClick = onClose,
                    isCircle = true,
                    modifier = Modifier.size(40.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (items.isEmpty()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    EmptyHistoryEnsoIllustration()
                    Spacer(modifier = Modifier.height(22.dp))
                    Text(
                        text = stringResource(R.string.history_empty_title),
                        style = minchoStyle(size = 24.sp, bold = true, color = KotenColor.ink),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.history_empty_body),
                        fontSize = 15.sp,
                        color = KotenColor.inkSecondary,
                        textAlign = TextAlign.Center
                    )
                }

                if (onStartCamera != null) {
                    KotenProminentButton(
                        onClick = onStartCamera,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                            .height(56.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.home_take_photo),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(items, key = { it.id }) { item ->
                        HistoryRowCard(
                            item = item,
                            historyManager = historyManager,
                            onClick = { onSelectItem(item) },
                            onDelete = { itemToDelete = item }
                        )
                    }
                }
            }
        }
    }

    if (itemToDelete != null) {
        val target = itemToDelete!!
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(stringResource(R.string.history_delete_item_title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        historyManager.delete(target)
                        itemToDelete = null
                    }
                ) {
                    Text(
                        text = stringResource(R.string.history_delete_item),
                        color = KotenColor.shu
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text(stringResource(R.string.history_delete_all_title)) },
            text = { Text(stringResource(R.string.history_delete_all_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        historyManager.deleteAll()
                        showDeleteAllDialog = false
                    }
                ) {
                    Text(
                        text = stringResource(R.string.history_delete_all),
                        color = KotenColor.shu
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun EmptyHistoryEnsoIllustration() {
    val inkColor = KotenColor.ink.copy(alpha = 0.85f)
    Box(modifier = Modifier.size(170.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = size.minDimension / 200f
            val path = Path().apply {
                moveTo(100f * s, 22f * s)
                cubicTo(150f * s, 20f * s, 182f * s, 58f * s, 178f * s, 104f * s)
                cubicTo(174f * s, 152f * s, 136f * s, 182f * s, 92f * s, 178f * s)
                cubicTo(46f * s, 174f * s, 20f * s, 138f * s, 24f * s, 94f * s)
                cubicTo(27f * s, 58f * s, 52f * s, 30f * s, 96f * s, 24f * s)
            }
            drawPath(
                path = path,
                color = inkColor,
                style = Stroke(
                    width = 13f * s,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
        KotenStamp(
            size = 38.dp,
            modifier = Modifier
                .offset(x = 100.dp, y = 110.dp)
                .rotate(1.5f)
        )
    }
}

@Composable
private fun HistoryRowCard(
    item: HistoryItem,
    historyManager: HistoryManager,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var thumbnail by remember(item.id) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(item.id) {
        thumbnail = historyManager.loadThumbnail(item)
    }

    val dateStr = remember(item.timestamp) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.timestamp))
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = KotenColor.washiCard,
        border = BorderStroke(1.dp, KotenColor.rule)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val bmp = thumbnail
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(KotenColor.mount, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = KotenColor.inkSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.text.take(60).replace("\n", " ").ifBlank { "—" },
                    style = minchoStyle(size = 16.sp, color = KotenColor.ink),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = dateStr,
                    fontSize = 12.sp,
                    color = KotenColor.inkSecondary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.lines_count_format, item.detections.size),
                        fontSize = 11.sp,
                        color = KotenColor.inkSecondary
                    )
                    if (item.translatedText != null) {
                        Text(
                            text = stringResource(R.string.history_translated_badge),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = KotenColor.shu,
                            modifier = Modifier
                                .border(1.dp, KotenColor.shu, RoundedCornerShape(3.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.history_delete_item),
                    tint = KotenColor.shu,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
