package com.nexusbudget.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/** A statement or CSV file the user picked, opened or shared into the app. */
data class ImportFile(val name: String?, val text: String)

object ImportFiles {
    private const val MAX_BYTES = 10 * 1024 * 1024

    /** Reads a picked or shared file, or returns null if it can't be read or is too large to be a statement. */
    fun read(context: Context, uri: Uri): ImportFile? = runCatching {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                if (out.size() > MAX_BYTES) return@runCatching null
            }
            out.toByteArray()
        } ?: return@runCatching null
        // Older statement files are often Windows-1252 rather than UTF-8.
        val utf8 = String(bytes, Charsets.UTF_8)
        val text = if ('�' in utf8) String(bytes, Charset.forName("windows-1252")) else utf8
        ImportFile(name, text.removePrefix("﻿"))
    }.getOrNull()
}
