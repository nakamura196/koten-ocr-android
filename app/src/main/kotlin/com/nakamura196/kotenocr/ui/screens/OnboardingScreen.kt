package com.nakamura196.kotenocr.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nakamura196.kotenocr.R
import com.nakamura196.kotenocr.ui.theme.KotenColor
import com.nakamura196.kotenocr.ui.theme.KotenGlassButton
import com.nakamura196.kotenocr.ui.theme.KotenProminentButton
import com.nakamura196.kotenocr.ui.theme.WashiBackground
import com.nakamura196.kotenocr.ui.theme.kotenMount
import com.nakamura196.kotenocr.ui.theme.minchoStyle
import com.nakamura196.kotenocr.util.ImageIntake
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 3-page Washi onboarding screen (`OnboardingView.swift` v2.1 port):
 * - Page 1: Replayable Genji manuscript scan animation (`onboarding_sample.jpg` + `onboarding_sample_ocr.json`)
 * - Page 2: Camera permission request / status
 * - Page 3: 3-step How to Use guide (including choosing photos from the library)
 */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onCancel: (() -> Unit)? = null
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Re-read on resume: granting the permission in the system Settings app
    // and coming back must update page 2.
    var hasCameraPermission by rememberCameraPermissionState()

    // System back: previous page; on the first page, leave the replay (from
    // Settings). On first run with no onCancel, the system default closes the app.
    BackHandler(enabled = pagerState.currentPage > 0 || onCancel != null) {
        if (pagerState.currentPage > 0) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        } else {
            onCancel?.invoke()
        }
    }
    var permissionRequested by remember { mutableStateOf(false) }
    var introFinished by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        permissionRequested = true
    }

    WashiBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> IntroPage(
                        introFinished = introFinished,
                        onFinished = { introFinished = true }
                    )
                    1 -> CameraPermissionPage(
                        hasCameraPermission = hasCameraPermission,
                        permissionDenied = permissionRequested && !hasCameraPermission,
                        onRequestPermission = {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        onOpenSettings = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                    )
                    2 -> HowToUsePage()
                }
            }

            // Page indicators
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(3) { index ->
                    val active = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (active) 9.dp else 7.dp)
                            .background(
                                color = if (active) KotenColor.shu else KotenColor.rule,
                                shape = CircleShape
                            )
                    )
                }
            }

            // Next / Start button
            val showButton = pagerState.currentPage > 0 || introFinished
            KotenProminentButton(
                onClick = {
                    if (pagerState.currentPage < 2) {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    } else {
                        onComplete()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
                    .height(56.dp)
                    .alpha(if (showButton) 1f else 0f),
                enabled = showButton
            ) {
                Text(
                    text = if (pagerState.currentPage < 2) {
                        stringResource(R.string.onboarding_next)
                    } else {
                        stringResource(R.string.onboarding_start)
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private data class SampleLine(
    val box: FloatArray,
    val text: String
)

@Composable
private fun IntroPage(
    introFinished: Boolean,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var sampleLines by remember { mutableStateOf<List<SampleLine>>(emptyList()) }
    val sweep = remember { Animatable(0f) }
    var revealed by remember { mutableIntStateOf(0) }
    var showLines by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            val bmp = ImageIntake.loadFromAsset(context, "samples/onboarding_sample.jpg", maxLongSide = 1024)
            val lines = try {
                val jsonStr = context.assets.open("samples/onboarding_sample_ocr.json")
                    .bufferedReader().use { it.readText() }
                val root = JSONObject(jsonStr)
                val arr = root.getJSONArray("lines")
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val b = obj.getJSONArray("box")
                        add(
                            SampleLine(
                                box = floatArrayOf(
                                    b.getDouble(0).toFloat(),
                                    b.getDouble(1).toFloat(),
                                    b.getDouble(2).toFloat(),
                                    b.getDouble(3).toFloat()
                                ),
                                text = obj.getString("text")
                            )
                        )
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
            bmp to lines
        }
        bitmap = loaded.first
        sampleLines = loaded.second

        delay(400)
        sweep.animateTo(1f, animationSpec = tween(durationMillis = 800, easing = LinearEasing))
        val count = sampleLines.size
        val stepMs = if (count > 0) (800L / count).coerceAtMost(80L) else 0L
        for (i in 1..count) {
            delay(stepMs)
            revealed = i
        }
        delay(100)
        showLines = true
        delay(250)
        onFinished()
    }

    val grayscaleFilter = remember {
        val cm = ColorMatrix().apply { setToSaturation(0f) }
        ColorFilter.colorMatrix(cm)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val bmp = bitmap
        if (bmp != null) {
            val aspect = bmp.width.toFloat() / bmp.height.toFloat().coerceAtLeast(1f)
            val shuColor = KotenColor.shu
            val shuFill = KotenColor.shuFill
            Box(
                modifier = Modifier
                    .height(290.dp)
                    .aspectRatio(aspect)
                    .kotenMount(padding = 6.dp)
            ) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    colorFilter = grayscaleFilter,
                    modifier = Modifier.fillMaxSize()
                )
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val scanned = revealed > 0
                    val s = if (scanned) 1f else sweep.value
                    val beamX = size.width * (1f - s)

                    // Draw beam while sweeping right-to-left
                    if (!scanned && s > 0f && s < 1f) {
                        drawLine(
                            color = shuColor,
                            start = Offset(beamX, 0f),
                            end = Offset(beamX, size.height),
                            strokeWidth = 2.5.dp.toPx()
                        )
                    }

                    // Draw revealed line boxes in reading order
                    for (i in 0 until minOf(revealed, sampleLines.size)) {
                        val b = sampleLines[i].box
                        val left = b[0] * size.width
                        val top = b[1] * size.height
                        val w = (b[2] - b[0]) * size.width
                        val h = (b[3] - b[1]) * size.height
                        val isNewest = i == revealed - 1
                        drawRoundRect(
                            color = shuFill.copy(alpha = if (isNewest) 0.25f else 0.14f),
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

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.onboarding_sample_credit),
                fontSize = 11.sp,
                color = KotenColor.inkSecondary,
                textAlign = TextAlign.Center
            )
        }

        AnimatedVisibility(visible = showLines && sampleLines.isNotEmpty(), enter = fadeIn()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                shape = RoundedCornerShape(14.dp),
                color = KotenColor.washiCard,
                border = BorderStroke(1.dp, KotenColor.rule)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    sampleLines.take(3).forEachIndexed { idx, line ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${idx + 1}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = KotenColor.shu
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = line.text,
                                style = minchoStyle(size = 15.sp, color = KotenColor.ink),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = stringResource(R.string.onboarding_heading),
            style = minchoStyle(size = 28.sp, bold = true, color = KotenColor.ink),
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(if (introFinished) 1f else 0f)
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = stringResource(R.string.onboarding_intro),
            fontSize = 15.sp,
            color = KotenColor.inkSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(if (introFinished) 1f else 0f)
        )
    }
}

@Composable
private fun CameraPermissionPage(
    hasCameraPermission: Boolean,
    permissionDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (hasCameraPermission) Icons.Default.CheckCircle else Icons.Default.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = KotenColor.shu
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onboarding_camera_title),
            style = minchoStyle(size = 26.sp, bold = true, color = KotenColor.ink)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_camera_description),
            fontSize = 15.sp,
            color = KotenColor.inkSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        when {
            hasCameraPermission -> {
                Text(
                    text = stringResource(R.string.onboarding_camera_granted),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = KotenColor.shu
                )
            }
            permissionDenied -> {
                Text(
                    text = stringResource(R.string.onboarding_camera_denied),
                    fontSize = 14.sp,
                    color = KotenColor.shu
                )
                Spacer(modifier = Modifier.height(12.dp))
                KotenGlassButton(onClick = onOpenSettings) {
                    Text(text = stringResource(R.string.camera_open_settings))
                }
            }
            else -> {
                KotenGlassButton(onClick = onRequestPermission) {
                    Text(text = stringResource(R.string.onboarding_allow_camera))
                }
            }
        }
    }
}

@Composable
private fun HowToUsePage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.onboarding_howto_title),
            style = minchoStyle(size = 26.sp, bold = true, color = KotenColor.ink)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
            HowToStep(
                icon = Icons.Default.CameraAlt,
                title = stringResource(R.string.onboarding_step1_title),
                description = stringResource(R.string.onboarding_step1_desc)
            )
            HowToStep(
                icon = Icons.Default.TextFields,
                title = stringResource(R.string.onboarding_step2_title),
                description = stringResource(R.string.onboarding_step2_desc)
            )
            HowToStep(
                icon = Icons.Default.ContentCopy,
                title = stringResource(R.string.onboarding_step3_title),
                description = stringResource(R.string.onboarding_step3_desc)
            )
        }
    }
}

@Composable
private fun HowToStep(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = KotenColor.shu
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                style = minchoStyle(size = 18.sp, bold = true, color = KotenColor.ink)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                fontSize = 14.sp,
                color = KotenColor.inkSecondary
            )
        }
    }
}
