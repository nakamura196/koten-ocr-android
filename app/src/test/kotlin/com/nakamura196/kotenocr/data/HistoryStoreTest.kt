package com.nakamura196.kotenocr.data

import com.nakamura196.kotenocr.models.HistoryItem
import com.nakamura196.kotenocr.ocr.Detection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Regression tests for the two ways history used to disappear:
 *  1. one unreadable index → loaded as empty → next save overwrote everything;
 *  2. more than 50 scans → the oldest were deleted without telling the user.
 */
class HistoryStoreTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun item(n: Int) = HistoryItem(
        id = "id-$n",
        timestamp = 1_000_000L + n,
        text = "行$n",
        detections = listOf(Detection(0, intArrayOf(1, 2, 3, 4), 0.9f, 1, "line_main", "行$n", 12f)),
        imageFileName = "id-$n.jpg",
        translatedText = if (n % 2 == 0) "line $n" else null,
    )

    @Test fun roundTripKeepsEveryField() {
        val store = HistoryStore(tmp.root)
        val items = listOf(item(2), item(1))
        assertTrue(store.write(items))
        val r = store.load()
        assertEquals(0, r.skipped)
        assertNull(r.backup)
        assertEquals(items.map { it.id }, r.items.map { it.id })
        assertEquals("line 2", r.items[0].translatedText)
        assertNull(r.items[1].translatedText)
        assertEquals(listOf(1, 2, 3, 4), r.items[0].detections[0].box.toList())
        assertEquals("行2", r.items[0].detections[0].text)
    }

    @Test fun moreThanFiftyItemsAreAllKept() {
        val store = HistoryStore(tmp.root)
        val items = (1..120).map(::item).sortedByDescending { it.timestamp }
        store.write(items)
        assertEquals(120, store.load().items.size)
    }

    @Test fun historyManagerHasNoItemCap() {
        // HistoryManager needs a Context, so check its source for the old cap instead.
        val src = File("src/main/kotlin/com/nakamura196/kotenocr/data/HistoryManager.kt").readText()
        assertTrue("MAX_ITEMS cap must not come back", !src.contains("MAX_ITEMS"))
        assertTrue(!Regex("""\.take\(""").containsMatchIn(src))
    }

    @Test fun unreadableIndexIsBackedUpNotLost() {
        val store = HistoryStore(tmp.root, clock = { 42L })
        store.write(listOf(item(1), item(2)))
        val original = store.indexFile.readText()
        store.indexFile.writeText(original.dropLast(10)) // truncated write / disk trouble
        val damaged = store.indexFile.readText()

        val r = store.load()
        assertEquals(0, r.items.size)
        assertNotNull(r.backup)
        assertEquals(File(tmp.root, "history.json.broken-42"), r.backup)
        assertEquals(damaged, r.backup!!.readText())

        // The next save must not destroy the only copy of the old history.
        store.write(listOf(item(3)))
        assertEquals(damaged, r.backup.readText())
    }

    @Test fun oneBadEntryDoesNotDropTheOthers() {
        val store = HistoryStore(tmp.root, clock = { 7L })
        store.write(listOf(item(3), item(2), item(1)))
        // Break the middle entry (missing required "imageFileName").
        val broken = store.indexFile.readText().replaceFirst("\"imageFileName\":\"id-2.jpg\"", "\"x\":1")
        store.indexFile.writeText(broken)

        val r = store.load()
        assertEquals(listOf("id-3", "id-1"), r.items.map { it.id })
        assertEquals(1, r.skipped)
        assertNotNull(r.backup)
        assertTrue(r.backup!!.readText().contains("id-2"))
    }

    @Test fun missingIndexIsEmptyWithoutBackup() {
        val r = HistoryStore(tmp.root).load()
        assertEquals(0, r.items.size)
        assertNull(r.backup)
        assertEquals(0, tmp.root.listFiles()!!.size)
    }

    @Test fun readsIndexWrittenByPreviousVersion() {
        // Format written by the old HistoryManager.persistToDisk (no "date", no translatedText key).
        File(tmp.root, "history.json").writeText(
            """[{"id":"a","timestamp":5,"text":"t","detections":[{"id":0,"box":[1,2,3,4],"score":0.5,""" +
                """"classIndex":1,"className":"line_main","text":"t","predCharCount":100}],"imageFileName":"a.jpg"}]"""
        )
        val r = HistoryStore(tmp.root).load()
        assertEquals(listOf("a"), r.items.map { it.id })
        assertNull(r.items[0].translatedText)
        assertNull(r.backup)
    }
}
