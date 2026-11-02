package com.nakamura196.kotenocr.ui.screens

import com.nakamura196.kotenocr.ui.theme.kotenMount
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Crop
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nakamura196.kotenocr.R
import com.nakamura196.kotenocr.data.ExportManager
import com.nakamura196.kotenocr.data.SettingsManager
import com.nakamura196.kotenocr.data.TranslationException
import com.nakamura196.kotenocr.data.TranslationService
import com.nakamura196.kotenocr.ocr.ConfidenceFilter
import com.nakamura196.kotenocr.ocr.Detection
import com.nakamura196.kotenocr.ui.theme.KotenColor
import com.nakamura196.kotenocr.ui.theme.KotenGlassButton
import com.nakamura196.kotenocr.ui.theme.KotenProminentButton
import com.nakamura196.kotenocr.ui.theme.KotenStamp
import com.nakamura196.kotenocr.ui.theme.WashiBackground
import com.nakamura196.kotenocr.ui.theme.minchoStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * OCR Result screen (`ResultOverlayView.swift` + `ContentView.resultView` v2.1 port).
 * Features:
 * - Outside vermilion line-number tags ([LineTagLayout])
 * - Interactive Box Editor ([BoxEditorState], [BoxEditCanvas], [BoxEditControls])
 * - Confidence filter slider ([ConfidenceFilter], range `0.25..0.90`, default `0.30`)
 * - Text editing dialog per line & full text editing saved back to History
 * - Floating vermilion Copy button + top capsule "コピーしました" toast
 * - Completion stamp ("典") animation on new scan completion
 * - Modern Japanese / English translation tab (`TranslationService`)
 * - Friendly No-Lines 3-tip guide when 0 lines are found
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ResultScreen(
    bitmap: Bitmap,
    initialDetections: List<Detection>,
    initialText: String,
    initialTranslatedText: String? = null,
    showStampInitial: Boolean = false,
    errorMessage: String? = null,
    settingsManager: SettingsManager,
    fromHistory: Boolean = false,
    onBack: () -> Unit,
    onReCrop: () -> Unit,
    onRetake: () -> Unit,
    onTranslationCompleted: ((String) -> Unit)? = null,
    onDetectionsUpdated: ((List<Detection>, String) -> Unit)? = null,
    onRereadBoxes: (suspend (List<Detection>) -> List<Detection>)? = null,
    onOrderBoxes: ((List<Detection>, Int, Int) -> List<Detection>)? = null,
    onOpenSettings: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var detections by remember(initialDetections) { mutableStateOf(initialDetections) }
    var confidenceThreshold by remember { mutableFloatStateOf(ConfidenceFilter.DEFAULT_THRESHOLD) }
    val visibleItems = remember(detections, confidenceThreshold) {
        ConfidenceFilter.visible(detections, confidenceThreshold)
    }
    val visibleText = remember(detections, confidenceThreshold) {
        ConfidenceFilter.text(detections, confidenceThreshold)
    }

    var showBoxes by remember { mutableStateOf(true) }
    var selectedOriginalIndex by remember { mutableStateOf<Int?>(null) }
    var showShareMenu by remember { mutableStateOf(false) }
    var showCopiedToast by remember { mutableStateOf(false) }

    // Line / full text editing dialog state
    var editingOriginalIndex by remember { mutableStateOf<Int?>(null) }
    var editBuffer by remember { mutableStateOf("") }

    // Box Editor state
    var boxEditor by remember { mutableStateOf<BoxEditorState?>(null) }
    var isRereadingBoxes by remember { mutableStateOf(false) }

    // Translation state
    var selectedTab by remember { mutableIntStateOf(0) }
    var translatedText by remember(initialTranslatedText) { mutableStateOf(initialTranslatedText) }
    var isTranslating by remember { mutableStateOf(false) }
    var translationError by remember { mutableStateOf<String?>(null) }
    var showNoKeyDialog by remember { mutableStateOf(false) }

    // Completion stamp animation
    var showStamp by remember { mutableStateOf(showStampInitial) }
    val stampScale = remember { Animatable(1.4f) }
    val stampAlpha = remember { Animatable(0f) }

    LaunchedEffect(showStampInitial) {
        if (showStampInitial) {
            showStamp = true
            stampAlpha.animateTo(1f, tween(140))
            stampScale.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
            )
            delay(500)
            stampAlpha.animateTo(0f, tween(240))
            showStamp = false
        }
    }

    fun copyToClipboard(textToCopy: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("OCR Text", textToCopy))
        scope.launch {
            showCopiedToast = true
            delay(1600)
            showCopiedToast = false
        }
    }

    fun persistDetections(updated: List<Detection>) {
        detections = updated
        val fullJoined = updated.map { it.text }.filter { it.isNotEmpty() }.joinToString("\n")
        onDetectionsUpdated?.invoke(updated, fullJoined)
    }

    fun startTranslation() {
        val apiKey = settingsManager.apiKey
        if (apiKey.isBlank()) {
            showNoKeyDialog = true
            return
        }
        selectedTab = 1
        isTranslating = true
        translationError = null
        scope.launch {
            try {
                val result = TranslationService.translate(
                    text = visibleText.ifBlank { initialText },
                    apiKey = apiKey,
                    endpoint = settingsManager.apiEndpoint,
                    model = settingsManager.apiModel,
                    targetLanguage = settingsManager.targetLanguage,
                    level = settingsManager.translationLevel
                )
                translatedText = result
                onTranslationCompleted?.invoke(result)
            } catch (e: TranslationException) {
                translationError = when (e.errorCode) {
                    TranslationException.ErrorCode.NO_KEY -> context.getString(R.string.translation_error_no_key)
                    TranslationException.ErrorCode.TOO_LONG -> context.getString(R.string.translation_error_too_long)
                    TranslationException.ErrorCode.SERVER -> "${context.getString(R.string.translation_error_server)}: ${e.message}"
                    TranslationException.ErrorCode.INVALID_RESPONSE -> context.getString(R.string.translation_error_invalid)
                }
            } catch (e: Exception) {
                translationError = e.localizedMessage ?: context.getString(R.string.translation_error_server)
            } finally {
                isTranslating = false
            }
        }
    }

    fun runPendingBoxReread(editor: BoxEditorState, finishAfter: Boolean) {
        val rereadFn = onRereadBoxes
        val orderFn = onOrderBoxes
        if (!editor.hasPending || rereadFn == null) {
            if (finishAfter) {
                val ordered = orderFn?.invoke(editor.detections, bitmap.width, bitmap.height)
                    ?: editor.detections
                persistDetections(ordered)
                boxEditor = null
            }
            return
        }
        val pendingSorted = editor.pendingIndices.sorted()
        val toRead = pendingSorted.mapNotNull { idx -> editor.detections.getOrNull(idx) }
        isRereadingBoxes = true
        scope.launch {
            val recognized = rereadFn(toRead)
            val merged = editor.detections.toMutableList()
            for ((k, idx) in pendingSorted.withIndex()) {
                if (idx in merged.indices && k in recognized.indices) {
                    merged[idx] = recognized[k]
                }
            }
            val ordered = orderFn?.invoke(merged, bitmap.width, bitmap.height) ?: merged
            editor.applyRecognizedPending(recognized, ordered)
            isRereadingBoxes = false
            if (finishAfter) {
                persistDetections(ordered)
                boxEditor = null
            }
        }
    }

    WashiBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Top bar (iOS): ‹ Back | "N 行" (Mincho) | crop
                val currentEditor = boxEditor
                if (currentEditor == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 4.dp)
                    ) {
                        KotenGlassButton(
                            onClick = onBack,
                            modifier = Modifier.align(Alignment.CenterStart).height(44.dp),
                            contentPadding = PaddingValues(start = 10.dp, end = 16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = stringResource(if (fromHistory) R.string.history_title else R.string.back),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (errorMessage == null && detections.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.lines_count_format, visibleItems.size),
                                style = minchoStyle(size = 18.sp, bold = true, color = KotenColor.ink),
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }

                        if (!fromHistory) {
                            KotenGlassButton(
                                onClick = onReCrop,
                                isCircle = true,
                                modifier = Modifier.align(Alignment.CenterEnd).size(44.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Crop,
                                    contentDescription = stringResource(R.string.retrim),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Upper half: the page image (pinch / double-tap to zoom, in view and edit mode),
                // with the line boxes and outside number tags, or the box editor.
                var imageAreaSize by remember { mutableStateOf(IntSize.Zero) }
                var pageZoom by remember(bitmap) { mutableStateOf(PageZoom.State()) }
                val containerSize = Size(imageAreaSize.width.toFloat(), imageAreaSize.height.toFloat())
                val fit = remember(imageAreaSize, bitmap) {
                    PageStage.fitRect(containerSize, bitmap.width.toFloat(), bitmap.height.toFloat())
                }
                val stageOf = {
                    PageStage(containerSize, fit, bitmap.width.toFloat(), bitmap.height.toFloat(), pageZoom.zoom, pageZoom.offset)
                }
                val density = LocalDensity.current.density
                val onTapPage by rememberUpdatedState { point: Offset ->
                    // The line under the finger (the one whose centre is nearest if several overlap).
                    var bestPos = -1
                    var bestOrigIdx = -1
                    var bestDist = Float.MAX_VALUE
                    visibleItems.forEachIndexed { visPos, item ->
                        val r = BoxEditorState.boxRect(item.detection.box)
                        if (r.contains(point)) {
                            val d = (r.center - point).getDistance()
                            if (d < bestDist) {
                                bestDist = d
                                bestPos = visPos
                                bestOrigIdx = item.originalIndex
                            }
                        }
                    }
                    if (bestPos >= 0 && showBoxes) {
                        selectedOriginalIndex = if (selectedOriginalIndex == bestOrigIdx) null else bestOrigIdx
                        scope.launch { listState.animateScrollToItem(bestPos) }
                    }
                }
                val pageTouch: PageTouch = remember(currentEditor, fit, imageAreaSize) {
                    if (currentEditor != null) {
                        BoxEditTouch(
                            editor = currentEditor,
                            stage = stageOf,
                            zoom = { pageZoom },
                            onZoom = { pageZoom = it },
                            grips = BoxEditGeometry.Grips.forDensity(density)
                        )
                    } else {
                        ViewPageTouch(
                            stage = stageOf,
                            zoom = { pageZoom },
                            onZoom = { pageZoom = it },
                            selection = { selectedOriginalIndex },
                            restoreSelection = { selectedOriginalIndex = it },
                            onTap = { onTapPage(it) }
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(if (currentEditor != null) 0.56f else 0.40f)
                        .padding(start = 14.dp, end = 14.dp, top = 4.dp)
                        .kotenMount(padding = 6.dp)
                        .onSizeChanged { imageAreaSize = it }
                        .clipToBounds()
                        .pageGestures(
                            key = pageTouch,
                            zoom = { pageZoom },
                            onZoom = { pageZoom = it },
                            touch = pageTouch
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = pageZoom.zoom
                                scaleY = pageZoom.zoom
                                translationX = pageZoom.offset.x
                                translationY = pageZoom.offset.y
                            }
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )

                        if (fit.width > 0f && fit.height > 0f) {
                            if (currentEditor != null) {
                                BoxEditDrawing(
                                    editor = currentEditor,
                                    fit = fit,
                                    zoom = { pageZoom.zoom }
                                )
                            } else if (showBoxes && visibleItems.isNotEmpty()) {
                                ResultBoxesDrawing(
                                    visibleItems = visibleItems,
                                    selectedOriginalIndex = selectedOriginalIndex,
                                    imageWidth = bitmap.width,
                                    fit = fit,
                                    zoom = { pageZoom.zoom }
                                )
                            }
                        }
                    }
                }

                // Lower half: Error panel / No-lines panel / BoxEditControls / Result text panel
                if (errorMessage != null) {
                    ErrorPanel(
                        message = errorMessage,
                        onRetake = onRetake,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.58f)
                    )
                } else if (currentEditor != null) {
                    BoxEditControls(
                        editor = currentEditor,
                        isRereading = isRereadingBoxes,
                        onReread = { runPendingBoxReread(currentEditor, finishAfter = false) },
                        onCancel = { boxEditor = null },
                        onDone = { runPendingBoxReread(currentEditor, finishAfter = true) }
                    )
                } else if (detections.isEmpty()) {
                    NoLinesPanel(
                        onRetake = onRetake,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.58f)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.60f)
                    ) {
                        // Controls under the image (iOS): 枠 / 枠を直す ... share
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 14.dp, top = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            KotenGlassButton(
                                onClick = { showBoxes = !showBoxes },
                                modifier = Modifier.height(40.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp)
                            ) {
                                Icon(
                                    imageVector = if (showBoxes) Icons.Default.CropFree else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.boxes_toggle),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            KotenGlassButton(
                                onClick = {
                                    selectedOriginalIndex = null
                                    showBoxes = true
                                    boxEditor = BoxEditorState(
                                        initialDetections = detections,
                                        imageWidth = bitmap.width,
                                        imageHeight = bitmap.height
                                    )
                                },
                                modifier = Modifier.height(40.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EditNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.box_edit_enter),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            Box {
                                KotenGlassButton(
                                    onClick = { showShareMenu = true },
                                    isCircle = true,
                                    modifier = Modifier.size(44.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.IosShare,
                                        contentDescription = stringResource(R.string.share),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showShareMenu,
                                    onDismissRequest = { showShareMenu = false },
                                    containerColor = KotenColor.washiCard,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, KotenColor.rule)
                                ) {
                                    ShareMenuItem(stringResource(R.string.share_text), Icons.Default.Share) {
                                        showShareMenu = false
                                        val shareBody = if (selectedTab == 1 && translatedText != null) {
                                            translatedText!!
                                        } else {
                                            visibleText
                                        }
                                        ExportManager.shareText(context, shareBody)
                                    }
                                    ShareMenuItem(stringResource(R.string.export_txt), Icons.Default.Description) {
                                        showShareMenu = false
                                        ExportManager.exportTxt(context, visibleText)
                                    }
                                    ShareMenuItem(stringResource(R.string.export_pdf), Icons.Default.PictureAsPdf) {
                                        showShareMenu = false
                                        ExportManager.exportPdf(
                                            context,
                                            bitmap,
                                            visibleItems.map { it.detection },
                                            visibleText
                                        )
                                    }
                                }
                            }
                        }

                        ConfidenceSliderBar(
                            threshold = confidenceThreshold,
                            hiddenCount = detections.size - visibleItems.size,
                            onThresholdChange = { newVal ->
                                confidenceThreshold = newVal
                                if (selectedOriginalIndex != null &&
                                    visibleItems.none { it.originalIndex == selectedOriginalIndex }
                                ) {
                                    selectedOriginalIndex = null
                                }
                            }
                        )

                        // Segmented tabs: OCR結果 / 現代語訳
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                                .background(KotenColor.ink.copy(alpha = 0.08f), RoundedCornerShape(50))
                                .padding(3.dp)
                        ) {
                            TabPill(
                                title = stringResource(R.string.tab_ocr),
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                modifier = Modifier.weight(1f)
                            )
                            TabPill(
                                title = stringResource(R.string.tab_translation),
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (selectedTab == 0) {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 96.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(
                                    items = visibleItems,
                                    key = { _, item -> item.originalIndex }
                                ) { visibleIdx, item ->
                                    val isSelected = selectedOriginalIndex == item.originalIndex
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 1.dp)
                                            .background(
                                                if (isSelected) KotenColor.shu.copy(alpha = 0.16f) else Color.Transparent,
                                                RoundedCornerShape(6.dp)
                                            )
                                            .combinedClickable(
                                                onClick = {
                                                    selectedOriginalIndex =
                                                        if (isSelected) null else item.originalIndex
                                                },
                                                onLongClick = {
                                                    editingOriginalIndex = item.originalIndex
                                                    editBuffer = item.detection.text
                                                }
                                            )
                                            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = "${visibleIdx + 1}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = KotenColor.shu,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier
                                                .width(24.dp)
                                                .padding(top = 9.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = item.detection.text.ifEmpty { "---" },
                                            style = minchoStyle(
                                                size = 18.sp,
                                                color = if (item.detection.text.isEmpty()) KotenColor.inkSecondary else KotenColor.ink
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(top = 6.dp, bottom = 6.dp)
                                        )
                                        IconButton(
                                            onClick = {
                                                editingOriginalIndex = item.originalIndex
                                                editBuffer = item.detection.text
                                            },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = stringResource(R.string.edit_text),
                                                tint = KotenColor.inkSecondary,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            TranslationTabContent(
                                isTranslating = isTranslating,
                                translationError = translationError,
                                translatedText = translatedText,
                                canTranslate = visibleText.isNotBlank(),
                                onTranslate = ::startTranslation
                            )
                        }
                    }
                }
            }

            // Prominent floating Copy button at bottom-right
            if (boxEditor == null && detections.isNotEmpty() && errorMessage == null) {
                // iOS hides Copy on the translation tab until there is a translation.
                val copyTarget = if (selectedTab == 1) translatedText.orEmpty() else visibleText
                if (copyTarget.isNotBlank()) {
                    KotenProminentButton(
                        onClick = { copyToClipboard(copyTarget) },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .navigationBarsPadding()
                            .padding(end = 20.dp, bottom = 18.dp)
                            .height(50.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = if (showCopiedToast) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (showCopiedToast) {
                                stringResource(R.string.copied)
                            } else {
                                stringResource(R.string.copy)
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Top "Copied" capsule toast
            AnimatedVisibility(
                visible = showCopiedToast,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = KotenColor.ink,
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = KotenColor.shu,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.copied_toast),
                            color = KotenColor.washiCard,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Completion stamp ("典") overlay
            if (showStamp) {
                KotenStamp(
                    size = 76.dp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .scale(stampScale.value)
                        .rotate(-3f)
                        .alpha(stampAlpha.value)
                )
            }
        }
    }

    // Edit Single Line Dialog
    if (editingOriginalIndex != null) {
        val targetIdx = editingOriginalIndex!!
        AlertDialog(
            onDismissRequest = { editingOriginalIndex = null },
            title = {
                Text(
                    text = stringResource(R.string.edit_detection_title),
                    style = minchoStyle(size = 18.sp, bold = true, color = KotenColor.ink)
                )
            },
            text = {
                OutlinedTextField(
                    value = editBuffer,
                    onValueChange = { editBuffer = it },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (targetIdx in detections.indices) {
                            val updated = detections.toMutableList()
                            val edited = updated[targetIdx].copy(text = editBuffer)
                            edited.markReaderConfirmed()
                            updated[targetIdx] = edited
                            persistDetections(updated)
                        }
                        editingOriginalIndex = null
                    }
                ) {
                    Text(stringResource(R.string.edit_save), color = KotenColor.shu)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingOriginalIndex = null }) {
                    Text(stringResource(R.string.edit_cancel))
                }
            }
        )
    }

    // API Key Required Dialog
    if (showNoKeyDialog) {
        AlertDialog(
            onDismissRequest = { showNoKeyDialog = false },
            title = { Text(stringResource(R.string.translation_no_key_title)) },
            text = { Text(stringResource(R.string.translation_no_key_message)) },
            confirmButton = {
                if (onOpenSettings != null) {
                    TextButton(
                        onClick = {
                            showNoKeyDialog = false
                            onOpenSettings()
                        }
                    ) {
                        Text(stringResource(R.string.translation_open_settings), color = KotenColor.shu)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoKeyDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * One-finger touches on the page outside the box editor: a tap selects the line under it, a drag
 * pans the zoomed page. The first tap of a double tap must not leave its selection change behind.
 */
private class ViewPageTouch(
    private val stage: () -> PageStage,
    private val zoom: () -> PageZoom.State,
    private val onZoom: (PageZoom.State) -> Unit,
    private val selection: () -> Int?,
    private val restoreSelection: (Int?) -> Unit,
    private val onTap: (Offset) -> Unit
) : PageTouch {
    private var start = PageZoom.State()
    private var beforeLastTap: Int? = null

    override fun begin(at: Offset) {
        start = zoom()
    }

    override fun drag(at: Offset, translation: Offset) {
        if (start.zoom > PageZoom.MIN) onZoom(PageZoom.panned(start, translation, stage().containerSize))
    }

    override fun end(at: Offset, moved: Boolean) {
        if (moved) return
        beforeLastTap = selection()
        onTap(stage().toImage(at))
    }

    override fun cancel() = Unit

    override fun doubleTap(at: Offset) {
        // The first tap of the pair toggled the selection; the second would toggle it back
        // only if it hit the same line, so restore what was selected before the pair.
        restoreSelection(beforeLastTap)
    }
}

/**
 * The line boxes and their outside number tags (iOS `ResultOverlayView`): each line in its palette
 * colour, outlines as thick as the page's lines allow ([LineTagLayout.boxLineWidth]) and kept that
 * thick on screen when zoomed; the tags zoom with the image.
 */
@Composable
private fun ResultBoxesDrawing(
    visibleItems: List<ConfidenceFilter.IndexedDetection>,
    selectedOriginalIndex: Int?,
    imageWidth: Int,
    fit: Rect,
    zoom: () -> Float
) {
    val shuFill = KotenColor.shuFill
    Canvas(modifier = Modifier.fillMaxSize()) {
        val s = fit.width / imageWidth.toFloat().coerceAtLeast(1f)
        val z = max(zoom(), 0.01f)
        val rects = visibleItems.map { item ->
            val r = BoxEditorState.boxRect(item.detection.box)
            Rect(fit.left + r.left * s, fit.top + r.top * s, fit.left + r.right * s, fit.top + r.bottom * s)
        }
        val lineWidth = LineTagLayout.boxLineWidth(rects, density) / z
        visibleItems.forEachIndexed { idx, item ->
            val r = rects[idx]
            val isSelected = item.originalIndex == selectedOriginalIndex
            val color = KotenColor.box(idx)
            drawRect(color.copy(alpha = if (isSelected) 0.3f else 0.08f), r.topLeft, r.size)
            drawRect(color, r.topLeft, r.size, style = Stroke(width = if (isSelected) lineWidth * 2f else lineWidth))
        }

        // Numbers sit at the start of each line, outside the box, so they never hide the first characters.
        val tags = LineTagLayout.frames(rects, fit, density)
        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val order = visibleItems.indices.sortedBy { if (visibleItems[it].originalIndex == selectedOriginalIndex) 1 else 0 }
        for (idx in order) {
            val tr = tags[idx]
            val isSelected = visibleItems[idx].originalIndex == selectedOriginalIndex
            val alpha = if (isSelected) 1f else 0.85f
            val radius = CornerRadius(tr.height / 2f)
            drawRoundRect(shuFill.copy(alpha = alpha), tr.topLeft, tr.size, radius)
            drawRoundRect(
                Color.White.copy(alpha = alpha), tr.topLeft, tr.size, radius,
                style = Stroke(width = max(0.5f * density, tr.height / 16f))
            )
            textPaint.textSize = tr.height * 0.75f
            textPaint.alpha = (alpha * 255).toInt()
            val fm = textPaint.fontMetrics
            val baseline = tr.center.y - (fm.ascent + fm.descent) * 0.5f
            drawContext.canvas.nativeCanvas.drawText("${idx + 1}", tr.center.x, baseline, textPaint)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfidenceSliderBar(
    threshold: Float,
    hiddenCount: Int,
    onThresholdChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.confidence_label),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = KotenColor.ink
            )
            Spacer(modifier = Modifier.width(10.dp))
            // iOS-style slider: thin track, 朱 fill, white pill thumb, no tick marks
            val colors = SliderDefaults.colors(
                activeTrackColor = KotenColor.shu,
                inactiveTrackColor = KotenColor.ink.copy(alpha = 0.12f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            )
            Slider(
                value = threshold,
                onValueChange = onThresholdChange,
                valueRange = ConfidenceFilter.RANGE,
                steps = ConfidenceFilter.STEPS - 1,
                colors = colors,
                thumb = {
                    Box(
                        modifier = Modifier
                            .size(width = 38.dp, height = 24.dp)
                            .shadow(3.dp, RoundedCornerShape(50))
                            .background(Color.White, RoundedCornerShape(50))
                    )
                },
                track = { state ->
                    SliderDefaults.Track(
                        sliderState = state,
                        colors = colors,
                        modifier = Modifier.height(5.dp),
                        thumbTrackGapSize = 0.dp,
                        drawStopIndicator = null,
                        drawTick = { _, _ -> }
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
            )
            Text(
                text = ConfidenceFilter.percentText(threshold),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = KotenColor.ink,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 44.dp)
            )
        }
        Text(
            text = if (hiddenCount > 0) {
                stringResource(R.string.confidence_hint_hidden, hiddenCount)
            } else {
                stringResource(R.string.confidence_hint)
            },
            fontSize = 12.sp,
            color = KotenColor.inkSecondary
        )
    }
}

@Composable
private fun TabPill(
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
        shadowElevation = if (selected) 1.dp else 0.dp
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = KotenColor.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 5.dp)
        )
    }
}

@Composable
private fun ShareMenuItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(title, fontSize = 16.sp, color = KotenColor.ink) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = KotenColor.ink, modifier = Modifier.size(20.dp)) },
        onClick = onClick
    )
}

@Composable
private fun TranslationTabContent(
    isTranslating: Boolean,
    translationError: String?,
    translatedText: String?,
    canTranslate: Boolean,
    onTranslate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when {
            isTranslating -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = KotenColor.shu,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.translating),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = KotenColor.inkSecondary
                    )
                }
            }
            !translatedText.isNullOrEmpty() -> {
                KotenGlassButton(
                    onClick = onTranslate,
                    modifier = Modifier.height(40.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.retranslate_button),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(
                        text = translatedText,
                        style = minchoStyle(size = 17.sp, color = KotenColor.ink),
                        lineHeight = 29.sp
                    )
                }
            }
        }

        if (translationError != null) {
            Text(text = translationError, color = KotenColor.shu, fontSize = 15.sp)
        }

        if (translatedText == null && !isTranslating) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = null,
                    tint = KotenColor.inkSecondary,
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = stringResource(R.string.translation_empty),
                    style = minchoStyle(size = 17.sp, color = KotenColor.inkSecondary)
                )
                KotenProminentButton(
                    onClick = onTranslate,
                    enabled = canTranslate,
                    modifier = Modifier.height(48.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.translate_button), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun NoLinesPanel(
    onRetake: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = stringResource(R.string.no_lines_title),
            style = minchoStyle(size = 20.sp, bold = true, color = KotenColor.ink),
            textAlign = TextAlign.Center
        )

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = KotenColor.washiCard,
            border = BorderStroke(1.dp, KotenColor.rule),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TipRow(icon = Icons.Default.CropFree, text = stringResource(R.string.no_lines_tip_page))
                TipRow(icon = Icons.Default.WbSunny, text = stringResource(R.string.no_lines_tip_light))
                TipRow(icon = Icons.Default.Lightbulb, text = stringResource(R.string.no_lines_tip_modern))
            }
        }

        KotenProminentButton(
            onClick = onRetake,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.ocr_error_retake),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ErrorPanel(
    message: String,
    onRetake: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = minchoStyle(size = 17.sp, color = KotenColor.ink),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        KotenProminentButton(
            onClick = onRetake,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.ocr_error_retake),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TipRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = KotenColor.shu,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            color = KotenColor.ink
        )
    }
}
