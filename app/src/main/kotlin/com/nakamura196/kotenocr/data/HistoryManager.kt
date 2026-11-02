package com.nakamura196.kotenocr.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.nakamura196.kotenocr.models.HistoryItem
import com.nakamura196.kotenocr.ocr.Detection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

/**
 * Persists up to [MAX_ITEMS] OCR scan history items on disk with atomic JSON updates
 * and downsampled thumbnail loading (`HistoryManager.swift` v2.1 parity).
 */
class HistoryManager(context: Context) {

    private val historyDir = File(context.filesDir, "history").apply { mkdirs() }
    private val indexFile = File(historyDir, "history.json")

    private val _items = MutableStateFlow<List<HistoryItem>>(emptyList())
    val items: StateFlow<List<HistoryItem>> = _items.asStateFlow()

    init {
        loadFromDisk()
    }

    fun save(bitmap: Bitmap, detections: List<Detection>, text: String): HistoryItem {
        val id = UUID.randomUUID().toString()
        val imgName = "$id.jpg"
        val imgFile = File(historyDir, imgName)

        FileOutputStream(imgFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }

        val item = HistoryItem(
            id = id,
            timestamp = System.currentTimeMillis(),
            text = text,
            detections = detections,
            imageFileName = imgName
        )

        val updated = listOf(item) + _items.value
        val trimmed = if (updated.size > MAX_ITEMS) {
            val removed = updated.drop(MAX_ITEMS)
            for (old in removed) {
                File(historyDir, old.imageFileName).delete()
            }
            updated.take(MAX_ITEMS)
        } else {
            updated
        }

        _items.value = trimmed
        persistToDisk(trimmed)
        return item
    }

    fun delete(item: HistoryItem) {
        File(historyDir, item.imageFileName).delete()
        val updated = _items.value.filter { it.id != item.id }
        _items.value = updated
        persistToDisk(updated)
    }

    fun deleteAll() {
        for (item in _items.value) {
            File(historyDir, item.imageFileName).delete()
        }
        _items.value = emptyList()
        persistToDisk(emptyList())
    }

    fun updateTranslation(itemId: String, translatedText: String) {
        val updated = _items.value.map {
            if (it.id == itemId) it.copy(translatedText = translatedText) else it
        }
        _items.value = updated
        persistToDisk(updated)
    }

    /**
     * Updates the detections and combined text of an existing history item
     * (when edited via text editor or Box Editor on the Result screen).
     */
    fun updateDetections(itemId: String, detections: List<Detection>, text: String) {
        val updated = _items.value.map {
            if (it.id == itemId) it.copy(detections = detections, text = text) else it
        }
        _items.value = updated
        persistToDisk(updated)
    }

    fun loadImage(item: HistoryItem): Bitmap? {
        val file = File(historyDir, item.imageFileName)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    /**
     * Loads a downsampled thumbnail off the main thread for history rows and home recent cards.
     */
    suspend fun loadThumbnail(item: HistoryItem, maxPixelSize: Int = 160): Bitmap? =
        withContext(Dispatchers.IO) {
            val file = File(historyDir, item.imageFileName)
            if (!file.exists()) return@withContext null
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                val w = bounds.outWidth
                val h = bounds.outHeight
                if (w <= 0 || h <= 0) return@withContext null
                var sample = 1
                val longSide = max(w, h)
                while (longSide / (sample * 2) >= maxPixelSize) {
                    sample *= 2
                }
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeFile(file.absolutePath, opts)
            } catch (_: Exception) {
                null
            }
        }

    private fun loadFromDisk() {
        if (!indexFile.exists()) return
        try {
            val json = JSONArray(indexFile.readText())
            val list = mutableListOf<HistoryItem>()
            for (i in 0 until json.length()) {
                val obj = json.getJSONObject(i)
                val detsArr = obj.getJSONArray("detections")
                val dets = mutableListOf<Detection>()
                for (j in 0 until detsArr.length()) {
                    val d = detsArr.getJSONObject(j)
                    val boxArr = d.getJSONArray("box")
                    dets.add(
                        Detection(
                            id = d.getInt("id"),
                            box = intArrayOf(
                                boxArr.getInt(0),
                                boxArr.getInt(1),
                                boxArr.getInt(2),
                                boxArr.getInt(3)
                            ),
                            score = d.getDouble("score").toFloat(),
                            classIndex = d.getInt("classIndex"),
                            className = d.getString("className"),
                            text = d.optString("text", ""),
                            predCharCount = d.optDouble("predCharCount", 100.0).toFloat()
                        )
                    )
                }
                val translated = if (obj.has("translatedText") && !obj.isNull("translatedText")) {
                    obj.getString("translatedText")
                } else {
                    null
                }
                list.add(
                    HistoryItem(
                        id = obj.getString("id"),
                        timestamp = obj.getLong("timestamp"),
                        text = obj.getString("text"),
                        detections = dets,
                        imageFileName = obj.getString("imageFileName"),
                        translatedText = translated
                    )
                )
            }
            _items.value = list
        } catch (_: Exception) {
            _items.value = emptyList()
        }
    }

    private fun persistToDisk(items: List<HistoryItem>) {
        val arr = JSONArray()
        for (item in items) {
            val detsArr = JSONArray()
            for (det in item.detections) {
                val d = JSONObject().apply {
                    put("id", det.id)
                    put("box", JSONArray(det.box.toList()))
                    put("score", det.score.toDouble())
                    put("classIndex", det.classIndex)
                    put("className", det.className)
                    put("text", det.text)
                    put("predCharCount", det.predCharCount.toDouble())
                }
                detsArr.put(d)
            }
            val obj = JSONObject().apply {
                put("id", item.id)
                put("timestamp", item.timestamp)
                put("text", item.text)
                put("detections", detsArr)
                put("imageFileName", item.imageFileName)
                if (item.translatedText != null) put("translatedText", item.translatedText)
            }
            arr.put(obj)
        }
        try {
            val tmpFile = File(historyDir, "history.json.tmp")
            tmpFile.writeText(arr.toString())
            if (!tmpFile.renameTo(indexFile)) {
                indexFile.writeText(arr.toString())
                tmpFile.delete()
            }
        } catch (_: Exception) {
            // Ignore disk write failure
        }
    }

    companion object {
        private const val MAX_ITEMS = 50
    }
}
