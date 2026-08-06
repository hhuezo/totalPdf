package com.hhuezo.pdfconverter.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Rotates pages in a PDF and writes a new document.
 * [pageRotations] maps 0-based page index to degrees to add (90, 180, or 270).
 * Pages not in the map or with 0° are left unchanged.
 */
class PdfPageRotator(context: Context) {

    private val appContext = context.applicationContext

    fun rotatePages(
        uri: Uri,
        pageRotations: Map<Int, Int>,
        outputFile: File,
    ): Int {
        val changes = pageRotations
            .filter { (_, degrees) -> normalizeRotation(degrees) != 0 }
        require(changes.isNotEmpty()) { "No hay páginas para rotar" }
        ensurePdfBoxInitialized(appContext)

        val sourceCopy = File(appContext.cacheDir, "rotate_src_${System.currentTimeMillis()}.pdf")
        return try {
            copyUriToFile(uri, sourceCopy)
            PDDocument.load(sourceCopy).use { document ->
                val pageCount = document.numberOfPages
                require(pageCount > 0) { "El PDF no tiene páginas" }

                changes.forEach { (index, degreesDelta) ->
                    require(index in 0 until pageCount) { "Índice de página no válida" }
                    require(degreesDelta == 90 || degreesDelta == 180 || degreesDelta == 270) {
                        "Rotación no válida"
                    }
                    val page = document.getPage(index)
                    page.rotation = normalizeRotation(page.rotation + degreesDelta)
                }

                outputFile.parentFile?.mkdirs()
                if (outputFile.exists()) outputFile.delete()
                document.save(outputFile)
                pageCount
            }
        } finally {
            sourceCopy.delete()
        }
    }

    private fun normalizeRotation(degrees: Int): Int {
        val normalized = ((degrees % 360) + 360) % 360
        return when (normalized) {
            0, 90, 180, 270 -> normalized
            else -> 0
        }
    }

    private fun copyUriToFile(uri: Uri, outputFile: File) {
        outputFile.parentFile?.mkdirs()
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            outputFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("No se pudo copiar el PDF original")
        if (outputFile.length() < 5L) {
            error("El PDF original está vacío o no se pudo leer")
        }
    }

    companion object {
        private val initialized = AtomicBoolean(false)

        private fun ensurePdfBoxInitialized(context: Context) {
            if (initialized.compareAndSet(false, true)) {
                PDFBoxResourceLoader.init(context)
            }
        }
    }
}
