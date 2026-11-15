package com.rawedit.app.domain

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.rawedit.app.data.CacheManager
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

object ChatbotFileTools {

    fun createStyledPdf(
        outFile: File,
        title: String,
        bodyText: String
    ): File {
        outFile.parentFile?.mkdirs()
        val pageWidth = 595
        val pageHeight = 842
        val marginX = 44f
        val maxTextWidth = pageWidth - marginX * 2f

        val doc = PdfDocument()
        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas = page.canvas
        var y = 56f

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111112")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8A5D3B")
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#222225")
            textSize = 11.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7A7A80")
            textSize = 9.5f
        }

        fun nextPageIfNeeded(neededHeight: Float) {
            if (y + neededHeight > pageHeight - 48f) {
                canvas.drawText("RAW EDIT Studio • Page $pageNum", marginX, pageHeight - 24f, footerPaint)
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                canvas = page.canvas
                y = 52f
            }
        }

        fun drawWrapped(text: String, paint: Paint, lineHeight: Float, indent: Float = 0f) {
            val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) {
                y += lineHeight * 0.5f
                return
            }
            val avail = (maxTextWidth - indent).coerceAtLeast(100f)
            var line = StringBuilder()
            for (w in words) {
                val candidate = if (line.isEmpty()) w else "$line $w"
                if (paint.measureText(candidate) > avail && line.isNotEmpty()) {
                    nextPageIfNeeded(lineHeight)
                    canvas.drawText(line.toString(), marginX + indent, y, paint)
                    y += lineHeight
                    line = StringBuilder(w)
                } else {
                    line = StringBuilder(candidate)
                }
            }
            if (line.isNotEmpty()) {
                nextPageIfNeeded(lineHeight)
                canvas.drawText(line.toString(), marginX + indent, y, paint)
                y += lineHeight
            }
        }

        drawWrapped(title.ifBlank { "RAW EDIT Document" }, titlePaint, 26f)
        y += 8f

        val lines = bodyText.replace("\r\n", "\n").lines()
        for (raw in lines) {
            val t = raw.trim()
            when {
                t.isEmpty() -> y += 8f
                t.startsWith("### ") -> {
                    y += 4f
                    drawWrapped(t.removePrefix("### ").replace("**", ""), headingPaint, 19f)
                }
                t.startsWith("## ") || t.startsWith("# ") -> {
                    y += 6f
                    val h = t.removePrefix("## ").removePrefix("# ").replace("**", "")
                    drawWrapped(h, headingPaint, 21f)
                }
                t.startsWith("- ") || t.startsWith("* ") || t.startsWith("\u2022 ") -> {
                    val b = "\u2022  " + t.drop(2).trim().replace("**", "")
                    drawWrapped(b, bodyPaint, 16f, indent = 10f)
                }
                else -> {
                    drawWrapped(t.replace("**", ""), bodyPaint, 16f)
                }
            }
        }

        canvas.drawText("RAW EDIT Studio • Page $pageNum", marginX, pageHeight - 24f, footerPaint)
        doc.finishPage(page)

        FileOutputStream(outFile).use { fos ->
            doc.writeTo(fos)
        }
        doc.close()
        return outFile
    }

    fun inspectLocalFile(file: File): String {
        if (!file.exists()) return "File not found: ${file.name}"
        val ext = file.extension.lowercase()
        if (ext == "zip") {
            return runCatching {
                val sb = StringBuilder()
                ZipFile(file).use { zf ->
                    val entries = zf.entries().toList()
                    sb.appendLine("ZIP Archive: ${file.name} (${entries.size} entries)")
                    entries.take(60).forEach { e ->
                        sb.appendLine(" - ${e.name} (${e.size} bytes)")
                    }
                    var previews = 0
                    for (e in entries) {
                        if (previews >= 3) break
                        if (!e.isDirectory && e.size in 1..40_000 &&
                            e.name.substringAfterLast('.').lowercase() in setOf("txt", "md", "skill", "json", "html", "css", "js", "kt", "srt")
                        ) {
                            previews++
                            val text = zf.getInputStream(e).bufferedReader(Charsets.UTF_8).use { it.readText() }
                            sb.appendLine("\n--- ${e.name} ---")
                            sb.appendLine(text.take(3000))
                        }
                    }
                }
                sb.toString()
            }.getOrElse { "Could not read ZIP: ${it.message}" }
        }
        return runCatching {
            file.readText(Charsets.UTF_8).take(14_000)
        }.getOrElse { "Binary file (${file.length()} bytes)" }
    }

    fun saveWebsiteHtml(cacheManager: CacheManager, title: String, html: String): File {
        val safeName = title.lowercase()
            .replace(Regex("[^a-z0-9_\\-]"), "_")
            .trim('_')
            .ifBlank { "site_${System.currentTimeMillis()}" }
        val outFile = File(cacheManager.sharedPacksDir, "${safeName}_preview.html")
        outFile.parentFile?.mkdirs()
        outFile.writeText(html, Charsets.UTF_8)
        return outFile
    }
}
