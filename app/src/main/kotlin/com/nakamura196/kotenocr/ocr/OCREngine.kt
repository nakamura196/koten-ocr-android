package com.nakamura196.kotenocr.ocr

import ai.onnxruntime.OrtEnvironment
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

enum class OCRMode(val key: String) {
    KOTEN("koten"),
    NDL("ndl");

    companion object {
        fun fromKey(key: String?): OCRMode =
            entries.firstOrNull { it.key == key } ?: KOTEN
    }
}

/**
 * Result of a full OCR pipeline run.
 * Includes the [image] (which may have been deskewed by [Deskew]) so the UI overlay,
 * Box Editor, History, and PDF export all align with the recognized line coordinates.
 */
data class OCRResult(
    val detections: List<Detection>,
    val text: String,
    val image: Bitmap
)

/**
 * Dual-mode OCR Engine supporting Classical (Koten) and Modern (NDL) pipelines.
 * Port of `OCREngine.swift` (v2.1):
 * - Automatic global deskew ([Deskew])
 * - Per-line tilt correction ([LineTilt]) + fallback on `>= 3` placeholder glyphs
 * - Page-majority direction inference for short/near-square boxes ([isVerticalPage], [readsVertically])
 * - Removal of placeholder-only lines (`□`, `〓`, `\uFFFD`) and duplicate overlapping columns
 * - Additive box re-recognition ([recognizeBoxes]) and reading order sorting ([orderReading]) for Box Editor
 */
class OCREngine(
    private val modelsDir: File,
    initialMode: OCRMode = OCRMode.KOTEN
) : AutoCloseable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()

    private var rtmDetector: RTMDetector? = null
    private var kotenRecognizer: PARSeqRecognizer? = null

    private var deimDetector: DEIMDetector? = null
    private var cascadeRecognizer: CascadePARSeqRecognizer? = null

    private val readingOrderProcessor = ReadingOrderProcessor()

    var currentMode: OCRMode = initialMode
        private set

    init {
        loadModelsForMode(initialMode)
    }

    private fun loadModelsForMode(mode: OCRMode) {
        when (mode) {
            OCRMode.KOTEN -> {
                if (rtmDetector == null) {
                    rtmDetector = RTMDetector(
                        env = env,
                        modelPath = File(modelsDir, "rtmdet-s-1280x1280.onnx").absolutePath,
                        maxDetections = 1000
                    )
                }
                if (kotenRecognizer == null) {
                    kotenRecognizer = PARSeqRecognizer(
                        env = env,
                        modelPath = File(modelsDir, "parseq-ndl-32x384-tiny-10.onnx").absolutePath,
                        vocabPath = File(modelsDir, "NDLmoji.yaml").absolutePath,
                        inputHeight = 32,
                        inputWidth = 384
                    )
                }
            }
            OCRMode.NDL -> {
                if (deimDetector == null) {
                    val classesFile = File(modelsDir, "ndl-deim.yaml")
                    deimDetector = DEIMDetector(
                        env = env,
                        modelPath = File(modelsDir, "deim-s-1024x1024.onnx").absolutePath,
                        classesPath = if (classesFile.exists()) classesFile.absolutePath else null,
                        maxDetections = 1000
                    )
                }
                if (cascadeRecognizer == null) {
                    cascadeRecognizer = CascadePARSeqRecognizer(env, modelsDir)
                }
            }
        }
    }

    fun switchMode(mode: OCRMode) {
        if (mode == currentMode) return
        loadModelsForMode(mode)
        currentMode = mode
    }

    /**
     * Releases the inactive mode's ONNX sessions when memory is tight.
     */
    fun releaseUnusedModels() {
        when (currentMode) {
            OCRMode.KOTEN -> {
                deimDetector?.close()
                deimDetector = null
                cascadeRecognizer?.close()
                cascadeRecognizer = null
            }
            OCRMode.NDL -> {
                rtmDetector?.close()
                rtmDetector = null
                kotenRecognizer?.close()
                kotenRecognizer = null
            }
        }
    }

    suspend fun process(
        bitmap: Bitmap,
        autoDeskew: Boolean = true,
        onProgress: (Float) -> Unit = {}
    ): OCRResult = withContext(Dispatchers.Default) {
        onProgress(0.05f)

        val orientation = when (currentMode) {
            OCRMode.KOTEN -> Deskew.TextOrientation.VERTICAL
            OCRMode.NDL -> Deskew.TextOrientation.AUTO
        }
        val input = if (autoDeskew) {
            Deskew.deskew(bitmap, orientation).first
        } else {
            bitmap
        }

        onProgress(0.10f)

        val recognized = when (currentMode) {
            OCRMode.KOTEN -> processKoten(input, lineTiltEnabled = autoDeskew, onProgress = onProgress)
            OCRMode.NDL -> processNDL(input, onProgress = onProgress)
        }

        if (recognized.isEmpty()) {
            onProgress(1.0f)
            return@withContext OCRResult(emptyList(), "", input)
        }

        val ordered = orderReading(recognized, input.width, input.height)
        val fullText = ordered.map { it.text }.filter { it.isNotEmpty() }.joinToString("\n")
        onProgress(1.0f)

        OCRResult(ordered, fullText, input)
    }

    private suspend fun processKoten(
        bitmap: Bitmap,
        lineTiltEnabled: Boolean,
        onProgress: (Float) -> Unit
    ): List<Detection> = coroutineScope {
        val detector = rtmDetector ?: throw IllegalStateException("RTMDetector not loaded")
        val recognizer = kotenRecognizer ?: throw IllegalStateException("Koten recognizer not loaded")

        val detections = detector.detect(bitmap)
        onProgress(0.30f)

        if (detections.isEmpty()) return@coroutineScope emptyList()

        val recognized = recognizeKotenLines(
            detections = detections,
            bitmap = bitmap,
            recognizer = recognizer,
            lineTiltEnabled = lineTiltEnabled,
            onProgress = onProgress
        )
        LineTilt.dropSameLineDuplicates(recognized)
    }

    private suspend fun recognizeKotenLines(
        detections: List<Detection>,
        bitmap: Bitmap,
        recognizer: PARSeqRecognizer,
        lineTiltEnabled: Boolean,
        onProgress: (Float) -> Unit
    ): List<Detection> = coroutineScope {
        val pageVertical = isVerticalPage(detections)
        val total = detections.size
        val results = arrayOfNulls<Detection>(total)
        val batchSize = 4

        for (batchStart in 0 until total step batchSize) {
            val batchEnd = min(batchStart + batchSize, total)
            val jobs = (batchStart until batchEnd).map { idx ->
                async(Dispatchers.Default) {
                    val det = detections[idx]
                    val tilted = LineTilt.crop(bitmap, det.box, enabled = lineTiltEnabled)
                        ?: return@async null
                    val vertical = if (tilted.plan == null) {
                        readsVertically(det.box, pageVertical)
                    } else {
                        null
                    }
                    var text = recognizer.recognize(tilted.bitmap, vertical = vertical)
                    tilted.bitmap.recycle()

                    if (tilted.plan != null && LineTilt.placeholderCount(text) >= 3) {
                        val plain = cropPlain(bitmap, det.box)
                        if (plain != null) {
                            val alt = recognizer.recognize(
                                plain,
                                vertical = readsVertically(det.box, pageVertical)
                            )
                            plain.recycle()
                            if (LineTilt.placeholderCount(alt) < LineTilt.placeholderCount(text)) {
                                text = alt
                            }
                        }
                    }

                    if (LineTilt.isPlaceholderOnly(text)) return@async null
                    det.copy(text = text)
                }
            }
            val batchResults = jobs.awaitAll()
            for (i in batchResults.indices) {
                results[batchStart + i] = batchResults[i]
            }
            onProgress(0.30f + 0.60f * (batchEnd.toFloat() / total.toFloat()))
        }

        results.filterNotNull()
    }

    private suspend fun processNDL(
        bitmap: Bitmap,
        onProgress: (Float) -> Unit
    ): List<Detection> = coroutineScope {
        val detector = deimDetector ?: throw IllegalStateException("DEIMDetector not loaded")
        val recognizer = cascadeRecognizer ?: throw IllegalStateException("Cascade recognizer not loaded")

        val allDetections = detector.detect(bitmap)
        val lineDetections = allDetections.filter { it.isTextLine }
        onProgress(0.30f)

        if (lineDetections.isEmpty()) return@coroutineScope emptyList()

        val pageVertical = isVerticalPage(lineDetections)
        val total = lineDetections.size
        val results = arrayOfNulls<Detection>(total)
        val batchSize = 4

        for (batchStart in 0 until total step batchSize) {
            val batchEnd = min(batchStart + batchSize, total)
            val jobs = (batchStart until batchEnd).map { idx ->
                async(Dispatchers.Default) {
                    val det = lineDetections[idx]
                    val cropped = cropPlain(bitmap, det.box) ?: return@async null
                    val vertical = readsVertically(det.box, pageVertical)
                    val text = recognizer.recognize(
                        bitmap = cropped,
                        predCharCount = det.predCharCount,
                        vertical = vertical
                    )
                    cropped.recycle()
                    if (LineTilt.isPlaceholderOnly(text)) return@async null
                    det.copy(text = text)
                }
            }
            val batchResults = jobs.awaitAll()
            for (i in batchResults.indices) {
                results[batchStart + i] = batchResults[i]
            }
            onProgress(0.30f + 0.60f * (batchEnd.toFloat() / total.toFloat()))
        }

        results.filterNotNull()
    }

    /**
     * Re-recognizes text inside user-edited [boxes] on [bitmap] using [mode] (used by Box Editor).
     */
    suspend fun recognizeBoxes(
        boxes: List<Detection>,
        bitmap: Bitmap,
        mode: OCRMode = currentMode,
        lineTiltEnabled: Boolean = true
    ): List<Detection> = withContext(Dispatchers.Default) {
        if (boxes.isEmpty()) return@withContext emptyList()
        loadModelsForMode(mode)

        val pageVertical = isVerticalPage(boxes)
        val total = boxes.size
        val results = arrayOfNulls<Detection>(total)
        val batchSize = 4

        for (batchStart in 0 until total step batchSize) {
            val batchEnd = min(batchStart + batchSize, total)
            val jobs = (batchStart until batchEnd).map { idx ->
                async(Dispatchers.Default) {
                    val det = boxes[idx]
                    when (mode) {
                        OCRMode.KOTEN -> {
                            val recognizer = kotenRecognizer
                                ?: return@async det.copy(text = "").also { it.markReaderConfirmed() }
                            val tilted = LineTilt.crop(bitmap, det.box, enabled = lineTiltEnabled)
                                ?: return@async det.copy(text = "").also { it.markReaderConfirmed() }
                            val vertical = if (tilted.plan == null) {
                                readsVertically(det.box, pageVertical)
                            } else {
                                null
                            }
                            var text = recognizer.recognize(tilted.bitmap, vertical = vertical)
                            tilted.bitmap.recycle()
                            if (tilted.plan != null && LineTilt.placeholderCount(text) >= 3) {
                                val plain = cropPlain(bitmap, det.box)
                                if (plain != null) {
                                    val alt = recognizer.recognize(
                                        plain,
                                        vertical = readsVertically(det.box, pageVertical)
                                    )
                                    plain.recycle()
                                    if (LineTilt.placeholderCount(alt) < LineTilt.placeholderCount(text)) {
                                        text = alt
                                    }
                                }
                            }
                            val finalText = if (LineTilt.isPlaceholderOnly(text)) "" else text
                            det.copy(text = finalText).also { it.markReaderConfirmed() }
                        }
                        OCRMode.NDL -> {
                            val recognizer = cascadeRecognizer
                                ?: return@async det.copy(text = "").also { it.markReaderConfirmed() }
                            val cropped = cropPlain(bitmap, det.box)
                                ?: return@async det.copy(text = "").also { it.markReaderConfirmed() }
                            val vertical = readsVertically(det.box, pageVertical)
                            val text = recognizer.recognize(
                                bitmap = cropped,
                                predCharCount = det.predCharCount,
                                vertical = vertical
                            )
                            cropped.recycle()
                            val finalText = if (LineTilt.isPlaceholderOnly(text)) "" else text
                            det.copy(text = finalText).also { it.markReaderConfirmed() }
                        }
                    }
                }
            }
            val batchResults = jobs.awaitAll()
            for (i in batchResults.indices) {
                results[batchStart + i] = batchResults[i]
            }
        }

        results.filterNotNull()
    }

    /**
     * Sorts [detections] in reading order and re-indexes `id` from `0..lastIndex`.
     */
    fun orderReading(
        detections: List<Detection>,
        imageWidth: Int,
        imageHeight: Int
    ): List<Detection> {
        if (detections.isEmpty()) return emptyList()
        return readingOrderProcessor
            .process(detections, imageWidth, imageHeight)
            .mapIndexed { idx, det -> det.copy(id = idx) }
    }

    private fun cropPlain(bitmap: Bitmap, box: IntArray): Bitmap? {
        if (box.size < 4) return null
        val x1 = max(0, min(bitmap.width, box[0]))
        val y1 = max(0, min(bitmap.height, box[1]))
        val x2 = max(x1, min(bitmap.width, box[2]))
        val y2 = max(y1, min(bitmap.height, box[3]))
        val w = x2 - x1
        val h = y2 - y1
        if (w <= 0 || h <= 0) return null
        return Bitmap.createBitmap(bitmap, x1, y1, w, h)
    }

    override fun close() {
        rtmDetector?.close()
        kotenRecognizer?.close()
        deimDetector?.close()
        cascadeRecognizer?.close()
    }

    companion object {
        /**
         * True when more than half of [detections] are taller than wide.
         */
        fun isVerticalPage(detections: List<Detection>): Boolean {
            val boxes = detections.map { it.box }.filter { it.size >= 4 }
            val tall = boxes.count { it[3] - it[1] > it[2] - it[0] }
            return boxes.size < tall * 2
        }

        /**
         * Reading direction for a single [box] (`[x1, y1, x2, y2]`):
         * clearly elongated boxes (`aspect >= 1.5`) use their own shape; near-square boxes
         * (1–2 character headings, page numbers, captions) follow [pageVertical].
         */
        fun readsVertically(box: IntArray, pageVertical: Boolean): Boolean {
            if (box.size < 4) return pageVertical
            val w = box[2] - box[0]
            val h = box[3] - box[1]
            if (max(w, h).toDouble() >= 1.5 * min(w, h).toDouble()) {
                return h > w
            }
            return pageVertical
        }
    }
}
