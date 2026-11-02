package com.nakamura196.kotenocr

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nakamura196.kotenocr.ocr.OCREngine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class OCRBenchmarkTest {

    private lateinit var engine: OCREngine
    private lateinit var modelDir: String

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cacheDir = File(context.cacheDir, "benchmark_models")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        // Copy models from assets to cache
        for (name in listOf(
            "rtmdet-s-1280x1280.onnx",
            "parseq-ndl-32x384-tiny-10.onnx",
            "NDLmoji.yaml",
            "ndl.yaml",
        )) {
            val dest = File(cacheDir, name)
            if (!dest.exists()) {
                context.assets.open("models/$name").use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                Log.d("Benchmark", "Copied: $name")
            }
        }

        modelDir = cacheDir.absolutePath
        engine = OCREngine()
        engine.initialize(modelDir)
        Log.d("Benchmark", "Engine initialized")
    }

    @After
    fun tearDown() {
        engine.close()
    }

    @Test
    fun benchmarkKotenOCR() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Load test image
        val imageBytes = context.assets.open("test_koten_sample.jpg").readBytes()
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        Log.d("Benchmark", "Image: ${bitmap.width}x${bitmap.height}")

        // Warm up
        engine.process(bitmap)

        // Measure
        val start = System.currentTimeMillis()
        val result = engine.process(bitmap)
        val elapsed = System.currentTimeMillis() - start

        Log.d("Benchmark", "")
        Log.d("Benchmark", "=== KOTLIN NATIVE BENCHMARK ===")
        Log.d("Benchmark", "Total: ${elapsed}ms")
        Log.d("Benchmark", "Detections: ${result.detections.size}")
        Log.d("Benchmark", "Text: ${result.text.take(200)}")
        Log.d("Benchmark", "================================")

        bitmap.recycle()

        assert(result.detections.isNotEmpty())
        assert(result.text.isNotEmpty())
    }
}
