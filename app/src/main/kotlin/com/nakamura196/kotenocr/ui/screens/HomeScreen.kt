package com.nakamura196.kotenocr.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import com.nakamura196.kotenocr.ui.theme.KotenSeal
import com.nakamura196.kotenocr.ui.theme.WashiBackground
import com.nakamura196.kotenocr.ui.theme.minchoStyle
import java.text.DateFormat
import java.util.Date

/**
 * Main v2 Home screen (`HomeView.swift` port).
 * Displays the Washi header with `KotenSeal`, recent scans (up to 3 items) or a 3-step guide,
 * primary dark Ink "Take Photo" card, secondary "Choose from Photos" and "History" cards,
 * and the "Try a sample page" button.
 */
@Composable
fun HomeScreen(
    historyManager: HistoryManager,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onShowHistory: () -> Unit,
    onShowSettings: () -> Unit,
    onSelectRecent: (HistoryItem) -> Unit,
    onTrySample: () -> Unit
) {
    val historyItems by historyManager.items.collectAsState()

    WashiBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top bar: Seal + Title + Settings button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KotenSeal(size = 30.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "KotenOCR",
                    style = minchoStyle(size = 22.sp, bold = true, color = KotenColor.ink)
                )
                Spacer(modifier = Modifier.weight(1f))
                KotenGlassButton(
                    onClick = onShowSettings,
                    isCircle = true,
                    modifier = Modifier.size(44.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.settings_button),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Middle content: Recent scans or empty state
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center
            ) {
                if (historyItems.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.brand_tagline),
                            style = minchoStyle(size = 26.sp, bold = true, color = KotenColor.ink),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.home_how_to),
                            fontSize = 14.sp,
                            color = KotenColor.inkSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    val recent = historyItems.take(3)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.home_recent),
                                style = minchoStyle(size = 16.sp, bold = true, color = KotenColor.ink)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "${stringResource(R.string.history_button)} (${historyItems.size})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = KotenColor.shu,
                                modifier = Modifier.clickable(onClick = onShowHistory)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = KotenColor.washiCard,
                            border = BorderStroke(1.dp, KotenColor.rule)
                        ) {
                            Column {
                                recent.forEachIndexed { index, item ->
                                    RecentItemRow(
                                        item = item,
                                        historyManager = historyManager,
                                        onClick = { onSelectRecent(item) }
                                    )
                                    if (index < recent.lastIndex) {
                                        HorizontalDivider(
                                            color = KotenColor.rule.copy(alpha = 0.6f),
                                            modifier = Modifier.padding(start = 68.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom action cards
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Primary Ink card: Take Photo
                val mainCardBg = Color(0.17f, 0.15f, 0.13f)
                val mainCardFg = Color(0.98f, 0.96f, 0.91f)
                Surface(
                    onClick = onTakePhoto,
                    shape = RoundedCornerShape(18.dp),
                    color = mainCardBg,
                    border = BorderStroke(
                        width = if (KotenColor.current.isDark) 1.dp else 0.dp,
                        color = if (KotenColor.current.isDark) KotenColor.rule else Color.Transparent
                    ),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .background(KotenColor.shuFill, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.home_take_photo),
                                style = minchoStyle(size = 20.sp, bold = true, color = mainCardFg)
                            )
                            Text(
                                text = stringResource(R.string.home_take_photo_sub),
                                fontSize = 12.sp,
                                color = mainCardFg.copy(alpha = 0.72f)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = mainCardFg.copy(alpha = 0.6f)
                        )
                    }
                }

                // Secondary cards: Choose from Photos + History
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SecondaryActionCard(
                        icon = Icons.Default.PhotoLibrary,
                        title = stringResource(R.string.home_choose_photo),
                        onClick = onPickPhoto,
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryActionCard(
                        icon = Icons.Default.History,
                        title = stringResource(R.string.history_button),
                        badge = if (historyItems.isEmpty()) null else "${historyItems.size}",
                        onClick = onShowHistory,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Try a sample page link
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onTrySample)
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = KotenColor.inkSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.home_try_sample),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = KotenColor.inkSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun SecondaryActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    badge: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = KotenColor.washiCard,
        border = BorderStroke(1.dp, KotenColor.rule)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = KotenColor.shu,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = KotenColor.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (badge != null) {
                Text(
                    text = badge,
                    fontSize = 12.sp,
                    color = KotenColor.inkSecondary
                )
            }
        }
    }
}

@Composable
private fun RecentItemRow(
    item: HistoryItem,
    historyManager: HistoryManager,
    onClick: () -> Unit
) {
    var thumbnail by remember(item.id) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(item.id) {
        thumbnail = historyManager.loadThumbnail(item)
    }

    val firstLine = remember(item.text) {
        item.text.lineSequence().firstOrNull()?.take(40) ?: "—"
    }
    val dateString = remember(item.timestamp) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.timestamp))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val bmp = thumbnail
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(KotenColor.mount, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = KotenColor.inkSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = firstLine,
                style = minchoStyle(size = 15.sp, color = KotenColor.ink),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$dateString · ${stringResource(R.string.lines_count_format, item.detections.size)}",
                fontSize = 12.sp,
                color = KotenColor.inkSecondary
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = KotenColor.inkSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}
