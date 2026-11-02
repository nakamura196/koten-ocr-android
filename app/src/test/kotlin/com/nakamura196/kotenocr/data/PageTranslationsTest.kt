package com.nakamura196.kotenocr.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression: leaving the result screen while a translation was running cancelled it, so the
 * translation was neither shown nor saved to history. The request now belongs to the app, not
 * the screen.
 */
class PageTranslationsTest {

    @Test
    fun translationFinishesAfterTheScreenThatStartedItIsGone() = runBlocking {
        val reply = CompletableDeferred<String>()
        val saved = mutableMapOf<String, String>()
        val translations = PageTranslations(
            scope = this,
            translate = { reply.await() },
            errorMessage = { it.message.orEmpty() },
            onTranslated = { key, result -> saved[key] = result }
        )

        // The screen's own scope: what the request used to run in.
        val screenScope = CoroutineScope(Job())
        assertTrue(translations.start("page-1", "いろは"))
        yield()
        screenScope.coroutineContext[Job]!!.cancelAndJoin() // user leaves the screen

        assertEquals(setOf("page-1"), translations.running.value)
        reply.complete("訳文")
        yield(); yield()

        assertEquals(mapOf("page-1" to "訳文"), saved)
        assertTrue(translations.running.value.isEmpty())
    }

    @Test
    fun sameScreenCannotStartTwoRequestsButOtherPagesCan() = runBlocking {
        val reply = CompletableDeferred<String>()
        val saved = mutableMapOf<String, String>()
        val translations = PageTranslations(this, { reply.await() }, { "" }) { k, r -> saved[k] = r }

        assertTrue(translations.start("a", "x"))
        assertFalse(translations.start("a", "x"))
        assertTrue(translations.start("b", "y"))
        assertEquals(setOf("a", "b"), translations.running.value)

        reply.complete("ok")
        yield(); yield()
        assertEquals(mapOf("a" to "ok", "b" to "ok"), saved)
    }

    @Test
    fun errorIsKeptPerPageAndClearedOnRetry() = runBlocking {
        var fail = true
        val translations = PageTranslations(
            scope = this,
            translate = { if (fail) throw IllegalStateException("down") else "ok" },
            errorMessage = { "error: ${it.message}" },
            onTranslated = { _, _ -> }
        )

        translations.start("a", "x")
        yield(); yield()
        assertEquals(mapOf("a" to "error: down"), translations.errors.value)
        assertTrue(translations.running.value.isEmpty())

        fail = false
        translations.start("a", "x")
        assertTrue(translations.errors.value.isEmpty())
    }
}
