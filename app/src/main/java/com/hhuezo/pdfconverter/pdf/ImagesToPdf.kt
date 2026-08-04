package com.hhuezo.pdfconverter.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream
import kotlin.math.min

/**
 * Builds a multi-page PDF from image files/URIs (camera scans or gallery).
 * Each image becomes one page, fitted to A4 while preserving aspect ratio.
 */
class ImagesToPdf(context: Context) {

    private val appContext = context.applicationContext

    fun create(imageUris: List<Uri>, outputFile: File) {
        require(imageUris.isNotEmpty()) { "No hay imágenes para convertir" }

        val document = PdfDocument()
        try {
            imageUris.forEachIndexed { index, uri ->
                val bitmap = loadOrientedBitmap(uri)
                    ?: error("No se pudo leer la imagen ${index + 1} ($uri)")
                try {
                    val (pageW, pageH) = pageSizeFor(bitmap)
                    val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, index + 1).create()
                    val page = document.startPage(pageInfo)
                    val canvas = page.canvas
                    canvas.drawColor(android.graphics.Color.WHITE)

                    val scale = min(
                        pageW.toFloat() / bitmap.width,
                        pageH.toFloat() / bitmap.height,
                    )
                    val drawW = bitmap.width * scale
                    val drawH = bitmap.height * scale
                    val left = (pageW - drawW) / 2f
                    val top = (pageH - drawH) / 2f
                    val dest = android.graphics.RectF(left, top, left + drawW, top + drawH)
                    canvas.drawBitmap(bitmap, null, dest, null)
                    document.finishPage(page)
                } finally {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
            }

            outputFile.parentFile?.mkdirs()
            if (outputFile.exists()) outputFile.delete()
            outputFile.outputStream().use { document.writeTo(it) }
        } catch (error: Exception) {
            Log.e(TAG, "Error creando PDF: ${error.message}", error)
            throw error
        } finally {
            document.close()
        }
    }

    private fun pageSizeFor(bitmap: Bitmap): Pair<Int, Int> {
        // A4 at ~150 dpi equivalent points for decent scan quality.
        val a4Short = 1240
        val a4Long = 1754
        return if (bitmap.width >= bitmap.height) {
            a4Long to a4Short // landscape
        } else {
            a4Short to a4Long // portrait
        }
    }

    private fun loadOrientedBitmap(uri: Uri): Bitmap? {
        return runCatching {
            val filePath = uri.toFilePathOrNull()
            if (filePath != null) {
                loadOrientedBitmapFromFile(filePath)
            } else {
                loadOrientedBitmapFromStream(uri)
            }
        }.onFailure { error ->
            Log.e(TAG, "Error leyendo imagen $uri: ${error.message}", error)
        }.getOrNull()
    }

    private fun Uri.toFilePathOrNull(): String? {
        if (scheme == "file") return path
        // Also accept absolute paths accidentally passed as opaque strings.
        val asString = toString()
        if (asString.startsWith("/")) return asString
        return null
    }

    private fun loadOrientedBitmapFromFile(path: String): Bitmap? {
        val file = File(path)
        if (!file.exists() || file.length() == 0L) {
            Log.e(TAG, "Archivo de imagen inexistente o vacío: $path")
            return null
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val sample = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeFile(path, opts) ?: return null
        val orientation = runCatching {
            ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        return applyOrientation(decoded, orientation)
    }

    private fun loadOrientedBitmapFromStream(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openInput(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null

        val sample = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = openInput(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null

        val orientation = runCatching {
            openInput(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        return applyOrientation(decoded, orientation)
    }

    private fun openInput(uri: Uri): InputStream? {
        return appContext.contentResolver.openInputStream(uri)
    }

    private fun sampleSizeFor(width: Int, height: Int): Int {
        val maxSide = 2500
        var sample = 1
        val longest = maxOf(width, height).coerceAtLeast(1)
        while (longest / sample > maxSide) {
            sample *= 2
        }
        return sample
    }

    private fun applyOrientation(decoded: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return decoded
        }

        return try {
            val rotated = Bitmap.createBitmap(
                decoded,
                0,
                0,
                decoded.width,
                decoded.height,
                matrix,
                true,
            )
            if (rotated !== decoded && !decoded.isRecycled) decoded.recycle()
            rotated
        } catch (_: Exception) {
            decoded
        }
    }

    companion object {
        private const val TAG = "PdfKitProScan"
    }
}
