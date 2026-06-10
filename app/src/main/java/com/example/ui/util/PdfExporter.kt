package com.example.ui.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.ChecklistItem
import com.example.data.Note
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExporter {

    fun exportNoteToPdf(
        context: Context,
        note: Note,
        checklistItems: List<ChecklistItem>
    ) {
        try {
            val pdfDocument = PdfDocument()
            val pageWidth = 595 // A4 Width in PostScript points
            val pageHeight = 842 // A4 Height in PostScript points
            val leftMargin = 54f
            val rightMargin = 54f
            val topMargin = 54f
            val bottomMargin = 54f
            val maxContentWidth = pageWidth - leftMargin - rightMargin
            val maxContentHeight = pageHeight - topMargin - bottomMargin

            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var currentPage = pdfDocument.startPage(pageInfo)
            var currentCanvas = currentPage.canvas

            // Paints for various drawings
            val titlePaint = TextPaint().apply {
                isAntiAlias = true
                color = 0xFF0F172A.toInt() // Slate dark
                textSize = 24f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            val metaPaint = TextPaint().apply {
                isAntiAlias = true
                color = 0xFF475569.toInt() // Soft slate
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }

            val headerPaint = TextPaint().apply {
                isAntiAlias = true
                color = 0xFF94A3B8.toInt()
                textSize = 8f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            val footerPaint = TextPaint().apply {
                isAntiAlias = true
                color = 0xFF94A3B8.toInt()
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }

            val linePaint = Paint().apply {
                isAntiAlias = true
                color = 0xFFE2E8F0.toInt() // Border light
                strokeWidth = 1f
                style = Paint.Style.STROKE
            }

            val categoryBoxPaint = Paint().apply {
                isAntiAlias = true
                color = 0xFF2563EB.toInt() // Electric Blue theme
                style = Paint.Style.FILL
            }

            val categoryTextPaint = TextPaint().apply {
                isAntiAlias = true
                color = 0xFFFFFFFF.toInt()
                textSize = 8f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            var currentY = topMargin

            // Helper to draw the header on pages 2+
            fun drawRunningHeader(canvas: Canvas, pageNum: Int) {
                canvas.drawText("SLATE NOTES ARCHIVE", leftMargin, topMargin - 15f, headerPaint)
                canvas.drawLine(leftMargin, topMargin - 8f, pageWidth - rightMargin, topMargin - 8f, linePaint)
            }

            // Helper to draw footer on all pages
            fun drawFooter(canvas: Canvas, pageNum: Int) {
                canvas.drawLine(leftMargin, pageHeight - bottomMargin + 10f, pageWidth - rightMargin, pageHeight - bottomMargin + 10f, linePaint)
                val footerText = "Page $pageNum"
                val textWidth = footerPaint.measureText(footerText)
                canvas.drawText(footerText, pageWidth - rightMargin - textWidth, pageHeight - bottomMargin + 24f, footerPaint)
                canvas.drawText("Slate WYSIWYG note exporter", leftMargin, pageHeight - bottomMargin + 24f, footerPaint)
            }

            // 1. Draw Page 1 Main Header
            // Subtle watermark
            currentCanvas.drawText("SLATE EXPORTED DOCUMENT", leftMargin, currentY, headerPaint)
            currentY += 20f

            // Note title
            val noteTitle = note.title.ifBlank { "Untitled Note" }
            
            // Handle title line wrapping just in case it is too long
            val titleLayout = StaticLayout.Builder.obtain(noteTitle, 0, noteTitle.length, titlePaint, maxContentWidth.toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.1f)
                .setIncludePad(true)
                .build()

            titleLayout.draw(currentCanvas)
            currentY += titleLayout.height + 15f

            // Category tag and Date row
            val categoryStr = note.category.uppercase()
            val catWidth = categoryTextPaint.measureText(categoryStr) + 12f
            val catHeight = 16f
            
            val catRect = RectF(leftMargin, currentY - 10f, leftMargin + catWidth, currentY - 10f + catHeight)
            currentCanvas.drawRoundRect(catRect, 4f, 4f, categoryBoxPaint)
            currentCanvas.drawText(categoryStr, leftMargin + 6f, currentY + 1f, categoryTextPaint)

            // Date text next to category
            val format = SimpleDateFormat("MMMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
            val dateStr = format.format(Date(note.updatedAt))
            currentCanvas.drawText("Last Saved: $dateStr", leftMargin + catWidth + 12f, currentY + 1f, metaPaint)

            currentY += 18f

            // Rule/Divider line under header
            currentCanvas.drawLine(leftMargin, currentY, pageWidth - rightMargin, currentY, linePaint)
            currentY += 24f

            // 2. Parse WYSIWYG Content to standard Android Spannable
            val spannedContent = parseHtmlToSpannable(note.content)

            // Let's layout the body text
            val bodyPaint = TextPaint().apply {
                isAntiAlias = true
                color = 0xFF1E293B.toInt() // Cosmic slate dark
                textSize = 12f
            }

            val bodyLayout = StaticLayout.Builder.obtain(spannedContent, 0, spannedContent.length, bodyPaint, maxContentWidth.toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.25f)
                .setIncludePad(true)
                .build()

            // Draw body text line-by-line supporting split/multipage
            val lineCount = bodyLayout.lineCount
            for (i in 0 until lineCount) {
                val lineTop = bodyLayout.getLineTop(i)
                val lineBottom = bodyLayout.getLineBottom(i)
                val lineHeight = lineBottom - lineTop

                // Check page height limit
                val maxLimitY = pageHeight - bottomMargin - 15f
                if (currentY + lineHeight > maxLimitY) {
                    // Close current page
                    drawFooter(currentCanvas, pageNumber)
                    pdfDocument.finishPage(currentPage)

                    // Start new page
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    currentPage = pdfDocument.startPage(pageInfo)
                    currentCanvas = currentPage.canvas

                    // Draw headers of new page
                    drawRunningHeader(currentCanvas, pageNumber)
                    currentY = topMargin + 15f
                }

                currentCanvas.save()
                // Clip output to this line
                currentCanvas.clipRect(leftMargin, currentY, pageWidth - rightMargin, currentY + lineHeight)
                // Translate layout coordinates to where the line runs
                currentCanvas.translate(leftMargin, currentY - lineTop)
                bodyLayout.draw(currentCanvas)
                currentCanvas.restore()

                currentY += lineHeight
            }

            // 3. Draw Checklist Section if items exist
            if (checklistItems.isNotEmpty()) {
                currentY += 25f

                // Header for Checklist
                val sectionHeaderPaint = TextPaint().apply {
                    isAntiAlias = true
                    color = 0xFF1E293B.toInt()
                    textSize = 13f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }

                // Check limit before section header
                if (currentY + 30f > pageHeight - bottomMargin) {
                    drawFooter(currentCanvas, pageNumber)
                    pdfDocument.finishPage(currentPage)

                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    currentPage = pdfDocument.startPage(pageInfo)
                    currentCanvas = currentPage.canvas
                    drawRunningHeader(currentCanvas, pageNumber)
                    currentY = topMargin + 15f
                }

                currentCanvas.drawText("TASK CHECKLIST", leftMargin, currentY, sectionHeaderPaint)
                currentY += 12f
                currentCanvas.drawLine(leftMargin, currentY, leftMargin + 100f, currentY, linePaint)
                currentY += 16f

                val checkOutlinePaint = Paint().apply {
                    isAntiAlias = true
                    color = 0xFF94A3B8.toInt()
                    strokeWidth = 1.2f
                    style = Paint.Style.STROKE
                }

                val checkFillPaint = Paint().apply {
                    isAntiAlias = true
                    color = 0xFF10B981.toInt() // Green for completed checkbox
                    style = Paint.Style.FILL
                }

                val checkTickPaint = Paint().apply {
                    isAntiAlias = true
                    color = 0xFFFFFFFF.toInt()
                    strokeWidth = 1.5f
                    style = Paint.Style.STROKE
                }

                val checkTextPaint = TextPaint().apply {
                    isAntiAlias = true
                    color = 0xFF334155.toInt()
                    textSize = 11f
                }

                checklistItems.forEach { item ->
                    val checkSize = 12f
                    val checkSpacing = 8f
                    val itemText = item.text.ifBlank { "(empty task)" }

                    // Parse potential html tags in checklist item too
                    val spannedCheckText = parseHtmlToSpannable(itemText)
                    
                    val itemLayout = StaticLayout.Builder.obtain(
                        spannedCheckText, 0, spannedCheckText.length, checkTextPaint, (maxContentWidth - checkSize - checkSpacing).toInt()
                    )
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(0f, 1.15f)
                        .setIncludePad(true)
                        .build()

                    val itemHeight = maxOf(checkSize, itemLayout.height.toFloat())

                    // Verify page space
                    if (currentY + itemHeight + 8f > pageHeight - bottomMargin) {
                        drawFooter(currentCanvas, pageNumber)
                        pdfDocument.finishPage(currentPage)

                        pageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                        currentPage = pdfDocument.startPage(pageInfo)
                        currentCanvas = currentPage.canvas
                        drawRunningHeader(currentCanvas, pageNumber)
                        currentY = topMargin + 15f
                    }

                    // Draw checkbox box
                    val cbX = leftMargin
                    val cbY = currentY + 1f
                    val boxRect = RectF(cbX, cbY, cbX + checkSize, cbY + checkSize)

                    if (item.isChecked) {
                        currentCanvas.drawRoundRect(boxRect, 2.5f, 2.5f, checkFillPaint)
                        // Draw tick mark
                        currentCanvas.drawLine(cbX + 3f, cbY + 6f, cbX + 5.5f, cbY + 8.5f, checkTickPaint)
                        currentCanvas.drawLine(cbX + 5.5f, cbY + 8.5f, cbX + 9f, cbY + 3.5f, checkTickPaint)
                        
                        // Strike through the finished text paint
                        checkTextPaint.isStrikeThruText = true
                    } else {
                        currentCanvas.drawRoundRect(boxRect, 2.5f, 2.5f, checkOutlinePaint)
                        checkTextPaint.isStrikeThruText = false
                    }

                    // Render checklist item text layout
                    currentCanvas.save()
                    currentCanvas.translate(leftMargin + checkSize + checkSpacing, currentY)
                    itemLayout.draw(currentCanvas)
                    currentCanvas.restore()

                    currentY += itemHeight + 8f
                }
            }

            // Draw footer on final page & finish document
            drawFooter(currentCanvas, pageNumber)
            pdfDocument.finishPage(currentPage)

            // Save PDF to cache directory
            val sanitizedTitle = noteTitle.replace("[^a-zA-Z0-9]".toRegex(), "_")
            val fileName = "SlateNote_${sanitizedTitle}_${System.currentTimeMillis()}.pdf"
            val file = File(context.cacheDir, fileName)

            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            outputStream.flush()
            outputStream.close()
            pdfDocument.close()

            // Trigger Sharing/Sending Action Chooser via FileProvider
            val fileUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                putExtra(Intent.EXTRA_SUBJECT, "Slate Note Link: $noteTitle")
                putExtra(Intent.EXTRA_TEXT, "Exported PDF copy of note: \"$noteTitle\"")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share exported PDF Note")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)

            Toast.makeText(context, "PDF saved successfully & ready!", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "PDF generation failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun parseHtmlToSpannable(htmlText: String): Spannable {
        val ssb = SpannableStringBuilder()
        var index = 0

        // Parse structures exactly matching WYSIWYG Editor Screen tags
        class TagInfo(
            val type: String,
            val startIdx: Int,
            var endIdx: Int,
            val attributes: Map<String, String> = emptyMap()
        )

        val tagStack = mutableListOf<TagInfo>()
        val cleanText = StringBuilder()

        while (index < htmlText.length) {
            val char = htmlText[index]
            if (char == '<') {
                val closeBracket = htmlText.indexOf('>', index)
                if (closeBracket != -1) {
                    val tagContent = htmlText.substring(index + 1, closeBracket)
                    val isClosing = tagContent.startsWith("/")
                    val tagType = if (isClosing) tagContent.substring(1).trim() else tagContent.split(" ")[0].trim()

                    if (isClosing) {
                        val openTagIdx = tagStack.indexOfLast { it.type == tagType && it.endIdx == -1 }
                        if (openTagIdx != -1) {
                            tagStack[openTagIdx].endIdx = cleanText.length
                        }
                    } else {
                        val attributes = mutableMapOf<String, String>()
                        if (tagContent.contains(" ")) {
                            val parts = tagContent.split(" ", limit = 2)
                            val attrString = parts.getOrNull(1) ?: ""
                            val regex = "(\\w+)=[\"']([^\"']*)[\"']".toRegex()
                            regex.findAll(attrString).forEach { match ->
                                val key = match.groupValues[1]
                                val value = match.groupValues[2]
                                attributes[key] = value
                            }
                        }
                        tagStack.add(TagInfo(tagType, cleanText.length, -1, attributes))
                    }
                    index = closeBracket + 1
                    continue
                }
            }
            cleanText.append(char)
            index++
        }

        ssb.append(cleanText.toString())

        for (tag in tagStack) {
            val start = tag.startIdx
            val end = if (tag.endIdx == -1) cleanText.length else tag.endIdx
            if (start >= end || start < 0 || end > cleanText.length) continue

            when (tag.type) {
                "b" -> ssb.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                "i" -> ssb.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                "u" -> ssb.setSpan(UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                "s" -> ssb.setSpan(StrikethroughSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                "size" -> {
                    val value = tag.attributes["value"]
                    val sizePt = when (value) {
                        "small" -> 9
                        "large" -> 18
                        else -> 12
                    }
                    ssb.setSpan(AbsoluteSizeSpan(sizePt, true), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                "bg" -> {
                    val colorAttr = tag.attributes["color"]
                    val colorVal = when (colorAttr) {
                        "red" -> 0x44EF4444.toInt()
                        "green" -> 0x4410B981.toInt()
                        "blue" -> 0x443B82F6.toInt()
                        else -> 0
                    }
                    if (colorVal != 0) {
                        ssb.setSpan(BackgroundColorSpan(colorVal), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
            }
        }

        return ssb
    }
}
