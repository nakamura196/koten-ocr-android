package com.nakamura196.kotenocr.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import com.google.android.gms.common.GoogleApiAvailability
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/**
 * Thin wrapper over the ML Kit Document Scanner (Google Play services). One page, JPEG,
 * no gallery import (the photo button covers that). BASE mode: crop/rotate only, so the
 * app's own crop screen is the only place that straightens the page.
 */
object DocumentScanner {

    /** The scanner lives in Google Play services; without it the button is hidden. */
    fun isSupported(context: Context): Boolean = try {
        GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context) == com.google.android.gms.common.ConnectionResult.SUCCESS
    } catch (_: Throwable) {
        false
    }

    /** Asks for the scanner's start intent. [onFailure] is called on any error (never throws). */
    fun start(activity: Activity, onReady: (IntentSender) -> Unit, onFailure: () -> Unit) {
        try {
            val options = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(false)
                .setPageLimit(1)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE)
                .build()
            GmsDocumentScanning.getClient(options)
                .getStartScanIntent(activity)
                .addOnSuccessListener { onReady(it) }
                .addOnFailureListener { onFailure() }
        } catch (_: Throwable) {
            onFailure()
        }
    }

    /** The first page's image, or null when the result carries none. */
    fun firstPageUri(data: Intent?): Uri? = try {
        GmsDocumentScanningResult.fromActivityResultIntent(data)?.pages?.firstOrNull()?.imageUri
    } catch (_: Throwable) {
        null
    }
}
