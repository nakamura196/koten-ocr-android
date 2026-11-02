package com.nakamura196.kotenocr.ocr

import ai.onnxruntime.OrtEnvironment
import android.graphics.Bitmap
import java.io.File
import kotlin.math.roundToInt

/**
 * 3-stage cascade PARSeq recognizer for modern Japanese texts (NDLOCR-Lite).
 * Selects the appropriate model based on DEIMv2's `predCharCount` category:
 * - `3`: <= 30 chars (`parseq-ndl-16x256-30-tiny-192epoch-tegaki3.onnx`)
 * - `2`: <= 50 chars (`parseq-ndl-16x384-50-tiny-146epoch-tegaki2.onnx`)
 * - otherwise: 100 chars (`parseq-ndl-16x768-100-tiny-165epoch-tegaki2.onnx`)
 */
class CascadePARSeqRecognizer(
    env: OrtEnvironment,
    modelsDir: File
) : AutoCloseable {

    private val recognizer30: PARSeqRecognizer
    private val recognizer50: PARSeqRecognizer
    private val recognizer100: PARSeqRecognizer

    init {
        val vocabPath = File(modelsDir, "NDLmoji.yaml").absolutePath

        recognizer30 = PARSeqRecognizer(
            env = env,
            modelPath = File(modelsDir, MODEL_30_FILENAME).absolutePath,
            vocabPath = vocabPath,
            inputHeight = 16,
            inputWidth = 256
        )
        recognizer50 = PARSeqRecognizer(
            env = env,
            modelPath = File(modelsDir, MODEL_50_FILENAME).absolutePath,
            vocabPath = vocabPath,
            inputHeight = 16,
            inputWidth = 384
        )
        recognizer100 = PARSeqRecognizer(
            env = env,
            modelPath = File(modelsDir, MODEL_100_FILENAME).absolutePath,
            vocabPath = vocabPath,
            inputHeight = 16,
            inputWidth = 768
        )
    }

    fun recognize(
        bitmap: Bitmap,
        predCharCount: Float = 100f,
        vertical: Boolean? = null
    ): String {
        val charCount = predCharCount.roundToInt()

        return when (charCount) {
            3 -> {
                val result = recognizer30.recognize(bitmap, vertical = vertical)
                if (result.length >= 25) {
                    val result50 = recognizer50.recognize(bitmap, vertical = vertical)
                    if (result50.length >= 45) {
                        recognizeWith100(bitmap, vertical = vertical)
                    } else {
                        result50
                    }
                } else {
                    result
                }
            }
            2 -> {
                val result = recognizer50.recognize(bitmap, vertical = vertical)
                if (result.length >= 45) {
                    recognizeWith100(bitmap, vertical = vertical)
                } else {
                    result
                }
            }
            else -> recognizeWith100(bitmap, vertical = vertical)
        }
    }

    private fun recognizeWith100(bitmap: Bitmap, vertical: Boolean?): String {
        val result = recognizer100.recognize(bitmap, vertical = vertical)
        val isVertical = vertical ?: (bitmap.height > bitmap.width)

        if (result.length >= 98 && !isVertical && bitmap.width >= 4) {
            val halfW = bitmap.width / 2
            val leftBitmap = Bitmap.createBitmap(bitmap, 0, 0, halfW, bitmap.height)
            val rightBitmap = Bitmap.createBitmap(bitmap, halfW, 0, bitmap.width - halfW, bitmap.height)

            val leftText = recognizer100.recognize(leftBitmap, vertical = false)
            val rightText = recognizer100.recognize(rightBitmap, vertical = false)

            leftBitmap.recycle()
            rightBitmap.recycle()

            return leftText + rightText
        }

        return result
    }

    override fun close() {
        recognizer30.close()
        recognizer50.close()
        recognizer100.close()
    }

    companion object {
        const val MODEL_30_FILENAME = "parseq-ndl-16x256-30-tiny-192epoch-tegaki3.onnx"
        const val MODEL_50_FILENAME = "parseq-ndl-16x384-50-tiny-146epoch-tegaki2.onnx"
        const val MODEL_100_FILENAME = "parseq-ndl-16x768-100-tiny-165epoch-tegaki2.onnx"
    }
}
