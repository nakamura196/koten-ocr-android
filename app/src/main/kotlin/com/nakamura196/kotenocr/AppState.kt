package com.nakamura196.kotenocr

import android.content.Context
import android.graphics.Bitmap
import com.nakamura196.kotenocr.data.HistoryManager
import com.nakamura196.kotenocr.data.PageTranslations
import com.nakamura196.kotenocr.data.SettingsManager
import com.nakamura196.kotenocr.data.TranslationException
import com.nakamura196.kotenocr.data.TranslationService
import com.nakamura196.kotenocr.data.ThemeMode
import com.nakamura196.kotenocr.models.HistoryItem
import com.nakamura196.kotenocr.ocr.Detection
import com.nakamura196.kotenocr.ocr.OCREngine
import com.nakamura196.kotenocr.ocr.OCRMode
import com.nakamura196.kotenocr.ui.screens.SamplePage
import com.nakamura196.kotenocr.util.ImageIntake
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class Screen {
    LAUNCH,
    ONBOARDING,
    HOME,
    CAMERA,
    CROP,
    CONFIRM_CROP,
    PROCESSING,
    RESULT,
    HISTORY,
    SETTINGS,
    TRANSLATION_SETTINGS
}

/**
 * Central application state machine (`ContentView.swift` v2.1 parity).
 */
class AppState(
    private val context: Context,
    private val scope: CoroutineScope
) {
    val settingsManager = SettingsManager(context)
    val historyManager = HistoryManager(context)

    private var ocrEngine: OCREngine? = null
    private var ocrJob: Job? = null

    private val _screen = MutableStateFlow(Screen.LAUNCH)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _animateLaunchStamp = MutableStateFlow(!settingsManager.hasSeenLaunchStamp)
    val animateLaunchStamp: StateFlow<Boolean> = _animateLaunchStamp.asStateFlow()

    private val _themeMode = MutableStateFlow(settingsManager.themeMode)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _ocrMode = MutableStateFlow(settingsManager.preferredOCRMode)
    val ocrMode: StateFlow<OCRMode> = _ocrMode.asStateFlow()

    private val _capturedBitmap = MutableStateFlow<Bitmap?>(null)
    val capturedBitmap: StateFlow<Bitmap?> = _capturedBitmap.asStateFlow()

    private val _croppedBitmap = MutableStateFlow<Bitmap?>(null)
    val croppedBitmap: StateFlow<Bitmap?> = _croppedBitmap.asStateFlow()

    private val _detections = MutableStateFlow<List<Detection>>(emptyList())
    val detections: StateFlow<List<Detection>> = _detections.asStateFlow()

    private val _ocrText = MutableStateFlow("")
    val ocrText: StateFlow<String> = _ocrText.asStateFlow()

    private val _translatedText = MutableStateFlow<String?>(null)
    val translatedText: StateFlow<String?> = _translatedText.asStateFlow()

    private val _currentHistoryItemId = MutableStateFlow<String?>(null)

    private val _scanProgress = MutableStateFlow(0f)
    val scanProgress: StateFlow<Float> = _scanProgress.asStateFlow()

    private val _scanRevealedDetections = MutableStateFlow<List<Detection>>(emptyList())
    val scanRevealedDetections: StateFlow<List<Detection>> = _scanRevealedDetections.asStateFlow()

    private val _scanRevealedCount = MutableStateFlow(0)
    val scanRevealedCount: StateFlow<Int> = _scanRevealedCount.asStateFlow()

    private val _showCompletionStamp = MutableStateFlow(false)
    val showCompletionStamp: StateFlow<Boolean> = _showCompletionStamp.asStateFlow()

    /** The result on screen was opened from History (iOS `cameFromHistory`). */
    private val _resultFromHistory = MutableStateFlow(false)
    val resultFromHistory: StateFlow<Boolean> = _resultFromHistory.asStateFlow()

    private val _initError = MutableStateFlow<String?>(null)
    val initError: StateFlow<String?> = _initError.asStateFlow()

    private val _ocrError = MutableStateFlow<String?>(null)
    val ocrError: StateFlow<String?> = _ocrError.asStateFlow()

    private var previousScreenBeforeModal: Screen = Screen.HOME
    private var onboardingReplayMode: Boolean = false
    val isReplayingOnboarding: Boolean get() = onboardingReplayMode

    init {
        // Android 13+ restores the per-app language on its own; re-applying here would
        // overwrite a choice made in the system Settings.
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            settingsManager.applyLanguage()
        }
        initModels()
    }

    fun initModels() {
        _initError.value = null
        _screen.value = Screen.LAUNCH
        val shouldPauseForStamp = !settingsManager.hasSeenLaunchStamp
        _animateLaunchStamp.value = shouldPauseForStamp

        scope.launch {
            try {
                val engine = withContext(Dispatchers.IO) {
                    val modelsDir = copyModelsToCache(context)
                    OCREngine(modelsDir, initialMode = settingsManager.preferredOCRMode)
                }
                ocrEngine = engine
                if (shouldPauseForStamp) {
                    settingsManager.hasSeenLaunchStamp = true
                    delay(650)
                }
                if (settingsManager.hasCompletedOnboarding) {
                    _screen.value = Screen.HOME
                } else {
                    onboardingReplayMode = false
                    _screen.value = Screen.ONBOARDING
                }
            } catch (e: Exception) {
                android.util.Log.e("OCREngine", "Model init failed", e)
                _initError.value = context.getString(R.string.ocr_error_model)
            }
        }
    }

    fun completeOnboarding() {
        settingsManager.hasCompletedOnboarding = true
        if (onboardingReplayMode) {
            onboardingReplayMode = false
            _screen.value = Screen.SETTINGS
        } else {
            _screen.value = Screen.HOME
        }
    }

    /** System back on the first onboarding page while replaying from Settings. */
    fun cancelOnboardingReplay() {
        if (!onboardingReplayMode) return
        onboardingReplayMode = false
        _screen.value = Screen.SETTINGS
    }

    fun replayOnboarding() {
        onboardingReplayMode = true
        _screen.value = Screen.ONBOARDING
    }

    fun updateTheme(mode: ThemeMode) {
        _themeMode.value = mode
    }

    fun navigateToHome() {
        clearPageImages()
        _screen.value = Screen.HOME
    }

    fun navigateToCamera() {
        clearPageImages()
        _screen.value = Screen.CAMERA
    }

    fun onImageSelected(bitmap: Bitmap) {
        val limited = ImageIntake.limit(bitmap)
        clearPageImages()
        _capturedBitmap.value = limited
        _screen.value = Screen.CROP
    }

    fun onImageCropped(cropped: Bitmap, rotatedSource: Bitmap) {
        _capturedBitmap.value = rotatedSource
        _croppedBitmap.value = cropped
        _screen.value = Screen.CONFIRM_CROP
    }

    fun onCropSkipped(rotatedSource: Bitmap) {
        _capturedBitmap.value = rotatedSource
        _croppedBitmap.value = rotatedSource
        _screen.value = Screen.CONFIRM_CROP
    }

    fun reCrop() {
        _croppedBitmap.value = null
        _screen.value = Screen.CROP
    }

    /**
     * Runs OCR on the confirmed crop and saves the chosen [mode] as the user's preferred mode.
     */
    fun runOCR(mode: OCRMode) {
        settingsManager.preferredOCRMode = mode
        _ocrMode.value = mode
        val bmp = _croppedBitmap.value ?: _capturedBitmap.value ?: return
        startScanPipeline(bmp, mode)
    }

    /**
     * Loads and immediately reads a bundled [SamplePage] without overwriting `preferredOCRMode`.
     */
    fun runSamplePage(sample: SamplePage) {
        scope.launch {
            val bmp = withContext(Dispatchers.IO) {
                ImageIntake.loadFromAsset(context, sample.assetPath)
            } ?: return@launch
            clearPageImages()
            _capturedBitmap.value = bmp
            _croppedBitmap.value = bmp
            _ocrMode.value = sample.mode
            startScanPipeline(bmp, sample.mode)
        }
    }

    private fun startScanPipeline(bitmap: Bitmap, mode: OCRMode) {
        ocrJob?.cancel()
        _resultFromHistory.value = false
        _scanProgress.value = 0.05f
        _scanRevealedDetections.value = emptyList()
        _scanRevealedCount.value = 0
        _ocrError.value = null
        _screen.value = Screen.PROCESSING

        ocrJob = scope.launch {
            try {
                val engine = ocrEngine ?: withContext(Dispatchers.IO) {
                    val modelsDir = copyModelsToCache(context)
                    OCREngine(modelsDir, initialMode = mode).also { ocrEngine = it }
                }
                withContext(Dispatchers.IO) {
                    engine.switchMode(mode)
                }

                val result = engine.process(
                    bitmap = bitmap,
                    autoDeskew = settingsManager.autoDeskew,
                    onProgress = { p -> _scanProgress.value = p }
                )

                // Store deskewed image so overlay boxes, Box Editor, History, and Export match
                _croppedBitmap.value = result.image
                _detections.value = result.detections
                _ocrText.value = result.text
                _translatedText.value = null

                val saved = historyManager.save(result.image, result.detections, result.text)
                _currentHistoryItemId.value = saved.id

                // Line-by-line box & text reveal animation on ProcessingScreen
                _scanProgress.value = 1f
                if (result.detections.isNotEmpty()) {
                    _scanRevealedDetections.value = result.detections
                    val total = result.detections.size
                    val stepMs = (800L / total).coerceAtMost(40L).coerceAtLeast(12L)
                    for (i in 1..total) {
                        delay(stepMs)
                        _scanRevealedCount.value = i
                    }
                    delay(350)
                }

                _showCompletionStamp.value = result.detections.isNotEmpty()
                _screen.value = Screen.RESULT
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("OCREngine", "OCR pipeline failed", e)
                _ocrError.value = context.getString(R.string.ocr_error_generic)
                _detections.value = emptyList()
                _ocrText.value = ""
                _showCompletionStamp.value = false
                _screen.value = Screen.RESULT
            }
        }
    }

    fun cancelProcessing() {
        ocrJob?.cancel()
        ocrJob = null
        _scanRevealedDetections.value = emptyList()
        _scanRevealedCount.value = 0
        // Same as iOS ContentView.cancelProcessing: back to the camera to take the page again.
        navigateToCamera()
    }

    fun updateCurrentDetections(updatedDetections: List<Detection>, updatedText: String) {
        _detections.value = updatedDetections
        _ocrText.value = updatedText
        val itemId = _currentHistoryItemId.value ?: return
        historyManager.updateDetections(itemId, updatedDetections, updatedText)
    }

    /**
     * Translations run here, not in the result screen, so leaving the screen no longer cancels
     * them; the result is saved to the page's history item when it arrives.
     */
    private val translations = PageTranslations(
        scope = scope,
        translate = { text ->
            TranslationService.translate(
                text = text,
                apiKey = settingsManager.apiKey,
                endpoint = settingsManager.apiEndpoint,
                model = settingsManager.apiModel,
                targetLanguage = settingsManager.targetLanguage,
                level = settingsManager.translationLevel
            )
        },
        errorMessage = ::translationErrorMessage,
        onTranslated = { key, result ->
            if (key != NO_HISTORY_PAGE) historyManager.updateTranslation(key, result)
            if ((_currentHistoryItemId.value ?: NO_HISTORY_PAGE) == key) _translatedText.value = result
        }
    )
    val translatingPages: StateFlow<Set<String>> = translations.running
    val translationErrors: StateFlow<Map<String, String>> = translations.errors

    /** Id of the page on screen, the key for [translatingPages] / [translationErrors]. */
    val currentPageKey: StateFlow<String?> = _currentHistoryItemId.asStateFlow()

    fun translateCurrentPage(text: String) {
        translations.start(_currentHistoryItemId.value ?: NO_HISTORY_PAGE, text)
    }

    private fun translationErrorMessage(e: Exception): String = when (e) {
        is TranslationException -> when (e.errorCode) {
            TranslationException.ErrorCode.NO_KEY -> context.getString(R.string.translation_error_no_key)
            TranslationException.ErrorCode.TOO_LONG -> context.getString(R.string.translation_error_too_long)
            TranslationException.ErrorCode.SERVER -> "${context.getString(R.string.translation_error_server)}: ${e.message}"
            TranslationException.ErrorCode.INVALID_RESPONSE -> context.getString(R.string.translation_error_invalid)
        }
        else -> e.localizedMessage ?: context.getString(R.string.translation_error_server)
    }

    suspend fun rereadBoxes(boxes: List<Detection>): List<Detection> {
        val engine = ocrEngine ?: return boxes
        val bmp = _croppedBitmap.value ?: _capturedBitmap.value ?: return boxes
        return engine.recognizeBoxes(
            boxes = boxes,
            bitmap = bmp,
            mode = _ocrMode.value,
            lineTiltEnabled = settingsManager.autoDeskew
        )
    }

    fun orderBoxes(boxes: List<Detection>, width: Int, height: Int): List<Detection> {
        return ocrEngine?.orderReading(boxes, width, height) ?: boxes
    }

    fun viewHistoryItem(item: HistoryItem) {
        val bmp = historyManager.loadImage(item) ?: return
        _capturedBitmap.value = bmp
        _croppedBitmap.value = bmp
        _detections.value = item.detections
        _ocrText.value = item.text
        _translatedText.value = item.translatedText
        _currentHistoryItemId.value = item.id
        _ocrError.value = null
        _showCompletionStamp.value = false
        _resultFromHistory.value = true
        _screen.value = Screen.RESULT
    }

    /**
     * "Back" on the result (iOS `backFromResult`): to History when the result came from there,
     * otherwise to the confirm screen with the same page, so another mode can be tried.
     */
    fun backFromResult() {
        if (_resultFromHistory.value) {
            _resultFromHistory.value = false
            navigateToHome()
            navigateToHistory()
            return
        }
        if (_croppedBitmap.value == null && _capturedBitmap.value == null) {
            navigateToCamera()
            return
        }
        _screen.value = Screen.CONFIRM_CROP
    }

    fun navigateToHistory() {
        if (_screen.value != Screen.HISTORY) {
            previousScreenBeforeModal = _screen.value
        }
        _screen.value = Screen.HISTORY
    }

    fun navigateToSettings() {
        if (_screen.value != Screen.SETTINGS && _screen.value != Screen.TRANSLATION_SETTINGS) {
            previousScreenBeforeModal = _screen.value
        }
        _screen.value = Screen.SETTINGS
    }

    /** Screen that opened TRANSLATION_SETTINGS (SETTINGS, or RESULT via the API-key prompt). */
    private var translationSettingsOrigin: Screen = Screen.SETTINGS

    fun navigateToTranslationSettings() {
        val from = _screen.value
        if (from != Screen.TRANSLATION_SETTINGS) {
            translationSettingsOrigin = from
        }
        _screen.value = Screen.TRANSLATION_SETTINGS
    }

    fun navigateBackFromTranslationSettings() {
        _screen.value = when (translationSettingsOrigin) {
            Screen.RESULT -> Screen.RESULT
            else -> Screen.SETTINGS
        }
    }

    fun closeModalScreen() {
        _screen.value = when (previousScreenBeforeModal) {
            Screen.CAMERA, Screen.RESULT, Screen.HOME -> previousScreenBeforeModal
            else -> Screen.HOME
        }
    }

    fun onTrimMemory() {
        ocrEngine?.releaseUnusedModels()
    }

    private fun clearPageImages() {
        _capturedBitmap.value = null
        _croppedBitmap.value = null
        _detections.value = emptyList()
        _ocrText.value = ""
        _translatedText.value = null
        _ocrError.value = null
        _showCompletionStamp.value = false
    }

    companion object {
        /** Translation key for a page that has no history item (should not happen after OCR). */
        const val NO_HISTORY_PAGE = ""

        private val MODEL_FILES = listOf(
            "rtmdet-s-1280x1280.onnx",
            "parseq-ndl-32x384-tiny-10.onnx",
            "NDLmoji.yaml",
            "ndl.yaml",
            "deim-s-1024x1024.onnx",
            "ndl-deim.yaml",
            CascadePARSeqRecognizerModelFiles.MODEL_30,
            CascadePARSeqRecognizerModelFiles.MODEL_50,
            CascadePARSeqRecognizerModelFiles.MODEL_100
        )

        private object CascadePARSeqRecognizerModelFiles {
            const val MODEL_30 = "parseq-ndl-16x256-30-tiny-192epoch-tegaki3.onnx"
            const val MODEL_50 = "parseq-ndl-16x384-50-tiny-146epoch-tegaki2.onnx"
            const val MODEL_100 = "parseq-ndl-16x768-100-tiny-165epoch-tegaki2.onnx"
        }

        private fun copyModelsToCache(context: Context): File {
            val modelsDir = File(context.cacheDir, "models").apply { mkdirs() }
            for (name in MODEL_FILES) {
                val outFile = File(modelsDir, name)
                if (outFile.exists() && outFile.length() > 0L) continue
                try {
                    context.assets.open("models/$name").use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output, bufferSize = 64 * 1024)
                        }
                    }
                } catch (_: Exception) {
                    // Ignore optional config files if missing
                }
            }
            return modelsDir
        }
    }
}
