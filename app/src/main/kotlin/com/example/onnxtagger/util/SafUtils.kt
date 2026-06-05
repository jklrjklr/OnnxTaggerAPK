package com.example.onnxtagger.util

import android.content.Context
import android.content.Intent
import android.net.Uri

object SafUtils {

    // FIX-4: reject file:// URIs silently passed by non-standard pickers.
    // Only content:// URIs support takePersistableUriPermission.
    fun persistUriIfContent(context: Context, uri: Uri): Boolean {
        if (uri.scheme != "content") return false
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        return true
    }

    // Lazy validation — called at first access, not at app startup.
    fun isUriPermissionPersisted(context: Context, uri: Uri): Boolean {
        if (uri.scheme != "content") return false
        return context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
    }

    fun validateUris(context: Context, uriStrings: List<String>): Map<String, Boolean> =
        uriStrings.associateWith { uriString ->
            if (uriString.isBlank()) false
            else runCatching { isUriPermissionPersisted(context, Uri.parse(uriString)) }.getOrDefault(false)
        }
}
