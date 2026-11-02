package com.nakamura196.kotenocr

import android.content.ComponentCallbacks2
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.nakamura196.kotenocr.ui.screens.CameraScreen
import com.nakamura196.kotenocr.ui.screens.ConfirmCropScreen
import com.nakamura196.kotenocr.ui.screens.CropScreen
import com.nakamura196.kotenocr.ui.screens.HistoryScreen
import com.nakamura196.kotenocr.ui.screens.HomeScreen
import com.nakamura196.kotenocr.ui.screens.LaunchScreen
import com.nakamura196.kotenocr.ui.screens.OnboardingScreen
import com.nakamura196.kotenocr.ui.screens.ProcessingScreen
import com.nakamura196.kotenocr.ui.screens.ResultScreen
import com.nakamura196.kotenocr.ui.screens.SampleSheet
import com.nakamura196.kotenocr.ui.screens.SettingsScreen
import com.nakamura196.kotenocr.ui.screens.TranslationSettingsScreen
import com.nakamura196.kotenocr.ui.theme.KotenOCRTheme
import com.nakamura196.kotenocr.util.ImageIntake
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var appState: AppState

    override fun onCreate(savedInstanceState: Bundle?) {
        // Swaps Theme.KotenOCR.Splash for postSplashScreenTheme (NoActionBar).
        // Without this the splash theme's action bar stays on screen.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        appState = AppState(applicationContext, lifecycleScope)

        setContent {
            val themeMode by appState.themeMode.collectAsState()
            KotenOCRTheme(themeMode = themeMode) {
                KotenOCRApp(appState)
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW && ::appState.isInitialized) {
            appState.onTrimMemory()
        }
    }
}

@Composable
private fun KotenOCRApp(appState: AppState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val screen by appState.screen.collectAsState()
    val animateLaunchStamp by appState.animateLaunchStamp.collectAsState()
    val initError by appState.initError.collectAsState()
    val ocrError by appState.ocrError.collectAsState()
    val capturedBitmap by appState.capturedBitmap.collectAsState()
    val croppedBitmap by appState.croppedBitmap.collectAsState()
    val detections by appState.detections.collectAsState()
    val ocrText by appState.ocrText.collectAsState()
    val translatedText by appState.translatedText.collectAsState()
    val translatingPages by appState.translatingPages.collectAsState()
    val translationErrors by appState.translationErrors.collectAsState()
    val currentPageKey by appState.currentPageKey.collectAsState()
    val ocrMode by appState.ocrMode.collectAsState()
    val scanProgress by appState.scanProgress.collectAsState()
    val scanRevealedDetections by appState.scanRevealedDetections.collectAsState()
    val scanRevealedCount by appState.scanRevealedCount.collectAsState()
    val showCompletionStamp by appState.showCompletionStamp.collectAsState()
    val resultFromHistory by appState.resultFromHistory.collectAsState()

    var showSampleSheet by remember { mutableStateOf(false) }

    val homePhotoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) {
                    ImageIntake.loadFromUri(context, uri)
                }
                if (bmp != null) {
                    appState.onImageSelected(bmp)
                }
            }
        }
    }

    BackHandler(enabled = screen != Screen.HOME && screen != Screen.LAUNCH && screen != Screen.ONBOARDING) {
        when (screen) {
            Screen.TRANSLATION_SETTINGS -> appState.navigateBackFromTranslationSettings()
            Screen.SETTINGS, Screen.HISTORY -> appState.closeModalScreen()
            Screen.CONFIRM_CROP -> appState.reCrop()
            Screen.CROP -> appState.navigateToCamera()
            Screen.PROCESSING -> appState.cancelProcessing()
            Screen.RESULT -> appState.backFromResult()
            Screen.CAMERA -> appState.navigateToHome()
            else -> Unit
        }
    }

    when (screen) {
        Screen.LAUNCH -> {
            LaunchScreen(
                animateStamp = animateLaunchStamp,
                errorMessage = initError,
                onRetry = { appState.initModels() }
            )
        }

        Screen.ONBOARDING -> {
            OnboardingScreen(
                onComplete = { appState.completeOnboarding() }
            )
        }

        Screen.HOME -> {
            HomeScreen(
                historyManager = appState.historyManager,
                onTakePhoto = { appState.navigateToCamera() },
                onPickPhoto = {
                    homePhotoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onShowHistory = { appState.navigateToHistory() },
                onShowSettings = { appState.navigateToSettings() },
                onSelectRecent = { item -> appState.viewHistoryItem(item) },
                onTrySample = { showSampleSheet = true }
            )

            if (showSampleSheet) {
                SampleSheet(
                    onSelect = { sample ->
                        showSampleSheet = false
                        appState.runSamplePage(sample)
                    },
                    onDismiss = { showSampleSheet = false }
                )
            }
        }

        Screen.CAMERA -> {
            CameraScreen(
                settingsManager = appState.settingsManager,
                onImageCaptured = { bmp -> appState.onImageSelected(bmp) },
                onHomeClick = { appState.navigateToHome() },
                onSettingsClick = { appState.navigateToSettings() },
                onHistoryClick = { appState.navigateToHistory() }
            )
        }

        Screen.CROP -> {
            val bmp = capturedBitmap
            if (bmp != null) {
                CropScreen(
                    bitmap = bmp,
                    settingsManager = appState.settingsManager,
                    onCropped = { cropped, rotatedSource ->
                        appState.onImageCropped(cropped, rotatedSource)
                    },
                    onSkip = { rotatedSource ->
                        appState.onCropSkipped(rotatedSource)
                    },
                    onCancel = { appState.navigateToCamera() }
                )
            }
        }

        Screen.CONFIRM_CROP -> {
            val bmp = croppedBitmap ?: capturedBitmap
            if (bmp != null) {
                ConfirmCropScreen(
                    bitmap = bmp,
                    initialMode = ocrMode,
                    onRunOCR = { mode -> appState.runOCR(mode) },
                    onReCrop = { appState.reCrop() },
                    onRetake = { appState.navigateToCamera() }
                )
            }
        }

        Screen.PROCESSING -> {
            ProcessingScreen(
                bitmap = croppedBitmap ?: capturedBitmap,
                progress = scanProgress,
                revealedDetections = scanRevealedDetections,
                revealedCount = scanRevealedCount,
                onCancel = { appState.cancelProcessing() }
            )
        }

        Screen.RESULT -> {
            val bmp = croppedBitmap ?: capturedBitmap
            if (bmp != null) {
                ResultScreen(
                    bitmap = bmp,
                    initialDetections = detections,
                    initialText = ocrText,
                    translatedText = translatedText,
                    isTranslating = (currentPageKey ?: AppState.NO_HISTORY_PAGE) in translatingPages,
                    translationError = translationErrors[currentPageKey ?: AppState.NO_HISTORY_PAGE],
                    showStampInitial = showCompletionStamp,
                    errorMessage = ocrError,
                    settingsManager = appState.settingsManager,
                    fromHistory = resultFromHistory,
                    onBack = { appState.backFromResult() },
                    onReCrop = { appState.reCrop() },
                    onRetake = { appState.navigateToCamera() },
                    onTranslate = { text -> appState.translateCurrentPage(text) },
                    onDetectionsUpdated = { updatedDets, updatedText ->
                        appState.updateCurrentDetections(updatedDets, updatedText)
                    },
                    onRereadBoxes = { boxes ->
                        appState.rereadBoxes(boxes)
                    },
                    onOrderBoxes = { boxes, w, h ->
                        appState.orderBoxes(boxes, w, h)
                    },
                    onOpenSettings = {
                        appState.navigateToTranslationSettings()
                    }
                )
            }
        }

        Screen.HISTORY -> {
            HistoryScreen(
                historyManager = appState.historyManager,
                onSelectItem = { item -> appState.viewHistoryItem(item) },
                onStartCamera = { appState.navigateToCamera() },
                onClose = { appState.closeModalScreen() }
            )
        }

        Screen.SETTINGS -> {
            SettingsScreen(
                settingsManager = appState.settingsManager,
                onThemeChanged = { mode -> appState.updateTheme(mode) },
                onNavigateToTranslationSettings = { appState.navigateToTranslationSettings() },
                onShowOnboarding = { appState.replayOnboarding() },
                onClose = { appState.closeModalScreen() }
            )
        }

        Screen.TRANSLATION_SETTINGS -> {
            TranslationSettingsScreen(
                settingsManager = appState.settingsManager,
                onBack = { appState.navigateBackFromTranslationSettings() }
            )
        }
    }
}
