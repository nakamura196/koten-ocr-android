package com.nakamura196.kotenocr.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.test.platform.app.InstrumentationRegistry
import com.nakamura196.kotenocr.data.SettingsManager
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Crop screen: a big pinch stops at 8x (was 5x) and shows "8.0x"; handles stay their normal size.
 * Screenshots go to the app's external files dir (crop-zoom-*.png) for a visual check.
 */
class CropZoomUiTest {
    @get:Rule val rule = createComposeRule()

    private fun sampleBitmap(): Bitmap {
        val bmp = Bitmap.createBitmap(800, 1200, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(240, 230, 210))
        val p = Paint().apply { color = Color.DKGRAY; strokeWidth = 4f }
        for (i in 0..12) c.drawLine(60f + i * 55f, 80f, 60f + i * 55f, 1120f, p)
        return bmp
    }

    private fun shot(name: String) {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = ctx.getExternalFilesDir(null) ?: return
        File(dir, "crop-zoom-$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun pinchReachesEightTimesAndShowsTheLabel() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = SettingsManager(ctx)
        val bmp = sampleBitmap()
        rule.setContent {
            CropScreen(bitmap = bmp, settingsManager = settings, onCropped = { _, _ -> }, onSkip = {}, onCancel = {})
        }
        rule.waitForIdle()
        rule.onNodeWithText("x", substring = true).assertDoesNotExist()
        shot("1x")

        // Pinch out several times from the middle of the screen; each step multiplies the zoom.
        repeat(4) {
            rule.onRoot().performTouchInput {
                val cx = center.x; val cy = center.y
                pinch(
                    start0 = Offset(cx - 40f, cy), end0 = Offset(cx - 300f, cy),
                    start1 = Offset(cx + 40f, cy), end1 = Offset(cx + 300f, cy),
                    durationMillis = 400
                )
            }
            rule.waitForIdle()
        }
        rule.onNodeWithText("8.0x").assertExists()
        shot("8x")

    }

    /** With the left edge pulled to the middle, zoom to 8x: its bar must look the same size as at 1x. */
    @Test fun cornerDotKeepsItsSizeAtEightTimes() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = SettingsManager(ctx)
        settings.markCoachMarkSeen(com.nakamura196.kotenocr.data.CoachMark.CROP_CORNER)
        val bmp = sampleBitmap()
        rule.setContent {
            CropScreen(bitmap = bmp, settingsManager = settings, onCropped = { _, _ -> }, onSkip = {}, onCancel = {})
        }
        rule.waitForIdle()
        // The left edge bar sits at the left of the frame, halfway down; drag it to just left of the centre.
        rule.onRoot().performTouchInput {
            val start = Offset(48f, center.y - 170f)
            val target = Offset(center.x - 30f, center.y - 170f)
            down(start)
            repeat(20) { i -> moveTo(start + (target - start) * ((i + 1) / 20f)) }
            up()
        }
        rule.waitForIdle()
        shot("corner-1x")
        repeat(4) {
            rule.onRoot().performTouchInput {
                val cx = center.x; val cy = center.y + 300f
                pinch(
                    start0 = Offset(cx - 40f, cy), end0 = Offset(cx - 300f, cy),
                    start1 = Offset(cx + 40f, cy), end1 = Offset(cx + 300f, cy),
                    durationMillis = 400
                )
            }
            rule.waitForIdle()
        }
        rule.onNodeWithText("8.0x").assertExists()
        shot("8x-corner")
    }
}
