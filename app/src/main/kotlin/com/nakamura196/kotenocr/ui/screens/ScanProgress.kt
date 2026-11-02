package com.nakamura196.kotenocr.ui.screens

/**
 * What the processing screen shows at each moment, kept free of Android so it can be unit-tested.
 * Mirrors `ScanningView.swift` and `ContentView.finishScan` on iOS.
 *
 * Two phases: while OCR runs, [progress] (0..1) drives everything; once it has finished,
 * the found lines are revealed one by one ([total] lines, [revealed] shown so far).
 */
object ScanProgress {
    enum class Status { PREPARING, FINDING_LINES, READING_TEXT, DONE }

    /** Recognition reports 0.9 when it ends, so the beam reaches the left edge there. */
    fun sweep(progress: Float): Float = (progress / 0.9f).coerceIn(0f, 1f)

    fun status(progress: Float, total: Int, revealed: Int): Status = when {
        total > 0 -> if (revealed >= total) Status.DONE else Status.READING_TEXT
        progress < 0.2f -> Status.PREPARING
        progress < 0.5f -> Status.FINDING_LINES
        else -> Status.READING_TEXT
    }

    /** The 朱 progress bar: the engine's progress, then the share of lines revealed. */
    fun bar(progress: Float, total: Int, revealed: Int): Float =
        if (total > 0) (revealed.toFloat() / total).coerceIn(0f, 1f) else progress.coerceIn(0f, 1f)

    /** The title switches from "Reading" to "N lines" once every line is shown. */
    fun titleShowsCount(total: Int, revealed: Int): Boolean = total > 0 && revealed >= total

    /** About 40 ms per line, at most 0.8 s for the whole page (iOS `min(0.04, 0.8 / n)`). */
    fun revealStepMs(total: Int): Long = if (total <= 0) 0L else minOf(40L, 800L / total)

    /** Pause on the fully revealed page before the result opens (iOS 0.45 s). */
    const val HOLD_AFTER_REVEAL_MS = 450L

    /** Only the newest few recognized lines are listed under the status. */
    const val LISTED_LINES = 4

    /** Indices (0-based) of the lines to list: the last [LISTED_LINES] of those revealed. */
    fun listedRange(total: Int, revealed: Int): IntRange {
        val shown = revealed.coerceIn(0, total)
        return maxOf(0, shown - LISTED_LINES) until shown
    }
}
