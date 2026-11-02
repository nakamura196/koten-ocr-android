package com.nakamura196.kotenocr.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.InputStream
import kotlin.math.max

/**
 * Normalizes EXIF orientation and caps incoming images at [MAX_LONG_SIDE] (4096 px)
 * before full-resolution bitmaps can exhaust memory on 48MP+ camera sensors.
 */
object ImageIntake {

    const val MAX_LONG_SIDE = 4096

    /**
     * Loads a [Bitmap] from [uri] with EXIF orientation applied and long side <= [maxLongSide].
     */
    fun loadFromUri(
        context: Context,
        uri: Uri,
        maxLongSide: Int = MAX_LONG_SIDE
    ): Bitmap? {
        return try {
            val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
                readExifOrientation(stream)
            } ?: ExifInterface.ORIENTATION_NORMAL

            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            val rawW = boundsOpts.outWidth
            val rawH = boundsOpts.outHeight
            if (rawW <= 0 || rawH <= 0) return null

            val sampleSize = computeSampleSize(rawW, rawH, maxLongSide)
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return null

            transformAndLimit(decoded, orientation, maxLongSide)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Loads a [Bitmap] from [file] with EXIF orientation applied and long side <= [maxLongSide].
     */
    fun loadFromFile(
        file: File,
        maxLongSide: Int = MAX_LONG_SIDE
    ): Bitmap? {
        if (!file.exists()) return null
        return try {
            val orientation = try {
                ExifInterface(file.absolutePath).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
            val rawW = boundsOpts.outWidth
            val rawH = boundsOpts.outHeight
            if (rawW <= 0 || rawH <= 0) return null

            val sampleSize = computeSampleSize(rawW, rawH, maxLongSide)
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null
            transformAndLimit(decoded, orientation, maxLongSide)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Loads a bundled asset image (e.g. `samples/sample_taketori.jpg`) with EXIF orientation applied.
     */
    fun loadFromAsset(
        context: Context,
        assetPath: String,
        maxLongSide: Int = MAX_LONG_SIDE
    ): Bitmap? {
        return try {
            val orientation = context.assets.open(assetPath).use { stream ->
                readExifOrientation(stream)
            }
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open(assetPath).use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            val rawW = boundsOpts.outWidth
            val rawH = boundsOpts.outHeight
            if (rawW <= 0 || rawH <= 0) return null

            val sampleSize = computeSampleSize(rawW, rawH, maxLongSide)
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = context.assets.open(assetPath).use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return null

            transformAndLimit(decoded, orientation, maxLongSide)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Limits an existing [Bitmap] so its long side is at most [maxLongSide]. Never upscales.
     */
    fun limit(bitmap: Bitmap, maxLongSide: Int = MAX_LONG_SIDE): Bitmap {
        val longSide = max(bitmap.width, bitmap.height)
        if (longSide <= maxLongSide) return bitmap
        val scale = maxLongSide.toFloat() / longSide.toFloat()
        val matrix = Matrix().apply { postScale(scale, scale) }
        val scaled = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    /**
     * Rotates [bitmap] 90° clockwise into a new [Bitmap].
     */
    fun rotate90Clockwise(bitmap: Bitmap): Bitmap {
        val matrix = Matrix().apply { postRotate(90f) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun readExifOrientation(stream: InputStream): Int {
        return try {
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    private fun computeSampleSize(width: Int, height: Int, maxLongSide: Int): Int {
        var sample = 1
        var longSide = max(width, height)
        while (longSide / (sample * 2) >= maxLongSide) {
            sample *= 2
        }
        return sample
    }

    private fun transformAndLimit(
        bitmap: Bitmap,
        orientation: Int,
        maxLongSide: Int
    ): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
        }

        val longSide = max(bitmap.width, bitmap.height)
        if (longSide > maxLongSide) {
            val scale = maxLongSide.toFloat() / longSide.toFloat()
            matrix.postScale(scale, scale)
        }

        if (matrix.isIdentity) return bitmap

        val transformed = Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
        if (transformed !== bitmap) {
            bitmap.recycle()
        }
        return transformed
    }
}
