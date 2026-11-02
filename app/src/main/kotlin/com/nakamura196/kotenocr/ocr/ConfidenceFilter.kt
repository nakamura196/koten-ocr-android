package com.nakamura196.kotenocr.ocr

import kotlin.math.roundToInt

/**
 * Display-only confidence filtering for the Result screen slider (v2.1).
 *
 * The full `[Detection]` list stays intact in memory and history so lowering the slider
 * restores hidden lines immediately without re-running OCR.
 */
object ConfidenceFilter {

    /** Default slider position (0.30). */
    const val DEFAULT_THRESHOLD: Float = 0.30f

    /** Allowed slider range (`0.25..0.90`). */
    val RANGE: ClosedFloatingPointRange<Float> = 0.25f..0.90f

    /** Number of discrete 0.05 steps inside `0.25..0.90` (13 intervals = 12 intermediate steps). */
    const val STEPS: Int = 12

    data class IndexedDetection(
        val originalIndex: Int,
        val detection: Detection
    )

    /**
     * Detections whose [Detection.score] is at or above [threshold], paired with their index
     * in the full [detections] list.
     */
    fun visible(detections: List<Detection>, threshold: Float): List<IndexedDetection> =
        detections.mapIndexedNotNull { idx, det ->
            if (det.score >= threshold - 1e-5f) IndexedDetection(idx, det) else null
        }

    /** Joined text of visible detections for Copy / Share / Export. */
    fun text(detections: List<Detection>, threshold: Float): String =
        visible(detections, threshold)
            .map { it.detection.text }
            .filter { it.isNotEmpty() }
            .joinToString("\n")

    /** Formats a threshold in `0..1` as a percentage string, e.g. `"30%"`. */
    fun percentText(value: Float): String = "${(value * 100f).roundToInt()}%"
}
