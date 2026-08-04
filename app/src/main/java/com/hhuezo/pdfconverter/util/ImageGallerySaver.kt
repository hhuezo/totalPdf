package com.hhuezo.pdfconverter.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream

object ImageGallerySaver {

    /**
     * Saves image files into Pictures/PdfKit Pro (visible in gallery / files app).
     * @return number of files saved successfully
     */
    fun saveToPictures(context: Context, files: List<File>): Int {
        if (files.isEmpty()) return 0
        var saved = 0
        val resolver = context.contentResolver

        files.forEach { file ->
            val mime = when (file.extension.lowercase()) {
                "png" -> "image/png"
                else -> "image/jpeg"
            }
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PdfKit Pro")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val uri = resolver.insert(collection, values) ?: return@forEach
            runCatching {
                resolver.openOutputStream(uri)?.use { output ->
                    FileInputStream(file).use { input -> input.copyTo(output) }
                } ?: error("No output stream")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                saved++
            }.onFailure {
                resolver.delete(uri, null, null)
            }
        }
        return saved
    }
}
