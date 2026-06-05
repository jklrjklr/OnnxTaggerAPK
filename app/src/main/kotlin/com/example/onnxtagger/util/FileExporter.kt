package com.example.onnxtagger.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FileExporter {

    suspend fun export(context: Context, content: String, suggestedName: String): Result<Uri> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, suggestedName)
                        put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    }
                    val uri = context.contentResolver.insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                    ) ?: error("MediaStore insert returned null")
                    context.contentResolver.openOutputStream(uri)!!.use {
                        it.write(content.toByteArray(Charsets.UTF_8))
                    }
                    uri
                } else {
                    throw UnsupportedOperationException("API < 29: use SAF CreateDocument")
                }
            }
        }
}
