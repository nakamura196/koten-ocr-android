package com.nakamura196.kotenocr.ocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import java.nio.FloatBuffer
import kotlin.math.max

/**
 * RTMDet-s layout detector (input 1024x1024 — the file is named 1280x1280 but its graph input is 1024, same as iOS) for classical Japanese texts (NDL古典籍OCR-Lite).
 * Port of `RTMDetector.swift` (v2.1) with `maxDetections = 1000` and `Detection.clampedBox`.
 */
class RTMDetector(
    private val env: OrtEnvironment,
    modelPath: String,
    private val inputWidth: Int = 1024,
    private val inputHeight: Int = 1024,
    private val scoreThreshold: Float = 0.3f,
    private val maxDetections: Int = 1000
) : AutoCloseable {

    private val session: OrtSession

    private val mean = floatArrayOf(123.675f, 116.28f, 103.53f)
    private val std = floatArrayOf(58.395f, 57.12f, 57.375f)

    private val classNames = listOf(
        "text_block", "line_main", "line_caption", "line_ad", "line_note",
        "line_Warichu", "block_fig", "block_ad", "block_pillar", "block_folio",
        "block_rubi", "block_chart", "block_eqn", "block_cfm", "block_eng", "block_table"
    )

    init {
        val opts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        session = env.createSession(modelPath, opts)
    }

    fun detect(bitmap: Bitmap): List<Detection> {
        val origW = bitmap.width
        val origH = bitmap.height
        val maxWH = max(origW, origH).toFloat()

        val tensorData = preprocess(bitmap)
        val shape = longArrayOf(1, 3, inputHeight.toLong(), inputWidth.toLong())
        val inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(tensorData), shape)

        inputTensor.use { tensor ->
            val inputName = session.inputNames.first()
            session.run(mapOf(inputName to tensor)).use { results ->
                return postprocess(results, maxWH, origW, origH)
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
        val detsTensor = results[0] as OnnxTensor
        val detsShape = detsTensor.info.shape
        val numDetections = detsShape[1].toInt()
        val detsData = detsTensor.floatBuffer

        val labels: IntArray? = if (results.size() > 1) {
            val labelsTensor = results[1] as OnnxTensor
            labelsTensor.longBuffer?.let { lb ->
                lb.rewind()
                IntArray(numDetections) { lb.get().toInt() }
            } ?: labelsTensor.intBuffer?.let { ib ->
                ib.rewind()
                IntArray(numDetections) { ib.get() }
            }
        } else {
            null
        }

        val scaleX = maxWH / inputWidth.toFloat()
        val scaleY = maxWH / inputHeight.toFloat()

        val detections = mutableListOf<Detection>()

        for (i in 0 until numDetections) {
            val baseIdx = i * 5
            val x1 = detsData.get(baseIdx) * scaleX
            val y1 = detsData.get(baseIdx + 1) * scaleY
            val x2 = detsData.get(baseIdx + 2) * scaleX
            val y2 = detsData.get(baseIdx + 3) * scaleY
            val score = detsData.get(baseIdx + 4)

            if (score < scoreThreshold) continue

            val box = paddedBox(x1, y1, x2, y2, origW, origH) ?: continue

            val classIdx = labels?.get(i) ?: 1
            val className = if (classIdx in classNames.indices) classNames[classIdx] else "unknown"

            detections.add(
                Detection(
                    id = 0,
                    box = box,
                    score = score,
                    classIndex = classIdx,
                    className = className
                )
            )
        }

        // No NMS: the ONNX model already outputs post-NMS boxes (same as iOS). A second pass
        // at IoU 0.4 can drop overlapping boxes that iOS keeps.
        return detections.sortedByDescending { it.score }.take(maxDetections).mapIndexed { index, det ->
            det.copy(id = index)
        }
    }

    companion object {
        /** Fraction of the box height added above and below (iOS `RTMDetector.swift`). */
        const val VERTICAL_PAD_RATIO = 0.02f

        /**
         * Model box (original-image pixels) → clamped integer box, 2% taller above and below,
         * as iOS (`RTMDetector.swift`) and NDL古典籍OCR-Lite do: the raw boxes clip the top and
         * bottom strokes of a column.
         */
        fun paddedBox(x1: Float, y1: Float, x2: Float, y2: Float, width: Int, height: Int): IntArray? {
            val pad = (y2 - y1) * VERTICAL_PAD_RATIO
            return Detection.clampedBox(x1, y1 - pad, x2, y2 + pad, width, height, truncateTowardZero = false)
        }
    }

    override fun close() {
        session.close()
    }
}
