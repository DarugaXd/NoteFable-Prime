package com.example

import android.content.Context
import android.graphics.Typeface
import android.text.style.AbsoluteSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.test.core.app.ApplicationProvider
import com.example.ui.util.PdfExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SlateNotes", appName)
  }

  @Test
  fun `test HTML parser for rich text styles`() {
    val richText = "This is <b>bold</b> text and <i>italic</i> content."
    val spannable = PdfExporter.parseHtmlToSpannable(richText)

    // Check clean text output
    assertEquals("This is bold text and italic content.", spannable.toString())

    // Check bold span
    val boldSpans = spannable.getSpans(8, 12, StyleSpan::class.java)
    assertNotNull(boldSpans)
    assertTrue(boldSpans.isNotEmpty())
    assertEquals(Typeface.BOLD, boldSpans[0].style)

    // Check italic span
    val italicSpans = spannable.getSpans(22, 28, StyleSpan::class.java)
    assertNotNull(italicSpans)
    assertTrue(italicSpans.isNotEmpty())
    assertEquals(Typeface.ITALIC, italicSpans[0].style)
  }

  @Test
  fun `test HTML parser for nested or custom size tags`() {
    val richText = "Say <size value=\"large\">Large</size> with <strike><s>stripe</s></strike> line."
    val spannable = PdfExporter.parseHtmlToSpannable(richText)

    // Check custom font size
    val sizeSpans = spannable.getSpans(4, 9, AbsoluteSizeSpan::class.java)
    assertNotNull(sizeSpans)
    assertTrue(sizeSpans.isNotEmpty())
    assertEquals(18, sizeSpans[0].size)

    // Check strikethrough tag
    val strikeSpans = spannable.getSpans(15, 21, StrikethroughSpan::class.java)
    assertNotNull(strikeSpans)
    assertTrue(strikeSpans.isNotEmpty())
  }
}

