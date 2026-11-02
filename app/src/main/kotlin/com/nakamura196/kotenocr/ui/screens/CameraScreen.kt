package com.nakamura196.kotenocr.ui.screens

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nakamura196.kotenocr.R
import com.nakamura196.kotenocr.data.CoachMark
import com.nakamura196.kotenocr.data.SettingsManager
import com.nakamura196.kotenocr.ui.theme.CoachBubble
import com.nakamura196.kotenocr.ui.theme.KotenColor
import com.nakamura196.kotenocr.ui.theme.KotenGlassButton
import com.nakamura196.kotenocr.ui.theme.KotenProminentButton
import com.nakamura196.kotenocr.ui.theme.ScanCorners
import com.nakamura196.kotenocr.ui.theme.WashiBackground
import com.nakamura196.kotenocr.ui.theme.minchoStyle
import com.nakamura196.kotenocr.util.CaptureMode
import com.nakamura196.kotenocr.util.DocumentScanner
import com.nakamura196.kotenocr.util.ImageIntake
import com.nakamura196.kotenocr.util.TiltMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import kotlin.math.roundToInt

/**
 * Camera viewfinder screen (`ContentView.cameraView` + `LevelGuideView` + `CameraDeniedView` port).
 */
@Composable
fun CameraScreen(
    settingsManager: SettingsManager,
    onImageCaptured: (Bitmap) -> Unit,
    onHomeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var flashEnabled by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    var showCaptureFailedAlert by remember { mutableStateOf(false) }
    var showShutterCoach by remember {
        mutableStateOf(!settingsManager.hasSeenCoachMark(CoachMark.SHUTTER))
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    ImageIntake.loadFromUri(context, uri)
                }
                if (bitmap != null) {
                    onImageCaptured(bitmap)
                }
            }
        }
    }

    // Document scanner (ML Kit, Google Play services). Cancelled or failed -> stay on the camera.
    val scannerSupported = remember { DocumentScanner.isSupported(context) }
    var scannerLaunched by remember { mutableStateOf(false) }
    var scannerHandled by rememberSaveable { mutableStateOf(false) }
    val scannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        scannerLaunched = false
        val uri = if (result.resultCode == android.app.Activity.RESULT_OK) {
            DocumentScanner.firstPageUri(result.data)
        } else null
        if (uri == null) {
            settingsManager.lastCaptureMode = CaptureMode.CAMERA
        } else {
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) { ImageIntake.loadFromUri(context, uri) }
                if (bitmap != null) {
                    settingsManager.lastCaptureMode = CaptureMode.SCAN
                    onImageCaptured(bitmap)
                } else {
                    settingsManager.lastCaptureMode = CaptureMode.CAMERA
                }
            }
        }
    }
    val openScanner: () -> Unit = openScanner@{
        val activity = context.findActivity() ?: return@openScanner
        if (scannerLaunched) return@openScanner
        scannerLaunched = true
        // Saved as SCAN only when a page actually comes back (iOS does the same).
        DocumentScanner.start(
            activity,
            onReady = { sender ->
                try {
                    scannerLauncher.launch(IntentSenderRequest.Builder(sender).build())
                } catch (_: Throwable) {
                    scannerLaunched = false
                    settingsManager.lastCaptureMode = CaptureMode.CAMERA
                }
            },
            onFailure = {
                scannerLaunched = false
                settingsManager.lastCaptureMode = CaptureMode.CAMERA
            }
        )
    }

    LaunchedEffect(Unit) {
        if (hasCameraPermission &&
            CaptureMode.shouldAutoOpenScanner(settingsManager.lastCaptureMode, scannerSupported, scannerHandled)
        ) {
            scannerHandled = true
            openScanner()
        }
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Re-check on every return to the app: the user may have granted the camera in the system
    // Settings app (iOS re-reads the status on scenePhase == .active). Revoking kills the process,
    // so only the denied → granted direction needs handling here.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Gravity / accelerometer tilt guide
    val tiltMonitor = remember { TiltMonitor(context) }
    val showTiltGuide = settingsManager.showTiltGuide

    DisposableEffect(hasCameraPermission, showTiltGuide) {
        if (hasCameraPermission && showTiltGuide) {
            tiltMonitor.start()
        }
        onDispose {
            tiltMonitor.stop()
        }
    }

    val tiltState by tiltMonitor.state.collectAsState()
    val gravity by tiltMonitor.gravity.collectAsState()

    if (!hasCameraPermission) {
        CameraDeniedView(
            onOpenSettings = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            },
            onPickPhoto = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onHomeClick = onHomeClick,
            onHistoryClick = onHistoryClick,
            onSettingsClick = onSettingsClick
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val previewView = remember { PreviewView(context) }

        DisposableEffect(hasCameraPermission) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture
                    )
                } catch (_: Exception) {
                }
            }, ContextCompat.getMainExecutor(context))

            onDispose {
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    cameraProvider.unbindAll()
                } catch (_: Exception) {
                }
            }
        }

        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Vermilion corner guide frame
        ScanCorners(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 120.dp)
        )

        // Top controls + Level guide + Bottom shutter bar
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar: Home | Spacer | Flash, History, Settings
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CameraRoundButton(onClick = onHomeClick) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = stringResource(R.string.home_button),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CameraRoundButton(
                        onClick = {
                            flashEnabled = !flashEnabled
                            imageCapture.flashMode = if (flashEnabled) {
                                ImageCapture.FLASH_MODE_ON
                            } else {
                                ImageCapture.FLASH_MODE_OFF
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = if (flashEnabled) {
                                stringResource(R.string.flash_on)
                            } else {
                                stringResource(R.string.flash_off)
                            },
                            tint = if (flashEnabled) Color(0xFFFFD54F) else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    CameraRoundButton(onClick = onHistoryClick) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = stringResource(R.string.history_button),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    CameraRoundButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_button),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Middle: Level guide + frame hint
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (showTiltGuide && tiltState.isActive) {
                    LevelGuideOverlay(
                        tiltState = tiltState,
                        gravity = gravity,
                        onTryScanner = if (scannerSupported) openScanner else null
                    )
                }

                if (!showTiltGuide || !tiltState.showsHint) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.55f)
                    ) {
                        Text(
                            text = stringResource(R.string.camera_frame_hint),
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Bottom bar:Shutter coach mark + Gallery / Shutter / Spacer
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                if (showShutterCoach) {
                    CoachBubble(
                        text = stringResource(R.string.coach_shutter),
                        onDismiss = {
                            settingsManager.markCoachMarkSeen(CoachMark.SHUTTER)
                            showShutterCoach = false
                        },
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CameraRoundButton(
                        size = 56.dp,
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = stringResource(R.string.gallery_button),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Vermilion shutter button
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .border(3.5.dp, Color.White, CircleShape)
                            .padding(6.dp)
                            .background(
                                color = if (isCapturing) {
                                    KotenColor.shuFill.copy(alpha = 0.5f)
                                } else {
                                    KotenColor.shuFill
                                },
                                shape = CircleShape
                            )
                            .clickable(enabled = !isCapturing) {
                                if (isCapturing) return@clickable
                                isCapturing = true
                                if (showShutterCoach) {
                                    settingsManager.markCoachMarkSeen(CoachMark.SHUTTER)
                                    showShutterCoach = false
                                }
                                val tempFile = File.createTempFile("capture_", ".jpg", context.cacheDir)
                                val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()
                                imageCapture.takePicture(
                                    outputOptions,
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                            scope.launch {
                                                val bmp = withContext(Dispatchers.IO) {
                                                    ImageIntake.loadFromFile(tempFile)
                                                }
                                                tempFile.delete()
                                                isCapturing = false
                                                if (bmp != null) {
                                                    if (settingsManager.saveToGallery) {
                                                        saveBitmapToGallery(context, bmp)
                                                    }
                                                    onImageCaptured(bmp)
                                                } else {
                                                    showCaptureFailedAlert = true
                                                }
                                            }
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            tempFile.delete()
                                            isCapturing = false
                                            showCaptureFailedAlert = true
                                        }
                                    }
                                )
                            }
                    )

                    if (scannerSupported) {
                        CameraRoundButton(size = 56.dp, onClick = openScanner) {
                            Icon(
                                imageVector = Icons.Default.DocumentScanner,
                                contentDescription = stringResource(R.string.scan_document),
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(56.dp))
                    }
                }
            }
        }
    }

    if (showCaptureFailedAlert) {
        AlertDialog(
            onDismissRequest = { showCaptureFailedAlert = false },
            text = { Text(stringResource(R.string.camera_capture_failed)) },
            confirmButton = {
                TextButton(onClick = { showCaptureFailedAlert = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
}

@Composable
private fun CameraRoundButton(
    size: androidx.compose.ui.unit.Dp = 44.dp,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = Color(0.14f, 0.12f, 0.11f, 0.85f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
private fun LevelGuideOverlay(
    tiltState: TiltMonitor.TiltState,
    gravity: TiltMonitor.Gravity,
    onTryScanner: (() -> Unit)? = null
) {
    val shuColor = Color(0xFFE0684C)
    val ringColor = if (tiltState.showsHint) shuColor else Color.White.copy(alpha = 0.85f)
    val dotColor = if (tiltState.showsHint) shuColor else Color.White

    val dx = (gravity.x * 3.0).coerceIn(-1.0, 1.0)
    val dy = ((if (tiltState is TiltMonitor.TiltState.Upright) gravity.z else -gravity.y) * 3.0)
        .coerceIn(-1.0, 1.0)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier.size(44.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = size.minDimension * 0.5f
                drawCircle(color = Color.Black.copy(alpha = 0.35f), radius = r)
                drawCircle(
                    color = ringColor,
                    radius = r,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.45f),
                    start = Offset(r, r - 6.dp.toPx()),
                    end = Offset(r, r + 6.dp.toPx()),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.45f),
                    start = Offset(r - 6.dp.toPx(), r),
                    end = Offset(r + 6.dp.toPx(), r),
                    strokeWidth = 1.dp.toPx()
                )
            }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (dx * 13.dp.toPx()).roundToInt(),
                            y = (dy * 13.dp.toPx()).roundToInt()
                        )
                    }
                    .size(12.dp)
                    .background(dotColor, CircleShape)
            )
        }

        if (tiltState.showsHint) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, shuColor.copy(alpha = 0.7f))
            ) {
                Text(
                    text = stringResource(R.string.tilt_hint),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
            if (onTryScanner != null) {
                Surface(
                    onClick = onTryScanner,
                    shape = CircleShape,
                    color = Color(0.14f, 0.12f, 0.11f, 0.85f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DocumentScanner,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.tilt_try_scanner),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

private fun android.content.Context.findActivity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
private fun CameraDeniedView(
    onOpenSettings: () -> Unit,
    onPickPhoto: () -> Unit,
    onHomeClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    WashiBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KotenGlassButton(
                    onClick = onHomeClick,
                    isCircle = true,
                    modifier = Modifier.size(44.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = stringResource(R.string.home_button),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                KotenGlassButton(
                    onClick = onHistoryClick,
                    isCircle = true,
                    modifier = Modifier.size(44.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = stringResource(R.string.history_button),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                KotenGlassButton(
                    onClick = onSettingsClick,
                    isCircle = true,
                    modifier = Modifier.size(44.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.settings_button),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = KotenColor.shu,
                    modifier = Modifier.size(44.dp)
                )
                Text(
                    text = stringResource(R.string.camera_unavailable_title),
                    style = minchoStyle(size = 24.sp, bold = true, color = KotenColor.ink),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.camera_unavailable_body),
                    fontSize = 15.sp,
                    color = KotenColor.inkSecondary,
                    textAlign = TextAlign.Center
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KotenProminentButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Text(
                        text = stringResource(R.string.camera_open_settings),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                KotenGlassButton(
                    onClick = onPickPhoto,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.home_choose_photo),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

private fun saveBitmapToGallery(context: android.content.Context, bitmap: Bitmap) {
    try {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "KotenOCR_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/KotenOCR")
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            val stream: OutputStream? = context.contentResolver.openOutputStream(uri)
            stream?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        }
    } catch (_: Exception) {
    }
}
