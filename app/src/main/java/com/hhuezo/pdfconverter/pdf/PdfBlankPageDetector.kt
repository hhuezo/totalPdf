package com.hhuezo.pdfconverter.pdf

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Detects PDF pages that are effectively blank by rendering at low resolution
 * and measuring how much of the page differs from white.
 */
object PdfBlankPageDetector {

    /** A page with less than this fraction of non-blank pixels is considered empty. */
    const val CONTENT_THRESHOLD = 0.05f

    private const val ANALYSIS_WIDTH_PX = 240
    private const val WHITE_CHANNEL_MIN = 240
    private const val ALPHA_MIN = 128
    private const val SAMPLE_STEP = 3

    fun findBlankPages(session: PdfDocumentSession): List<Int> {
        val blanks = mutableListOf<Int>()
        for (pageIndex in 0 until session.pageCount) {
            val bitmap = session.renderPage(pageIndex, ANALYSIS_WIDTH_PX)
            if (isBlank(bitmap)) {
                blanks.add(pageIndex)
            }
        }
        return blanks
    }

    fun isBlank(bitmap: Bitmap): Boolean =
        estimateContentRatio(bitmap) < CONTENT_THRESHOLD

    fun estimateContentRatio(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        if (width == 0 || height == 0) return 0f

        var sampled = 0
        var nonBlank = 0
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                sampled++
                if (!isBlankPixel(bitmap.getPixel(x, y))) {
                    nonBlank++
                }
                x += SAMPLE_STEP
            }
            y += SAMPLE_STEP
        }
        return if (sampled == 0) 0f else nonBlank.toFloat() / sampled
    }

    private fun isBlankPixel(color: Int): Boolean {
        if (Color.alpha(color) < ALPHA_MIN) return true
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return r >= WHITE_CHANNEL_MIN && g >= WHITE_CHANNEL_MIN && b >= WHITE_CHANNEL_MIN
    }
}
