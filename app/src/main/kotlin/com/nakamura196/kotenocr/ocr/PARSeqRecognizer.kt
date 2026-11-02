package com.nakamura196.kotenocr.ocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import java.io.File
import java.nio.FloatBuffer

/**
 * PARSeq character recognizer.
 * Supports explicit [vertical] line direction override so single-character or near-square boxes
 * follow the page's dominant writing direction instead of guessing from their own aspect ratio.
 */
class PARSeqRecognizer(
    private val env: OrtEnvironment,
    modelPath: String,
    vocabPath: String,
    private val inputHeight: Int = 32,
    private val inputWidth: Int = 384
) : AutoCloseable {

    private val session: OrtSession
    private val charList: List<String>

    init {
        val opts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        session = env.createSession(modelPath, opts)
        charList = loadVocab(vocabPath)
    }

    /**
     * Recognizes text in [bitmap].
     * @param vertical `true` for vertical text (rotates 90° CCW), `false` for horizontal text,
     *                 or `null` to infer from `bitmap.height > bitmap.width`.
     */
    fun recognize(bitmap: Bitmap, vertical: Boolean? = null): String {
        val tensorData = preprocess(bitmap, vertical)
        val shape = longArrayOf(1, 3, inputHeight.toLong(), inputWidth.toLong())
        val inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(tensorData), shape)

        inputTensor.use { tensor ->
            val inputName = session.inputNames.first()
            session.run(mapOf(inputName to tensor)).use { results ->
                return decode(results)
            }
        }
    }

    private fun preprocess(bitmap: Bitmap, vertical: Boolean?): FloatArray {
        val w = bitmap.width
        val h = bitmap.height
        val shouldRotate = vertical ?: (h > w)

        val workBitmap = if (shouldRotate) {
            val matrix = Matrix().apply { postRotate(-90f) }
            Bitmap.createBitmap(bitmap, 0, 0, w, h, matrix, true)
        } else {
            bitmap
        }

        val resized = Bitmap.createBitmap(inputWidth, inputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resized)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(
            workBitmap,
            Rect(0, 0, workBitmap.width, workBitmap.height),
            Rect(0, 0, inputWidth, inputHeight),
            paint
        )
        if (workBitmap !== bitmap) {
            workBitmap.recycle()
        }

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

            // Normalize to [-1, 1]: 2 * (px / 255) - 1
            floatData[i] = 2.0f * (r / 255.0f) - 1.0f
            floatData[channelSize + i] = 2.0f * (g / 255.0f) - 1.0f
            floatData[2 * channelSize + i] = 2.0f * (b / 255.0f) - 1.0f
        }

        return floatData
    }

    private fun decode(results: OrtSession.Result): String {
        val logitsTensor = results[0] as OnnxTensor
        val shape = logitsTensor.info.shape
        val seqLen = shape[1].toInt()
        val vocabSize = shape[2].toInt()
        val logits = logitsTensor.floatBuffer

        val tokenIds = mutableListOf<Int>()

        for (s in 0 until seqLen) {
            val offset = s * vocabSize
            var maxIdx = 0
            var maxVal = logits.get(offset)

            for (v in 1 until vocabSize) {
                val value = logits.get(offset + v)
                if (value > maxVal) {
                    maxVal = value
                    maxIdx = v
                }
            }

            // Index 0 is EOS token
            if (maxIdx == 0) break

            tokenIds.add(maxIdx)
        }

        // PARSeq is autoregressive: repeated characters are real (no CTC dedup).
        val result = StringBuilder()
        for (id in tokenIds) {
            val charIdx = id - 1
            if (charIdx >= 0 && charIdx < charList.size) {
                result.append(charList[charIdx])
            }
        }

        return result.toString()
    }

    override fun close() {
        session.close()
    }

    companion object {
        /**
         * Reads `charset_train: "..."` from NDLmoji.yaml (same as iOS `loadCharset`).
         * Splits by code point so the list length matches the model vocab (7141 + EOS).
         */
        fun loadVocab(path: String): List<String> {
            val content = File(path).readText()
            val key = content.indexOf("charset_train:")
            if (key < 0) error("charset_train not found in $path")
            val q = content.indexOf('"', key)
            if (q < 0) error("charset_train value not quoted in $path")
            val sb = StringBuilder()
            var escaped = false
            var i = q + 1
            while (i < content.length) {
                val c = content[i]
                if (escaped) {
                    when (c) {
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        else -> sb.append(c)
                    }
                    escaped = false
                } else if (c == '\\') {
                    escaped = true
                } else if (c == '"') {
                    break
                } else {
                    sb.append(c)
                }
                i++
            }
            val raw = sb.toString()
            val out = ArrayList<String>(raw.length)
            var j = 0
            while (j < raw.length) {
                val cp = raw.codePointAt(j)
                out.add(String(Character.toChars(cp)))
                j += Character.charCount(cp)
            }
            return out
        }
    }
}
