package com.nakamura196.kotenocr.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DocumentScanner
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nakamura196.kotenocr.R
import com.nakamura196.kotenocr.ocr.OCRMode
import com.nakamura196.kotenocr.ui.theme.KotenColor
import com.nakamura196.kotenocr.ui.theme.KotenGlassButton
import com.nakamura196.kotenocr.ui.theme.KotenProminentButton
import com.nakamura196.kotenocr.ui.theme.WashiBackground
import com.nakamura196.kotenocr.ui.theme.kotenMount
import com.nakamura196.kotenocr.ui.theme.minchoStyle

/**
 * Confirm Crop screen (`ContentView.confirmCropView` v2.1 port).
 * Shows the cropped image mounted on Washi, with a bottom glass toolbar for choosing between
 * Classical (`古典籍 / くずし字`) and Modern (`近代 / 活字・手書き`) OCR modes and starting recognition.
 */
@Composable
fun ConfirmCropScreen(
    bitmap: Bitmap,
    initialMode: OCRMode,
    onRunOCR: (OCRMode) -> Unit,
    onReCrop: () -> Unit,
    onRetake: () -> Unit
) {
    var selectedMode by remember(initialMode) { mutableStateOf(initialMode) }

    WashiBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top bar (iOS): ‹ Re-crop | Confirm | Cancel
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 6.dp)
            ) {
                KotenGlassButton(
                    onClick = onReCrop,
                    modifier = Modifier.align(Alignment.CenterStart).height(44.dp),
                    contentPadding = PaddingValues(start = 10.dp, end = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = stringResource(R.string.retrim),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = stringResource(R.string.confirm_crop_title),
                    style = minchoStyle(size = 18.sp, bold = true, color = KotenColor.ink),
                    modifier = Modifier.align(Alignment.Center)
                )

                KotenGlassButton(
                    onClick = onRetake,
                    modifier = Modifier.align(Alignment.CenterEnd).height(44.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Mounted preview, as large as the space allows
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                val mountPad = 16.dp // kotenMount padding on both sides
                val ratio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
                val availW = maxWidth - mountPad
                val availH = maxHeight - mountPad
                val fitW = if (availW / availH > ratio) availH * ratio else availW
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .kotenMount()
                        .size(width = fitW, height = fitW / ratio)
                )
            }

            // Bottom glass toolbar (iOS `ocrModeToolbar`): mode switch + 朱 Read, in one row
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 16.dp),
                shape = RoundedCornerShape(36.dp),
                color = KotenColor.washiCard.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, KotenColor.rule.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .background(KotenColor.ink.copy(alpha = 0.08f), RoundedCornerShape(50))
                            .padding(3.dp)
                    ) {
                        OCRModeChoiceButton(
                            title = stringResource(R.string.ocr_mode_koten),
                            subtitle = stringResource(R.string.ocr_mode_koten_short),
                            selected = selectedMode == OCRMode.KOTEN,
                            onClick = { selectedMode = OCRMode.KOTEN },
                            modifier = Modifier.weight(1f)
                        )
                        OCRModeChoiceButton(
                            title = stringResource(R.string.ocr_mode_ndl),
                            subtitle = stringResource(R.string.ocr_mode_ndl_short),
                            selected = selectedMode == OCRMode.NDL,
                            onClick = { selectedMode = OCRMode.NDL },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    KotenProminentButton(
                        onClick = { onRunOCR(selectedMode) },
                        modifier = Modifier
                            .height(50.dp)
                            .widthIn(min = 120.dp),
                        contentPadding = PaddingValues(horizontal = 22.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.run_ocr),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OCRModeChoiceButton(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(50),
        color = if (selected) KotenColor.washiCard else Color.Transparent,
        shadowElevation = if (selected) 1.dp else 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val c = if (selected) KotenColor.ink else KotenColor.inkSecondary
            Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c, maxLines = 1)
            Text(text = subtitle, fontSize = 11.sp, color = c.copy(alpha = 0.8f), maxLines = 1)
        }
    }
}
