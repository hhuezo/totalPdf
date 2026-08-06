package com.hhuezo.pdfconverter.pdf

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Single selectable glyph on a page (normalized coords, origin top-left, 0..1). */
data class PdfTextGlyph(
    val text: String,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    val centerX: Float get() = left + width / 2f
    val centerY: Float get() = top + height / 2f
    val right: Float get() = left + width
    val bottom: Float get() = top + height

    fun contains(x: Float, y: Float, pad: Float = 0.008f): Boolean =
        x >= left - pad &&
            x <= right + pad &&
            y >= top - pad &&
            y <= bottom + pad
}

data class PdfPageTextLayer(
    val pageIndex: Int,
    val glyphs: List<PdfTextGlyph>,
) {
    fun isEmpty(): Boolean = glyphs.isEmpty()

    fun glyphIndexAt(x: Float, y: Float): Int? {
        if (glyphs.isEmpty()) return null

        var bestInside = -1
        var bestInsideDist = Float.POSITIVE_INFINITY
        for (i in glyphs.indices) {
            val glyph = glyphs[i]
            if (!glyph.contains(x, y)) continue
            val dist = hypot(glyph.centerX - x, glyph.centerY - y)
            if (dist < bestInsideDist) {
                bestInsideDist = dist
                bestInside = i
            }
        }
        if (bestInside >= 0) return bestInside

        var best = -1
        var bestDist = Float.POSITIVE_INFINITY
        for (i in glyphs.indices) {
            val glyph = glyphs[i]
            val dx = when {
                x < glyph.left -> glyph.left - x
                x > glyph.right -> x - glyph.right
                else -> 0f
            }
            val dy = when {
                y < glyph.top -> glyph.top - y
                y > glyph.bottom -> y - glyph.bottom
                else -> 0f
            }
            val dist = hypot(dx, dy)
            if (dist < bestDist) {
                bestDist = dist
                best = i
            }
        }
        // Ignore taps far from any text (e.g. empty margins).
        return if (best >= 0 && bestDist <= 0.045f) best else null
    }

    /**
     * Nearest glyph for selection-handle dragging. Always returns a glyph so the
     * handles keep updating even when the finger sits below/beside the text.
     */
    fun glyphIndexForHandle(x: Float, y: Float): Int? {
        if (glyphs.isEmpty()) return null

        var best = 0
        var bestScore = Float.POSITIVE_INFINITY
        for (i in glyphs.indices) {
            val glyph = glyphs[i]
            val dx = when {
                x < glyph.left -> glyph.left - x
                x > glyph.right -> x - glyph.right
                else -> 0f
            }
            // Prefer matching the reading line, then the horizontal position.
            val dy = abs(glyph.centerY - y)
            val score = dx + dy * 2.2f
            if (score < bestScore) {
                bestScore = score
                best = i
            }
        }
        return best
    }

    /**
     * Word (or contiguous token) under [index], expanding across letters/digits
     * until whitespace, line break or a large horizontal gap.
     */
    fun wordRangeAt(index: Int): IntRange {
        if (glyphs.isEmpty()) return IntRange.EMPTY
        val safe = index.coerceIn(0, glyphs.lastIndex)
        if (!isWordChar(glyphs[safe].text)) {
            // Prefer the nearest word around punctuation/space.
            val nearby = nearestWordIndex(safe) ?: return safe..safe
            return expandWord(nearby)
        }
        return expandWord(safe)
    }

    fun textInRange(startIndex: Int, endIndex: Int): String {
        if (glyphs.isEmpty()) return ""
        val start = min(startIndex, endIndex).coerceIn(0, glyphs.lastIndex)
        val end = max(startIndex, endIndex).coerceIn(0, glyphs.lastIndex)
        if (start > end) return ""

        val avgHeight = glyphs
            .subList(start, end + 1)
            .map { it.height }
            .average()
            .toFloat()
            .coerceAtLeast(0.01f)
        val lineTolerance = avgHeight * 0.7f
        val spaceGap = avgHeight * 0.55f

        val builder = StringBuilder()
        for (i in start..end) {
            val glyph = glyphs[i]
            if (i > start) {
                val prev = glyphs[i - 1]
                val sameLine = abs(prev.centerY - glyph.centerY) <= lineTolerance
                if (!sameLine) {
                    builder.append('\n')
                } else {
                    val gap = glyph.left - prev.right
                    val prevEndsSpace = prev.text.lastOrNull()?.isWhitespace() == true
                    val currStartsSpace = glyph.text.firstOrNull()?.isWhitespace() == true
                    if (gap > spaceGap && !prevEndsSpace && !currStartsSpace) {
                        builder.append(' ')
                    }
                }
            }
            builder.append(glyph.text)
        }
        return builder.toString()
    }

    fun rectsInRange(startIndex: Int, endIndex: Int): List<PdfHighlightRect> {
        if (glyphs.isEmpty()) return emptyList()
        val start = min(startIndex, endIndex).coerceIn(0, glyphs.lastIndex)
        val end = max(startIndex, endIndex).coerceIn(0, glyphs.lastIndex)
        val selected = glyphs.subList(start, end + 1)
        if (selected.isEmpty()) return emptyList()

        val avgHeight = selected.map { it.height }.average().toFloat().coerceAtLeast(0.01f)
        val lineTolerance = avgHeight * 0.7f

        val lines = mutableListOf<MutableList<PdfTextGlyph>>()
        for (glyph in selected.sortedWith(compareBy<PdfTextGlyph> { it.centerY }.thenBy { it.left })) {
            val line = lines.lastOrNull()
            if (line != null && abs(line.first().centerY - glyph.centerY) <= lineTolerance) {
                line += glyph
            } else {
                lines += mutableListOf(glyph)
            }
        }

        return lines.map { line ->
            var minLeft = Float.POSITIVE_INFINITY
            var maxRight = Float.NEGATIVE_INFINITY
            var minTop = Float.POSITIVE_INFINITY
            var maxBottom = Float.NEGATIVE_INFINITY
            for (glyph in line) {
                minLeft = min(minLeft, glyph.left)
                maxRight = max(maxRight, glyph.right)
                minTop = min(minTop, glyph.top)
                maxBottom = max(maxBottom, glyph.bottom)
            }
            PdfHighlightRect(
                left = minLeft.coerceIn(0f, 1f),
                top = minTop.coerceIn(0f, 1f),
                width = (maxRight - minLeft).coerceAtLeast(0.004f),
                height = (maxBottom - minTop).coerceAtLeast(0.006f),
            )
        }
    }

    private fun expandWord(index: Int): IntRange {
        var start = index
        var end = index
        while (start > 0 && belongsToSameWord(start - 1, start)) start--
        while (end < glyphs.lastIndex && belongsToSameWord(end, end + 1)) end++
        return start..end
    }

    private fun nearestWordIndex(from: Int): Int? {
        for (distance in 1..8) {
            val left = from - distance
            if (left >= 0 && isWordChar(glyphs[left].text)) return left
            val right = from + distance
            if (right <= glyphs.lastIndex && isWordChar(glyphs[right].text)) return right
        }
        return null
    }

    private fun belongsToSameWord(left: Int, right: Int): Boolean {
        val a = glyphs[left]
        val b = glyphs[right]
        if (!isWordChar(a.text) || !isWordChar(b.text)) return false
        val avgHeight = ((a.height + b.height) / 2f).coerceAtLeast(0.01f)
        if (abs(a.centerY - b.centerY) > avgHeight * 0.7f) return false
        val gap = b.left - a.right
        return gap <= avgHeight * 0.55f
    }

    private fun isWordChar(text: String): Boolean {
        val char = text.firstOrNull() ?: return false
        if (char.isWhitespace()) return false
        return char.isLetterOrDigit() ||
            char == '\'' ||
            char == '’' ||
            char == '-' ||
            char == '_' ||
            char == '­' // soft hyphen
    }
}
