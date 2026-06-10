package com.example.ui.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color

object RichTextParser {
    fun parse(text: String): AnnotatedString {
        return buildAnnotatedString {
            var index = 0
            val tagStack = mutableListOf<TagInfo>()
            val cleanTextBuilder = StringBuilder()

            while (index < text.length) {
                if (text[index] == '<') {
                    val closeBracket = text.indexOf('>', index)
                    if (closeBracket != -1) {
                        val tagContent = text.substring(index + 1, closeBracket)
                        val isClosing = tagContent.startsWith("/")
                        val tagType = if (isClosing) tagContent.substring(1) else tagContent.split(" ")[0]

                        if (isClosing) {
                            // Find matching open tag from the end
                            val openTagIdx = tagStack.indexOfLast { it.type == tagType && it.endIdx == -1 }
                            if (openTagIdx != -1) {
                                tagStack[openTagIdx] = tagStack[openTagIdx].copy(endIdx = cleanTextBuilder.length)
                            }
                        } else {
                            val attributes = mutableMapOf<String, String>()
                            if (tagContent.contains(" ")) {
                                val parts = tagContent.split(" ", limit = 2)
                                val attrString = parts.getOrNull(1) ?: ""
                                // Regex support for double and single quotes
                                val regex = "(\\w+)=[\"']([^\"']*)[\"']".toRegex()
                                regex.findAll(attrString).forEach { match ->
                                    val key = match.groupValues[1]
                                    val value = match.groupValues[2]
                                    attributes[key] = value
                                }
                            }
                            tagStack.add(TagInfo(tagType, cleanTextBuilder.length, -1, attributes))
                        }
                        index = closeBracket + 1
                        continue
                    }
                }
                cleanTextBuilder.append(text[index])
                index++
            }

            append(cleanTextBuilder.toString())

            // Apply all matching style tags to the AnnotatedString
            for (tag in tagStack) {
                val start = tag.startIdx
                val end = if (tag.endIdx == -1) cleanTextBuilder.length else tag.endIdx
                if (start >= end || start < 0 || end > cleanTextBuilder.length) continue

                val style = when (tag.type) {
                    "b" -> SpanStyle(fontWeight = FontWeight.Bold)
                    "i" -> SpanStyle(fontStyle = FontStyle.Italic)
                    "u" -> SpanStyle(textDecoration = TextDecoration.Underline)
                    "s" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                    "size" -> {
                        val value = tag.attributes["value"]
                        when (value) {
                            "small" -> SpanStyle(fontSize = 12.sp)
                            "large" -> SpanStyle(fontSize = 20.sp)
                            else -> SpanStyle()
                        }
                    }
                    "bg" -> {
                        val colorAttr = tag.attributes["color"]
                        val color = when (colorAttr) {
                            "red" -> Color(0x66EF4444)
                            "green" -> Color(0x6610B981)
                            "blue" -> Color(0x663B82F6)
                            else -> Color.Transparent
                        }
                        SpanStyle(background = color)
                    }
                    "link" -> {
                        val url = tag.attributes["url"] ?: "https://example.com"
                        addStringAnnotation("URL", url, start, end)
                        SpanStyle(color = Color(0xFF38BDF8), textDecoration = TextDecoration.Underline)
                    }
                    else -> null
                }
                if (style != null) {
                    addStyle(style, start, end)
                }
            }
        }
    }

    private data class TagInfo(
        val type: String,
        val startIdx: Int,
        val endIdx: Int,
        val attributes: Map<String, String> = emptyMap()
    )
}
