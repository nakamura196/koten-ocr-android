package com.nakamura196.kotenocr.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Translation requests, one per page (history item), run in an app-lifetime [scope] instead of
 * the result screen's. Leaving the screen — translation settings, history, another page — used to
 * cancel the request and drop the result; now it finishes and [onTranslated] stores it.
 * iOS `ResultOverlayView.performTranslation` behaves the same (an unstructured Task that saves
 * to the history item it started from).
 */
class PageTranslations(
    private val scope: CoroutineScope,
    private val translate: suspend (String) -> String,
    private val errorMessage: (Exception) -> String,
    private val onTranslated: (pageKey: String, result: String) -> Unit
) {
    private val _running = MutableStateFlow<Set<String>>(emptySet())
    /** Pages whose translation is still in flight. */
    val running: StateFlow<Set<String>> = _running.asStateFlow()

    private val _errors = MutableStateFlow<Map<String, String>>(emptyMap())
    /** Last error per page; cleared when that page is translated again. */
    val errors: StateFlow<Map<String, String>> = _errors.asStateFlow()

    /** Starts translating [text] for [pageKey]; false if that page is already being translated. */
    fun start(pageKey: String, text: String): Boolean {
        if (pageKey in _running.value) return false
        _running.value = _running.value + pageKey
        _errors.value = _errors.value - pageKey
        scope.launch {
            try {
                onTranslated(pageKey, translate(text))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errors.value = _errors.value + (pageKey to errorMessage(e))
            } finally {
                _running.value = _running.value - pageKey
            }
        }
        return true
    }
}
