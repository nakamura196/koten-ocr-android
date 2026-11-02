package com.nakamura196.kotenocr.util

/**
 * Remembers whether the reader last used the normal camera or the document scanner
 * (iOS `CaptureMode`). Pure Kotlin so the decisions can be tested on the JVM.
 */
enum class CaptureMode(val key: String) {
    CAMERA("camera"),
    SCAN("scan");

    companion object {
        const val PREF_KEY = "lastCaptureMode"

        /** Unknown or missing values mean the normal camera. */
        fun fromKey(key: String?): CaptureMode = entries.firstOrNull { it.key == key } ?: CAMERA

        /**
         * Whether the camera screen should open the scanner by itself when it appears.
         * Only when the last use was the scanner, the scanner can run on this device,
         * and the camera screen was not just left by a cancelled scan in this visit.
         */
        fun shouldAutoOpenScanner(
            last: CaptureMode,
            scannerSupported: Boolean,
            alreadyHandledThisVisit: Boolean
        ): Boolean = last == SCAN && scannerSupported && !alreadyHandledThisVisit
    }
}

/** Reads and writes the last capture mode through plain functions (backed by SharedPreferences in the app). */
class CaptureModeStore(
    private val read: () -> String?,
    private val write: (String) -> Unit
) {
    fun load(): CaptureMode = CaptureMode.fromKey(read())
    fun save(mode: CaptureMode) = write(mode.key)
}
