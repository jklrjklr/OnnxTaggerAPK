package com.example.onnxtagger.data.repository

import android.content.Context
import android.net.Uri
import com.example.onnxtagger.util.SafUtils

class ModelRepository {

    // FIX-4: only persist content:// URIs; reject file:// URIs from non-standard pickers
    fun persistUri(context: Context, uri: Uri): Boolean =
        SafUtils.persistUriIfContent(context, uri)

    fun isUriAccessible(context: Context, uri: Uri): Boolean =
        SafUtils.isUriPermissionPersisted(context, uri)

    fun releaseUri(context: Context, uri: Uri) {
        if (uri.scheme == "content") {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
    }
}
