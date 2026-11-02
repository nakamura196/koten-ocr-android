package com.nakamura196.kotenocr.data

import com.nakamura196.kotenocr.models.HistoryItem
import org.json.JSONArray
import java.io.File

/**
 * Reads and writes the history index (`history.json`) without ever losing entries
 * to a bad read.
 *
 * iOS keeps one JSON file per scan, so a damaged file costs one entry. Android keeps
 * a single index, which used to be treated as empty when it failed to parse — and
 * the next save then overwrote the whole history. Here:
 *  - a damaged entry is skipped, the others load;
 *  - if anything could not be read, the original index is copied aside
 *    (`history.json.broken-<time>`) before it can be overwritten;
 *  - writes go to a temp file and are renamed into place.
 *
 * Pure JVM (no Android types) so it can be unit tested.
 */
class HistoryStore(private val dir: File, private val clock: () -> Long = System::currentTimeMillis) {

    val indexFile = File(dir, INDEX_NAME)

    data class LoadResult(
        val items: List<HistoryItem>,
        /** Entries that could not be parsed and were skipped. */
        val skipped: Int,
        /** Where the unreadable original was copied, or null when nothing was lost. */
        val backup: File?,
    )

    fun load(): LoadResult {
        if (!indexFile.exists()) return LoadResult(emptyList(), 0, null)

        val arr = try {
            JSONArray(indexFile.readText())
        } catch (_: Exception) {
            return LoadResult(emptyList(), 0, backUp())
        }

        val items = mutableListOf<HistoryItem>()
        var skipped = 0
        for (i in 0 until arr.length()) {
            try {
                items.add(HistoryItem.fromJson(arr.getJSONObject(i)))
            } catch (_: Exception) {
                skipped++
            }
        }
        val backup = if (skipped > 0) backUp() else null
        return LoadResult(items.sortedByDescending { it.timestamp }, skipped, backup)
    }

    /** Returns false when the index could not be written (the previous one is left intact). */
    fun write(items: List<HistoryItem>): Boolean {
        val arr = JSONArray()
        for (item in items) arr.put(item.toJson())
        val tmp = File(dir, "$INDEX_NAME.tmp")
        return try {
            tmp.writeText(arr.toString())
            if (tmp.renameTo(indexFile)) {
                true
            } else {
                // Some filesystems refuse to rename over an existing file.
                tmp.copyTo(indexFile, overwrite = true)
                tmp.delete()
                true
            }
        } catch (_: Exception) {
            tmp.delete()
            false
        }
    }

    private fun backUp(): File? = try {
        val dest = File(dir, "$INDEX_NAME.broken-${clock()}")
        indexFile.copyTo(dest, overwrite = true)
        dest
    } catch (_: Exception) {
        null
    }

    companion object {
        const val INDEX_NAME = "history.json"
    }
}
