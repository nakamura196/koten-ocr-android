package com.nakamura196.kotenocr.ui.screens

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nakamura196.kotenocr.R
import com.nakamura196.kotenocr.ocr.OCRMode
import com.nakamura196.kotenocr.ui.theme.KotenColor
import com.nakamura196.kotenocr.ui.theme.minchoStyle
import com.nakamura196.kotenocr.util.ImageIntake
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bundled sample manuscript / modern document pages (`SamplePage` in `SampleSheet.swift`).
 */
enum class SamplePage(
    val id: String,
    val assetPath: String,
    val mode: OCRMode,
    @StringRes val titleRes: Int,
    @StringRes val creditRes: Int
) {
    TAKETORI(
        id = "taketori",
        assetPath = "samples/sample_taketori.jpg",
        mode = OCRMode.KOTEN,
        titleRes = R.string.sample_taketori_title,
        creditRes = R.string.sample_ndl_credit
    ),
    GENJI(
        id = "genji",
        assetPath = "samples/sample_genji.jpg",
        mode = OCRMode.KOTEN,
        titleRes = R.string.sample_genji_title,
        creditRes = R.string.onboarding_sample_credit
    ),
    KIKUCHI(
        id = "kikuchi",
        assetPath = "samples/sample_kikuchi.jpg",
        mode = OCRMode.KOTEN,
        titleRes = R.string.sample_kikuchi_title,
        creditRes = R.string.sample_kikuchi_credit
    ),
    SHIBATA(
        id = "shibata",
        assetPath = "samples/sample_shibata.jpg",
        mode = OCRMode.KOTEN,
        titleRes = R.string.sample_shibata_title,
        creditRes = R.string.sample_shibata_credit
    ),
    MODERN(
        id = "modern",
        assetPath = "samples/sample_modern.jpg",
        mode = OCRMode.NDL,
        titleRes = R.string.sample_modern_title,
        creditRes = R.string.sample_ndl_credit
    );

    @get:StringRes
    val modeLabelRes: Int
        get() = when (mode) {
            OCRMode.KOTEN -> R.string.ocr_mode_koten
            OCRMode.NDL -> R.string.ocr_mode_ndl
        }
}

/**
 * Modal bottom sheet picker for bundled sample pages (`SampleSheet.swift` port).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SampleSheet(
    onSelect: (SamplePage) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = KotenColor.washi
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.sample_sheet_title),
                    style = minchoStyle(size = 20.sp, bold = true, color = KotenColor.ink),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = KotenColor.ink
                    )
                }
            }

            Text(
                text = stringResource(R.string.sample_sheet_lead),
                fontSize = 14.sp,
                color = KotenColor.inkSecondary
            )

            SamplePage.entries.forEach { sample ->
                SamplePageRow(
                    sample = sample,
                    onClick = { onSelect(sample) }
                )
            }
        }
    }
}

@Composable
private fun SamplePageRow(
    sample: SamplePage,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var thumbnail by remember(sample.id) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(sample.id) {
        thumbnail = withContext(Dispatchers.IO) {
            ImageIntake.loadFromAsset(context, sample.assetPath, maxLongSide = 200)
        }
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
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 84.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(KotenColor.mount)
                    .border(1.dp, KotenColor.rule, RoundedCornerShape(8.dp))
            ) {
                thumbnail?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(sample.modeLabelRes),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = KotenColor.shu,
                    modifier = Modifier
                        .border(1.dp, KotenColor.shu.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(sample.titleRes),
                    style = minchoStyle(size = 17.sp, bold = true, color = KotenColor.ink)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(sample.creditRes),
                    fontSize = 11.sp,
                    color = KotenColor.inkSecondary
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = KotenColor.inkSecondary
            )
        }
    }
}
