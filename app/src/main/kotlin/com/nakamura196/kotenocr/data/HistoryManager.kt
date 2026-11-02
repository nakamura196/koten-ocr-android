package com.nakamura196.kotenocr.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.nakamura196.kotenocr.models.HistoryItem
import com.nakamura196.kotenocr.ocr.Detection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

/**
 * Persists OCR scan history on disk (no item cap, like `HistoryManager.swift`) with
 * downsampled thumbnail loading. Reading and writing the index is in [HistoryStore].
 */
class HistoryManager(context: Context) {

    private val historyDir = File(context.filesDir, "history").apply { mkdirs() }
    private val store = HistoryStore(historyDir)

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

        // No cap: iOS keeps every scan, and silently dropping old ones lost user work.
        val updated = listOf(item) + _items.value
        _items.value = updated
        persistToDisk(updated)
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
        val result = store.load()
        if (result.skipped > 0 || result.backup != null) {
            Log.w(TAG, "history index partly unreadable: skipped=${result.skipped} backup=${result.backup?.name}")
        }
        _items.value = result.items
    }

    private fun persistToDisk(items: List<HistoryItem>) {
        if (!store.write(items)) Log.w(TAG, "failed to write history index")
    }

    companion object {
        private const val TAG = "HistoryManager"
    }
}
