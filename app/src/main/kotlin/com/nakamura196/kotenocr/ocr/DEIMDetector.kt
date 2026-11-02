package com.nakamura196.kotenocr.ocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * DEIMv2 layout detector (input 800x800 — the file is named 1024x1024 but its graph input is 800, same as iOS) for modern Japanese texts (NDLOCR-Lite).
 * Port of `DEIMDetector.swift` (v2.1):
 * - `confThreshold = 0.25f` (`score <= confThreshold` skipped)
 * - `maxDetections = 1000` (no 100-line cap on dense pages)
 * - Truncates box coordinates toward zero (`toInt()`) matching NDLOCR-Lite `astype(np.int32)`
 * - Decodes `char_counts` from `LongArray` (`Int64`), `IntArray`, or `FloatArray`
 * - Suppresses only `isTextLine` (`line_*`) pairs during NMS so single-line headings inside `text_block` are preserved
 */
class DEIMDetector(
    private val env: OrtEnvironment,
    modelPath: String,
    classesPath: String? = null,
    private val inputWidth: Int = 800,
    private val inputHeight: Int = 800,
    private val scoreThreshold: Float = 0.2f,
    private val confThreshold: Float = 0.25f,
    // iOS `OCREngine.loadNDLModels` passes 0.2 (lines only suppress lines).
    private val iouThreshold: Float = 0.2f,
    private val maxDetections: Int = 1000
) : AutoCloseable {

    private val session: OrtSession
    private val classNames: List<String>

    private val mean = floatArrayOf(123.675f, 116.28f, 103.53f)
    private val std = floatArrayOf(58.395f, 57.12f, 57.375f)

    init {
        val opts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        session = env.createSession(modelPath, opts)
        classNames = if (classesPath != null && File(classesPath).exists()) {
            loadClassNames(classesPath)
        } else {
            defaultClassNames
        }
    }

    fun detect(bitmap: Bitmap): List<Detection> {
        val origW = bitmap.width
        val origH = bitmap.height
        val maxWH = max(origW, origH).toFloat()

        val tensorData = preprocess(bitmap)
        val inputShape = longArrayOf(1, 3, inputHeight.toLong(), inputWidth.toLong())
        val imageTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(tensorData), inputShape)

        val sizeData = longArrayOf(inputHeight.toLong(), inputWidth.toLong())
        val sizeShape = longArrayOf(1, 2)
        val sizeTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(sizeData), sizeShape)

        imageTensor.use { imgT ->
            sizeTensor.use { szT ->
                val inputNames = session.inputNames.toList()
                val inputs = if (inputNames.size >= 2) {
                    mapOf(inputNames[0] to imgT, inputNames[1] to szT)
                } else {
                    mapOf(inputNames[0] to imgT)
                }

                session.run(inputs).use { results ->
                    return postprocess(results, maxWH, origW, origH)
                }
            }
        }
    }

    private fun preprocess(bitmap: Bitmap): FloatArray {
        val origW = bitmap.width
        val origH = bitmap.height
        val maxDim = max(origW, origH)

        val padded = Bitmap.createBitmap(maxDim, maxDim, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(padded)
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(bitmap, 0f, 0f, null)

        val resized = Bitmap.createBitmap(inputWidth, inputHeight, Bitmap.Config.ARGB_8888)
        val resizeCanvas = Canvas(resized)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        resizeCanvas.drawBitmap(
            padded,
            Rect(0, 0, maxDim, maxDim),
            Rect(0, 0, inputWidth, inputHeight),
            paint
        )
        padded.recycle()

        val pixels = IntArray(inputWidth * inputHeight)
        resized.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
        resized.recycle()

        val channelSize = inputWidth * inputHeight
        val floatData = FloatArray(3 * channelSize)

        for (i in 0 until channelSize) {
            val pixel = pixels[i]
            val r = ((pixel shr 16) and 0xFF).toFloat()
            val g = ((pixel shr 8) and 0xFF).toFloat()
            val b = (pixel and 0xFF).toFloat()

            floatData[i] = (r - mean[0]) / std[0]
            floatData[channelSize + i] = (g - mean[1]) / std[1]
            floatData[2 * channelSize + i] = (b - mean[2]) / std[2]
        }

        return floatData
    }

    private fun postprocess(
        results: OrtSession.Result,
        maxWH: Float,
        origW: Int,
        origH: Int
    ): List<Detection> {
        val numOutputs = results.size()
        if (numOutputs < 3) return emptyList()

        val classIdsRaw = results[0] as OnnxTensor
        val bboxesRaw = results[1] as OnnxTensor
        val scoresRaw = results[2] as OnnxTensor
        val charCountRaw = if (numOutputs >= 4) results[3] as OnnxTensor else null

        val bboxes = extractFloats(bboxesRaw)
        val scores = extractFloats(scoresRaw)
        val numDetections = scores.size

        val classIds: IntArray = extractInts(classIdsRaw, numDetections)
        val charCounts: FloatArray? = charCountRaw?.let { extractFloats(it) }

        val scaleX = maxWH / inputWidth.toFloat()
        val scaleY = maxWH / inputHeight.toFloat()

        val detections = mutableListOf<Detection>()

        for (i in 0 until numDetections) {
            val score = scores[i]
            if (score < scoreThreshold || score <= confThreshold) continue

            val rawIdx = if (i < classIds.size) classIds[i] else 0
            // DEIMv2 outputs 1-indexed class IDs; convert to 0-indexed
            val classIdx = max(0, rawIdx - 1)
            val className = if (classIdx < classNames.size) classNames[classIdx] else "unknown_$classIdx"

            val x1 = bboxes[i * 4] * scaleX
            val y1 = bboxes[i * 4 + 1] * scaleY
            val x2 = bboxes[i * 4 + 2] * scaleX
            val y2 = bboxes[i * 4 + 3] * scaleY

            val box = Detection.clampedBox(
                x1 = x1,
                y1 = y1,
                x2 = x2,
                y2 = y2,
                width = origW,
                height = origH,
                truncateTowardZero = true
            ) ?: continue

            val predCharCount = if (charCounts != null && i < charCounts.size) {
                charCounts[i]
            } else {
                100f
            }

            detections.add(
                Detection(
                    id = 0,
                    box = box,
                    score = score,
                    classIndex = classIdx,
                    className = className,
                    predCharCount = predCharCount
                )
            )
        }

        val nmsResult = applyNMS(detections)
        return nmsResult.take(maxDetections).mapIndexed { index, det ->
            det.copy(id = index)
        }
    }

    private fun extractFloats(tensor: OnnxTensor): FloatArray {
        tensor.floatBuffer?.let { fb ->
            fb.rewind()
            return FloatArray(fb.remaining()).also { fb.get(it) }
        }
        tensor.longBuffer?.let { lb ->
            lb.rewind()
            return FloatArray(lb.remaining()) { lb.get().toFloat() }
        }
        tensor.intBuffer?.let { ib ->
            ib.rewind()
            return FloatArray(ib.remaining()) { ib.get().toFloat() }
        }
        return when (val v = tensor.value) {
            is FloatArray -> v
            is LongArray -> FloatArray(v.size) { v[it].toFloat() }
            is IntArray -> FloatArray(v.size) { v[it].toFloat() }
            is Array<*> -> {
                val list = mutableListOf<Float>()
                fun flatten(any: Any?) {
                    when (any) {
                        is FloatArray -> for (x in any) list.add(x)
                        is LongArray -> for (x in any) list.add(x.toFloat())
                        is IntArray -> for (x in any) list.add(x.toFloat())
                        is Array<*> -> for (x in any) flatten(x)
                        is Number -> list.add(any.toFloat())
                    }
                }
                flatten(v)
                list.toFloatArray()
            }
            else -> FloatArray(0)
        }
    }

    private fun extractInts(tensor: OnnxTensor, fallbackSize: Int): IntArray {
        tensor.longBuffer?.let { lb ->
            lb.rewind()
            return IntArray(lb.remaining()) { lb.get().toInt() }
        }
        tensor.intBuffer?.let { ib ->
            ib.rewind()
            return IntArray(ib.remaining()).also { ib.get(it) }
        }
        tensor.floatBuffer?.let { fb ->
            fb.rewind()
            return IntArray(fb.remaining()) { fb.get().toInt() }
        }
        return IntArray(fallbackSize)
    }

    private fun applyNMS(detections: List<Detection>): List<Detection> {
        val sorted = detections.sortedByDescending { it.score }
        val kept = mutableListOf<Detection>()

        for (det in sorted) {
            var suppressed = false
            for (existing in kept) {
                if (det.isTextLine && existing.isTextLine && computeIoU(det.box, existing.box) > iouThreshold) {
                    suppressed = true
                    break
                }
            }
            if (!suppressed) {
                kept.add(det)
            }
        }

        return kept
    }

    private fun computeIoU(a: IntArray, b: IntArray): Float {
        val x1 = max(a[0], b[0])
        val y1 = max(a[1], b[1])
        val x2 = min(a[2], b[2])
        val y2 = min(a[3], b[3])

        val interW = max(0, x2 - x1)
        val interH = max(0, y2 - y1)
        val interArea = interW.toFloat() * interH.toFloat()

        val areaA = (a[2] - a[0]).toFloat() * (a[3] - a[1]).toFloat()
        val areaB = (b[2] - b[0]).toFloat() * (b[3] - b[1]).toFloat()
        val unionArea = areaA + areaB - interArea

        return if (unionArea > 0f) interArea / unionArea else 0f
    }

    override fun close() {
        session.close()
    }

    companion object {
        val defaultClassNames = listOf(
            "text_block", "line_main", "line_caption", "line_ad",
            "line_note", "line_note_tochu", "block_fig", "block_ad",
            "block_pillar", "block_folio", "block_rubi", "block_chart",
            "block_eqn", "block_cfm", "block_eng", "block_table",
            "line_title"
        )

        private fun loadClassNames(path: String): List<String> {
            val content = File(path).readText()
            val names = mutableListOf<String>()
            var inNames = false
            for (line in content.lines()) {
                val trimmed = line.trim()
                if (trimmed.startsWith("names:")) {
                    inNames = true
                    continue
                }
                if (inNames) {
                    if (trimmed.startsWith("-") || trimmed.contains(":")) {
                        val parts = trimmed.split(":", limit = 2)
                        val name = if (parts.size == 2) {
                            parts[1].trim().trim('\'', '"')
                        } else {
                            trimmed.removePrefix("-").trim().trim('\'', '"')
                        }
                        if (name.isNotEmpty()) names.add(name)
                    } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        inNames = false
                    }
                }
            }
            return names.ifEmpty { defaultClassNames }
        }
    }
}
