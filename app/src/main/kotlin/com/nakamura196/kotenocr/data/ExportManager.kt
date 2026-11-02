package com.nakamura196.kotenocr.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.nakamura196.kotenocr.ocr.Detection
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportManager {

    private fun timestampedBaseName(date: Date = Date()): String {
        val fmt = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US)
        return "KotenOCR_${fmt.format(date)}"
    }

    fun shareText(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    fun exportTxt(context: Context, text: String) {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, "${timestampedBaseName()}.txt")
        file.writeText(text)
        shareFile(context, file, "text/plain")
    }

    fun exportPdf(
        context: Context,
        bitmap: Bitmap,
        detections: List<Detection>,
        text: String
    ) {
        val pageWidth = 595 // A4 points
        val pageHeight = 842
        val margin = 40f

        val doc = PdfDocument()

        // Page 1: Image with bounding boxes
        val page1Info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page1 = doc.startPage(page1Info)
        val canvas1: Canvas = page1.canvas

        val availW = pageWidth - margin * 2
        val availH = pageHeight - margin * 2
        val scale = minOf(availW / bitmap.width, availH / bitmap.height)
        val drawW = (bitmap.width * scale).toInt()
        val drawH = (bitmap.height * scale).toInt()
        val offsetX = margin + (availW - drawW) / 2f
        val offsetY = margin + (availH - drawH) / 2f

        val dstRect = Rect(
            offsetX.toInt(),
            offsetY.toInt(),
            (offsetX + drawW).toInt(),
            (offsetY + drawH).toInt()
        )
        canvas1.drawBitmap(bitmap, null, dstRect, null)

        val boxPaint = Paint().apply {
            color = Color.argb(180, 181, 69, 47)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        val numPaint = Paint().apply {
            color = Color.rgb(181, 69, 47)
            textSize = 9f
            isAntiAlias = true
        }
        for ((index, det) in detections.withIndex()) {
            val b = det.box
            if (b.size < 4) continue
            val left = offsetX + b[0] * scale
            val top = offsetY + b[1] * scale
            val right = offsetX + b[2] * scale
            val bottom = offsetY + b[3] * scale
            canvas1.drawRect(left, top, right, bottom, boxPaint)
            canvas1.drawText("${index + 1}", left + 2f, top + 10f, numPaint)
        }

        doc.finishPage(page1)

        // Page 2+: Recognized text
        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
            isAntiAlias = true
        }
        val headerPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 14f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val lines = text.split("\n")
        val lineHeight = 18f
        var currentY = margin + 20f
        var pageNum = 2
        var currentPage = doc.startPage(
            PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
        )
        currentPage.canvas.drawText("OCR Result", margin, currentY, headerPaint)
        currentY += lineHeight * 1.5f

        for ((index, line) in lines.withIndex()) {
            if (currentY + lineHeight > pageHeight - margin) {
                doc.finishPage(currentPage)
                pageNum++
                currentPage = doc.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                )
                currentY = margin + lineHeight
            }
            val numbered = "${index + 1}. $line"
            val maxChars = ((availW) / 7f).toInt().coerceAtLeast(20)
            var remaining = numbered
            while (remaining.isNotEmpty()) {
                val chunk = remaining.take(maxChars)
                remaining = remaining.drop(maxChars)
                if (currentY + lineHeight > pageHeight - margin) {
                    doc.finishPage(currentPage)
                    pageNum++
                    currentPage = doc.startPage(
                        PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                    )
                    currentY = margin + lineHeight
                }
                currentPage.canvas.drawText(chunk, margin, currentY, textPaint)
                currentY += lineHeight
            }
        }
        doc.finishPage(currentPage)

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, "${timestampedBaseName()}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()

        shareFile(context, file, "application/pdf")
    }

    private fun shareFile(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }
}
