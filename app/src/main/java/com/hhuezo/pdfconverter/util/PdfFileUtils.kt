package com.hhuezo.pdfconverter.util

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class PdfFileInfo(
    val displayName: String,
    val sizeBytes: Long,
)

fun Context.queryPdfInfo(uri: Uri): PdfFileInfo {
    var name = "documento.pdf"
    var size = 0L
    contentResolver.query(uri, null, null, null, null)?.use { cursor: Cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst()) {
            if (nameIndex >= 0) {
                name = cursor.getString(nameIndex) ?: name
            }
            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                size = cursor.getLong(sizeIndex)
            }
        }
    }
    return PdfFileInfo(displayName = name, sizeBytes = size)
}

fun Context.isPdfUriAccessible(uri: Uri): Boolean {
    return runCatching {
        contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
    }.getOrDefault(false)
}

fun Context.takePersistableReadPermission(uri: Uri, intentFlags: Int = 0) {
    val flags = (intentFlags and (
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )).let { granted ->
        if (granted != 0) granted else Intent.FLAG_GRANT_READ_URI_PERMISSION
    }
    runCatching {
        contentResolver.takePersistableUriPermission(uri, flags)
    }
}


fun formatFileSize(sizeBytes: Long): String {
    if (sizeBytes <= 0L) return "—"
    val kb = sizeBytes / 1024.0
    return if (kb < 1024) {
        "${kb.roundToInt()} KB"
    } else {
        String.format(Locale.getDefault(), "%.1f MB", kb / 1024.0)
    }
}

fun formatRecentDate(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    val formatter = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.forLanguageTag("es-ES"))
    return formatter.format(Date(epochMillis))
}
