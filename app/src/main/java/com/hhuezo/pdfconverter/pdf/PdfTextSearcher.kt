package com.hhuezo.pdfconverter.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Normalized rectangle on a page (origin top-left, values 0..1). */
data class PdfHighlightRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

data class PdfSearchMatch(
    val pageIndex: Int,
    val snippet: String,
    val rects: List<PdfHighlightRect>,
)

data class PdfSearchResult(
    val matches: List<PdfSearchMatch>,
    val hadExtractableText: Boolean,
)

class PdfTextSearcher(context: Context) {

    private val appContext = context.applicationContext
    private val pageLayerCache = mutableMapOf<String, PdfPageTextLayer>()

    fun loadPageTextLayer(uri: Uri, pageIndex: Int): PdfPageTextLayer {
        val cacheKey = "${uri}#$pageIndex"
        pageLayerCache[cacheKey]?.let { return it }

        ensurePdfBoxInitialized(appContext)

        val input = appContext.contentResolver.openInputStream(uri)
            ?: error("No se pudo abrir el PDF para leer texto")

        val layer = input.use { stream ->
            PDDocument.load(stream).use { document ->
                require(pageIndex in 0 until document.numberOfPages) {
                    "Página fuera de rango: $pageIndex"
                }
                val pageNumber = pageIndex + 1
                val collector = PageTextCollector().apply {
                    startPage = pageNumber
                    endPage = pageNumber
                    sortByPosition = true
                }
                collector.getText(document)
                PdfPageTextLayer(
                    pageIndex = pageIndex,
                    glyphs = buildGlyphs(collector.positions),
                )
            }
        }
        pageLayerCache[cacheKey] = layer
        return layer
    }

    fun clearPageTextCache() {
        pageLayerCache.clear()
    }

    fun search(uri: Uri, query: String): PdfSearchResult {
        val needle = normalize(query)
        if (needle.isEmpty()) {
            return PdfSearchResult(matches = emptyList(), hadExtractableText = true)
        }

        ensurePdfBoxInitialized(appContext)

        val input = appContext.contentResolver.openInputStream(uri)
            ?: error("No se pudo abrir el PDF para buscar")

        input.use { stream ->
            PDDocument.load(stream).use { document ->
                val matches = mutableListOf<PdfSearchMatch>()
                var hadText = false

                for (pageNumber in 1..document.numberOfPages) {
                    val collector = PageTextCollector().apply {
                        startPage = pageNumber
                        endPage = pageNumber
                        sortByPosition = true
                    }
                    collector.getText(document)

                    val (searchText, positionMap) = buildSearchIndex(collector.positions)
                    if (searchText.isNotBlank()) {
                        hadText = true
                    }

                    var fromIndex = 0
                    while (fromIndex <= searchText.length - needle.length) {
                        val foundAt = searchText.indexOf(needle, fromIndex)
                        if (foundAt < 0) break
                        val endExclusive = foundAt + needle.length
                        val matchedPositions = positionMap
                            .subList(foundAt, endExclusive)
                            .filterNotNull()
                            .distinct()
                        matches += PdfSearchMatch(
                            pageIndex = pageNumber - 1,
                            snippet = buildSnippet(searchText, foundAt, needle.length),
                            rects = buildHighlightRects(matchedPositions),
                        )
                        fromIndex = endExclusive.coerceAtLeast(foundAt + 1)
                    }
                }

                return PdfSearchResult(
                    matches = matches,
                    hadExtractableText = hadText,
                )
            }
        }
    }

    /**
     * Builds a lowercase, whitespace-collapsed string and a parallel map to [TextPosition]s
     * so match ranges can be converted into highlight rectangles.
     */
    private fun buildSearchIndex(
        positions: List<TextPosition>,
    ): Pair<String, List<TextPosition?>> {
        val text = StringBuilder()
        val map = mutableListOf<TextPosition?>()

        for (position in positions) {
            val unicode = position.unicode ?: continue
            for (char in unicode) {
                val normalized = if (char.isWhitespace()) ' ' else char.lowercaseChar()
                if (normalized == ' ' && text.lastOrNull() == ' ') continue
                text.append(normalized)
                map.add(if (normalized == ' ') null else position)
            }
        }

        var start = 0
        var end = text.length
        while (start < end && text[start] == ' ') start++
        while (end > start && text[end - 1] == ' ') end--
        return text.substring(start, end) to map.subList(start, end).toList()
    }

    private fun buildGlyphs(positions: List<TextPosition>): List<PdfTextGlyph> {
        if (positions.isEmpty()) return emptyList()
        val sample = positions.first()
        val (displayWidth, displayHeight) = displaySize(sample)
        if (displayWidth <= 0f || displayHeight <= 0f) return emptyList()

        return positions.mapNotNull { position ->
            val unicode = position.unicode?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val glyphHeight = position.height
                .coerceAtLeast(position.fontSizeInPt * 0.7f)
                .coerceAtLeast(4f)
            val glyphWidth = position.width.coerceAtLeast(1f)
            val left = (position.x / displayWidth).coerceIn(0f, 1f)
            val top = ((position.y - glyphHeight) / displayHeight).coerceIn(0f, 1f)
            val right = ((position.x + glyphWidth) / displayWidth).coerceIn(0f, 1f)
            val bottom = ((position.y + glyphHeight * 0.2f) / displayHeight).coerceIn(0f, 1f)
            PdfTextGlyph(
                text = unicode,
                left = left,
                top = top,
                width = (right - left).coerceAtLeast(0.002f),
                height = (bottom - top).coerceAtLeast(0.004f),
            )
        }
    }

    private fun buildHighlightRects(positions: List<TextPosition>): List<PdfHighlightRect> {
        if (positions.isEmpty()) return emptyList()

        val sample = positions.first()
        val (displayWidth, displayHeight) = displaySize(sample)
        if (displayWidth <= 0f || displayHeight <= 0f) return emptyList()

        val lineTolerance = positions
            .map { it.height.coerceAtLeast(1f) }
            .average()
            .toFloat()
            .coerceAtLeast(2f) * 0.6f

        // getX()/getY() already use upper-left origin and page rotation.
        val lines = mutableListOf<MutableList<TextPosition>>()
        for (position in positions.sortedWith(compareBy<TextPosition> { it.y }.thenBy { it.x })) {
            val line = lines.lastOrNull()
            if (line != null && abs(line.first().y - position.y) <= lineTolerance) {
                line += position
            } else {
                lines += mutableListOf(position)
            }
        }

        return lines.mapNotNull { line ->
            var minLeft = Float.POSITIVE_INFINITY
            var maxRight = Float.NEGATIVE_INFINITY
            var minTop = Float.POSITIVE_INFINITY
            var maxBottom = Float.NEGATIVE_INFINITY

            for (position in line) {
                // getY() is baseline distance from the top of the page.
                val glyphHeight = position.height
                    .coerceAtLeast(position.fontSizeInPt * 0.7f)
                    .coerceAtLeast(4f)
                val glyphWidth = position.width.coerceAtLeast(1f)
                val left = position.x
                val top = position.y - glyphHeight
                val bottom = position.y + glyphHeight * 0.2f

                minLeft = min(minLeft, left)
                maxRight = max(maxRight, left + glyphWidth)
                minTop = min(minTop, top)
                maxBottom = max(maxBottom, bottom)
            }

            if (!minLeft.isFinite() || !maxRight.isFinite()) return@mapNotNull null

            val padX = 1f
            val left = ((minLeft - padX) / displayWidth).coerceIn(0f, 1f)
            val top = (minTop / displayHeight).coerceIn(0f, 1f)
            val right = ((maxRight + padX) / displayWidth).coerceIn(0f, 1f)
            val bottom = (maxBottom / displayHeight).coerceIn(0f, 1f)

            PdfHighlightRect(
                left = left,
                top = top,
                width = (right - left).coerceAtLeast(0.004f),
                height = (bottom - top).coerceAtLeast(0.006f),
            )
        }
    }

    /**
     * PdfBox stores crop-box width/height; after page rotation the visible page
     * (and Android PdfRenderer) swaps axes for 90°/270°.
     */
    private fun displaySize(position: TextPosition): Pair<Float, Float> {
        val rotation = position.rotation
        return if (rotation == 90 || rotation == 270) {
            position.pageHeight to position.pageWidth
        } else {
            position.pageWidth to position.pageHeight
        }
    }

    private fun buildSnippet(text: String, matchStart: Int, matchLength: Int): String {
        val radius = 36
        val start = (matchStart - radius).coerceAtLeast(0)
        val end = (matchStart + matchLength + radius).coerceAtMost(text.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < text.length) "…" else ""
        return prefix + text.substring(start, end).trim() + suffix
    }

    private fun normalize(value: String): String =
        value.lowercase()
            .replace(WHITESPACE, " ")
            .trim()

    private class PageTextCollector : PDFTextStripper() {
        val positions = mutableListOf<TextPosition>()

        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            positions += textPositions
        }
    }

    companion object {
        private val WHITESPACE = Regex("\\s+")
        private val initialized = AtomicBoolean(false)

        private fun ensurePdfBoxInitialized(context: Context) {
            if (initialized.compareAndSet(false, true)) {
                PDFBoxResourceLoader.init(context)
            }
        }
    }
}
